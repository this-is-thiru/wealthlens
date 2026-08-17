package com.thiru.wealthlens.portfolio.entity.model;

import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
public class ReportModel {
    @Field("purchase_amount")
    private double purchaseAmount;

    @Field("sell_amount")
    private double sellAmount;

    @Field("profit")
    private double profit;

    @Field("misc_charges")
    private double miscCharges;

    @Field("brokerage")
    private double brokerage;

    @Field(name = "last_updated_time")
    @LastModifiedDate
    private LocalDateTime lastUpdatedTime;
}
