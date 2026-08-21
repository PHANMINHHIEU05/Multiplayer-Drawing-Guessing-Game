package com.drawgame.game.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerScoreData {
    private String playerId;
    private String username;
    private int score;
    private boolean hasGuessed;
}
