package com.thiru.wealthlens.portfolio.dto.analytics;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioSummaryResponse {
    private Double totalInvested;
    private Double currentPortfolioValue;
    private Double totalRealizedProfit;
    private Double totalUnrealizedProfit;
    private Double overallReturnPercentage;
    private Long totalTrades;
    private Long buyTrades;
    private Long sellTrades;
    private Long holdingCount;
    private LocalDateTime lastUpdated;
}
