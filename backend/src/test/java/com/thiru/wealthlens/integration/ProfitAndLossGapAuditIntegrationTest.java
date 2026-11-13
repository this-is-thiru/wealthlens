package com.thiru.wealthlens.integration;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.RestAssured;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The W-264d scenarios: the P&amp;L read and the analytics driven through the HTTP API, each expected value worked by
 * hand in {@code docs/taxplanning-build/plans/W-264d.md} before the first run. Zerodha equity delivery, ₹0 charges.
 */
class ProfitAndLossGapAuditIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "pnl-audit@example.com";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ResponseEntity<String> call(HttpMethod method, String path, String role, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(generateToken(EMAIL, role));
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

    private void trade(String flow, String stockCode, String side, double quantity, double price, String date) {
        Map<String, Object> body = new HashMap<>();
        body.put("stockCode", stockCode);
        body.put("stockName", stockCode);
        body.put("exchangeName", "NSE");
        body.put("brokerName", "ZERODHA");
        body.put("assetType", "EQUITY");
        body.put("segment", "DELIVERY");
        body.put("price", price);
        body.put("quantity", quantity);
        body.put("transactionType", side);
        body.put("accountType", "SELF");
        body.put("accountHolder", "Self");
        body.put("transactionDate", date);
        ResponseEntity<String> response = call(HttpMethod.POST, "/portfolio/user/" + EMAIL + flow, "USER", body);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
    }

    private void v2(String stockCode, String side, double quantity, double price, String date) {
        trade("/transaction/v2", stockCode, side, quantity, price, date);
    }

    private JsonNode profitAndLoss(String financialYear) {
        return JSON.readTree(call(HttpMethod.GET, "/portfolio/user/" + EMAIL + "/profit-and-loss?financialYear=" + financialYear,
                "USER", null).getBody());
    }

    private static JsonNode delivery(JsonNode pnl) {
        return pnl.get("realisedProfits").get("gainsByAssetAndSegment").get("EQUITY").get("DELIVERY");
    }

    private JsonNode analytics(String what) {
        return JSON.readTree(call(HttpMethod.GET, "/analytics/user/" + EMAIL + "/" + what, "USER", null).getBody());
    }

    private ResponseEntity<String> xirr(List<Map<String, Object>> prices) {
        return call(HttpMethod.POST, "/analytics/user/" + EMAIL + "/xirr", "USER", Map.of("currentPrices", prices));
    }

    // Scenario 1
    @Test
    void profitAndLoss_whenAV2SaleGains_showsItInItsYearMonthAndFortnight() {
        // Given -- 10 @ ₹100, then 4 sold @ ₹150 on 5 Mar 2026
        v2("TCS", "BUY", 10, 100, "2026-01-10");
        v2("TCS", "SELL", 4, 150, "2026-03-05");

        // When
        JsonNode delivery = delivery(profitAndLoss("2025-2026"));

        // Then -- 4 × (150 − 100) = ₹200, in March's first half
        assertMoney(200.0, delivery.get("profit").asDouble());
        assertMoney(200.0, delivery.get("monthlyReport").get("MARCH").get("firstFortnightReport").get("profit").asDouble());
    }

    // Scenario 2
    @Test
    void profitAndLoss_onTheLastAndFirstDayOfAFinancialYear_putsEachSaleInItsOwnYear() {
        // Given -- 1 sold @ ₹150 on 31 Mar 2026 and 1 on 1 Apr 2026, each ₹50
        v2("TCS", "BUY", 10, 100, "2026-01-10");
        v2("TCS", "SELL", 1, 150, "2026-03-31");
        v2("TCS", "SELL", 1, 150, "2026-04-01");

        // Then
        assertMoney(50.0, delivery(profitAndLoss("2025-2026")).get("profit").asDouble());
        assertMoney(50.0, delivery(profitAndLoss("2026-2027")).get("profit").asDouble());
    }

    // Scenario 3
    @Test
    void profitAndLoss_onTheFifteenthAndSixteenth_splitsTheMonthIntoItsHalves() {
        // Given -- ₹50 on 15 Mar, ₹50 on 16 Mar
        v2("TCS", "BUY", 10, 100, "2026-01-10");
        v2("TCS", "SELL", 1, 150, "2026-03-15");
        v2("TCS", "SELL", 1, 150, "2026-03-16");

        // When
        JsonNode march = delivery(profitAndLoss("2025-2026")).get("monthlyReport").get("MARCH");

        // Then
        assertMoney(50.0, march.get("firstFortnightReport").get("profit").asDouble());
        assertMoney(50.0, march.get("secondFortnightReport").get("profit").asDouble());
    }

    // Scenario 4
    @Test
    void profitAndLoss_whenTheSaleIsV1_showsTheSameGainAsV2() {
        // Given -- scenario 1 through V1
        trade("/transaction", "TCS", "BUY", 10, 100, "2026-01-10");
        trade("/transaction", "TCS", "SELL", 4, 150, "2026-03-05");

        // Then
        assertMoney(200.0, delivery(profitAndLoss("2025-2026")).get("profit").asDouble());
    }

    // Scenario 7
    @Test
    void assetAllocation_afterAPartSale_showsWhatIsStillInvested() {
        // Given -- scenario 1: 6 of 10 @ ₹100 still held
        v2("TCS", "BUY", 10, 100, "2026-01-10");
        v2("TCS", "SELL", 4, 150, "2026-03-05");

        // When
        JsonNode allocation = analytics("asset-allocation");

        // Then -- ₹600, all of it equity
        assertThat(allocation.size()).isEqualTo(1);
        assertMoney(600.0, allocation.get(0).get("investedValue").asDouble());
        assertMoney(100.0, allocation.get(0).get("percentage").asDouble());
    }

    // Scenario 9
    @Test
    void xirr_whenAHoldingGainsTenPercentInOneYear_isTenPercent() {
        // Given -- ₹1,000 out on 1 Jan 2025, ₹1,100 back on 1 Jan 2026
        v2("TCS", "BUY", 10, 100, "2025-01-01");
        v2("TCS", "SELL", 10, 110, "2026-01-01");

        // When
        JsonNode result = JSON.readTree(xirr(List.of()).getBody());

        // Then
        assertThat(result.get("xirr").asDouble()).isCloseTo(0.10, org.assertj.core.data.Offset.offset(1e-6));
    }

    // Scenario 5 (W-320)
    @Test
    void profitAndLoss_whenTheYearIsNotWrittenAsStored_answers400NamingTheForm() {
        // Given
        v2("TCS", "BUY", 10, 100, "2026-01-10");
        v2("TCS", "SELL", 4, 150, "2026-03-05");

        // When
        ResponseEntity<String> shortForm = call(HttpMethod.GET, "/portfolio/user/" + EMAIL + "/profit-and-loss?financialYear=2025-26",
                "USER", null);
        ResponseEntity<String> untraded = call(HttpMethod.GET, "/portfolio/user/" + EMAIL + "/profit-and-loss?financialYear=2027-2028",
                "USER", null);

        // Then -- the short form is refused naming the stored form; a year with nothing traded is simply empty
        assertThat(shortForm.getStatusCode().value()).as(shortForm.getBody()).isEqualTo(400);
        assertThat(JSON.readTree(shortForm.getBody()).get("message").asString())
                .isEqualTo("A financial year is written as portfolio writes it, such as 2025-2026, but was 2025-26");
        assertThat(untraded.getStatusCode().value()).isEqualTo(200);
    }

    // Scenario 8 (W-322)
    @Test
    void performanceMetrics_whenThereAreWinsAndNoLosses_giveNoWinLossRatio() {
        // Given -- scenario 1: one win of ₹200
        v2("TCS", "BUY", 10, 100, "2026-01-10");
        v2("TCS", "SELL", 4, 150, "2026-03-05");

        // When
        JsonNode performance = analytics("performance-metrics");

        // Then -- a ratio with no losses has no value; 0.0 would say no wins
        assertThat(performance.get("winCount").asLong()).isEqualTo(1);
        assertThat(performance.get("lossCount").asLong()).isEqualTo(0);
        assertThat(performance.get("winLossRatio").isNull()).as(performance.toString()).isTrue();
    }

}
