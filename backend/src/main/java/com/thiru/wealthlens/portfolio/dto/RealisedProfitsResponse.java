package com.thiru.wealthlens.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RealisedProfitsResponse {
	private FinancialReportResponse shortTermCapitalGains;
	private FinancialReportResponse longTermCapitalGains;

	/**
	 * Every realised amount, partitioned by classification (D8).
	 *
	 * <p>Beside the pair above, not instead of it, so an existing caller sees exactly what it saw
	 * before. Mapped by name through {@code TJsonMapper.safeCopy}, so the field name here must keep
	 * matching {@code RealisedProfits.gainsByClassification}.
	 *
	 * <p><b>Not summable</b> — see {@code CapitalGainsType.countsAsCapitalGains()}.
	 */
	private Map<CapitalGainsType, FinancialReportResponse> gainsByClassification = new EnumMap<>(CapitalGainsType.class);

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime lastUpdatedTime;

}
