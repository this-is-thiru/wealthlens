package com.thiru.wealthlens.portfolio.entity.model;

import java.time.Month;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor(staticName = "empty")
@Data
public class FinancialReport extends ReportModel {

	/**
	 * This stores P&L for each month (i.e each fortnight)
	 */
    @Field("capital_gains")
	private Map<Month, MonthlyReport> monthlyReport = new HashMap<>();
}
