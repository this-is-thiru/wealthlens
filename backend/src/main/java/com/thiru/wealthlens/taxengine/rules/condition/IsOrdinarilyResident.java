package com.thiru.wealthlens.taxengine.rules.condition;

/**
 * The assessee is resident and not "not ordinarily resident".
 *
 * <p>r.164(2) of the Income-tax Rules, 2026 gives ITR-1 to "a resident other than not ordinarily resident", and
 * r.164(5) ITR-4 likewise — a narrower test than {@link IsResident}, which counts RNOR as resident (W-090c1a, D-525).
 */
public record IsOrdinarilyResident() implements Condition {

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.residence() == ResidentialStatus.RESIDENT;
    }

    @Override
    public String describe() {
        return "the assessee is resident other than not ordinarily resident";
    }
}
