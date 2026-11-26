package com.thiru.wealthlens.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.thiru.wealthlens.auth.entity.RefreshToken;
import com.thiru.wealthlens.auth.repository.RefreshTokenRepository;
import com.thiru.wealthlens.insurance.entity.model.PolicyDetails;
import com.thiru.wealthlens.insurance.repository.InsuranceRepository;
import com.thiru.wealthlens.integration.AbstractIntegrationTest;
import com.thiru.wealthlens.shared.privacy.BreachNoticeRepository;
import com.thiru.wealthlens.shared.protection.FieldCipher;
import com.thiru.wealthlens.taxplanning.documents.store.TaxDocumentBytesRepository;
import com.thiru.wealthlens.taxplanning.jobs.JobFailureRepository;
import com.thiru.wealthlens.taxplanning.jobs.JobLeaseRepository;
import com.thiru.wealthlens.taxplanning.profile.AssesseeKind;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRepository;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspace;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspaceRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The copier against both stores (W-337g1, D-953): rows written by hand in the shapes the {@code m1.6.1} classes stored, never
 * from the copier's own output, and each check made to fail once.
 */
class MongoCopierIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final String PROFILE_ID = "65a1b2c3d4e5f60718293a4b";

    @Autowired
    private MongoCopier copier;

    @Autowired
    private FieldCipher cipher;

    @Autowired
    private TaxpayerProfileRepository profiles;

    @Autowired
    private TaxYearWorkspaceRepository workspaces;

    @Autowired
    private JobLeaseRepository leases;

    @Autowired
    private TaxDocumentBytesRepository documentBytes;

    @Autowired
    private JobFailureRepository failures;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private InsuranceRepository insurances;

    @Autowired
    private BreachNoticeRepository breachNotices;

    @Test
    void copyAll_whenDocumentsHoldLists_fillsTheChildTablesTheApplicationReads() {
        // Given — one document of each of W-337g2a1's three, two list entries each
        storeListHolders();

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then — ciphertext carried as stored, so each entity decrypts it
        assertThat(reports).allSatisfy(report -> assertThat(report.problems()).isEmpty());
        RefreshToken token = refreshTokens.findById("token-1").orElseThrow();
        assertThat(token.getFactors()).containsExactlyInAnyOrder("FACTOR_PASSWORD", "FACTOR_OTP");
        assertThat(token.getEmail()).isEqualTo("owner@example.com");
        List<PolicyDetails> details = insurances.findById("insurance-1").orElseThrow().getPolicyDetails();
        assertThat(details).extracting(PolicyDetails::getPolicyId).containsExactlyInAnyOrder("P-1", "P-2");
        assertThat(details).extracting(PolicyDetails::getStartDate).containsExactlyInAnyOrder(LocalDate.of(2026, 4, 1), null);
        assertThat(breachNotices.findById("notice-1").orElseThrow().getAffected())
                .containsExactlyInAnyOrder("owner@example.com", "other@example.com");
    }

    @Test
    void copyAll_whenListsAreCopiedAgain_doublesNoChild() {
        // Given
        storeListHolders();
        copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then
        assertThat(reports).allSatisfy(report -> assertThat(report.problems()).isEmpty());
        assertThat(childCounts()).containsExactly(2L, 2L, 2L);
    }

    @Test
    void copyAll_whenAChildIsRefusedAfterItsParent_writesNeitherAndTheNextRunFinishes() {
        // Given — a policy detail without premium, which insurance_policy_details refuses (NOT NULL) after the insurance row
        storeListHolders();
        mongoTemplate.getCollection("insurances").updateOne(new Document("_id", "insurance-1"),
                new Document("$unset", new Document("policyDetails.1.premium", "")));
        assertThatThrownBy(() -> copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc().queryForList("SELECT id FROM insurances", String.class)).isEmpty();
        assertThat(jdbc().queryForObject("SELECT count(*) FROM insurance_policy_details", Long.class)).isZero();
        mongoTemplate.getCollection("insurances").updateOne(new Document("_id", "insurance-1"),
                new Document("$set", new Document("policyDetails.1.premium", 700.0)));

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then
        assertThat(reports).allSatisfy(report -> assertThat(report.problems()).isEmpty());
        assertThat(childCounts()).containsExactly(2L, 2L, 2L);
    }

    @Test
    void copyAll_whenACopiedChildWasChanged_reportsItsParentsKey() {
        // Given
        storeListHolders();
        copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);
        jdbc().update("UPDATE refresh_token_factors SET factor = 'FACTOR_CHANGED' WHERE factor = 'FACTOR_OTP'");

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then
        assertThat(report(reports, "refresh_tokens").problems()).containsExactly("rows differ from the source: token-1");
    }

    @Test
    void copyAll_whenACopiedChildIsMissing_reportsTheChildCounts() {
        // Given
        storeListHolders();
        copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);
        jdbc().update("DELETE FROM privacy_breach_notice_affected WHERE notice_id = 'notice-1' AND affected = ?",
                cipher.encryptSearchable("other@example.com"));

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then
        assertThat(report(reports, "privacy_breach_notices").problems()).containsExactlyInAnyOrder(
                "rows differ from the source: notice-1", "2 source child rows but 1 target child rows in privacy_breach_notice_affected");
    }

    @Test
    void copyAll_whenFlatRowsAndDocumentBytes_storesWhatTheApplicationReads() {
        // Given
        storeProfile();
        mongoTemplate.getCollection("tax_year_workspaces").insertOne(workspace("ws-1", "65a1b2c3d4e5f60718293a4b", 2026));
        mongoTemplate.getCollection("tax_job_leases").insertOne(new Document("_id", "gazette-watch").append("holder", "host-1")
                .append("expires_at", Date.from(Instant.parse("2026-02-01T10:00:00Z"))));
        mongoTemplate.getCollection("tax_job_failures").insertOne(new Document("_id", "failure-1").append("job", "gazette-watch")
                .append("failed_at", Date.from(Instant.parse("2026-01-31T18:30:00Z"))));
        byte[] sealed = cipher.encryptBytes("%PDF-1.7 a document".getBytes(StandardCharsets.US_ASCII));
        bucket().uploadFromStream("doc-1", new ByteArrayInputStream(sealed));

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then — every source proven, and every time in UTC: auditing held Kolkata's local midnight (stored 18:30Z, its true
        // instant), JobContext.nowUtc() held midnight UTC (stored shifted to 18:30Z through the Kolkata JVM)
        assertThat(reports).hasSize(26).allSatisfy(report -> assertThat(report.problems()).isEmpty());
        TaxpayerProfile profile = profiles.findById(PROFILE_ID).orElseThrow();
        assertThat(profile.getEmail()).isEqualTo("owner@example.com");
        assertThat(profile.getPan()).isEqualTo("ABCDE1234F");
        assertThat(profile.getDateOfBirth()).isEqualTo(LocalDate.of(1980, 5, 17));
        assertThat(profile.getDateOfDeath()).isEqualTo(LocalDate.of(2025, 1, 2));
        assertThat(profile.getKind()).isEqualTo(AssesseeKind.INDIVIDUAL);
        assertThat(profile.isHoldsPortfolio()).isFalse();
        assertThat(profile.getAuditMetadata().getCreatedBy()).isEqualTo("owner@example.com");
        assertThat(profile.getAuditMetadata().getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 31, 18, 30));
        assertThat(failures.findById("failure-1").orElseThrow().getFailedAt()).isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0));
        TaxYearWorkspace workspace = workspaces.findById("ws-1").orElseThrow();
        assertThat(workspace.getTaxYearStart()).isEqualTo(2026);
        assertThat(workspace.getPrimaryDraftId()).isEqualTo("draft-1");
        assertThat(leases.findById("gazette-watch").orElseThrow().getExpiresAt()).isEqualTo(Instant.parse("2026-02-01T10:00:00Z"));
        assertThat(documentBytes.findById("doc-1").orElseThrow().getBytes()).isEqualTo(sealed);
        assertThat(profiles.findByEmail("owner@example.com")).hasSize(1);
    }

    @Test
    void copyAll_whenRunAgain_changesNothing() {
        // Given
        storeProfile();
        copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);
        List<Map<String, Object>> before = jdbc().queryForList("SELECT * FROM tax_profiles");

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then
        assertThat(reports).allSatisfy(report -> assertThat(report.problems()).isEmpty());
        assertThat(jdbc().queryForList("SELECT * FROM tax_profiles")).isEqualTo(before);
    }

    @Test
    void copyAll_whenAWriteFailsPartWay_theNextRunFinishesTheCopy() {
        // Given — ws-b repeats ws-a's profile and year, which tax_year_workspaces_idx refuses after ws-a is written
        mongoTemplate.getCollection("tax_year_workspaces").insertMany(List.of(workspace("ws-a", "profile-1", 2026),
                workspace("ws-b", "profile-1", 2026), workspace("ws-c", "profile-2", 2026)));
        assertThatThrownBy(() -> copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc().queryForList("SELECT id FROM tax_year_workspaces", String.class)).containsExactly("ws-a");
        mongoTemplate.getCollection("tax_year_workspaces").deleteOne(new Document("_id", "ws-b"));

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then
        assertThat(reports).allSatisfy(report -> assertThat(report.problems()).isEmpty());
        assertThat(jdbc().queryForList("SELECT id FROM tax_year_workspaces", String.class)).containsExactlyInAnyOrder("ws-a", "ws-c");
    }

    @Test
    void copyAll_whenTheTargetHoldsARowTheSourceLacks_refusesBeforeWriting() {
        // Given
        mongoTemplate.getCollection("tax_job_failures").insertOne(new Document("_id", "failure-1").append("job", "gazette-watch")
                .append("failed_at", Date.from(Instant.parse("2026-01-31T18:30:00Z"))));
        jdbc().update("INSERT INTO tax_job_failures (id, job) VALUES ('stray', 'gazette-watch')");

        // When / Then
        assertThatThrownBy(() -> copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA)).isInstanceOf(IllegalStateException.class)
                .hasMessage("tax_job_failures holds rows the source lacks: stray; nothing was copied");
        assertThat(jdbc().queryForList("SELECT id FROM tax_job_failures", String.class)).containsExactly("stray");
    }

    @Test
    void copyAll_whenACopiedRowWasChanged_reportsItsKey() {
        // Given
        storeProfile();
        copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);
        jdbc().update("UPDATE tax_profiles SET pan_digest = 'changed' WHERE id = ?", PROFILE_ID);

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then
        assertThat(report(reports, "tax_profiles").problems()).containsExactly("rows differ from the source: " + PROFILE_ID);
    }

    @Test
    void verify_whenASourceRowIsMissingFromTheTarget_reportsItAndTheCounts() {
        // Given — a source row the target never received
        JdbcTemplate jdbc = jdbc();
        TargetTable table = TargetTable.read(jdbc, "tax_job_failures");
        PreparedCopy prepared = new PreparedCopy("tax_job_failures", table,
                List.of(), List.of(new SourceRow("failure-1", Map.of("id", "failure-1", "job", "gazette-watch"))));

        // When
        CopyReport report = copier.verify(jdbc, prepared);

        // Then
        assertThat(report.problems()).contains("missing from the target: failure-1", "1 source rows but 0 target rows");
    }

    @Test
    void copyAll_whenAnEncryptedValueIsNotCiphertext_reportsTheReadBack() {
        // Given — a landlord whose name the cipher cannot open
        mongoTemplate.getCollection("tax_landlords").insertOne(new Document("_id", "landlord-1").append("profile_id", PROFILE_ID)
                .append("name", "not ciphertext"));

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then
        assertThat(report(reports, "tax_landlords").problems()).singleElement().asString().startsWith("Landlord cannot load the copied rows");
    }

    @Test
    void requireKnownSource_whenAnUnknownCollectionHoldsADocument_refuses() {
        // Given
        mongoTemplate.getCollection("tax_policies").insertOne(new Document("_id", "policy-1"));

        // When / Then
        assertThatThrownBy(copier::requireKnownSource).isInstanceOf(IllegalStateException.class)
                .hasMessage("The source holds collections the copier does not know: [tax_policies]; nothing was copied");
    }

    private void storeProfile() {
        mongoTemplate.getCollection("tax_profiles").insertOne(new Document("_id", new ObjectId(PROFILE_ID))
                .append("_class", "com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile")
                .append("email", cipher.encryptSearchable("owner@example.com")).append("pan", cipher.encrypt("ABCDE1234F"))
                .append("pan_digest", "digest-1").append("name", cipher.encrypt("A Person")).append("kind", "INDIVIDUAL")
                .append("date_of_birth", cipher.encrypt("1980-05-17")).append("residential_status", "RESIDENT")
                .append("date_of_death", Date.from(Instant.parse("2025-01-02T00:00:00Z")))
                .append("audit_metadata", new Document("createdBy", cipher.encryptSearchable("owner@example.com"))
                        .append("createdAt", Date.from(Instant.parse("2026-01-31T18:30:00Z")))));
    }

    private void storeListHolders() {
        Date issued = Date.from(Instant.parse("2026-02-01T10:00:00Z"));
        mongoTemplate.getCollection("refresh_tokens").insertOne(new Document("_id", "token-1").append("token_hash", "hash-1")
                .append("family_id", "family-1").append("email", cipher.encryptSearchable("owner@example.com"))
                .append("factors", List.of("FACTOR_PASSWORD", "FACTOR_OTP")).append("issued_at", issued)
                .append("expires_at", Date.from(Instant.parse("2026-02-15T10:00:00Z"))));
        mongoTemplate.getCollection("insurances").insertOne(new Document("_id", "insurance-1").append("_class",
                "com.thiru.wealthlens.insurance.entity.InsuranceEntity").append("email", cipher.encryptSearchable("owner@example.com"))
                .append("premium", 1200.0).append("policyDetails", List.of(
                        new Document("policy_id", cipher.encrypt("P-1")).append("premium", 500.0)
                                .append("start_date", Date.from(Instant.parse("2026-04-01T00:00:00Z"))).append("policy_name", "Term"),
                        new Document("policy_id", cipher.encrypt("P-2")).append("premium", 700.0))));
        mongoTemplate.getCollection("privacy_breach_notices").insertOne(new Document("_id", "notice-1").append("description", "a breach")
                .append("affected", List.of(cipher.encryptSearchable("owner@example.com"), cipher.encryptSearchable("other@example.com"))));
    }

    private List<Long> childCounts() {
        return List.of("refresh_token_factors", "insurance_policy_details", "privacy_breach_notice_affected").stream()
                .map(table -> jdbc().queryForObject("SELECT count(*) FROM " + table, Long.class)).toList();
    }

    private static Document workspace(String id, String profileId, int year) {
        return new Document("_id", id).append("profile_id", profileId).append("tax_year", year).append("primary_draft_id", "draft-1");
    }

    private GridFSBucket bucket() {
        return GridFSBuckets.create(mongoTemplate.getDb(), CollectionCopies.DOCUMENT_BYTES_BUCKET);
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(postgresDataSource);
    }

    private static CopyReport report(List<CopyReport> reports, String table) {
        return reports.stream().filter(report -> report.table().equals(table)).findFirst().orElseThrow();
    }
}
