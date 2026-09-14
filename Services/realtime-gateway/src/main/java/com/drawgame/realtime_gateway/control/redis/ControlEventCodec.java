package com.drawgame.realtime_gateway.control.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Canonical (de)serialization for {@link ControlEventEnvelope}.
 *
 * <p>Keeps Jackson handling in one place so publishers and subscribers cannot drift
 * apart. Malformed messages are dropped with a WARN — a bad message must never
 * break the subscriber stream.
 */
final class ControlEventCodec {

    private static final Logger log = LoggerFactory.getLogger(ControlEventCodec.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ControlEventCodec() {
    }

    static String write(ControlEventEnvelope envelope) {
        try {
            return MAPPER.writeValueAsString(envelope);
        } catch (Exception e) {
            // Should never happen for a simple record of Strings
            log.warn("Failed to serialize ControlEventEnvelope: type={} room={}: {}",
                    envelope.eventType(), envelope.targetRoomId(), e.getMessage());
            return "{}";
        }
    }

    static ControlEventEnvelope read(String json) {
        try {
            return MAPPER.readValue(json, ControlEventEnvelope.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize ControlEventEnvelope: {}", e.getMessage());
            return null;
        }
    }
}
