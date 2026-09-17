package com.drawgame.game.model;

import java.util.List;
import java.util.Map;

public record RoundRecapData(
        int roundNumber,
        String drawerId,
        String answer,
        List<RoundScoreDeltaData> scoreDeltas,
        int drawerScore,
        String fastestPlayerId,
        String fastestUsername,
        long fastestElapsedMillis,
        List<String> correctPlayerIds,
        Map<String, Long> correctGuessTimesMillis
) {
    public RoundRecapData {
        scoreDeltas = scoreDeltas == null ? List.of() : List.copyOf(scoreDeltas);
        correctPlayerIds = correctPlayerIds == null ? List.of() : List.copyOf(correctPlayerIds);
        correctGuessTimesMillis = correctGuessTimesMillis == null ? Map.of() : Map.copyOf(correctGuessTimesMillis);
    }
}
