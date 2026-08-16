package com.drawgame.room.service;

import com.drawgame.room.domain.Room;
import com.drawgame.room.domain.RoomPlayer;
import com.drawgame.room.domain.RoomStatus;
import com.drawgame.room.exception.RoomNotFoundException;
import com.drawgame.room.repository.RoomRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomManagementServiceTest {

    @Mock
    private RoomRepository repository;

    @Mock
    private RoomCodeGenerator roomCodeGenerator;

    private RoomManagementService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomManagementService(repository, roomCodeGenerator);
    }

    @Test
    @DisplayName("CREATE_ROOM - Should create room with host as first player and status WAITING")
    void createRoom_success() {
        when(roomCodeGenerator.generate()).thenReturn("A7F2K9");
        when(repository.exists("A7F2K9")).thenReturn(false);
        when(repository.create(any(Room.class))).thenAnswer(i -> i.getArgument(0));

        Room created = roomService.createRoom("u01", "Minh", "Test Room", 6, 3, 60);

        assertNotNull(created);
        assertEquals("A7F2K9", created.id());
        assertEquals("Test Room", created.name());
        assertEquals("u01", created.hostId());
        assertEquals(RoomStatus.WAITING, created.status());
        assertEquals(6, created.maxPlayers());
        assertEquals(3, created.roundCount());
        assertEquals(60, created.roundDuration());

        assertEquals(1, created.players().size());
        assertEquals("u01", created.players().get(0).playerId());
        assertEquals("Minh", created.players().get(0).username());

        verify(repository).create(any(Room.class));
    }

    @Test
    @DisplayName("CREATE_ROOM - Should throw IllegalArgumentException for invalid inputs")
    void createRoom_validationFailures() {
        assertThrows(IllegalArgumentException.class, () ->
                roomService.createRoom("", "Minh", "Room", 6, 3, 60));
        assertThrows(IllegalArgumentException.class, () ->
                roomService.createRoom("u01", "  ", "Room", 6, 3, 60));
        assertThrows(IllegalArgumentException.class, () ->
                roomService.createRoom("u01", "Minh", "", 6, 3, 60));
        assertThrows(IllegalArgumentException.class, () ->
                roomService.createRoom("u01", "Minh", "Room", 1, 3, 60));
        assertThrows(IllegalArgumentException.class, () ->
                roomService.createRoom("u01", "Minh", "Room", 15, 3, 60));
        assertThrows(IllegalArgumentException.class, () ->
                roomService.createRoom("u01", "Minh", "Room", 6, 0, 60));
        assertThrows(IllegalArgumentException.class, () ->
                roomService.createRoom("u01", "Minh", "Room", 6, 3, 20));
    }

    @Test
    @DisplayName("CREATE_ROOM - Should handle collision and pick next available room code")
    void createRoom_codeCollisionHandling() {
        when(roomCodeGenerator.generate()).thenReturn("COLLID", "UNIQUE");
        when(repository.exists("COLLID")).thenReturn(true);
        when(repository.exists("UNIQUE")).thenReturn(false);
        when(repository.create(any(Room.class))).thenAnswer(i -> i.getArgument(0));

        Room room = roomService.createRoom("u01", "Minh", "Room", 4, 3, 60);

        assertEquals("UNIQUE", room.id());
        verify(roomCodeGenerator, times(2)).generate();
    }

    @Test
    @DisplayName("CREATE_ROOM - Should throw IllegalStateException if collision limit reached")
    void createRoom_maxCollisionExceeded() {
        when(roomCodeGenerator.generate()).thenReturn("COLLID");
        when(repository.exists("COLLID")).thenReturn(true);

        assertThrows(IllegalStateException.class, () ->
                roomService.createRoom("u01", "Minh", "Room", 4, 3, 60));

        verify(roomCodeGenerator, times(10)).generate();
    }

    @Test
    @DisplayName("GET_ROOM - Should return room when room exists")
    void getRoom_found() {
        Room expected = new Room("A7F2K9", "Room", "u01", RoomStatus.WAITING, 4, 3, 60, List.of());
        when(repository.findById("A7F2K9")).thenReturn(Optional.of(expected));

        Room actual = roomService.getRoom("A7F2K9");
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("GET_ROOM - Should throw RoomNotFoundException when room does not exist")
    void getRoom_notFound() {
        when(repository.findById("NOTFOUND")).thenReturn(Optional.empty());

        assertThrows(RoomNotFoundException.class, () -> roomService.getRoom("NOTFOUND"));
    }

    @Test
    @DisplayName("JOIN_ROOM - Should delegate to repository addPlayer")
    void joinRoom_success() {
        Room updated = new Room("A7F2K9", "Room", "u01", RoomStatus.WAITING, 4, 3, 60,
                List.of(new RoomPlayer("u01", "Host"), new RoomPlayer("u02", "Guest")));

        when(repository.addPlayer("A7F2K9", new RoomPlayer("u02", "Guest"))).thenReturn(updated);

        Room result = roomService.joinRoom("A7F2K9", "u02", "Guest");
        assertEquals(2, result.players().size());
        verify(repository).addPlayer("A7F2K9", new RoomPlayer("u02", "Guest"));
    }

    @Test
    @DisplayName("LEAVE_ROOM - Normal player leaves, host unchanged")
    void leaveRoom_normalPlayerLeaves() {
        Room oldRoom = new Room("A7F2K9", "Room", "u01", RoomStatus.WAITING, 4, 3, 60,
                List.of(new RoomPlayer("u01", "Host"), new RoomPlayer("u02", "Guest")));
        Room updatedRoom = new Room("A7F2K9", "Room", "u01", RoomStatus.WAITING, 4, 3, 60,
                List.of(new RoomPlayer("u01", "Host")));

        when(repository.findById("A7F2K9")).thenReturn(Optional.of(oldRoom));
        when(repository.removePlayer("A7F2K9", "u02")).thenReturn(Optional.of(updatedRoom));

        Optional<Room> result = roomService.leaveRoom("A7F2K9", "u02");
        assertTrue(result.isPresent());
        assertEquals("u01", result.get().hostId());
    }

    @Test
    @DisplayName("LEAVE_ROOM - Host leaves, host migrates")
    void leaveRoom_hostLeaves() {
        Room oldRoom = new Room("A7F2K9", "Room", "u01", RoomStatus.WAITING, 4, 3, 60,
                List.of(new RoomPlayer("u01", "Host"), new RoomPlayer("u02", "Guest")));
        Room updatedRoom = new Room("A7F2K9", "Room", "u02", RoomStatus.WAITING, 4, 3, 60,
                List.of(new RoomPlayer("u02", "Guest")));

        when(repository.findById("A7F2K9")).thenReturn(Optional.of(oldRoom));
        when(repository.removePlayer("A7F2K9", "u01")).thenReturn(Optional.of(updatedRoom));

        Optional<Room> result = roomService.leaveRoom("A7F2K9", "u01");
        assertTrue(result.isPresent());
        assertEquals("u02", result.get().hostId());
    }

    @Test
    @DisplayName("LEAVE_ROOM - Last player leaves, room deleted")
    void leaveRoom_lastPlayerLeaves() {
        Room oldRoom = new Room("A7F2K9", "Room", "u01", RoomStatus.WAITING, 4, 3, 60,
                List.of(new RoomPlayer("u01", "Host")));

        when(repository.findById("A7F2K9")).thenReturn(Optional.of(oldRoom));
        when(repository.removePlayer("A7F2K9", "u01")).thenReturn(Optional.empty());

        Optional<Room> result = roomService.leaveRoom("A7F2K9", "u01");
        assertTrue(result.isEmpty());
    }
}
