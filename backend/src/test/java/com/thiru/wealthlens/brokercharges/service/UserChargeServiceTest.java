package com.thiru.wealthlens.brokercharges.service;

import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.ACCOUNT_HOLDER;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.EMAIL;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.STOCK_CODE;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.TRADE_DATE;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.trade;
import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeRuleSource;
import com.thiru.wealthlens.brokercharges.engine.ChargeEngine;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Computing a charge and recording what was computed.
 *
 * <p>The stored row is the source of truth: a contract note has to be reconstructible from one
 * document, and the profit-and-loss charge hierarchy is a projection of these rows rather than the
 * other way round.
 *
 * <h2>A row is written even when nothing is charged</h2>
 * Backfilling several years crosses periods with no rate card on file, and a warning in a log
 * scrolls away long before anyone notices the gap. The reason is stored with the row.
 *
 * <h2>Why the sequence guard exists</h2>
 * Transactions are meant to arrive in quarterly batches in chronological order, which makes holding
 * periods and first-purchase rules safe. That guarantee is a process convention, not something the
 * system enforces — someone will eventually re-run a quarter or load two out of order. The guard
 * turns a silently wrong number into a visible flag for one indexed query per batch.
 */
@ExtendWith(MockitoExtension.class)
class UserChargeServiceTest {

    @Mock
    private ChargeEngine chargeEngine;

    @Mock
    private UserChargeRepository userChargeRepository;

    @InjectMocks
    private UserChargeService service;

    // ---------------------------------------------------------------- recording one trade

    @Test
    void computeAndRecord_storesTheLinesAndTheTotal() {
        // Given
        givenComputation(computation(ChargeResolution.RESOLVED, line("BROKERAGE", 20.0), line("STT", 100.0)));
        givenNoExistingRow();

        // When
        service.computeAndRecord(trade());

        // Then — a contract note reconstructible from one document
        UserChargeEntity saved = captureSaved();
        assertThat(saved.getLines()).extracting(ChargeLine::getCode).containsExactly("BROKERAGE", "STT");
        assertMoney(120.0, saved.getTotalCharges());
        assertThat(saved.getAmountByCode()).containsEntry("BROKERAGE", 20.0).containsEntry("STT", 100.0);
    }

