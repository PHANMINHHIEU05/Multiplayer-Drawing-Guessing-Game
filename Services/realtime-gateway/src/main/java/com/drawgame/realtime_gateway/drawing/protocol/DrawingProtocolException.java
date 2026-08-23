package com.drawgame.realtime_gateway.drawing.protocol;

public class DrawingProtocolException extends RuntimeException {
    private final DrawingProtocolErrorCode errorCode;

    public DrawingProtocolException(String message) {
        super(message);
        this.errorCode = DrawingProtocolErrorCode.MALFORMED_FRAME;
    }

    public DrawingProtocolException(DrawingProtocolErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DrawingProtocolErrorCode getErrorCode() {
        return errorCode;
    }
}
