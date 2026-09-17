package com.drawgame.game.model;

public record MatchAwardData(
        String type,
        String label,
        String playerId,
        String username,
        int value,
        long elapsedMillis
) {}
