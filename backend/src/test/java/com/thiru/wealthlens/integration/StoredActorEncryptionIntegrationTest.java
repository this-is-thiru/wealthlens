package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;

import com.thiru.wealthlens.taxplanning.gazette.GazetteCursor;
import com.thiru.wealthlens.taxplanning.jobs.StalenessSweep;
import com.thiru.wealthlens.taxplanning.jobs.TaxJobRunner;
import com.thiru.wealthlens.taxplanning.workspace.DraftRevision;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The email of whoever wrote a row, stored encrypted like the identifiers (W-256, D-486): written by auditing and by hand, and
 * encrypted in rows stored before it by the one-time endpoint (C3a). The counts are by hand from the rows each test inserts.
 */
class StoredActorEncryptionIntegrationTest extends AbstractIntegrationTest {

    private static final String ACTOR = "actor@example.com";
    private static final String OWNER = "owner@example.com";

    /** Every collection whose entity holds {@code AuditMetadata}, listed by hand from the 36 entities, and the cursor. */
    private static final String[] ACTOR_COLLECTIONS = {"allowance_catalogue", "allowance_limits", "assets", "charge_accounts",
        "charge_catalogue", "charge_instruments", "charge_schedules", "corporate_action", "insurances",
        "lastly_performed_corporate_action", "perquisite_policies", "privacy_breach_intimations", "privacy_breach_notices",
        "privacy_consents", "privacy_erasure_requests", "privacy_grievances", "profit_and_loss", "salary_profiles", "tax_bank_accounts",
        "tax_computations", "tax_draft_computations", "tax_draft_revisions", "tax_employers", "tax_family_members", "tax_filing_drafts",
        "tax_landlords", "tax_lenders", "tax_profile_erasures", "tax_profiles", "tax_regime_election_history", "tax_slab_policies",
        "tax_year_registry", "tax_year_workspaces", "trade_outcomes", "transactions", "user_charges", "tax_gazette_cursor"};

    @Autowired
    private TaxJobRunner runner;

    @Autowired
    private StalenessSweep stalenessSweep;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private ValidatableResponse run(boolean dryRun) {
        return given().header("Authorization", "Bearer " + generateToken(OWNER, "SUPER_USER")).contentType(ContentType.JSON)
                .when().post("/privacy/stored-fields/encrypt?dryRun=" + dryRun).then().statusCode(200);
    }

    private static String counted(String collection, String count) {
        return "collections.find { it.collection == '" + collection + "' }." + count;
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

    @Test
    void encrypt_whenActorsAreStoredPlain_encryptsThemOnceAndLeavesAMissingOneMissing() {
        // Given — one row per kind of actor field, and an audited row no one was recorded against
        mongoTemplate.getCollection("corporate_action").insertMany(List.of(
                new Document("_id", "a1").append("audit_metadata", new Document("createdBy", ACTOR).append("updatedBy", "unknown")),
                new Document("_id", "a2").append("audit_metadata", new Document("createdBy", null))));
        mongoTemplate.getCollection("tax_draft_revisions").insertOne(new Document("_id", "r1").append("changed_by", ACTOR)
                .append("audit_metadata", new Document("createdBy", ACTOR).append("updatedBy", ACTOR)));
        mongoTemplate.getCollection("tax_gazette_cursor").insertOne(new Document("_id", GazetteCursor.ID).append("updated_by", ACTOR));

        // When — a dry run counts and changes nothing; a real run encrypts
        run(true).body(counted("corporate_action", "plainRows"), equalTo(1))
                .body(counted("tax_draft_revisions", "plainRows"), equalTo(1))
                .body(counted("tax_gazette_cursor", "plainRows"), equalTo(1))
                .body(counted("corporate_action", "encryptedRows"), equalTo(0));
        assertThat(audit(raw("corporate_action", "a1")).getString("createdBy")).isEqualTo(ACTOR);
        run(false).body(counted("corporate_action", "encryptedRows"), equalTo(1))
                .body(counted("tax_draft_revisions", "encryptedRows"), equalTo(1))
                .body(counted("tax_gazette_cursor", "encryptedRows"), equalTo(1));

        // Then
        assertThat(audit(raw("corporate_action", "a1")).getString("createdBy")).isEqualTo(storedIdentifier(ACTOR));
        assertThat(audit(raw("corporate_action", "a1")).getString("updatedBy")).isEqualTo(storedIdentifier("unknown"));
        assertThat(audit(raw("corporate_action", "a2")).containsKey("createdBy")).isTrue();
        assertThat(audit(raw("corporate_action", "a2")).getString("createdBy")).isNull();
        Document revision = raw("tax_draft_revisions", "r1");
        assertThat(List.of(revision.getString("changed_by"), audit(revision).getString("createdBy"), audit(revision).getString("updatedBy")))
                .containsOnly(storedIdentifier(ACTOR));
        assertThat(mongoTemplate.findById(GazetteCursor.ID, GazetteCursor.class).getUpdatedBy()).isEqualTo(ACTOR);
        run(false).body(counted("corporate_action", "plainRows"), equalTo(0))
                .body(counted("tax_draft_revisions", "plainRows"), equalTo(0))
                .body(counted("tax_gazette_cursor", "plainRows"), equalTo(0));
    }

    @Test
    void encrypt_whenRun_coversEveryCollectionHoldingAnActor() {
        // When / Then
        run(true).body("collections.collection", hasItems(ACTOR_COLLECTIONS));
    }

    /** Why runbook §1.r's first start keeps the jobs off: the sweep that runs on every start cannot read a plain actor. */
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
