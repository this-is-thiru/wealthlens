package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer;
import com.thiru.wealthlens.taxengine.rules.regime.PipelineSpec;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.Instrument;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The whole graph, run — and what happens when a node cannot compute.
 */
class PipelineRunnerTest {

    private static final NodeId COMPONENTS = NodeIds.INPUTS_COMPONENTS;
    private static final NodeId GROSS = NodeIds.SALARY_GROSS;
    // Not a slot of the shipped pipeline, so not a constant
    private static final NodeId SALARY = new NodeId("salary.income");
    private static final RuleId SLAB = new RuleId("RATE-SLAB-NEW-TAX-REGIME");

    private static RulePack pack() {
        Instrument act = Instrument.assented(
                "Income-tax Act, 2025",
                LocalDate.of(2025, 8, 21),
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-21082025-000000",
                URI.create("https://egazette.gov.in/WriteReadData/2025/000000.pdf"),
                VerificationLevel.SOURCE_CHECKED);
        Instrument rules = Instrument.notified(
                "Income-tax Rules, 2026",
                LocalDate.of(2026, 4, 1),
                "CG-DL-E-20032026-271092",
                URI.create("https://egazette.gov.in/WriteReadData/2026/271092.pdf"),
                VerificationLevel.SOURCE_CHECKED);
        return new ClasspathRulePackProvider(
                        "rulepacks",
                        Set.of(TaxYearId.of(2026)),
                        new JsonPackAssembler(new GoverningInstruments(act, act, rules)))
                .load(TaxYearId.of(2026));
    }

    private static Provenance provenanceFrom(RulePack pack) {
        return pack.rule(SLAB).headProvenance();
    }

    /**
     * A source. Requires nothing — it reads {@code EngineInputs}, which is passed in rather than
     * reached for (B-004, D-031).
     */
    private record SourceNode(NodeId produces, Provenance provenance) implements ComputationNode {

        @Override
        public Set<NodeId> requires() {
            return Set.of();
        }

        @Override
        public Set<RuleId> rules() {
            return Set.of();
        }

        @Override
        public NodeResult evaluate(NodeContext ctx) {
            // The taxpayer's own date of birth is an input, not a slot. This is what a source is.
            int year = ((Individual) ctx.inputs().assessee()).dateOfBirth().getYear();
            return NodeResult.untraced(MoneyValue.of(Money.of(String.valueOf(year))));
        }
    }

    /** Emits one line. */
    private record EarningNode(NodeId produces, NodeId from, Money amount, Provenance provenance)
            implements ComputationNode {

        @Override
        public Set<NodeId> requires() {
            return Set.of(from);
        }

        @Override
        public Set<RuleId> rules() {
            return Set.of(SLAB);
        }

        @Override
        public NodeResult evaluate(NodeContext ctx) {
            ctx.get(from);
            return NodeResult.of(
                    MoneyValue.of(amount),
                    TraceLine.of(
                            1,
                            produces,
                            SLAB,
                            StepKind.INCOME,
                            MoneyValue.of(Money.ZERO),
                            MoneyValue.of(amount),
                            provenance));
        }
    }

    /** Adds two slots and emits two lines, so renumbering across nodes is visible. */
    private record AddingNode(NodeId produces, NodeId left, NodeId right, Provenance provenance)
            implements ComputationNode {

        @Override
        public Set<NodeId> requires() {
            return Set.of(left, right);
        }

        @Override
        public Set<RuleId> rules() {
            return Set.of(SLAB);
        }

        @Override
        public NodeResult evaluate(NodeContext ctx) {
            Money sum = ctx.get(left).asMoney().plus(ctx.get(right).asMoney());
            TraceLine line = TraceLine.of(
                    1, produces, SLAB, StepKind.INCOME,
                    MoneyValue.of(Money.ZERO), MoneyValue.of(sum), provenance);
            return new NodeResult(MoneyValue.of(sum), List.of(line, line));
        }
    }

    /** components (source) -> salary.gross -> salary.income, which also reads components. */
    private static NodeRegistry registry(RulePack pack) {
        Provenance p = provenanceFrom(pack);
        return NodeRegistry.of(List.of(
                new SourceNode(COMPONENTS, p),
                new EarningNode(GROSS, COMPONENTS, Money.of("2800000"), p),
                new AddingNode(SALARY, GROSS, COMPONENTS, p)));
    }

