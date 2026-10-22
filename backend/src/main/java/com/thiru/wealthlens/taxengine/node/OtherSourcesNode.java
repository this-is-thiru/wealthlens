package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.BusinessTrustUnits;
import com.thiru.wealthlens.taxengine.input.GiftReceived;
import com.thiru.wealthlens.taxengine.input.LifePolicyReceipt;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.input.ProvidentFundAccount;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapShape;
import com.thiru.wealthlens.taxengine.rules.cap.PercentOfBase;
import com.thiru.wealthlens.taxengine.rules.condition.Condition;
import com.thiru.wealthlens.taxengine.rules.condition.ConditionContext;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Income from other sources (S2 ss.92–94; 1961 ss.56–58; {@code DR 01} R17; D-194): interest, dividends, Mutual Fund
 * unit income and interest on compensation, each less what the law lets the assessee deduct against it in the year.
 *
 * <p>Interest bears the banker's commission (on securities only) and other expenditure laid out wholly and exclusively
 * for it. Dividends and unit income bear nothing but interest expense, and that only up to twenty per cent of the
 * year's dividend and unit income together ({@link DividendInterestAllowance}) — the proviso caps the year's
 * deduction, not each item's — until TY
 * 2026-27, when nothing at all. A deemed dividend bears nothing in either year; interest on compensation bears half of
 * itself and nothing else. An expense the law does not allow is a nil line naming why, never silently left out.
 *
 * <p>A family pension bears one-third of itself within the regime's limit, and other expenditure; a fund pension's
 * commutation and a Government gratuity received on the employee's death bear the entire amount (D-196).
 *
 * <p>A provident fund's interest on the person's own contributions beyond the threshold is income in full: the
 * interest the fund accrued in the taxable contribution account, as the fund reports it, with the year's excess over
 * the threshold measured per fund to show what entered that account (D-198).
 *
 * <p>Gifts — sums and property received without, or for inadequate, consideration — are income above the threshold:
 * the year's sums together, the year's other property received free together, each immovable property and each
 * other purchase below value on its own; a receipt on an excluded ground counts toward nothing ({@link GiftIncome};
 * D-201).
 *
 * <p>Keyman policy sums, forfeited advances, compensation on termination from someone other than the employer, a
 * business trust's specified sums and the residue are income in full, the first four and the residue bearing other
 * expenditure (D-203). A life policy's sums are excluded or charged by {@link LifePolicySums} (D-205).
 *
 * <p>Which row charges an item is its kind's row in {@code RS-OS-INCOME}; a kind no row charges — winnings, whose rate
 * is deferred in the coverage register, and employee contributions and the letting of machinery, plant or furniture,
 * whose deductions are the business head's (D-202) — is refused (D-321). The head below nil is a loss, which {@link SetOffNode} sets off
 * against the other heads (D-217).
 */
public final class OtherSourcesNode implements ComputationNode {

    /** The item fact a row tests for which kinds of the head's income it reaches: the kind's constant name (D-318). */
    static final ItemFactId KIND = new ItemFactId("othersources.kind");

    private static final BaseId COMMISSION = new BaseId("COMMISSION");
    private static final BaseId EXPENDITURE = new BaseId("EXPENDITURE");
    private static final BaseId COMPENSATION_INTEREST = new BaseId("COMPENSATION_INTEREST");
    private static final BaseId COMMUTED_PENSION = new BaseId("COMMUTED_PENSION");
    private static final BaseId GRATUITY = new BaseId("GRATUITY");
    private static final BaseId BUSINESS_TRUST = new BaseId("BUSINESS_TRUST");

    /** The rule set, its base and the trace's words for a kind deducted whole (D-203, D-252), until W-153c2. */
    private record KindRow(RuleSetId ruleSet, BaseId base, String what) {
    }

    private static final Map<OtherSourceKind, KindRow> WHOLE_DEDUCTED = Map.of(
            OtherSourceKind.COMMUTED_FUND_PENSION, new KindRow(RuleSetIds.RS_OS_COMMUTED_PENSION, COMMUTED_PENSION,
                    "commutation of a fund pension"),
            OtherSourceKind.DEATH_GRATUITY, new KindRow(RuleSetIds.RS_OS_DEATH_GRATUITY, GRATUITY,
                    "gratuity received on the employee's death"));

