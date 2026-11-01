package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.taxplanning.enums.EmployerType;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * An employer, one per TAN within a profile, referenced by id across years ({@code DR 15} F3; D-046 B5).
 */
@Data
@NoArgsConstructor
@Document("tax_employers")
@CompoundIndex(name = "tax_employer_tan_idx", def = "{'profile_id': 1, 'tan': 1}", unique = true)
public class Employer implements AuditableEntity, ProfileScoped {

    @MongoId
    private String id;

    @Field("profile_id")
    private String profileId;

    @Field("name")
    private String name;

    @Field("tan")
    private String tan;

    @Field("pan")
    private String pan;

    @Field(name = "type", targetType = FieldType.STRING)
    private EmployerType type;

    @Field("address")
    private String address;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
