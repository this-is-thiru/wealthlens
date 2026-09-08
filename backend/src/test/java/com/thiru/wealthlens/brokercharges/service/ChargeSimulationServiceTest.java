package com.thiru.wealthlens.brokercharges.service;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.context.LotSlice;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeRuleSource;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.brokercharges.dto.request.ChargeSimulationRequest;
import com.thiru.wealthlens.brokercharges.dto.response.ChargeBreakdownResponse;
import com.thiru.wealthlens.brokercharges.engine.ChargeEngine;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The dry run behind {@code POST /charges/simulate}.
 *
 * <p>It exists so the engine can be exercised — from the API collection, from a UI asking "what
 * will this cost?" — without writing a charge to anybody's history. That is the property most worth
 * protecting here, so it is asserted structurally rather than described in a comment: the service
 * holds no repository and no recording service, which makes persistence impossible rather than
 * merely absent today.
 *
 * <p>The rest of the work is turning a JSON request into a {@link ChargeContext} the engine can
 * price. The one piece of judgement in that translation is the base amount: a cash trade's is
 * price × quantity, but a derivatives trade carries several and the caller must be able to state
 * them, because an option priced on notional rather than premium is wrong by orders of magnitude.
 */
@ExtendWith(MockitoExtension.class)
class ChargeSimulationServiceTest {

    @Mock
    private ChargeEngine chargeEngine;

    @InjectMocks
    private ChargeSimulationService chargeSimulationService;

    @Test
    void simulate_pricesTheRequestedTradeThroughTheEngine() {
        // Given
        when(chargeEngine.compute(any())).thenReturn(computation(
                line("BROKERAGE", 20.00), line("STT", 100.00)));

        // When
        ChargeBreakdownResponse response = chargeSimulationService.simulate(sellRequest());

        // Then
        assertThat(response.getResolution()).isEqualTo(ChargeResolution.RESOLVED);
        assertThat(response.getScheduleCode()).isEqualTo("zerodha-equity-2025");
        assertThat(response.getLines()).extracting(ChargeLine::getCode).containsExactly("BROKERAGE", "STT");
        assertThat(response.getAmountByCode()).containsExactly(
                Map.entry("BROKERAGE", 20.00), Map.entry("STT", 100.00));
        assertMoney(120.00, response.getTotalCharges());
    }

    @Test
    void simulate_whenNoBaseAmountsGiven_derivesTurnoverFromPriceAndQuantity() {
        // Given
        when(chargeEngine.compute(any())).thenReturn(computation());

        // When
        chargeSimulationService.simulate(sellRequest());

        // Then
        assertMoney(100_000.00, capturedContext().amount(AmountBasis.TURNOVER));
    }

    @Test
    void simulate_whenLotSizeIsGiven_multipliesItIntoTheDerivedTurnover() {
        // Given — a derivatives lot is priced per unit but traded whole
        when(chargeEngine.compute(any())).thenReturn(computation());
        ChargeSimulationRequest request = sellRequest();
        request.setPrice(50.0);
        request.setQuantity(2);
        request.setLotSize(75);

        // When
        chargeSimulationService.simulate(request);

        // Then
        assertMoney(7_500.00, capturedContext().amount(AmountBasis.TURNOVER));
        assertThat(capturedContext().lotSize()).isEqualTo(75);
    }

    @Test
    void simulate_whenBaseAmountsAreGiven_usesThemUntouched() {
        // Given — an option is charged on premium, never on the notional it controls
        when(chargeEngine.compute(any())).thenReturn(computation());
        ChargeSimulationRequest request = sellRequest();
        request.setBaseAmounts(Map.of(AmountBasis.PREMIUM, 12_500.00, AmountBasis.NOTIONAL, 9_00_000.00));

        // When
        chargeSimulationService.simulate(request);

        // Then — not overwritten by price × quantity, which would be neither of them
        ChargeContext context = capturedContext();
        assertMoney(12_500.00, context.amount(AmountBasis.PREMIUM));
        assertMoney(9_00_000.00, context.amount(AmountBasis.NOTIONAL));
        assertMoney(0.00, context.amount(AmountBasis.TURNOVER));
    }

