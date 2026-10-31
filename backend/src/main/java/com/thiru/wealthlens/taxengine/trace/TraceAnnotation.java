package com.thiru.wealthlens.taxengine.trace;

import java.util.List;

/**
 * Everything said <i>about</i> a {@link TraceLine}, in a layer of its own.
 *
 * <h2>Why this is not fields on the line</h2>
 *
 * <p>{@code 23-architecture-consolidation.md} A8 (defect G-7). Commentary has a different lifetime
 * from the computation it describes. It is derived from {@code content/}, so it changes when an
 * explanation is re-worded, when the claim catalogue gains a step, or when a priced alternative is
 * re-costed — none of which is a change to a number. Keeping the two apart buys three things:
 *
 * <ul>
 *   <li>a cached {@code TaxComputation} survives a content edit untouched;
 *   <li>invariant I27 becomes checkable — perturbing {@code content/} must change annotations and
 *       <b>never</b> the trace, which is only expressible because they are separate objects;
 *   <li>the API gets a natural projection seam: {@code ?view=summary} omits annotations.
 * </ul>
 *
 * <h2>This record will grow</h2>
 *
 * <p>A8 also lists an {@code ExplanationRef}, a {@code ClaimCatalogueRef} and a list of
 * {@code PricedAlternative} — <b>three types the design names here and declares nowhere</b>. They
 * are in no work item, and in neither of the backlog's two type audits: Appendix A covered types
 * the design declares, Appendix B types named in a {@code permits} clause, and a type named only as
 * a record component in a design code block falls between them. Each component arrives with the
 * item that owns its type, the way {@code CapShape} and {@code NodeValue} grew (rule B2).
 *
 * <p>Note the cost, which a sealed family does not have: adding a component to a record changes
 * every construction site. Today there are none outside this item's tests.
 *
 * @param sequence the {@link TraceLine#sequence()} this is about — the only link between the layers,
 *     and the reason a projection may hide lines but must never renumber them
 * @param notices what is said; may be empty, never null
 */
public record TraceAnnotation(int sequence, List<Notice> notices) {

    public TraceAnnotation {
        if (sequence < 0) {
            throw new IllegalArgumentException(
                    "TraceAnnotation.sequence must not be negative, but was " + sequence);
        }
        if (notices == null) {
            throw new IllegalArgumentException(
                    "TraceAnnotation.notices may not be null; pass an empty list where there are"
                            + " none");
        }
        notices = List.copyOf(notices);
    }

    /** The annotation for a line that has one thing said about it. */
    public static TraceAnnotation of(int sequence, Notice notice) {
        return new TraceAnnotation(sequence, List.of(notice));
    }

    /** True where there is nothing to say — a summary projection drops these. */
    public boolean isEmpty() {
        return notices.isEmpty();
    }
}
