package com.thiru.wealthlens.taxengine.rules.filing;

/**
 * A cell placed in a form: the path the Gazette prints it at, such as {@code B1.iii} or {@code S.2} (W-092).
 *
 * @param path where the form prints it
 * @param cell the figure
 */
public record ScheduleField(String path, ScheduleCell cell) {

    public ScheduleField {
        if (path == null || path.isBlank() || cell == null) {
            throw new IllegalArgumentException("A schedule field names its path and its cell");
        }
    }
}
