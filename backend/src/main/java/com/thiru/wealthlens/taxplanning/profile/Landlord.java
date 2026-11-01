package com.thiru.wealthlens.taxplanning.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * A landlord: data about someone who is not the user ({@code DR 15} F3; retention is W-232's, {@code DR 14} O5).
 */
@Data
@NoArgsConstructor
@Document("tax_landlords")
public class Landlord implements AuditableEntity, ProfileScoped {

    @MongoId
    private String id;

    @Indexed
    @Field("profile_id")
    private String profileId;

    @Field("name")
    private String name;

    @Field("address")
    private String address;

    @JsonIgnore
    @Field("pan")
    private String pan;

    @Field(name = "relationship_to_taxpayer", targetType = FieldType.STRING)
    private FamilySnapshot.Relationship relationshipToTaxpayer;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();

    /** The PAN as a response shows it: masked, since it is an identifier ({@code DR 14} O1.1). */
    @JsonProperty("maskedPan")
    public String maskedPan() {
        return Pan.maskedOrNull(pan);
    }
}
