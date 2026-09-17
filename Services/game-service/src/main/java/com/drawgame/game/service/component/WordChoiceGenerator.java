package com.drawgame.game.service.component;

import com.drawgame.game.entity.WordEntity;
import com.drawgame.game.model.WordChoiceData;
import com.drawgame.game.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WordChoiceGenerator {
    private final WordRepository wordRepository;

    public List<WordChoiceData> generate(List<String> selectedCategories, String stableSeed) {
        LinkedHashMap<String, String> uniqueWords = new LinkedHashMap<>();
        if (selectedCategories != null && !selectedCategories.isEmpty()) {
            wordRepository.findByCategoryIn(selectedCategories).forEach(word -> addCanonical(uniqueWords, word));
        }
        if (uniqueWords.size() < 3) {
            log.warn("Selected word pool is smaller than 3; supplementing globally enabled words (seed={})", stableSeed);
            wordRepository.findAll().forEach(word -> addCanonical(uniqueWords, word));
        }
        if (uniqueWords.size() < 3) {
            throw new IllegalStateException("At least three distinct canonical words are required");
        }

        List<String> words = new ArrayList<>(uniqueWords.values());
        words.sort(String::compareTo);
        java.util.Collections.shuffle(words, new Random(String.valueOf(stableSeed).hashCode()));
        return words.subList(0, 3).stream()
                .map(word -> new WordChoiceData(UUID.randomUUID().toString(), word))
                .toList();
    }

    private static void addCanonical(LinkedHashMap<String, String> destination, WordEntity entity) {
        if (entity == null || entity.getWord() == null || entity.getWord().isBlank()) return;
        String word = Normalizer.normalize(entity.getWord().trim(), Normalizer.Form.NFC);
        String key = Normalizer.normalize(word, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        destination.putIfAbsent(key, word);
    }
}
