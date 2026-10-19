package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.GiftExclusion;
import com.thiru.wealthlens.taxengine.input.GiftKind;
import com.thiru.wealthlens.taxengine.input.GiftReceived;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.definition.GiftThreshold;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import java.util.List;
import java.util.Set;

/**
 * Gifts as income from other sources (S2 s.92(2)(m), (3); 1961 s.56(2)(x); D-201) — a part of
 * {@link OtherSourcesNode}, kept apart because it is the head's one concern with a threshold of its own and grounds of
 * exclusion (D-270).
 *
 * <p>Sums and property received without, or for inadequate, consideration are income above the threshold: the year's
 * sums together, the year's other property received free together, each immovable property and each other purchase
 * below value on its own; a receipt on an excluded ground counts toward nothing.
 */
final class GiftIncome {

    private static final BaseId GIFT = new BaseId("GIFT");
    private static final Set<GiftExclusion> COVID_GROUNDS = Set.of(GiftExclusion.COVID_TREATMENT,
            GiftExclusion.COVID_DEATH_FROM_EMPLOYER, GiftExclusion.COVID_DEATH_FROM_OTHERS);

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
        for (int position = 0; position < gifts.size(); position++) {
            GiftReceived gift = gifts.get(position);
            String where = "gift " + position;
            if (gift.excludedAs().isPresent()) {
                GiftExclusion ground = gift.excludedAs().get();
                requireGroundOfTheYear(ctx, where, gift, ground);
                if (ground == GiftExclusion.COVID_DEATH_FROM_OTHERS) {
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
        sums = sums.plus(covidDeathSumsBeyondTheLimit(lines, covidDeathSums));
        charged = charged.plus(chargedGift(lines, "sums of money received without consideration", sums,
                threshold.isExceededBy(sums) ? sums : Money.ZERO));
        return charged.plus(chargedGift(lines, "other property received without consideration", propertyFree,
                threshold.isExceededBy(propertyFree) ? propertyFree : Money.ZERO));
    }

    /** 1961 s.56(2)(x) proviso (XIII)(B): such sums are excluded only up to the limit; the rest are sums like any. */
    private static Money covidDeathSumsBeyondTheLimit(OtherSourcesLines lines, Money covidDeathSums) {
        if (covidDeathSums.equals(Money.ZERO)) {
            return Money.ZERO;
        }
        RuleId row = lines.optionalRow(RuleSetIds.RS_OS_GIFT_COVID_DEATH).orElseThrow(() -> new IllegalStateException(
                "Sums excluded on a COVID-19 ground reached " + RuleSetIds.RS_OS_GIFT_COVID_DEATH + ", which the year does not"
                        + " hold; requireGroundOfTheYear refuses such a gift first"));
        Money excluded = Money.min(covidDeathSums, lines.applied(row, GIFT, covidDeathSums));
        lines.add(row, StepKind.EXEMPTION, covidDeathSums, excluded,
                "sums to the family of a person whose death was related to COVID-19, excluded up to the limit");
        return covidDeathSums.minus(excluded);
    }

    /**
     * The COVID-19 grounds are the 1961 proviso's (XII), (XIII), which the 2025 Act's s.92(3) does not have; they exist
     * where the pack carries {@code OS-GIFT-COVID-DEATH}, the one row FA 2022's insertion needs a number for. (XIII)(B)
     * reaches sums only.
     */
    private static void requireGroundOfTheYear(NodeContext ctx, String where, GiftReceived gift, GiftExclusion ground) {
        if (!COVID_GROUNDS.contains(ground)) {
            return;
        }
        if (ctx.optionalRuleOf(RuleSetIds.RS_OS_GIFT_COVID_DEATH).isEmpty()) {
            throw new IllegalArgumentException(where + ": " + ground + " is a ground of the Income-tax Act, 1961 s.56(2)(x)"
                    + " proviso (XII), (XIII) only; the Income-tax Act, 2025 s.92(3) has no such exclusion");
        }
        if (ground == GiftExclusion.COVID_DEATH_FROM_OTHERS && gift.kind() != GiftKind.SUM_OF_MONEY) {
            throw new IllegalArgumentException(where + ": proviso (XIII)(B) excludes sums of money only, not "
                    + gift.kind());
        }
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
