package com.thiru.wealthlens.taxengine.rules.pack;

/**
 * How a rule that changed inside the tax year is applied to the whole of it.
 *
 * <p><b>Declared, never assumed.</b> Guessing here is the most dangerous kind of silent error: it
 * produces a plausible number, it occurs only in the year the rule moved, and no golden test written
 * before the change would catch it. So {@link #UNDETERMINED} is the default for a newly authored
 * mid-year change, and a computation that reaches it <b>fails</b> rather than picking.
 *
 * <p>This is not a rare case. The Income-tax Rules 2026 were amended four times inside the year they
 * govern, and one amending instrument commenced partly on 1 April and partly on the date of its own
 * publication.
 */
public enum MidYearCombination {

    /** Each segment gets its own cap, prorated to the segment. House rent allowance, CEA, car. */
    PRORATE_PER_SEGMENT,

    /** One annual cap; the version in force at year end governs. Slab tables. */
    LATEST_VERSION_GOVERNS,

    /** One annual cap; the version at year start governs. Rare, and must be stated. */
    EARLIEST_VERSION_GOVERNS,

    /** The statute says how, and a named rule supplies it. */
    STATUTORY,

    /**
     * Nobody has decided. A rule that changes mid-year with this setting fails the computation.
     *
     * <p>The default for a newly authored change, deliberately: the pack author must state the
     * combination, and until they do, failing loudly beats a plausible wrong figure.
     */
    UNDETERMINED
}
