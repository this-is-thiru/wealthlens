package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.TRADE_DATE;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.trade;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.tradeInScope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.brokercharges.entity.ChargeScheduleEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeScheduleRepository;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.testsupport.LogCapture;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Which rate card priced a trade.
 *
 * <p>Validity — the date window and the status predicate — belongs to the repository query and is
 * asserted in {@code ChargeRepositoryIntegrationTest} against a real Mongo, including the point that
 * a superseded card still prices a transaction backdated into its own window (ADR-12). What is under
 * test here is the choice between cards that are all already valid.
 *
 * <p>A dimension a card leaves null matches anything. A dimension it declares must agree, and a card
 * that disagrees is removed from consideration rather than merely outranked — otherwise an intraday
 * card would price a delivery trade whenever no delivery card existed.
 */
@ExtendWith(MockitoExtension.class)
class ChargeScheduleResolverTest {

    @Mock
    private ChargeScheduleRepository chargeScheduleRepository;

    @InjectMocks
    private ChargeScheduleResolver resolver;

    @Test
    void resolve_selectsTheCardWhoseDeclaredDimensionsAllAgree() {
        // Given
        givenCandidates(card("EXACT", AssetType.EQUITY, TradeSegment.DELIVERY, "NSE", null, TRADE_DATE));

        // When / Then
        assertThat(resolver.resolve(trade())).get()
                .extracting(ChargeScheduleEntity::getScheduleCode).isEqualTo("EXACT");
    }

    @Test
    void resolve_whenACardDeclaresAConflictingDimension_disqualifiesIt() {
        // Given — an intraday card and nothing else. Outranking is not enough: with no other
        // candidate it would win by default and price a delivery trade at intraday rates.
        givenCandidates(card("INTRADAY", AssetType.EQUITY, TradeSegment.INTRADAY, null, null, TRADE_DATE));

        // When / Then
        assertThat(resolver.resolve(trade())).isEmpty();
    }

    @Test
    void resolve_prefersACardNamingTheAssetTypeOverOneThatMatchesAnything() {
        // Given
        givenCandidates(
                card("GENERIC", null, null, null, null, TRADE_DATE),
                card("EQUITY", AssetType.EQUITY, null, null, null, TRADE_DATE));

        // When / Then
        assertThat(codeOf(resolver.resolve(trade()))).isEqualTo("EQUITY");
    }

    @Test
    void resolve_prefersACardNamingTheSegmentOverOneNamingOnlyTheAssetType() {
        // Given — segment scores 2, asset type 1
        givenCandidates(
                card("EQUITY", AssetType.EQUITY, null, null, null, TRADE_DATE),
                card("DELIVERY", null, TradeSegment.DELIVERY, null, null, TRADE_DATE));

        // When / Then
        assertThat(codeOf(resolver.resolve(trade()))).isEqualTo("DELIVERY");
    }

    @Test
    void resolve_ranksExchangeAboveSegmentAndAssetTypeTogether() {
        // Given — exchange scores 4, which beats segment plus asset type at 3. NSE and BSE levy
        // different transaction charges, so naming the exchange is the more specific statement.
        givenCandidates(
                card("SEGMENT_AND_TYPE", AssetType.EQUITY, TradeSegment.DELIVERY, null, null, TRADE_DATE),
                card("EXCHANGE", null, null, "NSE", null, TRADE_DATE));

        // When / Then
        assertThat(codeOf(resolver.resolve(trade()))).isEqualTo("EXCHANGE");
    }

    @Test
    void resolve_ranksPlanCodeAboveEveryOtherDimensionCombined() {
        // Given — a negotiated rate for one user beats the most specific published card. 8 > 4+2+1.
        givenCandidates(
                card("PUBLISHED", AssetType.EQUITY, TradeSegment.DELIVERY, "NSE", null, TRADE_DATE),
                card("NEGOTIATED", null, null, null, "PLAN_A", TRADE_DATE));

        // When / Then
        assertThat(codeOf(resolver.resolve(
                tradeInScope(AssetType.EQUITY, TradeSegment.DELIVERY, "NSE", "PLAN_A"))))
                .isEqualTo("NEGOTIATED");
    }

    @Test
    void resolve_whenTwoCardsAreEquallySpecific_prefersTheLaterStartDate() {
        // Given — a repriced card published later for the same scope
        givenCandidates(
                card("APRIL", AssetType.EQUITY, null, null, null, LocalDate.of(2025, 4, 1)),
                card("MAY", AssetType.EQUITY, null, null, null, LocalDate.of(2025, 5, 1)));

        // When / Then
        assertThat(codeOf(resolver.resolve(trade()))).isEqualTo("MAY");
    }

