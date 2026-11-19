package com.thiru.wealthlens.taxplanning.documents.assembly;

/** How a year's certificates carry Part C ({@code DR 06} G5.3; {@code DOC-F130-ANNEX-001}: each employer, or the last). */
public enum AssemblyMode {
    /** One Part C, the last employer's, beside several Part A/B blocks: it should include the earlier salary in row 1(e). */
    LAST_EMPLOYER_CONSOLIDATED,
    /** A Part C from every employer: each may grant a whole year's standard deduction. */
    PER_EMPLOYER,
    /** Some employers issued Part C and some did not: the year cannot be compared as one (M4.2's CANNOT_COMPARE). */
    MIXED
}
