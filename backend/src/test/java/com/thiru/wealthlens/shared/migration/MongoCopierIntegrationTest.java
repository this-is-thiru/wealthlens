package com.thiru.wealthlens.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.thiru.wealthlens.auth.entity.RefreshToken;
import com.thiru.wealthlens.auth.repository.RefreshTokenRepository;
import com.thiru.wealthlens.brokercharges.entity.ChargeAccountEntity.BillingEvent;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeAccountRepository;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import com.thiru.wealthlens.corporate.entity.model.DemergerDetail;
import com.thiru.wealthlens.corporate.entity.model.DemergerDetail.DemergerStock;
import com.thiru.wealthlens.corporate.repository.CorporateActionRepository;
import com.thiru.wealthlens.insurance.entity.model.PolicyDetails;
import com.thiru.wealthlens.insurance.repository.InsuranceRepository;
import com.thiru.wealthlens.integration.AbstractIntegrationTest;
import com.thiru.wealthlens.shared.privacy.BreachNoticeRepository;
import com.thiru.wealthlens.shared.protection.FieldCipher;
import com.thiru.wealthlens.taxplanning.documents.store.StoredConfirmation;
import com.thiru.wealthlens.taxplanning.documents.store.StoredConfirmationRepository;
import com.thiru.wealthlens.taxplanning.documents.store.TaxDocumentBytesRepository;
import com.thiru.wealthlens.taxplanning.gazette.GazetteLead;
import com.thiru.wealthlens.taxplanning.gazette.GazetteLeadRepository;
import com.thiru.wealthlens.taxplanning.jobs.JobFailureRepository;
import com.thiru.wealthlens.taxplanning.jobs.JobLeaseRepository;
import com.thiru.wealthlens.taxplanning.profile.AssesseeKind;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRepository;
import com.thiru.wealthlens.taxplanning.workspace.RegimeElectionHistory.ElectionEvent;
import com.thiru.wealthlens.taxplanning.workspace.RegimeElectionHistoryRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

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

    @Autowired
    private ChargeAccountRepository chargeAccounts;

    @Autowired
    private UserChargeRepository userCharges;

    @Autowired
    private CorporateActionRepository corporateActions;

    @Autowired
    private RegimeElectionHistoryRepository electionHistories;

    @Autowired
    private StoredConfirmationRepository confirmations;

    @Autowired
    private GazetteLeadRepository leads;

    @Test
    void copyAll_whenTaxDocumentsHoldLists_fillsTheChildTablesTheApplicationReads() {
        // Given — one document of each of W-337g2a3's three, and a lead with neither list
        storeTaxListHolders();

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then — ElectionEvent's Java-named keys reach their snake_case columns
        assertThat(reports).allSatisfy(report -> assertThat(report.problems()).isEmpty());
        assertThat(electionHistories.findById("history-1").orElseThrow().getEvents()).containsExactlyInAnyOrder(
                new ElectionEvent(2025, "draft-1"), new ElectionEvent(2026, "draft-2"));
        StoredConfirmation confirmation = confirmations.findById("attempt-1").orElseThrow();
        assertThat(confirmation.getRejectedFields()).containsExactlyInAnyOrder("gross_salary", "tds");
        assertThat(confirmation.getConfirmedBy()).isEqualTo("owner@example.com");
        GazetteLead lead = leads.findById("lead-1").orElseThrow();
        assertThat(lead.getMatchedTerms()).containsExactlyInAnyOrder("income-tax", "section 80C");
        assertThat(lead.getPossiblyAffected()).containsExactly("RS-CH6A-80C");
        assertThat(leads.findById("lead-2").orElseThrow().getMatchedTerms()).isEmpty();
    }

    @Test
    void copyAll_whenTaxListsAreCopiedAgain_doublesNoChild() {
        // Given
        storeTaxListHolders();
        copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then
        assertThat(reports).allSatisfy(report -> assertThat(report.problems()).isEmpty());
        assertThat(List.of("tax_regime_election_events", "tax_document_confirmation_rejected_fields", "tax_gazette_lead_matched_terms",
                "tax_gazette_lead_possibly_affected").stream()
                .map(table -> jdbc().queryForObject("SELECT count(*) FROM " + table, Long.class)).toList())
                .containsExactly(2L, 2L, 2L, 1L);
    }

    @Test
    void copyAll_whenOrderedListsMapsAndEmbeddedDetail_storesWhatTheApplicationReads() {
        // Given — one document of each of W-337g2a2's three, and a corporate action with no demerger detail
        storeChargeAndActionHolders();

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then
        assertThat(reports).allSatisfy(report -> assertThat(report.problems()).isEmpty());
        assertThat(chargeAccounts.findById("account-1").orElseThrow().getBillingEvents()).extracting(BillingEvent::getUserChargeId,
                BillingEvent::getPeriodFrom, BillingEvent::getAmount).containsExactlyInAnyOrder(
                        tuple("uc-1", LocalDate.of(2026, 1, 1), 88.5),
                        tuple("uc-2", null, 0.0));
        UserChargeEntity charge = userCharges.findById("uc-1").orElseThrow();
        assertThat(charge.getLines()).extracting(ChargeLine::getCode).containsExactly("BROKERAGE", "STT", "GST");
        assertThat(charge.getLines().get(1).getRate()).isEqualTo(0.001);
        assertThat(charge.getAmountByCode()).containsExactlyInAnyOrderEntriesOf(Map.of("BROKERAGE", 20.0, "STT", 10.0));
        DemergerDetail detail = corporateActions.findById("ca-1").orElseThrow().getDemergerDetail();
        assertThat(detail.getDemergerRatio()).isEqualTo("1:1");
        assertThat(detail.getMainStockCode()).isEqualTo("MAIN");
        assertThat(detail.getDemergerStocks()).extracting(DemergerStock::getStockCode).containsExactlyInAnyOrder("NEW1", "NEW2");
        assertThat(corporateActions.findById("ca-2").orElseThrow().getDemergerDetail()).isNull();
    }

    @Test
    void copyAll_whenOrderedListsAndMapsAreCopiedAgain_doublesNoChild() {
        // Given
        storeChargeAndActionHolders();
        copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then
        assertThat(reports).allSatisfy(report -> assertThat(report.problems()).isEmpty());
        assertThat(List.of("charge_account_billing_events", "user_charge_lines", "user_charge_amounts",
                "corporate_action_demerger_stocks").stream()
                .map(table -> jdbc().queryForObject("SELECT count(*) FROM " + table, Long.class)).toList())
                .containsExactly(2L, 3L, 2L, 2L);
    }

    @Test
    void copyAll_whenTwoCopiedLinesWereReordered_reportsTheUserCharge() {
        // Given
        storeChargeAndActionHolders();
        copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);
        jdbc().update("UPDATE user_charge_lines SET line_order = 1 - line_order WHERE line_order IN (0, 1)");

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then
        assertThat(report(reports, "user_charges").problems()).containsExactly("rows differ from the source: uc-1");
    }

    @Test
    void copyAll_whenACopiedMapAmountWasChanged_reportsTheUserCharge() {
        // Given
        storeChargeAndActionHolders();
        copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);
        jdbc().update("UPDATE user_charge_amounts SET amount = 99 WHERE charge_code = 'STT'");

        // When
        List<CopyReport> reports = copier.copyAll(CollectionCopies.SPECIFIED, KOLKATA);

        // Then
        assertThat(report(reports, "user_charges").problems()).containsExactly("rows differ from the source: uc-1");
    }

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

    private List<Map<String, Object>> seededCatalogue;

    /**
     * cleanDatabase keeps charge_catalogue between classes as reference data, so a class that seeded it leaves rows the copier
     * rightly refuses ("holds rows the source lacks"); each test here starts from a fresh target and puts the rows back after.
     */
    @BeforeEach
    void setAsideTheSeededCatalogue() {
        seededCatalogue = jdbc().queryForList("SELECT * FROM charge_catalogue");
        jdbc().update("DELETE FROM charge_catalogue");
    }

    @AfterEach
    void restoreTheSeededCatalogue() {
        jdbc().update("DELETE FROM charge_catalogue");
        SimpleJdbcInsert insert = new SimpleJdbcInsert(postgresDataSource).withTableName("charge_catalogue");
        seededCatalogue.forEach(insert::execute);
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
        assertThat(reports).hasSize(32).allSatisfy(report -> assertThat(report.problems()).isEmpty());
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

    private void storeChargeAndActionHolders() {
        mongoTemplate.getCollection("charge_accounts").insertOne(new Document("_id", "account-1")
                .append("email", cipher.encryptSearchable("owner@example.com")).append("billing_events", List.of(
                        new Document("user_charge_id", "uc-1").append("period_from", Date.from(Instant.parse("2026-01-01T00:00:00Z")))
                                .append("amount", 88.5),
                        new Document("user_charge_id", "uc-2").append("amount", 0.0))));
        mongoTemplate.getCollection("user_charges").insertOne(new Document("_id", "uc-1")
                .append("email", cipher.encryptSearchable("owner@example.com")).append("transaction_id", "txn-1")
                .append("computed_on", Date.from(Instant.parse("2026-01-31T18:30:00Z"))).append("total_charges", 33.6)
                .append("lines", List.of(new Document("code", "BROKERAGE").append("base_amount", 50000.0).append("amount", 20.0).append("taxable", true),
                        new Document("code", "STT").append("rate", 0.001).append("base_amount", 10000.0).append("amount", 10.0).append("taxable", false),
                        new Document("code", "GST").append("category", "TAX").append("base_amount", 20.0).append("amount", 3.6).append("taxable", false)))
                .append("amount_by_code", new Document("BROKERAGE", 20.0).append("STT", 10.0)));
        mongoTemplate.getCollection("corporate_action").insertMany(List.of(new Document("_id", "ca-1").append("stock_code", "MAIN")
                .append("type", "DEMERGER").append("demerger_detail", new Document("demerger_ratio", "1:1")
                        .append("demerger_price_ratio", "70:30").append("main_stock_code", "MAIN").append("main_stock_name", "Main Ltd")
                        .append("demerger_stocks", List.of(new Document("stock_code", "NEW1").append("stock_name", "New One"),
                                new Document("stock_code", "NEW2").append("stock_name", "New Two")))),
                new Document("_id", "ca-2").append("stock_code", "OTHER").append("type", "BONUS")));
    }

    private void storeTaxListHolders() {
        mongoTemplate.getCollection("tax_regime_election_history").insertOne(new Document("_id", "history-1")
                .append("profile_id", PROFILE_ID).append("regime_code", "OLD").append("events", List.of(
                        new Document("taxYearStart", 2025).append("sourceDraft", "draft-1"),
                        new Document("taxYearStart", 2026).append("sourceDraft", "draft-2"))));
        mongoTemplate.getCollection("tax_document_confirmations").insertOne(new Document("_id", "attempt-1")
                .append("confirmed_by", cipher.encryptSearchable("owner@example.com"))
                .append("confirmed_at", Date.from(Instant.parse("2026-02-01T10:00:00Z")))
                .append("accepted_json", cipher.encrypt("{}")).append("rejected_fields", List.of("gross_salary", "tds")));
        mongoTemplate.getCollection("tax_gazette_leads").insertMany(List.of(new Document("_id", "lead-1")
                .append("url", "https://egazette.gov.in/one").append("year", 2026).append("number", 12L).append("page", 3)
                .append("matched_terms", List.of("income-tax", "section 80C")).append("possibly_affected", List.of("RS-CH6A-80C"))
                .append("found_at", Date.from(Instant.parse("2026-02-01T10:00:00Z"))),
                new Document("_id", "lead-2").append("url", "https://egazette.gov.in/two").append("year", 2026).append("number", 13L)
                        .append("page", 1)));
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
