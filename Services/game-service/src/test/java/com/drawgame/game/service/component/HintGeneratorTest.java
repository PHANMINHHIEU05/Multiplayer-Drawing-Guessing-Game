package com.drawgame.game.service.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HintGeneratorTest {

    private HintGenerator hintGenerator;

    @BeforeEach
    void setUp() {
        hintGenerator = new HintGenerator();
    }

    @Test
    void generateInitialHint_MasksAllLettersAndPreservesSpaces() {
        String hint = hintGenerator.generateInitialHint("con meo");
        assertTrue(hint.contains("_"));
        assertEquals(2, hint.split("   ").length); // 2 words separated by 3 spaces
    }

    @Test
    void renderHint_UsesCanonicalUnicodeCharactersAndKeepsSpacesAndSeparators() {
        assertEquals("_ r _ _   đ _ _", hintGenerator.renderHint("trái đất", Set.of(1, 5)));
        assertEquals("_ _ - _", hintGenerator.renderHint("ab-c", Set.of()));
    }

    @Test
    void revealSchedule_IsDeterministicCumulativeAndNeverShowsTheWholeAnswer() {
        List<Set<Integer>> first = hintGenerator.createRevealSchedule("trái đất", "game-1:1");
        List<Set<Integer>> second = hintGenerator.createRevealSchedule("trái đất", "game-1:1");

        assertEquals(first, second);
        assertEquals(3, first.size());
        assertTrue(first.get(0).size() <= first.get(1).size());
        assertTrue(first.get(1).size() <= first.get(2).size());
        assertTrue(first.get(2).size() < 7); // spaces excluded; one letter always remains hidden
    }

    @Test
    void hintGeneration_NormalizesDecomposedVietnameseToNfc() {
        String decomposed = Normalizer.normalize("trái đất", Normalizer.Form.NFD);
        assertEquals(hintGenerator.generateInitialHint("trái đất"), hintGenerator.generateInitialHint(decomposed));
        assertEquals("_ r _ _   đ _ _", hintGenerator.renderHint(decomposed, Set.of(1, 5)));
    }

    @Test
    void shortWordsReceiveAnEarlyHintWithoutEverRevealingTheWholeWord() {
        List<Set<Integer>> schedule = hintGenerator.createRevealSchedule("mèo", "short-word");
        assertEquals(1, schedule.get(0).size());
        assertTrue(schedule.get(2).size() < 3);
    }
}
