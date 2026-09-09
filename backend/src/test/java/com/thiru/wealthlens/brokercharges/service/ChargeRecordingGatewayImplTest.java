package com.thiru.wealthlens.brokercharges.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.config.ChargeEngineProperties;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.context.BuyContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitLossContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.testsupport.LogCapture;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Phase B's adapter: the trade path hands a trade over, and the engine prices and records it
 * without the trade path caring what came back.
 *
 * <p>Two properties matter more than the mapping and are asserted first. Shadow recording is off
 * unless configured on, because the flag is what makes Phase B reversible. And nothing this class
 * does may escape into the caller — a shadow record exists to be compared later, and a trade that
 * fails to save because its shadow copy could not be priced would be a strictly worse outcome than
 * having no shadow copy at all.
 */
@ExtendWith(MockitoExtension.class)
class ChargeRecordingGatewayImplTest {

    private static final String EMAIL = "shadow@wealthlens.test";
    private static final String STOCK_CODE = "RELIANCE";
    private static final String ACCOUNT_HOLDER = "self";
    private static final String TRANSACTION_ID = "txn-42";
    private static final LocalDate TRADE_DATE = LocalDate.of(2025, 6, 10);

    @Mock
    private UserChargeService userChargeService;

    private ChargeRecordingGatewayImpl gateway(boolean shadowRecording) {
        return new ChargeRecordingGatewayImpl(
                new ChargeEngineProperties(true, shadowRecording, false), userChargeService);
    }

    private static UserMail userMail() {
        return UserMail.from(EMAIL);
    }

    private static ProfitLossContext buy(AssetType assetType) {
        return new ProfitLossContext(TRANSACTION_ID, 10, TRADE_DATE, 100.0, STOCK_CODE, BrokerName.ZERODHA,
                "NSE", assetType, TransactionType.BUY, null, AccountType.SELF, ACCOUNT_HOLDER, List.of());
    }

    private static ProfitLossContext sell(List<BuyContext> buyContexts) {
        return new ProfitLossContext(TRANSACTION_ID, 10, TRADE_DATE, 150.0, STOCK_CODE, BrokerName.ZERODHA,
                "NSE", AssetType.EQUITY, TransactionType.SELL, null, AccountType.SELF, ACCOUNT_HOLDER, buyContexts);
    }

    private static ChargeComputation computed() {
        return new ChargeComputation("sched-1", "ZERODHA_EQ_DELIVERY_2025_04", null,
                ChargeResolution.RESOLVED, List.of(), 23.45);
    }

    private ChargeContext recordedContext() {
        ArgumentCaptor<ChargeContext> captor = ArgumentCaptor.forClass(ChargeContext.class);
        verify(userChargeService).computeAndRecord(captor.capture());
        return captor.getValue();
    }

    // ========================================
    // The flag
    // ========================================

    @Test
    void record_whenShadowRecordingDisabled_recordsNothing() {
        // Given
        ChargeRecordingGatewayImpl gateway = gateway(false);

        // When
        Optional<ChargeComputation> result = gateway.record(userMail(), buy(AssetType.EQUITY));

        // Then
        assertThat(result).isEmpty();
        verifyNoInteractions(userChargeService);
    }

    @Test
    void record_whenShadowRecordingEnabled_returnsWhatTheEngineComputed() {
        // Given
        when(userChargeService.computeAndRecord(any())).thenReturn(computed());

        // When
        Optional<ChargeComputation> result = gateway(true).record(userMail(), buy(AssetType.EQUITY));

        // Then
        assertThat(result).contains(computed());
    }

    // ========================================
    // Mapping ProfitLossContext onto ChargeContext
    // ========================================

