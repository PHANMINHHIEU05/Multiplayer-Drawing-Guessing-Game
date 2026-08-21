package com.drawgame.chat.service;

import com.drawgame.chat.client.RoomGrpcClient;
import com.drawgame.chat.domain.ChatMessage;
import com.drawgame.chat.domain.ChatMessageType;
import com.drawgame.chat.exception.ChatPlayerNotInRoomException;
import com.drawgame.chat.exception.ChatRateLimitException;
import com.drawgame.chat.exception.ChatRoomNotFoundException;
import com.drawgame.chat.exception.InvalidChatMessageException;
import com.drawgame.chat.repository.ChatRepository;
import com.drawgame.room.grpc.generated.PlayerMessage;
import com.drawgame.room.grpc.generated.RoomResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ChatManagementService {

    private final ChatRepository chatRepository;
    private final RoomGrpcClient roomGrpcClient;
    private final StringRedisTemplate redisTemplate;

    private final int maxMessageLength;
    private final int defaultLimit;
    private final int maxLimit;
    private final int maxMessagesPerWindow;
    private final int windowSeconds;

    public ChatManagementService(
            ChatRepository chatRepository,
            RoomGrpcClient roomGrpcClient,
            StringRedisTemplate redisTemplate,
            @Value("${chat.max-message-length:300}") int maxMessageLength,
            @Value("${chat.history.default-limit:50}") int defaultLimit,
            @Value("${chat.history.max-limit:100}") int maxLimit,
            @Value("${chat.rate-limit.max-messages:5}") int maxMessagesPerWindow,
            @Value("${chat.rate-limit.window-seconds:2}") int windowSeconds
    ) {
        this.chatRepository = chatRepository;
        this.roomGrpcClient = roomGrpcClient;
        this.redisTemplate = redisTemplate;
        this.maxMessageLength = maxMessageLength;
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
        this.maxMessagesPerWindow = maxMessagesPerWindow;
        this.windowSeconds = windowSeconds;
    }

    public ChatMessage sendMessage(String roomId, String playerId, String clientUsername, String content) {
        // 1. Input validation
        if (roomId == null || roomId.isBlank()) {
            throw new InvalidChatMessageException("roomId cannot be blank");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new InvalidChatMessageException("playerId cannot be blank");
        }
        if (content == null) {
            throw new InvalidChatMessageException("content cannot be null");
        }

        String trimmed = content.trim();
        if (trimmed.isBlank()) {
            throw new InvalidChatMessageException("ChatMessage content cannot be empty or whitespace");
        }
        if (trimmed.length() > maxMessageLength) {
            throw new InvalidChatMessageException("ChatMessage content exceeds max length of " + maxMessageLength);
        }

        // 2. Rate limiting check
        checkRateLimit(roomId, playerId);

        // 3. Membership validation & authoritative username resolution via Room Service
        String authoritativeUsername = validateRoomMembershipAndGetUsername(roomId, playerId, clientUsername);

        // 4. Create ChatMessage domain model
        ChatMessage message = ChatMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .roomId(roomId)
                .playerId(playerId)
                .username(authoritativeUsername)
                .content(trimmed)
                .type(ChatMessageType.USER)
                .createdAt(Instant.now())
                .build();

        // 5. Append to repository
        chatRepository.append(message);

        log.info("CHAT_MESSAGE_ACCEPTED: messageId={}, roomId={}, playerId={}",
                message.getMessageId(), roomId, playerId);

        return message;
    }

    public List<ChatMessage> getRecentMessages(String roomId, String playerId, Integer limit) {
        if (roomId == null || roomId.isBlank()) {
            throw new InvalidChatMessageException("roomId cannot be blank");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new InvalidChatMessageException("playerId cannot be blank");
        }

        // Validate membership
        validateRoomMembershipAndGetUsername(roomId, playerId, null);

        int actualLimit = (limit == null || limit <= 0) ? defaultLimit : limit;
        if (actualLimit > maxLimit) {
            actualLimit = maxLimit;
        }

        List<ChatMessage> messages = chatRepository.findRecent(roomId, actualLimit);
        log.info("CHAT_HISTORY_READ: roomId={}, playerId={}, count={}", roomId, playerId, messages.size());
        return messages;
    }

    private void checkRateLimit(String roomId, String playerId) {
        String rateKey = "chat:rate:" + roomId + ":" + playerId;
        Long count = redisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            redisTemplate.expire(rateKey, Duration.ofSeconds(windowSeconds));
        }
        if (count != null && count > maxMessagesPerWindow) {
            log.warn("CHAT_RATE_LIMITED: roomId={}, playerId={}", roomId, playerId);
            throw new ChatRateLimitException("You are sending messages too quickly");
        }
    }

    private String validateRoomMembershipAndGetUsername(String roomId, String playerId, String clientUsername) {
        RoomResponse roomResponse;
        try {
            roomResponse = roomGrpcClient.getRoom(roomId);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                log.warn("CHAT_MESSAGE_REJECTED: Room not found {}", roomId);
                throw new ChatRoomNotFoundException("Room not found: " + roomId);
            }
            log.error("Error communicating with Room Service for room {}", roomId, e);
            throw new ChatRoomNotFoundException("Failed to verify room: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error contacting Room Service for room {}", roomId, e);
            throw new ChatRoomNotFoundException("Failed to verify room: " + e.getMessage());
        }

        if (roomResponse == null || roomResponse.getRoomId().isBlank()) {
            throw new ChatRoomNotFoundException("Room not found: " + roomId);
        }

        PlayerMessage playerMsg = roomResponse.getPlayersList().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("CHAT_MESSAGE_REJECTED: Player {} not in room {}", playerId, roomId);
                    return new ChatPlayerNotInRoomException("Player " + playerId + " is not in room " + roomId);
                });

        String authoritativeUsername = playerMsg.getUsername();
        if (authoritativeUsername == null || authoritativeUsername.isBlank()) {
            authoritativeUsername = (clientUsername != null && !clientUsername.isBlank())
                    ? clientUsername
                    : "Player-" + playerId;
        }

        return authoritativeUsername;
    }
}
