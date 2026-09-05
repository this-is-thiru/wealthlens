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

    @Field(name = "status", targetType = FieldType.STRING)
    private EntityStatus status;

    @Field("audit_metadata")
    @Setter(value = AccessLevel.NONE)
    private AuditMetadata auditMetadata = new AuditMetadata();
}
