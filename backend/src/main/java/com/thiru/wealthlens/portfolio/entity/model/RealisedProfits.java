package com.thiru.wealthlens.portfolio.entity.model;
import com.thiru.wealthlens.brokercharges.entity.model.YearlyChargeSummary;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stored only as JSON inside {@code profit_and_loss.realised_profits_json} / {@code .out_sourced_realised_profits_json}
 * (W-337e, D-949 C3a) — {@code lastUpdatedTime} is stamped by {@code ProfitAndLossService} explicitly, never by
 * Spring Data auditing, which only ever fires for the aggregate root.
 *
 * <p>Deliberately not {@code Serializable}: nothing nested inside it ({@code FinancialReport},
 * {@code YearlyChargeSummary}, …) is, and Hibernate 7 falls back to byte-serializing the whole graph for dirty-checking
 * an attribute-converted type that claims to be — which then fails on the first non-serializable nested class.
 */
@Data
@NoArgsConstructor(staticName = "empty")
public class RealisedProfits {

    /**
     * What the charges engine computed for this period, keyed by charge code.
     *
     * <p>Sits <em>beside</em> {@code yearlyBrokerCharges} rather than replacing it. The old
     * hierarchy has six fixed columns and is fed by the superseded implementation; this one is a map
     * and is fed by the engine, so adding a charge is a data change rather than a schema change.
     * Both are written during the cutover and the old one is deleted in Chunk 11 — replacing it in
     * place would put a cutover and a rewrite in one step.
     */
    private YearlyChargeSummary yearlyChargeSummary;

    /**
     * Every realised amount for the period, split by what was sold and how it was traded — facts, so a
     * reader may sum it, and the tax side decides any term or head of income from them (D-362, D-366).
     * It replaced the short / long pair and the by-classification map (W-160d2). V1 writes purchase,
     * sell, profit, brokerage and misc; V2 purchase and sell (W-169).
     */
    private Map<AssetType, Map<TradeSegment, FinancialReport>> gainsByAssetAndSegment = new EnumMap<>(AssetType.class);

    private LocalDateTime lastUpdatedTime;
}
