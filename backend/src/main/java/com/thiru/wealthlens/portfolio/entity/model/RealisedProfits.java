package com.thiru.wealthlens.portfolio.entity.model;
import com.thiru.wealthlens.brokercharges.entity.model.YearlyChargeSummary;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
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

    @Field("short_term_capital_gains")
	private FinancialReport shortTermCapitalGains;

    @Field("long_term_capital_gains")
	private FinancialReport longTermCapitalGains;


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
     * Every realised amount for the period, partitioned by how the disposal was classified
     * (decision D8).
     *
     * <p>Sits <em>beside</em> the two named fields above rather than replacing them, the same
     * cutover shape {@code yearlyChargeSummary} uses beside {@code yearlyBrokerCharges}: the pair
     * is still written exactly as before, so no existing reader changes, and it goes when V1 goes.
     * V1 writes only the pair; the V2 sell writes both.
     *
     * <p>Two fields cannot hold five classifications, and forcing them is the defect this replaces
     * — an unknown and a fixed deposit's interest were both counted as short-term capital gains.
     *
     * <p><b>Do not sum this map.</b> The partition is complete, which is what makes it reconcile
     * against total proceeds — and also what makes the sum wrong: speculative income, a different
     * head of income, and an outright unknown are all in here. Ask
     * {@link com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType#countsAsCapitalGains()}.
     */
    @Field("gains_by_classification")
    private Map<CapitalGainsType, FinancialReport> gainsByClassification = new EnumMap<>(CapitalGainsType.class);

    @Field("last_updated_time")
    @LastModifiedDate
    private LocalDateTime lastUpdatedTime;
}
