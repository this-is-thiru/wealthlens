package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.compute.DraftComputation;
import com.thiru.wealthlens.taxplanning.compute.DraftComputationRepository;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentConfirmation;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import com.thiru.wealthlens.taxplanning.documents.ingest.IngestedDocument;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParserRun;
import com.thiru.wealthlens.taxplanning.documents.parser.DocumentContent;
import com.thiru.wealthlens.taxplanning.documents.store.DocumentBytes;
import com.thiru.wealthlens.taxplanning.documents.store.StoredConfirmation;
import com.thiru.wealthlens.taxplanning.documents.store.StoredConfirmationRepository;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocument;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocumentRepository;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttemptRepository;
import com.thiru.wealthlens.taxplanning.documents.store.TaxDocumentBytesRepository;
import com.thiru.wealthlens.taxplanning.jobs.AutoArchive;
import com.thiru.wealthlens.taxplanning.jobs.JobContext;
import com.thiru.wealthlens.taxplanning.jobs.JobFailureRepository;
import com.thiru.wealthlens.taxplanning.jobs.JobLease;
import com.thiru.wealthlens.taxplanning.jobs.JobLeaseRepository;
import com.thiru.wealthlens.taxplanning.jobs.RetentionPurge;
import com.thiru.wealthlens.taxplanning.jobs.StalenessSweep;
import com.thiru.wealthlens.taxplanning.jobs.TaxJobRunner;
import com.thiru.wealthlens.taxplanning.profile.Landlord;
import com.thiru.wealthlens.taxplanning.profile.LandlordRepository;
import com.thiru.wealthlens.taxplanning.profile.Lender;
import com.thiru.wealthlens.taxplanning.profile.LenderRepository;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRepository;
import com.thiru.wealthlens.taxplanning.workspace.DraftRevision;
import com.thiru.wealthlens.taxplanning.workspace.DraftRevisionRepository;
import com.thiru.wealthlens.taxplanning.workspace.DraftStaleness;
import com.thiru.wealthlens.taxplanning.workspace.DraftStatus;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraftRepository;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspace;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspaceRepository;
import com.thiru.wealthlens.testsupport.TestPdfs;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auto-archive and retention purge over PostgreSQL (W-137, W-136; D-449; ported off Mongo W-337f, D-951), on a
 * clock the test sets. Every expected date is worked by hand in {@code docs/modules/taxplanning.md} §11 (W-137):
 * 18 months before 2026-11-06 is 2025-05-06; 12 months before it is 2025-11-06; tax year 2025-26 ends 31-3-2026,
 * and its eight succeeding years end 31-3-2034.
 */
class TaxJobsIntegrationTest extends AbstractIntegrationTest {

    /** A clock each test sets, so a run can be placed on either side of a boundary. */
    static final class SettableClock extends Clock {

        private Instant instant = Instant.parse("2026-11-06T06:30:00Z");
        private ZoneId zone = ZoneOffset.UTC;

        void set(String isoInstant) {
            instant = Instant.parse(isoInstant);
        }

