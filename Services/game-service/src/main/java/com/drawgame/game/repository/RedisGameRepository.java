package com.drawgame.game.repository;

import com.drawgame.game.model.GameStateData;
import com.drawgame.game.model.PlayerScoreData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.*;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisGameRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String SUBMIT_GUESS_LUA =
            "local added = redis.call('SADD', KEYS[1], ARGV[1])\n" +
            "if added == 1 then\n" +
            "    redis.call('HINCRBY', KEYS[2], ARGV[1], tonumber(ARGV[2]))\n" +
            "    if tonumber(ARGV[3]) > 0 and ARGV[4] ~= '' then\n" +
            "        redis.call('HINCRBY', KEYS[2], ARGV[4], tonumber(ARGV[3]))\n" +
            "    end\n" +
            "    return 1\n" +
            "else\n" +
            "    return 0\n" +
            "end";

    private final DefaultRedisScript<Long> submitGuessScript = new DefaultRedisScript<>(SUBMIT_GUESS_LUA, Long.class);

    private static String getKey(String roomId) {
        return "game:" + roomId + ":state";
    }

    private static String getScoresKey(String roomId) {
        return "game:" + roomId + ":scores";
    }

    private static String getGuessedKey(String roomId) {
        return "game:" + roomId + ":guessed";
    }

    private static String getLockKey(String roomId) {
        return "game:" + roomId + ":start_lock";
    }

    public boolean tryLockGameStart(String roomId) {
        String lockKey = getLockKey(roomId);
        Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(10));
        return Boolean.TRUE.equals(success);
    }

    public void releaseGameStartLock(String roomId) {
        redisTemplate.delete(getLockKey(roomId));
    }

    public void saveState(GameStateData state) {
        String key = getKey(state.getRoomId());
        Map<String, String> map = new HashMap<>();
        map.put("status", state.getStatus() != null ? state.getStatus() : "WAITING");
        map.put("currentRound", String.valueOf(state.getCurrentRound()));
        map.put("totalRounds", String.valueOf(state.getTotalRounds()));
        map.put("drawerId", state.getDrawerId() != null ? state.getDrawerId() : "");
        map.put("secretWord", state.getSecretWord() != null ? state.getSecretWord() : "");
        map.put("hint", state.getHint() != null ? state.getHint() : "");
        map.put("roundStartedAt", String.valueOf(state.getRoundStartedAt()));
        map.put("roundEndsAt", String.valueOf(state.getRoundEndsAt()));
        map.put("playerOrder", state.getPlayerOrder() != null ? String.join(",", state.getPlayerOrder()) : "");

        redisTemplate.opsForHash().putAll(key, map);
    }

    public Optional<GameStateData> findState(String roomId) {
        String key = getKey(roomId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }

        String poStr = (String) entries.getOrDefault("playerOrder", "");
        List<String> playerOrder = poStr.isEmpty() ? Collections.emptyList() : Arrays.asList(poStr.split(","));

        GameStateData state = GameStateData.builder()
                .roomId(roomId)
                .status((String) entries.getOrDefault("status", "WAITING"))
                .currentRound(Integer.parseInt((String) entries.getOrDefault("currentRound", "0")))
                .totalRounds(Integer.parseInt((String) entries.getOrDefault("totalRounds", "5")))
                .drawerId((String) entries.getOrDefault("drawerId", ""))
                .secretWord((String) entries.getOrDefault("secretWord", ""))
                .hint((String) entries.getOrDefault("hint", ""))
                .roundStartedAt(Long.parseLong((String) entries.getOrDefault("roundStartedAt", "0")))
                .roundEndsAt(Long.parseLong((String) entries.getOrDefault("roundEndsAt", "0")))
                .playerOrder(playerOrder)
                .scores(getScores(roomId))
                .build();

        return Optional.of(state);
    }

    public void setPlayerScore(String roomId, String playerId, int score) {
        String scoresKey = getScoresKey(roomId);
        redisTemplate.opsForHash().put(scoresKey, playerId, String.valueOf(score));
    }

    public boolean atomicSubmitGuess(String roomId, String playerId, int scoreAwarded, int drawerBonus, String drawerId) {
        String guessedKey = getGuessedKey(roomId);
        String scoresKey = getScoresKey(roomId);

        Long result = redisTemplate.execute(
                submitGuessScript,
                Arrays.asList(guessedKey, scoresKey),
                playerId,
                String.valueOf(scoreAwarded),
                String.valueOf(drawerBonus),
                drawerId != null ? drawerId : ""
        );

        return result != null && result == 1L;
    }

    public List<PlayerScoreData> getScores(String roomId) {
        String scoresKey = getScoresKey(roomId);
        String guessedKey = getGuessedKey(roomId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(scoresKey);
        Set<String> guessedPlayers = redisTemplate.opsForSet().members(guessedKey);
        if (guessedPlayers == null) {
            guessedPlayers = Collections.emptySet();
        }

        List<PlayerScoreData> list = new ArrayList<>();
        if (entries != null) {
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                String pId = (String) entry.getKey();
                int score = Integer.parseInt((String) entry.getValue());
                list.add(PlayerScoreData.builder()
                        .playerId(pId)
                        .score(score)
                        .hasGuessed(guessedPlayers.contains(pId))
                        .build());
            }
        }
        return list;
    }

    public boolean hasPlayerGuessed(String roomId, String playerId) {
        String guessedKey = getGuessedKey(roomId);
        Boolean isMember = redisTemplate.opsForSet().isMember(guessedKey, playerId);
        return Boolean.TRUE.equals(isMember);
    }

    public long getGuessedCount(String roomId) {
        String guessedKey = getGuessedKey(roomId);
        Long size = redisTemplate.opsForSet().size(guessedKey);
        return size != null ? size : 0;
    }

    public void clearGuessed(String roomId) {
        String guessedKey = getGuessedKey(roomId);
        redisTemplate.delete(guessedKey);
    }

    public void deleteGame(String roomId) {
        redisTemplate.delete(Arrays.asList(getKey(roomId), getScoresKey(roomId), getGuessedKey(roomId), getLockKey(roomId)));
    }
}
