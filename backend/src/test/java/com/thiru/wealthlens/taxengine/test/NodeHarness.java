package com.thiru.wealthlens.taxengine.test;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.input.LedgerSnapshot;
import com.thiru.wealthlens.taxengine.input.RegimeElectionSnapshot;
import com.thiru.wealthlens.taxengine.input.TaxpayerFacts;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Runs one node on its own, with stubbed inputs.
 *
 * <h2>Why a node can be tested alone at all</h2>
 *
 * <p>{@code 23-architecture-consolidation.md} A11, case {@code T23-10} and goal {@code G-12}. This
 * only works because a node declares {@link ComputationNode#requires()}: the harness supplies
 * <b>exactly</b> those and refuses anything else, so a node that secretly reaches for something is
 * a failure at the node level, before any pipeline exists to hide it.
 *
 * <pre>{@code
 * var result = NodeHarness.with(pack)
 *         .regime(new RegimeCode("NEW_TAX_REGIME"))
 *         .given(new NodeId("totalincome"), Money.of("1200000"))
 *         .run(new TaxByBasisNode());
 * }</pre>
 *
 * <h2>It brings its own inputs</h2>
 *
 * <p>A11's sketch names a pack and a regime; {@link NodeContext} also needs an
 * {@link EngineInputs}, which is ten components. The harness supplies a plain one — a resident
 * individual, no family, empty ledgers, nothing elected, no capital gains, return not yet furnished
 * — so that a test which does not care about the taxpayer does not have to describe one. Tests that
 * do care pass {@link #inputs(EngineInputs)}.
 *
 * <p>Deliberately one fixture rather than one per test class: the alternative is every node test
 * rebuilding ten components, and they drift.
 *
 * <p>Test scope. It is in {@code ..taxengine..} but not production code, and
 * {@code ArchitectureTest} scans with {@code DoNotIncludeTests}, so the engine-purity rules do not
 * apply to it — which is right: the harness exists to stand in for the things the engine may not do
 * for itself.
 */
public final class NodeHarness {

    private final RulePack pack;
    private final Map<NodeId, NodeValue> stubs = new LinkedHashMap<>();
    private RegimeSpec regime;
    private EngineInputs inputs;

    private NodeHarness(RulePack pack) {
        this.pack = pack;
    }

    public static NodeHarness with(RulePack pack) {
        if (pack == null) {
            throw new IllegalArgumentException("A NodeHarness needs a rule pack");
        }
        return new NodeHarness(pack);
    }

    /** The regime to run under, looked up in the pack so a test cannot invent one. */
    public NodeHarness regime(RegimeCode code) {
        RegimeSpec found = pack.regimes().get(code);
        if (found == null) {
            throw new IllegalArgumentException(
                    "Pack " + pack.taxYear() + " has no regime " + code + "; it holds "
                            + pack.regimes().keySet());
        }
        this.regime = found;
        return this;
    }

    public NodeHarness regime(RegimeSpec spec) {
        this.regime = spec;
        return this;
    }

    /** Stub one of the node's required slots. */
    public NodeHarness given(NodeId node, NodeValue value) {
        if (node == null || value == null) {
            throw new IllegalArgumentException("given() needs both a node id and a value");
        }
        stubs.put(node, value);
        return this;
    }

    /** The common case: a slot holding a single amount. */
    public NodeHarness given(NodeId node, Money amount) {
        return given(node, MoneyValue.of(amount));
    }

    /** Replace the default taxpayer with one the test cares about. */
    public NodeHarness inputs(EngineInputs inputs) {
        this.inputs = inputs;
        return this;
    }

    /**
     * Evaluate the node.
     *
     * @throws IllegalArgumentException if the stubs and the node's {@code requires()} disagree in
     *     either direction — see the class note
     */
    public NodeResult run(ComputationNode node) {
        if (node == null) {
            throw new IllegalArgumentException("NodeHarness.run() needs a node");
        }
        if (regime == null) {
            throw new IllegalArgumentException(
                    "No regime chosen. Call .regime(...) before .run(...) -- a node evaluates rules"
                            + " whose applicability depends on it");
        }
        Set<NodeId> required = Set.copyOf(node.requires());

        Set<NodeId> missing = sortedDifference(required, stubs.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Node " + node.produces() + " requires " + missing
                            + ", which the harness was not given. Supply each with .given(id, value)"
                            + " -- the harness provides exactly what a node declares, so that a node"
                            + " reaching for anything else fails here rather than in a pipeline");
        }

        Set<NodeId> unused = sortedDifference(stubs.keySet(), required);
        if (!unused.isEmpty()) {
            throw new IllegalArgumentException(
                    "The harness was given " + unused + ", which node " + node.produces()
                            + " does not require. It requires " + sortedDifference(required, Set.of())
                            + ". A stub the node cannot read means the test and the node disagree"
                            + " about what it reads, and the test would pass for the wrong reason");
        }

        NodeContext run = NodeContext.forRun(pack, regime, inputs == null ? plainTaxpayer() : inputs);
        stubs.forEach(run::put);
        return node.evaluate(run.forNode(node));
    }

    private static Set<NodeId> sortedDifference(Set<NodeId> from, Set<NodeId> minus) {
        Set<NodeId> difference =
                new TreeSet<>(java.util.Comparator.comparing(NodeId::value));
        from.stream().filter(id -> !minus.contains(id)).forEach(difference::add);
        return new LinkedHashSet<>(difference);
    }

    /**
     * The default input set: a resident individual with nothing remarkable about them.
     *
     * <p>Chosen so that a node test which does not care about the taxpayer says nothing about one.
     * Everything here is the empty or neutral value of its type, so a test that depends on any of
     * it is depending on something it did not state — and should call {@link #inputs}.
     */
    public static EngineInputs plainTaxpayer() {
        TaxYearId year = TaxYearId.of(2026);
        return new EngineInputs(
                year,
                new RegimeCode("NEW_TAX_REGIME"),
                TemporalContext.forTaxYear(year, LocalDate.of(2026, 9, 21)),
                EnteredFacts.none(),
                new TaxpayerFacts(
                        LocalDate.of(1985, 6, 15),
                        TaxpayerFacts.ResidentialStatus.RESIDENT,
                        Set.of(PersonType.INDIVIDUAL),
                        Optional.empty(),
                        Optional.empty()),
                FamilySnapshot.none(),
                LedgerSnapshot.empty(),
                RegimeElectionSnapshot.none(),
                CapitalGainsInput.none(Instant.parse("2026-09-01T00:00:00Z"), "harness-run"),
                FilingFacts.notYetFurnished(LocalDate.of(2026, 7, 31)));
    }
}