    @Test
    void record_whenBuy_mapsTheTradeOntoABuyEvent() {
        // Given
        when(userChargeService.computeAndRecord(any())).thenReturn(computed());

        // When
        gateway(true).record(userMail(), buy(AssetType.EQUITY));

        // Then
        ChargeContext context = recordedContext();
        assertThat(context.event()).isEqualTo(ChargeEvent.BUY);
        assertThat(context.email()).isEqualTo(EMAIL);
        assertThat(context.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(context.stockCode()).isEqualTo(STOCK_CODE);
        assertThat(context.accountHolder()).isEqualTo(ACCOUNT_HOLDER);
        assertThat(context.brokerName()).isEqualTo(BrokerName.ZERODHA);
        assertThat(context.exchange()).isEqualTo("NSE");
        assertThat(context.transactionDate()).isEqualTo(TRADE_DATE);
        assertThat(context.quantity()).isEqualTo(10);
        assertThat(context.price()).isEqualTo(100.0);
    }

    @Test
    void record_whenSell_mapsTheTradeOntoASellEvent() {
        // Given
        when(userChargeService.computeAndRecord(any())).thenReturn(computed());

        // When
        gateway(true).record(userMail(), sell(List.of()));

        // Then
        assertThat(recordedContext().event()).isEqualTo(ChargeEvent.SELL);
    }

    /**
     * {@code ProfitLossContext} carries no segment — that field arrives in Phase C. Until it does
     * every trade is priced as delivery, which is what the existing flow already assumes.
     */
    @Test
    void record_whenContextCarriesNoSegment_pricesItAsDelivery() {
        // Given
        when(userChargeService.computeAndRecord(any())).thenReturn(computed());

        // When
        gateway(true).record(userMail(), buy(AssetType.EQUITY));

        // Then
        assertThat(recordedContext().segment()).isEqualTo(TradeSegment.DELIVERY);
    }

    @Test
    void record_derivesTurnoverFromPriceAndQuantity() {
        // Given
        when(userChargeService.computeAndRecord(any())).thenReturn(computed());

        // When
        gateway(true).record(userMail(), buy(AssetType.EQUITY));

        // Then
        assertThat(recordedContext().amount(AmountBasis.TURNOVER)).isEqualTo(1000.0);
    }

    @Test
    void record_whenTradeAroseFromACorporateAction_carriesTheActionType() {
        // Given
        when(userChargeService.computeAndRecord(any())).thenReturn(computed());
        ProfitLossContext context = new ProfitLossContext(TRANSACTION_ID, 10, TRADE_DATE, 100.0, STOCK_CODE,
                BrokerName.ZERODHA, "NSE", AssetType.EQUITY, TransactionType.BUY, CorporateActionType.BONUS,
                AccountType.SELF, ACCOUNT_HOLDER, List.of());

        // When
        gateway(true).record(userMail(), context);

        // Then
        assertThat(recordedContext().corporateActionType()).isEqualTo(CorporateActionType.BONUS);
        assertThat(recordedContext().isCorporateAction()).isTrue();
    }

    // ========================================
    // FIFO lots — the reason a sell is not just a buy with a different event
    // ========================================

    @Test
    void record_whenSell_carriesTheConsumedLots() {
        // Given
        when(userChargeService.computeAndRecord(any())).thenReturn(computed());
        List<BuyContext> lots = List.of(
                new BuyContext(4, LocalDate.of(2025, 1, 5), 90.0),
                new BuyContext(6, LocalDate.of(2025, 5, 20), 110.0));

        // When
        gateway(true).record(userMail(), sell(lots));

        // Then
        assertThat(recordedContext().lots())
                .extracting(lot -> lot.quantity(), lot -> lot.acquisitionDate(), lot -> lot.price())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(4.0, LocalDate.of(2025, 1, 5), 90.0),
                        org.assertj.core.groups.Tuple.tuple(6.0, LocalDate.of(2025, 5, 20), 110.0));
    }

