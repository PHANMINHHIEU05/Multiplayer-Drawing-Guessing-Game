package com.drawgame.room.domain;

import java.util.List;
import java.util.Locale;

/** Stable room/game category identifiers shared with the canonical word pack. */
public final class RoomCategories {
    public static final List<String> ALL = List.of(
            "ANIMALS", "FOOD", "OBJECTS", "PLACES", "NATURE", "TECHNOLOGY");

    private RoomCategories() {}

    public static List<String> normalize(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("At least one word category must be selected");
        }
        var normalized = new java.util.HashSet<String>();
        for (String category : requested) {
            if (category == null || category.isBlank()) {
                throw new IllegalArgumentException("Category identifier cannot be blank");
            }
            String id = category.trim().toUpperCase(Locale.ROOT);
            if (!ALL.contains(id)) {
                throw new IllegalArgumentException("Unknown word category: " + category);
            }
            normalized.add(id);
        }
        return ALL.stream().filter(normalized::contains).toList();
    }
}
