package com.thiru.wealthlens.taxplanning.workspace;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.thiru.wealthlens.shared.entity.helper.AuditColumns;
import com.thiru.wealthlens.shared.entity.model.CreatedWithId;
import com.thiru.wealthlens.shared.entity.model.CreatedWithIdSaved;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A profile's elections of one regime, append-only ({@code DR 17} L3; W-082d). Holds what the user asserted when a draft was acted
 * on. The withdrawal, lapse and revival branches are W-082e's.
 */
@Entity
@Table(name = "tax_regime_election_history")
@EntityListeners({AuditingEntityListener.class, CreatedWithIdSaved.class})
@Data
@NoArgsConstructor
public class RegimeElectionHistory implements CreatedWithId {

    @Id
    private String id;

    @Column(name = "profile_id")
    private String profileId;

    @Column(name = "regime_code")
    private String regimeCode;

    /** Child table from the W-337b baseline (V5), not a JSON column: {@code tax_regime_election_events}. */
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SELECT)
    @CollectionTable(name = "tax_regime_election_events", joinColumns = @JoinColumn(name = "history_id"))
    private List<ElectionEvent> events = new ArrayList<>();

    @Embedded
    private AuditColumns auditMetadata = new AuditColumns();

    /**
     * An election in a tax year, and the draft that asserted it.
     *
     * @param taxYearStart the year's first calendar year
     * @param sourceDraft the {@code ACTED_ON} draft
     */
    @Embeddable
    public record ElectionEvent(@Column(name = "tax_year_start") int taxYearStart, @Column(name = "source_draft") String sourceDraft) {
    }

    /** Whether an election stands recorded by {@code year}; what state that makes it is W-082e's, on the engine's rows. */
    public boolean isElectedBy(TaxYearId year) {
        return events.stream().anyMatch(event -> event.taxYearStart() <= year.startYear());
    }

    /** Records the election unless this year already holds one, so a draft acted on twice adds one event. */
    public void elect(int taxYearStart, String sourceDraft) {
        if (events.stream().noneMatch(event -> event.taxYearStart() == taxYearStart)) {
            events.add(new ElectionEvent(taxYearStart, sourceDraft));
        }
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
