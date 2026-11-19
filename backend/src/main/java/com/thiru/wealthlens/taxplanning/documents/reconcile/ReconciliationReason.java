package com.thiru.wealthlens.taxplanning.documents.reconcile;

/**
 * M4.2's closed set of reasons a certificate and a computation differ, each with its class (D-755 C2a): an expected reason
 * keeps a difference minor, an employer's error makes it material. W-130b and W-130c add the claim and multi-employer
 * reasons.
 *
 * @param relatedRule the rule or design reference the reason rests on
 * @param sentence what the reason means for the user, in words the report carries (W-132 C2a)
 */
public enum ReconciliationReason {
    /** Part B's tax deducted is tax plus surcharge plus cess, so it is compared with the whole bill, never row 13. */
    TAXSUM(Kind.EXPECTED, "DOC-F130-TAXSUM-001",
            "Part B's tax deducted is tax, surcharge and cess together, so it is compared with the whole bill, not row 13 alone"),
    /** The employer used another regime than the one computed (row A). */
    REGIME_DIFFERS(Kind.EXPECTED, "DOC-F130-C-A",
            "The employer applied another regime than the one computed, so their figures differ by design"),
    /** A difference of ₹10 or less: rounding to the nearest ₹10. */
    ROUNDING(Kind.EXPECTED, "RATE-ROUND-002", "A difference of ₹10 or less is rounding to the nearest ₹10"),
    /** The computation carries capital gains, which Form No. 130 cannot. */
    CAPITAL_GAINS(Kind.EXPECTED, "D3",
            "The computation includes capital gains, which an employer's certificate never carries"),
    /** A claim declared to the employer but not proven, so not in the computation: more tax at filing (W-130b). */
    DECLARED_NOT_PROVEN(Kind.EXPECTED, "TDS-SAL-12BB-001",
            "A claim declared to the employer is not proven, so the computation leaves it out: more tax will be payable at filing"),
    /** A deduction the user is entitled to but never declared to the employer: a refund due (W-130b). */
    ENTITLED_NOT_DECLARED(Kind.EXPECTED, "TDS-SAL-12B-001",
            "A deduction the computation allows was never declared to the employer: a refund is due at filing"),
    /** Each employer granted a year's standard deduction (W-129's notice; W-130c). */
    STANDARD_DEDUCTION_TWICE(Kind.EMPLOYER_ERROR, "SAL-STDDED-001",
            "Each employer granted a whole year's standard deduction, where the year allows one: too little tax was deducted"),
    /** An employer taxed its salary without the earlier employer's: row 1(e) nil (W-129; T8-1, W-130c). */
    PREVIOUS_SALARY_UNDECLARED(Kind.EMPLOYER_ERROR, "TDS-SAL-12B-001",
            "An employer was not told of the salary from an earlier employer, so it deducted tax as if its own salary were the year's"),
    /** The employee reported more other-head income to the employer than the computation holds: it could only raise TDS (W-130d). */
    OTHER_INCOME_REPORTED(Kind.EXPECTED, "TDS-SAL-REDUCE-001",
            "More income from other heads was reported to the employer than the computation holds: it could only raise the tax deducted"),
    /** The employee did not report other-head income the computation holds, as is their option: tax payable at filing (W-130d). */
    OTHER_INCOME_NOT_REPORTED(Kind.EXPECTED, "TDS-SAL-12B-001",
            "Income from other heads was not reported to the employer, as is the employee's choice: its tax is payable at filing");

    /** Whether a reason keeps a difference minor or makes it material. */
    public enum Kind {
        EXPECTED,
        EMPLOYER_ERROR
    }

    private final Kind kind;
    private final String relatedRule;
    private final String sentence;

    ReconciliationReason(Kind kind, String relatedRule, String sentence) {
        this.kind = kind;
        this.relatedRule = relatedRule;
        this.sentence = sentence;
    }

    public Kind kind() {
        return kind;
    }

    public String relatedRule() {
        return relatedRule;
    }

    public String sentence() {
        return sentence;
    }
}
