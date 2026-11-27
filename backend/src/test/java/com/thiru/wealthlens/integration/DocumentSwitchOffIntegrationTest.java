package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxplanning.documents.store.StoredDocumentRepository;
import io.restassured.RestAssured;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/** W-131d1 (D-765 C2a): with the switch off, as every profile yaml ships it, each documents endpoint is a 503 and writes nothing. */
@TestPropertySource(properties = "app.tax.documents.enabled=false")
class DocumentSwitchOffIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "owner@example.com";

    @Autowired
    private StoredDocumentRepository documents;

    private HttpStatus status(HttpMethod method, String path, Object body, MediaType type) {
        RestTemplate client = new RestTemplate(new JdkClientHttpRequestFactory());
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(OWNER));
        headers.setContentType(type);
        return HttpStatus.valueOf(client.exchange(URI.create("http://localhost:" + RestAssured.port + "/api/v1/me/tax" + path), method,
                new HttpEntity<>(body, headers), String.class).getStatusCode().value());
    }

    @Test
    void everyDocumentsEndpoint_whenTheSwitchIsOff_isUnavailable() {
        // Given
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource("%PDF-1.4".getBytes(StandardCharsets.US_ASCII)) {
            @Override
            public String getFilename() {
                return "form-130.pdf";
            }
        });
        form.add("profileId", "p");
        form.add("taxYear", "2026");
        form.add("documentType", "FORM_130");

        // When / Then
        assertThat(status(HttpMethod.POST, "/documents", form, MediaType.MULTIPART_FORM_DATA)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(status(HttpMethod.GET, "/documents/d/parse", null, MediaType.APPLICATION_JSON)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(status(HttpMethod.POST, "/documents/d/confirm",
                "{\"attemptId\": \"a\", \"accepted\": [], \"corrected\": {}, \"rejected\": []}", MediaType.APPLICATION_JSON))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(status(HttpMethod.GET, "/drafts/d/reconciliation", null, MediaType.APPLICATION_JSON)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(documents.findAll()).isEmpty();
    }
}
