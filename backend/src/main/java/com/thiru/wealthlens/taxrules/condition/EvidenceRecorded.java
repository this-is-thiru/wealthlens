package com.thiru.wealthlens.taxrules.condition;

/**
 * The artefact a claim requires has been furnished.
 *
 * <p>Absence is a legitimate <b>false</b>, never an error: the rules in
 * {@code 12-claim-integrity.md} C3.2 require these documents <em>with the return</em>, so not having
 * one is the answer rather than a gap in what we know. That is the same distinction
 * {@link IsCertifiedDisabled} draws, and the opposite of {@link IncomeAtMost}, where a value nobody
 * computed is a bug.
 *
 * @param type the artefact required
 */
public record EvidenceRecorded(EvidenceType type) implements Condition {

    public EvidenceRecorded {
        if (type == null) {
            throw new IllegalArgumentException("EvidenceRecorded must name an evidence type");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.evidenceRecorded().contains(type);
    }

    @Override
    public String describe() {
        return type + " has been furnished";
    }
}
