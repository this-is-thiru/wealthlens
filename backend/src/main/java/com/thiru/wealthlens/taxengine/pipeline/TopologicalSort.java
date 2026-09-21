package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxrules.id.NodeId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The evaluation order, derived from the edges rather than declared anywhere.
 *
 * <p>{@code 15-review-findings.md} B3. Nobody maintains this order: each node states what it
 * {@link ComputationNode#requires()} and the order falls out. That is the whole reason the pipeline
 * is a graph — a hand-kept list of thirty-one steps has to be re-reasoned every time a step is
 * added, and it is wrong silently.
 *
 * <h2>The order is canonical, not merely valid</h2>
 *
 * <p>A graph usually admits many valid topological orders. This returns <b>one</b> of them, always
 * the same one, by breaking ties on {@link NodeId}. That is not tidiness:
 * {@code TraceLine.sequence} comes from this order, the trace <b>is</b> the result
 * ({@code D-VIEWFROMTRACE}), and computations are cached, compared and replayed years later. An
 * order that varied with iteration order would make identical inputs produce traces that differ run
 * to run — surfacing as golden tests that fail for no reason, and as scenario diffs that report
 * changes nobody made.
 */
public final class TopologicalSort {

    private TopologicalSort() {}

    /**
     * Order these nodes so that every node follows everything it requires.
     *
     * @throws IllegalArgumentException if a required slot has no producer among these nodes, or if
     *     the edges contain a cycle — both are defects in what the node classes declare, so they
     *     name the nodes involved rather than reporting that sorting failed
     */
    public static List<ComputationNode> of(Collection<ComputationNode> nodes) {
        if (nodes == null) {
            throw new IllegalArgumentException("Cannot order a null collection of nodes");
        }

        Map<NodeId, ComputationNode> byProduces = new TreeMap<>(Comparator.comparing(NodeId::value));
        for (ComputationNode node : nodes) {
            if (node == null) {
                throw new IllegalArgumentException("Cannot order a null node");
            }
            ComputationNode clash = byProduces.put(node.produces(), node);
            if (clash != null) {
                throw new IllegalArgumentException(
                        "Two nodes produce " + node.produces()
                                + "; a slot has one writer, or the order is ambiguous");
            }
        }

        Map<NodeId, Set<NodeId>> waitingFor = new LinkedHashMap<>();
        Map<NodeId, Set<NodeId>> unblocks = new LinkedHashMap<>();
        byProduces.keySet().forEach(id -> unblocks.put(id, new LinkedHashSet<>()));

        for (ComputationNode node : byProduces.values()) {
            Set<NodeId> edges = new LinkedHashSet<>();
            for (NodeId required : node.requires()) {
                if (!byProduces.containsKey(required)) {
                    throw new IllegalArgumentException(
                            "Node " + node.produces() + " requires " + required
                                    + ", which no node here produces. The pipeline holds "
                                    + byProduces.keySet()
                                    + ". Either the pipeline is missing a node, or "
                                    + node.produces() + " names a slot that no longer exists");
                }
                edges.add(required);
                unblocks.get(required).add(node.produces());
            }
            waitingFor.put(node.produces(), edges);
        }

        // Kahn's algorithm. The ready set is kept sorted, so ties resolve the same way every run.
        Deque<NodeId> ready = new ArrayDeque<>();
        waitingFor.entrySet().stream()
                .filter(entry -> entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(NodeId::value))
                .forEach(ready::add);

        List<ComputationNode> ordered = new ArrayList<>(byProduces.size());
        while (!ready.isEmpty()) {
            NodeId next = ready.poll();
            ordered.add(byProduces.get(next));

            List<NodeId> freed = new ArrayList<>();
            for (NodeId dependent : unblocks.get(next)) {
                Set<NodeId> remaining = waitingFor.get(dependent);
                remaining.remove(next);
                if (remaining.isEmpty()) {
                    freed.add(dependent);
                }
            }
            freed.sort(Comparator.comparing(NodeId::value));
            freed.forEach(ready::add);
        }

        if (ordered.size() != byProduces.size()) {
            throw new IllegalArgumentException(cycleMessage(waitingFor, unblocks, ordered.size()));
        }
        return List.copyOf(ordered);
    }

    /**
     * Name the cycle, not the symptom.
     *
     * <p>"Sorting failed" sends someone to read thirty node classes. A named cycle —
     * {@code a -> b -> c -> a} — is the defect itself, and the two declarations that disagree are
     * at its ends.
     */
    private static String cycleMessage(
            Map<NodeId, Set<NodeId>> waitingFor, Map<NodeId, Set<NodeId>> unblocks, int ordered) {
        Set<NodeId> stuck = new LinkedHashSet<>();
        waitingFor.forEach((id, edges) -> {
            if (!edges.isEmpty()) {
                stuck.add(id);
            }
        });

        // Arrows read "feeds into", matching the pipeline diagrams in B3 -- so the path is walked
        // along unblocks, not along waitingFor, which is the same cycle drawn backwards.
        Map<NodeId, Set<NodeId>> feeds = new TreeMap<>(Comparator.comparing(NodeId::value));
        for (NodeId id : stuck) {
            Set<NodeId> onward = new LinkedHashSet<>(unblocks.get(id));
            onward.retainAll(stuck);
            feeds.put(id, onward);
        }

        List<NodeId> cycle = findCycle(feeds);
        StringBuilder path = new StringBuilder();
        cycle.forEach(id -> path.append(id.value()).append(" -> "));
        if (!cycle.isEmpty()) {
            path.append(cycle.getFirst().value());
        }

        return "The pipeline has a dependency cycle: " + path
                + ". Evaluation order is derived from requires(), so a cycle means two nodes each"
                + " wait for the other and neither can ever run. " + ordered + " of "
                + (ordered + stuck.size()) + " nodes could be ordered; these could not: "
                + feeds.keySet();
    }

    /** Walks the remaining edges until it revisits a node — that repeat is the cycle. */
    private static List<NodeId> findCycle(Map<NodeId, Set<NodeId>> feeds) {
        if (feeds.isEmpty()) {
            return List.of();
        }
        List<NodeId> path = new ArrayList<>();
        Set<NodeId> seen = new LinkedHashSet<>();
        NodeId at = feeds.keySet().iterator().next();

        while (seen.add(at)) {
            path.add(at);
            Set<NodeId> onward = feeds.get(at);
            if (onward == null || onward.isEmpty()) {
                return path;
            }
            at = onward.stream().min(Comparator.comparing(NodeId::value)).orElseThrow();
        }
        return path.subList(path.indexOf(at), path.size());
    }
}
