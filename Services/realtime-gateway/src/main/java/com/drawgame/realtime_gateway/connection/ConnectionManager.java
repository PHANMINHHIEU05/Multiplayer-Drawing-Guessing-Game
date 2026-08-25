package com.drawgame.realtime_gateway.connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectionManager {

    private static final Logger log =
            LoggerFactory.getLogger(ConnectionManager.class);

    private final Map<String, Sinks.Many<OutboundFrame>> clients =
            new ConcurrentHashMap<>();

    private final Map<String, String> sessionToRoom =
            new ConcurrentHashMap<>();

    private final Map<String, String> sessionToPlayer =
            new ConcurrentHashMap<>();

    public Flux<OutboundFrame> register(String sessionId) {
        Sinks.Many<OutboundFrame> outbound =
                Sinks.many()
                        .unicast()
                        .onBackpressureBuffer();

        clients.put(sessionId, outbound);

        log.info(
                "Client connected: {} | Online clients: {}",
                sessionId,
                clients.size()
        );

        return outbound.asFlux();
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
        clients.forEach((sessionId, sink) -> {
            if (sessionId.equals(senderSessionId)) {
                return;
            }

            Sinks.EmitResult result = sink.tryEmitNext(frame);
            if (result.isFailure()) {
                log.warn("Cannot send message to session {}. Result: {}", sessionId, result);
            }
        });
    }

    private void sendFrame(String sessionId, OutboundFrame frame) {
        Sinks.Many<OutboundFrame> sink = clients.get(sessionId);
        if (sink != null) {
            Sinks.EmitResult result = sink.tryEmitNext(frame);
            if (result.isFailure()) {
                log.warn("Cannot send frame to session {}. Result: {}", sessionId, result);
            }
        }
    }

    private void broadcastFrameToRoom(String roomId, OutboundFrame frame) {
        clients.forEach((sessionId, sink) -> {
            String boundRoom = sessionToRoom.get(sessionId);
            if (boundRoom != null && boundRoom.equals(roomId)) {
                Sinks.EmitResult result = sink.tryEmitNext(frame);
                if (result.isFailure()) {
                    log.warn("Cannot send frame to session {}. Result: {}", sessionId, result);
                }
            }
        });
    }

    private void broadcastFrameToRoomExcept(String roomId, String senderSessionId, OutboundFrame frame) {
        clients.forEach((sessionId, sink) -> {
            if (sessionId.equals(senderSessionId)) {
                return;
            }
            String boundRoom = sessionToRoom.get(sessionId);
            if (boundRoom != null && boundRoom.equals(roomId)) {
                Sinks.EmitResult result = sink.tryEmitNext(frame);
                if (result.isFailure()) {
                    log.warn("Cannot send frame to session {}. Result: {}", sessionId, result);
                }
            }
        });
    }

    public void remove(String sessionId) {
        sessionToRoom.remove(sessionId);
        sessionToPlayer.remove(sessionId);
        Sinks.Many<OutboundFrame> sink = clients.remove(sessionId);

        if (sink != null) {
            sink.tryEmitComplete();
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
}