package com.drawgame.realtime_gateway.drawing.protocol;

import java.util.Objects;
import java.util.UUID;

public record DrawEndMessage(
        int version,
        int round,
        UUID strokeId
) implements DrawingMessage {

    public DrawEndMessage {
        if (version != DrawingProtocol.VERSION) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.UNSUPPORTED_VERSION,
                    "Unsupported drawing protocol version: " + version
            );
        }
        DrawingProtocol.validateRound(round);
        Objects.requireNonNull(strokeId, "strokeId must not be null");
    }

    @Override
    public DrawingOpcode opcode() {
        return DrawingOpcode.DRAW_END;
    }
}
