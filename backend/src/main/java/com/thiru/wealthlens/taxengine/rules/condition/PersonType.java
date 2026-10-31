package com.thiru.wealthlens.taxengine.rules.condition;

/**
 * The kind of assessee a rule, regime or return form applies to — one per record of
 * {@code taxengine.input.Assessee} (D-039).
 *
 * <p>{@code HUF} exists because an HUF is a different assessee, with different facts (no date of
 * birth), not because any rule yet computes for one. No shipped regime applies to an HUF: the
 * rulebook sources only s.202's heading for it, so widening a regime waits for its rows (D-062).
 * That is why invariant I5 counts only the person types some applicable regime serves.
 *
 * <p>The design's own word is kept ({@code 23-architecture-consolidation.md} A5): {@code 21-itr-forms.md:73}
 * writes {@code "appliesTo": ["INDIVIDUAL"]}, and {@code exactly one default regime per (taxYear,
 * PersonType)} (I5/I11) and {@code at least one form per (PersonType, ReturnKind)} (I19) are stated
 * over it.
 */
public enum PersonType {
    INDIVIDUAL,
    HUF
}
