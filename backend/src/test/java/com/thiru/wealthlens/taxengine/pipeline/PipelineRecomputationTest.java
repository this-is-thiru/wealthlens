package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.node.RebateNode;
import com.thiru.wealthlens.taxengine.node.RoundTotalIncomeNode;
import com.thiru.wealthlens.taxengine.node.TaxByBasisNode;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.PipelineSpec;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Re-entry (D-086): a node asks for {@code tax.afterrebate} as it would be at another total income, and the
 * runner re-runs the real nodes between the two slots — how {@code SurchargeNode} prices U ({@code DR 06}
 * G3.2).
 */
class PipelineRecomputationTest {

    private static final TaxYearId YEAR = TaxYearId.of(2026);
    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");
    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final NodeId PROBE = new NodeId("probe.reentered");

    private static final RulePack PACK = new ClasspathRulePackProvider(
                    "rulepacks", Set.of(YEAR), new JsonPackAssembler(LockedInstruments.of(YEAR)))
            .load(YEAR);

    /** The outer run's total income, standing in for the head nodes not built yet. */
    private record StatedTotalIncome(Money amount) implements ComputationNode {

        @Override
        public NodeId produces() {
            return NodeIds.TOTALINCOME;
        }

        @Override
        public Set<NodeId> requires() {
            return Set.of();
        }

        @Override
        public Set<RuleSetId> ruleSets() {
            return Set.of();
        }

        @Override
        public NodeResult evaluate(NodeContext ctx) {
            return NodeResult.untraced(MoneyValue.of(amount));
        }
    }

    /** The heads after set-off with no capital gains, which {@code tax.bybasis} reads for its special-rate parts. */
    private record NoCapitalGains() implements ComputationNode {

        @Override
        public NodeId produces() {
            return NodeIds.SETOFF;
        }

        @Override
        public Set<NodeId> requires() {
            return Set.of();
        }

        @Override
        public Set<RuleSetId> ruleSets() {
            return Set.of();
        }

        @Override
        public NodeResult evaluate(NodeContext ctx) {
            return NodeResult.untraced(new HeadsAfterSetOff(Money.ZERO, Map.of()));
        }
    }

    /** Re-enters once: {@code target} as it would be had {@code pinned} been {@code at}. */
    private record ReenteringProbe(NodeId target, NodeId pinned, Money at) implements ComputationNode {

        @Override
        public NodeId produces() {
            return PROBE;
        }

        @Override
        public Set<NodeId> requires() {
            return Set.of(NodeIds.TAX_AFTERREBATE, NodeIds.ROUND_TOTALINCOME);
        }

        @Override
        public Set<RuleSetId> ruleSets() {
            return Set.of();
        }

        @Override
        public NodeResult evaluate(NodeContext ctx) {
            return NodeResult.untraced(ctx.recomputed(target, pinned, MoneyValue.of(at)));
        }
    }

    private static ReenteringProbe afterRebateAt(String totalIncome) {
        return new ReenteringProbe(NodeIds.TAX_AFTERREBATE, NodeIds.ROUND_TOTALINCOME, Money.of(totalIncome));
    }

    private static PipelineRunner.Result run(
            RegimeCode code, int ageAtYearEnd, String totalIncome, Optional<ComputationNode> probe) {
        List<ComputationNode> nodes = Stream.concat(
                        Stream.of(new StatedTotalIncome(Money.of(totalIncome)), new NoCapitalGains(),
                                new RoundTotalIncomeNode(),
                                new TaxByBasisNode(), new RebateNode()),
                        probe.stream())
                .toList();
        RegimeSpec shipped = PACK.regimes().get(code);
        RegimeSpec regime = new RegimeSpec(shipped.code(), shipped.displayName(), shipped.provision1961(),
                shipped.provision2025(), shipped.appliesTo(), shipped.rateTable(), shipped.disallowances(),
                shipped.eligibility(), shipped.election(), shipped.lifecycle(), shipped.isDefault(),
                new PipelineSpec(nodes.stream().map(ComputationNode::produces).toList()), shipped.behaviour(),
                shipped.provenance());
        return PipelineRunner.run(NodeRegistry.of(nodes), PACK, regime, inputs(code, ageAtYearEnd));
    }

