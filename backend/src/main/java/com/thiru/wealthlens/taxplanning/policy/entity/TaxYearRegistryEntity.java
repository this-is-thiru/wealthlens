package com.thiru.wealthlens.taxplanning.policy.entity;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(value = "tax_year_registry")
@Data
public class TaxYearRegistryEntity implements AuditableEntity {

    @Id
    private String id;

    @Field("tax_year")
    @Indexed(unique = true)
    private String taxYear;

    @Field("financial_year")
    @Indexed(unique = true)
    private String financialYear;

    @Field("assessment_year")
    @Indexed(unique = true)
    private String assessmentYear;

    @Field("start_date")
    private LocalDate startDate;

    @Field("end_date")
    private LocalDate endDate;

    @Field("governing_act")
    private String governingAct;

    @Field("governing_rules")
    private String governingRules;

    @Field(name = "default_regime", targetType = FieldType.STRING)
    private RegimeType defaultRegime;

    @Field(name = "status", targetType = FieldType.STRING)
    private EntityStatus status;

    @Field("remarks")
    private String remarks;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();
}
