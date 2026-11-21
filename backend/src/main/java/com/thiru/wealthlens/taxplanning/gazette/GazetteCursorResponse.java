package com.thiru.wealthlens.taxplanning.gazette;

import java.time.LocalDateTime;

/** Where the Gazette watch will read next, as the owner seeded it (W-358c2, D-857). */
public record GazetteCursorResponse(String id, int year, long lastNumber, LocalDateTime updatedAt, String updatedBy) {

    public static GazetteCursorResponse from(GazetteCursor entity) {
        return new GazetteCursorResponse(entity.getId(), entity.getYear(), entity.getLastNumber(), entity.getUpdatedAt(), entity.getUpdatedBy());
    }
}
