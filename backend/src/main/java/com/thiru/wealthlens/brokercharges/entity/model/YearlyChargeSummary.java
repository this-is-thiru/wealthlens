package com.thiru.wealthlens.brokercharges.entity.model;

import com.thiru.wealthlens.shared.util.time.FinancialYear;
import java.time.Month;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * One financial year of charges, with its months beneath it.
 *
 * <p>The year is not derived from the months on read. It carries its own totals, merged alongside
 * them, because the summary is written on the live transaction path and a report should not have to
 * walk twelve nested maps to print one figure.
 *
 * <p>Embedded only inside {@code ProfitAndLossEntity.realisedProfits}, stored as JSON (W-337e, D-949 C3a).
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class YearlyChargeSummary extends ChargeSummaryReport {

    private Map<Month, MonthlyChargeSummary> monthlyReport = new HashMap<>();

    /**
     * The months that carry charges, in financial-year order: April first, March last.
     *
     * <p>{@code Map<Month, …>} iterates in calendar order, so a report rendered straight from it
     * opens in January — three months into the year it is reporting. Offered as an accessor rather
     * than by making the map ordered, because the stored order is the store's business and the reading
     * order is the report's.
     */
    public List<MonthlyChargeSummary> monthsInFinancialYearOrder() {
        return monthlyReport.values().stream()
                .sorted(Comparator.comparingInt(monthly -> FinancialYear.positionOf(monthly.getMonth())))
                .toList();
    }
}
