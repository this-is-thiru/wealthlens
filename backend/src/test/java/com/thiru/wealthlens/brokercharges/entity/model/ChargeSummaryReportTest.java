package com.thiru.wealthlens.brokercharges.entity.model;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertBreakdown;
import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The aggregation shape that replaces six fixed columns with a map keyed by charge code.
 *
 * <p>The point of the type is AC-1: a charge that exists only in a rate card must reach every
 * report without a Java change. The superseded {@code BrokerChargesReport} had one setter per
 * charge, so a new code was invisible until six classes were edited — and silently absent rather
 * than obviously missing, which is the worse failure.
 *
 * <p>Money is summed here, so the two properties a reader depends on are asserted directly: the
 * total always equals the sum of its parts, and repeated merging does not drift. Neither holds for
 * naive {@code double} accumulation, which is why {@code merge} does not use it.
 */
class ChargeSummaryReportTest {

    @Test
    void merge_whenCodeIsUnknownToJava_surfacesItInTheBreakdown() {
        // Given — a code that appears nowhere in the codebase, as a rate card's new rule would
        ChargeSummaryReport report = new ChargeSummaryReport();

        // When
        report.merge(Map.of("SYNTHETIC_LEVY_FOR_TEST", 12.50));

        // Then — it aggregates on its own, with no code change (AC-1)
        assertBreakdown(Map.of("SYNTHETIC_LEVY_FOR_TEST", 12.50), report.getAmountByCode());
        assertMoney(12.50, report.getTotalCharges());
    }

    @Test
    void merge_whenCodeAlreadyPresent_addsToTheRunningAmount() {
        // Given
        ChargeSummaryReport report = new ChargeSummaryReport();
        report.merge(Map.of("BROKERAGE", 20.00, "STT", 100.00));

        // When — a second trade in the same period
        report.merge(Map.of("BROKERAGE", 15.00, "DP", 13.50));

        // Then
        assertBreakdown(Map.of("BROKERAGE", 35.00, "STT", 100.00, "DP", 13.50), report.getAmountByCode());
        assertMoney(148.50, report.getTotalCharges());
    }

    @Test
    void merge_keepsTheTotalEqualToTheSumOfTheBreakdown() {
        // Given
        ChargeSummaryReport report = new ChargeSummaryReport();

        // When
        report.merge(Map.of("BROKERAGE", 20.00, "EXCHANGE_TXN", 2.97, "GST", 4.13));
        report.merge(Map.of("STT", 100.00, "STAMP_DUTY", 15.00));

        // Then — a reader who sums the columns must land on the printed total
        double summed = report.getAmountByCode().values().stream().mapToDouble(Double::doubleValue).sum();
        assertMoney("total against the sum of its parts", summed, report.getTotalCharges());
    }

    @Test
    void merge_acrossManyIncrements_doesNotDrift() {
        // Given — ten paise-scale charges, the case where double addition visibly fails:
        // 0.1 added ten times is 0.9999999999999999, and a report showing that is a bug report
        ChargeSummaryReport report = new ChargeSummaryReport();

        // When
        for (int i = 0; i < 10; i++) {
            report.merge(Map.of("DP", 0.10));
        }

        // Then — exact, not merely close
        assertThat(report.getAmountByCode().get("DP")).isEqualTo(1.00);
        assertThat(report.getTotalCharges()).isEqualTo(1.00);
    }

    @Test
    void merge_roundsEachCodeToPaise() {
        // Given — a rule carrying RoundingPolicy.NONE can emit sub-paise amounts
        ChargeSummaryReport report = new ChargeSummaryReport();

        // When
        report.merge(Map.of("EXCHANGE_TXN", 2.9745));

        // Then — a report is denominated in paise; the unrounded value stays on the contract note
        assertThat(report.getAmountByCode().get("EXCHANGE_TXN")).isEqualTo(2.97);
    }

