package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.taxengine.api.ComputedTax;
import java.util.List;

/**
 * A computed draft as a client sees it.
 *
 * @param tax the figures and trace
 * @param draftId the draft
 * @param revision the draft revision it read
 * @param capitalGainsIncluded whether the portfolio's gains were read: only for the profile that holds it
 * @param notIncluded what the figure does not yet take into account, so nobody mistakes it for complete
 */
public record DraftComputationView(ComputedTax tax, String draftId, int revision, boolean capitalGainsIncluded,
        List<String> notIncluded) {
}
