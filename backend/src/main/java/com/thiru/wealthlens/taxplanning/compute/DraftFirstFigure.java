package com.thiru.wealthlens.taxplanning.compute;

import java.util.List;

/**
 * A first figure from step 1 alone (PRD F34.1; W-122c, D-722): the draft it created, and its computation under each
 * regime the year compares, each stored with its receipt and labelled coarse with its band.
 *
 * @param draftId the draft created
 * @param revision the revision step 1's answer made
 * @param figures one computation per regime, the year's default first
 */
public record DraftFirstFigure(String draftId, int revision, List<DraftComputationView> figures) {

    public DraftFirstFigure {
        figures = List.copyOf(figures);
    }
}
