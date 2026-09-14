package com.drawgame.realtime_gateway.drawing.recovery;

import com.drawgame.realtime_gateway.drawing.protocol.ClearCanvasMessage;
import com.drawgame.realtime_gateway.drawing.protocol.DrawBatchMessage;
import com.drawgame.realtime_gateway.drawing.protocol.DrawEndMessage;
import com.drawgame.realtime_gateway.drawing.protocol.DrawStartMessage;
import com.drawgame.realtime_gateway.drawing.protocol.DrawingPoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * TV7 — unit tests for the cross-Gateway Canvas recovery store.
 * Uses a REAL Redis (docker, localhost:6380 in this environment / default 6379)
 * to verify stream append, ordering, CLEAR compaction, bounds and cleanup.
 *
 * Requires a reachable Redis; skipped gracefully when unavailable.
 */
@ExtendWith(MockitoExtension.class)
class DrawingRecoveryRepositoryTest {

    private static ReactiveStringRedisTemplate redis;
    private static boolean redisAvailable = false;

    private DrawingRecoveryRepository repository;

    @Mock private org.springframework.data.redis.core.ReactiveStringRedisTemplate mockRedis;

    @BeforeAll
    static void connectRedis() {
        String host = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        String port = System.getenv().getOrDefault("REDIS_PORT", "6379");
        try {
            LettuceConnectionFactory factory = new LettuceConnectionFactory(host, Integer.parseInt(port));
            factory.afterPropertiesSet();
            redis = new ReactiveStringRedisTemplate(factory);
            redis.opsForValue().get("ping").block(java.time.Duration.ofSeconds(2));
            redisAvailable = true;
        } catch (Exception e) {
            redisAvailable = false;
        }
    }

    @BeforeEach
    void setUp() {
        org.junit.jupiter.api.Assumptions.assumeTrue(redisAvailable, "Redis not reachable — skipping");
        if (redisAvailable) {
            repository = new DrawingRecoveryRepository(redis, 64, 300);
            String roomId = "rc-test-room";
            // clean slate
            Mono.when(
                redis.delete(String.format("drawing:room:%s:round:%d:events", roomId, 1)),
                redis.delete(String.format("drawing:room:%s:round:%d:events", roomId, 2)),
                redis.delete(String.format("drawing:room:%s:recovery-keys", roomId))
            ).block(java.time.Duration.ofSeconds(2));
        }
    }

    private DrawStartMessage start(int round, String hex) {
        java.awt.Color c = java.awt.Color.decode(hex);
        return new DrawStartMessage(1, round, UUID.randomUUID(), 0.1, 0.2, c.getRed(), c.getGreen(), c.getBlue(), 8);
    }

    private DrawBatchMessage batch(int round, UUID strokeId) {
        return new DrawBatchMessage(1, round, strokeId, 0,
                List.of(new DrawingPoint(0.3, 0.4), new DrawingPoint(0.5, 0.6)));
    }

    @Test
    @DisplayName("Append + readHistory preserves event order and semantic fields")
    void appendAndReadPreservesOrder() {
        String roomId = "rc-test-room";
        UUID stroke = UUID.randomUUID();
        DrawStartMessage s = new DrawStartMessage(1, 1, stroke, 0.1, 0.2, 0xEF, 0x44, 0x44, 12);
        DrawBatchMessage b = batch(1, stroke);
        DrawEndMessage e = new DrawEndMessage(1, 1, stroke);

        repository.record(roomId, s).block(java.time.Duration.ofSeconds(2));
        repository.record(roomId, b).block(java.time.Duration.ofSeconds(2));
        repository.record(roomId, e).block(java.time.Duration.ofSeconds(2));

        DrawingRecoveryRepository.RecoveryResult result =
                repository.readHistory(roomId, 1).block(java.time.Duration.ofSeconds(2));

        assertThat(result).isNotNull();
        assertThat(result.events).hasSize(3);
        assertThat(result.events.get(0).get("type")).isEqualTo("DRAW_START");
        assertThat(result.events.get(0).get("r")).isEqualTo("239"); // 0xEF
        assertThat(result.events.get(0).get("width")).isEqualTo("12");
        assertThat(result.events.get(1).get("type")).isEqualTo("DRAW_BATCH");
        assertThat(String.valueOf(result.events.get(1).get("points"))).contains("0.3");
        assertThat(result.events.get(2).get("type")).isEqualTo("DRAW_END");
        assertThat(result.historyComplete).isTrue();
        // ordering: streamIds strictly increasing
        String id0 = String.valueOf(result.events.get(0).get("streamId"));
        String id2 = String.valueOf(result.events.get(2).get("streamId"));
        assertThat(id2).isGreaterThan(id0);
    }

