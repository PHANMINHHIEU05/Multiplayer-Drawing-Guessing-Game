package com.drawgame.realtime_gateway.drawing.protocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BinaryDrawingEncoderTest {

    private BinaryDrawingEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new BinaryDrawingEncoder();
    }

    @Test
    void encodeDrawStart_returnsCorrectSizeAndBytes() {
        UUID strokeId = UUID.randomUUID();
        DrawStartMessage msg = new DrawStartMessage(1, 2, strokeId, 0.5, 0.5, 255, 0, 128, 10);

        byte[] bytes = encoder.encode(msg);

        assertThat(bytes).hasSize(DrawingProtocol.DRAW_START_FRAME_SIZE);
        assertThat(bytes[0]).isEqualTo((byte) 1); // version
        assertThat(bytes[1]).isEqualTo((byte) 1); // DRAW_START opcode
        assertThat(bytes[2]).isEqualTo((byte) 0); // round high
        assertThat(bytes[3]).isEqualTo((byte) 2); // round low
    }

    @Test
    void encodeDrawBatch_returnsCorrectSize() {
        UUID strokeId = UUID.randomUUID();
        List<DrawingPoint> points = List.of(
                new DrawingPoint(0.1, 0.2),
                new DrawingPoint(0.3, 0.4)
        );
        DrawBatchMessage msg = new DrawBatchMessage(1, 5, strokeId, 100L, points);

        byte[] bytes = encoder.encode(msg);

        int expectedLength = DrawingProtocol.DRAW_BATCH_HEADER_SIZE + 2 * DrawingProtocol.POINT_SIZE;
        assertThat(bytes).hasSize(expectedLength);
        assertThat(bytes[0]).isEqualTo((byte) 1); // version
        assertThat(bytes[1]).isEqualTo((byte) 2); // DRAW_BATCH opcode
    }

    @Test
    void encodeDrawEnd_returnsCorrectSize() {
        UUID strokeId = UUID.randomUUID();
        DrawEndMessage msg = new DrawEndMessage(1, 3, strokeId);

        byte[] bytes = encoder.encode(msg);

        assertThat(bytes).hasSize(DrawingProtocol.DRAW_END_FRAME_SIZE);
        assertThat(bytes[0]).isEqualTo((byte) 1); // version
        assertThat(bytes[1]).isEqualTo((byte) 3); // DRAW_END opcode
    }

    @Test
    void encodeClearCanvas_returnsCorrectSize() {
        ClearCanvasMessage msg = new ClearCanvasMessage(1, 10);

        byte[] bytes = encoder.encode(msg);

        assertThat(bytes).hasSize(DrawingProtocol.CLEAR_CANVAS_FRAME_SIZE);
        assertThat(bytes[0]).isEqualTo((byte) 1); // version
        assertThat(bytes[1]).isEqualTo((byte) 4); // CLEAR_CANVAS opcode
    }

    @Test
    void encode_nullMessage_throwsDrawingProtocolException() {
        assertThatThrownBy(() -> encoder.encode(null))
                .isInstanceOf(DrawingProtocolException.class)
                .hasMessageContaining("Message to encode cannot be null");
    }
}
