package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.thiru.wealthlens.taxplanning.jobs.RetentionPurge;
import com.thiru.wealthlens.taxplanning.jobs.TaxJobRunner;
import com.thiru.wealthlens.taxplanning.profile.BankAccount;
import com.thiru.wealthlens.taxplanning.profile.BankAccountRepository;
import com.thiru.wealthlens.taxplanning.profile.Employer;
import com.thiru.wealthlens.taxplanning.profile.EmployerRepository;
import com.thiru.wealthlens.taxplanning.profile.FamilyMember;
import com.thiru.wealthlens.taxplanning.profile.FamilyMemberRepository;
import com.thiru.wealthlens.taxplanning.profile.Landlord;
import com.thiru.wealthlens.taxplanning.profile.LandlordRepository;
import com.thiru.wealthlens.taxplanning.profile.Lender;
import com.thiru.wealthlens.taxplanning.profile.LenderRepository;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRepository;
import com.thiru.wealthlens.taxplanning.protection.AccessRecord;
import com.thiru.wealthlens.taxplanning.protection.AccessRecordRepository;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import com.thiru.wealthlens.taxplanning.workspace.DraftRevision;
import com.thiru.wealthlens.taxplanning.workspace.DraftRevisionRepository;
import com.thiru.wealthlens.taxplanning.workspace.DraftStatus;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraftRepository;
import com.thiru.wealthlens.taxplanning.workspace.RegimeElectionHistory;
import com.thiru.wealthlens.taxplanning.workspace.RegimeElectionHistoryRepository;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspace;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspaceRepository;
import com.thiru.wealthlens.testsupport.TestPdfs;
import io.restassured.RestAssured;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Erasure on request over HTTP and PostgreSQL (W-235, C1a – C5a), on a clock the test sets. The dates are worked by hand in
 * {@code docs/modules/taxplanning.md} §11 (W-235): a request at 2026-11-07T10:00Z with a one-year hold is erased from 2027-11-07T10:00Z, not a second before.
 */
class TaxErasureIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "owner@example.com";
    private static final String REQUEST_TIME = "2026-11-07T10:00:00Z";

    @TestConfiguration
    static class SetDay {

        @Bean
        @Primary
        TaxJobsIntegrationTest.SettableClock settableClock() {
            return new TaxJobsIntegrationTest.SettableClock();
        }
    }

    @Autowired
    private DocumentBytes documentBytes;

    @Autowired
    private TaxJobsIntegrationTest.SettableClock clock;

    @Autowired
    private TaxJobRunner runner;

    @Autowired
    private RetentionPurge retentionPurge;

    @Autowired
    private AutoArchive autoArchive;

    @Autowired
    private TaxpayerProfileRepository profiles;

    @Autowired
    private FamilyMemberRepository familyMembers;

    @Autowired
    private EmployerRepository employers;

    @Autowired
    private LandlordRepository landlords;

    @Autowired
    private LenderRepository lenders;

    @Autowired
    private BankAccountRepository bankAccounts;

    @Autowired
    private TaxYearWorkspaceRepository workspaces;

    @Autowired
    private RegimeElectionHistoryRepository regimeElectionHistories;

    @Autowired
    private FilingDraftRepository filingDrafts;

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
    private AccessRecordRepository accessRecords;

    @Autowired
    private TaxDocumentBytesRepository documentBytesRows;

    @BeforeEach
    void theDayOfTheRequest() {
        clock.set(REQUEST_TIME);
    }

    private static String profile(String pan) {
        return """
                {"name": "Self", "pan": "%s", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                 "residentialStatus": "RESIDENT"}""".formatted(pan);
    }

    private ResponseEntity<String> call(HttpMethod method, String path, String body) {
        RestTemplate client = new RestTemplate();
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(OWNER));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return client.exchange(URI.create("http://localhost:" + RestAssured.port + "/api/v1/me/tax" + path), method,
                new HttpEntity<>(body, headers), String.class);
    }

    private String createdProfile(String pan) {
        Set<String> before = profileIds();
        assertThat(call(HttpMethod.POST, "/profiles", profile(pan)).getStatusCode()).isEqualTo(HttpStatus.OK);
        Set<String> after = new HashSet<>(profileIds());
        after.removeAll(before);
        return after.iterator().next();
    }

    private Set<String> profileIds() {
        return profiles.findAll().stream().map(TaxpayerProfile::getId).collect(Collectors.toSet());
    }

    private ResponseEntity<String> erase(String profileId) {
        return call(HttpMethod.POST, "/profiles/" + profileId + "/erasure", null);
    }

    /** One family member, employer, landlord, lender and bank account under the profile. */
    private void referencesUnder(String profileId) {
        FamilyMember member = new FamilyMember();
        member.setId("member-of-" + profileId);
        member.setNewlyCreated(true);
        member.setProfileId(profileId);
        familyMembers.save(member);
        Employer employer = new Employer();
        employer.setId("employer-of-" + profileId);
        employer.setNewlyCreated(true);
        employer.setProfileId(profileId);
        employers.save(employer);
        Landlord landlord = new Landlord();
        landlord.setId("landlord-of-" + profileId);
        landlord.setNewlyCreated(true);
        landlord.setProfileId(profileId);
        landlords.save(landlord);
        Lender lender = new Lender();
        lender.setId("lender-of-" + profileId);
        lender.setNewlyCreated(true);
        lender.setProfileId(profileId);
        lenders.save(lender);
        BankAccount account = new BankAccount();
        account.setId("account-of-" + profileId);
        account.setNewlyCreated(true);
        account.setProfileId(profileId);
        bankAccounts.save(account);
    }

    /** One record in every collection a profile owns, a draft with a revision and a receipt, and reads of each. */
    private void everythingUnder(String profileId) {
        referencesUnder(profileId);
        TaxYearWorkspace workspace = new TaxYearWorkspace();
        workspace.setId("workspace-of-" + profileId);
        workspace.setNewlyCreated(true);
        workspace.setProfileId(profileId);
        workspace.setTaxYearStart(2025);
        workspaces.save(workspace);
        RegimeElectionHistory elections = new RegimeElectionHistory();
        elections.setId("elections-of-" + profileId);
        elections.setNewlyCreated(true);
        elections.setProfileId(profileId);
        elections.setRegimeCode("NEW");
        regimeElectionHistories.save(elections);
        FilingDraft draft = new FilingDraft();
        draft.setId("draft-of-" + profileId);
        draft.setNewlyCreated(true);
        draft.setWorkspaceId(workspace.getId());
        draft.setProfileId(profileId);
        draft.setTaxYearStart(2025);
        draft.setStatus(DraftStatus.ACTED_ON);
        draft.setActedOnAt(LocalDateTime.parse("2026-07-31T12:00:00"));
        draft.setInputsJson("{}");
        filingDrafts.save(draft);
        DraftRevision revision = new DraftRevision();
        revision.setId("revision-of-" + profileId);
        revision.setNewlyCreated(true);
        revision.setDraftId(draft.getId());
        revisions.save(revision);
        DraftComputation computation = new DraftComputation();
        computation.setId("computation-of-" + profileId);
        computation.setNewlyCreated(true);
        computation.setDraftId(draft.getId());
        computation.setEmail(OWNER);
        computation.setReceipt("receipt-of-" + profileId);
        computation.setResultJson("{\"revision\": 1, \"capitalGainsIncluded\": false}");
        computations.save(computation);
        documentUnder(profileId);
        read(ReadSubject.PROFILE, profileId);
        read(ReadSubject.DRAFT, draft.getId());
        read(ReadSubject.COMPUTATION, computation.getReceipt());
    }

    /** A document with one attempt and its confirmation (W-131a); the same bytes under each profile, which the key allows. */
    private void documentUnder(String profileId) {
        String documentId = "document-of-" + profileId;
        String attemptId = "attempt-of-" + profileId;
        documents.save(StoredDocument.of(new IngestedDocument(documentId, profileId, TaxYearId.of(2025), DocumentType.FORM_130,
                "b".repeat(64), LocalDateTime.parse("2026-06-15T09:00:00"), 2)));
        attempts.save(StoredParseAttempt.of(new ParseAttempt(attemptId, documentId, new ParserRun("FORM_130", "1",
                LocalDateTime.parse("2026-06-15T09:01:00"), LocalDateTime.parse("2026-06-15T09:02:00")), ParseOutcome.SUCCESS,
                List.of(), List.of(), List.of())));
        documentBytes.store(documentId, new DocumentContent(TestPdfs.pages(1)));
        confirmations.save(StoredConfirmation.of(new DocumentConfirmation(attemptId, OWNER, LocalDateTime.parse("2026-06-15T09:05:00"),
                Map.of(), Set.of())));
    }

    private void read(ReadSubject subject, String subjectId) {
        AccessRecord record = new AccessRecord();
        record.setPrincipal(OWNER);
        record.setSubject(subject);
        record.setSubjectId(subjectId);
        record.setReadAt(Instant.parse("2026-11-01T00:00:00Z"));
        accessRecords.save(record);
    }

    private long storedUnder(String profileId) {
        String draftId = "draft-of-" + profileId;
        long accessRecordCount = new JdbcTemplate(postgresDataSource).queryForObject(
                "SELECT COUNT(*) FROM tax_access_log WHERE subject_id IN (?, ?, ?)", Long.class, profileId, draftId,
                "receipt-of-" + profileId);
        return familyMembers.findByProfileId(profileId).size() + employers.findByProfileId(profileId).size()
                + landlords.findByProfileId(profileId).size() + lenders.findByProfileId(profileId).size()
                + bankAccounts.findByProfileId(profileId).size()
                + (workspaces.findByProfileIdAndTaxYearStart(profileId, 2025).isPresent() ? 1 : 0)
                + regimeElectionHistories.findByProfileId(profileId).size()
                + filingDrafts.findByProfileId(profileId).size()
                + documents.findByProfileId(profileId).size()
                + (documentBytesRows.existsById("document-of-" + profileId) ? 1 : 0)
                + (attempts.existsById("attempt-of-" + profileId) ? 1 : 0)
                + (confirmations.existsById("attempt-of-" + profileId) ? 1 : 0)
                + (profiles.existsById(profileId) ? 1 : 0)
                + revisions.findByDraftIdOrderByRevisionNumber(draftId).size()
                + computations.findByDraftId(draftId).size()
                + accessRecordCount;
    }

    @Test
    void request_whenTheOwnerAsks_holdsTheProfileUnreadAndNamesTheEraseDateAndContact() {
        // Given
        String held = createdProfile("ABCDE1234F");

        // When
        ResponseEntity<String> receipt = erase(held);

        // Then — 2026-11-07T10:00Z plus P1Y is 2027-11-07T10:00Z
        assertThat(receipt.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(receipt.getBody()).contains("\"requestedAt\":\"2026-11-07T10:00:00\"", "\"eraseOn\":\"2027-11-07T10:00:00\"",
                "\"contact\":\"privacy@example.com\"", "r.8(3)");
        assertThat(call(HttpMethod.GET, "/profiles/" + held, null).getBody()).contains("This account holds no such profile");
        assertThat(call(HttpMethod.GET, "/profiles", null).getBody()).isEqualTo("[]");
        assertThat(erase(held).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        TaxpayerProfile stored = profiles.findById(held).orElseThrow();
        assertThat(stored.getErasureRequestedAt()).isEqualTo(LocalDateTime.parse("2026-11-07T10:00:00"));
        assertThat(stored.getPan()).isEqualTo("ABCDE1234F"); // kept until erased, so a reapply has it (W-428c C1a)
    }

    @Test
    void request_whenThePanIsRegisteredAgainAfterTwoHoldsOfIt_isAccepted() {
        // Given — two held profiles with the same PAN, which a unique index over every profile would refuse (W-428c C1a)
        assertThat(erase(createdProfile("ABCDE1234F")).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(erase(createdProfile("ABCDE1234F")).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // When
        ResponseEntity<String> again = call(HttpMethod.POST, "/profiles", profile("ABCDE1234F"));

        // Then
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void receipt_whenItsProfileIsHeld_isRefused() {
        // Given
        String held = createdProfile("ABCDE1234F");
        everythingUnder(held);
        assertThat(call(HttpMethod.GET, "/computations/receipt-of-" + held, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        // When
        erase(held);

        // Then
        ResponseEntity<String> refused = call(HttpMethod.GET, "/computations/receipt-of-" + held, null);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).contains("This account holds no such profile");
    }

    @Test
    void retentionPurge_whenTheHoldHasPassed_erasesEverythingUnderTheProfileAndNothingElse() {
        // Given — every collection holds one record under each profile, plus reads: 7 scoped + 1 draft + 1 profile + 1 revision
        // + 1 computation + 3 access records + 1 document, 1 attempt and 1 confirmation (W-131a) + its stored bytes (W-131b) = 18
        String held = createdProfile("ABCDE1234F");
        String kept = createdProfile("PQRSX5678K");
        everythingUnder(held);
        everythingUnder(kept);
        erase(held);
        assertThat(storedUnder(held)).isEqualTo(18);

        // When — one second before the hold ends
        clock.set("2027-11-07T09:59:59Z");
        runner.run(retentionPurge);

        // Then
        assertThat(storedUnder(held)).isEqualTo(18);

        // When — the hold's end
        clock.set("2027-11-07T10:00:00Z");
        runner.run(retentionPurge);

        // Then
        assertThat(storedUnder(held)).isZero();
        assertThat(storedUnder(kept)).isEqualTo(18);

        // When — run again
        runner.run(retentionPurge);

        // Then — nothing more changes
        assertThat(storedUnder(kept)).isEqualTo(18);
    }

    @Test
    void autoArchive_whenTheProfileIsHeld_leavesItsDraftsAlone() {
        // Given — a draft untouched since 2025-01-01, past the 18-month cutoff of 2025-05-07T10:00
        String held = createdProfile("ABCDE1234F");
        FilingDraft draft = new FilingDraft();
        draft.setId("untouched");
        draft.setNewlyCreated(true);
        draft.setWorkspaceId("workspace-1");
        draft.setProfileId(held);
        draft.setTaxYearStart(2024);
        draft.setStatus(DraftStatus.DRAFT);
        draft.setInputsJson("{}");
        filingDrafts.save(draft);
        new JdbcTemplate(postgresDataSource).update("UPDATE tax_filing_drafts SET updated_at = ? WHERE id = ?",
                LocalDateTime.parse("2025-01-01T00:00:00"), "untouched");
        erase(held);

        // When
        runner.run(autoArchive);

        // Then
        assertThat(filingDrafts.findById("untouched").orElseThrow().getStatus()).isEqualTo(DraftStatus.DRAFT);
    }
}
