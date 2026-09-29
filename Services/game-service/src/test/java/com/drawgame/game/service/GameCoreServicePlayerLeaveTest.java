package com.drawgame.game.service;

import com.drawgame.game.entity.GameResultEntity;
import com.drawgame.game.grpc.client.RoomGrpcClient;
import com.drawgame.game.model.GameStateData;
import com.drawgame.game.model.MatchAwardData;
import com.drawgame.game.model.PlayerScoreData;
import com.drawgame.game.model.WordChoiceData;
import com.drawgame.game.repository.GameResultRepository;
import com.drawgame.game.repository.RedisGameRepository;
import com.drawgame.game.repository.WordRepository;
import com.drawgame.game.service.component.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameCoreServicePlayerLeaveTest {
    private final RedisGameRepository redis = mock(RedisGameRepository.class);
    private final WordRepository words = mock(WordRepository.class);
    private final GameResultRepository results = mock(GameResultRepository.class);
    private final RoomGrpcClient roomClient = mock(RoomGrpcClient.class);
    private final RoundScheduler scheduler = mock(RoundScheduler.class);
    private final GameControlEventPublisher events = mock(GameControlEventPublisher.class);
    private final WordChoiceGenerator choices = mock(WordChoiceGenerator.class);
    private final MatchAwardCalculator awards = mock(MatchAwardCalculator.class);
    private final GameCoreService service = new GameCoreService(redis, words, results, roomClient,
            new HintGenerator(), new ScoreCalculator(), new AnswerEvaluator(), scheduler, events,
            choices, awards, new ObjectMapper());

    @BeforeEach
    void setUp() {
        when(choices.generate(anyList(), anyString()))
                .thenReturn(List.of(new WordChoiceData("choice", "con mèo")));
        when(awards.calculate(anyList(), anyList(), anyList())).thenReturn(List.<MatchAwardData>of());
    }

    @Test
    void futureTurnsSkipThePlayerWhoLeftAndPreserveRotation() {
        GameStateData state = state(2, 5, "B", "ROUND_RECAP", List.of("A", "B", "C", "D"));
        when(redis.findState("room")).thenReturn(Optional.of(state));
        when(redis.getScores("room")).thenReturn(scores("A", "B", "D"));

        GameStateData updated = service.removePlayer("room", "C");

        assertFalse(updated.getPlayerOrder().contains("C"));
        assertEquals("D", updated.getPlayerOrder().get(2)); // round 3 drawer
        verify(redis).removePlayer("room", "C", 2);
        verify(redis).saveState(state);
    }

    @Test
    void currentDrawerLeavingSkipsImmediatelyToNextActivePlayer() {
        GameStateData state = state(1, 4, "A", "WORD_SELECTION", List.of("A", "B", "C"));
        when(redis.findState("room")).thenReturn(Optional.of(state));
        when(redis.getScores("room")).thenReturn(scores("B", "C"));

        GameStateData updated = service.removePlayer("room", "A");

        assertEquals(2, updated.getCurrentRound());
        assertEquals("B", updated.getDrawerId());
        assertEquals("WORD_SELECTION", updated.getRoundPhase());
        assertFalse(updated.getPlayerOrder().contains("A"));
        verify(scheduler).cancelAll("room");
        verify(redis).clearRoundGuesses("room", 2);
        verify(events).publishWordSelectionStarted("room", state);
    }

    @Test
    void gameFinishesImmediatelyWhenOnlyOnePlayerRemains() {
        GameStateData state = state(1, 4, "A", "DRAWING", List.of("A", "B"));
        when(redis.findState("room")).thenReturn(Optional.of(state));
        when(redis.getScores("room")).thenReturn(scores("A"));
        when(results.findByGameId("game")).thenReturn(Optional.of(mock(GameResultEntity.class)));

        GameStateData finished = service.removePlayer("room", "B");

        assertEquals("FINISHED", finished.getStatus());
        assertEquals(List.of("A"), finished.getPlayerOrder());
        assertEquals(List.of("A"), finished.getScores().stream().map(PlayerScoreData::getPlayerId).toList());
        verify(scheduler).cancelAll("room");
        verify(roomClient).finishGame("room");
        verify(events).publishGameFinished(eq("room"), eq("game"), anyList(), anyList());
    }

    private static GameStateData state(int round, int totalRounds, String drawer,
                                       String phase, List<String> playerOrder) {
        LinkedHashMap<String, Integer> startScores = new LinkedHashMap<>();
        playerOrder.forEach(player -> startScores.put(player, 0));
        return GameStateData.builder()
                .roomId("room")
                .gameId("game")
                .status("PLAYING")
                .roundPhase(phase)
                .currentRound(round)
                .totalRounds(totalRounds)
                .drawerId(drawer)
                .playerOrder(new ArrayList<>(playerOrder))
                .selectedCategories(List.of("ANIMALS"))
                .roundStartScores(startScores)
                .build();
    }

    private static List<PlayerScoreData> scores(String... playerIds) {
        List<PlayerScoreData> result = new ArrayList<>();
        for (String playerId : playerIds) {
            result.add(PlayerScoreData.builder()
                    .playerId(playerId).username(playerId).score(0).build());
        }
        return result;
    }
}
