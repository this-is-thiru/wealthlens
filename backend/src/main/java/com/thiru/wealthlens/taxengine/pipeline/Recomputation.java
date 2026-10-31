package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.id.NodeId;

/**
 * A slot recomputed as it would be had an earlier slot held another value.
 *
 * <p>Surcharge marginal relief needs U, the tax at exactly a threshold, and {@code DR 06} G3.2 calls
 * re-entering the computation "the only honest way to get U when the slab ladder, special-rate split and
 * rebate all bear on it" — pricing it in the asking node would duplicate those nodes (D-086). Only the
 * runner holds the graph, so the runner supplies this; a node reaches it through
 * {@link NodeContext#recomputed}, which holds it to the slots it declared.
 */
@FunctionalInterface
public interface Recomputation {

    /** {@code target} as it would be had {@code pinned} been {@code value}. */
    NodeValue recompute(NodeId target, NodeId pinned, NodeValue value);
}
