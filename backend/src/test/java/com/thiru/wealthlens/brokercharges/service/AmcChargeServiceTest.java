package com.thiru.wealthlens.brokercharges.service;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeResolution;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeRuleSource;
import com.thiru.wealthlens.brokercharges.entity.ChargeAccountEntity;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeAccountRepository;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Annual maintenance, billed per demat account rather than per user.
 *
 * <p>Unlike every other charge in the system this one is not triggered by a trade: it is a cycle a
 * clock runs, over accounts rather than transactions. That is why it is a service of its own and not
 * another calculator.
 *
 * <p>The property that matters is that re-running a cycle is a no-op. Someone will run it twice —
 * a retried job, a reprocessed quarter — and a second charge would be indistinguishable from a
 * legitimate one.
 */
@ExtendWith(MockitoExtension.class)
class AmcChargeServiceTest {

    private static final LocalDate BILLED_THROUGH = LocalDate.of(2026, 3, 31);
    private static final String EMAIL = "investor@example.com";
    private static final String DEMAT = "1208160000000001";

    @Mock
    private ChargeAccountRepository chargeAccountRepository;

    @Mock
    private UserChargeService userChargeService;

    @InjectMocks
    private AmcChargeService service;

    @Test
    void runCycle_billsEveryAccountThatIsDue() {
        // Given
        givenDue(account(DEMAT, null), account("1208160000000002", null));
        givenCharged(300.0);

        // When
        service.runCycle(AmcChargeFrequency.ANNUALLY, BILLED_THROUGH);

        // Then
        verify(userChargeService, times(2)).computeAndRecord(any());
    }

    @Test
    void runCycle_pricesTheAccountAsAnAmcCycleRatherThanATrade() {
        // Given — no scrip, no quantity, no turnover. A rate card scoped to an asset type cannot
        // apply to it, which is why the AMC card must leave that dimension unset.
        givenDue(account(DEMAT, null));
        givenCharged(300.0);

        // When
        service.runCycle(AmcChargeFrequency.ANNUALLY, BILLED_THROUGH);

        // Then
        ChargeContext context = capturePriced();
        assertThat(context.event()).isEqualTo(ChargeEvent.AMC_CYCLE);
        assertThat(context.email()).isEqualTo(EMAIL);
        assertThat(context.accountHolder()).isEqualTo("self");
        assertThat(context.brokerName()).isEqualTo(BrokerName.ZERODHA);
        assertThat(context.transactionDate()).isEqualTo(BILLED_THROUGH);
        assertThat(context.stockCode()).isNull();
        assertThat(context.assetType()).isNull();
    }

    @Test
    void runCycle_keysTheChargeOnTheAccountAndThePeriod() {
        // Given — user charges are keyed on transaction id, so a stable synthetic id is what makes
        // a re-run rewrite the same row instead of appending a second charge
        givenDue(account(DEMAT, null));
        givenCharged(300.0);

        // When
        service.runCycle(AmcChargeFrequency.ANNUALLY, BILLED_THROUGH);

        // Then
        assertThat(capturePriced().transactionId()).isEqualTo("AMC-" + DEMAT + "-" + BILLED_THROUGH);
    }

    @Test
    void runCycle_advancesTheAccountsBillingWatermark() {
        // Given
        givenDue(account(DEMAT, null));
        givenCharged(300.0);

        // When
        service.runCycle(AmcChargeFrequency.ANNUALLY, BILLED_THROUGH);

        // Then
        assertThat(captureSaved().getLastBilledThrough()).isEqualTo(BILLED_THROUGH);
    }

    @Test
    void runCycle_recordsWhatWasBilledAndForWhichPeriod() {
        // Given — billed once before, through the end of the previous year
        givenDue(account(DEMAT, LocalDate.of(2025, 3, 31)));
        givenCharged(300.0);

        // When
        service.runCycle(AmcChargeFrequency.ANNUALLY, BILLED_THROUGH);

        // Then — the new period starts the day after the last one ended, leaving no gap and no overlap
        ChargeAccountEntity.BillingEvent event = captureSaved().getBillingEvents().getLast();
        assertThat(event.getPeriodFrom()).isEqualTo(LocalDate.of(2025, 4, 1));
        assertThat(event.getPeriodTo()).isEqualTo(BILLED_THROUGH);
        assertThat(event.getChargedOn()).isEqualTo(BILLED_THROUGH);
        assertThat(event.getUserChargeId()).isEqualTo("row-1");
        assertMoney(300.0, event.getAmount());
    }

    @Test
    void runCycle_whenTheAccountHasNeverBeenBilled_chargesFromWhenItWasOpened() {
        // Given
        givenDue(account(DEMAT, null));
        givenCharged(300.0);

        // When
        service.runCycle(AmcChargeFrequency.ANNUALLY, BILLED_THROUGH);

        // Then
        assertThat(captureSaved().getBillingEvents().getLast().getPeriodFrom())
                .isEqualTo(LocalDate.of(2024, 4, 1));
    }

    @Test
    void runCycle_whenAnAccountIsAlreadyBilledThroughThatDate_skipsIt() {
        // Given — the repository query excludes these, but the guard is restated here because a
        // second charge is indistinguishable from a legitimate one once written
        givenDue(account(DEMAT, BILLED_THROUGH));

        // When
        service.runCycle(AmcChargeFrequency.ANNUALLY, BILLED_THROUGH);

        // Then
        verify(userChargeService, never()).computeAndRecord(any());
        verify(chargeAccountRepository, never()).save(any());
    }

