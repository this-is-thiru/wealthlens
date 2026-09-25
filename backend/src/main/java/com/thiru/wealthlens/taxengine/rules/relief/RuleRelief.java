package com.thiru.wealthlens.taxengine.rules.relief;

/**
 * The relief a rule version grants, and whether the Act limits it to the tax at slab rates.
 *
 * <p>The limit is stated, never assumed: S2 s.156(3) caps the s.156(2) rebates at "income-tax payable
 * as per the rates provided in section 202(1)", and s.156(1) has no such words. The two rebates share
 * a shape and differ in exactly this (D-074, D-075).
 *
 * @param shape how the relief is computed
 * @param limitedToSlabRateTax whether the relief may not exceed the tax at slab rates
 */
public record RuleRelief(ReliefShape shape, boolean limitedToSlabRateTax) {

    public RuleRelief {
        if (shape == null) {
            throw new IllegalArgumentException("A rule relief needs a shape");
        }
    }
}
