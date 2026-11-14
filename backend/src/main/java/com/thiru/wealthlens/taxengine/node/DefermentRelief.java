package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.CapitalGainsInput;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.FilingFacts;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.definition.DefermentInterest;
import com.thiru.wealthlens.taxengine.rules.definition.DefermentInterest.RelievedIncome;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The relief from deferment interest at one instalment date (2025 s.425(4); 1961 s.234C(1) first proviso; W-112a,
 * D-660): the tax on the relieved incomes that arose after the date is left out of the tax due on the returned income
 * there, where it was paid after the date and by the rule's paid-by day.
 *
 * <p>That tax is the tax due on the returned income less the same tax had those incomes not arisen, the pipeline run again
 * without them, because (b) measures it "had such income been part of total income" and leaving an income out can move
 * the slab tax on the rest. Capital gains carry their transfer date; a dividend reaches the relief only with the date it was
 * received. The other two incomes the section names have no head here, so nothing of theirs is entered to leave out.
 */
final class DefermentRelief {

    /** What s.425(6) and Explanation 2 count as a dividend among the kinds an assessee enters. */
    private static final Set<OtherSourceKind> DIVIDENDS = Set.of(OtherSourceKind.DIVIDEND, OtherSourceKind.DEEMED_DIVIDEND);

    private final NodeContext ctx;

    private final Set<RelievedIncome> relieved;

    private final LocalDate paidBy;

    private final FilingFacts filing;

    private final Money due;

    /**
     * One date's relief.
     *
     * @param leftOut the tax left out of the date's share, nil where none
     * @param reason what the trace line says of it; {@code Optional.empty()} where no relieved income arose after the date
     */
    record AtDate(Money leftOut, Optional<String> reason) {

        static final AtDate NONE = new AtDate(Money.ZERO, Optional.empty());
    }

    DefermentRelief(NodeContext ctx, DefermentInterest rule, Money due) {
        this.ctx = ctx;
        this.relieved = Set.copyOf(rule.relievedIncomes());
        this.paidBy = AdvanceTaxNode.inFinancialYear(ctx, rule.relievedIfPaidBy());
        this.filing = ctx.inputs().filing();
        this.due = due;
    }

    /** The relief at {@code date}: the tax on relieved income arising after it, where (b) holds. */
    AtDate at(LocalDate date) {
        Optional<EngineInputs> without = withoutIncomeArisingAfter(date);
        if (without.isEmpty()) {
            return AtDate.NONE;
        }
        Money dueWithout = ((MoneyValue) ctx.recomputedOn(NodeIds.TAX_NETPAYABLE, without.get())).amount()
                .minus(filing.taxAtSource()).floorAtZero();
        Money leftOut = due.minus(dueWithout).floorAtZero();
        if (!leftOut.isGreaterThan(Money.ZERO)) {
            return AtDate.NONE;
        }
        Money paidAfter = filing.advanceTaxPaidBy(paidBy).minus(filing.advanceTaxPaidBy(date));
        if (paidAfter.isLessThan(leftOut)) {
            return new AtDate(Money.ZERO, Optional.of("not relieved: the " + leftOut + " of tax on income arising after "
                    + date + " was not paid after it by " + paidBy + " (" + paidAfter + " was)"));
        }
        return new AtDate(leftOut, Optional.of("relieved " + leftOut + ": tax on income arising after " + date
                + ", paid after it by " + paidBy));
    }

    /** The day by which the tax on relieved income must be paid, in the year. */
    LocalDate paidBy() {
        return paidBy;
    }

    /** Whether a dividend the relief would reach was entered without the date it was received. */
    boolean hasUndatedDividend() {
        return relieved.contains(RelievedIncome.DIVIDEND) && ctx.inputs().entered().otherSourceItems().stream()
                .anyMatch(item -> DIVIDENDS.contains(item.kind()) && item.receivedOn().isEmpty());
    }

    private Optional<EngineInputs> withoutIncomeArisingAfter(LocalDate date) {
        EngineInputs inputs = ctx.inputs();
        CapitalGainsInput gains = inputs.capitalGains();
        List<CapitalGainsInput.Bucket> buckets = relieved.contains(RelievedIncome.CAPITAL_GAINS)
                ? gains.buckets().stream().filter(bucket -> !bucket.facts().transferredOn().isAfter(date)).toList()
                : gains.buckets();
        List<OtherSourceItem> items = relieved.contains(RelievedIncome.DIVIDEND)
                ? inputs.entered().otherSourceItems().stream().filter(item -> !arisesAfter(item, date)).toList()
                : inputs.entered().otherSourceItems();
        if (buckets.size() == gains.buckets().size() && items.size() == inputs.entered().otherSourceItems().size()) {
            return Optional.empty();
        }
        return Optional.of(new EngineInputs(inputs.taxYear(), inputs.regime(), inputs.temporal(),
                inputs.entered().withOtherSourceItems(items), inputs.assessee(), inputs.family(), inputs.ledgers(),
                inputs.elections(), new CapitalGainsInput(buckets, gains.asOf(), gains.portfolioComputationRef()), filing));
    }

    private static boolean arisesAfter(OtherSourceItem item, LocalDate date) {
        return DIVIDENDS.contains(item.kind()) && item.receivedOn().filter(on -> on.isAfter(date)).isPresent();
    }
}
