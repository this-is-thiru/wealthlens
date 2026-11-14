package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.thiru.wealthlens.corporate.dto.CorporateActionPerformDto;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.service.CorporateActionService;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A trade waits for a split, bonus or demerger recorded in an earlier quarter and not yet performed, when the user
 * holds lots it would reach (W-289, D-587 C1a). Before, only an action in the trade's own quarter held it, so a sale in
 * the next quarter drew pre-split units: holding 10, a 2:1 split, selling 4 left (10 − 4) × 2 = 12, not 10 × 2 − 4 = 16.
 */
class EarlierQuarterHoldIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "held-owner@example.com";
    private static final String HELD = "Transaction stored as temporary transaction";

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private CorporateActionService corporateActionService;

    @BeforeEach
    void splitRecordedLastQuarter() {
        // A 2:1 split recorded 20 Mar 2026 (the January quarter), not performed
        CorporateActionEntity split = new CorporateActionEntity();
        split.setStockCode("INFY");
        split.setStockName("Infosys");
        split.setType(CorporateActionType.STOCK_SPLIT);
        split.setAssetType(AssetType.EQUITY);
        split.setRecordDate(LocalDate.of(2026, 3, 20));
        split.setExDate(LocalDate.of(2026, 3, 20));
        split.setRatio("2:1");
        split.setPriority(1);
        mongoTemplate.save(split);
    }

    @Test
    void addTransactionV2_whenAnEarlierQuartersSplitIsUnperformed_holdsTheSaleUntilItIs() {
        // Given -- 10 bought 5 Jan 2026, before the split's record date
        post(trade(TransactionType.BUY, 10, 100, LocalDate.of(2026, 1, 5))).body(not(containsString(HELD)));

        // When -- 4 sold 5 Apr 2026, the next quarter
        ValidatableResponse sold = post(trade(TransactionType.SELL, 4, 60, LocalDate.of(2026, 4, 5)));

        // Then -- held, the lot untouched; performed and redriven: 10 × 2 − 4 = 16
        sold.body(containsString(HELD));
        assertThat(portfolioRepository.findByEmail(OWNER)).extracting(AssetEntity::getQuantity).containsExactly(10.0);
        corporateActionService.performPendingCorporateActions(OWNER,
                new CorporateActionPerformDto("JANUARY", 2026, BrokerName.ZERODHA), false);
        given().header("Authorization", "Bearer " + generateToken(OWNER))
                .post("/temporary-transactions/user/" + OWNER + "/redrive").then().statusCode(200);
        assertThat(portfolioRepository.findByEmail(OWNER)).extracting(AssetEntity::getQuantity).containsExactly(16.0);
    }

    @Test
    void addTransactionV2_whenNothingHeldIsReachedByTheEarlierSplit_appliesTheSale() {
        // Given -- 10 bought 2 Apr 2026, the next quarter: the split, recorded before it, reaches nothing of this user's
        post(trade(TransactionType.BUY, 10, 100, LocalDate.of(2026, 4, 2))).body(not(containsString(HELD)));

        // When / Then -- the 5 Apr sale is applied: 10 − 4 = 6
        post(trade(TransactionType.SELL, 4, 60, LocalDate.of(2026, 4, 5))).body(not(containsString(HELD)));
        assertThat(portfolioRepository.findByEmail(OWNER)).extracting(AssetEntity::getQuantity).containsExactly(6.0);
    }

    private ValidatableResponse post(Map<String, Object> body) {
        return given().header("Authorization", "Bearer " + generateToken(OWNER)).contentType(ContentType.JSON).body(body)
                .post("/portfolio/user/" + OWNER + "/transaction/v2").then().statusCode(200);
    }

    private static Map<String, Object> trade(TransactionType type, double quantity, double price, LocalDate date) {
        Map<String, Object> body = new HashMap<>();
        body.put("stockCode", "INFY");
        body.put("stockName", "Infosys");
        body.put("exchangeName", "NSE");
        body.put("brokerName", BrokerName.ZERODHA.name());
        body.put("assetType", AssetType.EQUITY.name());
        body.put("price", price);
        body.put("quantity", quantity);
        body.put("transactionType", type.name());
        body.put("accountType", AccountType.SELF.name());
        body.put("accountHolder", "Self");
        body.put("transactionDate", date.toString());
        return body;
    }
}
