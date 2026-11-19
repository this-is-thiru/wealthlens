package com.thiru.wealthlens.taxengine.election;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HouseOccupancy;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
import com.thiru.wealthlens.taxengine.node.HousePropertyNode;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The house election's choices (D-230) on the TY 2026-27 pack, where at most two houses may be specified. */
class HouseElectionTest {

    private static final RulePack PACK = new ClasspathRulePackProvider("rulepacks", Set.of(TaxYearId.of(2026)),
            new JsonPackAssembler(LockedInstruments.of(TaxYearId.of(2026)))).load(TaxYearId.of(2026));

    private static HouseProperty house(HouseOccupancy occupancy) {
        Money rent = occupancy == HouseOccupancy.LET ? Money.of("120000") : Money.ZERO;
        return new HouseProperty(occupancy, Money.of("300000"), rent, Money.ZERO, false, Money.ZERO,
                Rate.percent("100"), Money.ZERO, Optional.empty(), List.of());
    }

    /** The rules as the house election reads them: through its node, {@code houseproperty.income} (D-255). */
    private static NodeContext rules(EngineInputs inputs) {
        return NodeContext.forRun(PACK, PACK.regimes().get(inputs.regime()), inputs).forNode(new HousePropertyNode());
    }

    private static EngineInputs entering(HouseOccupancy... occupancies) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        return new EngineInputs(plain.taxYear(), plain.regime(), plain.temporal(),
                EnteredFacts.none().withHouseProperties(Arrays.stream(occupancies).map(HouseElectionTest::house).toList()),
                plain.assessee(), plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
    }

    @Test
    void candidates_whenThreeHousesQualifyAndOneIsLet_areEverySetOfAtMostTwoTheEnteredFirst() {
        // Given — properties 1, 2 own residences, 3 not let, 4 let
        EngineInputs inputs = entering(HouseOccupancy.OWN_RESIDENCE, HouseOccupancy.OWN_RESIDENCE,
                HouseOccupancy.NOT_LET, HouseOccupancy.LET);

        // When
        List<List<Integer>> candidates = new HouseElection().candidates(rules(inputs));

        // Then — C(3,2) + C(3,1) + C(3,0) = 7; the let house (index 3) in none
        assertThat(candidates).containsExactly(List.of(0, 1), List.of(0, 2), List.of(1, 2), List.of(0), List.of(1),
                List.of(2), List.of());
    }

    @Test
    void candidates_whenMoreThanTheLimitAreEntered_offerNoAsEnteredSet() {
        // Given — three entered as own residences, two allowed
        EngineInputs inputs = entering(HouseOccupancy.OWN_RESIDENCE, HouseOccupancy.OWN_RESIDENCE,
                HouseOccupancy.OWN_RESIDENCE);

        // When
        List<List<Integer>> candidates = new HouseElection().candidates(rules(inputs));

        // Then
        assertThat(candidates).hasSize(7).first().isEqualTo(List.of(0, 1));
        assertThat(candidates).allSatisfy(set -> assertThat(set).hasSizeLessThanOrEqualTo(2));
    }

    @Test
    void choosing_whenASetIsChosen_relabelsTheQualifyingHousesAndLeavesTheLetOne() {
        // Given
        EngineInputs inputs = entering(HouseOccupancy.OWN_RESIDENCE, HouseOccupancy.NOT_LET, HouseOccupancy.LET);

        // When
        EngineInputs chosen = new HouseElection().choosing(List.of(1), inputs);

        // Then
        assertThat(chosen.entered().houseProperties()).extracting(HouseProperty::occupancy)
                .containsExactly(HouseOccupancy.NOT_LET, HouseOccupancy.OWN_RESIDENCE, HouseOccupancy.LET);
    }
}
