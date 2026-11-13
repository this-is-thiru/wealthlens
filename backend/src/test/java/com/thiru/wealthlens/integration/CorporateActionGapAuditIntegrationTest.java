package com.thiru.wealthlens.integration;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.RestAssured;
import java.net.URI;
import java.util.ArrayList;
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
 * The W-264c scenarios: corporate actions driven through the HTTP API, each expected value worked by hand in
 * {@code docs/taxplanning-build/plans/W-264c.md} before the first run. Zerodha equity delivery lots, bought through V2.
 */
class CorporateActionGapAuditIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "corporate-audit@example.com";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private int priority;

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

    private void buy(String stockCode, double quantity, double price, String date, String holder) {
        Map<String, Object> body = new HashMap<>();
        body.put("stockCode", stockCode);
        body.put("stockName", stockCode);
        body.put("exchangeName", "NSE");
        body.put("brokerName", "ZERODHA");
        body.put("assetType", "EQUITY");
        body.put("segment", "DELIVERY");
        body.put("price", price);
        body.put("quantity", quantity);
        body.put("transactionType", "BUY");
        body.put("accountType", "Self".equals(holder) ? "SELF" : "OUTSOURCED");
        body.put("accountHolder", holder);
        body.put("transactionDate", date);
        ResponseEntity<String> response = call(HttpMethod.POST, "/portfolio/user/" + EMAIL + "/transaction/v2", "USER", body);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
    }

    private ResponseEntity<String> sell(String stockCode, double quantity, double price, String date) {
        Map<String, Object> body = new HashMap<>();
        body.put("stockCode", stockCode);
        body.put("stockName", stockCode);
        body.put("exchangeName", "NSE");
        body.put("brokerName", "ZERODHA");
        body.put("assetType", "EQUITY");
        body.put("segment", "DELIVERY");
        body.put("price", price);
        body.put("quantity", quantity);
        body.put("transactionType", "SELL");
        body.put("accountType", "SELF");
        body.put("accountHolder", "Self");
        body.put("transactionDate", date);
        return call(HttpMethod.POST, "/portfolio/user/" + EMAIL + "/transaction/v2", "USER", body);
    }

    private ResponseEntity<String> addAction(String stockCode, String type, String ratio, String recordDate, String allotmentDate) {
        Map<String, Object> body = new HashMap<>();
        body.put("stockCode", stockCode);
        body.put("stockName", stockCode);
        body.put("type", type);
        body.put("assetType", "EQUITY");
        body.put("ratio", ratio);
        body.put("priority", ++priority);
        body.put("recordDate", recordDate);
        body.put("exDate", recordDate);
        body.put("allotmentDate", allotmentDate);
        return call(HttpMethod.POST, "/corporate-action/add", "SUPER_USER", body);
    }

    private void addDemerger(String main, String spun, String ratio, String priceRatio, String recordDate) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("demergerRatio", ratio);
        detail.put("demergerPriceRatio", priceRatio);
        detail.put("mainStockCode", main);
        detail.put("mainStockName", main);
        detail.put("demergerStocks", List.of(Map.of("stockCode", spun, "stockName", spun)));
        Map<String, Object> body = new HashMap<>();
        body.put("stockCode", main);
        body.put("stockName", main);
        body.put("type", "DEMERGER");
        body.put("assetType", "EQUITY");
        body.put("priority", ++priority);
        body.put("recordDate", recordDate);
        body.put("exDate", recordDate);
        body.put("demergerDetail", detail);
        ResponseEntity<String> response = call(HttpMethod.POST, "/corporate-action/add", "SUPER_USER", body);
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(200);
    }

    private ResponseEntity<String> perform(String month, int year) {
        return call(HttpMethod.PUT, "/corporate-action/user/" + EMAIL + "/perform", "USER",
                Map.of("month", month, "year", year, "brokerName", "ZERODHA"));
    }

    private ResponseEntity<String> redrive() {
        return call(HttpMethod.POST, "/temporary-transactions/user/" + EMAIL + "/redrive", "USER", null);
    }

    private List<JsonNode> lots(String stockCode) {
        List<JsonNode> lots = new ArrayList<>();
        JSON.readTree(call(HttpMethod.GET, "/portfolio/user/" + EMAIL + "/stocks/all", "USER", null).getBody())
                .forEach(lot -> {
                    if (stockCode.equals(lot.get("stockCode").asString())) {
                        lots.add(lot);
                    }
                });
        return lots;
    }

    private double quantity(String stockCode) {
        return lots(stockCode).stream().mapToDouble(lot -> lot.get("quantity").asDouble()).sum();
    }

    private double quantityOf(String stockCode, String holder) {
        return lots(stockCode).stream().filter(lot -> holder.equals(lot.get("accountHolder").asString()))
                .mapToDouble(lot -> lot.get("quantity").asDouble()).sum();
    }

    private double cost(String stockCode) {
        return lots(stockCode).stream().mapToDouble(lot -> lot.get("quantity").asDouble() * lot.get("price").asDouble()).sum();
    }

    // Scenario 2
    @Test
    void perform_whenABonusEntitlementIsFractional_allotsTheWholeSharesOnly() {
        // Given -- 3 held, 1:2 (one new for two held): 1.5, whole shares only
        buy("MSWIL", 3, 100, "2026-01-05", "Self");
        addAction("MSWIL", "BONUS", "1:2", "2026-01-15", "2026-01-17");

        // When
        perform("JANUARY", 2026);

        // Then
        assertThat(quantity("MSWIL")).isEqualTo(4.0);
    }

    // Scenario 3
    @Test
    void perform_whenASplitIsTwoForOne_doublesTheSharesAndKeepsTheCost() {
        // Given -- 10 @ ₹500; split 2:1 (new:old)
        buy("NESTLE", 10, 500, "2026-01-05", "Self");
        addAction("NESTLE", "STOCK_SPLIT", "2:1", "2026-01-15", null);

        // When
        perform("JANUARY", 2026);

        // Then -- 20 @ ₹250, ₹5,000
        assertThat(quantity("NESTLE")).isEqualTo(20.0);
        assertMoney(250.0, lots("NESTLE").getFirst().get("price").asDouble());
        assertMoney(5000.0, cost("NESTLE"));
    }

    // Scenarios 4 and 12
    @Test
    void perform_onTheRecordDateBoundary_countsOnlyLotsBeforeItAndRedriveAddsTheHeldTrade() {
        // Given -- 5 bought the day before the record date and 7 on it, then the bonus 1:1 is announced
        buy("RELIANCE", 5, 100, "2026-01-14", "Self");
        buy("RELIANCE", 7, 100, "2026-01-15", "Self");
        addAction("RELIANCE", "BONUS", "1:1", "2026-01-15", "2026-01-17");
        // and a buy of 3 after the record date, held while the bonus is pending
        buy("RELIANCE", 3, 100, "2026-01-20", "Self");
        assertThat(quantity("RELIANCE")).isEqualTo(12.0);

        // When
        perform("JANUARY", 2026);
        ResponseEntity<String> redriven = redrive();

        // Then -- bonus on the 5 only; the held 3 comes back: 5 + 7 + 5 + 3 = 20
        assertThat(redriven.getStatusCode().value()).as(redriven.getBody()).isEqualTo(200);
        assertThat(quantity("RELIANCE")).isEqualTo(20.0);
    }

    // Scenario 5
    @Test
    void perform_whenADemergerIsOneForOne_splitsTheCostInThePriceRatio() {
        // Given -- 10 A @ ₹1,000; demerger 1:1, cost 80:20
        buy("HINDUNILVR", 10, 1000, "2026-01-05", "Self");
        addDemerger("HINDUNILVR", "KWIL", "1:1", "80:20", "2026-01-15");

        // When
        perform("JANUARY", 2026);

        // Then -- A 10 @ ₹800, B 10 @ ₹200; ₹10,000 in total
        assertThat(quantity("HINDUNILVR")).isEqualTo(10.0);
        assertThat(quantity("KWIL")).isEqualTo(10.0);
        assertMoney(8000.0, cost("HINDUNILVR"));
        assertMoney(2000.0, cost("KWIL"));
    }

    // Scenario 7
    @Test
    void sellV2_whenTheSharesCameFromADemerger_isRecorded() {
        // Given -- the scenario 5 demerger
        buy("HINDUNILVR", 10, 1000, "2026-01-05", "Self");
        addDemerger("HINDUNILVR", "KWIL", "1:1", "80:20", "2026-01-15");
        perform("JANUARY", 2026);

        // When -- 5 of the 10 new shares sold at ₹300
        ResponseEntity<String> sold = sell("KWIL", 5, 300, "2026-02-10");

        // Then
        assertThat(sold.getStatusCode().value()).as(sold.getBody()).isEqualTo(200);
        assertThat(quantity("KWIL")).isEqualTo(5.0);
    }

    // Scenario 9
    @Test
    void perform_whenRunTwice_changesNothingTheSecondTime() {
        // Given
        buy("TCS", 10, 100, "2026-01-05", "Self");
        addAction("TCS", "BONUS", "1:1", "2026-01-15", "2026-01-17");
        perform("JANUARY", 2026);

        // When
        perform("JANUARY", 2026);

        // Then
        assertThat(quantity("TCS")).isEqualTo(20.0);
    }

    // Scenario 16: refused, with 401 (noted in the report: a signed-in caller on another user's path)
    @Test
    void userRoutes_whenThePathNamesAnotherUser_areRefused() {
        // Given -- another user's holding
        String other = "corporate-audit-other@example.com";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(generateToken(EMAIL, "USER"));
        RestTemplate client = new RestTemplate();
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });

        // When -- this user's token on the other user's paths
        ResponseEntity<String> performed = client.exchange(URI.create("http://localhost:" + RestAssured.port
                        + "/corporate-action/user/" + other + "/perform"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("month", "JANUARY", "year", 2026, "brokerName", "ZERODHA"), headers), String.class);
        ResponseEntity<String> read = client.exchange(URI.create("http://localhost:" + RestAssured.port
                + "/portfolio/user/" + other + "/stocks/all"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        // Then
        assertThat(performed.getStatusCode().value()).as(performed.getBody()).isEqualTo(401);
        assertThat(read.getStatusCode().value()).as(read.getBody()).isEqualTo(401);
    }

    // Scenario 8 (W-314)
    @Test
    void perform_whenASplitAndABonusFallInOneQuarter_appliesThemByRecordDate() {
        // Given -- 10 @ ₹100; the bonus (record 20 Jan) is announced before the split (record 10 Jan)
        buy("INFY", 10, 100, "2026-01-05", "Self");
        addAction("INFY", "BONUS", "1:1", "2026-01-20", "2026-01-22");
        addAction("INFY", "STOCK_SPLIT", "2:1", "2026-01-10", null);

        // When
        perform("JANUARY", 2026);

        // Then -- split first: 20 @ ₹50; then the bonus on 20: +20 @ ₹0 = 40, cost ₹1,000
        assertThat(quantity("INFY")).isEqualTo(40.0);
        assertMoney(1000.0, cost("INFY"));
    }

    // Scenario 10 (W-315)
    @Test
    void perform_whenTheMonthIsNotAQuarterStart_answers400NamingTheMonths() {
        // When
        ResponseEntity<String> lowercase = perform("april", 2026);
        ResponseEntity<String> february = perform("FEBRUARY", 2026);

        // Then -- the caller is told the four months a quarter starts in
        assertThat(lowercase.getStatusCode().value()).isEqualTo(400);
        assertThat(JSON.readTree(lowercase.getBody()).get("message").asString())
                .isEqualTo("A quarter starts in one of [JANUARY, APRIL, JULY, OCTOBER], but the month was 'april'");
        assertThat(february.getStatusCode().value()).isEqualTo(400);
        assertThat(JSON.readTree(february.getBody()).get("message").asString())
                .isEqualTo("A quarter starts in one of [JANUARY, APRIL, JULY, OCTOBER], but the month was 'FEBRUARY'");
    }

    // Scenario 15 (W-317)
    @Test
    void adminRoutes_whenTheCallerIsNotASuperUser_answer403() {
        // When
        ResponseEntity<String> added = call(HttpMethod.POST, "/corporate-action/add", "USER",
                Map.of("stockCode", "X", "type", "BONUS", "ratio", "1:1", "recordDate", "2026-01-15", "exDate", "2026-01-15"));
        ResponseEntity<String> performedForAll = call(HttpMethod.PUT, "/corporate-action/perform", "USER",
                Map.of("stockCode", "X", "type", "NAME_OR_SYMBOL_CHANGE", "toStockCode", "Y"));
        ResponseEntity<String> tested = call(HttpMethod.PUT, "/corporate-action/perform/test", "USER",
                Map.of("stockCode", "X", "recordDate", "2026-01-15"));
        ResponseEntity<String> reprioritised = call(HttpMethod.PUT, "/corporate-action/update/priority/abc/2", "USER", null);

        // Then
        assertThat(added.getStatusCode().value()).isEqualTo(403);
        assertThat(performedForAll.getStatusCode().value()).isEqualTo(403);
        assertThat(tested.getStatusCode().value()).isEqualTo(403);
        assertThat(reprioritised.getStatusCode().value()).isEqualTo(403);
    }

    // Scenario 1 (W-312)
    @Test
    void perform_whenTwoHoldersHoldTheStockAtOneBroker_givesEachHolderTheirOwnBonus() {
        // Given -- Self 10 and Mother 6, both before the record date; bonus 1:1
        buy("HDFCBANK", 10, 100, "2026-01-05", "Self");
        buy("HDFCBANK", 6, 100, "2026-01-06", "Mother");
        assertThat(addAction("HDFCBANK", "BONUS", "1:1", "2026-01-15", "2026-01-17").getStatusCode().value()).isEqualTo(200);

        // When
        perform("JANUARY", 2026);

        // Then -- each holder's entitlement is their own: 10 + 10 and 6 + 6
        assertThat(quantityOf("HDFCBANK", "Self")).isEqualTo(20.0);
        assertThat(quantityOf("HDFCBANK", "Mother")).isEqualTo(12.0);
    }

    // Scenario 1b (W-312)
    @Test
    void perform_whenEachHolderHasAFractionalEntitlement_roundsEachDownOnItsOwn() {
        // Given -- Self 3 and Mother 3; bonus 1:2: 1.5 each, whole shares per demat account: 1 each (pooled: 3)
        buy("MSWIL", 3, 100, "2026-01-05", "Self");
        buy("MSWIL", 3, 100, "2026-01-06", "Mother");
        addAction("MSWIL", "BONUS", "1:2", "2026-01-15", "2026-01-17");

        // When
        perform("JANUARY", 2026);

        // Then
        assertThat(quantityOf("MSWIL", "Self")).isEqualTo(4.0);
        assertThat(quantityOf("MSWIL", "Mother")).isEqualTo(4.0);
    }

    // Scenario 6 (W-313)
    @Test
    void perform_whenADemergerIsOneForTen_allotsOneNewShareForEveryTenHeld() {
        // Given -- ITC's own demerger: 1,000 ITC @ ₹400, 1:10 (new:held), cost 86.49:13.51
        buy("ITC", 1000, 400, "2026-01-05", "Self");
        addDemerger("ITC", "ITCHOTELS", "1:10", "86.49:13.51", "2026-01-15");

        // When
        perform("JANUARY", 2026);

        // Then -- ITC 1,000 (₹3,45,960); ITC Hotels 100 @ ₹540.40 (₹54,040); ₹4,00,000 kept
        assertThat(quantity("ITC")).isEqualTo(1000.0);
        assertMoney(345960.0, cost("ITC"));
        assertThat(quantity("ITCHOTELS")).isEqualTo(100.0);
        assertMoney(540.40, lots("ITCHOTELS").getFirst().get("price").asDouble());
        assertMoney(54040.0, cost("ITCHOTELS"));
    }

    // W-313: the ratio is refused at entry when it is not two whole numbers
    @Test
    void addDemerger_whenTheRatioIsNotTwoWholeNumbers_answers400NamingTheForm() {
        // Given
        Map<String, Object> detail = new HashMap<>();
        detail.put("demergerRatio", "1.5:10");
        detail.put("demergerPriceRatio", "86.49:13.51");
        detail.put("mainStockCode", "ITC");
        detail.put("mainStockName", "ITC");
        detail.put("demergerStocks", List.of(Map.of("stockCode", "ITCHOTELS", "stockName", "ITCHOTELS")));
        Map<String, Object> body = new HashMap<>();
        body.put("stockCode", "ITC");
        body.put("type", "DEMERGER");
        body.put("priority", ++priority);
        body.put("recordDate", "2026-01-15");
        body.put("exDate", "2026-01-15");
        body.put("demergerDetail", detail);

        // When
        ResponseEntity<String> added = call(HttpMethod.POST, "/corporate-action/add", "SUPER_USER", body);

        // Then
        assertThat(added.getStatusCode().value()).isEqualTo(400);
        assertThat(JSON.readTree(added.getBody()).get("message").asString()).isEqualTo(
                "A demerger's ratio is new shares to shares held, two whole numbers above nil such as 1:10, but it was 1.5:10");
    }

    // Scenario 11 (W-316)
    @Test
    void perform_whenARightsIssueIsInTheQuarter_neitherHoldsATradeNorStopsTheQuarter() {
        // Given -- 10 SBIN and 10 WIPRO held; a rights issue on SBIN (15 Jan) and a bonus on WIPRO (20 Jan)
        buy("SBIN", 10, 100, "2026-01-05", "Self");
        buy("WIPRO", 10, 100, "2026-01-05", "Self");
        addAction("SBIN", "RIGHTS_ISSUANCE", "1:5", "2026-01-15", null);
        addAction("WIPRO", "BONUS", "1:1", "2026-01-20", "2026-01-22");

        // When -- a buy of 5 SBIN after the rights issue's record date, then the quarter's run
        buy("SBIN", 5, 100, "2026-01-21", "Self");
        ResponseEntity<String> performed = perform("JANUARY", 2026);

        // Then -- the buy is recorded at once, and the run applies the quarter's bonus
        assertThat(quantity("SBIN")).isEqualTo(15.0);
        assertThat(performed.getStatusCode().value()).as(performed.getBody()).isEqualTo(200);
        assertThat(quantity("WIPRO")).isEqualTo(20.0);
    }
}
