package com.drawgame.realtime_gateway.connection;

import com.drawgame.realtime_gateway.metrics.GatewayMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TV4 — Bounded Outbound Queue for managing per-WebSocket-session backpressure.
 * <p>
 * Ensures that slow clients do not cause unbounded memory growth on the Gateway.
 * Employs a priority policy where high-frequency DRAW_BATCH frames are safely dropped
 * under queue pressure, while critical control frames (chat, score, round lifecycle, canvas clear)
 * are prioritized and preserved.
 */
public class BoundedOutboundQueue {

    private static final Logger log = LoggerFactory.getLogger(BoundedOutboundQueue.class);

    public enum EmitStatus {
        EMITTED,
        DROPPED,
        OVERFLOW
    }

    private final String sessionId;
    private final int maxCapacity;
    private final GatewayMetrics metrics;
    private final Sinks.Many<OutboundFrame> sink;
    private final AtomicInteger currentQueueSize = new AtomicInteger(0);
    private final AtomicLong droppedFramesCount = new AtomicLong(0);
    private final AtomicLong overflowCount = new AtomicLong(0);

    public BoundedOutboundQueue(String sessionId, int maxCapacity, GatewayMetrics metrics) {
        this.sessionId = sessionId;
        this.maxCapacity = Math.max(16, maxCapacity);
        this.metrics = metrics;
        this.sink = Sinks.many().unicast().onBackpressureBuffer();
    }

    public Flux<OutboundFrame> asFlux() {
        return sink.asFlux()
                .doOnNext(frame -> {
                    int size = currentQueueSize.decrementAndGet();
                    if (metrics != null) {
                        metrics.recordQueueSizeChange(-1);
                    }
                    log.trace("Session {} consumed frame. Remaining queue: {}", sessionId, size);
                });
    }

    /**
     * Enqueue a frame with backpressure prioritization.
     *
     * @param frame the frame to send
     * @return EmitStatus indicating whether the frame was queued, dropped, or overflowed
     */
    public EmitStatus enqueue(OutboundFrame frame) {
        int current = currentQueueSize.get();

        // Check if queue has reached or exceeded max capacity
        if (current >= maxCapacity) {
            if (frame.isDroppable()) {
                // Drop stale drawing batch to protect Gateway memory & connection health
                droppedFramesCount.incrementAndGet();
                if (metrics != null) {
                    metrics.incrementDroppedDrawBatch();
                }
                log.debug("Backpressure DROP drawing frame for slow session {} (queue={}/{})",
                        sessionId, current, maxCapacity);
                return EmitStatus.DROPPED;
            } else {
                // Control frame under pressure: attempt to deliver, log warning
                log.warn("Queue full ({}/{}) for session {}, forcing control frame through",
                        current, maxCapacity, sessionId);
                overflowCount.incrementAndGet();
                if (metrics != null) {
                    metrics.incrementQueueOverflow();
                }
            }
        }

        Sinks.EmitResult result = sink.tryEmitNext(frame);
        if (result.isSuccess()) {
            currentQueueSize.incrementAndGet();
            if (metrics != null) {
                metrics.recordQueueSizeChange(1);
            }
            return EmitStatus.EMITTED;
        } else {
            if (frame.isDroppable()) {
                droppedFramesCount.incrementAndGet();
                if (metrics != null) {
                    metrics.incrementDroppedDrawBatch();
                }
                log.debug("Sink emission failed ({}) for drawing frame on session {}, dropped", result, sessionId);
                return EmitStatus.DROPPED;
            } else {
                overflowCount.incrementAndGet();
                if (metrics != null) {
                    metrics.incrementQueueOverflow();
                }
                log.warn("Sink emission failed ({}) for control frame on session {}", result, sessionId);
                return EmitStatus.OVERFLOW;
            }
        }
    }

    public void complete() {
        sink.tryEmitComplete();
    }

    public int getCurrentQueueSize() {
        return Math.max(0, currentQueueSize.get());
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public long getDroppedFramesCount() {
        return droppedFramesCount.get();
    }

    public long getOverflowCount() {
        return overflowCount.get();
    }

    public String getSessionId() {
        return sessionId;
    }
}
