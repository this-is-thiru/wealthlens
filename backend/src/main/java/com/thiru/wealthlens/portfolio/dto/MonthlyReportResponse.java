package com.thiru.wealthlens.portfolio.dto;

import java.time.Month;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class MonthlyReportResponse extends ReportModelResponse {
	private Month month;
	private ReportModelResponse firstFortnightReport;
	private ReportModelResponse secondFortnightReport;
}
