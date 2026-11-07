package com.thiru.wealthlens.shared.privacy;

import java.util.List;

/**
 * The affected users the owner has sent the notice to by email (W-237c).
 *
 * @param emails each user emailed
 */
public record BreachEmails(List<String> emails) {

    public BreachEmails {
        emails = emails == null ? List.of() : List.copyOf(emails);
    }
}
