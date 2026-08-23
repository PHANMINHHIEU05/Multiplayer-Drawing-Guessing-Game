package com.drawgame.realtime_gateway.drawing.protocol;

public record ClearCanvasMessage(
        int version,
        int round
) implements DrawingMessage {

    public ClearCanvasMessage {
        if (version != DrawingProtocol.VERSION) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.UNSUPPORTED_VERSION,
                    "Unsupported drawing protocol version: " + version
            );
        }
        DrawingProtocol.validateRound(round);
    }

    @Override
    public DrawingOpcode opcode() {
        return DrawingOpcode.CLEAR_CANVAS;
    }
}