    @Override
    public NodeId produces() {
        return NodeIds.OTHERSOURCES_INCOME;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of();
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of(RuleSetIds.RS_OS_INCOME, RuleSetIds.RS_OS_DIVIDEND_INTEREST,
                RuleSetIds.RS_OS_DEEMED_DIVIDEND, RuleSetIds.RS_OS_BANKER_COMMISSION, RuleSetIds.RS_OS_OTHER_EXPENDITURE,
                RuleSetIds.RS_OS_COMPENSATION_INTEREST, RuleSetIds.RS_OS_FAMILY_PENSION,
                RuleSetIds.RS_OS_COMMUTED_PENSION, RuleSetIds.RS_OS_DEATH_GRATUITY, RuleSetIds.RS_OS_PF_TAXABLE_INTEREST, RuleSetIds.RS_OS_GIFT,
                RuleSetIds.RS_OS_GIFT_COVID_DEATH, RuleSetIds.RS_OS_BUSINESS_TRUST,
                RuleSetIds.RS_OS_LIFE_POLICY_RATIO, RuleSetIds.RS_OS_LIFE_POLICY_PREMIUM, RuleSetIds.RS_OS_LIFE_POLICY);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        List<OtherSourceItem> items = ctx.inputs().entered().otherSourceItems();
        List<ProvidentFundAccount> accounts = ctx.inputs().entered().providentFundAccounts();
        List<GiftReceived> gifts = ctx.inputs().entered().giftsReceived();
        List<BusinessTrustUnits> trusts = ctx.inputs().entered().businessTrustUnits();
        List<LifePolicyReceipt> policies = ctx.inputs().entered().lifePolicyReceipts();
        if (items.isEmpty() && accounts.isEmpty() && gifts.isEmpty() && trusts.isEmpty() && policies.isEmpty()) {
            return NodeResult.untraced(MoneyValue.of(Money.ZERO));
        }
        requireCharged(ctx, items);
        OtherSourcesLines lines = new OtherSourcesLines(ctx, ctx.inputs().temporal());
        Money head = Money.ZERO;
        for (int position = 0; position < items.size(); position++) {
            head = head.plus(itemIncome(lines, position, items.get(position)));
        }
        head = head.minus(DividendInterestAllowance.allowed(lines, items));
        for (int position = 0; position < accounts.size(); position++) {
            head = head.plus(providentFundInterest(lines, position, accounts.get(position)));
        }
        if (!gifts.isEmpty()) {
            head = head.plus(GiftIncome.income(ctx, lines, gifts));
        }
        for (int position = 0; position < trusts.size(); position++) {
            head = head.plus(businessTrustIncome(lines, position, trusts.get(position)));
        }
        if (!policies.isEmpty()) {
            head = head.plus(LifePolicySums.income(ctx, lines, policies, ctx.inputs().entered().claimedLifePolicies()));
        }
        return new NodeResult(MoneyValue.of(head), lines.all());
    }

    /**
     * One item's income less what is allowed against it; dividends' interest expense is the year's, below. The arms
     * are the paths W-153c2 replaces with deduction rows reaching the kind (D-321); every other kind is charged by its
     * income row and bears what the expense rows reach.
     */
    private static Money itemIncome(OtherSourcesLines lines, int position, OtherSourceItem item) {
        String where = "item " + position;
        return switch (item.kind()) {
            case DIVIDEND, UNIT_INCOME -> dividend(lines, where, item, lines.row(RuleSetIds.RS_OS_DIVIDEND_INTEREST),
                    "only interest expense is deducted against dividend and unit income, within the year's limit");
            case DEEMED_DIVIDEND -> dividend(lines, where, item, lines.row(RuleSetIds.RS_OS_DEEMED_DIVIDEND),
                    "nothing is deducted against a deemed dividend");
            case INTEREST_ON_COMPENSATION -> compensationInterest(lines, where, item);
            case FAMILY_PENSION -> familyPension(lines, where, item);
            case COMMUTED_FUND_PENSION, DEATH_GRATUITY -> wholeDeducted(lines, where, item);
            default -> charged(lines, where, item);
        };
    }

