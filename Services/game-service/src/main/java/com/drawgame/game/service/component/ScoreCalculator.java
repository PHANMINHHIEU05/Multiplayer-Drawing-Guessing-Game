package com.drawgame.game.service.component;

import org.springframework.stereotype.Component;

@Component
public class ScoreCalculator {

    private static final int BASE_MAX_SCORE = 100;
    private static final int MIN_SCORE = 20;

    /**
     * Calculates score for a guesser based on time remaining and guess sequence order.
     * @param remainingSeconds Time left in seconds.
     * @param totalRoundSeconds Total duration of the round in seconds.
     * @param guessOrder 1-based order of correct guess (1st, 2nd, 3rd...).
     */
    public int calculateGuesserScore(long remainingSeconds, long totalRoundSeconds, int guessOrder) {
        if (totalRoundSeconds <= 0) {
            totalRoundSeconds = 60;
        }
        double timeFraction = Math.max(0.0, Math.min(1.0, (double) remainingSeconds / totalRoundSeconds));
        
        // Bonus deduction per additional guesser: 1st gets 100%, 2nd gets 90%, 3rd gets 80%, etc.
        double orderMultiplier = Math.max(0.5, 1.0 - (guessOrder - 1) * 0.1);

        int score = (int) Math.round(BASE_MAX_SCORE * timeFraction * orderMultiplier);
        return Math.max(MIN_SCORE, score);
    }

    /**
     * Calculates bonus points awarded to the drawer when a player guesses correctly.
     */
    public int calculateDrawerBonus(int totalPlayers) {
        return 25;
    }
}
