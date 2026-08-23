package com.drawgame.realtime_gateway.drawing.protocol;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DrawBatchMessage(
        int version,
        int round,
        UUID strokeId,
        long seqStart,
        List<DrawingPoint> points
) implements DrawingMessage {

    public DrawBatchMessage {
        if (version != DrawingProtocol.VERSION) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.UNSUPPORTED_VERSION,
                    "Unsupported drawing protocol version: " + version
            );
        }
        DrawingProtocol.validateRound(round);
        Objects.requireNonNull(strokeId, "strokeId must not be null");
        DrawingProtocol.validateSequenceStart(seqStart);
        Objects.requireNonNull(points, "points must not be null");
        if (points.isEmpty()) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_POINT_COUNT,
                    "points list must not be empty"
            );
        }
        if (points.size() > DrawingProtocol.MAX_POINTS_PER_BATCH) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_POINT_COUNT,
                    "points list size (" + points.size() + ") exceeds MAX_POINTS_PER_BATCH (" + DrawingProtocol.MAX_POINTS_PER_BATCH + ")"
            );
        }
        points = List.copyOf(points);
    }

    @Override
    public DrawingOpcode opcode() {
        return DrawingOpcode.DRAW_BATCH;
    }
}
