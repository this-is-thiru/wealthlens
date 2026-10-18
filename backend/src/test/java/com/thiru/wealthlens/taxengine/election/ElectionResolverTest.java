package com.thiru.wealthlens.taxengine.election;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HouseOccupancy;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
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
import com.thiru.wealthlens.taxengine.rules.money.Rate;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("several elections together")
    class Together {

        private static final List<Integer> NONE = List.of();
        private static final List<Integer> FIRST = List.of(0);

        /** The net payable for the elected houses and order together: the table's figure, or 200. */
        private record PricedByHousesAndOrder(Map<List<Object>, Money> prices) implements ComputationNode {

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
                List<HouseProperty> houses = ctx.inputs().entered().houseProperties();
                List<Integer> specified = IntStream.range(0, houses.size())
                        .filter(i -> houses.get(i).occupancy() == HouseOccupancy.OWN_RESIDENCE).boxed().toList();
                List<HeadCode> order = ctx.inputs().entered().absorptionOrder().orElseThrow();
                return NodeResult.untraced(MoneyValue.of(prices.getOrDefault(List.of(specified, order),
                        Money.of("200"))));
            }
        }

        /** Two houses, both not let: either may be specified, the entered specification none (D-230). */
        private static EngineInputs twoHousesNotLet() {
            EngineInputs plain = NodeHarness.plainTaxpayer();
            HouseProperty house = new HouseProperty(HouseOccupancy.NOT_LET, Money.of("300000"), Money.ZERO,
                    Money.ZERO, false, Money.ZERO, Rate.percent("100"), Money.ZERO, Optional.empty(), List.of());
            return new EngineInputs(plain.taxYear(), plain.regime(), plain.temporal(),
                    EnteredFacts.none().withHouseProperties(List.of(house, house)), plain.assessee(), plain.family(),
                    plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
        }

        /*
         * Alone, the houses at the default order take {0} (150 < 160), and the order with {0} takes other sources
         * first (140); together, no house specified with other sources first nets 100.
         */
        private static final Map<List<Object>, Money> PRICES = Map.of(
                List.of(FIRST, DEFAULT), Money.of("150"),
                List.of(NONE, DEFAULT), Money.of("160"),
                List.of(FIRST, OTHER_SOURCES_FIRST), Money.of("140"),
                List.of(NONE, OTHER_SOURCES_FIRST), Money.of("100"));

        private static ElectionResolver.JointResolution resolveTogether(Map<List<Object>, Money> prices,
                EngineInputs inputs) {
            RegimeSpec base = PACK.regimes().values().iterator().next();
            RegimeSpec regime = new RegimeSpec(base.code(), base.displayName(), base.provision1961(),
                    base.provision2025(), base.appliesTo(), base.rateTable(), base.disallowances(), base.eligibility(),
                    base.election(), base.lifecycle(), base.isDefault(),
                    new PipelineSpec(List.of(NodeIds.TAX_NETPAYABLE)), base.behaviour(), base.provenance());
            NodeRegistry registry = NodeRegistry.of(List.of(new PricedByHousesAndOrder(prices)));
            return ElectionResolver.resolveTogether(List.of(new HouseElection(), new SetOffOrderElection()),
                    registry, PACK, regime, inputs);
        }

        /** One outcome's alternatives, whatever its election chooses. */
        private static List<Object> alternatives(ElectionResolver.JointResolution resolution, int election) {
            return List.copyOf(resolution.outcomes().get(election).alternatives());
        }

        @Test
        void resolveTogether_whenTheBestPairIsNotEachBestAlone_takesTheCheapestPair() {
            // When — 4 house sets ({}, {0, 1}, {0}, {1}) × 6 orders = 24 combinations
            ElectionResolver.JointResolution resolution = resolveTogether(PRICES, twoHousesNotLet());

            // Then — each outcome's choice from the pair at 100; {0} priced at its best order, 140; the default
            // order at its best houses, 150
            assertThat(resolution.run().valueOf(NodeIds.TAX_NETPAYABLE)).isEqualTo(MoneyValue.of(Money.of("100")));
            assertThat(resolution.outcomes()).hasSize(2);
            assertThat(resolution.outcomes().get(0).chosen()).isEqualTo(NONE);
            assertThat(alternatives(resolution, 0))
                    .contains(new PricedAlternative<>(NONE, Money.of("100")),
                            new PricedAlternative<>(FIRST, Money.of("140")));
            assertThat(resolution.outcomes().get(1).chosen()).isEqualTo(OTHER_SOURCES_FIRST);
            assertThat(alternatives(resolution, 1))
                    .contains(new PricedAlternative<>(OTHER_SOURCES_FIRST, Money.of("100")),
                            new PricedAlternative<>(DEFAULT, Money.of("150")));
            assertThat(resolution.outcomes()).allSatisfy(outcome ->
                    assertThat(outcome.mode()).isEqualTo(ElectionMode.AUTO_OPTIMISE));
        }

        @Test
        void resolveTogether_whenNoOrderIsElected_writesOneLinePerChoiceOfEachElection() {
            // When
            ElectionResolver.JointResolution resolution = resolveTogether(PRICES, twoHousesNotLet());

            // Then — 4 house lines, then 6 order lines, numbered on; each names the others at their best
            assertThat(resolution.run().trace()).hasSize(10).extracting(TraceLine::sequence)
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
            assertThat(resolution.run().trace().subList(0, 4)).allSatisfy(line -> {
                assertThat(line.node()).isEqualTo(NodeIds.HOUSEPROPERTY_INCOME);
                assertThat(line.rule()).isEqualTo(RuleIds.HP_OWN_RESIDENCE);
            });
            assertThat(resolution.run().trace().subList(4, 10)).allSatisfy(line -> {
                assertThat(line.node()).isEqualTo(NodeIds.SETOFF);
                assertThat(line.kind()).isEqualTo(StepKind.MEASURE);
                assertThat(line.limitedBy().orElseThrow()).contains("priced jointly by recomputation against 24");
            });
            assertThat(resolution.run().trace()).filteredOn(line -> line.limitedBy().orElseThrow().contains("; chosen"))
                    .hasSize(2).allSatisfy(line -> assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("100"))));
        }

        @Test
        void resolveTogether_whenTheOrderIsStated_pricesTheHousesAtThatOrderOnly() {
            // Given — the declared order stated
            EngineInputs stated = new SetOffOrderElection().choosing(DEFAULT, twoHousesNotLet());

            // When
            ElectionResolver.JointResolution resolution = resolveTogether(PRICES, stated);

            // Then — at the default order {0} is cheapest, 150; the order computed alone, as stated
            assertThat(resolution.outcomes().get(0).chosen()).isEqualTo(FIRST);
            assertThat(resolution.run().valueOf(NodeIds.TAX_NETPAYABLE)).isEqualTo(MoneyValue.of(Money.of("150")));
            assertThat(resolution.outcomes().get(1).mode()).isEqualTo(ElectionMode.USER_SPECIFIED);
            assertThat(alternatives(resolution, 1))
                    .containsExactly(new PricedAlternative<>(DEFAULT, Money.of("150")));
        }

        @Test
        void resolveTogether_whenEveryCombinationCostsTheSame_takesEveryDefault() {
            // When — every combination nets 200
            ElectionResolver.JointResolution resolution = resolveTogether(Map.of(), twoHousesNotLet());

            // Then — the houses as entered (none specified) and the declared order
            assertThat(resolution.outcomes().get(0).chosen()).isEqualTo(NONE);
            assertThat(resolution.outcomes().get(1).chosen()).isEqualTo(DEFAULT);
        }

        @Test
        void resolveTogether_whenGivenOneElection_isRefused() {
            // When / Then
            assertThatThrownBy(() -> ElectionResolver.resolveTogether(List.of(new HouseElection()),
                    NodeRegistry.of(List.of()), PACK, PACK.regimes().values().iterator().next(), twoHousesNotLet()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("two or more elections of different kinds");
        }

        @Test
        void resolveTogether_whenTwoElectionsAreOfOneKind_isRefused() {
            // When / Then
            assertThatThrownBy(() -> ElectionResolver.resolveTogether(
                    List.of(new HouseElection(), new HouseElection()), NodeRegistry.of(List.of()), PACK,
                    PACK.regimes().values().iterator().next(), twoHousesNotLet()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("two or more elections of different kinds");
        }
    }
}
