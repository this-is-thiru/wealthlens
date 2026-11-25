package com.thiru.wealthlens.taxplanning.workspace;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.shared.entity.model.CreatedWithIdSaved;
import com.thiru.wealthlens.shared.protection.EncryptedColumn;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One version of a year's entries ({@code DR 11} F2). {@code inputsJson} is {@code EnteredFacts} as JSON text, one model with the
 * engine's rather than a second to keep in step (D-441 C1a); text, so no amount is reshaped by storage.
 */
@Entity
@Table(name = "tax_filing_drafts")
@EntityListeners({AuditingEntityListener.class, CreatedWithIdSaved.class})
@Data
@NoArgsConstructor
public class FilingDraft implements CreatedWithId {

    @Id
    private String id;

    @Column(name = "workspace_id")
    private String workspaceId;

    @Column(name = "profile_id")
    private String profileId;

    @Column(name = "tax_year_start")
    private int taxYearStart;

    @Column(name = "label")
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private DraftStatus status;

    @Column(name = "current_revision")
    private int currentRevision;

    @Convert(converter = EncryptedColumn.class)
    @Column(name = "inputs_json")
    private String inputsJson;

    @Column(name = "regime_election")
    private String regimeElection;

    /** When it was acted on, in UTC: a filed draft is kept by this, whatever its status later ({@code DR 14} O5; W-137). */
    @Column(name = "acted_on_at")
    private LocalDateTime actedOnAt;

    /** When it was last archived, in UTC; cleared on restore. What an archived draft's retention runs from (W-137). */
    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    /** Set by the staleness sweep when the law moved under this draft's last computation; cleared by computing again (W-138). */
    @Convert(converter = DraftStalenessConverter.class)
    @Column(name = "staleness")
    private DraftStaleness staleness;

    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

    /** Moves to {@code next}, recording when it was acted on or archived; a draft leaving {@code ARCHIVED} is no longer archived. */
    public void moveTo(DraftStatus next, LocalDateTime atUtc) {
        status = next;
        if (next == DraftStatus.ACTED_ON) {
            actedOnAt = atUtc;
        }
        archivedAt = next == DraftStatus.ARCHIVED ? atUtc : null;
    }

    /** Whether this instance is not yet stored (W-329b): never stored, never read back. */
    @Transient
    @JsonIgnore
    private boolean newlyCreated;

    @Override
    public boolean isNew() {
        return newlyCreated || id == null;
    }
}
