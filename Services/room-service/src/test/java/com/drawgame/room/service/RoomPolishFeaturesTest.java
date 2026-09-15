package com.drawgame.room.service;

import com.drawgame.room.domain.Room;
import com.drawgame.room.domain.RoomPlayer;
import com.drawgame.room.domain.RoomStatus;
import com.drawgame.room.exception.InvalidRoomStateException;
import com.drawgame.room.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TV10 — REMATCH / READY / KICK service-layer tests.
 * Readiness gating on beginGame is covered by repository integration tests
 * (RedisRoomRepositoryTest) because the check reads the ready set.
 */
@ExtendWith(MockitoExtension.class)
class RoomPolishFeaturesTest {

    @Mock
    private RoomRepository repository;

    @Mock
    private RoomCodeGenerator roomCodeGenerator;

    private RoomManagementService roomService;

    private final Room finishedRoom = new Room(
            "A7F2K9", "Phòng", "u01", RoomStatus.FINISHED, 8, 5, 60,
            List.of(new RoomPlayer("u01", "Host", true), new RoomPlayer("u02", "Guest", true)));

    @BeforeEach
    void setUp() {
        roomService = new RoomManagementService(repository, roomCodeGenerator);
    }

    @Test
    @DisplayName("SET_READY - toggles a member's readiness")
    void setReady_success() {
        when(repository.setReady("A7F2K9", "u02", true)).thenReturn(finishedRoom);
        Room result = roomService.setReady("A7F2K9", "u02", true);
        assertEquals(RoomStatus.FINISHED, result.status());
        verify(repository).setReady("A7F2K9", "u02", true);
    }

    @Test
    @DisplayName("SET_READY - blank ids rejected")
    void setReady_blankRejected() {
        assertThrows(IllegalArgumentException.class, () -> roomService.setReady("", "u02", true));
        assertThrows(IllegalArgumentException.class, () -> roomService.setReady("A7F2K9", "", true));
    }

    @Test
    @DisplayName("RESET_ROOM (REMATCH) - host resets FINISHED room to WAITING")
    void resetRoom_success() {
        Room waiting = new Room("A7F2K9", "Phòng", "u01", RoomStatus.WAITING, 8, 5, 60,
                List.of(new RoomPlayer("u01", "Host"), new RoomPlayer("u02", "Guest")));
        when(repository.resetRoom("A7F2K9", "u01")).thenReturn(waiting);
        Room result = roomService.resetRoom("A7F2K9", "u01");
        assertEquals(RoomStatus.WAITING, result.status());
        assertEquals(2, result.players().size()); // membership preserved
        verify(repository).resetRoom("A7F2K9", "u01");
    }

    @Test
    @DisplayName("RESET_ROOM - repository authorization failure propagates (non-host rejected)")
    void resetRoom_nonHostPropagates() {
        when(repository.resetRoom("A7F2K9", "u02"))
                .thenThrow(new IllegalArgumentException("Requester is not host of room A7F2K9"));
        assertThrows(IllegalArgumentException.class, () -> roomService.resetRoom("A7F2K9", "u02"));
    }

    @Test
    @DisplayName("KICK_PLAYER - host removes another member")
    void kick_success() {
        Room after = new Room("A7F2K9", "Phòng", "u01", RoomStatus.WAITING, 8, 5, 60,
                List.of(new RoomPlayer("u01", "Host")));
        when(repository.kickPlayer("A7F2K9", "u01", "u02")).thenReturn(after);
        Room result = roomService.kickPlayer("A7F2K9", "u01", "u02");
        assertEquals(1, result.players().size());
        verify(repository).kickPlayer("A7F2K9", "u01", "u02");
    }

    @Test
    @DisplayName("KICK_PLAYER - self-kick / non-host / not-WAITING failures propagate from repository")
    void kick_failuresPropagate() {
        when(repository.kickPlayer("A7F2K9", "u01", "u01"))
                .thenThrow(new IllegalArgumentException("Host cannot kick themselves"));
        assertThrows(IllegalArgumentException.class, () -> roomService.kickPlayer("A7F2K9", "u01", "u01"));

        when(repository.kickPlayer("A7F2K9", "u02", "u01"))
                .thenThrow(new IllegalArgumentException("Requester is not host"));
        assertThrows(IllegalArgumentException.class, () -> roomService.kickPlayer("A7F2K9", "u02", "u01"));

        when(repository.kickPlayer("A7F2K9", "u01", "u02"))
                .thenThrow(new InvalidRoomStateException("Kicking is only allowed while the room is waiting"));
        assertThrows(InvalidRoomStateException.class, () -> roomService.kickPlayer("A7F2K9", "u01", "u02"));
    }
}
