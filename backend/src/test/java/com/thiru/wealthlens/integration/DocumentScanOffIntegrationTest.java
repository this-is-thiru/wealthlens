package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxplanning.documents.store.StoredDocument;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.testsupport.TestPdfs;
import io.restassured.RestAssured;
import java.net.URI;
import java.util.List;
import java.util.Optional;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/** W-131h (C1a, C3a): with the scan off, as staging and prod ship it, an upload is stored without reaching the scanner, recorded unscanned. */
@TestPropertySource(properties = "app.tax.documents.scan.enabled=false")
class DocumentScanOffIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "owner@example.com";

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

    private HttpHeaders headers(MediaType type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(OWNER));
        headers.setContentType(type);
        return headers;
    }

    private String aProfile() {
        client().exchange(URI.create("http://localhost:" + RestAssured.port + "/tax/user/" + OWNER + "/profiles"), HttpMethod.POST,
                new HttpEntity<>("""
                        {"name": "Self", "pan": "ABCDE1234F", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                         "residentialStatus": "RESIDENT"}""", headers(MediaType.APPLICATION_JSON)), String.class);
        return mongoTemplate.findAll(TaxpayerProfile.class).getFirst().getId();
    }

    private ResponseEntity<String> upload(String profileId, byte[] file) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(file) {
            @Override
            public String getFilename() {
                return "form-130.pdf";
            }
        });
        form.add("profileId", profileId);
        form.add("taxYear", "2026");
        form.add("documentType", "FORM_130");
        return client().exchange(URI.create("http://localhost:" + RestAssured.port + "/tax/user/" + OWNER + "/documents"), HttpMethod.POST,
                new HttpEntity<>(form, headers(MediaType.MULTIPART_FORM_DATA)), String.class);
    }

    @Test
    void upload_whenTheScanIsOff_storesItUnscannedWithoutCallingTheScanner() {
        // Given — a scanner that would refuse with a 503 if it were asked
        String profileId = aProfile();
        int commandsBefore = CLAMD.commands().size();
        CLAMD.answerWith(stream -> Optional.empty());

        // When
        ResponseEntity<String> response;
        try {
            response = upload(profileId, TestPdfs.pages(1));
        } finally {
            CLAMD.answerAsClamd();
        }

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(CLAMD.commands()).hasSize(commandsBefore);
        List<StoredDocument> stored = mongoTemplate.findAll(StoredDocument.class);
        assertThat(stored).singleElement().satisfies(document -> assertThat(document.isScanned()).isFalse());
        assertThat(mongoTemplate.getCollection("tax_document_bytes.files").countDocuments()).isOne();
    }
}
