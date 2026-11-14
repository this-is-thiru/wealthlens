package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.IndexByYear;
import com.thiru.wealthlens.taxengine.rules.definition.IndexedCost;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonFact;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The gain of a capital-gains bucket recomputed with its cost lifted by the Cost Inflation Index — the second measure
 * the cap on the tax on land or building compares against (1961 s.112(1)(a) second proviso; 2025 s.197(3); W-088g,
 * D-426).
 *
 * <p>Whether a bucket has this measure at all is {@code RS-CG-INDEXED-COST}'s: its row states the asset kind, the
 * acquisition date, who the gain belongs to and the year indexation starts from at the latest, so no legal boundary is
 * written here. A bucket no row meets has none — which is what keeps a non-resident's land, and land acquired on or
 * after 23-7-2024, at the single 12.5 per cent of {@code RATE-CG-LONG-OTHER}.
 *
 * <p>The divisor is the later of the year the asset was acquired and the row's floor year (2025 s.72(8)(b)-(c); 1961
 * s.48 Explanation (iii)); the table it divides by is {@code RATE-CG-COST-INFLATION-INDEX}'s, a capital-gains-only
 * table so that widening the shared one cannot lift {@link PerquisiteNode}'s refusal of a year before it (D-115,
 * D-424 C2a).
 *
 * <p>The cost lifted is the price and every charge {@code RS-CG-CHARGE} classes as cost of acquisition (D-434 C1a): a
 * charge deducted at face value beside an indexed price would measure the cap on too large a gain, and so charge too much
 * tax.
 *
 * <p>The facts handed to the row are the asset kind and the acquisition date alone, because those are the facts the
 * row may name: {@link ItemFacts} throws on a fact the item does not carry, so a row that came to read the term would
 * refuse the computation rather than silently find no measure.
 */
final class CapitalGainsIndexation {

    static final RuleSetId QUALIFIER = RuleSetIds.RS_CG_INDEXED_COST;

    private static final RuleSetId INDEX = RuleSetIds.RS_RATE_CG_COST_INFLATION_INDEX;

    private static final ItemFactId ASSET = new ItemFactId("capitalgains.asset");

    private static final ItemFactId ACQUIRED_ON = new ItemFactId("capitalgains.acquired.on");

    private CapitalGainsIndexation() {
    }

    /**
     * The bucket's gain with its cost indexed, a measure line naming the row, both years and the indexed cost; empty
     * where no row of {@link #QUALIFIER} meets the bucket.
     */
    static Optional<Money> indexedGainOf(NodeContext ctx, CapitalGainsInput.Bucket bucket, Money gain,
            List<TraceLine> lines) {
        Optional<RuleRow> qualifying = ctx.optionalRuleOf(QUALIFIER, factsOf(bucket));
        if (qualifying.isEmpty()) {
            return Optional.empty();
        }
        RuleRow row = qualifying.get();
        TaxYearId floor = RuleDefinitions.require(TemporallyApportionedCap.governingVersion(row, ctx.inputs().temporal())
                .definition(), IndexedCost.class, row.id()).floorYear();
        IndexByYear index = index(ctx);
        TaxYearId saleYear = TaxYearId.containing(bucket.facts().transferredOn());
        TaxYearId acquiredYear = TaxYearId.containing(bucket.facts().acquiredOn());
        TaxYearId divisorYear = acquiredYear.startYear() >= floor.startYear() ? acquiredYear : floor;
        Money cost = bucket.cost().plus(CapitalGainsCharges.acquisitionCostOf(ctx, bucket));
        Money indexedCost = cost.scaledBy(index.of(saleYear), index.of(divisorYear));
        Money indexedGain = gain.plus(cost).minus(indexedCost);
        lines.add(ctx.traceLine(lines.size() + 1, row.id(), StepKind.MEASURE, gain, indexedGain)
                .withLimitedBy("the cost " + cost + " indexed by the index for " + saleYear.label() + " ("
                        + index.of(saleYear) + ") over that for " + divisorYear.label() + " (" + index.of(divisorYear)
                        + "): " + indexedCost)
                .withReason(new Reason(ReasonCodes.CG_COST_INDEXED, Map.of("cost", new ReasonFact.MoneyFact(cost),
                        "from", new ReasonFact.TaxYearFact(divisorYear), "to", new ReasonFact.TaxYearFact(saleYear),
                        "indexed", new ReasonFact.MoneyFact(indexedCost)))));
        return Optional.of(indexedGain);
    }

    private static IndexByYear index(NodeContext ctx) {
        RuleRow row = ctx.ruleOf(INDEX);
        return RuleDefinitions.require(TemporallyApportionedCap.governingVersion(row, ctx.inputs().temporal())
                .definition(), IndexByYear.class, row.id());
    }

    private static ItemFacts factsOf(CapitalGainsInput.Bucket bucket) {
        return new ItemFacts(Map.of(ASSET, bucket.facts().assetKind()), Map.of(),
                Map.of(ACQUIRED_ON, bucket.facts().acquiredOn()));
    }
}
