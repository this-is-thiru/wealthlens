package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxplanning.gazette.GazetteCursor;
import com.thiru.wealthlens.taxplanning.gazette.GazetteCursorRepository;
import com.thiru.wealthlens.taxplanning.jobs.StalenessSweep;
import com.thiru.wealthlens.taxplanning.jobs.TaxJobRunner;
import com.thiru.wealthlens.taxplanning.workspace.DraftRevision;
import com.thiru.wealthlens.taxplanning.workspace.DraftRevisionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The email of whoever wrote a row, stored encrypted like the identifiers (W-256, D-486): written by auditing and by hand, and
 * refused when found plain. Rows stored before W-256 were sealed by the one-time pass that W-254 removed (runbook §1.r, retired).
 */
class StoredActorEncryptionIntegrationTest extends AbstractIntegrationTest {

    private static final String ACTOR = "actor@example.com";

    @Autowired
    private DraftRevisionRepository revisions;

    @Autowired
    private GazetteCursorRepository cursors;

    @Autowired
    private TaxJobRunner runner;

    @Autowired
    private StalenessSweep stalenessSweep;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private Map<String, Object> raw(String table, String id) {
        return new JdbcTemplate(postgresDataSource).queryForMap("SELECT * FROM " + table + " WHERE id = ?", id);
    }

    @Test
    void save_whenSignedIn_storesEveryActorEncryptedAndReadsThemBackPlain() {
        // Given — a new row, so auditing records both who created it and who last changed it
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(ACTOR, null, List.of()));
        DraftRevision revision = new DraftRevision();
        revision.setId("r1");
        revision.setNewlyCreated(true);
        revision.setDraftId("d1");
        revision.setChangedBy(ACTOR);

        // When
        revisions.save(revision);

        // Then
        Map<String, Object> stored = raw("tax_draft_revisions", revision.getId());
        assertThat(List.of(stored.get("changed_by"), stored.get("created_by"), stored.get("updated_by")))
                .containsOnly(storedIdentifier(ACTOR));
        DraftRevision read = revisions.findById(revision.getId()).orElseThrow();
        assertThat(List.of(read.getChangedBy(), read.getAuditMetadata().getCreatedBy(), read.getAuditMetadata().getUpdatedBy()))
                .containsOnly(ACTOR);
    }

    @Test
    void save_whenTheCursorIsMovedByTheJob_storesTheJobsNameEncrypted() {
        // Given — the job's own name is the authenticated actor, same as auditor.runAs in TaxJobRunner
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("gazette-watch", null, List.of()));
        GazetteCursor cursor = new GazetteCursor();
        cursor.setId(GazetteCursor.ID);

        // When
        cursors.save(cursor);

        // Then
        assertThat(raw("tax_gazette_cursor", GazetteCursor.ID).get("updated_by")).isEqualTo(storedIdentifier("gazette-watch"));
        assertThat(cursors.findById(GazetteCursor.ID).orElseThrow().getAuditMetadata().getUpdatedBy()).isEqualTo("gazette-watch");
    }

    /** A plain actor is refused, not read: the sweep fails rather than act on a row it cannot attribute. */
    @Test
    void stalenessSweep_whenADraftsActorIsStillPlain_failsTheRun() {
        // Given
        new JdbcTemplate(postgresDataSource).update(
                "INSERT INTO tax_filing_drafts (id, workspace_id, profile_id, tax_year_start, status, created_by, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                "d1", "w1", "p1", 2025, "READY", ACTOR, LocalDateTime.now());

        // When / Then
        // Hibernate wraps what a converter throws, so the cipher's refusal is the root cause
        assertThatThrownBy(() -> runner.run(stalenessSweep)).rootCause().isInstanceOf(IllegalStateException.class)
                .hasMessage("A stored field is not encrypted under a known key");
    }
}
