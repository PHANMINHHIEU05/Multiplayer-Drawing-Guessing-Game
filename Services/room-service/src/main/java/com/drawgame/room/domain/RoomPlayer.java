package com.drawgame.room.domain;

public record RoomPlayer(
        String playerId,
        String username,
        boolean ready
) {
    /** TV10 legacy 2-arg constructor (not ready by default). */
    public RoomPlayer(String playerId, String username) {
        this(playerId, username, false);
    }
}