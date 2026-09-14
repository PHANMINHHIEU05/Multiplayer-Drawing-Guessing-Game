package com.drawgame.realtime_gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * TV8 — server-side bounds for client-controlled text inputs.
 *
 * <p>Frontend validation is UX only; these are the authoritative limits enforced at
 * the Gateway before any expensive processing or gRPC call:
 * <ul>
 *   <li>nickname — trimmed, 1..32 chars, no control characters, no pathological whitespace</li>
 *   <li>roomName — trimmed, 1..64 chars, no control characters</li>
 *   <li>guess — 1..128 chars (Vietnamese Unicode preserved; NFC normalization and
 *       matching happen later in Game Service unchanged)</li>
 *   <li>roomCode/roomId — 1..32 chars, alphanumeric-uppercase enforced for joins</li>
 * </ul>
 */
@Component
public class InputValidator {

    private final int maxNicknameLength;
    private final int maxRoomNameLength;
    private final int maxGuessLength;

    public InputValidator(
            @Value("${security.input.max-nickname-length:32}") int maxNicknameLength,
            @Value("${security.input.max-room-name-length:64}") int maxRoomNameLength,
            @Value("${security.input.max-guess-length:128}") int maxGuessLength
    ) {
        this.maxNicknameLength = maxNicknameLength;
        this.maxRoomNameLength = maxRoomNameLength;
        this.maxGuessLength = maxGuessLength;
    }

    /** Returns a sanitized value, or null when invalid (caller returns a clean ERROR). */
    public String sanitizeNickname(String raw) {
        if (raw == null) return null;
        String trimmed = raw.strip();
        if (trimmed.isEmpty() || trimmed.length() > maxNicknameLength) return null;
        if (containsControlChars(trimmed)) return null;
        return trimmed;
    }

    public String sanitizeRoomName(String raw) {
        if (raw == null) return null;
        String trimmed = raw.strip();
        if (trimmed.isEmpty() || trimmed.length() > maxRoomNameLength) return null;
        if (containsControlChars(trimmed)) return null;
        return trimmed;
    }

    /** Guess text: bounded, control-char-free; Vietnamese diacritics preserved. */
    public String sanitizeGuess(String raw) {
        if (raw == null) return null;
        String trimmed = raw.strip();
        if (trimmed.isEmpty() || trimmed.length() > maxGuessLength) return null;
        if (containsControlChars(trimmed)) return null;
        return trimmed;
    }

    /** Room code for joining: 4-32 chars, uppercase letters/digits only. */
    public boolean isValidRoomCode(String code) {
        return code != null && code.length() >= 4 && code.length() <= 32
                && code.matches("[A-Z0-9]+");
    }

    private static boolean containsControlChars(String s) {
        return s.chars().anyMatch(c -> Character.isISOControl(c));
    }
}
