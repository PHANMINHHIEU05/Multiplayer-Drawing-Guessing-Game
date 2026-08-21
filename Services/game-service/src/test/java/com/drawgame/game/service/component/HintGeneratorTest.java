package com.drawgame.game.service.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
