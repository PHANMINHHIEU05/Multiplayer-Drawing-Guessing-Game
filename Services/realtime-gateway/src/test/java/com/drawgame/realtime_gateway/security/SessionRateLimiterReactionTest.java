package com.drawgame.realtime_gateway.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionRateLimiterReactionTest {
    @Test
    void reactionBucketAllowsOneAndRejectsSpamWithinWindow() {
        SessionRateLimiter limiter = new SessionRateLimiter(3, 10, 120, 1, 1000, 500);
        assertNull(limiter.tryAcquire("session", SessionRateLimiter.Bucket.REACTION, "r1"));
        assertNotNull(limiter.tryAcquire("session", SessionRateLimiter.Bucket.REACTION, "r2"));
        // Reaction throttling is isolated from the normal control bucket.
        assertNull(limiter.tryAcquire("session", SessionRateLimiter.Bucket.CONTROL, "r3"));
    }
}
