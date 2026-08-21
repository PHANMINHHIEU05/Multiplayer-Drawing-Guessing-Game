package com.drawgame.room.repository;

import com.drawgame.room.domain.Room;
import com.drawgame.room.domain.RoomPlayer;

import java.util.Optional;

public interface RoomRepository {

    Room create(Room room);

    Optional<Room> findById(String roomId);

    boolean exists(String roomId);

    Room addPlayer(String roomId, RoomPlayer player);

    Optional<Room> removePlayer(String roomId, String playerId);

    Room beginGame(String roomId, String hostId);

    Room finishGame(String roomId);

    void delete(String roomId);
}