    @Test
    void computeAndRecord_storesTheTradeItPriced() {
        // Given — the row has to stand on its own, without joining back to the transaction
        givenComputation(computation(ChargeResolution.RESOLVED, line("BROKERAGE", 20.0)));
        givenNoExistingRow();

        // When
        service.computeAndRecord(trade());

        // Then
        UserChargeEntity saved = captureSaved();
        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getAccountHolder()).isEqualTo(ACCOUNT_HOLDER);
        assertThat(saved.getBrokerName()).isEqualTo(BrokerName.ZERODHA);
        assertThat(saved.getStockCode()).isEqualTo(STOCK_CODE);
        assertThat(saved.getTransactionId()).isEqualTo("txn-1");
        assertThat(saved.getOrderId()).isEqualTo("ord-1");
        assertThat(saved.getTransactionDate()).isEqualTo(TRADE_DATE);
        assertThat(saved.getAssetType()).isEqualTo(AssetType.EQUITY);
        assertThat(saved.getSegment()).isEqualTo(TradeSegment.DELIVERY);
        assertThat(saved.getExchange()).isEqualTo("NSE");
        assertThat(saved.getEvent()).isEqualTo(ChargeEvent.SELL);
        assertMoney(100000.0, saved.getTurnover());
        assertMoney(100.0, saved.getQuantity());
        assertThat(saved.getComputedOn()).isNotNull();
    }

    @Test
    void computeAndRecord_storesTheProvenanceOfTheComputation() {
        // Given — a corrected rate card has to be able to find every row it produced
        givenComputation(new ChargeComputation("sched-1", "ZERODHA_EQ_2025_04", "prof-1",
                ChargeResolution.RESOLVED, List.of(line("BROKERAGE", 20.0)), 20.0));
        givenNoExistingRow();

        // When
        service.computeAndRecord(trade());

        // Then
        UserChargeEntity saved = captureSaved();
        assertThat(saved.getScheduleId()).isEqualTo("sched-1");
        assertThat(saved.getScheduleCode()).isEqualTo("ZERODHA_EQ_2025_04");
        assertThat(saved.getInstrumentId()).isEqualTo("prof-1");
        assertThat(saved.getResolution()).isEqualTo(ChargeResolution.RESOLVED);
    }

    @Test
    void computeAndRecord_whenNothingIsCharged_stillWritesARowCarryingTheReason() {
        // Given — a period with no rate card on file. A warning in a log scrolls away; this does not.
        givenComputation(ChargeComputation.empty(ChargeResolution.NO_SCHEDULE));
        givenNoExistingRow();

        // When
        service.computeAndRecord(trade());

        // Then
        UserChargeEntity saved = captureSaved();
        assertThat(saved.getResolution()).isEqualTo(ChargeResolution.NO_SCHEDULE);
        assertThat(saved.getLines()).isEmpty();
        assertMoney(0.0, saved.getTotalCharges());
    }

    @Test
    void computeAndRecord_whenTheTransactionWasAlreadyPriced_replacesThatRow() {
        // Given — re-running a quarter to correct a file must not double-charge
        UserChargeEntity existing = new UserChargeEntity();
        existing.setId("row-1");
        when(userChargeRepository.findByEmailAndTransactionId(EMAIL, "txn-1"))
                .thenReturn(Optional.of(existing));
        givenComputation(computation(ChargeResolution.RESOLVED, line("BROKERAGE", 20.0)));

        // When
        service.computeAndRecord(trade());

        // Then — the same document, rewritten
        assertThat(captureSaved().getId()).isEqualTo("row-1");
        verify(userChargeRepository, times(1)).save(any());
    }

    @Test
    void computeAndRecord_returnsWhatTheEngineComputed() {
        // Given
        ChargeComputation computed = computation(ChargeResolution.RESOLVED, line("BROKERAGE", 20.0));
        givenComputation(computed);
        givenNoExistingRow();

        // When / Then — the caller gets the computation, not the persisted shape
        assertThat(service.computeAndRecord(trade())).isSameAs(computed);
    }

    // ---------------------------------------------------------------- batches










    // ---------------------------------------------------------------- queries

    @Test
    void findHistory_returnsEveryRowNewestFirst() {
        List<UserChargeEntity> rows = List.of(new UserChargeEntity());
        when(userChargeRepository.findByEmailOrderByTransactionDateDesc(EMAIL)).thenReturn(rows);

        assertThat(service.findHistory(EMAIL)).isEqualTo(rows);
    }

    @Test
    void findGaps_returnsEveryRowWhoseChargesCouldNotBeFullyAssessed() {
        // Given — the four reasons a stored zero should not be read as "this trade was free".
        //
        // NO_MATCHING_RULES is one of them because a card can resolve and still price nothing: an
        // unscoped card — a maintenance card is the obvious one — matches every dimension of a trade
        // whose asset type has no card of its own, wins by default, and then matches none of its
        // rules against a trade event. Without this the trade charges zero and never surfaces.
        List<UserChargeEntity> rows = List.of(new UserChargeEntity());
        when(userChargeRepository.findByEmailAndResolutionIn(EMAIL, List.of(
                ChargeResolution.NO_SCHEDULE,
                ChargeResolution.NO_INSTRUMENT_PROFILE,
                ChargeResolution.NO_MATCHING_RULES,
                ChargeResolution.PROVISIONAL))).thenReturn(rows);

        // When / Then
        assertThat(service.findGaps(EMAIL)).isEqualTo(rows);
    }

    @Test
    void findForTransaction_returnsTheContractNote() {
        UserChargeEntity row = new UserChargeEntity();
        when(userChargeRepository.findByEmailAndTransactionId(EMAIL, "txn-1")).thenReturn(Optional.of(row));

        assertThat(service.findForTransaction(EMAIL, "txn-1")).isSameAs(row);
    }

    @Test
    void findForTransaction_whenTheTransactionWasNeverPriced_isRejectedByName() {
        when(userChargeRepository.findByEmailAndTransactionId(anyString(), anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findForTransaction(EMAIL, "txn-9"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("txn-9");
    }

    @Test
    void deleteByEmail_removesEveryRowForTheUser() {
        service.deleteByEmail(EMAIL);

        verify(userChargeRepository).deleteByEmail(EMAIL);
    }

    // ---------------------------------------------------------------- fixtures

    private void givenComputation(ChargeComputation computation) {
        when(chargeEngine.compute(any())).thenReturn(computation);
    }

    private void givenNoExistingRow() {
        when(userChargeRepository.findByEmailAndTransactionId(anyString(), anyString()))
                .thenReturn(Optional.empty());
    }


    private UserChargeEntity captureSaved() {
        ArgumentCaptor<UserChargeEntity> captor = ArgumentCaptor.forClass(UserChargeEntity.class);
        verify(userChargeRepository).save(captor.capture());
        return captor.getValue();
    }

    private static ChargeComputation computation(ChargeResolution resolution, ChargeLine... lines) {
        double total = List.of(lines).stream().mapToDouble(ChargeLine::getAmount).sum();
        return new ChargeComputation("sched-1", "ZERODHA_EQ_2025_04", null, resolution, List.of(lines), total);
    }

    private static ChargeLine line(String code, double amount) {
        ChargeLine line = new ChargeLine();
        line.setCode(code);
        line.setCategory(ChargeCategory.BROKERAGE);
        line.setBasis(ChargeBasis.FLAT);
        line.setSource(ChargeRuleSource.SCHEDULE);
        line.setAmount(amount);
        return line;
    }

    private static ChargeContext tradeOn(String transactionId, LocalDate date) {
        ChargeContext base = trade();
        return new ChargeContext(
                base.email(), transactionId, base.orderId(), base.stockCode(), base.accountHolder(),
                base.brokerName(), base.assetType(), base.segment(), base.exchange(), base.planCode(),
                base.event(), date, base.corporateActionType(), base.quantity(), base.price(),
                base.lotSize(), base.baseAmounts(), base.lots(), base.attributes());
    }

    // ------------------------------------------------------- filtered history (Chunk 9)

    @Test
    void findHistory_whenNoFilterIsGiven_returnsEverythingNewestFirst() {
        // Given
        when(userChargeRepository.findByEmailOrderByTransactionDateDesc(EMAIL))
                .thenReturn(List.of(charge(AssetType.EQUITY, LocalDate.of(2025, 6, 2))));

        // When
        List<UserChargeEntity> history = service.findHistory(EMAIL, null, null, null);

        // Then — no dates means no range query; the full history is already ordered
        assertThat(history).hasSize(1);
        verify(userChargeRepository).findByEmailOrderByTransactionDateDesc(EMAIL);
    }

    @Test
    void findHistory_whenBothDatesAreGiven_queriesTheRangeRatherThanFilteringInMemory() {
        // Given — the selective predicate belongs in the database; a year of charges is not a
        // list worth pulling across to discard most of it
        LocalDate from = LocalDate.of(2025, 4, 1);
        LocalDate to = LocalDate.of(2025, 6, 30);
        when(userChargeRepository.findByEmailAndTransactionDateBetween(EMAIL, from, to))
                .thenReturn(List.of(charge(AssetType.EQUITY, LocalDate.of(2025, 6, 2))));

        // When
        List<UserChargeEntity> history = service.findHistory(EMAIL, from, to, null);

        // Then
        assertThat(history).hasSize(1);
        verify(userChargeRepository).findByEmailAndTransactionDateBetween(EMAIL, from, to);
    }

    @Test
    void findHistory_whenOnlyOneDateIsGiven_isRejectedRatherThanGuessed() {
        // Given / When / Then — an open-ended range could mean "since April" or "up to April",
        // and answering the wrong one looks like missing charges
        assertThatThrownBy(() -> service.findHistory(EMAIL, LocalDate.of(2025, 4, 1), null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("both");
    }

    @Test
    void findHistory_whenRangeIsInverted_isRejected() {
        assertThatThrownBy(() -> service.findHistory(
                EMAIL, LocalDate.of(2025, 6, 30), LocalDate.of(2025, 4, 1), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("after");
    }

    @Test
    void findHistory_whenAssetTypeIsGiven_keepsOnlyThatAssetType() {
        // Given
        when(userChargeRepository.findByEmailOrderByTransactionDateDesc(EMAIL)).thenReturn(List.of(
                charge(AssetType.EQUITY, LocalDate.of(2025, 6, 2)),
                charge(AssetType.MUTUAL_FUND, LocalDate.of(2025, 6, 3))));

        // When
        List<UserChargeEntity> history = service.findHistory(EMAIL, null, null, AssetType.MUTUAL_FUND);

        // Then
        assertThat(history).extracting(UserChargeEntity::getAssetType)
                .containsExactly(AssetType.MUTUAL_FUND);
    }

    private static UserChargeEntity charge(AssetType assetType, LocalDate date) {
        UserChargeEntity charge = new UserChargeEntity();
        charge.setEmail(EMAIL);
        charge.setAssetType(assetType);
        charge.setTransactionDate(date);
        return charge;
    }
}