    /** The row of {@code RS-OS-INCOME} that charges the item, chosen by its kind (S2 s.92; 1961 s.56; D-321). */
    private static Optional<RuleRow> incomeRow(NodeContext ctx, OtherSourceItem item) {
        return ctx.optionalRuleOf(RuleSetIds.RS_OS_INCOME, factsOf(item));
    }

    private static RuleRow chargingRow(NodeContext ctx, OtherSourceItem item) {
        return incomeRow(ctx, item).orElseThrow(() -> new IllegalStateException(item.kind() + " passed requireCharged"));
    }

    /** The base an income row's cap is a percentage of — the row's, so no kind's base is written here (D-321). */
    private static BaseId baseOf(NodeContext ctx, RuleRow row) {
        Optional<CapShape> cap = TemporallyApportionedCap.governingVersion(row, ctx.inputs().temporal()).cap();
        if (cap.isPresent() && cap.get() instanceof PercentOfBase percent) {
            return percent.base();
        }
        throw new IllegalStateException("Income row " + row.id() + " is not a percentage of a base");
    }

    /** The item's income line on its income row. */
    private static Money incomeLine(OtherSourcesLines lines, String where, OtherSourceItem item) {
        RuleRow row = chargingRow(lines.ctx(), item);
        Money income = lines.applied(row.id(), baseOf(lines.ctx(), row), item.received());
        lines.add(row.id(), StepKind.INCOME, item.received(), income, where + ": " + item.kind());
        return income;
    }

    /**
     * S2 s.92, s.93(1)(a), (e); 1961 s.56, s.57(i), (iii): the item as its income row charges it, less what the expense
     * rows reach (D-203, D-320, D-321).
     */
    private static Money charged(OtherSourcesLines lines, String where, OtherSourceItem item) {
        Money income = incomeLine(lines, where, item);
        return income.minus(otherExpenditure(lines, where, item));
    }

    /** The facts a row's conditions read about one item. */
    static ItemFacts factsOf(OtherSourceItem item) {
        return ItemFacts.of(KIND, item.kind().name());
    }

    /**
     * Whether the row reaches the item: its conditions about the item hold for this one, its others being the
     * person's (W-153a, W-153b; D-319, D-320).
     */
    static boolean reaches(NodeContext ctx, RuleId row, OtherSourceItem item) {
        ConditionContext about = ctx.conditions().about(factsOf(item));
        return TemporallyApportionedCap.governingVersion(ctx.rule(row), ctx.inputs().temporal()).conditions().stream()
                .filter(Condition::isAboutItem)
                .allMatch(condition -> condition.holds(about));
    }

    /**
     * An interest item's income as the head includes it — the interest less the banker's commission and the
     * expenditure, each where its row reaches the item, nothing where payable outside India untaxed — the figure s.153
     * is worked on (D-228, D-231). {@link #charged} traces the same arithmetic.
     */
    static Money interestIncluded(NodeContext ctx, OtherSourceItem item) {
        TemporalContext t = ctx.inputs().temporal();
        RuleRow incomeRow = chargingRow(ctx, item);
        Money income = applied(ctx, t, incomeRow.id(), baseOf(ctx, incomeRow), item.received());
        boolean allowed = !item.payableOutsideIndiaUntaxed();
        RuleId commissionRow = ctx.ruleIdOf(RuleSetIds.RS_OS_BANKER_COMMISSION);
        Money commission = allowed && reaches(ctx, commissionRow, item) && item.bankerCommission().isGreaterThan(Money.ZERO)
                ? applied(ctx, t, commissionRow, COMMISSION, item.bankerCommission()) : Money.ZERO;
        RuleId expenditureRow = ctx.ruleIdOf(RuleSetIds.RS_OS_OTHER_EXPENDITURE);
        Money expenses = expenditureClaimed(ctx, item);
        Money expenditure = allowed && reaches(ctx, expenditureRow, item) && expenses.isGreaterThan(Money.ZERO)
                ? applied(ctx, t, expenditureRow, EXPENDITURE, expenses) : Money.ZERO;
        return income.minus(commission).minus(expenditure);
    }

