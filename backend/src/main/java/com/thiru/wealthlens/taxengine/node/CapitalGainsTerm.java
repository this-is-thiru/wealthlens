package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.BucketFacts;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.definition.ShortTermHoldingPeriod;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Whether a capital-gains bucket is short-term or long-term, decided on the tax side (W-088k3, D-305).
 *
 * <p>The one row of {@code RS-CG-TERM} the bucket's facts meet gives the period (1961 s.2(42A); 2025 s.2(101)); the
 * bucket is long-term only when its lots were held beyond it, counted in calendar months from the acquisition to the
 * transfer — long-term only after the same date that many months on, by the owner's ruling D-303. A bucket no row
 * covers is refused, its facts named, never given a period by default.
 */
final class CapitalGainsTerm {

    static final RuleSetId HOLDING_PERIOD = RuleSetIds.RS_CG_TERM;

    private static final String UNRECORDED = "UNRECORDED";

    private static final ItemFactId ASSET = new ItemFactId("capitalgains.asset");

    private static final ItemFactId TRANSFER_EXCHANGE = new ItemFactId("trade.transfer.exchange");

    private CapitalGainsTerm() {
    }

    /** The bucket's term, its trace line added to {@code lines}. */
    static Term of(NodeContext ctx, CapitalGainsInput.Bucket bucket, List<TraceLine> lines) {
        BucketFacts facts = bucket.facts();
        String exchange = facts.transferVenue().exchange().orElse(UNRECORDED);
        Optional<RuleRow> row = ctx.optionalRuleOf(HOLDING_PERIOD, new ItemFacts(Map.of(ASSET, facts.assetKind(),
                TRANSFER_EXCHANGE, exchange), Map.of(), Map.of()));
        if (row.isEmpty()) {
            throw new IllegalArgumentException("Capital gains of " + bucket.gain() + " (" + facts + ") fall in no row"
                    + " of " + HOLDING_PERIOD + " for " + ctx.pack().taxYear().label() + ", so how long a holding is"
                    + " short-term is not known for them");
        }
        int months = RuleDefinitions.require(TemporallyApportionedCap.governingVersion(row.get(),
                ctx.inputs().temporal()).definition(), ShortTermHoldingPeriod.class, row.get().id()).months();
        boolean longTerm = facts.acquiredOn().plusMonths(months).isBefore(facts.transferredOn());
        Term term = longTerm ? Term.LONG_TERM : Term.SHORT_TERM;
        lines.add(ctx.traceLine(lines.size() + 1, row.get().id(), StepKind.MEASURE, bucket.gain(), bucket.gain())
                .withLimitedBy("Held " + facts.acquiredOn() + " → " + facts.transferredOn() + ": "
                        + (longTerm ? "more than " : "not more than ") + months + " months, " + term
                        + " (calendar months, D-303)"
                        + (facts.corporateAction().equals(Optional.of("BONUS"))
                                ? "; a bonus lot processed before W-088k4a may be dated at its ex-date (W-088k4b)"
                                : "")));
        return term;
    }
}
