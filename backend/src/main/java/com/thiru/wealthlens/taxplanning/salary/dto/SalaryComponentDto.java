package com.thiru.wealthlens.taxplanning.salary.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SalaryComponentDto {

    private String allowanceCode;
    private Long annualAmount;
    private Boolean isCurrent;
    private String notes;
}
