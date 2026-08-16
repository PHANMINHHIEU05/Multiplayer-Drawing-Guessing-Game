package com.drawgame.room.domain;

import java.util.List;

public record Room(
        String id,
        String name,
        String hostId,
        RoomStatus status,
        int maxPlayers,
        int roundCount,
        int roundDuration,
        List<RoomPlayer> players
) {
}