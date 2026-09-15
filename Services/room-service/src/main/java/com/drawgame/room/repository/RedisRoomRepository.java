package com.drawgame.room.repository;

import com.drawgame.room.domain.Room;
import com.drawgame.room.domain.RoomPlayer;
import com.drawgame.room.domain.RoomStatus;
import com.drawgame.room.exception.InvalidRoomStateException;
import com.drawgame.room.exception.PlayerAlreadyInRoomException;
import com.drawgame.room.exception.RoomFullException;
import com.drawgame.room.exception.RoomNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisRoomRepository implements RoomRepository {

    private final StringRedisTemplate redis;
    private final long roomTtlSeconds;

    private static final RedisScript<Long> JOIN_ROOM_SCRIPT = new DefaultRedisScript<>(
            """
            local roomKey = KEYS[1]
            local playersKey = KEYS[2]
            local orderKey = KEYS[3]
            local playerId = ARGV[1]
            local username = ARGV[2]
            local ttl = tonumber(ARGV[3])

            if redis.call('EXISTS', roomKey) == 0 then
                return -1
            end

            local status = redis.call('HGET', roomKey, 'status')
            if status ~= 'WAITING' then
                return -2
            end

            if redis.call('HEXISTS', playersKey, playerId) == 1 then
                return -3
            end

            local maxPlayers = tonumber(redis.call('HGET', roomKey, 'maxPlayers'))
            local currentCount = tonumber(redis.call('HLEN', playersKey))
            if currentCount >= maxPlayers then
                return -4
            end

            redis.call('HSET', playersKey, playerId, username)
            redis.call('RPUSH', orderKey, playerId)

            if ttl > 0 then
                redis.call('EXPIRE', roomKey, ttl)
                redis.call('EXPIRE', playersKey, ttl)
                redis.call('EXPIRE', orderKey, ttl)
            end

            return 0
            """,
            Long.class
    );

    private static final RedisScript<Long> LEAVE_ROOM_SCRIPT = new DefaultRedisScript<>(
            """
            local roomKey = KEYS[1]
            local playersKey = KEYS[2]
            local orderKey = KEYS[3]
            local playerId = ARGV[1]
            local ttl = tonumber(ARGV[2])

            if redis.call('EXISTS', roomKey) == 0 then
                return -1
            end

            if redis.call('HEXISTS', playersKey, playerId) == 0 then
                return -2
            end

            redis.call('HDEL', playersKey, playerId)
            redis.call('LREM', orderKey, 0, playerId)

            local currentCount = tonumber(redis.call('HLEN', playersKey))
            if currentCount == 0 then
                redis.call('DEL', roomKey, playersKey, orderKey)
                return 1
            else
                local hostId = redis.call('HGET', roomKey, 'hostId')
                if hostId == playerId then
                    local newHostId = redis.call('LINDEX', orderKey, 0)
                    if newHostId then
                        redis.call('HSET', roomKey, 'hostId', newHostId)
                    end
                end
                if ttl > 0 then
                    redis.call('EXPIRE', roomKey, ttl)
                    redis.call('EXPIRE', playersKey, ttl)
                    redis.call('EXPIRE', orderKey, ttl)
                end
                return 0
            end
            """,
            Long.class
    );

    public RedisRoomRepository(
            StringRedisTemplate redis,
            @Value("${room.ttl:7200}") long roomTtlSeconds
    ) {
        this.redis = redis;
        this.roomTtlSeconds = roomTtlSeconds;
    }

    private String roomKey(String roomId) {
        return "room:" + roomId;
    }

    private String roomPlayersKey(String roomId) {
        return "room:" + roomId + ":players";
    }

    /** TV10: ready players set — lobby state, authoritative server-side. */
    private String roomReadyKey(String roomId) {
        return "room:" + roomId + ":ready";
    }

    private Set<String> readyPlayers(String roomId) {
        Set<String> members = redis.opsForSet().members(roomReadyKey(roomId));
        return members != null ? members : Collections.emptySet();
    }

    private String roomOrderKey(String roomId) {
        return "room:" + roomId + ":player-order";
    }

    @Override
    public Room create(Room room) {
        String key = roomKey(room.id());
        String playersKey = roomPlayersKey(room.id());
        String orderKey = roomOrderKey(room.id());

        Map<String, String> meta = Map.of(
                "name", room.name(),
                "hostId", room.hostId(),
                "status", room.status().name(),
                "maxPlayers", String.valueOf(room.maxPlayers()),
                "roundCount", String.valueOf(room.roundCount()),
                "roundDuration", String.valueOf(room.roundDuration())
        );

        redis.opsForHash().putAll(key, meta);
        redis.delete(List.of(playersKey, orderKey));

        if (room.players() != null) {
            for (RoomPlayer player : room.players()) {
                redis.opsForHash().put(playersKey, player.playerId(), player.username());
                redis.opsForList().rightPush(orderKey, player.playerId());
            }
        }

        if (roomTtlSeconds > 0) {
            redis.expire(key, roomTtlSeconds, TimeUnit.SECONDS);
            redis.expire(playersKey, roomTtlSeconds, TimeUnit.SECONDS);
            redis.expire(orderKey, roomTtlSeconds, TimeUnit.SECONDS);
        }

        return room;
    }

    @Override
    public Optional<Room> findById(String roomId) {
        String key = roomKey(roomId);
        Map<Object, Object> values = redis.opsForHash().entries(key);
        if (values.isEmpty() || !values.containsKey("name")) {
            return Optional.empty();
        }

        String playersKey = roomPlayersKey(roomId);
        String orderKey = roomOrderKey(roomId);

        Map<Object, Object> playersMap = new HashMap<>(redis.opsForHash().entries(playersKey));
        List<String> orderList = redis.opsForList().range(orderKey, 0, -1);

        Set<String> readySet = readyPlayers(roomId);

        List<RoomPlayer> roomPlayers = new ArrayList<>();
        if (orderList != null) {
            for (String pid : orderList) {
                Object usernameObj = playersMap.remove(pid);
                if (usernameObj != null) {
                    // TV10: join with authoritative ready state (host is implicitly ready)
                    String hostId = values.get("hostId").toString();
                    roomPlayers.add(new RoomPlayer(pid, usernameObj.toString(), readySet.contains(pid) || pid.equals(hostId)));
                }
            }
        }

        for (Map.Entry<Object, Object> entry : playersMap.entrySet()) {
            roomPlayers.add(new RoomPlayer(entry.getKey().toString(), entry.getValue().toString(),
                    readySet.contains(entry.getKey().toString())));
        }

        Room room = new Room(
                roomId,
                values.get("name").toString(),
                values.get("hostId").toString(),
                RoomStatus.valueOf(values.get("status").toString()),
                Integer.parseInt(values.get("maxPlayers").toString()),
                Integer.parseInt(values.get("roundCount").toString()),
                Integer.parseInt(values.get("roundDuration").toString()),
                roomPlayers
        );

        return Optional.of(room);
    }

    @Override
    public boolean exists(String roomId) {
        return Boolean.TRUE.equals(redis.hasKey(roomKey(roomId)));
    }

    @Override
    public void delete(String roomId) {
        redis.delete(List.of(
                roomKey(roomId),
                roomPlayersKey(roomId),
                roomOrderKey(roomId)
        ));
    }

    @Override
    public Room addPlayer(String roomId, RoomPlayer player) {
        List<String> keys = List.of(
                roomKey(roomId),
                roomPlayersKey(roomId),
                roomOrderKey(roomId)
        );

        Long result = redis.execute(
                JOIN_ROOM_SCRIPT,
                keys,
                player.playerId(),
                player.username(),
                String.valueOf(roomTtlSeconds)
        );

        if (result == null) {
            throw new IllegalStateException("Redis script execution returned null");
        }

        if (result == -1) {
            throw new RoomNotFoundException("Room not found: " + roomId);
        } else if (result == -2) {
            throw new InvalidRoomStateException("Room is not in WAITING state: " + roomId);
        } else if (result == -3) {
            throw new PlayerAlreadyInRoomException("Player " + player.playerId() + " is already in room " + roomId);
        } else if (result == -4) {
            throw new RoomFullException("Room " + roomId + " is full");
        }

        return findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found after join: " + roomId));
    }

    @Override
    public Optional<Room> removePlayer(String roomId, String playerId) {
        List<String> keys = List.of(
                roomKey(roomId),
                roomPlayersKey(roomId),
                roomOrderKey(roomId)
        );

        Long result = redis.execute(
                LEAVE_ROOM_SCRIPT,
                keys,
                playerId,
                String.valueOf(roomTtlSeconds)
        );

        if (result == null) {
            throw new IllegalStateException("Redis script execution returned null");
        }

        if (result == -1) {
            throw new RoomNotFoundException("Room not found: " + roomId);
        } else if (result == -2) {
            throw new IllegalArgumentException("Player " + playerId + " is not in room " + roomId);
        } else if (result == 1) {
            return Optional.empty();
        }

        return findById(roomId);
    }

    @Override
    public Room beginGame(String roomId, String hostId) {
        Room room = findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));
        if (!room.hostId().equals(hostId)) {
            throw new IllegalArgumentException("Requester is not host of room " + roomId);
        }
        if (room.status() != RoomStatus.WAITING) {
            throw new InvalidRoomStateException("Room is not in WAITING state: " + roomId);
        }
        // TV10: server-side readiness gate — every non-host member must be ready
        Set<String> ready = readyPlayers(roomId);
        for (RoomPlayer p : room.players()) {
            if (!p.playerId().equals(room.hostId()) && !ready.contains(p.playerId())) {
                throw new InvalidRoomStateException(
                        "Player " + p.username() + " is not ready");
            }
        }

        redis.opsForHash().put(roomKey(roomId), "status", RoomStatus.PLAYING.name());
        // ready state consumed by the match start
        redis.delete(roomReadyKey(roomId));
        return findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found after status update: " + roomId));
    }

    @Override
    public Room setReady(String roomId, String playerId, boolean ready) {
        Room room = findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));
        boolean isMember = room.players().stream().anyMatch(p -> p.playerId().equals(playerId));
        if (!isMember) {
            throw new IllegalArgumentException("Player " + playerId + " is not a member of room " + roomId);
        }
        if (room.status() != RoomStatus.WAITING) {
            throw new InvalidRoomStateException("Room is not in WAITING state: " + roomId);
        }

        if (ready) {
            redis.opsForSet().add(roomReadyKey(roomId), playerId);
        } else {
            redis.opsForSet().remove(roomReadyKey(roomId), playerId);
        }
        if (roomTtlSeconds > 0) {
            redis.expire(roomReadyKey(roomId), roomTtlSeconds, TimeUnit.SECONDS);
        }
        return findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found after ready update: " + roomId));
    }

    /**
     * TV10 REMATCH: FINISHED -> WAITING. Host-only. Preserves room code, name,
     * membership, host, and configuration; clears ready state so the lobby
     * starts fresh. Old match result is already persisted separately by
     * Game Service finishGame (never mutated here).
     */
    @Override
    public Room resetRoom(String roomId, String requesterId) {
        Room room = findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));
        if (!room.hostId().equals(requesterId)) {
            throw new IllegalArgumentException("Requester is not host of room " + roomId);
        }
        if (room.status() != RoomStatus.FINISHED) {
            throw new InvalidRoomStateException("Room is not in FINISHED state: " + roomId);
        }

        redis.opsForHash().put(roomKey(roomId), "status", RoomStatus.WAITING.name());
        redis.delete(roomReadyKey(roomId));
        return findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found after reset: " + roomId));
    }

    /**
     * TV10 KICK: host removes another member (WAITING-only by design — mid-round
     * kick would entangle score/scheduler consistency). Host cannot kick self.
     */
    @Override
    public Room kickPlayer(String roomId, String requesterId, String targetPlayerId) {
        Room room = findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));
        if (!room.hostId().equals(requesterId)) {
            throw new IllegalArgumentException("Requester is not host of room " + roomId);
        }
        if (requesterId.equals(targetPlayerId)) {
            throw new IllegalArgumentException("Host cannot kick themselves — use Leave");
        }
        if (room.status() != RoomStatus.WAITING) {
            throw new InvalidRoomStateException("Kicking is only allowed while the room is waiting: " + roomId);
        }
        boolean isMember = room.players().stream().anyMatch(p -> p.playerId().equals(targetPlayerId));
        if (!isMember) {
            throw new IllegalArgumentException("Target " + targetPlayerId + " is not a member of room " + roomId);
        }

        removePlayer(roomId, targetPlayerId);
        redis.opsForSet().remove(roomReadyKey(roomId), targetPlayerId);
        return findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found after kick: " + roomId));
    }

    @Override
    public Room finishGame(String roomId) {
        Room room = findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));

        redis.opsForHash().put(roomKey(roomId), "status", RoomStatus.FINISHED.name());
        return findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found after finish: " + roomId));
    }
}