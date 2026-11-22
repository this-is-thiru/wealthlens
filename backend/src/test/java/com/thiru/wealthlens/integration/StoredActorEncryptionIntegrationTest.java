package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxplanning.gazette.GazetteCursor;
import com.thiru.wealthlens.taxplanning.jobs.StalenessSweep;
import com.thiru.wealthlens.taxplanning.jobs.TaxJobRunner;
import com.thiru.wealthlens.taxplanning.workspace.DraftRevision;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The email of whoever wrote a row, stored encrypted like the identifiers (W-256, D-486): written by auditing and by hand, and
 * refused when found plain. Rows stored before W-256 were sealed by the one-time pass that W-254 removed (runbook §1.r, retired).
 */
class StoredActorEncryptionIntegrationTest extends AbstractIntegrationTest {

    private static final String ACTOR = "actor@example.com";

    @Autowired
    private TaxJobRunner runner;

    @Autowired
    private StalenessSweep stalenessSweep;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private Document raw(String collection, String id) {
        return mongoTemplate.getCollection(collection).find(new Document("_id", id)).first();
    }

    private static Document audit(Document row) {
        return row.get("audit_metadata", Document.class);
    }

    @Test
    void save_whenSignedIn_storesEveryActorEncryptedAndReadsThemBackPlain() {
        // Given — a new row, so auditing records both who created it and who last changed it
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(ACTOR, null, List.of()));
        DraftRevision revision = new DraftRevision();
        revision.setDraftId("d1");
        revision.setChangedBy(ACTOR);

        // When
        mongoTemplate.save(revision);

        // Then
        Document stored = raw("tax_draft_revisions", revision.getId());
        assertThat(List.of(stored.getString("changed_by"), audit(stored).getString("createdBy"), audit(stored).getString("updatedBy")))
                .containsOnly(storedIdentifier(ACTOR));
        DraftRevision read = mongoTemplate.findById(revision.getId(), DraftRevision.class);
        assertThat(List.of(read.getChangedBy(), read.getAuditMetadata().getCreatedBy(), read.getAuditMetadata().getUpdatedBy()))
                .containsOnly(ACTOR);
    }

    @Test
    void save_whenTheCursorIsMovedByTheJob_storesTheJobsNameEncrypted() {
        // Given
        GazetteCursor cursor = new GazetteCursor();
        cursor.setId(GazetteCursor.ID);
        cursor.setUpdatedBy("gazette-watch");

        // When
        mongoTemplate.save(cursor);

        // Then
        assertThat(raw("tax_gazette_cursor", GazetteCursor.ID).getString("updated_by")).isEqualTo(storedIdentifier("gazette-watch"));
        assertThat(mongoTemplate.findById(GazetteCursor.ID, GazetteCursor.class).getUpdatedBy()).isEqualTo("gazette-watch");
    }

    /** A plain actor is refused, not read: the sweep fails rather than act on a row it cannot attribute. */
    @Test
    void stalenessSweep_whenADraftsActorIsStillPlain_failsTheRun() {
        // Given
        mongoTemplate.getCollection("tax_filing_drafts").insertOne(new Document("_id", "d1").append("status", "READY")
                .append("profile_id", "p1").append("tax_year", 2025).append("audit_metadata", new Document("createdBy", ACTOR)));

        // When / Then
        assertThatThrownBy(() -> runner.run(stalenessSweep)).isInstanceOf(IllegalStateException.class)
                .hasMessage("A stored field is not encrypted under a known key");
    }
}
