package com.thiru.wealthlens.taxplanning.compute;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import lombok.Data;
import lombok.NoArgsConstructor;
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

    @Field("result_json")
    private String resultJson;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
