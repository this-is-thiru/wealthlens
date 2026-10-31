package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * What a computation node produces.
 *
 * <p>A node's output is rarely one number. {@code TaxByBasis} carries slab tax and special-rate tax
 * apart because downstream rules read one and not the other, and flattening them to a single
 * {@code Money} is how the rebate comes to be computed on the wrong base. Making the partition a
 * <b>value</b> rather than a side channel means it travels through the pipeline and into the trace,
 * where it can be checked.
 *
 * <p>{@link #asMoney()} exists for the trace's single summary column and for nothing else. A caller
 * that needs the parts should pattern-match rather than sum them back up.
 *
 * <p><b>This clause grows.</b> It permits the values that exist ({@code ComponentSet} arrived with the
 * partition, D-090; {@code SalaryBases} with the salary bases, D-092). {@code 15-review-findings.md}
 * B4 also names {@code HeadResult}, {@code ComponentSet}, {@code ElectionOutcome} and
 * {@code InterestBreakdown} — none of which the design declares anywhere, only lists here — so each
 * arrives with the node that produces it, as the cap shapes did.
 */
public sealed interface NodeValue
        permits MoneyValue, TaxByBasis, ComponentSet, SalaryBases, CapitalGainsHead, HeadsAfterSetOff {

    /** The single summary figure, for the trace's {@code out} column. */
    Money asMoney();
}
