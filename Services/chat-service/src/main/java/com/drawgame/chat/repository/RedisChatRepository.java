package com.drawgame.chat.repository;

import com.drawgame.chat.domain.ChatMessage;
import com.drawgame.chat.domain.ChatMessageType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisZSetCommands.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Repository
public class RedisChatRepository implements ChatRepository {

    private final StringRedisTemplate redisTemplate;
    private final long maxLength;
    private final long ttlSeconds;

    public RedisChatRepository(
            StringRedisTemplate redisTemplate,
            @Value("${chat.history.max-length:200}") long maxLength,
            @Value("${chat.history.ttl-seconds:7200}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.maxLength = maxLength;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public ChatMessage append(ChatMessage message) {
        String key = getStreamKey(message.getRoomId());

        Map<String, String> body = new HashMap<>();
        body.put("messageId", message.getMessageId() != null ? message.getMessageId() : "");
        body.put("roomId", message.getRoomId() != null ? message.getRoomId() : "");
        body.put("playerId", message.getPlayerId() != null ? message.getPlayerId() : "");
        body.put("username", message.getUsername() != null ? message.getUsername() : "");
        body.put("content", message.getContent() != null ? message.getContent() : "");
        body.put("type", message.getType() != null ? message.getType().name() : ChatMessageType.USER.name());
        body.put("createdAt", message.getCreatedAt() != null
                ? String.valueOf(message.getCreatedAt().toEpochMilli())
                : String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForStream().add(key, body);
        redisTemplate.opsForStream().trim(key, maxLength, true);
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));

        log.debug("Appended chat message {} to stream {}", message.getMessageId(), key);
        return message;
    }

    @Override
    public List<ChatMessage> findRecent(String roomId, int limit) {
        String key = getStreamKey(roomId);
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            return Collections.emptyList();
        }

        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));

        List<MapRecord<String, String, String>> records = redisTemplate.<String, String>opsForStream().reverseRange(
                key,
                Range.unbounded(),
                Limit.limit().count(limit)
        );

        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChatMessage> messages = new ArrayList<>();
        for (MapRecord<String, String, String> record : records) {
            Map<String, String> valueMap = record.getValue();
            ChatMessage msg = ChatMessage.builder()
                    .messageId(valueMap.get("messageId"))
                    .roomId(valueMap.get("roomId"))
                    .playerId(valueMap.get("playerId"))
                    .username(valueMap.get("username"))
                    .content(valueMap.get("content"))
                    .type(parseType(valueMap.get("type")))
                    .createdAt(parseInstant(valueMap.get("createdAt")))
                    .build();
            messages.add(msg);
        }

        // Reverse to return oldest -> newest
        Collections.reverse(messages);
        return messages;
    }

    private String getStreamKey(String roomId) {
        return "chat:" + roomId + ":messages";
    }

    private ChatMessageType parseType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            return ChatMessageType.USER;
        }
        try {
            return ChatMessageType.valueOf(typeStr);
        } catch (Exception e) {
            return ChatMessageType.USER;
        }
    }

    private Instant parseInstant(String epochStr) {
        if (epochStr == null || epochStr.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(epochStr));
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
