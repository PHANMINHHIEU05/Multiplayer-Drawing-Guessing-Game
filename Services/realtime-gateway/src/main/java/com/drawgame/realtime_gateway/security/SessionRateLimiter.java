package com.drawgame.realtime_gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TV8 — simple per-session token-bucket rate limiter (in-memory, Gateway-local).
 *
 * <p>Purpose: one malicious or buggy client must not overwhelm the services. This is
 * NOT distributed anti-DDoS infrastructure — per-session limits at each Gateway are a
 * documented, sufficient trade-off for the current deployment scale (a player spamming
 * through one socket hits the bucket on that Gateway; cross-Gateway abusers are still
 * bounded per-connection).
 *
 * <p>Buckets (configurable via application.yml / env):
 * <ul>
 *   <li>guesses — low (gameplay: a few guesses per second is already very fast)</li>
 *   <li>control — room/game/chat/canvas requests (10/s default; recovery flows poll at ~1/s
 *       and reconnect bursts stay well under this)</li>
 *   <li>drawBatches — deliberately high ceiling to catch only extreme abuse; normal
 *       ~16ms batching (≈60-65/s) is unaffected</li>
 * </ul>
 *
 * <p>Entries are lazily created per WebSocket session and removed on disconnect via
 * {@link #removeSession(String)} — no unbounded growth across reconnect cycles.
 */
@Component
public class SessionRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(SessionRateLimiter.class);

    /** Replenish rate = capacity tokens per windowSeconds (fixed-window token bucket). */
    public enum Bucket {
        GUESS, CONTROL, DRAW, REACTION
    }

    private static final class Window {
        final AtomicLong count = new AtomicLong();
        volatile long windowStartMs;

        Window(long now) {
            this.windowStartMs = now;
        }
    }

    private final Map<String, Map<Bucket, Window>> sessions = new ConcurrentHashMap<>();
    private final int guessMax;
    private final int controlMax;
    private final int drawMax;
    private final int reactionMax;
    private final long windowMs;
    private final int retryAfterMs;

    public SessionRateLimiter(
            @Value("${security.rate-limit.guess-max-per-window:${SECURITY_GUESS_MAX_PER_SECOND:3}}") int guessMax,
            @Value("${security.rate-limit.control-max-per-window:10}") int controlMax,
            @Value("${security.rate-limit.draw-max-per-window:120}") int drawMax,
            @Value("${security.rate-limit.window-ms:1000}") long windowMs,
            @Value("${security.rate-limit.retry-after-ms:500}") int retryAfterMs
    ) {
        this(guessMax, controlMax, drawMax, 1, windowMs, retryAfterMs);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SessionRateLimiter(
            @Value("${security.rate-limit.guess-max-per-window:${SECURITY_GUESS_MAX_PER_SECOND:3}}") int guessMax,
            @Value("${security.rate-limit.control-max-per-window:10}") int controlMax,
            @Value("${security.rate-limit.draw-max-per-window:120}") int drawMax,
            @Value("${security.rate-limit.reaction-max-per-window:1}") int reactionMax,
            @Value("${security.rate-limit.window-ms:1000}") long windowMs,
            @Value("${security.rate-limit.retry-after-ms:500}") int retryAfterMs
    ) {
        this.guessMax = guessMax;
        this.controlMax = controlMax;
        this.drawMax = drawMax;
        this.reactionMax = reactionMax;
        this.windowMs = windowMs;
        this.retryAfterMs = retryAfterMs;
        log.info("SessionRateLimiter initialized: guess={}/{}ms control={}/{}ms draw={}/{}ms reaction={}/{}ms",
                guessMax, windowMs, controlMax, windowMs, drawMax, windowMs, reactionMax, windowMs);
    }

    /**
     * Try to consume one permit for the session/bucket.
     *
     * @return null if allowed, otherwise a client-facing RATE_LIMITED JSON payload
     */
    public String tryAcquire(String sessionId, Bucket bucket, String requestId) {
        long now = System.currentTimeMillis();
        Map<Bucket, Window> buckets = sessions.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
        Window w = buckets.computeIfAbsent(bucket, k -> new Window(now));

        synchronized (w) {
            if (now - w.windowStartMs >= windowMs) {
                w.windowStartMs = now;
                w.count.set(0);
            }
            long used = w.count.incrementAndGet();
            int max = switch (bucket) {
                case GUESS -> guessMax;
                case CONTROL -> controlMax;
                case DRAW -> drawMax;
                case REACTION -> reactionMax;
            };
            if (used > max) {
                log.debug("Rate limited: session={} bucket={} used={}/{}", sessionId, bucket, used, max);
                return rateLimitedJson(requestId);
            }
        }
        return null;
    }

    /** Drop all buckets for a disconnected session (prevents memory growth across cycles). */
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public int getRetryAfterMs() {
        return retryAfterMs;
    }

    /** Standardized error response — no internal limiter state leaked. */
    private String rateLimitedJson(String requestId) {
        StringBuilder sb = new StringBuilder("{\"type\":\"ERROR\",\"code\":\"RATE_LIMITED\"");
        if (requestId != null && !requestId.isBlank()) {
            sb.append(",\"requestId\":\"").append(escape(requestId)).append("\"");
        }
        sb.append(",\"message\":\"Too many requests, slow down\"");
        sb.append(",\"retryAfterMs\":").append(retryAfterMs);
        sb.append(",\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests, slow down\"}}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
