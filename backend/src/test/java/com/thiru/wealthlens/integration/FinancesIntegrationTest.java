package com.thiru.wealthlens.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.thiru.wealthlens.finance.dto.FinanceRequest;
import com.thiru.wealthlens.finance.dto.FinanceResponse;
import com.thiru.wealthlens.finance.dto.enums.CalculationType;
import com.thiru.wealthlens.finance.service.FinancesService;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public class FinancesIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_EMAIL = "finance@test.com";

    @Autowired
    private FinancesService financesService;

    private RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });
        return rt;
    }

    private String baseUrl() {
        return "http://localhost:" + RestAssured.port;
    }

    @Test
    void calculate_whenEmi_shouldReturnEmi() {
        // GIVEN
        String token = generateToken(TEST_EMAIL);

        FinanceRequest request = new FinanceRequest();
        request.setCalculationType(CalculationType.EMI);
        request.setPrincipalAmount(100000.0);
        request.setRateOfInterest(12.0);
        request.setTimePeriod(12);

        // WHEN
        String url = baseUrl() + "/finances/calculate";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<FinanceRequest> entity = new HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        ResponseEntity<FinanceResponse> response = rt.exchange(URI.create(url), HttpMethod.POST, entity, FinanceResponse.class);

        // THEN
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getEmiAmount() > 0);
    }

    @Test
    void calculate_whenTotalAmount_shouldReturnTotal() {
        // GIVEN
        String token = generateToken(TEST_EMAIL);

        FinanceRequest request = new FinanceRequest();
        request.setCalculationType(CalculationType.TOTAL_AMOUNT);
        request.setPrincipalAmount(50000.0);
        request.setRateOfInterest(10.0);
        request.setTimePeriod(6);

        // WHEN
        String url = baseUrl() + "/finances/calculate";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<FinanceRequest> entity = new HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        ResponseEntity<FinanceResponse> response = rt.exchange(URI.create(url), HttpMethod.POST, entity, FinanceResponse.class);

        // THEN
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        // Final amount should be > 0 for valid inputs
        assertTrue(response.getBody().getFinalAmount() > 0.0);
    }

    @Test
    void calculate_whenRateOfInterest_shouldPassthrough() {
        // GIVEN
        String token = generateToken(TEST_EMAIL);

        FinanceRequest request = new FinanceRequest();
        request.setCalculationType(CalculationType.RATE_OF_INTEREST);
        request.setRateOfInterest(8.5);

        // WHEN
        String url = baseUrl() + "/finances/calculate";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<FinanceRequest> entity = new HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        ResponseEntity<FinanceResponse> response = rt.exchange(URI.create(url), HttpMethod.POST, entity, FinanceResponse.class);

        // THEN
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(8.5, response.getBody().getRateOfInterest(), 0.01);
    }

    @Test
    void calculate_whenNone_shouldThrow400() {
        // GIVEN
        String token = generateToken(TEST_EMAIL);

        FinanceRequest request = new FinanceRequest();
        request.setCalculationType(CalculationType.NONE);

        // WHEN
        String url = baseUrl() + "/finances/calculate";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<FinanceRequest> entity = new HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.POST, entity, String.class);

        // THEN
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void calculate_whenZeroPrincipal_shouldReturnZero() {
        // GIVEN
        String token = generateToken(TEST_EMAIL);

        FinanceRequest request = new FinanceRequest();
        request.setCalculationType(CalculationType.EMI);
        request.setPrincipalAmount(0.0);
        request.setRateOfInterest(12.0);
        request.setTimePeriod(12);

        // WHEN
        String url = baseUrl() + "/finances/calculate";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<FinanceRequest> entity = new HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        ResponseEntity<FinanceResponse> response = rt.exchange(URI.create(url), HttpMethod.POST, entity, FinanceResponse.class);

        // THEN - EMI with 0 principal should be 0
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().getEmiAmount());
    }
}
