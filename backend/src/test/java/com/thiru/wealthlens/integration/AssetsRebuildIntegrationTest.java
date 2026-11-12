package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.thiru.wealthlens.corporate.dto.CorporateActionPerformDto;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.service.CorporateActionService;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The {@code assets} replay against lots the live flows built, over a real database (W-283a2, D-582): a user whose
 * lots came through V1 and V2, and one whose lots went through a performed split, are rebuilt lot for lot and figure
 * for figure, and nothing is written. Each scenario's lots are worked by hand in its comments.
 */
class AssetsRebuildIntegrationTest extends AbstractIntegrationTest {

    private static final String MIXED = "rebuild-mixed@example.com";
    private static final String SPLIT = "rebuild-split@example.com";
    private static final String FIXED = "rebuild-fixed@example.com";
    private static final String ADMIN = "rebuild-admin@example.com";

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private CorporateActionService corporateActionService;

    @Autowired
    private TradeOutcomeRepository tradeOutcomeRepository;

    @Test
    void rebuild_whenLotsCameThroughBothFlows_rebuildsEveryLotAsStoredAndWritesNothing() {
        // Given -- V1: 10 @ 100 and 10 @ 120 on 5 Jan 2026 (one lot, 20 @ 110); V2: 6 @ 110 on 20 Jan (its own lot);
        // V2 sells 12 on 10 Feb (merged lot 20 → 8); V1 sells 3 on 12 Feb (8 → 5, its share of charges recorded)
        post(MIXED, "/transaction", trade(TransactionType.BUY, 10, 100, LocalDate.of(2026, 1, 5)));
        post(MIXED, "/transaction", trade(TransactionType.BUY, 10, 120, LocalDate.of(2026, 1, 5)));
        post(MIXED, "/transaction/v2", trade(TransactionType.BUY, 6, 110, LocalDate.of(2026, 1, 20)));
        post(MIXED, "/transaction/v2", trade(TransactionType.SELL, 12, 150, LocalDate.of(2026, 2, 10)));
        post(MIXED, "/transaction", trade(TransactionType.SELL, 3, 150, LocalDate.of(2026, 2, 12)));
        List<AssetEntity> before = portfolioRepository.findByEmail(MIXED);

        // When / Then -- two lots, each the same on both sides
        rebuild(MIXED, "SUPER_USER").statusCode(200)
                .body("lotsDiffering", equalTo(0)).body("lots", hasSize(2))
                .body("shapeNotFound", hasSize(0)).body("saleFlowUnknown", hasSize(0));
        assertThat(portfolioRepository.findByEmail(MIXED)).usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(before);
        assertThat(before).extracting(AssetEntity::getQuantity).containsExactlyInAnyOrder(5.0, 6.0);
    }

    @Test
    void rebuild_whenASplitWasPerformed_replaysItBetweenTheTrades() {
        // Given -- V2 buy 10 @ 100 on 5 Jan 2026; a 2:1 split recorded 10 Feb, performed; V2 sells 4 on 15 Feb
        post(SPLIT, "/transaction/v2", trade(TransactionType.BUY, 10, 100, LocalDate.of(2026, 1, 5)));
        CorporateActionEntity split = new CorporateActionEntity();
        split.setStockCode("INFY");
        split.setStockName("Infosys");
        split.setType(CorporateActionType.STOCK_SPLIT);
        split.setAssetType(AssetType.EQUITY);
        split.setRecordDate(LocalDate.of(2026, 2, 10));
        split.setExDate(LocalDate.of(2026, 2, 10));
        split.setRatio("2:1");
        split.setPriority(1);
        mongoTemplate.save(split);
        corporateActionService.performPendingCorporateActions(SPLIT,
                new CorporateActionPerformDto("JANUARY", 2026, BrokerName.ZERODHA), false);
        post(SPLIT, "/transaction/v2", trade(TransactionType.SELL, 4, 60, LocalDate.of(2026, 2, 15)));

        // When / Then -- 10 → 20 @ 50 at the split, 4 sold → 16; rebuilt the same, the split listed as replayed
        assertThat(portfolioRepository.findByEmail(SPLIT)).extracting(AssetEntity::getQuantity).containsExactly(16.0);
        rebuild(SPLIT, "SUPER_USER").statusCode(200)
                .body("lotsDiffering", equalTo(0)).body("lots", hasSize(1))
                .body("actionsApplied", contains("STOCK_SPLIT INFY 2026-02-10 ZERODHA"));
    }

