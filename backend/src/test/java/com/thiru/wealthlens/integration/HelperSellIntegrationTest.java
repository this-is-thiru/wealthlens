package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;

import com.thiru.wealthlens.brokercharges.service.ChargeSeederService;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A holding in the test sell hook may omit the lists its initialisers supply (W-205, D-409).
 *
 * <p>The api-collection's {@code Transact - New} body carries no {@code sellTransactionIds}; before the fix the
 * service NPE'd on it at {@code PortfolioService:641} and the caller got a bare 500. The body here is that request
 * with the four initialised lists left out.
 */
class HelperSellIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "helper-sell@example.com";

    @Autowired
    private ChargeSeederService chargeSeederService;

    @Test
    void sell_whenAHoldingOmitsItsTransactionLists_answers200() {
        // Given — one lot covering the sale, with the lists a client need not send left out
        chargeSeederService.seed("integration-test");

        String body = """
                {
                  "transactionId": "transactionId_w205",
                  "assetRequest": {
                    "stockCode": "TESTCODE1",
                    "stockName": "TESTCODE1 stockName",
                    "exchangeName": "NSE",
                    "brokerName": "ZERODHA",
                    "assetType": "EQUITY",
                    "price": 20.00,
                    "quantity": 30.00,
                    "transactionType": "SELL",
                    "accountType": "SELF",
                    "transactionDate": "2025-10-23"
                  },
                  "stockEntities": [
                    {
                      "stockCode": "TESTCODE1",
                      "stockName": "TESTCODE1 stockName",
                      "exchangeName": "NSE",
                      "brokerName": "ZERODHA",
                      "price": 10.00,
                      "quantity": 30.00,
                      "assetType": "EQUITY",
                      "transactionDate": "2025-10-01",
                      "accountType": "SELF",
                      "corporateActionType": "DIVIDEND",
                      "brokerCharges": 0.0,
                      "miscCharges": 0.0,
                      "allocatedBuyCharges": 0.0,
                      "allocatedBuyMiscCharges": 0.0
                    }
                  ]
                }
                """;

        // When / Then
        given().header("Authorization", "Bearer " + generateToken(EMAIL)).contentType(ContentType.JSON).body(body)
                .when().post("/test/user/" + EMAIL + "/transact/sell")
                .then().statusCode(200);
    }
}
