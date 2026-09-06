package com.thiru.wealthlens.brokercharges.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thiru.wealthlens.brokercharges.dto.enums.AmcChargeFrequency;
import com.thiru.wealthlens.brokercharges.entity.ChargeAccountEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeAccountRepository;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The demat accounts an annual-maintenance charge is billed against.
 *
 * <p>A user may hold several with one broker, and the charge is levied per account — which is why
 * the account, not the user, is what gets billed.
 */
@ExtendWith(MockitoExtension.class)
class ChargeAccountServiceTest {

    private static final String EMAIL = "investor@example.com";
    private static final String DEMAT = "1208160000000001";

    @Mock
    private ChargeAccountRepository chargeAccountRepository;

    @InjectMocks
    private ChargeAccountService service;

    @Test
    void register_savesAnAccountThatDoesNotExistYet() {
        // Given
        ChargeAccountEntity account = account(DEMAT);
        givenNoExistingAccount();
        when(chargeAccountRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        // When / Then
        assertThat(service.register(account)).isSameAs(account);
    }

    @Test
    void register_whenTheAccountCarriesNoStatus_marksItActive() {
        ChargeAccountEntity account = account(DEMAT);
        account.setStatus(null);
        givenNoExistingAccount();
        when(chargeAccountRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        assertThat(service.register(account).getStatus()).isEqualTo(EntityStatus.ACTIVE);
    }

    @Test
    void register_whenTheAccountAlreadyExists_updatesItRatherThanDuplicating() {
        // Given — the same demat account registered again, with a corrected holder
        ChargeAccountEntity existing = account(DEMAT);
        existing.setId("acct-1");
        existing.setAccountHolder("self");
        when(chargeAccountRepository.findByEmailAndBrokerNameAndDematAccountId(EMAIL, BrokerName.ZERODHA, DEMAT))
                .thenReturn(Optional.of(existing));
        when(chargeAccountRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        ChargeAccountEntity resubmitted = account(DEMAT);
        resubmitted.setAccountHolder("spouse");
        resubmitted.setAmcFrequency(AmcChargeFrequency.QUARTERLY);
        resubmitted.setPlanCode("PLAN_A");
        resubmitted.setOpenedOn(LocalDate.of(2023, 7, 1));
        resubmitted.setStatus(EntityStatus.INACTIVE);

        // When
        ChargeAccountEntity saved = service.register(resubmitted);

        // Then — one document, and every editable field carried over. Anything silently dropped here
        // would leave the stored account disagreeing with what was submitted.
        assertThat(saved.getId()).isEqualTo("acct-1");
        assertThat(saved.getAccountHolder()).isEqualTo("spouse");
        assertThat(saved.getAmcFrequency()).isEqualTo(AmcChargeFrequency.QUARTERLY);
        assertThat(saved.getPlanCode()).isEqualTo("PLAN_A");
        assertThat(saved.getOpenedOn()).isEqualTo(LocalDate.of(2023, 7, 1));
        assertThat(saved.getStatus()).isEqualTo(EntityStatus.INACTIVE);
        verify(chargeAccountRepository, times(1)).save(any());
    }

    @Test
    void register_whenTheAccountAlreadyExists_keepsItsBillingHistory() {
        // Given — re-registering an account must not make it billable for periods already charged.
        // Resetting lastBilledThrough would silently re-levy every past cycle.
        ChargeAccountEntity existing = account(DEMAT);
        existing.setId("acct-1");
        existing.setLastBilledThrough(LocalDate.of(2025, 3, 31));
        existing.setBillingEvents(new ArrayList<>(List.of(new ChargeAccountEntity.BillingEvent(
                "row-1", LocalDate.of(2024, 4, 1), LocalDate.of(2025, 3, 31), LocalDate.of(2025, 4, 1), 300.0))));
        when(chargeAccountRepository.findByEmailAndBrokerNameAndDematAccountId(EMAIL, BrokerName.ZERODHA, DEMAT))
                .thenReturn(Optional.of(existing));
        when(chargeAccountRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        // When
        ChargeAccountEntity saved = service.register(account(DEMAT));

        // Then
        assertThat(saved.getLastBilledThrough()).isEqualTo(LocalDate.of(2025, 3, 31));
        assertThat(saved.getBillingEvents()).hasSize(1);
    }

    @Test
    void findByEmail_listsEveryAccountTheUserHolds() {
        List<ChargeAccountEntity> accounts = List.of(account(DEMAT), account("1208160000000002"));
        when(chargeAccountRepository.findByEmail(EMAIL)).thenReturn(accounts);

        assertThat(service.findByEmail(EMAIL)).isEqualTo(accounts);
    }

    @Test
    void findAccount_returnsTheAccount() {
        ChargeAccountEntity account = account(DEMAT);
        when(chargeAccountRepository.findByEmailAndBrokerNameAndDematAccountId(EMAIL, BrokerName.ZERODHA, DEMAT))
                .thenReturn(Optional.of(account));

        assertThat(service.findAccount(EMAIL, BrokerName.ZERODHA, DEMAT)).isSameAs(account);
    }

    @Test
    void findAccount_whenThereIsNoSuchAccount_isRejectedNamingIt() {
        givenNoExistingAccount();

        assertThatThrownBy(() -> service.findAccount(EMAIL, BrokerName.ZERODHA, DEMAT))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(DEMAT);
    }

    @Test
    void deleteByEmail_removesEveryAccountForTheUser() {
        service.deleteByEmail(EMAIL);

        verify(chargeAccountRepository).deleteByEmail(EMAIL);
    }

    private void givenNoExistingAccount() {
        when(chargeAccountRepository.findByEmailAndBrokerNameAndDematAccountId(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    private static ChargeAccountEntity account(String dematAccountId) {
        ChargeAccountEntity account = new ChargeAccountEntity();
        account.setEmail(EMAIL);
        account.setAccountHolder("self");
        account.setBrokerName(BrokerName.ZERODHA);
        account.setDematAccountId(dematAccountId);
        account.setOpenedOn(LocalDate.of(2024, 4, 1));
        account.setAmcFrequency(AmcChargeFrequency.ANNUALLY);
        account.setStatus(EntityStatus.ACTIVE);
        return account;
    }
}
