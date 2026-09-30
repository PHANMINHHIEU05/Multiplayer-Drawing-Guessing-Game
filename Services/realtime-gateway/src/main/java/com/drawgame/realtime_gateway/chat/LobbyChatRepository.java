package com.drawgame.realtime_gateway.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Repository
public class LobbyChatRepository {

    private static final Logger log = LoggerFactory.getLogger(LobbyChatRepository.class);
    private static final String LOBBY_CHAT_KEY = "chat:lobby:history";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final int maxHistory;

    @Autowired
    public LobbyChatRepository(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${lobby.chat.max-history:50}") int maxHistory) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.maxHistory = maxHistory;
    }

    public Mono<Void> appendMessage(Map<String, Object> messageMap) {
        if (redisTemplate == null) {
            return Mono.empty();
        }
        try {
            String json = objectMapper.writeValueAsString(messageMap);
            return redisTemplate.opsForList().rightPush(LOBBY_CHAT_KEY, json)
                    .flatMap(count -> {
                        if (count > maxHistory) {
                            return redisTemplate.opsForList().trim(LOBBY_CHAT_KEY, -maxHistory, -1);
                        }
                        return Mono.just(true);
                    })
                    .flatMap(res -> redisTemplate.expire(LOBBY_CHAT_KEY, Duration.ofDays(1)))
                    .then()
                    .onErrorResume(e -> {
                        log.warn("Failed to persist lobby chat message to Redis: {}", e.getMessage());
                        return Mono.empty();
                    });
        } catch (Exception e) {
            log.warn("Failed to serialize lobby chat message: {}", e.getMessage());
            return Mono.empty();
        }
    }

    public Flux<Map<String, Object>> getRecentMessages(int limit) {
        if (redisTemplate == null) {
            return Flux.empty();
        }
        int count = Math.min(Math.max(1, limit), maxHistory);
        return redisTemplate.opsForList().range(LOBBY_CHAT_KEY, -count, -1)
                .flatMap(json -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = objectMapper.readValue(json, Map.class);
                        return Mono.just(map);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize lobby chat message: {}", e.getMessage());
                        return Mono.empty();
                    }
                });
    }
}
