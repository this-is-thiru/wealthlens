package com.thiru.wealthlens.taxengine.election;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.regime.PipelineSpec;
import com.thiru.wealthlens.taxengine.rules.regime.RegimeSpec;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Resolving an election by recomputation (D-220, D-221), on a stub graph whose one node prices each absorption order
 * from a table — so every expected figure is the table's. The choices are the TY 2026-27 pack's
 * {@code SETOFF-ACROSS-HEADS} order and its permutations.
 */
class ElectionResolverTest {

    private static final List<HeadCode> DEFAULT =
            List.of(HeadCode.SALARIES, HeadCode.HOUSE_PROPERTY, HeadCode.OTHER_SOURCES);
    private static final List<HeadCode> OTHER_SOURCES_FIRST =
            List.of(HeadCode.OTHER_SOURCES, HeadCode.HOUSE_PROPERTY, HeadCode.SALARIES);
    private static final List<HeadCode> HOUSE_PROPERTY_FIRST =
            List.of(HeadCode.HOUSE_PROPERTY, HeadCode.SALARIES, HeadCode.OTHER_SOURCES);

    private static final RulePack PACK = new ClasspathRulePackProvider("rulepacks", Set.of(TaxYearId.of(2026)),
            new JsonPackAssembler(LockedInstruments.of(TaxYearId.of(2026)))).load(TaxYearId.of(2026));

    /** The net payable for the elected order: the table's figure, or {@code otherwise}. */
    private record PricedByOrder(Map<List<HeadCode>, Money> prices, Money otherwise) implements ComputationNode {

        @Override
        public NodeId produces() {
            return NodeIds.TAX_NETPAYABLE;
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
            List<HeadCode> order = ctx.inputs().entered().absorptionOrder().orElseThrow();
            return NodeResult.untraced(MoneyValue.of(prices.getOrDefault(order, otherwise)));
        }
    }

    private static ElectionResolver.Resolution<List<HeadCode>> resolve(Map<List<HeadCode>, Money> prices,
            EngineInputs inputs) {
        RegimeSpec base = PACK.regimes().values().iterator().next();
        RegimeSpec regime = new RegimeSpec(base.code(), base.displayName(), base.provision1961(),
                base.provision2025(), base.appliesTo(), base.rateTable(), base.disallowances(), base.eligibility(),
                base.election(), base.lifecycle(), base.isDefault(), new PipelineSpec(List.of(NodeIds.TAX_NETPAYABLE)),
                base.behaviour(), base.provenance());
        NodeRegistry registry = NodeRegistry.of(List.of(new PricedByOrder(prices, Money.of("200"))));
        return ElectionResolver.resolve(new SetOffOrderElection(), registry, PACK, regime, inputs);
    }

