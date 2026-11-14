package com.thiru.wealthlens.shared.entity.helper;

import com.thiru.wealthlens.shared.protection.SearchableIdentifier;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.convert.ValueConverter;

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
