package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.entity.model.YearlyChargeSummary;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import com.thiru.wealthlens.brokercharges.service.ChargeSeederService;
import com.thiru.wealthlens.portfolio.dto.context.BuyContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitLossContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.repository.ProfitAndLossRepository;
import com.thiru.wealthlens.portfolio.service.ChargeRecordingGateway;
import com.thiru.wealthlens.portfolio.service.ProfitAndLossService;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Phase B end to end: the live trade path hands every trade to the engine and is itself unchanged.
 *
 * <p>A separate class because it is the only one that runs with
 * {@code app.charges.shadow-recording=true}. That is deliberate — every other integration class
 * runs with the shipped default of {@code false}, so the fact that they are all still green is
 * itself the evidence that shadow recording is opt-in.
 *
 * <p>What only this tier can show is that the wiring exists at all. The unit tests stub the gateway
 * and prove {@code ProfitAndLossService} calls it; they cannot prove Spring has an implementation to
 * inject, that {@code brokercharges} may depend on {@code portfolio} to provide one, or that a row
 * actually reaches Mongo with its lines mapped.
 */
@TestPropertySource(properties = "app.charges.shadow-recording=true")
class ShadowRecordingIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "shadow-it@example.com";
    private static final String HOLDER = "self";
    private static final LocalDate TRADE_DATE = LocalDate.of(2025, 6, 10);

    @Autowired
    private ProfitAndLossService profitAndLossService;

    @Autowired
    private ProfitAndLossRepository profitAndLossRepository;

    @Autowired
    private UserChargeRepository userChargeRepository;

    @Autowired
    private ChargeSeederService chargeSeederService;

    @Autowired
    private ChargeRecordingGateway chargeRecordingGateway;

    @BeforeEach
    void seedTheShippedCards() {
        chargeSeederService.seed("shadow-recording-it");
    }

    private static ProfitLossContext buy(String transactionId, AssetType assetType) {
        return new ProfitLossContext(transactionId, 100, TRADE_DATE, 1000.0, "INFY", BrokerName.ZERODHA,
                "NSE", assetType, TransactionType.BUY, null, AccountType.SELF, HOLDER, List.of());
    }

    @Test
    void aBuyThroughTheLiveFlow_isRecordedByTheEngine() {
        // When
        profitAndLossService.updateProfitAndLoss(UserMail.from(EMAIL), buy("txn-shadow-1", AssetType.EQUITY));

        // Then
        Optional<UserChargeEntity> recorded = userChargeRepository.findByEmailAndTransactionId(EMAIL, "txn-shadow-1");
        assertThat(recorded).isPresent();
        assertThat(recorded.get().getEvent()).isEqualTo(ChargeEvent.BUY);
        assertThat(recorded.get().getResolution()).isEqualTo(ChargeResolution.RESOLVED);
        assertThat(recorded.get().getTotalCharges()).isGreaterThan(0.0);
        assertThat(recorded.get().getLines()).isNotEmpty();
    }

    @Test
    void aSellThroughTheLiveFlow_isRecordedWithTheLotsItConsumed() {
        // Given — a disposal drawn from two lots of different ages, as the FIFO walk produces them
        ProfitLossContext context = new ProfitLossContext("txn-shadow-2", 100, TRADE_DATE, 1200.0, "INFY",
                BrokerName.ZERODHA, "NSE", AssetType.EQUITY, TransactionType.SELL, null, AccountType.SELF, HOLDER,
                List.of(new BuyContext(40, LocalDate.of(2025, 1, 5), 900.0),
                        new BuyContext(60, LocalDate.of(2025, 5, 20), 1100.0)));

        // When
        profitAndLossService.updateProfitAndLoss(UserMail.from(EMAIL), context);

        // Then
        Optional<UserChargeEntity> recorded = userChargeRepository.findByEmailAndTransactionId(EMAIL, "txn-shadow-2");
        assertThat(recorded).isPresent();
        assertThat(recorded.get().getEvent()).isEqualTo(ChargeEvent.SELL);
        assertThat(recorded.get().getAmountByCode()).containsKey("STT");
    }

    /**
     * FR-8. The superseded implementation skips a mutual fund; the engine does not. This is the
     * whole reason Phase B produces data worth reconciling beyond equity.
     */
    @Test
    void aNonEquityBuy_reachesTheEngineEvenThoughTheOldPathSkipsIt() {
        // When
        profitAndLossService.updateProfitAndLoss(UserMail.from(EMAIL), buy("txn-shadow-mf", AssetType.MUTUAL_FUND));

        // Then
        Optional<UserChargeEntity> recorded = userChargeRepository.findByEmailAndTransactionId(EMAIL, "txn-shadow-mf");
        assertThat(recorded).isPresent();
        assertThat(recorded.get().getAssetType()).isEqualTo(AssetType.MUTUAL_FUND);
    }

    /**
     * The property the phase rests on. The engine recorded a charge; the profit and loss document
     * the same call saved carries no trace of it.
     */
    @Test
    void recordingACharge_leavesTheProfitAndLossDocumentUntouched() {
        // When
        profitAndLossService.updateProfitAndLoss(UserMail.from(EMAIL), buy("txn-shadow-3", AssetType.EQUITY));

        // Then — the engine did record something, so this is not vacuous
        assertThat(userChargeRepository.findByEmailAndTransactionId(EMAIL, "txn-shadow-3")).isPresent();

        Optional<ProfitAndLossEntity> profitAndLoss =
                profitAndLossRepository.findByEmailAndFinancialYear(EMAIL, "2025-2026");
        assertThat(profitAndLoss).isPresent();
        assertThat(profitAndLoss.get().getRealisedProfits()).isNull();
    }

    /**
     * Chunk 10b part 2, over a real document. The summary is a {@code Map<String, Double>} nested two
     * levels inside {@code ProfitAndLossEntity}, which is precisely the shape a mapping drops without
     * complaining — the write succeeds, the read comes back empty, and only a report notices.
     */
    @Test
    void aV2Buy_writesTheComputedChargesIntoTheYearlyChargeSummary() {
        // When
        profitAndLossService.updateProfitAndLoss(UserMail.from(EMAIL), buy("txn-summary", AssetType.EQUITY),
                userChargeRepository.findByEmailAndTransactionId(EMAIL, "txn-summary").isPresent()
                        ? Optional.empty()
                        : Optional.of(chargeSimulationOf("txn-summary")));

        // Then — read back from Mongo, not from the object that was written
        ProfitAndLossEntity saved = profitAndLossRepository
                .findByEmailAndFinancialYear(EMAIL, "2025-2026").orElseThrow();
        YearlyChargeSummary summary = saved.getRealisedProfits().getYearlyChargeSummary();

        assertThat(summary).isNotNull();
        assertThat(summary.getAmountByCode()).containsKeys("BROKERAGE", "STT");
        assertThat(summary.getTotalCharges()).isGreaterThan(0.0);
        assertThat(summary.getMonthlyReport().get(TRADE_DATE.getMonth())).isNotNull();
        assertThat(summary.getMonthlyReport().get(TRADE_DATE.getMonth()).getFirstHalfCharges())
                .isNotNull();
    }

    /** Prices the trade through the real engine so the codes are the shipped card's, not invented. */
    private ChargeComputation chargeSimulationOf(String transactionId) {
        return chargeRecordingGateway.record(UserMail.from(EMAIL), buy(transactionId, AssetType.EQUITY))
                .orElseThrow(() -> new IllegalStateException("the engine declined to price the fixture"));
    }
}
