package com.thiru.wealthlens.portfolio.entity.model;
import com.thiru.wealthlens.brokercharges.entity.model.YearlyChargeSummary;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor(staticName = "empty")
public class RealisedProfits implements Serializable {

    /**
     * What the charges engine computed for this period, keyed by charge code.
     *
     * <p>Sits <em>beside</em> {@code yearlyBrokerCharges} rather than replacing it. The old
     * hierarchy has six fixed columns and is fed by the superseded implementation; this one is a map
     * and is fed by the engine, so adding a charge is a data change rather than a schema change.
     * Both are written during the cutover and the old one is deleted in Chunk 11 — replacing it in
     * place would put a cutover and a rewrite in one step.
     */
    @Field("yearly_charge_summary")
    private YearlyChargeSummary yearlyChargeSummary;

    /**
     * Every realised amount for the period, split by what was sold and how it was traded — facts, so a
     * reader may sum it, and the tax side decides any term or head of income from them (D-362, D-366).
     * It replaced the short / long pair and the by-classification map (W-160d2). V1 writes purchase,
     * sell, profit, brokerage and misc; V2 purchase and sell (W-169).
     */
    @Field("gains_by_asset_and_segment")
    private Map<AssetType, Map<TradeSegment, FinancialReport>> gainsByAssetAndSegment = new EnumMap<>(AssetType.class);

    @Field("last_updated_time")
    @LastModifiedDate
    private LocalDateTime lastUpdatedTime;
}
