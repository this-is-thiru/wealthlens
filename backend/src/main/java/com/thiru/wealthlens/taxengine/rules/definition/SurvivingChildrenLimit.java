package com.thiru.wealthlens.taxengine.rules.definition;

import java.time.LocalDate;

/**
 * How many surviving children the leave travel concession covers, and the children the limit leaves alone (S4
 * r.278(5)–(6); Rules 1962 r.2B(4) and its proviso; D-212).
 *
 * <p>A count and a date, so no cap shape carries it. The limit falls on children, not journeys: a journey on which a
 * child beyond it travels keeps the rest of the family's travel exempt. Children born before {@code exceptBornBefore}
 * and children of a multiple birth after one child are not counted against it; which of the counted children it
 * covers the Rules do not say, and the two eldest are taken (D-212 C3a).
 *
 * @param children how many of the counted children the concession covers
 * @param exceptBornBefore children born before this date are not counted
 */
public record SurvivingChildrenLimit(int children, LocalDate exceptBornBefore) implements RuleDefinition {

    public SurvivingChildrenLimit {
        if (children < 0 || exceptBornBefore == null) {
            throw new IllegalArgumentException("A surviving-children limit needs a count of none or more and the date"
                    + " before which children are not counted");
        }
    }

    /** Whether a child born on {@code dateOfBirth} is counted against the limit, unless born of a multiple birth. */
    public boolean counts(LocalDate dateOfBirth) {
        return !dateOfBirth.isBefore(exceptBornBefore);
    }
}
