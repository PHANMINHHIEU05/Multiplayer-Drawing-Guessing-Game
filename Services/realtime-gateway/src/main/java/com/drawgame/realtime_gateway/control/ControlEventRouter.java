package com.drawgame.realtime_gateway.control;

import com.drawgame.realtime_gateway.control.redis.ControlRedisPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Routes room-scoped CONTROL events through the dual fanout model:
 *
 * <pre>
 *              control event
 *                   |
 *           +-------+-------+
 *           |               |
 *           v               v
 *    local broadcast    Redis publish
 *                          |
 *                          v
 *                     other Gateways
 *                          |
 *                          v
 *                    their local broadcast
 * </pre>
 *
 * <p>The caller passes the client-facing JSON ONCE; the router local-broadcasts it
 * and (asynchronously) publishes it to Redis. Redis failures never break the local
 * path (see {@link ControlRedisPublisher} failure policy).
 *
 * <p>PRIVATE events (GUESS_RESULT, ERROR, viewer-specific responses) must NOT go
 * through this router — they use {@code ConnectionManager.sendToSession} directly.
 */
@Component
public class ControlEventRouter {

    private static final Logger log = LoggerFactory.getLogger(ControlEventRouter.class);

    private final LocalRoomBroadcaster local;
    private final ControlRedisPublisher redisPublisher;

    public ControlEventRouter(LocalRoomBroadcaster local, ControlRedisPublisher redisPublisher) {
        this.local = local;
        this.redisPublisher = redisPublisher;
    }

    /** Broadcast a control event to all room members on this Gateway, then fan out remotely. */
    public void broadcastToRoom(String roomId, String eventType, String payloadJson) {
        local.broadcastToRoom(roomId, payloadJson);
        redisPublisher.publish(roomId, eventType, payloadJson).subscribe();
        log.debug("Control event routed: type={} room={} (local + redis)", eventType, roomId);
    }

    /**
     * Broadcast to all room members on this Gateway EXCEPT the sending session
     * (which already has the event locally), then fan out remotely.
     */
    public void broadcastToRoomExcept(String roomId, String eventType, String payloadJson, String senderSessionId) {
        local.broadcastToRoomExcept(roomId, payloadJson, senderSessionId);
        redisPublisher.publish(roomId, eventType, payloadJson).subscribe();
        log.debug("Control event routed: type={} room={} sender excluded={} (local + redis)",
                eventType, roomId, senderSessionId);
    }
}
