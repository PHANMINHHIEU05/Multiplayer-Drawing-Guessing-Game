package com.drawgame.realtime_gateway.drawing.protocol;

import java.nio.ByteOrder;

public final class DrawingProtocol {

    public static final int VERSION = 1;
    public static final int MAX_POINTS_PER_BATCH = 256;
    public static final int MIN_BRUSH_WIDTH = 1;
    public static final int MAX_BRUSH_WIDTH = 64;
    public static final int MIN_COLOR_VALUE = 0;
    public static final int MAX_COLOR_VALUE = 255;
    public static final int MIN_ROUND_VALUE = 0;
    public static final int MAX_ROUND_VALUE = 65535;
    public static final long MIN_SEQ_VALUE = 0L;
    public static final long MAX_SEQ_VALUE = 4294967295L; // 2^32 - 1

    public static final int COMMON_HEADER_SIZE = 4;
    public static final int UUID_SIZE = 16;
    public static final int DRAW_START_FRAME_SIZE = 28;
    public static final int DRAW_BATCH_HEADER_SIZE = 26;
    public static final int POINT_SIZE = 4;
    public static final int DRAW_END_FRAME_SIZE = 20;
    public static final int CLEAR_CANVAS_FRAME_SIZE = 4;

    public static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
    public static final double QUANTIZATION_FACTOR = 65535.0;

    private DrawingProtocol() {
    }

    public static int encodeCoordinate(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 1.0) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_COORDINATE,
                    "Coordinate value must be between 0.0 and 1.0 (inclusive), got: " + value
            );
        }
        return (int) Math.round(value * QUANTIZATION_FACTOR);
    }

    public static double decodeCoordinate(int encodedValue) {
        if (encodedValue < 0 || encodedValue > 65535) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_COORDINATE,
                    "Encoded coordinate value must be between 0 and 65535, got: " + encodedValue
            );
        }
        return encodedValue / QUANTIZATION_FACTOR;
    }

    public static void validateRound(int round) {
        if (round < MIN_ROUND_VALUE || round > MAX_ROUND_VALUE) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_ROUND,
                    "Round must be between " + MIN_ROUND_VALUE + " and " + MAX_ROUND_VALUE + ", got: " + round
            );
        }
    }

    public static void validateColor(int colorComponent, String name) {
        if (colorComponent < MIN_COLOR_VALUE || colorComponent > MAX_COLOR_VALUE) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_COLOR,
                    name + " color component must be between 0 and 255, got: " + colorComponent
            );
        }
    }

    public static void validateBrushWidth(int width) {
        if (width < MIN_BRUSH_WIDTH || width > MAX_BRUSH_WIDTH) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_BRUSH_WIDTH,
                    "Brush width must be between " + MIN_BRUSH_WIDTH + " and " + MAX_BRUSH_WIDTH + ", got: " + width
            );
        }
    }

    public static void validateSequenceStart(long seqStart) {
        if (seqStart < MIN_SEQ_VALUE || seqStart > MAX_SEQ_VALUE) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_SEQUENCE,
                    "Sequence start must be between " + MIN_SEQ_VALUE + " and " + MAX_SEQ_VALUE + ", got: " + seqStart
            );
        }
    }
}