    @Test
    void merge_whenIncrementsAreNullOrEmpty_changesNothing() {
        // Given — a zero-charge row is legitimate: a period with no rate card still writes one
        ChargeSummaryReport report = new ChargeSummaryReport();
        report.merge(Map.of("BROKERAGE", 20.00));

        // When
        report.merge(null);
        report.merge(Map.of());

        // Then
        assertBreakdown(Map.of("BROKERAGE", 20.00), report.getAmountByCode());
        assertMoney(20.00, report.getTotalCharges());
    }

    @Test
    void merge_whenAnAmountIsNull_isRejected() {
        // Given — a null amount is a caller defect, and folding it in as zero would hide it
        ChargeSummaryReport report = new ChargeSummaryReport();
        Map<String, Double> increments = new HashMap<>();
        increments.put("BROKERAGE", null);

        // When / Then
        assertThatThrownBy(() -> report.merge(increments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BROKERAGE");
    }

    @Test
    void merge_doesNotAliasTheSourceMap() {
        // Given — the source is a persisted entity's own map; the report must not share it
        ChargeSummaryReport report = new ChargeSummaryReport();
        Map<String, Double> increments = new HashMap<>();
        increments.put("BROKERAGE", 20.00);
        report.merge(increments);

        // When — the caller reuses its map for the next trade
        increments.put("BROKERAGE", 999.00);

        // Then
        assertBreakdown(Map.of("BROKERAGE", 20.00), report.getAmountByCode());
    }

    @Test
    void merge_stampsTheLastUpdatedTime() {
        // Given
        ChargeSummaryReport report = new ChargeSummaryReport();
        assertThat(report.getLastUpdatedTime()).isNull();
        // UTC, not the local clock: CLAUDE.md stores every time in UTC, and a baseline taken
        // from LocalDateTime.now() would pass in London and fail in India
        LocalDateTime before = TLocalDateTime.now().minusMinutes(1);

        // When
        report.merge(Map.of("BROKERAGE", 20.00));

        // Then — @LastModifiedDate does not fire on a nested document, so merge stamps it itself
        assertThat(report.getLastUpdatedTime()).isAfter(before);
    }

    @Test
    void yearlyChargeSummary_aggregatesAlongsideItsMonths() {
        // Given
        YearlyChargeSummary yearly = new YearlyChargeSummary();
        MonthlyChargeSummary april = new MonthlyChargeSummary(Month.APRIL);
        april.merge(Map.of("BROKERAGE", 20.00));
        yearly.getMonthlyReport().put(Month.APRIL, april);

        // When — the year rolls up the same increment the month took
        yearly.merge(Map.of("BROKERAGE", 20.00));

        // Then
        assertMoney(20.00, yearly.getTotalCharges());
        assertMoney(20.00, yearly.getMonthlyReport().get(Month.APRIL).getTotalCharges());
        assertThat(yearly.getMonthlyReport().get(Month.APRIL).getMonth()).isEqualTo(Month.APRIL);
    }

    @Test
    void monthlyChargeSummary_holdsBothFortnightHalvesIndependently() {
        // Given
        MonthlyChargeSummary april = new MonthlyChargeSummary(Month.APRIL);
        ChargeSummaryReport firstHalf = new ChargeSummaryReport();
        ChargeSummaryReport secondHalf = new ChargeSummaryReport();
        april.setFirstHalfCharges(firstHalf);
        april.setSecondHalfCharges(secondHalf);

        // When
        firstHalf.merge(Map.of("BROKERAGE", 20.00));
        secondHalf.merge(Map.of("BROKERAGE", 15.00));
        april.merge(Map.of("BROKERAGE", 20.00));
        april.merge(Map.of("BROKERAGE", 15.00));

        // Then
        assertMoney(20.00, april.getFirstHalfCharges().getTotalCharges());
        assertMoney(15.00, april.getSecondHalfCharges().getTotalCharges());
        assertMoney(35.00, april.getTotalCharges());
    }
}
