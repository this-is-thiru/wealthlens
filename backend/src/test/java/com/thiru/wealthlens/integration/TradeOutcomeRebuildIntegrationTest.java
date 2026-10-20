package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.holding.HoldingPeriodSeeder;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import io.restassured.response.ValidatableResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The {@code trade_outcomes} rebuild as a {@code SUPER_USER} operation, over a real database (W-088u8b, D-284): users
 * a and b each bought 10 @ ₹100 and sold 5 and 5, so the rebuild makes 2 rows each; user c already has a row.
 */
class TradeOutcomeRebuildIntegrationTest extends AbstractIntegrationTest {

    private static final String A = "a@example.com";
    private static final String B = "b@example.com";
    private static final String C = "c@example.com";
    private static final String ADMIN = "admin@example.com";

    @Autowired
    private TradeOutcomeRepository tradeOutcomeRepository;

    @Autowired
    private HoldingPeriodSeeder holdingPeriodSeeder;

    @BeforeEach
    void seed() {
        holdingPeriodSeeder.seed("test");
        for (String user : new String[] {A, B, C}) {
            mongoTemplate.save(transaction(user, "buy", TransactionType.BUY, 10.0, LocalDate.of(2024, 1, 15)));
            mongoTemplate.save(transaction(user, "sell-1", TransactionType.SELL, 5.0, LocalDate.of(2025, 2, 3)));
            mongoTemplate.save(transaction(user, "sell-2", TransactionType.SELL, 5.0, LocalDate.of(2025, 3, 3)));
        }
        TradeOutcomeEntity existing = new TradeOutcomeEntity();
        existing.setId("c-v2-row");
        existing.setEmail(C);
        existing.setSourceBuyLotId("asset-64f0c0ffee");
        tradeOutcomeRepository.save(existing);
    }

    private ValidatableResponse rebuild(String query, String role) {
        return given().header("Authorization", "Bearer " + generateToken(ADMIN, role))
                .when().post("/trade-outcomes/rebuild" + query).then();
    }

    @Test
    void rebuild_whenADryRun_listsWhatItWouldWriteAndWritesNothing() {
        // When / Then — dryRun defaults to true; 2 users × 2 rows; c skipped with its 1 row
        rebuild("", "SUPER_USER").statusCode(200).body("dryRun", equalTo(true)).body("usersExamined", equalTo(3))
                .body("usersRebuilt", equalTo(2)).body("rowsWritten", equalTo(4))
                .body("skipped.email", contains(C)).body("skipped.existingRows", contains(1));
        assertThat(tradeOutcomeRepository.count()).isEqualTo(1);
    }

    @Test
    void rebuild_whenWritten_writesTheMissingUsersAsTheCaller() {
        // When
        rebuild("?dryRun=false", "SUPER_USER").statusCode(200).body("rowsWritten", equalTo(4));

        // Then — a's and b's rows by the caller; c's own row untouched
        assertThat(tradeOutcomeRepository.findByEmail(A)).hasSize(2);
        assertThat(tradeOutcomeRepository.findByEmail(B)).hasSize(2);
        assertThat(tradeOutcomeRepository.findByEmail(C)).extracting(TradeOutcomeEntity::getId).containsExactly("c-v2-row");
        assertThat(mongoTemplate.getCollection("trade_outcomes").find(new Document("email", A)).first()
                .get("audit_metadata", Document.class).getString("createdBy")).isEqualTo(ADMIN);
    }

    @Test
    void rebuild_whenRunAgain_writesNothing() {
        // Given
        rebuild("?dryRun=false", "SUPER_USER").statusCode(200);

        // When / Then
        rebuild("?dryRun=false", "SUPER_USER").statusCode(200).body("usersRebuilt", equalTo(0))
                .body("rowsWritten", equalTo(0)).body("skipped.email", contains(A, B, C));
        assertThat(tradeOutcomeRepository.count()).isEqualTo(5);
    }

    @Test
    void rebuild_whenAPartialRunLeftOneUserWritten_writesOnlyTheOther() {
        // Given — a's rows written by an earlier run that then failed
        TradeOutcomeEntity aRow = new TradeOutcomeEntity();
        aRow.setEmail(A);
        tradeOutcomeRepository.save(aRow);

        // When / Then
        rebuild("?dryRun=false", "SUPER_USER").statusCode(200).body("usersRebuilt", equalTo(1))
                .body("rowsWritten", equalTo(2)).body("skipped.email", contains(A, C));
        assertThat(tradeOutcomeRepository.findByEmail(B)).hasSize(2);
    }

    @Test
    void rebuild_whenNotASuperUser_isForbidden() {
        rebuild("?dryRun=false", "USER").statusCode(403);
        assertThat(tradeOutcomeRepository.count()).isEqualTo(1);
    }

    private static TransactionEntity transaction(String email, String id, TransactionType type, double quantity,
            LocalDate date) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(email + "-" + id);
        transaction.setEmail(email);
        transaction.setTransactionType(type);
        transaction.setStockCode("RELIANCE");
        transaction.setStockName("Reliance Industries");
        transaction.setExchangeName("NSE");
        transaction.setBrokerName(BrokerName.ZERODHA);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setAccountType(AccountType.SELF);
        transaction.setAccountHolder("main");
        transaction.setPrice(type == TransactionType.BUY ? 100.0 : 150.0);
        transaction.setQuantity(quantity);
        transaction.setBrokerCharges(type == TransactionType.BUY ? 10.0 : 0.0);
        transaction.setMiscCharges(type == TransactionType.BUY ? 4.0 : 0.0);
        transaction.setTransactionDate(date);
        transaction.setCorporateActions(new ArrayList<>());
        return transaction;
    }
}
