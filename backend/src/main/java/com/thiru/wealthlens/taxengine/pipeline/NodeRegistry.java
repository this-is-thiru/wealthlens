package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxrules.id.NodeId;
import com.thiru.wealthlens.taxrules.id.RuleId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Every node the engine can run, addressed by what it produces.
 *
 * <p>{@code 15-review-findings.md} B2 and B3. A pipeline in a pack names {@link NodeId}s; this is
 * what turns those names into nodes. Registration is where a graph's structural rules are enforced,
 * because every one of them is cheaper to catch here than during a run:
 *
 * <ul>
 *   <li><b>two nodes producing the same slot</b> — the graph would be ambiguous and which value
 *       survived would depend on iteration order;
 *   <li><b>a node whose {@code requires()} is null</b> — the order is derived from that set, so it
 *       must exist, though it may be empty: a <b>source</b> node reads {@code EngineInputs} through
 *       {@code ctx.inputs()} rather than an upstream slot, and every acyclic graph needs one
 *       (decision <b>D-031</b>, blocker <b>B-004</b>);
 *   <li><b>a node requiring what it produces</b> — a self-edge, which no topological order exists
 *       for.
 * </ul>
 */
public final class NodeRegistry {

    private final Map<NodeId, ComputationNode> byProduces;

    private NodeRegistry(Map<NodeId, ComputationNode> byProduces) {
        this.byProduces = byProduces;
    }

    public static NodeRegistry of(Collection<ComputationNode> nodes) {
        if (nodes == null) {
            throw new IllegalArgumentException("A NodeRegistry needs a collection of nodes");
        }
        Map<NodeId, ComputationNode> registered = new LinkedHashMap<>();
        for (ComputationNode node : nodes) {
            validate(node);
            ComputationNode clash = registered.put(node.produces(), node);
            if (clash != null) {
                throw new IllegalArgumentException(
                        "Two nodes produce " + node.produces() + ": "
                                + clash.getClass().getSimpleName() + " and "
                                + node.getClass().getSimpleName()
                                + ". A slot has one writer, or which value survives depends on the"
                                + " order they happened to be registered in");
            }
        }
        return new NodeRegistry(registered);
    }

    private static void validate(ComputationNode node) {
        if (node == null) {
            throw new IllegalArgumentException("A NodeRegistry may not hold a null node");
        }
        if (node.produces() == null) {
            throw new IllegalArgumentException(
                    node.getClass().getSimpleName() + " must name the slot it produces");
        }
        if (node.requires() == null) {
            throw new IllegalArgumentException(
                    "Node " + node.produces() + " must state what it requires, even if nothing;"
                            + " the evaluation order is derived from that set");
        }
        if (node.rules() == null) {
            throw new IllegalArgumentException(
                    "Node " + node.produces() + " must state the rules it evaluates, even if none;"
                            + " reachability (invariant I3) is computed from that set");
        }
        if (node.requires().contains(node.produces())) {
            throw new IllegalArgumentException(
                    "Node " + node.produces() + " requires what it produces. No evaluation order"
                            + " exists for a self-edge");
        }
    }

    /** The node writing one slot. */
    public ComputationNode producing(NodeId node) {
        ComputationNode found = byProduces.get(node);
        if (found == null) {
            throw new IllegalArgumentException(
                    "No node produces " + node + "; the registry holds " + byProduces.keySet());
        }
        return found;
    }

    /** The nodes a pipeline names, in the order named. Ordering for evaluation is W-040's. */
    public List<ComputationNode> resolveAll(Collection<NodeId> nodes) {
        if (nodes == null) {
            throw new IllegalArgumentException("Cannot resolve a null collection of node ids");
        }
        return nodes.stream().map(this::producing).toList();
    }

    public Set<NodeId> registered() {
        return Set.copyOf(byProduces.keySet());
    }

    public int size() {
        return byProduces.size();
    }

    /**
     * Invariant <b>I3</b> — the rules in a pack that no registered node evaluates.
     *
     * <h2>Why a pack-only version would not do</h2>
     *
     * <p>{@code 02-TECH-SPEC.md} T5 words I3 as "every {@code RuleId} in the pack is reachable from
     * some pipeline", and {@code W-029} deferred it here because reachability means <b>evaluated by
     * some node</b>, and the node-to-rule mapping is {@link ComputationNode#rules()} — engine-side.
     * A version computed from the pack alone would have to count a rule as reachable because some
     * component mentions it, which passes exactly the dead rule {@code O-19} describes: a deduction
     * present in the pack, absent from every answer, and silent about it.
     *
     * <p><b>This is not yet wired to the shipped pack, deliberately.</b> There are no
     * {@link ComputationNode} implementations until Phases 5 to 9, so run it against the real
     * 2026-27 pack today and every rule reports unreachable. The check is built and proven against
     * fixtures now; enabling it against a real pipeline is a call at the item that first ships one
     * (decision <b>D-029</b>).
     *
     * @param packRuleIds every rule the pack declares
     * @return those no node evaluates, sorted, so the message is stable and diffable
     */
    public Set<RuleId> rulesNotEvaluatedByAnyNode(Collection<RuleId> packRuleIds) {
        if (packRuleIds == null) {
            throw new IllegalArgumentException("Cannot check reachability against a null rule set");
        }
        Set<RuleId> evaluated = new LinkedHashSet<>();
        byProduces.values().forEach(node -> evaluated.addAll(node.rules()));

        Set<RuleId> unreachable = new TreeSet<>(java.util.Comparator.comparing(RuleId::value));
        packRuleIds.stream().filter(id -> !evaluated.contains(id)).forEach(unreachable::add);
        return unreachable;
    }
}
