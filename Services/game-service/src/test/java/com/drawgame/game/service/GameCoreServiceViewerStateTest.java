package com.drawgame.game.service;

import com.drawgame.game.model.GameStateData;
import com.drawgame.game.model.WordChoiceData;
import com.drawgame.game.repository.GameResultRepository;
import com.drawgame.game.repository.RedisGameRepository;
import com.drawgame.game.repository.WordRepository;
import com.drawgame.game.grpc.client.RoomGrpcClient;
import com.drawgame.game.service.component.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameCoreServiceViewerStateTest {
    private final RedisGameRepository redis = mock(RedisGameRepository.class);
    private final WordRepository words = mock(WordRepository.class);
    private final GameResultRepository results = mock(GameResultRepository.class);
    private final RoomGrpcClient roomClient = mock(RoomGrpcClient.class);
    private final RoundScheduler scheduler = mock(RoundScheduler.class);
    private final GameControlEventPublisher events = mock(GameControlEventPublisher.class);
    private final GameCoreService service = new GameCoreService(redis, words, results, roomClient,
            new HintGenerator(), new ScoreCalculator(), new AnswerEvaluator(), scheduler, events,
            new WordChoiceGenerator(words), new MatchAwardCalculator(), new ObjectMapper());

    @Test
    void wordChoicesArePrivateToDrawerAndNeverIncludeSecretForGuesser() {
        GameStateData drawerView = selectionState();
        GameStateData guesserView = selectionState();
        when(redis.findState("room")).thenReturn(Optional.of(drawerView), Optional.of(guesserView));

        GameStateData drawer = service.getGameState("room", "drawer");
        GameStateData guesser = service.getGameState("room", "guesser");

        assertEquals(3, drawer.getWordChoices().size());
        assertTrue(drawer.getWordChoices().stream().anyMatch(choice -> choice.displayWord().contains("á")));
        assertEquals(List.of(), guesser.getWordChoices());
        assertEquals("", guesser.getSecretWord());
    }

    @Test
    void countdownAndDrawingHideAnswerFromGuessersButRecapMayRevealIt() {
        for (String phase : List.of("COUNTDOWN", "DRAWING")) {
            GameStateData guesserState = selectionState();
            guesserState.setRoundPhase(phase);
            guesserState.setSecretWord("trái đất");
            GameStateData drawerState = selectionState();
            drawerState.setRoundPhase(phase);
            drawerState.setSecretWord("trái đất");
            when(redis.findState("room")).thenReturn(Optional.of(guesserState), Optional.of(drawerState));
            assertEquals("", service.getGameState("room", "guesser").getSecretWord(), phase);
            assertEquals("trái đất", service.getGameState("room", "drawer").getSecretWord(), phase);
        }

        GameStateData recap = selectionState();
        recap.setRoundPhase("ROUND_RECAP");
        recap.setSecretWord("trái đất");
        when(redis.findState("room")).thenReturn(Optional.of(recap));
        assertEquals("trái đất", service.getGameState("room", "guesser").getSecretWord());
    }

    @Test
    void onlyCurrentDrawerCanSubmitOfferedChoice() {
        when(redis.findState("room")).thenReturn(Optional.of(selectionState()));
        assertThrows(IllegalArgumentException.class, () -> service.selectWord("room", "guesser", "choice-1"));
        verify(redis, never()).atomicSelectWord(anyString(), anyString(), anyInt(), anyString(), anyLong(), anyLong(), anyBoolean());
    }

    private static GameStateData selectionState() {
        return GameStateData.builder().roomId("room").gameId("game").status("PLAYING")
                .roundPhase("WORD_SELECTION").currentRound(1).drawerId("drawer").secretWord("secret")
                .wordChoices(List.of(new WordChoiceData("choice-1", "trái đất"),
                        new WordChoiceData("choice-2", "máy bay"), new WordChoiceData("choice-3", "con mèo")))
                .build();
    }
}
