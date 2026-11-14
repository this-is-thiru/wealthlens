package com.thiru.wealthlens.integration;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/** A V2 buy with authoritative off: the flag's wiring end to end, shown before only at the unit tier (W-264b, D-611 C2a). */
@TestPropertySource(properties = {"app.charges.authoritative=false"})
class ChargeAuditAuthoritativeOffIntegrationTest extends AbstractChargeAuditIntegrationTest {

    @Test
    void tradeV2_withAuthoritativeOff_savesTheTradeAndKeepsTheEnteredCost() {
        // Given -- a delivery buy the shipped cards price at ₹12.38, entered with ₹15 of charges
        Map<String, Object> buy = trade("INFY", "DELIVERY", "BUY", 10, 1000, "2025-06-10");
        buy.put("brokerCharges", 15.0);

        // When
        int status = tradeV2(buy).getStatusCode().value();

        // Then -- the ₹12.38 is recorded and the lot still costs the entered ₹15
        JsonNode lot = json(get("/portfolio/user/" + EMAIL + "/stocks/all")).get(0);
        JsonNode recorded = json(get("/user-charges/user/" + EMAIL));
        assertEquals(200, status);
        assertEquals(1, recorded.size());
        assertMoney(15.0, lot.get("brokerCharges").asDouble());
    }
}