    @Test
    void runCycle_whenAnAccountIsBilledBeyondThatDate_skipsIt() {
        // Given — a cycle re-run for an earlier period than the account has reached
        givenDue(account(DEMAT, LocalDate.of(2027, 3, 31)));

        // When
        service.runCycle(AmcChargeFrequency.ANNUALLY, BILLED_THROUGH);

        // Then
        verify(userChargeService, never()).computeAndRecord(any());
    }

    @Test
    void runCycle_whenNoRateCardCoversTheCycle_leavesTheWatermarkWhereItWas() {
        // Given — charging nothing because no card was on file is a gap, not a free account. Moving
        // the watermark would mean the period is never billed once the card is added.
        givenDue(account(DEMAT, null));
        when(userChargeService.computeAndRecord(any()))
                .thenReturn(ChargeComputation.empty(ChargeResolution.NO_SCHEDULE));

        // When
        service.runCycle(AmcChargeFrequency.ANNUALLY, BILLED_THROUGH);

        // Then
        verify(chargeAccountRepository, never()).save(any());
    }

    @Test
    void runCycle_whenTheCardResolvesToNoCharge_stillAdvancesTheWatermark() {
        // Given — a broker that genuinely levies no maintenance charge. This is a real rate, and the
        // period is settled, so it must not be retried every cycle.
        givenDue(account(DEMAT, null));
        when(userChargeService.computeAndRecord(any()))
                .thenReturn(new ChargeComputation("sched-1", "ZERODHA_AMC", null,
                        ChargeResolution.RESOLVED, List.of(), 0.0));
        when(userChargeService.findForTransaction(anyString(), anyString())).thenReturn(row("row-1"));
        when(chargeAccountRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        // When
        service.runCycle(AmcChargeFrequency.ANNUALLY, BILLED_THROUGH);

        // Then
        assertThat(captureSaved().getLastBilledThrough()).isEqualTo(BILLED_THROUGH);
    }

    @Test
    void runCycle_returnsWhatItBilled() {
        // Given
        givenDue(account(DEMAT, null), account("1208160000000002", BILLED_THROUGH));
        givenCharged(300.0);

        // When / Then — the account already billed is not counted
        assertThat(service.runCycle(AmcChargeFrequency.ANNUALLY, BILLED_THROUGH)).hasSize(1);
    }

    @Test
    void runCycle_whenNothingIsDue_doesNothing() {
        // Given
        when(chargeAccountRepository.findDueForAmc(AmcChargeFrequency.ANNUALLY, BILLED_THROUGH))
                .thenReturn(List.of());

        // When / Then
        assertThat(service.runCycle(AmcChargeFrequency.ANNUALLY, BILLED_THROUGH)).isEmpty();
    }

    // ---------------------------------------------------------------- fixtures

    private void givenDue(ChargeAccountEntity... accounts) {
        when(chargeAccountRepository.findDueForAmc(AmcChargeFrequency.ANNUALLY, BILLED_THROUGH))
                .thenReturn(List.of(accounts));
    }

    private void givenCharged(double amount) {
        ChargeLine line = new ChargeLine();
        line.setCode("AMC");
        line.setCategory(ChargeCategory.SUBSCRIPTION);
        line.setBasis(ChargeBasis.FLAT);
        line.setSource(ChargeRuleSource.SCHEDULE);
        line.setAmount(amount);

        when(userChargeService.computeAndRecord(any())).thenReturn(new ChargeComputation(
                "sched-1", "ZERODHA_AMC", null, ChargeResolution.RESOLVED, List.of(line), amount));
        when(userChargeService.findForTransaction(anyString(), anyString())).thenReturn(row("row-1"));
        when(chargeAccountRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    private ChargeContext capturePriced() {
        ArgumentCaptor<ChargeContext> captor = ArgumentCaptor.forClass(ChargeContext.class);
        verify(userChargeService).computeAndRecord(captor.capture());
        return captor.getValue();
    }

    private ChargeAccountEntity captureSaved() {
        ArgumentCaptor<ChargeAccountEntity> captor = ArgumentCaptor.forClass(ChargeAccountEntity.class);
        verify(chargeAccountRepository).save(captor.capture());
        return captor.getValue();
    }

    private static UserChargeEntity row(String id) {
        UserChargeEntity row = new UserChargeEntity();
        row.setId(id);
        return row;
    }

    private static ChargeAccountEntity account(String dematAccountId, LocalDate lastBilledThrough) {
        ChargeAccountEntity account = new ChargeAccountEntity();
        account.setEmail(EMAIL);
        account.setAccountHolder("self");
        account.setBrokerName(BrokerName.ZERODHA);
        account.setDematAccountId(dematAccountId);
        account.setOpenedOn(LocalDate.of(2024, 4, 1));
        account.setAmcFrequency(AmcChargeFrequency.ANNUALLY);
        account.setLastBilledThrough(lastBilledThrough);
        account.setBillingEvents(new ArrayList<>());
        account.setStatus(EntityStatus.ACTIVE);
        return account;
    }
}
