package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.ComputedTax;
import java.util.List;
import java.util.Optional;

/**
 * A computed draft as a client sees it.
 *
 * @param tax the figures and trace
 * @param draftId the draft
 * @param revision the draft revision it read
 * @param capitalGainsIncluded whether the portfolio's gains were read: only for the profile that holds it
 * @param coarse whether not every onboarding step was answered when it was computed (W-122b C5a); absent on a view
 *     stored before W-122b, which said neither
 * @param band where the bill lies while the figure is coarse; absent otherwise
 * @param notIncluded what the figure does not yet take into account, so nobody mistakes it for complete
 */
public record DraftComputationView(ComputedTax tax, String draftId, int revision, boolean capitalGainsIncluded,
        Optional<Boolean> coarse, Optional<Band> band, List<String> notIncluded) {
}
