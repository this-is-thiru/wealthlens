package com.thiru.wealthlens.taxplanning.protection;

import com.thiru.wealthlens.shared.protection.SearchableIdentifier;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * One read of special-category data, recorded even though nothing changed ({@code DR 14} O1.1, O4): who, what, when — never a
 * value. Appended only; nothing updates one, and only the erasure of what it names deletes it (W-235 C4a) — the hold before an
 * erasure is the year the Data Protection Rules, 2025 r.6(1)(e) keep such logs.
 */
@Data
@NoArgsConstructor
@Document("tax_access_log")
public class AccessRecord {

    @MongoId
    private String id;

    @Field("principal")
    @ValueConverter(SearchableIdentifier.class)
    private String principal;

    @Field(name = "subject", targetType = FieldType.STRING)
    private ReadSubject subject;

    @Field("subject_id")
    private String subjectId;

    @Field("read_at")
    private Instant readAt;
}
