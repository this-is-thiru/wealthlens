package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.node.OtherSourcesNode.DividendInterest;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.cap.SlabLadder;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.relief.ReliefContext;
import com.thiru.wealthlens.taxengine.rules.relief.ReliefResult;
import com.thiru.wealthlens.taxengine.rules.relief.RuleRelief;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
 * <p><b>Dividends out of the 25%/37% test.</b> The excluding ladder reads the total income less the dividend income
 * it includes — dividends and deemed dividends (S2 s.2(40)), less the interest the head allows against them; Mutual
 * Fund unit income is not dividend and stays in (D-200). Where that leaves the ladder unbitten, the whole tax takes
 * the one rate and the 15% limit on the dividends' tax cannot bind. Where the ladder still bites with dividends
 * present, the case is refused: the limit needs the tax "computed in respect of" slab-taxed dividends, which no text
 * read measures ({@code OQ-26}; {@code W-088}). Tax at a special rate is refused rather than surcharged as if it
 * were not there: {@code RATE-SUR-SPLIT-001} limits its surcharge to 15%, and that is {@code W-088}'s.
 */
public final class SurchargeNode implements ComputationNode {

    /** One regime's rows: the two ladders the Act tests (D-084) and the ceiling on both (D-085). */
    private record SurchargeRows(RuleId onTotalIncome, RuleId onIncomeExcludingGains, RuleId ceiling) {

        Stream<RuleId> all() {
            return Stream.of(onTotalIncome, onIncomeExcludingGains, ceiling);
        }
    }

    /** The rate a ladder set, the ladder that set it, and the ladder it beat, if the other was lower. */
    private record ChosenRate(Rate rate, RuleId rule, RuleVersion version, Optional<String> over) {
    }

    private static final Set<OtherSourceKind> DIVIDEND_KINDS = Set.of(OtherSourceKind.DIVIDEND,
            OtherSourceKind.DEEMED_DIVIDEND);

    private static final List<SurchargeRows> ROWS = List.of(
            new SurchargeRows(RuleIds.RATE_SUR_NEW_TAX_REGIME, RuleIds.RATE_SUR_NEW_TAX_REGIME_EXCLUDING,
                    RuleIds.RATE_SUR_NEW_TAX_REGIME_CEILING),
            new SurchargeRows(RuleIds.RATE_SUR_FIRST_SCHEDULE, RuleIds.RATE_SUR_FIRST_SCHEDULE_EXCLUDING,
                    RuleIds.RATE_SUR_FIRST_SCHEDULE_CEILING));

    @Override
    public NodeId produces() {
        return NodeIds.TAX_SURCHARGE;
    }

