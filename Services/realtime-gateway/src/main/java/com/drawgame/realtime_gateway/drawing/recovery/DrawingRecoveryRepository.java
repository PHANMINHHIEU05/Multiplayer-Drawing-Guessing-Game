package com.drawgame.realtime_gateway.drawing.recovery;

import com.drawgame.realtime_gateway.drawing.protocol.ClearCanvasMessage;
import com.drawgame.realtime_gateway.drawing.protocol.DrawBatchMessage;
import com.drawgame.realtime_gateway.drawing.protocol.DrawEndMessage;
import com.drawgame.realtime_gateway.drawing.protocol.DrawStartMessage;
import com.drawgame.realtime_gateway.drawing.protocol.DrawingMessage;
import com.drawgame.realtime_gateway.drawing.protocol.DrawingPoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisZSetCommands.Limit;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TV7 — current-round Canvas recovery store, shared across Gateways via Redis Streams.
 *
 * <p>Key layout (per doc/reconnect-canvas-recovery.md §5.2):
 * <pre>
 *   drawing:room:{roomId}:round:{round}:events   — Redis Stream of accepted drawing ops
 *   drawing:room:{roomId}:recovery-keys          — Set index of exact stream keys (cleanup without KEYS)
 * </pre>
 *
 * <p>Only ACCEPTED drawing events (post-authorization) are recorded. Redis Stream ID is
 * the authoritative recovery cursor/ordering (global across strokes and CLEAR_CANVAS).
 * CLEAR_CANVAS applies the compaction rule: old stream is deleted and a fresh generation
 * starts with a single CLEAR entry — replay can never restore pre-clear strokes.
 *
 * <p>Bounded: XADD with approximate MAXLEN + a TTL safety net on every write.
 * Fully reactive (ReactiveStringRedisTemplate) — no blocking calls on the Netty loop.
 */
@Component
public class DrawingRecoveryRepository {

    private static final Logger log = LoggerFactory.getLogger(DrawingRecoveryRepository.class);
    private static final String STREAM_KEY_FORMAT = "drawing:room:%s:round:%d:events";
    private static final String INDEX_KEY_FORMAT = "drawing:room:%s:recovery-keys";
    /** CLEAR compaction writes a CLEAR marker into the fresh generation. */
    private static final String CLEAR_MARKER = "CLEAR";

    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final long maxEventsPerRound;
    private final long ttlSeconds;

    public DrawingRecoveryRepository(
            ReactiveStringRedisTemplate redis,
            @Value("${gateway.drawing.recovery.max-events-per-round:8192}") long maxEventsPerRound,
            @Value("${gateway.drawing.recovery.ttl-seconds:300}") long ttlSeconds
    ) {
        this.redis = redis;
        this.objectMapper = new ObjectMapper();
        this.maxEventsPerRound = maxEventsPerRound;
        this.ttlSeconds = ttlSeconds;
        log.info("DrawingRecoveryRepository initialized: maxEvents={} ttl={}s", maxEventsPerRound, ttlSeconds);
    }

    // ─── Recording (called only for authorized drawing events) ──────────

    /** Record an accepted drawing event. Returns the Redis Stream ID (recovery cursor). */
    public Mono<String> record(String roomId, DrawingMessage message) {
        if (message instanceof ClearCanvasMessage) {
            return compactOnClear(roomId, message.round());
        }
        Map<String, String> body = toStreamBody(message);
        return append(roomId, message.round(), body);
    }

    /**
     * CLEAR_CANVAS compaction (§5.4): atomically-ish delete the old generation and start
     * a fresh stream containing one CLEAR marker. A concurrent recovery therefore sees
     * either the old stream or the new generation — never pre-clear strokes.
     */
    private Mono<String> compactOnClear(String roomId, int round) {
        String streamKey = streamKey(roomId, round);
        Map<String, String> body = new HashMap<>();
        body.put("type", "CLEAR_CANVAS");

        // Mono.defer: a SYNCHRONOUS Redis failure (e.g. connection factory down) is also
        // captured by onErrorResume instead of propagating to the drawing hot path.
        return Mono.defer(() ->
                        redis.delete(streamKey)
                                .then(redis.opsForStream().add(streamKey, body).map(Object::toString))
                                .flatMap(streamId -> afterAppend(roomId, streamKey, streamId)))
                .onErrorResume(e -> isolationFallback(roomId, streamKey, e));
    }

    private Mono<String> append(String roomId, int round, Map<String, String> body) {
        String streamKey = streamKey(roomId, round);
        return Mono.defer(() ->
                        redis.opsForStream().add(streamKey, body).map(Object::toString)
                                .flatMap(streamId -> afterAppend(roomId, streamKey, streamId)))
                .onErrorResume(e -> isolationFallback(roomId, streamKey, e));
    }

    /** Recovery recording must never break live drawing — failures degrade to a no-op cursor. */
    private Mono<String> isolationFallback(String roomId, String streamKey, Throwable e) {
        log.warn("Recovery record failed (live drawing unaffected): key={}: {}", streamKey, e.getMessage());
        return Mono.just("0-0");
    }

