package com.thiru.wealthlens.shared.privacy;

import java.util.List;

/**
 * The record the owner answers the Board from (DPDP Rules r.7(2)(b)(vi); W-237c), and the text and addresses to email.
 *
 * @param breach the breach
 * @param intimations each affected user and how they were told
 */
public record BreachReport(BreachCase breach, List<BreachIntimationLine> intimations) {

    public BreachReport {
        intimations = List.copyOf(intimations);
    }
}
