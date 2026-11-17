package com.thiru.wealthlens.taxplanning.documents.reconcile;

/**
 * M4.2's closed set of reasons a certificate and a computation differ, each with its class (D-755 C2a): an expected reason
 * keeps a difference minor, an employer's error makes it material. W-130b and W-130c add the claim and multi-employer
 * reasons.
 *
 * @param relatedRule the rule or design reference the reason rests on
 */
public enum ReconciliationReason {
    /** Part B's tax deducted is tax plus surcharge plus cess, so it is compared with the whole bill, never row 13. */
    TAXSUM(Kind.EXPECTED, "DOC-F130-TAXSUM-001"),
    /** The employer used another regime than the one computed (row A). */
    REGIME_DIFFERS(Kind.EXPECTED, "DOC-F130-C-A"),
    /** A difference of ₹10 or less: rounding to the nearest ₹10. */
    ROUNDING(Kind.EXPECTED, "RATE-ROUND-002"),
    /** The computation carries capital gains, which Form No. 130 cannot. */
    CAPITAL_GAINS(Kind.EXPECTED, "D3");

    /** Whether a reason keeps a difference minor or makes it material. */
    public enum Kind {
        EXPECTED,
        EMPLOYER_ERROR
    }

    private final Kind kind;
    private final String relatedRule;

    ReconciliationReason(Kind kind, String relatedRule) {
        this.kind = kind;
        this.relatedRule = relatedRule;
    }

    public Kind kind() {
        return kind;
    }

    public String relatedRule() {
        return relatedRule;
    }
}
