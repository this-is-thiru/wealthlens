package com.thiru.wealthlens.portfolio.entity.model;

import com.thiru.wealthlens.brokercharges.dto.enums.BrokerageAggregatorType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * <b>Superseded.</b> The brokerage block embedded in a superseded rate card.
 *
 * <p>Replaced by a {@code ChargeRule}.
 *
 * <p><b>Do not delete yet.</b> Still reached by live code, and removal waits on human testing of
 * the charges engine — see {@code docs/charges-engine/implementation-checklist.md}, Chunk 11.
 *
 * <p>Plain {@code @Deprecated} rather than {@code forRemoval = true} on purpose. A removal warning
 * is <em>not</em> suppressed at a deprecated use site, so seventeen interlinked classes would warn
 * about each other and bury the only signal worth having. An ordinary deprecation warning is
 * suppressed inside deprecated code, which leaves the build reporting exactly the <b>live</b>
 * callers still to be migrated — and that list reaching zero is the precondition for deleting any
 * of this.
 */
@Deprecated
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrokerageCharges {
    /**
     * Percentage of amount to be deducted for the amount invested by the investor
     */
    @Field("brokerage")
    private double brokerage;

    /**
     * Fixed Amount to be deducted for the amount invested by the investor
     */
    @Field("brokerage_charges")
    private double brokerageCharges;

    /**
     * Gives the info to consider MIN(brokerage,brokerageCharges) or MAX(brokerage,brokerageCharges)
     */
    @Field("brokerage_aggregator")
    private BrokerageAggregatorType brokerageAggregator;

    @Field("minimum_brokerage")
    private double minimumBrokerage;

    @Field("maximum_brokerage")
    private double maximumBrokerage;

}
