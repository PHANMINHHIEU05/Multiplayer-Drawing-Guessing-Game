package com.drawgame.realtime_gateway.drawing.protocol;

public enum DrawingProtocolErrorCode {
    UNSUPPORTED_VERSION,
    UNKNOWN_OPCODE,
    INVALID_LENGTH,
    INVALID_COORDINATE,
    INVALID_POINT_COUNT,
    INVALID_SEQUENCE,
    INVALID_BRUSH_WIDTH,
    INVALID_COLOR,
    INVALID_ROUND,
    MALFORMED_FRAME
}
