package com.drawgame.realtime_gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * TV8 — signed GAME SESSION / RESUME credentials.
 *
 * <p>This is NOT an account access token: it is a narrowly-scoped credential proving
 * that a WebSocket connection may act as the logical player {@code sub} inside the
 * room {@code room}. Issued by the Gateway only after Room Service membership succeeds
 * (CREATE_ROOM / JOIN_ROOM / RESUME_SESSION), verified by any Gateway instance sharing
 * the same {@code GAME_SESSION_JWT_SECRET} (cross-Gateway resume).
 *
 * <p>Claims: sub (playerId), room (roomId), purpose ("GAME_SESSION"), iat, exp, jti.
 * The token carries NO game state — no secret word, score, aliases, chat or drawing.
 *
 * <p>Revocation model (documented): signature + expiry + purpose + room scope are
 * verified cryptographically; whether the player is STILL a member is checked against
 * authoritative Room Service state at every resume — a valid old token cannot resurrect
 * deleted membership. Explicit leave therefore invalidates resume through membership
 * removal, not through a token blacklist.
 *
 * <p>Security notes: HS256 with a shared backend secret (env-provided; a dev fallback
 * exists and is loudly logged). Tokens are never logged in full — only jti.
 */
@Component
public class GameSessionTokenService {

    private static final Logger log = LoggerFactory.getLogger(GameSessionTokenService.class);
    static final String PURPOSE = "GAME_SESSION";
    static final String CLAIM_ROOM = "room";
    static final String CLAIM_PURPOSE = "purpose";

    private final SecretKey key;
    private final Duration ttl;
    private final boolean usingDevSecret;

    public GameSessionTokenService(
            @Value("${security.game-session.secret:${GAME_SESSION_JWT_SECRET:}}") String secret,
            @Value("${security.game-session.ttl-minutes:${GAME_SESSION_TOKEN_TTL_MINUTES:720}}") long ttlMinutes
    ) {
        this.ttl = Duration.ofMinutes(ttlMinutes);
        if (secret == null || secret.length() < 32) {
            // Development fallback ONLY — production must set GAME_SESSION_JWT_SECRET.
            String devSecret = "dev-only-insecure-game-session-secret-do-not-use-in-prod";
            this.key = Keys.hmacShaKeyFor(devSecret.getBytes(StandardCharsets.UTF_8));
            this.usingDevSecret = true;
            log.warn("SECURITY: GAME_SESSION_JWT_SECRET is missing/short (<32 chars) — using an INSECURE "
                    + "development secret. Set GAME_SESSION_JWT_SECRET in production!");
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            this.usingDevSecret = false;
        }
        log.info("GameSessionTokenService initialized: ttl={}min devSecret={}", ttlMinutes, usingDevSecret);
    }

    public boolean isUsingDevSecret() {
        return usingDevSecret;
    }

    /** Issue a game-session credential for a player who just established room membership. */
    public String issue(String playerId, String roomId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(playerId)
                .claim(CLAIM_ROOM, roomId)
                .claim(CLAIM_PURPOSE, PURPOSE)
                .id(UUID.randomUUID().toString()) // jti — safe to log
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Verification outcome with a stable, sanitized error code for the client. */
    public sealed interface Verification permits Verification.Verified, Verification.Invalid {
        record Verified(String playerId, String roomId, String jti) implements Verification {}

        /** code: one of the stable client-facing codes (never parser internals). */
        record Invalid(String code, String detail) implements Verification {}
    }

    /**
     * Verify signature, expiry, purpose and room scope. Returns the authoritative
     * playerId/roomId derived from the VERIFIED claims — never from client payload.
     */
    public Verification verify(String token, String expectedRoomId) {
        if (token == null || token.isBlank()) {
            return new Verification.Invalid("AUTH_REQUIRED", "Missing session token");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return new Verification.Invalid("SESSION_TOKEN_EXPIRED", "Session token expired");
        } catch (JwtException | IllegalArgumentException e) {
            return new Verification.Invalid("INVALID_SESSION_TOKEN", "Session token is invalid");
        }

        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            return new Verification.Invalid("INVALID_SESSION_TOKEN", "Token has no subject");
        }
        if (!PURPOSE.equals(claims.get(CLAIM_PURPOSE, String.class))) {
            return new Verification.Invalid("INVALID_SESSION_TOKEN", "Token purpose mismatch");
        }
        String tokenRoom = claims.get(CLAIM_ROOM, String.class);
        if (expectedRoomId != null && !expectedRoomId.isBlank() && !expectedRoomId.equals(tokenRoom)) {
            return new Verification.Invalid("ROOM_SCOPE_MISMATCH",
                    "Token was not issued for room " + expectedRoomId);
        }
        if (tokenRoom == null || tokenRoom.isBlank()) {
            return new Verification.Invalid("INVALID_SESSION_TOKEN", "Token has no room scope");
        }
        // log only jti — never the token itself
        log.debug("Game session token verified: sub={} room={} jti={}", sub, tokenRoom, claims.getId());
        return new Verification.Verified(sub, tokenRoom, claims.getId());
    }

    /** Optional-room verification (when the room comes from the token itself). */
    public Verification verify(String token) {
        return verify(token, null);
    }
}
