package com.thiru.wealthlens.integration;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertBreakdown;
import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;

/**
 * The W-264b scenarios that passed and that {@link ChargesIntegrationTest} does not already show (D-611 C2a). Every
 * expected amount is worked by hand in {@code reviews/2026-11-13-charge-engine-gap-audit.md} §2 from the shipped
 * cards: Zerodha's equity cards (brokerage, exchange, IPFT, DP) and the statutory ones (STT, SEBI fee, stamp duty,
 * GST on the taxable lines after each is rounded).
 */
class ChargeGapAuditIntegrationTest extends AbstractChargeAuditIntegrationTest {

    @Test
    void simulate_whenADeliveryBuyIsPricedOnTheShippedCards_matchesTheHandComputation() {
        // Given -- 10 at ₹1,000 on 10 Jun 2025: turnover ₹10,000
        Map<String, Object> buy = simulation("DELIVERY", "BUY", 10, 1000, "2025-06-10");

        // When
        JsonNode breakdown = json(simulate(buy));

        // Then -- STT 0.1 % to the rupee 10, exchange 0.297 → 0.30, SEBI and IPFT 0.01 each, stamp 1.5 → 2,
        // GST 18 % of 0.32 = 0.0576 → 0.06
        Map<String, Double> expected = new LinkedHashMap<>();
        expected.put("BROKERAGE", 0.0);
        expected.put("STT", 10.0);
        expected.put("EXCHANGE_TXN", 0.30);
        expected.put("SEBI_FEE", 0.01);
        expected.put("IPFT", 0.01);
        expected.put("STAMP_DUTY", 2.0);
        expected.put("GST", 0.06);
        assertBreakdown(expected, amounts(breakdown));
        assertMoney(12.38, breakdown.get("totalCharges").asDouble());
    }

    @Test
    void simulate_whenADeliverySaleIsPricedOnTheShippedCards_addsDpAndItsGst() {
        // Given -- 10 at ₹1,100 on 10 Jul 2025: turnover ₹11,000
        Map<String, Object> sale = simulation("DELIVERY", "SELL", 10, 1100, "2025-07-10");

        // When
        JsonNode breakdown = json(simulate(sale));

        // Then -- STT 11, exchange 0.3267 → 0.33, SEBI and IPFT 0.011 → 0.01, DP 13.50, no stamp on a sale,
        // GST 18 % of 13.85 = 2.493 → 2.49
        Map<String, Double> expected = new LinkedHashMap<>();
        expected.put("BROKERAGE", 0.0);
        expected.put("STT", 11.0);
        expected.put("EXCHANGE_TXN", 0.33);
        expected.put("SEBI_FEE", 0.01);
        expected.put("IPFT", 0.01);
        expected.put("DP", 13.50);
        expected.put("GST", 2.49);
        assertBreakdown(expected, amounts(breakdown));
        assertMoney(27.34, breakdown.get("totalCharges").asDouble());
    }

    @Test
    void simulate_onTheLastAndFirstDayOfTwoCards_eachDayIsPricedByItsOwnCard() {
        // When -- ZERODHA_EQ_DELIVERY_2025_04 ends 28 Feb 2026, ZERODHA_EQ_DELIVERY_2026_03 starts 1 Mar 2026
        JsonNode lastDay = json(simulate(simulation("DELIVERY", "BUY", 10, 1000, "2026-02-28")));
        JsonNode firstDay = json(simulate(simulation("DELIVERY", "BUY", 10, 1000, "2026-03-01")));

        // Then -- the end date is inside its card; the new card's exchange rate 0.0030699 % gives 0.30699 → 0.31
        assertEquals("ZERODHA_EQ_DELIVERY_2025_04", lastDay.get("scheduleCode").asString());
        assertEquals("ZERODHA_EQ_DELIVERY_2026_03", firstDay.get("scheduleCode").asString());
        assertMoney(0.31, amounts(firstDay).get("EXCHANGE_TXN"));
    }

    @Test
    void simulate_whenACardIsScopedToTheTradesExchange_itOutranksTheBrokersGeneralCard() {
        // Given -- a Zerodha delivery card for NSE only, flat brokerage ₹5
        Map<String, Object> card = new HashMap<>();
        card.put("scheduleCode", "AUDIT_ZERODHA_NSE");
        card.put("brokerName", "ZERODHA");
        card.put("assetType", "EQUITY");
        card.put("segment", "DELIVERY");
        card.put("exchange", "NSE");
        card.put("startDate", "2025-04-01");
        card.put("status", "ACTIVE");
        card.put("requiresInstrumentProfile", false);
        card.put("rules", List.of(flatBrokerage(5.0)));
        assertEquals(200, call(HttpMethod.POST, "/charge-schedules", admin(), card).getStatusCode().value());
        Map<String, Object> onNse = simulation("DELIVERY", "BUY", 10, 1000, "2025-06-10");
        onNse.put("exchange", "NSE");
        Map<String, Object> onBse = new HashMap<>(onNse);
        onBse.put("exchange", "BSE");

        // When
        JsonNode nse = json(simulate(onNse));
        JsonNode bse = json(simulate(onBse));

        // Then -- NSE: brokerage 5 and GST 18 % of 5.01 = 0.9018 → 0.90; BSE: the general card, as before
        assertEquals("AUDIT_ZERODHA_NSE", nse.get("scheduleCode").asString());
        assertMoney(5.0, amounts(nse).get("BROKERAGE"));
        assertMoney(0.90, amounts(nse).get("GST"));
        assertEquals("ZERODHA_EQ_DELIVERY_2025_04", bse.get("scheduleCode").asString());
    }

