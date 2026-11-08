package com.thiru.wealthlens.taxengine.optimise;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.AllocationSpace;
import com.thiru.wealthlens.taxengine.node.ShippedNodes;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.Fixtures;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The measured saturation point (W-238 C1a) against the hand figure from {@code SAL-HOUSE-RENT-ALLOWANCE}'s limbs: basic
 * 6,00,000 and rent 3,00,000 in Mumbai give rent less 10 % of salary = 3,00,000 − 60,000 = 2,40,000 and 50 % of salary
 * = 3,00,000, so the least is 2,40,000; the new regime exempts nothing, so its point is nil.
 */
class SaturationTest {

    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final RulePack PACK = ShippedRulePacks.compile("rulepacks", Set.of(TY_2026_27)).load(TY_2026_27);
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final AllocationSpace SPACE = new AllocationSpace(
            Fixtures.rentingInMumbai("600000", "600000", "300000", List.of()), Set.of(ComponentCodes.HRA),
            ComponentCodes.SPECIAL_ALLOWANCE, Money.ZERO, 6);

    private static Money saturation(RegimeCode regime) {
        AllocationPricing pricing = AllocationPricing.over(NodeRegistry.of(ShippedNodes.all()), PACK,
                PACK.regimes().get(NEW), SPACE);
        return Saturation.of(pricing, pricing.entered(SPACE), ComponentCodes.HRA, regime);
    }

    @Test
    void of_whenTheOldRegimeExemptsHra_findsTheLeastLimb() {
        // When
        Money point = saturation(new RegimeCode("FIRST_SCHEDULE_RATES"));

        // Then
        assertThat(point).isEqualTo(Money.of("240000"));
    }

    @Test
    void of_whenTheNewRegimeExemptsNothing_isNil() {
        // When
        Money point = saturation(NEW);

        // Then
        assertThat(point).isEqualTo(Money.ZERO);
    }
}
