package com.thiru.wealthlens.taxplanning.workspace;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * That a draft's figures were computed under law that has since moved ({@code 13-edge-cases.md} R-8, {@code 18-temporal-versioning.md}
 * V9; W-138): the pack versions then and now, and which of the rules its computation read have changed. Nothing is recomputed; the
 * user re-runs. {@code ruleDiffAvailable} is false for a computation stored before its rules' checksums were kept, whose changed
 * rules cannot be named.
 *
 * @param since when the sweep first saw it, in UTC
 * @param receipt the computation whose figures are stale
 */
public record DraftStaleness(
        @Field("since") LocalDateTime since,
        @Field("receipt") String receipt,
        @Field("computed_under") List<String> computedUnder,
        @Field("current") List<String> current,
        @Field("changed_rules") List<String> changedRules,
        @Field("rule_diff_available") boolean ruleDiffAvailable) {

    public DraftStaleness {
        computedUnder = List.copyOf(computedUnder);
        current = List.copyOf(current);
        changedRules = List.copyOf(changedRules);
    }
}
