package com.drawgame.realtime_gateway.drawing.protocol;

public record DrawingPoint(
        double x,
        double y
) {
    public DrawingPoint {
        if (Double.isNaN(x) || Double.isInfinite(x) || x < 0.0 || x > 1.0) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_COORDINATE,
                    "Point x must be between 0.0 and 1.0, got: " + x
            );
        }
        if (Double.isNaN(y) || Double.isInfinite(y) || y < 0.0 || y > 1.0) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_COORDINATE,
                    "Point y must be between 0.0 and 1.0, got: " + y
            );
        }
    }
}
