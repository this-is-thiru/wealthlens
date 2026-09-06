package com.thiru.wealthlens.brokercharges.engine;

import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.ACCOUNT_HOLDER;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.EMAIL;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.STOCK_CODE;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.TRADE_DATE;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.rule;
import static com.thiru.wealthlens.brokercharges.engine.ChargeFixtures.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.DedupeScope;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import com.thiru.wealthlens.brokercharges.repository.UserChargeRepository;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A fixed amount levied at most once per window.
 *
 * <p>Depository charges are the case: one debit per scrip per day per demat account, however many
 * sell transactions make it up. What the window is keyed on is the whole difficulty — the key must
 * include the account holder, because a user tracking two people who each sell the same scrip on the
 * same day incurs two separate debits and therefore two charges (D10).
 */
@ExtendWith(MockitoExtension.class)
class ScopedFlatChargeCalculatorTest {

    @Mock
    private UserChargeRepository userChargeRepository;

    @InjectMocks
    private ScopedFlatChargeCalculator calculator;

    @Test
    void basis_isScopedFlat() {
        assertThat(calculator.basis()).isEqualTo(ChargeBasis.SCOPED_FLAT);
    }

    @Test
    void compute_whenTheScopeIsNone_chargesWithoutConsultingHistory() {
        // Given — an unscoped charge is levied every time, so the query is waste
        ChargeRule dp = scopedRule(DedupeScope.NONE);

        // When / Then
        assertThat(calculator.compute(dp, trade(), new ChargeAccumulator())).isEqualByComparingTo("13.50");
        verifyNoInteractions(userChargeRepository);
    }

    @Test
    void compute_whenTheScopeIsAbsent_chargesWithoutConsultingHistory() {
        ChargeRule dp = scopedRule(null);

        assertThat(calculator.compute(dp, trade(), new ChargeAccumulator())).isEqualByComparingTo("13.50");
        verifyNoInteractions(userChargeRepository);
    }

