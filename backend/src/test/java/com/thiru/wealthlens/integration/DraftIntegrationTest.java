package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
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
    void declarations_whenTy2026_listsTheThirteenQuestionsOfR164() {
        // When
        ResponseEntity<String> read = call(HttpMethod.GET, OWNER, "/declarations/2026", null, null);

        // Then
        JsonNode questions = JsonMapper.builder().build().readTree(read.getBody());
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(questions).hasSize(13);
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
}
