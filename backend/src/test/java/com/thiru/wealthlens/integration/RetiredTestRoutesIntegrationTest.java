package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thiru.wealthlens.brokercharges.service.ChargeSeederService;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The routes written for testing are no longer served (W-311, D-659): the sale replay wrote a whole sale outside the
 * trade flow without saving the lots it changed (B-9), {@code /request} echoed its body, and {@code /request2}'s
 * asset filter now lives at {@code /assets/search}.
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
                .when().post("/test/user/" + EMAIL + "/transact/sell")
                .then().statusCode(404);
        assertEquals(0, mongoTemplate.getCollection("profit_and_loss").countDocuments());
        assertEquals(0, mongoTemplate.getCollection("trade_outcomes").countDocuments());
    }

    @Test
    void request_whenABodyIsPosted_answers404() {
        // When / Then
        given().header("Authorization", "Bearer " + generateToken(EMAIL)).contentType(ContentType.JSON)
                .body("{\"stockCode\": \"TESTCODE1\"}")
                .when().post("/portfolio/user/" + EMAIL + "/request")
                .then().statusCode(404);
    }

    @Test
    void request2_whenFiltersArePosted_answers404() {
        // When / Then — the filter is served at /assets/search
        given().header("Authorization", "Bearer " + generateToken(EMAIL)).contentType(ContentType.JSON)
                .body("{\"queryFilters\": []}")
                .when().post("/portfolio/user/" + EMAIL + "/request2")
                .then().statusCode(404);
    }
}
