package com.thiru.wealthlens.taxengine.rules.temporal;

/**
 * Whether a rule has a counterpart in one of the two Acts.
 *
 * <p>Every rule is stated twice — once under the Income-tax Act 1961 and once under the Act of 2025
 * — and the pair is not always complete. {@code NOT_YET_VERIFIED} is the value that matters: it is
 * the difference between <em>&ldquo;this Act has no such provision&rdquo;</em> and <em>&ldquo;nobody
 * has opened the Act to look&rdquo;</em>, and collapsing the two is how an unchecked assumption
 * becomes a stated fact.
 */
public enum Presence {

    /** The provision exists in that Act and has been read. */
    PRESENT,

    /** That Act has no counterpart — a positive finding, not an absence of effort. */
    ABSENT_IN_THIS_ACT,

    /** Nobody has opened that Act for this rule yet. */
    NOT_YET_VERIFIED
}
