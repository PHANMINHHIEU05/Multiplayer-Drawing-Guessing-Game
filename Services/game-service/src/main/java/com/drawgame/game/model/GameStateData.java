package com.drawgame.game.model;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameStateData {
    private String roomId;
    private String status; // WAITING, PLAYING, ROUND_IN_PROGRESS, ROUND_ENDED, FINISHED
    private int currentRound;
    private int totalRounds;
    private String drawerId;
    private String secretWord;
    private String hint;
    private long roundStartedAt;
    private long roundEndsAt;

    @Builder.Default
    private List<String> playerOrder = new ArrayList<>();

    @Builder.Default
    private List<PlayerScoreData> scores = new ArrayList<>();
}
