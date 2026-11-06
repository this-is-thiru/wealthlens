package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.taxplanning.protection.EncryptedField;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * A computation as it was shown, with the draft revision it read, so a figure can be explained later ({@code DR 19} E6). Its own
 * collection: the legacy module still writes {@code tax_computations} (D-442).
 */
@Data
@NoArgsConstructor
@Document("tax_draft_computations")
public class DraftComputation implements AuditableEntity {

    @MongoId
    private String id;

    @Indexed(unique = true)
    @Field("receipt")
    private String receipt;

    @Field("email")
    private String email;

    @Indexed
    @Field("draft_id")
    private String draftId;

    @Field("revision")
    private int revision;

    @ValueConverter(EncryptedField.class)
    @Field("result_json")
    private String resultJson;

    /** The law and exec layer versions it ran under, in the clear: pack structure, not personal data ({@code DR 14} O7; W-138). */
    @Field("law_and_exec")
    private List<String> lawAndExec;

    /** The checksum, then, of each rule its trace read: what later names the rules that changed under it (W-138). */
    @Field("rule_checksums")
    private Map<String, String> ruleChecksums;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
