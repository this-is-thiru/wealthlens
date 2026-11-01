package com.thiru.wealthlens.taxplanning.profile;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.taxengine.input.FamilySnapshot;
import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import java.time.LocalDate;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * A person in a taxpayer's family, as facts only ({@code DR 15} F2, departing from it by CLAUDE.md §4, D-440): no field or method here
 * states a legal date or a status derived from one; the tax side's rows read {@code dateOfBirth}.
 */
@Data
@NoArgsConstructor
@Document("tax_family_members")
public class FamilyMember implements AuditableEntity, ProfileScoped {

    @MongoId
    private String id;

    @Indexed
    @Field("profile_id")
    private String profileId;

    @Field("name")
    private String name;

    @Field(name = "relationship", targetType = FieldType.STRING)
    private FamilySnapshot.Relationship relationship;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    @Field("date_of_birth")
    private LocalDate dateOfBirth;

    @Field("dependant")
    private boolean dependant;

    @Field(name = "disability", targetType = FieldType.STRING)
    private DisabilitySeverity disability;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    @Field("date_of_death")
    private LocalDate dateOfDeath;

    @Field("multiple_birth_after_first_child")
    private boolean multipleBirthAfterFirstChild;

    @Field("resident_in_india")
    private boolean residentInIndia;

    @Field("pan")
    private String pan;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
