package com.drawgame.room.grpc;

import com.drawgame.room.domain.Room;
import com.drawgame.room.exception.InvalidRoomStateException;
import com.drawgame.room.exception.PlayerAlreadyInRoomException;
import com.drawgame.room.exception.RoomFullException;
import com.drawgame.room.exception.RoomNotFoundException;
import com.drawgame.room.grpc.generated.CreateRoomRequest;
import com.drawgame.room.grpc.generated.GetRoomRequest;
import com.drawgame.room.grpc.generated.JoinRoomRequest;
import com.drawgame.room.grpc.generated.LeaveRoomRequest;
import com.drawgame.room.grpc.generated.RoomResponse;
import com.drawgame.room.grpc.generated.RoomServiceGrpc;
import com.drawgame.room.service.RoomManagementService;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;

import java.util.Optional;

@GrpcService
public class RoomGrpcService extends RoomServiceGrpc.RoomServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(RoomGrpcService.class);

    private final RoomManagementService roomService;
    private final RoomGrpcMapper mapper;

    public RoomGrpcService(
            RoomManagementService roomService,
            RoomGrpcMapper mapper
    ) {
        this.roomService = roomService;
        this.mapper = mapper;
    }

    @Override
    public void createRoom(
            CreateRoomRequest request,
            StreamObserver<RoomResponse> responseObserver
    ) {
        try {
            Room room = roomService.createRoom(
                    request.getHostId(),
                    request.getUsername(),
                    request.getRoomName(),
                    request.getMaxPlayers(),
                    request.getRoundCount(),
                    request.getRoundDuration()
            );

            RoomResponse response = mapper.toResponse(room);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void getRoom(
            GetRoomRequest request,
            StreamObserver<RoomResponse> responseObserver
    ) {
        try {
            Room room = roomService.getRoom(request.getRoomId());
            RoomResponse response = mapper.toResponse(room);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void joinRoom(
            JoinRoomRequest request,
            StreamObserver<RoomResponse> responseObserver
    ) {
        try {
            Room room = roomService.joinRoom(
                    request.getRoomId(),
                    request.getPlayerId(),
                    request.getUsername()
            );

            RoomResponse response = mapper.toResponse(room);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void leaveRoom(
            LeaveRoomRequest request,
            StreamObserver<RoomResponse> responseObserver
    ) {
        try {
            Optional<Room> roomOpt = roomService.leaveRoom(
                    request.getRoomId(),
                    request.getPlayerId()
            );

            RoomResponse response;
            if (roomOpt.isPresent()) {
                response = mapper.toResponse(roomOpt.get());
            } else {
                response = RoomResponse.newBuilder()
                        .setRoomId(request.getRoomId())
                        .setStatus("FINISHED")
                        .build();
            }

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void beginGame(
            com.drawgame.room.grpc.generated.BeginGameRequest request,
            StreamObserver<RoomResponse> responseObserver
    ) {
        try {
            Room room = roomService.beginGame(
                    request.getRoomId(),
                    request.getRequesterPlayerId()
            );

            RoomResponse response = mapper.toResponse(room);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    @Override
    public void finishGame(
            com.drawgame.room.grpc.generated.FinishGameRequest request,
            StreamObserver<RoomResponse> responseObserver
    ) {
        try {
            Room room = roomService.finishGame(request.getRoomId());
            RoomResponse response = mapper.toResponse(room);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    private void handleException(Exception e, StreamObserver<?> responseObserver) {
        if (e instanceof IllegalArgumentException) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } else if (e instanceof RoomNotFoundException) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } else if (e instanceof PlayerAlreadyInRoomException) {
            responseObserver.onError(
                    Status.ALREADY_EXISTS
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } else if (e instanceof RoomFullException) {
            responseObserver.onError(
                    Status.RESOURCE_EXHAUSTED
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } else if (e instanceof InvalidRoomStateException) {
            responseObserver.onError(
                    Status.FAILED_PRECONDITION
                            .withDescription(e.getMessage())
                            .asRuntimeException()
            );
        } else {
            log.error("Unexpected error in gRPC handler", e);
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Internal server error")
                            .asRuntimeException()
            );
        }
    }
}