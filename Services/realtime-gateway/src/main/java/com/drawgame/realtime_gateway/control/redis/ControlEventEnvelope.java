package com.drawgame.realtime_gateway.control.redis;

/**
 * Envelope wrapping a room-scoped CONTROL event for cross-Gateway fanout via Redis Pub/Sub.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code originGatewayId} — ID of the Gateway that originally received and authorized
 *       the control event. Used by subscribers to suppress self-echo.</li>
 *   <li>{@code targetRoomId} — the room the event belongs to. The receiving Gateway routes
 *       it only to local connections bound to this room (room isolation).</li>
 *   <li>{@code eventType} — the control event type (e.g. PLAYER_JOINED), for diagnostics.</li>
 *   <li>{@code eventId} — unique ID of this publication, for diagnostics/dedup tracing.</li>
 *   <li>{@code payload} — the ready-to-send client-facing JSON string, built by the
 *       originating Gateway with the same safe-payload mapping used for local broadcast.</li>
 * </ul>
 *
 * <p>Payload secrecy rule: the payload must be identical to what the origin Gateway
 * broadcasts locally, and must already be safe for ALL room members. Private events
 * (GUESS_RESULT, ERROR, viewer-specific GET_GAME_STATE responses, ...) never enter
 * this envelope.
 */
public record ControlEventEnvelope(
        String originGatewayId,
        String targetRoomId,
        String eventType,
        String eventId,
        String payload
) {
}
