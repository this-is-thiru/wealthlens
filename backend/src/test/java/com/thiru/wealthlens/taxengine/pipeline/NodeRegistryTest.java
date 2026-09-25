package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.cap.Fixed;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RegimeApplicability;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackLayer;
import com.thiru.wealthlens.taxengine.rules.pack.RulePackRef;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.regime.BindingSpec;
import com.thiru.wealthlens.taxengine.rules.regime.ConditionSetId;
import com.thiru.wealthlens.taxengine.rules.regime.ContinuationPolicy;
import com.thiru.wealthlens.taxengine.rules.regime.DeadlineSpec;
import com.thiru.wealthlens.taxengine.rules.regime.DisallowanceSetId;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionKind;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionProtocol;
import com.thiru.wealthlens.taxengine.rules.regime.ElectionVehicle;
import com.thiru.wealthlens.taxengine.rules.regime.InvalidationPolicy;
import com.thiru.wealthlens.taxengine.rules.regime.PipelineSpec;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeLifecycle;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.Instrument;
import com.thiru.wealthlens.taxengine.rules.registry.LegalStatus;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.registry.PublicationStatus;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.temporal.AmendmentRef;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.MissingTemporalCoordinateException;
import com.thiru.wealthlens.taxengine.rules.temporal.Provision;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalAxis;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.temporal.Validity;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.net.URI;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The graph's structural rules, and the two invariants that need the node-to-rule mapping.
 */
class NodeRegistryTest {

    private static final NodeId GROSS = new NodeId("salary.gross");
    private static final NodeId EXEMPT = new NodeId("salary.exemptions");
    private static final NodeId INCOME = new NodeId("salary.income");
    private static final RuleId STD = new RuleId("SAL-STDDED-001");
    private static final RuleId HRA = new RuleId("SAL-HRA-001");

    /** A node that does nothing but declare, which is all the registry inspects. */
    private record StubNode(NodeId produces, Set<NodeId> requires, Set<RuleId> rules)
            implements ComputationNode {

        @Override
        public NodeResult evaluate(NodeContext ctx) {
            return NodeResult.untraced(MoneyValue.of(Money.ZERO));
        }
    }

    private static ComputationNode node(NodeId produces, Set<NodeId> requires, RuleId... rules) {
        return new StubNode(produces, requires, Set.of(rules));
    }

    @Nested
    @DisplayName("two nodes producing the same NodeId is an error")
    class OneWriterPerSlot {

