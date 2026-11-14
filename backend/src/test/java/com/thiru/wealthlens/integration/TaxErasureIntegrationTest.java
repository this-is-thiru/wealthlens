package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.taxplanning.compute.DraftComputation;
import com.thiru.wealthlens.taxplanning.jobs.AutoArchive;
import com.thiru.wealthlens.taxplanning.jobs.RetentionPurge;
import com.thiru.wealthlens.taxplanning.jobs.TaxJobRunner;
import com.thiru.wealthlens.taxplanning.profile.BankAccount;
import com.thiru.wealthlens.taxplanning.profile.Employer;
import com.thiru.wealthlens.taxplanning.profile.FamilyMember;
import com.thiru.wealthlens.taxplanning.profile.Landlord;
import com.thiru.wealthlens.taxplanning.profile.Lender;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.protection.AccessRecord;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import com.thiru.wealthlens.taxplanning.workspace.DraftRevision;
import com.thiru.wealthlens.taxplanning.workspace.DraftStatus;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.RegimeElectionHistory;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspace;
import io.restassured.RestAssured;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Erasure on request over HTTP and Mongo (W-235, C1a – C5a), on a clock the test sets. The dates are worked by hand in
 * {@code plans/W-235.md}: a request at 2026-11-07T10:00Z with a one-year hold is erased from 2027-11-07T10:00Z, not a second before.
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
    private TaxJobsIntegrationTest.SettableClock clock;

    @Autowired
    private TaxJobRunner runner;

    @Autowired
    private RetentionPurge retentionPurge;

    @Autowired
    private AutoArchive autoArchive;

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
        return client.exchange(URI.create("http://localhost:" + RestAssured.port + "/tax/user/" + OWNER + path), method,
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
        return mongoTemplate.findAll(TaxpayerProfile.class).stream().map(TaxpayerProfile::getId).collect(Collectors.toSet());
    }

    private ResponseEntity<String> erase(String profileId) {
        return call(HttpMethod.POST, "/profiles/" + profileId + "/erasure", null);
    }

    /** One record in every collection a profile owns, a draft with a revision and a receipt, and reads of each. */
    private void everythingUnder(String profileId) {
        FamilyMember member = new FamilyMember();
        member.setProfileId(profileId);
        Employer employer = new Employer();
        employer.setProfileId(profileId);
        Landlord landlord = new Landlord();
        landlord.setProfileId(profileId);
        Lender lender = new Lender();
        lender.setProfileId(profileId);
        BankAccount account = new BankAccount();
        account.setProfileId(profileId);
        TaxYearWorkspace workspace = new TaxYearWorkspace();
        workspace.setProfileId(profileId);
        workspace.setTaxYearStart(2025);
        RegimeElectionHistory elections = new RegimeElectionHistory();
        elections.setProfileId(profileId);
        elections.setRegimeCode("NEW");
        List.of(member, employer, landlord, lender, account, workspace, elections).forEach(mongoTemplate::save);
        FilingDraft draft = new FilingDraft();
        draft.setId("draft-of-" + profileId);
        draft.setWorkspaceId(workspace.getId());
        draft.setProfileId(profileId);
        draft.setTaxYearStart(2025);
        draft.setStatus(DraftStatus.ACTED_ON);
        draft.setActedOnAt(LocalDateTime.parse("2026-07-31T12:00:00"));
        draft.setInputsJson("{}");
        mongoTemplate.save(draft);
        DraftRevision revision = new DraftRevision();
        revision.setDraftId(draft.getId());
        mongoTemplate.save(revision);
        DraftComputation computation = new DraftComputation();
        computation.setDraftId(draft.getId());
        computation.setEmail(OWNER);
        computation.setReceipt("receipt-of-" + profileId);
        computation.setResultJson("{\"revision\": 1, \"capitalGainsIncluded\": false}");
        mongoTemplate.save(computation);
        read(ReadSubject.PROFILE, profileId);
        read(ReadSubject.DRAFT, draft.getId());
        read(ReadSubject.COMPUTATION, computation.getReceipt());
    }

    private void read(ReadSubject subject, String subjectId) {
        AccessRecord record = new AccessRecord();
        record.setPrincipal(OWNER);
        record.setSubject(subject);
        record.setSubjectId(subjectId);
        record.setReadAt(Instant.parse("2026-11-01T00:00:00Z"));
        mongoTemplate.save(record);
    }

    private long storedUnder(String profileId) {
        long scoped = List.of(FamilyMember.class, Employer.class, Landlord.class, Lender.class, BankAccount.class,
                TaxYearWorkspace.class, RegimeElectionHistory.class, FilingDraft.class).stream()
                .mapToLong(type -> mongoTemplate.count(query(where("profile_id").is(profileId)), type)).sum();
        String draftId = "draft-of-" + profileId;
        return scoped + mongoTemplate.count(query(where("_id").is(profileId)), TaxpayerProfile.class)
                + mongoTemplate.count(query(where("draft_id").is(draftId)), DraftRevision.class)
                + mongoTemplate.count(query(where("draft_id").is(draftId)), DraftComputation.class)
                + mongoTemplate.count(query(where("subject_id").in(profileId, draftId, "receipt-of-" + profileId)),
                        AccessRecord.class);
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
        TaxpayerProfile stored = mongoTemplate.findById(held, TaxpayerProfile.class);
        assertThat(stored.getErasureRequestedAt()).isEqualTo(LocalDateTime.parse("2026-11-07T10:00:00"));
        assertThat(stored.getPanDigest()).isNull();
    }

    @Test
    void request_whenThePanIsRegisteredAgainAfterTwoHolds_isAccepted() {
        // Given — two held profiles both lack a digest, which a unique index that is not sparse would refuse
        assertThat(erase(createdProfile("ABCDE1234F")).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(erase(createdProfile("PQRSX5678K")).getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

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
        // + 1 computation + 3 access records = 14
        String held = createdProfile("ABCDE1234F");
        String kept = createdProfile("PQRSX5678K");
        everythingUnder(held);
        everythingUnder(kept);
        erase(held);
        assertThat(storedUnder(held)).isEqualTo(14);

        // When — one second before the hold ends
        clock.set("2027-11-07T09:59:59Z");
        runner.run(retentionPurge);

        // Then
        assertThat(storedUnder(held)).isEqualTo(14);

        // When — the hold's end
        clock.set("2027-11-07T10:00:00Z");
        runner.run(retentionPurge);

        // Then
        assertThat(storedUnder(held)).isZero();
        assertThat(storedUnder(kept)).isEqualTo(14);

        // When — run again
        runner.run(retentionPurge);

        // Then — nothing more changes
        assertThat(storedUnder(kept)).isEqualTo(14);
    }

    @Test
    void autoArchive_whenTheProfileIsHeld_leavesItsDraftsAlone() {
        // Given — a draft untouched since 2025-01-01, past the 18-month cutoff of 2025-05-07T10:00
        String held = createdProfile("ABCDE1234F");
        FilingDraft draft = new FilingDraft();
        draft.setId("untouched");
        draft.setWorkspaceId("workspace-1");
        draft.setProfileId(held);
        draft.setTaxYearStart(2024);
        draft.setStatus(DraftStatus.DRAFT);
        draft.setInputsJson("{}");
        mongoTemplate.save(draft);
        mongoTemplate.updateFirst(query(where("_id").is("untouched")),
                new Update().set("audit_metadata.updatedAt", LocalDateTime.parse("2025-01-01T00:00:00")), FilingDraft.class);
        erase(held);

        // When
        runner.run(autoArchive);

        // Then
        assertThat(mongoTemplate.findById("untouched", FilingDraft.class).getStatus()).isEqualTo(DraftStatus.DRAFT);
    }
}
