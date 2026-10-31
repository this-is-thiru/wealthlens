package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.CapitalGainsInput.Term;
import com.thiru.wealthlens.taxengine.input.HouseReinvestment;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.ExemptReinvestment;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The part of a long-term capital gain that putting it into a residential house takes out of the charge (2025 s.86;
 * 1961 s.54F; D-248, W-088i).
 *
 * <p>Whether the exemption reaches a bucket at all is {@code RS-CG-EXEMPTION}'s: its row states the term, the asset
 * kinds it serves, the person types and the bar — how many other residential houses the taxpayer may own on the
 * transfer date, and how many may be bought or built around it. The periods within which the new house must be bought
 * or built, and the cap on its cost, are the row's definition's, so no legal period or boundary is written here.
 *
 * <p>The row is matched per bucket by the transfer date the reinvestment names. Two buckets transferring on the same
 * date cannot be told apart, so a second claim on that date is refused rather than one of them chosen.
 *
 * <p>The exemption is taken off the gain alone, and the head's second measure is computed from what is left, so the
 * same amount leaves both. What is not exempted is charged in full where the new house's cost is at least the net
 * consideration (2025 s.86(1)(ii); 1961 s.54F(1)(a)), otherwise in the proportion that cost bears to the net
 * consideration (s.86(1)(i); s.54F(1)(b)), the net consideration being the consideration less the transfer expenditure
 * the charge rows deduct (s.86(10); s.54F Explanation; OQ-38's reading, approved with W-088i).
 */
final class ReinvestmentExemption {

    static final RuleSetId EXEMPTION = RuleSetIds.RS_CG_EXEMPTION;

    private static final ItemFactId TERM = new ItemFactId("capitalgains.term");

    private static final ItemFactId ASSET = new ItemFactId("capitalgains.asset");

    private static final ItemFactId OTHER_HOUSES_OWNED =
            new ItemFactId("capitalgains.reinvestment.other.houses.owned");

    private static final ItemFactId OTHER_HOUSES_ACQUIRED =
            new ItemFactId("capitalgains.reinvestment.other.houses.acquired");

    private ReinvestmentExemption() {
    }

    /**
     * The bucket's gain after the exemption, one {@code EXEMPTION} line added where a row reached it; the gain
     * unchanged where no row does, or where the taxpayer entered no reinvestment for the transfer.
     *
     * @throws IllegalArgumentException where two entered reinvestments name the bucket's transfer
     */
    static Money chargeableGainOf(NodeContext ctx, CapitalGainsInput.Bucket bucket, Money gain, Term term,
            Money netConsideration, List<TraceLine> lines) {
        Optional<HouseReinvestment> claim = claimFor(ctx, bucket);
        if (claim.isEmpty() || !gain.isGreaterThan(Money.ZERO)) {
            return gain;
        }
        Optional<RuleRow> qualifying = ctx.optionalRuleOf(EXEMPTION, factsOf(bucket, term, claim.get()));
        if (qualifying.isEmpty()) {
            return gain;
        }
        RuleRow row = qualifying.get();
        ExemptReinvestment definition = RuleDefinitions.require(TemporallyApportionedCap.governingVersion(row,
                ctx.inputs().temporal()).definition(), ExemptReinvestment.class, row.id());
        HouseReinvestment reinvestment = claim.get();
        Optional<String> outside = outsideWindow(definition, reinvestment);
        Money exempt = outside.isPresent() ? Money.ZERO
                : exemptGain(definition, reinvestment, gain, netConsideration);
        Money chargeable = gain.minus(exempt);
        lines.add(ctx.traceLine(lines.size() + 1, row.id(), StepKind.EXEMPTION, gain, chargeable)
                .withLimitedBy(outside.orElseGet(() -> basisOf(definition, reinvestment, netConsideration))));
        return chargeable;
    }

    /** The reinvestment the taxpayer entered for this bucket's transfer; two on one date are refused. */
    private static Optional<HouseReinvestment> claimFor(NodeContext ctx, CapitalGainsInput.Bucket bucket) {
        LocalDate transferDate = bucket.facts().transferredOn();
        List<HouseReinvestment> matching = ctx.inputs().entered().houseReinvestments().stream()
                .filter(claim -> claim.transferDate().equals(transferDate))
                .toList();
        if (matching.size() > 1) {
            throw new IllegalArgumentException(matching.size() + " reinvestments name the transfer of " + transferDate
                    + ", so which answers this bucket cannot be told: " + matching);
        }
        return matching.stream().findFirst();
    }

    /** The bucket's facts as the rows test them: its term and asset kind, and the bar's two counts. */
    private static ItemFacts factsOf(CapitalGainsInput.Bucket bucket, Term term, HouseReinvestment reinvestment) {
        return new ItemFacts(
                Map.of(TERM, term.name(), ASSET, bucket.facts().assetKind()),
                Map.of(OTHER_HOUSES_OWNED, (long) reinvestment.otherHousesOwned(),
                        OTHER_HOUSES_ACQUIRED, (long) reinvestment.otherHousesAcquired()),
                Map.of());
    }

    /**
     * Why nothing is exempted, where it is not: the new house's date lies outside the window the row's periods give,
     * the window starting at the transfer for a house built and before it for one bought.
     */
    private static Optional<String> outsideWindow(ExemptReinvestment definition, HouseReinvestment reinvestment) {
        LocalDate transfer = reinvestment.transferDate();
        LocalDate from = reinvestment.constructed() ? transfer : transfer.minusMonths(definition.monthsBeforePurchase());
        LocalDate to = transfer.plusMonths(reinvestment.constructed()
                ? definition.monthsAfterConstruction() : definition.monthsAfterPurchase());
        if (!reinvestment.newAssetOn().isBefore(from) && !reinvestment.newAssetOn().isAfter(to)) {
            return Optional.empty();
        }
        return Optional.of("the new house dated " + reinvestment.newAssetOn() + " is outside the window " + from
                + " to " + to + " (S2 s.86(1)(b); 1961 s.54F(1)), so nothing is exempt");
    }

    /** The exempt gain: the whole of it where the new house's cost reaches the net consideration, else its share. */
    private static Money exemptGain(ExemptReinvestment definition, HouseReinvestment reinvestment, Money gain,
            Money netConsideration) {
        Money cost = Money.min(reinvestment.newAssetCost(), definition.newAssetCostCap());
        if (!cost.isLessThan(netConsideration)) {
            return gain;
        }
        return gain.scaledBy(cost, netConsideration);
    }

    /** The figures the exemption was measured on, for the line that shows it. */
    private static String basisOf(ExemptReinvestment definition, HouseReinvestment reinvestment,
            Money netConsideration) {
        Money cost = Money.min(reinvestment.newAssetCost(), definition.newAssetCostCap());
        String capped = cost.isLessThan(reinvestment.newAssetCost())
                ? ", its cost " + reinvestment.newAssetCost() + " capped at " + cost + " (S2 s.86(8))" : "";
        return "the new house " + (reinvestment.constructed() ? "built " : "bought ") + reinvestment.newAssetOn()
                + capped + ", against the net consideration " + netConsideration;
    }
}
