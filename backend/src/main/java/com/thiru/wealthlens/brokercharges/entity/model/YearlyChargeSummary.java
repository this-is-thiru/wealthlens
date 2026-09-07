package com.thiru.wealthlens.brokercharges.entity.model;

import java.time.Month;
import java.util.HashMap;
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
}
