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

    void delete(String roomId);
}