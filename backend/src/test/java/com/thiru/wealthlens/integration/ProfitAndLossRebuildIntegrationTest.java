package com.thiru.wealthlens.integration;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import com.thiru.wealthlens.brokercharges.service.ChargeSeederService;
import com.thiru.wealthlens.portfolio.dto.ProfitAndLossRebuildReport;
import com.thiru.wealthlens.portfolio.dto.ProfitAndLossRebuildReport.YearComparison;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.model.FinancialReport;
import com.thiru.wealthlens.portfolio.entity.model.RealisedProfits;
import com.thiru.wealthlens.portfolio.repository.ProfitAndLossRepository;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The {@code profit_and_loss} rebuild over a real database, from trades made through the API (W-283, D-575): a V2
 * user's documents come back as the trades wrote them, a V1 user's recorded charges are folded where V1 never folded
 * them (C2a), and the operation is a {@code SUPER_USER}'s alone. Expected figures are worked from each trade by hand.
 */
class ProfitAndLossRebuildIntegrationTest extends AbstractIntegrationTest {

    private static final String V2_USER = "rebuild-v2@example.com";
    private static final String V1_USER = "rebuild-v1@example.com";
    private static final String ADMIN = "rebuild-admin@example.com";
    private static final String YEAR = "2025-2026";
    private static final double PAISA = 0.005;

    @Autowired
    private ProfitAndLossRepository profitAndLossRepository;

    @Autowired
    private UserChargeRepository userChargeRepository;

    @Autowired
    private ChargeSeederService chargeSeederService;

    @Autowired
    private TradeOutcomeRepository tradeOutcomeRepository;

    @BeforeEach
    void seedTheShippedCards() {
        chargeSeederService.seed("profit-and-loss-rebuild-it");
    }

    @Test
    void rebuild_whenV2TradesOnly_reportsWhatTheTradesStored() {
        // Given -- buy 10 @ 100 (5 Jan 2026), buy 5 @ 120 (5 Feb), sell 12 @ 150 (5 Mar), all V2
        tradeV2Scenario();

        // When
        ProfitAndLossRebuildReport report = rebuild(V2_USER, "", "SUPER_USER").statusCode(200)
                .extract().as(ProfitAndLossRebuildReport.class);

        // Then -- one sale row per lot (10 from the first, 2 from the second); two buys and a sale priced
        YearComparison year = onlyYear(report);
        assertThat(report.dryRun()).isTrue();
        assertThat(report.salesFolded()).isEqualTo(2);
        assertThat(report.chargesFolded()).isEqualTo(3);
        // sold 12 × 150 = 1,800; bought 10 × 100 + 2 × 120 = 1,240
        assertMoney(1800.0, year.rebuilt().sellAmountRupees());
        assertMoney(1240.0, year.rebuilt().purchaseAmountRupees());
        assertThat(year.hasStoredDocument()).isTrue();
        assertTotalsEqual(year.stored(), year.rebuilt());
    }

    @Test
    void rebuild_whenTheDocumentWasLost_restoresItAsTheTradesWroteIt() {
        // Given -- the V2 trades, their document kept, then deleted
        tradeV2Scenario();
        ProfitAndLossEntity written = profitAndLossRepository.findByEmailAndFinancialYear(V2_USER, YEAR).orElseThrow();
        profitAndLossRepository.deleteByEmail(V2_USER);

        // When -- a dry run, then a real one
        ProfitAndLossRebuildReport dryRun = rebuild(V2_USER, "", "SUPER_USER").statusCode(200)
                .extract().as(ProfitAndLossRebuildReport.class);
        boolean nothingWritten = profitAndLossRepository.findAllByEmail(V2_USER).isEmpty();
        rebuild(V2_USER, "?dryRun=false", "SUPER_USER").statusCode(200);

        // Then -- the dry run saw no stored year and wrote nothing; the real run wrote the trades' document back
        assertThat(onlyYear(dryRun).hasStoredDocument()).isFalse();
        assertThat(nothingWritten).isTrue();
        ProfitAndLossEntity rebuilt = profitAndLossRepository.findByEmailAndFinancialYear(V2_USER, YEAR).orElseThrow();
        assertSameFigures(written.getRealisedProfits(), rebuilt.getRealisedProfits());
        assertThat(rebuilt.getOutSourcedRealisedProfits()).isNull();
    }

    @Test
    void rebuild_whenRunTwice_writesTheSameDocument() {
        // Given
        tradeV2Scenario();
        rebuild(V2_USER, "?dryRun=false", "SUPER_USER").statusCode(200);
        ProfitAndLossEntity first = profitAndLossRepository.findByEmailAndFinancialYear(V2_USER, YEAR).orElseThrow();

        // When
        rebuild(V2_USER, "?dryRun=false", "SUPER_USER").statusCode(200);

        // Then -- one document, the same figures
        List<ProfitAndLossEntity> documents = profitAndLossRepository.findAllByEmail(V2_USER);
        assertThat(documents).hasSize(1);
        assertSameFigures(first.getRealisedProfits(), documents.getFirst().getRealisedProfits());
    }

