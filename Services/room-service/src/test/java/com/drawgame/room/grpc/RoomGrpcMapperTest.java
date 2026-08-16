package com.drawgame.room.grpc;

import com.drawgame.room.domain.Room;
import com.drawgame.room.domain.RoomPlayer;
import com.drawgame.room.domain.RoomStatus;
import com.drawgame.room.grpc.generated.RoomResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoomGrpcMapperTest {

    private RoomGrpcMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(RoomGrpcMapper.class);
    }

    @Test
    @DisplayName("toResponse should map all scalar fields and player list correctly")
    void testToResponse() {
        RoomPlayer player1 = new RoomPlayer("u01", "Alice");
        RoomPlayer player2 = new RoomPlayer("u02", "Bob");

        Room room = new Room(
                "A7F2K9",
                "Fun Room",
                "u01",
                RoomStatus.WAITING,
                6,
                3,
                80,
                List.of(player1, player2)
        );

        RoomResponse response = mapper.toResponse(room);

        assertNotNull(response);
        assertEquals("A7F2K9", response.getRoomId());
        assertEquals("Fun Room", response.getName());
        assertEquals("u01", response.getHostId());
        assertEquals("WAITING", response.getStatus());
        assertEquals(6, response.getMaxPlayers());
        assertEquals(3, response.getRoundCount());
        assertEquals(80, response.getRoundDuration());

        assertEquals(2, response.getPlayersCount());
        assertEquals("u01", response.getPlayers(0).getPlayerId());
        assertEquals("Alice", response.getPlayers(0).getUsername());
        assertEquals("u02", response.getPlayers(1).getPlayerId());
        assertEquals("Bob", response.getPlayers(1).getUsername());
    }

    @Test
    @DisplayName("toResponse should handle null Room gracefully")
    void testToResponseNull() {
        assertNull(mapper.toResponse(null));
    }
}
