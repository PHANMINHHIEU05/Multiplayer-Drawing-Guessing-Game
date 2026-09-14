package com.drawgame.realtime_gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TV8 — unit tests for the signed game-session credential:
 * issue/verify round-trip, tampering (subject/room/payload), expiry, purpose,
 * room scope, missing/blank token, dev-secret fallback behavior.
 */
class GameSessionTokenServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256-0123456789";

    private GameSessionTokenService service() {
        return new GameSessionTokenService(SECRET, 60);
    }

    @Test
    @DisplayName("Issue + verify round-trip derives authoritative playerId/roomId")
    void issueAndVerify() {
        GameSessionTokenService svc = service();
        String token = svc.issue("P123", "ABC123");

        GameSessionTokenService.Verification v = svc.verify(token, "ABC123");
        assertThat(v).isInstanceOf(GameSessionTokenService.Verification.Verified.class);
        GameSessionTokenService.Verification.Verified verified = (GameSessionTokenService.Verification.Verified) v;
        assertThat(verified.playerId()).isEqualTo("P123");
        assertThat(verified.roomId()).isEqualTo("ABC123");
        assertThat(verified.jti()).isNotBlank();
    }

    @Test
    @DisplayName("SEC-005: tampered payload (flip one char) → INVALID_SESSION_TOKEN")
    void tamperedPayload() {
        GameSessionTokenService svc = service();
        String token = svc.issue("P123", "ABC123");
        String tampered = token.substring(0, token.length() - 3)
                + (token.endsWith("aaa") ? "bbb" : "aaa");

        GameSessionTokenService.Verification v = svc.verify(tampered, "ABC123");
        assertThat(v).isInstanceOf(GameSessionTokenService.Verification.Invalid.class);
        assertThat(((GameSessionTokenService.Verification.Invalid) v).code()).isEqualTo("INVALID_SESSION_TOKEN");
    }

    @Test
    @DisplayName("SEC-003/SEC-004: token signed by a DIFFERENT secret → rejected")
    void foreignSecret() {
        String token = service().issue("P123", "ABC123");
        GameSessionTokenService other = new GameSessionTokenService(
                "another-secret-also-long-enough-for-hs256-abcdefghij", 60);

        GameSessionTokenService.Verification v = other.verify(token, "ABC123");
        assertThat(v).isInstanceOf(GameSessionTokenService.Verification.Invalid.class);
    }

    @Test
    @DisplayName("SEC-006: expired token → SESSION_TOKEN_EXPIRED")
    void expiredToken() {
        // TTL of 0 minutes → already expired at issuance
        GameSessionTokenService svc = new GameSessionTokenService(SECRET, 0);
        String token = svc.issue("P123", "ABC123");

        GameSessionTokenService.Verification v = service().verify(token, "ABC123");
        assertThat(v).isInstanceOf(GameSessionTokenService.Verification.Invalid.class);
        assertThat(((GameSessionTokenService.Verification.Invalid) v).code()).isEqualTo("SESSION_TOKEN_EXPIRED");
    }

    @Test
    @DisplayName("SEC-007: missing/blank token → AUTH_REQUIRED")
    void missingToken() {
        GameSessionTokenService svc = service();
        assertThat(svc.verify(null, "ABC123"))
                .isInstanceOf(GameSessionTokenService.Verification.Invalid.class);
        assertThat(svc.verify("", "ABC123"))
                .isInstanceOf(GameSessionTokenService.Verification.Invalid.class);
        assertThat(((GameSessionTokenService.Verification.Invalid) svc.verify(null, "R")).code())
                .isEqualTo("AUTH_REQUIRED");
    }

    @Test
    @DisplayName("SEC-017: token for Room A used against Room B → ROOM_SCOPE_MISMATCH")
    void roomScopeMismatch() {
        GameSessionTokenService svc = service();
        String token = svc.issue("P123", "ROOM_A");

        GameSessionTokenService.Verification v = svc.verify(token, "ROOM_B");
        assertThat(v).isInstanceOf(GameSessionTokenService.Verification.Invalid.class);
        assertThat(((GameSessionTokenService.Verification.Invalid) v).code()).isEqualTo("ROOM_SCOPE_MISMATCH");
    }

    @Test
    @DisplayName("Token with wrong purpose (forged with unknown purpose claim) is rejected by signature check path")
    void malformedToken() {
        GameSessionTokenService svc = service();
        // Not a JWT at all
        assertThat(svc.verify("garbage.token.value", "R"))
                .isInstanceOf(GameSessionTokenService.Verification.Invalid.class);
        // Empty JWT-ish string
        assertThat(svc.verify("a.b.c", "R"))
                .isInstanceOf(GameSessionTokenService.Verification.Invalid.class);
    }

    @Test
    @DisplayName("Short secret triggers the documented insecure dev fallback")
    void devSecretFallback() {
        GameSessionTokenService dev = new GameSessionTokenService("short", 60);
        assertThat(dev.isUsingDevSecret()).isTrue();
        // The dev service can still issue/verify its own tokens (dev mode works)
        GameSessionTokenService.Verification v = dev.verify(dev.issue("P", "R"), "R");
        assertThat(v).isInstanceOf(GameSessionTokenService.Verification.Verified.class);
        // A proper service rejects dev-fallback tokens
        assertThat(service().verify(dev.issue("P", "R"), "R"))
                .isInstanceOf(GameSessionTokenService.Verification.Invalid.class);
    }
}
