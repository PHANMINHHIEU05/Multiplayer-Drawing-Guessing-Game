package com.drawgame.chat.grpc;

import com.drawgame.chat.domain.ChatMessage;
import com.drawgame.chat.exception.ChatPlayerNotInRoomException;
import com.drawgame.chat.exception.ChatRateLimitException;
import com.drawgame.chat.exception.ChatRoomNotFoundException;
import com.drawgame.chat.exception.InvalidChatMessageException;
import com.drawgame.chat.grpc.generated.ChatMessageResponse;
import com.drawgame.chat.grpc.generated.ChatServiceGrpc;
import com.drawgame.chat.grpc.generated.GetRecentMessagesRequest;
import com.drawgame.chat.grpc.generated.GetRecentMessagesResponse;
import com.drawgame.chat.grpc.generated.SendMessageRequest;
import com.drawgame.chat.service.ChatManagementService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

import java.util.List;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class ChatGrpcService extends ChatServiceGrpc.ChatServiceImplBase {

    private final ChatManagementService chatService;
    private final ChatGrpcMapper mapper;

    @Override
    public void sendMessage(
            SendMessageRequest request,
            StreamObserver<ChatMessageResponse> responseObserver
    ) {
        try {
            ChatMessage domainMsg = chatService.sendMessage(
                    request.getRoomId(),
                    request.getPlayerId(),
                    request.getUsername(),
                    request.getContent()
            );

            ChatMessageResponse response = mapper.toResponse(domainMsg);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void getRecentMessages(
            GetRecentMessagesRequest request,
            StreamObserver<GetRecentMessagesResponse> responseObserver
    ) {
        try {
            List<ChatMessage> domainMessages = chatService.getRecentMessages(
                    request.getRoomId(),
                    request.getPlayerId(),
                    request.getLimit()
            );

            List<ChatMessageResponse> protoMessages = domainMessages.stream()
                    .map(mapper::toResponse)
                    .toList();

            GetRecentMessagesResponse response = GetRecentMessagesResponse.newBuilder()
                    .addAllMessages(protoMessages)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    private void handleException(Exception e, StreamObserver<?> responseObserver) {
        if (e instanceof InvalidChatMessageException || e instanceof IllegalArgumentException) {
            log.warn("gRPC INVALID_ARGUMENT: {}", e.getMessage());
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } else if (e instanceof ChatRoomNotFoundException) {
            log.warn("gRPC NOT_FOUND: {}", e.getMessage());
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } else if (e instanceof ChatPlayerNotInRoomException) {
            log.warn("gRPC PERMISSION_DENIED: {}", e.getMessage());
            responseObserver.onError(
                    Status.PERMISSION_DENIED
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } else if (e instanceof ChatRateLimitException) {
            log.warn("gRPC RESOURCE_EXHAUSTED: {}", e.getMessage());
            responseObserver.onError(
                    Status.RESOURCE_EXHAUSTED
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } else {
            log.error("gRPC INTERNAL: Unexpected error", e);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Internal server error")
                            .asRuntimeException()
            );
        }
    }
}
