package com.thiru.wealthlens.shared.privacy;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Every registered user's email, the one mode of communication a user registers (W-237c). Declared here and implemented
 * in {@code auth}, because {@code auth} depends on {@code shared} and the reverse would be a cycle.
 */
public interface RegisteredUsers {

    List<String> emails();

    /**
     * Starts counting at {@code nowUtc} for each account with no sign-in recorded, so an account registered before W-249 gets a
     * full {@code purpose-lapse} from deploy. Only an absent value is set, so a second call changes nothing.
     */
    void startCounting(LocalDateTime nowUtc);

    /** Each account last signed in to at or before {@code cutoffUtc} (W-249). */
    List<LastSignIn> signedInLastBy(LocalDateTime cutoffUtc);
}
