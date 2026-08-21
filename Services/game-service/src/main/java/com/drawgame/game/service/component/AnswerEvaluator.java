package com.drawgame.game.service.component;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class AnswerEvaluator {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    public enum Result {
        CORRECT,
        CLOSE,
        INCORRECT
    }

    /**
     * Checks whether guess matches secret word or any of its registered aliases.
     */
    public Result evaluate(String guess, String secretWord, List<String> aliases) {
        if (guess == null || secretWord == null || secretWord.trim().isEmpty()) {
            return Result.INCORRECT;
        }

        if (aliases == null) {
            aliases = Collections.emptyList();
        }

        String normalizedGuess = normalize(guess);
        String normalizedSecret = normalize(secretWord);

        // 1. Exact Match on Canonical Word
        if (normalizedGuess.equals(normalizedSecret)) {
            return Result.CORRECT;
        }

        // 2. Unaccented Match on Canonical Word
        String unaccentedGuess = stripAccents(normalizedGuess);
        String unaccentedSecret = stripAccents(normalizedSecret);
        if (unaccentedGuess.equals(unaccentedSecret)) {
            return Result.CORRECT;
        }

        // 3. Alias Match (Exact & Unaccented)
        for (String alias : aliases) {
            if (alias == null) continue;
            String normalizedAlias = normalize(alias);
            if (normalizedGuess.equals(normalizedAlias)) {
                return Result.CORRECT;
            }
            if (unaccentedGuess.equals(stripAccents(normalizedAlias))) {
                return Result.CORRECT;
            }
        }

        // 4. Fuzzy Check (Levenshtein) -> CLOSE (Does NOT award score automatically)
        if (isClose(unaccentedGuess, unaccentedSecret)) {
            return Result.CLOSE;
        }
        for (String alias : aliases) {
            if (alias != null && isClose(unaccentedGuess, stripAccents(normalize(alias)))) {
                return Result.CLOSE;
            }
        }

        return Result.INCORRECT;
    }

    public Result evaluate(String guess, String secretWord) {
        return evaluate(guess, secretWord, Collections.emptyList());
    }

    private String normalize(String str) {
        return str.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private String stripAccents(String str) {
        String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD);
        return DIACRITICS_PATTERN.matcher(nfdNormalizedString).replaceAll("").replace('đ', 'd').replace('Đ', 'd');
    }

    private boolean isClose(String str1, String str2) {
        int distance = computeLevenshteinDistance(str1, str2);
        return distance == 1 && str2.length() > 3;
    }

    private int computeLevenshteinDistance(String lhs, String rhs) {
        int[][] dp = new int[lhs.length() + 1][rhs.length() + 1];

        for (int i = 0; i <= lhs.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= rhs.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= lhs.length(); i++) {
            for (int j = 1; j <= rhs.length(); j++) {
                int cost = lhs.charAt(i - 1) == rhs.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[lhs.length()][rhs.length()];
    }
}
