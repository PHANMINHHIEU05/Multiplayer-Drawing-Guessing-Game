package com.drawgame.game.service.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnswerEvaluatorTest {

    private AnswerEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AnswerEvaluator();
    }

    @Test
    void evaluate_ExactCanonicalMatch_ReturnsCorrect() {
        AnswerEvaluator.Result result = evaluator.evaluate("con meo", "con meo");
        assertEquals(AnswerEvaluator.Result.CORRECT, result);
    }

    @Test
    void evaluate_CaseInsensitiveAndUnaccented_ReturnsCorrect() {
        AnswerEvaluator.Result result = evaluator.evaluate("CON MEO", "con mèo");
        assertEquals(AnswerEvaluator.Result.CORRECT, result);
    }

    @Test
    void evaluate_AliasMatch_ReturnsCorrect() {
        List<String> aliases = Arrays.asList("meo", "con miu");
        AnswerEvaluator.Result result = evaluator.evaluate("meo", "con meo", aliases);
        assertEquals(AnswerEvaluator.Result.CORRECT, result);
    }

    @Test
    void evaluate_CloseMatch_ReturnsClose() {
        AnswerEvaluator.Result result = evaluator.evaluate("con meoo", "con meo");
        assertEquals(AnswerEvaluator.Result.CLOSE, result);
    }

    @Test
    void evaluate_WrongGuess_ReturnsIncorrect() {
        AnswerEvaluator.Result result = evaluator.evaluate("con cho", "con meo");
        assertEquals(AnswerEvaluator.Result.INCORRECT, result);
    }
}