    @Test
    void rebuild_whenTheV2BackfillRunsAfterIt_addsNoSaleAgain() {
        // Given -- the V2 trades, their sale rows dated before the deploy as the backfill needs, then rebuilt
        tradeV2Scenario();
        List<TradeOutcomeEntity> rows = tradeOutcomeRepository.findByEmail(V2_USER);
        rows.forEach(row -> row.getAuditMetadata().setCreatedAt(LocalDateTime.now().minusHours(1)));
        tradeOutcomeRepository.saveAll(rows);
        rebuild(V2_USER, "?dryRun=false", "SUPER_USER").statusCode(200);
        ProfitAndLossEntity rebuilt = profitAndLossRepository.findByEmailAndFinancialYear(V2_USER, YEAR).orElseThrow();

        // When -- the one-time backfill (§2.8) for every row written before tomorrow
        given().header("Authorization", "Bearer " + generateToken(ADMIN, "SUPER_USER"))
                .post("/profit-and-loss/v2-figures/backfill?dryRun=false&recordedBefore="
                        + LocalDateTime.now().plusDays(1).withNano(0))
                .then().statusCode(200).body("placed", hasSize(0)).body("alreadyCounted", equalTo(2));

        // Then -- both sale rows were already in the rebuilt figures (C3a), so nothing moved
        ProfitAndLossEntity after = profitAndLossRepository.findByEmailAndFinancialYear(V2_USER, YEAR).orElseThrow();
        assertSameFigures(rebuilt.getRealisedProfits(), after.getRealisedProfits());
    }

    @Test
    void rebuild_whenALiveTradeSavesTheYearAfterIt_keepsTheBackfillsList() {
        // Given -- the V2 trades, rebuilt, so the year's document lists both sale rows
        tradeV2Scenario();
        rebuild(V2_USER, "?dryRun=false", "SUPER_USER").statusCode(200);
        List<String> listed = profitAndLossRepository.findByEmailAndFinancialYear(V2_USER, YEAR).orElseThrow()
                .getBackfilledOutcomeIds();

        // When -- a later buy in the same year saves the document
        post(V2_USER, "/transaction/v2", trade(TransactionType.BUY, 1.0, 100.0, LocalDate.of(2026, 3, 10)));

        // Then -- a save replaces the whole document; the list survives it
        assertThat(listed).hasSize(2);
        assertThat(profitAndLossRepository.findByEmailAndFinancialYear(V2_USER, YEAR).orElseThrow()
                .getBackfilledOutcomeIds()).containsExactlyInAnyOrderElementsOf(listed);
    }

    @Test
    void rebuild_whenV1Trades_foldsTheirRecordedChargesWhichV1NeverFolded() {
        // Given -- buy 10 @ 100 (5 Jan 2026), sell 4 @ 150 (5 Mar), both V1
        post(V1_USER, "/transaction", trade(TransactionType.BUY, 10.0, 100.0, LocalDate.of(2026, 1, 5)));
        post(V1_USER, "/transaction", trade(TransactionType.SELL, 4.0, 150.0, LocalDate.of(2026, 3, 5)));
        double recordedTradeCharges = userChargeRepository.findByEmailOrderByTransactionDateDesc(V1_USER).stream()
                .filter(row -> row.getEvent() == ChargeEvent.BUY || row.getEvent() == ChargeEvent.SELL)
                .mapToDouble(UserChargeEntity::getTotalCharges)
                .sum();

        // When
        ProfitAndLossRebuildReport report = rebuild(V1_USER, "", "SUPER_USER").statusCode(200)
                .extract().as(ProfitAndLossRebuildReport.class);

        // Then -- sold 4 × 150 = 600 from a lot bought at 4 × 100 = 400, stored and rebuilt alike (C2a)
        YearComparison year = onlyYear(report);
        assertMoney(600.0, year.stored().sellAmountRupees());
        assertMoney(600.0, year.rebuilt().sellAmountRupees());
        assertMoney(400.0, year.stored().purchaseAmountRupees());
        assertMoney(400.0, year.rebuilt().purchaseAmountRupees());
        // V1 writes no charge summary; the rebuild folds the charges V1 recorded
        assertThat(recordedTradeCharges).isGreaterThan(0.0);
        assertMoney(0.0, year.stored().chargesRupees());
        assertMoney(recordedTradeCharges, year.rebuilt().chargesRupees());
    }

