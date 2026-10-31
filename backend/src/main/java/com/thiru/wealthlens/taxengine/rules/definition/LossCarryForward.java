package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;

/**
 * How a loss not set off in its own year is carried forward: the head it may then be set off against, the tax years it
 * lasts, and whether it must have been determined in a return filed in time (S2 ss.110, 111, 121; 1961 ss.71B, 74, 80;
 * D-263). A definition so that each carried loss's law is the row's, not the node's: a section that changes, or a third
 * carried loss, is new data.
 *
 * @param absorbedBy the one head the carried loss is set off against — each section names one ("only against the
 *     income from house property", s.110(1)(a); "only against the income under the head 'Capital gains'", s.111(1)(a))
 * @param span the tax year the loss arose in and the years after it in which it may be used
 * @param requiresReturnFiledInTime whether a loss not determined in a return filed in time is not carried at all
 */
public record LossCarryForward(HeadCode absorbedBy, TaxYearSpan span, boolean requiresReturnFiledInTime)
        implements RuleDefinition {

    public LossCarryForward {
        if (absorbedBy == null || span == null) {
            throw new IllegalArgumentException("A loss carried forward needs the head it is set off against and its"
                    + " span of tax years");
        }
    }
}
