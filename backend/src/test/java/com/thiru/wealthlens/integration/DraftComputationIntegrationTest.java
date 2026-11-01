package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.portfolio.dto.enums.AcquisitionMode;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.taxplanning.compute.DraftComputation;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import com.thiru.wealthlens.taxplanning.workspace.FilingDraft;
import io.restassured.RestAssured;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
 * gains, 84,375 at 12.5 %, nil rebate, 3,375 of cess, a bill of 87,750.
 */
class DraftComputationIntegrationTest extends AbstractIntegrationTest {

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

        // Then — the same 87,750 the golden case gets from its own inputs
        assertThat(rupees(view, "bill")).isEqualByComparingTo("87750");
        assertThat(rupees(view, "credits")).isEqualByComparingTo("0");
        assertThat(rupees(view, "finalAmount")).isEqualByComparingTo("87750");
        assertThat(view.get("capitalGainsIncluded").asBoolean()).isTrue();
        assertThat(view.get("revision").asInt()).isEqualTo(1);
        assertThat(view.get("notIncluded").size()).isEqualTo(2);
        assertThat(view.at("/tax/trace").size()).isPositive();
    }

    @Test
    void compute_whenTaxWasDeductedAtSource_creditsItAgainstTheBill() {
        // Given — 87,750 − 75,000
        listedEquitySale();

        // When
        JsonNode view = compute(draft(true, "75000"));

        // Then
        assertThat(rupees(view, "bill")).isEqualByComparingTo("87750");
        assertThat(rupees(view, "credits")).isEqualByComparingTo("75000");
        assertThat(rupees(view, "finalAmount")).isEqualByComparingTo("12750");
    }

    @Test
    void compute_whenMoreWasDeductedThanIsDue_showsARefundToTheNearestTen() {
        // Given — 87,750 − 1,00,004 = −12,254
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
}
