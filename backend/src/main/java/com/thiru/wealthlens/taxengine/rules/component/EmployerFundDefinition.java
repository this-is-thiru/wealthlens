package com.thiru.wealthlens.taxengine.rules.component;

import com.thiru.wealthlens.taxengine.rules.id.EmployerFundCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.util.Set;

/**
 * One row of the employer-fund registry (W-238e): what the year's law does with an employer's contribution to the fund.
 * A contribution is not a component of pay (S2 s.17(1)(h); D-126), so it has no component row, yet a reader must still be
 * able to say which rules it feeds — as a fact in the pack, never a list written in Java (CLAUDE.md §4).
 *
 * @param code the fund, as the rules' {@code employer.fund} fact names it
 * @param displayName what a user sees
 * @param feedsInto every rule the contribution can feed: those whose conditions read the fund, and those a node reaches
 *     with it, each said in the row's note; never empty, since a fund no rule reads is one the engine ignores
 * @param validity when the row exists
 * @param provenance where it was sourced
 */
public record EmployerFundDefinition(EmployerFundCode code, String displayName, Set<RuleId> feedsInto,
        DateRange validity, Provenance provenance) {

    public EmployerFundDefinition {
        if (code == null) {
            throw new IllegalArgumentException("An employer-fund definition must have a code");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException(code + " must have a display name");
        }
        if (feedsInto == null || feedsInto.isEmpty()) {
            throw new IllegalArgumentException(code + " feeds no rule; a fund no rule reads is one the engine ignores");
        }
        if (validity == null || provenance == null) {
            throw new IllegalArgumentException(code + " must say when it exists and where it was sourced");
        }
        feedsInto = Set.copyOf(feedsInto);
    }
}
