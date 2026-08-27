package com.drawgame.realtime_gateway.drawing.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Publishes drawing events to Redis Pub/Sub for cross-Gateway fanout.
 *
 * <p>Channel strategy: one channel per room → {@code drawing:room:{roomId}}.
 * This avoids all subscribers receiving all rooms' traffic and filtering per message.
 *
 * <p>Failure policy: Redis publish failure is caught and logged at WARN level;
 * it does NOT interrupt local broadcast or crash the WebSocket handler.
 */
@Component
public class DrawingRedisPublisher {

    private static final Logger log = LoggerFactory.getLogger(DrawingRedisPublisher.class);
    private static final String CHANNEL_PREFIX = "drawing:room:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String gatewayInstanceId;

    public DrawingRedisPublisher(
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${gateway.instance-id:gateway-default}") String gatewayInstanceId
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.gatewayInstanceId = gatewayInstanceId;
        log.info("DrawingRedisPublisher initialized: gatewayId={}", gatewayInstanceId);
    }

    /**
     * Publish already-encoded drawing bytes for the given room to Redis.
     *
     * @param roomId       the target room
     * @param drawingBytes binary drawing payload (already encoded by BinaryDrawingEncoder)
     * @return a {@link Mono} that completes when publish succeeds, or empty on failure
     */
    public Mono<Void> publish(String roomId, byte[] drawingBytes) {
        String channel = CHANNEL_PREFIX + roomId;
        RedisDrawingEnvelope envelope = new RedisDrawingEnvelope(gatewayInstanceId, roomId, drawingBytes);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.warn("Failed to serialize RedisDrawingEnvelope for room={}: {}", roomId, e.getMessage());
            return Mono.empty();
        }

        return redisTemplate.convertAndSend(channel, json)
                .then()
                .doOnSuccess(v -> log.trace("Drawing published to Redis: channel={} bytes={}", channel, drawingBytes.length))
                .onErrorResume(e -> {
                    log.warn("Redis publish failed for room={}: {} — local broadcast unaffected", roomId, e.getMessage());
                    return Mono.empty();
                });
    }
}
