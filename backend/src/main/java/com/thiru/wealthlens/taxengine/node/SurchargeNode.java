package com.thiru.wealthlens.taxengine.node;

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
 * <p><b>No split yet.</b> The graph carries no dividend income ({@code W-079}) and no special-rate tax
 * ({@code W-088}), so the excluding income is the total income and the whole tax takes the one rate. Tax
 * at a special rate is refused rather than surcharged as if it were not there: {@code RATE-SUR-SPLIT-001}
 * limits its surcharge to 15%, and that is {@code W-088}'s.
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
        return ROWS.stream().flatMap(SurchargeRows::all).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Money totalIncome = ctx.get(NodeIds.ROUND_TOTALINCOME, MoneyValue.class).amount();
        TaxByBasis afterRebate = ctx.get(NodeIds.TAX_AFTERREBATE, TaxByBasis.class);
        refuseSpecialRateTax(afterRebate);
        SurchargeRows rows = rowsFor(ctx);

        Money tax = afterRebate.asMoney();
        ChosenRate rate = rateAt(ctx, rows, totalIncome);
        Money surcharge = rate.rate().of(tax);
        RuleVersion ceiling = version(ctx, rows.ceiling());
        ReliefResult relief = ceilingOf(rows.ceiling(), ceiling).shape().apply(new ReliefContext(
                totalIncome, tax.plus(surcharge), Optional.of(income -> unrelievedAt(ctx, rows, income))));
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
     * The greater of the two ladders' rates at this income. The excluding income is the total income while
     * the graph carries no dividends or special gains (D-086); where both rates are equal the total-income
     * ladder is cited, its clauses coming first in the Act.
     */
    private static ChosenRate rateAt(NodeContext ctx, SurchargeRows rows, Money totalIncome) {
        RuleVersion onTotal = version(ctx, rows.onTotalIncome());
        RuleVersion excluding = version(ctx, rows.onIncomeExcludingGains());
        Rate totalRate = bandRate(rows.onTotalIncome(), onTotal, totalIncome);
        Rate excludingRate = bandRate(rows.onIncomeExcludingGains(), excluding, totalIncome);
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

    /** U: the real tax after rebate at this income, recomputed, plus its surcharge without relief. */
    private static Money unrelievedAt(NodeContext ctx, SurchargeRows rows, Money income) {
        TaxByBasis atIncome = (TaxByBasis) ctx.recomputed(
                NodeIds.TAX_AFTERREBATE, NodeIds.ROUND_TOTALINCOME, MoneyValue.of(income));
        Money tax = atIncome.asMoney();
        return tax.plus(rateAt(ctx, rows, income).rate().of(tax));
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
