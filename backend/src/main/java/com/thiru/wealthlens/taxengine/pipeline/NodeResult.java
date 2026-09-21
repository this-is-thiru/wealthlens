package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;

/**
 * What one node produced: a value, and the trace lines explaining how it got there.
 *
 * <p>{@code 15-review-findings.md} B2. The trace is not a log of the computation, it <b>is</b> the
 * result ({@code M5.1}, {@code D-VIEWFROMTRACE}) — so a node hands back both, and a node that
 * produced a figure without explaining it has not finished.
 *
 * @param value what this node computed
 * @param trace the lines it emitted; may be empty for a node that merely re-shapes a value, never
 *     null
 */
public record NodeResult(NodeValue value, List<TraceLine> trace) {

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
