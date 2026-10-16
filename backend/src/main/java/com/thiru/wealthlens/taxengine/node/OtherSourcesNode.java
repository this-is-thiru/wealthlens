package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Income from other sources (S2 ss.92–94; 1961 ss.56–58; {@code DR 01} R17; D-194): interest, dividends, Mutual Fund
 * unit income and interest on compensation, each less what the law lets the assessee deduct against it in the year.
 *
 * <p>Interest bears the banker's commission (on securities only) and other expenditure laid out wholly and exclusively
 * for it. Dividends and unit income bear nothing but interest expense, and that only up to twenty per cent of the
 * year's dividend and unit income together — the proviso caps the year's deduction, not each item's — until TY
 * 2026-27, when nothing at all. A deemed dividend bears nothing in either year; interest on compensation bears half of
 * itself and nothing else. An expense the law does not allow is a nil line naming why, never silently left out.
 *
 * <p>Winnings and the other kinds are refused: winnings' own rate is deferred in the coverage register, and the
 * remaining clauses of S2 s.92(2) are {@code W-079g}'s. The head below nil is refused until set-off is built
 * ({@code W-081}), as house property's is.
 */
public final class OtherSourcesNode implements ComputationNode {

    private static final BaseId INTEREST = new BaseId("INTEREST");
    private static final BaseId DIVIDEND = new BaseId("DIVIDEND");
    private static final BaseId COMMISSION = new BaseId("COMMISSION");
    private static final BaseId EXPENDITURE = new BaseId("EXPENDITURE");
    private static final BaseId COMPENSATION_INTEREST = new BaseId("COMPENSATION_INTEREST");

    private static final String UNTAXED_ABROAD = ": payable outside India untaxed (S2 s.94(1)(b), (c); 1961"
            + " s.58(1)(a)(ii), (iii))";

    @Override
    public NodeId produces() {
        return NodeIds.OTHERSOURCES_INCOME;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of();
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of(RuleIds.OS_INTEREST, RuleIds.OS_DIVIDEND, RuleIds.OS_DIVIDEND_INTEREST,
                RuleIds.OS_DEEMED_DIVIDEND, RuleIds.OS_BANKER_COMMISSION, RuleIds.OS_OTHER_EXPENDITURE,
                RuleIds.OS_COMPENSATION_INTEREST);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        List<OtherSourceItem> items = ctx.inputs().entered().otherSourceItems();
        if (items.isEmpty()) {
            return NodeResult.untraced(MoneyValue.of(Money.ZERO));
        }
        requireBuiltKinds(items);
        Lines lines = new Lines(ctx, ctx.inputs().temporal());
        Money head = Money.ZERO;
        for (int position = 0; position < items.size(); position++) {
            head = head.plus(itemIncome(lines, position, items.get(position)));
        }
        head = head.minus(dividendInterestAllowed(lines, items));
        if (head.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("Income from other sources is a loss of " + Money.ZERO.minus(head)
                    + "; setting it off against other heads or carrying it forward (S2 s.109; 1961 s.71) waits on"
                    + " W-081");
        }
        return new NodeResult(MoneyValue.of(head), lines.all());
    }

    /** One item's income less what its own kind allows; dividends' interest expense is the year's, below. */
    private static Money itemIncome(Lines lines, int position, OtherSourceItem item) {
        String where = "item " + position;
        return switch (item.kind()) {
            case INTEREST_ON_SECURITIES, SAVINGS_DEPOSIT_INTEREST, DEPOSIT_INTEREST, OTHER_INTEREST ->
                    interest(lines, where, item);
            case DIVIDEND, UNIT_INCOME -> dividend(lines, where, item, RuleIds.OS_DIVIDEND_INTEREST,
                    "only interest expense is deducted against dividend and unit income, within the year's limit");
            case DEEMED_DIVIDEND -> dividend(lines, where, item, RuleIds.OS_DEEMED_DIVIDEND,
                    "nothing is deducted against a deemed dividend");
            case INTEREST_ON_COMPENSATION -> compensationInterest(lines, where, item);
            case WINNINGS, OTHER -> throw new IllegalStateException(item.kind() + " passed requireBuiltKinds");
        };
    }

    /** S2 s.92(1), (2)(e), s.93(1)(a), (e); 1961 s.56(1), (2)(id), s.57(i), (iii). */
    private static Money interest(Lines lines, String where, OtherSourceItem item) {
        Money income = lines.applied(RuleIds.OS_INTEREST, INTEREST, item.received());
        lines.add(RuleIds.OS_INTEREST, StepKind.INCOME, item.received(), income, where + ": " + item.kind());
        Money commission = Money.ZERO;
        if (item.bankerCommission().isGreaterThan(Money.ZERO)) {
            commission = item.kind() == OtherSourceKind.INTEREST_ON_SECURITIES
                    ? lines.deduction(RuleIds.OS_BANKER_COMMISSION, COMMISSION, item, item.bankerCommission(), where)
                    : lines.nil(RuleIds.OS_BANKER_COMMISSION, item.bankerCommission(), where
                            + ": a banker's commission is deducted against interest on securities only");
        }
        Money expenses = item.interestExpense().plus(item.otherExpenditure());
        Money expenditure = expenses.isGreaterThan(Money.ZERO)
                ? lines.deduction(RuleIds.OS_OTHER_EXPENDITURE, EXPENDITURE, item, expenses, where)
                : Money.ZERO;
        return income.minus(commission).minus(expenditure);
    }

