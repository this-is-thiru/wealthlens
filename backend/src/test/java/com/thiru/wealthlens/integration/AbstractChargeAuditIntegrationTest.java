package com.thiru.wealthlens.integration;

import io.restassured.RestAssured;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The W-264b audit's scenarios run against the <b>shipped</b> rate cards, seeded the way a deployment is seeded
 * ({@code POST /charges/seed}), where {@link ChargesIntegrationTest} writes its own cards. Expected amounts are
 * worked by hand from the card files (D-611); this class holds the requests both the plain and the flag classes make.
 */
abstract class AbstractChargeAuditIntegrationTest extends AbstractIntegrationTest {

    protected static final String EMAIL = "charge-audit@example.com";

    private final RestTemplate rest = lenientRestTemplate();

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void seedTheShippedCards() {
        ResponseEntity<String> seeded = call(HttpMethod.POST, "/charges/seed", admin(), null);
        if (seeded.getStatusCode().value() != 200) {
            throw new IllegalStateException("Seeding failed: " + seeded.getBody());
        }
    }

    protected ResponseEntity<String> simulate(Map<String, Object> body) {
        return call(HttpMethod.POST, "/charges/simulate", generateToken(EMAIL), body);
    }

    protected ResponseEntity<String> tradeV2(Map<String, Object> body) {
        return call(HttpMethod.POST, "/portfolio/user/" + EMAIL + "/transaction/v2", generateToken(EMAIL), body);
    }

    protected ResponseEntity<String> tradeV1(Map<String, Object> body) {
        return call(HttpMethod.POST, "/portfolio/user/" + EMAIL + "/transaction", generateToken(EMAIL), body);
    }

    protected ResponseEntity<String> get(String path) {
        return call(HttpMethod.GET, path, generateToken(EMAIL), null);
    }

    protected ResponseEntity<String> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange(URI.create("http://localhost:" + RestAssured.port + path), method,
                new HttpEntity<>(body, headers), String.class);
    }

    protected String admin() {
        return generateToken(EMAIL, "SUPER_USER");
    }

    protected JsonNode json(ResponseEntity<String> response) {
        return mapper.readTree(response.getBody());
    }

    /** A breakdown's {@code amountByCode}, in the order the engine applied the lines. */
    protected static Map<String, Double> amounts(JsonNode breakdown) {
        Map<String, Double> amounts = new LinkedHashMap<>();
        breakdown.get("amountByCode").properties().forEach(entry -> amounts.put(entry.getKey(),
                entry.getValue().asDouble()));
        return amounts;
    }

    protected static Map<String, Object> simulation(String segment, String event, double quantity, double price,
                                                    String date) {
        Map<String, Object> body = new HashMap<>();
        body.put("brokerName", "ZERODHA");
        body.put("assetType", "EQUITY");
        body.put("segment", segment);
        body.put("event", event);
        body.put("stockCode", "INFY");
        body.put("quantity", quantity);
        body.put("price", price);
        body.put("transactionDate", date);
        return body;
    }

    protected static Map<String, Object> trade(String stockCode, String segment, String side, double quantity,
                                               double price, String date) {
        Map<String, Object> body = new HashMap<>();
        body.put("stockCode", stockCode);
        body.put("stockName", stockCode);
        body.put("exchangeName", "NSE");
        body.put("brokerName", "ZERODHA");
        body.put("assetType", "EQUITY");
        body.put("segment", segment);
        body.put("price", price);
        body.put("quantity", quantity);
        body.put("transactionType", side);
        body.put("accountType", "SELF");
        body.put("accountHolder", "Self");
        body.put("transactionDate", date);
        return body;
    }

    /** The one rule a published test card carries; every primitive is named, or the body does not bind. */
    protected static Map<String, Object> flatBrokerage(double amountRupees) {
        Map<String, Object> rule = new HashMap<>();
        rule.put("code", "BROKERAGE");
        rule.put("displayName", "Brokerage");
        rule.put("category", "BROKERAGE");
        rule.put("basis", "FLAT");
        rule.put("side", "BOTH");
        rule.put("events", List.of("BUY", "SELL"));
        rule.put("flatAmount", amountRupees);
        rule.put("rounding", "HALF_UP_2");
        rule.put("taxable", true);
        rule.put("active", true);
        rule.put("perLot", false);
        rule.put("appliesToCorporateActions", false);
        rule.put("order", 10);
        return rule;
    }

    private static RestTemplate lenientRestTemplate() {
        RestTemplate template = new RestTemplate(new JdkClientHttpRequestFactory());
        template.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return template;
    }
}
