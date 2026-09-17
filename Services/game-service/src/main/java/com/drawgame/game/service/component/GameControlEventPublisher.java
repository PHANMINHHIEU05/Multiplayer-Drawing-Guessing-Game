package com.drawgame.game.service.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import com.drawgame.game.model.GameStateData;
import com.drawgame.game.model.MatchAwardData;
import com.drawgame.game.model.RoundRecapData;

/**
 * Publishes game-lifecycle CONTROL events to Redis Pub/Sub so that EVERY Realtime
 * Gateway instance receives them (TV6 cross-Gateway control fanout).
 *
 * <p>Channel: {@code control:room:{roomId}} — the same channel family the Gateways
 * subscribe to ({@code control:room:*}). The envelope shape matches the Gateway's
 * {@code ControlEventEnvelope} (originGatewayId / targetRoomId / eventType / eventId /
 * payload). {@code originGatewayId} is fixed to "game-service" so no Gateway
 * self-echo-suppresses these events — every instance fans them out locally.
 *
 * <p>Events published here (server-authoritative lifecycle transitions that today
 * only reach clients via the 5s GET_GAME_STATE poll, leaving remote-Gateway
 * drawing-authorization caches stale in between):
 * <ul>
 *   <li>ROUND_STARTED — clients clear canvas; Gateways refresh DrawingRoomStateCache</li>
 *   <li>ROUND_ENDED — clients transition out of the round</li>
 *   <li>GAME_FINISHED — clients enter the finished state</li>
 * </ul>
 *
 * <p>Payload secrecy: payloads carry only roomId / round / drawerId / status —
 * NEVER the secret word (clients fetch viewer-safe state via GET_GAME_STATE).
 *
 * <p>Failure policy: publish failure is logged and swallowed — a fanout hiccup must
 * never break the round lifecycle (the 5s client poll remains the safety net).
 *
 * <p>Runs on the RoundScheduler executor thread (NOT a Netty event loop), so the
 * blocking {@link StringRedisTemplate} is acceptable here.
 */
@Slf4j
@Component
public class GameControlEventPublisher {

    static final String ORIGIN_ID = "game-service";
    private static final String CHANNEL_PREFIX = "control:room:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GameControlEventPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** Broadcast ROOM-scoped event: a new round started with a new drawer. */
    public void publishRoundStarted(String roomId, int round, String drawerId) {
        publishRoundStarted(roomId, "", round, drawerId, 0, 0);
    }

    public void publishWordSelectionStarted(String roomId, GameStateData state) {
        Map<String, Object> payload = publicPhasePayload("WORD_SELECTION_STARTED", roomId, state, "WORD_SELECTION");
        publish(roomId, "WORD_SELECTION_STARTED", payload);
    }

    public void publishCountdownStarted(String roomId, GameStateData state) {
        Map<String, Object> payload = publicPhasePayload("ROUND_COUNTDOWN_STARTED", roomId, state, "COUNTDOWN");
        publish(roomId, "ROUND_COUNTDOWN_STARTED", payload);
    }

    public void publishRoundStarted(String roomId, String gameId, int round, String drawerId,
                                    long startedAt, long endsAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ROUND_STARTED");
        payload.put("roomId", roomId);
        payload.put("gameId", gameId);
        payload.put("roundPhase", "DRAWING");
        payload.put("currentRound", round);
        payload.put("drawerId", drawerId);
        payload.put("phaseStartedAt", startedAt);
        payload.put("phaseEndsAt", endsAt);
        payload.put("roundStartedAt", startedAt);
        payload.put("roundEndsAt", endsAt);
        payload.put("status", "PLAYING");
        publish(roomId, "ROUND_STARTED", payload);
    }

