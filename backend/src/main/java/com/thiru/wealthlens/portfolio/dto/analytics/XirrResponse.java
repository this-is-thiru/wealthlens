package com.thiru.wealthlens.portfolio.dto.analytics;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XirrResponse {
    private Double xirr;
    private Double xirrPercentage;
    private LocalDate calculationDate;
    private Integer cashFlowsCount;
    private Boolean converged;
    private Boolean includesOpenPositions;
    private String message;
}
