package com.drawgame.realtime_gateway.drawing.protocol;

import java.util.Objects;
import java.util.UUID;

public record DrawStartMessage(
        int version,
        int round,
        UUID strokeId,
        double x,
        double y,
        int red,
        int green,
        int blue,
        int width
) implements DrawingMessage {

    public DrawStartMessage {
        if (version != DrawingProtocol.VERSION) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.UNSUPPORTED_VERSION,
                    "Unsupported drawing protocol version: " + version
            );
        }
        DrawingProtocol.validateRound(round);
        Objects.requireNonNull(strokeId, "strokeId must not be null");
        if (Double.isNaN(x) || Double.isInfinite(x) || x < 0.0 || x > 1.0) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_COORDINATE,
                    "x must be between 0.0 and 1.0, got: " + x
            );
        }
        if (Double.isNaN(y) || Double.isInfinite(y) || y < 0.0 || y > 1.0) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_COORDINATE,
                    "y must be between 0.0 and 1.0, got: " + y
            );
        }
        DrawingProtocol.validateColor(red, "Red");
        DrawingProtocol.validateColor(green, "Green");
        DrawingProtocol.validateColor(blue, "Blue");
        DrawingProtocol.validateBrushWidth(width);
    }

    @Override
    public DrawingOpcode opcode() {
        return DrawingOpcode.DRAW_START;
    }
}
