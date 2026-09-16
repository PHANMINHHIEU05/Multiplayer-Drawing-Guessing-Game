package com.drawgame.game.service.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

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
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ROUND_STARTED");
        payload.put("roomId", roomId);
        payload.put("currentRound", round);
        payload.put("drawerId", drawerId);
        payload.put("status", "PLAYING");
        publish(roomId, "ROUND_STARTED", payload);
    }

    /** Broadcast ROOM-scoped event: the current round ended (intermission). */
    public void publishRoundEnded(String roomId, int round) {
        publishRoundEnded(roomId, round, null);
    }

    /** Broadcast ROOM-scoped event: the current round ended with revealed answer. */
    public void publishRoundEnded(String roomId, int round, String revealedWord) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ROUND_ENDED");
        payload.put("roomId", roomId);
        payload.put("currentRound", round);
        payload.put("status", "ROUND_ENDED");
        if (revealedWord != null && !revealedWord.isBlank()) {
            payload.put("word", revealedWord);
            payload.put("revealedWord", revealedWord);
        }
        publish(roomId, "ROUND_ENDED", payload);
    }

    /** Broadcast ROOM-scoped event: the game finished — no more drawing/guessing. */
    public void publishGameFinished(String roomId) {
        publishGameFinished(roomId, Collections.emptyList());
    }

    public void publishGameFinished(String roomId, List<com.drawgame.game.model.PlayerScoreData> scores) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "GAME_FINISHED");
        payload.put("roomId", roomId);
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
        publish(roomId, "GAME_FINISHED", payload);
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
