package com.drawgame.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "game_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 50)
    private String roomId;

    @Column(name = "winner_id", length = 50)
    private String winnerId;

    @Column(name = "winner_username", length = 100)
    private String winnerUsername;

    @Column(name = "total_rounds", nullable = false)
    private Integer totalRounds;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @OneToMany(mappedBy = "gameResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GamePlayerResultEntity> playerResults = new ArrayList<>();
}
