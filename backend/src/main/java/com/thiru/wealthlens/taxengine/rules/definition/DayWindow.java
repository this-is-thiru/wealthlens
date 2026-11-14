package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * A number of days a date may lie before another — the merchant banker's "specified date", not "more than one hundred
 * and eighty days earlier" than the exercise of the option (S4 r.15(8)(l); Rules 1962 r.3(8)(iv)(e); D-121).
 *
 * <p>A definition because it is days, not rupees: no cap shape holds it, and as data a later change is a row version.
 *
 * @param days the most days earlier the date may be
 */
public record DayWindow(int days) implements RuleDefinition {

    public DayWindow {
        if (days < 0) {
            throw new IllegalArgumentException("A window of days may not be negative, but was " + days);
        }
    }
}
