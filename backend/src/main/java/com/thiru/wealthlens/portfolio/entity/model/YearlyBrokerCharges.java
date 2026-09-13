package com.thiru.wealthlens.portfolio.entity.model;

import java.time.Month;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@EqualsAndHashCode(callSuper = true)
public class YearlyBrokerCharges extends BrokerChargesReport {
    @Field("monthly_report")
    private Map<Month, MonthlyBrokerCharges> monthlyReport = new HashMap<>();
}
