package com.thiru.wealthlens.taxplanning.workspace;

import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import com.thiru.wealthlens.shared.entity.model.AuditableEntity;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

/**
 * A profile's elections of one regime, append-only ({@code DR 17} L3; W-082d). Holds what the user asserted when a draft was acted
 * on. The withdrawal, lapse and revival branches are W-082e's.
 */
@Data
@NoArgsConstructor
@Document("tax_regime_election_history")
@CompoundIndex(name = "tax_election_regime_idx", def = "{'profile_id': 1, 'regime_code': 1}", unique = true)
public class RegimeElectionHistory implements AuditableEntity {

    @MongoId
    private String id;

    @Field("profile_id")
    private String profileId;

    @Field("regime_code")
    private String regimeCode;

    @Field("events")
    private List<ElectionEvent> events = new ArrayList<>();

    @Field("audit_metadata")
    private AuditMetadata auditMetadata = new AuditMetadata();

    /**
     * An election in a tax year, and the draft that asserted it.
     *
     * @param taxYearStart the year's first calendar year
     * @param sourceDraft the {@code ACTED_ON} draft
     */
    public record ElectionEvent(int taxYearStart, String sourceDraft) {
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
}
