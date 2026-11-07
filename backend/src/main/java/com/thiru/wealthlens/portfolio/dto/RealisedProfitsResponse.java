package com.thiru.wealthlens.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.brokercharges.entity.model.YearlyChargeSummary;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RealisedProfitsResponse {

	/**
	 * What the charges engine computed for this period, keyed by charge code, with the months
	 * beneath it.
	 *
	 * <p>Stored on every V2 sell and, until now, returned to nobody: {@code TJsonMapper.safeCopy}
	 * matches by field name and silently drops what the target does not declare, so a field missing
	 * here is not an error anywhere — the endpoint simply answers without it.
	 *
	 * <p>Carries {@code YearlyChargeSummary} itself rather than a parallel response type. The
	 * charge hierarchy is keyed by charge code rather than by fixed columns precisely so that adding
	 * a charge is a data change; a translation layer over it would have nothing to translate and one
	 * more place to forget. The same reasoning already puts {@code ChargeLine} directly on
	 * {@code ChargeBreakdownResponse}.
	 *
	 * <p>Null for a period the engine did not price — a V1 sell, or any trade made before the
	 * engine was switched on, which ADR-32 settles will never be re-driven. Distinct from a period
	 * that genuinely attracted nothing, which carries a summary totalling zero.
	 */
	private YearlyChargeSummary yearlyChargeSummary;

	/**
	 * Every realised amount, split by asset type and segment (D-366). Summable. Mapped by name, so the
	 * field name must keep matching {@code RealisedProfits.gainsByAssetAndSegment}.
	 */
	private Map<AssetType, Map<TradeSegment, FinancialReportResponse>> gainsByAssetAndSegment = new EnumMap<>(AssetType.class);

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime lastUpdatedTime;

}
