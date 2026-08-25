package com.drawgame.realtime_gateway.connection;

public sealed interface OutboundFrame permits OutboundFrame.TextFrame, OutboundFrame.BinaryFrame {

    record TextFrame(String text) implements OutboundFrame {
    }

    record BinaryFrame(byte[] bytes) implements OutboundFrame {
    }
}
