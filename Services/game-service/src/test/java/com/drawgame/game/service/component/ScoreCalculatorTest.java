package com.drawgame.game.service.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreCalculatorTest {

    private ScoreCalculator scoreCalculator;

    @BeforeEach
    void setUp() {
        scoreCalculator = new ScoreCalculator();
    }

    @Test
    void calculateGuesserScore_FirstGuesserWithFullTime_ReturnsHighScore() {
        int score = scoreCalculator.calculateGuesserScore(60, 60, 1);
        assertTrue(score >= 90);
    }

    @Test
    void calculateGuesserScore_LaterGuesserWithLessTime_ReturnsLowerScore() {
        int score1 = scoreCalculator.calculateGuesserScore(60, 60, 1);
        int score2 = scoreCalculator.calculateGuesserScore(30, 60, 2);
        assertTrue(score1 > score2);
    }

    @Test
    void calculateDrawerBonus_ReturnsPositiveBonus() {
        int bonus = scoreCalculator.calculateDrawerBonus(4);
        assertTrue(bonus > 0);
    }
}
