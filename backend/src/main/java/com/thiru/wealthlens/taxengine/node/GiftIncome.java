package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.GiftReceived;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.GiftAggregation;
import com.thiru.wealthlens.taxengine.rules.definition.GiftThreshold;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Gifts as income from other sources (S2 s.92(2)(m), (3); 1961 s.56(2)(x); D-201) — a part of
 * {@link OtherSourcesNode}, kept apart because it is the head's one concern with a threshold of its own and grounds of
 * exclusion (D-270).
 *
 * <p>Sums and property received without, or for inadequate, consideration are income above the threshold of the limb
 * that reaches them, tested as the limb says — the year's total, or each receipt on its own; a receipt on an excluded
 * ground counts toward nothing. Which limb reaches a receipt, its threshold and its measure, which grounds the year
 * has and which of them is limited, are all rows' (W-156f, W-156f2; D-351, D-352).
 */
final class GiftIncome {

    private static final BaseId GIFT = new BaseId("GIFT");

    /** The ground claimed — the {@code GiftExclusion} name — which the exclusion rows read (W-156f, D-351). */
    private static final ItemFactId EXCLUSION = new ItemFactId("gift.exclusion");

    /** What was received — the {@code GiftKind} name — which the limb and exclusion rows read (D-351, D-352). */
    private static final ItemFactId KIND = new ItemFactId("gift.kind");

    /**
     * Whether anything was paid for it — {@code WITHOUT_CONSIDERATION} or {@code FOR_CONSIDERATION}, the Act's words —
     * which the limb rows read (W-156f2, D-352).
     */
    private static final ItemFactId RECEIVED = new ItemFactId("gift.received");

    /** The limbs of the charge, each the lone row of its set, in the Act's order: (i), (ii), (iii)(A), (iii)(B). */
    static final List<RuleSetId> LIMBS = List.of(RuleSetIds.RS_OS_GIFT, RuleSetIds.RS_OS_GIFT_IMMOVABLE,
            RuleSetIds.RS_OS_GIFT_PROPERTY_FREE, RuleSetIds.RS_OS_GIFT_PROPERTY_BOUGHT);

    private GiftIncome() {
    }

    /** What a limb has received: the value and the consideration paid for it, for one receipt or a year's total. */
    private record Received(Money consideration, Money value) {

        Received plus(Received other) {
            return new Received(consideration.plus(other.consideration), value.plus(other.value));
        }
    }

    /**
     * S2 s.92(2)(m), (3); 1961 s.56(2)(x) and its proviso: the year's gifts. Each receipt not excluded goes to the one
     * limb that reaches it; a limb that totals the year ("the total of which", "the aggregate fair market value") is
     * charged on its total, after the receipts, and any other on each receipt as it comes.
     */
    static Money income(NodeContext ctx, OtherSourcesLines lines, List<GiftReceived> gifts) {
        Map<RuleId, Received> totals = new HashMap<>();
        Money covidDeathSums = Money.ZERO;
        Set<RuleId> covidDeathLimbs = new LinkedHashSet<>();
        Money charged = Money.ZERO;
        Optional<RuleId> covidDeathRow = lines.optionalRow(RuleSetIds.RS_OS_GIFT_COVID_DEATH);
        for (int position = 0; position < gifts.size(); position++) {
            GiftReceived gift = gifts.get(position);
            String where = "gift " + position;
            ItemFacts facts = factsOf(gift);
            if (gift.excludedAs().isPresent()) {
                requireGroundOfTheYear(ctx, where, gift, facts);
                if (covidDeathRow.filter(row -> RowReach.holdsFor(ctx, ctx.rule(row), facts)).isPresent()) {
                    covidDeathSums = covidDeathSums.plus(gift.value());
                    covidDeathLimbs.add(limbOf(ctx, where, gift, facts));
                } else {
                    lines.add(ctx.ruleIdOf(RuleSetIds.RS_OS_GIFT_EXCLUSION), StepKind.EXEMPTION, gift.value(),
                            gift.value(), where + ": excluded, " + gift.excludedAs().get());
                }
                continue;
            }
            charged = charged.plus(receive(lines, totals, limbOf(ctx, where, gift, facts), where + ": " + gift.kind(),
                    new Received(gift.consideration(), gift.value())));
        }
        if (covidDeathRow.isPresent() && covidDeathSums.isGreaterThan(Money.ZERO)) {
            Money beyond = covidDeathSumsBeyondTheLimit(lines, covidDeathRow.get(), covidDeathSums);
            charged = charged.plus(receive(lines, totals, onlyLimb(covidDeathLimbs),
                    "sums excluded on a COVID-19 death beyond the limit", new Received(Money.ZERO, beyond)));
        }
        for (RuleSetId limb : LIMBS) {
            RuleId row = ctx.ruleIdOf(limb);
            if (totals.containsKey(row)) {
                Received total = totals.get(row);
                charged = charged.plus(chargedGift(lines, row, "the year's total", total.value(),
                        threshold(lines, row).charged(total.consideration(), total.value())));
            }
        }
        return charged;
    }

