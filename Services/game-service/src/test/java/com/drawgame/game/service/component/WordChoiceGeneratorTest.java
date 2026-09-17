package com.drawgame.game.service.component;

import com.drawgame.game.entity.WordEntity;
import com.drawgame.game.model.WordChoiceData;
import com.drawgame.game.repository.WordRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class WordChoiceGeneratorTest {
    @Test
    void selectsThreeDistinctCanonicalWordsFromSelectedCategoryUnion() {
        WordRepository repository = mock(WordRepository.class);
        List<WordEntity> pool = List.of(word("con mèo", "ANIMALS"), word("bánh mì", "FOOD"), word("robot", "TECHNOLOGY"));
        when(repository.findByCategoryIn(anyCollection())).thenReturn(pool);

        List<WordChoiceData> choices = new WordChoiceGenerator(repository)
                .generate(List.of("ANIMALS", "FOOD", "TECHNOLOGY"), "game-a:1");

        assertEquals(3, choices.size());
        assertEquals(Set.of("con mèo", "bánh mì", "robot"),
                choices.stream().map(WordChoiceData::displayWord).collect(Collectors.toSet()));
        assertEquals(3, choices.stream().map(WordChoiceData::choiceId).distinct().count());
        verify(repository, never()).findAll();
    }

    @Test
    void supplementsSmallSelectedPoolFromGlobalPoolAndDeduplicatesCanonicalNfcWords() {
        WordRepository repository = mock(WordRepository.class);
        when(repository.findByCategoryIn(anyCollection())).thenReturn(List.of(
                word("trái đất", "NATURE"),
                word(Normalizer.normalize("trái đất", Normalizer.Form.NFD), "NATURE")));
        when(repository.findAll()).thenReturn(List.of(
                word("trái đất", "NATURE"), word("con mèo", "ANIMALS"),
                word("bánh mì", "FOOD"), word("con mèo", "ANIMALS")));

        List<WordChoiceData> choices = new WordChoiceGenerator(repository)
                .generate(List.of("NATURE"), "game-a:2");

        assertEquals(3, choices.size());
        assertEquals(3, choices.stream().map(WordChoiceData::displayWord).distinct().count());
        verify(repository).findAll();
    }

    private static WordEntity word(String word, String category) {
        return WordEntity.builder().word(word).category(category).build();
    }
}
