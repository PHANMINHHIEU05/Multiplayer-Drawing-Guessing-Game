package com.drawgame.realtime_gateway.drawing.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class DrawingRoomStateCacheTest {

    private DrawingRoomStateCache cache;

    @BeforeEach
    void setUp() {
        cache = new DrawingRoomStateCache();
    }

    @Test
    void update_and_get_returnsCachedState() {
        DrawingRoomState state = DrawingRoomState.playing("player-01", 2);
        cache.update("room-ABC", state);

        Optional<DrawingRoomState> result = cache.get("room-ABC");
        assertThat(result).isPresent();
        assertThat(result.get().currentDrawerId()).isEqualTo("player-01");
        assertThat(result.get().currentRound()).isEqualTo(2);
        assertThat(result.get().isPlaying()).isTrue();
    }

    @Test
    void get_unknownRoom_returnsEmpty() {
        assertThat(cache.get("non-existent-room")).isEmpty();
    }

    @Test
    void remove_clearsState() {
        cache.update("room-XYZ", DrawingRoomState.playing("player-02", 1));
        cache.remove("room-XYZ");
        assertThat(cache.get("room-XYZ")).isEmpty();
    }

    @Test
    void remove_nonExistentRoom_doesNotThrow() {
        // Should silently do nothing
        cache.remove("room-does-not-exist");
    }

    @Test
    void update_overwritesPreviousState() {
        cache.update("room-ABC", DrawingRoomState.playing("player-01", 1));
        cache.update("room-ABC", DrawingRoomState.playing("player-02", 2));

        Optional<DrawingRoomState> result = cache.get("room-ABC");
        assertThat(result).isPresent();
        assertThat(result.get().currentDrawerId()).isEqualTo("player-02");
        assertThat(result.get().currentRound()).isEqualTo(2);
    }

    @Test
    void size_reflectsNumberOfRooms() {
        assertThat(cache.size()).isEqualTo(0);
        cache.update("room-1", DrawingRoomState.playing("p1", 1));
        cache.update("room-2", DrawingRoomState.playing("p2", 1));
        assertThat(cache.size()).isEqualTo(2);
        cache.remove("room-1");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void concurrentUpdates_doNotCorruptState() throws InterruptedException {
        int threadCount = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    cache.update("room-concurrent", DrawingRoomState.playing("player-" + idx, idx));
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // After all concurrent writes, the cache must still return a valid (non-null) state
        Optional<DrawingRoomState> result = cache.get("room-concurrent");
        assertThat(result).isPresent();
        assertThat(result.get().currentDrawerId()).startsWith("player-");
    }
}
