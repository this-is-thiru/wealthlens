package com.thiru.wealthlens.auth.service;

import com.thiru.wealthlens.auth.repository.UserDetailsRepository;
import com.thiru.wealthlens.shared.privacy.PersonalDataHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** A person's login and its refresh tokens, erased last with their account (W-237d), so a run cut off part-way never leaves data without its owner. */
@Component
@RequiredArgsConstructor
public class LoginErasure implements PersonalDataHolder {

    private final UserDetailsRepository repository;
    private final RefreshTokens refreshTokens;

    @Override
    public String noticePart() {
        return "login";
    }

    @Override
    @Transactional
    public void erase(String email) {
        refreshTokens.erase(email);
        repository.deleteByEmail(email);
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
