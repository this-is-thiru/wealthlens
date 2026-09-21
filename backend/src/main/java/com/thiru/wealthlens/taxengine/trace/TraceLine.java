package com.thiru.wealthlens.taxengine.trace;

import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxrules.id.NodeId;
import com.thiru.wealthlens.taxrules.id.RuleId;
import com.thiru.wealthlens.taxrules.registry.Provenance;
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
 */
public record TraceLine(
        int sequence,
        NodeId node,
        RuleId rule,
        StepKind kind,
        NodeValue in,
        NodeValue out,
        Optional<String> limitedBy,
        Provenance provenance) {

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
        return new TraceLine(sequence, node, rule, kind, in, out, Optional.empty(), provenance);
    }

    /** A step whose output was held down by a cap or a competing limb. */
    public static TraceLine limited(
            int sequence,
            NodeId node,
            RuleId rule,
            StepKind kind,
            NodeValue in,
            NodeValue out,
            String limitedBy,
            Provenance provenance) {
        return new TraceLine(
                sequence, node, rule, kind, in, out, Optional.of(limitedBy), provenance);
    }
}
