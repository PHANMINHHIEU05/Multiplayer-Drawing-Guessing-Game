package com.drawgame.realtime_gateway.drawing.protocol;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BinaryDrawingDecoder {

    public DrawingMessage decode(byte[] data) {
        if (data == null) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.MALFORMED_FRAME,
                    "Byte array data cannot be null"
            );
        }
        return decode(ByteBuffer.wrap(data));
    }

    public DrawingMessage decode(ByteBuffer inputBuffer) {
        if (inputBuffer == null) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.MALFORMED_FRAME,
                    "ByteBuffer input cannot be null"
            );
        }

        try {
            ByteBuffer buffer = inputBuffer.duplicate();
            buffer.order(DrawingProtocol.BYTE_ORDER);

            int totalLength = buffer.remaining();

            // 1. Minimum frame validation (Common Header = 4 bytes)
            if (totalLength < DrawingProtocol.COMMON_HEADER_SIZE) {
                throw new DrawingProtocolException(
                        DrawingProtocolErrorCode.INVALID_LENGTH,
                        "Frame size (" + totalLength + " bytes) is less than minimum header size (" + DrawingProtocol.COMMON_HEADER_SIZE + " bytes)"
                );
            }

            // 2. Read version
            int version = Byte.toUnsignedInt(buffer.get());
            if (version != DrawingProtocol.VERSION) {
                throw new DrawingProtocolException(
                        DrawingProtocolErrorCode.UNSUPPORTED_VERSION,
                        "Unsupported drawing protocol version: " + version
                );
            }

            // 3. Read opcode
            int opcodeCode = Byte.toUnsignedInt(buffer.get());
            DrawingOpcode opcode = DrawingOpcode.fromCode(opcodeCode);

            // 4. Read round
            int round = Short.toUnsignedInt(buffer.getShort());
            DrawingProtocol.validateRound(round);

            // 5. Dispatch based on opcode
            DrawingMessage message = switch (opcode) {
                case DRAW_START -> decodeDrawStart(buffer, version, round, totalLength);
                case DRAW_BATCH -> decodeDrawBatch(buffer, version, round, totalLength);
                case DRAW_END -> decodeDrawEnd(buffer, version, round, totalLength);
                case CLEAR_CANVAS -> decodeClearCanvas(buffer, version, round, totalLength);
            };

            // 6. Trailing bytes check
            if (buffer.hasRemaining()) {
                throw new DrawingProtocolException(
                        DrawingProtocolErrorCode.INVALID_LENGTH,
                        "Trailing bytes detected in frame: " + buffer.remaining() + " extra bytes"
                );
            }

            return message;

        } catch (DrawingProtocolException e) {
            throw e;
        } catch (Exception e) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.MALFORMED_FRAME,
                    "Failed to decode binary drawing frame: " + e.getMessage()
            );
        }
    }

    private DrawStartMessage decodeDrawStart(ByteBuffer buffer, int version, int round, int totalLength) {
        if (totalLength != DrawingProtocol.DRAW_START_FRAME_SIZE) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_LENGTH,
                    "DRAW_START frame size must be exactly " + DrawingProtocol.DRAW_START_FRAME_SIZE + " bytes, got: " + totalLength
            );
        }
        UUID strokeId = readUUID(buffer);
        double x = DrawingProtocol.decodeCoordinate(Short.toUnsignedInt(buffer.getShort()));
        double y = DrawingProtocol.decodeCoordinate(Short.toUnsignedInt(buffer.getShort()));
        int red = Byte.toUnsignedInt(buffer.get());
        int green = Byte.toUnsignedInt(buffer.get());
        int blue = Byte.toUnsignedInt(buffer.get());
        int width = Byte.toUnsignedInt(buffer.get());

        return new DrawStartMessage(version, round, strokeId, x, y, red, green, blue, width);
    }

    private DrawBatchMessage decodeDrawBatch(ByteBuffer buffer, int version, int round, int totalLength) {
        if (totalLength < DrawingProtocol.DRAW_BATCH_HEADER_SIZE) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_LENGTH,
                    "DRAW_BATCH frame size (" + totalLength + " bytes) is less than batch header size (" + DrawingProtocol.DRAW_BATCH_HEADER_SIZE + " bytes)"
            );
        }
        long seqStart = Integer.toUnsignedLong(buffer.getInt());
        int pointCount = Short.toUnsignedInt(buffer.getShort());

        if (pointCount <= 0 || pointCount > DrawingProtocol.MAX_POINTS_PER_BATCH) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_POINT_COUNT,
                    "DRAW_BATCH pointCount must be > 0 and <= " + DrawingProtocol.MAX_POINTS_PER_BATCH + " (exceeds MAX_POINTS_PER_BATCH), got: " + pointCount
            );
        }

        int expectedLength = DrawingProtocol.DRAW_BATCH_HEADER_SIZE + pointCount * DrawingProtocol.POINT_SIZE;
        if (totalLength != expectedLength) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_LENGTH,
                    "DRAW_BATCH frame size mismatch: expected " + expectedLength + " bytes for " + pointCount + " points, got: " + totalLength
            );
        }

        UUID strokeId = readUUID(buffer);

        List<DrawingPoint> points = new ArrayList<>(pointCount);
        for (int i = 0; i < pointCount; i++) {
            double px = DrawingProtocol.decodeCoordinate(Short.toUnsignedInt(buffer.getShort()));
            double py = DrawingProtocol.decodeCoordinate(Short.toUnsignedInt(buffer.getShort()));
            points.add(new DrawingPoint(px, py));
        }

        return new DrawBatchMessage(version, round, strokeId, seqStart, points);
    }

    private DrawEndMessage decodeDrawEnd(ByteBuffer buffer, int version, int round, int totalLength) {
        if (totalLength != DrawingProtocol.DRAW_END_FRAME_SIZE) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_LENGTH,
                    "DRAW_END frame size must be exactly " + DrawingProtocol.DRAW_END_FRAME_SIZE + " bytes, got: " + totalLength
            );
        }
        UUID strokeId = readUUID(buffer);
        return new DrawEndMessage(version, round, strokeId);
    }

    private ClearCanvasMessage decodeClearCanvas(ByteBuffer buffer, int version, int round, int totalLength) {
        if (totalLength != DrawingProtocol.CLEAR_CANVAS_FRAME_SIZE) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.INVALID_LENGTH,
                    "CLEAR_CANVAS frame size must be exactly " + DrawingProtocol.CLEAR_CANVAS_FRAME_SIZE + " bytes, got: " + totalLength
            );
        }
        return new ClearCanvasMessage(version, round);
    }

    private UUID readUUID(ByteBuffer buffer) {
        long msb = buffer.getLong();
        long lsb = buffer.getLong();
        return new UUID(msb, lsb);
    }
}
