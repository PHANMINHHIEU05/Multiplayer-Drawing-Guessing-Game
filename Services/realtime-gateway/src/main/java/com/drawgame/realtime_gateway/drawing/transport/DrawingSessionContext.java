package com.drawgame.realtime_gateway.drawing.transport;

public record DrawingSessionContext(
        String sessionId,
        String roomId,
        String playerId
) {
}
