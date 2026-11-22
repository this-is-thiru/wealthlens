package com.thiru.wealthlens.finance.dto;

import com.thiru.wealthlens.finance.dto.enums.StepUpFrequency;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StepUpSIPRequest {
    private double initialAmount;
    private double stepUpRate;
    @NotNull
    private StepUpFrequency stepUpFrequency;
    private int months;
    private double targetAmount;
}
