package com.thiru.wealthlens.portfolio.entity.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class ReportModel {
    private double purchaseAmount;

    private double sellAmount;

    private double profit;

    private double miscCharges;

    private double brokerage;

    private LocalDateTime lastUpdatedTime;
}
