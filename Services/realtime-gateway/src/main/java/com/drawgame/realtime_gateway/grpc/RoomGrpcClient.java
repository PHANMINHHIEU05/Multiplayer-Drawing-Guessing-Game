package com.drawgame.realtime_gateway.grpc;

import com.drawgame.room.grpc.generated.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class RoomGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(RoomGrpcClient.class);

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

    public Mono<RoomResponse> createRoom(String hostPlayerId, String hostUsername, String roomName, int maxPlayers, int totalRounds, int roundDuration) {
        return Mono.fromCallable(() -> {
            String validRoomName = (roomName != null && !roomName.isBlank()) ? roomName : (hostUsername + "'s Room");
            CreateRoomRequest request = CreateRoomRequest.newBuilder()
                    .setHostId(hostPlayerId)
                    .setUsername(hostUsername)
                    .setRoomName(validRoomName)
                    .setMaxPlayers(maxPlayers > 0 ? maxPlayers : 4)
                    .setRoundCount(totalRounds > 0 ? totalRounds : 5)
                    .setRoundDuration(roundDuration > 0 ? roundDuration : 60)
                    .build();
            return blockingStub.createRoom(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<RoomResponse> createRoom(String hostPlayerId, String hostUsername, int maxPlayers, int totalRounds) {
        return createRoom(hostPlayerId, hostUsername, hostUsername + "'s Room", maxPlayers, totalRounds, 60);
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
