package com.thiru.wealthlens.taxengine.rules.condition;

/**
 * A certificate of disability at least this severe has been recorded.
 *
 * <p>A <b>minimum</b>, not an equality: {@code IsCertifiedDisabled(DISABILITY)} holds for someone
 * certified severe, because {@code DED-80U-001} allows &#8377;75,000 to a person with disability and
 * &#8377;1,25,000 to a person with severe disability — the second is not a different rule, it is a
 * higher rung of the same one.
 *
 * <p>No certificate recorded is a legitimate <b>false</b>, not a missing fact. Both s.154(2) and
 * s.127(7) require the certificate to be furnished with the return, so its absence is itself the
 * answer — unlike an unsupplied age, which is a gap in what the taxpayer told us.
 *
 * @param minimum the least severity that satisfies this rule
 */
public record IsCertifiedDisabled(DisabilitySeverity minimum) implements Condition {

    public IsCertifiedDisabled {
        if (minimum == null) {
            throw new IllegalArgumentException("IsCertifiedDisabled must state a minimum severity");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.disability(AgeSubject.SELF)
                .filter(certified -> certified.compareTo(minimum) >= 0)
                .isPresent();
    }

    @Override
    public String describe() {
        return "a certificate of " + (minimum == DisabilitySeverity.SEVERE_DISABILITY
                ? "severe disability" : "disability") + " has been furnished";
    }
}