    private static RegimeSpec regimeRunning(RulePack pack, NodeId... nodes) {
        RegimeSpec base = pack.regimes().values().iterator().next();
        return new RegimeSpec(
                base.code(),
                base.displayName(),
                base.provision1961(),
                base.provision2025(),
                base.appliesTo(),
                base.rateTable(),
                base.disallowances(),
                base.eligibility(),
                base.election(),
                base.lifecycle(),
                base.isDefault(),
                new PipelineSpec(List.of(nodes)),
                base.behaviour(),
                base.provenance());
    }

    private static PipelineRunner.Result runAll(RulePack pack) {
        return PipelineRunner.run(
                registry(pack),
                pack,
                regimeRunning(pack, SALARY, GROSS, COMPONENTS),
                NodeHarness.plainTaxpayer());
    }

    @Nested
    @DisplayName("the graph runs in order and collects the answer")
    class Running {

        @Test
        void orderIsDerivedFromTheEdgesAndNotFromThePipelineListing() {
            // The pipeline names them salary, gross, components. They run the other way round.
            RulePack pack = pack();

            assertThat(runAll(pack).order()).containsExactly(COMPONENTS, GROSS, SALARY);
        }

        @Test
        void aSourceNodeReadsTheInputsRatherThanASlot() {
            // B-004's whole point: this node requires nothing and still has something to read.
            RulePack pack = pack();

            assertThat(runAll(pack).valueOf(COMPONENTS).asMoney()).isEqualTo(Money.of("1985"));
        }

        @Test
        void aNodeSeesWhatItsDependenciesProduced() {
            RulePack pack = pack();
            PipelineRunner.Result result = runAll(pack);

            assertThat(result.valueOf(SALARY).asMoney())
                    .isEqualTo(result.valueOf(GROSS).asMoney()
                            .plus(result.valueOf(COMPONENTS).asMoney()));
        }

