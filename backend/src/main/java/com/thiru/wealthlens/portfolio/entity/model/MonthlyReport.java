package com.thiru.wealthlens.portfolio.entity.model;

import java.time.Month;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MonthlyReport extends ReportModel {
    private Month month;

    private FortnightReport firstFortnightReport;

    private FortnightReport secondFortnightReport;

    public MonthlyReport(Month month) {
        this.month = month;
    }
}
