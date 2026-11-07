package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * The record that a user asked for one profile to be erased, and when it was (W-235; W-237f C2a′). The hold itself lives on
 * the profile, and the erasure removes the profile, so without this nothing would outlive either: a database restored from a
 * snapshot taken before them would bring the profile back with nothing to say it was erased. Kept a year after the erasure
 * (Rules r.8(3)), as an account's erasure request is.
 */
@Data
@NoArgsConstructor
@Document("tax_profile_erasures")
public class ProfileErasure implements AuditableEntity {

    @MongoId
    private String id;

    @Field("email")
    private String email;

    @Field("profile_id")
    private String profileId;

    /** UTC. */
    @Field("requested_at")
    private LocalDateTime requestedAt;

    /** UTC; absent until the retention purge has erased the profile. */
    @Field("erased_at")
    private LocalDateTime erasedAt;

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();
}
