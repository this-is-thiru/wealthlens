package com.thiru.wealthlens.taxplanning.policy.entity;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.taxplanning.enums.AllowanceCategory;
import com.thiru.wealthlens.taxplanning.enums.AvailabilityPath;
import com.thiru.wealthlens.taxplanning.enums.HrSupportLikelihood;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(value = "allowance_catalogue")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AllowanceCatalogueEntity implements AuditableEntity {

    @Id
    private String id;

    @Field("code")
    private String code;

    @Field("display_name")
    private String displayName;

    @Field("short_name")
    private String shortName;

    @Field(name = "category", targetType = FieldType.STRING)
    private AllowanceCategory category;

    @Field("tax_year")
    private String taxYear;

    @Field(name = "regime_type", targetType = FieldType.STRING)
    private RegimeType regimeType;

    @Field("requires_bills")
    private Boolean requiresBills;

    @Field("requires_rent_receipt")
    private Boolean requiresRentReceipt;

    @Field("is_employer_contribution")
    private Boolean isEmployerContribution;

    @Field("annual_limit_fixed")
    private Long annualLimitFixed;

    @Field("monthly_limit_fixed")
    private Long monthlyLimitFixed;

    @Field("limit_formula")
    private String limitFormula;

    @Field("it_section")
    private String itSection;

    @Field("it_act_reference")
    private String itActReference;

    @Field("description")
    private String description;

    @Field("why_it_matters")
    private String whyItMatters;

    @Field(name = "availability_path", targetType = FieldType.STRING)
    private AvailabilityPath availabilityPath;

    @Field(name = "hr_support_likelihood", targetType = FieldType.STRING)
    private HrSupportLikelihood hrSupportLikelihood;

    @Field("how_to_avail_via_hr")
    private String howToAvailViaHr;

    @Field("hr_ask_template")
    private String hrAskTemplate;

    @Field("what_if_hr_says_no")
    private String whatIfHrSaysNo;

    @Field("documents_required")
    private List<String> documentsRequired;

    @Field("documents_to_keep")
    private List<String> documentsToKeep;

    @Field("eligibility_conditions")
    private List<String> eligibilityConditions;

    @Field("common_mistakes")
    private List<String> commonMistakes;

    @Field(name = "status", targetType = FieldType.STRING)
    private EntityStatus status;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();

    @Getter @Setter @ToString @EqualsAndHashCode
    public static class ItrPortalPath {
        @Field("step_number")
        private Integer stepNumber;

        @Field("section_name")
        private String sectionName;

        @Field("field_name")
        private String fieldName;

        @Field("how_to_fill")
        private String howToFill;
    }

    @Getter @Setter @ToString @EqualsAndHashCode
    public static class FaqEntry {
        @Field("question")
        private String question;

        @Field("answer")
        private String answer;
    }
}
