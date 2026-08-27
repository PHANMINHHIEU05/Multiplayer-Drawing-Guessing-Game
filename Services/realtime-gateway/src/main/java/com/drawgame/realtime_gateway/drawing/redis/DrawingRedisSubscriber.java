package com.drawgame.realtime_gateway.drawing.redis;

import com.drawgame.realtime_gateway.drawing.routing.DrawingBroadcaster;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Subscribes to Redis drawing channels and fans out received drawing events
 * to local WebSocket connections.
 *
 * <p>Channel pattern: {@code drawing:room:*} — subscribes to all rooms at once.
 *
 * <p>Self-echo prevention: events originating from this Gateway instance are ignored
 * (they were already local-broadcast before publishing to Redis).
 *
 * <p>No gRPC / Game Service call is made here — the source Gateway already authorized
 * the event. Receiver only does room routing and local fanout.
 */
@Component
public class DrawingRedisSubscriber {

    private static final Logger log = LoggerFactory.getLogger(DrawingRedisSubscriber.class);
    private static final String CHANNEL_PATTERN = "drawing:room:*";

    private final ReactiveRedisMessageListenerContainer listenerContainer;
    private final DrawingBroadcaster broadcaster;
    private final ObjectMapper objectMapper;
    private final String gatewayInstanceId;

    // Metrics counters (simple volatile longs; TV4 can expose via Actuator later)
    private volatile long receivedCount = 0L;
    private volatile long selfEchoIgnoredCount = 0L;
    private volatile long broadcastCount = 0L;
    private volatile long deserializeErrorCount = 0L;

    public DrawingRedisSubscriber(
            ReactiveRedisMessageListenerContainer listenerContainer,
            DrawingBroadcaster broadcaster,
            ObjectMapper objectMapper,
            @Value("${gateway.instance-id:gateway-default}") String gatewayInstanceId
    ) {
        this.listenerContainer = listenerContainer;
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
        this.gatewayInstanceId = gatewayInstanceId;
    }

    @PostConstruct
    public void subscribe() {
        log.info("DrawingRedisSubscriber starting: gatewayId={} pattern={}", gatewayInstanceId, CHANNEL_PATTERN);

        listenerContainer
                .receive(PatternTopic.of(CHANNEL_PATTERN))
                .map(ReactiveSubscription.PatternMessage::getMessage)
                .doOnNext(this::handleMessage)
                .doOnError(e -> log.error("DrawingRedisSubscriber stream error: {}", e.getMessage(), e))
                .onErrorContinue((e, obj) ->
                        log.warn("DrawingRedisSubscriber skipping message due to error: {}", e.getMessage()))
                .subscribe();

        log.info("DrawingRedisSubscriber subscribed to pattern: {}", CHANNEL_PATTERN);
    }

    private void handleMessage(String json) {
        receivedCount++;

        RedisDrawingEnvelope envelope;
        try {
            envelope = objectMapper.readValue(json, RedisDrawingEnvelope.class);
        } catch (Exception e) {
            deserializeErrorCount++;
            log.warn("DrawingRedisSubscriber failed to deserialize message: {}", e.getMessage());
            return;
        }

        // Self-echo prevention: ignore events we originally published
        if (gatewayInstanceId.equals(envelope.originGatewayId())) {
            selfEchoIgnoredCount++;
            log.trace("DrawingRedisSubscriber self-echo ignored: origin={} room={}",
                    envelope.originGatewayId(), envelope.targetRoomId());
            return;
        }

        // Broadcast to ALL local connections in the room (sender is on a different Gateway)
        broadcaster.broadcastBytesToRoomExcept(envelope.targetRoomId(), null, envelope.drawingBytes());
        broadcastCount++;
        log.trace("DrawingRedisSubscriber fanout: origin={} room={} bytes={}",
                envelope.originGatewayId(), envelope.targetRoomId(), envelope.drawingBytes().length);
    }

    // --- Metrics hooks for TV4 ---

    public long getReceivedCount()           { return receivedCount; }
    public long getSelfEchoIgnoredCount()    { return selfEchoIgnoredCount; }
    public long getBroadcastCount()          { return broadcastCount; }
    public long getDeserializeErrorCount()   { return deserializeErrorCount; }
}
