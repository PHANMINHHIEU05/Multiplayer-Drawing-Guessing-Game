package com.drawgame.game.service.component;

import com.drawgame.game.model.MatchAwardData;
import com.drawgame.game.model.PlayerScoreData;
import com.drawgame.game.model.RoundRecapData;
import com.drawgame.game.model.RoundScoreDeltaData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MatchAwardCalculatorTest {
    private final MatchAwardCalculator calculator = new MatchAwardCalculator();

    @Test
    void awardsUseAuthoritativeScoresRoundRecordsAndStableJoinOrderForTies() {
        List<PlayerScoreData> scores = List.of(score("p2", "An", 100), score("p1", "Minh", 100));
        List<RoundRecapData> rounds = List.of(
                round(1, "p1", 25, Map.of("p2", 420L)),
                round(2, "p1", 25, Map.of("p2", 310L)),
                round(3, "p2", 0, Map.of("p1", 350L)));

        List<MatchAwardData> awards = calculator.calculate(scores, rounds, List.of("p1", "p2"));

        assertEquals("p1", find(awards, "WINNER").playerId());
        assertEquals("p1", find(awards, "BEST_ARTIST").playerId());
        assertEquals("p2", find(awards, "FASTEST_GUESS").playerId());
        assertEquals("p2", find(awards, "MOST_CORRECT").playerId());
        assertEquals("p2", find(awards, "BEST_STREAK").playerId());
    }

    @Test
    void unsupportedAwardsAreOmittedInsteadOfInvented() {
        List<MatchAwardData> awards = calculator.calculate(
                List.of(score("p1", "Minh", 0)), List.of(), List.of("p1"));
        assertEquals(List.of("WINNER"), awards.stream().map(MatchAwardData::type).toList());
    }

    private static MatchAwardData find(List<MatchAwardData> awards, String type) {
        return awards.stream().filter(award -> award.type().equals(type)).findFirst().orElseThrow();
    }

    private static PlayerScoreData score(String id, String name, int score) {
        return PlayerScoreData.builder().playerId(id).username(name).score(score).build();
    }

    private static RoundRecapData round(int number, String drawer, int drawerScore, Map<String, Long> timings) {
        List<RoundScoreDeltaData> deltas = List.of(new RoundScoreDeltaData(drawer, drawer, drawerScore, drawerScore));
        String fastestId = timings.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
        long fastest = timings.values().stream().mapToLong(Long::longValue).min().orElse(-1L);
        return new RoundRecapData(number, drawer, "đáp án", deltas, drawerScore,
                fastestId, fastestId, fastest, List.copyOf(timings.keySet()), timings);
    }
}
