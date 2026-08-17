package com.thiru.wealthlens.portfolio.dto.reports.brokerage;

import java.time.Month;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class YearlyBrokerChargesResponse extends BrokerChargesReportResponse {
    private Map<Month, MonthlyBrokerChargesResponse> monthlyReport = new HashMap<>();
}