    @Test
    void resolve_whenTwoCardsAreIndistinguishable_isRejectedNamingBoth() {
        // Given — same scope, same start date, both valid. Picking either would be arbitrary, and
        // whichever was picked would price every trade in the window until someone noticed.
        givenCandidates(
                card("FIRST", AssetType.EQUITY, null, null, null, TRADE_DATE),
                card("SECOND", AssetType.EQUITY, null, null, null, TRADE_DATE));

        // When / Then
        assertThatThrownBy(() -> resolver.resolve(trade()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FIRST")
                .hasMessageContaining("SECOND");
    }

    @Test
    void resolve_whenNoCardIsOnFile_isEmptyAndWarns() {
        // Given — a quarter backdated before any rate card was recorded
        givenCandidates();

        // When / Then
        try (LogCapture logs = LogCapture.on(ChargeScheduleResolver.class)) {
            assertThat(resolver.resolve(trade())).isEmpty();
            assertThat(logs.warnings()).singleElement().asString().contains("ZERODHA");
        }
    }

    @Test
    void resolve_whenEveryCandidateIsDisqualified_isEmptyAndWarns() {
        // Given — cards exist for the broker, none of them for this trade
        givenCandidates(card("INTRADAY", AssetType.EQUITY, TradeSegment.INTRADAY, null, null, TRADE_DATE));

        // When / Then
        try (LogCapture logs = LogCapture.on(ChargeScheduleResolver.class)) {
            assertThat(resolver.resolve(trade())).isEmpty();
            assertThat(logs.warnings()).isNotEmpty();
        }
    }

    @Test
    void resolve_cachesTheAnswerForAScope() {
        // Given — rate cards change monthly at most, and this runs per transaction
        givenCandidates(card("EXACT", AssetType.EQUITY, TradeSegment.DELIVERY, "NSE", null, TRADE_DATE));

        // When
        resolver.resolve(trade());
        resolver.resolve(trade());

        // Then
        verify(chargeScheduleRepository, times(1)).findCandidates(any(), any());
    }

    @Test
    void resolve_cachesTheAbsenceOfACardToo() {
        // Given — backfilling years of trades through a period with no card must not re-ask on
        // every one of them
        givenCandidates();

        // When
        resolver.resolve(trade());
        resolver.resolve(trade());

        // Then
        verify(chargeScheduleRepository, times(1)).findCandidates(any(), any());
    }

    @Test
    void resolve_keepsScopesApartInTheCache() {
        // Given — the same broker and date, a different segment
        givenCandidates(card("GENERIC", null, null, null, null, TRADE_DATE));

        // When
        resolver.resolve(tradeInScope(AssetType.EQUITY, TradeSegment.DELIVERY, "NSE", null));
        resolver.resolve(tradeInScope(AssetType.EQUITY, TradeSegment.INTRADAY, "NSE", null));

        // Then — a cache keyed too loosely would answer the second from the first
        verify(chargeScheduleRepository, times(2)).findCandidates(any(), any());
    }

    @Test
    void evictAll_makesTheNextResolutionQueryAgain() {
        // Given — publishing a card has to be visible to the next trade priced
        givenCandidates(card("EXACT", AssetType.EQUITY, TradeSegment.DELIVERY, "NSE", null, TRADE_DATE));
        resolver.resolve(trade());

        // When
        resolver.evictAll();
        resolver.resolve(trade());

        // Then
        verify(chargeScheduleRepository, times(2)).findCandidates(any(), any());
    }

    private void givenCandidates(ChargeScheduleEntity... candidates) {
        when(chargeScheduleRepository.findCandidates(BrokerName.ZERODHA, TRADE_DATE))
                .thenReturn(List.of(candidates));
    }

    private static String codeOf(Optional<ChargeScheduleEntity> resolved) {
        return resolved.map(ChargeScheduleEntity::getScheduleCode).orElse(null);
    }

    private static ChargeScheduleEntity card(String code, AssetType assetType, TradeSegment segment,
                                             String exchange, String planCode, LocalDate startDate) {
        ChargeScheduleEntity schedule = new ChargeScheduleEntity();
        schedule.setId("id-" + code);
        schedule.setScheduleCode(code);
        schedule.setBrokerName(BrokerName.ZERODHA);
        schedule.setAssetType(assetType);
        schedule.setSegment(segment);
        schedule.setExchange(exchange);
        schedule.setPlanCode(planCode);
        schedule.setStartDate(startDate);
        return schedule;
    }
}