        @Test
        void aDuplicateProducerIsRejectedAndNamesBoth() {
            assertThatThrownBy(() -> NodeRegistry.of(List.of(
                            node(INCOME, Set.of(GROSS)), node(INCOME, Set.of(EXEMPT)))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Two nodes produce")
                    .hasMessageContaining("salary.income");
        }

        @Test
        void distinctProducersRegisterFine() {
            NodeRegistry registry = NodeRegistry.of(List.of(
                    node(GROSS, Set.of(EXEMPT)), node(INCOME, Set.of(GROSS))));

            assertThat(registry.size()).isEqualTo(2);
            assertThat(registry.registered()).containsExactlyInAnyOrder(GROSS, INCOME);
        }

        @Test
        void aSlotNobodyProducesIsAnErrorWhenResolved() {
            NodeRegistry registry = NodeRegistry.of(List.of(node(GROSS, Set.of(EXEMPT))));

            assertThatThrownBy(() -> registry.producing(INCOME))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No node produces");
        }

        @Test
        void resolveAllKeepsTheOrderThePipelineNamed() {
            NodeRegistry registry = NodeRegistry.of(List.of(
                    node(GROSS, Set.of(EXEMPT)), node(INCOME, Set.of(GROSS))));

            assertThat(registry.resolveAll(List.of(INCOME, GROSS)))
                    .extracting(ComputationNode::produces)
                    .containsExactly(INCOME, GROSS);
        }
    }

    @Nested
    @DisplayName("requires and produces are disjoint, and a source may require nothing")
    class Edges {

        @Test
        void aSourceNodeMayRequireNothing() {
            // B-004, D-031. Every acyclic graph needs a source, and TopologicalSort rejects a
            // required slot with no producer -- so forbidding an empty requires() made every graph
            // unbuildable. A source reads EngineInputs through ctx.inputs(), which is declared and
            // fingerprinted; only an upstream slot is an edge. Reading an UNdeclared slot is still
            // refused, by NodeContext.get, which is the stronger check and the one that matters.
            NodeRegistry registry = NodeRegistry.of(List.of(node(GROSS, Set.of())));

            assertThat(registry.registered()).containsExactly(GROSS);
        }

        @Test
        void aNullRequiresIsStillRejected() {
            assertThatThrownBy(() -> NodeRegistry.of(List.of(
                            new StubNode(GROSS, null, Set.of()))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must state what it requires");
        }

        @Test
        void aSelfEdgeIsRejected() {
            assertThatThrownBy(() -> NodeRegistry.of(List.of(node(GROSS, Set.of(GROSS, EXEMPT)))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("requires what it produces");
        }

        @Test
        void aNodeWithNoRulesIsAllowedButNullIsNot() {
            assertThat(NodeRegistry.of(List.of(node(INCOME, Set.of(GROSS)))).size()).isEqualTo(1);

            assertThatThrownBy(() -> NodeRegistry.of(List.of(
                            new StubNode(INCOME, Set.of(GROSS), null))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("invariant I3");
        }
    }

    @Nested
    @DisplayName("invariant I3 — a rule no node evaluates is a dead rule")
    class Reachability {

        @Test
        void aRuleNoNodeEvaluatesIsReported() {
            NodeRegistry registry = NodeRegistry.of(List.of(node(INCOME, Set.of(GROSS), STD)));

            assertThat(registry.rulesNotEvaluatedByAnyNode(List.of(STD, HRA)))
                    .containsExactly(HRA);
        }

        @Test
        void aFullyCoveredPackReportsNothing() {
            NodeRegistry registry = NodeRegistry.of(List.of(
                    node(INCOME, Set.of(GROSS), STD), node(GROSS, Set.of(EXEMPT), HRA)));

            assertThat(registry.rulesNotEvaluatedByAnyNode(List.of(STD, HRA))).isEmpty();
        }

        @Test
        void theUnreachableSetIsSortedSoTheMessageIsStable() {
            RuleId a = new RuleId("AAA-001");
            RuleId z = new RuleId("ZZZ-001");
            NodeRegistry registry = NodeRegistry.of(List.of(node(INCOME, Set.of(GROSS))));

            assertThat(registry.rulesNotEvaluatedByAnyNode(List.of(z, a)))
                    .containsExactly(a, z);
        }

        @Test
        void thereAreStillNoRealNodesSoThisIsNotWiredToTheShippedPack() {
            // D-029. Every rule in the 2026-27 pack would report unreachable today, because the
            // nodes that evaluate them are Phases 5 to 9. The check is proven here and enabled
            // when a real pipeline first exists.
            assertThat(NodeRegistry.of(List.of()).size()).isZero();
            assertThat(NodeRegistry.of(List.of()).rulesNotEvaluatedByAnyNode(List.of(STD)))
                    .containsExactly(STD);
        }
    }

    @Nested
    @DisplayName("invariant I18 — a non-TAX_YEAR rule without its coordinate cannot yield a number")
    class TemporalCoordinates {

        @Test
        void aMissingCoordinateThrowsRatherThanFallingBackToTheYearEnd() {
            // T5's I18 is "a non-TAX_YEAR axis rule is supplied its coordinate by every node that
            // evaluates it". It is already enforced: RuleRow.versionAt calls TemporalContext.on,
            // which throws for any non-TAX_YEAR axis with no coordinate -- so a node evaluating
            // such a rule without supplying it gets an exception, not a plausible wrong figure.
            // Same shape as I1, which W-029 showed was enforced by Provenance's constructor.
            TemporalContext noCoordinates =
                    TemporalContext.forTaxYear(TaxYearId.of(2026), LocalDate.of(2026, 9, 21));

            assertThatThrownBy(() -> noCoordinates.on(TemporalAxis.ACQUISITION_DATE))
                    .isInstanceOf(MissingTemporalCoordinateException.class);
        }

        @Test
        void theTaxYearAxisNeedsNoCoordinate() {
            TemporalContext context =
                    TemporalContext.forTaxYear(TaxYearId.of(2026), LocalDate.of(2026, 9, 21));

            assertThat(context.on(TemporalAxis.TAX_YEAR)).isEqualTo(context.taxYearEnd());
        }
    }

    @Nested
    @DisplayName("a NodeContext is scoped to the node, so an undeclared read fails")
    class Scoping {

        private static NodeContext run() {
            return NodeContext.forRun(pack(), regime(), NodeHarness.plainTaxpayer());
        }

        @Test
        void aNodeMayReadWhatItDeclared() {
            NodeContext ctx = run();
            ctx.put(GROSS, MoneyValue.of(Money.of("2800000")));

            NodeContext scoped = ctx.forNode(node(INCOME, Set.of(GROSS)));

            assertThat(scoped.get(GROSS).asMoney()).isEqualTo(Money.of("2800000"));
        }

        @Test
        void aNodeMayNotReadWhatItDidNotDeclare() {
            // B2's headline property. B3 sketches ONE context for the whole run, which cannot know
            // which node is asking and so cannot make this check at all -- hence the per-node view.
            NodeContext ctx = run();
            ctx.put(EXEMPT, MoneyValue.of(Money.of("228000")));

            NodeContext scoped = ctx.forNode(node(INCOME, Set.of(GROSS)));

            assertThatThrownBy(() -> scoped.get(EXEMPT))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("did not declare")
                    .hasMessageContaining("luck of ordering");
        }

        @Test
        void aDeclaredButUncomputedSlotSaysTheOrderIsWrong() {
            NodeContext scoped = run().forNode(node(INCOME, Set.of(GROSS)));

            assertThatThrownBy(() -> scoped.get(GROSS))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("has not been computed yet");
        }

        @Test
        void twoNodesShareOneValueStore() {
            NodeContext ctx = run();
            ctx.put(GROSS, MoneyValue.of(Money.of("100")));

            assertThat(ctx.forNode(node(INCOME, Set.of(GROSS))).get(GROSS).asMoney())
                    .isEqualTo(Money.of("100"));
            assertThat(ctx.forNode(node(EXEMPT, Set.of(GROSS))).get(GROSS).asMoney())
                    .isEqualTo(Money.of("100"));
        }

        @Test
        void aSlotIsWrittenOnce() {
            NodeContext ctx = run();
            ctx.put(GROSS, MoneyValue.of(Money.of("100")));

            assertThatThrownBy(() -> ctx.put(GROSS, MoneyValue.of(Money.of("200"))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already been computed");
        }

        @Test
        void theTypedGetRefusesTheWrongShape() {
            NodeContext ctx = run();
            ctx.put(GROSS, MoneyValue.of(Money.of("100")));
            NodeContext scoped = ctx.forNode(node(INCOME, Set.of(GROSS)));

            assertThatThrownBy(() -> scoped.get(GROSS, TaxByBasis.class))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("expected a TaxByBasis");
        }

        @Test
        void aNodeMayNotEvaluateARuleItDidNotDeclare() {
            // The same discipline for rules. An undeclared rule would look unreachable to I3
            // while being applied, which is the mapping going stale in the worst direction.
            NodeContext scoped = run().forNode(node(INCOME, Set.of(GROSS), STD));

            assertThatThrownBy(() -> scoped.rule(HRA))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("invariant I3");
        }

        @Test
        void theRunLevelContextDeclaresNothingAndSoCanReadNothing() {
            NodeContext ctx = run();
            ctx.put(GROSS, MoneyValue.of(Money.of("100")));

            assertThatThrownBy(() -> ctx.get(GROSS))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("did not declare");
            assertThat(ctx.hasValueFor(GROSS)).isTrue();
        }

        @Test
        void theEngineIsHandedItsPackRegimeAndInputsRatherThanFetchingThem() {
            NodeContext ctx = run();

            assertThat(ctx.pack()).isNotNull();
            assertThat(ctx.regime().code()).isEqualTo(new RegimeCode("NEW_TAX_REGIME"));
            assertThat(ctx.inputs().taxYear()).isEqualTo(TaxYearId.of(2026));
        }
    }

    // -- fixtures -------------------------------------------------------------------------------

    private static Provenance provenance() {
        return new Provenance(
                URI.create("https://egazette.gov.in/WriteReadData/2025/000000.pdf"),
                "Income-tax Act 2025",
                LocalDate.of(2026, 9, 19),
                VerificationLevel.SOURCE_CHECKED,
                null);
    }

    private static GoverningInstruments instruments() {
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
        return new GoverningInstruments(act, act, rules);
    }

    private static RulePack pack() {
        Map<RulePackLayer, RulePackRef> layers = new EnumMap<>(RulePackLayer.class);
        for (RulePackLayer layer : RulePackLayer.values()) {
            String name = layer.name().toLowerCase(java.util.Locale.ROOT);
            layers.put(
                    layer, new RulePackRef(layer, "2026-27." + name + ".1", "sha256:" + "a".repeat(64)));
        }
        return new RulePack(
                new TaxYearId(2026),
                layers,
                instruments(),
                Map.of(STD, stdDeductionRule()),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of());
    }

    private static RuleRow stdDeductionRule() {
        return new RuleRow(
                STD,
                TemporalAxis.TAX_YEAR,
                List.of(new RuleVersion(
                        Validity.Between.fromOnwards(LocalDate.of(2026, 4, 1)),
                        new RegimeApplicability.AllRegimes(),
                        new Fixed(Money.ZERO),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        Provision.notYetVerified("Income-tax Act, 1961"),
                        Provision.present("Income-tax Act, 2025", "s.202(1)"),
                        new AmendmentRef(
                                "Finance Act, 2026 (4 of 2026), s.38",
                                "CG-DL-E-31032026-271439",
                                LocalDate.of(2026, 3, 31),
                                LocalDate.of(2026, 4, 1),
                                false,
                                URI.create("https://egazette.gov.in/WriteReadData/2026/271439.pdf"),
                                VerificationLevel.SOURCE_CHECKED),
                        LegalStatus.IN_FORCE,
                        PublicationStatus.PUBLISHED,
                        provenance())),
                provenance());
    }

    private static RegimeSpec regime() {
        return new RegimeSpec(
                new RegimeCode("NEW_TAX_REGIME"),
                "Default regime",
                Provision.notYetVerified("Income-tax Act, 1961"),
                Provision.present("Income-tax Act, 2025", "s.202(1)"),
                Set.of(PersonType.INDIVIDUAL),
                STD,
                Set.<DisallowanceSetId>of(),
                Set.<ConditionSetId>of(),
                new ElectionProtocol(
                        ElectionKind.DEFAULT_NO_ACTION,
                        ElectionVehicle.NONE,
                        Optional.empty(),
                        new DeadlineSpec.NoDeadline(),
                        BindingSpec.free(),
                        provenance()),
                new RegimeLifecycle(
                        DateRange.fromOnwards(LocalDate.of(2024, 4, 1)),
                        DateRange.fromOnwards(LocalDate.of(2024, 4, 1)),
                        new ContinuationPolicy.ContinueIndefinitely(),
                        new InvalidationPolicy.NoInvalidation(),
                        provenance()),
                true,
                new PipelineSpec(List.of(new NodeId("tax.bybasis"))),
                Optional.empty(),
                provenance());
    }
}
