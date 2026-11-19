package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;

/**
 * The employer's retirement contributions go beyond the limit, and the accretion on the excess cannot be computed
 * because the funds' history is not entered (S2 s.17(1)(i); 1961 s.17(2)(viia); D-127). A refusal of the input like any
 * other, so a caller that only computes still answers 400. It has a type of its own because the optimiser must tell
 * this refusal apart: an allocation it tries that raises it is one the engine cannot price, and the answer says so
 * rather than failing (W-238h C1a).
 */
public final class FundHistoryRequiredException extends IllegalArgumentException {

    /** The entered fact that is missing, by its name in {@code EnteredFacts}. */
    public static final String MISSING_INPUT = "retirementFundHistory";

    private final transient RuleId rule;

    /** @param rule the rule whose computation needs the history */
    public FundHistoryRequiredException(RuleId rule) {
        super("The accretion on employer contributions beyond the limit (S2 s.17(1)(i); 1961 s.17(2)(viia)) needs the"
                + " funds' income and balances for the year; enter the retirement fund history");
        this.rule = rule;
    }

    public RuleId rule() {
        return rule;
    }
}
