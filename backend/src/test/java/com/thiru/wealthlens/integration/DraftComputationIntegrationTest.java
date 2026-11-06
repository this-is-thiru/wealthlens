package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.portfolio.dto.enums.AcquisitionMode;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.taxplanning.compute.DraftComputation;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import io.restassured.RestAssured;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The first tax figure over HTTP: a stored draft, through the assembler and the shipped pipeline (W-103, W-104, W-107, W-109;
 * D-442). The case is {@code DR 13} T13-1, worked by hand in {@code plans/W-089.md}: 1,00,000 of salary, 11,00,000 of listed-equity
 * gains, 84,375 at 12.5 %, nil rebate, 3,375 of cess, a bill of 87,750. Computed on a fixed day, 6-11-2026, because interest for
 * deferring advance tax runs to the day of computation (W-112, D-446): by then 15 June and 15 September have passed.
 */
class DraftComputationIntegrationTest extends AbstractIntegrationTest {

    /** The day every computation here is made on. */
    @TestConfiguration
    static class FixedDay {

        @Bean
        @Primary
        Clock fixedDay() {
            return Clock.fixed(Instant.parse("2026-11-06T06:30:00Z"), ZoneId.of("Asia/Kolkata"));
        }
    }

    private static final String OWNER = "computer@example.com";
    private static final String REGIME = "{\"regime\": \"NEW_TAX_REGIME\"}";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ENTRIES = """
            [{"op": "add", "path": "/entered/employments/-", "value": {
              "components": [{"code": {"value": "BASIC"}, "annualAmount": {"amountInRupees": 175000}}],
              "termsIncludeDearnessAllowance": false, "employerKind": "OTHER",
              "dearnessAllowanceCountsForRetirement": false, "companyEmployer": null}},
             {"op": "replace", "path": "/filing/dueDate", "value": "2027-07-31"},
             {"op": "replace", "path": "/filing/taxAtSource", "value": {"amountInRupees": %s}}]""";

