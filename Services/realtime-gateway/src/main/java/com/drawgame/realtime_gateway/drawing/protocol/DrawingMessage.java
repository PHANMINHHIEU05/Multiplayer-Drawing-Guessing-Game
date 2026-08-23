package com.drawgame.realtime_gateway.drawing.protocol;

public sealed interface DrawingMessage
        permits DrawStartMessage,
                DrawBatchMessage,
                DrawEndMessage,
                ClearCanvasMessage {

    int version();

    DrawingOpcode opcode();

    int round();
}
