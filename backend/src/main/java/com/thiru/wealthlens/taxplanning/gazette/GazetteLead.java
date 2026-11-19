package com.thiru.wealthlens.taxplanning.gazette;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * A Gazette copy that may change a shipped pack, for the owner to read (W-236). A lead, never law: nothing is taken from it
 * until its Gazette copy is read. One per copy — the unique {@code url} makes a second run over the same number add nothing.
 */
@Data
@NoArgsConstructor
@Document("tax_gazette_leads")
public class GazetteLead {

    @MongoId
    private String id;

    @Indexed(unique = true)
    @Field("url")
    private String url;

    @Field("year")
    private int year;

    @Field("number")
    private long number;

    /** The first page naming the terms, counting from 1; 0 where the copy was too large to read. */
    @Field("page")
    private int page;

    @Field("matched_terms")
    private List<String> matchedTerms;

    /** Each shipped rule citing a section the matching pages name, as {@code "2025 s.19 SAL-..."}; for triage only. */
    @Field("possibly_affected")
    private List<String> possiblyAffected;

    @Field("note")
    private String note;

    @Field("found_at")
    private LocalDateTime foundAt;
}
