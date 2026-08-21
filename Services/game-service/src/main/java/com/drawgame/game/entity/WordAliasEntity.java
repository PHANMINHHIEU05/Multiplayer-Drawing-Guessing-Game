package com.drawgame.game.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "word_aliases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WordAliasEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private WordEntity word;

    @Column(nullable = false, unique = true, length = 100)
    private String alias;
}
