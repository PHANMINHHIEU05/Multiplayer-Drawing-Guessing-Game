package com.drawgame.realtime_gateway.drawing.protocol;

public enum DrawingOpcode {
    DRAW_START(0x01),
    DRAW_BATCH(0x02),
    DRAW_END(0x03),
    CLEAR_CANVAS(0x04);

    private final int code;

    DrawingOpcode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static DrawingOpcode fromCode(int code) {
        for (DrawingOpcode opcode : values()) {
            if (opcode.code == code) {
                return opcode;
            }
        }
        throw new DrawingProtocolException(
                DrawingProtocolErrorCode.UNKNOWN_OPCODE,
                "Unknown drawing opcode: 0x" + Integer.toHexString(code)
        );
    }
}