    /** The item's other expenditure, and its interest expense where the year's dividend limit does not take it. */
    private static Money expenditureClaimed(NodeContext ctx, OtherSourceItem item) {
        boolean interestToTheLimit = reaches(ctx, ctx.ruleIdOf(RuleSetIds.RS_OS_DIVIDEND_INTEREST), item);
        return interestToTheLimit ? item.otherExpenditure() : item.interestExpense().plus(item.otherExpenditure());
    }

    private static Money applied(NodeContext ctx, TemporalContext t, RuleId rule, BaseId base, Money amount) {
        return TemporallyApportionedCap.apply(ctx.rule(rule), t, CapContext.of(base, amount)).value();
    }

    /** S2 s.92, s.93(2); 1961 s.56, s.57 provisos: the income, with every expense but interest refused. */
    private static Money dividend(OtherSourcesLines lines, String where, OtherSourceItem item, RuleId refusingRow, String why) {
        Money income = incomeLine(lines, where, item);
        Money refused = item.bankerCommission().plus(item.otherExpenditure());
        if (!reaches(lines.ctx(), lines.row(RuleSetIds.RS_OS_DIVIDEND_INTEREST), item)) {
            refused = refused.plus(item.interestExpense());
        }
        if (refused.isGreaterThan(Money.ZERO)) {
            lines.nil(refusingRow, refused, where + ": " + why);
        }
        return income;
    }

    /** S2 s.92(2)(i), s.93(1)(f); 1961 s.56(2)(viii), s.57(iv): half deducted, and no other deduction. */
    private static Money compensationInterest(OtherSourcesLines lines, String where, OtherSourceItem item) {
        Money deduction = lines.applied(lines.row(RuleSetIds.RS_OS_COMPENSATION_INTEREST), COMPENSATION_INTEREST, item.received());
        Money income = item.received().minus(deduction);
        lines.add(lines.row(RuleSetIds.RS_OS_COMPENSATION_INTEREST), StepKind.INCOME, item.received(), income,
                where + ": interest on compensation less " + deduction);
        Money refused = item.bankerCommission().plus(item.interestExpense()).plus(item.otherExpenditure());
        if (refused.isGreaterThan(Money.ZERO)) {
            lines.nil(lines.row(RuleSetIds.RS_OS_COMPENSATION_INTEREST), refused, where + ": no other deduction is allowed");
        }
        return income;
    }

    /**
     * S2 s.93(1)(d), (e); 1961 s.57(iia), (iii): one-third of the pension within the regime's limit, then other
     * expenditure; a banker's commission is for interest on securities only.
     */
    private static Money familyPension(OtherSourcesLines lines, String where, OtherSourceItem item) {
        RuleId row = lines.row(RuleSetIds.RS_OS_FAMILY_PENSION);
        Money share = lines.familyPensionShare(row).deduction(item.received());
        Money income = item.received().minus(share);
        lines.add(row, StepKind.INCOME, item.received(), income, where + ": family pension less " + share);
        return income.minus(otherExpenditure(lines, where, item));
    }

    /**
     * S2 s.93(1)(a), (e); 1961 s.57(i), (iii): a banker's commission, and the interest expense and other expenditure
     * laid out for the income, each deducted where its row reaches the item and a nil line naming the kind where not.
     */
    private static Money otherExpenditure(OtherSourcesLines lines, String where, OtherSourceItem item) {
        Money deducted = Money.ZERO;
        if (item.bankerCommission().isGreaterThan(Money.ZERO)) {
            RuleId row = lines.row(RuleSetIds.RS_OS_BANKER_COMMISSION);
            deducted = reaches(lines.ctx(), row, item)
                    ? lines.deduction(row, COMMISSION, item, item.bankerCommission(), where)
                    : lines.nil(row, item.bankerCommission(), where + ": a banker's commission is not deducted against "
                            + item.kind());
        }
        Money expenses = expenditureClaimed(lines.ctx(), item);
        if (expenses.isGreaterThan(Money.ZERO)) {
            RuleId row = lines.row(RuleSetIds.RS_OS_OTHER_EXPENDITURE);
            deducted = deducted.plus(reaches(lines.ctx(), row, item)
                    ? lines.deduction(row, EXPENDITURE, item, expenses, where)
                    : lines.nil(row, expenses, where + ": expenditure is not deducted against " + item.kind()));
        }
        return deducted;
    }