    private static EngineInputs inputs(RegimeCode code, int ageAtYearEnd) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Individual person = new Individual(YEAR.endsOn().minusYears(ageAtYearEnd).minusMonths(6),
                ResidentialStatus.RESIDENT, Optional.empty(), Optional.empty());
        return new EngineInputs(YEAR, code, TemporalContext.forTaxYear(YEAR, YEAR.startsOn().plusMonths(5)),
                plain.entered(), person, plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(),
                plain.filing());
    }

    private static Money afterRebate(PipelineRunner.Result result) {
        return result.valueOf(NodeIds.TAX_AFTERREBATE).asMoney();
    }

    @Test
    void recomputed_whenAskedAtTheRebateMarginalReliefIncome_givesTheRebatedTaxAtThatIncome() {
        // Given — the rulebook's REBATE-NEW-MR-001 example, TY 2026-27 at 12,10,000: tax 61,500; rebate
        // 61,500 − (12,10,000 − 12,00,000) = 51,500; tax after rebate 10,000. The outer run is at 60,00,000.
        ReenteringProbe probe = afterRebateAt("1210000");

        // When
        PipelineRunner.Result result = run(NEW_TAX_REGIME, 40, "6000000", Optional.of(probe));

        // Then
        assertThat(result.valueOf(PROBE).asMoney()).isEqualTo(Money.of("10000"));
    }

    static Stream<Arguments> regimesAgesAndIncomes() {
        return Stream.of(NEW_TAX_REGIME, FIRST_SCHEDULE_RATES).flatMap(regime -> Stream.of(40, 80).flatMap(age ->
                Stream.of("500000", "5000000", "20000000").map(income -> Arguments.of(regime, age, income))));
    }

    @ParameterizedTest(name = "{0}, age {1}, at {2}")
    @MethodSource("regimesAgesAndIncomes")
    void recomputed_whenComparedWithAFreshRunAtThatIncome_isTheSame(RegimeCode regime, int age, String income) {
        // Given
        Money fresh = afterRebate(run(regime, age, income, Optional.empty()));

        // When
        PipelineRunner.Result reentered = run(regime, age, "60000000", Optional.of(afterRebateAt(income)));

        // Then
        assertThat(reentered.valueOf(PROBE).asMoney()).isEqualTo(fresh);
    }

    @Test
    void recomputed_whenItRuns_leavesTheOuterRunsValuesAndTraceAlone() {
        // Given
        PipelineRunner.Result plain = run(NEW_TAX_REGIME, 40, "6000000", Optional.empty());

        // When
        PipelineRunner.Result withProbe = run(NEW_TAX_REGIME, 40, "6000000", Optional.of(afterRebateAt("1210000")));

        // Then
        assertThat(afterRebate(withProbe)).isEqualTo(afterRebate(plain));
        assertThat(withProbe.trace().stream().map(TraceLine::rule).toList())
                .isEqualTo(plain.trace().stream().map(TraceLine::rule).toList());
    }

    @Test
    void recomputed_whenTheTargetIsNotDeclared_isRefused() {
        // Given — tax.bybasis is upstream of tax.afterrebate but the probe did not declare it
        ReenteringProbe probe =
                new ReenteringProbe(NodeIds.TAX_BYBASIS, NodeIds.ROUND_TOTALINCOME, Money.of("1210000"));

        // When / Then
        assertThatThrownBy(() -> run(NEW_TAX_REGIME, 40, "6000000", Optional.of(probe)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not declare " + NodeIds.TAX_BYBASIS);
    }

    @Test
    void recomputed_whenTheTargetDoesNotDependOnThePinnedSlot_isRefused() {
        // Given — round.totalincome does not depend on tax.afterrebate; recomputing it would return the
        // outer value and look like an answer
        ReenteringProbe probe =
                new ReenteringProbe(NodeIds.ROUND_TOTALINCOME, NodeIds.TAX_AFTERREBATE, Money.of("1"));

        // When / Then
        assertThatThrownBy(() -> run(NEW_TAX_REGIME, 40, "6000000", Optional.of(probe)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not depend on " + NodeIds.TAX_AFTERREBATE);
    }

    @Test
    void recomputed_whenTheContextWasBuiltWithoutRecomputation_isRefused() {
        // Given — a harness run that states no re-entry
        NodeHarness harness = NodeHarness.with(PACK)
                .regime(NEW_TAX_REGIME)
                .given(NodeIds.TAX_AFTERREBATE, TaxByBasis.atSlabRatesOnly(Money.ZERO))
                .given(NodeIds.ROUND_TOTALINCOME, Money.of("6000000"));

        // When / Then
        assertThatThrownBy(() -> harness.run(afterRebateAt("1210000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot re-enter");
    }
}
