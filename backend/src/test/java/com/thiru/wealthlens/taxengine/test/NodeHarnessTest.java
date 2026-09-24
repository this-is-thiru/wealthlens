package com.thiru.wealthlens.taxengine.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.registry.GoverningInstruments;
import com.thiru.wealthlens.taxengine.rules.registry.Instrument;
import com.thiru.wealthlens.taxengine.rules.registry.VerificationLevel;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.net.URI;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * A node runs alone, and only on what it declared.
 */
class NodeHarnessTest {

    private static final NodeId TOTAL_INCOME = NodeIds.TOTALINCOME;
    // Not a slot of the shipped pipeline, so not a constant
    private static final NodeId GTI = new NodeId("gti");
    private static final NodeId TAX = NodeIds.TAX_BYBASIS;
    private static final RuleId SLAB = new RuleId("RATE-SLAB-NEW-TAX-REGIME");

    private static RulePack pack() {
        return new ClasspathRulePackProvider(
                        "rulepacks", Set.of(TaxYearId.of(2026)), new JsonPackAssembler(instruments()))
                .load(TaxYearId.of(2026));
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

    private static RegimeCode anyRegimeIn(RulePack pack) {
        return pack.regimes().keySet().iterator().next();
    }

    /** Doubles its input and says so, which is enough to show the wiring works. */
    private record DoublingNode(NodeId produces, Set<NodeId> requires, Set<RuleId> rules)
            implements ComputationNode {

        @Override
        public NodeResult evaluate(NodeContext ctx) {
            Money in = ctx.get(requires.iterator().next()).asMoney();
            Money out = in.plus(in);
            return NodeResult.of(
                    MoneyValue.of(out),
                    TraceLine.of(
                            1,
                            produces,
                            rules.iterator().next(),
                            StepKind.TAX,
                            MoneyValue.of(in),
                            MoneyValue.of(out),
                            ctx.rule(rules.iterator().next()).headProvenance()));
        }
    }

    private static ComputationNode doubling() {
        return new DoublingNode(TAX, Set.of(TOTAL_INCOME), Set.of(SLAB));
    }

    @Nested
    @DisplayName("the harness runs one node in isolation")
    class InIsolation {

        @Test
        void aNodeEvaluatesWithNoPipelineAtAll() {
            RulePack pack = pack();

            NodeResult result = NodeHarness.with(pack)
                    .regime(anyRegimeIn(pack))
                    .given(TOTAL_INCOME, Money.of("1200000"))
                    .run(doubling());

            assertThat(result.value().asMoney()).isEqualTo(Money.of("2400000"));
        }

        @Test
        void theTraceComesBackWithTheValue() {
            RulePack pack = pack();

            NodeResult result = NodeHarness.with(pack)
                    .regime(anyRegimeIn(pack))
                    .given(TOTAL_INCOME, Money.of("1200000"))
                    .run(doubling());

            assertThat(result.trace()).hasSize(1);
            assertThat(result.trace().getFirst().rule()).isEqualTo(SLAB);
        }

        @Test
        void theNodeCanReadTheRuleItDeclared() {
            // ctx.rule(...) resolves out of the real shipped pack, so the node is exercised
            // against real rule data rather than a stub that agrees with it by construction.
            RulePack pack = pack();

            assertThat(pack.rule(SLAB).id()).isEqualTo(SLAB);
        }

        @Test
        void aRegimeThePackDoesNotHoldIsRejected() {
            RulePack pack = pack();

            assertThatThrownBy(() -> NodeHarness.with(pack).regime(new RegimeCode("NO_SUCH")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("has no regime");
        }

        @Test
        void runningWithoutARegimeIsRejected() {
            assertThatThrownBy(() -> NodeHarness.with(pack())
                            .given(TOTAL_INCOME, Money.of("1"))
                            .run(doubling()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No regime chosen");
        }
    }

    @Nested
    @DisplayName("its stub inputs are honoured, and only those")
    class Stubs {

        @Test
        void theStubbedValueIsWhatTheNodeReads() {
            RulePack pack = pack();

            NodeResult result = NodeHarness.with(pack)
                    .regime(anyRegimeIn(pack))
                    .given(TOTAL_INCOME, Money.of("50"))
                    .run(doubling());

            assertThat(result.value().asMoney()).isEqualTo(Money.of("100"));
        }

        @Test
        void aNodeValueMayBeStubbedDirectlyRatherThanAsMoney() {
            RulePack pack = pack();
            NodeValue stub = MoneyValue.of(Money.of("7"));

            NodeResult result = NodeHarness.with(pack)
                    .regime(anyRegimeIn(pack))
                    .given(TOTAL_INCOME, stub)
                    .run(doubling());

            assertThat(result.value().asMoney()).isEqualTo(Money.of("14"));
        }

        @Test
        void aRequiredSlotThatWasNotGivenFailsAndSaysHowToSupplyIt() {
            RulePack pack = pack();

            assertThatThrownBy(() -> NodeHarness.with(pack).regime(anyRegimeIn(pack)).run(doubling()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("totalincome")
                    .hasMessageContaining(".given(id, value)");
        }

        @Test
        void aStubTheNodeDoesNotRequireFails() {
            // A11: the harness supplies exactly what a node declares and refuses anything else.
            // A stub the node cannot read means the test and the node disagree about what it
            // reads, and the test would pass for the wrong reason.
            RulePack pack = pack();

            assertThatThrownBy(() -> NodeHarness.with(pack)
                            .regime(anyRegimeIn(pack))
                            .given(TOTAL_INCOME, Money.of("1"))
                            .given(GTI, Money.of("1"))
                            .run(doubling()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("gti")
                    .hasMessageContaining("does not require");
        }

        @Test
        void aNodeReachingForSomethingUndeclaredStillFails() {
            // T23-6, inherited from W-039's scoped NodeContext: the harness gets it for free.
            RulePack pack = pack();
            ComputationNode sneaky = new ComputationNode() {
                @Override
                public NodeId produces() {
                    return TAX;
                }

                @Override
                public Set<NodeId> requires() {
                    return Set.of(TOTAL_INCOME);
                }

                @Override
                public Set<RuleId> rules() {
                    return Set.of();
                }

                @Override
                public NodeResult evaluate(NodeContext ctx) {
                    return NodeResult.untraced(MoneyValue.of(ctx.get(GTI).asMoney()));
                }
            };

            assertThatThrownBy(() -> NodeHarness.with(pack)
                            .regime(anyRegimeIn(pack))
                            .given(TOTAL_INCOME, Money.of("1"))
                            .run(sneaky))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("did not declare");
        }
    }

    @Nested
    @DisplayName("the default taxpayer")
    class Defaults {

        @Test
        void aTestThatDoesNotCareAboutTheTaxpayerSaysNothingAboutOne() {
            RulePack pack = pack();

            assertThat(NodeHarness.with(pack)
                            .regime(anyRegimeIn(pack))
                            .given(TOTAL_INCOME, Money.of("1"))
                            .run(doubling())
                            .value()
                            .asMoney())
                    .isEqualTo(Money.of("2"));
        }

        @Test
        void thePlainTaxpayerIsNeutralInEveryComponent() {
            EngineInputs plain = NodeHarness.plainTaxpayer();

            assertThat(plain.family().members()).isEmpty();
            assertThat(plain.capitalGains().buckets()).isEmpty();
            assertThat(plain.ledgers().vrsAlreadyClaimed()).isFalse();
            assertThat(plain.filing().furnishedLate()).isFalse();
            assertThat(plain.elections().stateByRegime()).isEmpty();
        }

        @Test
        void aTestThatCaresPassesItsOwnInputs() {
            RulePack pack = pack();
            EngineInputs mine = NodeHarness.plainTaxpayer();

            NodeResult result = NodeHarness.with(pack)
                    .regime(anyRegimeIn(pack))
                    .inputs(mine)
                    .given(TOTAL_INCOME, Money.of("3"))
                    .run(doubling());

            assertThat(result.value().asMoney()).isEqualTo(Money.of("6"));
        }
    }
}
