package com.thiru.wealthlens.taxplanning.policy.entity;

import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.taxplanning.enums.RegimeType;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(value = "tax_slab_policies")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TaxSlabPolicyEntity implements AuditableEntity {

    @Id
    private String id;

    @Field("tax_year")
    private String taxYear;

    @Field(name = "regime_type", targetType = FieldType.STRING)
    private RegimeType regimeType;

    @Field(name = "status", targetType = FieldType.STRING)
    private EntityStatus status;

    @Field("source_reference")
    private String sourceReference;

    @Field("standard_deduction")
    private Long standardDeduction;

    @Field("rebate87a_limit")
    private Long rebate87aLimit;

    @Field("rebate87a_amount")
    private Long rebate87aAmount;

    @Field("basic_exemption_limit")
    private Long basicExemptionLimit;

    @Field("slabs")
    private List<TaxSlab> slabs;

    @Field("surcharge_slabs")
    private List<SurchargeSlab> surchargeSlabs;

    @Field("cess_percentage")
    private Double cessPercentage;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TaxSlab {
        @Field("from_amount")
        private Long fromAmount;

        @Field("to_amount")
        private Long toAmount;

        @Field("rate_percent")
        private Double ratePercent;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SurchargeSlab {
        @Field(name = "regime_type", targetType = FieldType.STRING)
        private RegimeType regimeType;

        @Field("from_amount")
        private Long fromAmount;

        @Field("to_amount")
        private Long toAmount;

        @Field("rate_percent")
        private Double ratePercent;

        @Field("marginal_relief_applicable")
        private Boolean marginalReliefApplicable;
    }
}
