package com.drawgame.realtime_gateway.grpc;

import com.drawgame.game.grpc.generated.*;
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
public class GameGrpcClient {

    @Value("${grpc.client.game-service.host:localhost}")
    private String gameServiceHost;

    @Value("${grpc.client.game-service.port:9092}")
    private int gameServicePort;

    private ManagedChannel channel;
    private GameServiceGrpc.GameServiceBlockingStub blockingStub;

    @PostConstruct
    public void init() {
        log.info("Connecting to Game Service gRPC at {}:{}", gameServiceHost, gameServicePort);
        this.channel = ManagedChannelBuilder.forAddress(gameServiceHost, gameServicePort)
                .usePlaintext()
                .build();
        this.blockingStub = GameServiceGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }

    public Mono<GameStateResponse> startGame(String roomId, String requesterPlayerId) {
        return Mono.fromCallable(() -> {
            StartGameRequest request = StartGameRequest.newBuilder()
                    .setRoomId(roomId)
                    .setRequesterPlayerId(requesterPlayerId)
                    .build();
            return blockingStub.startGame(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<GameStateResponse> getGameState(String roomId, String viewerPlayerId) {
        return Mono.fromCallable(() -> {
            GetGameStateRequest request = GetGameStateRequest.newBuilder()
                    .setRoomId(roomId)
                    .setViewerPlayerId(viewerPlayerId != null ? viewerPlayerId : "")
                    .build();
            return blockingStub.getGameState(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<GuessResponse> submitGuess(String roomId, String playerId, String guess) {
        return Mono.fromCallable(() -> {
            SubmitGuessRequest request = SubmitGuessRequest.newBuilder()
                    .setRoomId(roomId)
                    .setPlayerId(playerId)
                    .setGuess(guess)
                    .build();
            return blockingStub.submitGuess(request);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
