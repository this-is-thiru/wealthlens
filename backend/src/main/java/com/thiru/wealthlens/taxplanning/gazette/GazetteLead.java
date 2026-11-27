package com.thiru.wealthlens.taxplanning.gazette;

import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A Gazette copy that may change a shipped pack, for the owner to read (W-236). A lead, never law: nothing is taken from it
 * until its Gazette copy is read. One per copy — the unique {@code url} makes a second run over the same number add nothing.
 */
@Entity
@Table(name = "tax_gazette_leads")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
public class GazetteLead {

    @Id
    private String id;

    @Column(name = "url")
    private String url;

    @Column(name = "year")
    private int year;

    @Column(name = "number")
    private long number;

    /** The first page naming the terms, counting from 1; 0 where the copy was too large to read. */
    @Column(name = "page")
    private int page;

    /** Child table from the W-337b baseline (V5), not a JSON column: {@code tax_gazette_lead_matched_terms}. */
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @CollectionTable(name = "tax_gazette_lead_matched_terms", joinColumns = @JoinColumn(name = "lead_id"))
    @Column(name = "matched_term")
    private List<String> matchedTerms = new ArrayList<>();

    /**
     * Each shipped rule citing a section the matching pages name, as {@code "2025 s.19 SAL-..."}; for triage only.
     * Child table from the W-337b baseline (V5): {@code tax_gazette_lead_possibly_affected}.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @CollectionTable(name = "tax_gazette_lead_possibly_affected", joinColumns = @JoinColumn(name = "lead_id"))
    @Column(name = "possibly_affected")
    private List<String> possiblyAffected = new ArrayList<>();

    @Column(name = "note")
    private String note;

    @Column(name = "found_at")
    private LocalDateTime foundAt;

    /** Who found it and when (W-337f, D-951 C7a): new, this entity had no audit trail under Mongo. */
    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

    @PrePersist
    void assignIdIfAbsent() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }
}
