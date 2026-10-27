package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Whether the capital-gains head takes a bucket at all, decided by rule data before its term (W-160a, D-363).
 *
 * <p>A bucket a row of {@code RS-CG-NOT-COMPUTED} reaches is income of another head this engine does not compute — a
 * speculative transaction, a derivative (2025 s.66(31), (33); 1961 s.43(5)) — and is refused, naming the row. One a
 * row of {@code RS-CG-ELSEWHERE} reaches is income another head charges and is entered there — a deposit's interest —
 * and is left out with a nil line naming the row. Which buckets each reaches is the rows' conditions on the asset and
 * the transfer's segment, so no asset or segment is named here.
 */
final class CapitalGainsScope {

    static final RuleSetId NOT_COMPUTED = RuleSetIds.RS_CG_NOT_COMPUTED;

    static final RuleSetId ELSEWHERE = RuleSetIds.RS_CG_ELSEWHERE;

    private static final String UNRECORDED = "UNRECORDED";

    private static final ItemFactId ASSET = new ItemFactId("capitalgains.asset");

    private static final ItemFactId TRANSFER_SEGMENT = new ItemFactId("trade.transfer.segment");

    private CapitalGainsScope() {
    }

    /**
     * Whether the head takes the bucket of {@code gain}; a bucket another head charges is traced as left out on
     * {@code lines}.
     *
     * @throws IllegalArgumentException where the bucket is income of a head this engine does not compute
     */
    static boolean takes(NodeContext ctx, CapitalGainsInput.Bucket bucket, Money gain, List<TraceLine> lines) {
        BucketFacts facts = bucket.facts();
        ItemFacts item = new ItemFacts(Map.of(ASSET, facts.assetKind(),
                TRANSFER_SEGMENT, facts.transferVenue().segment().orElse(UNRECORDED)), Map.of(), Map.of());
        Optional<RuleRow> notComputed = ctx.optionalRuleOf(NOT_COMPUTED, item);
        if (notComputed.isPresent()) {
            throw new IllegalArgumentException("Capital gains of " + gain + " (" + facts + ") are reached by "
                    + notComputed.get().id() + " in TY " + ctx.pack().taxYear().label() + ": income of a head this"
                    + " engine does not compute, so they cannot be taxed here");
        }
        Optional<RuleRow> elsewhere = ctx.optionalRuleOf(ELSEWHERE, item);
        elsewhere.ifPresent(row -> lines.add(ctx.traceLine(lines.size() + 1, row.id(), StepKind.MEASURE,
                        gain, Money.ZERO)
                .withLimitedBy("left out of the head: " + row.id() + " — another head charges it, entered there")));
        return elsewhere.isEmpty();
    }
}
