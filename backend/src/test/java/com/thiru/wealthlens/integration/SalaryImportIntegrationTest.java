package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import io.restassured.RestAssured;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/** W-133a (F28): a salary workbook previewed against a draft through the endpoint, read in the child process; nothing stored. */
class SalaryImportIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "owner@example.com";
    private static final String STRANGER = "stranger@example.com";

    private String draftId;

    private static RestTemplate client() {
        RestTemplate client = new RestTemplate(new JdkClientHttpRequestFactory());
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return client;
    }

    private ResponseEntity<String> call(HttpMethod method, String caller, String path, Object body, MediaType type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(caller));
        headers.setContentType(type);
        return client().exchange(URI.create("http://localhost:" + RestAssured.port + "/tax/user/" + caller + path), method,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> upload(String caller, byte[] workbook) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(workbook) {
            @Override
            public String getFilename() {
                return "ctc.xlsx";
            }
        });
        return call(HttpMethod.POST, caller, "/drafts/" + draftId + "/salary-import", form, MediaType.MULTIPART_FORM_DATA);
    }

    private static byte[] workbook() throws IOException {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Salary");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Component");
            header.createCell(1).setCellValue("Annual Amount");
            Row basic = sheet.createRow(1);
            basic.createCell(0).setCellValue("Basic salary");
            basic.createCell(1).setCellValue(600000);
            Row other = sheet.createRow(2);
            other.createCell(0).setCellValue("Conveyance");
            other.createCell(1).setCellValue(19200);
            book.write(out);
            return out.toByteArray();
        }
    }

    @BeforeEach
    void aDraft() {
        call(HttpMethod.POST, OWNER, "/profiles", """
                {"name": "Self", "pan": "ABCDE1234F", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                 "residentialStatus": "RESIDENT"}""", MediaType.APPLICATION_JSON);
        String profileId = mongoTemplate.findAll(TaxpayerProfile.class).getFirst().getId();
        call(HttpMethod.POST, OWNER, "/profiles/" + profileId + "/workspaces/2026/drafts", "{\"label\": \"Mine\"}", MediaType.APPLICATION_JSON);
        draftId = mongoTemplate.findAll(FilingDraft.class).getFirst().getId();
    }

    @Test
    void preview_whenTheOwnerUploadsACtcSheet_matchesWhatItCanReturnsTheRestAndStoresNothing() throws IOException {
        // Given
        int revisionBefore = mongoTemplate.findById(draftId, FilingDraft.class).getCurrentRevision();

        // When
        ResponseEntity<String> response = upload(OWNER, workbook());

        // Then — "Basic salary" is BASIC's name; "Conveyance" comes back; the draft is untouched
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"code\":\"BASIC\"", "\"label\":\"Conveyance\"", "\"taxYear\":\"2026-27\"");
        assertThat(mongoTemplate.findById(draftId, FilingDraft.class).getCurrentRevision()).isEqualTo(revisionBefore);
    }

    private ResponseEntity<String> apply(String caller, String ifMatch, byte[] workbook, String decisions) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(workbook) {
            @Override
            public String getFilename() {
                return "ctc.xlsx";
            }
        });
        HttpHeaders part = new HttpHeaders();
        part.setContentType(MediaType.APPLICATION_JSON);
        form.add("decisions", new HttpEntity<>(decisions, part));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(caller));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setIfMatch(ifMatch);
        return client().exchange(URI.create("http://localhost:" + RestAssured.port + "/tax/user/" + caller + "/drafts/" + draftId
                + "/salary-import/apply"), HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
    }

    private static final String DECISIONS = """
            {"mappings": [{"row": 3, "code": "CONVEYANCE_ALLOWANCE"}], "leftOut": [],
             "employment": {"termsIncludeDearnessAllowance": false, "employerKind": "OTHER",
                            "dearnessAllowanceCountsForRetirement": false, "companyEmployer": null}}""";

    private String currentTag() {
        return "\"rev-" + mongoTemplate.findById(draftId, FilingDraft.class).getCurrentRevision() + "\"";
    }

    @Test
    void apply_whenTheReturnedRowIsMapped_addsOneEmploymentAndTheRevisionNamesTheWorkbook() throws IOException {
        // Given
        byte[] workbook = workbook();
        int before = mongoTemplate.findById(draftId, FilingDraft.class).getCurrentRevision();

        // When
        ResponseEntity<String> applied = apply(OWNER, currentTag(), workbook, DECISIONS);

        // Then — BASIC 6,00,000 matched, row 3 mapped to the conveyance allowance at 19,200, one revision on
        assertThat(applied.getStatusCode()).as(applied.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(applied.getBody()).contains("\"BASIC\"", "\"CONVEYANCE_ALLOWANCE\"", "\"revision\":" + (before + 1));
        ResponseEntity<String> revisions = call(HttpMethod.GET, OWNER, "/drafts/" + draftId + "/revisions", null, MediaType.APPLICATION_JSON);
        assertThat(revisions.getBody()).contains("\"rowsApplied\":[2,3]", "\"rowsMapped\":[3]", "\"workbookSha256\":\"");
    }

    @Test
    void apply_whenTheSameWorkbookIsAppliedAgain_refusesNamingTheRevision() throws IOException {
        // Given
        byte[] workbook = workbook();
        apply(OWNER, currentTag(), workbook, DECISIONS);
        int importedAt = mongoTemplate.findById(draftId, FilingDraft.class).getCurrentRevision();

        // When
        ResponseEntity<String> again = apply(OWNER, currentTag(), workbook, DECISIONS);

        // Then
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(again.getBody()).contains("This workbook was already imported into the draft at revision " + importedAt);
    }

    @Test
    void apply_whenIfMatchIsStale_isAConflict() throws IOException {
        // When
        ResponseEntity<String> stale = apply(OWNER, "\"rev-99\"", workbook(), DECISIONS);

        // Then
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void apply_whenAnEmploymentFactIsMissing_refusesThroughTheDraftsOwnCheck() throws IOException {
        // Given — no employerKind
        String noKind = DECISIONS.replace("\"employerKind\": \"OTHER\",", "");

        // When
        ResponseEntity<String> refused = apply(OWNER, currentTag(), workbook(), noKind);

        // Then
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void preview_whenTheDraftIsAnotherAccounts_refuses() throws IOException {
        // When / Then
        assertThat(upload(STRANGER, workbook()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void preview_whenTheFileIsNoWorkbook_refusesAsUnreadable() {
        // When
        ResponseEntity<String> response = upload(OWNER, "label,amount".getBytes(StandardCharsets.US_ASCII));

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("The workbook could not be read");
    }
}
