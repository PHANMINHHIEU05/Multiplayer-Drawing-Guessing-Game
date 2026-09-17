package com.drawgame.game.repository;

import com.drawgame.game.entity.GameResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;

@Repository
public interface GameResultRepository extends JpaRepository<GameResultEntity, Long> {
    List<GameResultEntity> findByRoomId(String roomId);

    Optional<GameResultEntity> findByGameId(String gameId);

    @EntityGraph(attributePaths = "playerResults")
    Optional<GameResultEntity> findTopByRoomIdOrderByIdDesc(String roomId);
}
