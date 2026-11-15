package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.protection.AccessRecord;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A stored computation's PDF over HTTP (W-123a C4a): who may read it, and that the read is recorded; and the listing that
 * finds every report of a profile before it is erased (W-123b C1a).
 */
class ComputationReportIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "reporter@example.com";
    private static final String OTHER = "other@example.com";

    private String profileId;
    private String receipt;

    private <T> ResponseEntity<T> call(HttpMethod method, String caller, String path, String body, Class<T> type,
            String ifMatch) {
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
                new HttpEntity<>(body, headers), type);
    }

    /** Basic 1,75,000, the default regime: one computation stored. */
    @BeforeEach
    void aComputation() {
        call(HttpMethod.POST, OWNER, "/profiles", """
                {"name": "Self", "pan": "ABCDE1234F", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                 "residentialStatus": "RESIDENT"}""", String.class, null);
        profileId = mongoTemplate.findAll(TaxpayerProfile.class).getFirst().getId();
        receipt = compute(draftOf(profileId, "Mine"));
    }

    private String draftOf(String profile, String label) {
        ResponseEntity<String> created = call(HttpMethod.POST, OWNER, "/profiles/" + profile + "/workspaces/2026/drafts",
                "{\"label\": \"" + label + "\"}", String.class, null);
        assertThat(created.getStatusCode()).as(created.getBody()).isEqualTo(HttpStatus.OK);
        String draftId = JsonMapper.builder().build().readTree(created.getBody()).get("id").asString();
        ResponseEntity<String> saved = call(HttpMethod.PATCH, OWNER, "/drafts/" + draftId, """
                [{"op": "add", "path": "/entered/employments/-", "value": {
                  "components": [{"code": {"value": "BASIC"}, "annualAmount": {"amountInRupees": 175000}}],
                  "termsIncludeDearnessAllowance": false, "employerKind": "OTHER",
                  "dearnessAllowanceCountsForRetirement": false, "companyEmployer": null}},
                 {"op": "replace", "path": "/filing/dueDate", "value": "2027-07-31"}]""", String.class, "\"rev-0\"");
        assertThat(saved.getStatusCode()).as(saved.getBody()).isEqualTo(HttpStatus.OK);
        return draftId;
    }

    private String compute(String draftId) {
        ResponseEntity<String> computed = call(HttpMethod.POST, OWNER, "/drafts/" + draftId + "/compute",
                "{\"regime\": \"NEW_TAX_REGIME\"}", String.class, null);
        assertThat(computed.getStatusCode()).as(computed.getBody()).isEqualTo(HttpStatus.OK);
        return JsonMapper.builder().build().readTree(computed.getBody()).at("/tax/receipt").asString();
    }

    private ResponseEntity<String> listing(String caller, String profile) {
        return call(HttpMethod.GET, caller, "/profiles/" + profile + "/computations", null, String.class, null);
    }

    @Test
    void report_whenTheOwnerAsks_isAPdfOfThatReceiptAndTheReadIsRecorded() throws IOException {
        // When
        ResponseEntity<byte[]> report = call(HttpMethod.GET, OWNER, "/computations/" + receipt + "/report", null,
                byte[].class, null);

        // Then
        assertThat(report.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(report.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        PdfReader reader = new PdfReader(report.getBody());
        assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains(receipt);
        assertThat(mongoTemplate.findAll(AccessRecord.class))
                .anySatisfy(read -> {
                    assertThat(read.getSubject()).isEqualTo(ReadSubject.COMPUTATION);
                    assertThat(read.getSubjectId()).isEqualTo(receipt);
                });
    }

    @Test
    void report_whenAnotherAccountAsks_expected400() {
        // When / Then
        assertThat(call(HttpMethod.GET, OTHER, "/computations/" + receipt + "/report", null, String.class, null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void report_whenTheProfileIsHeldForErasure_expected400() {
        // Given — DR 14 O5: a held profile is read by nobody
        TaxpayerProfile profile = mongoTemplate.findById(profileId, TaxpayerProfile.class);
        profile.setErasureRequestedAt(LocalDateTime.of(2026, 11, 7, 10, 0));
        mongoTemplate.save(profile);

        // When / Then
        assertThat(call(HttpMethod.GET, OWNER, "/computations/" + receipt + "/report", null, String.class, null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void computations_whenTwoDraftsOfAProfileAreComputed_listsEveryOneNewestFirstAndNoOtherProfiles() throws IOException {
        // Given — "Mine" computed once (setup); a second draft computed twice; another profile of the account once
        String second = draftOf(profileId, "Second");
        String older = compute(second);
        String newest = compute(second);
        call(HttpMethod.POST, OWNER, "/profiles", """
                {"name": "Spouse", "pan": "FGHIJ5678K", "kind": "INDIVIDUAL", "dateOfBirth": "1987-02-01",
                 "residentialStatus": "RESIDENT"}""", String.class, null);
        String spouse = mongoTemplate.findAll(TaxpayerProfile.class).stream().map(TaxpayerProfile::getId)
                .filter(id -> !id.equals(profileId)).findFirst().orElseThrow();
        String spouses = compute(draftOf(spouse, "Spouse's"));

        // When
        ResponseEntity<String> response = listing(OWNER, profileId);

        // Then
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode rows = JsonMapper.builder().build().readTree(response.getBody());
        assertThat(rows.findValuesAsString("receipt")).containsExactly(newest, older, receipt).doesNotContain(spouses);
        assertThat(rows.at("/0/draftLabel").asString()).isEqualTo("Second");
        assertThat(rows.at("/0/taxYear").asString()).isEqualTo("2026-27");
        assertThat(rows.at("/0/regime").asString()).isEqualTo("NEW_TAX_REGIME");
        assertThat(rows.at("/0/revision").asInt()).isEqualTo(1);
        assertThat(rows.at("/2/draftLabel").asString()).isEqualTo("Mine");
        assertThat(rows.at("/0/computedAt").asString()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}");
        for (String each : List.of(newest, older, receipt)) {
            assertThat(call(HttpMethod.GET, OWNER, "/computations/" + each + "/report", null, byte[].class, null)
                    .getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void computations_whenAnotherAccountAsks_expected400() {
        // When / Then
        assertThat(listing(OTHER, profileId).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void computations_whenTheProfileIsHeldForErasure_refusesTheListingAndEachReport() {
        // Given — the owner's own erasure request
        ResponseEntity<String> requested = call(HttpMethod.POST, OWNER, "/profiles/" + profileId + "/erasure", null,
                String.class, null);
        assertThat(requested.getStatusCode()).as(requested.getBody()).isEqualTo(HttpStatus.ACCEPTED);

        // When / Then
        assertThat(listing(OWNER, profileId).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.GET, OWNER, "/computations/" + receipt + "/report", null, String.class, null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
