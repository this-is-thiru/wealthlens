package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.protection.AccessRecord;
import com.thiru.wealthlens.taxplanning.protection.ReadSubject;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
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
import tools.jackson.databind.json.JsonMapper;

/** A stored computation's PDF over HTTP (W-123a C4a): who may read it, and that the read is recorded. */
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
        call(HttpMethod.POST, OWNER, "/profiles/" + profileId + "/workspaces/2026/drafts", "{\"label\": \"Mine\"}",
                String.class, null);
        String draftId = mongoTemplate.findAll(FilingDraft.class).getFirst().getId();
        ResponseEntity<String> saved = call(HttpMethod.PATCH, OWNER, "/drafts/" + draftId, """
                [{"op": "add", "path": "/entered/employments/-", "value": {
                  "components": [{"code": {"value": "BASIC"}, "annualAmount": {"amountInRupees": 175000}}],
                  "termsIncludeDearnessAllowance": false, "employerKind": "OTHER",
                  "dearnessAllowanceCountsForRetirement": false, "companyEmployer": null}},
                 {"op": "replace", "path": "/filing/dueDate", "value": "2027-07-31"}]""", String.class, "\"rev-0\"");
        assertThat(saved.getStatusCode()).as(saved.getBody()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> computed = call(HttpMethod.POST, OWNER, "/drafts/" + draftId + "/compute",
                "{\"regime\": \"NEW_TAX_REGIME\"}", String.class, null);
        assertThat(computed.getStatusCode()).as(computed.getBody()).isEqualTo(HttpStatus.OK);
        receipt = JsonMapper.builder().build().readTree(computed.getBody()).at("/tax/receipt").asString();
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
}
