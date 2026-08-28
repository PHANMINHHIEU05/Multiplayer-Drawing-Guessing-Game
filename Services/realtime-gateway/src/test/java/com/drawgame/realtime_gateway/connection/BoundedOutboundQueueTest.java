package com.drawgame.realtime_gateway.connection;

import com.drawgame.realtime_gateway.metrics.GatewayMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class BoundedOutboundQueueTest {

    private GatewayMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new GatewayMetrics();
    }

    @Test
    void testNormalEnqueueAndConsume() {
        BoundedOutboundQueue queue = new BoundedOutboundQueue("s-1", 16, metrics);
        OutboundFrame frame1 = new OutboundFrame.TextFrame("{\"type\":\"CHAT_MESSAGE\"}");
        OutboundFrame frame2 = new OutboundFrame.BinaryFrame(new byte[]{1, 1, 0, 0});

        assertEquals(BoundedOutboundQueue.EmitStatus.EMITTED, queue.enqueue(frame1));
        assertEquals(BoundedOutboundQueue.EmitStatus.EMITTED, queue.enqueue(frame2));
        assertEquals(2, queue.getCurrentQueueSize());
        assertEquals(2, metrics.getTotalQueueSize());

        StepVerifier.create(queue.asFlux().take(2))
                .expectNext(frame1)
                .expectNext(frame2)
                .verifyComplete();

        assertEquals(0, queue.getCurrentQueueSize());
        assertEquals(0, metrics.getTotalQueueSize());
    }

    @Test
    void testDropDrawBatchWhenQueueIsFull() {
        int capacity = 16;
        BoundedOutboundQueue queue = new BoundedOutboundQueue("s-slow", capacity, metrics);

        // Fill queue to capacity with dummy control messages
        for (int i = 0; i < capacity; i++) {
            OutboundFrame frame = new OutboundFrame.TextFrame("{\"type\":\"CHAT\",\"id\":" + i + "}");
            assertEquals(BoundedOutboundQueue.EmitStatus.EMITTED, queue.enqueue(frame));
        }
        assertEquals(capacity, queue.getCurrentQueueSize());

        // Attempt to enqueue a binary DRAW_BATCH (opcode 0x02)
        byte[] binaryBatch = new byte[]{1, 2, 0, 0, 0, 0};
        OutboundFrame drawBatchFrame = new OutboundFrame.BinaryFrame(binaryBatch);
        assertTrue(drawBatchFrame.isDroppable());

        BoundedOutboundQueue.EmitStatus status = queue.enqueue(drawBatchFrame);
        assertEquals(BoundedOutboundQueue.EmitStatus.DROPPED, status);
        assertEquals(1, queue.getDroppedFramesCount());
        assertEquals(1, metrics.getDroppedDrawBatchCount());
        assertEquals(capacity, queue.getCurrentQueueSize()); // Queue did not exceed capacity
    }

    @Test
    void testDropTextDrawBatchWhenQueueIsFull() {
        int capacity = 16;
        BoundedOutboundQueue queue = new BoundedOutboundQueue("s-slow", capacity, metrics);

        for (int i = 0; i < capacity; i++) {
            queue.enqueue(new OutboundFrame.TextFrame("{\"type\":\"PING\"}"));
        }

        OutboundFrame textBatch = new OutboundFrame.TextFrame("{\"type\":\"DRAW_BATCH_EVENT\",\"points\":[]}");
        assertTrue(textBatch.isDroppable());

        BoundedOutboundQueue.EmitStatus status = queue.enqueue(textBatch);
        assertEquals(BoundedOutboundQueue.EmitStatus.DROPPED, status);
        assertEquals(1, queue.getDroppedFramesCount());
    }

    @Test
    void testControlEventsPreservedWhenQueueFull() {
        int capacity = 16;
        BoundedOutboundQueue queue = new BoundedOutboundQueue("s-slow", capacity, metrics);

        for (int i = 0; i < capacity; i++) {
            queue.enqueue(new OutboundFrame.TextFrame("{\"type\":\"MSG\"}"));
        }

        // Critical events: DRAW_START (0x01), DRAW_END (0x03), CLEAR_CANVAS (0x04), GAME_FINISHED
        OutboundFrame drawStart = new OutboundFrame.BinaryFrame(new byte[]{1, 1, 0, 0});
        OutboundFrame clearCanvas = new OutboundFrame.BinaryFrame(new byte[]{1, 4, 0, 0});
        OutboundFrame gameFinished = new OutboundFrame.TextFrame("{\"type\":\"GAME_FINISHED\"}");

        assertFalse(drawStart.isDroppable());
        assertFalse(clearCanvas.isDroppable());
        assertFalse(gameFinished.isDroppable());

        // Control events are prioritized / not dropped under drawing drop policy
        BoundedOutboundQueue.EmitStatus status1 = queue.enqueue(drawStart);
        assertNotEquals(BoundedOutboundQueue.EmitStatus.DROPPED, status1);

        BoundedOutboundQueue.EmitStatus status2 = queue.enqueue(gameFinished);
        assertNotEquals(BoundedOutboundQueue.EmitStatus.DROPPED, status2);
    }
}
