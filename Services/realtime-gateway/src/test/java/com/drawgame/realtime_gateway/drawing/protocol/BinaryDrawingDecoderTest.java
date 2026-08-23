package com.drawgame.realtime_gateway.drawing.protocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinaryDrawingDecoderTest {

    private BinaryDrawingEncoder encoder;
    private BinaryDrawingDecoder decoder;

    @BeforeEach
    void setUp() {
        encoder = new BinaryDrawingEncoder();
        decoder = new BinaryDrawingDecoder();
    }

    @Test
    void decode_nullInput_throwsException() {
        assertThatThrownBy(() -> decoder.decode((byte[]) null))
                .isInstanceOf(DrawingProtocolException.class);

        assertThatThrownBy(() -> decoder.decode((ByteBuffer) null))
                .isInstanceOf(DrawingProtocolException.class);
    }

    @Test
    void decode_truncatedFrame_throwsDrawingProtocolException() {
        byte[] frame = new byte[]{1, 1, 0}; // 3 bytes, less than header size (4)
        assertThatThrownBy(() -> decoder.decode(frame))
                .isInstanceOf(DrawingProtocolException.class)
                .hasMessageContaining("less than minimum header size");

        // Truncated DRAW_START frame (10 bytes instead of 28)
        byte[] truncatedDrawStart = new byte[10];
        truncatedDrawStart[0] = 1; // version
        truncatedDrawStart[1] = 1; // DRAW_START
        truncatedDrawStart[2] = 0; // round high
        truncatedDrawStart[3] = 1; // round low
        assertThatThrownBy(() -> decoder.decode(truncatedDrawStart))
                .isInstanceOf(DrawingProtocolException.class);
    }

    @Test
    void decode_unsupportedVersion_throwsDrawingProtocolException() {
        byte[] frame = new byte[]{99, 4, 0, 1}; // version 99
        assertThatThrownBy(() -> decoder.decode(frame))
                .isInstanceOf(DrawingProtocolException.class)
                .hasMessageContaining("Unsupported drawing protocol version: 99");
    }

    @Test
    void decode_unknownOpcode_throwsDrawingProtocolException() {
        byte[] frame = new byte[]{1, (byte) 0x7F, 0, 1}; // opcode 0x7F
        assertThatThrownBy(() -> decoder.decode(frame))
                .isInstanceOf(DrawingProtocolException.class)
                .hasMessageContaining("Unknown drawing opcode: 0x7f");
    }

    @Test
    void decode_trailingData_throwsDrawingProtocolException() {
        ClearCanvasMessage validMsg = new ClearCanvasMessage(1, 1);
        byte[] validBytes = encoder.encode(validMsg);

        byte[] frameWithTrailing = new byte[validBytes.length + 2];
        System.arraycopy(validBytes, 0, frameWithTrailing, 0, validBytes.length);
        frameWithTrailing[validBytes.length] = 0x12;
        frameWithTrailing[validBytes.length + 1] = 0x34;

        assertThatThrownBy(() -> decoder.decode(frameWithTrailing))
                .isInstanceOf(DrawingProtocolException.class);
    }

    @Test
    void decode_drawBatch_zeroPointCount_throwsDrawingProtocolException() {
        // Construct DRAW_BATCH frame with pointCount = 0
        ByteBuffer bb = ByteBuffer.allocate(DrawingProtocol.DRAW_BATCH_HEADER_SIZE);
        bb.order(DrawingProtocol.BYTE_ORDER);
        bb.put((byte) 1); // version
        bb.put((byte) 2); // DRAW_BATCH
        bb.putShort((short) 1); // round
        bb.putInt(10); // seqStart
        bb.putShort((short) 0); // pointCount = 0
        UUID strokeId = UUID.randomUUID();
        bb.putLong(strokeId.getMostSignificantBits());
        bb.putLong(strokeId.getLeastSignificantBits());

        assertThatThrownBy(() -> decoder.decode(bb.array()))
                .isInstanceOf(DrawingProtocolException.class)
                .hasMessageContaining("pointCount must be > 0");
    }

    @Test
    void decode_drawBatch_exceedsMaxPoints_throwsDrawingProtocolException() {
        ByteBuffer bb = ByteBuffer.allocate(DrawingProtocol.DRAW_BATCH_HEADER_SIZE);
        bb.order(DrawingProtocol.BYTE_ORDER);
        bb.put((byte) 1);
        bb.put((byte) 2);
        bb.putShort((short) 1);
        bb.putInt(10);
        bb.putShort((short) 300); // 300 > 256
        UUID strokeId = UUID.randomUUID();
        bb.putLong(strokeId.getMostSignificantBits());
        bb.putLong(strokeId.getLeastSignificantBits());

        assertThatThrownBy(() -> decoder.decode(bb.array()))
                .isInstanceOf(DrawingProtocolException.class)
                .hasMessageContaining("exceeds MAX_POINTS_PER_BATCH");
    }

    @Test
    void decode_drawBatch_lengthMismatch_throwsDrawingProtocolException() {
        // pointCount = 2, but provide bytes for only 1 point
        int pointCount = 2;
        int expectedLen = DrawingProtocol.DRAW_BATCH_HEADER_SIZE + pointCount * DrawingProtocol.POINT_SIZE;
        int actualLen = DrawingProtocol.DRAW_BATCH_HEADER_SIZE + 1 * DrawingProtocol.POINT_SIZE;

        ByteBuffer bb = ByteBuffer.allocate(actualLen);
        bb.order(DrawingProtocol.BYTE_ORDER);
        bb.put((byte) 1);
        bb.put((byte) 2);
        bb.putShort((short) 1);
        bb.putInt(10);
        bb.putShort((short) pointCount); // pointCount = 2
        UUID strokeId = UUID.randomUUID();
        bb.putLong(strokeId.getMostSignificantBits());
        bb.putLong(strokeId.getLeastSignificantBits());
        bb.putShort((short) 100);
        bb.putShort((short) 200);

        assertThatThrownBy(() -> decoder.decode(bb.array()))
                .isInstanceOf(DrawingProtocolException.class)
                .hasMessageContaining("frame size mismatch");
    }
}
