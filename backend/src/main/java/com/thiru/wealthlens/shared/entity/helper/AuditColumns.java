package com.thiru.wealthlens.shared.entity.helper;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

/**
 * JPA counterpart of {@link AuditMetadata} (W-337c1, D-941): embedded in JPA entities with
 * {@code @EntityListeners(AuditingEntityListener.class)} so Spring Data JPA auditing fills creation and update
 * timestamps and actor emails.
 */
@Embeddable
@Data
public class AuditColumns {

    @Column(name = "created_by")
    @CreatedBy
    private String createdBy;

    @Column(name = "updated_by")
    @LastModifiedBy
    private String updatedBy;

    @Column(name = "created_at")
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
