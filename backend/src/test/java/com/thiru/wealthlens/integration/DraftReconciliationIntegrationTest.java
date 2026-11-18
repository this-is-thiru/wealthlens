package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.DocumentType;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.IngestedDocument;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParserRun;
import com.thiru.wealthlens.taxplanning.documents.parser.DocumentContent;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocument;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttempt;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.protection.AccessRecord;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import io.restassured.RestAssured;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * W-131d2 (D-765 C1a): a draft's confirmed Form No. 130s reconciled with its computation, on the real endpoint with the switch on
 * (the integration-test yaml). The figures are {@code DR 08} T8-1, worked by hand there: salaries 9,00,000 and 12,00,000, one
 * standard deduction of 75,000, total income 20,25,000, tax 2,06,250, cess 8,250, a bill of 2,14,500 that neither employer deducted.
 */
class DraftReconciliationIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "owner@example.com";
    private static final String STRANGER = "stranger@example.com";
    private static final String PAN = "ABCDE1234F";
    private static final String TAX_DEDUCTED = "partB.summary.total.taxDeducted";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, List<String>> readPaths = new HashMap<>();
    private String profileId;
    private String draftId;

    private ResponseEntity<String> call(HttpMethod method, String caller, String path, String body) {
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
        if (method == HttpMethod.PATCH) {
            headers.set(HttpHeaders.IF_MATCH, "\"rev-0\"");
        }
        return client.exchange(URI.create("http://localhost:" + RestAssured.port + "/tax/user/" + caller + path), method,
                new HttpEntity<>(body, headers), String.class);
    }

    /** T8-1's year: employments of 9,00,000 and 12,00,000 basic from OTHER employers, nothing at source entered. */
    @BeforeEach
    void aDraft() {
        call(HttpMethod.POST, OWNER, "/profiles", """
                {"name": "Self", "pan": "%s", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                 "residentialStatus": "RESIDENT", "holdsPortfolio": false}""".formatted(PAN));
        profileId = mongoTemplate.findAll(TaxpayerProfile.class).getFirst().getId();
        call(HttpMethod.POST, OWNER, "/profiles/" + profileId + "/workspaces/2026/drafts", "{\"label\": \"Mine\"}");
        draftId = mongoTemplate.findAll(FilingDraft.class).getFirst().getId();
        ResponseEntity<String> saved = call(HttpMethod.PATCH, OWNER, "/drafts/" + draftId, """
                [%s, %s,
                 {"op": "replace", "path": "/filing/dueDate", "value": "2027-07-31"}]""".formatted(employment(900000),
                employment(1200000)));
        assertThat(saved.getStatusCode()).as(saved.getBody()).isEqualTo(HttpStatus.OK);
    }

    private static String employment(int basic) {
        return """
                {"op": "add", "path": "/entered/employments/-", "value": {
                  "components": [{"code": {"value": "BASIC"}, "annualAmount": {"amountInRupees": %d}}],
                  "termsIncludeDearnessAllowance": false, "employerKind": "OTHER",
                  "dearnessAllowanceCountsForRetirement": false, "companyEmployer": null}}""".formatted(basic);
    }

    /** A Form No. 130 of the profile's TY 2026-27, stored as an upload stores it. */
    private String certificate(String name) {
        String documentId = "document-" + name;
        mongoTemplate.save(StoredDocument.of(new IngestedDocument(documentId, profileId, TaxYearId.of(2026), DocumentType.FORM_130,
                new DocumentContent(name.getBytes(StandardCharsets.UTF_8)).sha256(), LocalDateTime.parse("2027-06-01T09:00:00"), 2)));
        return documentId;
    }

    /**
     * A reading, as the parser stores one, of the lines {@code FORM_130.json} names by fact — Part A's PAN, TAN and period, and
     * Part C's row A, 1(e) and 4(b) where {@code partC} holds 1(e) and 4(b) — and Part B's Total of tax deducted.
     */
    private String reading(String documentId, String startedAt, String tan, String from, String to, Optional<String[]> partC,
            String taxDeducted) {
        Map<String, ExtractedValue> values = new LinkedHashMap<>();
        values.put("partA.employee.pan", new ExtractedValue.TextRead(PAN));
        values.put("partA.employer.tan", new ExtractedValue.TextRead(tan));
        values.put("partA.employer.period", new ExtractedValue.PeriodRead(LocalDate.parse(from), LocalDate.parse(to)));
        values.put(TAX_DEDUCTED, new ExtractedValue.MoneyRead(Money.of(taxDeducted)));
        partC.ifPresent(rows -> {
            values.put("partC.annexureI.rowA", new ExtractedValue.FlagRead(false));
            values.put("partC.annexureI.row1e", new ExtractedValue.MoneyRead(Money.of(rows[0])));
            values.put("partC.annexureI.row4b", new ExtractedValue.MoneyRead(Money.of(rows[1])));
        });
        List<ExtractedField> fields = new ArrayList<>();
        values.forEach((path, value) -> fields.add(new ExtractedField(path, Optional.of("printed"), Optional.of(value), 0.9,
                Optional.empty(), Optional.empty())));
        String attemptId = "attempt-" + documentId + "-" + startedAt;
        LocalDateTime started = LocalDateTime.parse("2027-06-01T" + startedAt);
        mongoTemplate.save(StoredParseAttempt.of(new ParseAttempt(attemptId, documentId, new ParserRun("FORM_130", "1", started,
                started.plusMinutes(1)), ParseOutcome.SUCCESS, fields, List.of(), List.of())));
        readPaths.put(attemptId, List.copyOf(values.keySet()));
        return attemptId;
    }

    private static Optional<String[]> partC(String row1e, String row4b) {
        return Optional.of(new String[] {row1e, row4b});
    }

    /** Confirms every row as read, but those in {@code corrected}, written anew. */
    private void confirm(String documentId, String attemptId, Map<String, String> corrected) {
        List<String> accepted = readPaths.get(attemptId).stream().filter(path -> !corrected.containsKey(path)).toList();
        ResponseEntity<String> confirmed = call(HttpMethod.POST, OWNER, "/documents/" + documentId + "/confirm",
                JSON.writeValueAsString(Map.of("attemptId", attemptId, "accepted", accepted, "corrected", corrected, "rejected", List.of())));
        assertThat(confirmed.getStatusCode()).as(confirmed.getBody()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<String> reconciliation(String caller) {
        return call(HttpMethod.GET, caller, "/drafts/" + draftId + "/reconciliation", null);
    }

    private static JsonNode body(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        return JSON.readTree(response.getBody());
    }

    private static List<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asString).toList();
    }

    private static BigDecimal rupees(JsonNode money) {
        return money.get("amountInRupees").decimalValue();
    }

    @Test
    void reconcile_whenTwoEmployersEachGrantTheStandardDeduction_isAMaterialDifferenceOfTheWholeBill() {
        // Given — T8-1: each employer its own Part C, 4(b) 75,000, 1(e) nil, nothing deducted
        String first = certificate("a");
        confirm(first, reading(first, "09:01:00", "MUMA12345B", "2026-04-01", "2026-09-30", partC("0", "75000"), "0"), Map.of());
        String second = certificate("b");
        confirm(second, reading(second, "09:01:00", "BLRA12345C", "2026-10-01", "2027-03-31", partC("0", "75000"), "0"), Map.of());

        // When
        JsonNode view = body(reconciliation(OWNER));

        // Then — Part B's Totals summed, 0 + 0, against the bill 2,14,500: −2,14,500, both employers' errors (W-130c C3a)
        JsonNode report = view.get("report");
        assertThat(report.get("verdict").asString()).isEqualTo("MATERIAL_DIFFERENCE");
        assertThat(report.get("lines")).hasSize(1);
        JsonNode line = report.get("lines").get(0);
        assertThat(line.get("printed").asString()).isEqualTo(TAX_DEDUCTED);
        assertThat(rupees(line.get("documentValue"))).isEqualByComparingTo("0");
        assertThat(rupees(line.get("computedValue"))).isEqualByComparingTo("214500");
        assertThat(rupees(line.get("difference"))).isEqualByComparingTo("-214500");
        assertThat(texts(line.get("reasons"))).containsExactly("STANDARD_DEDUCTION_TWICE", "PREVIOUS_SALARY_UNDECLARED");
        assertThat(texts(report.get("documentIds"))).containsExactlyInAnyOrder(first, second);
        assertThat(view.get("assembly").get("mode").asString()).isEqualTo("PER_EMPLOYER");
        assertThat(view.get("assembly").get("notices").toString()).contains("SAL-STDDED-001");
        assertThat(view.get("regime").asString()).isEqualTo("NEW_TAX_REGIME");
        assertThat(view.get("unconfirmedDocumentIds")).isEmpty();
        assertThat(mongoTemplate.findAll(AccessRecord.class).stream().map(AccessRecord::getSubjectId)).contains(first, second);
    }

    @Test
    void reconcile_whenTheLastEmployersOtherSalaryIsCorrected_noLongerSaysAPreviousSalaryWasUndeclared() {
        // Given — only the second employer issues Part C, its 1(e) read nil and corrected to the first employer's 9,00,000
        String first = certificate("a");
        confirm(first, reading(first, "09:01:00", "MUMA12345B", "2026-04-01", "2026-09-30", Optional.empty(), "0"), Map.of());
        String second = certificate("b");
        confirm(second, reading(second, "09:01:00", "BLRA12345C", "2026-10-01", "2027-03-31", partC("0", "75000"), "0"),
                Map.of("partC.annexureI.row1e", "900000"));

        // When
        JsonNode view = body(reconciliation(OWNER));

        // Then — the assembly reads the confirmed 1(e), so W-129's notice (TDS-SAL-12B-001) and its reason are gone
        assertThat(view.get("assembly").get("mode").asString()).isEqualTo("LAST_EMPLOYER_CONSOLIDATED");
        assertThat(view.get("assembly").get("notices").toString()).doesNotContain("TDS-SAL-12B-001");
        assertThat(view.get("report").get("lines").toString()).doesNotContain("PREVIOUS_SALARY_UNDECLARED");
    }

    @Test
    void reconcile_whenACertificateIsReadAgainAfterItWasConfirmed_usesTheConfirmedReadingAndNamesTheUnconfirmed() {
        // Given — a confirmed, then read again by a later parser and not confirmed; c read and never confirmed
        String confirmed = certificate("a");
        confirm(confirmed, reading(confirmed, "09:01:00", "MUMA12345B", "2026-04-01", "2027-03-31", partC("0", "75000"), "0"), Map.of());
        reading(confirmed, "10:01:00", "MUMA12345B", "2026-04-01", "2027-03-31", partC("0", "75000"), "0");
        String unconfirmed = certificate("c");
        reading(unconfirmed, "09:01:00", "BLRA12345C", "2026-04-01", "2027-03-31", partC("0", "75000"), "0");

        // When
        JsonNode view = body(reconciliation(OWNER));

        // Then
        assertThat(texts(view.get("report").get("documentIds"))).containsExactly(confirmed);
        assertThat(texts(view.get("unconfirmedDocumentIds"))).containsExactly(unconfirmed);
    }

    @Test
    void reconcile_whenNoCertificateIsConfirmed_refusesRatherThanSayMatched() {
        // Given
        String document = certificate("a");
        reading(document, "09:01:00", "MUMA12345B", "2026-04-01", "2027-03-31", partC("0", "75000"), "0");

        // When
        ResponseEntity<String> refused = reconciliation(OWNER);

        // Then
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).contains("No certificate of this draft's tax year is confirmed yet");
    }

    @Test
    void reconcile_whenTheDraftIsAnotherAccounts_refuses() {
        // Given
        String document = certificate("a");
        confirm(document, reading(document, "09:01:00", "MUMA12345B", "2026-04-01", "2027-03-31", partC("0", "75000"), "0"), Map.of());

        // When
        ResponseEntity<String> refused = reconciliation(STRANGER);

        // Then
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).doesNotContain("214500");
    }
}