    /** S2 s.92(2)(k); 1961 s.56(2)(xii): the holding's specified sum, A − B − C, nil where B and C exceed A. */
    private static Money businessTrustIncome(OtherSourcesLines lines, int position, BusinessTrustUnits units) {
        Money income = lines.applied(lines.row(RuleSetIds.RS_OS_BUSINESS_TRUST), BUSINESS_TRUST, units.specifiedSum());
        lines.add(lines.row(RuleSetIds.RS_OS_BUSINESS_TRUST), StepKind.INCOME, units.distributed(), income, "business trust holding "
                + position + ": " + units.distributed() + " distributed less " + units.issuedAt() + " issued and "
                + units.chargedEarlier() + " charged earlier, never below nil");
        return income;
    }

    /** S2 s.93(1)(g), (h); 1961 s.10(10A)(iii), s.10(10)(i): the entire amount, so nothing is left to bear an expense. */
    private static Money wholeDeducted(OtherSourcesLines lines, String where, OtherSourceItem item) {
        KindRow kind = WHOLE_DEDUCTED.get(item.kind());
        RuleId row = lines.row(kind.ruleSet());
        Money deduction = lines.applied(row, kind.base(), item.received());
        Money income = item.received().minus(deduction);
        lines.add(row, StepKind.INCOME, item.received(), income, where + ": " + kind.what() + " less " + deduction);
        Money refused = item.bankerCommission().plus(item.interestExpense()).plus(item.otherExpenditure());
        if (refused.isGreaterThan(Money.ZERO)) {
            lines.nil(row, refused, where + ": the entire amount is deducted already");
        }
        return income;
    }

    /**
     * S2 Schedule II Sl. 3(a), 4(a) with S4 r.277; 1961 s.10(11), (12) provisos with Rules 1962 r.9D: the interest
     * accrued in the fund's taxable contribution account, all of it income, and this year's contribution beyond the
     * threshold, which is what entered that account this year.
     */
    private static Money providentFundInterest(OtherSourcesLines lines, int position, ProvidentFundAccount account) {
        String where = "provident fund " + position;
        Money excess = lines.providentFundThreshold().excess(account.contributed(), account.employerContributes());
        lines.add(lines.row(RuleSetIds.RS_OS_PF_TAXABLE_INTEREST), StepKind.MEASURE, account.contributed(), excess,
                where + ": the contribution beyond the threshold enters the taxable contribution account");
        lines.add(lines.row(RuleSetIds.RS_OS_PF_TAXABLE_INTEREST), StepKind.INCOME, account.taxableAccountInterest(),
                account.taxableAccountInterest(), where + ": interest accrued in the taxable contribution account");
        return account.taxableAccountInterest();
    }

    /**
     * Every item a row of {@code RS-OS-INCOME} charges; a kind none charges in the year — today winnings and the
     * business head's kinds (D-202) — is refused by name rather than computed as something else (D-321).
     */
    private static void requireCharged(NodeContext ctx, List<OtherSourceItem> items) {
        for (int position = 0; position < items.size(); position++) {
            OtherSourceItem item = items.get(position);
            if (incomeRow(ctx, item).isEmpty()) {
                throw new IllegalArgumentException("Item " + position + ": no row of " + RuleSetIds.RS_OS_INCOME
                        + " charges " + item.kind() + " in TY " + ctx.inputs().taxYear().label()
                        + ", so it is not computed");
            }
        }
    }
}
