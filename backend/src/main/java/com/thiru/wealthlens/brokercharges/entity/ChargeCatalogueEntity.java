package com.thiru.wealthlens.brokercharges.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * The registry of valid charge codes.
 *
 * <p>Charge codes are strings rather than an enum, so that adding a charge stays a data change. The
 * cost of that choice is that no compiler checks them, and this collection is what replaces the
 * compiler: a rule naming a code absent from here is rejected when the rate card is written, not
 * when a trade is priced.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(value = "charge_catalogue")
public class ChargeCatalogueEntity implements AuditableEntity {

    @JsonIgnore
    @MongoId
    private String id;

    @Indexed(unique = true)
    @Field("code")
    private String code;

    @Field("display_name")
    private String displayName;

    @Field(name = "category", targetType = FieldType.STRING)
    private ChargeCategory category;

    @Field("description")
    private String description;

    /** The statute, circular or exchange notice the charge derives from. */
    @Field("statutory_reference")
    private String statutoryReference;

    /**
     * Whether this charge may be deducted from a capital gain.
     *
     * <p>Not every charge can. Securities transaction tax is expressly disallowed — the trade-off for
     * the concessional rates on listed equity — and it is the largest charge on a delivery sell, so
     * treating it as deductible inflates the cost base and understates the gain. Account-level
     * charges are not incurred in connection with any particular transfer and are not deductible
     * against one either.
     *
     * <p>Declared here rather than hardcoded where gains are computed, so that adding a charge stays
     * a data change — the property the whole engine is built around. A {@code Boolean} rather than a
     * {@code boolean} on purpose: undeclared must be distinguishable from declared-false, because a
     * code that silently defaults to deductible is the failure this field exists to prevent.
     * {@code ChargeCodes.requireDeductibilityDeclared} refuses an undeclared code at seed time.
     */
    @Field("deductible_for_capital_gains")
    private Boolean deductibleForCapitalGains;

    @Field(name = "status", targetType = FieldType.STRING)
    private EntityStatus status;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();
}
