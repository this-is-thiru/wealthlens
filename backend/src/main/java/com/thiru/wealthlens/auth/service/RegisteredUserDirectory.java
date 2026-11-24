package com.thiru.wealthlens.auth.service;

import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.auth.repository.UserDetailsRepository;
import com.thiru.wealthlens.shared.privacy.LastSignIn;
import com.thiru.wealthlens.shared.privacy.RegisteredUsers;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every registered account's email, for a breach notice addressed to everyone (W-237c), and when each was last signed in to,
 * for erasure once its purpose lapses (W-249). Ported to PostgreSQL in W-337c1 (D-941).
 */
@Service
@RequiredArgsConstructor
public class RegisteredUserDirectory implements RegisteredUsers {

    private final UserDetailsRepository repository;

    @Override
    public List<String> emails() {
        return repository.findAll().stream().map(UserDetail::getEmail).toList();
    }

    /** A targeted update, so a sign-in never overwrites a password changed at the same moment. */
    @Transactional("postgresTransactionManager")
    public void recordSignIn(String email, LocalDateTime atUtc) {
        repository.recordSignIn(email, atUtc);
    }

    /** A targeted update by email, refusing when no login holds that email (W-369). */
    @Transactional("postgresTransactionManager")
    public void storePassword(String email, String hash) {
        int matched = repository.storePassword(email, hash);
        if (matched != 1) {
            throw new IllegalStateException("No login holds this email to store its password");
        }
    }

    @Override
    @Transactional("postgresTransactionManager")
    public void startCounting(LocalDateTime nowUtc) {
        repository.startCounting(nowUtc);
    }

    @Override
    public List<LastSignIn> signedInLastBy(LocalDateTime cutoffUtc) {
        return repository.findByLastSignedInAtLessThanEqual(cutoffUtc).stream()
                .map(user -> new LastSignIn(user.getEmail(), user.getLastSignedInAt())).toList();
    }
}