        /** The zone the clock reads in: the application's clock is in the business zone (D-958). */
        void inZone(ZoneId readIn) {
            zone = readIn;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class SetDay {

        @Bean
        @Primary
        SettableClock settableClock() {
            return new SettableClock();
        }
    }

    @Autowired
    private SettableClock clock;

    @Autowired
    private TaxJobRunner runner;

    @Autowired
    private AutoArchive autoArchive;

    @Autowired
    private RetentionPurge retentionPurge;

    @Autowired
    private JobFailureRepository failures;

    @Autowired
    private JobLeaseRepository leases;

    @Autowired
    private StalenessSweep stalenessSweep;

    @Autowired
    private TaxCalculator taxCalculator;

    @Autowired
    private DocumentBytes documentBytes;

    @Autowired
    private TaxDocumentBytesRepository documentBytesRepository;

    @Autowired
    private TaxYearWorkspaceRepository workspaces;

    @Autowired
    private FilingDraftRepository drafts;

    @Autowired
    private DraftRevisionRepository revisions;

    @Autowired
    private DraftComputationRepository computations;

    @Autowired
    private StoredDocumentRepository documents;

    @Autowired
    private StoredParseAttemptRepository attempts;

    @Autowired
    private StoredConfirmationRepository confirmations;

    @Autowired
    private LandlordRepository landlords;

    @Autowired
    private LenderRepository lenders;

    @Autowired
    private TaxpayerProfileRepository profiles;

    @BeforeEach
    void theDayOfTheHandFigures() {
        clock.set("2026-11-06T06:30:00Z");
        clock.inZone(ZoneOffset.UTC);
    }

    private void workspace(String id, String primaryDraftId) {
        TaxYearWorkspace workspace = new TaxYearWorkspace();
        workspace.setId(id);
        workspace.setProfileId("profile-1");
        workspace.setTaxYearStart(2025);
        workspace.setPrimaryDraftId(primaryDraftId);
        workspaces.save(workspace);
    }

    private void draft(String id, DraftStatus status, int taxYear, String lastChanged) {
        FilingDraft draft = new FilingDraft();
        draft.setId(id);
        draft.setWorkspaceId("workspace-1");
        draft.setProfileId("profile-1");
        draft.setTaxYearStart(taxYear);
        draft.setLabel(id);
        draft.setStatus(status);
        draft.setInputsJson("{}");
        drafts.save(draft);
        setColumn("tax_filing_drafts", "updated_at", id, LocalDateTime.parse(lastChanged));
    }

    /** Sets a column directly by JDBC, bypassing JPA auditing, so a row can be given a historical timestamp. */
    private void setColumn(String table, String column, String id, Object value) {
        new JdbcTemplate(postgresDataSource).update("UPDATE " + table + " SET " + column + " = ? WHERE id = ?", value, id);
    }

    private void set(String draftId, String column, LocalDateTime value) {
        setColumn("tax_filing_drafts", column, draftId, value);
    }

    private FilingDraft stored(String id) {
        return drafts.findById(id).orElse(null);
    }

    private void landlord(String id, String profileId, String lastChanged) {
        Landlord landlord = new Landlord();
        landlord.setId(id);
        landlord.setProfileId(profileId);
        landlord.setName("A landlord");
        landlord.setPan("ABCPL1234K");
        landlords.save(landlord);
        setColumn("tax_landlords", "updated_at", id, LocalDateTime.parse(lastChanged));
    }

    private void lender(String id, String profileId, String lastChanged) {
        Lender lender = new Lender();
        lender.setId(id);
        lender.setProfileId(profileId);
        lender.setName("A bank");
        lenders.save(lender);
        setColumn("tax_lenders", "updated_at", id, LocalDateTime.parse(lastChanged));
    }

    private void draftOf(String id, String profileId, int taxYear) {
        draft(id, DraftStatus.DRAFT, taxYear, "2026-10-01T12:00:00");
        FilingDraft draft = stored(id);
        draft.setProfileId(profileId);
        drafts.save(draft);
    }

    /** Has the stored draft name {@code landlordId} and, if given, {@code lenderId} (W-239a). */
    private void naming(String draftId, String landlordId, String... lenderIds) {
        FilingDraft draft = stored(draftId);
        draft.setInputsJson("{\"thirdParties\":{\"landlordIds\":[\"" + landlordId + "\"],\"lenderIds\":["
                + String.join(",", Arrays.stream(lenderIds).map(id -> "\"" + id + "\"").toList()) + "]}}");
        drafts.save(draft);
    }

    /** The actor as the row holds it, past the converter (W-256). */
    private String rawUpdatedBy(String table, String id) {
        return (String) new JdbcTemplate(postgresDataSource).queryForMap("SELECT * FROM " + table + " WHERE id = ?", id)
                .get("updated_by");
    }

    @Test
    void autoArchive_whenADraftWasUntouchedForEighteenMonths_archivesItButNotThePrimaryOrAReadyOne() {
        // Given — run on 2026-11-06T06:30Z; the cutoff is 2025-05-06T06:30
        workspace("workspace-1", "primary");
        draft("old", DraftStatus.DRAFT, 2025, "2025-05-05T12:00:00");
        draft("recent", DraftStatus.DRAFT, 2025, "2025-05-07T12:00:00");
        draft("primary", DraftStatus.DRAFT, 2025, "2025-05-05T12:00:00");
        draft("ready", DraftStatus.READY, 2025, "2025-05-05T12:00:00");

        // When
        boolean ran = runner.run(autoArchive);

        // Then
        assertThat(ran).isTrue();
        assertThat(stored("old").getStatus()).isEqualTo(DraftStatus.ARCHIVED);
        assertThat(stored("old").getArchivedAt()).isEqualTo(LocalDateTime.parse("2026-11-06T06:30:00"));
        assertThat(stored("recent").getStatus()).isEqualTo(DraftStatus.DRAFT);
        assertThat(stored("primary").getStatus()).isEqualTo(DraftStatus.DRAFT);
        assertThat(stored("ready").getStatus()).isEqualTo(DraftStatus.READY);
        assertThat(leases.findAll()).isEmpty();
        assertThat(stored("old").getAuditMetadata().getUpdatedBy()).isEqualTo("auto-archive");
        assertThat(rawUpdatedBy("tax_filing_drafts", "old")).isEqualTo("auto-archive");
    }

    @Test
    void autoArchive_whenTheClockReadsInKolkata_takesTheCutoffInUtc() {
        // Given — run at 2026-11-06T06:30Z, 12:00 in Kolkata. updated_at is stored in UTC (W-402), so the cutoff is
        // 2025-05-06T06:30 UTC; taken in Kolkata's time it would be 2025-05-06T12:00, which "between" (09:00 UTC) precedes
        clock.inZone(ZoneId.of("Asia/Kolkata"));
        workspace("workspace-1", "primary");
        draft("old", DraftStatus.DRAFT, 2025, "2025-05-06T06:00:00");
        draft("between", DraftStatus.DRAFT, 2025, "2025-05-06T09:00:00");

        // When
        runner.run(autoArchive);

        // Then
        assertThat(stored("old").getStatus()).isEqualTo(DraftStatus.ARCHIVED);
        assertThat(stored("old").getArchivedAt()).isEqualTo(LocalDateTime.parse("2026-11-06T06:30:00"));
        assertThat(stored("between").getStatus()).isEqualTo(DraftStatus.DRAFT);
    }

    @Test
    void retentionPurge_whenAnArchivedDraftWasNeverActedOn_deletesItAndItsOwnRecordsAfterTwelveMonths() {
        // Given — run on 2026-11-06T06:30Z; the cutoff is 2025-11-06T06:30
        workspace("workspace-1", "expired");
        draft("expired", DraftStatus.ARCHIVED, 2024, "2025-11-05T12:00:00");
        set("expired", "archived_at", LocalDateTime.parse("2025-11-05T12:00:00"));
        draft("young", DraftStatus.ARCHIVED, 2024, "2025-11-07T12:00:00");
        set("young", "archived_at", LocalDateTime.parse("2025-11-07T12:00:00"));
        DraftRevision revision = new DraftRevision();
        revision.setId("revision-1");
        revision.setDraftId("expired");
        revisions.save(revision);
        DraftComputation computation = new DraftComputation();
        computation.setId("computation-1");
        computation.setDraftId("expired");
        computation.setResultJson("{}");
        computation.setEmail("owner@example.com");
        computations.save(computation);

        // When
        runner.run(retentionPurge);

        // Then
        assertThat(stored("expired")).isNull();
        assertThat(revisions.findAll()).isEmpty();
        assertThat(computations.findAll()).isEmpty();
        assertThat(workspaces.findById("workspace-1").orElseThrow().getPrimaryDraftId()).isNull();
        assertThat(workspaces.findById("workspace-1").orElseThrow().getAuditMetadata().getUpdatedBy())
                .isEqualTo("retention-purge");
        assertThat(stored("young")).isNotNull();
    }

    @Test
    void retentionPurge_whenADraftWasActedOn_keepsItThroughTheLastYearItsLossCouldBeCarriedInto() {
        // Given — tax year 2025-26, acted on 2026-05-01; one later archived by the user on 2026-05-02
        workspace("workspace-1", null);
        draft("filed", DraftStatus.ACTED_ON, 2025, "2026-05-01T12:00:00");
        set("filed", "acted_on_at", LocalDateTime.parse("2026-05-01T12:00:00"));
        draft("filed-then-archived", DraftStatus.ARCHIVED, 2025, "2026-05-02T12:00:00");
        set("filed-then-archived", "acted_on_at", LocalDateTime.parse("2026-05-01T12:00:00"));
        set("filed-then-archived", "archived_at", LocalDateTime.parse("2026-05-02T12:00:00"));

        // When — 2027-06-01: twelve months past the archiving, not eight years past the year
        clock.set("2027-06-01T06:30:00Z");
        runner.run(retentionPurge);

        // Then
        assertThat(stored("filed-then-archived")).isNotNull();

        // When — 31-3-2034, the last day of 2033-34, the eighth succeeding year
        clock.set("2034-03-31T12:00:00Z");
        runner.run(retentionPurge);

        // Then
        assertThat(stored("filed")).isNotNull();

        // When — the day after
        clock.set("2034-04-01T00:30:00Z");
        runner.run(retentionPurge);

        // Then
        assertThat(stored("filed")).isNull();
        assertThat(stored("filed-then-archived")).isNull();
        assertThat(failures.findAll()).isEmpty();
    }

    @Test
    void retentionPurge_whenNoPackCoversTheYear_keepsTheDraftAndRecordsWhy() {
        // Given — the application ships 2025-26 and 2026-27 only
        workspace("workspace-1", null);
        draft("old-year", DraftStatus.ACTED_ON, 2019, "2020-07-01T12:00:00");
        set("old-year", "acted_on_at", LocalDateTime.parse("2020-07-01T12:00:00"));

        // When
        runner.run(retentionPurge);

        // Then
        assertThat(stored("old-year")).isNotNull();
        assertThat(failures.findAll()).singleElement().satisfies(failure -> {
            assertThat(failure.getJob()).isEqualTo("retention-purge");
            assertThat(failure.getSubject()).isEqualTo("tax year 2019-20");
        });
    }

    private void documentOf(String id, int taxYear) {
        documents.save(StoredDocument.of(new IngestedDocument(id, "profile-1", TaxYearId.of(taxYear), DocumentType.FORM_130,
                id.substring(0, 1).repeat(64), LocalDateTime.parse("2026-06-15T09:00:00"), 2)));
        attempts.save(StoredParseAttempt.of(new ParseAttempt("attempt-of-" + id, id, new ParserRun("FORM_130", "1",
                LocalDateTime.parse("2026-06-15T09:01:00"), LocalDateTime.parse("2026-06-15T09:02:00")), ParseOutcome.SUCCESS,
                List.of(), List.of(), List.of())));
        confirmations.save(StoredConfirmation.of(new DocumentConfirmation("attempt-of-" + id, "owner@example.com",
                LocalDateTime.parse("2026-06-15T09:05:00"), Map.of(), Set.of())));
        documentBytes.store(id, new DocumentContent(TestPdfs.pages(1)));
    }

    @Test
    void retentionPurge_whenStoredBytesNameNoDocument_deletesThemOnceADayOld() {
        // Given — bytes stored now (the machine's clock) under an id no document holds, and a document's own bytes
        documentBytes.store("orphan", new DocumentContent(TestPdfs.pages(1)));
        documentOf("b-2026", 2026);

        // When — the job's clock a day before the upload: the orphan is younger than P1D on that clock
        clock.set(java.time.Instant.now().minus(java.time.Duration.ofDays(1)).toString());
        runner.run(retentionPurge);

        // Then
        assertThat(documentBytesRepository.existsById("orphan")).isTrue();

        // When — two days after the upload
        clock.set(java.time.Instant.now().plus(java.time.Duration.ofDays(2)).toString());
        runner.run(retentionPurge);

        // Then — the orphan is gone; the document's bytes stay
        assertThat(documentBytesRepository.existsById("orphan")).isFalse();
        assertThat(storedWithItsReading("b-2026")).isEqualTo(4);
    }

    private long storedWithItsReading(String documentId) {
        long count = 0;
        count += documentBytesRepository.existsById(documentId) ? 1 : 0;
        count += documents.existsById(documentId) ? 1 : 0;
        count += attempts.existsById("attempt-of-" + documentId) ? 1 : 0;
        count += confirmations.existsById("attempt-of-" + documentId) ? 1 : 0;
        return count;
    }

    @Test
    void retentionPurge_whenADocumentsYearHasRunOut_deletesItWithItsAttemptsAndConfirmations() {
        // Given — a certificate for tax year 2025-26 (D-761 C5a: its eight succeeding years end 31-3-2034) and one for 2026-27
        documentOf("a-2025", 2025);
        documentOf("b-2026", 2026);

        // When — 31-3-2034, the last day of 2033-34
        clock.set("2034-03-31T12:00:00Z");
        runner.run(retentionPurge);

        // Then
        assertThat(storedWithItsReading("a-2025")).isEqualTo(4);

        // When — the day after
        clock.set("2034-04-01T00:30:00Z");
        runner.run(retentionPurge);

        // Then — 2026-27's run to 31-3-2035, so it stays
        assertThat(storedWithItsReading("a-2025")).isZero();
        assertThat(storedWithItsReading("b-2026")).isEqualTo(4);
        assertThat(failures.findAll()).isEmpty();
    }

    @Test
    void retentionPurge_whenNoPackCoversADocumentsYear_keepsItAndRecordsWhy() {
        // Given — the application ships 2025-26 and 2026-27 only
        documentOf("c-2019", 2019);

        // When
        runner.run(retentionPurge);

        // Then
        assertThat(storedWithItsReading("c-2019")).isEqualTo(4);
        assertThat(failures.findAll()).singleElement().satisfies(failure -> {
            assertThat(failure.getJob()).isEqualTo("retention-purge");
            assertThat(failure.getSubject()).isEqualTo("documents of tax year 2019-20");
        });
    }

    @Test
    void run_whenAnotherRunHoldsTheLease_doesNothingUntilItExpires() {
        // Given
        workspace("workspace-1", null);
        draft("old", DraftStatus.DRAFT, 2025, "2025-05-05T12:00:00");
        JobLease held = new JobLease();
        held.setJob("auto-archive");
        held.setHolder("another-instance");
        held.setExpiresAt(Instant.parse("2026-11-06T06:40:00Z"));
        leases.save(held);

        // When
        boolean whileHeld = runner.run(autoArchive);
        clock.set("2026-11-06T06:41:00Z");
        boolean afterExpiry = runner.run(autoArchive);

        // Then
        assertThat(whileHeld).isFalse();
        assertThat(afterExpiry).isTrue();
        assertThat(stored("old").getStatus()).isEqualTo(DraftStatus.ARCHIVED);
    }

    @Test
    void retentionPurge_whenTheLeaseIsLostBeforeADelete_deletesNothing() {
        // Given
        workspace("workspace-1", null);
        draft("expired", DraftStatus.ARCHIVED, 2024, "2025-01-01T12:00:00");
        set("expired", "archived_at", LocalDateTime.parse("2025-01-01T12:00:00"));

        // When — a run whose lease another has taken over
        retentionPurge.run(new JobContext("retention-purge", clock, () -> false, failures));

        // Then
        assertThat(stored("expired")).isNotNull();
        assertThat(failures.findAll()).isEmpty();
    }

    @Test
    void retentionPurge_whenALandlordOutlivesTheLatestDraftNamingItByTwoYears_deletesItTheDayAfter() {
        // Given — landlord-1 last changed 01-06-2025 (2025-26), named by the 2026-27 draft, not by the 2028-29 one: max(2026-27,
        // 2025-26) = 2026-27, ending 31-03-2027; + P2Y = 31-03-2029. lender-1, same profile, named by none, changed 01-06-2025:
        // 2025-26 ends 31-03-2026; + P2Y = 31-03-2028. lender-2, profile-2, named by none, changed 10-05-2027 (2027-28): ending
        // 31-03-2028; + P2Y = 31-03-2030
        workspace("workspace-1", null);
        draftOf("draft-2026", "profile-1", 2026);
        naming("draft-2026", "landlord-1");
        draftOf("draft-2028", "profile-1", 2028);
        landlord("landlord-1", "profile-1", "2025-06-01T12:00:00");
        lender("lender-1", "profile-1", "2025-06-01T12:00:00");
        lender("lender-2", "profile-2", "2027-05-10T12:00:00");

        // When — 31-03-2029, landlord-1's last day kept
        clock.set("2029-03-31T12:00:00Z");
        runner.run(retentionPurge);

        // Then
        assertThat(landlords.findById("landlord-1")).isPresent();
        assertThat(lenders.findById("lender-1")).isEmpty();

        // When — the day after
        clock.set("2029-04-01T00:30:00Z");
        runner.run(retentionPurge);

        // Then
        assertThat(landlords.findById("landlord-1")).isEmpty();
        assertThat(lenders.findById("lender-2")).isPresent();
        assertThat(stored("draft-2026")).isNotNull();
        assertThat(failures.findAll()).isEmpty();
    }

    @Test
    void retentionPurge_whenALaterDraftNamesTheRecord_keepsItThroughThatDraftsYear() {
        // Given — changed 01-07-2024, in 2024-25 (alone: 31-03-2025 + P2Y = 31-03-2027, past on 01-04-2029); the 2028-29 draft
        // names both, ending 31-03-2029; + P2Y = 31-03-2031
        workspace("workspace-1", null);
        draftOf("draft-2028", "profile-1", 2028);
        naming("draft-2028", "landlord-1", "lender-1");
        landlord("landlord-1", "profile-1", "2024-07-01T12:00:00");
        lender("lender-1", "profile-1", "2024-07-01T12:00:00");

        // When
        clock.set("2029-04-01T00:30:00Z");
        runner.run(retentionPurge);

        // Then
        assertThat(landlords.findById("landlord-1")).isPresent();
        assertThat(lenders.findById("lender-1")).isPresent();
    }

    @Test
    void retentionPurge_whenALandlordHasNoStoredDate_keepsItAndRecordsWhy() {
        // Given
        landlord("landlord-1", "profile-1", "2020-06-01T12:00:00");
        new JdbcTemplate(postgresDataSource)
                .update("UPDATE tax_landlords SET updated_at = NULL, created_at = NULL WHERE id = ?", "landlord-1");

        // When
        clock.set("2029-04-01T00:30:00Z");
        runner.run(retentionPurge);

        // Then
        assertThat(landlords.findById("landlord-1")).isPresent();
        assertThat(failures.findAll()).singleElement().satisfies(failure -> {
            assertThat(failure.getSubject()).isEqualTo("landlord landlord-1");
            assertThat(failure.getJob()).isEqualTo("retention-purge");
        });
    }

    @Test
    void retentionPurge_whenTheLeaseIsLostBeforeDeletingALandlord_deletesNothing() {
        // Given — no draft; changed in 2025-26, so past 31-03-2027 + P2Y = 31-03-2028 on 01-04-2029
        landlord("landlord-1", "profile-1", "2025-06-01T12:00:00");
        clock.set("2029-04-01T00:30:00Z");

        // When
        retentionPurge.run(new JobContext("retention-purge", clock, () -> false, failures));

        // Then
        assertThat(landlords.findById("landlord-1")).isPresent();
        assertThat(failures.findAll()).isEmpty();
    }

    /** The shipped 2026-27 pack's law and exec versions, with one of them replaced by an older one. */
    private List<String> withOlder(String layer) {
        return taxCalculator.packVersions(TaxYearId.of(2026)).orElseThrow().stream()
                .map(version -> version.contains("layer=" + layer) ? version.replaceAll("version=[^,]+", "version=2026-27.old.1") : version)
                .sorted().toList();
    }

    private void computation(String draftId, List<String> lawAndExec, Map<String, String> ruleChecksums) {
        DraftComputation computation = new DraftComputation();
        computation.setId("computation-" + draftId);
        computation.setReceipt("receipt-" + draftId);
        computation.setDraftId(draftId);
        computation.setRevision(1);
        computation.setResultJson("{}");
        computation.setLawAndExec(lawAndExec);
        computation.setRuleChecksums(ruleChecksums);
        computation.setEmail("owner@example.com");
        computations.save(computation);
    }

    /** One of several computations of a draft at revision 1, by its own receipt and time (W-329a). */
    private void computationAt(String draftId, String receipt, LocalDateTime computedAt, List<String> lawAndExec) {
        DraftComputation computation = new DraftComputation();
        computation.setId("computation-" + receipt);
        computation.setReceipt(receipt);
        computation.setDraftId(draftId);
        computation.setRevision(1);
        computation.setComputedAt(computedAt);
        computation.setResultJson("{}");
        computation.setLawAndExec(lawAndExec);
        computation.setRuleChecksums(pinnedWithOneChanged());
        computation.setEmail("owner@example.com");
        computations.save(computation);
    }

    private Map<String, String> pinnedWithOneChanged() {
        String unchanged = taxCalculator.ruleChecksums(TaxYearId.of(2026), Set.of("ADV-LIABILITY")).get("ADV-LIABILITY");
        return Map.of("ADV-INSTALMENTS", "sha256:0", "ADV-LIABILITY", unchanged);
    }

    @Test
    void stalenessSweep_whenTheLawMovedUnderAReadyDraft_flagsItWithTheRulesItReadThatChanged() {
        // Given — computed under an older law layer; of its two rules, ADV-INSTALMENTS has changed since
        workspace("workspace-1", null);
        draft("ready", DraftStatus.READY, 2026, "2026-10-01T12:00:00");
        computation("ready", withOlder("LAW"), pinnedWithOneChanged());

        // When
        runner.run(stalenessSweep);

        // Then — R-8: flagged, never recomputed
        DraftStaleness staleness = stored("ready").getStaleness();
        assertThat(staleness.changedRules()).containsExactly("ADV-INSTALMENTS");
        assertThat(staleness.ruleDiffAvailable()).isTrue();
        assertThat(staleness.receipt()).isEqualTo("receipt-ready");
        assertThat(staleness.computedUnder()).isEqualTo(withOlder("LAW"));
        assertThat(staleness.current()).isEqualTo(taxCalculator.packVersions(TaxYearId.of(2026)).orElseThrow());
        assertThat(staleness.since()).isEqualTo(LocalDateTime.parse("2026-11-06T06:30:00"));
        assertThat(computations.findAll()).hasSize(1);
        assertThat(stored("ready").getAuditMetadata().getUpdatedBy()).isEqualTo("staleness-sweep");
    }

    @Test
    void stalenessSweep_whenTwoComputationsShareARevision_readsTheLaterOne() {
        // Given — W-122c's first figure stores one computation per regime at revision 1; the audit's createdAt is never
        // filled for them (W-329), so it cannot break the tie. The later one was made under an older law layer
        workspace("workspace-1", null);
        draft("ready", DraftStatus.READY, 2026, "2026-10-01T12:00:00");
        List<String> current = taxCalculator.packVersions(TaxYearId.of(2026)).orElseThrow();
        computationAt("ready", "receipt-earlier", LocalDateTime.parse("2026-11-06T09:00:00"), current);
        computationAt("ready", "receipt-later", LocalDateTime.parse("2026-11-06T09:01:00"), withOlder("LAW"));

        // When
        runner.run(stalenessSweep);

        // Then
        assertThat(stored("ready").getStaleness()).isNotNull();
        assertThat(stored("ready").getStaleness().receipt()).isEqualTo("receipt-later");
    }

    @Test
    void stalenessSweep_whenTwoComputationsShareARevisionAndNeitherHasATime_breaksTheTieByReceipt() {
        // Given — both stored before W-123b's computed_at; "receipt-b" sorts after "receipt-a"
        workspace("workspace-1", null);
        draft("ready", DraftStatus.READY, 2026, "2026-10-01T12:00:00");
        computationAt("ready", "receipt-a", null, taxCalculator.packVersions(TaxYearId.of(2026)).orElseThrow());
        computationAt("ready", "receipt-b", null, withOlder("LAW"));

        // When
        runner.run(stalenessSweep);

        // Then
        assertThat(stored("ready").getStaleness().receipt()).isEqualTo("receipt-b");
    }

    @Test
    void stalenessSweep_whenOnlyTheExecLayerMoved_flagsAFiledDraftToo() {
        // Given — V9: any change to a computed year, not only to the law layer
        workspace("workspace-1", null);
        draft("filed", DraftStatus.ACTED_ON, 2026, "2026-10-01T12:00:00");
        computation("filed", withOlder("EXEC"), pinnedWithOneChanged());

        // When
        runner.run(stalenessSweep);

        // Then
        assertThat(stored("filed").getStaleness()).isNotNull();
        assertThat(stored("filed").getStatus()).isEqualTo(DraftStatus.ACTED_ON);
    }

    @Test
    void stalenessSweep_whenADraftIsBeingWorkedOrItsLawIsCurrent_leavesItAlone() {
        // Given
        workspace("workspace-1", null);
        draft("working", DraftStatus.DRAFT, 2026, "2026-10-01T12:00:00");
        computation("working", withOlder("LAW"), pinnedWithOneChanged());
        draft("current", DraftStatus.READY, 2026, "2026-10-01T12:00:00");
        computation("current", taxCalculator.packVersions(TaxYearId.of(2026)).orElseThrow(), pinnedWithOneChanged());

        // When
        runner.run(stalenessSweep);

        // Then
        assertThat(stored("working").getStaleness()).isNull();
        assertThat(stored("current").getStaleness()).isNull();
    }

    @Test
    void stalenessSweep_whenRunAgainLater_keepsTheFirstFlag() {
        // Given
        workspace("workspace-1", null);
        draft("ready", DraftStatus.READY, 2026, "2026-10-01T12:00:00");
        computation("ready", withOlder("LAW"), pinnedWithOneChanged());
        runner.run(stalenessSweep);

        // When
        clock.set("2026-11-07T06:30:00Z");
        runner.run(stalenessSweep);

        // Then
        assertThat(stored("ready").getStaleness().since()).isEqualTo(LocalDateTime.parse("2026-11-06T06:30:00"));
    }

    @Test
    void stalenessSweep_whenTheComputationPredatesItsPins_readsTheVersionsFromItsResultAndNamesNoRules() {
        // Given — stored before W-138: the versions only inside the result, no rule checksums
        workspace("workspace-1", null);
        draft("old", DraftStatus.READY, 2026, "2026-10-01T12:00:00");
        DraftComputation computation = new DraftComputation();
        computation.setId("computation-old");
        computation.setReceipt("TXC-2026-BBBBBB");
        computation.setDraftId("old");
        computation.setRevision(1);
        computation.setResultJson("{\"tax\": {\"lawAndExec\": [\"" + String.join("\", \"", withOlder("LAW")) + "\"]}}");
        computation.setEmail("owner@example.com");
        computations.save(computation);

        // When
        runner.run(stalenessSweep);

        // Then
        assertThat(stored("old").getStaleness().ruleDiffAvailable()).isFalse();
        assertThat(stored("old").getStaleness().changedRules()).isEmpty();
    }

    @Test
    void stalenessSweep_whenTheProfileIsHeldForErasure_flagsNothing() {
        // Given — a READY draft whose law moved, as in the flagging case, but its profile held for erasure (W-235)
        TaxpayerProfile held = new TaxpayerProfile();
        held.setId("profile-1");
        held.setErasureRequestedAt(LocalDateTime.parse("2026-11-01T00:00:00"));
        profiles.save(held);
        workspace("workspace-1", null);
        draft("ready", DraftStatus.READY, 2026, "2026-10-01T12:00:00");
        computation("ready", withOlder("LAW"), pinnedWithOneChanged());

        // When
        runner.run(stalenessSweep);

        // Then
        assertThat(stored("ready").getStaleness()).isNull();
    }
}
