package com.drawgame.game.repository;

import com.drawgame.game.entity.WordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WordRepository extends JpaRepository<WordEntity, Long> {

    @Query(value = "SELECT * FROM words ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<WordEntity> findRandomWord();

    Optional<WordEntity> findByWord(String word);

    @Query("SELECT wa.alias FROM WordAliasEntity wa WHERE wa.word.word = :canonicalWord")
    List<String> findAliasesByCanonicalWord(@Param("canonicalWord") String canonicalWord);
}
