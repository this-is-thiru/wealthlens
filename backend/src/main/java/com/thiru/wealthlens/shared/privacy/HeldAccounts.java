package com.thiru.wealthlens.shared.privacy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Whether an account is held for erasure (W-237d C3a): asked, not yet erased. Sign-in, every token and billing ask this,
 * so a request stops processing at once while the data waits out Rules r.8(3)'s year. Kept apart from
 * {@link AccountErasureService} because the sign-in path must not depend on every module's holder.
 */
@Component
@RequiredArgsConstructor
public class HeldAccounts {

    private final AccountErasureRequestRepository requests;

    public boolean contains(String email) {
        return requests.existsByEmailAndErasedAtIsNull(email);
    }
}
