package com.thiru.wealthlens.portfolio.entity.model;

import java.time.Month;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@EqualsAndHashCode(callSuper = true)
public class MonthlyBrokerCharges extends BrokerChargesReport {
    private Month month;

    @Field("first_half_broker_charges")
    private BrokerChargesReport firstHalfBrokerCharges;

    @Field("second_half_broker_charges")
    private BrokerChargesReport secondHalfBrokerCharges;

    public MonthlyBrokerCharges(Month month) {
        this.month = month;
    }
}
