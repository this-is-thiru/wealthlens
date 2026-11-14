package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * What a row of {@code RS-CG-TERM} says of a capital asset: it is short-term if held for not more than {@code months}
 * immediately preceding the date of its transfer (1961 s.2(42A); 2025 s.2(101); W-088k3, D-305).
 *
 * <p>The period is the law's, so it is data and a change in it is a new version; how the months are counted (calendar
 * months, the owner's ruling D-303) is the step's.
 *
 * @param months the longest holding, in months, that is still short-term
 */
public record ShortTermHoldingPeriod(int months) implements RuleDefinition {

    public ShortTermHoldingPeriod {
        if (months <= 0) {
            throw new IllegalArgumentException("A holding-period row must name a positive number of months, not "
                    + months);
        }
    }
}
