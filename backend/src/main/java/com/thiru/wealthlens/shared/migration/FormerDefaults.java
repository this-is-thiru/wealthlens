package com.thiru.wealthlens.shared.migration;

import java.util.Map;

/**
 * The keys {@code m1.6.1} read as its field's initial value when a document lacked them (W-419, D-1002), so the copier writes
 * that value and counts it: a missing key would otherwise be a NULL column, and the row would read differently from what
 * {@code m1.6.1} showed. A default the schema already gives is not listed, as the copier writes only the columns a document
 * fills ({@code segment}'s {@code DEFAULT 'DELIVERY'}, {@code V9__portfolio.sql}). Removed with the copier (W-337j).
 */
final class FormerDefaults {

    private static final Map<String, Map<String, String>> BY_COLLECTION = Map.of(
            // TransactionEntity.java:95 – 96 at m1.6.1: timezoneId = TLocalDate.TIME_ZONE_IST, kept when timezone_id was not stored
            "transactions", Map.of("timezone_id", "Asia/Kolkata"));

    private FormerDefaults() {
    }

    /** Each key of the collection's documents that {@code m1.6.1} read as a default when absent, with that default. */
    static Map<String, String> of(String collection) {
        return BY_COLLECTION.getOrDefault(collection, Map.of());
    }
}