    @Test
    void rebuild_whenAStoredLotWasCorrupted_restoresItUnderItsIdAndKeepsTheSalesLink() {
        // Given -- V2 buy 10 @ 100 (5 Jan 2026), V2 sells 4 (10 Feb): one lot of 6, named by the sale's outcome row;
        // then its quantity is corrupted to 8
        post(FIXED, "/transaction/v2", trade(TransactionType.BUY, 10, 100, LocalDate.of(2026, 1, 5)));
        post(FIXED, "/transaction/v2", trade(TransactionType.SELL, 4, 150, LocalDate.of(2026, 2, 10)));
        AssetEntity lot = portfolioRepository.findByEmail(FIXED).getFirst();
        lot.setQuantity(8.0);
        portfolioRepository.save(lot);

        // When -- a dry run, then a real one, then a second dry run
        rebuild(FIXED, "", "SUPER_USER").statusCode(200).body("lotsDiffering", equalTo(1)).body("lotsWritten", equalTo(0));
        double afterDryRun = portfolioRepository.findByEmail(FIXED).getFirst().getQuantity();
        rebuild(FIXED, "?dryRun=false", "SUPER_USER").statusCode(200)
                .body("lotsWritten", equalTo(1)).body("lotsKeptById", equalTo(1)).body("newLots", hasSize(0));

        // Then -- 8 untouched by the dry run; 10 − 4 = 6 restored under the same id, which the outcome row still names
        assertThat(afterDryRun).isEqualTo(8.0);
        List<AssetEntity> lots = portfolioRepository.findByEmail(FIXED);
        assertThat(lots).hasSize(1);
        assertThat(lots.getFirst().getId()).isEqualTo(lot.getId());
        assertThat(lots.getFirst().getQuantity()).isEqualTo(6.0);
        assertThat(tradeOutcomeRepository.findByEmail(FIXED)).extracting(TradeOutcomeEntity::getSourceBuyLotId)
                .containsExactly(lot.getId());
        rebuild(FIXED, "", "SUPER_USER").statusCode(200).body("lotsDiffering", equalTo(0));
    }

    @Test
    void rebuild_whenAStoredLotWasLost_restoresItUnderANewIdAndListsIt() {
        // Given -- V2 buy 10 @ 100 (5 Jan 2026); its lot then deleted
        post(FIXED, "/transaction/v2", trade(TransactionType.BUY, 10, 100, LocalDate.of(2026, 1, 5)));
        portfolioRepository.deleteAll(portfolioRepository.findByEmail(FIXED));

        // When
        rebuild(FIXED, "?dryRun=false", "SUPER_USER").statusCode(200)
                .body("lotsWritten", equalTo(1)).body("lotsKeptById", equalTo(0)).body("newLots", hasSize(1));

        // Then
        assertThat(portfolioRepository.findByEmail(FIXED)).extracting(AssetEntity::getQuantity).containsExactly(10.0);
    }

    @Test
    void rebuild_whenAUserAsksForTheirOwn_isForbidden() {
        given().header("Authorization", "Bearer " + generateToken(MIXED, "USER"))
                .post("/portfolio/user/" + MIXED + "/assets/rebuild").then().statusCode(403);
    }

    private void post(String email, String path, Map<String, Object> body) {
        given().header("Authorization", "Bearer " + generateToken(email)).contentType(ContentType.JSON).body(body)
                .post("/portfolio/user/" + email + path).then().statusCode(200);
    }

    private ValidatableResponse rebuild(String email, String role) {
        return rebuild(email, "", role);
    }

    private ValidatableResponse rebuild(String email, String query, String role) {
        return given().header("Authorization", "Bearer " + generateToken(ADMIN, role))
                .post("/portfolio/user/" + email + "/assets/rebuild" + query).then();
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
