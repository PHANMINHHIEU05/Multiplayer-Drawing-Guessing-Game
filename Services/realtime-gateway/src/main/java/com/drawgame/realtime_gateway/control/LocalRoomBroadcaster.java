package com.drawgame.realtime_gateway.control;

import com.drawgame.realtime_gateway.connection.ConnectionManager;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over {@link ConnectionManager} room-scoped text broadcast.
 *
 * <p>Exists so {@link ControlEventRouter} depends on a narrow control-layer
 * abstraction rather than the full connection manager, and to give remote
 * (Redis-received) control events a single local fanout entry point
 * (also used by {@code ControlRedisSubscriber}).
 */
@Component
public class LocalRoomBroadcaster {

    private final ConnectionManager connectionManager;

    public LocalRoomBroadcaster(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /** Broadcast a text control payload to every local session bound to the room. */
    public void broadcastToRoom(String roomId, String payloadJson) {
        connectionManager.broadcastToRoom(roomId, payloadJson);
    }

    /** Broadcast a text control payload to every local session in the room except the sender. */
    public void broadcastToRoomExcept(String roomId, String payloadJson, String senderSessionId) {
        connectionManager.broadcastToRoomExcept(roomId, senderSessionId, payloadJson);
    }
}
