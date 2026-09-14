package com.drawgame.realtime_gateway.control.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Publishes room-scoped CONTROL events to Redis Pub/Sub for cross-Gateway fanout.
 *
 * <p>Channel strategy (consistent with the drawing Pub/Sub design):
 * one channel per room → {@code control:room:{roomId}}.
 *
 * <p>Failure policy: Redis publish failure is caught and logged at WARN level;
 * it does NOT interrupt the local broadcast or crash the WebSocket handler.
 */
@Component
public class ControlRedisPublisher {

    private static final Logger log = LoggerFactory.getLogger(ControlRedisPublisher.class);
    private static final String CHANNEL_PREFIX = "control:room:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final String gatewayInstanceId;

    public ControlRedisPublisher(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${gateway.instance-id:gateway-default}") String gatewayInstanceId
    ) {
        this.redisTemplate = redisTemplate;
        this.gatewayInstanceId = gatewayInstanceId;
        log.info("ControlRedisPublisher initialized: gatewayId={}", gatewayInstanceId);
    }

    /**
     * Publish a room-broadcast control event. The payload must be the exact
     * client-facing JSON already broadcast locally — safe for all room members.
     *
     * @param roomId    target room for routing on remote Gateways
     * @param eventType control event type, for diagnostics
     * @param payload   ready-to-send client JSON
     * @return a {@link Mono} that completes when publish succeeds, or empty on failure
     */
    public Mono<Void> publish(String roomId, String eventType, String payload) {
        String channel = CHANNEL_PREFIX + roomId;
        ControlEventEnvelope envelope = new ControlEventEnvelope(
                gatewayInstanceId, roomId, eventType, UUID.randomUUID().toString(), payload);

        return redisTemplate.convertAndSend(channel, writeEnvelope(envelope))
                .then()
                .doOnSuccess(v -> log.debug("Control event published: type={} room={} origin={}",
                        eventType, roomId, gatewayInstanceId))
                .onErrorResume(e -> {
                    log.warn("Control Redis publish failed: type={} room={} — local broadcast unaffected: {}",
                            eventType, roomId, e.getMessage());
                    return Mono.empty();
                });
    }

    private String writeEnvelope(ControlEventEnvelope envelope) {
        // Serialized by ControlEventCodec to keep Jackson handling in one place.
        return ControlEventCodec.write(envelope);
    }
}
