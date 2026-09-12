package com.thiru.wealthlens.portfolio.entity.model;
import com.thiru.wealthlens.brokercharges.entity.model.YearlyChargeSummary;
import java.io.Serializable;
import java.time.LocalDateTime;
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

    @Field("last_updated_time")
    @LastModifiedDate
    private LocalDateTime lastUpdatedTime;
}
