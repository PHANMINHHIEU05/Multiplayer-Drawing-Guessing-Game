package com.drawgame.room.repository;

import com.drawgame.room.domain.Room;
import com.drawgame.room.domain.RoomPlayer;
import com.drawgame.room.domain.RoomStatus;
import com.drawgame.room.exception.PlayerAlreadyInRoomException;
import com.drawgame.room.exception.RoomFullException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RedisRoomRepositoryTest {

    @Autowired
    private RedisRoomRepository repository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @AfterEach
    void cleanRedis() {
        try {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("create and findById - Should save room and reconstruct it from Redis")
    void testCreateAndFindById() {
        Room room = new Room(
                "ROOM01",
                "Test Room",
                "u01",
                RoomStatus.WAITING,
                6,
                3,
                60,
                List.of(new RoomPlayer("u01", "Alice"))
        );

        repository.create(room);

        Optional<Room> foundOpt = repository.findById("ROOM01");
        assertTrue(foundOpt.isPresent());

        Room found = foundOpt.get();
        assertEquals("ROOM01", found.id());
        assertEquals("Test Room", found.name());
        assertEquals("u01", found.hostId());
        assertEquals(RoomStatus.WAITING, found.status());
        assertEquals(6, found.maxPlayers());
        assertEquals(3, found.roundCount());
        assertEquals(60, found.roundDuration());
        assertEquals(1, found.players().size());
        assertEquals("u01", found.players().get(0).playerId());
        assertEquals("Alice", found.players().get(0).username());
    }

    @Test
    @DisplayName("exists and delete - Should check existence and clean up keys")
    void testExistsAndDelete() {
        Room room = new Room("ROOM02", "Room", "u01", RoomStatus.WAITING, 4, 3, 60,
                List.of(new RoomPlayer("u01", "Alice")));

        repository.create(room);
        assertTrue(repository.exists("ROOM02"));

        repository.delete("ROOM02");
        assertFalse(repository.exists("ROOM02"));
        assertTrue(repository.findById("ROOM02").isEmpty());
    }

    @Test
    @DisplayName("addPlayer - Should atomically add player and prevent duplicate player or exceeding capacity")
    void testAddPlayer() {
        Room room = new Room("ROOM03", "Small Room", "u01", RoomStatus.WAITING, 2, 3, 60,
                List.of(new RoomPlayer("u01", "Alice")));
        repository.create(room);

        Room updated = repository.addPlayer("ROOM03", new RoomPlayer("u02", "Bob"));
        assertEquals(2, updated.players().size());

        assertThrows(PlayerAlreadyInRoomException.class, () ->
                repository.addPlayer("ROOM03", new RoomPlayer("u02", "Bob")));

        assertThrows(RoomFullException.class, () ->
                repository.addPlayer("ROOM03", new RoomPlayer("u03", "Charlie")));
    }

    @Test
    @DisplayName("removePlayer - Host leaves -> host migration; Last player leaves -> room deleted")
    void testRemovePlayer() {
        Room room = new Room("ROOM04", "Migration Room", "u01", RoomStatus.WAITING, 4, 3, 60,
                List.of(new RoomPlayer("u01", "Host"), new RoomPlayer("u02", "Guest1"), new RoomPlayer("u03", "Guest2")));
        repository.create(room);

        Optional<Room> afterHostLeavesOpt = repository.removePlayer("ROOM04", "u01");
        assertTrue(afterHostLeavesOpt.isPresent());
        Room afterHostLeaves = afterHostLeavesOpt.get();
        assertEquals("u02", afterHostLeaves.hostId());
        assertEquals(2, afterHostLeaves.players().size());

        Optional<Room> afterGuest1LeavesOpt = repository.removePlayer("ROOM04", "u02");
        assertTrue(afterGuest1LeavesOpt.isPresent());
        assertEquals("u03", afterGuest1LeavesOpt.get().hostId());

        Optional<Room> lastLeavesOpt = repository.removePlayer("ROOM04", "u03");
        assertTrue(lastLeavesOpt.isEmpty());
        assertFalse(repository.exists("ROOM04"));
    }

    @Test
    @DisplayName("CONCURRENCY - Room with 1 slot remaining, concurrent JOIN requests: only 1 succeeds")
    void testConcurrentJoin() throws InterruptedException {
        Room room = new Room("ROOM_CONC", "Concurrency Room", "u01", RoomStatus.WAITING, 2, 3, 60,
                List.of(new RoomPlayer("u01", "Host")));
        repository.create(room);

        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger roomFullCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final String playerId = "user_" + i;
            final String username = "Player_" + i;

            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    repository.addPlayer("ROOM_CONC", new RoomPlayer(playerId, username));
                    successCount.incrementAndGet();
                } catch (RoomFullException e) {
                    roomFullCount.incrementAndGet();
                } catch (Exception e) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Only 1 concurrent join request should succeed");
        assertEquals(9, roomFullCount.get(), "Remaining 9 concurrent requests should fail with RoomFullException");

        Room finalRoom = repository.findById("ROOM_CONC").orElseThrow();
        assertEquals(2, finalRoom.players().size(), "Final player count must not exceed maxPlayers");
    }
}
