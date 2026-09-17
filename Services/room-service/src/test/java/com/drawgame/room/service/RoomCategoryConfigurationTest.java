package com.drawgame.room.service;

import com.drawgame.room.domain.Room;
import com.drawgame.room.domain.RoomStatus;
import com.drawgame.room.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomCategoryConfigurationTest {
    @Mock RoomRepository repository;
    @Mock RoomCodeGenerator roomCodeGenerator;
    RoomManagementService service;

    @BeforeEach
    void setUp() {
        service = new RoomManagementService(repository, roomCodeGenerator);
    }

    @Test
    void hostCanPersistMultipleCategoriesInCanonicalOrderAndDuplicatesAreCollapsed() {
        Room room = new Room("A1B2C3", "room", "host", RoomStatus.WAITING, 6, 3, 60,
                List.of(), List.of("ANIMALS", "FOOD", "TECHNOLOGY"));
        when(repository.setCategories("A1B2C3", "host", List.of("ANIMALS", "FOOD", "TECHNOLOGY")))
                .thenReturn(room);

        Room updated = service.setCategories("A1B2C3", "host",
                List.of("TECHNOLOGY", "ANIMALS", "FOOD", "ANIMALS"));

        assertEquals(List.of("ANIMALS", "FOOD", "TECHNOLOGY"), updated.selectedCategories());
        verify(repository).setCategories("A1B2C3", "host", List.of("ANIMALS", "FOOD", "TECHNOLOGY"));
    }

    @Test
    void emptyAndUnknownCategorySelectionsAreRejectedBeforePersistence() {
        assertThrows(IllegalArgumentException.class, () -> service.setCategories("A1B2C3", "host", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> service.setCategories("A1B2C3", "host", List.of("ANIMALS", "RANDOM")));
        verifyNoInteractions(repository);
    }
}
