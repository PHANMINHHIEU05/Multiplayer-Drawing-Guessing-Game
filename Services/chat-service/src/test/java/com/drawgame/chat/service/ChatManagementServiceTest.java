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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatManagementServiceTest {

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private RoomGrpcClient roomGrpcClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ChatManagementService chatService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        chatService = new ChatManagementService(
                chatRepository,
                roomGrpcClient,
                redisTemplate,
                300,  // maxMessageLength
                50,   // defaultLimit
                100,  // maxLimit
                5,    // maxMessagesPerWindow
                2     // windowSeconds
        );
    }

    @Test
    void sendMessage_Success_TrimsContentAndUsesAuthoritativeUsername() {
        String roomId = "room-101";
        String playerId = "player-1";
        String clientUsername = "SpoofedAdmin";

        when(valueOperations.increment(anyString())).thenReturn(1L);

        RoomResponse mockRoom = RoomResponse.newBuilder()
                .setRoomId(roomId)
                .addPlayers(PlayerMessage.newBuilder().setPlayerId(playerId).setUsername("RealMinh").build())
                .build();
        when(roomGrpcClient.getRoom(roomId)).thenReturn(mockRoom);
        when(chatRepository.append(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessage result = chatService.sendMessage(roomId, playerId, clientUsername, "   hello tiếng Việt  ");

        assertNotNull(result);
        assertEquals(roomId, result.getRoomId());
        assertEquals(playerId, result.getPlayerId());
        assertEquals("RealMinh", result.getUsername()); // Authoritative username, spoof prevented
        assertEquals("hello tiếng Việt", result.getContent()); // Trimmed
        assertEquals(ChatMessageType.USER, result.getType());
        verify(chatRepository, times(1)).append(any(ChatMessage.class));
    }

    @Test
    void sendMessage_EmptyContent_ThrowsInvalidChatMessageException() {
        assertThrows(InvalidChatMessageException.class, () ->
                chatService.sendMessage("room-1", "p1", "User", "   ")
        );
    }

    @Test
    void sendMessage_TooLongContent_ThrowsInvalidChatMessageException() {
        String longContent = "a".repeat(301);
        assertThrows(InvalidChatMessageException.class, () ->
                chatService.sendMessage("room-1", "p1", "User", longContent)
        );
    }

    @Test
    void sendMessage_RoomNotFound_ThrowsChatRoomNotFoundException() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(roomGrpcClient.getRoom("room-404"))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND.withDescription("Room not found")));

        assertThrows(ChatRoomNotFoundException.class, () ->
                chatService.sendMessage("room-404", "p1", "User", "hello")
        );
    }

    @Test
    void sendMessage_PlayerNotInRoom_ThrowsChatPlayerNotInRoomException() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        RoomResponse mockRoom = RoomResponse.newBuilder()
                .setRoomId("room-101")
                .addPlayers(PlayerMessage.newBuilder().setPlayerId("other-player").setUsername("Other").build())
                .build();
        when(roomGrpcClient.getRoom("room-101")).thenReturn(mockRoom);

        assertThrows(ChatPlayerNotInRoomException.class, () ->
                chatService.sendMessage("room-101", "p1", "User", "hello")
        );
    }

    @Test
    void sendMessage_ExceedRateLimit_ThrowsChatRateLimitException() {
        when(valueOperations.increment(anyString())).thenReturn(6L); // > 5

        assertThrows(ChatRateLimitException.class, () ->
                chatService.sendMessage("room-101", "p1", "User", "hello")
        );
    }

    @Test
    void getRecentMessages_Success_AppliesLimitBounds() {
        String roomId = "room-101";
        String playerId = "p1";
        RoomResponse mockRoom = RoomResponse.newBuilder()
                .setRoomId(roomId)
                .addPlayers(PlayerMessage.newBuilder().setPlayerId(playerId).setUsername("Minh").build())
                .build();
        when(roomGrpcClient.getRoom(roomId)).thenReturn(mockRoom);
        when(chatRepository.findRecent(roomId, 100)).thenReturn(List.of());

        List<ChatMessage> result = chatService.getRecentMessages(roomId, playerId, 999); // max bound is 100

        assertNotNull(result);
        verify(chatRepository).findRecent(roomId, 100);
    }
}
