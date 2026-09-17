package com.drawgame.game.model;

import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameStateData {
    private String roomId;
    private String gameId;
    private String status; // WAITING, PLAYING, ROUND_IN_PROGRESS, ROUND_ENDED, FINISHED
    private String roundPhase;
    private int currentRound;
    private int totalRounds;
    private String drawerId;
    private String secretWord;
    private String hint;
    private long roundStartedAt;
    private long roundEndsAt;
    private long phaseStartedAt;
    private long phaseEndsAt;
    private int hintStage;

    /** Round length in seconds, sourced from the room configuration. */
    @Builder.Default
    private int roundDurationSeconds = 60;

    @Builder.Default
    private List<String> playerOrder = new ArrayList<>();

    @Builder.Default
    private List<PlayerScoreData> scores = new ArrayList<>();

    @Builder.Default
    private List<String> selectedCategories = new ArrayList<>();

    @Builder.Default
    private List<WordChoiceData> wordChoices = new ArrayList<>();

    @Builder.Default
    private List<Set<Integer>> hintRevealSchedule = new ArrayList<>();

    @Builder.Default
    private Map<String, Integer> roundStartScores = new HashMap<>();

    private RoundRecapData roundRecap;

    @Builder.Default
    private List<RoundRecapData> roundResults = new ArrayList<>();

    @Builder.Default
    private List<MatchAwardData> awards = new ArrayList<>();
}
