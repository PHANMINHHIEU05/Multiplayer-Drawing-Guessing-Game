package com.drawgame.realtime_gateway.drawing.routing;

import com.drawgame.realtime_gateway.drawing.protocol.DrawingMessage;
import com.drawgame.realtime_gateway.drawing.transport.DrawingSessionContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * TV3 Stabilization — Default no-op implementation of {@link DrawingEventHook}.
 *
 * <p>Active during the Stabilization Sprint. Canvas Recovery will replace this
 * with a real implementation in a future phase by registering a bean named
 * {@code drawingEventHook} (this bean uses {@code @ConditionalOnMissingBean}).
 */
@Component
@ConditionalOnMissingBean(name = "drawingEventHook")
public class NoOpDrawingEventHook implements DrawingEventHook {

    @Override
    public void onAccepted(DrawingSessionContext session, DrawingMessage message, byte[] encodedBytes) {
        // No-op: Canvas Recovery history storage not yet implemented.
    }

    @Override
    public void onRoundReset(String roomId, int newRound) {
        // No-op: Recovery state reset not yet implemented.
    }

    @Override
    public void onGameFinished(String roomId) {
        // No-op: Recovery window close not yet implemented.
    }
}
