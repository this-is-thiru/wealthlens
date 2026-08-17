package com.thiru.wealthlens.portfolio.dto.analytics;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashFlow {
    private Double amount;
    private LocalDate date;
}
