package com.thiru.wealthlens.taxplanning.salary.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SalaryComponentEntity {

    @Field("allowance_code")
    private String allowanceCode;

    @Field("annual_amount")
    private Long annualAmount;

    @Field("is_current")
    private Boolean isCurrent;

    @Field("notes")
    private String notes;
}