    public void publishHintUpdated(String roomId, String gameId, int round, int hintStage,
                                   long phaseEndsAt, String pattern) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "HINT_UPDATED");
        payload.put("roomId", roomId);
        payload.put("gameId", gameId);
        payload.put("currentRound", round);
        payload.put("roundPhase", "DRAWING");
        payload.put("hintStage", hintStage);
        payload.put("phaseEndsAt", phaseEndsAt);
        payload.put("hint", pattern);
        publish(roomId, "HINT_UPDATED", payload);
    }

    /** Broadcast ROOM-scoped event: the current round ended (intermission). */
    public void publishRoundEnded(String roomId, int round) {
        publishRoundEnded(roomId, round, null);
    }

    /** Legacy ROUND_ENDED signal. Answers are only published by ROUND_RECAP_STARTED. */
    public void publishRoundEnded(String roomId, int round, String revealedWord) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ROUND_ENDED");
        payload.put("roomId", roomId);
        payload.put("currentRound", round);
        payload.put("status", "ROUND_ENDED");
        publish(roomId, "ROUND_ENDED", payload);
    }

    /** The answer becomes public only after the authoritative ROUND_RECAP transition. */
    public void publishRoundRecapStarted(String roomId, GameStateData state, RoundRecapData recap) {
        Map<String, Object> payload = publicPhasePayload("ROUND_RECAP_STARTED", roomId, state, "ROUND_RECAP");
        payload.put("type", "ROUND_RECAP_STARTED");
        payload.put("answer", recap.answer());
        payload.put("word", recap.answer());
        payload.put("roundRecap", recap);
        payload.put("scores", state.getScores());
        publish(roomId, "ROUND_RECAP_STARTED", payload);
    }

    /** Broadcast ROOM-scoped event: the game finished — no more drawing/guessing. */
    public void publishGameFinished(String roomId) {
        publishGameFinished(roomId, Collections.emptyList());
    }

    public void publishGameFinished(String roomId, List<com.drawgame.game.model.PlayerScoreData> scores) {
        publishGameFinished(roomId, "", scores, List.of());
    }

    public void publishGameFinished(String roomId, String gameId,
                                    List<com.drawgame.game.model.PlayerScoreData> scores,
                                    List<MatchAwardData> awards) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "GAME_FINISHED");
        payload.put("roomId", roomId);
        payload.put("gameId", gameId);
        payload.put("status", "FINISHED");
        if (scores != null && !scores.isEmpty()) {
            payload.put("scores", scores.stream()
                    .map(s -> Map.of(
                            "playerId", s.getPlayerId(),
                            "username", s.getUsername() != null ? s.getUsername() : s.getPlayerId(),
                            "score", s.getScore()
                    ))
                    .collect(java.util.stream.Collectors.toList()));
        }
        payload.put("awards", awards == null ? List.of() : awards);
        publish(roomId, "GAME_FINISHED", payload);
    }

    private Map<String, Object> publicPhasePayload(String type, String roomId,
                                                   GameStateData state, String phase) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("roomId", roomId);
        payload.put("gameId", state.getGameId());
        payload.put("currentRound", state.getCurrentRound());
        payload.put("drawerId", state.getDrawerId());
        payload.put("roundPhase", phase);
        payload.put("status", "PLAYING");
        payload.put("phaseStartedAt", state.getPhaseStartedAt());
        payload.put("phaseEndsAt", state.getPhaseEndsAt());
        return payload;
    }

    private void publish(String roomId, String eventType, Map<String, Object> payload) {
        try {
            Map<String, Object> envelope = new HashMap<>();
            envelope.put("originGatewayId", ORIGIN_ID);
            envelope.put("targetRoomId", roomId);
            envelope.put("eventType", eventType);
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.put("payload", objectMapper.writeValueAsString(payload));

            String channel = CHANNEL_PREFIX + roomId;
            redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(envelope));
            log.debug("Control event published: type={} room={}", eventType, roomId);
        } catch (Exception e) {
            log.warn("Control event publish failed (non-fatal): type={} room={}: {}",
                    eventType, roomId, e.getMessage());
        }
    }
}
