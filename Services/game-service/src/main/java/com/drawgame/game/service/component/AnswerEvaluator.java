package com.drawgame.game.service.component;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * TV4 Stabilization — AnswerEvaluator
 * Evaluates player guesses against the canonical secret word.
 * Strict pipeline: Normalization -> Exact Canonical Match -> Diacritic-Only Guard -> Fuzzy Match -> Alias Match.
 *
 * <p>Current gameplay rules (Part D):
 * <ul>
 *   <li>CORRECT requires normalized exact canonical equality (diacritics preserved).</li>
 *   <li>Guesses differing from the secret word only by Vietnamese diacritics ("ngoi nha", "ngói nhà") are WRONG.</li>
 *   <li>Fuzzy matching on unaccented forms may return CLOSE, never CORRECT.</li>
 *   <li>Aliases/synonyms never produce CORRECT; an exact alias match returns CLOSE.</li>
 * </ul>
 */
@Component
public class AnswerEvaluator {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[\\p{Punct}\\p{IsPunctuation}]+");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    public static final int MIN_WORD_LENGTH_FOR_FUZZY = 4;
    public static final int MAX_EDIT_DISTANCE_SHORT = 1; // For words of length 4..7
    public static final int MAX_EDIT_DISTANCE_LONG = 2;  // For words of length >= 8

    public enum Result {
        CORRECT,
        CLOSE,
        WRONG,
        INCORRECT // Kept as alias for backward compatibility
    }

    /**
     * Evaluates whether guess matches the canonical secret word under strict diacritic rules.
     *
     * @param guess raw guess text from player
     * @param secretWord canonical secret word for current round
     * @param aliases list of registered synonyms / alternative spellings (never CORRECT)
     * @return Result.CORRECT, Result.CLOSE, or Result.WRONG
     */
    public Result evaluate(String guess, String secretWord, List<String> aliases) {
        if (guess == null || secretWord == null || secretWord.trim().isEmpty()) {
            return Result.WRONG;
        }

        String normalizedGuess = normalize(guess);
        String normalizedSecret = normalize(secretWord);

        if (normalizedGuess.isEmpty() || normalizedSecret.isEmpty()) {
            return Result.WRONG;
        }

        if (aliases == null) {
            aliases = Collections.emptyList();
        }

        // 1. Exact Match on Canonical Word (diacritics preserved) — the only path to CORRECT
        if (normalizedGuess.equals(normalizedSecret)) {
            return Result.CORRECT;
        }

        // 2. Diacritic-only difference guard: "ngoi nha" / "ngói nhà" vs "ngôi nhà" -> WRONG
        String unaccentedGuess = stripAccents(normalizedGuess);
        String unaccentedSecret = stripAccents(normalizedSecret);
        if (unaccentedGuess.equals(unaccentedSecret)) {
            return Result.WRONG;
        }

        // 3. Fuzzy Check on unaccented forms (Levenshtein distance) -> CLOSE (Does NOT award score)
        // Guard against false positives on short words (<= 3 chars, e.g. "ba" vs "ca")
        if (isClose(unaccentedGuess, unaccentedSecret)) {
            return Result.CLOSE;
        }

        // 4. Aliases never produce CORRECT: exact or unaccented alias match -> CLOSE, fuzzy alias match -> CLOSE
        for (String alias : aliases) {
            if (alias == null || alias.trim().isEmpty()) continue;
            String normalizedAlias = normalize(alias);
            String unaccentedAlias = stripAccents(normalizedAlias);
            if (normalizedGuess.equals(normalizedAlias)
                    || unaccentedGuess.equals(unaccentedAlias)
                    || isClose(unaccentedGuess, unaccentedAlias)) {
                return Result.CLOSE;
            }
        }

        return Result.WRONG;
    }

    public Result evaluate(String guess, String secretWord) {
        return evaluate(guess, secretWord, Collections.emptyList());
    }

    /**
     * Normalizes text by converting to NFC, lowercase, stripping punctuation, and collapsing whitespace.
     */
    public String normalize(String str) {
        if (str == null) return "";
        // 1. NFC normalization to ensure composed Unicode
        String nfc = Normalizer.normalize(str, Normalizer.Form.NFC);
        // 2. Safe lowercase
        String lower = nfc.toLowerCase(Locale.ROOT);
        // 3. Replace punctuation with space
        String withoutPunct = PUNCTUATION_PATTERN.matcher(lower).replaceAll(" ");
        // 4. Collapse whitespace and trim
        return WHITESPACE_PATTERN.matcher(withoutPunct).replaceAll(" ").trim();
    }

    /**
     * Strips Vietnamese and Latin diacritics / accent marks.
     */
    public String stripAccents(String str) {
        if (str == null) return "";
        String nfd = Normalizer.normalize(str, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS_PATTERN.matcher(nfd).replaceAll("");
        return withoutDiacritics
                .replace('đ', 'd')
                .replace('Đ', 'd');
    }

    /**
     * Determines whether str1 is a close typo of target str2.
     */
    private boolean isClose(String str1, String str2) {
        int targetLen = str2.length();
        if (targetLen < MIN_WORD_LENGTH_FOR_FUZZY || str1.length() < MIN_WORD_LENGTH_FOR_FUZZY - 1) {
            return false;
        }

        int distance = computeLevenshteinDistance(str1, str2);
        if (targetLen <= 7) {
            return distance == MAX_EDIT_DISTANCE_SHORT;
        } else {
            return distance >= 1 && distance <= MAX_EDIT_DISTANCE_LONG;
        }
    }

    /**
     * Standard 2-row Levenshtein distance computation with O(min(M, N)) space optimization.
     */
    public int computeLevenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) return Integer.MAX_VALUE;
        int m = s1.length();
        int n = s2.length();

        if (m == 0) return n;
        if (n == 0) return m;

        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            char c1 = s1.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                char c2 = s2.charAt(j - 1);
                int cost = (c1 == c2) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            System.arraycopy(curr, 0, prev, 0, n + 1);
        }

        return prev[n];
    }
}
