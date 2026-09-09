package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * The master switch, over the real filter chain and the real exception mapping.
 *
 * <p>Only this tier can show the status code. {@code ControllerAdviser} maps
 * {@code ServiceUnavailableException} to 503, and a unit test asserting the exception type proves
 * the service refuses without proving the caller is told anything useful — a refusal that surfaces
 * as 500 reads as a crash, and one that surfaces as 400 sends the caller hunting for a fault in
 * their own request that is not there.
 *
 * <p>The only class running with {@code engine-enabled=false}, so every other integration class
 * staying green is itself the evidence that the switch ships on.
 */
@TestPropertySource(properties = "app.charges.engine-enabled=false")
class ChargeEngineDisabledIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "kill-switch-it@example.com";

    @Autowired
    private UserChargeRepository userChargeRepository;

    @Test
    void simulate_whenTheEngineIsDisabled_answers503RatherThan400Or500() {
        // When
        ResponseEntity<String> response = exchange("/charges/simulate", HttpMethod.POST,
                generateToken(EMAIL), """
                        {"brokerName":"ZERODHA","assetType":"EQUITY","event":"SELL",
                         "transactionDate":"2025-06-10","quantity":100,"price":1000,"exchange":"NSE"}
                        """);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(response.getBody()).contains("engine-enabled");
    }

    @Test
    void backfill_whenTheEngineIsDisabled_refusesAndWritesNothing() {
        // When
        ResponseEntity<String> response = exchange("/charges/backfill/user/" + EMAIL, HttpMethod.POST,
                generateToken(EMAIL, "SUPER_USER"), null);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(userChargeRepository.findByEmailOrderByTransactionDateDesc(EMAIL)).isEmpty();
    }

    /**
     * Reading what was already computed stays available. The switch stops the engine producing new
     * numbers; it does not hide the ones already recorded, which are what an operator needs to look
     * at while deciding whether to turn it back on.
     */
    @Test
    void readingRecordedChargesStillWorksWhileTheEngineIsDisabled() {
        // When
        ResponseEntity<String> response =
                exchange("/user-charges/user/" + EMAIL, HttpMethod.GET, generateToken(EMAIL), null);

        // Then
        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.OK.value());
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });

        return restTemplate.exchange(
                URI.create("http://localhost:" + RestAssured.port + path),
                method, new HttpEntity<>(body, headers), String.class);
    }
}
