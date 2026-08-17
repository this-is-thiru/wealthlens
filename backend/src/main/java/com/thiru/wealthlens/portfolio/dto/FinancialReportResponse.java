package com.thiru.wealthlens.portfolio.dto;

import java.time.Month;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(staticName = "empty")
@Data
public class FinancialReportResponse extends ReportModelResponse {
	private Map<Month, MonthlyReportResponse> monthlyReport = new HashMap<>();
}