    @Test
    void record_whenBuy_carriesNoLots() {
        // Given
        when(userChargeService.computeAndRecord(any())).thenReturn(computed());

        // When
        gateway(true).record(userMail(), buy(AssetType.EQUITY));

        // Then
        assertThat(recordedContext().lots()).isEmpty();
    }

    // ========================================
    // FR-8 — every asset type reaches the engine
    // ========================================

    @Test
    void record_whenAssetTypeIsNotEquity_stillRecords() {
        // Given
        when(userChargeService.computeAndRecord(any())).thenReturn(computed());

        // When
        gateway(true).record(userMail(), buy(AssetType.MUTUAL_FUND));

        // Then
        assertThat(recordedContext().assetType()).isEqualTo(AssetType.MUTUAL_FUND);
    }

    // ========================================
    // Nothing escapes into the trade path
    // ========================================

    @Test
    void record_whenRecordingFails_swallowsTheFailureAndReturnsEmpty() {
        // Given
        when(userChargeService.computeAndRecord(any()))
                .thenThrow(new IllegalStateException("rate card blew up"));

        // When
        Optional<ChargeComputation> result;
        try (LogCapture logs = LogCapture.on(ChargeRecordingGatewayImpl.class)) {
            result = gateway(true).record(userMail(), buy(AssetType.EQUITY));

            // Then
            assertThat(logs.errors()).anyMatch(message -> message.contains(TRANSACTION_ID));
        }
        assertThat(result).isEmpty();
    }

    @Test
    void record_whenContextIsNull_recordsNothingRatherThanThrowing() {
        // Given
        ChargeRecordingGatewayImpl gateway = gateway(true);

        // When
        Optional<ChargeComputation> result = gateway.record(userMail(), null);

        // Then
        assertThat(result).isEmpty();
        verify(userChargeService, never()).computeAndRecord(any());
    }

    @Test
    void record_whenUserMailIsNull_recordsNothingRatherThanThrowing() {
        // Given
        ChargeRecordingGatewayImpl gateway = gateway(true);

        // When
        Optional<ChargeComputation> result = gateway.record(null, buy(AssetType.EQUITY));

        // Then
        assertThat(result).isEmpty();
        verify(userChargeService, never()).computeAndRecord(any());
    }

    /**
     * {@code ProfitLossContext} is a record with no validation, so a null side is reachable. It is
     * refused loudly rather than guessed at: defaulting to BUY would record a purchase's charges
     * against a disposal, and defaulting to SELL would levy securities transaction tax on a buy.
     */
    @Test
    void record_whenTheTradeHasNoSide_refusesItAndSaysSo() {
        // Given
        ProfitLossContext sideless = new ProfitLossContext(TRANSACTION_ID, 10, TRADE_DATE, 100.0, STOCK_CODE,
                BrokerName.ZERODHA, "NSE", AssetType.EQUITY, null, null, AccountType.SELF, ACCOUNT_HOLDER,
                List.of());

        // When
        Optional<ChargeComputation> result;
        try (LogCapture logs = LogCapture.on(ChargeRecordingGatewayImpl.class)) {
            result = gateway(true).record(userMail(), sideless);

            // Then
            assertThat(logs.errors()).anyMatch(message -> message.contains(TRANSACTION_ID));
        }
        assertThat(result).isEmpty();
        verify(userChargeService, never()).computeAndRecord(any());
    }

    @Test
    void record_whenTheLotListIsNull_carriesNoLotsRatherThanFailing() {
        // Given
        when(userChargeService.computeAndRecord(any())).thenReturn(computed());
        ProfitLossContext noLots = new ProfitLossContext(TRANSACTION_ID, 10, TRADE_DATE, 150.0, STOCK_CODE,
                BrokerName.ZERODHA, "NSE", AssetType.EQUITY, TransactionType.SELL, null, AccountType.SELF,
                ACCOUNT_HOLDER, null);

        // When
        gateway(true).record(userMail(), noLots);

        // Then
        assertThat(recordedContext().lots()).isEmpty();
    }
}
