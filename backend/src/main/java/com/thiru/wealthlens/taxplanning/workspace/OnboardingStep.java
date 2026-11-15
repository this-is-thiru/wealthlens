package com.thiru.wealthlens.taxplanning.workspace;

/**
 * The five groups of questions a draft is filled in by, in the order they are asked ({@code DR 20} F34; W-122a C2). A
 * product grouping, not law: which entries belong to a step is the person's to say, and nothing here decides it from
 * the entries.
 */
public enum OnboardingStep {
    /** The year's gross pay. */
    PAY,
    /** How pay is split into components, and the rent paid. */
    PAY_STRUCTURE_AND_RENT,
    /** What the employer pays into provident and pension funds. */
    EMPLOYER_FUNDS,
    /** The person's own investments and insurance. */
    INVESTMENTS_AND_INSURANCE,
    /** Income beyond pay, and property. */
    OTHER_INCOME_AND_PROPERTY
}
