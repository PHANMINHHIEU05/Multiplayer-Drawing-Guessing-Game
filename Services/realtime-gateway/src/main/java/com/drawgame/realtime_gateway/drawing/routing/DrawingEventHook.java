package com.drawgame.realtime_gateway.drawing.routing;

import com.drawgame.realtime_gateway.drawing.protocol.DrawingMessage;
import com.drawgame.realtime_gateway.drawing.transport.DrawingSessionContext;

/**
 * TV3 Stabilization — Recovery Groundwork Hook (TV3-G08).
 *
 * <p>This interface defines lifecycle callbacks that will be used in a future phase
 * to implement Canvas Recovery (storing accepted drawing events and replaying them
 * to reconnecting clients).
 *
 * <p>In the current sprint (Stabilization), the only active implementation is
 * {@link NoOpDrawingEventHook}. Canvas Recovery storage is NOT implemented yet.
 *
 * <p>Key design constraints (doc TV3-G08):
 * <ul>
 *   <li>Only ACCEPTED events (post-authorization) are passed to the hook.</li>
 *   <li>Redis Pub/Sub must NOT be used as history/recovery store.</li>
 *   <li>No Postgres drawing history in this sprint.</li>
 * </ul>
 */
public interface DrawingEventHook {

    /**
     * Called after a drawing event has been authorized and before/during local+Redis fanout.
     *
     * @param session      the authorized session context
     * @param message      the accepted drawing message (domain object)
     * @param encodedBytes the binary-encoded payload (ready for Redis/local broadcast)
     */
    void onAccepted(DrawingSessionContext session, DrawingMessage message, byte[] encodedBytes);

    /**
     * Called when the round resets — used to clear transient drawing state for the room.
     * Triggered by CORRECT guess (round transition) or explicit ROUND_UPDATE.
     *
     * @param roomId   the room being reset
     * @param newRound the new round number
     */
    void onRoundReset(String roomId, int newRound);

    /**
     * Called when the game finishes — used to close the recovery window for this room.
     *
     * @param roomId the room whose game has ended
     */
    void onGameFinished(String roomId);
}
