package com.thiru.wealthlens.portfolio.entity.model;

import java.time.Month;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MonthlyReport extends ReportModel {
    private Month month;

    @Field("first_half_report")
    private FortnightReport firstFortnightReport;

    @Field("second_half_report")
    private FortnightReport secondFortnightReport;

    public MonthlyReport(Month month) {
        this.month = month;
    }
}
