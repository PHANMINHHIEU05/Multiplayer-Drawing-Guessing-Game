package com.drawgame.game.service.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TV4 Workstream 2 — Comprehensive Unit Test Suite for AnswerEvaluator
 * Covers Canonical, Aliases, Normalization, Vietnamese Diacritics, Unicode NFC/NFD,
 * Levenshtein distance thresholds, and Boundary / Edge cases.
 */
class AnswerEvaluatorTest {

    private AnswerEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AnswerEvaluator();
    }

    @Nested
    @DisplayName("Canonical & Exact Matches")
    class CanonicalMatchTests {

        @Test
        @DisplayName("Case A: Exact canonical match with diacritics -> CORRECT")
        void testExactCanonicalMatch() {
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("máy bay", "máy bay"));
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("con mèo", "con mèo"));
        }

        @Test
        @DisplayName("Case A: Case-insensitive and extra whitespace normalization -> CORRECT")
        void testCaseAndWhitespaceNormalization() {
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("  Máy   Bay  ", "máy bay"));
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("CON   MÈO", "con mèo"));
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("   con    mèo   ", "  CON   MÈO  "));
        }

        @Test
        @DisplayName("Punctuation normalization in guesses -> CORRECT")
        void testPunctuationHandling() {
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("máy bay!", "máy bay"));
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("máy bay???", "máy bay"));
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("máy-bay", "máy bay"));
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("\"máy bay\"", "máy bay"));
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("máy bay...", "máy bay"));
        }

        @Test
        @DisplayName("Unaccented Vietnamese canonical match -> WRONG (strict diacritic rule)")
        void testUnaccentedCanonicalMatch() {
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("may bay", "máy bay"));
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("con meo", "con mèo"));
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("xe dap", "xe đạp"));
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("dong ho", "đồng hồ"));
        }

        @Test
        @DisplayName("Wrong diacritic on canonical word -> WRONG")
        void testWrongDiacriticMatch() {
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("ngói nhà", "ngôi nhà"));
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("ngồi nhà", "ngôi nhà"));
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("căn nhà", "ngôi nhà"));
        }
    }

    @Nested
    @DisplayName("Alias & Synonym Matches")
    class AliasMatchTests {

        private final List<String> planeAliases = Arrays.asList("phi cơ", "tàu bay", "phi co");
        private final List<String> catAliases = Arrays.asList("mèo", "con miu", "bé mèo");

        @Test
        @DisplayName("Case B: Exact alias match -> CLOSE (aliases never produce CORRECT)")
        void testExactAliasMatch() {
            assertEquals(AnswerEvaluator.Result.CLOSE, evaluator.evaluate("phi cơ", "máy bay", planeAliases));
            assertEquals(AnswerEvaluator.Result.CLOSE, evaluator.evaluate("con miu", "con mèo", catAliases));
        }

        @Test
        @DisplayName("Case B: Unaccented alias match -> CLOSE")
        void testUnaccentedAliasMatch() {
            assertEquals(AnswerEvaluator.Result.CLOSE, evaluator.evaluate("PHI CO", "máy bay", planeAliases));
            assertEquals(AnswerEvaluator.Result.CLOSE, evaluator.evaluate("phi co", "máy bay", planeAliases));
            assertEquals(AnswerEvaluator.Result.CLOSE, evaluator.evaluate("  tau   bay  ", "máy bay", planeAliases));
        }

        @Test
        @DisplayName("Alias list containing nulls, empty strings, or duplicates")
        void testMessyAliasList() {
            List<String> messyAliases = Arrays.asList(null, "", "   ", "phi cơ", "PHI CƠ", null);
            assertEquals(AnswerEvaluator.Result.CLOSE, evaluator.evaluate("phi cơ", "máy bay", messyAliases));
            // "may bay" differs from "máy bay" only by diacritics -> WRONG (not rescued by aliases)
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("may bay", "máy bay", messyAliases));
        }
    }

    @Nested
    @DisplayName("Fuzzy & Typo Tolerance (Levenshtein Distance)")
    class FuzzyMatchTests {

        @Test
        @DisplayName("Case C: Single typo in medium words (length 4..7) -> CLOSE")
        void testTypoInMediumWord() {
            // "máy bai" has edit distance 1 from "máy bay" -> CLOSE
            assertEquals(AnswerEvaluator.Result.CLOSE, evaluator.evaluate("máy bai", "máy bay"));
            assertEquals(AnswerEvaluator.Result.CLOSE, evaluator.evaluate("con meoo", "con mèo"));
            assertEquals(AnswerEvaluator.Result.CLOSE, evaluator.evaluate("xe dapx", "xe đạp"));
        }

        @Test
        @DisplayName("Typo in long word (length >= 8) -> CLOSE")
        void testTypoInLongWord() {
            // "may bay truc thangg" vs "máy bay trực thăng" (edit distance 1) -> CLOSE
            assertEquals(AnswerEvaluator.Result.CLOSE, evaluator.evaluate("may bay truc thangg", "máy bay trực thăng"));
            assertEquals(AnswerEvaluator.Result.CLOSE, evaluator.evaluate("khung log", "khủng long"));
        }

        @Test
        @DisplayName("Typo on registered alias -> CLOSE")
        void testTypoOnAlias() {
            List<String> aliases = List.of("phi cơ");
            assertEquals(AnswerEvaluator.Result.CLOSE, evaluator.evaluate("phi coi", "máy bay", aliases));
        }

        @Test
        @DisplayName("Short words (<= 3 characters) MUST NOT trigger fuzzy match")
        void testShortWordsDoNotFuzzyMatch() {
            // "ba" vs "ca" has distance 1, but length <= 3 -> MUST BE WRONG
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("ba", "ca"));
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("ca", "ga"));
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("cho", "bo"));

            // Exact match on short words still works; missing diacritic does not
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("bò", "bò"));
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("ca", "cá"));
        }

        @Test
        @DisplayName("Excessive edit distance beyond threshold -> WRONG")
        void testTooManyErrorsIsWrong() {
            // "máy kéo" vs "máy bay" differs by 3 chars -> WRONG
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("máy kéo", "máy bay"));
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("con thỏ", "con mèo"));
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("xe hơi", "xe máy"));
        }
    }

    @Nested
    @DisplayName("Unicode Normalization (NFC vs NFD)")
    class UnicodeNormalizationTests {

        @Test
        @DisplayName("NFC (composed) and NFD (decomposed) forms match identically")
        void testNfcAndNfdEquivalence() {
            String composed = "máy bay"; // Precomposed NFC
            String decomposed = Normalizer.normalize("máy bay", Normalizer.Form.NFD); // Decomposed NFD

            assertNotEquals(composed, decomposed, "NFC and NFD raw strings must differ in byte sequence");
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate(composed, decomposed));
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate(decomposed, composed));
        }

        @Test
        @DisplayName("Vietnamese specific characters: đ and Đ")
        void testVietnameseDWithStroke() {
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("đồng hồ", "đồng hồ"));
            // Unaccented forms no longer match — strict diacritic rule
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("dong ho", "đồng hồ"));
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("ĐỒNG HỒ", "dong ho"));
        }
    }

    @Nested
    @DisplayName("QA Matrix: Canonical 'trái đất' (QA-005 -> QA-014)")
    class TraiDatQaMatrixTests {

        private final String canonical = "trái đất";
        private final List<String> aliases = Arrays.asList("địa cầu", "quả đất");

        @Test
        @DisplayName("QA-005: 'trái đất' -> CORRECT")
        void testExactAccent() {
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("trái đất", canonical, aliases));
        }

        @Test
        @DisplayName("QA-006: 'Trái Đất' -> CORRECT")
        void testCapitalization() {
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("Trái Đất", canonical, aliases));
        }

        @Test
        @DisplayName("QA-007: '  trái    đất ' -> CORRECT")
        void testExtraWhitespace() {
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("  trái    đất ", canonical, aliases));
        }

        @Test
        @DisplayName("QA-008: NFD decomposed equivalent -> CORRECT")
        void testNfdDecomposed() {
            String nfd = Normalizer.normalize(canonical, Normalizer.Form.NFD);
            assertNotEquals(canonical, nfd);
            assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate(nfd, canonical, aliases));
        }

        @Test
        @DisplayName("QA-009: 'trai dat' -> WRONG (unaccented)")
        void testUnaccented() {
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("trai dat", canonical, aliases));
        }

        @Test
        @DisplayName("QA-010: 'trái dat' -> WRONG (partially accented)")
        void testPartiallyAccentedFirstWord() {
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("trái dat", canonical, aliases));
        }

        @Test
        @DisplayName("QA-011: 'trai đất' -> WRONG (partially accented)")
        void testPartiallyAccentedSecondWord() {
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("trai đất", canonical, aliases));
        }

        @Test
        @DisplayName("QA-012: Wrong accent variant -> WRONG")
        void testWrongAccentVariant() {
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("trại đất", canonical, aliases));
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("trải đắt", canonical, aliases));
        }

        @Test
        @DisplayName("QA-013: Alias/synonym -> NOT CORRECT (returns CLOSE)")
        void testAliasSynonym() {
            assertEquals(AnswerEvaluator.Result.CLOSE, evaluator.evaluate("địa cầu", canonical, aliases));
            assertEquals(AnswerEvaluator.Result.CLOSE, evaluator.evaluate("quả đất", canonical, aliases));
            assertNotEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("địa cầu", canonical, aliases));
        }

        @Test
        @DisplayName("QA-014: Typo -> CLOSE only if fuzzy qualifies, never CORRECT")
        void testTypoClose() {
            // Edit distance 1 on unaccented form ("trai dat" length 8 -> max distance 2)
            // e.g. "trai dat" typo: "trai day" -> unaccented distance 1 -> CLOSE
            AnswerEvaluator.Result res = evaluator.evaluate("trái đật", canonical, aliases);
            assertNotEquals(AnswerEvaluator.Result.CORRECT, res);
        }
    }

    @Nested
    @DisplayName("Negative, Null, and Boundary Safety")
    class SafetyAndNegativeTests {

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t\n", "   \n\r  "})
        @DisplayName("Empty or whitespace-only guess -> WRONG")
        void testEmptyGuesses(String emptyGuess) {
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate(emptyGuess, "máy bay"));
        }

        @Test
        @DisplayName("Null guess or secret word -> WRONG without throwing exception")
        void testNullHandling() {
            assertDoesNotThrow(() -> {
                assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate(null, "máy bay"));
                assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("máy bay", null));
                assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate(null, null));
                // Matching canonical with null aliases safely passes
                assertEquals(AnswerEvaluator.Result.CORRECT, evaluator.evaluate("máy bay", "máy bay", null));
                // Non-matching guess with null aliases returns WRONG
                assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("con vịt", "máy bay", null));
            });
        }

        @Test
        @DisplayName("Completely wrong guess -> WRONG")
        void testCompletelyWrongGuess() {
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("con cá", "ngôi nhà"));
            assertEquals(AnswerEvaluator.Result.WRONG, evaluator.evaluate("hello world", "bông hoa"));
        }
    }

    @Nested
    @DisplayName("Algorithm Distance Unit Verification")
    class LevenshteinUnitTests {

        @Test
        void testLevenshteinComputation() {
            assertEquals(0, evaluator.computeLevenshteinDistance("test", "test"));
            assertEquals(1, evaluator.computeLevenshteinDistance("test", "tent"));
            assertEquals(1, evaluator.computeLevenshteinDistance("test", "tests"));
            assertEquals(1, evaluator.computeLevenshteinDistance("tests", "test"));
            assertEquals(3, evaluator.computeLevenshteinDistance("kitten", "sitting"));
        }
    }
}
