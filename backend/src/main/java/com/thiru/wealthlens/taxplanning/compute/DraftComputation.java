package com.thiru.wealthlens.taxplanning.compute;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.shared.entity.model.CreatedWithIdSaved;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A computation as it was shown, with the draft revision it read, so a figure can be explained later ({@code DR 19} E6). Its own
 * collection, {@code tax_draft_computations}: the legacy module held {@code tax_computations} when it was built (D-442).
 */
@Entity
@Table(name = "tax_draft_computations")
@EntityListeners({AuditingEntityListener.class, CreatedWithIdSaved.class})
@Data
@NoArgsConstructor
public class DraftComputation implements CreatedWithId {

    @Id
    private String id;

    @Column(name = "receipt")
    private String receipt;

    @Column(name = "email")
    private String email;

    @Column(name = "draft_id")
    private String draftId;

    @Column(name = "revision")
    private int revision;

    @Column(name = "result_json")
    private String resultJson;

    /**
     * The law and exec layer versions it ran under, in the clear: pack structure, not personal data ({@code DR 14}
     * O7; W-138). Child table, not a JSON column, matching every other plain string list in this module (W-337b's
     * baseline, V5); {@code @OrderColumn} because {@code ComputationReportService} compares this list with
     * {@code equals}, which is order-sensitive.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @CollectionTable(name = "tax_draft_computation_law_and_exec", joinColumns = @JoinColumn(name = "draft_computation_id"))
    @OrderColumn(name = "position")
    @Column(name = "version")
    private List<String> lawAndExec = new ArrayList<>();

    /** The checksum, then, of each rule its trace read: what later names the rules that changed under it (W-138). */
    @Convert(converter = StringMapConverter.class)
    @Column(name = "rule_checksums")
    private Map<String, String> ruleChecksums;

    /**
     * When it was computed, in UTC, from the clock (W-123b): its own field, since the audit's created-at is never filled
     * for an entity whose id is set before saving. Null for one stored before W-123b.
     */
    @Column(name = "computed_at")
    private LocalDateTime computedAt;

    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

    /** Whether this instance is not yet stored (W-329b): never stored, never read back. */
    @Transient
    @JsonIgnore
    private boolean newlyCreated;

    @Override
    public boolean isNew() {
        return newlyCreated || id == null;
    }
}
