package com.thiru.wealthlens.portfolio.service;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.service.UserChargeService;
import com.thiru.wealthlens.portfolio.dto.ProfitAndLossRebuildReport;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.ProfitAndLossRepository;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** The P&amp;L rebuild's choices of what to fold and when to write (W-283, D-575); the fold itself is the live one. */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfitAndLossRebuildTest {

    private static final String EMAIL = "rebuild@example.com";
    private static final LocalDate JAN_5 = LocalDate.of(2026, 1, 5);

    @Mock private ProfitAndLossRepository profitAndLossRepository;
    @Mock private TradeOutcomeRepository tradeOutcomeRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserChargeService userChargeService;

    @InjectMocks private ProfitAndLossRebuild rebuild;

    @BeforeEach
    void setUp() {
        when(tradeOutcomeRepository.findByEmail(EMAIL)).thenReturn(List.of());
        when(profitAndLossRepository.findAllByEmail(EMAIL)).thenReturn(List.of());
    }

    @Test
    void rebuild_whenAnAmcCycleIsRecorded_foldsOnlyTheTradesCharges() {
        // Given -- a buy charged ₹20 and an AMC cycle charged ₹300, the same day
        when(userChargeService.findHistory(EMAIL)).thenReturn(List.of(
                charge("t-1", ChargeEvent.BUY, 20.0), charge("amc-1", ChargeEvent.AMC_CYCLE, 300.0)));
        // the AMC row's id resolves too, so only the event can keep it out
        transactions(transaction("t-1", AccountType.SELF), transaction("amc-1", AccountType.SELF));

        // When
        ProfitAndLossRebuildReport report = rebuild.rebuild(UserMail.from(EMAIL), true);

        // Then -- the live P&L never folds an AMC cycle, so only the buy's ₹20
        assertEquals(1, report.chargesFolded());
        assertMoney(20.0, report.years().getFirst().rebuilt().chargesRupees());
    }

    @Test
    void rebuild_whenADryRun_writesNothing() {
        // Given
        when(userChargeService.findHistory(EMAIL)).thenReturn(List.of(charge("t-1", ChargeEvent.BUY, 20.0)));
        transactions(transaction("t-1", AccountType.SELF));

        // When
        rebuild.rebuild(UserMail.from(EMAIL), true);

        // Then
        verify(profitAndLossRepository, never()).deleteByEmail(any());
        verify(profitAndLossRepository, never()).saveAll(anyIterable());
    }

    @Test
    void rebuild_whenWritten_replacesTheUsersDocumentsWithOnePerYear() {
        // Given -- an outsourced buy charged ₹20 on 5 Jan 2026
        when(userChargeService.findHistory(EMAIL)).thenReturn(List.of(charge("t-1", ChargeEvent.BUY, 20.0)));
        transactions(transaction("t-1", AccountType.OUTSOURCED));

        // When
        rebuild.rebuild(UserMail.from(EMAIL), false);

        // Then -- the stored documents go, one for 2025-2026 replaces them, its charge on the outsourced side
        verify(profitAndLossRepository).deleteByEmail(EMAIL);
        List<ProfitAndLossEntity> saved = savedDocuments();
        assertEquals(1, saved.size());
        assertEquals("2025-2026", saved.getFirst().getFinancialYear());
        assertNull(saved.getFirst().getRealisedProfits());
        assertMoney(20.0, saved.getFirst().getOutSourcedRealisedProfits().getYearlyChargeSummary().getTotalCharges());
    }

    @SuppressWarnings("unchecked")
    private List<ProfitAndLossEntity> savedDocuments() {
        ArgumentCaptor<Iterable<ProfitAndLossEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(profitAndLossRepository).saveAll(captor.capture());
        return List.copyOf((Collection<ProfitAndLossEntity>) captor.getValue());
    }

    private void transactions(TransactionEntity... stored) {
        when(transactionRepository.findAllById(anyIterable())).thenReturn(List.of(stored));
    }

    private static UserChargeEntity charge(String transactionId, ChargeEvent event, double brokerage) {
        UserChargeEntity row = new UserChargeEntity();
        row.setEmail(EMAIL);
        row.setTransactionId(transactionId);
        row.setEvent(event);
        row.setTransactionDate(JAN_5);
        row.setAmountByCode(new HashMap<>(Map.of("BROKERAGE", brokerage)));
        return row;
    }

    private static TransactionEntity transaction(String id, AccountType accountType) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setAccountType(accountType);
        return transaction;
    }
}
