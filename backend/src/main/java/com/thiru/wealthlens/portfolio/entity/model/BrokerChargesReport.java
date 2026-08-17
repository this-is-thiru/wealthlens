package com.thiru.wealthlens.portfolio.entity.model;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
public class BrokerChargesReport {
    @Field("brokerage")
    private double brokerage;

    @Field("account_opening_charges")
    private double accountOpeningCharges;

    @Field("amc_charges")
    private double amcCharges;

    @Field("govt_charges")
    private double govtCharges;

    @Field("taxes")
    private double taxes;

    @Field("dp_charges")
    private double dpCharges;

    @Field(name = "last_updated_time")
    @LastModifiedDate
    private LocalDateTime lastUpdatedTime;
}
