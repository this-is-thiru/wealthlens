package com.thiru.wealthlens.taxengine.rules.definition;

import java.time.LocalDate;
import java.util.Optional;

/**
 * The issue dates — and, for one band, whom the policy covers — that choose a life policy's share of its actual
 * capital sum assured (S2 Schedule XV para 2; D-134, D-135) — the window of one of {@link PremiumShareBands}' bands.
 *
 * @param from the earliest issue date in the band, if it has one
 * @param to the latest issue date in the band, if it has one
 * @param onlyCoveringDisabilityOrDisease whether the band holds only a policy covering a person with a disability or
 *     severe disability (s.154) or suffering from a disease or ailment specified in the rules under s.128
 */
public record PolicyIssueWindow(Optional<LocalDate> from, Optional<LocalDate> to,
        boolean onlyCoveringDisabilityOrDisease) {

    public PolicyIssueWindow {
        if (from == null || to == null) {
            throw new IllegalArgumentException("A policy issue window's bounds may not be null; use Optional.empty()");
        }
        if (from.isPresent() && to.isPresent() && to.get().isBefore(from.get())) {
            throw new IllegalArgumentException("A policy issue window ends " + to.get() + ", before it starts "
                    + from.get());
        }
    }

    /** Whether a policy issued on {@code issuedOn}, covering such a person or not, falls in this band. */
    public boolean holds(LocalDate issuedOn, boolean coversDisabilityOrDisease) {
        boolean inWindow = from.map(start -> !issuedOn.isBefore(start)).orElse(true)
                && to.map(end -> !issuedOn.isAfter(end)).orElse(true);
        return inWindow && (coversDisabilityOrDisease || !onlyCoveringDisabilityOrDisease);
    }
}
