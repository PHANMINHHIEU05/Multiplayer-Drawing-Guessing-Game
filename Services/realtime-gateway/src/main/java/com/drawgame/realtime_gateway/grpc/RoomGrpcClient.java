package com.drawgame.realtime_gateway.grpc;

import com.drawgame.room.grpc.generated.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Slf4j
@Component
public class RoomGrpcClient {

    @Value("${grpc.client.room-service.host:localhost}")
    private String roomServiceHost;

    @Value("${grpc.client.room-service.port:9091}")
    private int roomServicePort;

    private ManagedChannel channel;
    private RoomServiceGrpc.RoomServiceBlockingStub blockingStub;

    @PostConstruct
    public void init() {
        log.info("Connecting to Room Service gRPC at {}:{}", roomServiceHost, roomServicePort);
        this.channel = ManagedChannelBuilder.forAddress(roomServiceHost, roomServicePort)
                .usePlaintext()
                .build();
        this.blockingStub = RoomServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }

    public Mono<RoomResponse> createRoom(String hostPlayerId, String hostUsername, int maxPlayers, int totalRounds) {
        return Mono.fromCallable(() -> {
            CreateRoomRequest request = CreateRoomRequest.newBuilder()
                    .setHostId(hostPlayerId)
                    .setUsername(hostUsername)
                    .setMaxPlayers(maxPlayers)
                    .setRoundCount(totalRounds)
                    .build();
            return blockingStub.createRoom(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<RoomResponse> joinRoom(String roomId, String playerId, String username) {
        return Mono.fromCallable(() -> {
            JoinRoomRequest request = JoinRoomRequest.newBuilder()
                    .setRoomId(roomId)
                    .setPlayerId(playerId)
                    .setUsername(username)
                    .build();
            return blockingStub.joinRoom(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<RoomResponse> getRoom(String roomId) {
        return Mono.fromCallable(() -> {
            GetRoomRequest request = GetRoomRequest.newBuilder()
                    .setRoomId(roomId)
                    .build();
            return blockingStub.getRoom(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<RoomResponse> leaveRoom(String roomId, String playerId) {
        return Mono.fromCallable(() -> {
            LeaveRoomRequest request = LeaveRoomRequest.newBuilder()
                    .setRoomId(roomId)
                    .setPlayerId(playerId)
                    .build();
            return blockingStub.leaveRoom(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
