package com.thiru.wealthlens.taxplanning.profile;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.taxengine.input.Assessee.ResidentialStatus;
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
 * A taxpayer: who a computation is for, with their PAN and the facts the Acts turn on ({@code DR 15} F1). An account owns several
 * (D-046 B2), so a PAN is unique and an email is not. {@code id} is an opaque UUID, the key deep paths carry instead of an email
 * ({@code DR 14} O1.2).
 */
@Data
@NoArgsConstructor
@Document("tax_profiles")
public class TaxpayerProfile implements AuditableEntity {

    @MongoId
    private String id;

    @Indexed
    @Field("email")
    private String email;

    @JsonIgnore
    @Indexed(unique = true)
    @Field("pan")
    private String pan;

    @Field("name")
    private String name;

    @Field(name = "kind", targetType = FieldType.STRING)
    private AssesseeKind kind;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    @Field("date_of_birth")
    private LocalDate dateOfBirth;

    @Field(name = "residential_status", targetType = FieldType.STRING)
    private ResidentialStatus residentialStatus;

    /** Whether the account's portfolio is this person's: its trades belong to one profile, or they would be taxed twice (D-442). */
    @Field("holds_portfolio")
    private boolean holdsPortfolio;

    @Field(name = "disability", targetType = FieldType.STRING)
    private DisabilitySeverity disability;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = TCollectionUtil.DATE_FORMAT)
    @Field("date_of_death")
    private LocalDate dateOfDeath;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();

    /** The PAN as a response shows it: masked, since it is an identifier ({@code DR 14} O1.1). */
    @JsonProperty("maskedPan")
    public String maskedPan() {
        return Pan.maskedOrNull(pan);
    }
}
