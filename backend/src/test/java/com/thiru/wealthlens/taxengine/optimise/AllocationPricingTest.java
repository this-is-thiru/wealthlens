package com.thiru.wealthlens.taxengine.optimise;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.rules.component.CashNature;
import com.thiru.wealthlens.taxengine.rules.id.EmployerFundCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The cash a move costs in hand before tax (W-238b, A2.6's {@code cashImpact}). The rule is the one take-home already
 * reads (C3): CASH counts toward cash, CASH_OUTFLOW against it, NON_CASH not at all; the residual gives up what the
 * moved component takes. Every figure below is that rule applied by hand.
 */
class AllocationPricingTest {

    @Test
    void cashImpact_whenCashFundsABenefitInKind_costsTheWholeMoveInHand() {
        // Given — 60,000 leaves the cash residual (−60,000) for a component that pays nothing in hand (0)

        // When
        Money impact = AllocationPricing.cashImpact(CashNature.CASH, CashNature.NON_CASH, Money.ZERO, Money.of("60000"));

        // Then
        assertThat(impact).isEqualTo(Money.of("-60000"));
    }

    @Test
    void cashImpact_whenCashMovesToCash_isNil() {
        // Given — −8,00,000 from the residual, +8,00,000 into HRA, both paid in money

        // When
        Money impact = AllocationPricing.cashImpact(CashNature.CASH, CashNature.CASH, Money.ZERO, Money.of("800000"));

        // Then
        assertThat(impact).isEqualTo(Money.ZERO);
    }

    @Test
    void cashImpact_whenCashFundsAnOutflow_costsTheMoveTwice() {
        // Given — the residual gives up 50,000 (−50,000) and the outflow grows by 50,000 (−50,000)

        // When
        Money impact = AllocationPricing.cashImpact(CashNature.CASH, CashNature.CASH_OUTFLOW, Money.ZERO,
                Money.of("50000"));

        // Then
        assertThat(impact).isEqualTo(Money.of("-100000"));
    }

    @Test
    void cashImpact_whenABenefitInKindIsReturnedToCash_addsItInHand() {
        // Given — 60,000 comes back from the benefit (0) to the cash residual (+60,000)

        // When
        Money impact = AllocationPricing.cashImpact(CashNature.CASH, CashNature.NON_CASH, Money.of("60000"), Money.ZERO);

        // Then
        assertThat(impact).isEqualTo(Money.of("60000"));
    }

    @Test
    void feedsInto_whenAnyEmployerFundMoves_findsItsRowInEveryShippedYear() {
        // Given — a fund the inputs can name with no row would refuse the optimisation (W-238e)
        Set<TaxYearId> years = Set.of(TaxYearId.of(2025), TaxYearId.of(2026));
        ShippedRulePacks packs = ShippedRulePacks.compile("rulepacks", years);

        // When / Then
        for (TaxYearId year : years) {
            assertThat(packs.load(year).funds().keySet()).as(year.label()).containsExactlyInAnyOrderElementsOf(
                    Arrays.stream(EmployerFund.values()).map(fund -> new EmployerFundCode(fund.name())).toList());
        }
    }
}
