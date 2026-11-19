package com.thiru.wealthlens.taxplanning.documents.ingest;

/** The refusals every value in this package shares: a missing part is refused when the value is made. */
final class Checks {

    private Checks() {
    }

    static void text(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    static void present(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }
}
