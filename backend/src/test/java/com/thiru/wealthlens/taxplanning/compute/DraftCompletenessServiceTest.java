package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Completeness ranked by measured worth (W-115, D-670), worked by hand from the TY 2026-27 rows: standard deduction
 * ₹50,000; slabs nil to 2,50,000, 5 % to 5,00,000, 20 % to 10,00,000, 30 % above; cess 4 %. Basic 10,00,000 and HRA
 * 4,00,000 with no rent and nothing paid make an income of 13,50,000, so every rupee of room below 3,50,000 saves 30 % with
 * cess on it, 31.2 %: Schedule XV 1,50,000 → 46,800; own NPS 50,000 → 15,600; the assessee's health cover 25,000 → 7,800.
 * Rent exempts the whole allowance: 2,26,200 less 1,06,600 is 1,19,600 ({@code RentRoomPricingTest}).
 */
class DraftCompletenessServiceTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(
            ShippedRulePacks.compile("rulepacks", ClasspathRulePackProvider.shippedYears("rulepacks")));

    private static final DraftCompletenessService SERVICE = new DraftCompletenessService(null, null, CALCULATOR, null);

    static EngineInputs inputs(String regime) {
        EngineInputs plain = NodeHarness.plainTaxpayer();
        Employment pay = new Employment(List.of(new SalaryComponent(new ComponentCode("BASIC"), Money.of("1000000")),
                new SalaryComponent(new ComponentCode("HRA"), Money.of("400000"))), false, EmployerKind.OTHER, false,
                Optional.empty());
        return new EngineInputs(TaxYearId.of(2026), new RegimeCode(regime), plain.temporal(),
                EnteredFacts.none().withEmployments(List.of(pay)), plain.assessee(), plain.family(), plain.ledgers(),
                plain.elections(), plain.capitalGains(), plain.filing());
    }

    @Test
    void items_whenNothingIsEnteredInTheOldRegime_ranksEachByTheTaxItSaves() {
        // Given
        EngineInputs inputs = inputs("FIRST_SCHEDULE_RATES");

        // When
        List<DraftCompleteness.Item> items = SERVICE.items(inputs);

        // Then
        assertThat(items).containsExactly(
                new DraftCompleteness.Item("SAL-HOUSE-RENT-ALLOWANCE", DraftCompleteness.Direction.REDUCES, Money.ZERO,
                        Money.of("400000"), Optional.of(Money.of("500000")), Optional.of("ANY_OTHER_PLACE"),
                        Money.of("119600")),
                new DraftCompleteness.Item("DED-SCHEDULE-XV", DraftCompleteness.Direction.REDUCES, Money.ZERO,
                        Money.of("150000"), Optional.empty(), Optional.empty(), Money.of("46800")),
                new DraftCompleteness.Item("DED-NPS-OWN", DraftCompleteness.Direction.REDUCES, Money.ZERO,
                        Money.of("50000"), Optional.empty(), Optional.empty(), Money.of("15600")),
                new DraftCompleteness.Item("DED-HEALTH-FAMILY", DraftCompleteness.Direction.REDUCES, Money.ZERO,
                        Money.of("25000"), Optional.empty(), Optional.empty(), Money.of("7800")));
    }

    @Test
    void items_whenTheRegimeAllowsNoneOfThem_isEmpty() {
        // Given the new regime: no Schedule XV, s.124(3), health or HRA (s.202(2)(a)), so every worth is nil and dropped
        EngineInputs inputs = inputs("NEW_TAX_REGIME");

        // When / Then
        assertThat(SERVICE.items(inputs)).isEmpty();
    }
}
