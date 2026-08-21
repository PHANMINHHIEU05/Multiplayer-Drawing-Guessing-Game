package com.drawgame.game.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "game_player_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GamePlayerResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_result_id", nullable = false)
    private GameResultEntity gameResult;

    @Column(name = "player_id", nullable = false, length = 50)
    private String playerId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(name = "final_score", nullable = false)
    private Integer finalScore;

    @Column(name = "rank", nullable = false)
    private Integer rank;
}
