package com.drawgame.realtime_gateway.drawing.protocol;

import java.nio.ByteBuffer;

public class BinaryDrawingEncoder {

    public byte[] encode(DrawingMessage message) {
        ByteBuffer buffer = encodeToByteBuffer(message);
        return buffer.array();
    }

    public ByteBuffer encodeToByteBuffer(DrawingMessage message) {
        if (message == null) {
            throw new DrawingProtocolException(
                    DrawingProtocolErrorCode.MALFORMED_FRAME,
                    "Message to encode cannot be null"
            );
        }

        int frameSize = calculateFrameSize(message);
        ByteBuffer buffer = ByteBuffer.allocate(frameSize);
        buffer.order(DrawingProtocol.BYTE_ORDER);

        // Common Header
        buffer.put((byte) message.version());
        buffer.put((byte) message.opcode().code());
        buffer.putShort((short) message.round());

        // Opcode-specific payload
        switch (message) {
            case DrawStartMessage msg -> encodeDrawStartPayload(msg, buffer);
            case DrawBatchMessage msg -> encodeDrawBatchPayload(msg, buffer);
            case DrawEndMessage msg -> encodeDrawEndPayload(msg, buffer);
            case ClearCanvasMessage msg -> {} // No additional payload
        }

        buffer.flip();
        return buffer;
    }

    private int calculateFrameSize(DrawingMessage message) {
        return switch (message) {
            case DrawStartMessage msg -> DrawingProtocol.DRAW_START_FRAME_SIZE;
            case DrawBatchMessage msg -> DrawingProtocol.DRAW_BATCH_HEADER_SIZE + msg.points().size() * DrawingProtocol.POINT_SIZE;
            case DrawEndMessage msg -> DrawingProtocol.DRAW_END_FRAME_SIZE;
            case ClearCanvasMessage msg -> DrawingProtocol.CLEAR_CANVAS_FRAME_SIZE;
        };
    }

    private void encodeDrawStartPayload(DrawStartMessage msg, ByteBuffer buffer) {
        buffer.putLong(msg.strokeId().getMostSignificantBits());
        buffer.putLong(msg.strokeId().getLeastSignificantBits());
        buffer.putShort((short) DrawingProtocol.encodeCoordinate(msg.x()));
        buffer.putShort((short) DrawingProtocol.encodeCoordinate(msg.y()));
        buffer.put((byte) msg.red());
        buffer.put((byte) msg.green());
        buffer.put((byte) msg.blue());
        buffer.put((byte) msg.width());
    }

    private void encodeDrawBatchPayload(DrawBatchMessage msg, ByteBuffer buffer) {
        buffer.putInt((int) msg.seqStart());
        buffer.putShort((short) msg.points().size());
        buffer.putLong(msg.strokeId().getMostSignificantBits());
        buffer.putLong(msg.strokeId().getLeastSignificantBits());
        for (DrawingPoint point : msg.points()) {
            buffer.putShort((short) DrawingProtocol.encodeCoordinate(point.x()));
            buffer.putShort((short) DrawingProtocol.encodeCoordinate(point.y()));
        }
    }

    private void encodeDrawEndPayload(DrawEndMessage msg, ByteBuffer buffer) {
        buffer.putLong(msg.strokeId().getMostSignificantBits());
        buffer.putLong(msg.strokeId().getLeastSignificantBits());
    }
}
