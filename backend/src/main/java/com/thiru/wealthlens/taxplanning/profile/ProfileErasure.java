package com.thiru.wealthlens.taxplanning.profile;

import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.protection.SearchableColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * The record that a user asked for one profile to be erased, and when it was (W-235; W-237f C2a′). The hold itself lives on
 * the profile, and the erasure removes the profile, so without this nothing would outlive either: a database restored from a
 * snapshot taken before them would bring the profile back with nothing to say it was erased. Kept a year after the erasure
 * (Rules r.8(3)), as an account's erasure request is.
 */
@Entity
@Table(name = "tax_profile_erasures")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
public class ProfileErasure {

    @Id
    private String id;

    @Convert(converter = SearchableColumn.class)
    @Column(name = "email")
    private String email;

    @Column(name = "profile_id")
    private String profileId;

    /** UTC. */
    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    /** UTC; absent until the retention purge has erased the profile. */
    @Column(name = "erased_at")
    private LocalDateTime erasedAt;

    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
