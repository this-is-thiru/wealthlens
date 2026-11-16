package com.thiru.wealthlens.shared.entity.helper;

import com.thiru.wealthlens.shared.protection.SearchableIdentifier;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.convert.ValueConverter;

/**
 * Who created and last changed a record, and when, filled by Spring Data's auditing. Creation is filled only for a record
 * auditing sees as new: an entity given its id before its first save says so through {@code CreatedWithId} (W-329b,
 * D-725), and one of those stored before W-329b has no creation fields, since its creation was never recorded.
 */
@Data
public class AuditMetadata {
    @ValueConverter(SearchableIdentifier.class)
    @CreatedBy
    private String createdBy;
    @ValueConverter(SearchableIdentifier.class)
    @LastModifiedBy
    private String updatedBy;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
