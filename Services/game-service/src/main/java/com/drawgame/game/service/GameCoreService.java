package com.drawgame.game.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.drawgame.game.entity.GamePlayerResultEntity;
import com.drawgame.game.entity.GameResultEntity;
import com.drawgame.game.grpc.client.RoomGrpcClient;
import com.drawgame.game.model.GameStateData;
import com.drawgame.game.model.MatchAwardData;
import com.drawgame.game.model.PlayerScoreData;
import com.drawgame.game.model.RoundRecapData;
import com.drawgame.game.model.RoundScoreDeltaData;
import com.drawgame.game.model.WordChoiceData;
import com.drawgame.game.repository.GameResultRepository;
import com.drawgame.game.repository.RedisGameRepository;
import com.drawgame.game.repository.WordRepository;
import com.drawgame.game.service.component.AnswerEvaluator;
import com.drawgame.game.service.component.GameControlEventPublisher;
import com.drawgame.game.service.component.HintGenerator;
import com.drawgame.game.service.component.MatchAwardCalculator;
import com.drawgame.game.service.component.RoundScheduler;
import com.drawgame.game.service.component.ScoreCalculator;
import com.drawgame.game.service.component.WordChoiceGenerator;
import com.drawgame.room.grpc.generated.PlayerMessage;
import com.drawgame.room.grpc.generated.RoomResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameCoreService {
    private static final int DEFAULT_ROUND_DURATION_SECONDS = 60;
    private static final int DEFAULT_TOTAL_ROUNDS = 5;
    private static final int SELECTION_SECONDS = 10;
    private static final int COUNTDOWN_SECONDS = 3;
    private static final int RECAP_SECONDS = 3;
    private static final List<String> DEFAULT_CATEGORIES =
            List.of("ANIMALS", "FOOD", "OBJECTS", "PLACES", "NATURE", "TECHNOLOGY");
    private static final int[] HINT_PERCENTAGES = {35, 60, 80};

    private final RedisGameRepository redisGameRepository;
    private final WordRepository wordRepository;
    private final GameResultRepository gameResultRepository;
    private final RoomGrpcClient roomGrpcClient;
    private final HintGenerator hintGenerator;
    private final ScoreCalculator scoreCalculator;
    private final AnswerEvaluator answerEvaluator;
    private final RoundScheduler roundScheduler;
    private final GameControlEventPublisher controlEventPublisher;
    private final WordChoiceGenerator wordChoiceGenerator;
    private final MatchAwardCalculator matchAwardCalculator;
    private final ObjectMapper objectMapper;

    public GameStateData startGame(String roomId, String requesterPlayerId) {
        log.info("Starting game: roomId={} requesterId={}", roomId, requesterPlayerId);
        if (!redisGameRepository.tryLockGameStart(roomId)) {
            return redisGameRepository.findState(roomId)
                    .orElseThrow(() -> new IllegalStateException("Game start already in progress for room " + roomId));
        }

        try {
            RoomResponse room = roomGrpcClient.beginGame(roomId, requesterPlayerId);
            List<PlayerMessage> players = room.getPlayersList();
            if (players.isEmpty()) throw new IllegalStateException("Cannot start game in an empty room");

            redisGameRepository.deleteGame(roomId);
            List<String> playerOrder = new ArrayList<>();
            Map<String, String> usernames = new HashMap<>();
            for (PlayerMessage player : players) {
                playerOrder.add(player.getPlayerId());
                usernames.put(player.getPlayerId(), player.getUsername());
                redisGameRepository.setPlayerScore(roomId, player.getPlayerId(), 0);
            }
            redisGameRepository.setUsernames(roomId, usernames);

            int roundDuration = room.getRoundDuration() > 0
                    ? room.getRoundDuration() : DEFAULT_ROUND_DURATION_SECONDS;
            int totalRounds = room.getRoundCount() > 0 ? room.getRoundCount() : DEFAULT_TOTAL_ROUNDS;
            List<String> categories = room.getSelectedCategoriesList().isEmpty()
                    ? DEFAULT_CATEGORIES : room.getSelectedCategoriesList();
            long now = System.currentTimeMillis();
            String gameId = UUID.randomUUID().toString();
            GameStateData state = GameStateData.builder()
                    .roomId(roomId)
                    .gameId(gameId)
                    .status("PLAYING")
                    .roundPhase("WORD_SELECTION")
                    .currentRound(1)
                    .totalRounds(totalRounds)
                    .drawerId(playerOrder.get(0))
                    .secretWord("")
                    .hint("")
                    .phaseStartedAt(now)
                    .phaseEndsAt(now + SELECTION_SECONDS * 1000L)
                    .roundDurationSeconds(roundDuration)
                    .playerOrder(playerOrder)
                    .selectedCategories(categories)
                    .wordChoices(wordChoiceGenerator.generate(categories, gameId + ":1"))
                    .roundStartScores(zeroScores(playerOrder))
                    .scores(redisGameRepository.getScores(roomId))
                    .build();
            redisGameRepository.clearRoundGuesses(roomId, 1);
            redisGameRepository.saveState(state);
            scheduleSelectionTimeout(state);
            controlEventPublisher.publishWordSelectionStarted(roomId, state);
            log.info("GAME_STARTED roomId={} gameId={} round=1 phase=WORD_SELECTION drawerId={}",
                    roomId, gameId, state.getDrawerId());
            return state;
        } finally {
            redisGameRepository.releaseGameStartLock(roomId);
        }
    }

    /** Rebuild deadlines after Game Service restart from the shared authoritative Redis state. */
    @PostConstruct
    public void restorePendingTimers() {
        try {
            for (String roomId : redisGameRepository.findActiveRoomIds()) {
                redisGameRepository.findState(roomId).ifPresent(this::restoreTimersForState);
            }
        } catch (Exception e) {
            log.warn("Could not restore persisted game timers during startup: {}", e.getMessage());
        }
    }

    public GameStateData getGameState(String roomId, String viewerPlayerId) {
        Optional<GameStateData> active = redisGameRepository.findState(roomId);
        if (active.isEmpty()) return finishedStateFromHistory(roomId);

        GameStateData state = active.get();
        boolean isDrawer = viewerPlayerId != null && viewerPlayerId.equals(state.getDrawerId());
        String phase = state.getRoundPhase();
        if ("WORD_SELECTION".equals(phase)) {
            state.setSecretWord("");
            state.setWordChoices(isDrawer ? state.getWordChoices() : List.of());
        } else if ("COUNTDOWN".equals(phase) || "DRAWING".equals(phase)) {
            state.setSecretWord(isDrawer ? state.getSecretWord() : "");
            state.setWordChoices(List.of());
        } else if ("ROUND_RECAP".equals(phase)) {
            state.setSecretWord(state.getRoundRecap() == null ? state.getSecretWord() : state.getRoundRecap().answer());
            state.setWordChoices(List.of());
        } else {
            state.setSecretWord("");
            state.setWordChoices(List.of());
        }
        return state;
    }

    private GameStateData finishedStateFromHistory(String roomId) {
        GameResultEntity result = gameResultRepository.findTopByRoomIdOrderByIdDesc(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found for room " + roomId));
        List<PlayerScoreData> scores = result.getPlayerResults() == null ? List.of()
                : result.getPlayerResults().stream().map(player -> PlayerScoreData.builder()
                        .playerId(player.getPlayerId()).username(player.getUsername())
                        .score(player.getFinalScore()).hasGuessed(false).build()).toList();
        return GameStateData.builder().roomId(roomId).gameId(result.getGameId()).status("FINISHED")
                .roundPhase("").totalRounds(result.getTotalRounds()).currentRound(result.getTotalRounds())
                .scores(scores).roundResults(readJson(result.getRoundResultsJson(), new TypeReference<>() {}, List.of()))
                .awards(readJson(result.getAwardsJson(), new TypeReference<>() {}, List.of()))
                .secretWord("").hint("").build();
    }

    public synchronized GameStateData selectWord(String roomId, String playerId, String choiceId) {
        GameStateData state = requiredState(roomId);
        requireCurrentPhase(state, "WORD_SELECTION");
        if (!Objects.equals(state.getDrawerId(), playerId)) {
            throw new IllegalArgumentException("Only the current drawer may select a word");
        }
        boolean offered = state.getWordChoices().stream().anyMatch(choice -> choice.choiceId().equals(choiceId));
        if (!offered) throw new IllegalArgumentException("Choice is not available for this round");

        long now = System.currentTimeMillis();
        if (now >= state.getPhaseEndsAt()) {
            autoPickWord(roomId, state.getGameId(), state.getCurrentRound());
            throw new IllegalStateException("Word selection deadline has expired");
        }
        int result = redisGameRepository.atomicSelectWord(roomId, state.getGameId(), state.getCurrentRound(),
                choiceId, now, now + COUNTDOWN_SECONDS * 1000L, false);
        if (result != 1) throw new IllegalStateException("Word selection is no longer active");
        roundScheduler.cancel(roomId, taskName(state, "selection"));
        GameStateData countdown = requiredState(roomId);
        controlEventPublisher.publishCountdownStarted(roomId, countdown);
        scheduleCountdown(countdown);
        return getGameState(roomId, playerId);
    }

    public synchronized GuessResult submitGuess(String roomId, String playerId, String guess) {
        GameStateData state = redisGameRepository.findState(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found for room " + roomId));
        if (!"DRAWING".equals(state.getRoundPhase())) return new GuessResult("ROUND_NOT_ACTIVE", 0);
        long now = System.currentTimeMillis();
        if (now >= state.getRoundEndsAt()) return new GuessResult("TIME_EXPIRED", 0);
        if (Objects.equals(playerId, state.getDrawerId())) return new GuessResult("DRAWER_CANNOT_GUESS", 0);
        if (redisGameRepository.hasPlayerGuessed(roomId, playerId)) return new GuessResult("ALREADY_GUESSED", 0);

        List<String> aliases = wordRepository.findAliasesByCanonicalWord(state.getSecretWord());
        AnswerEvaluator.Result evaluation = answerEvaluator.evaluate(guess, state.getSecretWord(), aliases);
        if (evaluation == AnswerEvaluator.Result.CLOSE) return new GuessResult("CLOSE", 0);
        if (evaluation != AnswerEvaluator.Result.CORRECT) return new GuessResult("WRONG", 0);

        long remainingSeconds = Math.max(0, (state.getRoundEndsAt() - now) / 1000L);
        int guessOrder = (int) redisGameRepository.getGuessedCount(roomId) + 1;
        int awarded = scoreCalculator.calculateGuesserScore(remainingSeconds,
                state.getRoundDurationSeconds() > 0 ? state.getRoundDurationSeconds() : DEFAULT_ROUND_DURATION_SECONDS,
                guessOrder);
        int drawerBonus = scoreCalculator.calculateDrawerBonus(state.getPlayerOrder().size());
        int result = redisGameRepository.atomicSubmitGuess(roomId, state.getGameId(), state.getCurrentRound(),
                playerId, awarded, drawerBonus, state.getDrawerId(), now, Math.max(0, now - state.getRoundStartedAt()));
        if (result == 0) return new GuessResult("ALREADY_GUESSED", 0);
        if (result < 0) return new GuessResult("ROUND_NOT_ACTIVE", 0);

        log.info("GUESS_CORRECT roomId={} gameId={} round={} playerId={} scoreAwarded={}",
                roomId, state.getGameId(), state.getCurrentRound(), playerId, awarded);
        long guessCount = redisGameRepository.getGuessedCount(roomId);
        int eligibleGuessers = Math.max(0, state.getPlayerOrder().size() - 1);
        if (eligibleGuessers > 0 && guessCount >= eligibleGuessers) {
            roundScheduler.schedule(roomId, taskName(state, "draw-end"), 500,
                    () -> endRound(roomId, state.getGameId(), state.getCurrentRound()));
        }
        return new GuessResult("CORRECT", awarded);
    }

    private void scheduleSelectionTimeout(GameStateData state) {
        long delay = Math.max(0, state.getPhaseEndsAt() - System.currentTimeMillis());
        roundScheduler.schedule(state.getRoomId(), taskName(state, "selection"), delay,
                () -> autoPickWord(state.getRoomId(), state.getGameId(), state.getCurrentRound()));
    }

    private synchronized void autoPickWord(String roomId, String gameId, int round) {
        GameStateData state = redisGameRepository.findState(roomId).orElse(null);
        if (!matches(state, gameId, round, "WORD_SELECTION") || state.getWordChoices().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now < state.getPhaseEndsAt()) {
            scheduleSelectionTimeout(state);
            return;
        }
        List<WordChoiceData> choices = state.getWordChoices();
        WordChoiceData selected = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
        int result = redisGameRepository.atomicSelectWord(roomId, gameId, round, selected.choiceId(), now,
                now + COUNTDOWN_SECONDS * 1000L, true);
        if (result != 1) return;
        roundScheduler.cancel(roomId, taskName(state, "selection"));
        GameStateData countdown = requiredState(roomId);
        controlEventPublisher.publishCountdownStarted(roomId, countdown);
        scheduleCountdown(countdown);
        log.info("WORD_SELECTION_AUTO_PICK roomId={} gameId={} round={} drawerId={}",
                roomId, gameId, round, state.getDrawerId());
    }

    private void scheduleCountdown(GameStateData state) {
        long delay = Math.max(0, state.getPhaseEndsAt() - System.currentTimeMillis());
        roundScheduler.schedule(state.getRoomId(), taskName(state, "countdown"), delay,
                () -> beginDrawing(state.getRoomId(), state.getGameId(), state.getCurrentRound()));
    }

    private synchronized void beginDrawing(String roomId, String gameId, int round) {
        GameStateData state = redisGameRepository.findState(roomId).orElse(null);
        if (!matches(state, gameId, round, "COUNTDOWN")) return;
        long now = System.currentTimeMillis();
        if (now < state.getPhaseEndsAt()) {
            scheduleCountdown(state);
            return;
        }
        int durationSeconds = state.getRoundDurationSeconds() > 0
                ? state.getRoundDurationSeconds() : DEFAULT_ROUND_DURATION_SECONDS;
        long endsAt = now + durationSeconds * 1000L;
        String initialHint = hintGenerator.generateInitialHint(state.getSecretWord());
        List<Set<Integer>> revealSchedule = hintGenerator.createRevealSchedule(
                state.getSecretWord(), state.getGameId() + ":" + round);
        Map<String, Integer> scoreSnapshot = currentScoreMap(roomId, state.getPlayerOrder());
        int transitioned = redisGameRepository.beginDrawing(roomId, gameId, round, now, endsAt,
                durationSeconds, initialHint, revealSchedule, scoreSnapshot);
        if (transitioned == -1) {
            scheduleCountdown(state);
            return;
        }
        if (transitioned != 1) return;

        state.setRoundPhase("DRAWING");
        state.setPhaseStartedAt(now);
        state.setPhaseEndsAt(endsAt);
        state.setRoundStartedAt(now);
        state.setRoundEndsAt(endsAt);
        state.setHint(initialHint);
        state.setHintStage(0);
        state.setHintRevealSchedule(revealSchedule);
        state.setRoundStartScores(scoreSnapshot);
        state.setScores(redisGameRepository.getScores(roomId));
        controlEventPublisher.publishRoundStarted(roomId, gameId, round, state.getDrawerId(), now, endsAt);
        scheduleHintCallbacks(state);
        scheduleRoundEnd(state);
        log.info("ROUND_DRAWING_STARTED roomId={} gameId={} round={} drawerId={}", roomId, gameId, round, state.getDrawerId());
    }

    private void scheduleHintCallbacks(GameStateData state) {
        long duration = Math.max(1, state.getRoundEndsAt() - state.getRoundStartedAt());
        for (int index = Math.max(0, state.getHintStage()); index < HINT_PERCENTAGES.length; index++) {
            final int hintIndex = index;
            long revealAt = state.getRoundStartedAt() + duration * HINT_PERCENTAGES[index] / 100L;
            if (revealAt >= state.getRoundEndsAt()) continue;
            roundScheduler.schedule(state.getRoomId(), taskName(state, "hint-" + (index + 1)),
                    Math.max(0, revealAt - System.currentTimeMillis()),
                    () -> revealHint(state.getRoomId(), state.getGameId(), state.getCurrentRound(), hintIndex));
        }
    }

    private synchronized void revealHint(String roomId, String gameId, int round, int index) {
        GameStateData state = redisGameRepository.findState(roomId).orElse(null);
        if (!matches(state, gameId, round, "DRAWING") || index >= state.getHintRevealSchedule().size()) return;
        if (state.getHintStage() > index || System.currentTimeMillis() >= state.getRoundEndsAt()) return;
        long revealAt = state.getRoundStartedAt()
                + Math.max(1, state.getRoundEndsAt() - state.getRoundStartedAt()) * HINT_PERCENTAGES[index] / 100L;
        if (System.currentTimeMillis() < revealAt) {
            long delay = revealAt - System.currentTimeMillis();
            roundScheduler.schedule(roomId, taskName(state, "hint-" + (index + 1)), delay,
                    () -> revealHint(roomId, gameId, round, index));
            return;
        }
        state.setHint(hintGenerator.renderHint(state.getSecretWord(), state.getHintRevealSchedule().get(index)));
        state.setHintStage(index + 1);
        state.setScores(redisGameRepository.getScores(roomId));
        redisGameRepository.saveState(state);
        controlEventPublisher.publishHintUpdated(roomId, gameId, round, state.getHintStage(),
                state.getRoundEndsAt(), state.getHint());
    }

    private void scheduleRoundEnd(GameStateData state) {
        long delay = Math.max(0, state.getRoundEndsAt() - System.currentTimeMillis());
        roundScheduler.schedule(state.getRoomId(), taskName(state, "draw-end"), delay,
                () -> endRound(state.getRoomId(), state.getGameId(), state.getCurrentRound()));
    }

    private synchronized void endRound(String roomId, String gameId, int round) {
        GameStateData state = redisGameRepository.findState(roomId).orElse(null);
        if (!matches(state, gameId, round, "DRAWING")) return;
        int eligibleGuessers = Math.max(0, state.getPlayerOrder().size() - 1);
        boolean allGuessed = eligibleGuessers > 0 && redisGameRepository.getGuessedCount(roomId) >= eligibleGuessers;
        long now = System.currentTimeMillis();
        if (!allGuessed && now < state.getRoundEndsAt()) {
            scheduleRoundEnd(state);
            return;
        }

        cancelDrawingTasks(roomId, state);
        RoundRecapData recap = buildRoundRecap(roomId, state);
        long recapEndsAt = now + RECAP_SECONDS * 1000L;
        if (!redisGameRepository.beginRecap(roomId, gameId, round, now, recapEndsAt, recap)) return;
        state.setRoundPhase("ROUND_RECAP");
        state.setPhaseStartedAt(now);
        state.setPhaseEndsAt(recapEndsAt);
        state.setRoundRecap(recap);
        state.setScores(redisGameRepository.getScores(roomId));
        List<RoundRecapData> roundResults = new ArrayList<>(state.getRoundResults());
        if (roundResults.stream().noneMatch(existing -> existing.roundNumber() == round)) roundResults.add(recap);
        state.setRoundResults(roundResults);
        redisGameRepository.saveState(state);
        controlEventPublisher.publishRoundRecapStarted(roomId, state, recap);
        scheduleRecapAdvance(state);
        log.info("ROUND_RECAP_STARTED roomId={} gameId={} round={} correctCount={}",
                roomId, gameId, round, recap.correctPlayerIds().size());
    }

    private RoundRecapData buildRoundRecap(String roomId, GameStateData state) {
        List<PlayerScoreData> scores = redisGameRepository.getScores(roomId);
        Map<String, PlayerScoreData> byId = new HashMap<>();
        scores.forEach(score -> byId.put(score.getPlayerId(), score));
        List<RoundScoreDeltaData> deltas = new ArrayList<>();
        int drawerDelta = 0;
        for (String playerId : state.getPlayerOrder()) {
            PlayerScoreData score = byId.get(playerId);
            if (score == null) continue;
            int delta = score.getScore() - state.getRoundStartScores().getOrDefault(playerId, 0);
            deltas.add(new RoundScoreDeltaData(playerId,
                    score.getUsername() == null ? playerId : score.getUsername(), delta, score.getScore()));
            if (playerId.equals(state.getDrawerId())) drawerDelta = delta;
        }
        Map<String, Long> timings = redisGameRepository.getRoundGuessTimings(roomId, state.getCurrentRound());
        String fastestId = "";
        long fastestMillis = -1;
        for (String playerId : state.getPlayerOrder()) {
            Long elapsed = timings.get(playerId);
            if (elapsed != null && elapsed >= 0 && (fastestMillis < 0 || elapsed < fastestMillis)) {
                fastestId = playerId;
                fastestMillis = elapsed;
            }
        }
        PlayerScoreData fastestPlayer = byId.get(fastestId);
        List<String> correctPlayers = state.getPlayerOrder().stream().filter(timings::containsKey).toList();
        return new RoundRecapData(state.getCurrentRound(), state.getDrawerId(), state.getSecretWord(), deltas,
                drawerDelta, fastestId, fastestPlayer == null ? fastestId : fastestPlayer.getUsername(),
                fastestMillis, correctPlayers, timings);
    }

    private void cancelDrawingTasks(String roomId, GameStateData state) {
        roundScheduler.cancel(roomId, taskName(state, "draw-end"));
        for (int hint = 1; hint <= HINT_PERCENTAGES.length; hint++) {
            roundScheduler.cancel(roomId, taskName(state, "hint-" + hint));
        }
    }

    private void scheduleRecapAdvance(GameStateData state) {
        roundScheduler.schedule(state.getRoomId(), taskName(state, "recap"),
                Math.max(0, state.getPhaseEndsAt() - System.currentTimeMillis()),
                () -> advanceFromRecap(state.getRoomId(), state.getGameId(), state.getCurrentRound()));
    }

    private synchronized void advanceFromRecap(String roomId, String gameId, int round) {
        GameStateData state = redisGameRepository.findState(roomId).orElse(null);
        if (!matches(state, gameId, round, "ROUND_RECAP")) return;
        long now = System.currentTimeMillis();
        if (now < state.getPhaseEndsAt()) {
            scheduleRecapAdvance(state);
            return;
        }
        if (round >= state.getTotalRounds() || state.getPlayerOrder().isEmpty()) {
            finishGame(roomId);
            return;
        }
        int nextRound = round + 1;
        String nextDrawer = state.getPlayerOrder().get((nextRound - 1) % state.getPlayerOrder().size());
        prepareWordSelection(state, nextRound, nextDrawer);
    }

    private void prepareWordSelection(GameStateData state, int round, String drawerId) {
        long now = System.currentTimeMillis();
        List<WordChoiceData> choices = wordChoiceGenerator.generate(state.getSelectedCategories(),
                state.getGameId() + ":" + round);
        redisGameRepository.clearRoundGuesses(state.getRoomId(), round);
        state.setCurrentRound(round);
        state.setDrawerId(drawerId);
        state.setRoundPhase("WORD_SELECTION");
        state.setStatus("PLAYING");
        state.setPhaseStartedAt(now);
        state.setPhaseEndsAt(now + SELECTION_SECONDS * 1000L);
        state.setRoundStartedAt(0);
        state.setRoundEndsAt(0);
        state.setSecretWord("");
        state.setHint("");
        state.setHintStage(0);
        state.setHintRevealSchedule(List.of());
        state.setRoundStartScores(currentScoreMap(state.getRoomId(), state.getPlayerOrder()));
        state.setRoundRecap(null);
        state.setWordChoices(choices);
        state.setScores(redisGameRepository.getScores(state.getRoomId()));
        redisGameRepository.saveState(state);
        scheduleSelectionTimeout(state);
        controlEventPublisher.publishWordSelectionStarted(state.getRoomId(), state);
        log.info("WORD_SELECTION_STARTED roomId={} gameId={} round={} drawerId={}",
                state.getRoomId(), state.getGameId(), round, drawerId);
    }

    @Transactional
    public synchronized GameStateData finishGame(String roomId) {
        GameStateData state = redisGameRepository.findState(roomId).orElse(null);
        if (state == null) return finishedStateFromHistory(roomId);
        if ("FINISHED".equals(state.getStatus())) return state;
        roundScheduler.cancelAll(roomId);

        List<PlayerScoreData> scores = redisGameRepository.getScores(roomId);
        scores.sort(scoreComparator(state.getPlayerOrder()));
        List<MatchAwardData> awards = matchAwardCalculator.calculate(scores, state.getRoundResults(), state.getPlayerOrder());
        List<RoundRecapData> roundResults = state.getRoundResults();

        if (gameResultRepository.findByGameId(state.getGameId()).isEmpty()) {
            GameResultEntity result = GameResultEntity.builder()
                    .gameId(state.getGameId())
                    .roomId(roomId)
                    .winnerId(scores.isEmpty() ? null : scores.get(0).getPlayerId())
                    .winnerUsername(scores.isEmpty() ? null : scores.get(0).getUsername())
                    .totalRounds(state.getCurrentRound())
                    .finishedAt(LocalDateTime.now())
                    .roundResultsJson(writeJson(roundResults))
                    .awardsJson(writeJson(awards))
                    .build();
            List<GamePlayerResultEntity> playerResults = new ArrayList<>();
            for (int index = 0; index < scores.size(); index++) {
                PlayerScoreData score = scores.get(index);
                playerResults.add(GamePlayerResultEntity.builder().gameResult(result)
                        .playerId(score.getPlayerId())
                        .username(score.getUsername() == null ? score.getPlayerId() : score.getUsername())
                        .finalScore(score.getScore()).rank(index + 1).build());
            }
            result.setPlayerResults(playerResults);
            gameResultRepository.save(result);
            log.info("GAME_RESULT_PERSISTED roomId={} gameId={} winnerId={}", roomId, state.getGameId(),
                    scores.isEmpty() ? "NONE" : scores.get(0).getPlayerId());
        }

        try {
            roomGrpcClient.finishGame(roomId);
        } catch (Exception e) {
            log.error("Failed to notify Room Service that game finished: roomId={}", roomId, e);
        }
        state.setStatus("FINISHED");
        state.setRoundPhase("");
        state.setScores(scores);
        state.setAwards(awards);
        redisGameRepository.deleteGame(roomId);
        controlEventPublisher.publishGameFinished(roomId, state.getGameId(), scores, awards);
        log.info("GAME_FINISHED roomId={} gameId={} rounds={} awardCount={}",
                roomId, state.getGameId(), state.getCurrentRound(), awards.size());
        return state;
    }

    private void restoreTimersForState(GameStateData state) {
        if (!"PLAYING".equals(state.getStatus())) return;
        switch (state.getRoundPhase()) {
            case "WORD_SELECTION" -> scheduleSelectionTimeout(state);
            case "COUNTDOWN" -> scheduleCountdown(state);
            case "DRAWING" -> {
                scheduleHintCallbacks(state);
                scheduleRoundEnd(state);
            }
            case "ROUND_RECAP" -> scheduleRecapAdvance(state);
            default -> log.warn("Ignoring unknown persisted round phase: roomId={} phase={}",
                    state.getRoomId(), state.getRoundPhase());
        }
    }

    private GameStateData requiredState(String roomId) {
        return redisGameRepository.findState(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Game not found for room " + roomId));
    }

    private void requireCurrentPhase(GameStateData state, String expected) {
        if (!"PLAYING".equals(state.getStatus()) || !expected.equals(state.getRoundPhase())) {
            throw new IllegalStateException("Game phase is not " + expected);
        }
    }

    private boolean matches(GameStateData state, String gameId, int round, String phase) {
        return state != null && Objects.equals(state.getGameId(), gameId)
                && state.getCurrentRound() == round && phase.equals(state.getRoundPhase());
    }

    private Map<String, Integer> zeroScores(List<String> playerOrder) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        playerOrder.forEach(player -> scores.put(player, 0));
        return scores;
    }

    private Map<String, Integer> currentScoreMap(String roomId, List<String> playerOrder) {
        Map<String, Integer> result = new LinkedHashMap<>();
        redisGameRepository.getScores(roomId).forEach(score -> result.put(score.getPlayerId(), score.getScore()));
        for (String playerId : playerOrder) result.putIfAbsent(playerId, 0);
        return result;
    }

    private Comparator<PlayerScoreData> scoreComparator(List<String> playerOrder) {
        Map<String, Integer> order = new HashMap<>();
        for (int index = 0; index < playerOrder.size(); index++) order.put(playerOrder.get(index), index);
        return Comparator.comparingInt(PlayerScoreData::getScore).reversed()
                .thenComparingInt(score -> order.getOrDefault(score.getPlayerId(), Integer.MAX_VALUE))
                .thenComparing(PlayerScoreData::getPlayerId);
    }

    private String taskName(GameStateData state, String suffix) {
        return state.getGameId() + ":" + state.getCurrentRound() + ":" + suffix;
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Could not persist match result", e); }
    }

    private <T> T readJson(String value, TypeReference<T> type, T fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return objectMapper.readValue(value, type); }
        catch (Exception e) {
            log.warn("Stored match statistics are invalid; returning no unsupported award data");
            return fallback;
        }
    }

    public record GuessResult(String status, int scoreAwarded) {}
}