    private ResponseEntity<String> call(HttpMethod method, String caller, String path, String body, String ifMatch) {
        RestTemplate client = new RestTemplate(new JdkClientHttpRequestFactory());
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(caller));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (ifMatch != null) {
            headers.set(HttpHeaders.IF_MATCH, ifMatch);
        }
        return client.exchange(URI.create("http://localhost:" + RestAssured.port + "/tax/user/" + caller + path), method,
                new HttpEntity<>(body, headers), String.class);
    }

    /** A profile, its draft for TY 2026-27 with the salary and {@code taxAtSource}, and the draft's id. */
    private String draft(boolean holdsPortfolio, String taxAtSource) {
        call(HttpMethod.POST, OWNER, "/profiles", """
                {"name": "Self", "pan": "ABCDE1234F", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                 "residentialStatus": "RESIDENT", "holdsPortfolio": %s}""".formatted(holdsPortfolio), null);
        String profileId = mongoTemplate.findAll(TaxpayerProfile.class).getFirst().getId();
        call(HttpMethod.POST, OWNER, "/profiles/" + profileId + "/workspaces/2026/drafts", "{\"label\": \"Mine\"}", null);
        String draftId = mongoTemplate.findAll(FilingDraft.class).getFirst().getId();
        ResponseEntity<String> saved = call(HttpMethod.PATCH, OWNER, "/drafts/" + draftId, ENTRIES.formatted(taxAtSource),
                "\"rev-0\"");
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        return draftId;
    }

    /** T13-1's lot: bought 1-4-2024 for 5,00,000, sold on NSE on 1-9-2026 for 16,00,000, STT paid both sides. */
    private void listedEquitySale() {
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setId("t13-lot");
        row.setEmail(OWNER);
        row.setStockCode("INFY");
        row.setAssetType(AssetType.EQUITY);
        row.setBuyDate(LocalDate.of(2024, 4, 1));
        row.setSellDate(LocalDate.of(2026, 9, 1));
        row.setBuyQuantity(1.0);
        row.setSellQuantity(1.0);
        row.setCorporateActionAdjustedBuyPrice(500000);
        row.setSellPrice(1600000);
        row.setBuyChargeBreakup(Map.of("STT", 1.0));
        row.setSellChargeBreakup(Map.of("STT", 1.0));
        row.setBuyBrokerCharges(1.0);
        row.setSellBrokerCharges(1.0);
        row.setSellExchangeName("NSE");
        row.setAcquisitionMode(AcquisitionMode.PURCHASE);
        mongoTemplate.save(row);
    }

    /** A short lot and a deposit beside T13-1's: 10-1-2026 → 1-9-2026, 1,00,000 → 1,20,000; a deposit's interest elsewhere. */
    private void shortLotAndDeposit() {
        TradeOutcomeEntity lot = new TradeOutcomeEntity();
        lot.setId("short-lot");
        lot.setEmail(OWNER);
        lot.setStockCode("TCS");
        lot.setAssetType(AssetType.EQUITY);
        lot.setBuyDate(LocalDate.of(2026, 1, 10));
        lot.setSellDate(LocalDate.of(2026, 9, 1));
        lot.setBuyQuantity(1.0);
        lot.setSellQuantity(1.0);
        lot.setCorporateActionAdjustedBuyPrice(100000);
        lot.setSellPrice(120000);
        lot.setBuyChargeBreakup(Map.of("STT", 1.0));
        lot.setSellChargeBreakup(Map.of("STT", 1.0));
        lot.setBuyBrokerCharges(1.0);
        lot.setSellBrokerCharges(1.0);
        lot.setSellExchangeName("NSE");
        lot.setAcquisitionMode(AcquisitionMode.PURCHASE);
        mongoTemplate.save(lot);
        TradeOutcomeEntity deposit = new TradeOutcomeEntity();
        deposit.setId("deposit");
        deposit.setEmail(OWNER);
        deposit.setStockCode("FD-1");
        deposit.setAssetType(AssetType.FD);
        deposit.setBuyDate(LocalDate.of(2024, 5, 1));
        deposit.setSellDate(LocalDate.of(2026, 9, 2));
        deposit.setBuyQuantity(1.0);
        deposit.setSellQuantity(1.0);
        deposit.setCorporateActionAdjustedBuyPrice(100000);
        deposit.setSellPrice(107000);
        deposit.setAcquisitionMode(AcquisitionMode.PURCHASE);
        mongoTemplate.save(deposit);
    }

    private JsonNode compute(String draftId) {
        ResponseEntity<String> computed = call(HttpMethod.POST, OWNER, "/drafts/" + draftId + "/compute", REGIME, null);
        assertThat(computed.getStatusCode()).as(computed.getBody()).isEqualTo(HttpStatus.OK);
        return JSON.readTree(computed.getBody());
    }

    private static BigDecimal rupees(JsonNode view, String figure) {
        return view.at("/tax/" + figure + "/amountInRupees").decimalValue();
    }

    @Test
    void compute_whenT13IsEntered_chargesTheGoldenBill() {
        // Given
        listedEquitySale();
        String draftId = draft(true, "0");

        // When
        JsonNode view = compute(draftId);

        // Then — the same 87,750 the golden case gets from its own inputs; nothing paid in advance, so by 6-11-2026 June
        // owes 13,100 × 3 % = 393 and September 39,400 × 3 % = 1,182: 87,750 + 1,575 = 89,325, to the nearest ₹10 89,330
        assertThat(rupees(view, "bill")).isEqualByComparingTo("87750");
        assertThat(rupees(view, "credits")).isEqualByComparingTo("0");
        assertThat(view.at("/tax/advanceTax/liable").asBoolean()).isTrue();
        assertThat(view.at("/tax/interestAndFees/0/rule").asString()).isEqualTo("INT-DEFERMENT");
        assertThat(view.at("/tax/interestAndFees/0/amount/amountInRupees").decimalValue()).isEqualByComparingTo("1575");
        assertThat(rupees(view, "finalAmount")).isEqualByComparingTo("89330");
        assertThat(view.get("capitalGainsIncluded").asBoolean()).isTrue();
        assertThat(view.get("revision").asInt()).isEqualTo(1);
        assertThat(view.get("notIncluded").size()).isEqualTo(8);
        assertThat(view.at("/tax/trace").size()).isPositive();
    }

    @Test
    void compute_whenTaxWasDeductedAtSource_creditsItAgainstTheBill() {
        // Given — 87,750 − 75,000 = 12,750, still ₹10,000 or more, so advance tax was due
        listedEquitySale();

        // When
        JsonNode view = compute(draft(true, "75000"));

        // Then — June 1,900 × 3 % = 57, September 5,700 × 3 % = 171: 12,978, to the nearest ₹10 12,980
        assertThat(rupees(view, "bill")).isEqualByComparingTo("87750");
        assertThat(rupees(view, "credits")).isEqualByComparingTo("75000");
        assertThat(rupees(view, "finalAmount")).isEqualByComparingTo("12980");
    }

    @Test
    void compute_whenMoreWasDeductedThanIsDue_showsARefundToTheNearestTen() {
        // Given — 87,750 − 1,00,004 = −12,254; no advance tax was due, so nothing is owed on it
        listedEquitySale();

        // When / Then
        assertThat(rupees(compute(draft(true, "100004")), "finalAmount")).isEqualByComparingTo("-12250");
    }

    @Test
    void compute_whenTheProfileDoesNotHoldThePortfolio_readsNoGains() {
        // Given — the same trades in the account, on a profile that does not hold them: 1,00,000 of salary is under the nil band
        listedEquitySale();

        // When
        JsonNode view = compute(draft(false, "0"));

        // Then
        assertThat(rupees(view, "bill")).isEqualByComparingTo("0");
        assertThat(view.get("capitalGainsIncluded").asBoolean()).isFalse();
    }

    @Test
    void profile_whenASecondHoldsThePortfolio_expected400() {
        // Given
        draft(true, "0");

        // When
        ResponseEntity<String> second = call(HttpMethod.POST, OWNER, "/profiles", """
                {"name": "HUF", "pan": "PQRST5678K", "kind": "HUF", "residentialStatus": "RESIDENT",
                 "holdsPortfolio": true}""", null);

        // Then
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void compute_whenNoDueDateIsStated_expected400NamingIt() {
        // Given
        call(HttpMethod.POST, OWNER, "/profiles", """
                {"name": "Self", "pan": "ABCDE1234F", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                 "residentialStatus": "RESIDENT"}""", null);
        String profileId = mongoTemplate.findAll(TaxpayerProfile.class).getFirst().getId();
        call(HttpMethod.POST, OWNER, "/profiles/" + profileId + "/workspaces/2026/drafts", "{\"label\": \"Mine\"}", null);
        String draftId = mongoTemplate.findAll(FilingDraft.class).getFirst().getId();

        // When
        ResponseEntity<String> refused = call(HttpMethod.POST, OWNER, "/drafts/" + draftId + "/compute", REGIME, null);

        // Then
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).contains("/filing/dueDate");
    }

    @Test
    void compute_whenTheRegimeIsUnknown_expected400() {
        // When / Then
        assertThat(call(HttpMethod.POST, OWNER, "/drafts/" + draft(false, "0") + "/compute", "{\"regime\": \"NO_SUCH\"}", null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void byReceipt_whenTheOwnerAsks_isWhatWasShownAndAnotherAccountIsRefused() {
        // Given
        JsonNode view = compute(draft(false, "0"));
        String receipt = view.at("/tax/receipt").asString();

        // When
        ResponseEntity<String> again = call(HttpMethod.GET, OWNER, "/computations/" + receipt, null, null);

        // Then
        assertThat(JSON.readTree(again.getBody())).isEqualTo(view);
        assertThat(call(HttpMethod.GET, "other@example.com", "/computations/" + receipt, null, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mongoTemplate.count(new Query(Criteria.where("email").is(OWNER)), DraftComputation.class)).isEqualTo(1);
    }

    @Test
    void compute_whenRunTwiceOnOneRevision_givesOneFingerprint() {
        // Given
        String draftId = draft(false, "0");

        // When / Then — dates of a run are not in it: asOf is the same day here
        assertThat(compute(draftId).at("/tax/inputsFingerprint").asString())
                .isEqualTo(compute(draftId).at("/tax/inputsFingerprint").asString());
    }

    @Test
    void compareRegimes_whenAsked_pricesEveryRegimeAndChangesNothing() {
        // Given
        String draftId = draft(false, "0");

        // When
        ResponseEntity<String> compared = call(HttpMethod.POST, OWNER, "/drafts/" + draftId + "/compare-regimes", null, null);

        // Then
        assertThat(compared.getStatusCode()).as(compared.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode comparison = JSON.readTree(compared.getBody());
        assertThat(comparison.get("priced").size()).isEqualTo(2);
        assertThat(mongoTemplate.findAll(FilingDraft.class).getFirst().getCurrentRevision()).isEqualTo(1);
        assertThat(mongoTemplate.findAll(DraftComputation.class)).isEmpty();
    }

    @Test
    void byReceipt_whenBucketsDiffer_recordsWhatWasDecidedForEach() {
        // Given
        listedEquitySale();
        shortLotAndDeposit();
        JsonNode view = compute(draft(true, "0"));

        // When
        JsonNode buckets = JSON.readTree(call(HttpMethod.GET, OWNER, "/computations/" + view.at("/tax/receipt").asString(), null,
                null).getBody()).at("/tax/capitalGainsBuckets");

        // Then — sold 1-9, 1-9, 2-9: 16,00,000 − 5,00,000; 1,20,000 − 1,00,000; 1,07,000 − 1,00,000 left to other sources
        assertThat(buckets.size()).isEqualTo(3);
        JsonNode longLot = bucketGaining(buckets, "1100000");
        assertThat(longLot.get("term").asString()).isEqualTo("LONG_TERM");
        assertThat(longLot.get("termRow").asString()).isEqualTo("CG-TERM-LISTED-SECURITY");
        assertThat(longLot.get("monthsRead").asInt()).isEqualTo(12);
        JsonNode shortLot = bucketGaining(buckets, "20000");
        assertThat(shortLot.get("term").asString()).isEqualTo("SHORT_TERM");
        assertThat(shortLot.get("termRow").asString()).isEqualTo("CG-TERM-LISTED-SECURITY");
        JsonNode deposit = bucketGaining(buckets, "7000");
        assertThat(deposit.get("leftOutBy").asString()).isEqualTo("CG-DEPOSIT");
        assertThat(deposit.get("term").isNull()).isTrue();
    }

    private static JsonNode bucketGaining(JsonNode buckets, String rupees) {
        for (JsonNode bucket : buckets) {
            if (bucket.at("/gain/amountInRupees").decimalValue().compareTo(new BigDecimal(rupees)) == 0) {
                return bucket;
            }
        }
        throw new AssertionError("No bucket gained " + rupees + " in " + buckets);
    }

    private String holderId() {
        call(HttpMethod.POST, OWNER, "/profiles", """
                {"name": "Self", "pan": "ABCDE1234F", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                 "residentialStatus": "RESIDENT", "holdsPortfolio": true}""", null);
        return mongoTemplate.findAll(TaxpayerProfile.class).getFirst().getId();
    }

    private JsonNode capitalGains(String profileId, String query) {
        ResponseEntity<String> view = call(HttpMethod.GET, OWNER, "/profiles/" + profileId + "/capital-gains/2026" + query, null, null);
        assertThat(view.getStatusCode()).as(view.getBody()).isEqualTo(HttpStatus.OK);
        return JSON.readTree(view.getBody());
    }

    private void heldLot() {
        AssetEntity lot = new AssetEntity();
        lot.setEmail(OWNER);
        lot.setStockCode("HELD");
        lot.setAssetType(AssetType.EQUITY);
        lot.setExchangeName("NSE");
        lot.setQuantity(10.0);
        lot.setPrice(100);
        lot.setTransactionDate(LocalDate.of(2025, 6, 1));
        mongoTemplate.save(lot);
    }

    @Test
    void capitalGains_whenChargesAreOnASale_namesEachOnesLimb() {
        // Given — brokerage 100 and STT 80 on each side
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setId("charged");
        row.setEmail(OWNER);
        row.setStockCode("INFY");
        row.setAssetType(AssetType.EQUITY);
        row.setBuyDate(LocalDate.of(2024, 4, 1));
        row.setSellDate(LocalDate.of(2026, 9, 1));
        row.setBuyQuantity(1.0);
        row.setSellQuantity(1.0);
        row.setCorporateActionAdjustedBuyPrice(500000);
        row.setSellPrice(1600000);
        row.setBuyChargeBreakup(Map.of("BROKERAGE", 100.0, "STT", 80.0));
        row.setSellChargeBreakup(Map.of("BROKERAGE", 100.0, "STT", 80.0));
        row.setSellExchangeName("NSE");
        row.setAcquisitionMode(AcquisitionMode.PURCHASE);
        mongoTemplate.save(row);

        // When
        JsonNode charges = capitalGains(holderId(), "").at("/realised/0/charges");

        // Then
        Map<String, String> limbs = new java.util.HashMap<>();
        charges.forEach(charge -> limbs.put(charge.get("side").asString() + "/" + charge.get("code").asString(), charge.get("limb").asString()));
        assertThat(limbs).containsEntry("ACQUISITION/BROKERAGE", "COST_OF_ACQUISITION")
                .containsEntry("TRANSFER/BROKERAGE", "TRANSFER_EXPENDITURE")
                .containsEntry("ACQUISITION/STT", "NOT_DEDUCTED")
                .containsEntry("TRANSFER/STT", "NOT_DEDUCTED");
    }

    @Test
    void capitalGains_whenHeldLotsAreAskedFor_readsEachLotsTermIfSoldThen() {
        // Given — bought 1-6-2025; twelve calendar months end 1-6-2026, long-term only after it (D-303)
        heldLot();
        String id = holderId();

        // When / Then
        JsonNode may = capitalGains(id, "?asOf=2026-05-01").at("/held/0");
        assertThat(may.get("term").asString()).as(may.toString()).isEqualTo("SHORT_TERM");
        JsonNode june = capitalGains(id, "?asOf=2026-06-01").at("/held/0");
        assertThat(june.get("term").asString()).as(june.toString()).isEqualTo("SHORT_TERM");
        JsonNode longTerm = capitalGains(id, "?asOf=2026-06-02").at("/held/0");
        assertThat(longTerm.get("term").asString()).isEqualTo("LONG_TERM");
        assertThat(longTerm.get("termRow").asString()).isEqualTo("CG-TERM-LISTED-SECURITY");
        assertThat(mongoTemplate.findAll(DraftComputation.class)).isEmpty();
    }

    @Test
    void capitalGains_whenTheProfileDoesNotHoldThePortfolio_expected400() {
        // Given
        draft(false, "0");
        String id = mongoTemplate.findAll(TaxpayerProfile.class).getFirst().getId();

        // When / Then
        assertThat(call(HttpMethod.GET, OWNER, "/profiles/" + id + "/capital-gains/2026", null, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void compareRegimes_whenARegimeWasElectedInAnEarlierYear_stillPricesBoth() {
        // Given — filed TY 2025-26 on FIRST_SCHEDULE_RATES; a person with no business income has the free annual choice
        call(HttpMethod.POST, OWNER, "/profiles", """
                {"name": "Self", "pan": "ABCDE1234F", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                 "residentialStatus": "RESIDENT"}""", null);
        String id = mongoTemplate.findAll(TaxpayerProfile.class).getFirst().getId();
        call(HttpMethod.POST, OWNER, "/profiles/" + id + "/workspaces/2025/drafts", "{\"label\": \"Filed\"}", null);
        String filed = mongoTemplate.findAll(FilingDraft.class).getFirst().getId();
        call(HttpMethod.POST, OWNER, "/drafts/" + filed + "/status", "{\"status\": \"READY\"}", null);
        call(HttpMethod.POST, OWNER, "/drafts/" + filed + "/status", "{\"status\": \"ACTED_ON\", \"regimeCode\": \"FIRST_SCHEDULE_RATES\"}", null);
        call(HttpMethod.POST, OWNER, "/profiles/" + id + "/workspaces/2026/drafts", "{\"label\": \"Next\"}", null);
        String next = mongoTemplate.findAll(FilingDraft.class).stream().filter(d -> d.getTaxYearStart() == 2026).findFirst().orElseThrow().getId();
        call(HttpMethod.PATCH, OWNER, "/drafts/" + next, "[{\"op\": \"replace\", \"path\": \"/filing/dueDate\", \"value\": \"2027-07-31\"}]", "\"rev-0\"");

        // When
        ResponseEntity<String> compared = call(HttpMethod.POST, OWNER, "/drafts/" + next + "/compare-regimes", null, null);

        // Then
        assertThat(compared.getStatusCode()).as(compared.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(compared.getBody()).get("priced").size()).isEqualTo(2);
    }

    private ResponseEntity<String> bring(String draftId, String map, String key, String value) {
        return call(HttpMethod.PATCH, OWNER, "/drafts/" + draftId,
                "[{\"op\": \"add\", \"path\": \"/brought/" + map + "/" + key + "\", \"value\": " + value + "}]", "\"rev-1\"");
    }

    private static String money(String rupees) {
        return "{\"amountInRupees\": " + rupees + "}";
    }

    @Test
    void compute_whenALongTermLossIsBroughtForward_setsItOffBeforeTheBill() {
        // Given — 11,00,000 − 1,00,000 = 10,00,000; 3,00,000 of the nil band → 7,00,000; above 1,25,000 is 5,75,000; at 12.5 %
        // 71,875; cess 2,875
        listedEquitySale();
        String draftId = draft(true, "0");
        String loss = "{\"shortTermLoss\": " + money("0") + ", \"longTermLoss\": " + money("100000") + ", \"shortTermAbsorbedSince\": "
                + money("0") + ", \"longTermAbsorbedSince\": " + money("0") + ", \"determinedInReturnFiledInTime\": true}";
        assertThat(bring(draftId, "capitalLossCarriedForward", "2024", loss).getStatusCode()).isEqualTo(HttpStatus.OK);

        // When / Then
        assertThat(rupees(compute(draftId), "bill")).isEqualByComparingTo("74750");
    }

    @Test
    void compute_whenAHousePropertyLossHasNoHousePropertyIncomeToMeet_changesNothing() {
        // Given — a carried house-property loss meets only house-property income (2025 s.110), and this draft has none
        listedEquitySale();
        String draftId = draft(true, "0");
        String loss = "{\"loss\": " + money("50000") + ", \"attributableToNewRegimeDisallowance\": " + money("0") + ", \"absorbedSince\": "
                + money("0") + "}";
        assertThat(bring(draftId, "housePropertyLossCarriedForward", "2024", loss).getStatusCode()).isEqualTo(HttpStatus.OK);

        // When / Then
        assertThat(rupees(compute(draftId), "bill")).isEqualByComparingTo("87750");
    }

    @Test
    void patch_whenWhatIsBroughtForwardIsMisspelt_expected400NamingIt() {
        // Given
        String draftId = draft(false, "0");

        // When
        ResponseEntity<String> refused = call(HttpMethod.PATCH, OWNER, "/drafts/" + draftId,
                "[{\"op\": \"add\", \"path\": \"/brought/capitalLossCarried\", \"value\": {}}]", "\"rev-1\"");

        // Then
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refused.getBody()).contains("capitalLossCarried");
    }
}
