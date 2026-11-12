package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.portfolio.dto.SegmentCorrectionReport;
import com.thiru.wealthlens.portfolio.dto.SegmentCorrectionReport.UserSegmentCorrection;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.migration.SegmentCorrection;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The segment stored before W-292 / W-295 restored from what stored data records of it — a trade's charge row, an
 * outcome row's {@code sell_segment} — over a real database (W-297, D-599). Every expected count is the seeded data's.
 */
class SegmentCorrectionIntegrationTest extends AbstractIntegrationTest {

    private static final String USER = "segment@example.com";
    private static final String HOLDER = "Self";
    private static final LocalDate DAY = LocalDate.of(2026, 1, 5);

    @Autowired
    private SegmentCorrection correction;

    @Test
    void correct_whenDryRun_reportsTheIntradayRoundTripAndWritesNothing() {
        // Given -- an intraday buy and sale stored as delivery; their charge rows say intraday
        intradayRoundTripStoredAsDelivery();

        // When
        SegmentCorrectionReport report = correction.correct(true, "admin@example.com");

        // Then -- two transactions and one row to correct, none written
        assertThat(report.transactionsCorrected()).isEqualTo(2);
        assertThat(report.rowsCorrected()).isEqualTo(1);
        assertThat(report.users()).containsExactly(new UserSegmentCorrection(USER, 2, 1));
        assertThat(transaction("sell-1").getSegment()).isEqualTo(TradeSegment.DELIVERY);
        assertThat(row("row-1").getSegment()).isNull();
    }

    @Test
    void correct_whenWritten_restoresTheTransactionsAndTheRowAndASecondRunFindsNothing() {
        // Given
        intradayRoundTripStoredAsDelivery();

        // When -- a real run, then a second
        correction.correct(false, "admin@example.com");
        SegmentCorrectionReport second = correction.correct(false, "admin@example.com");

        // Then -- both transactions and every segment on the row are intraday; nothing is left to correct
        assertThat(transaction("buy-1").getSegment()).isEqualTo(TradeSegment.INTRADAY);
        assertThat(transaction("sell-1").getSegment()).isEqualTo(TradeSegment.INTRADAY);
        TradeOutcomeEntity row = row("row-1");
        assertThat(row.getSegment()).isEqualTo(TradeSegment.INTRADAY);
        assertThat(row.getSellSegment()).isEqualTo(TradeSegment.INTRADAY);
        assertThat(row.getBuySegment()).isEqualTo(TradeSegment.INTRADAY);
        assertThat(second.transactionsCorrected()).isZero();
        assertThat(second.rowsCorrected()).isZero();
        assertThat(second.users()).isEmpty();
    }

    @Test
    void correct_whenAV1RowRecordsAnIntradaySaleButItsTransactionDoesNot_takesTheRowsOwnSellSegment() {
        // Given -- a V1 row written from the request (sell_segment intraday, no segment); no charge rows (W-295 §3.19)
        mongoTemplate.save(trade("buy-1", TransactionType.BUY, TradeSegment.DELIVERY));
        mongoTemplate.save(trade("sell-1", TransactionType.SELL, TradeSegment.DELIVERY));
        mongoTemplate.save(outcome("row-1", null, TradeSegment.INTRADAY, TradeSegment.DELIVERY));

        // When
        SegmentCorrectionReport report = correction.correct(false, "admin@example.com");

        // Then -- the row's segment takes its sell_segment; its intraday sell_segment is not overwritten; the
        // transactions have no fact to correct from and stay as stored
        TradeOutcomeEntity row = row("row-1");
        assertThat(row.getSegment()).isEqualTo(TradeSegment.INTRADAY);
        assertThat(row.getSellSegment()).isEqualTo(TradeSegment.INTRADAY);
        assertThat(transaction("sell-1").getSegment()).isEqualTo(TradeSegment.DELIVERY);
        assertThat(report.transactionsCorrected()).isZero();
        assertThat(report.rowsCorrected()).isEqualTo(1);
    }

    @Test
    void correct_whenATradeHasNoChargeRow_leavesItAsStored() {
        // Given -- a buy with no charge row (a V1 buy before W-280, or one recorded with the engine off)
        mongoTemplate.save(trade("buy-1", TransactionType.BUY, TradeSegment.DELIVERY));

        // When
        SegmentCorrectionReport report = correction.correct(false, "admin@example.com");

        // Then -- unknown stays unknown
        assertThat(transaction("buy-1").getSegment()).isEqualTo(TradeSegment.DELIVERY);
        assertThat(report.transactionsCorrected()).isZero();
        assertThat(report.users()).isEmpty();
    }

