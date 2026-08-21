package com.drawgame.realtime_gateway.grpc;

import com.drawgame.chat.grpc.generated.ChatMessageResponse;
import com.drawgame.chat.grpc.generated.ChatServiceGrpc;
import com.drawgame.chat.grpc.generated.GetRecentMessagesRequest;
import com.drawgame.chat.grpc.generated.GetRecentMessagesResponse;
import com.drawgame.chat.grpc.generated.SendMessageRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
public class ChatGrpcClient {

    @Value("${grpc.client.chat-service.host:localhost}")
    private String chatServiceHost;

    @Value("${grpc.client.chat-service.port:9093}")
    private int chatServicePort;

    private ManagedChannel channel;
    private ChatServiceGrpc.ChatServiceBlockingStub blockingStub;

    @PostConstruct
    public void init() {
        log.info("Connecting to Chat Service gRPC at {}:{}", chatServiceHost, chatServicePort);
        this.channel = ManagedChannelBuilder.forAddress(chatServiceHost, chatServicePort)
                .usePlaintext()
                .build();
        this.blockingStub = ChatServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }

    public Mono<ChatMessageResponse> sendMessage(String roomId, String playerId, String username, String content) {
        return Mono.fromCallable(() -> {
            SendMessageRequest request = SendMessageRequest.newBuilder()
                    .setRoomId(roomId != null ? roomId : "")
                    .setPlayerId(playerId != null ? playerId : "")
                    .setUsername(username != null ? username : "")
                    .setContent(content != null ? content : "")
                    .build();
            return blockingStub.sendMessage(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<GetRecentMessagesResponse> getRecentMessages(String roomId, String playerId, int limit) {
        return Mono.fromCallable(() -> {
            GetRecentMessagesRequest request = GetRecentMessagesRequest.newBuilder()
                    .setRoomId(roomId != null ? roomId : "")
                    .setPlayerId(playerId != null ? playerId : "")
                    .setLimit(limit)
                    .build();
            return blockingStub.getRecentMessages(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
