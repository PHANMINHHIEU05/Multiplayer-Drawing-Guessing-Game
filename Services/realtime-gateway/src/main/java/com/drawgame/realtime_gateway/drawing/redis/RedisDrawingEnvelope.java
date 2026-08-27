package com.drawgame.realtime_gateway.drawing.redis;

/**
 * Envelope wrapping a drawing payload for cross-Gateway fanout via Redis Pub/Sub.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code originGatewayId} — ID of the Gateway that originally received and authorized
 *       the drawing event. Used by subscribers to suppress self-echo.</li>
 *   <li>{@code targetRoomId} — the room the drawing belongs to, used by the receiving Gateway
 *       to route to the correct local connections.</li>
 *   <li>{@code drawingBytes} — the raw binary drawing payload already encoded by
 *       {@code BinaryDrawingEncoder}. Avoids decode-then-re-encode overhead at the subscriber.</li>
 * </ul>
 *
 * <p>Jackson serializes {@code byte[]} as Base64, which is acceptable for this phase.
 */
public record RedisDrawingEnvelope(
        String originGatewayId,
        String targetRoomId,
        byte[] drawingBytes
) {
}
