package com.thiru.wealthlens.taxengine.trace;

import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.pipeline.RegimeVerdict;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.cap.Headroom;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import java.util.Optional;

/**
 * One step of the computation: what was done, under which rule, to what, producing what.
 *
 * <h2>The trace is the result, not a log of it</h2>
 *
 * <p>{@code 05-domain-model.md} M5.1, {@code D-VIEWFROMTRACE}. The JSON response, the PDF and the
 * ITR figures are all <b>projections</b> of a list of these. Nothing computes a number a second time
 * for display, so a PDF section with no matching lines renders as visibly nothing rather than as a
 * plausible wrong figure.
 *
 * <h2>Values and provenance only</h2>
 *
 * <p>{@code 23-architecture-consolidation.md} A8 (defect G-7). This record used to carry four
 * unrelated concerns; everything <i>said about</i> a line now lives in {@link TraceAnnotation},
 * which has a separate lifetime and may be absent. The division is not tidiness. Annotations are
 * derived from {@code content/}, so editing an explanation changes them and must never change a
 * number here — a property that is only checkable because the two are separate objects.
 *
 * <p>For the same reason this record carries <b>no display label</b>. A8 drops M5.1's {@code label},
 * and rightly: pack text is resolved from {@link #rule()} at render time, so re-wording a label does
 * not invalidate a cached computation.
 *
 * @param sequence position in the trace, ascending; gaps are allowed, since a projection may hide
 *     lines but must never renumber them
 * @param node the node that produced this line — the address in the DAG
 * @param rule the rule applied, and the key every reader uses; reports, the ITR mapping and
 *     {@code ScenarioDiff} all read by rule id, never by index or field name (M5.1)
 * @param kind what sort of step this was
 * @param in what went in
 * @param out what came out
 * @param limitedBy why the output was smaller than it might have been — a {@code LeastOf} limb name,
 *     or the cap that bound. Empty where nothing bound
 * @param provenance where the rule came from and whether it is verified
 * @param reason why the output is what it is, as facts the pack's content puts into words (F37.3; W-327a, D-678).
 *     A computed fact like {@code limitedBy}, which stays the audit text, and not commentary: nothing in
 *     {@code content/} is read to build it, so a content edit still changes no line (I27). Empty where the
 *     line's node publishes none yet, and the explanation then shows {@code limitedBy}
 * @param headroom what would change the figure, where a least-of could say (W-326a, D-696): a computed fact like
 *     {@code reason}, carried from the cap; empty otherwise
 */
public record TraceLine(
        int sequence,
        NodeId node,
        RuleId rule,
        StepKind kind,
        NodeValue in,
        NodeValue out,
        Optional<String> limitedBy,
        Provenance provenance,
        Optional<Reason> reason,
        Optional<Headroom> headroom) {

    public TraceLine {
        if (sequence < 0) {
            throw new IllegalArgumentException(
                    "TraceLine.sequence must not be negative, but was " + sequence);
        }
        if (node == null) {
            throw new IllegalArgumentException("A TraceLine must name the node that produced it");
        }
        if (rule == null) {
            throw new IllegalArgumentException(
                    "A TraceLine must name its rule; every figure is addressable by rule id");
        }
        if (kind == null) {
            throw new IllegalArgumentException("A TraceLine must state its StepKind");
        }
        if (in == null || out == null) {
            throw new IllegalArgumentException(
                    "A TraceLine must carry both its in and out values; a step that consumes or"
                            + " produces nothing says so with a zero, because a zero with a reason is"
                            + " the point of the trace and a null is a silent zero");
        }
        if (limitedBy == null) {
            throw new IllegalArgumentException(
                    "TraceLine.limitedBy may not be null; pass Optional.empty() where nothing bound");
        }
        if (limitedBy.isPresent() && limitedBy.get().isBlank()) {
            throw new IllegalArgumentException(
                    "TraceLine.limitedBy is present but blank; say what bound the figure, or pass"
                            + " Optional.empty()");
        }
        if (provenance == null) {
            throw new IllegalArgumentException(
                    "A TraceLine must carry its provenance; an unsourced figure is what this engine"
                            + " exists to avoid");
        }
        requireOptional(reason, "reason");
        requireOptional(headroom, "headroom");
    }

    private static void requireOptional(Optional<?> value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("TraceLine." + field + " may not be null; pass Optional.empty() where the"
                    + " node publishes none");
        }
    }

    /** A step that nothing bound. */
    public static TraceLine of(
            int sequence,
            NodeId node,
            RuleId rule,
            StepKind kind,
            NodeValue in,
            NodeValue out,
            Provenance provenance) {
        return new TraceLine(sequence, node, rule, kind, in, out, Optional.empty(), provenance, Optional.empty(),
                Optional.empty());
    }

    /**
     * This line, recording what held its output down — a {@code LeastOf} limb name or the cap that
     * bound. A copy; the record is immutable.
     */
    public TraceLine withLimitedBy(String bound) {
        return new TraceLine(sequence, node, rule, kind, in, out, Optional.of(bound), provenance, reason, headroom);
    }

    /**
     * This line, for a rule the regime does not apply: the verdict's {@code whyNot} as {@code limitedBy}, the audit
     * text with its provision, and its reason as facts for the explanation (W-327a, D-678).
     */
    public TraceLine withRegimeVerdict(RegimeVerdict verdict) {
        return withLimitedBy(verdict.whyNot()).withReason(verdict.reason());
    }

    /**
     * This line, limited by a cap: the cap's {@code limitedBy} as the audit text, and its reason where the cap
     * publishes one (W-327a1, D-679) — a least-of's limb is worded by name and carries none.
     */
    public TraceLine withCapResult(CapResult capped) {
        TraceLine limited = new TraceLine(sequence, node, rule, kind, in, out, Optional.of(capped.limitedBy()),
                provenance, reason, capped.headroom());
        return capped.reason().map(limited::withReason).orElse(limited);
    }

    /** This line, carrying why its output is what it is as facts (W-327a, D-678); {@code limitedBy} is kept. */
    public TraceLine withReason(Reason why) {
        return new TraceLine(sequence, node, rule, kind, in, out, limitedBy, provenance, Optional.of(why), headroom);
    }
}
