package com.thiru.wealthlens.shared.dto.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.util.time.TLocalDateTime;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuditMetadataDto extends AuditMetadata {

    @JsonFormat(pattern = TLocalDateTime.COMPLETE_DATE_TIME_FORMAT)
    @Override
    public LocalDateTime getUpdatedAt() {
        return super.getUpdatedAt();
    }

    @JsonFormat(pattern = TLocalDateTime.COMPLETE_DATE_TIME_FORMAT)
    @Override
    public LocalDateTime getCreatedAt() {
        return super.getCreatedAt();
    }
}