    @Test
    void compute_whenNothingWasChargedForThisScripToday_charges() {
        // Given
        when(userChargeRepository.existsChargeForScripOnDate(
                anyString(), anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(false);

        // When / Then
        assertThat(calculator.compute(scopedRule(DedupeScope.PER_SCRIP_PER_DAY), trade(), new ChargeAccumulator()))
                .isEqualByComparingTo("13.50");
    }

    @Test
    void compute_whenTheScripWasAlreadyChargedToday_isZero() {
        // Given — a second sell of the same scrip on the same day is one depository debit, not two
        when(userChargeRepository.existsChargeForScripOnDate(
                anyString(), anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(true);

        // When / Then
        assertThat(calculator.compute(scopedRule(DedupeScope.PER_SCRIP_PER_DAY), trade(), new ChargeAccumulator()))
                .isEqualByComparingTo("0");
    }

    @Test
    void compute_keysTheScripWindowOnTheAccountHolder() {
        // Given — D10. Without the account holder in the key, a user tracking two people who each
        // sell the same scrip on the same day is charged once for two separate demat debits.
        when(userChargeRepository.existsChargeForScripOnDate(
                anyString(), anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(false);

        // When
        calculator.compute(scopedRule(DedupeScope.PER_SCRIP_PER_DAY), trade(), new ChargeAccumulator());

        // Then — every part of the key, asserted by name
        verify(userChargeRepository).existsChargeForScripOnDate(
                eq(EMAIL), eq(ACCOUNT_HOLDER), eq(BrokerName.ZERODHA), eq(STOCK_CODE), eq(TRADE_DATE), eq("DP"));
    }

    @Test
    void compute_whenTheScopeIsPerOrder_keysOnTheOrder() {
        // Given — brokerage is capped per order while the engine runs per trade, so a partially
        // filled order must not be charged once per fill
        when(userChargeRepository.existsChargeForOrder(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);

        // When / Then
        assertThat(calculator.compute(scopedRule(DedupeScope.PER_ORDER), trade(), new ChargeAccumulator()))
                .isEqualByComparingTo("0");
        verify(userChargeRepository).existsChargeForOrder(EMAIL, ACCOUNT_HOLDER, "ord-1", "DP");
        verify(userChargeRepository, never()).existsChargeForScripOnDate(
                anyString(), anyString(), any(), anyString(), any(), anyString());
    }

    @Test
    void compute_whenTheScopeIsPerOrderAndNothingWasChargedForIt_charges() {
        // Given — the first fill of an order. Without this, a deduplication check stuck at "already
        // charged" would look correct: every assertion would still be zero.
        when(userChargeRepository.existsChargeForOrder(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(false);

        // When / Then
        assertThat(calculator.compute(scopedRule(DedupeScope.PER_ORDER), trade(), new ChargeAccumulator()))
                .isEqualByComparingTo("13.50");
    }

    @Test
    void compute_whenTheScopeIsPerDayAndNothingWasChargedToday_charges() {
        // Given — the first trade of the day
        when(userChargeRepository.existsChargeForDay(anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(false);

        // When / Then
        assertThat(calculator.compute(scopedRule(DedupeScope.PER_DAY), trade(), new ChargeAccumulator()))
                .isEqualByComparingTo("13.50");
    }

    @Test
    void compute_whenTheScopeIsPerDay_keysOnTheDayAcrossEveryScrip() {
        // Given
        when(userChargeRepository.existsChargeForDay(anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(true);

        // When / Then
        assertThat(calculator.compute(scopedRule(DedupeScope.PER_DAY), trade(), new ChargeAccumulator()))
                .isEqualByComparingTo("0");
        verify(userChargeRepository).existsChargeForDay(
                EMAIL, ACCOUNT_HOLDER, BrokerName.ZERODHA, TRADE_DATE, "DP");
    }

    @Test
    void compute_whenTheRepositoryFails_propagates() {
        // Given — a failed lookup must never be read as "nothing charged yet". Absorbing it would
        // double-charge silently, which is worse than the trade failing.
        when(userChargeRepository.existsChargeForScripOnDate(
                anyString(), anyString(), any(), anyString(), any(), anyString()))
                .thenThrow(new IllegalStateException("mongo is down"));

        // When / Then
        assertThatThrownBy(() -> calculator.compute(
                        scopedRule(DedupeScope.PER_SCRIP_PER_DAY), trade(), new ChargeAccumulator()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mongo is down");
    }

    @Test
    void compute_whenTheAmountIsAbsent_isZeroWithoutConsultingHistory() {
        // Given — nothing to levy, so nothing to deduplicate
        ChargeRule dp = scopedRule(DedupeScope.PER_SCRIP_PER_DAY);
        dp.setFlatAmount(null);

        // When / Then
        assertThat(calculator.compute(dp, trade(), new ChargeAccumulator())).isEqualByComparingTo("0");
        verifyNoInteractions(userChargeRepository);
    }

    @Test
    void compute_whenTheTradeCarriesNoOrderId_chargesRatherThanDeduplicatingOnNothing() {
        // Given — an order-scoped charge on a trade with no order to key on. Deduplicating against
        // a null key would suppress every such charge after the first, across unrelated orders.
        ChargeRule dp = scopedRule(DedupeScope.PER_ORDER);

        // When / Then
        assertThat(calculator.compute(dp, tradeWithoutOrderId(), new ChargeAccumulator()))
                .isEqualByComparingTo("13.50");
        verifyNoInteractions(userChargeRepository);
    }

    private static ChargeRule scopedRule(DedupeScope scope) {
        ChargeRule rule = rule("DP", ChargeBasis.SCOPED_FLAT);
        rule.setFlatAmount(13.5);
        rule.setDedupeScope(scope);
        return rule;
    }

    private static ChargeContext tradeWithoutOrderId() {
        ChargeContext base = trade();
        return new ChargeContext(
                base.email(), base.transactionId(), null, base.stockCode(), base.accountHolder(),
                base.brokerName(), base.assetType(), base.segment(), base.exchange(), base.planCode(),
                base.event(), base.transactionDate(), base.corporateActionType(), base.quantity(),
                base.price(), base.lotSize(), base.baseAmounts(), base.lots(), base.attributes());
    }
}