    /** MAXLEN trim + TTL + index registration after a successful XADD. */
    private Mono<String> afterAppend(String roomId, String streamKey, String streamId) {
        return redis.opsForStream().trim(streamKey, maxEventsPerRound, true)
                .then(redis.expire(streamKey, java.time.Duration.ofSeconds(ttlSeconds)))
                .then(redis.opsForSet().add(indexKey(roomId), streamKey))
                .then(redis.expire(indexKey(roomId), java.time.Duration.ofSeconds(ttlSeconds)))
                .thenReturn(streamId)
                .doOnSuccess(v -> log.trace("Recovery event recorded: key={} streamId={}", streamKey, streamId));
    }

    // ─── Reading (GET_CANVAS_STATE) ──────────────────────────────────────

    public static class RecoveryResult {
        public final List<Map<String, Object>> events;
        public final String lastStreamId;
        public final boolean historyComplete;

        RecoveryResult(List<Map<String, Object>> events, String lastStreamId, boolean historyComplete) {
            this.events = events;
            this.lastStreamId = lastStreamId;
            this.historyComplete = historyComplete;
        }
    }

    /**
     * Read the full bounded current-round history (oldest → newest).
     * historyComplete=false when MAXLEN trimming removed the earliest events.
     */
    public Mono<RecoveryResult> readHistory(String roomId, int round) {
        String streamKey = streamKey(roomId, round);
        return redis.<String, String>opsForStream()
                .range(streamKey, Range.unbounded())
                .collectList()
                .flatMap(records -> {
                    List<Map<String, Object>> events = new ArrayList<>(records.size());
                    String lastId = "0-0";
                    for (MapRecord<String, String, String> r : records) {
                        Map<String, Object> ev = new HashMap<>(r.getValue());
                        ev.put("streamId", r.getId().getValue());
                        events.add(ev);
                        lastId = r.getId().getValue();
                    }
                    boolean complete = records.size() < maxEventsPerRound;
                    return Mono.just(new RecoveryResult(events, lastId, complete));
                })
                .onErrorResume(e -> {
                    log.warn("Recovery read failed: key={}: {}", streamKey, e.getMessage());
                    return Mono.error(new RecoveryUnavailableException("Recovery stream unavailable"));
                });
    }

    public boolean hasHistory(String roomId, int round) {
        // Reactive hasKey wrapped as blocking is NOT acceptable on the event loop.
        // Callers use readHistory(...) which returns empty list for a missing key.
        throw new UnsupportedOperationException("use readHistory (reactive)");
    }

    // ─── Lifecycle cleanup (§8) ──────────────────────────────────────────

    /** ROUND_STARTED / new authoritative round: drop all old-round streams for the room. */
    public Mono<Void> resetForNewRound(String roomId) {
        return deleteIndexed(roomId);
    }

    /** GAME_FINISHED / room deleted: remove all recovery data for the room. */
    public Mono<Void> removeAll(String roomId) {
        return deleteIndexed(roomId);
    }

    private Mono<Void> deleteIndexed(String roomId) {
        String index = indexKey(roomId);
        return redis.opsForSet().members(index)
                .flatMap(key -> redis.delete(key).then(Mono.just(key)))
                .then(redis.delete(index))
                .then()
                .doOnSuccess(v -> log.debug("Recovery state cleared for room={}", roomId))
                .onErrorResume(e -> {
                    log.warn("Recovery cleanup failed for room={} (TTL is the safety net): {}", roomId, e.getMessage());
                    return Mono.empty();
                });
    }

    // ─── Stream body mapping (semantic, transport-neutral — §5.3) ────────

    private Map<String, String> toStreamBody(DrawingMessage message) {
        Map<String, String> body = new HashMap<>();
        if (message instanceof DrawStartMessage m) {
            body.put("type", "DRAW_START");
            body.put("round", String.valueOf(m.round()));
            body.put("strokeId", m.strokeId().toString());
            body.put("x", String.valueOf(m.x()));
            body.put("y", String.valueOf(m.y()));
            body.put("r", String.valueOf(m.red()));
            body.put("g", String.valueOf(m.green()));
            body.put("b", String.valueOf(m.blue()));
            body.put("width", String.valueOf(m.width()));
        } else if (message instanceof DrawBatchMessage m) {
            body.put("type", "DRAW_BATCH");
            body.put("round", String.valueOf(m.round()));
            body.put("strokeId", m.strokeId().toString());
            body.put("seqStart", String.valueOf(m.seqStart()));
            List<String> pts = new ArrayList<>(m.points().size());
            for (DrawingPoint p : m.points()) {
                pts.add(p.x() + "," + p.y());
            }
            body.put("points", String.join(" ", pts));
        } else if (message instanceof DrawEndMessage m) {
            body.put("type", "DRAW_END");
            body.put("round", String.valueOf(m.round()));
            body.put("strokeId", m.strokeId().toString());
        }
        return body;
    }

    private String streamKey(String roomId, int round) {
        return String.format(STREAM_KEY_FORMAT, roomId, round);
    }

    private String indexKey(String roomId) {
        return String.format(INDEX_KEY_FORMAT, roomId);
    }

    /** Raised when the recovery stream cannot be read (Redis failure) — gateway stays alive. */
    public static class RecoveryUnavailableException extends RuntimeException {
        public RecoveryUnavailableException(String message) {
            super(message);
        }
    }
}
