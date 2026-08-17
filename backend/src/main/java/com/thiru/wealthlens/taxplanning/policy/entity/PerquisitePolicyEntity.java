package com.thiru.wealthlens.taxplanning.policy.entity;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(value = "perquisite_policies")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PerquisitePolicyEntity implements AuditableEntity {

    @Id
    private String id;

    @Field("tax_year")
    private String taxYear;

    @Field(name = "status", targetType = FieldType.STRING)
    private EntityStatus status;

    @Field("source_reference")
    private String sourceReference;

    @Field("car_leq1600cc_or_ev_no_driver")
    private Long carLeq1600ccOrEvNoDriver;

    @Field("car_leq1600cc_or_ev_with_driver")
    private Long carLeq1600ccOrEvWithDriver;

    @Field("car_gt1600cc_no_driver")
    private Long carGt1600ccNoDriver;

    @Field("car_gt1600cc_with_driver")
    private Long carGt1600ccWithDriver;

    @Field("driver_perquisite_monthly")
    private Long driverPerquisiteMonthly;

    @Field("meal_per_meal_amount")
    private Long mealPerMealAmount;

    @Field("meal_meals_per_day")
    private Integer mealMealsPerDay;

    @Field("meal_working_days_per_month")
    private Integer mealWorkingDaysPerMonth;

    @Field("gift_annual_limit")
    private Long giftAnnualLimit;

    @Field("concessional_loan_exempt_limit")
    private Long concessionalLoanExemptLimit;

    @Field("accommodation_metro_percent")
    private Double accommodationMetroPercent;

    @Field("accommodation_large_city_percent")
    private Double accommodationLargeCityPercent;

    @Field("accommodation_other_percent")
    private Double accommodationOtherPercent;

    @Field("employer_school_monthly_per_child")
    private Long employerSchoolMonthlyPerChild;

    @Field("car_depreciation_rate_percent")
    private Double carDepreciationRatePercent;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();
}
