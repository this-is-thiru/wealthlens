package com.thiru.wealthlens.taxengine.rules.catalogue;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;

/**
 * A rule the claim catalogue places outside itself, and why (W-117d C1a): I23 asks that every deduction or exemption a
 * person claims has an entry, so every other rule says why it has none.
 */
public record NotAClaim(RuleId rule, NotAClaimReason because) {

    public NotAClaim {
        if (rule == null || because == null) {
            throw new IllegalArgumentException("A rule outside the claim catalogue needs its rule and why");
        }
    }
}
