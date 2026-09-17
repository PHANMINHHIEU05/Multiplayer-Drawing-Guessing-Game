package com.drawgame.game.grpc;

import com.drawgame.game.grpc.generated.*;
import com.drawgame.game.model.GameStateData;
import com.drawgame.game.model.PlayerScoreData;
import com.drawgame.game.model.MatchAwardData;
import com.drawgame.game.model.RoundRecapData;
import com.drawgame.game.model.RoundScoreDeltaData;
import com.drawgame.game.model.WordChoiceData;
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
            // START_GAME is room-broadcast by the Gateway; private drawer choices are fetched
            // separately through viewer-filtered GET_GAME_STATE.
            state.setSecretWord("");
            state.setWordChoices(java.util.List.of());
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

    @Override
    public void selectWord(SelectWordRequest request, StreamObserver<GameStateResponse> responseObserver) {
        try {
            GameStateData state = gameCoreService.selectWord(
                    request.getRoomId(), request.getPlayerId(), request.getChoiceId());
            responseObserver.onNext(mapToResponse(state));
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalStateException e) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            log.error("Error selecting word for room {}", request.getRoomId(), e);
            responseObserver.onError(Status.INTERNAL.withDescription("Internal error").asRuntimeException());
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
                .setGameId(state.getGameId() == null ? "" : state.getGameId())
                .setRoundPhase(state.getRoundPhase() == null ? "" : state.getRoundPhase())
                .setPhaseStartedAt(state.getPhaseStartedAt())
                .setPhaseEndsAt(state.getPhaseEndsAt())
                .setRoundDurationSeconds(state.getRoundDurationSeconds())
                .setHint(state.getHint() != null ? state.getHint() : "")
                .setSecretWord(state.getSecretWord() != null ? state.getSecretWord() : "");

        for (WordChoiceData choice : state.getWordChoices()) {
            builder.addWordChoices(WordChoiceMessage.newBuilder()
                    .setChoiceId(choice.choiceId()).setDisplayWord(choice.displayWord()).build());
        }
        if (state.getRoundRecap() != null) builder.setRoundRecap(mapRecap(state.getRoundRecap()));
        for (MatchAwardData award : state.getAwards()) {
            builder.addAwards(MatchAwardMessage.newBuilder().setType(award.type()).setLabel(award.label())
                    .setPlayerId(award.playerId()).setUsername(award.username()).setValue(award.value())
                    .setElapsedMillis(award.elapsedMillis()).build());
        }

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

    private RoundRecapMessage mapRecap(RoundRecapData recap) {
        RoundRecapMessage.Builder builder = RoundRecapMessage.newBuilder()
                .setRoundNumber(recap.roundNumber()).setDrawerId(recap.drawerId()).setAnswer(recap.answer())
                .setDrawerScore(recap.drawerScore()).setFastestPlayerId(recap.fastestPlayerId())
                .setFastestUsername(recap.fastestUsername() == null ? "" : recap.fastestUsername())
                .setFastestElapsedMillis(recap.fastestElapsedMillis())
                .addAllCorrectPlayerIds(recap.correctPlayerIds());
        for (RoundScoreDeltaData delta : recap.scoreDeltas()) {
            builder.addScoreDeltas(RoundScoreDeltaMessage.newBuilder().setPlayerId(delta.playerId())
                    .setUsername(delta.username()).setRoundDelta(delta.roundDelta())
                    .setTotalScore(delta.totalScore()).build());
        }
        recap.correctGuessTimesMillis().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> builder.addCorrectGuessTimes(CorrectGuessTimingMessage.newBuilder()
                        .setPlayerId(entry.getKey()).setElapsedMillis(entry.getValue()).build()));
        return builder.build();
    }
}
