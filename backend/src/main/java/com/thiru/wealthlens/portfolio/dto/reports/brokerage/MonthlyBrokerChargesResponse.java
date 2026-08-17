package com.thiru.wealthlens.portfolio.dto.reports.brokerage;

import java.time.Month;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MonthlyBrokerChargesResponse extends BrokerChargesReportResponse {
    private Month month;
    private BrokerChargesReportResponse firstHalfBrokerCharges;
    private BrokerChargesReportResponse secondHalfBrokerCharges;
}