    @Test
    void correct_whenARunStoppedAfterTheTransactions_completesTheRowsAndTouchesNothingTwice() {
        // Given -- the state a run leaves when it fails after step 1: transactions corrected, the row not
        intradayRoundTripStoredAsDelivery();
        mongoTemplate.save(trade("buy-1", TransactionType.BUY, TradeSegment.INTRADAY));
        mongoTemplate.save(trade("sell-1", TransactionType.SELL, TradeSegment.INTRADAY));

        // When
        SegmentCorrectionReport report = correction.correct(false, "admin@example.com");

        // Then -- no transaction corrected again; the row completed
        assertThat(report.transactionsCorrected()).isZero();
        assertThat(report.rowsCorrected()).isEqualTo(1);
        assertThat(row("row-1").getSegment()).isEqualTo(TradeSegment.INTRADAY);
        assertThat(row("row-1").getBuySegment()).isEqualTo(TradeSegment.INTRADAY);
    }

    @Test
    void correct_whenTheRowsBuysThatDayDisagree_leavesItsBuySegmentAndListsIt() {
        // Given -- a delivery buy and an intraday buy on one day (merged before W-296), and a delivery sale
        mongoTemplate.save(trade("buy-1", TransactionType.BUY, TradeSegment.DELIVERY));
        mongoTemplate.save(trade("buy-2", TransactionType.BUY, TradeSegment.DELIVERY));
        mongoTemplate.save(charge("buy-2", ChargeEvent.BUY));
        mongoTemplate.save(trade("sell-1", TransactionType.SELL, TradeSegment.DELIVERY));
        mongoTemplate.save(outcome("row-1", TradeSegment.DELIVERY, TradeSegment.DELIVERY, TradeSegment.DELIVERY));

        // When
        SegmentCorrectionReport report = correction.correct(false, "admin@example.com");

        // Then -- buy-2 corrected; the row's buy side cannot be told and is listed, not guessed
        assertThat(transaction("buy-2").getSegment()).isEqualTo(TradeSegment.INTRADAY);
        assertThat(row("row-1").getBuySegment()).isEqualTo(TradeSegment.DELIVERY);
        assertThat(report.rowsWithMixedBuys()).containsExactly("row-1");
        assertThat(report.rowsCorrected()).isZero();
    }

    @Test
    void correct_whenAskedOverHttp_isForSuperUsersOnly() {
        given().header("Authorization", "Bearer " + generateToken(USER, "USER"))
                .post("/transactions/segment/correct").then().statusCode(403);
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "SUPER_USER"))
                .post("/transactions/segment/correct").then().statusCode(200);
    }

    private void intradayRoundTripStoredAsDelivery() {
        mongoTemplate.save(trade("buy-1", TransactionType.BUY, TradeSegment.DELIVERY));
        mongoTemplate.save(trade("sell-1", TransactionType.SELL, TradeSegment.DELIVERY));
        mongoTemplate.save(charge("buy-1", ChargeEvent.BUY));
        mongoTemplate.save(charge("sell-1", ChargeEvent.SELL));
        mongoTemplate.save(outcome("row-1", null, TradeSegment.DELIVERY, TradeSegment.DELIVERY));
    }

    private static TransactionEntity trade(String id, TransactionType type, TradeSegment segment) {
        TransactionEntity trade = new TransactionEntity();
        trade.setId(id);
        trade.setEmail(USER);
        trade.setStockCode("INFY");
        trade.setBrokerName(BrokerName.ZERODHA);
        trade.setAccountHolder(HOLDER);
        trade.setTransactionType(type);
        trade.setTransactionDate(DAY);
        trade.setQuantity(10.0);
        trade.setPrice(100.0);
        trade.setSegment(segment);
        return trade;
    }

    /** A charge row priced under the request's segment, which was intraday. */
    private static UserChargeEntity charge(String transactionId, ChargeEvent event) {
        UserChargeEntity charge = new UserChargeEntity();
        charge.setEmail(USER);
        charge.setTransactionId(transactionId);
        charge.setEvent(event);
        charge.setSegment(TradeSegment.INTRADAY);
        charge.setTransactionDate(DAY);
        return charge;
    }

    private static TradeOutcomeEntity outcome(String id, TradeSegment segment, TradeSegment sellSegment,
                                              TradeSegment buySegment) {
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setId(id);
        row.setEmail(USER);
        row.setStockCode("INFY");
        row.setBrokerName(BrokerName.ZERODHA);
        row.setAccountHolder(HOLDER);
        row.setBuyDate(DAY);
        row.setSellDate(DAY);
        row.setSegment(segment);
        row.setSellSegment(sellSegment);
        row.setBuySegment(buySegment);
        row.setSourceSellTransactionId("sell-1");
        return row;
    }

    private TransactionEntity transaction(String id) {
        return mongoTemplate.findById(id, TransactionEntity.class);
    }

    private TradeOutcomeEntity row(String id) {
        return mongoTemplate.findById(id, TradeOutcomeEntity.class);
    }
}