    /** S2 s.92(2)(a), s.93(2); 1961 s.56(2)(i), s.57 provisos: the income, with every expense but interest refused. */
    private static Money dividend(Lines lines, String where, OtherSourceItem item, RuleId refusingRow, String why) {
        Money income = lines.applied(RuleIds.OS_DIVIDEND, DIVIDEND, item.received());
        lines.add(RuleIds.OS_DIVIDEND, StepKind.INCOME, item.received(), income, where + ": " + item.kind());
        Money refused = item.bankerCommission().plus(item.otherExpenditure());
        if (item.kind() == OtherSourceKind.DEEMED_DIVIDEND) {
            refused = refused.plus(item.interestExpense());
        }
        if (refused.isGreaterThan(Money.ZERO)) {
            lines.nil(refusingRow, refused, where + ": " + why);
        }
        return income;
    }

    /** S2 s.92(2)(i), s.93(1)(f); 1961 s.56(2)(viii), s.57(iv): half deducted, and no other deduction. */
    private static Money compensationInterest(Lines lines, String where, OtherSourceItem item) {
        Money deduction = lines.applied(RuleIds.OS_COMPENSATION_INTEREST, COMPENSATION_INTEREST, item.received());
        Money income = item.received().minus(deduction);
        lines.add(RuleIds.OS_COMPENSATION_INTEREST, StepKind.INCOME, item.received(), income,
                where + ": interest on compensation less " + deduction);
        Money refused = item.bankerCommission().plus(item.interestExpense()).plus(item.otherExpenditure());
        if (refused.isGreaterThan(Money.ZERO)) {
            lines.nil(RuleIds.OS_COMPENSATION_INTEREST, refused, where + ": no other deduction is allowed");
        }
        return income;
    }

    /**
     * S2 s.93(2) as substituted by FA 2026 s.44(b); 1961 s.57 proviso (FA 2020 s.30(b)): the interest expense against
     * the year's dividend and unit income, within the row's share of that income — the year's, not each item's.
     */
    private static Money dividendInterestAllowed(Lines lines, List<OtherSourceItem> items) {
        Money dividends = Money.ZERO;
        Money interest = Money.ZERO;
        boolean untaxedAbroad = false;
        for (OtherSourceItem item : items) {
            if (item.kind() == OtherSourceKind.DIVIDEND || item.kind() == OtherSourceKind.UNIT_INCOME) {
                dividends = dividends.plus(item.received());
                interest = interest.plus(item.interestExpense());
                untaxedAbroad |= item.payableOutsideIndiaUntaxed() && item.interestExpense().isGreaterThan(Money.ZERO);
            }
        }
        if (interest.equals(Money.ZERO)) {
            return Money.ZERO;
        }
        if (untaxedAbroad) {
            return lines.nil(RuleIds.OS_DIVIDEND_INTEREST, interest, "dividend and unit income" + UNTAXED_ABROAD);
        }
        Money limit = lines.applied(RuleIds.OS_DIVIDEND_INTEREST, DIVIDEND, dividends);
        Money allowed = Money.min(interest, limit);
        lines.add(RuleIds.OS_DIVIDEND_INTEREST, StepKind.DEDUCTION, interest, allowed,
                "interest expense against dividend and unit income of " + dividends + ", limited to " + limit);
        return allowed;
    }

    /** Winnings and the remaining clauses are not built: refused by name rather than computed as something else. */
    private static void requireBuiltKinds(List<OtherSourceItem> items) {
        for (int position = 0; position < items.size(); position++) {
            OtherSourceKind kind = items.get(position).kind();
            if (kind == OtherSourceKind.WINNINGS) {
                throw new IllegalArgumentException("Item " + position + ": winnings from lotteries, races, games or"
                        + " betting are taxed at a rate of their own, which is deferred in the coverage register"
                        + " (Income-tax Act, 1961 s.115BB) and not built");
            }
            if (kind == OtherSourceKind.OTHER) {
                throw new IllegalArgumentException("Item " + position + ": income from other sources other than"
                        + " interest and dividends — S2 s.92(2)(c), (d), (f)–(h), (j)–(l) and the residue — waits on"
                        + " W-079g");
            }
        }
    }

    /** The node's trace lines, numbered as they are added, each citing the version of its row that governs. */
    private static final class Lines {

        private final NodeContext ctx;
        private final TemporalContext temporal;
        private final List<TraceLine> lines = new ArrayList<>();

        Lines(NodeContext ctx, TemporalContext temporal) {
            this.ctx = ctx;
            this.temporal = temporal;
        }

        Money applied(RuleId rule, BaseId base, Money amount) {
            return TemporallyApportionedCap.apply(ctx.rule(rule), temporal, CapContext.of(base, amount)).value();
        }

        /** An expense the row allows: nil where it is payable outside India untaxed, else the row's share of it. */
        Money deduction(RuleId rule, BaseId base, OtherSourceItem item, Money amount, String where) {
            if (item.payableOutsideIndiaUntaxed()) {
                return nil(rule, amount, where + UNTAXED_ABROAD);
            }
            Money allowed = applied(rule, base, amount);
            add(rule, StepKind.DEDUCTION, amount, allowed, where);
            return allowed;
        }

        Money nil(RuleId rule, Money amount, String why) {
            add(rule, StepKind.DEDUCTION, amount, Money.ZERO, why);
            return Money.ZERO;
        }

        void add(RuleId rule, StepKind kind, Money in, Money out, String why) {
            lines.add(TraceLine.of(lines.size() + 1, NodeIds.OTHERSOURCES_INCOME, rule, kind, MoneyValue.of(in),
                            MoneyValue.of(out), TemporallyApportionedCap.governingVersion(ctx.rule(rule), temporal)
                                    .provenance())
                    .withLimitedBy(why));
        }

        List<TraceLine> all() {
            return List.copyOf(lines);
        }
    }
}
