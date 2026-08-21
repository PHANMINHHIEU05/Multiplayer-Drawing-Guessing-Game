package com.drawgame.chat.grpc;

import com.drawgame.chat.domain.ChatMessage;
import com.drawgame.chat.domain.ChatMessageType;
import com.drawgame.chat.exception.ChatPlayerNotInRoomException;
import com.drawgame.chat.exception.ChatRateLimitException;
import com.drawgame.chat.exception.ChatRoomNotFoundException;
import com.drawgame.chat.exception.InvalidChatMessageException;
import com.drawgame.chat.grpc.generated.ChatMessageResponse;
import com.drawgame.chat.grpc.generated.SendMessageRequest;
import com.drawgame.chat.service.ChatManagementService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatGrpcServiceTest {

    @Mock
    private ChatManagementService chatService;

    @Mock
    private ChatGrpcMapper mapper;

    @Mock
    private StreamObserver<ChatMessageResponse> responseObserver;

    private ChatGrpcService grpcService;

    @BeforeEach
    void setUp() {
        grpcService = new ChatGrpcService(chatService, mapper);
    }

    @Test
    void sendMessage_Success_CallsOnNextAndOnCompleted() {
        SendMessageRequest request = SendMessageRequest.newBuilder()
                .setRoomId("room-1")
                .setPlayerId("p1")
                .setUsername("Minh")
                .setContent("Hello")
                .build();

        ChatMessage domainMsg = ChatMessage.builder()
                .messageId("m1")
                .roomId("room-1")
                .playerId("p1")
                .username("Minh")
                .content("Hello")
                .type(ChatMessageType.USER)
                .createdAt(Instant.now())
                .build();

        ChatMessageResponse responseMsg = ChatMessageResponse.newBuilder()
                .setMessageId("m1")
                .setRoomId("room-1")
                .setPlayerId("p1")
                .setUsername("Minh")
                .setContent("Hello")
                .setType("USER")
                .build();

        when(chatService.sendMessage("room-1", "p1", "Minh", "Hello")).thenReturn(domainMsg);
        when(mapper.toResponse(domainMsg)).thenReturn(responseMsg);

        grpcService.sendMessage(request, responseObserver);

        verify(responseObserver).onNext(responseMsg);
        verify(responseObserver).onCompleted();
    }

    @Test
    void sendMessage_RoomNotFoundException_MapsToNotFound() {
        SendMessageRequest request = SendMessageRequest.newBuilder().setRoomId("r1").setPlayerId("p1").setContent("c").build();
        when(chatService.sendMessage(any(), any(), any(), any()))
                .thenThrow(new ChatRoomNotFoundException("Room not found"));

        grpcService.sendMessage(request, responseObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(captor.capture());
        assertTrue(captor.getValue() instanceof StatusRuntimeException);
        StatusRuntimeException ex = (StatusRuntimeException) captor.getValue();
        assertEquals(Status.Code.NOT_FOUND, ex.getStatus().getCode());
    }

    @Test
    void sendMessage_PlayerNotInRoomException_MapsToPermissionDenied() {
        SendMessageRequest request = SendMessageRequest.newBuilder().setRoomId("r1").setPlayerId("p1").setContent("c").build();
        when(chatService.sendMessage(any(), any(), any(), any()))
                .thenThrow(new ChatPlayerNotInRoomException("Player not in room"));

        grpcService.sendMessage(request, responseObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(captor.capture());
        StatusRuntimeException ex = (StatusRuntimeException) captor.getValue();
        assertEquals(Status.Code.PERMISSION_DENIED, ex.getStatus().getCode());
    }

    @Test
    void sendMessage_RateLimitException_MapsToResourceExhausted() {
        SendMessageRequest request = SendMessageRequest.newBuilder().setRoomId("r1").setPlayerId("p1").setContent("c").build();
        when(chatService.sendMessage(any(), any(), any(), any()))
                .thenThrow(new ChatRateLimitException("Rate limit exceeded"));

        grpcService.sendMessage(request, responseObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(captor.capture());
        StatusRuntimeException ex = (StatusRuntimeException) captor.getValue();
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, ex.getStatus().getCode());
    }

    @Test
    void sendMessage_InvalidChatMessageException_MapsToInvalidArgument() {
        SendMessageRequest request = SendMessageRequest.newBuilder().setRoomId("r1").setPlayerId("p1").setContent("c").build();
        when(chatService.sendMessage(any(), any(), any(), any()))
                .thenThrow(new InvalidChatMessageException("Invalid message"));

        grpcService.sendMessage(request, responseObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(captor.capture());
        StatusRuntimeException ex = (StatusRuntimeException) captor.getValue();
        assertEquals(Status.Code.INVALID_ARGUMENT, ex.getStatus().getCode());
    }
}
