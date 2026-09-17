package com.drawgame.game.service.component;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Random;

@Component
public class HintGenerator {

    /**
     * Generates a masked hint string for a given secret word.
     * Spaces in multi-word terms remain visible.
     * Example: "con meo" -> "c _ _   m _ _"
     */
    public String generateInitialHint(String secretWord) {
        return renderHint(secretWord, Set.of());
    }

    /**
     * Creates three reproducible cumulative reveal sets indexed by Unicode code point,
     * not UTF-16 char. At most 30% of letters are revealed and one remains hidden.
     */
    public List<Set<Integer>> createRevealSchedule(String secretWord, String stableSeed) {
        String canonical = canonical(secretWord);
        List<Integer> letterPositions = new ArrayList<>();
        int position = 0;
        for (int codePoint : canonical.codePoints().toArray()) {
            if (Character.isLetterOrDigit(codePoint)) letterPositions.add(position);
            position++;
        }
        if (letterPositions.size() < 2) {
            return List.of(Set.of(), Set.of(), Set.of());
        }

        Collections.shuffle(letterPositions, new Random(String.valueOf(stableSeed).hashCode()));
        int maxReveal = letterPositions.size() - 1;
        int[] ratios = {10, 20, 30};
        List<Set<Integer>> stages = new ArrayList<>(3);
        Set<Integer> cumulative = new HashSet<>();
        for (int stage = 0; stage < ratios.length; stage++) {
            int target = Math.min(maxReveal, Math.max(1,
                    (int) Math.round(letterPositions.size() * ratios[stage] / 100.0)));
            while (cumulative.size() < target) cumulative.add(letterPositions.get(cumulative.size()));
            stages.add(Set.copyOf(cumulative));
        }
        return List.copyOf(stages);
    }

    /** Render a server-selected set of code-point positions as the public hint pattern. */
    public String renderHint(String secretWord, Set<Integer> revealedPositions) {
        String canonical = canonical(secretWord);
        if (canonical.isEmpty()) return "";
        Set<Integer> revealed = revealedPositions == null ? Set.of() : revealedPositions;
        StringBuilder pattern = new StringBuilder();
        int position = 0;
        for (int codePoint : canonical.codePoints().toArray()) {
            if (Character.isWhitespace(codePoint)) {
                pattern.append("   ");
            } else {
                if (position > 0 && !Character.isWhitespace(canonical.codePointBefore(canonical.offsetByCodePoints(0, position)))) {
                    pattern.append(' ');
                }
                if (Character.isLetterOrDigit(codePoint) && !revealed.contains(position)) {
                    pattern.append('_');
                } else {
                    pattern.appendCodePoint(codePoint);
                }
            }
            position++;
        }
        return pattern.toString().trim();
    }

    /**
     * Generates a progressively revealed hint with a given percentage of revealed characters.
     */
    public String generateProgressiveHint(String secretWord, double revealRatio) {
        double ratio = Math.min(0.3, Math.max(0, revealRatio));
        int stage = ratio >= 0.25 ? 2 : ratio >= 0.15 ? 1 : 0;
        return renderHint(secretWord, createRevealSchedule(secretWord, secretWord).get(stage));
    }

    private String canonical(String word) {
        return word == null ? "" : Normalizer.normalize(word, Normalizer.Form.NFC);
    }
}