    @Test
    void rebuild_whenAV1SaleWasIntraday_filesItUnderIntraday() {
        // Given -- a V1 intraday buy of 10 @ 100 and its intraday sale @ 110, both 5 Jan 2026 (W-295, D-592)
        Map<String, Object> buy = trade(TransactionType.BUY, 10.0, 100.0, LocalDate.of(2026, 1, 5));
        buy.put("segment", TradeSegment.INTRADAY.name());
        post(V1_USER, "/transaction", buy);
        Map<String, Object> sell = trade(TransactionType.SELL, 10.0, 110.0, LocalDate.of(2026, 1, 5));
        sell.put("segment", TradeSegment.INTRADAY.name());
        post(V1_USER, "/transaction", sell);

        // When -- the year rebuilt from the stored rows and written
        rebuild(V1_USER, "?dryRun=false", "SUPER_USER").statusCode(200);

        // Then -- the sale (10 × 110 = ₹1,100) is filed under intraday, and nothing under delivery
        Map<TradeSegment, FinancialReport> equity = profitAndLossRepository.findByEmailAndFinancialYear(V1_USER, YEAR)
                .orElseThrow().getRealisedProfits().getGainsByAssetAndSegment().get(AssetType.EQUITY);
        assertThat(equity).containsOnlyKeys(TradeSegment.INTRADAY);
        assertMoney(1100.0, equity.get(TradeSegment.INTRADAY).getSellAmount());
    }

    @Test
    void rebuild_whenAChargeRowNamesNoStoredTransaction_isRefusedNamingIt() {
        // Given -- a buy's charge row whose transaction was deleted by hand
        UserChargeEntity orphan = new UserChargeEntity();
        orphan.setEmail(V2_USER);
        orphan.setTransactionId("deleted-txn");
        orphan.setEvent(ChargeEvent.BUY);
        orphan.setTransactionDate(LocalDate.of(2026, 1, 5));
        orphan.setAmountByCode(new HashMap<>(Map.of("BROKERAGE", 20.0)));
        userChargeRepository.save(orphan);

        // When / Then
        rebuild(V2_USER, "?dryRun=false", "SUPER_USER").statusCode(400).body(containsString("deleted-txn"));
        assertThat(profitAndLossRepository.findAllByEmail(V2_USER)).isEmpty();
    }

    @Test
    void rebuild_whenAUserAsksForTheirOwn_isForbidden() {
        // When / Then -- the role, not the path, refuses: the user owns the path but is no SUPER_USER
        given().header("Authorization", "Bearer " + generateToken(V2_USER, "USER"))
                .post("/portfolio/user/" + V2_USER + "/profit-and-loss/rebuild?dryRun=false").then().statusCode(403);
    }

    private void tradeV2Scenario() {
        post(V2_USER, "/transaction/v2", trade(TransactionType.BUY, 10.0, 100.0, LocalDate.of(2026, 1, 5)));
        post(V2_USER, "/transaction/v2", trade(TransactionType.BUY, 5.0, 120.0, LocalDate.of(2026, 2, 5)));
        post(V2_USER, "/transaction/v2", trade(TransactionType.SELL, 12.0, 150.0, LocalDate.of(2026, 3, 5)));
    }

    private void post(String email, String path, Map<String, Object> body) {
        given().header("Authorization", "Bearer " + generateToken(email)).contentType(ContentType.JSON).body(body)
                .post("/portfolio/user/" + email + path).then().statusCode(200);
    }

    private ValidatableResponse rebuild(String email, String query, String role) {
        return given().header("Authorization", "Bearer " + generateToken(ADMIN, role))
                .post("/portfolio/user/" + email + "/profit-and-loss/rebuild" + query).then();
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

    private static YearComparison onlyYear(ProfitAndLossRebuildReport report) {
        assertThat(report.years()).hasSize(1);
        YearComparison year = report.years().getFirst();
        assertThat(year.financialYear()).isEqualTo(YEAR);
        return year;
    }

    private static void assertTotalsEqual(ProfitAndLossRebuildReport.YearTotals expected,
                                          ProfitAndLossRebuildReport.YearTotals actual) {
        assertMoney(expected.purchaseAmountRupees(), actual.purchaseAmountRupees());
        assertMoney(expected.sellAmountRupees(), actual.sellAmountRupees());
        assertMoney(expected.profitRupees(), actual.profitRupees());
        assertMoney(expected.brokerageRupees(), actual.brokerageRupees());
        assertMoney(expected.miscChargesRupees(), actual.miscChargesRupees());
        assertMoney(expected.chargesRupees(), actual.chargesRupees());
    }

    /** Every figure at every level, to the paisa; only the time each level was last touched may differ. */
    private static void assertSameFigures(RealisedProfits expected, RealisedProfits actual) {
        assertThat(actual).usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes(".*lastUpdatedTime")
                .withComparatorForType((Double left, Double right) -> Math.abs(left - right) < PAISA
                        ? 0 : Double.compare(left, right), Double.class)
                .isEqualTo(expected);
    }
}
