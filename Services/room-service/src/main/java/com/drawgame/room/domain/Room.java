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
        List<RoomPlayer> players,
        List<String> selectedCategories
) {
    public Room {
        players = players == null ? List.of() : List.copyOf(players);
        selectedCategories = selectedCategories == null
                ? RoomCategories.ALL
                : List.copyOf(selectedCategories);
    }

    /** Backward-compatible constructor for callers predating room categories. */
    public Room(String id, String name, String hostId, RoomStatus status,
                int maxPlayers, int roundCount, int roundDuration, List<RoomPlayer> players) {
        this(id, name, hostId, status, maxPlayers, roundCount, roundDuration, players, RoomCategories.ALL);
    }
}
