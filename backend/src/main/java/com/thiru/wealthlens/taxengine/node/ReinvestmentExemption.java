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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The part of a long-term capital gain that putting it into a residential house takes out of the charge (2025 s.86;
 * 1961 s.54F; D-248, W-088i).
 *
 * <p>Whether the exemption reaches a bucket at all is {@code RS-CG-EXEMPTION}'s: its row states the term, the asset
 * kinds it serves, the person types and the bar — how many other residential houses the taxpayer may own on the
 * transfer date, and how many may be bought or built around it. The periods within which the new house must be bought
 * or built, and the cap on its cost, are the row's definition's, so no legal period or boundary is written here.
 *
 * <p>A claim names the transfer it answers by its date, and reaches every bucket of that date a row reaches.
 * {@code portfolio} splits one sale into a bucket per lot key, so those buckets are measured together — their gains
 * and net considerations summed, the cap and the proportion applied once — and the exempt amount is shared back to the
 * buckets with a gain in proportion to that gain, the last taking the remainder. A losing bucket of the sale lowers the
 * gain measured and is never written down itself. The figure is then the same however the sale was split, and the new
 * house's cost is counted once (W-088i5, D-430). A second claim on one date is refused rather than one of them chosen.
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
     * A bucket the head takes, with what the exemption is measured from: its gain and net consideration, and the term
     * the rows decided for it.
     */
    record BucketGain(CapitalGainsInput.Bucket bucket, CapitalGainsCharges.Gain gain, Term term) {
    }

    /**
     * Each bucket's gain after the exemption, in the order given: an {@code EXEMPTION} line for each bucket with a gain
     * that a claim's row reached; the gain unchanged where no row does, or where no reinvestment names the transfer.
     *
     * @throws IllegalArgumentException where two entered reinvestments name one transfer
     */
    static List<Money> chargeableGainsOf(NodeContext ctx, List<BucketGain> buckets, List<TraceLine> lines) {
        List<Money> chargeable = new ArrayList<>(buckets.stream().map(bucket -> bucket.gain().gain()).toList());
        Set<LocalDate> transfers = new LinkedHashSet<>();
        buckets.forEach(bucket -> transfers.add(bucket.bucket().facts().transferredOn()));
        for (LocalDate transfer : transfers) {
            claimOn(ctx, transfer).ifPresent(claim -> exempt(ctx, claim, reached(ctx, buckets, claim), buckets,
                    chargeable, lines));
        }
        return List.copyOf(chargeable);
    }

    /** The reinvestment the taxpayer entered for the transfer on {@code transferDate}; two on one date are refused. */
    private static Optional<HouseReinvestment> claimOn(NodeContext ctx, LocalDate transferDate) {
        List<HouseReinvestment> matching = ctx.inputs().entered().houseReinvestments().stream()
                .filter(claim -> claim.transferDate().equals(transferDate))
                .toList();
        if (matching.size() > 1) {
            throw new IllegalArgumentException(matching.size() + " reinvestments name the transfer of " + transferDate
                    + ", so which answers this bucket cannot be told: " + matching);
        }
        return matching.stream().findFirst();
    }

    /** The positions of the buckets of the claim's transfer that a row reaches, each with the row that reached it. */
    private static Map<Integer, RuleRow> reached(NodeContext ctx, List<BucketGain> buckets, HouseReinvestment claim) {
        Map<Integer, RuleRow> reached = new LinkedHashMap<>();
        for (int position = 0; position < buckets.size(); position++) {
            BucketGain bucket = buckets.get(position);
            if (bucket.bucket().facts().transferredOn().equals(claim.transferDate())) {
                int at = position;
                ctx.optionalRuleOf(EXEMPTION, factsOf(bucket.bucket(), bucket.term(), claim))
                        .ifPresent(row -> reached.put(at, row));
            }
        }
        return reached;
    }

    /** The claim measured once over the buckets it reached, the exempt amount shared to those with a gain. */
    private static void exempt(NodeContext ctx, HouseReinvestment claim, Map<Integer, RuleRow> reached,
            List<BucketGain> buckets, List<Money> chargeable, List<TraceLine> lines) {
        if (reached.isEmpty()) {
            return;
        }
        RuleRow first = reached.values().iterator().next();
        ExemptReinvestment definition = RuleDefinitions.require(TemporallyApportionedCap.governingVersion(first,
                ctx.inputs().temporal()).definition(), ExemptReinvestment.class, first.id());
        Measure measure = Measure.of(reached.keySet(), buckets);
        Optional<String> outside = outsideWindow(definition, claim);
        Money exempt = outside.isPresent() || !measure.hasGain() ? Money.ZERO
                : exemptGain(definition, claim, measure.gain(), measure.netConsideration());
        String reason = outside.orElseGet(() -> reasonOf(definition, claim, measure));
        Money shared = Money.ZERO;
        for (int index = 0; index < measure.gainers().size(); index++) {
            int position = measure.gainers().get(index);
            Money before = buckets.get(position).gain().gain();
            Money share = index == measure.gainers().size() - 1 ? exempt.minus(shared)
                    : exempt.scaledBy(before, measure.gainOfGainers());
            shared = shared.plus(share);
            chargeable.set(position, before.minus(share));
            lines.add(ctx.traceLine(lines.size() + 1, reached.get(position).id(), StepKind.EXEMPTION, before,
                    before.minus(share)).withLimitedBy(reason));
        }
    }

    /** Why the line reads as it does, where the house is inside the window. */
    private static String reasonOf(ExemptReinvestment definition, HouseReinvestment claim, Measure measure) {
        String buckets = "the " + measure.size() + " buckets transferred on " + claim.transferDate();
        if (!measure.hasGain()) {
            return buckets + " show no gain together (" + measure.gain() + "), so nothing is exempt";
        }
        String basis = basisOf(definition, claim, measure.netConsideration());
        return measure.size() == 1 ? basis : basis + "; measured with " + buckets + ": gain " + measure.gain()
                + " against the net consideration " + measure.netConsideration();
    }

    /**
     * The buckets one claim reached, taken as one sale: their gain and net consideration summed, and the buckets with a
     * gain, in order, with the sum of their gains — the base the exempt amount is shared on.
     */
    private record Measure(int size, Money gain, Money netConsideration, List<Integer> gainers, Money gainOfGainers) {

        static Measure of(Set<Integer> positions, List<BucketGain> buckets) {
            Money gain = Money.ZERO;
            Money netConsideration = Money.ZERO;
            Money gainOfGainers = Money.ZERO;
            List<Integer> gainers = new ArrayList<>();
            for (int position : positions) {
                CapitalGainsCharges.Gain measured = buckets.get(position).gain();
                gain = gain.plus(measured.gain());
                netConsideration = netConsideration.plus(measured.netConsideration());
                if (measured.gain().isGreaterThan(Money.ZERO)) {
                    gainers.add(position);
                    gainOfGainers = gainOfGainers.plus(measured.gain());
                }
            }
            return new Measure(positions.size(), gain, netConsideration, List.copyOf(gainers), gainOfGainers);
        }

        boolean hasGain() {
            return gain.isGreaterThan(Money.ZERO);
        }
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
