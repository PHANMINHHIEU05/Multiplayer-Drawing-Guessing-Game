package com.drawgame.realtime_gateway.control.redis;

import com.drawgame.realtime_gateway.connection.ConnectionManager;
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
 * (they were already local-broadcast before publishing to Redis).
 *
 * <p>Loop prevention: a Redis-received event is NEVER republished to Redis — the
 * remote path terminates at the local WebSocket broadcast.
 *
 * <p>No gRPC call is made here — the originating Gateway already authorized the event.
 */
@Component
public class ControlRedisSubscriber {

    private static final Logger log = LoggerFactory.getLogger(ControlRedisSubscriber.class);
    private static final String CHANNEL_PATTERN = "control:room:*";

    private final ReactiveRedisMessageListenerContainer listenerContainer;
    private final ConnectionManager connectionManager;
    private final String gatewayInstanceId;

    // Simple volatile counters for diagnostics (same style as DrawingRedisSubscriber)
    private volatile long receivedCount = 0L;
    private volatile long selfEchoIgnoredCount = 0L;
    private volatile long broadcastCount = 0L;

    public ControlRedisSubscriber(
            ReactiveRedisMessageListenerContainer listenerContainer,
            ConnectionManager connectionManager,
            @Value("${gateway.instance-id:gateway-default}") String gatewayInstanceId
    ) {
        this.listenerContainer = listenerContainer;
        this.connectionManager = connectionManager;
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

        // Self-echo prevention: ignore events we originally published
        if (gatewayInstanceId.equals(envelope.originGatewayId())) {
            selfEchoIgnoredCount++;
            log.debug("ControlRedisSubscriber self-echo ignored: type={} origin={} room={}",
                    envelope.eventType(), envelope.originGatewayId(), envelope.targetRoomId());
            return;
        }

        // Room-scoped local fanout only — no sessions outside targetRoomId are touched,
        // and the event is never republished to Redis.
        connectionManager.broadcastToRoom(envelope.targetRoomId(), envelope.payload());
        broadcastCount++;
        log.debug("ControlRedisSubscriber fanout: type={} origin={} room={}",
                envelope.eventType(), envelope.originGatewayId(), envelope.targetRoomId());
    }

    // --- Diagnostics counters ---

    public long getReceivedCount()        { return receivedCount; }
    public long getSelfEchoIgnoredCount() { return selfEchoIgnoredCount; }
    public long getBroadcastCount()       { return broadcastCount; }
}
