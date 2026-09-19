package com.thiru.wealthlens.taxrules.regime;

import com.thiru.wealthlens.taxrules.id.TaxYearId;
import com.thiru.wealthlens.taxrules.registry.Provenance;
import com.thiru.wealthlens.taxrules.temporal.DateRange;
import com.thiru.wealthlens.taxrules.validate.PackValidationException;

/**
 * When a regime may be entered, when it may be applied, and what happens at each edge.
 *
 * <h2>Two windows, not one</h2>
 *
 * <p>The grandfathering shape needs two independent intervals, and collapsing them is the mistake
 * this record exists to prevent. A regime closed to new entrants in 2024 still applies in full, for
 * years afterwards, to everyone who elected in before the door shut — which is exactly what
 * sections 201 and 204 do <b>today</b>, not hypothetically.
 *
 * <p>With one window you can express &ldquo;this regime exists&rdquo; and nothing else. You cannot
 * tell a taxpayer that they may keep using a regime they could not now join, and you cannot stop a
 * new taxpayer joining one that closed.
 *
 * @param entryWindow when a taxpayer may <b>first</b> elect in — s.201(2)'s "on or before 31-03-2024"
 * @param inForceWindow when the regime may be <b>applied</b> by someone already in it; outlives
 *     {@code entryWindow}
 * @param onSunset what happens to an incumbent when {@code inForceWindow} closes
 * @param onConditionFailure what happens when eligibility later fails — s.203(2)
 * @param provenance where these windows were read from
 */
public record RegimeLifecycle(
        DateRange entryWindow,
        DateRange inForceWindow,
        ContinuationPolicy onSunset,
        InvalidationPolicy onConditionFailure,
        Provenance provenance) {

    public RegimeLifecycle {
        if (entryWindow == null || inForceWindow == null) {
            throw new IllegalArgumentException("A regime lifecycle needs both windows");
        }
        if (onSunset == null || onConditionFailure == null) {
            throw new IllegalArgumentException(
                    "A regime lifecycle must state what happens at sunset and on condition failure;"
                            + " leaving either undefined makes a taxpayer's position unanswerable");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("A regime lifecycle must say where it was read from");
        }
        if (!entryWindow.liesWithin(inForceWindow)) {
            throw new PackValidationException(
                    "entryWindow " + entryWindow + " must lie within inForceWindow " + inForceWindow
                            + ": a regime cannot be entered in a year it does not apply");
        }
    }

    /** Whether a taxpayer may elect into this regime for a given year. */
    public boolean openToNewEntrants(TaxYearId taxYear) {
        return entryWindow.coversTaxYear(taxYear);
    }

    /** Whether the regime may be applied at all in a given year. */
    public boolean applicable(TaxYearId taxYear) {
        return inForceWindow.coversTaxYear(taxYear);
    }

    /**
     * Whether the regime is closed to new entrants but still applies to incumbents.
     *
     * <p>The state sections 201 and 204 are in now. A product that cannot express this either
     * refuses a valid computation or offers a regime the taxpayer cannot join.
     */
    public boolean grandfatheredOnly(TaxYearId taxYear) {
        return applicable(taxYear) && !openToNewEntrants(taxYear);
    }
}
