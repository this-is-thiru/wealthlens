package com.thiru.wealthlens.brokercharges.entity.model;

import java.time.Month;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One financial year of charges, with its months beneath it.
 *
 * <p>The year is not derived from the months on read. It carries its own totals, merged alongside
 * them, because the summary is written on the live transaction path and a report should not have to
 * walk twelve nested maps to print one figure.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class YearlyChargeSummary extends ChargeSummaryReport {

    @Field("monthly_report")
    private Map<Month, MonthlyChargeSummary> monthlyReport = new HashMap<>();

    /**
     * The months that carry charges, in financial-year order: April first, March last.
     *
     * <p>{@code Map<Month, …>} iterates in calendar order, so a report rendered straight from it
     * opens in January — three months into the year it is reporting. Offered as an accessor rather
     * than by making the map ordered, because the stored order is Mongo's business and the reading
     * order is the report's.
     */
    public List<MonthlyChargeSummary> monthsInFinancialYearOrder() {
        return monthlyReport.values().stream()
                .sorted(Comparator.comparingInt(YearlyChargeSummary::financialYearPosition))
                .toList();
    }

    /** April is month 1 of a financial year; March is month 12. */
    private static int financialYearPosition(MonthlyChargeSummary monthly) {
        return (monthly.getMonth().getValue() - Month.APRIL.getValue() + 12) % 12;
    }
}
