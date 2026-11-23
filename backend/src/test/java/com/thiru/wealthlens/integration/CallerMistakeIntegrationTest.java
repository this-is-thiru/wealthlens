package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.RestAssured;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        RestTemplate client = new RestTemplate(new org.springframework.http.client.JdkClientHttpRequestFactory());
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
        assertThat(JSON.readTree(response.getBody()).get("detail").asString()).isEqualTo(message);
    }

    @Test
    void due_whenTheFrequencyIsNotOne_expected400ListingTheFrequencies() {
        assertAnswered(exchange(HttpMethod.GET, "/api/v1/admin/charges/charge-accounts/due?frequency=WEEKLY&billedThrough=2026-11-01",
                MediaType.APPLICATION_JSON, null), HttpStatus.BAD_REQUEST,
                "Parameter 'frequency' must be one of [ANNUALLY, QUARTERLY], but was 'WEEKLY'");
    }

    @Test
    void due_whenBilledThroughIsNotADate_expected400NamingTheDateFormat() {
        assertAnswered(exchange(HttpMethod.GET, "/api/v1/admin/charges/charge-accounts/due?frequency=ANNUALLY&billedThrough=01-11-2026",
                MediaType.APPLICATION_JSON, null), HttpStatus.BAD_REQUEST,
                "Parameter 'billedThrough' must be a date as yyyy-MM-dd, but was '01-11-2026'");
    }

    @Test
    void rebuildProfitAndLoss_whenDryRunIsNeitherTrueNorFalse_expected400() {
        // On the V2 backfill's route until W-173 removed it; the date-time wording stays in ControllerAdviserTest
        assertAnswered(exchange(HttpMethod.POST, "/api/v1/admin/portfolio/users/someone@example.com/profit-and-loss/rebuild?dryRun=maybe",
                MediaType.APPLICATION_JSON, null), HttpStatus.BAD_REQUEST, "Parameter 'dryRun' must be true or false, but was 'maybe'");
    }

    @Test
    void updatePriority_whenThePriorityIsNotANumber_expected400NamingAWholeNumber() {
        // Since W-358c3a the priority is a body field, not a path segment (D-859)
        assertAnswered(exchange(HttpMethod.PATCH, "/api/v1/admin/corporate-actions/any-id", MediaType.APPLICATION_JSON,
                Map.of("priority", "high")), HttpStatus.BAD_REQUEST, "Field 'priority' must be a whole number, but was 'high'");
    }

    @Test
    void uploadTransactions_whenTheFileIsMissing_expected400NamingThePart() {
        // Given — a multipart request carrying the quarter and no file
        LinkedMultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("quarter", "Q1");

        // When / Then
        assertAnswered(exchange(HttpMethod.POST, "/api/v1/me/portfolio/transactions/uploads",
                MediaType.MULTIPART_FORM_DATA, parts), HttpStatus.BAD_REQUEST, "Required part 'file' is not present");
    }

    @Test
    void due_whenTheMethodIsNotServed_expected405() {
        ResponseEntity<String> response = exchange(HttpMethod.DELETE,
                "/api/v1/admin/charges/charge-accounts/due?frequency=ANNUALLY&billedThrough=2026-11-01", MediaType.APPLICATION_JSON, null);

        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void addTransactionV2_whenTheBodyIsNotJson_expected415() {
        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/v1/me/portfolio/transaction/v2",
                MediaType.APPLICATION_PDF, "not json");

        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void simulate_whenThereIsNoBody_expected400SayingSo() {
        assertAnswered(exchange(HttpMethod.POST, "/api/v1/me/charges/simulate", MediaType.APPLICATION_JSON, null), HttpStatus.BAD_REQUEST,
                "The request body is missing");
    }

    @Test
    void simulate_whenThePriceIsNotANumber_expected400NamingTheFieldAndValue() {
        assertAnswered(exchange(HttpMethod.POST, "/api/v1/me/charges/simulate", MediaType.APPLICATION_JSON, "{\"price\":\"abc\"}"),
                HttpStatus.BAD_REQUEST, "Field 'price' must be a number, but was 'abc'");
    }

    @Test
    void publish_whenARuleOmitsAPrimitive_expected400NamingItsPath() {
        // W-264b scenario 4: Jackson's text named DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
        assertAnswered(exchange(HttpMethod.POST, "/api/v1/admin/charges/charge-schedules", MediaType.APPLICATION_JSON,
                "{\"scheduleCode\":\"X\",\"rules\":[{\"code\":\"STT\"}]}"), HttpStatus.BAD_REQUEST,
                "Field 'rules[0].perLot' must be true or false");
    }

    @Test
    void registerChargeAccount_whenTheBodyNamesNothing_expected400NamingEachRequiredFieldAndStoresNothing() {
        // W-358d4: an empty body was stored as an account with every field null — no broker, no demat id, no AMC frequency
        assertAnswered(exchange(HttpMethod.POST, "/api/v1/me/charges/charge-accounts", MediaType.APPLICATION_JSON, "{}"), HttpStatus.BAD_REQUEST,
                "Field 'amcFrequency' must not be null; field 'brokerName' must not be null; field 'dematAccountId' must not be blank");
        assertThat(chargeAccountRepository.count()).isZero();
    }

    @Test
    void performCorporateAction_whenTheActionHasNoType_expected400NamingIt() {
        // W-358d4: a missing type crashed the perform with a 500 naming the code's insides
        assertAnswered(exchange(HttpMethod.PUT, "/api/v1/admin/corporate-actions/perform", MediaType.APPLICATION_JSON, "{\"stockCode\": \"RELIANCE\"}"),
                HttpStatus.BAD_REQUEST, "Field 'type' must not be null");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "/api/v1/me/portfolio/transaction    | stockCode       | A trade must name its stock code",
        "/api/v1/me/portfolio/transaction    | brokerName      | A trade must name its broker",
        "/api/v1/me/portfolio/transaction    | transactionType | A trade must say whether it is a BUY or a SELL",
        "/api/v1/me/portfolio/transaction    | transactionDate | A trade must carry its transaction date; it is not taken to be today",
        "/api/v1/me/portfolio/transaction/v2 | stockCode       | A trade must name its stock code",
        "/api/v1/me/portfolio/transaction/v2 | brokerName      | A trade must name its broker",
        "/api/v1/me/portfolio/transaction/v2 | transactionType | A trade must say whether it is a BUY or a SELL",
        "/api/v1/me/portfolio/transaction/v2 | transactionDate | A trade must carry its transaction date; it is not taken to be today"})
    void addTransaction_whenARequiredFieldIsMissing_expected400NamingItAndStoresNothing(String route, String missing, String message) {
        // W-365: without a stock, broker or date the trade was stored; without a type it crashed with a 500
        Map<String, Object> trade = new LinkedHashMap<>(Map.of("stockCode", "RELIANCE", "brokerName", "ZERODHA", "assetType", "EQUITY",
                "exchangeName", "NSE", "transactionType", "BUY", "price", 1000.0, "quantity", 10.0, "transactionDate", "2025-06-10"));
        trade.remove(missing);

        assertAnswered(exchange(HttpMethod.POST, route, MediaType.APPLICATION_JSON, trade), HttpStatus.BAD_REQUEST, message);
        assertThat(mongoTemplate.getCollection("transactions").countDocuments()).isZero();
    }

    @Test
    void calculate_whenTheCalculationTypeIsMissing_expected400NamingIt() {
        // W-365: the service switched on a null type, a 500
        assertAnswered(exchange(HttpMethod.POST, "/api/v1/public/finances/calculate", MediaType.APPLICATION_JSON,
                "{\"principalAmount\": 100000, \"rateOfInterest\": 9, \"timePeriod\": 12, \"finalAmount\": 0, \"emiAmount\": 0}"), HttpStatus.BAD_REQUEST, "Field 'calculationType' must not be null");
    }

    @Test
    void calculateInterestRate_whenTheStepUpFrequencyIsMissing_expected400NamingIt() {
        // W-365: the calculator read a null frequency's months, a 500
        assertAnswered(exchange(HttpMethod.POST, "/api/v1/public/finances/interest-rate", MediaType.APPLICATION_JSON,
                "{\"initialAmount\": 5000, \"stepUpRate\": 10, \"months\": 12, \"targetAmount\": 100000}"), HttpStatus.BAD_REQUEST, "Field 'stepUpFrequency' must not be null");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "price     | price is required; it may be 0 for a free allotment",
        "stockCode | stockCode is required on a BUY or a SELL: it finds the scheme's own charges, such as an exit load"})
    void simulate_whenATradeLeavesOutWhatPricesIt_expected400NamingIt(String missing, String message) {
        // W-365: a missing price was read as 0 and a missing stock code priced no instrument charge, both answered 200
        Map<String, Object> trade = new LinkedHashMap<>(Map.of("brokerName", "ZERODHA", "assetType", "EQUITY", "event", "SELL",
                "transactionDate", "2025-06-02", "stockCode", "INFY", "price", 1000.0, "quantity", 10));
        trade.remove(missing);

        assertAnswered(exchange(HttpMethod.POST, "/api/v1/me/charges/simulate", MediaType.APPLICATION_JSON, trade),
                HttpStatus.BAD_REQUEST, message);
    }
}
