package com.thiru.wealthlens.brokercharges.entity.model;

import java.time.Month;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One month of charges, split into the two fortnights the reports are drawn against.
 *
 * <p>A half is null until something is charged in it, rather than an empty report: a month whose
 * second fortnight has no trades should read as having none, not as having charged zero.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MonthlyChargeSummary extends ChargeSummaryReport {

    @Field("month")
    private Month month;

    @Field("first_half_charges")
    private ChargeSummaryReport firstHalfCharges;

    @Field("second_half_charges")
    private ChargeSummaryReport secondHalfCharges;

    public MonthlyChargeSummary(Month month) {
        this.month = month;
    }
}
