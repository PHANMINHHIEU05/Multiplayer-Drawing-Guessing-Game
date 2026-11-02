package com.drawgame.realtime_gateway.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionRateLimiterPingTest {
    @Test
    void pingBucketAllowsBurstUpToLimitAndRejectsSpamWithinWindow() {
        SessionRateLimiter limiter = new SessionRateLimiter(3, 10, 120, 1, 60, 5, 1000, 500);
        assertNull(limiter.tryAcquire("session", SessionRateLimiter.Bucket.PING, "r1"));
        assertNull(limiter.tryAcquire("session", SessionRateLimiter.Bucket.PING, "r2"));
        assertNull(limiter.tryAcquire("session", SessionRateLimiter.Bucket.PING, "r3"));
        assertNull(limiter.tryAcquire("session", SessionRateLimiter.Bucket.PING, "r4"));
        assertNull(limiter.tryAcquire("session", SessionRateLimiter.Bucket.PING, "r5"));
        assertNotNull(limiter.tryAcquire("session", SessionRateLimiter.Bucket.PING, "r6"));
        // Ping throttling is isolated from the normal control bucket.
        assertNull(limiter.tryAcquire("session", SessionRateLimiter.Bucket.CONTROL, "r7"));
    }
}
