package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.InputsFingerprint;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import com.thiru.wealthlens.taxrules.id.NodeId;
import com.thiru.wealthlens.taxrules.pack.RulePack;
import com.thiru.wealthlens.taxrules.pack.RulePackLayer;
import com.thiru.wealthlens.taxrules.pack.RulePackRef;
import com.thiru.wealthlens.taxrules.regime.RegimeSpec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks the graph and collects the answer.
 *
 * <h2>It catches nothing</h2>
 *
 * <p>{@code 15-review-findings.md} B3, and {@code D-NOSILENT}. There is no {@code try} in this
 * class and there must never be one. The failure this prevents is the one the placeholder actually
 * had: a rule that could not be evaluated, caught, and silently replaced with zero — so a deduction
 * vanished and someone filed on the result. A computation that cannot be completed must stop, not
 * produce a smaller number. {@code ArchitectureTest.taxengine_does_not_swallow_failures} enforces
 * the same thing structurally.
 *
 * <h2>Order and scope come from the nodes</h2>
 *
 * <p>The pipeline names which nodes run; {@link TopologicalSort} decides when, from the edges they
 * declared; and each node evaluates through a context scoped to its own {@code requires()}, so
 * reading anything else throws rather than picking up whatever the ordering happened to leave
 * behind.
 */
public final class PipelineRunner {

    private PipelineRunner() {}

    /**
     * What one run produced.
     *
     * <p>Nested because it is this class's output shape and nothing else's — for now.
     * {@code 23-architecture-consolidation.md} A6 splits the durable answer into
     * {@code TaxComputation} (date-independent, cached forever) and {@code FilingPosition}
     * (date-dependent, recomputed freely); building those is {@code W-043}, which projects from
     * this. B3 has the runner return a {@code Computation} directly, which it cannot: that type
     * belongs to a later item, and its {@code totalIncome} / {@code taxByBasis} fields need
     * well-known node anchors that do not exist yet.
     *
     * @param order the nodes as evaluated, which is the canonical order for this graph
     * @param values every node's output, keyed by the slot it wrote
     * @param trace the whole explanation, renumbered into one ascending sequence
     * @param inputs the fingerprint of what was computed from — half of the cache key
     * @param lawAndExec the pack layers that can change a number; {@code content} and {@code forms}
     *     are deliberately excluded, which is what makes an editorial revision free (G-11)
     */
    public record Result(
            List<NodeId> order,
            Map<NodeId, NodeValue> values,
            List<TraceLine> trace,
            InputsFingerprint inputs,
            Map<RulePackLayer, RulePackRef> lawAndExec) {

        public Result {
            order = List.copyOf(order);
            values = Map.copyOf(values);
            trace = List.copyOf(trace);
            lawAndExec = Map.copyOf(lawAndExec);
        }

        /** One node's output. */
        public NodeValue valueOf(NodeId node) {
            NodeValue value = values.get(node);
            if (value == null) {
                throw new IllegalArgumentException(
                        "This run produced no value for " + node + "; it ran " + order);
            }
            return value;
        }
    }

    /**
     * Run the regime's pipeline.
     *
     * @throws IllegalArgumentException if the pipeline names a node the registry does not hold, if
     *     the edges do not admit an order, or if a node requires a slot this pipeline does not
     *     produce
     */
    public static Result run(
            NodeRegistry registry, RulePack pack, RegimeSpec regime, EngineInputs inputs) {
        if (registry == null || pack == null || regime == null || inputs == null) {
            throw new IllegalArgumentException(
                    "A pipeline run needs a registry, a pack, a regime and the inputs; the engine"
                            + " fetches none of them for itself");
        }

        List<ComputationNode> ordered =
                TopologicalSort.of(registry.resolveAll(regime.pipeline().nodeRefs()));

        NodeContext context = NodeContext.forRun(pack, regime, inputs);
        List<NodeId> order = new ArrayList<>(ordered.size());
        Map<NodeId, NodeValue> values = new LinkedHashMap<>();
        List<TraceLine> trace = new ArrayList<>();

        for (ComputationNode node : ordered) {
            // No try/catch. A node that cannot evaluate stops the run -- D-NOSILENT.
            NodeResult result = node.evaluate(context.forNode(node));

            context.put(node.produces(), result.value());
            order.add(node.produces());
            values.put(node.produces(), result.value());
            result.trace().forEach(line -> trace.add(renumbered(line, trace.size() + 1)));
        }

        return new Result(
                order,
                values,
                trace,
                InputsFingerprint.of(inputs),
                computationLayersOf(pack));
    }

    /**
     * Put a line in its place in the whole trace.
     *
     * <p>A node emits its lines without knowing where it sits in the run, so the sequence it
     * carries is a local ordinal — every node's first line would otherwise be line 1, and
     * {@code TraceAnnotation}, which addresses a line <b>by sequence</b>, could not tell ten of
     * them apart. Only the runner knows the order, so only the runner can number. Nodes keep their
     * lines' relative order; the numbers become global.
     */
    private static TraceLine renumbered(TraceLine line, int sequence) {
        return new TraceLine(
                sequence,
                line.node(),
                line.rule(),
                line.kind(),
                line.in(),
                line.out(),
                line.limitedBy(),
                line.provenance());
    }

    /** The pack layers that can change a number — A2 and G-11. */
    private static Map<RulePackLayer, RulePackRef> computationLayersOf(RulePack pack) {
        Map<RulePackLayer, RulePackRef> layers = new EnumMap<>(RulePackLayer.class);
        pack.layers().forEach((layer, ref) -> {
            if (layer.affectsComputation()) {
                layers.put(layer, ref);
            }
        });
        return layers;
    }
}
