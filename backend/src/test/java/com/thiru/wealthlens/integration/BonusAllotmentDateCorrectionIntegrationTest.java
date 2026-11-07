package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.portfolio.dto.BonusAllotmentCorrectionReport;
import com.thiru.wealthlens.portfolio.dto.BonusAllotmentCorrectionReport.ActionCorrection;
import com.thiru.wealthlens.portfolio.dto.BonusAllotmentDate;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.migration.BonusAllotmentDateCorrection;
import java.time.LocalDate;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Bonus lots processed before W-088k4a moved from the ex-date to the allotment date, over a real database (D-308). */
class BonusAllotmentDateCorrectionIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate EX_DATE = LocalDate.of(2024, 1, 20);

    private static final LocalDate ALLOTTED = LocalDate.of(2024, 1, 22);

    private static final LocalDate SOLD = LocalDate.of(2025, 1, 21);

    private static final LocalDate BOUGHT = LocalDate.of(2023, 6, 1);

    @Autowired
    private BonusAllotmentDateCorrection correction;

    /** A bonus action, a holding's purchase lot and its bonus lot, both transactions, and a sale of the bonus lot. */
    private String bonusProcessedBeforeK4a(CorporateActionType type) {
        CorporateActionEntity action = new CorporateActionEntity();
        action.setStockCode("RELIANCE");
        action.setType(type);
        action.setAssetType(AssetType.EQUITY);
        action.setRecordDate(EX_DATE);
        action.setExDate(EX_DATE);
        action.setRatio("1:1");
        action = mongoTemplate.save(action);
        mongoTemplate.save(lot("parent", null, BOUGHT, action));
        mongoTemplate.save(lot("bonus", CorporateActionType.BONUS, EX_DATE, action));
        mongoTemplate.save(transaction("parent-txn", null, BOUGHT, action));
        mongoTemplate.save(transaction("bonus-txn", CorporateActionType.BONUS, EX_DATE, action));
        TradeOutcomeEntity sale = new TradeOutcomeEntity();
        sale.setId("sale");
        sale.setEmail("bonus@example.com");
        sale.setStockCode("RELIANCE");
        sale.setAssetType(AssetType.EQUITY);
        sale.setSegment(TradeSegment.DELIVERY);
        sale.setAcquisitionCorporateAction(CorporateActionType.BONUS);
        sale.setBuyDate(EX_DATE);
        sale.setSellDate(SOLD);
        sale.setHoldingPeriodDays(367L);
        mongoTemplate.save(sale);
        return action.getId();
    }

    private static AssetEntity lot(String id, CorporateActionType type, LocalDate on, CorporateActionEntity action) {
        AssetEntity lot = new AssetEntity();
        lot.setId(id);
        lot.setEmail("bonus@example.com");
        lot.setStockCode("RELIANCE");
        lot.setAssetType(AssetType.EQUITY);
        lot.setTransactionType(TransactionType.BUY);
        lot.setCorporateActionType(type);
        lot.setTransactionDate(on);
        lot.getCorporateActions().add(action);
        return lot;
    }

    private static TransactionEntity transaction(String id, CorporateActionType type, LocalDate on,
            CorporateActionEntity action) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setEmail("bonus@example.com");
        transaction.setStockCode("RELIANCE");
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setTransactionType(TransactionType.BUY);
        transaction.setCorporateActionType(type);
        transaction.setTransactionDate(on);
        transaction.getCorporateActions().add(action);
        return transaction;
    }

    private LocalDate lotDate(String id) {
        return mongoTemplate.findById(id, AssetEntity.class).getTransactionDate();
    }

    private LocalDate transactionDate(String id) {
        return mongoTemplate.findById(id, TransactionEntity.class).getTransactionDate();
    }

    private TradeOutcomeEntity sale() {
        return mongoTemplate.findById("sale", TradeOutcomeEntity.class);
    }

    @Test
    void correct_whenADryRun_reportsTheBonusDocumentsAndWritesNone() {
        // Given -- W-088k4b case 1
        String id = bonusProcessedBeforeK4a(CorporateActionType.BONUS);

        // When
        BonusAllotmentCorrectionReport report = correction.correct(List.of(new BonusAllotmentDate(id, ALLOTTED)),
                true, "test");

        // Then
        assertThat(report.actions()).containsExactly(
                new ActionCorrection(id, "RELIANCE", EX_DATE, ALLOTTED, 1, 1, 1));
        assertThat(lotDate("bonus")).isEqualTo(EX_DATE);
        assertThat(transactionDate("bonus-txn")).isEqualTo(EX_DATE);
        assertThat(sale().getBuyDate()).isEqualTo(EX_DATE);
        assertThat(mongoTemplate.findById(id, CorporateActionEntity.class).getAllotmentDate()).isNull();
    }

    @Test
    void correct_whenWritten_redatesTheBonusLotItsTransactionAndItsSaleOnly() {
        // Given -- W-088k4b case 2
        String id = bonusProcessedBeforeK4a(CorporateActionType.BONUS);

        // When
        correction.correct(List.of(new BonusAllotmentDate(id, ALLOTTED)), false, "test");

        // Then -- 22-1-2024 to 21-1-2025 is 365 days; the purchase lot keeps its date
        assertThat(mongoTemplate.findById(id, CorporateActionEntity.class).getAllotmentDate()).isEqualTo(ALLOTTED);
        assertThat(lotDate("bonus")).isEqualTo(ALLOTTED);
        assertThat(transactionDate("bonus-txn")).isEqualTo(ALLOTTED);
        assertThat(lotDate("parent")).isEqualTo(BOUGHT);
        assertThat(transactionDate("parent-txn")).isEqualTo(BOUGHT);
        assertThat(sale().getBuyDate()).isEqualTo(ALLOTTED);
        assertThat(sale().getHoldingPeriodDays()).isEqualTo(365L);
    }

    @Test
    void correct_whenRunAgain_findsNothingLeft() {
        // Given -- W-088k4b case 3
        String id = bonusProcessedBeforeK4a(CorporateActionType.BONUS);
        correction.correct(List.of(new BonusAllotmentDate(id, ALLOTTED)), false, "test");

        // When
        BonusAllotmentCorrectionReport second = correction.correct(List.of(new BonusAllotmentDate(id, ALLOTTED)),
                false, "test");

        // Then
        assertThat(second.actions()).containsExactly(
                new ActionCorrection(id, "RELIANCE", EX_DATE, ALLOTTED, 0, 0, 0));
        assertThat(lotDate("bonus")).isEqualTo(ALLOTTED);
    }

    @Test
    void correct_whenTheActionIsNotABonus_isRefusedWritingNothing() {
        // Given -- W-088k4b case 4
        String id = bonusProcessedBeforeK4a(CorporateActionType.DEMERGER);

        // When / Then
        assertThatThrownBy(() -> correction.correct(List.of(new BonusAllotmentDate(id, ALLOTTED)), false, "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is a DEMERGER, not a bonus");
        assertThat(lotDate("bonus")).isEqualTo(EX_DATE);
    }

    @Test
    void correct_whenTheActionHoldsAnotherAllotmentDate_isRefused() {
        // Given -- W-088k4b case 5
        String id = bonusProcessedBeforeK4a(CorporateActionType.BONUS);
        CorporateActionEntity action = mongoTemplate.findById(id, CorporateActionEntity.class);
        action.setAllotmentDate(ALLOTTED.plusDays(1));
        mongoTemplate.save(action);

        // When / Then
        assertThatThrownBy(() -> correction.correct(List.of(new BonusAllotmentDate(id, ALLOTTED)), false, "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already holds the allotment date 2024-01-23");
        assertThat(lotDate("bonus")).isEqualTo(EX_DATE);
    }

    @Test
    void correct_whenALaterEntryIsInvalid_writesNoEntry() {
        // Given -- W-088k4b case 6: every entry is validated before any is written
        String id = bonusProcessedBeforeK4a(CorporateActionType.BONUS);

        // When / Then
        assertThatThrownBy(() -> correction.correct(List.of(new BonusAllotmentDate(id, ALLOTTED),
                new BonusAllotmentDate("no-such-action", ALLOTTED)), false, "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No corporate action no-such-action");
        assertThat(lotDate("bonus")).isEqualTo(EX_DATE);
        assertThat(mongoTemplate.findById(id, CorporateActionEntity.class).getAllotmentDate()).isNull();
    }

    @Test
    void endpoint_whenWritten_redatesAndRecordsTheCaller() {
        // Given
        String id = bonusProcessedBeforeK4a(CorporateActionType.BONUS);

        // When / Then
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "SUPER_USER"))
                .contentType("application/json")
                .body("[{\"corporateActionId\": \"" + id + "\", \"allotmentDate\": \"2024-01-22\"}]")
                .when().post("/corporate-actions/bonus/allotment-dates/correct?dryRun=false")
                .then().statusCode(200).body("dryRun", equalTo(false)).body("actions[0].assets", equalTo(1));
        assertThat(lotDate("bonus")).isEqualTo(ALLOTTED);
        assertThat(mongoTemplate.getCollection("trade_outcomes").find(new Document("_id", "sale")).first()
                .get("audit_metadata", Document.class).getString("updatedBy")).isEqualTo("admin@example.com");
    }

    @Test
    void endpoint_whenNotASuperUser_isForbidden() {
        // Given
        String id = bonusProcessedBeforeK4a(CorporateActionType.BONUS);

        // When / Then
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "USER"))
                .contentType("application/json")
                .body("[{\"corporateActionId\": \"" + id + "\", \"allotmentDate\": \"2024-01-22\"}]")
                .when().post("/corporate-actions/bonus/allotment-dates/correct?dryRun=false")
                .then().statusCode(403);
        assertThat(lotDate("bonus")).isEqualTo(EX_DATE);
    }
}
