package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.compute.DraftComputation;
import com.thiru.wealthlens.taxplanning.profile.Landlord;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.protection.AccessRecord;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import com.thiru.wealthlens.taxplanning.workspace.DraftRevision;
import com.thiru.wealthlens.taxplanning.workspace.DraftService;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.RegimeElectionHistory;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspace;
import io.restassured.RestAssured;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A draft over HTTP and into Mongo: its revisions, its locking, its freeze and its election (W-099, W-100, W-108, W-110, W-082d; D-441). */
class DraftIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "drafter@example.com";
    private static final String OTHER = "other@example.com";
    private static final String ADD_EMPLOYMENT = """
            [{"op": "add", "path": "/entered/employments/-", "value": {
              "components": [{"code": {"value": "BASIC"}, "annualAmount": {"amountInRupees": %s}}],
              "termsIncludeDearnessAllowance": false, "employerKind": "OTHER",
              "dearnessAllowanceCountsForRetirement": false, "companyEmployer": null}}]""";

    @Autowired
    private DraftService draftService;

    @Autowired
    private MongoTransactionManager transactionManager;

    private String profileId;
    private String draftId;

    private ResponseEntity<String> call(HttpMethod method, String caller, String path, String body, String ifMatch) {
        RestTemplate client = new RestTemplate(new JdkClientHttpRequestFactory());
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(caller));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (ifMatch != null) {
            headers.set(HttpHeaders.IF_MATCH, ifMatch);
        }
        return client.exchange(URI.create("http://localhost:" + RestAssured.port + "/tax/user/" + caller + path), method,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> save(String body, String ifMatch) {
        return call(HttpMethod.PATCH, OWNER, "/drafts/" + draftId, body, ifMatch);
    }

    private ResponseEntity<String> status(String body) {
        return call(HttpMethod.POST, OWNER, "/drafts/" + draftId + "/status", body, null);
    }

    @BeforeEach
    void aDraft() {
        call(HttpMethod.POST, OWNER, "/profiles", """
                {"name": "Self", "pan": "ABCDE1234F", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                 "residentialStatus": "RESIDENT"}""", null);
        profileId = mongoTemplate.findAll(TaxpayerProfile.class).getFirst().getId();
        call(HttpMethod.POST, OWNER, "/profiles/" + profileId + "/workspaces/2026/drafts", "{\"label\": \"As per Form 16\"}", null);
        draftId = mongoTemplate.findAll(FilingDraft.class).getFirst().getId();
    }

    @Test
    void createDraft_whenTheFirst_isPrimaryAtRevisionNil() {
        // When
        ResponseEntity<String> read = call(HttpMethod.GET, OWNER, "/drafts/" + draftId, null, null);

        // Then
        assertThat(read.getHeaders().getETag()).isEqualTo("\"rev-0\"");
        assertThat(mongoTemplate.findAll(TaxYearWorkspace.class).getFirst().getPrimaryDraftId()).isEqualTo(draftId);
        assertThat(mongoTemplate.findAll(DraftRevision.class)).hasSize(1);
    }

    @Test
    void workspace_whenAskedTwice_isOne() {
        // When
        call(HttpMethod.GET, OWNER, "/profiles/" + profileId + "/workspaces/2026", null, null);

        // Then
        assertThat(mongoTemplate.findAll(TaxYearWorkspace.class)).hasSize(1);
    }

    @Test
    void patch_whenTheRevisionIsCurrent_savesAndMovesItOn() {
        // When
        ResponseEntity<String> saved = save(ADD_EMPLOYMENT.formatted("175000"), "\"rev-0\"");

        // Then
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getHeaders().getETag()).isEqualTo("\"rev-1\"");
        assertThat(saved.getBody()).contains("175000");
        assertThat(mongoTemplate.findAll(DraftRevision.class)).hasSize(2);
    }

    @Test
    void patch_whenTheRevisionIsStale_expected409WithTheCurrentDraft() {
        // Given
        save(ADD_EMPLOYMENT.formatted("175000"), "\"rev-0\"");

        // When
        ResponseEntity<String> stale = save(ADD_EMPLOYMENT.formatted("999"), "\"rev-0\"");

        // Then
        // The body is parsed: its raw text carries the draft's random ids, and one held "999" (W-252)
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode employments = JsonMapper.builder().build().readTree(stale.getBody()).at("/current/inputs/entered/employments");
        assertThat(employments).hasSize(1);
        assertThat(employments.at("/0/components/0/annualAmount/amountInRupees").decimalValue())
                .isEqualByComparingTo("175000");
        assertThat(mongoTemplate.findAll(DraftRevision.class)).hasSize(2);
    }

    @Test
    void patch_whenNoRevisionIsSent_expected400() {
        // When / Then
        assertThat(save(ADD_EMPLOYMENT.formatted("1"), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void patch_whenTheResultWouldNotBind_expected400NamingWhereAndStoresNothing() {
        // When
        ResponseEntity<String> refused = save("[{\"op\": \"add\", \"path\": \"/employmentz\", \"value\": 1}]", "\"rev-0\"");

        // Then
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).contains("employmentz");
        assertThat(mongoTemplate.findAll(DraftRevision.class)).hasSize(1);
    }

    private static final String ANSWER = """
            [{"op": "add", "path": "/filing/declarations", "value": %s}]""";

    @Test
    void declarations_whenTy2026_listsR164sThirteenAndTheRatesQuestion() {
        // When
        ResponseEntity<String> read = call(HttpMethod.GET, OWNER, "/declarations/2026", null, null);

        // Then — r.164's thirteen and FA 2026 s.3(2)'s net agricultural income (W-258)
        JsonNode questions = JsonMapper.builder().build().readTree(read.getBody());
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(questions).hasSize(14);
        assertThat(questions.findValuesAsString("code")).contains("NET_AGRICULTURAL_INCOME");
        assertThat(questions.get(0).get("code").asString()).isEqualTo("AGRICULTURAL_INCOME");
        assertThat(questions.get(0).get("answerKind").asString()).isEqualTo("AMOUNT");
    }

    @Test
    void patch_whenDeclarationsAreAnswered_savesThemAndReadsThemBack() {
        // When
        ResponseEntity<String> saved = save(ANSWER.formatted("""
                {"DIRECTOR_IN_COMPANY": {"@type": "YesNo", "yes": true},
                 "AGRICULTURAL_INCOME": {"@type": "Amount", "amount": {"amountInRupees": 4000}}}"""), "\"rev-0\"");

        // Then
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode read = JsonMapper.builder().build().readTree(
                call(HttpMethod.GET, OWNER, "/drafts/" + draftId, null, null).getBody()).at("/inputs/filing/declarations");
        assertThat(read.at("/DIRECTOR_IN_COMPANY/yes").asBoolean()).isTrue();
        assertThat(read.at("/AGRICULTURAL_INCOME/amount/amountInRupees").decimalValue()).isEqualByComparingTo("4000");
    }

    @Test
    void patch_whenAQuestionTheYearDoesNotAskIsAnswered_expected400NamingItAndStoresNothing() {
        // When
        ResponseEntity<String> refused = save(ANSWER.formatted("{\"NOT_ASKED\": {\"@type\": \"YesNo\", \"yes\": true}}"),
                "\"rev-0\"");

        // Then
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).contains("NOT_ASKED is not a question 2026-27 asks");
        assertThat(mongoTemplate.findAll(DraftRevision.class)).hasSize(1);
    }

    @Test
    void patch_whenAnAmountAnswersAYesOrNoQuestion_expected400NamingTheKindNeverTheAmount() {
        // When
        ResponseEntity<String> refused = save(ANSWER.formatted(
                "{\"DIRECTOR_IN_COMPANY\": {\"@type\": \"Amount\", \"amount\": {\"amountInRupees\": 98765}}}"),
                "\"rev-0\"");

        // Then
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).contains("DIRECTOR_IN_COMPANY is answered as AMOUNT").doesNotContain("98765");
        assertThat(mongoTemplate.findAll(DraftRevision.class)).hasSize(1);
    }

    private String landlordOf(String ownerProfileId) {
        call(HttpMethod.POST, OWNER, "/profiles/" + ownerProfileId + "/landlords", """
                {"name": "A landlord", "address": "1 Main Road", "pan": "ABCPL1234K"}""", null);
        return mongoTemplate.findAll(Landlord.class).getLast().getId();
    }

    private static String naming(String list, String id) {
        return "[{\"op\": \"add\", \"path\": \"/thirdParties/" + list + "/-\", \"value\": \"" + id + "\"}]";
    }

    @Test
    void patch_whenTheDraftNamesItsProfilesLandlord_savesTheName() {
        // Given
        String landlordId = landlordOf(profileId);

        // When
        ResponseEntity<String> saved = save(naming("landlordIds", landlordId), "\"rev-0\"");

        // Then
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode read = JsonMapper.builder().build().readTree(call(HttpMethod.GET, OWNER, "/drafts/" + draftId, null, null).getBody());
        assertThat(read.at("/inputs/thirdParties/landlordIds/0").asString()).isEqualTo(landlordId);
    }

    @Test
    void patch_whenTheDraftNamesAnotherProfilesLandlord_expected400AndStoresNothing() {
        // Given — a second profile of the same account, holding the landlord
        call(HttpMethod.POST, OWNER, "/profiles", """
                {"name": "Spouse", "pan": "BCDEF2345G", "kind": "INDIVIDUAL", "dateOfBirth": "1987-02-01",
                 "residentialStatus": "RESIDENT"}""", null);
        String otherProfileId = mongoTemplate.findAll(TaxpayerProfile.class).stream().map(TaxpayerProfile::getId)
                .filter(id -> !id.equals(profileId)).findFirst().orElseThrow();
        String landlordId = landlordOf(otherProfileId);

        // When
        ResponseEntity<String> refused = save(naming("landlordIds", landlordId), "\"rev-0\"");

        // Then
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).contains("landlord " + landlordId + " is not one of this profile's landlords");
        assertThat(mongoTemplate.findAll(DraftRevision.class)).hasSize(1);
    }

    @Test
    void patch_whenTheDraftNamesAnUnknownLender_expected400() {
        // When
        ResponseEntity<String> refused = save(naming("lenderIds", "no-such-lender"), "\"rev-0\"");

        // Then
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).contains("lender no-such-lender is not one of this profile's lenders");
    }

    @Test
    void patch_whenANamedRecordWasDeletedSince_savesAChangeElsewhere() {
        // Given — named, then removed as retention would
        String landlordId = landlordOf(profileId);
        save(naming("landlordIds", landlordId), "\"rev-0\"");
        mongoTemplate.remove(mongoTemplate.findById(landlordId, Landlord.class));

        // When
        ResponseEntity<String> saved = save(ADD_EMPLOYMENT.formatted("175000"), "\"rev-1\"");

        // Then
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void inputsAt_whenThreeSavesAreReplayed_isTheStateAfterTheSecond() {
        // Given — 100, then 200, then 300 added
        save(ADD_EMPLOYMENT.formatted("100"), "\"rev-0\"");
        save(ADD_EMPLOYMENT.formatted("200"), "\"rev-1\"");
        save(ADD_EMPLOYMENT.formatted("300"), "\"rev-2\"");

        // When
        String second = call(HttpMethod.GET, OWNER, "/drafts/" + draftId + "/revisions/2", null, null).getBody();

        // Then
        assertThat(second).contains("100", "200").doesNotContain("300");
        assertThat(call(HttpMethod.GET, OWNER, "/drafts/" + draftId + "/revisions/4", null, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void patch_whenTheDraftIsReady_returnsItToDraftAndRecordsThat() {
        // Given
        status("{\"status\": \"READY\"}");

        // When
        ResponseEntity<String> saved = save(ADD_EMPLOYMENT.formatted("1"), "\"rev-0\"");

        // Then
        assertThat(saved.getBody()).contains("\"status\":\"DRAFT\"");
        assertThat(mongoTemplate.findAll(DraftRevision.class).stream().filter(DraftRevision::isReopened)).hasSize(1);
    }

    @Test
    void patch_whenTheDraftIsActedOn_isFrozen() {
        // Given
        status("{\"status\": \"READY\"}");
        status("{\"status\": \"ACTED_ON\"}");

        // When
        ResponseEntity<String> frozen = save(ADD_EMPLOYMENT.formatted("1"), "\"rev-0\"");

        // Then — T11-6
        assertThat(frozen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mongoTemplate.findAll(DraftRevision.class)).hasSize(1);
    }

    @Test
    void changeStatus_whenTheMoveIsNotInTheLifeCycle_expected400() {
        // When / Then — straight from DRAFT to filed
        assertThat(status("{\"status\": \"ACTED_ON\"}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void changeStatus_whenActedOnWithARegime_recordsOneElectionThatBindsLaterYears() {
        // Given
        status("{\"status\": \"READY\"}");

        // When
        status("{\"status\": \"ACTED_ON\", \"regimeCode\": \"FIRST_SCHEDULE_RATES\"}");

        // Then
        RegimeElectionHistory history = mongoTemplate.findAll(RegimeElectionHistory.class).getFirst();
        assertThat(history.getEvents()).hasSize(1);
        assertThat(history.isElectedBy(TaxYearId.of(2027))).isTrue();
        assertThat(history.isElectedBy(TaxYearId.of(2025))).isFalse();
    }

    @Test
    void anyRoute_whenAnotherAccountNamesTheDraftUnderItsOwnPath_expected400() {
        // When / Then
        assertThat(call(HttpMethod.GET, OTHER, "/drafts/" + draftId, null, null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.PATCH, OTHER, "/drafts/" + draftId, ADD_EMPLOYMENT.formatted("1"), "\"rev-0\"")
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDraft_whenFiveAreMadeForOneYear_keepsThemAll() {
        // Given — T11-1: the first made in aDraft
        for (int label = 2; label <= 5; label++) {
            call(HttpMethod.POST, OWNER, "/profiles/" + profileId + "/workspaces/2026/drafts", "{\"label\": \"Draft " + label + "\"}", null);
        }

        // When / Then
        assertThat(mongoTemplate.findAll(FilingDraft.class)).hasSize(5);
        assertThat(mongoTemplate.findAll(TaxYearWorkspace.class)).hasSize(1);
    }

    @Test
    void patch_whenTwoSavesRaceOnOnePath_oneWinsAndTheOtherNamesThePath() throws Exception {
        // Given — T11-2
        ExecutorService threads = Executors.newFixedThreadPool(2);
        Callable<ResponseEntity<String>> save = () -> save(ADD_EMPLOYMENT.formatted("1"), "\"rev-0\"");

        // When
        List<ResponseEntity<String>> both = threads.invokeAll(List.of(save, save)).stream().map(DraftIntegrationTest::resultOf).toList();
        threads.shutdown();

        // Then
        assertThat(both).extracting(ResponseEntity::getStatusCode).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
        String loser = both.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).findFirst().orElseThrow().getBody();
        assertThat(loser).contains("\"conflictingPaths\":[\"/entered/employments/-\"]");
        assertThat(mongoTemplate.findAll(DraftRevision.class)).hasSize(2);
    }

    private static ResponseEntity<String> resultOf(Future<ResponseEntity<String>> future) {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void patch_whenTheWinningSaveCommitsDuringTheRetry_namesTheConflictingPath() throws Exception {
        // Given — W-310: revision 1 written by another save whose transaction has not committed
        ExecutorService threads = Executors.newFixedThreadPool(2);
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> winner = threads.submit(() -> heldSave(written, release));
        assertThat(written.await(10, TimeUnit.SECONDS)).isTrue();

        // When — the winner commits 100 ms after the loser is sent, inside the loser's retries
        Future<ResponseEntity<String>> loser = threads.submit(() -> save(ADD_EMPLOYMENT.formatted("2"), "\"rev-0\""));
        Thread.sleep(100);
        release.countDown();
        ResponseEntity<String> answer = loser.get(10, TimeUnit.SECONDS);
        winner.get(10, TimeUnit.SECONDS);
        threads.shutdown();

        // Then
        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(answer.getBody()).contains("moved on to revision 1", "\"conflictingPaths\":[\"/entered/employments/-\"]");
        assertThat(mongoTemplate.findAll(DraftRevision.class)).hasSize(2);
    }

    @Test
    void patch_whenTheWinningSaveOutlastsEveryRetry_saysItHadNotFinished() throws Exception {
        // Given — W-310: revision 1 written by another save held open until the loser has its answer
        ExecutorService threads = Executors.newSingleThreadExecutor();
        CountDownLatch written = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> winner = threads.submit(() -> heldSave(written, release));
        assertThat(written.await(10, TimeUnit.SECONDS)).isTrue();

        // When
        ResponseEntity<String> answer = save(ADD_EMPLOYMENT.formatted("2"), "\"rev-0\"");
        release.countDown();
        winner.get(10, TimeUnit.SECONDS);
        threads.shutdown();

        // Then
        assertThat(answer.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(answer.getBody()).contains("Another save of this draft had not finished; read the draft and send again")
                .doesNotContain("moved on to revision");
        assertThat(mongoTemplate.findAll(DraftRevision.class)).hasSize(2);
    }

    /** Writes revision 1 inside a transaction that stays open until released, so the race is the same on every run. */
    private void heldSave(CountDownLatch written, CountDownLatch release) {
        JsonNode patch = JsonMapper.builder().build().readTree(ADD_EMPLOYMENT.formatted("1"));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            draftService.patch(OWNER, draftId, 0, patch);
            written.countDown();
            try {
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });
    }

    @Test
    void patch_whenSavesTouchDisjointPaths_theLoserResendsAndBothApply() {
        // Given — T11-3
        save("[{\"op\": \"replace\", \"path\": \"/filing/dueDate\", \"value\": \"2027-07-31\"}]", "\"rev-0\"");
        String other = ADD_EMPLOYMENT.formatted("500");

        // When
        ResponseEntity<String> stale = save(other, "\"rev-0\"");
        ResponseEntity<String> resent = save(other, "\"rev-1\"");

        // Then
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody()).contains("\"conflictingPaths\":[]");
        assertThat(resent.getHeaders().getETag()).isEqualTo("\"rev-2\"");
        assertThat(resent.getBody()).contains("2027-07-31", "500");
    }

    @Test
    void inputsAt_whenEachSaveIsReplayed_isByteIdenticalToWhatWasRead() {
        // Given — T11-4
        String blank = mongoTemplate.findAll(FilingDraft.class).getFirst().getInputsJson();
        save(ADD_EMPLOYMENT.formatted("100"), "\"rev-0\"");
        String first = mongoTemplate.findAll(FilingDraft.class).getFirst().getInputsJson();
        save(ADD_EMPLOYMENT.formatted("200"), "\"rev-1\"");

        // When / Then
        assertThat(call(HttpMethod.GET, OWNER, "/drafts/" + draftId + "/revisions/0", null, null).getBody()).isEqualTo(blank);
        assertThat(call(HttpMethod.GET, OWNER, "/drafts/" + draftId + "/revisions/1", null, null).getBody()).isEqualTo(first);
    }

    @Test
    void patch_whenAFigureIsSaved_storesTheDraftAndItsRevisionsEncryptedAndRecordsTheirReads() {
        // Given
        save(ADD_EMPLOYMENT.formatted("175000"), "\"rev-0\"");

        // When
        call(HttpMethod.GET, OWNER, "/drafts/" + draftId, null, null);
        String atOne = call(HttpMethod.GET, OWNER, "/drafts/" + draftId + "/revisions/1", null, null).getBody();

        // Then — DR 14 O1.1: the figure is read back but is in no stored document; O4: both reads recorded
        assertThat(atOne).contains("175000");
        assertThat(mongoTemplate.getCollection("tax_filing_drafts").find().first().getString("inputs_json")).startsWith("k1:");
        mongoTemplate.getCollection("tax_filing_drafts").find()
                .forEach(stored -> assertThat(stored.toJson()).doesNotContain("175000", "BASIC"));
        mongoTemplate.getCollection("tax_draft_revisions").find()
                .forEach(stored -> assertThat(stored.toJson()).doesNotContain("175000", "BASIC"));
        assertThat(mongoTemplate.findAll(AccessRecord.class))
                .extracting(AccessRecord::getSubject, AccessRecord::getSubjectId)
                .containsExactlyInAnyOrder(tuple(ReadSubject.DRAFT, draftId), tuple(ReadSubject.DRAFT_REVISION, draftId));
    }

    /** A salary, a tenancy for the whole of TY 2026-27 and a due date, saved as revision 1. */
    private void renting() {
        ResponseEntity<String> saved = save("""
                [{"op": "add", "path": "/entered/employments/-", "value": {
                  "components": [{"code": {"value": "BASIC"}, "annualAmount": {"amountInRupees": 1600000}}],
                  "termsIncludeDearnessAllowance": false, "employerKind": "OTHER",
                  "dearnessAllowanceCountsForRetirement": false, "companyEmployer": null}},
                 {"op": "add", "path": "/entered/tenancies/-", "value": {
                  "occupied": {"from": "2026-04-01", "to": "2027-03-31"}, "location": "MUMBAI",
                  "ownedByAssessee": false, "rentPaid": {"amountInRupees": 960000}}},
                 {"op": "replace", "path": "/filing/dueDate", "value": "2027-07-31"}]""", "\"rev-0\"");
        assertThat(saved.getStatusCode()).as(saved.getBody()).isEqualTo(HttpStatus.OK);
    }

    private JsonNode computed() {
        ResponseEntity<String> computed = call(HttpMethod.POST, OWNER, "/drafts/" + draftId + "/compute",
                "{\"regime\": \"FIRST_SCHEDULE_RATES\"}", null);
        assertThat(computed.getStatusCode()).as(computed.getBody()).isEqualTo(HttpStatus.OK);
        return JsonMapper.builder().build().readTree(computed.getBody()).get("tax");
    }

    /** W-119 C6a: what a person can show is the product's record; recording it changes no figure and no fingerprint. */
    @Test
    void patch_whenEvidenceIsRecorded_keepsItInTheRevisionAndMovesNoFigureOrFingerprint() {
        // Given — computed before any evidence is recorded
        renting();
        JsonNode before = computed();

        // When — eight of the tenancy's twelve months receipted
        ResponseEntity<String> saved = save(
                "[{\"op\": \"add\", \"path\": \"/evidence\", \"value\": {\"receiptedMonths\": [8]}}]", "\"rev-1\"");
        JsonNode after = computed();

        // Then
        assertThat(saved.getStatusCode()).as(saved.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(JsonMapper.builder().build().readTree(saved.getBody()).at("/inputs/evidence/receiptedMonths/0").asInt())
                .isEqualTo(8);
        assertThat(call(HttpMethod.GET, OWNER, "/drafts/" + draftId + "/revisions/2", null, null).getBody())
                .contains("\"receiptedMonths\":[8]");
        assertThat(after.get("inputsFingerprint")).isEqualTo(before.get("inputsFingerprint"));
        assertThat(after.get("totalIncome")).isEqualTo(before.get("totalIncome"));
        assertThat(after.get("finalAmount")).isEqualTo(before.get("finalAmount"));
        assertThat(after.get("trace")).isEqualTo(before.get("trace"));
    }

    /** W-119b1: evidence for a donation is the product's record too; recording it moves no figure and no fingerprint. */
    @Test
    void patch_whenChapterViiiEvidenceIsRecorded_keepsItAndMovesNoFigureOrFingerprint() {
        // Given — a ₹10,000 donation to a named fund, computed before any evidence of it
        renting();
        ResponseEntity<String> donated = save("[{\"op\": \"add\", \"path\": \"/entered/donations/-\", \"value\":"
                + " {\"clause\": \"NAMED_FUND\", \"amount\": {\"amountInRupees\": 10000}, \"paidInCash\": false,"
                + " \"reportedByDonee\": false}}]", "\"rev-1\"");
        assertThat(donated.getStatusCode()).as(donated.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode before = computed();

        // When — a receipt for ₹4,000 of it
        ResponseEntity<String> saved = save("[{\"op\": \"add\", \"path\": \"/evidence/chapterViii\", \"value\":"
                + " {\"donations\": [{\"amountInRupees\": 4000}]}}]", "\"rev-2\"");
        JsonNode after = computed();
        ResponseEntity<String> beyond = save("[{\"op\": \"replace\", \"path\":"
                + " \"/evidence/chapterViii/donations/0/amountInRupees\", \"value\": 12000}]", "\"rev-3\"");

        // Then
        assertThat(saved.getStatusCode()).as(saved.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, OWNER, "/drafts/" + draftId + "/revisions/3", null, null).getBody())
                .contains("\"chapterViii\":{\"donations\":[{\"amountInRupees\":4000}]}");
        assertThat(after.get("inputsFingerprint")).isEqualTo(before.get("inputsFingerprint"));
        assertThat(after.get("finalAmount")).isEqualTo(before.get("finalAmount"));
        assertThat(after.get("trace")).isEqualTo(before.get("trace"));
        assertThat(beyond.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(beyond.getBody()).contains("/evidence/chapterViii/donations/0 must be from nil to");
    }

    @Test
    void patch_whenEvidenceDoesNotFitTheEntries_expected400NamingIt() {
        // Given
        renting();

        // When — thirteen months of a twelve-month tenancy; months for two tenancies when one is entered
        ResponseEntity<String> beyond = save(
                "[{\"op\": \"add\", \"path\": \"/evidence\", \"value\": {\"receiptedMonths\": [13]}}]", "\"rev-1\"");
        ResponseEntity<String> unmatched = save(
                "[{\"op\": \"add\", \"path\": \"/evidence\", \"value\": {\"receiptedMonths\": [8, 8]}}]", "\"rev-1\"");

        // Then
        assertThat(beyond.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(beyond.getBody()).contains("/evidence/receiptedMonths/0", "12 months");
        assertThat(unmatched.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unmatched.getBody()).contains("/evidence/receiptedMonths holds 2 but /entered/tenancies holds 1");
    }

    @Test
    void claimIntegrity_whenSixMonthsAreReceipted_showsTheGapAndMovesNothingStored() {
        // Given — basic 16,00,000, HRA 4,80,000, rent 9,60,000 in Mumbai, the old regime elected; computed once, so one
        // computation is stored. Rent for six of twelve months is 4,80,000, less a tenth of salary 1,60,000 is 3,20,000,
        // under Mumbai's half of salary 8,00,000 and the allowance 4,80,000: 4,80,000 allowed, 3,20,000 evidenced, 1,60,000
        renting();
        save("""
                [{"op": "add", "path": "/entered/employments/0/components/-",
                  "value": {"code": {"value": "HRA"}, "annualAmount": {"amountInRupees": 480000}}},
                 {"op": "add", "path": "/evidence", "value": {"receiptedMonths": [6]}}]""", "\"rev-1\"");
        status("{\"status\": \"READY\"}");
        status("{\"status\": \"ACTED_ON\", \"regimeCode\": \"FIRST_SCHEDULE_RATES\"}");
        String fingerprint = computed().get("inputsFingerprint").asString();
        long stored = mongoTemplate.count(new Query(), DraftComputation.class);

        // When
        ResponseEntity<String> response = call(HttpMethod.GET, OWNER, "/drafts/" + draftId + "/claim-integrity", null, null);

        // Then
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode panel = JsonMapper.builder().build().readTree(response.getBody());
        assertThat(panel.get("rows").size()).isEqualTo(1);
        assertThat(panel.at("/rows/0/rule").asString()).isEqualTo("SAL-HOUSE-RENT-ALLOWANCE");
        assertThat(panel.at("/rows/0/allowed/amountInRupees").decimalValue()).isEqualByComparingTo("480000");
        assertThat(panel.at("/rows/0/evidenced/amountInRupees").decimalValue()).isEqualByComparingTo("320000");
        assertThat(panel.at("/rows/0/gap/amountInRupees").decimalValue()).isEqualByComparingTo("160000");
        assertThat(panel.at("/rows/0/says").toString()).contains("6 months not yet receipted", "not all on file");
        assertThat(panel.get("notIncluded").toString()).contains("landlords together").doesNotContain("W-119b1");
        assertThat(mongoTemplate.count(new Query(), DraftComputation.class)).isEqualTo(stored);
        assertThat(computed().get("inputsFingerprint").asString()).isEqualTo(fingerprint);
    }

    @Test
    void documentChecklist_whenSixMonthsAreReceipted_namesWhatIsMissingAndWhomToAskAndStoresNothing() {
        // Given — basic 16,00,000, HRA 4,80,000, rent 9,60,000 in Mumbai (above r.205(2) Sl. 1's 1,00,000, so the
        // landlord's particulars are asked), six of twelve months receipted, no landlord linked; computed once
        renting();
        save("""
                [{"op": "add", "path": "/entered/employments/0/components/-",
                  "value": {"code": {"value": "HRA"}, "annualAmount": {"amountInRupees": 480000}}},
                 {"op": "add", "path": "/evidence", "value": {"receiptedMonths": [6]}}]""", "\"rev-1\"");
        status("{\"status\": \"READY\"}");
        status("{\"status\": \"ACTED_ON\", \"regimeCode\": \"FIRST_SCHEDULE_RATES\"}");
        String fingerprint = computed().get("inputsFingerprint").asString();
        long stored = mongoTemplate.count(new Query(), DraftComputation.class);

        // When
        ResponseEntity<String> response =
                call(HttpMethod.GET, OWNER, "/drafts/" + draftId + "/document-checklist", null, null);

        // Then — 12 − 6 = 6 months outstanding; the landlord is asked for their particulars
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode checklist = JsonMapper.builder().build().readTree(response.getBody());
        JsonNode rent = null;
        for (JsonNode claim : checklist.get("claims")) {
            if (claim.get("rule").asString().equals("SAL-HOUSE-RENT-ALLOWANCE")) {
                rent = claim.get("documents");
            }
        }
        assertThat(rent).as(response.getBody()).isNotNull();
        assertThat(rent.at("/0/name").asString()).isEqualTo("Rent receipts");
        assertThat(rent.at("/0/state").asString()).isEqualTo("NOT_HELD");
        assertThat(rent.at("/0/outstanding/months").asLong()).isEqualTo(6L);
        assertThat(rent.at("/1/name").asString()).isEqualTo("Your landlord's particulars");
        assertThat(rent.at("/1/state").asString()).isEqualTo("NOT_HELD");
        assertThat(rent.at("/1/from").asString()).isEqualTo("LANDLORD");
        assertThat(checklist.get("notIncluded").toString()).contains("not yet catalogued");
        assertThat(mongoTemplate.count(new Query(), DraftComputation.class)).isEqualTo(stored);
        assertThat(computed().get("inputsFingerprint").asString()).isEqualTo(fingerprint);
    }

    @Test
    void firstFigure_whenGrossPayAndADueDateAreGiven_computesBothRegimesCoarseAndAnswersStepOne() throws Exception {
        // Given — gross pay 12,00,000, the return due 31-07-2027, the profile born 1985

        // When
        ResponseEntity<String> response = call(HttpMethod.POST, OWNER, "/profiles/" + profileId + "/workspaces/2026/first-figure",
                """
                {"label": "First figure", "grossPay": {"amountInRupees": 1200000}, "dueDate": "2027-07-31"}""", null);

        // Then — new regime: 11,25,000 taxable, 20,000 + 32,500 = 52,500, all rebated (≤ 60,000): 0. Old regime:
        // 11,50,000 taxable, 1,12,500 + 45,000 = 1,57,500 + cess 6,300 = 1,63,800; band down to 88,920 (W-122b's rooms)
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode first = JsonMapper.builder().build().readTree(response.getBody());
        String created = first.get("draftId").asString();
        assertThat(first.get("revision").asInt()).isEqualTo(1);
        assertThat(first.get("figures").size()).isEqualTo(2);
        JsonNode byDefault = first.at("/figures/0");
        assertThat(byDefault.at("/tax/regime").asString()).isEqualTo("NEW_TAX_REGIME");
        assertThat(byDefault.at("/tax/bill/amountInRupees").decimalValue()).isEqualByComparingTo("0");
        assertThat(byDefault.get("coarse").asBoolean()).isTrue();
        JsonNode old = first.at("/figures/1");
        assertThat(old.at("/tax/regime").asString()).isEqualTo("FIRST_SCHEDULE_RATES");
        assertThat(old.at("/tax/bill/amountInRupees").decimalValue()).isEqualByComparingTo("163800");
        assertThat(old.get("coarse").asBoolean()).isTrue();
        assertThat(old.at("/band/lower/amountInRupees").decimalValue()).isEqualByComparingTo("88920");
        assertThat(mongoTemplate.count(new Query(), DraftComputation.class)).isEqualTo(2);
        JsonNode steps = JsonMapper.builder().build().readTree(
                call(HttpMethod.GET, OWNER, "/drafts/" + created + "/onboarding", null, null).getBody());
        assertThat(steps.at("/steps/0/answered").asBoolean()).isTrue();
        assertThat(steps.at("/next").asString()).isEqualTo("PAY_STRUCTURE_AND_RENT");
    }

    @Test
    void compareRegimes_whenAStepIsOpenAndThenAllAreAnswered_isCoarseAndThenNot() throws Exception {
        // Given — an employment and a due date, no onboarding step answered
        renting();

        // When
        JsonNode open = JsonMapper.builder().build().readTree(
                call(HttpMethod.POST, OWNER, "/drafts/" + draftId + "/compare-regimes", null, null).getBody());
        save("""
                [{"op": "add", "path": "/onboarding/answered/-", "value": "PAY"},
                 {"op": "add", "path": "/onboarding/answered/-", "value": "PAY_STRUCTURE_AND_RENT"},
                 {"op": "add", "path": "/onboarding/answered/-", "value": "EMPLOYER_FUNDS"},
                 {"op": "add", "path": "/onboarding/answered/-", "value": "INVESTMENTS_AND_INSURANCE"},
                 {"op": "add", "path": "/onboarding/answered/-", "value": "OTHER_INCOME_AND_PROPERTY"}]""", "\"rev-1\"");
        JsonNode answered = JsonMapper.builder().build().readTree(
                call(HttpMethod.POST, OWNER, "/drafts/" + draftId + "/compare-regimes", null, null).getBody());

        // Then — the engine's fields as before, the label beside them
        assertThat(open.get("chosen").asString()).isNotBlank();
        assertThat(open.get("priced").size()).isPositive();
        assertThat(open.get("coarse").asBoolean()).isTrue();
        assertThat(open.get("says").asString()).startsWith("This is a coarse figure");
        assertThat(answered.get("coarse").asBoolean()).isFalse();
        assertThat(answered.path("says").isNull() || answered.path("says").isMissingNode()).isTrue();
    }

    @Test
    void firstFigure_whenNoDueDateIsGiven_expected400AndCreatesNoDraft() {
        // Given
        long drafts = mongoTemplate.count(new Query(), FilingDraft.class);

        // When
        ResponseEntity<String> response = call(HttpMethod.POST, OWNER, "/profiles/" + profileId + "/workspaces/2026/first-figure",
                "{\"label\": \"First figure\", \"grossPay\": {\"amountInRupees\": 1200000}}", null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("/dueDate");
        assertThat(mongoTemplate.count(new Query(), FilingDraft.class)).isEqualTo(drafts);
    }

    @Test
    void documentChecklist_whenAnotherAccountAsks_expected400() {
        // When / Then
        assertThat(call(HttpMethod.GET, OTHER, "/drafts/" + draftId + "/document-checklist", null, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void claimIntegrity_whenAnotherAccountAsks_expected400() {
        // When / Then
        assertThat(call(HttpMethod.GET, OTHER, "/drafts/" + draftId + "/claim-integrity", null, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** W-122a C1a: which steps are answered is the product's record; marking one changes no figure and no fingerprint. */
    @Test
    void patch_whenAnOnboardingStepIsAnswered_keepsItInTheRevisionAndMovesNoFigureOrFingerprint() {
        // Given — computed before any step is answered
        renting();
        JsonNode before = computed();

        // When — step 4 answered: nothing to invest
        ResponseEntity<String> saved = save("[{\"op\": \"add\", \"path\": \"/onboarding/answered/-\","
                + " \"value\": \"INVESTMENTS_AND_INSURANCE\"}]", "\"rev-1\"");
        JsonNode after = computed();
        ResponseEntity<String> read = call(HttpMethod.GET, OWNER, "/drafts/" + draftId + "/onboarding", null, null);

        // Then
        assertThat(saved.getStatusCode()).as(saved.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET, OWNER, "/drafts/" + draftId + "/revisions/2", null, null).getBody())
                .contains("\"answered\":[\"INVESTMENTS_AND_INSURANCE\"]");
        assertThat(after.get("inputsFingerprint")).isEqualTo(before.get("inputsFingerprint"));
        assertThat(after.get("finalAmount")).isEqualTo(before.get("finalAmount"));
        assertThat(after.get("trace")).isEqualTo(before.get("trace"));
        assertThat(read.getStatusCode()).as(read.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode onboarding = JsonMapper.builder().build().readTree(read.getBody());
        assertThat(onboarding.get("revision").asInt()).isEqualTo(2);
        assertThat(onboarding.get("next").asString()).isEqualTo("PAY");
        assertThat(onboarding.at("/steps/3/step").asString()).isEqualTo("INVESTMENTS_AND_INSURANCE");
        assertThat(onboarding.at("/steps/3/answered").asBoolean()).isTrue();
        assertThat(onboarding.at("/steps/0/answered").asBoolean()).isFalse();
    }

    @Test
    void patch_whenAnOnboardingStepIsUnknownOrTheRevisionStale_isRefused() {
        // Given
        renting();

        // When
        ResponseEntity<String> unknown = save(
                "[{\"op\": \"add\", \"path\": \"/onboarding/answered/-\", \"value\": \"SALARY\"}]", "\"rev-1\"");
        ResponseEntity<String> stale = save(
                "[{\"op\": \"add\", \"path\": \"/onboarding/answered/-\", \"value\": \"PAY\"}]", "\"rev-0\"");

        // Then
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknown.getBody()).contains("/onboarding/answered");
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void onboarding_whenAnotherAccountAsks_expected400() {
        // When / Then
        assertThat(call(HttpMethod.GET, OTHER, "/drafts/" + draftId + "/onboarding", null, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
