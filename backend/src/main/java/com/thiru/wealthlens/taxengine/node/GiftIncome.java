package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.GiftExclusion;
import com.thiru.wealthlens.taxengine.input.GiftReceived;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.GiftThreshold;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gifts as income from other sources (S2 s.92(2)(m), (3); 1961 s.56(2)(x); D-201) — a part of
 * {@link OtherSourcesNode}, kept apart because it is the head's one concern with a threshold of its own and grounds of
 * exclusion (D-270).
 *
 * <p>Sums and property received without, or for inadequate, consideration are income above the threshold: the year's
 * sums together, the year's other property received free together, each immovable property and each other purchase
 * below value on its own; a receipt on an excluded ground counts toward nothing. Which grounds the year has, and
 * which of them is limited, are rows' conditions on the ground and the kind (W-156f, D-351).
 */
final class GiftIncome {

    private static final BaseId GIFT = new BaseId("GIFT");

    /** The ground claimed — the {@code GiftExclusion} name — which the exclusion rows read (W-156f, D-351). */
    private static final ItemFactId EXCLUSION = new ItemFactId("gift.exclusion");

    /** What was received — the {@code GiftKind} name — for a ground that reaches only some kinds (W-156f, D-351). */
    private static final ItemFactId KIND = new ItemFactId("gift.kind");

    private GiftIncome() {
    }

    /**
     * S2 s.92(2)(m), (3); 1961 s.56(2)(x) and its proviso: the year's gifts. The sums received are tested together,
     * as is the other property received free ("the total of which", "the aggregate fair market value"); each
     * immovable property, and each other property bought below its value, is tested on its own.
     */
    static Money income(NodeContext ctx, OtherSourcesLines lines, List<GiftReceived> gifts) {
        GiftThreshold threshold = threshold(lines);
        Money sums = Money.ZERO;
        Money propertyFree = Money.ZERO;
        Money covidDeathSums = Money.ZERO;
        Money charged = Money.ZERO;
        Optional<RuleId> covidDeathRow = lines.optionalRow(RuleSetIds.RS_OS_GIFT_COVID_DEATH);
        for (int position = 0; position < gifts.size(); position++) {
            GiftReceived gift = gifts.get(position);
            String where = "gift " + position;
            if (gift.excludedAs().isPresent()) {
                GiftExclusion ground = gift.excludedAs().get();
                ItemFacts facts = factsOf(gift, ground);
                requireGroundOfTheYear(ctx, where, gift, facts);
                if (covidDeathRow.filter(row -> RowReach.holdsFor(ctx, ctx.rule(row), facts)).isPresent()) {
                    covidDeathSums = covidDeathSums.plus(gift.value());
                } else {
                    lines.add(ctx.ruleIdOf(RuleSetIds.RS_OS_GIFT), StepKind.EXEMPTION, gift.value(), gift.value(),
                            where + ": excluded, " + ground);
                }
                continue;
            }
            switch (gift.kind()) {
                case SUM_OF_MONEY -> sums = sums.plus(gift.value());
                case IMMOVABLE_PROPERTY -> charged = charged.plus(chargedGift(lines, where + ": immovable property",
                        gift.value(), threshold.immovablePropertyCharged(gift.consideration(), gift.value())));
                case OTHER_PROPERTY -> {
                    if (gift.consideration().equals(Money.ZERO)) {
                        propertyFree = propertyFree.plus(gift.value());
                    } else {
                        charged = charged.plus(chargedGift(lines, where + ": property bought below its value",
                                gift.value(), threshold.propertyShortfallCharged(gift.consideration(), gift.value())));
                    }
                }
            }
        }
        if (covidDeathRow.isPresent()) {
            sums = sums.plus(covidDeathSumsBeyondTheLimit(lines, covidDeathRow.get(), covidDeathSums));
        }
        charged = charged.plus(chargedGift(lines, "sums of money received without consideration", sums,
                threshold.isExceededBy(sums) ? sums : Money.ZERO));
        return charged.plus(chargedGift(lines, "other property received without consideration", propertyFree,
                threshold.isExceededBy(propertyFree) ? propertyFree : Money.ZERO));
    }

    /** 1961 s.56(2)(x) proviso (XIII)(B): such sums are excluded only up to the limit; the rest are sums like any. */
    private static Money covidDeathSumsBeyondTheLimit(OtherSourcesLines lines, RuleId row, Money covidDeathSums) {
        if (covidDeathSums.equals(Money.ZERO)) {
            return Money.ZERO;
        }
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

    private static ItemFacts factsOf(GiftReceived gift, GiftExclusion ground) {
        return new ItemFacts(Map.of(EXCLUSION, ground.name(), KIND, gift.kind().name()), Map.of(), Map.of());
    }

    private static GiftThreshold threshold(OtherSourcesLines lines) {
        RuleId row = lines.row(RuleSetIds.RS_OS_GIFT);
        return RuleDefinitions.require(lines.version(row).definition(), GiftThreshold.class, row);
    }

    /** One INCOME line for a gift or a total of gifts: what was received, what of it is charged. */
    private static Money chargedGift(OtherSourcesLines lines, String what, Money received, Money chargedAmount) {
        if (received.isGreaterThan(Money.ZERO)) {
            lines.add(lines.row(RuleSetIds.RS_OS_GIFT), StepKind.INCOME, received, chargedAmount, what);
        }
        return chargedAmount;
    }
}
