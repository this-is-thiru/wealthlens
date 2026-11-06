package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.taxplanning.protection.EncryptedField;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * A lender: data about someone who is not the user ({@code DR 15} F3; retention is W-232's, {@code DR 14} O5).
 */
@Data
@NoArgsConstructor
@Document("tax_lenders")
public class Lender implements AuditableEntity, ProfileScoped {

    @MongoId
    private String id;

    @Indexed
    @Field("profile_id")
    private String profileId;

    @ValueConverter(EncryptedField.class)
    @Field("name")
    private String name;

    @ValueConverter(EncryptedField.class)
    @Field("pan")
    private String pan;

    @Field(name = "kind", targetType = FieldType.STRING)
    private LenderKind kind;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
