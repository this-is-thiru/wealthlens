package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.ComputedTax;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The forecast's arithmetic (W-113, D-664), on {@code DR 13} T13-1's bill of 87,750 ({@code plans/W-089.md}) with the
 * deferment interest {@code DraftComputationIntegrationTest} works by hand for 75,000 at source on 6-11-2026: 57 + 171 = 228.
 * The instalments are what 87,750 − 75,000 = 12,750 gives at 15, 45, 75 and 100 % ({@code ADV-INSTALMENTS}).
 */
class DraftForecastServiceTest {

    private static final LocalDate DUE = LocalDate.of(2027, 7, 31);
    private static final LocalDate SIXTH_NOVEMBER = LocalDate.of(2026, 11, 6);

    private static ComputedTax.AdvanceTax.Instalment instalment(LocalDate dueOn, String owed) {
        return new ComputedTax.AdvanceTax.Instalment(dueOn, Money.of(owed), Money.ZERO, Money.of(owed));
    }

    private static final List<ComputedTax.AdvanceTax.Instalment> NOTHING_PAID = List.of(
            instalment(LocalDate.of(2026, 6, 15), "1912.50"), instalment(LocalDate.of(2026, 9, 15), "5737.50"),
            instalment(LocalDate.of(2026, 12, 15), "9562.50"), instalment(LocalDate.of(2027, 3, 15), "12750"));

    private static ComputedTax tax(String bill, List<ComputedTax.Owed> interest, String credits,
            List<ComputedTax.AdvanceTax.Instalment> instalments, String finalAmount) {
        return new ComputedTax("id", "receipt", "2026-27", "NEW_TAX_REGIME", Money.of(bill), Money.of(bill), Money.of(credits),
                new ComputedTax.AdvanceTax(!instalments.isEmpty(), Money.ZERO, instalments), interest, List.of(),
                Money.of(finalAmount), "fingerprint", List.of(), List.of(), List.of());
    }

    private static final List<ComputedTax.Owed> DEFERMENT = List.of(new ComputedTax.Owed("INT-DEFERMENT", "INTEREST", Money.of("228")));

    @Test
    void forecastOf_whenTheEmployersPartIsStated_showsThreeFiguresThatAddUpToTheRoundedNet() {
        // Given — 75,000 at source, 60,000 of it the employer's
        ComputedTax tax = tax("87750", DEFERMENT, "75000", NOTHING_PAID, "12980");

        // When
        DraftForecast forecast = DraftForecastService.forecastOf(tax, Optional.of(Money.of("60000")), DUE, SIXTH_NOVEMBER, 1,
                List.of());

        // Then — 87,750 + 228 = 87,978; 75,000 − 60,000 = 15,000; 87,978 − 60,000 − 15,000 = 12,978, to the nearest ₹10 12,980
        assertThat(forecast.liability()).isEqualTo(Money.of("87978"));
        assertThat(forecast.byEmployer()).contains(Money.of("60000"));
        assertThat(forecast.otherCredits()).isEqualTo(Money.of("15000"));
        assertThat(forecast.rounding()).isEqualTo(Money.of("2"));
        assertThat(forecast.net()).isEqualTo(Money.of("12980"));
        assertThat(forecast.settlement()).isEqualTo(DraftForecast.Settlement.PAYABLE_NOW);
    }

    @Test
    void forecastOf_whenAnInstalmentIsStillToCome_isPayableByTheFirstOneStillOwed() {
        // Given — 15 June and 15 September have passed by 6-11-2026; 15 December is the next
        ComputedTax tax = tax("87750", DEFERMENT, "75000", NOTHING_PAID, "12980");

        // When
        DraftForecast forecast = DraftForecastService.forecastOf(tax, Optional.empty(), DUE, SIXTH_NOVEMBER, 1, List.of());

        // Then
        assertThat(forecast.payableBy()).contains(LocalDate.of(2026, 12, 15));
    }

    @Test
    void forecastOf_whenTheNextInstalmentIsPaid_isPayableByTheOneAfter() {
        // Given — December's 9,562.50 paid in full
        List<ComputedTax.AdvanceTax.Instalment> decemberPaid = List.of(NOTHING_PAID.get(0), NOTHING_PAID.get(1),
                new ComputedTax.AdvanceTax.Instalment(LocalDate.of(2026, 12, 15), Money.of("9562.50"), Money.of("9562.50"),
                        Money.ZERO),
                NOTHING_PAID.get(3));

        // When
        DraftForecast forecast = DraftForecastService.forecastOf(tax("87750", DEFERMENT, "75000", decemberPaid, "12980"),
                Optional.empty(), DUE, SIXTH_NOVEMBER, 1, List.of());

        // Then
        assertThat(forecast.payableBy()).contains(LocalDate.of(2027, 3, 15));
    }

    @Test
    void forecastOf_whenEveryInstalmentHasPassed_isPayableByTheReturnsDueDate() {
        // Given / When
        DraftForecast forecast = DraftForecastService.forecastOf(tax("87750", DEFERMENT, "75000", NOTHING_PAID, "12980"),
                Optional.empty(), DUE, LocalDate.of(2027, 4, 10), 1, List.of());

        // Then
        assertThat(forecast.payableBy()).contains(DUE);
    }

    @Test
    void forecastOf_whenMoreWasCreditedThanIsOwed_isARefundAfterFilingWithNoDate() {
        // Given — 87,750 − 1,00,004 = −12,254, to the nearest ₹10 −12,250; the employer's part not stated
        ComputedTax tax = tax("87750", List.of(), "100004", List.of(), "-12250");

        // When
        DraftForecast forecast = DraftForecastService.forecastOf(tax, Optional.empty(), DUE, SIXTH_NOVEMBER, 1, List.of());

        // Then — all 1,00,004 is another credit; −12,250 − (−12,254) = 4
        assertThat(forecast.byEmployer()).isEmpty();
        assertThat(forecast.otherCredits()).isEqualTo(Money.of("100004"));
        assertThat(forecast.rounding()).isEqualTo(Money.of("4"));
        assertThat(forecast.settlement()).isEqualTo(DraftForecast.Settlement.REFUND_AFTER_FILING);
        assertThat(forecast.payableBy()).isEmpty();
    }

    @Test
    void forecastOf_whenNothingIsOwedOrCredited_isNil() {
        // Given / When
        DraftForecast forecast = DraftForecastService.forecastOf(tax("0", List.of(), "0", List.of(), "0"), Optional.empty(), DUE,
                SIXTH_NOVEMBER, 1, List.of());

        // Then
        assertThat(forecast.settlement()).isEqualTo(DraftForecast.Settlement.NIL);
        assertThat(forecast.payableBy()).isEmpty();
    }
}
