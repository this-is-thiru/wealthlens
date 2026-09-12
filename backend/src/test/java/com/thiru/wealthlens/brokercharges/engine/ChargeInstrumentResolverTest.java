package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.STOCK_CODE;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.TRADE_DATE;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.entity.ChargeInstrumentEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeInstrumentRepository;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The instrument's own charges and attributes, as they stood on the trade date.
 *
 * <p>Simpler than the schedule resolver because there is nothing to rank: a profile is keyed on the
 * stock code, and the only question is which version was in force. Overlapping versions are a data
 * error, and the later one is the one that was published second.
 */
@ExtendWith(MockitoExtension.class)
class ChargeInstrumentResolverTest {

    @Mock
    private ChargeInstrumentRepository chargeInstrumentRepository;

    @InjectMocks
    private ChargeInstrumentResolver resolver;

    @Test
    void resolve_findsTheProfileInForce() {
        // Given
        givenProfiles(profile("prof-1", TRADE_DATE));

        // When / Then
        assertThat(resolver.resolve(trade())).get()
                .extracting(ChargeInstrumentEntity::getId).isEqualTo("prof-1");
    }

    @Test
    void resolve_whenNoProfileIsOnFile_isEmpty() {
        // Given — an equity, or a fund whose reference data has not been loaded
        givenProfiles();

        // When / Then — empty rather than fatal. Blocking a quarterly upload because reference data
        // is missing is the wrong trade; the gap is recorded by the engine instead.
        assertThat(resolver.resolve(trade())).isEmpty();
    }

    @Test
    void resolve_whenVersionsOverlap_prefersTheOneThatStartedLater() {
        // Given — an asset management company revised the exit load without closing the old window
        givenProfiles(profile("old", LocalDate.of(2024, 1, 1)), profile("new", LocalDate.of(2025, 1, 1)));

        // When / Then
        assertThat(resolver.resolve(trade())).get()
                .extracting(ChargeInstrumentEntity::getId).isEqualTo("new");
    }

    @Test
    void resolve_whenTwoVersionsShareAStartDate_isRejectedNamingBoth() {
        // Given — indistinguishable, so choosing either would be arbitrary
        givenProfiles(profile("first", TRADE_DATE), profile("second", TRADE_DATE));

        // When / Then
        assertThatThrownBy(() -> resolver.resolve(trade()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("first")
                .hasMessageContaining("second");
    }

    @Test
    void resolve_whenTheTradeNamesNoInstrument_isEmptyWithoutQuerying() {
        // Given — an account-level event such as an annual maintenance charge has no scrip
        // When / Then
        assertThat(resolver.resolve(tradeWithoutStockCode())).isEmpty();
        verify(chargeInstrumentRepository, times(0)).findCandidates(anyString(), any());
    }

    @Test
    void resolve_cachesTheAnswer() {
        // Given
        givenProfiles(profile("prof-1", TRADE_DATE));

        // When
        resolver.resolve(trade());
        resolver.resolve(trade());

        // Then
        verify(chargeInstrumentRepository, times(1)).findCandidates(anyString(), any());
    }

    @Test
    void evictAll_makesTheNextResolutionQueryAgain() {
        givenProfiles(profile("prof-1", TRADE_DATE));
        resolver.resolve(trade());

        resolver.evictAll();
        resolver.resolve(trade());

        verify(chargeInstrumentRepository, times(2)).findCandidates(anyString(), any());
    }

    private void givenProfiles(ChargeInstrumentEntity... profiles) {
        when(chargeInstrumentRepository.findCandidates(STOCK_CODE, TRADE_DATE)).thenReturn(List.of(profiles));
    }

    private static ChargeContext tradeWithoutStockCode() {
        ChargeContext base = trade();
        return new ChargeContext(
                base.email(), base.transactionId(), base.orderId(), null, base.accountHolder(),
                base.brokerName(), base.assetType(), base.segment(), base.exchange(), base.planCode(),
                base.event(), base.transactionDate(), base.corporateActionType(), base.quantity(),
                base.price(), base.lotSize(), base.baseAmounts(), base.lots(), base.attributes());
    }

    private static ChargeInstrumentEntity profile(String id, LocalDate startDate) {
        ChargeInstrumentEntity instrument = new ChargeInstrumentEntity();
        instrument.setId(id);
        instrument.setStockCode(STOCK_CODE);
        instrument.setStartDate(startDate);
        return instrument;
    }
}
