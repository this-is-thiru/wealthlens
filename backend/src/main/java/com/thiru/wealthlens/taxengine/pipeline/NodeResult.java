package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.trace.TraceAnnotation;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;

/**
 * What one node produced: a value, and the trace lines explaining how it got there.
 *
 * <p>{@code 15-review-findings.md} B2. The trace is not a log of the computation, it <b>is</b> the
 * result ({@code M5.1}, {@code D-VIEWFROMTRACE}) — so a node hands back both, and a node that
 * produced a figure without explaining it has not finished.
 *
 * <p>What the node has to say <i>about</i> a line — a notice — travels beside the trace as a {@link TraceAnnotation}
 * addressing that line, never as a free list of warnings ({@code Notice}, O-5; {@code 23-architecture-consolidation.md}
 * A8; D-167). The annotation carries the line's sequence as the node numbered it; the runner renumbers both together.
 *
 * @param value what this node computed
 * @param trace the lines it emitted; may be empty for a node that merely re-shapes a value, never
 *     null
 * @param annotations what it says about its own lines, each addressing exactly one of them; may be empty, never null
 */
public record NodeResult(NodeValue value, List<TraceLine> trace, List<TraceAnnotation> annotations) {

    public NodeResult {
        if (value == null) {
            throw new IllegalArgumentException(
                    "A NodeResult must carry a value; a node that computed nothing says so with a"
                            + " zero, because a null is O-1's silent zero");
        }
        if (trace == null) {
            throw new IllegalArgumentException(
                    "NodeResult.trace may not be null; pass an empty list where the node emitted no"
                            + " lines");
        }
        trace = List.copyOf(trace);
        if (annotations == null) {
            throw new IllegalArgumentException(
                    "NodeResult.annotations may not be null; pass an empty list where the node says nothing");
        }
        annotations = List.copyOf(annotations);
        for (TraceAnnotation annotation : annotations) {
            long addressed = trace.stream().filter(line -> line.sequence() == annotation.sequence()).count();
            if (addressed != 1) {
                throw new IllegalArgumentException("An annotation addresses line " + annotation.sequence() + ", which "
                        + addressed + " of this node's lines carry; a notice hangs off exactly one line it produced");
            }
        }
    }

    /** A value and its lines, with nothing said about them. */
    public NodeResult(NodeValue value, List<TraceLine> trace) {
        this(value, trace, List.of());
    }

    /** A value with one explaining line — the ordinary case. */
    public static NodeResult of(NodeValue value, TraceLine line) {
        return new NodeResult(value, List.of(line));
    }

    /** A value that needs no line of its own, because it only re-shapes what upstream produced. */
    public static NodeResult untraced(NodeValue value) {
        return new NodeResult(value, List.of());
    }
}
