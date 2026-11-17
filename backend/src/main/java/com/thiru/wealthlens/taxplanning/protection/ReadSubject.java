package com.thiru.wealthlens.taxplanning.protection;

/** What a recorded read returned: each carries special-category data ({@code DR 14} O1.1) — disability, family, claims. */
public enum ReadSubject {
    PROFILE,
    FAMILY_MEMBERS,
    DRAFT,
    DRAFT_REVISION,
    COMPUTATION,
    /** What was read from an uploaded certificate (W-131d1). */
    DOCUMENT
}
