package com.drawgame.realtime_gateway.metrics;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TV4 — Central telemetry & metrics registry for Realtime Gateway.
 * Tracks connection health, outbound queue pressure, dropped frames, and heartbeat stats.
 */
@Component
public class GatewayMetrics {

    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger totalQueueSize = new AtomicInteger(0);
    private final AtomicLong queueOverflowCount = new AtomicLong(0);
    private final AtomicLong droppedDrawBatchCount = new AtomicLong(0);
    private final AtomicLong coalescedDrawBatchCount = new AtomicLong(0);
    private final AtomicLong heartbeatPingReceived = new AtomicLong(0);
    private final AtomicLong heartbeatPongSent = new AtomicLong(0);

    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }

    public void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }

    public int getActiveConnections() {
        return activeConnections.get();
    }

    public void recordQueueSizeChange(int delta) {
        totalQueueSize.addAndGet(delta);
    }

    public int getTotalQueueSize() {
        return totalQueueSize.get();
    }

    public void incrementQueueOverflow() {
        queueOverflowCount.incrementAndGet();
    }

    public long getQueueOverflowCount() {
        return queueOverflowCount.get();
    }

    public void incrementDroppedDrawBatch() {
        droppedDrawBatchCount.incrementAndGet();
    }

    public void addDroppedDrawBatches(long count) {
        droppedDrawBatchCount.addAndGet(count);
    }

    public long getDroppedDrawBatchCount() {
        return droppedDrawBatchCount.get();
    }

    public void incrementCoalescedDrawBatch() {
        coalescedDrawBatchCount.incrementAndGet();
    }

    public long getCoalescedDrawBatchCount() {
        return coalescedDrawBatchCount.get();
    }

    public void incrementHeartbeatPingReceived() {
        heartbeatPingReceived.incrementAndGet();
    }

    public long getHeartbeatPingReceived() {
        return heartbeatPingReceived.get();
    }

    public void incrementHeartbeatPongSent() {
        heartbeatPongSent.incrementAndGet();
    }

    public long getHeartbeatPongSent() {
        return heartbeatPongSent.get();
    }

    public void reset() {
        activeConnections.set(0);
        totalQueueSize.set(0);
        queueOverflowCount.set(0);
        droppedDrawBatchCount.set(0);
        coalescedDrawBatchCount.set(0);
        heartbeatPingReceived.set(0);
        heartbeatPongSent.set(0);
    }
}