    @Test
    void simulate_whenLotSizeIsUnset_defaultsToOne() {
        // Given — zero would multiply every notional-based charge down to nothing
        when(chargeEngine.compute(any())).thenReturn(computation());
        ChargeSimulationRequest request = sellRequest();
        request.setLotSize(0);

        // When
        chargeSimulationService.simulate(request);

        // Then
        assertThat(capturedContext().lotSize()).isEqualTo(1);
        assertMoney(100_000.00, capturedContext().amount(AmountBasis.TURNOVER));
    }

    @Test
    void simulate_whenAttributesAreAbsent_passesAnEmptyMapRatherThanNull() {
        // Given — every eligibility predicate reads attributes; null there is a crash, not a default
        when(chargeEngine.compute(any())).thenReturn(computation());

        // When
        chargeSimulationService.simulate(sellRequest());

        // Then
        assertThat(capturedContext().attributes()).isNotNull().isEmpty();
        assertThat(capturedContext().lots()).isNotNull().isEmpty();
    }

    // ------------------------------------------------------- FIFO lots (AC-6 through the API)

    @Test
    void simulate_whenLotsAreSupplied_passesThemToTheEngine() {
        // Given — a charge conditioned on holding period is answered per lot, and the engine can
        // only do that if the caller can say which lots the disposal consumed. Without them a
        // perLot rule evaluates zero times and exit load silently prices at nothing.
        when(chargeEngine.compute(any())).thenReturn(computation());
        ChargeSimulationRequest request = sellRequest();
        request.setPrice(100);
        request.setQuantity(1000);
        request.setLots(List.of(
                new LotSlice(600, LocalDate.of(2024, 2, 1), 100),
                new LotSlice(400, LocalDate.of(2025, 3, 1), 100)));

        // When
        chargeSimulationService.simulate(request);

        // Then
        assertThat(capturedContext().lots()).hasSize(2);
        assertThat(capturedContext().lots().getFirst().holdingDays(request.getTransactionDate()))
                .isEqualTo(487);
    }

