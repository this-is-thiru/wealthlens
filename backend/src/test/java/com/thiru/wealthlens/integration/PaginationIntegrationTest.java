package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.taxplanning.gazette.GazetteLead;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import io.restassured.RestAssured;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
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
 * The seven lists that grow without bound answer a page (W-358c3b, D-861): Spring Data's `{"content", "page"}`, 50 by default, at
 * most 200. Each is read with three rows behind it, two to a page.
 */
class PaginationIntegrationTest extends AbstractIntegrationTest {

    private static final String USER = "pager@example.com";
    private static final String OWNER = "pager-admin@example.com";

    private ResponseEntity<String> call(HttpMethod method, String caller, String role, String path, String body, String ifMatch) {
        RestTemplate client = new RestTemplate(new JdkClientHttpRequestFactory());
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(caller, role));
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (ifMatch != null) {
            headers.set(HttpHeaders.IF_MATCH, ifMatch);
        }
        return client.exchange(URI.create("http://localhost:" + RestAssured.port + path), method, new HttpEntity<>(body, headers),
                String.class);
    }

    private static JsonNode json(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        return JsonMapper.builder().build().readTree(response.getBody());
    }

    /** Page 0 of two holds two, page 1 the third, and the page says three in all. */
    private void pagesThree(HttpMethod method, String caller, String role, String path, String body) {
        String join = path.contains("?") ? "&" : "?";
        JsonNode first = json(call(method, caller, role, path + join + "size=2", body, null));
        JsonNode second = json(call(method, caller, role, path + join + "size=2&page=1", body, null));
        assertThat(first.get("content").size()).as(path).isEqualTo(2);
        assertThat(first.at("/page/totalElements").asLong()).as(path).isEqualTo(3);
        assertThat(first.at("/page/size").asInt()).as(path).isEqualTo(2);
        assertThat(second.get("content").size()).as(path).isEqualTo(1);
    }

    private void threeTransactions() {
        for (int day = 1; day <= 3; day++) {
            TransactionEntity transaction = new TransactionEntity();
            transaction.setId("T-" + day);
            transaction.setEmail(USER);
            transaction.setStockCode("INFY");
            transaction.setStockName("INFY");
            transaction.setBrokerName(BrokerName.ZERODHA);
            transaction.setAssetType(AssetType.EQUITY);
            transaction.setQuantity(1.0);
            transaction.setTransactionType(TransactionType.BUY);
            transaction.setTransactionDate(LocalDate.of(2026, 1, day));
            mongoTemplate.save(transaction);
        }
    }

    @Test
    void transactions_whenThreeAreHeld_answerAPageOfTwo() {
        // Given
        threeTransactions();

        // When / Then
        pagesThree(HttpMethod.GET, USER, "USER", "/api/v1/me/portfolio/transactions", null);
    }

    @Test
    void transactionSearch_whenThreeMatch_answersAPageOfTwo() {
        // Given
        threeTransactions();

        // When / Then
        pagesThree(HttpMethod.POST, USER, "USER", "/api/v1/me/portfolio/transactions/search", "{\"queryFilters\": []}");
    }

    @Test
    void chargeHistory_whenThreeTradesWerePriced_answersAPageOfTwo() {
        // Given
        for (int day = 1; day <= 3; day++) {
            UserChargeEntity charge = new UserChargeEntity();
            charge.setEmail(USER);
            charge.setTransactionId("T-" + day);
            charge.setStockCode("INFY");
            charge.setAssetType(AssetType.EQUITY);
            charge.setTransactionDate(LocalDate.of(2026, 1, day));
            mongoTemplate.save(charge);
        }

        // When / Then
        pagesThree(HttpMethod.GET, USER, "USER", "/api/v1/me/charges/user-charges", null);
    }

    @Test
    void corporateActions_whenThreeAreRecorded_answerAPageOfTwo() {
        // Given
        for (int day = 1; day <= 3; day++) {
            CorporateActionEntity action = new CorporateActionEntity();
            action.setStockCode("STOCK" + day);
            action.setStockName("STOCK" + day);
            action.setType(CorporateActionType.BONUS);
            action.setAssetType(AssetType.EQUITY);
            action.setRatio("1:1");
            action.setPriority(day);
            action.setRecordDate(LocalDate.of(2026, 1, day));
            corporateActionRepository.save(action);
        }

        // When / Then
        pagesThree(HttpMethod.GET, USER, "USER", "/api/v1/me/corporate-actions", null);
    }

    @Test
    void gazetteLeads_whenThreeWereFlagged_answerAPageOfTwo() {
        // Given
        for (int number = 1; number <= 3; number++) {
            GazetteLead lead = new GazetteLead();
            lead.setUrl("https://egazette.gov.in/" + number);
            lead.setYear(2026);
            lead.setNumber(number);
            lead.setMatchedTerms(List.of("Income-tax"));
            lead.setFoundAt(LocalDateTime.of(2026, 1, number, 9, 0));
            mongoTemplate.save(lead);
        }

        // When / Then
        pagesThree(HttpMethod.GET, OWNER, "SUPER_USER", "/api/v1/admin/tax/gazette-leads", null);
    }

    private String profile() {
        call(HttpMethod.POST, USER, "USER", "/api/v1/me/tax/profiles", """
                {"name": "Self", "pan": "ABCDE1234F", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                 "residentialStatus": "RESIDENT"}""", null);
        return mongoTemplate.findAll(TaxpayerProfile.class).getFirst().getId();
    }

    private String draft(String profileId, String label) {
        String draftId = json(call(HttpMethod.POST, USER, "USER", "/api/v1/me/tax/profiles/" + profileId + "/workspaces/2026/drafts",
                "{\"label\": \"" + label + "\"}", null)).get("id").asString();
        json(call(HttpMethod.PATCH, USER, "USER", "/api/v1/me/tax/drafts/" + draftId, """
                [{"op": "add", "path": "/entered/employments/-", "value": {
                  "components": [{"code": {"value": "BASIC"}, "annualAmount": {"amountInRupees": 175000}}],
                  "termsIncludeDearnessAllowance": false, "employerKind": "OTHER",
                  "dearnessAllowanceCountsForRetirement": false, "companyEmployer": null}},
                 {"op": "replace", "path": "/filing/dueDate", "value": "2027-07-31"}]""", "\"rev-0\""));
        return draftId;
    }

    @Test
    void computations_whenThreeAreStored_answerAPageOfTwo() {
        // Given — three drafts, each computed once
        String profileId = profile();
        for (String label : List.of("One", "Two", "Three")) {
            json(call(HttpMethod.POST, USER, "USER", "/api/v1/me/tax/drafts/" + draft(profileId, label) + "/compute",
                    "{\"regime\": \"NEW_TAX_REGIME\"}", null));
        }

        // When / Then
        pagesThree(HttpMethod.GET, USER, "USER", "/api/v1/me/tax/profiles/" + profileId + "/computations", null);
    }

    @Test
    void revisions_whenADraftWasEditedTwice_answerAPageOfTwo() {
        // Given — revision 0 at creation, 1 and 2 from two edits
        String draftId = draft(profile(), "Mine");
        json(call(HttpMethod.PATCH, USER, "USER", "/api/v1/me/tax/drafts/" + draftId,
                "[{\"op\": \"replace\", \"path\": \"/filing/dueDate\", \"value\": \"2027-08-31\"}]", "\"rev-1\""));

        // When / Then
        pagesThree(HttpMethod.GET, USER, "USER", "/api/v1/me/tax/drafts/" + draftId + "/revisions", null);
    }

    @Test
    void page_whenAskedForMoreThanTwoHundred_isCutToTwoHundred() {
        // Given
        threeTransactions();

        // When
        JsonNode page = json(call(HttpMethod.GET, USER, "USER", "/api/v1/me/portfolio/transactions?size=1000", null, null));

        // Then — Spring caps the size at max-page-size (D-861 C2a)
        assertThat(page.at("/page/size").asInt()).isEqualTo(200);
        assertThat(page.get("content").size()).isEqualTo(3);
    }
}
