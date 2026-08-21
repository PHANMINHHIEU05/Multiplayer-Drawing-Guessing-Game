package com.drawgame.room.service;

import com.drawgame.room.domain.Room;
import com.drawgame.room.domain.RoomPlayer;
import com.drawgame.room.domain.RoomStatus;
import com.drawgame.room.exception.RoomNotFoundException;
import com.drawgame.room.repository.RoomRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RoomManagementService {

    private static final Logger log = LoggerFactory.getLogger(RoomManagementService.class);

    private final RoomRepository repository;
    private final RoomCodeGenerator roomCodeGenerator;

    public RoomManagementService(
            RoomRepository repository,
            RoomCodeGenerator roomCodeGenerator
    ) {
        this.repository = repository;
        this.roomCodeGenerator = roomCodeGenerator;
    }

    public Room createRoom(
            String hostId,
            String username,
            String roomName,
            int maxPlayers,
            int roundCount,
            int roundDuration
    ) {
        validateCreateRoom(hostId, username, roomName, maxPlayers, roundCount, roundDuration);

        String roomId = generateUniqueRoomId();
        RoomPlayer host = new RoomPlayer(hostId, username);
        Room room = new Room(
                roomId,
                roomName,
                hostId,
                RoomStatus.WAITING,
                maxPlayers,
                roundCount,
                roundDuration,
                List.of(host)
        );

        Room savedRoom = repository.create(room);
        log.info("ROOM_CREATED roomId={} hostId={} maxPlayers={}", roomId, hostId, maxPlayers);
        return savedRoom;
    }

    public Room getRoom(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("Room ID cannot be blank");
        }
        return repository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found: " + roomId));
    }

    public Room joinRoom(String roomId, String playerId, String username) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("Room ID cannot be blank");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("Player ID cannot be blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }

        Room updatedRoom = repository.addPlayer(roomId, new RoomPlayer(playerId, username));
        log.info("PLAYER_JOINED roomId={} playerId={} username={}", roomId, playerId, username);
        return updatedRoom;
    }

    public Optional<Room> leaveRoom(String roomId, String playerId) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("Room ID cannot be blank");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("Player ID cannot be blank");
        }

        Optional<Room> oldRoomOpt = repository.findById(roomId);

        Optional<Room> updatedRoomOpt = repository.removePlayer(roomId, playerId);
        if (updatedRoomOpt.isEmpty()) {
            log.info("ROOM_DELETED roomId={} reason=last_player_left", roomId);
        } else {
            Room updatedRoom = updatedRoomOpt.get();
            log.info("PLAYER_LEFT roomId={} playerId={}", roomId, playerId);
            if (oldRoomOpt.isPresent() && !oldRoomOpt.get().hostId().equals(updatedRoom.hostId())) {
                log.info("HOST_CHANGED roomId={} newHostId={}", roomId, updatedRoom.hostId());
            }
        }
        return updatedRoomOpt;
    }

    public Room beginGame(String roomId, String requesterId) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("Room ID cannot be blank");
        }
        if (requesterId == null || requesterId.isBlank()) {
            throw new IllegalArgumentException("Requester ID cannot be blank");
        }

        Room room = repository.beginGame(roomId, requesterId);
        log.info("ROOM_GAME_STARTED roomId={} hostId={}", roomId, requesterId);
        return room;
    }

    public Room finishGame(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException("Room ID cannot be blank");
        }

        Room room = repository.finishGame(roomId);
        log.info("ROOM_GAME_FINISHED roomId={}", roomId);
        return room;
    }

    private String generateUniqueRoomId() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String id = roomCodeGenerator.generate();
            if (!repository.exists(id)) {
                return id;
            }
        }
        throw new IllegalStateException("Unable to generate unique room id");
    }

    private void validateCreateRoom(
            String hostId,
            String username,
            String roomName,
            int maxPlayers,
            int roundCount,
            int roundDuration
    ) {
        if (hostId == null || hostId.isBlank()) {
            throw new IllegalArgumentException("Host id is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }
        if (roomName == null || roomName.isBlank()) {
            throw new IllegalArgumentException("Room name is required");
        }
        if (maxPlayers < 2 || maxPlayers > 10) {
            throw new IllegalArgumentException("maxPlayers must be between 2 and 10");
        }
        if (roundCount < 1 || roundCount > 10) {
            throw new IllegalArgumentException("roundCount must be between 1 and 10");
        }
        if (roundDuration < 30 || roundDuration > 180) {
            throw new IllegalArgumentException("roundDuration must be between 30 and 180 seconds");
        }
    }
}