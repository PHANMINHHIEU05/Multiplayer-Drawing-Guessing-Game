package com.drawgame.realtime_gateway.drawing.protocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

class BinaryDrawingCodecTest {

    private static final double TOLERANCE = 1.0 / 65535.0;

    private BinaryDrawingEncoder encoder;
    private BinaryDrawingDecoder decoder;

    @BeforeEach
    void setUp() {
        encoder = new BinaryDrawingEncoder();
        decoder = new BinaryDrawingDecoder();
    }

    @Test
    void roundTrip_drawStart() {
        UUID strokeId = UUID.randomUUID();
        DrawStartMessage original = new DrawStartMessage(1, 2, strokeId, 0.315, 0.527, 12, 34, 56, 4);

        byte[] encoded = encoder.encode(original);
        DrawingMessage decodedMsg = decoder.decode(encoded);

        assertThat(decodedMsg).isInstanceOf(DrawStartMessage.class);
        DrawStartMessage decoded = (DrawStartMessage) decodedMsg;

        assertThat(decoded.version()).isEqualTo(original.version());
        assertThat(decoded.opcode()).isEqualTo(DrawingOpcode.DRAW_START);
        assertThat(decoded.round()).isEqualTo(original.round());
        assertThat(decoded.strokeId()).isEqualTo(original.strokeId());
        assertThat(decoded.x()).isCloseTo(original.x(), offset(TOLERANCE));
        assertThat(decoded.y()).isCloseTo(original.y(), offset(TOLERANCE));
        assertThat(decoded.red()).isEqualTo(original.red());
        assertThat(decoded.green()).isEqualTo(original.green());
        assertThat(decoded.blue()).isEqualTo(original.blue());
        assertThat(decoded.width()).isEqualTo(original.width());
    }

    @Test
    void roundTrip_drawBatch() {
        UUID strokeId = UUID.randomUUID();
        List<DrawingPoint> points = List.of(
                new DrawingPoint(0.1, 0.2),
                new DrawingPoint(0.3, 0.4),
                new DrawingPoint(0.5, 0.6),
                new DrawingPoint(1.0, 0.0)
        );
        DrawBatchMessage original = new DrawBatchMessage(1, 2, strokeId, 100L, points);

        byte[] encoded = encoder.encode(original);
        DrawingMessage decodedMsg = decoder.decode(encoded);

        assertThat(decodedMsg).isInstanceOf(DrawBatchMessage.class);
        DrawBatchMessage decoded = (DrawBatchMessage) decodedMsg;

        assertThat(decoded.version()).isEqualTo(original.version());
        assertThat(decoded.opcode()).isEqualTo(DrawingOpcode.DRAW_BATCH);
        assertThat(decoded.round()).isEqualTo(original.round());
        assertThat(decoded.strokeId()).isEqualTo(original.strokeId());
        assertThat(decoded.seqStart()).isEqualTo(original.seqStart());

        assertThat(decoded.points()).hasSize(original.points().size());
        for (int i = 0; i < points.size(); i++) {
            assertThat(decoded.points().get(i).x()).isCloseTo(original.points().get(i).x(), offset(TOLERANCE));
            assertThat(decoded.points().get(i).y()).isCloseTo(original.points().get(i).y(), offset(TOLERANCE));
        }
    }

    @Test
    void roundTrip_drawEnd() {
        UUID strokeId = UUID.randomUUID();
        DrawEndMessage original = new DrawEndMessage(1, 3, strokeId);

        byte[] encoded = encoder.encode(original);
        DrawingMessage decodedMsg = decoder.decode(encoded);

        assertThat(decodedMsg).isInstanceOf(DrawEndMessage.class);
        DrawEndMessage decoded = (DrawEndMessage) decodedMsg;

        assertThat(decoded.version()).isEqualTo(original.version());
        assertThat(decoded.opcode()).isEqualTo(DrawingOpcode.DRAW_END);
        assertThat(decoded.round()).isEqualTo(original.round());
        assertThat(decoded.strokeId()).isEqualTo(original.strokeId());
    }

    @Test
    void roundTrip_clearCanvas() {
        ClearCanvasMessage original = new ClearCanvasMessage(1, 42);

        byte[] encoded = encoder.encode(original);
        DrawingMessage decodedMsg = decoder.decode(encoded);

        assertThat(decodedMsg).isInstanceOf(ClearCanvasMessage.class);
        ClearCanvasMessage decoded = (ClearCanvasMessage) decodedMsg;

        assertThat(decoded.version()).isEqualTo(original.version());
        assertThat(decoded.opcode()).isEqualTo(DrawingOpcode.CLEAR_CANVAS);
        assertThat(decoded.round()).isEqualTo(original.round());
    }

    @Test
    void coordinateBoundary_validAndInvalid() {
        // Test boundaries 0.0, 0.5, 1.0
        double[] testValues = {0.0, 0.5, 1.0};
        for (double val : testValues) {
            int encoded = DrawingProtocol.encodeCoordinate(val);
            double decoded = DrawingProtocol.decodeCoordinate(encoded);
            assertThat(decoded).isCloseTo(val, offset(TOLERANCE));
        }

        // Test invalid coordinates
        assertThatThrownBy(() -> DrawingProtocol.encodeCoordinate(-0.001))
                .isInstanceOf(DrawingProtocolException.class);

        assertThatThrownBy(() -> DrawingProtocol.encodeCoordinate(1.001))
                .isInstanceOf(DrawingProtocolException.class);

        assertThatThrownBy(() -> DrawingProtocol.encodeCoordinate(Double.NaN))
                .isInstanceOf(DrawingProtocolException.class);

        assertThatThrownBy(() -> DrawingProtocol.encodeCoordinate(Double.POSITIVE_INFINITY))
                .isInstanceOf(DrawingProtocolException.class);

        assertThatThrownBy(() -> DrawingProtocol.encodeCoordinate(Double.NEGATIVE_INFINITY))
                .isInstanceOf(DrawingProtocolException.class);
    }

    @Test
    void uint32Sequence_rangeTesting() {
        UUID strokeId = UUID.randomUUID();
        List<DrawingPoint> points = List.of(new DrawingPoint(0.5, 0.5));

        long[] validSeqs = {0L, 2147483647L, 2147483648L, 4294967295L};
        for (long seq : validSeqs) {
            DrawBatchMessage original = new DrawBatchMessage(1, 1, strokeId, seq, points);
            byte[] bytes = encoder.encode(original);
            DrawBatchMessage decoded = (DrawBatchMessage) decoder.decode(bytes);
            assertThat(decoded.seqStart()).isEqualTo(seq);
        }

        // Test invalid sequences
        assertThatThrownBy(() -> new DrawBatchMessage(1, 1, strokeId, -1L, points))
                .isInstanceOf(DrawingProtocolException.class);

        assertThatThrownBy(() -> new DrawBatchMessage(1, 1, strokeId, 4294967296L, points))
                .isInstanceOf(DrawingProtocolException.class);
    }
}