    /** A receipt on its limb: added to the year's total where the limb totals the year, else charged on its own. */
    private static Money receive(OtherSourcesLines lines, Map<RuleId, Received> totals, RuleId row, String what,
            Received received) {
        GiftThreshold threshold = threshold(lines, row);
        if (threshold.aggregation() == GiftAggregation.YEAR_TOTAL) {
            totals.merge(row, received, Received::plus);
            return Money.ZERO;
        }
        return chargedGift(lines, row, what, received.value(),
                threshold.charged(received.consideration(), received.value()));
    }

    /**
     * The one limb whose row reaches the receipt (S2 s.92(2)(m)(i)–(iii)). One that no limb reaches, or several do, is
     * refused rather than charged under another, as an item of the head is (D-321).
     */
    private static RuleId limbOf(NodeContext ctx, String where, GiftReceived gift, ItemFacts facts) {
        List<RuleId> reaching = LIMBS.stream().map(ctx::ruleIdOf)
                .filter(row -> RowReach.holdsFor(ctx, ctx.rule(row), facts)).toList();
        if (reaching.size() != 1) {
            throw new IllegalArgumentException(where + ": " + gift.kind() + ", " + facts.code(RECEIVED) + ", is reached"
                    + " by " + reaching + " in TY " + ctx.inputs().taxYear().label() + "; exactly one of the limbs "
                    + LIMBS + " must reach it");
        }
        return reaching.getFirst();
    }

    /** The limb the COVID-19 death sums reach; the exclusion row admits them as sums only, so they reach one. */
    private static RuleId onlyLimb(Set<RuleId> limbs) {
        if (limbs.size() != 1) {
            throw new IllegalStateException("Sums excluded on a COVID-19 death reached the limbs " + limbs
                    + "; OS-GIFT-EXCLUSION admits them as one kind only");
        }
        return limbs.iterator().next();
    }

    /** 1961 s.56(2)(x) proviso (XIII)(B): such sums are excluded only up to the limit; the rest are sums like any. */
    private static Money covidDeathSumsBeyondTheLimit(OtherSourcesLines lines, RuleId row, Money covidDeathSums) {
        Money excluded = Money.min(covidDeathSums, lines.applied(row, GIFT, covidDeathSums));
        lines.add(row, StepKind.EXEMPTION, covidDeathSums, excluded,
                "sums to the family of a person whose death was related to COVID-19, excluded up to the limit");
        return covidDeathSums.minus(excluded);
    }

    /**
     * A claimed ground the year's exclusion row does not reach, for the kind received, is refused rather than charged:
     * the person claimed an exclusion the law of the year does not give.
     */
    private static void requireGroundOfTheYear(NodeContext ctx, String where, GiftReceived gift, ItemFacts facts) {
        RuleId row = ctx.ruleIdOf(RuleSetIds.RS_OS_GIFT_EXCLUSION);
        if (!RowReach.holdsFor(ctx, ctx.rule(row), facts)) {
            throw new IllegalArgumentException(where + ": " + facts.code(EXCLUSION) + ", claimed for " + gift.kind()
                    + ", is not a ground " + row + " has in TY " + ctx.inputs().taxYear().label());
        }
    }

    private static ItemFacts factsOf(GiftReceived gift) {
        Map<ItemFactId, String> codes = new HashMap<>(Map.of(KIND, gift.kind().name(), RECEIVED,
                gift.consideration().equals(Money.ZERO) ? "WITHOUT_CONSIDERATION" : "FOR_CONSIDERATION"));
        gift.excludedAs().ifPresent(ground -> codes.put(EXCLUSION, ground.name()));
        return new ItemFacts(codes, Map.of(), Map.of());
    }

    private static GiftThreshold threshold(OtherSourcesLines lines, RuleId row) {
        return RuleDefinitions.require(lines.version(row).definition(), GiftThreshold.class, row);
    }

    /** One INCOME line on a limb's row for a gift or a year's total: what was received, what of it is charged. */
    private static Money chargedGift(OtherSourcesLines lines, RuleId row, String what, Money received,
            Money chargedAmount) {
        if (received.isGreaterThan(Money.ZERO)) {
            lines.add(row, StepKind.INCOME, received, chargedAmount, what);
        }
        return chargedAmount;
    }
}
