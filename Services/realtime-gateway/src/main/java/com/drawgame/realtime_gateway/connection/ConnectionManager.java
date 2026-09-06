package com.drawgame.realtime_gateway.connection;

import com.drawgame.realtime_gateway.metrics.GatewayMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active WebSocket client sessions and outbound bounded queues.
 *
 * <p>TV3 Stabilization:
 * - unbindSession(): called on LEAVE_ROOM (GW-07)
 * - playerRoomToSession reverse map: duplicate session guard (GW-10)
 * - Dead-sink cleanup in broadcast loops (GW-09)
 */
@Component
public class ConnectionManager {

    private static final Logger log =
            LoggerFactory.getLogger(ConnectionManager.class);

    private final Map<String, BoundedOutboundQueue> clients =
            new ConcurrentHashMap<>();

    private final Map<String, String> sessionToRoom =
            new ConcurrentHashMap<>();

    private final Map<String, String> sessionToPlayer =
            new ConcurrentHashMap<>();

    /** TV3: "{playerId}:{roomId}" -> sessionId. Prevents duplicate logical player on reconnect (GW-10). */
    private final Map<String, String> playerRoomToSession =
            new ConcurrentHashMap<>();

    private final GatewayMetrics gatewayMetrics;
    private final int defaultMaxQueueCapacity;