    @Test
    void simulate_whenLotQuantitiesDoNotSumToTheTrade_isRejected() {
        // Given — 1,000 units disposed of but only 900 accounted for. Priced as given, the shortfall
        // is charged nothing and the answer looks like a smaller exit load rather than a bad
        // request, which is the failure this endpoint exists to avoid producing confidently.
        ChargeSimulationRequest request = sellRequest();
        request.setQuantity(1000);
        request.setLots(List.of(
                new LotSlice(500, LocalDate.of(2024, 2, 1), 100),
                new LotSlice(400, LocalDate.of(2025, 3, 1), 100)));

        // When / Then
        assertThatThrownBy(() -> chargeSimulationService.simulate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("900")
                .hasMessageContaining("1000");
    }

    @Test
    void simulate_whenLotQuantitiesSumToExactlyTheTolerance_isAccepted() {
        // Given — the boundary itself. Mutual fund units are quoted to four decimals, so a holding
        // reassembled from its parts can miss by exactly one unit of that precision; rejecting it
        // would make the endpoint unusable for the very asset class exit load belongs to.
        when(chargeEngine.compute(any())).thenReturn(computation());
        ChargeSimulationRequest request = sellRequest();
        request.setQuantity(100);
        request.setLots(List.of(
                new LotSlice(50.0, LocalDate.of(2025, 1, 1), 100),
                new LotSlice(50.0001, LocalDate.of(2025, 2, 1), 100)));

        // When / Then — inclusive, and asserted as such because it is a boundary a card's author
        // will sit on rather than near
        assertThat(chargeSimulationService.simulate(request)).isNotNull();
    }

    @Test
    void simulate_whenLotQuantitiesMissByMoreThanTheTolerance_isRejected() {
        // Given — twice the tolerance is a real discrepancy, not rounding
        ChargeSimulationRequest request = sellRequest();
        request.setQuantity(100);
        request.setLots(List.of(
                new LotSlice(50.0, LocalDate.of(2025, 1, 1), 100),
                new LotSlice(50.0002, LocalDate.of(2025, 2, 1), 100)));

        // When / Then
        assertThatThrownBy(() -> chargeSimulationService.simulate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("100.0002");
    }

    @Test
    void simulate_whenALotIsNull_isRejected() {
        // Given — a JSON array with a hole in it deserialises to exactly this
        ChargeSimulationRequest request = sellRequest();
        request.setLots(java.util.Collections.singletonList(null));

        assertThatThrownBy(() -> chargeSimulationService.simulate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void simulate_whenALotHasNoQuantity_isRejected() {
        // Given — a zero-unit lot contributes nothing to the sum check either, so without this the
        // request is rejected for a reason that does not name the actual mistake
        ChargeSimulationRequest request = sellRequest();
        request.setLots(List.of(new LotSlice(0, LocalDate.of(2025, 1, 1), 100)));

        assertThatThrownBy(() -> chargeSimulationService.simulate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("quantity greater than zero");
    }

    @Test
    void simulate_whenALotPriceIsZero_isAccepted() {
        // Given — the same boundary the trade itself has. Bonus units are allotted free, so a lot
        // acquired at nothing is real input rather than a missing field; it simply contributes no
        // turnover of its own. Mutation testing asked for this one: >= 0 mutated to > 0 and every
        // other test still passed.
        when(chargeEngine.compute(any())).thenReturn(computation());
        ChargeSimulationRequest request = sellRequest();
        request.setLots(List.of(new LotSlice(100, LocalDate.of(2025, 1, 1), 0)));

        // When / Then
        assertThat(chargeSimulationService.simulate(request)).isNotNull();
        assertThat(capturedContext().lots()).hasSize(1);
    }

    @Test
    void simulate_whenALotPriceIsNegative_isRejected() {
        // Given — the lot's own price sets its turnover, so a negative one produces a negative
        // charge: money back on a redemption
        ChargeSimulationRequest request = sellRequest();
        request.setLots(List.of(new LotSlice(100, LocalDate.of(2025, 1, 1), -1)));

        assertThatThrownBy(() -> chargeSimulationService.simulate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("price must not be negative");
    }

    @Test
    void simulate_whenALotWasAcquiredAfterTheTrade_isRejected() {
        // Given — a negative holding period. A band of [0, 365) does not cover it, so the charge
        // quietly disappears instead of the impossible input being reported.
        ChargeSimulationRequest request = sellRequest();
        request.setLots(List.of(new LotSlice(100, request.getTransactionDate().plusDays(1), 100)));

        // When / Then
        assertThatThrownBy(() -> chargeSimulationService.simulate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("acquired");
    }

    @Test
    void simulate_whenALotHasNoAcquisitionDate_isRejected() {
        // Given — the one field the whole per-lot mechanism reads
        ChargeSimulationRequest request = sellRequest();
        request.setLots(List.of(new LotSlice(100, null, 100)));

        assertThatThrownBy(() -> chargeSimulationService.simulate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("acquisitionDate");
    }

    @Test
    void simulate_whenTheLotsDoNotAddUpAndTheQuantitiesAreFractional_saysSoInTheCallersUnits() {
        // Given — fund units are fractional, and a message that rounded them to whole numbers would
        // report "100 units against 100" for a genuine shortfall
        ChargeSimulationRequest request = sellRequest();
        request.setQuantity(100.5);
        request.setLots(List.of(new LotSlice(99.25, LocalDate.of(2025, 1, 1), 100)));

        // When / Then
        assertThatThrownBy(() -> chargeSimulationService.simulate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("99.25")
                .hasMessageContaining("100.5");
    }

    @Test
    void simulate_whenTheRequestIsNull_isRejected() {
        // Given / When / Then — the guard existed and nothing exercised it; the branch gate found
        // that when the lot checks were added beside it
        assertThatThrownBy(() -> chargeSimulationService.simulate(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must be supplied");
    }

    @Test
    void simulate_whenNoLotsAreSupplied_stillPrices() {
        // Given — lots are optional. A purchase consumes none, and most charges do not read them.
        when(chargeEngine.compute(any())).thenReturn(computation(line("STT", 100.00)));

        // When
        ChargeBreakdownResponse response = chargeSimulationService.simulate(sellRequest());

        // Then
        assertMoney(100.00, response.getTotalCharges());
        assertThat(capturedContext().lots()).isEmpty();
    }

    @Test
    void simulate_whenBrokerIsMissing_isRejectedNamingTheField() {
        // Given
        ChargeSimulationRequest request = sellRequest();
        request.setBrokerName(null);

        // When / Then
        assertThatThrownBy(() -> chargeSimulationService.simulate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("brokerName");
    }

    @Test
    void simulate_whenAssetTypeIsMissing_isRejectedNamingTheField() {
        ChargeSimulationRequest request = sellRequest();
        request.setAssetType(null);

        assertThatThrownBy(() -> chargeSimulationService.simulate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("assetType");
    }

    @Test
    void simulate_whenEventIsMissing_isRejectedNamingTheField() {
        ChargeSimulationRequest request = sellRequest();
        request.setEvent(null);

        assertThatThrownBy(() -> chargeSimulationService.simulate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("event");
    }

    @Test
    void simulate_whenTransactionDateIsMissing_isRejectedNamingTheField() {
        // Given — the date picks the rate card, so a default of today would silently price a
        // backfilled trade against the wrong one
        ChargeSimulationRequest request = sellRequest();
        request.setTransactionDate(null);

        assertThatThrownBy(() -> chargeSimulationService.simulate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("transactionDate");
    }

    @Test
    void simulate_whenQuantityIsNotPositive_isRejected() {
        ChargeSimulationRequest request = sellRequest();
        request.setQuantity(0);

        assertThatThrownBy(() -> chargeSimulationService.simulate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void simulate_whenPriceIsNegative_isRejected() {
        ChargeSimulationRequest request = sellRequest();
        request.setPrice(-1.0);

        assertThatThrownBy(() -> chargeSimulationService.simulate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("price");
    }

    @Test
    void simulate_whenPriceIsZero_isAccepted() {
        // Given — bonus shares and split allotments are issued free and still attract charges, so
        // zero is a real price rather than missing input. Mutation testing found this: the boundary
        // was stated in a comment and asserted nowhere, and >= 0 mutated to > 0 with every test
        // still passing.
        when(chargeEngine.compute(any())).thenReturn(computation(line("DP", 13.50)));
        ChargeSimulationRequest request = sellRequest();
        request.setPrice(0.0);

        // When
        ChargeBreakdownResponse response = chargeSimulationService.simulate(request);

        // Then
        assertMoney(13.50, response.getTotalCharges());
        assertMoney(0.00, capturedContext().amount(AmountBasis.TURNOVER));
    }

    @Test
    void simulate_cannotPersist_becauseItHoldsNothingThatCould() {
        // Given / When — the structural guarantee behind "persists nothing"
        List<Class<?>> dependencies = Arrays.stream(ChargeSimulationService.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .<Class<?>>map(Field::getType)
                .toList();

        // Then
        assertThat(dependencies)
                .as("a dry run must not be able to write; it holds the engine and nothing else")
                .doesNotContain(UserChargeRepository.class, UserChargeService.class)
                .allSatisfy(type -> assertThat(type.getPackageName()).doesNotContain(".repository"));
    }

    // ---------------------------------------------------------------- fixtures

    private ChargeContext capturedContext() {
        ArgumentCaptor<ChargeContext> captor = ArgumentCaptor.forClass(ChargeContext.class);
        org.mockito.Mockito.verify(chargeEngine).compute(captor.capture());
        return captor.getValue();
    }

    private static ChargeSimulationRequest sellRequest() {
        ChargeSimulationRequest request = new ChargeSimulationRequest();
        request.setBrokerName(BrokerName.ZERODHA);
        request.setAssetType(AssetType.EQUITY);
        request.setSegment(TradeSegment.DELIVERY);
        request.setExchange("NSE");
        request.setEvent(ChargeEvent.SELL);
        request.setTransactionDate(LocalDate.of(2025, 6, 2));
        request.setStockCode("INFY");
        request.setPrice(1000.0);
        request.setQuantity(100);
        return request;
    }

    private static ChargeComputation computation(ChargeLine... lines) {
        double total = Arrays.stream(lines).mapToDouble(ChargeLine::getAmount).sum();
        return new ChargeComputation("sched-1", "zerodha-equity-2025", null,
                ChargeResolution.RESOLVED, List.of(lines), total);
    }

    private static ChargeLine line(String code, double amount) {
        return new ChargeLine(code, code, ChargeCategory.BROKERAGE, ChargeBasis.FLAT,
                ChargeRuleSource.SCHEDULE, null, 0.0, amount, true);
    }
}
