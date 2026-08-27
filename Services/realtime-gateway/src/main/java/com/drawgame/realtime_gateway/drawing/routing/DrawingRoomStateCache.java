package com.drawgame.realtime_gateway.drawing.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, thread-safe cache of {@link DrawingRoomState} keyed by roomId.
 *
 * <p>Purpose: fast-path drawing authorization without calling Game Service per frame.
 * Must be kept consistent via game lifecycle events (GAME_STARTED, etc.).
 */
@Component
public class DrawingRoomStateCache {

    private static final Logger log = LoggerFactory.getLogger(DrawingRoomStateCache.class);

    private final ConcurrentHashMap<String, DrawingRoomState> cache = new ConcurrentHashMap<>();

    /**
     * Update (or insert) the drawing state for a room.
     * Called when Gateway receives GAME_STARTED / ROUND_STARTED / ROUND_ENDED events.
     */
    public void update(String roomId, DrawingRoomState state) {
        cache.put(roomId, state);
        log.debug("DrawingRoomStateCache updated: room={} drawer={} round={} status={}",
                roomId, state.currentDrawerId(), state.currentRound(), state.gameStatus());
    }

    /**
     * Look up current drawing state for the given room.
     *
     * @return empty if no active game state is cached for this room
     */
    public Optional<DrawingRoomState> get(String roomId) {
        return Optional.ofNullable(cache.get(roomId));
    }

    /**
     * Remove cached state when a game ends or room is cleaned up.
     */
    public void remove(String roomId) {
        DrawingRoomState removed = cache.remove(roomId);
        if (removed != null) {
            log.debug("DrawingRoomStateCache cleared: room={}", roomId);
        }
    }

    /** Exposed for testing / metrics. */
    public int size() {
        return cache.size();
    }
}
