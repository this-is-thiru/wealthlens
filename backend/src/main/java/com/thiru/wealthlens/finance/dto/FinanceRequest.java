package com.thiru.wealthlens.finance.dto;

import com.thiru.wealthlens.finance.dto.enums.CalculationType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FinanceRequest {
    @NotNull
    private CalculationType calculationType;
    private double principalAmount;
    private double rateOfInterest;
    private int timePeriod;
    private double finalAmount;
    private double emiAmount;
}
