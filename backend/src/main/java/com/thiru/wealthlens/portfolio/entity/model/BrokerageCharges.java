package com.thiru.wealthlens.portfolio.entity.model;

import com.thiru.wealthlens.brokercharges.dto.enums.BrokerageAggregatorType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

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
