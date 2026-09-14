package com.drawgame.realtime_gateway.control.redis;

import com.drawgame.realtime_gateway.connection.ConnectionManager;
import com.drawgame.realtime_gateway.control.LocalRoomBroadcaster;
import com.drawgame.realtime_gateway.drawing.recovery.DrawingRecoveryRepository;
import com.drawgame.realtime_gateway.drawing.routing.DrawingRoomState;
import com.drawgame.realtime_gateway.drawing.routing.DrawingRoomStateCache;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Subscribes to Redis control channels and fans out received room-scoped control
 * events to LOCAL WebSocket connections only.
 *
 * <p>Channel pattern: {@code control:room:*} — one pattern subscription covers all
 * rooms; routing to the right local sessions is done via the room binding index.
 *
 * <p>Self-echo prevention: events originating from this Gateway instance are ignored
 * (they were already local-broadcast before publishing to Redis). Events published
 * by the Game Service (origin "game-service") are never self-echo-suppressed — every
 * Gateway must fan them out locally.
 *
 * <p>Loop prevention: a Redis-received event is NEVER republished to Redis — the
 * remote path terminates at the local WebSocket broadcast.
 *
 * <p>Round lifecycle: ROUND_STARTED / GAME_FINISHED events also refresh/evict the
 * drawing fast-path authorization cache so remote-Gateway drawing validation stays
 * in sync with server-authoritative round transitions.
 */
@Component
public class ControlRedisSubscriber {

    private static final Logger log = LoggerFactory.getLogger(ControlRedisSubscriber.class);
    private static final String CHANNEL_PATTERN = "control:room:*";
    static final String GAME_SERVICE_ORIGIN = "game-service";

    private final ReactiveRedisMessageListenerContainer listenerContainer;
    private final ConnectionManager connectionManager;
    private final LocalRoomBroadcaster localBroadcaster;
    private final DrawingRoomStateCache drawingRoomStateCache;
    private final DrawingRecoveryRepository recoveryRepository;
    private final String gatewayInstanceId;

    // Simple volatile counters for diagnostics (same style as DrawingRedisSubscriber)
    private volatile long receivedCount = 0L;
    private volatile long selfEchoIgnoredCount = 0L;
    private volatile long broadcastCount = 0L;

    public ControlRedisSubscriber(
            ReactiveRedisMessageListenerContainer listenerContainer,
            ConnectionManager connectionManager,
            LocalRoomBroadcaster localBroadcaster,
            DrawingRoomStateCache drawingRoomStateCache,
            DrawingRecoveryRepository recoveryRepository,
            @Value("${gateway.instance-id:gateway-default}") String gatewayInstanceId
    ) {
        this.listenerContainer = listenerContainer;
        this.connectionManager = connectionManager;
        this.localBroadcaster = localBroadcaster;
        this.drawingRoomStateCache = drawingRoomStateCache;
        this.recoveryRepository = recoveryRepository;
        this.gatewayInstanceId = gatewayInstanceId;
    }

    @PostConstruct
    public void subscribe() {
        log.info("ControlRedisSubscriber starting: gatewayId={} pattern={}", gatewayInstanceId, CHANNEL_PATTERN);

        listenerContainer
                .receive(PatternTopic.of(CHANNEL_PATTERN))
                .map(ReactiveSubscription.PatternMessage::getMessage)
                .doOnNext(this::handleMessage)
                .doOnError(e -> log.error("ControlRedisSubscriber stream error: {}", e.getMessage(), e))
                .onErrorContinue((e, obj) ->
                        log.warn("ControlRedisSubscriber skipping message due to error: {}", e.getMessage()))
                .subscribe();

        log.info("ControlRedisSubscriber subscribed to pattern: {}", CHANNEL_PATTERN);
    }

    private void handleMessage(String json) {
        receivedCount++;

        ControlEventEnvelope envelope = ControlEventCodec.read(json);
        if (envelope == null) {
            return;
        }

        // Self-echo prevention: ignore events we originally published.
        // Game Service publications carry origin "game-service" and must reach
        // every Gateway (including the one whose client triggered the transition).
        if (gatewayInstanceId.equals(envelope.originGatewayId())) {
            selfEchoIgnoredCount++;
            log.debug("ControlRedisSubscriber self-echo ignored: type={} origin={} room={}",
                    envelope.eventType(), envelope.originGatewayId(), envelope.targetRoomId());
            return;
        }

        // Keep the drawing authorization cache in sync with server-authoritative
        // round transitions (affects ALL Gateways, not just the one with the drawer).
        // TV7: also drive Canvas-recovery lifecycle (reset on new round, cleanup on finish).
        switch (envelope.eventType() == null ? "" : envelope.eventType()) {
            case "ROUND_STARTED" -> {
                ControlEventPayload payload = ControlEventCodec.readPayload(envelope.payload());
                if (payload != null && payload.drawerId() != null && !payload.drawerId().isBlank()) {
                    drawingRoomStateCache.update(envelope.targetRoomId(),
                            DrawingRoomState.playing(payload.drawerId(), payload.currentRound()));
                    log.debug("DrawingRoomStateCache refreshed via ROUND_STARTED: room={} drawer={} round={}",
                            envelope.targetRoomId(), payload.drawerId(), payload.currentRound());
                }
                // Old-round recovery history must never become the new round's canvas
                recoveryRepository.resetForNewRound(envelope.targetRoomId()).subscribe();
            }
            case "GAME_FINISHED" -> {
                drawingRoomStateCache.remove(envelope.targetRoomId());
                recoveryRepository.removeAll(envelope.targetRoomId()).subscribe();
                log.info("DrawingRoomStateCache evicted via GAME_FINISHED: room={}", envelope.targetRoomId());
            }
            default -> { /* no cache action needed for other control events */ }
        }

        // Room-scoped local fanout only — no sessions outside targetRoomId are touched,
        // and the event is never republished to Redis.
        localBroadcaster.broadcastToRoom(envelope.targetRoomId(), envelope.payload());
        broadcastCount++;
        log.debug("ControlRedisSubscriber fanout: type={} origin={} room={}",
                envelope.eventType(), envelope.originGatewayId(), envelope.targetRoomId());
    }

    /** Test seam: direct access to the private handler (unit tests only). */
    void handleMessageForTest(String json) {
        handleMessage(json);
    }

    // --- Diagnostics counters ---

    public long getReceivedCount()        { return receivedCount; }
    public long getSelfEchoIgnoredCount() { return selfEchoIgnoredCount; }
    public long getBroadcastCount()       { return broadcastCount; }
}