        @Test
        void aValueNoNodeProducedIsAnError() {
            RulePack pack = pack();
            PipelineRunner.Result result = PipelineRunner.run(
                    registry(pack),
                    pack,
                    regimeRunning(pack, COMPONENTS),
                    NodeHarness.plainTaxpayer());

            assertThatThrownBy(() -> result.valueOf(SALARY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("produced no value");
        }

        @Test
        void theRunnerFetchesNothingForItself() {
            RulePack pack = pack();

            assertThatThrownBy(() -> PipelineRunner.run(
                            null, pack, regimeRunning(pack, COMPONENTS), NodeHarness.plainTaxpayer()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("fetches none of them");
        }
    }

    @Nested
    @DisplayName("the trace is renumbered into one ascending sequence")
    class Trace {

        @Test
        void everyNodesLinesJoinOneRunningSequence() {
            // Each node emits lines numbered from 1. Left alone, this run would produce three
            // line 1s, and TraceAnnotation -- which addresses a line BY sequence -- could not
            // tell them apart.
            assertThat(runAll(pack()).trace())
                    .extracting(TraceLine::sequence)
                    .containsExactly(1, 2, 3);
        }

        @Test
        void linesAppearInEvaluationOrderAndANodesOwnLinesKeepTheirOrder() {
            assertThat(runAll(pack()).trace())
                    .extracting(TraceLine::node)
                    .containsExactly(GROSS, SALARY, SALARY);
        }

        @Test
        void everythingElseOnTheLineSurvivesRenumbering() {
            RulePack pack = pack();
            TraceLine line = runAll(pack).trace().getFirst();

            assertThat(line.rule()).isEqualTo(SLAB);
            assertThat(line.node()).isEqualTo(GROSS);
            assertThat(line.kind()).isEqualTo(StepKind.INCOME);
            assertThat(line.out().asMoney()).isEqualTo(Money.of("2800000"));
            assertThat(line.provenance()).isEqualTo(provenanceFrom(pack));
        }

        @Test
        void aNodeThatEmitsNoLineContributesNothingToTheTrace() {
            // The source is untraced; the trace holds three lines from the other two nodes.
            assertThat(runAll(pack()).trace()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("T9-20 — the same inputs computed twice")
    class Determinism {

        @Test
        void twoRunsGiveAByteIdenticalTrace() {
            RulePack pack = pack();

            assertThat(runAll(pack).trace()).isEqualTo(runAll(pack).trace());
        }

        @Test
        void theSamePackVersionAndChecksumAreRecordedBothTimes() {
            RulePack pack = pack();

            assertThat(runAll(pack).lawAndExec()).isEqualTo(runAll(pack).lawAndExec());
            assertThat(runAll(pack).inputs()).isEqualTo(runAll(pack).inputs());
        }

        @Test
        void thePackRefsRecordedAreLawAndExecOnly() {
            // G-11: content and forms are excluded, so an editorial revision invalidates nothing.
            assertThat(runAll(pack()).lawAndExec().keySet())
                    .isNotEmpty()
                    .allMatch(RulePackLayer::affectsComputation)
                    .doesNotContain(RulePackLayer.CONTENT);
        }
    }

    @Nested
    @DisplayName("it catches nothing — D-NOSILENT")
    class NoSilentFailure {

        private record ThrowingNode(Provenance provenance) implements ComputationNode {
            @Override
            public NodeId produces() {
                return GROSS;
            }

            @Override
            public Set<NodeId> requires() {
                return Set.of(COMPONENTS);
            }

            @Override
            public Set<RuleId> rules() {
                return Set.of();
            }

            @Override
            public NodeResult evaluate(NodeContext ctx) {
                throw new IllegalStateException("this rule cannot be evaluated");
            }
        }

        @Test
        void aNodeThatCannotComputeStopsTheRunRatherThanYieldingZero() {
            // The failure the placeholder actually had: a rule caught and silently replaced with
            // zero, so a deduction vanished and someone filed on the result.
            RulePack pack = pack();
            Provenance p = provenanceFrom(pack);
            NodeRegistry registry = NodeRegistry.of(List.of(
                    new SourceNode(COMPONENTS, p), new ThrowingNode(p)));

            assertThatThrownBy(() -> PipelineRunner.run(
                            registry,
                            pack,
                            regimeRunning(pack, GROSS, COMPONENTS),
                            NodeHarness.plainTaxpayer()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("this rule cannot be evaluated");
        }

        @Test
        void nothingIsRecordedFromARunThatFailedPartWayThrough() {
            // No partial Result escapes: the caller gets the failure, not a short trace that looks
            // like a complete computation.
            RulePack pack = pack();
            Provenance p = provenanceFrom(pack);
            NodeRegistry registry = NodeRegistry.of(List.of(
                    new SourceNode(COMPONENTS, p), new ThrowingNode(p)));

            assertThatThrownBy(() -> PipelineRunner.run(
                            registry,
                            pack,
                            regimeRunning(pack, GROSS, COMPONENTS),
                            NodeHarness.plainTaxpayer()))
                    .isNotInstanceOf(AssertionError.class);
        }

        @Test
        void thereIsNoTryInTheRunner() throws IOException {
            // The ArchUnit rule is structural, but the source is the thing being promised: a catch
            // added here would pass every other test in this class.
            String source = Files.readString(Path.of(
                    "src/main/java/com/thiru/wealthlens/taxengine/pipeline/PipelineRunner.java"));

            assertThat(source).doesNotContain("try {").doesNotContain("catch (");
        }

        @Test
        void aNodeReachingForAnUndeclaredSlotFailsThroughTheRunnerToo() {
            // T23-6, inherited from W-039's scoped context and shown to survive assembly.
            RulePack pack = pack();
            Provenance p = provenanceFrom(pack);
            ComputationNode sneaky = new ComputationNode() {
                @Override
                public NodeId produces() {
                    return GROSS;
                }

                @Override
                public Set<NodeId> requires() {
                    return Set.of();
                }

                @Override
                public Set<RuleId> rules() {
                    return Set.of();
                }

                @Override
                public NodeResult evaluate(NodeContext ctx) {
                    return NodeResult.untraced(MoneyValue.of(ctx.get(COMPONENTS).asMoney()));
                }
            };
            NodeRegistry registry =
                    NodeRegistry.of(List.of(new SourceNode(COMPONENTS, p), sneaky));

            assertThatThrownBy(() -> PipelineRunner.run(
                            registry,
                            pack,
                            regimeRunning(pack, GROSS, COMPONENTS),
                            NodeHarness.plainTaxpayer()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("did not declare");
        }
    }
}
