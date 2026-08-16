package com.drawgame.room.grpc;

import com.drawgame.room.domain.Room;
import com.drawgame.room.domain.RoomPlayer;
import com.drawgame.room.domain.RoomStatus;
import com.drawgame.room.exception.InvalidRoomStateException;
import com.drawgame.room.exception.PlayerAlreadyInRoomException;
import com.drawgame.room.exception.RoomFullException;
import com.drawgame.room.exception.RoomNotFoundException;
import com.drawgame.room.grpc.generated.CreateRoomRequest;
import com.drawgame.room.grpc.generated.GetRoomRequest;
import com.drawgame.room.grpc.generated.JoinRoomRequest;
import com.drawgame.room.grpc.generated.LeaveRoomRequest;
import com.drawgame.room.grpc.generated.RoomResponse;
import com.drawgame.room.service.RoomManagementService;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomGrpcServiceTest {

    @Mock
    private RoomManagementService roomService;

    @Mock
    private RoomGrpcMapper mapper;

    @Mock
    private StreamObserver<RoomResponse> responseObserver;

    private RoomGrpcService grpcService;

    @BeforeEach
    void setUp() {
        grpcService = new RoomGrpcService(roomService, mapper);
    }

    @Test
    @DisplayName("createRoom - Success should call onNext and onCompleted")
    void createRoom_success() {
        CreateRoomRequest request = CreateRoomRequest.newBuilder()
                .setHostId("u01")
                .setUsername("Alice")
                .setRoomName("My Room")
                .setMaxPlayers(6)
                .setRoundCount(3)
                .setRoundDuration(60)
                .build();

        Room room = new Room("R12345", "My Room", "u01", RoomStatus.WAITING, 6, 3, 60, List.of(new RoomPlayer("u01", "Alice")));
        RoomResponse response = RoomResponse.newBuilder().setRoomId("R12345").setName("My Room").build();

        when(roomService.createRoom("u01", "Alice", "My Room", 6, 3, 60)).thenReturn(room);
        when(mapper.toResponse(room)).thenReturn(response);

        grpcService.createRoom(request, responseObserver);

        verify(responseObserver).onNext(response);
        verify(responseObserver).onCompleted();
        verify(responseObserver, never()).onError(any());
    }

    @Test
    @DisplayName("createRoom - IllegalArgumentException maps to Status.INVALID_ARGUMENT")
    void createRoom_invalidArgument() {
        CreateRoomRequest request = CreateRoomRequest.newBuilder().build();
        when(roomService.createRoom(any(), any(), any(), anyInt(), anyInt(), anyInt()))
                .thenThrow(new IllegalArgumentException("Host id is required"));

        grpcService.createRoom(request, responseObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(captor.capture());
        assertTrue(captor.getValue() instanceof StatusRuntimeException);
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertEquals(Status.Code.INVALID_ARGUMENT, sre.getStatus().getCode());
    }

    @Test
    @DisplayName("getRoom - RoomNotFoundException maps to Status.NOT_FOUND")
    void getRoom_notFound() {
        GetRoomRequest request = GetRoomRequest.newBuilder().setRoomId("NOTFOUND").build();
        when(roomService.getRoom("NOTFOUND")).thenThrow(new RoomNotFoundException("Room not found: NOTFOUND"));

        grpcService.getRoom(request, responseObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(captor.capture());
        StatusRuntimeException sre = (StatusRuntimeException) captor.getValue();
        assertEquals(Status.Code.NOT_FOUND, sre.getStatus().getCode());
    }

    @Test
    @DisplayName("joinRoom - PlayerAlreadyInRoomException maps to Status.ALREADY_EXISTS")
    void joinRoom_alreadyExists() {
        JoinRoomRequest request = JoinRoomRequest.newBuilder()
                .setRoomId("R100")
                .setPlayerId("u02")
                .setUsername("Bob")
                .build();

        when(roomService.joinRoom("R100", "u02", "Bob")).thenThrow(new PlayerAlreadyInRoomException("Already in room"));
        grpcService.joinRoom(request, responseObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(captor.capture());
        assertEquals(Status.Code.ALREADY_EXISTS, ((StatusRuntimeException) captor.getValue()).getStatus().getCode());
    }

    @Test
    @DisplayName("joinRoom - RoomFullException maps to Status.RESOURCE_EXHAUSTED")
    void joinRoom_roomFull() {
        JoinRoomRequest request = JoinRoomRequest.newBuilder()
                .setRoomId("R100")
                .setPlayerId("u02")
                .setUsername("Bob")
                .build();

        when(roomService.joinRoom("R100", "u02", "Bob")).thenThrow(new RoomFullException("Room full"));
        grpcService.joinRoom(request, responseObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(captor.capture());
        assertEquals(Status.Code.RESOURCE_EXHAUSTED, ((StatusRuntimeException) captor.getValue()).getStatus().getCode());
    }

    @Test
    @DisplayName("joinRoom - InvalidRoomStateException maps to Status.FAILED_PRECONDITION")
    void joinRoom_invalidState() {
        JoinRoomRequest request = JoinRoomRequest.newBuilder()
                .setRoomId("R100")
                .setPlayerId("u02")
                .setUsername("Bob")
                .build();

        when(roomService.joinRoom("R100", "u02", "Bob")).thenThrow(new InvalidRoomStateException("Not WAITING"));
        grpcService.joinRoom(request, responseObserver);

        ArgumentCaptor<Throwable> captor = ArgumentCaptor.forClass(Throwable.class);
        verify(responseObserver).onError(captor.capture());
        assertEquals(Status.Code.FAILED_PRECONDITION, ((StatusRuntimeException) captor.getValue()).getStatus().getCode());
    }

    @Test
    @DisplayName("leaveRoom - When room deleted after last player leaves")
    void leaveRoom_roomDeleted() {
        LeaveRoomRequest request = LeaveRoomRequest.newBuilder().setRoomId("R100").setPlayerId("u01").build();
        when(roomService.leaveRoom("R100", "u01")).thenReturn(Optional.empty());

        grpcService.leaveRoom(request, responseObserver);

        ArgumentCaptor<RoomResponse> captor = ArgumentCaptor.forClass(RoomResponse.class);
        verify(responseObserver).onNext(captor.capture());
        assertEquals("R100", captor.getValue().getRoomId());
        assertEquals("FINISHED", captor.getValue().getStatus());
        verify(responseObserver).onCompleted();
    }
}
