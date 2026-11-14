package com.thiru.wealthlens.taxengine.rules.filing;

import com.thiru.wealthlens.taxengine.rules.id.CellCode;

/**
 * One figure a return form shows, defined once and placed by each form at its own path (W-092 C2a, D-654).
 *
 * @param code the cell
 * @param label what the figure is, in the forms' words
 * @param source where the figure comes from
 * @param sign how the form shows the figure's sign
 */
public record ScheduleCell(CellCode code, String label, FieldSource source, Sign sign) {

    /**
     * How a form shows a signed figure (Q4a, D-654): as computed; nil where it is a loss ("enter nil if loss"); or one
     * side of a figure the form splits in two, as payable (the positive part) and refund (the negative part, shown
     * without its sign).
     */
    public enum Sign { AS_COMPUTED, NIL_IF_LOSS, POSITIVE_PART, NEGATIVE_PART }

    public ScheduleCell {
        if (code == null || source == null || sign == null) {
            throw new IllegalArgumentException("A schedule cell names its code, its source and its sign");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException(code + " must have a label; it reaches the person");
        }
    }
}