    @Test
    @DisplayName("CLEAR_CANVAS compaction: pre-clear strokes never reappear")
    void clearCompaction() {
        String roomId = "rc-test-room";
        UUID stroke1 = UUID.randomUUID();
        repository.record(roomId, start(1, "#FF0000")).block(java.time.Duration.ofSeconds(2));
        repository.record(roomId, batch(1, stroke1)).block(java.time.Duration.ofSeconds(2));
        // CLEAR
        repository.record(roomId, new ClearCanvasMessage(1, 1)).block(java.time.Duration.ofSeconds(2));
        // post-clear stroke
        repository.record(roomId, start(1, "#00FF00")).block(java.time.Duration.ofSeconds(2));

        DrawingRecoveryRepository.RecoveryResult result =
                repository.readHistory(roomId, 1).block(java.time.Duration.ofSeconds(2));

        assertThat(result.events).hasSize(2); // CLEAR marker + post-clear DRAW_START
        assertThat(result.events.get(0).get("type")).isEqualTo("CLEAR_CANVAS");
        assertThat(result.events.get(1).get("type")).isEqualTo("DRAW_START");
        assertThat(String.valueOf(result.events.get(1).get("g"))).isEqualTo("255"); // green stroke
    }

    @Test
    @DisplayName("resetForNewRound removes old-round history (never served as new round)")
    void resetForNewRound() {
        String roomId = "rc-test-room";
        repository.record(roomId, start(1, "#0000FF")).block(java.time.Duration.ofSeconds(2));
        repository.resetForNewRound(roomId).block(java.time.Duration.ofSeconds(2));

        DrawingRecoveryRepository.RecoveryResult result =
                repository.readHistory(roomId, 1).block(java.time.Duration.ofSeconds(2));
        assertThat(result.events).isEmpty();
        // round 2 stream is fresh and independent
        repository.record(roomId, start(2, "#0000FF")).block(java.time.Duration.ofSeconds(2));
        DrawingRecoveryRepository.RecoveryResult r2 =
                repository.readHistory(roomId, 2).block(java.time.Duration.ofSeconds(2));
        assertThat(r2.events).hasSize(1);
    }

    @Test
    @DisplayName("removeAll deletes every indexed round stream + index")
    void removeAll() {
        String roomId = "rc-test-room";
        repository.record(roomId, start(1, "#123456")).block(java.time.Duration.ofSeconds(2));
        repository.record(roomId, start(2, "#123456")).block(java.time.Duration.ofSeconds(2));
        repository.removeAll(roomId).block(java.time.Duration.ofSeconds(2));

        assertThat(repository.readHistory(roomId, 1).block(java.time.Duration.ofSeconds(2)).events).isEmpty();
        assertThat(repository.readHistory(roomId, 2).block(java.time.Duration.ofSeconds(2)).events).isEmpty();
    }

    @Test
    @DisplayName("Redis failure during record does not propagate (live drawing unaffected)")
    void recordFailureIsolated() {
        when(mockRedis.opsForStream()).thenThrow(new RuntimeException("redis down"));
        DrawingRecoveryRepository failing = new DrawingRecoveryRepository(mockRedis, 64, 300);
        // Must not throw — returns a fallback cursor
        String id = failing.record("room-x", start(1, "#000000")).block(java.time.Duration.ofSeconds(2));
        assertThat(id).isEqualTo("0-0");
    }
}
