package com.thiru.wealthlens.taxengine.rules.regime;

import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import java.util.List;

/**
 * Which computation nodes a regime runs.
 *
 * <h2>This is a set, not an order</h2>
 *
 * <p>Execution order is <b>derived</b>, by topological sort over each node's declared
 * {@code requires} and {@code produces}. This list says which nodes take part; it does not say when
 * they run, and two packs listing the same nodes in different orders compute identically.
 *
 * <p>Worth stating plainly because the pack JSON happens to list them in a plausible running order,
 * and that is exactly how someone comes to depend on it. It is a {@code List} only because JSON has
 * no set literal and round-tripping should not reorder it.
 *
 * <p>Each regime carries its own — {@code D-REGIMESTEP}. A step that exists in one regime and not
 * another is a different node set, never an {@code if} inside a node.
 *
 * @param nodeRefs the nodes this regime runs; order is not significant
 */
public record PipelineSpec(List<NodeId> nodeRefs) {

    public PipelineSpec {
        if (nodeRefs == null || nodeRefs.isEmpty()) {
            throw new IllegalArgumentException(
                    "A pipeline must name at least one node; a regime that runs nothing computes nothing");
        }
        nodeRefs = List.copyOf(nodeRefs);

        if (nodeRefs.size() != java.util.Set.copyOf(nodeRefs).size()) {
            throw new IllegalArgumentException(
                    "A node is listed twice in " + nodeRefs
                            + "; since order is not significant, a repeat is a mistake rather than a rerun");
        }
    }

    /** Whether this regime runs a given node. */
    public boolean runs(NodeId node) {
        return nodeRefs.contains(node);
    }
}
