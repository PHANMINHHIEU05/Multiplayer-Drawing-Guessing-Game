package com.drawgame.chat.client;

import com.drawgame.room.grpc.generated.GetRoomRequest;
import com.drawgame.room.grpc.generated.RoomResponse;
import com.drawgame.room.grpc.generated.RoomServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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

    public RoomResponse getRoom(String roomId) {
        log.debug("Calling RoomService.GetRoom for room {}", roomId);
        GetRoomRequest request = GetRoomRequest.newBuilder()
                .setRoomId(roomId)
                .build();
        return blockingStub.getRoom(request);
    }
}
