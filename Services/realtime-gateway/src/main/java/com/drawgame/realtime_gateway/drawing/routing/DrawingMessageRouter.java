package com.drawgame.realtime_gateway.drawing.routing;

import com.drawgame.realtime_gateway.drawing.protocol.DrawingMessage;
import com.drawgame.realtime_gateway.drawing.redis.DrawingRedisPublisher;
import com.drawgame.realtime_gateway.drawing.transport.DrawingMessageHandler;
import com.drawgame.realtime_gateway.drawing.transport.DrawingSessionContext;
import com.drawgame.realtime_gateway.drawing.transport.DrawingWebSocketEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * TV3 entry point: receives a decoded DrawingMessage from the transport layer (TV1)
 * and orchestrates the full drawing fast-path pipeline:
 *
 *   DrawingMessage
 *     -> Authorization (drawer check, round check)
 *     -> DrawingEventHook.onAccepted() (recovery groundwork hook — no-op in stabilization sprint)
 *     -> Local room broadcast (exclude sender)
 *     -> Redis Pub/Sub publish (cross-Gateway fanout)
 *
 * Registered as "customDrawingMessageHandler" to override the TV1 stub.
 * Drawing bytes are encoded once and reused for both local broadcast and Redis publish.
 * This bean is fully non-blocking.
 */
@Component("customDrawingMessageHandler")
public class DrawingMessageRouter implements DrawingMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(DrawingMessageRouter.class);

    private final DrawingAuthorizationService authService;
    private final DrawingBroadcaster broadcaster;
    private final DrawingRedisPublisher redisPublisher;
    private final DrawingWebSocketEncoder encoder;
    /** TV3 Stabilization (TV3-G08) — recovery groundwork hook; no-op by default. */
    private final DrawingEventHook eventHook;

    // Metrics hooks — TV4 can instrument via Actuator
    private volatile long acceptedCount = 0L;
    private volatile long rejectedCount = 0L;
    private volatile long localBroadcastCount = 0L;
    private volatile long redisPublishedCount = 0L;

    public DrawingMessageRouter(
            DrawingAuthorizationService authService,
            DrawingBroadcaster broadcaster,
            DrawingRedisPublisher redisPublisher,
            DrawingWebSocketEncoder encoder,
            DrawingEventHook eventHook
    ) {
        this.authService = authService;
        this.broadcaster = broadcaster;
        this.redisPublisher = redisPublisher;
        this.encoder = encoder;
        this.eventHook = eventHook;
    }

    @Override
    public Mono<Void> handle(DrawingSessionContext session, DrawingMessage message) {
        // 1. Authorization — fast check against in-memory cache
        DrawingAuthorizationService.AuthResult authResult = authService.authorize(session, message);

        if (!authResult.isAuthorized()) {
            rejectedCount++;
            DrawingAuthorizationService.AuthResult.Rejected rejected =
                    (DrawingAuthorizationService.AuthResult.Rejected) authResult;
            log.debug("Drawing rejected [{}]: session={} player={} room={} opcode={} round={}",
                    rejected.reason(), session.sessionId(), session.playerId(),
                    session.roomId(), message.opcode(), message.round());
            return Mono.empty();
        }

        acceptedCount++;

        // 2. Encode once — reuse for both local broadcast and Redis
        byte[] drawingBytes = encoder.encodeToBytes(message);

        // 3. Recovery groundwork hook (TV3-G08) — no-op in this sprint, swappable in next phase
        eventHook.onAccepted(session, message, drawingBytes);

        // 4. Local room broadcast (exclude the sender — drawer renders locally)
        broadcaster.broadcastBytesToRoomExcept(session.roomId(), session.sessionId(), drawingBytes);
        localBroadcastCount++;

        // 5. Publish to Redis for cross-Gateway fanout (failure is isolated inside publisher)
        return redisPublisher.publish(session.roomId(), drawingBytes)
                .doOnSuccess(v -> redisPublishedCount++)
                .onErrorResume(e -> {
                    log.warn("DrawingMessageRouter unexpected error from Redis publisher: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    // --- Metrics hooks for TV4 ---
    public long getAcceptedCount()       { return acceptedCount; }
    public long getRejectedCount()       { return rejectedCount; }
    public long getLocalBroadcastCount() { return localBroadcastCount; }
    public long getRedisPublishedCount() { return redisPublishedCount; }
}
