package com.drawgame.game.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.drawgame.game.model.GameStateData;
import com.drawgame.game.model.MatchAwardData;
import com.drawgame.game.model.PlayerScoreData;
import com.drawgame.game.model.RoundRecapData;
import com.drawgame.game.model.WordChoiceData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisGameRepository {
    private static final String SUBMIT_GUESS_LUA = """
            if redis.call('HGET', KEYS[3], 'gameId') ~= ARGV[5]
                    or redis.call('HGET', KEYS[3], 'currentRound') ~= ARGV[6]
                    or redis.call('HGET', KEYS[3], 'roundPhase') ~= 'DRAWING'
                    or tonumber(ARGV[7]) >= tonumber(redis.call('HGET', KEYS[3], 'roundEndsAt') or '0') then
                return -1
            end
            local added = redis.call('SADD', KEYS[1], ARGV[1])
            if added == 1 then
                redis.call('HINCRBY', KEYS[2], ARGV[1], tonumber(ARGV[2]))
                if tonumber(ARGV[3]) > 0 and ARGV[4] ~= '' then
                    redis.call('HINCRBY', KEYS[2], ARGV[4], tonumber(ARGV[3]))
                end
                redis.call('HSET', KEYS[4], ARGV[1], ARGV[8])
                redis.call('EXPIRE', KEYS[4], 14400)
                return 1
            end
            return 0
            """;

    private static final String SELECT_WORD_LUA = """
            if redis.call('HGET', KEYS[1], 'gameId') ~= ARGV[1]
                    or redis.call('HGET', KEYS[1], 'currentRound') ~= ARGV[2]
                    or redis.call('HGET', KEYS[1], 'roundPhase') ~= 'WORD_SELECTION' then return -2 end
            local now = tonumber(ARGV[3])
            local expires = tonumber(redis.call('HGET', KEYS[1], 'phaseEndsAt') or '0')
            local automatic = ARGV[5] == '1'
            if automatic and now < expires then return -3 end
            if not automatic and now >= expires then return -4 end
            local ok, choices = pcall(cjson.decode, redis.call('HGET', KEYS[1], 'wordChoicesJson') or '[]')
            if not ok or type(choices) ~= 'table' then return -5 end
            local word = nil
            for _, choice in ipairs(choices) do
                if choice.choiceId == ARGV[4] then word = choice.displayWord break end
            end
            if not word or word == '' then return -1 end
            redis.call('HSET', KEYS[1], 'secretWord', word, 'roundPhase', 'COUNTDOWN',
                    'phaseStartedAt', ARGV[3], 'phaseEndsAt', ARGV[6], 'wordChoicesJson', '[]', 'hintStage', '0')
            return 1
            """;

    private static final String BEGIN_DRAWING_LUA = """
            if redis.call('HGET', KEYS[1], 'gameId') ~= ARGV[1]
                    or redis.call('HGET', KEYS[1], 'currentRound') ~= ARGV[2]
                    or redis.call('HGET', KEYS[1], 'roundPhase') ~= 'COUNTDOWN' then return 0 end
            if tonumber(ARGV[3]) < tonumber(redis.call('HGET', KEYS[1], 'phaseEndsAt') or '0') then return -1 end
            redis.call('HSET', KEYS[1], 'roundPhase', 'DRAWING', 'phaseStartedAt', ARGV[3],
                    'phaseEndsAt', ARGV[4], 'roundStartedAt', ARGV[3], 'roundEndsAt', ARGV[4],
                    'roundDurationSeconds', ARGV[5], 'hint', ARGV[6], 'hintStage', '0',
                    'hintRevealScheduleJson', ARGV[7], 'roundStartScoresJson', ARGV[8])
            return 1
            """;

    private static final String BEGIN_RECAP_LUA = """
            if redis.call('HGET', KEYS[1], 'gameId') ~= ARGV[1]
                    or redis.call('HGET', KEYS[1], 'currentRound') ~= ARGV[2]
                    or redis.call('HGET', KEYS[1], 'roundPhase') ~= 'DRAWING' then return 0 end
            redis.call('HSET', KEYS[1], 'roundPhase', 'ROUND_RECAP', 'phaseStartedAt', ARGV[3],
                    'phaseEndsAt', ARGV[4], 'roundRecapJson', ARGV[5])
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<Long> submitGuessScript = new DefaultRedisScript<>(SUBMIT_GUESS_LUA, Long.class);
    private final DefaultRedisScript<Long> selectWordScript = new DefaultRedisScript<>(SELECT_WORD_LUA, Long.class);
    private final DefaultRedisScript<Long> beginDrawingScript = new DefaultRedisScript<>(BEGIN_DRAWING_LUA, Long.class);
    private final DefaultRedisScript<Long> beginRecapScript = new DefaultRedisScript<>(BEGIN_RECAP_LUA, Long.class);

    private static String stateKey(String roomId) { return "game:" + roomId + ":state"; }
    private static String scoresKey(String roomId) { return "game:" + roomId + ":scores"; }
    private static String guessedKey(String roomId) { return "game:" + roomId + ":guessed"; }
    private static String namesKey(String roomId) { return "game:" + roomId + ":names"; }
    private static String lockKey(String roomId) { return "game:" + roomId + ":start_lock"; }
    private static String guessTimesKey(String roomId, int round) {
        return "game:" + roomId + ":round:" + round + ":guess-times";
    }

    public boolean tryLockGameStart(String roomId) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey(roomId), "LOCKED", Duration.ofSeconds(10)));
    }

    public void releaseGameStartLock(String roomId) { redisTemplate.delete(lockKey(roomId)); }

    public void saveState(GameStateData state) {
        Map<String, String> map = new HashMap<>();
        map.put("gameId", value(state.getGameId()));
        map.put("status", value(state.getStatus(), "WAITING"));
        map.put("roundPhase", value(state.getRoundPhase()));
        map.put("currentRound", String.valueOf(state.getCurrentRound()));
        map.put("totalRounds", String.valueOf(state.getTotalRounds()));
        map.put("drawerId", value(state.getDrawerId()));
        map.put("secretWord", value(state.getSecretWord()));
        map.put("hint", value(state.getHint()));
        map.put("roundStartedAt", String.valueOf(state.getRoundStartedAt()));
        map.put("roundEndsAt", String.valueOf(state.getRoundEndsAt()));
        map.put("phaseStartedAt", String.valueOf(state.getPhaseStartedAt()));
        map.put("phaseEndsAt", String.valueOf(state.getPhaseEndsAt()));
        map.put("roundDurationSeconds", String.valueOf(state.getRoundDurationSeconds()));
        map.put("hintStage", String.valueOf(state.getHintStage()));
        map.put("playerOrder", state.getPlayerOrder() == null ? "" : String.join(",", state.getPlayerOrder()));
        map.put("selectedCategories", state.getSelectedCategories() == null ? "" : String.join(",", state.getSelectedCategories()));
        map.put("wordChoicesJson", toJson(state.getWordChoices()));
        map.put("hintRevealScheduleJson", toJson(state.getHintRevealSchedule()));
        map.put("roundStartScoresJson", toJson(state.getRoundStartScores()));
        map.put("roundRecapJson", toJson(state.getRoundRecap()));
        map.put("roundResultsJson", toJson(state.getRoundResults()));
        map.put("awardsJson", toJson(state.getAwards()));
        redisTemplate.opsForHash().putAll(stateKey(state.getRoomId()), map);
    }

    public Optional<GameStateData> findState(String roomId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(stateKey(roomId));
        if (entries == null || entries.isEmpty()) return Optional.empty();
        String playerOrderValue = field(entries, "playerOrder", "");
        String phase = field(entries, "roundPhase", "");
        if (phase.isBlank()) {
            phase = "ROUND_ENDED".equals(field(entries, "status", "")) ? "ROUND_RECAP" : "DRAWING";
        }
        List<String> playerOrder = playerOrderValue.isEmpty() ? List.of() : Arrays.asList(playerOrderValue.split(","));
        String selectedValue = field(entries, "selectedCategories", "");
        List<String> categories = selectedValue.isBlank() ? List.of() : Arrays.asList(selectedValue.split(","));
        long roundStartedAt = longField(entries, "roundStartedAt", 0);
        long roundEndsAt = longField(entries, "roundEndsAt", 0);
        GameStateData state = GameStateData.builder()
                .roomId(roomId)
                .gameId(field(entries, "gameId", ""))
                .status(field(entries, "status", "WAITING"))
                .roundPhase(phase)
                .currentRound(intField(entries, "currentRound", 0))
                .totalRounds(intField(entries, "totalRounds", 5))
                .drawerId(field(entries, "drawerId", ""))
                .secretWord(field(entries, "secretWord", ""))
                .hint(field(entries, "hint", ""))
                .roundStartedAt(roundStartedAt)
                .roundEndsAt(roundEndsAt)
                .phaseStartedAt(longField(entries, "phaseStartedAt", roundStartedAt))
                .phaseEndsAt(longField(entries, "phaseEndsAt", roundEndsAt))
                .roundDurationSeconds(intField(entries, "roundDurationSeconds", 60))
                .hintStage(intField(entries, "hintStage", 0))
                .playerOrder(playerOrder)
                .selectedCategories(categories)
                .wordChoices(fromJson(field(entries, "wordChoicesJson", "[]"), new TypeReference<List<WordChoiceData>>() {}, List.of()))
                .hintRevealSchedule(fromJson(field(entries, "hintRevealScheduleJson", "[]"), new TypeReference<List<Set<Integer>>>() {}, List.of()))
                .roundStartScores(fromJson(field(entries, "roundStartScoresJson", "{}"), new TypeReference<Map<String, Integer>>() {}, Map.of()))
                .roundRecap(fromJson(field(entries, "roundRecapJson", "null"), new TypeReference<RoundRecapData>() {}, null))
                .roundResults(fromJson(field(entries, "roundResultsJson", "[]"), new TypeReference<List<RoundRecapData>>() {}, List.of()))
                .awards(fromJson(field(entries, "awardsJson", "[]"), new TypeReference<List<MatchAwardData>>() {}, List.of()))
                .scores(getScores(roomId))
                .build();
        return Optional.of(state);
    }

    public Set<String> findActiveRoomIds() {
        Set<String> rooms = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> found = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions().match("game:*:state").count(256).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    String[] parts = key.split(":");
                    if (parts.length == 3 && "game".equals(parts[0]) && "state".equals(parts[2])) {
                        found.add(parts[1]);
                    }
                }
            }
            return found;
        });
        return rooms == null ? Set.of() : rooms;
    }

    public void setPlayerScore(String roomId, String playerId, int score) {
        redisTemplate.opsForHash().put(scoresKey(roomId), playerId, String.valueOf(score));
    }

    public void setUsernames(String roomId, Map<String, String> playerIdToUsername) {
        Map<String, String> hash = new HashMap<>();
        playerIdToUsername.forEach((id, name) -> { if (name != null && !name.isBlank()) hash.put(id, name); });
        if (!hash.isEmpty()) redisTemplate.opsForHash().putAll(namesKey(roomId), hash);
    }

    /** Returns 1 for success, 0 for duplicate, and -1 when phase/deadline is no longer valid. */
    public int atomicSubmitGuess(String roomId, String gameId, int round, String playerId,
                                 int scoreAwarded, int drawerBonus, String drawerId,
                                 long now, long elapsedMillis) {
        Long result = redisTemplate.execute(submitGuessScript,
                List.of(guessedKey(roomId), scoresKey(roomId), stateKey(roomId), guessTimesKey(roomId, round)),
                playerId, String.valueOf(scoreAwarded), String.valueOf(drawerBonus), value(drawerId),
                value(gameId), String.valueOf(round), String.valueOf(now), String.valueOf(elapsedMillis));
        return result == null ? -1 : result.intValue();
    }

    /** Returns 1 if this user/timeout won the selection race; other return codes reject it. */
    public int atomicSelectWord(String roomId, String gameId, int round, String choiceId,
                                long now, long countdownEndsAt, boolean automatic) {
        Long result = redisTemplate.execute(selectWordScript, List.of(stateKey(roomId)),
                value(gameId), String.valueOf(round), String.valueOf(now), value(choiceId),
                automatic ? "1" : "0", String.valueOf(countdownEndsAt));
        return result == null ? -5 : result.intValue();
    }

    public int beginDrawing(String roomId, String gameId, int round, long now, long drawingEndsAt,
                           int durationSeconds, String initialHint, List<Set<Integer>> revealSchedule,
                           Map<String, Integer> roundStartScores) {
        Long result = redisTemplate.execute(beginDrawingScript, List.of(stateKey(roomId)),
                value(gameId), String.valueOf(round), String.valueOf(now), String.valueOf(drawingEndsAt),
                String.valueOf(durationSeconds), value(initialHint), toJson(revealSchedule), toJson(roundStartScores));
        return result == null ? 0 : result.intValue();
    }

    public boolean beginRecap(String roomId, String gameId, int round, long now, long recapEndsAt,
                              RoundRecapData recap) {
        Long result = redisTemplate.execute(beginRecapScript, List.of(stateKey(roomId)),
                value(gameId), String.valueOf(round), String.valueOf(now), String.valueOf(recapEndsAt), toJson(recap));
        return result != null && result == 1L;
    }

    public Map<String, Long> getRoundGuessTimings(String roomId, int round) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(guessTimesKey(roomId, round));
        Map<String, Long> timings = new HashMap<>();
        if (entries != null) entries.forEach((player, millis) -> timings.put(player.toString(), Long.parseLong(millis.toString())));
        return timings;
    }

    public List<PlayerScoreData> getScores(String roomId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(scoresKey(roomId));
        Map<Object, Object> usernames = redisTemplate.opsForHash().entries(namesKey(roomId));
        Set<String> members = redisTemplate.opsForSet().members(guessedKey(roomId));
        final Set<String> guessed = members == null ? Set.of() : members;
        List<PlayerScoreData> scores = new ArrayList<>();
        if (entries != null) entries.forEach((id, score) -> {
            String playerId = id.toString();
            scores.add(PlayerScoreData.builder().playerId(playerId)
                    .username(usernames == null ? null : (String) usernames.get(id))
                    .score(Integer.parseInt(score.toString())).hasGuessed(guessed.contains(playerId)).build());
        });
        return scores;
    }

    public boolean hasPlayerGuessed(String roomId, String playerId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(guessedKey(roomId), playerId));
    }

    public long getGuessedCount(String roomId) {
        Long count = redisTemplate.opsForSet().size(guessedKey(roomId));
        return count == null ? 0 : count;
    }

    public void clearGuessed(String roomId) { redisTemplate.delete(guessedKey(roomId)); }

    public void clearRoundGuesses(String roomId, int round) {
        redisTemplate.delete(List.of(guessedKey(roomId), guessTimesKey(roomId, round)));
    }

    public void deleteGame(String roomId) {
        int rounds = findState(roomId).map(GameStateData::getCurrentRound).orElse(0);
        List<String> keys = new ArrayList<>(List.of(stateKey(roomId), scoresKey(roomId), guessedKey(roomId), namesKey(roomId)));
        for (int round = 1; round <= rounds; round++) keys.add(guessTimesKey(roomId, round));
        redisTemplate.delete(keys);
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) {
            log.error("Could not serialize authoritative game state field", e);
            throw new IllegalStateException("Unable to persist game state", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type, T fallback) {
        try { return objectMapper.readValue(json, type); }
        catch (Exception e) {
            log.warn("Ignoring malformed persisted game state JSON field");
            return fallback;
        }
    }

    private static String value(String value) { return value == null ? "" : value; }
    private static String value(String value, String fallback) { return value == null ? fallback : value; }
    private static String field(Map<Object, Object> values, String key, String fallback) {
        Object value = values.get(key);
        return value == null ? fallback : value.toString();
    }
    private static int intField(Map<Object, Object> values, String key, int fallback) {
        try { return Integer.parseInt(field(values, key, String.valueOf(fallback))); }
        catch (NumberFormatException e) { return fallback; }
    }
    private static long longField(Map<Object, Object> values, String key, long fallback) {
        try { return Long.parseLong(field(values, key, String.valueOf(fallback))); }
        catch (NumberFormatException e) { return fallback; }
    }
}