    public ConnectionManager() {
        this(new GatewayMetrics(), 256);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ConnectionManager(
            GatewayMetrics gatewayMetrics,
            @Value("${gateway.queue.max-capacity:256}") int defaultMaxQueueCapacity
    ) {
        this.gatewayMetrics = gatewayMetrics;
        this.defaultMaxQueueCapacity = defaultMaxQueueCapacity;
    }

    public Flux<OutboundFrame> register(String sessionId) {
        return register(sessionId, defaultMaxQueueCapacity);
    }

    public Flux<OutboundFrame> register(String sessionId, int maxCapacity) {
        BoundedOutboundQueue queue = new BoundedOutboundQueue(sessionId, maxCapacity, gatewayMetrics);
        clients.put(sessionId, queue);
        gatewayMetrics.incrementActiveConnections();
        log.info("Client connected: {} | Online clients: {} | QueueCapacity: {}",
                sessionId, clients.size(), maxCapacity);
        return queue.asFlux();
    }

    /**
     * Bind a WebSocket session to a room and player.
     *
     * <p>TV3 Stabilization (GW-10): If the same player is already bound to the same room
     * via a different session, the old session's routing state is evicted silently.
     * The old WebSocket connection is NOT closed here — it cleans itself up via doFinally.
     */
    public void bindSession(String sessionId, String roomId, String playerId) {
        if (roomId != null) {
            sessionToRoom.put(sessionId, roomId);
        }
        if (playerId != null) {
            sessionToPlayer.put(sessionId, playerId);
        }

        if (playerId != null && roomId != null) {
            String key = playerId + ":" + roomId;
            String prev = playerRoomToSession.put(key, sessionId);
            if (prev != null && !prev.equals(sessionId)) {
                log.info("Duplicate session evicted: player={} room={} old={} new={}",
                        playerId, roomId, prev, sessionId);
                sessionToRoom.remove(prev);
                sessionToPlayer.remove(prev);
            }
        }
    }

    /**
     * TV3 Stabilization (GW-07): unbind routing state after explicit LEAVE_ROOM.
     * The WebSocket connection stays open; the session no longer receives drawing events.
     */
    public void unbindSession(String sessionId) {
        String roomId = sessionToRoom.remove(sessionId);
        String playerId = sessionToPlayer.remove(sessionId);
        if (playerId != null && roomId != null) {
            playerRoomToSession.remove(playerId + ":" + roomId, sessionId);
        }
        log.info("Session unbound: session={} prevRoom={} prevPlayer={}", sessionId, roomId, playerId);
    }

    public String getRoomId(String sessionId) {
        return sessionToRoom.get(sessionId);
    }

    public String getPlayerId(String sessionId) {
        return sessionToPlayer.get(sessionId);
    }

    public void sendToSession(String sessionId, String message) {
        sendFrame(sessionId, new OutboundFrame.TextFrame(message));
    }

    public void sendBinaryToSession(String sessionId, byte[] bytes) {
        sendFrame(sessionId, new OutboundFrame.BinaryFrame(bytes));
    }

    public void broadcastToRoom(String roomId, String message) {
        broadcastFrameToRoom(roomId, new OutboundFrame.TextFrame(message));
    }

    public void broadcastBinaryToRoom(String roomId, byte[] bytes) {
        broadcastFrameToRoom(roomId, new OutboundFrame.BinaryFrame(bytes));
    }

    public void broadcastToRoomExcept(String roomId, String senderSessionId, String message) {
        broadcastFrameToRoomExcept(roomId, senderSessionId, new OutboundFrame.TextFrame(message));
    }

    public void broadcastBinaryToRoomExcept(String roomId, String senderSessionId, byte[] bytes) {
        broadcastFrameToRoomExcept(roomId, senderSessionId, new OutboundFrame.BinaryFrame(bytes));
    }

    public void broadcastExcept(String senderSessionId, String message) {
        OutboundFrame frame = new OutboundFrame.TextFrame(message);
        clients.forEach((sessionId, queue) -> {
            if (!sessionId.equals(senderSessionId)) {
                queue.enqueue(frame);
            }
        });
    }

    private void sendFrame(String sessionId, OutboundFrame frame) {
        BoundedOutboundQueue queue = clients.get(sessionId);
        if (queue != null) {
            queue.enqueue(frame);
        }
    }

    /** TV3 Stabilization (GW-09): OVERFLOW means dead sink; remove after broadcast. */
    private void broadcastFrameToRoom(String roomId, OutboundFrame frame) {
        Set<String> toRemove = ConcurrentHashMap.newKeySet();
        clients.forEach((sessionId, queue) -> {
            String boundRoom = sessionToRoom.get(sessionId);
            if (boundRoom != null && boundRoom.equals(roomId)) {
                if (queue.enqueue(frame) == BoundedOutboundQueue.EmitStatus.OVERFLOW) {
                    log.warn("Dead sink — scheduling cleanup: session={} room={}", sessionId, roomId);
                    toRemove.add(sessionId);
                }
            }
        });
        toRemove.forEach(this::remove);
    }

    private void broadcastFrameToRoomExcept(String roomId, String senderSessionId, OutboundFrame frame) {
        Set<String> toRemove = ConcurrentHashMap.newKeySet();
        clients.forEach((sessionId, queue) -> {
            if (sessionId.equals(senderSessionId)) return;
            String boundRoom = sessionToRoom.get(sessionId);
            if (boundRoom != null && boundRoom.equals(roomId)) {
                if (queue.enqueue(frame) == BoundedOutboundQueue.EmitStatus.OVERFLOW) {
                    log.warn("Dead sink — scheduling cleanup: session={} room={}", sessionId, roomId);
                    toRemove.add(sessionId);
                }
            }
        });
        toRemove.forEach(this::remove);
    }

    public void remove(String sessionId) {
        String roomId = sessionToRoom.remove(sessionId);
        String playerId = sessionToPlayer.remove(sessionId);
        BoundedOutboundQueue queue = clients.remove(sessionId);
        if (playerId != null && roomId != null) {
            playerRoomToSession.remove(playerId + ":" + roomId, sessionId);
        }
        if (queue != null) {
            gatewayMetrics.decrementActiveConnections();
            queue.complete();
        }
        log.info("Client disconnected: {} | Online clients: {}", sessionId, clients.size());
    }

    public int getOnlineCount() {
        return clients.size();
    }

    public BoundedOutboundQueue getQueueForSession(String sessionId) {
        return clients.get(sessionId);
    }

    public GatewayMetrics getGatewayMetrics() {
        return gatewayMetrics;
    }
}
