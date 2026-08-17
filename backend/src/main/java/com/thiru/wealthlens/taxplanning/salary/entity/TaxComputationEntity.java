package com.thiru.wealthlens.taxplanning.salary.entity;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(value = "tax_computations")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TaxComputationEntity implements AuditableEntity {

    @Id
    private String id;

    @Field("salary_profile_id")
    private String salaryProfileId;

    @Field("email")
    private String email;

    @Field("tax_year")
    private String taxYear;

    @Field("new_regime_result")
    private TaxResult newRegimeResult;

    @Field("old_regime_result")
    private TaxResult oldRegimeResult;

    @Field(name = "recommended_regime", targetType = FieldType.STRING)
    private RegimeType recommendedRegime;

    @Field("annual_saving_vs_current")
    private Long annualSavingVsCurrent;

    @Field("capital_gains_result")
    private Object capitalGainsResult;

    @Field(name = "status", targetType = FieldType.STRING)
    private EntityStatus status;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TaxResult {
        @Field("gross_salary")
        private Long grossSalary;

        @Field("total_exemptions")
        private Long totalExemptions;

        @Field("total_deductions")
        private Long totalDeductions;

        @Field("taxable_income")
        private Long taxableIncome;

        @Field("basic_tax_before_rebate")
        private Long basicTaxBeforeRebate;

        @Field("rebate87a_applied")
        private Long rebate87aApplied;

        @Field("tax_after_rebate")
        private Long taxAfterRebate;

        @Field("marginal_relief_applied")
        private Boolean marginalReliefApplied;

        @Field("surcharge")
        private Long surcharge;

        @Field("cess")
        private Long cess;

        @Field("total_tax")
        private Long totalTax;

        @Field("net_take_home")
        private Long netTakeHome;

        @Field("applied_exemptions")
        private Object appliedExemptions;

        @Field("applied_deductions")
        private Object appliedDeductions;

        @Field("warnings")
        private Object warnings;
    }
}
