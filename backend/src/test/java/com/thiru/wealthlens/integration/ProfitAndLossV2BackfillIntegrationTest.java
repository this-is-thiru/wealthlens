package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.thiru.wealthlens.portfolio.dto.V2BackfillReport;
import com.thiru.wealthlens.portfolio.dto.context.TradeOutcomeContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.migration.ProfitAndLossV2Backfill;
import com.thiru.wealthlens.portfolio.service.TradeOutcomeService;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.testsupport.MoneyAssert;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The V2 sales' profit, brokerage and misc backfilled into the P&L split, over a real database (W-172, D-373). */
class ProfitAndLossV2BackfillIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "backfill@example.com";

    @Autowired
    private TradeOutcomeService tradeOutcomeService;

    @Autowired
    private ProfitAndLossV2Backfill backfill;

    /** Well after any row a test writes: every row is "before the deploy" unless a test says otherwise. */
    private static final LocalDateTime AFTER_EVERY_ROW = LocalDateTime.now().plusDays(1);

    /** Purchase 1,000, sell 1,500, no profit, brokerage or misc — as the V2 P&L left it — at year, December, first half. */
    private static Document v2Entry() {
        Document half = new Document("purchase_amount", 1000.0).append("sell_amount", 1500.0).append("profit", 0.0)
                .append("brokerage", 0.0).append("misc_charges", 0.0);
        Document december = Document.parse(half.toJson()).append("month", "DECEMBER").append("first_half_report", half);
        return Document.parse(half.toJson()).append("capital_gains", new Document("DECEMBER", december));
    }

    private void insertPnl(String id) {
        mongoTemplate.getCollection("profit_and_loss").insertOne(new Document("_id", id).append("email", EMAIL)
                .append("financial_year", "2024-2025").append("realised_profits", new Document(
                        "gains_by_asset_and_segment", new Document("EQUITY", new Document("DELIVERY", v2Entry())))));
    }

    /**
     * A recorder row sold on 2024-12-10: buy charge 20, buy misc 5, sell charges 30, net profit 445 (10 bought at 100,
     * sold at 150: 1,470 - 1,025).
     */
    private TradeOutcomeEntity recorderRow(String id) {
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setId(id);
        row.setEmail(EMAIL);
        row.setAssetType(AssetType.EQUITY);
        row.setSellSegment(TradeSegment.DELIVERY);
        row.setAccountType(AccountType.SELF);
        row.setSellDate(LocalDate.of(2024, 12, 10));
        row.setBuyBrokerCharges(20.0);
        row.setBuyMiscCharges(5.0);
        row.setSellBrokerCharges(30.0);
        row.setNetProfit(445.0);
        row.setBuyChargeBreakup(Map.of());
        row.setSellChargeBreakup(Map.of("BROKERAGE", 30.0));
        // As auditing stamps a row saved without an id; a fixture with an id is not "new", so it is set here
        row.getAuditMetadata().setCreatedAt(LocalDateTime.now());
        return mongoTemplate.save(row);
    }

    private Document entry(String id) {
        return mongoTemplate.getCollection("profit_and_loss").find(new Document("_id", id)).first()
                .get("realised_profits", Document.class).get("gains_by_asset_and_segment", Document.class)
                .get("EQUITY", Document.class).get("DELIVERY", Document.class);
    }

    private static Document[] levels(Document year) {
        Document december = year.get("capital_gains", Document.class).get("DECEMBER", Document.class);
        return new Document[] {year, december, december.get("first_half_report", Document.class)};
    }

    @Test
    void backfill_whenAV2RowWasWrittenBeforeTheDeploy_addsItsThreeFiguresAtEveryLevel() {
        // Given
        insertPnl("p1");
        recorderRow("r1");

        // When
        V2BackfillReport report = backfill.backfill(false, AFTER_EVERY_ROW, "test");

        // Then -- brokerage 20 + 30 = 50, misc 5, profit 445; purchase and sell as they were
        for (Document level : levels(entry("p1"))) {
            MoneyAssert.assertMoney(50.0, level.getDouble("brokerage"));
            MoneyAssert.assertMoney(5.0, level.getDouble("misc_charges"));
            MoneyAssert.assertMoney(445.0, level.getDouble("profit"));
            MoneyAssert.assertMoney(1000.0, level.getDouble("purchase_amount"));
            MoneyAssert.assertMoney(1500.0, level.getDouble("sell_amount"));
        }
        assertThat(report.placed()).singleElement().satisfies(placed -> assertThat(placed.rowId()).isEqualTo("r1"));
    }

    @Test
    void backfill_whenARowHasNoChargeBreakup_leavesItAlone() {
        // Given -- a V1 or rebuilt row: its figures are in the P&L already
        insertPnl("p1");
        TradeOutcomeEntity v1 = recorderRow("v1");
        v1.setBuyChargeBreakup(null);
        v1.setSellChargeBreakup(null);
        mongoTemplate.save(v1);

        // When
        V2BackfillReport report = backfill.backfill(false, AFTER_EVERY_ROW, "test");

        // Then
        assertThat(report.placed()).isEmpty();
        MoneyAssert.assertMoney(0.0, entry("p1").getDouble("profit"));
    }

    @Test
    void backfill_whenTheRowWasWrittenAfterTheCutOff_leavesItAlone() {
        // Given -- written now, cut-off an hour ago: W-169 recorded it in full
        insertPnl("p1");
        recorderRow("r1");

        // When
        V2BackfillReport report = backfill.backfill(false, LocalDateTime.now().minusHours(1), "test");

        // Then
        assertThat(report.placed()).isEmpty();
        MoneyAssert.assertMoney(0.0, entry("p1").getDouble("profit"));
    }

    @Test
    void backfill_whenRunAgain_addsNothingTwice() {
        // Given
        insertPnl("p1");
        recorderRow("r1");
        backfill.backfill(false, AFTER_EVERY_ROW, "first");

        // When
        V2BackfillReport second = backfill.backfill(false, AFTER_EVERY_ROW, "second");

        // Then
        assertThat(second.placed()).isEmpty();
        assertThat(second.alreadyCounted()).isEqualTo(1);
        MoneyAssert.assertMoney(445.0, entry("p1").getDouble("profit"));
    }

    @Test
    void backfill_whenOneRowWasCountedBefore_addsOnlyTheOther() {
        // Given -- a run that stopped after r1
        insertPnl("p1");
        recorderRow("r1");
        backfill.backfill(false, AFTER_EVERY_ROW, "first");
        recorderRow("r2");

        // When
        V2BackfillReport report = backfill.backfill(false, AFTER_EVERY_ROW, "second");

        // Then -- 445 + 445 = 890
        assertThat(report.placed()).singleElement().satisfies(placed -> assertThat(placed.rowId()).isEqualTo("r2"));
        MoneyAssert.assertMoney(890.0, entry("p1").getDouble("profit"));
    }

    @Test
    void backfill_whenTheSplitHasNoEntryForTheRow_listsItAndWritesNothing() {
        // Given -- the row is intraday; the document has a delivery entry only
        insertPnl("p1");
        TradeOutcomeEntity intraday = recorderRow("r1");
        intraday.setSellSegment(TradeSegment.INTRADAY);
        mongoTemplate.save(intraday);

        // When
        V2BackfillReport report = backfill.backfill(false, AFTER_EVERY_ROW, "test");

        // Then
        assertThat(report.unplaced()).singleElement()
                .satisfies(unplaced -> assertThat(unplaced.reason()).contains("EQUITY/INTRADAY"));
        MoneyAssert.assertMoney(0.0, entry("p1").getDouble("profit"));
    }

    @Test
    void backfill_whenARowRecordsNoTimeItWasWritten_listsItAndWritesNothing() {
        // Given -- saved with an id, so auditing never stamped it; before or after the deploy is unknown
        insertPnl("p1");
        TradeOutcomeEntity unstamped = recorderRow("r1");
        unstamped.getAuditMetadata().setCreatedAt(null);
        mongoTemplate.save(unstamped);

        // When
        V2BackfillReport report = backfill.backfill(false, AFTER_EVERY_ROW, "test");

        // Then
        assertThat(report.unplaced()).singleElement()
                .satisfies(unplaced -> assertThat(unplaced.reason()).isEqualTo("the row records no time it was written"));
        MoneyAssert.assertMoney(0.0, entry("p1").getDouble("profit"));
    }

    @Test
    void backfill_whenADryRun_reportsAndWritesNothing() {
        // Given
        insertPnl("p1");
        recorderRow("r1");

        // When
        V2BackfillReport report = backfill.backfill(true, AFTER_EVERY_ROW, "test");

        // Then
        assertThat(report.placed()).hasSize(1);
        MoneyAssert.assertMoney(0.0, entry("p1").getDouble("profit"));
    }

    @Test
    void endpoint_whenWritten_addsAndRecordsTheCaller() {
        // Given
        insertPnl("p1");
        recorderRow("r1");

        // When / Then
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "SUPER_USER"))
                .when().post("/profit-and-loss/v2-figures/backfill?dryRun=false&recordedBefore=" + AFTER_EVERY_ROW
                        .withNano(0))
                .then().statusCode(200).body("placed.size()", equalTo(1));
        MoneyAssert.assertMoney(445.0, entry("p1").getDouble("profit"));
        assertThat(mongoTemplate.getCollection("profit_and_loss").find(new Document("_id", "p1")).first()
                .get("audit_metadata", Document.class).getString("updatedBy")).isEqualTo("admin@example.com");
    }

    @Test
    void endpoint_whenNotASuperUser_isForbidden() {
        // Given
        insertPnl("p1");
        recorderRow("r1");

        // When / Then
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "USER"))
                .when().post("/profit-and-loss/v2-figures/backfill?dryRun=false&recordedBefore=" + AFTER_EVERY_ROW
                        .withNano(0))
                .then().statusCode(403);
        MoneyAssert.assertMoney(0.0, entry("p1").getDouble("profit"));
    }

    @Test
    void saveTradeOutcome_whenTheRecorderPricedNothing_storesAnEmptySellBreakup() {
        // Given -- the recorder's row for a sale the engine declined: an empty sell breakup, not none
        TradeOutcomeContext row = TradeOutcomeContext.builder().email(EMAIL).stockCode("INFY")
                .buyChargeBreakup(Map.of()).sellChargeBreakup(Map.of()).build();

        // When
        tradeOutcomeService.saveTradeOutcome(UserMail.from(EMAIL), row);

        // Then -- the field is there, so it marks the row as the recorder's
        Document stored = mongoTemplate.getCollection("trade_outcomes").find(new Document("email", EMAIL)).first();
        assertThat(stored).containsKey("sell_charge_breakup");
        assertThat(stored.get("sell_charge_breakup", Document.class)).isEmpty();
    }
}
