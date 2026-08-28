package com.drawgame.realtime_gateway.connection;

public sealed interface OutboundFrame permits OutboundFrame.TextFrame, OutboundFrame.BinaryFrame {

    boolean isDroppable();

    record TextFrame(String text) implements OutboundFrame {
        @Override
        public boolean isDroppable() {
            if (text == null) return false;
            // High frequency drawing batches can be dropped under high backpressure
            return text.contains("\"DRAW_BATCH\"") || text.contains("\"DRAW_BATCH_EVENT\"") || text.contains("\"DRAW_POINT\"");
        }
    }

    record BinaryFrame(byte[] bytes) implements OutboundFrame {
        @Override
        public boolean isDroppable() {
            if (bytes == null || bytes.length < 2) return false;
            // Opcode byte at index 1: 0x02 is DRAW_BATCH (lossy under backpressure)
            // 0x01 (DRAW_START), 0x03 (DRAW_END), 0x04 (CLEAR_CANVAS) are critical
            return bytes[1] == 0x02;
        }
    }
}
