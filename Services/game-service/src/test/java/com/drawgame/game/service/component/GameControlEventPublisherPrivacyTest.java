package com.drawgame.game.service.component;

import com.drawgame.game.model.GameStateData;
import com.drawgame.game.model.WordChoiceData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GameControlEventPublisherPrivacyTest {
    @Test
    void publicSelectionAndCountdownEventsNeverContainDrawerChoicesOrAnswer() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        GameControlEventPublisher publisher = new GameControlEventPublisher(redis);
        GameStateData state = GameStateData.builder().roomId("room").gameId("game").currentRound(1)
                .drawerId("drawer").roundPhase("WORD_SELECTION").secretWord("trái đất")
                .wordChoices(java.util.List.of(new WordChoiceData("opaque-1", "trái đất"),
                        new WordChoiceData("opaque-2", "máy bay"), new WordChoiceData("opaque-3", "con mèo")))
                .phaseStartedAt(100).phaseEndsAt(200).build();

        publisher.publishWordSelectionStarted("room", state);
        publisher.publishCountdownStarted("room", state);
        publisher.publishRoundEnded("room", 1, "trái đất");

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redis, times(3)).convertAndSend(eq("control:room:room"), captor.capture());
        ObjectMapper mapper = new ObjectMapper();
        for (String envelopeJson : captor.getAllValues()) {
            String eventPayload = mapper.readTree(envelopeJson).get("payload").asText();
            assertFalse(eventPayload.contains("trái đất"));
            assertFalse(eventPayload.contains("máy bay"));
            assertFalse(eventPayload.contains("con mèo"));
            assertFalse(eventPayload.contains("opaque-1"));
        }
    }
}
