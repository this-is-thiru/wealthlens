package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.workspace.DraftRevision;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import com.thiru.wealthlens.taxplanning.workspace.RegimeElectionHistory;
import com.thiru.wealthlens.taxplanning.workspace.TaxYearWorkspace;
import io.restassured.RestAssured;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/** A draft over HTTP and into Mongo: its revisions, its locking, its freeze and its election (W-099, W-100, W-108, W-110, W-082d; D-441). */
class DraftIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "drafter@example.com";
    private static final String OTHER = "other@example.com";
    private static final String ADD_EMPLOYMENT = """
            [{"op": "add", "path": "/employments/-", "value": {
              "components": [{"code": {"value": "BASIC"}, "annualAmount": {"amountInRupees": %s}}],
              "termsIncludeDearnessAllowance": false, "employerKind": "OTHER",
              "dearnessAllowanceCountsForRetirement": false, "companyEmployer": null}}]""";

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
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody()).contains("175000").doesNotContain("999");
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

        // When / Then
        assertThat(save(ADD_EMPLOYMENT.formatted("1"), "\"rev-0\"").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
}
