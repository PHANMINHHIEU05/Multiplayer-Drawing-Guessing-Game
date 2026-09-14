package com.drawgame.realtime_gateway.drawing.recovery;

import com.drawgame.realtime_gateway.drawing.protocol.DrawingMessage;
import com.drawgame.realtime_gateway.drawing.routing.DrawingEventHook;
import com.drawgame.realtime_gateway.drawing.transport.DrawingSessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TV7 — active implementation of the TV3-G08 recovery groundwork hook.
 *
 * <p>Records every ACCEPTED drawing event into the shared Redis recovery Stream
 * (cross-Gateway) and performs round/game lifecycle cleanup. Recording is
 * asynchronous and failure-isolated: a recovery write failure must never break
 * live drawing fanout.
 */
@Component("drawingEventHook")
public class DrawingRecoveryHook implements DrawingEventHook {

    private static final Logger log = LoggerFactory.getLogger(DrawingRecoveryHook.class);

    private final DrawingRecoveryRepository repository;

    public DrawingRecoveryHook(DrawingRecoveryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onAccepted(DrawingSessionContext session, DrawingMessage message, byte[] encodedBytes) {
        // Fire-and-forget on the recording path; errors are swallowed inside the repository.
        repository.record(session.roomId(), message).subscribe(
                null,
                e -> log.trace("Recovery record ignored for room={}", session.roomId()));
    }

    @Override
    public void onRoundReset(String roomId, int newRound) {
        log.debug("Recovery reset for round transition: room={} newRound={}", roomId, newRound);
        repository.resetForNewRound(roomId).subscribe();
    }

    @Override
    public void onGameFinished(String roomId) {
        log.info("Recovery state removed on game finish: room={}", roomId);
        repository.removeAll(roomId).subscribe();
    }
}