    @Test
    void tradeV2_whenTheTradeIsIntraday_isRecordedOnTheIntradayCardAndCostsItsComputedCharges() {
        // Given -- 10 at ₹1,000 intraday: brokerage min(0.03 % = 3.00, 20) = 3.00, exchange 0.30, SEBI and IPFT
        // 0.01, stamp 0.003 % = 0.3 → 0, GST 18 % of 3.32 = 0.5976 → 0.60
        assertEquals(200, tradeV2(trade("TCS", "INTRADAY", "BUY", 10, 1000, "2025-06-10")).getStatusCode().value());

        // When
        JsonNode recorded = json(get("/user-charges/user/" + EMAIL)).get(0);
        JsonNode lot = json(get("/portfolio/user/" + EMAIL + "/stocks/all")).get(0);

        // Then -- recorded on the intraday card, and with authoritative on the lot costs the computed ₹3.92
        assertEquals("ZERODHA_EQ_INTRADAY_2025_04", recorded.get("scheduleCode").asString());
        assertMoney(3.92, recorded.get("totalCharges").asDouble());
        assertMoney(3.92, lot.get("brokerCharges").asDouble());
    }

    @Test
    void tradeV1_whenTheTradeIsIntraday_isRecordedOnTheIntradayCard() {
        // Given -- PS-13: V1 once dropped the segment, so every V1 trade was priced as delivery (W-280, D-566)
        assertEquals(200, tradeV1(trade("TCS", "INTRADAY", "BUY", 10, 1000, "2025-06-10")).getStatusCode().value());

        // When
        JsonNode recorded = json(get("/user-charges/user/" + EMAIL)).get(0);

        // Then -- priced and recorded as intraday; V1 records, but only V2 makes it the cost (W-264b G4)
        assertEquals("ZERODHA_EQ_INTRADAY_2025_04", recorded.get("scheduleCode").asString());
        assertMoney(3.92, recorded.get("totalCharges").asDouble());
    }

    @Test
    void imposeAmc_onTheShippedMaintenanceCard_billsItsFeeWithGstOnce() {
        // Given -- a Zerodha account billed annually; the card's AMC is ₹300 and the statutory card adds GST 18 %
        Map<String, Object> account = new HashMap<>();
        account.put("accountHolder", "Self");
        account.put("brokerName", "ZERODHA");
        account.put("dematAccountId", "DEMAT-AUDIT");
        account.put("openedOn", "2025-04-01");
        account.put("amcFrequency", "ANNUALLY");
        account.put("status", "ACTIVE");
        assertEquals(200, call(HttpMethod.POST, "/charge-accounts/user/" + EMAIL, generateToken(EMAIL), account)
                .getStatusCode().value());
        String cycle = "/charges/amc/impose?frequency=ANNUALLY&billedThrough=2026-03-31";

        // When
        int billed = json(call(HttpMethod.POST, cycle, admin(), null)).size();
        int billedAgain = json(call(HttpMethod.POST, cycle, admin(), null)).size();

        // Then -- 300 + 54 = ₹354, and the second run bills nobody
        JsonNode charge = json(get("/user-charges/user/" + EMAIL)).get(0);
        assertEquals(1, billed);
        assertEquals(0, billedAgain);
        assertMoney(354.0, charge.get("totalCharges").asDouble());
    }

    @Test
    void history_whenTheRangeStartsAfterItEnds_answers400NamingBothDates() {
        // When
        ResponseEntity<String> response = get("/user-charges/user/" + EMAIL + "?from=2026-01-01&to=2025-01-01");

        // Then
        assertEquals(400, response.getStatusCode().value());
        assertEquals("Date range starts after it ends: 2026-01-01 is after 2025-01-01",
                json(response).get("message").asString());
    }

    @Test
    void tradeV2_whenAFundIsRedeemedInsideItsExitLoadWindow_chargesTheLoadOnTheRedemptionValue() {
        // Given -- 60 units bought 10 Apr 2025 and 40 bought 10 Jun 2025, both at ₹100; 100 redeemed at ₹110 on
        // 1 May 2026 (W-264b G-2, W-303)
        tradeV2(fund("BUY", 60, 100, "2025-04-10"));
        tradeV2(fund("BUY", 40, 100, "2025-06-10"));
        assertEquals(200, tradeV2(fund("SELL", 100, 110, "2026-05-01")).getStatusCode().value());

        // When
        JsonNode sale = null;
        for (JsonNode row : json(get("/user-charges/user/" + EMAIL))) {
            if ("SELL".equals(row.get("event").asString())) {
                sale = row;
            }
        }

        // Then -- the 386-day lot pays nothing; the 325-day lot pays 1 % of its redemption value, 40 × ₹110 = ₹44.00
        // (prd.md:89 "% of redemption value"; was ₹40.00, 1 % of its cost)
        assertMoney(44.0, amounts(sale).get("EXIT_LOAD"));
    }

    private static Map<String, Object> fund(String side, double quantity, double price, String date) {
        Map<String, Object> body = trade("PARAGPARIKHFLEXICAP", "DELIVERY", side, quantity, price, date);
        body.put("assetType", "MUTUAL_FUND");
        body.remove("segment");
        return body;
    }
}
