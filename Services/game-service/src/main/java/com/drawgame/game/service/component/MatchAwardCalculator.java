package com.drawgame.game.service.component;

import com.drawgame.game.model.MatchAwardData;
import com.drawgame.game.model.PlayerScoreData;
import com.drawgame.game.model.RoundRecapData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class MatchAwardCalculator {
    public List<MatchAwardData> calculate(List<PlayerScoreData> scores,
                                          List<RoundRecapData> rounds,
                                          List<String> playerOrder) {
        if (scores == null || scores.isEmpty()) return List.of();
        List<String> stableOrder = playerOrder == null ? List.of() : playerOrder;
        Map<String, Integer> order = new HashMap<>();
        for (int i = 0; i < stableOrder.size(); i++) order.putIfAbsent(stableOrder.get(i), i);
        Comparator<PlayerScoreData> stablePlayerOrder = Comparator
                .comparingInt((PlayerScoreData score) -> order.getOrDefault(score.getPlayerId(), Integer.MAX_VALUE))
                .thenComparing(PlayerScoreData::getPlayerId);
        List<PlayerScoreData> ranked = scores.stream()
                .sorted(Comparator.comparingInt(PlayerScoreData::getScore).reversed().thenComparing(stablePlayerOrder))
                .toList();
        PlayerScoreData winner = ranked.get(0);
        List<MatchAwardData> awards = new ArrayList<>();
        awards.add(award("WINNER", "Người chiến thắng", winner, winner.getScore(), -1));

        List<RoundRecapData> completedRounds = (rounds == null ? List.<RoundRecapData>of() : rounds).stream()
                .sorted(Comparator.comparingInt(RoundRecapData::roundNumber)).toList();
        Map<String, Integer> artistPoints = new HashMap<>();
        Map<String, Integer> distinctCorrectRounds = new HashMap<>();
        Map<String, Set<Integer>> playerCorrectRounds = new HashMap<>();
        RoundRecapData fastest = null;
        for (RoundRecapData round : completedRounds) {
            if (round.drawerScore() > 0) artistPoints.merge(round.drawerId(), round.drawerScore(), Integer::sum);
            for (String playerId : new HashSet<>(round.correctPlayerIds())) {
                distinctCorrectRounds.merge(playerId, 1, Integer::sum);
                playerCorrectRounds.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(round.roundNumber());
            }
            if (round.fastestPlayerId() != null && !round.fastestPlayerId().isBlank()
                    && round.fastestElapsedMillis() >= 0
                    && (fastest == null || round.fastestElapsedMillis() < fastest.fastestElapsedMillis()
                        || (round.fastestElapsedMillis() == fastest.fastestElapsedMillis()
                            && (round.roundNumber() < fastest.roundNumber()
                                || (round.roundNumber() == fastest.roundNumber()
                                    && order.getOrDefault(round.fastestPlayerId(), Integer.MAX_VALUE)
                                        < order.getOrDefault(fastest.fastestPlayerId(), Integer.MAX_VALUE)))))) {
                fastest = round;
            }
        }

        PlayerScoreData bestArtist = bestPlayer(scores, artistPoints, stablePlayerOrder);
        if (bestArtist != null && artistPoints.getOrDefault(bestArtist.getPlayerId(), 0) > 0) {
            awards.add(award("BEST_ARTIST", "Họa sĩ xuất sắc", bestArtist,
                    artistPoints.get(bestArtist.getPlayerId()), -1));
        }
        if (fastest != null) {
            RoundRecapData fastestRound = fastest;
            String name = completedRounds.stream()
                    .flatMap(round -> round.scoreDeltas().stream())
                    .filter(delta -> delta.playerId().equals(fastestRound.fastestPlayerId()))
                    .map(delta -> delta.username()).filter(value -> value != null && !value.isBlank())
                    .findFirst().orElse(fastestRound.fastestUsername());
            awards.add(new MatchAwardData("FASTEST_GUESS", "Đoán nhanh nhất", fastestRound.fastestPlayerId(),
                    name == null ? fastestRound.fastestPlayerId() : name, 0, fastestRound.fastestElapsedMillis()));
        }

        PlayerScoreData mostCorrect = bestPlayer(scores, distinctCorrectRounds, stablePlayerOrder);
        if (mostCorrect != null && distinctCorrectRounds.getOrDefault(mostCorrect.getPlayerId(), 0) > 0) {
            awards.add(award("MOST_CORRECT", "Đoán đúng nhiều nhất", mostCorrect,
                    distinctCorrectRounds.get(mostCorrect.getPlayerId()), -1));
        }

        Map<String, Integer> streaks = longestStreaks(scores, completedRounds, playerCorrectRounds);
        PlayerScoreData bestStreak = bestPlayer(scores, streaks, stablePlayerOrder);
        if (bestStreak != null && streaks.getOrDefault(bestStreak.getPlayerId(), 0) >= 2) {
            awards.add(award("BEST_STREAK", "Chuỗi đúng tốt nhất", bestStreak,
                    streaks.get(bestStreak.getPlayerId()), -1));
        }
        return List.copyOf(awards);
    }

    private static Map<String, Integer> longestStreaks(List<PlayerScoreData> scores,
                                                        List<RoundRecapData> rounds,
                                                        Map<String, Set<Integer>> correct) {
        Map<String, Integer> best = new HashMap<>();
        Map<String, Integer> current = new HashMap<>();
        for (PlayerScoreData player : scores) {
            int lastRound = -1;
            for (RoundRecapData round : rounds) {
                int length = correct.getOrDefault(player.getPlayerId(), Set.of()).contains(round.roundNumber())
                        ? (lastRound == round.roundNumber() - 1 ? current.getOrDefault(player.getPlayerId(), 0) + 1 : 1)
                        : 0;
                current.put(player.getPlayerId(), length);
                best.merge(player.getPlayerId(), length, Math::max);
                lastRound = round.roundNumber();
            }
        }
        return best;
    }

    private static PlayerScoreData bestPlayer(List<PlayerScoreData> scores, Map<String, Integer> values,
                                               Comparator<PlayerScoreData> stableOrder) {
        return scores.stream().max(Comparator
                .comparingInt((PlayerScoreData score) -> values.getOrDefault(score.getPlayerId(), 0))
                .thenComparing(stableOrder.reversed())).orElse(null);
    }

    private static MatchAwardData award(String type, String label, PlayerScoreData player,
                                        int value, long elapsedMillis) {
        return new MatchAwardData(type, label, player.getPlayerId(),
                player.getUsername() == null ? player.getPlayerId() : player.getUsername(), value, elapsedMillis);
    }
}
