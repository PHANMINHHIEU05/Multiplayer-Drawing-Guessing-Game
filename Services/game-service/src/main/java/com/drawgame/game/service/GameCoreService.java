package com.drawgame.game.service;

import com.drawgame.game.entity.GamePlayerResultEntity;
import com.drawgame.game.entity.GameResultEntity;
import com.drawgame.game.entity.WordEntity;
import com.drawgame.game.grpc.client.RoomGrpcClient;
import com.drawgame.game.model.GameStateData;
import com.drawgame.game.model.PlayerScoreData;
import com.drawgame.game.repository.GameResultRepository;
import com.drawgame.game.repository.RedisGameRepository;
import com.drawgame.game.repository.WordRepository;
import com.drawgame.game.service.component.AnswerEvaluator;
import com.drawgame.game.service.component.GameControlEventPublisher;
import com.drawgame.game.service.component.HintGenerator;
import com.drawgame.game.service.component.RoundScheduler;
import com.drawgame.game.service.component.ScoreCalculator;
import com.drawgame.room.grpc.generated.PlayerMessage;
import com.drawgame.room.grpc.generated.RoomResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameCoreService {

    private final RedisGameRepository redisGameRepository;
    private final WordRepository wordRepository;
    private final GameResultRepository gameResultRepository;
    private final RoomGrpcClient roomGrpcClient;
    private final HintGenerator hintGenerator;
    private final ScoreCalculator scoreCalculator;
    private final AnswerEvaluator answerEvaluator;
    private final RoundScheduler roundScheduler;
    private final GameControlEventPublisher controlEventPublisher;

    private static final int ROUND_DURATION_SECONDS = 60;
    private static final int INTERMISSION_SECONDS = 5;
    private static final int DEFAULT_TOTAL_ROUNDS = 5;

    public GameStateData startGame(String roomId, String requesterPlayerId) {
        log.info("Starting game for room {} requested by player {}", roomId, requesterPlayerId);

        // 1. Concurrency lock to prevent duplicate game creation
        if (!redisGameRepository.tryLockGameStart(roomId)) {
            log.warn("Game start requested concurrently for room {}", roomId);
            return redisGameRepository.findState(roomId)
                    .orElseThrow(() -> new IllegalStateException("Game start in progress for room " + roomId));
        }

        try {
            // 2. Call Room Service BeginGame
            RoomResponse roomResponse;
            try {
                roomResponse = roomGrpcClient.beginGame(roomId, requesterPlayerId);
            } catch (Exception e) {
                log.error("Failed to begin game in Room Service for room {}", roomId, e);
                redisGameRepository.releaseGameStartLock(roomId);
                throw new IllegalStateException("Room Service failed to begin game: " + e.getMessage());
            }

            List<PlayerMessage> players = roomResponse.getPlayersList();
            if (players.isEmpty()) {
                redisGameRepository.releaseGameStartLock(roomId);
                throw new IllegalStateException("Cannot start game in empty room");
            }

            // Preserving deterministic join order
            List<String> playerOrder = new ArrayList<>();
            Map<String, String> usernames = new HashMap<>();
            for (PlayerMessage p : players) {
                playerOrder.add(p.getPlayerId());
                usernames.put(p.getPlayerId(), p.getUsername());
            }
            // TV7: persist display usernames so GET_GAME_STATE scores carry them
            redisGameRepository.setUsernames(roomId, usernames);

            // Round 1 drawer
            String drawerId = playerOrder.get(0);

            WordEntity wordEntity = wordRepository.findRandomWord()
                    .orElse(WordEntity.builder().word("con meo").build());
            String secretWord = wordEntity.getWord();

            String hint = hintGenerator.generateInitialHint(secretWord);
            long now = System.currentTimeMillis();
            // TV5 regression fix: honor the room's configured round duration (was hardcoded 60s)
            int roundDurationSeconds = roomResponse.getRoundDuration() > 0
                    ? roomResponse.getRoundDuration()
                    : ROUND_DURATION_SECONDS;
            long endsAt = now + (roundDurationSeconds * 1000L);

            for (String pId : playerOrder) {
                redisGameRepository.setPlayerScore(roomId, pId, 0);
            }
            redisGameRepository.clearGuessed(roomId);

            // TV5 regression fix: honor the room's configured round count (was mistakenly using maxPlayers)
            int totalRounds = roomResponse.getRoundCount() > 0
                    ? roomResponse.getRoundCount()
                    : DEFAULT_TOTAL_ROUNDS;

            GameStateData state = GameStateData.builder()
                    .roomId(roomId)
                    .status("PLAYING")
                    .currentRound(1)
                    .totalRounds(totalRounds)
                    .drawerId(drawerId)
                    .secretWord(secretWord)
                    .hint(hint)
                    .roundStartedAt(now)
                    .roundEndsAt(endsAt)
                    .roundDurationSeconds(roundDurationSeconds)
                    .playerOrder(playerOrder)
                    .scores(redisGameRepository.getScores(roomId))
                    .build();

            redisGameRepository.saveState(state);

            // Schedule server authoritative round timer
            roundScheduler.scheduleRoundEnd(roomId, roundDurationSeconds * 1000L, () -> endRound(roomId));

            log.info("GAME_STARTED: roomId={}, round=1, drawerId={}", roomId, drawerId);
            return state;
        } finally {
            redisGameRepository.releaseGameStartLock(roomId);
        }
    }

    public GameStateData getGameState(String roomId, String viewerPlayerId) {
        Optional<GameStateData> stateOpt = redisGameRepository.findState(roomId);
        if (stateOpt.isEmpty()) {
            // Check if game has finished and was persisted in DB
            List<GameResultEntity> results = gameResultRepository.findByRoomId(roomId);
            if (!results.isEmpty()) {
                GameResultEntity res = results.get(results.size() - 1);
                List<PlayerScoreData> scores = res.getPlayerResults() != null
                        ? res.getPlayerResults().stream()
                                .map(p -> PlayerScoreData.builder()
                                        .playerId(p.getPlayerId())
                                        .username(p.getUsername())
                                        .score(p.getFinalScore())
                                        .build())
                                .collect(java.util.stream.Collectors.toList())
                        : Collections.emptyList();
                return GameStateData.builder()
                        .roomId(roomId)
                        .status("FINISHED")
                        .totalRounds(res.getTotalRounds())
                        .currentRound(res.getTotalRounds())
                        .scores(scores)
                        .secretWord("")
                        .hint("")
                        .build();
            }
            throw new IllegalArgumentException("Game not found for room " + roomId);
        }

        GameStateData state = stateOpt.get();

        // Secret word protection: Only drawer sees secret word
        if (viewerPlayerId == null || !viewerPlayerId.equals(state.getDrawerId())) {
            state.setSecretWord("");
        }
        return state;
    }

    public GuessResult submitGuess(String roomId, String playerId, String guess) {
        GameStateData state = redisGameRepository.findState(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found for room " + roomId));

        if (!"PLAYING".equalsIgnoreCase(state.getStatus()) && !"ROUND_IN_PROGRESS".equalsIgnoreCase(state.getStatus())) {
            return new GuessResult("ROUND_NOT_ACTIVE", 0);
        }

        // Time check: reject guess after deadline
        long now = System.currentTimeMillis();
        if (now >= state.getRoundEndsAt()) {
            return new GuessResult("TIME_EXPIRED", 0);
        }

        if (playerId.equals(state.getDrawerId())) {
            return new GuessResult("DRAWER_CANNOT_GUESS", 0);
        }

        if (redisGameRepository.hasPlayerGuessed(roomId, playerId)) {
            return new GuessResult("ALREADY_GUESSED", 0);
        }

        List<String> aliases = wordRepository.findAliasesByCanonicalWord(state.getSecretWord());
        AnswerEvaluator.Result evalResult = answerEvaluator.evaluate(guess, state.getSecretWord(), aliases);

        if (evalResult == AnswerEvaluator.Result.CORRECT) {
            long remainingSeconds = Math.max(0, (state.getRoundEndsAt() - now) / 1000);
            int guessOrder = (int) redisGameRepository.getGuessedCount(roomId) + 1;

            int roundDurationSeconds = state.getRoundDurationSeconds() > 0
                    ? state.getRoundDurationSeconds()
                    : ROUND_DURATION_SECONDS;
            int awardedScore = scoreCalculator.calculateGuesserScore(remainingSeconds, roundDurationSeconds, guessOrder);
            int drawerBonus = scoreCalculator.calculateDrawerBonus(state.getPlayerOrder().size());

            // Atomic Lua script execution to prevent duplicate scoring
            boolean success = redisGameRepository.atomicSubmitGuess(roomId, playerId, awardedScore, drawerBonus, state.getDrawerId());
            if (!success) {
                return new GuessResult("ALREADY_GUESSED", 0);
            }

            log.info("GUESS_CORRECT: roomId={}, playerId={}, scoreAwarded={}", roomId, playerId, awardedScore);

            // Check if all non-drawer players have guessed correctly
            long guessedCount = redisGameRepository.getGuessedCount(roomId);
            int totalGuessers = Math.max(1, state.getPlayerOrder().size() - 1);
            if (guessedCount >= totalGuessers) {
                log.info("All eligible players guessed correctly for room {}. Ending round early.", roomId);
                roundScheduler.scheduleRoundEnd(roomId, 500, () -> endRound(roomId));
            }

            return new GuessResult("CORRECT", awardedScore);
        } else if (evalResult == AnswerEvaluator.Result.CLOSE) {
            return new GuessResult("CLOSE", 0);
        } else {
            return new GuessResult("WRONG", 0);
        }
    }

    public synchronized void endRound(String roomId) {
        Optional<GameStateData> optionalState = redisGameRepository.findState(roomId);
        if (optionalState.isEmpty()) {
            return;
        }

        GameStateData state = optionalState.get();
        if ("ROUND_ENDED".equals(state.getStatus()) || "FINISHED".equals(state.getStatus())) {
            return; // Idempotent guard
        }

        log.info("ROUND_ENDED: roomId={}, round={}", roomId, state.getCurrentRound());
        roundScheduler.cancelScheduledTask(roomId);

        state.setStatus("ROUND_ENDED");
        redisGameRepository.saveState(state);

        // TV6: cross-Gateway control fanout — every Gateway's clients AND drawing
        // authorization caches learn the round ended immediately.
        controlEventPublisher.publishRoundEnded(roomId, state.getCurrentRound(), state.getSecretWord());

        // Schedule next round after intermission
        roundScheduler.scheduleRoundEnd(roomId, INTERMISSION_SECONDS * 1000L, () -> nextRound(roomId));
    }

    public synchronized void nextRound(String roomId) {
        Optional<GameStateData> optionalState = redisGameRepository.findState(roomId);
        if (optionalState.isEmpty()) {
            return;
        }

        GameStateData state = optionalState.get();
        int nextRoundNumber = state.getCurrentRound() + 1;

        if (nextRoundNumber > state.getTotalRounds()) {
            finishGame(roomId);
            return;
        }

        List<String> playerOrder = state.getPlayerOrder();
        if (playerOrder.isEmpty()) {
            finishGame(roomId);
            return;
        }

        // Deterministic Drawer Rotation
        String nextDrawerId = playerOrder.get((nextRoundNumber - 1) % playerOrder.size());

        WordEntity wordEntity = wordRepository.findRandomWord()
                .orElse(WordEntity.builder().word("con meo").build());
        String secretWord = wordEntity.getWord();
        String hint = hintGenerator.generateInitialHint(secretWord);

        long now = System.currentTimeMillis();
        int roundDurationSeconds = state.getRoundDurationSeconds() > 0
                ? state.getRoundDurationSeconds()
                : ROUND_DURATION_SECONDS;
        long endsAt = now + (roundDurationSeconds * 1000L);

        redisGameRepository.clearGuessed(roomId);

        state.setStatus("PLAYING");
        state.setCurrentRound(nextRoundNumber);
        state.setDrawerId(nextDrawerId);
        state.setSecretWord(secretWord);
        state.setHint(hint);
        state.setRoundStartedAt(now);
        state.setRoundEndsAt(endsAt);

        redisGameRepository.saveState(state);

        roundScheduler.scheduleRoundEnd(roomId, roundDurationSeconds * 1000L, () -> endRound(roomId));
        // TV6: cross-Gateway control fanout — clients clear canvas, Gateways refresh
        // DrawingRoomStateCache with the new drawer/round immediately.
        controlEventPublisher.publishRoundStarted(roomId, nextRoundNumber, nextDrawerId);
        log.info("ROUND_STARTED: roomId={}, round={}, drawerId={}", roomId, nextRoundNumber, nextDrawerId);
    }

    @Transactional
    public synchronized GameStateData finishGame(String roomId) {
        log.info("FINISHING_GAME: roomId={}", roomId);
        Optional<GameStateData> optionalState = redisGameRepository.findState(roomId);
        if (optionalState.isEmpty()) {
            log.warn("Game already finished or state absent for room {}", roomId);
            return GameStateData.builder().roomId(roomId).status("FINISHED").build();
        }

        GameStateData state = optionalState.get();
        if ("FINISHED".equals(state.getStatus())) {
            return state;
        }

        roundScheduler.cancelScheduledTask(roomId);

        List<PlayerScoreData> scores = redisGameRepository.getScores(roomId);
        scores.sort(Comparator.comparingInt(PlayerScoreData::getScore).reversed());

        PlayerScoreData winner = scores.isEmpty() ? null : scores.get(0);

        // Check if game result already persisted (idempotency)
        if (gameResultRepository.findByRoomId(roomId).isEmpty()) {
            GameResultEntity gameResult = GameResultEntity.builder()
                    .roomId(roomId)
                    .winnerId(winner != null ? winner.getPlayerId() : null)
                    .winnerUsername(winner != null ? winner.getUsername() : null)
                    .totalRounds(state.getCurrentRound())
                    .finishedAt(LocalDateTime.now())
                    .build();

            List<GamePlayerResultEntity> playerResults = new ArrayList<>();
            for (int i = 0; i < scores.size(); i++) {
                PlayerScoreData p = scores.get(i);
                playerResults.add(GamePlayerResultEntity.builder()
                        .gameResult(gameResult)
                        .playerId(p.getPlayerId())
                        .username(p.getUsername() != null ? p.getUsername() : p.getPlayerId())
                        .finalScore(p.getScore())
                        .rank(i + 1)
                        .build());
            }
            gameResult.setPlayerResults(playerResults);
            gameResultRepository.save(gameResult);
            log.info("GAME_RESULT_PERSISTED: roomId={}, winnerId={}", roomId, winner != null ? winner.getPlayerId() : "NONE");
        }

        // Room post-game lifecycle update: Reset Room status back to WAITING so players can Rematch
        try {
            roomGrpcClient.finishGame(roomId);
        } catch (Exception e) {
            log.error("Failed to notify Room Service finishGame for room {}", roomId, e);
        }

        state.setStatus("FINISHED");
        state.setScores(scores);

        // Delete Redis ephemeral game state ONLY after DB persistence succeeds
        redisGameRepository.deleteGame(roomId);
        // TV6: cross-Gateway control fanout — clients on every Gateway enter finished state.
        controlEventPublisher.publishGameFinished(roomId, scores);
        log.info("GAME_FINISHED_SUCCESSFULLY: roomId={}", roomId);

        return state;
    }

    public record GuessResult(String status, int scoreAwarded) {}
}
