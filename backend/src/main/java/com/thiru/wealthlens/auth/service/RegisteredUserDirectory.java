package com.thiru.wealthlens.auth.service;

import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.auth.repository.UserDetailsRepository;
import com.thiru.wealthlens.shared.privacy.RegisteredUsers;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Every registered account's email, for a breach notice addressed to everyone (W-237c). */
@Service
@RequiredArgsConstructor
public class RegisteredUserDirectory implements RegisteredUsers {

    private final UserDetailsRepository repository;

    @Override
    public List<String> emails() {
        return repository.findAll().stream().map(UserDetail::getEmail).toList();
    }
}
