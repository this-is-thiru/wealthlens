package com.thiru.wealthlens.auth.service;

import com.thiru.wealthlens.auth.repository.UserDetailsRepository;
import com.thiru.wealthlens.shared.privacy.PersonalDataHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** A person's login, erased last with their account (W-237d), so a run cut off part-way never leaves data without its owner. */
@Component
@RequiredArgsConstructor
public class LoginErasure implements PersonalDataHolder {

    private final UserDetailsRepository repository;

    @Override
    public void erase(String email) {
        repository.deleteById(email);
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
