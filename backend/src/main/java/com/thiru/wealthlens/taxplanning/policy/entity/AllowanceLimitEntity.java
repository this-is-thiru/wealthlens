package com.thiru.wealthlens.taxplanning.policy.entity;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.taxplanning.enums.EmployerType;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(value = "allowance_limits")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AllowanceLimitEntity implements AuditableEntity {

    @Id
    private String id;

    @Field("allowance_code")
    @Indexed
    private String allowanceCode;

    @Field("tax_year")
    @Indexed
    private String taxYear;

    @Field("financial_year")
    private String financialYear;

    @Field("assessment_year")
    private String assessmentYear;

    @Field(name = "regime_type", targetType = FieldType.STRING)
    @Indexed
    private RegimeType regimeType;

    @Field(name = "employer_type", targetType = FieldType.STRING)
    @Indexed
    private EmployerType employerType;

    @Field("annual_limit_fixed")
    private Long annualLimitFixed;

    @Field("monthly_limit_fixed")
    private Long monthlyLimitFixed;

    @Field("limit_formula")
    private String limitFormula;

    @Field("rate_percent")
    private Double ratePercent;

    @Field("requires_documentation")
    private Boolean requiresDocumentation;

    @Field("requires_employer_action")
    private Boolean requiresEmployerAction;

    @Field(name = "status", targetType = FieldType.STRING)
    private EntityStatus status;

    @Field("notified_date")
    private LocalDate notifiedDate;

    @Field("effective_date")
    private LocalDate effectiveDate;

    @Field("source_reference")
    private String sourceReference;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();
}
