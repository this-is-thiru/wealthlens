package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.node.OtherSourcesNode.DividendInterest;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBase;
import com.thiru.wealthlens.taxengine.rules.cap.SlabLadder;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.relief.ReliefContext;
import com.thiru.wealthlens.taxengine.rules.relief.ReliefResult;
import com.thiru.wealthlens.taxengine.rules.relief.RuleRelief;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The surcharge on the tax after rebate, less its marginal relief (FA 2026 s.2(4)/(5), s.3(4)/(5), First
 * Schedule Parts I-A and I-B Paragraph F; D-084, D-085, D-086).
 *
 * <p><b>One rate on the whole tax.</b> A surcharge band's rate is not marginal: the band the income falls in
 * sets it. The Act tests two incomes — the 10% and 15% clauses the total income, the 25% and 37% clauses the
 * total income excluding dividends and the special gains — and its clauses compose as the greater of the
 * two ladders' rates (D-084).
 *
 * <p><b>Relief by re-entry.</b> W = U + V caps tax and surcharge at the tax and surcharge at the threshold
 * (U) plus the income above it (V). U is the real engine's tax after rebate at the threshold, recomputed
 * ({@code DR 06} G3.2, D-086), plus the surcharge on it without relief.
 *
 * <p><b>Dividends and the special gains out of the 25%/37% test.</b> The excluding ladder reads the total income
 * less the dividend income it includes — dividends and deemed dividends (S2 s.2(40)), less the interest the head allows
 * against them; Mutual Fund unit income is not dividend and stays in (D-200, D-245) — and less the capital gains
 * charged at special rates ({@code setoff}'s parts; D-246).
 *
 * <p><b>The 15% limit on that part's tax.</b> The tax "in respect of that part of income" — the tax at the special
 * rates, and the dividends' share of the slab tax, pro rata to their share of the aggregate income (D-229) — bears the
 * total-income ladder's rate, "clause (i) or (ii)", never more than the {@code -LIMIT} row's fifteen per cent; the rest
 * of the tax bears the chosen rate (FA 2026 s.2(4)(b), s.3(4)(b) Table Sl. 10 (v); First Schedule Para F Table 1
 * Sl. 1 (vi); D-246).
 */
public final class SurchargeNode implements ComputationNode {

    /**
     * One regime's rows: the two ladders the Act tests (D-084), the ceiling on both (D-085) and the limit on the
     * surcharge of the dividends' and special gains' tax (D-246).
     */
    private record SurchargeRows(RuleId onTotalIncome, RuleId onIncomeExcludingGains, RuleId ceiling, RuleId limit) {
    }

    /**
     * The tax split for the surcharge: the part "in respect of" dividends and the special gains, and its two shares.
     *
     * @param tax the whole tax after rebate
     * @param dividendShare the dividends' share of the slab tax (D-229)
     * @param specialRates the tax at the special rates
     */
    private record LimitedPart(Money tax, Money dividendShare, Money specialRates) {

        Money limited() {
            return dividendShare.plus(specialRates);
        }

        Money rest() {
            return tax.minus(limited());
        }
    }

    /** The surcharge on the rest of the tax at the chosen rate, and on the limited part within the limit. */
    private record Charged(Money onRest, Money onLimited) {

        Money total() {
            return onRest.plus(onLimited);
        }
    }

    private static final BaseId LIMITED_PART_TAX = new BaseId("LIMITED_PART_TAX");

    /** The rate a ladder set, the ladder that set it, and the ladder it beat, if the other was lower. */
    private record ChosenRate(Rate rate, RuleId rule, RuleVersion version, Optional<String> over) {
    }

    private static final Set<OtherSourceKind> DIVIDEND_KINDS = Set.of(OtherSourceKind.DIVIDEND,
            OtherSourceKind.DEEMED_DIVIDEND);

    @Override
    public NodeId produces() {
        return NodeIds.TAX_SURCHARGE;
    }

    /**
     * The total income as well as the tax: it picks the band, and it is the slot U is recomputed at; the heads after
     * set-off for the special-rate gains the excluding income leaves out.
     */
    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.TAX_AFTERREBATE, NodeIds.ROUND_TOTALINCOME, NodeIds.SETOFF);
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of(RuleSetIds.RS_RATE_SUR, RuleSetIds.RS_RATE_SUR_EXCLUDING, RuleSetIds.RS_RATE_SUR_CEILING,
                RuleSetIds.RS_RATE_SUR_LIMIT, RuleSetIds.RS_OS_DIVIDEND_INTEREST);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Money totalIncome = ctx.get(NodeIds.ROUND_TOTALINCOME, MoneyValue.class).amount();
        TaxByBasis afterRebate = ctx.get(NodeIds.TAX_AFTERREBATE, TaxByBasis.class);
        Money specialGains = TaxByBasisNode.atSpecialRates(ctx.get(NodeIds.SETOFF, HeadsAfterSetOff.class));
        SurchargeRows rows = rowsFor(ctx);
        Money dividends = excludingRate(ctx, rows, totalIncome, specialGains).compareTo(Rate.ZERO) > 0
                ? dividendIncome(ctx)
                : Money.ZERO;
        Money excluded = dividends.plus(specialGains);

        LimitedPart part = limitedPart(afterRebate, dividends, totalIncome.minus(specialGains));
        ChosenRate rate = rateAt(ctx, rows, totalIncome, excluded);
        Charged surcharge = charge(ctx, rows, totalIncome, rate, part);
        Money tax = afterRebate.asMoney();
        RuleVersion ceiling = version(ctx, rows.ceiling());
        ReliefResult relief = ceilingOf(rows.ceiling(), ceiling).shape().apply(new ReliefContext(totalIncome,
                tax.plus(surcharge.total()), Optional.of(income -> unrelievedAt(ctx, rows, income, dividends,
                        specialGains))));
        if (relief.amount().isGreaterThan(surcharge.total())) {
            throw new IllegalStateException("Marginal relief of " + relief.amount() + " exceeds the surcharge of "
                    + surcharge.total() + " at " + totalIncome + "; " + rows.ceiling() + " and the ladders disagree");
        }
        List<TraceLine> lines = new ArrayList<>();
        TraceLine charged = TraceLine.of(1, produces(), rate.rule(), StepKind.TAX, MoneyValue.of(part.rest()),
                MoneyValue.of(surcharge.onRest()), rate.version().provenance());
        lines.add(rate.over().map(charged::withLimitedBy).orElse(charged));
        if (part.limited().isGreaterThan(Money.ZERO)) {
            lines.add(TraceLine.of(2, produces(), rows.limit(), StepKind.TAX, MoneyValue.of(part.limited()),
                            MoneyValue.of(surcharge.onLimited()), version(ctx, rows.limit()).provenance())
                    .withLimitedBy("the tax in respect of dividends (" + part.dividendShare() + ", their share of the"
                            + " slab tax by income, D-229) and of the special-rate gains (" + part.specialRates()
                            + "), at " + rows.onTotalIncome() + "'s rate, at most the limit"));
        }
        lines.add(TraceLine.of(lines.size() + 1, produces(), rows.ceiling(), StepKind.RELIEF,
                        MoneyValue.of(tax.plus(surcharge.total())), MoneyValue.of(relief.amount()), ceiling.provenance())
                .withLimitedBy(relief.reason()));
        return new NodeResult(MoneyValue.of(surcharge.total().minus(relief.amount())), lines);
    }

    /**
     * The tax in respect of dividends and the special gains: the tax at the special rates, and the dividends' share of
     * the slab tax, pro rata to their share of the aggregate income — total income less the income at special rates
     * (ITR-2 Part B-TI row 15; the Department's utility, D-229 C1).
     */
    private static LimitedPart limitedPart(TaxByBasis afterRebate, Money dividends, Money aggregateIncome) {
        Money share = dividends.isGreaterThan(Money.ZERO) && aggregateIncome.isGreaterThan(Money.ZERO)
                ? afterRebate.atSlabRates().scaledBy(dividends, aggregateIncome)
                : Money.ZERO;
        return new LimitedPart(afterRebate.asMoney(), share, afterRebate.totalAtSpecialRates());
    }

    /**
     * The chosen rate on the rest of the tax; on the limited part the total-income ladder's rate ("the provisions of
     * clause (i) or (ii) … shall apply accordingly"), never more than the limit row's. Each part is rounded to paise
     * once; where both bear one rate the whole tax is charged at it, so a split that changes nothing changes no paisa.
     */
    private static Charged charge(NodeContext ctx, SurchargeRows rows, Money totalIncome, ChosenRate rate,
            LimitedPart part) {
        Rate totalRate = bandRate(rows.onTotalIncome(), version(ctx, rows.onTotalIncome()), totalIncome);
        Rate limitedRate = totalRate.compareTo(limitOf(ctx, rows)) > 0 ? limitOf(ctx, rows) : totalRate;
        Money onLimited = limitedRate.of(part.limited());
        Money onRest = limitedRate.compareTo(rate.rate()) == 0
                ? rate.rate().of(part.tax()).minus(onLimited)
                : rate.rate().of(part.rest());
        return new Charged(onRest, onLimited);
    }

    /** The limit row's rate: its cap is a percentage of the limited part's tax. */
    private static Rate limitOf(NodeContext ctx, SurchargeRows rows) {
        if (!(version(ctx, rows.limit()).cap() instanceof PercentOfBase limit)
                || !limit.base().equals(LIMITED_PART_TAX)) {
            throw new IllegalStateException("Surcharge limit " + rows.limit() + " is not a percentage of "
                    + LIMITED_PART_TAX.value());
        }
        return limit.percent();
    }

    /**
     * The dividend income the total income includes (S2 s.2(40); D-200): dividends and deemed dividends received, less
     * the interest the head allows against the dividend items. Unit income is not dividend.
     */
    private static Money dividendIncome(NodeContext ctx) {
        List<OtherSourceItem> items = ctx.inputs().entered().otherSourceItems();
        Money received = items.stream()
                .filter(item -> DIVIDEND_KINDS.contains(item.kind()))
                .map(OtherSourceItem::received)
                .reduce(Money.ZERO, Money::plus);
        if (received.equals(Money.ZERO)) {
            return Money.ZERO;
        }
        return received.minus(interestAllowedOnDividends(OtherSourcesNode.dividendInterest(ctx, items)));
    }

    /**
     * The dividends' part of the interest allowed: in proportion to the dividends' share of the dividend and unit
     * income (D-229), but never more than was claimed against the dividends nor less than the units' claim leaves
     * them, since the head deducts no item more than its own expense (D-234). Where the limit does not bind each side
     * keeps its own claim; where only one side claimed, it takes all that is allowed.
     */
    private static Money interestAllowedOnDividends(DividendInterest interest) {
        Money allowed = interest.allowed();
        if (allowed.equals(Money.ZERO)) {
            return Money.ZERO;
        }
        Money proportional = allowed.scaledBy(interest.receivedOnDividends(), interest.base());
        Money claimedOnUnits = interest.claimed().minus(interest.claimedOnDividends());
        return Money.min(interest.claimedOnDividends(), Money.max(allowed.minus(claimedOnUnits), proportional));
    }

    /** The excluding ladder's rate at the income less what it excludes, never below nil. */
    private static Rate excludingRate(NodeContext ctx, SurchargeRows rows, Money income, Money excluded) {
        return bandRate(rows.onIncomeExcludingGains(), version(ctx, rows.onIncomeExcludingGains()),
                excluding(income, excluded));
    }

    private static Money excluding(Money income, Money excluded) {
        return income.minus(excluded).floorAtZero();
    }

    /** The regime's rows, one of each role, as each role's rule set gives them (D-252). */
    private static SurchargeRows rowsFor(NodeContext ctx) {
        return new SurchargeRows(ctx.ruleIdOf(RuleSetIds.RS_RATE_SUR), ctx.ruleIdOf(RuleSetIds.RS_RATE_SUR_EXCLUDING),
                ctx.ruleIdOf(RuleSetIds.RS_RATE_SUR_CEILING), ctx.ruleIdOf(RuleSetIds.RS_RATE_SUR_LIMIT));
    }

    /**
     * The greater of the two ladders' rates at this income: the total-income ladder at the total income, the
     * excluding ladder at the total income less the dividend income and the special gains (D-086, D-200, D-246). Where
     * both rates are equal the total-income ladder is cited, its clauses coming first in the Act.
     */
    private static ChosenRate rateAt(NodeContext ctx, SurchargeRows rows, Money totalIncome, Money excluded) {
        RuleVersion onTotal = version(ctx, rows.onTotalIncome());
        RuleVersion excluding = version(ctx, rows.onIncomeExcludingGains());
        Rate totalRate = bandRate(rows.onTotalIncome(), onTotal, totalIncome);
        Rate excludingRate = bandRate(rows.onIncomeExcludingGains(), excluding, excluding(totalIncome, excluded));
        if (excludingRate.compareTo(totalRate) > 0) {
            return new ChosenRate(excludingRate, rows.onIncomeExcludingGains(), excluding,
                    Optional.of("greater than " + rows.onTotalIncome() + " at " + totalRate.asPercentLabel()));
        }
        Optional<String> over = totalRate.compareTo(excludingRate) > 0
                ? Optional.of("greater than " + rows.onIncomeExcludingGains() + " at " + excludingRate.asPercentLabel())
                : Optional.empty();
        return new ChosenRate(totalRate, rows.onTotalIncome(), onTotal, over);
    }

    /** The rate of the band the income falls in; an income of nil is in no band and bears none. */
    private static Rate bandRate(RuleId id, RuleVersion version, Money income) {
        if (!(version.cap() instanceof SlabLadder ladder)) {
            throw new IllegalStateException("Surcharge rule " + id + " is "
                    + (version.cap() == null ? "not a ladder" : "a " + version.cap().shapeName()) + ", not a slab ladder");
        }
        return ladder.bandFor(income).map(SlabLadder.Band::rate).orElse(Rate.ZERO);
    }

    /**
     * U: the real tax after rebate at this income, recomputed, plus its surcharge without relief — the dividend income
     * and the special gains held as they are, so the excluding ladder reads the threshold less them and the limited
     * part is measured at it.
     */
    private static Money unrelievedAt(NodeContext ctx, SurchargeRows rows, Money income, Money dividends,
            Money specialGains) {
        TaxByBasis atIncome = (TaxByBasis) ctx.recomputed(
                NodeIds.TAX_AFTERREBATE, NodeIds.ROUND_TOTALINCOME, MoneyValue.of(income));
        LimitedPart part = limitedPart(atIncome, dividends, income.minus(specialGains));
        ChosenRate rate = rateAt(ctx, rows, income, dividends.plus(specialGains));
        return atIncome.asMoney().plus(charge(ctx, rows, income, rate, part).total());
    }

    private static RuleRelief ceilingOf(RuleId id, RuleVersion version) {
        RuleRelief relief = version.relief().orElseThrow(() -> new IllegalStateException(
                "Surcharge ceiling " + id + " carries no relief"));
        if (relief.limitedToSlabRateTax()) {
            throw new IllegalStateException("Surcharge ceiling " + id + " is limited to slab-rate tax; the"
                    + " Finance Act's W = U + V bounds income-tax and surcharge, and no such limit is sourced");
        }
        return relief;
    }

    /** The one version governing the year: a ladder's bands cannot be apportioned (D-098). */
    private static RuleVersion version(NodeContext ctx, RuleId id) {
        return TemporallyApportionedCap.governingVersion(ctx.rule(id), ctx.inputs().temporal());
    }
}
