package com.thiru.wealthlens.taxengine.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.HouseOccupancy;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.condition.HousePropertiesOwnedAtMost;
import com.thiru.wealthlens.taxengine.rules.condition.IsOrdinarilyResident;
import com.thiru.wealthlens.taxengine.rules.condition.IsResident;
import com.thiru.wealthlens.taxengine.rules.condition.ResidentialStatus;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The person facts r.164(2) reads reach every condition context from the inputs (W-090c1a): the residential status
 * whole, and the house properties owned, a share counting as owning (D-524 C2a).
 */
class NodeContextPersonFactsTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final RulePack PACK = ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27)).load(TY_2026_27);

    private static HouseProperty owned(String sharePercent) {
        return new HouseProperty(HouseOccupancy.NOT_LET, Money.of("300000"), Money.ZERO, Money.ZERO, false, Money.ZERO,
                Rate.percent(sharePercent), Money.ZERO, Optional.empty(), List.of());
    }

    private static ConditionContext conditions(ResidentialStatus residence, List<HouseProperty> houses) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(plain.taxYear(), plain.regime(), plain.temporal(),
                EnteredFacts.none().withHouseProperties(houses),
                new Individual(LocalDate.of(1986, 6, 1), residence, Optional.empty(), Optional.empty()),
                plain.family(), plain.ledgers(), plain.elections(), plain.capitalGains(), plain.filing());
        return NodeContext.forRun(PACK, PACK.regimes().get(new RegimeCode("NEW_TAX_REGIME")), inputs).conditions();
    }

    @Test
    void conditions_whenNotOrdinarilyResident_carriesTheStatusWhole() {
        // Given / When
        ConditionContext rnor = conditions(ResidentialStatus.RNOR, List.of());

        // Then
        assertThat(rnor.residence()).isEqualTo(ResidentialStatus.RNOR);
        assertThat(new IsResident().holds(rnor)).isTrue();
        assertThat(new IsOrdinarilyResident().holds(rnor)).isFalse();
    }

    @Test
    void conditions_whenAHalfShareAndAWholePropertyAreEntered_countsTwoOwned() {
        // Given / When
        ConditionContext two = conditions(ResidentialStatus.RESIDENT, List.of(owned("50"), owned("100")));

        // Then
        assertThat(two.housePropertiesOwned()).isEqualTo(2);
        assertThat(new HousePropertiesOwnedAtMost(2).holds(two)).isTrue();
        assertThat(new HousePropertiesOwnedAtMost(1).holds(two)).isFalse();
    }

    @Test
    void conditions_whenNoPropertyIsEntered_countsNone() {
        assertThat(conditions(ResidentialStatus.RESIDENT, List.of()).housePropertiesOwned()).isZero();
    }
}
