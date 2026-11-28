package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedField;
import com.thiru.wealthlens.taxplanning.documents.ingest.ExtractedValue;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParseOutcome;
import com.thiru.wealthlens.taxplanning.documents.ingest.ParserRun;
import com.thiru.wealthlens.taxplanning.documents.store.StoredConfirmation;
import com.thiru.wealthlens.taxplanning.documents.store.StoredConfirmationRepository;
import com.thiru.wealthlens.taxplanning.documents.store.StoredDocumentRepository;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttempt;
import com.thiru.wealthlens.taxplanning.documents.store.StoredParseAttemptRepository;
import com.thiru.wealthlens.taxplanning.documents.store.TaxDocumentBytesRepository;
import com.thiru.wealthlens.taxplanning.jobs.ParseDocuments;
import com.thiru.wealthlens.taxplanning.jobs.ProfileRemoval;
import com.thiru.wealthlens.taxplanning.jobs.TaxJobRunner;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfileRepository;
import com.thiru.wealthlens.taxplanning.protection.AccessRecordRepository;
import com.thiru.wealthlens.testsupport.FakeClamd;
import com.thiru.wealthlens.testsupport.TestPdfs;
import io.restassured.RestAssured;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

/** W-131d1 (D-765): upload a certificate, see what was read, confirm it — with the switch on (the integration-test yaml). */
class DocumentIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "owner@example.com";
    private static final String STRANGER = "stranger@example.com";

    @Autowired
    private TaxJobRunner runner;

    @Autowired
    private ParseDocuments parseDocuments;

    @Autowired
    private StoredDocumentRepository documents;

    @Autowired
    private StoredParseAttemptRepository attempts;

    @Autowired
    private StoredConfirmationRepository confirmations;

    @Autowired
    private TaxDocumentBytesRepository documentBytesRows;

    @Autowired
    private AccessRecordRepository accessRecords;

    @Autowired
    private TaxpayerProfileRepository profiles;

    @Autowired
    private ProfileRemoval profileRemoval;

    private String profileId;

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

    private ResponseEntity<String> call(HttpMethod method, String caller, String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(caller));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return client().exchange(URI.create("http://localhost:" + RestAssured.port + "/api/v1/me/tax" + path), method,
                new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> upload(String caller, String profile, int taxYear, String type, byte[] file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(caller));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(file) {
            @Override
            public String getFilename() {
                return "form-130.pdf";
            }
        });
        form.add("profileId", profile);
        form.add("taxYear", String.valueOf(taxYear));
        form.add("documentType", type);
        return client().exchange(URI.create("http://localhost:" + RestAssured.port + "/api/v1/me/tax/documents"), HttpMethod.POST,
                new HttpEntity<>(form, headers), String.class);
    }

    private String storedDocumentId() {
        return documents.findAll().getFirst().getId();
    }

    /** A reading of Part A's PAN and row 12, as the parser would store it. */
    private String readingOf(String documentId, String pan) {
        String attemptId = "attempt-of-" + documentId;
        attempts.save(StoredParseAttempt.of(new ParseAttempt(attemptId, documentId, new ParserRun("FORM_130", "1",
                LocalDateTime.parse("2027-06-01T09:01:00"), LocalDateTime.parse("2027-06-01T09:02:00")), ParseOutcome.PARTIAL,
                List.of(new ExtractedField("partA.employee.pan", Optional.of(pan), Optional.of(new ExtractedValue.TextRead(pan)), 1,
                                Optional.empty(), Optional.empty()),
                        new ExtractedField("partC.annexureI.row12", Optional.of("10,00,000"),
                                Optional.of(new ExtractedValue.MoneyRead(Money.of("1000000"))), 0.9, Optional.empty(), Optional.empty())),
                List.of("an unread line"), List.of())));
        return attemptId;
    }

    @BeforeEach
    void aProfile() {
        call(HttpMethod.POST, OWNER, "/profiles", """
                {"name": "Self", "pan": "ABCDE1234F", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                 "residentialStatus": "RESIDENT"}""");
        profileId = profiles.findAll().getFirst().getId();
    }

    @Test
    void upload_whenAFormNo130ForTaxYear2026_27_storesItOnceAndReturnsItAgainForTheSameBytes() {
        // Given
        byte[] pdf = TestPdfs.pages(2);

        // When
        ResponseEntity<String> first = upload(OWNER, profileId, 2026, "FORM_130", pdf);
        ResponseEntity<String> again = upload(OWNER, profileId, 2026, "FORM_130", pdf);

        // Then
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).contains("\"pages\":2", "\"newlyStored\":true", "\"taxYear\":\"2026-27\"");
        assertThat(again.getBody()).contains("\"newlyStored\":false", "\"documentId\":\"" + storedDocumentId() + "\"");
        assertThat(documents.findAll()).singleElement().satisfies(document -> assertThat(document.isScanned()).isTrue());
        assertThat(documentBytesRows.count()).isOne();
    }

    @Test
    void upload_whenTheYearHasNoLayoutOrTheFormNoParser_refusesAndStoresNothing() {
        // When — TY 2025-26's pack has no Form No. 130 layout (D-748); no parser reads Form No. 122
        ResponseEntity<String> oldYear = upload(OWNER, profileId, 2025, "FORM_130", TestPdfs.pages(1));
        ResponseEntity<String> noParser = upload(OWNER, profileId, 2026, "FORM_122", TestPdfs.pages(1));

        // Then
        assertThat(oldYear.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(oldYear.getBody()).contains("FORM_130 has no layout for tax year 2025-26");
        assertThat(noParser.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(noParser.getBody()).contains("No parser reads FORM_122 yet");
        assertThat(documents.findAll()).isEmpty();
    }

    @Test
    void upload_whenTheScanFindsMalware_refusesAndStoresNothing() {
        // Given — a Form No. 130 carrying the fake scanner's marker after its end, which PDF readers ignore
        byte[] pdf = TestPdfs.pages(1);
        byte[] infected = java.util.Arrays.copyOf(pdf, pdf.length + FakeClamd.MALWARE_MARKER.length);
        System.arraycopy(FakeClamd.MALWARE_MARKER, 0, infected, pdf.length, FakeClamd.MALWARE_MARKER.length);

        // When
        ResponseEntity<String> response = upload(OWNER, profileId, 2026, "FORM_130", infected);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("This file failed the malware scan and was not stored");
        assertThat(documents.findAll()).isEmpty();
        assertThat(documentBytesRows.count()).isZero();
    }

    @Test
    void upload_whenTheScannerClosesWithoutAnswering_isUnavailableAndStoresNothing() {
        // Given
        CLAMD.answerWith(stream -> Optional.empty());

        // When
        ResponseEntity<String> response;
        try {
            response = upload(OWNER, profileId, 2026, "FORM_130", TestPdfs.pages(1));
        } finally {
            CLAMD.answerAsClamd();
        }

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).contains("The malware scan could not be completed; nothing was stored");
        assertThat(documents.findAll()).isEmpty();
        assertThat(documentBytesRows.count()).isZero();
    }

    @Test
    void upload_whenTheProfileIsAnotherAccounts_refuses() {
        // When
        ResponseEntity<String> response = upload(STRANGER, profileId, 2026, "FORM_130", TestPdfs.pages(1));

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("This account holds no such profile");
    }

    @Test
    void parse_whenNotYetReadThenRead_saysWaitingThenShowsTheReadingAndRecordsTheRead() {
        // Given
        upload(OWNER, profileId, 2026, "FORM_130", TestPdfs.pages(1));
        String documentId = storedDocumentId();

        // When / Then — before any attempt
        assertThat(call(HttpMethod.GET, OWNER, "/documents/" + documentId + "/parse", null).getBody()).contains("\"state\":\"WAITING\"");

        // When — the job reads it with the real Form No. 130 parser
        runner.run(parseDocuments);
        ResponseEntity<String> read = call(HttpMethod.GET, OWNER, "/documents/" + documentId + "/parse", null);

        // Then
        assertThat(read.getBody()).contains("\"state\":\"READ\"", "\"employeePan\":\"NOT_READ\"", "\"confirmed\":false");
        assertThat(accessRecords.findAll()).anySatisfy(record -> assertThat(record.getSubjectId()).isEqualTo(documentId));
    }

    @Test
    void parse_whenTheDocumentIsAnotherAccounts_refusesInTheSameWordsAsAnAbsentOne() {
        // Given
        upload(OWNER, profileId, 2026, "FORM_130", TestPdfs.pages(1));

        // When
        ResponseEntity<String> stranger = call(HttpMethod.GET, STRANGER, "/documents/" + storedDocumentId() + "/parse", null);
        ResponseEntity<String> absent = call(HttpMethod.GET, OWNER, "/documents/no-such-document/parse", null);

        // Then
        assertThat(stranger.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stranger.getBody()).contains("This account holds no such document");
        assertThat(absent.getBody()).contains("This account holds no such document");
    }

    @Test
    void confirm_whenThePanIsTheProfilesAndARowIsCorrected_storesTheConfirmationOnce() {
        // Given — row 12 read as ₹10,00,000, corrected to ₹9,50,000
        upload(OWNER, profileId, 2026, "FORM_130", TestPdfs.pages(1));
        String documentId = storedDocumentId();
        String attemptId = readingOf(documentId, "ABCDE1234F");
        String body = """
                {"attemptId": "%s", "accepted": ["partA.employee.pan"], "corrected": {"partC.annexureI.row12": "950000"},
                 "rejected": []}""".formatted(attemptId);

        // When
        ResponseEntity<String> confirmed = call(HttpMethod.POST, OWNER, "/documents/" + documentId + "/confirm", body);
        ResponseEntity<String> again = call(HttpMethod.POST, OWNER, "/documents/" + documentId + "/confirm", body);

        // Then
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody()).contains("\"confirmed\":true", "\"employeePan\":\"MATCHES\"");
        StoredConfirmation stored = confirmations.findById(attemptId).orElseThrow();
        assertThat(stored.toRecord().acceptedFields()).containsEntry("partC.annexureI.row12",
                new ExtractedValue.MoneyRead(Money.of("950000")));
        assertThat(stored.toRecord().confirmedBy()).isEqualTo(OWNER);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(again.getBody()).contains("This reading is already confirmed");
    }

    @Test
    void confirm_whenThePanIsAnotherPersons_refuses() {
        // Given
        upload(OWNER, profileId, 2026, "FORM_130", TestPdfs.pages(1));
        String documentId = storedDocumentId();
        String attemptId = readingOf(documentId, "PQRSX5678K");

        // When
        ResponseEntity<String> response = call(HttpMethod.POST, OWNER, "/documents/" + documentId + "/confirm", """
                {"attemptId": "%s", "accepted": ["partA.employee.pan", "partC.annexureI.row12"], "corrected": {}, "rejected": []}"""
                .formatted(attemptId));

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("This certificate is for another PAN than the profile's");
        assertThat(confirmations.findById(attemptId)).isEmpty();
    }

    @Test
    void confirm_whenACorrectionDoesNotRead_refusesNamingTheRow() {
        // Given
        upload(OWNER, profileId, 2026, "FORM_130", TestPdfs.pages(1));
        String documentId = storedDocumentId();
        String attemptId = readingOf(documentId, "ABCDE1234F");

        // When
        ResponseEntity<String> response = call(HttpMethod.POST, OWNER, "/documents/" + documentId + "/confirm", """
                {"attemptId": "%s", "accepted": ["partA.employee.pan"], "corrected": {"partC.annexureI.row12": "nine lakh"},
                 "rejected": []}""".formatted(attemptId));

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("partC.annexureI.row12");
    }

    @Test
    void erase_whenTheProfileIsErased_removesTheRecordsOfItsDocumentsBeingRead() {
        // Given
        upload(OWNER, profileId, 2026, "FORM_130", TestPdfs.pages(1));
        String documentId = storedDocumentId();
        call(HttpMethod.GET, OWNER, "/documents/" + documentId + "/parse", null);

        // When — ProfileRemoval.remove is itself @Transactional; no outer wrapping needed
        profileRemoval.remove(profiles.findById(profileId).orElseThrow());

        // Then
        assertThat(accessRecords.findAll()).noneSatisfy(record -> assertThat(record.getSubjectId()).isEqualTo(documentId));
    }
}
