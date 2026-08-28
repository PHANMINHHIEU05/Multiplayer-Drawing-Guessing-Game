package com.drawgame.realtime_gateway.connection;

import com.drawgame.realtime_gateway.metrics.GatewayMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active WebSocket client sessions and outbound bounded queues.
 * Instrument with {@link GatewayMetrics} for telemetry and observability (TV4).
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

        log.info(
                "Client connected: {} | Online clients: {} | QueueCapacity: {}",
                sessionId,
                clients.size(),
                maxCapacity
        );

        return queue.asFlux();
    }

    public void bindSession(String sessionId, String roomId, String playerId) {
        if (roomId != null) {
            sessionToRoom.put(sessionId, roomId);
        }
        if (playerId != null) {
            sessionToPlayer.put(sessionId, playerId);
        }
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

    public void broadcastExcept(
            String senderSessionId,
            String message
    ) {
        OutboundFrame frame = new OutboundFrame.TextFrame(message);
        clients.forEach((sessionId, queue) -> {
            if (sessionId.equals(senderSessionId)) {
                return;
            }
            queue.enqueue(frame);
        });
    }

    private void sendFrame(String sessionId, OutboundFrame frame) {
        BoundedOutboundQueue queue = clients.get(sessionId);
        if (queue != null) {
            queue.enqueue(frame);
        }
    }

    private void broadcastFrameToRoom(String roomId, OutboundFrame frame) {
        clients.forEach((sessionId, queue) -> {
            String boundRoom = sessionToRoom.get(sessionId);
            if (boundRoom != null && boundRoom.equals(roomId)) {
                queue.enqueue(frame);
            }
        });
    }

    private void broadcastFrameToRoomExcept(String roomId, String senderSessionId, OutboundFrame frame) {
        clients.forEach((sessionId, queue) -> {
            if (sessionId.equals(senderSessionId)) {
                return;
            }
            String boundRoom = sessionToRoom.get(sessionId);
            if (boundRoom != null && boundRoom.equals(roomId)) {
                queue.enqueue(frame);
            }
        });
    }

    public void remove(String sessionId) {
        sessionToRoom.remove(sessionId);
        sessionToPlayer.remove(sessionId);
        BoundedOutboundQueue queue = clients.remove(sessionId);

        if (queue != null) {
            gatewayMetrics.decrementActiveConnections();
            queue.complete();
        }

        log.info(
                "Client disconnected: {} | Online clients: {}",
                sessionId,
                clients.size()
        );
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