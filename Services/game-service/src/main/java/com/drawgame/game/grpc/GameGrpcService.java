package com.drawgame.game.grpc;

import com.drawgame.game.grpc.generated.*;
import com.drawgame.game.model.GameStateData;
import com.drawgame.game.model.PlayerScoreData;
import com.drawgame.game.service.GameCoreService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class GameGrpcService extends GameServiceGrpc.GameServiceImplBase {

    private final GameCoreService gameCoreService;

    @Override
    public void startGame(StartGameRequest request, StreamObserver<GameStateResponse> responseObserver) {
        log.info("gRPC StartGame: roomId={}, requesterId={}", request.getRoomId(), request.getRequesterPlayerId());
        try {
            GameStateData state = gameCoreService.startGame(request.getRoomId(), request.getRequesterPlayerId());
            GameStateResponse response = mapToResponse(state);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            log.warn("StartGame invalid argument: {}", e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalStateException e) {
            log.warn("StartGame precondition failed: {}", e.getMessage());
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Error starting game", e);
            responseObserver.onError(Status.INTERNAL.withDescription("Internal error: " + e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getGameState(GetGameStateRequest request, StreamObserver<GameStateResponse> responseObserver) {
        try {
            GameStateData state = gameCoreService.getGameState(request.getRoomId(), request.getViewerPlayerId());
            GameStateResponse response = mapToResponse(state);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Error fetching game state", e);
            responseObserver.onError(Status.INTERNAL.withDescription("Internal error: " + e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void submitGuess(SubmitGuessRequest request, StreamObserver<GuessResponse> responseObserver) {
        log.info("gRPC SubmitGuess: roomId={}, playerId={}", request.getRoomId(), request.getPlayerId());
        try {
            GameCoreService.GuessResult result = gameCoreService.submitGuess(
                    request.getRoomId(), request.getPlayerId(), request.getGuess()
            );

            GuessResponse response = GuessResponse.newBuilder()
                    .setRoomId(request.getRoomId())
                    .setPlayerId(request.getPlayerId())
                    .setGuessStatus(result.status())
                    .setScoreAwarded(result.scoreAwarded())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalStateException e) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Error submitting guess", e);
            responseObserver.onError(Status.INTERNAL.withDescription("Internal error: " + e.getMessage()).asRuntimeException());
        }
    }

    private GameStateResponse mapToResponse(GameStateData state) {
        GameStateResponse.Builder builder = GameStateResponse.newBuilder()
                .setRoomId(state.getRoomId())
                .setStatus(state.getStatus())
                .setCurrentRound(state.getCurrentRound())
                .setTotalRounds(state.getTotalRounds())
                .setDrawerId(state.getDrawerId() != null ? state.getDrawerId() : "")
                .setRoundStartedAt(state.getRoundStartedAt())
                .setRoundEndsAt(state.getRoundEndsAt())
                .setHint(state.getHint() != null ? state.getHint() : "")
                .setSecretWord(state.getSecretWord() != null ? state.getSecretWord() : "");

        for (PlayerScoreData score : state.getScores()) {
            builder.addScores(PlayerScoreMessage.newBuilder()
                    .setPlayerId(score.getPlayerId())
                    .setUsername(score.getUsername() != null ? score.getUsername() : score.getPlayerId())
                    .setScore(score.getScore())
                    .setHasGuessed(score.isHasGuessed())
                    .build());
        }

        return builder.build();
    }
}
