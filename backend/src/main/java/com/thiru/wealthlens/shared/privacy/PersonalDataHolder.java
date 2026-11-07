package com.thiru.wealthlens.shared.privacy;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.core.Ordered;

/**
 * A module that stores a person's personal data, and so takes part in erasing their account (Digital Personal Data Protection
 * Act, 2023 s.8(7)(a), s.12(3); W-237d C1a). Declared here and implemented in each module, because every module depends on
 * {@code shared} and the reverse would be a cycle. {@link #erase} deletes by email, so a second call changes nothing and a run
 * cut off part-way is completed by the next.
 */
public interface PersonalDataHolder extends Ordered {

    /**
     * Stops processing the module does without the person asking (a job, a billing cycle). Most modules do nothing: a held
     * account cannot sign in ({@link HeldAccounts}), and that stops everything the person starts.
     */
    default void hold(String email, LocalDateTime requestedAt) {
        // Nothing runs here without the person, so refusing their sign-in is enough.
    }

    /** Deletes everything the module stores about the person. */
    void erase(String email);

    /**
     * After a restore from a snapshot taken at {@code snapshotTakenAt}, applies again each erasure of part of an account the
     * module recorded at or after it, and each hold of one asked then (W-237f C2a′). Most modules erase only whole accounts, which
     * {@link AccountErasureService#reapply} applies again itself.
     *
     * @return the id of each part erased or held again
     */
    default List<String> reapply(LocalDateTime snapshotTakenAt) {
        return List.of();
    }

    /** Holders erase in this order; the login goes last, so an account is never left without the record of its own data. */
    @Override
    default int getOrder() {
        return 0;
    }
}
