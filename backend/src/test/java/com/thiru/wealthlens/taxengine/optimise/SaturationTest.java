package com.thiru.wealthlens.taxengine.optimise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.api.AllocationSpace;
import com.thiru.wealthlens.taxengine.api.ProvidentFundTerms;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.node.ShippedNodes;
import com.thiru.wealthlens.taxengine.pipeline.NodeRegistry;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.Fixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
            Fixtures.rentingInMumbai("600000", "600000", "300000", List.of()), new AllocationSpace.Moves(Set.of(ComponentCodes.HRA), Set.of(), Set.of()),
            ComponentCodes.SPECIAL_ALLOWANCE, Money.ZERO, 6, ProvidentFundTerms.FOLLOW_BASIC);

    private static Money saturation(RegimeCode regime) {
        AllocationPricing pricing = AllocationPricing.over(NodeRegistry.of(ShippedNodes.all()), PACK,
                PACK.regimes().get(NEW), SPACE);
        return Saturation.of(pricing, pricing.entered(SPACE), new Movable.Pay(ComponentCodes.HRA), regime);
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

    /**
     * A total income that falls, is flat, then falls again — own NPS's shape before W-114d claimed s.124(3) first (W-238k
     * C3a; D-669): falling to 1,50,000, flat to the 1,60,000 sub-cap, falling again for 50,000, then flat. The search
     * must not stop at the flat stretch: the least amount reaching the floor is 2,10,000.
     */
    @Test
    void leastReaching_whenTotalIncomeIsFlatAndThenFallsAgain_findsTheSecondStop() {
        // Given
        Money income = Money.of("2350000");

        // When
        Money point = Saturation.leastReaching(Money.of("2400000"), amount -> income
                .minus(Money.min(amount, Money.of("150000")))
                .minus(Money.min(Money.max(amount.minus(Money.of("160000")), Money.ZERO), Money.of("50000"))));

        // Then
        assertThat(point).isEqualTo(Money.of("210000"));
    }

    @Test
    void leastReaching_whenTotalIncomeRisesWithTheAmount_isRefused() {
        // When / Then
        assertThatThrownBy(() -> Saturation.leastReaching(Money.of("1000"), amount -> Money.of("500").plus(amount)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Total income rises");
    }

    /**
     * Employee NPS through the pipeline where the flat stretch is wide (W-238k C3a): basic 30,00,000 puts the sub-cap at
     * 10 % × 30,00,000 = 3,00,000 ({@code 01-rulebook.md:1078}) above the 1,50,000 ceiling ({@code :1076}); own NPS claims
     * its 50,000 under s.124(3) first ({@code :972}; W-114d, D-669) and 1,50,000 more fills s.123, so the point is 2,00,000
     * (3,50,000 before W-114d, when s.123 took the first 3,00,000).
     */
    @Test
    void of_whenTheContributionsTotalIncomeIsFlatPastTheCeiling_findsTheEndOfOwnNps() {
        // Given
        AllocationSpace space = new AllocationSpace(Fixtures.salaried("3000000", "800000", List.of(), Optional.empty()),
                new AllocationSpace.Moves(Set.of(), Set.of(), Set.of(ComponentCodes.EMPLOYEE_NPS)),
                ComponentCodes.SPECIAL_ALLOWANCE, Money.ZERO, 6, ProvidentFundTerms.FOLLOW_BASIC);
        AllocationPricing pricing = AllocationPricing.over(NodeRegistry.of(ShippedNodes.all()), PACK,
                PACK.regimes().get(NEW), space);

        // When
        Money point = Saturation.of(pricing, pricing.entered(space), new Movable.Contribution(ComponentCodes.EMPLOYEE_NPS),
                new RegimeCode("FIRST_SCHEDULE_RATES"));

        // Then
        assertThat(point).isEqualTo(Money.of("200000"));
    }

    private static final Movable.Contribution OWN_PF = new Movable.Contribution(ComponentCodes.EMPLOYEE_PF);
    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");

    /**
     * Basic 10,00,000 and special 14,00,000 from an OTHER employer, the employee's provident fund 60,000 following basic
     * and named as a contribution (W-238k1); the pricing over it.
     */
    private static AllocationSpace followingSpace() {
        EngineInputs paid = Fixtures.salaried("1000000", "1400000", List.of(), Optional.empty());
        Employment held = paid.entered().employments().getFirst();
        List<SalaryComponent> components = new ArrayList<>(held.components());
        components.add(new SalaryComponent(ComponentCodes.EMPLOYEE_PF, Money.of("60000")));
        Employment changed = new Employment(components, held.termsIncludeDearnessAllowance(), held.employerKind(),
                held.dearnessAllowanceCountsForRetirement(), held.companyEmployer());
        EngineInputs inputs = new EngineInputs(paid.taxYear(), paid.regime(), paid.temporal(),
                paid.entered().withEmployments(List.of(changed)), paid.assessee(), paid.family(), paid.ledgers(),
                paid.elections(), paid.capitalGains(), paid.filing());
        return new AllocationSpace(inputs, new AllocationSpace.Moves(Set.of(ComponentCodes.BASIC), Set.of(),
                Set.of(ComponentCodes.EMPLOYEE_PF)), ComponentCodes.SPECIAL_ALLOWANCE, Money.ZERO, 6,
                ProvidentFundTerms.FOLLOW_BASIC);
    }

    private static AllocationPricing pricingOver(AllocationSpace space) {
        return AllocationPricing.over(NodeRegistry.of(ShippedNodes.all()), PACK, PACK.regimes().get(NEW), space);
    }

    /**
     * Basic moved to 12,00,000 takes the follower to 60,000 × 12 ÷ 10 = 72,000, so the old regime's total income is
     * 24,00,000 − 50,000 − min(72,000 + a, 1,50,000) ({@code DED-80C-001}, {@code 01-rulebook.md:1076}) and the point
     * is 78,000 — not the 90,000 the pay as entered leaves (W-238k1).
     */
    @Test
    void of_whenTheContributionFollowsAMovedBasic_measuresTheHeadroomLeftAtTheMovedBasic() {
        // Given
        AllocationSpace space = followingSpace();
        AllocationPricing pricing = pricingOver(space);
        Allocation moved = pricing.entered(space).moved(new Movable.Pay(ComponentCodes.BASIC), Money.of("1200000"));

        // When
        Money point = Saturation.of(pricing, moved, OWN_PF, OLD);

        // Then
        assertThat(point).isEqualTo(Money.of("78000"));
    }

    /**
     * At basic 12,00,000 the headroom is 1,50,000 − 72,000 = 78,000 whatever the addition already is: an addition of
     * 90,000 is cut to it (W-238k1 C2a) and one of 50,000 raised to it (W-238k3), so the answer is re-measured both ways.
     */
    @Test
    void of_whenTheAdditionIsAboveOrBelowTheHeadroom_isTheHeadroomEitherWay() {
        // Given
        AllocationSpace space = followingSpace();
        AllocationPricing pricing = pricingOver(space);
        Allocation moved = pricing.entered(space).moved(new Movable.Pay(ComponentCodes.BASIC), Money.of("1200000"));

        // When
        Money overfilled = Saturation.of(pricing, moved.moved(OWN_PF, Money.of("90000")), OWN_PF, OLD);
        Money underfilled = Saturation.of(pricing, moved.moved(OWN_PF, Money.of("50000")), OWN_PF, OLD);

        // Then
        assertThat(overfilled).isEqualTo(Money.of("78000"));
        assertThat(underfilled).isEqualTo(Money.of("78000"));
    }
}
