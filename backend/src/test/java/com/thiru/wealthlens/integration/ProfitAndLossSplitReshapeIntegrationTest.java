package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.thiru.wealthlens.portfolio.dto.SplitReshapeReport;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.migration.ProfitAndLossSplitReshape;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.testsupport.MoneyAssert;
import java.time.LocalDate;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The stored short / long pair moved into the P&L's split, over a real database (W-160d3, D-369). */
class ProfitAndLossSplitReshapeIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "reshape@example.com";
    private static final String YEAR = "2024-2025";

    @Autowired
    private ProfitAndLossSplitReshape reshape;

    /** A report with the five figures at the year, and the same figures in December's first half. */
    private static Document report(double purchase, double sell, double profit, double brokerage, double misc) {
        Document half = figures(purchase, sell, profit, brokerage, misc);
        Document december = figures(purchase, sell, profit, brokerage, misc).append("month", "DECEMBER")
                .append("first_half_report", half);
        return figures(purchase, sell, profit, brokerage, misc)
                .append("capital_gains", new Document("DECEMBER", december));
    }

    private static Document figures(double purchase, double sell, double profit, double brokerage, double misc) {
        return new Document("purchase_amount", purchase).append("sell_amount", sell).append("profit", profit)
                .append("brokerage", brokerage).append("misc_charges", misc);
    }

    private void insertPnl(String id, Document realised, Document outsourced) {
        Document pnl = new Document("_id", id).append("email", EMAIL).append("financial_year", YEAR);
        if (realised != null) {
            pnl.append("realised_profits", realised);
        }
        if (outsourced != null) {
            pnl.append("out_sourced_realised_profits", outsourced);
        }
        mongoTemplate.getCollection("profit_and_loss").insertOne(pnl);
    }

    /** Short 1,000 / 1,500 / 450 / 40 / 10 and long 600 / 900 / 280 / 15 / 5, with the map V2 wrote beside them. */
    private static Document pairAndMap() {
        return new Document("short_term_capital_gains", report(1000, 1500, 450, 40, 10))
                .append("long_term_capital_gains", report(600, 900, 280, 15, 5))
                .append("gains_by_classification", new Document("SHORT_TERM", report(1000, 1500, 450, 40, 10)));
    }

    private void outcome(String id, AssetType assetType, TradeSegment segment, AccountType account) {
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setId(id);
        row.setEmail(EMAIL);
        row.setAssetType(assetType);
        row.setSellSegment(segment);
        row.setAccountType(account);
        row.setSellDate(LocalDate.of(2024, 12, 10));
        mongoTemplate.save(row);
    }

    private Document side(String id, String side) {
        return mongoTemplate.getCollection("profit_and_loss").find(new Document("_id", id)).first()
                .get(side, Document.class);
    }

    private static Document split(Document side, AssetType assetType, TradeSegment segment) {
        return side.get("gains_by_asset_and_segment", Document.class).get(assetType.name(), Document.class)
                .get(segment.name(), Document.class);
    }

    private static boolean carriesOldFields(Document side) {
        return side.containsKey("short_term_capital_gains") || side.containsKey("long_term_capital_gains")
                || side.containsKey("gains_by_classification");
    }

    @Test
    void reshape_whenTheYearHasOneAssetAndSegment_movesThePairIntoTheSplit() {
        // Given
        insertPnl("p1", pairAndMap(), null);
        outcome("o1", AssetType.EQUITY, TradeSegment.DELIVERY, AccountType.SELF);

        // When
        SplitReshapeReport report = reshape.reshape(false, "test");

        // Then -- 1,000 + 600 = 1,600; 1,500 + 900 = 2,400; 450 + 280 = 730; 40 + 15 = 55; 10 + 5 = 15, at every level
        Document realised = side("p1", "realised_profits");
        Document year = split(realised, AssetType.EQUITY, TradeSegment.DELIVERY);
        Document december = year.get("capital_gains", Document.class).get("DECEMBER", Document.class);
        Document half = december.get("first_half_report", Document.class);
        for (Document level : new Document[] {year, december, half}) {
            MoneyAssert.assertMoney(1600.0, level.getDouble("purchase_amount"));
            MoneyAssert.assertMoney(2400.0, level.getDouble("sell_amount"));
            MoneyAssert.assertMoney(730.0, level.getDouble("profit"));
            MoneyAssert.assertMoney(55.0, level.getDouble("brokerage"));
            MoneyAssert.assertMoney(15.0, level.getDouble("misc_charges"));
        }
        assertThat(carriesOldFields(realised)).isFalse();
        assertThat(report.placed()).hasSize(1);
        assertThat(report.unplaced()).isEmpty();
    }

    @Test
    void reshape_whenTheSplitAlreadyHasSales_addsThePairToThem() {
        // Given -- a sale since the deploy: 200 bought, 300 sold
        Document realised = pairAndMap().append("gains_by_asset_and_segment", new Document("EQUITY",
                new Document("DELIVERY", report(200, 300, 0, 0, 0))));
        insertPnl("p1", realised, null);
        outcome("o1", AssetType.EQUITY, TradeSegment.DELIVERY, AccountType.SELF);

        // When
        reshape.reshape(false, "test");

        // Then -- 1,600 + 200 = 1,800; 2,400 + 300 = 2,700
        Document year = split(side("p1", "realised_profits"), AssetType.EQUITY, TradeSegment.DELIVERY);
        MoneyAssert.assertMoney(1800.0, year.getDouble("purchase_amount"));
        MoneyAssert.assertMoney(2700.0, year.getDouble("sell_amount"));
    }

    @Test
    void reshape_whenBothSidesCarryThePair_placesEachByItsOwnOutcomes() {
        // Given
        insertPnl("p1", pairAndMap(), pairAndMap());
        outcome("self", AssetType.EQUITY, TradeSegment.DELIVERY, AccountType.SELF);
        outcome("outsourced", AssetType.MUTUAL_FUND, TradeSegment.DELIVERY, AccountType.OUTSOURCED);

        // When
        reshape.reshape(false, "test");

        // Then
        MoneyAssert.assertMoney(1600.0, split(side("p1", "realised_profits"), AssetType.EQUITY,
                TradeSegment.DELIVERY).getDouble("purchase_amount"));
        MoneyAssert.assertMoney(1600.0, split(side("p1", "out_sourced_realised_profits"), AssetType.MUTUAL_FUND,
                TradeSegment.DELIVERY).getDouble("purchase_amount"));
    }

    @Test
    void reshape_whenTheYearMixesSegments_listsTheSideAndLeavesItAsItIs() {
        // Given
        insertPnl("p1", pairAndMap(), null);
        outcome("delivery", AssetType.EQUITY, TradeSegment.DELIVERY, AccountType.SELF);
        outcome("intraday", AssetType.EQUITY, TradeSegment.INTRADAY, AccountType.SELF);

        // When
        SplitReshapeReport report = reshape.reshape(false, "test");

        // Then
        assertThat(report.placed()).isEmpty();
        assertThat(report.unplaced()).singleElement()
                .satisfies(side -> assertThat(side.reason()).contains("EQUITY/DELIVERY", "EQUITY/INTRADAY"));
        assertThat(carriesOldFields(side("p1", "realised_profits"))).isTrue();
    }

    @Test
    void reshape_whenTheYearHasNoOutcomes_listsTheSideAndLeavesItAsItIs() {
        // Given
        insertPnl("p1", pairAndMap(), null);

        // When
        SplitReshapeReport report = reshape.reshape(false, "test");

        // Then
        assertThat(report.unplaced()).singleElement()
                .satisfies(side -> assertThat(side.reason()).isEqualTo("no trade outcomes in the year"));
        assertThat(carriesOldFields(side("p1", "realised_profits"))).isTrue();
    }

    @Test
    void reshape_whenADryRun_reportsAndWritesNothing() {
        // Given
        insertPnl("p1", pairAndMap(), null);
        outcome("o1", AssetType.EQUITY, TradeSegment.DELIVERY, AccountType.SELF);

        // When
        SplitReshapeReport report = reshape.reshape(true, "test");

        // Then
        assertThat(report.placed()).singleElement().satisfies(side -> {
            assertThat(side.assetType()).isEqualTo(AssetType.EQUITY);
            assertThat(side.segment()).isEqualTo(TradeSegment.DELIVERY);
        });
        assertThat(carriesOldFields(side("p1", "realised_profits"))).isTrue();
        assertThat(side("p1", "realised_profits").containsKey("gains_by_asset_and_segment")).isFalse();
    }

    @Test
    void reshape_whenRunAgain_changesNothing() {
        // Given
        insertPnl("p1", pairAndMap(), null);
        outcome("o1", AssetType.EQUITY, TradeSegment.DELIVERY, AccountType.SELF);
        reshape.reshape(false, "first");

        // When
        SplitReshapeReport second = reshape.reshape(false, "second");

        // Then -- nothing matched, nothing added twice, no stamp moved
        assertThat(second.placed()).isEmpty();
        assertThat(second.unplaced()).isEmpty();
        MoneyAssert.assertMoney(1600.0, split(side("p1", "realised_profits"), AssetType.EQUITY,
                TradeSegment.DELIVERY).getDouble("purchase_amount"));
        assertThat(mongoTemplate.getCollection("profit_and_loss").find(new Document("_id", "p1")).first()
                .get("audit_metadata", Document.class).getString("updatedBy")).isEqualTo("first");
    }

    @Test
    void reshape_whenOneDocumentWasPlacedBefore_placesOnlyTheOther() {
        // Given -- a run that stopped after the first document
        insertPnl("p1", pairAndMap(), null);
        outcome("o1", AssetType.EQUITY, TradeSegment.DELIVERY, AccountType.SELF);
        reshape.reshape(false, "first");
        Document other = new Document("_id", "p2").append("email", EMAIL).append("financial_year", "2023-2024")
                .append("realised_profits", pairAndMap());
        mongoTemplate.getCollection("profit_and_loss").insertOne(other);
        TradeOutcomeEntity earlier = new TradeOutcomeEntity();
        earlier.setId("o2");
        earlier.setEmail(EMAIL);
        earlier.setAssetType(AssetType.EQUITY);
        earlier.setSellSegment(TradeSegment.DELIVERY);
        earlier.setAccountType(AccountType.SELF);
        earlier.setSellDate(LocalDate.of(2023, 12, 10));
        mongoTemplate.save(earlier);

        // When
        SplitReshapeReport report = reshape.reshape(false, "second");

        // Then
        assertThat(report.placed()).singleElement()
                .satisfies(side -> assertThat(side.financialYear()).isEqualTo("2023-2024"));
        MoneyAssert.assertMoney(1600.0, split(side("p1", "realised_profits"), AssetType.EQUITY,
                TradeSegment.DELIVERY).getDouble("purchase_amount"));
    }

    @Test
    void endpoint_whenWritten_movesAndRecordsTheCaller() {
        // Given
        insertPnl("p1", pairAndMap(), null);
        outcome("o1", AssetType.EQUITY, TradeSegment.DELIVERY, AccountType.SELF);

        // When / Then
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "SUPER_USER"))
                .when().post("/profit-and-loss/split/reshape?dryRun=false")
                .then().statusCode(200).body("dryRun", equalTo(false)).body("placed.size()", equalTo(1));
        assertThat(carriesOldFields(side("p1", "realised_profits"))).isFalse();
        assertThat(mongoTemplate.getCollection("profit_and_loss").find(new Document("_id", "p1")).first()
                .get("audit_metadata", Document.class).getString("updatedBy")).isEqualTo("admin@example.com");
    }

    @Test
    void endpoint_whenNotASuperUser_isForbidden() {
        // Given
        insertPnl("p1", pairAndMap(), null);

        // When / Then
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "USER"))
                .when().post("/profit-and-loss/split/reshape?dryRun=false").then().statusCode(403);
        assertThat(carriesOldFields(side("p1", "realised_profits"))).isTrue();
    }
}
