package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.entity.ChargeAccountEntity;
import com.thiru.wealthlens.brokercharges.repository.ChargeAccountRepository;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.shared.dto.enums.EntityStatus;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The demat accounts an annual-maintenance charge is billed against.
 *
 * <p>A user may hold several accounts with one broker, and maintenance is levied per account — so
 * the account is what carries the billing watermark, not the user.
 */
@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class ChargeAccountService {

    private final ChargeAccountRepository chargeAccountRepository;

    /**
     * Registers an account, or updates the one already on file for the same broker and demat id.
     *
     * <p>Billing history survives an update. Resetting the watermark would make every period already
     * charged billable again, and a second maintenance charge is indistinguishable from a first one
     * once written.
     */
    public ChargeAccountEntity register(ChargeAccountEntity account) {
        if (account.getStatus() == null) {
            account.setStatus(EntityStatus.ACTIVE);
        }

        Optional<ChargeAccountEntity> found = chargeAccountRepository
                .findByEmailAndBrokerNameAndDematAccountId(
                        account.getEmail(), account.getBrokerName(), account.getDematAccountId());

        if (found.isEmpty()) {
            return chargeAccountRepository.save(account);
        }

        ChargeAccountEntity existing = found.get();
        existing.setAccountHolder(account.getAccountHolder());
        existing.setPlanCode(account.getPlanCode());
        existing.setOpenedOn(account.getOpenedOn());
        existing.setAmcFrequency(account.getAmcFrequency());
        existing.setStatus(account.getStatus());
        return chargeAccountRepository.save(existing);
    }

    public List<ChargeAccountEntity> findByEmail(String email) {
        return chargeAccountRepository.findByEmail(email);
    }

    public ChargeAccountEntity findAccount(String email, BrokerName brokerName, String dematAccountId) {
        return chargeAccountRepository
                .findByEmailAndBrokerNameAndDematAccountId(email, brokerName, dematAccountId)
                .orElseThrow(() -> new BadRequestException(
                        "No charge account on file for demat account " + dematAccountId));
    }

    public void deleteByEmail(String email) {
        chargeAccountRepository.deleteByEmail(email);
    }
}
