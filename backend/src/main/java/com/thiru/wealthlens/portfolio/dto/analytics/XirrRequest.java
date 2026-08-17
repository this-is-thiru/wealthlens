package com.thiru.wealthlens.portfolio.dto.analytics;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class XirrRequest {
    private List<MarketPriceEntry> currentPrices;
}
