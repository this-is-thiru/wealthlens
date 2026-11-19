package com.thiru.wealthlens.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.service.CapitalGainsSummaryService;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.util.time.FinancialYear;
import com.thiru.wealthlens.testsupport.MoneyAssert;
import io.restassured.RestAssured;
import java.net.URI;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

/**
 * A V2 buy no rate card prices keeps its entered charge, and selling it refuses the capital-gains summary rather than guess
 * what the charge was (W-223, D-437).
 */
class NoCardCostBasisIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "bondholder@example.com";

    @Autowired
    private CapitalGainsSummaryService capitalGainsSummaryService;

    private void post(TransactionType type, double brokerCharges) {
        AssetRequest request = new AssetRequest();
        request.setEmail(EMAIL);
        request.setStockCode("SGB-1");
        request.setStockName("A gold bond");
        request.setExchangeName("NSE");
        request.setBrokerName(BrokerName.ZERODHA);
        request.setAssetType(AssetType.GOLD_BOND);
        request.setMaturityDate(LocalDate.now().plusYears(5));
        request.setPrice(5000.0);
        request.setQuantity(10.0);
        request.setTransactionType(type);
        request.setAccountType(AccountType.SELF);
        request.setAccountHolder("Self");
        request.setTransactionDate(LocalDate.now());
        request.setBrokerCharges(brokerCharges);
        request.setMiscCharges(0.0);
        request.setTimezoneId("Asia/Kolkata");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(EMAIL));
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = new RestTemplate().exchange(
                URI.create("http://localhost:" + RestAssured.port + "/portfolio/user/" + EMAIL + "/transaction/v2"),
                HttpMethod.POST, new HttpEntity<>(request, headers), String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void addTransactionV2_whenNoCardPricesTheBuy_keepsTheEnteredCharge() {
        // When
        post(TransactionType.BUY, 240.0);

        // Then
        AssetEntity lot = mongoTemplate.findOne(new Query(Criteria.where("email").is(EMAIL)), AssetEntity.class);
        MoneyAssert.assertMoney(240.0, lot.getBrokerCharges());
    }

    @Test
    void summaryFor_whenASoldLotKeptAnEnteredCharge_refusesNamingIt() {
        // Given
        post(TransactionType.BUY, 240.0);
        post(TransactionType.SELL, 0.0);
        String year = FinancialYear.containing(LocalDate.now()).label();

        // When
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> capitalGainsSummaryService.summaryFor(EMAIL, year));

        // Then
        assertTrue(refusal.getMessage().contains("the first sold SGB-1"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains("cannot be computed until its charges can be entered by code"),
                refusal.getMessage());
    }

    @Test
    void summaryFor_whenASaleKeptAnEnteredCharge_refusesNamingIt() {
        // Given — nothing entered on the buy, 30 on the sale
        post(TransactionType.BUY, 0.0);
        post(TransactionType.SELL, 30.0);
        TradeOutcomeEntity row = mongoTemplate.findOne(new Query(Criteria.where("email").is(EMAIL)), TradeOutcomeEntity.class);
        String year = FinancialYear.containing(LocalDate.now()).label();

        // When / Then
        MoneyAssert.assertMoney(30.0, row.getSellBrokerCharges());
        assertThrows(IllegalArgumentException.class, () -> capitalGainsSummaryService.summaryFor(EMAIL, year));
    }

    @Test
    void summaryFor_whenNothingWasEnteredOnEitherSide_computes() {
        // Given
        post(TransactionType.BUY, 0.0);
        post(TransactionType.SELL, 0.0);

        // When / Then
        assertEquals(1, capitalGainsSummaryService.summaryFor(EMAIL, FinancialYear.containing(LocalDate.now()).label())
                .buckets().size());
    }
}
