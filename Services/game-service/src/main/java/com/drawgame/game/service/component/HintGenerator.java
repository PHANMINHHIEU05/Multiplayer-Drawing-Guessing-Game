package com.drawgame.game.service.component;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

@Component
public class HintGenerator {

    private final Random random = new Random();

    /**
     * Generates a masked hint string for a given secret word.
     * Spaces in multi-word terms remain visible.
     * Example: "con meo" -> "c _ _   m _ _"
     */
    public String generateInitialHint(String secretWord) {
        if (secretWord == null || secretWord.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : secretWord.toCharArray()) {
            if (Character.isWhitespace(c)) {
                sb.append("   ");
            } else {
                sb.append("_ ");
            }
        }
        return sb.toString().trim();
    }

    /**
     * Generates a progressively revealed hint with a given percentage of revealed characters.
     */
    public String generateProgressiveHint(String secretWord, double revealRatio) {
        if (secretWord == null || secretWord.isEmpty()) {
            return "";
        }

        int totalLetters = 0;
        for (char c : secretWord.toCharArray()) {
            if (!Character.isWhitespace(c)) {
                totalLetters++;
            }
        }

        int lettersToReveal = (int) Math.round(totalLetters * Math.min(revealRatio, 0.5));
        Set<Integer> revealedIndices = new HashSet<>();

        while (revealedIndices.size() < lettersToReveal) {
            int idx = random.nextInt(secretWord.length());
            if (!Character.isWhitespace(secretWord.charAt(idx))) {
                revealedIndices.add(idx);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < secretWord.length(); i++) {
            char c = secretWord.charAt(i);
            if (Character.isWhitespace(c)) {
                sb.append("   ");
            } else if (revealedIndices.contains(i)) {
                sb.append(c).append(" ");
            } else {
                sb.append("_ ");
            }
        }
        return sb.toString().trim();
    }
}
