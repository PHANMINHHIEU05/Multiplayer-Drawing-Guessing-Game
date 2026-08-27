package com.drawgame.realtime_gateway.drawing.routing;

import com.drawgame.realtime_gateway.connection.ConnectionManager;
import com.drawgame.realtime_gateway.drawing.protocol.DrawingMessage;
import com.drawgame.realtime_gateway.drawing.transport.DrawingWebSocketEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Broadcasts drawing events to local WebSocket connections within the same room.
 *
 * <p>Sender echo policy: the sender is excluded from local broadcast because the
 * drawer already renders locally on the Canvas (frontend). Sending back to the
 * sender would cause double-render.
 */
@Component
public class DrawingBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(DrawingBroadcaster.class);

    private final ConnectionManager connectionManager;
    private final DrawingWebSocketEncoder encoder;

    public DrawingBroadcaster(ConnectionManager connectionManager, DrawingWebSocketEncoder encoder) {
        this.connectionManager = connectionManager;
        this.encoder = encoder;
    }

    /**
     * Encode and broadcast a {@link DrawingMessage} to all local connections in the room
     * except the sender.
     *
     * @param roomId          target room
     * @param senderSessionId session to exclude (the drawer)
     * @param message         the drawing message to encode and send
     */
    public void broadcastToRoomExcept(String roomId, String senderSessionId, DrawingMessage message) {
        byte[] bytes = encoder.encodeToBytes(message);
        broadcastBytesToRoomExcept(roomId, senderSessionId, bytes);
    }

    /**
     * Broadcast raw binary drawing bytes to all local connections in the room except the given session.
     *
     * <p>Use this variant when bytes have already been encoded (e.g., arriving from Redis subscriber)
     * to avoid re-encoding overhead.
     *
     * @param roomId          target room
     * @param senderSessionId session to exclude, or {@code null} to broadcast to everyone in room
     * @param bytes           already-encoded binary drawing payload
     */
    public void broadcastBytesToRoomExcept(String roomId, String senderSessionId, byte[] bytes) {
        if (senderSessionId != null) {
            connectionManager.broadcastBinaryToRoomExcept(roomId, senderSessionId, bytes);
        } else {
            connectionManager.broadcastBinaryToRoom(roomId, bytes);
        }
        log.trace("Drawing broadcast: room={} senderExcluded={} bytes={}", roomId, senderSessionId, bytes.length);
    }
}