    /** The total income as well as the tax: it picks the band, and it is the slot U is recomputed at. */
    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.TAX_AFTERREBATE, NodeIds.ROUND_TOTALINCOME);
    }

    @Override
    public Set<RuleId> rules() {
        return Stream.concat(ROWS.stream().flatMap(SurchargeRows::all), Stream.of(RuleIds.OS_DIVIDEND_INTEREST))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Money totalIncome = ctx.get(NodeIds.ROUND_TOTALINCOME, MoneyValue.class).amount();
        TaxByBasis afterRebate = ctx.get(NodeIds.TAX_AFTERREBATE, TaxByBasis.class);
        refuseSpecialRateTax(afterRebate);
        SurchargeRows rows = rowsFor(ctx);
        Money dividends = excludingRate(ctx, rows, totalIncome, Money.ZERO).compareTo(Rate.ZERO) > 0
                ? dividendIncome(ctx)
                : Money.ZERO;
        refuseWhereTheDividendLimitWouldBind(ctx, rows, totalIncome, dividends);

        Money tax = afterRebate.asMoney();
        ChosenRate rate = rateAt(ctx, rows, totalIncome, dividends);
        Money surcharge = rate.rate().of(tax);
        RuleVersion ceiling = version(ctx, rows.ceiling());
        ReliefResult relief = ceilingOf(rows.ceiling(), ceiling).shape().apply(new ReliefContext(
                totalIncome, tax.plus(surcharge), Optional.of(income -> unrelievedAt(ctx, rows, income, dividends))));
        if (relief.amount().isGreaterThan(surcharge)) {
            throw new IllegalStateException("Marginal relief of " + relief.amount() + " exceeds the surcharge of "
                    + surcharge + " at " + totalIncome + "; " + rows.ceiling() + " and the ladders disagree");
        }

        TraceLine charged = TraceLine.of(1, produces(), rate.rule(), StepKind.TAX, MoneyValue.of(tax),
                MoneyValue.of(surcharge), rate.version().provenance());
        TraceLine relieved = TraceLine.of(2, produces(), rows.ceiling(), StepKind.RELIEF,
                        MoneyValue.of(tax.plus(surcharge)), MoneyValue.of(relief.amount()), ceiling.provenance())
                .withLimitedBy(relief.reason());
        return new NodeResult(MoneyValue.of(surcharge.minus(relief.amount())),
                List.of(rate.over().map(charged::withLimitedBy).orElse(charged), relieved));
    }

    private static void refuseSpecialRateTax(TaxByBasis afterRebate) {
        if (afterRebate.totalAtSpecialRates().isGreaterThan(Money.ZERO)) {
            throw new IllegalStateException("The tax carries " + afterRebate.totalAtSpecialRates()
                    + " at special rates; its surcharge is limited to 15% (RATE-SUR-SPLIT-001) and that split,"
                    + " with the special gains taken out of the 25%/37% test, is W-088's (D-086)");
        }
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
     * The dividends' part of the interest allowed. It is determined unless the year's limit binds above nil over
     * interest claimed against both dividends and unit income, where no text says how the allowance divides (OQ-26).
     */
    private static Money interestAllowedOnDividends(DividendInterest interest) {
        Money allowed = interest.allowed();
        if (interest.claimedOnDividends().equals(Money.ZERO) || allowed.equals(Money.ZERO)) {
            return Money.ZERO;
        }
        if (allowed.equals(interest.claimed())) {
            return interest.claimedOnDividends();
        }
        if (interest.claimedOnDividends().equals(interest.claimed())) {
            return allowed;
        }
        throw new IllegalArgumentException("Interest expense of " + interest.claimed() + " against dividend and unit"
                + " income is limited to " + allowed + "; what part of that is the dividends' — and so how much"
                + " dividend income the 25%/37% surcharge test leaves out — is OQ-26");
    }

    /**
     * With dividends in the total income and the excluding ladder still biting without them, the dividends' own tax
     * bears at most 15% (the last clause of each surcharge table); measuring that tax is OQ-26, and building it W-088's.
     */
    private static void refuseWhereTheDividendLimitWouldBind(NodeContext ctx, SurchargeRows rows, Money totalIncome,
            Money dividends) {
        if (dividends.equals(Money.ZERO)) {
            return;
        }
        Rate excluding = excludingRate(ctx, rows, totalIncome, dividends);
        if (excluding.compareTo(Rate.ZERO) > 0) {
            throw new IllegalArgumentException("A total income of " + totalIncome + " less dividend income of "
                    + dividends + " still reaches the " + excluding.asPercentLabel() + " surcharge band of "
                    + rows.onIncomeExcludingGains() + "; the surcharge on the dividends' own tax is limited to 15%, and"
                    + " how that tax is measured for slab-taxed dividends is OQ-26, to be built by W-088 (D-200)");
        }
    }

    /** The excluding ladder's rate at the income less the dividend income it includes, never below nil. */
    private static Rate excludingRate(NodeContext ctx, SurchargeRows rows, Money income, Money dividends) {
        return bandRate(rows.onIncomeExcludingGains(), version(ctx, rows.onIncomeExcludingGains()),
                excludingDividends(income, dividends));
    }

    private static Money excludingDividends(Money income, Money dividends) {
        Money excluding = income.minus(dividends);
        return excluding.isLessThan(Money.ZERO) ? Money.ZERO : excluding;
    }

    /** The regime's rows: exactly one set applies, all three of its rows in this regime. */
    private static SurchargeRows rowsFor(NodeContext ctx) {
        List<SurchargeRows> applying = ROWS.stream()
                .filter(rows -> rows.all().allMatch(id -> ctx.inRegime(id, version(ctx, id)).applies()))
                .toList();
        if (applying.size() != 1) {
            throw new IllegalStateException("Regime " + ctx.regime().code() + " has " + applying.size()
                    + " complete sets of surcharge rows; exactly one must apply: " + applying);
        }
        return applying.getFirst();
    }

    /**
     * The greater of the two ladders' rates at this income: the total-income ladder at the total income, the
     * excluding ladder at the total income less the dividend income (D-086, D-200). Where both rates are equal the
     * total-income ladder is cited, its clauses coming first in the Act.
     */
    private static ChosenRate rateAt(NodeContext ctx, SurchargeRows rows, Money totalIncome, Money dividends) {
        RuleVersion onTotal = version(ctx, rows.onTotalIncome());
        RuleVersion excluding = version(ctx, rows.onIncomeExcludingGains());
        Rate totalRate = bandRate(rows.onTotalIncome(), onTotal, totalIncome);
        Rate excludingRate = bandRate(rows.onIncomeExcludingGains(), excluding,
                excludingDividends(totalIncome, dividends));
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
     * held as it is, so the excluding ladder reads the threshold less it.
     */
    private static Money unrelievedAt(NodeContext ctx, SurchargeRows rows, Money income, Money dividends) {
        TaxByBasis atIncome = (TaxByBasis) ctx.recomputed(
                NodeIds.TAX_AFTERREBATE, NodeIds.ROUND_TOTALINCOME, MoneyValue.of(income));
        Money tax = atIncome.asMoney();
        return tax.plus(rateAt(ctx, rows, income, dividends).rate().of(tax));
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
