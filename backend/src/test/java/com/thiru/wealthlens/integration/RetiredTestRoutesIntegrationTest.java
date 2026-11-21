package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thiru.wealthlens.brokercharges.service.ChargeSeederService;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Routes that are no longer served answer as any route the app does not serve. The routes written for testing (W-311, D-659):
 * the sale replay wrote a whole sale outside the trade flow without saving the lots it changed (B-9), {@code /request} echoed its
 * body, and {@code /request2}'s asset filter now lives at {@code /assets/search}. The one-time corrections that have run on every
 * database (Phase 15, D-286; D-874 – D-879): each put data written by older code right once, and calling one again could only
 * rewrite rows already correct.
 */
class RetiredTestRoutesIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "retired-routes@example.com";

    @Autowired
    private ChargeSeederService chargeSeederService;

    @Test
    void transactSell_whenTheOperatorReplaysASale_answers404AndRecordsNothing() {
        // Given — the operator's token and the body the replay used to accept: one held lot covering the sale
        chargeSeederService.seed("integration-test");
        String body = """
                {"transactionId": "transactionId_w311",
                 "assetRequest": {"stockCode": "TESTCODE1", "stockName": "TESTCODE1 stockName", "exchangeName": "NSE",
                   "brokerName": "ZERODHA", "assetType": "EQUITY", "price": 20.00, "quantity": 30.00,
                   "transactionType": "SELL", "accountType": "SELF", "transactionDate": "2025-10-23"},
                 "stockEntities": [{"stockCode": "TESTCODE1", "stockName": "TESTCODE1 stockName", "exchangeName": "NSE",
                   "brokerName": "ZERODHA", "price": 10.00, "quantity": 30.00, "assetType": "EQUITY",
                   "transactionDate": "2025-10-01", "accountType": "SELF"}]}
                """;

        // When / Then — no such route, and no gain or trade outcome written
        given().header("Authorization", "Bearer " + generateToken(EMAIL, "SUPER_USER")).contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/v1/admin/portfolio/test/transact/sell")
                .then().statusCode(404);
        assertEquals(0, mongoTemplate.getCollection("profit_and_loss").countDocuments());
        assertEquals(0, mongoTemplate.getCollection("trade_outcomes").countDocuments());
    }

    @Test
    void request_whenABodyIsPosted_answers404() {
        // When / Then
        given().header("Authorization", "Bearer " + generateToken(EMAIL)).contentType(ContentType.JSON)
                .body("{\"stockCode\": \"TESTCODE1\"}")
                .when().post("/api/v1/me/portfolio/request")
                .then().statusCode(404);
    }

    @Test
    void request2_whenFiltersArePosted_answers404() {
        // When / Then — the filter is served at /assets/search
        given().header("Authorization", "Bearer " + generateToken(EMAIL)).contentType(ContentType.JSON)
                .body("{\"queryFilters\": []}")
                .when().post("/api/v1/me/portfolio/request2")
                .then().statusCode(404);
    }

    @Test
    void rebuildChargeCorrection_whenAnAdminPosts_answers404() {
        // Given / When / Then — W-147, runbook §2.1: the rebuild's double-counted buy charges, corrected on prod (W-334e2) and staging (W-334d3a)
        refuses("/api/v1/admin/portfolio/trade-outcomes/rebuild-charges/correct");
    }

    @Test
    void financialYearRewrite_whenAnAdminPosts_answers404() {
        // Given / When / Then — W-149, runbook §2.3: the FY-form financial years, rewritten on prod (W-334e2, 163) and staging (W-334d3a, 56)
        refuses("/api/v1/admin/portfolio/trade-outcomes/financial-years/rewrite");
    }

    @Test
    void bonusAllotmentDateCorrection_whenAnAdminPosts_answers404() {
        // Given / When / Then — W-150, runbook §2.4: the bonus lots dated from their allotment on prod (W-334e2) and staging (W-334d3a)
        refuses("/api/v1/admin/portfolio/corporate-actions/bonus/allotment-dates/correct");
    }

    @Test
    void storedTermRemoval_whenAnAdminPosts_answers404() {
        // Given / When / Then — W-168, runbook §2.5: the stored term removed on prod (W-334e2, 163) and staging (W-334d3a, 201)
        refuses("/api/v1/admin/portfolio/trade-outcomes/stored-terms/remove");
    }

    @Test
    void profitAndLossSplitReshape_whenAnAdminPosts_answers404() {
        // Given / When / Then — W-170, runbook §2.6: the stored short / long pair moved into its split on prod (W-334e2) and staging (W-334d3a), 0 sides unplaced on either
        refuses("/api/v1/admin/portfolio/profit-and-loss/split/reshape");
    }

    @Test
    void deductibleChargeRemoval_whenAnAdminPosts_answers404() {
        // Given / When / Then — W-174, runbook §2.9: the deductible charges removed from trade_outcomes on staging (W-334d3a, 1, after 0); prod had none (W-334e2)
        refuses("/api/v1/admin/portfolio/trade-outcomes/deductible-charges/remove");
    }

    private void refuses(String route) {
        given().header("Authorization", "Bearer " + generateToken(EMAIL, "SUPER_USER")).contentType(ContentType.JSON).body("{}")
                .when().post(route)
                .then().statusCode(404);
    }
}