    private static EngineInputs electing(List<HeadCode> order) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new SetOffOrderElection().choosing(order, plain);
    }

    @Test
    void candidates_whenTheRowDeclaresThreeHeads_areEveryOrderTheDeclaredOneFirst() {
        // When
        List<List<HeadCode>> candidates = new SetOffOrderElection().candidates(NodeHarness.plainTaxpayer(), PACK);

        // Then — 3! = 6 orders, each once, SALARIES / HOUSE_PROPERTY / OTHER_SOURCES first
        assertThat(candidates).hasSize(6).doesNotHaveDuplicates().first().isEqualTo(DEFAULT);
        assertThat(candidates).allSatisfy(order -> assertThat(order).containsExactlyInAnyOrderElementsOf(DEFAULT));
    }

    @Test
    void resolve_whenNoOrderIsElected_takesTheCheapestAndRecordsEveryAlternative() {
        // Given — other sources first nets 100; every other order 200
        Map<List<HeadCode>, Money> prices = Map.of(OTHER_SOURCES_FIRST, Money.of("100"));

        // When
        ElectionResolver.Resolution<List<HeadCode>> resolution = resolve(prices, NodeHarness.plainTaxpayer());

        // Then — chosen by price; six alternatives, six lines, one of them chosen
        assertThat(resolution.outcome().mode()).isEqualTo(ElectionMode.AUTO_OPTIMISE);
        assertThat(resolution.outcome().chosen()).isEqualTo(OTHER_SOURCES_FIRST);
        assertThat(resolution.outcome().alternatives()).hasSize(6);
        assertThat(resolution.run().valueOf(NodeIds.TAX_NETPAYABLE)).isEqualTo(MoneyValue.of(Money.of("100")));
        assertThat(resolution.run().trace()).hasSize(6).allSatisfy(line -> {
            assertThat(line.node()).isEqualTo(NodeIds.SETOFF);
            assertThat(line.rule()).isEqualTo(RuleIds.SETOFF_ACROSS_HEADS);
            assertThat(line.kind()).isEqualTo(StepKind.MEASURE);
            assertThat(line.in()).isEqualTo(line.out());
        });
        assertThat(resolution.run().trace()).extracting(TraceLine::sequence).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(resolution.run().trace()).filteredOn(line -> line.limitedBy().orElseThrow().contains("; chosen"))
                .singleElement().satisfies(line -> {
                    assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("100")));
                    assertThat(line.limitedBy().orElseThrow()).contains(OTHER_SOURCES_FIRST.toString());
                });
    }

    @Test
    void resolve_whenEveryOrderCostsTheSame_takesTheDeclaredDefault() {
        // Given — every order nets 200

        // When
        ElectionResolver.Resolution<List<HeadCode>> resolution = resolve(Map.of(), NodeHarness.plainTaxpayer());

        // Then
        assertThat(resolution.outcome().chosen()).isEqualTo(DEFAULT);
    }

    @Test
    void resolve_whenTwoOrdersTieBelowTheDefault_takesTheEarlierCandidate() {
        // Given — other sources first and house property first both net 100. The candidates run S-HP-OS, S-OS-HP,
        // HP-S-OS, HP-OS-S, OS-S-HP, OS-HP-S, so house property first (third) comes before other sources first (sixth)
        Map<List<HeadCode>, Money> prices =
                Map.of(OTHER_SOURCES_FIRST, Money.of("100"), HOUSE_PROPERTY_FIRST, Money.of("100"));

        // When
        ElectionResolver.Resolution<List<HeadCode>> resolution = resolve(prices, NodeHarness.plainTaxpayer());

        // Then
        assertThat(resolution.outcome().chosen()).isEqualTo(HOUSE_PROPERTY_FIRST);
    }

    @Test
    void resolve_whenTheAssesseeStatedAnOrder_computesItAloneEvenIfAnotherIsCheaper() {
        // Given — the default stated; other sources first would net 100 against its 200
        Map<List<HeadCode>, Money> prices = Map.of(OTHER_SOURCES_FIRST, Money.of("100"));

        // When
        ElectionResolver.Resolution<List<HeadCode>> resolution = resolve(prices, electing(DEFAULT));

        // Then — one run, one line, saying it was stated
        assertThat(resolution.outcome().mode()).isEqualTo(ElectionMode.USER_SPECIFIED);
        assertThat(resolution.outcome().chosen()).isEqualTo(DEFAULT);
        assertThat(resolution.outcome().alternatives())
                .containsExactly(new PricedAlternative<>(DEFAULT, Money.of("200")));
        assertThat(resolution.run().trace()).singleElement()
                .satisfies(line -> assertThat(line.limitedBy().orElseThrow()).contains("stated by the assessee"));
    }

    @Test
    void electionOutcome_whenTheChoiceWasNotPriced_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new ElectionOutcome<>(DEFAULT, ElectionMode.AUTO_OPTIMISE,
                List.of(new PricedAlternative<>(OTHER_SOURCES_FIRST, Money.of("100")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not among the alternatives");
    }

    @Test
    void electionOutcome_whenAStatedChoiceCarriesSeveralAlternatives_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new ElectionOutcome<>(DEFAULT, ElectionMode.USER_SPECIFIED,
                List.of(new PricedAlternative<>(DEFAULT, Money.of("200")),
                        new PricedAlternative<>(OTHER_SOURCES_FIRST, Money.of("100")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("computed alone");
    }

    @Test
    void enteredFacts_whenAnElectedOrderNamesAHeadTwice_isRefused() {
        // When / Then
        assertThatThrownBy(() -> EnteredFacts.none()
                .withAbsorptionOrder(List.of(HeadCode.SALARIES, HeadCode.SALARIES, HeadCode.OTHER_SOURCES)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("each head once");
    }
}
