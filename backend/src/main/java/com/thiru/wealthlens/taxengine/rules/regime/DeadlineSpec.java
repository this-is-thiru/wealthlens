package com.thiru.wealthlens.taxengine.rules.regime;

/**
 * When a choice or a claim must be made by.
 *
 * <p>Resolved against the tax calendar rather than stored as a date, so that a deadline stays
 * correct when a due date is extended — which happens most years.
 *
 * <p>Also used by the claim catalogue ({@code 22-claim-catalogue.md} C4), which is why this is a
 * shared type rather than a field on {@link ElectionProtocol}.
 */
public sealed interface DeadlineSpec {

    /**
     * The due date under a named provision, e.g. s.263(1).
     *
     * @param section as cited, e.g. {@code "263(1)"}
     */
    record DueDateUnder(String section) implements DeadlineSpec {

        public DueDateUnder {
            if (section == null || section.isBlank()) {
                throw new IllegalArgumentException("DueDateUnder must name the provision it reads");
            }
        }
    }

    /** Whenever the return is furnished, with no separate date of its own. */
    record WithTheReturn() implements DeadlineSpec {}

    /**
     * No deadline, because nothing has to be done.
     *
     * <p><b>Not stated by the design.</b> Added because {@code ElectionProtocol.deadline} is not
     * optional and {@link ElectionKind#DEFAULT_NO_ACTION} — the regime you get by doing nothing —
     * has no deadline to state. The alternative was making the field optional, which contradicts
     * {@code 17-regime-lifecycle.md} L3's declared shape.
     */
    record NoDeadline() implements DeadlineSpec {}
}
