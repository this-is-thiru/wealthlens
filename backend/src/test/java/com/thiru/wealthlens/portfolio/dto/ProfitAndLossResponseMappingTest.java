package com.thiru.wealthlens.portfolio.dto;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.thiru.wealthlens.brokercharges.entity.model.MonthlyChargeSummary;
import com.thiru.wealthlens.brokercharges.entity.model.YearlyChargeSummary;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.model.RealisedProfits;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import java.time.Month;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * That the profit-and-loss response carries everything the stored record holds.
 *
 * <p>The mapping is {@code TJsonMapper.safeCopy}, which matches by field name and — being
 * {@code safe} — drops anything the target does not declare, without complaint. A field missing
 * from the response is therefore invisible: the endpoint simply answers without it. That is how the
 * charge hierarchy came to be written on every V2 sell and returned to nobody.
 */
@Tag("unit")
class ProfitAndLossResponseMappingTest {

    /**
     * The charge side of the hierarchy. It is keyed by charge code rather than by fixed columns, so
     * a new charge reaches a report as data — which it cannot do if the field is absent.
     */
    @Test
    @DisplayName("the yearly charge summary survives the mapping to the response")
    void safeCopy_whenChargesAreRecorded_carriesTheChargeSummary() {
        // Given
        ProfitAndLossEntity entity = new ProfitAndLossEntity();
        entity.setEmail("test@example.com");
        entity.setFinancialYear("2025-2026");
        entity.setRealisedProfits(realisedProfitsWithCharges());

        // When
        ProfitAndLossResponse response = TJsonMapper.safeCopy(entity, ProfitAndLossResponse.class);

        // Then
        YearlyChargeSummary yearly = response.getRealisedProfits().getYearlyChargeSummary();
        assertNotNull(yearly, "the charge hierarchy is stored but never reached the caller");
        assertMoney(123.60, yearly.getTotalCharges());
        assertEquals(Map.of("BROKERAGE", 100.00, "GST", 23.60), yearly.getAmountByCode());
    }

    /** The months beneath the year, so a caller can break a figure down without a second call. */
    @Test
    @DisplayName("the monthly charge breakdown survives with it")
    void safeCopy_whenChargesAreRecorded_carriesTheMonthlyBreakdown() {
        // Given
        ProfitAndLossEntity entity = new ProfitAndLossEntity();
        entity.setRealisedProfits(realisedProfitsWithCharges());

        // When
        ProfitAndLossResponse response = TJsonMapper.safeCopy(entity, ProfitAndLossResponse.class);

        // Then
        Map<Month, MonthlyChargeSummary> monthly =
                response.getRealisedProfits().getYearlyChargeSummary().getMonthlyReport();
        assertEquals(1, monthly.size());
        assertMoney(123.60, monthly.get(Month.APRIL).getTotalCharges());
    }

    private static RealisedProfits realisedProfitsWithCharges() {
        MonthlyChargeSummary april = new MonthlyChargeSummary();
        april.setMonth(Month.APRIL);
        april.merge(Map.of("BROKERAGE", 100.00, "GST", 23.60));

        YearlyChargeSummary yearly = new YearlyChargeSummary();
        yearly.merge(Map.of("BROKERAGE", 100.00, "GST", 23.60));
        yearly.getMonthlyReport().put(Month.APRIL, april);

        RealisedProfits realisedProfits = RealisedProfits.empty();
        realisedProfits.setYearlyChargeSummary(yearly);
        return realisedProfits;
    }
}
