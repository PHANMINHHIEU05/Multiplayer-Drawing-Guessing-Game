package com.drawgame.realtime_gateway.control.redis;

/**
 * Well-known fields of round-lifecycle control payloads (ROUND_STARTED / ROUND_ENDED /
 * GAME_FINISHED) used by {@link ControlRedisSubscriber} to refresh the drawing
 * authorization cache. Only the fields the Gateway needs — never the secret word.
 */
public record ControlEventPayload(
        String type,
        String roomId,
        Integer currentRound,
        String drawerId,
        String status
) {
}
