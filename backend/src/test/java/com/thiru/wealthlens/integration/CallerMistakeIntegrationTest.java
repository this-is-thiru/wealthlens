package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.RestAssured;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * A request the caller got wrong answers with the caller's status on the real routes, through the real filter chain
 * (W-263): one request for each kind of binding failure the controllers can meet, on a route of each module that has
 * it. {@code ControllerAdviserTest} holds the same wording per bound type; only this tier shows a route is reached.
 */
class CallerMistakeIntegrationTest extends AbstractIntegrationTest {

    private static final String OPERATOR = "caller-mistake-it@example.com";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ResponseEntity<String> exchange(HttpMethod method, String path, MediaType contentType, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setBearerAuth(generateToken(OPERATOR, "SUPER_USER"));
        RestTemplate client = new RestTemplate();
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return client.exchange(URI.create("http://localhost:" + RestAssured.port + path), method, new HttpEntity<>(body, headers),
                String.class);
    }

    private static void assertAnswered(ResponseEntity<String> response, HttpStatus status, String message) {
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(status);
        assertThat(JSON.readTree(response.getBody()).get("message").asString()).isEqualTo(message);
    }

    @Test
    void due_whenTheFrequencyIsNotOne_expected400ListingTheFrequencies() {
        assertAnswered(exchange(HttpMethod.GET, "/charge-accounts/due?frequency=WEEKLY&billedThrough=2026-11-01",
                MediaType.APPLICATION_JSON, null), HttpStatus.BAD_REQUEST,
                "Parameter 'frequency' must be one of [ANNUALLY, QUARTERLY], but was 'WEEKLY'");
    }

    @Test
    void due_whenBilledThroughIsNotADate_expected400NamingTheDateFormat() {
        assertAnswered(exchange(HttpMethod.GET, "/charge-accounts/due?frequency=ANNUALLY&billedThrough=01-11-2026",
                MediaType.APPLICATION_JSON, null), HttpStatus.BAD_REQUEST,
                "Parameter 'billedThrough' must be a date as yyyy-MM-dd, but was '01-11-2026'");
    }

    @Test
    void backfill_whenDryRunIsNeitherTrueNorFalse_expected400() {
        assertAnswered(exchange(HttpMethod.POST,
                "/profit-and-loss/v2-figures/backfill?dryRun=maybe&recordedBefore=2026-11-01T00:00:00",
                MediaType.APPLICATION_JSON, null), HttpStatus.BAD_REQUEST,
                "Parameter 'dryRun' must be true or false, but was 'maybe'");
    }

    @Test
    void backfill_whenRecordedBeforeIsNotADateTime_expected400NamingTheFormat() {
        assertAnswered(exchange(HttpMethod.POST, "/profit-and-loss/v2-figures/backfill?recordedBefore=yesterday",
                MediaType.APPLICATION_JSON, null), HttpStatus.BAD_REQUEST,
                "Parameter 'recordedBefore' must be a date and time as yyyy-MM-dd'T'HH:mm:ss, but was 'yesterday'");
    }

    @Test
    void updatePriority_whenThePriorityIsNotANumber_expected400NamingAWholeNumber() {
        assertAnswered(exchange(HttpMethod.PUT, "/corporate-action/update/priority/any-id/high", MediaType.APPLICATION_JSON,
                null), HttpStatus.BAD_REQUEST, "Parameter 'priority' must be a whole number, but was 'high'");
    }

    @Test
    void uploadTransactions_whenTheFileIsMissing_expected400NamingThePart() {
        // Given — a multipart request carrying the quarter and no file
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("quarter", "Q1");

        // When / Then
        assertAnswered(exchange(HttpMethod.POST, "/portfolio/user/" + OPERATOR + "/upload-transactions",
                MediaType.MULTIPART_FORM_DATA, parts), HttpStatus.BAD_REQUEST, "Required part 'file' is not present");
    }

    @Test
    void due_whenTheMethodIsNotServed_expected405() {
        ResponseEntity<String> response = exchange(HttpMethod.DELETE,
                "/charge-accounts/due?frequency=ANNUALLY&billedThrough=2026-11-01", MediaType.APPLICATION_JSON, null);

        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void addTransactionV2_whenTheBodyIsNotJson_expected415() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/portfolio/user/" + OPERATOR + "/transaction/v2",
                MediaType.APPLICATION_PDF, "not json");

        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }
}
