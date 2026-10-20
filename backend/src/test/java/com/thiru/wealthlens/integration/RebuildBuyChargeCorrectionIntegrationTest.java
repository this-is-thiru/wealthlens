package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.thiru.wealthlens.portfolio.dto.RebuildChargeCorrectionReport;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.holding.HoldingPeriodSeeder;
import com.thiru.wealthlens.portfolio.migration.RebuildBuyChargeCorrection;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.testsupport.MoneyAssert;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Rows the old rebuild stored for a lot sold 5 then 5, corrected over a real database; a V1 row beside them is left as
 * it is (W-088u7, D-276).
 */
class RebuildBuyChargeCorrectionIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "rebuild@example.com";
    private static final String LOT = "lot-RELIANCE-2024-01-15";

    @Autowired
    private TradeOutcomeRepository tradeOutcomeRepository;

    @Autowired
    private RebuildBuyChargeCorrection correction;

    @Autowired
    private HoldingPeriodSeeder holdingPeriodSeeder;

    /**
     * One buy of 10 @ 100 (broker 10, misc 4); sells of 5 @ 150 on 3 February and 3 March 2025, no sell charges. The old
     * rebuild stored the first sell's buy charges as 10 ÷ 10 × 5 = 5 and 4 ÷ 10 × 5 = 2, and the second's as
     * 10 ÷ 5 × 5 = 10 and 4 ÷ 5 × 5 = 4; plus a V1 row of another sell, which the correction must not read.
     */
    @BeforeEach
    void seed() {
        holdingPeriodSeeder.seed("test");
        mongoTemplate.save(transaction("buy-1", TransactionType.BUY, 100.0, 10.0, 10.0, 4.0, LocalDate.of(2024, 1, 15)));
        mongoTemplate.save(transaction("sell-1", TransactionType.SELL, 150.0, 5.0, 0.0, 0.0, LocalDate.of(2025, 2, 3)));
        mongoTemplate.save(transaction("sell-2", TransactionType.SELL, 150.0, 5.0, 0.0, 0.0, LocalDate.of(2025, 3, 3)));
        tradeOutcomeRepository.saveAll(List.of(stored("first", "sell-1", LOT, 5.0, 2.0),
                stored("second", "sell-2", LOT, 10.0, 4.0), stored("v1", "sell-9", "asset-64f0c0ffee", 10.0, 4.0)));
    }

    @Test
    void correct_whenADryRun_reportsTheSecondSellAndWritesNothing() {
        // When
        RebuildChargeCorrectionReport report = correction.correct(true, "admin@example.com");

        // Then — both lot rows examined; the second changes; charges 5 + 2 + 10 + 4 = 21 now, 5 + 2 + 5 + 2 = 14
        assertThat(report.dryRun()).isTrue();
        assertThat(report.rowsExamined()).isEqualTo(2);
        assertThat(report.rowsCorrected()).isEqualTo(1);
        MoneyAssert.assertCanonical("stored", 21.00, report.buyChargesBefore());
        MoneyAssert.assertCanonical("as allocated", 14.00, report.buyChargesAfter());
        assertThat(report.skipped()).isEmpty();
        MoneyAssert.assertMoney(10.0, row("second").getBuyBrokerCharges());
    }

    @Test
    void correct_whenWritten_correctsTheSecondSellAloneAndRecordsWhoAsked() {
        // Given — the audit the untouched rows carry before the run
        String firstAudit = row("first").getAuditMetadata().getUpdatedBy();
        String v1Audit = row("v1").getAuditMetadata().getUpdatedBy();

        // When
        correction.correct(false, "admin@example.com");

        // Then — the second sell's buy charges as allocated: (10 − 5) × 5 ÷ 5 = 5, (4 − 2) × 5 ÷ 5 = 2
        TradeOutcomeEntity second = row("second");
        MoneyAssert.assertMoney(5.0, second.getBuyBrokerCharges());
        MoneyAssert.assertMoney(2.0, second.getBuyMiscCharges());
        MoneyAssert.assertMoney(507.0, second.getTotalBuyValue());          // 100 × 5 + 5 + 2
        MoneyAssert.assertMoney(243.0, second.getNetProfit());              // 150 × 5 − 507
        assertThat(second.getProfitPercentage()).isEqualTo(243.0 / 507.0 * 100);
        assertThat(second.getAuditMetadata().getUpdatedBy()).isEqualTo("admin@example.com");
        assertThat(row("first").getAuditMetadata().getUpdatedBy()).isEqualTo(firstAudit).isNotEqualTo("admin@example.com");
        MoneyAssert.assertMoney(10.0, row("v1").getBuyBrokerCharges());
        assertThat(row("v1").getAuditMetadata().getUpdatedBy()).isEqualTo(v1Audit).isNotEqualTo("admin@example.com");
    }

    @Test
    void correct_whenRunAgain_changesNothing() {
        // Given
        correction.correct(false, "admin@example.com");

        // When
        RebuildChargeCorrectionReport second = correction.correct(false, "admin@example.com");

        // Then
        assertThat(second.rowsCorrected()).isZero();
        MoneyAssert.assertCanonical("after the correction", 14.00, second.buyChargesBefore());
    }

    @Test
    void correct_whenASellWasDeletedSinceTheRebuild_skipsTheUserAndWritesNothing() {
        // Given — the first sell is gone, so the rebuild now gives the second sell the lot's first five
        mongoTemplate.remove(mongoTemplate.findById("sell-1", TransactionEntity.class));

        // When
        RebuildChargeCorrectionReport report = correction.correct(false, "admin@example.com");

        // Then
        assertThat(report.skipped()).singleElement().satisfies(skipped -> assertThat(skipped.email()).isEqualTo(EMAIL));
        assertThat(report.rowsCorrected()).isZero();
        MoneyAssert.assertMoney(10.0, row("second").getBuyBrokerCharges());
    }

    @Test
    void endpoint_whenCalledWithoutDryRun_isADryRun() {
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "SUPER_USER"))
                .when().post("/trade-outcomes/rebuild-charges/correct")
                .then().statusCode(200).body("dryRun", equalTo(true)).body("rowsCorrected", equalTo(1));
        MoneyAssert.assertMoney(10.0, row("second").getBuyBrokerCharges());
    }

    @Test
    void endpoint_whenNotASuperUser_isForbidden() {
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "USER"))
                .when().post("/trade-outcomes/rebuild-charges/correct?dryRun=false")
                .then().statusCode(403);
        MoneyAssert.assertMoney(10.0, row("second").getBuyBrokerCharges());
    }

    private TradeOutcomeEntity row(String id) {
        return tradeOutcomeRepository.findById(id).orElseThrow();
    }

    private static TransactionEntity transaction(String id, TransactionType type, double price, double quantity,
            double brokerCharges, double miscCharges, LocalDate date) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setEmail(EMAIL);
        transaction.setTransactionType(type);
        transaction.setStockCode("RELIANCE");
        transaction.setStockName("Reliance Industries");
        transaction.setExchangeName("NSE");
        transaction.setBrokerName(BrokerName.ZERODHA);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setAccountType(AccountType.SELF);
        transaction.setAccountHolder("main");
        transaction.setPrice(price);
        transaction.setQuantity(quantity);
        transaction.setBrokerCharges(brokerCharges);
        transaction.setMiscCharges(miscCharges);
        transaction.setTransactionDate(date);
        transaction.setCorporateActions(new ArrayList<>());
        return transaction;
    }

    /** A row as the old rebuild stored it: 5 of the lot @ 100 sold @ 150, with the buy charges given. */
    private static TradeOutcomeEntity stored(String id, String sell, String lot, double buyBroker, double buyMisc) {
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setId(id);
        row.setEmail(EMAIL);
        row.setStockCode("RELIANCE");
        row.setBrokerName(BrokerName.ZERODHA);
        row.setAccountHolder("main");
        row.setSourceSellTransactionId(sell);
        row.setSourceBuyLotId(lot);
        row.setBuyQuantity(5.0);
        row.setSellQuantity(5.0);
        row.setCorporateActionAdjustedBuyPrice(100.0);
        row.setSellPrice(150.0);
        row.setBuyBrokerCharges(buyBroker);
        row.setBuyMiscCharges(buyMisc);
        double totalBuy = 100.0 * 5 + buyBroker + buyMisc;
        row.setTotalBuyValue(totalBuy);
        row.setTotalSellValue(750.0);
        row.setNetProfit(750.0 - totalBuy);
        row.setProfitPercentage((750.0 - totalBuy) / totalBuy * 100);
        return row;
    }
}
