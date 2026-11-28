package com.thiru.wealthlens.insurance.service;

import com.thiru.wealthlens.insurance.repository.InsuranceRepository;
import com.thiru.wealthlens.shared.privacy.PersonalDataHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person's insurance policies, erased with their account (W-237d; ported to PostgreSQL in W-337c1, D-941), the agent's
 * details in them included. Nothing writes policies today, but the table exists and may hold rows from earlier versions.
 */
@Component
@RequiredArgsConstructor
public class InsuranceRecordErasure implements PersonalDataHolder {

    private final InsuranceRepository repository;

    @Override
    public String noticePart() {
        return "insurance";
    }

    @Override
    @Transactional
    public void erase(String email) {
        repository.deleteByEmail(email);
    }
}
