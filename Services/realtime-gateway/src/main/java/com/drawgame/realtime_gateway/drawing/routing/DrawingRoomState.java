package com.drawgame.realtime_gateway.drawing.routing;

/**
 * Lightweight projection of the active game/round state for a room,
 * cached at the Gateway for O(1) drawing authorization (fast-path).
 *
 * <p>This is NOT an authoritative state — Game Service remains the source of truth.
 * TV3 uses this only to avoid calling Game Service on every DRAW_BATCH.
 */
public record DrawingRoomState(
        String currentDrawerId,
        int currentRound,
        String gameStatus
) {

    /** Convenience factory when game/round starts. */
    public static DrawingRoomState playing(String drawerId, int round) {
        return new DrawingRoomState(drawerId, round, "PLAYING");
    }

    public boolean isPlaying() {
        return "PLAYING".equalsIgnoreCase(gameStatus);
    }
}
