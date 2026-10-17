package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.GiftExclusion;
import com.thiru.wealthlens.taxengine.input.GiftKind;
import com.thiru.wealthlens.taxengine.input.GiftReceived;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.input.ProvidentFundAccount;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.definition.FamilyPensionShare;
import com.thiru.wealthlens.taxengine.rules.definition.GiftThreshold;
import com.thiru.wealthlens.taxengine.rules.definition.ProvidentFundThreshold;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
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
 * <p>A family pension bears one-third of itself within the regime's limit, and other expenditure; a fund pension's
 * commutation and a Government gratuity received on the employee's death bear the entire amount (D-196).
 *
 * <p>A provident fund's interest on the person's own contributions beyond the threshold is income in full: the
 * interest the fund accrued in the taxable contribution account, as the fund reports it, with the year's excess over
 * the threshold measured per fund to show what entered that account (D-198).
 *
 * <p>Gifts — sums and property received without, or for inadequate, consideration — are income above the threshold:
 * the year's sums together, the year's other property received free together, each immovable property and each
 * other purchase below value on its own; a receipt on an excluded ground counts toward nothing (D-201).
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
    private static final BaseId COMMUTED_PENSION = new BaseId("COMMUTED_PENSION");
    private static final BaseId GRATUITY = new BaseId("GRATUITY");
    private static final BaseId GIFT = new BaseId("GIFT");
    private static final Set<GiftExclusion> COVID_GROUNDS = Set.of(GiftExclusion.COVID_TREATMENT,
            GiftExclusion.COVID_DEATH_FROM_EMPLOYER, GiftExclusion.COVID_DEATH_FROM_OTHERS);
    private static final List<RuleId> FAMILY_PENSION_ROWS = List.of(RuleIds.OS_FAMILY_PENSION,
            RuleIds.OS_FAMILY_PENSION_NEW_TAX_REGIME);

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
                RuleIds.OS_COMPENSATION_INTEREST, RuleIds.OS_FAMILY_PENSION, RuleIds.OS_FAMILY_PENSION_NEW_TAX_REGIME,
                RuleIds.OS_COMMUTED_PENSION, RuleIds.OS_DEATH_GRATUITY, RuleIds.OS_PF_TAXABLE_INTEREST, RuleIds.OS_GIFT,
                RuleIds.OS_GIFT_COVID_DEATH);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        List<OtherSourceItem> items = ctx.inputs().entered().otherSourceItems();
        List<ProvidentFundAccount> accounts = ctx.inputs().entered().providentFundAccounts();
        List<GiftReceived> gifts = ctx.inputs().entered().giftsReceived();
        if (items.isEmpty() && accounts.isEmpty() && gifts.isEmpty()) {
            return NodeResult.untraced(MoneyValue.of(Money.ZERO));
        }
        requireBuiltKinds(items);
        Lines lines = new Lines(ctx, ctx.inputs().temporal());
        Money head = Money.ZERO;
        for (int position = 0; position < items.size(); position++) {
            head = head.plus(itemIncome(lines, position, items.get(position)));
        }
        head = head.minus(dividendInterestAllowed(lines, items));
        for (int position = 0; position < accounts.size(); position++) {
            head = head.plus(providentFundInterest(lines, position, accounts.get(position)));
        }
        if (!gifts.isEmpty()) {
            head = head.plus(giftIncome(ctx, lines, gifts));
        }
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
            case FAMILY_PENSION -> familyPension(lines, where, item);
            case COMMUTED_FUND_PENSION -> wholeDeducted(lines, where, item, RuleIds.OS_COMMUTED_PENSION, COMMUTED_PENSION,
                    "commutation of a fund pension");
            case DEATH_GRATUITY -> wholeDeducted(lines, where, item, RuleIds.OS_DEATH_GRATUITY, GRATUITY,
                    "gratuity received on the employee's death");
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
     * S2 s.93(1)(d), (e); 1961 s.57(iia), (iii): one-third of the pension within the regime's limit, then other
     * expenditure; a banker's commission is for interest on securities only.
     */
    private static Money familyPension(Lines lines, String where, OtherSourceItem item) {
        RuleId row = lines.applying(FAMILY_PENSION_ROWS);
        Money share = lines.familyPensionShare(row).deduction(item.received());
        Money income = item.received().minus(share);
        lines.add(row, StepKind.INCOME, item.received(), income, where + ": family pension less " + share);
        if (item.bankerCommission().isGreaterThan(Money.ZERO)) {
            lines.nil(RuleIds.OS_BANKER_COMMISSION, item.bankerCommission(), where
                    + ": a banker's commission is deducted against interest on securities only");
        }
        Money expenses = item.interestExpense().plus(item.otherExpenditure());
        Money expenditure = expenses.isGreaterThan(Money.ZERO)
                ? lines.deduction(RuleIds.OS_OTHER_EXPENDITURE, EXPENDITURE, item, expenses, where)
                : Money.ZERO;
        return income.minus(expenditure);
    }

    /** S2 s.93(1)(g), (h); 1961 s.10(10A)(iii), s.10(10)(i): the entire amount, so nothing is left to bear an expense. */
    private static Money wholeDeducted(Lines lines, String where, OtherSourceItem item, RuleId row, BaseId base,
            String what) {
        Money deduction = lines.applied(row, base, item.received());
        Money income = item.received().minus(deduction);
        lines.add(row, StepKind.INCOME, item.received(), income, where + ": " + what + " less " + deduction);
        Money refused = item.bankerCommission().plus(item.interestExpense()).plus(item.otherExpenditure());
        if (refused.isGreaterThan(Money.ZERO)) {
            lines.nil(row, refused, where + ": the entire amount is deducted already");
        }
        return income;
    }

    /**
     * S2 s.93(2) as substituted by FA 2026 s.44(b); 1961 s.57 proviso (FA 2020 s.30(b)): the interest expense against
     * the year's dividend and unit income, within the row's share of that income — the year's, not each item's.
     */
    private static Money dividendInterestAllowed(Lines lines, List<OtherSourceItem> items) {
        DividendInterest interest = dividendInterest(lines.ctx, items);
        if (interest.claimed().equals(Money.ZERO)) {
            return Money.ZERO;
        }
        if (interest.untaxedAbroad()) {
            return lines.nil(RuleIds.OS_DIVIDEND_INTEREST, interest.claimed(), "dividend and unit income"
                    + UNTAXED_ABROAD);
        }
        Money allowed = interest.allowed();
        lines.add(RuleIds.OS_DIVIDEND_INTEREST, StepKind.DEDUCTION, interest.claimed(), allowed,
                "interest expense against dividend and unit income of " + interest.base() + ", limited to "
                        + interest.limit());
        return allowed;
    }

    /**
     * The year's interest expense against dividend and unit income (S2 s.93(2); 1961 s.57 proviso): what was claimed,
     * how much of it on dividend items, and the limit on what is allowed. Computed once, here, for this head and for
     * the surcharge's dividend income (D-200).
     *
     * @param base the year's dividend and unit income, which the limit is a share of
     * @param claimed the interest expense claimed against dividend and unit items
     * @param claimedOnDividends the part of {@code claimed} entered against dividend items
     * @param limit the row's share of {@code base}
     * @param untaxedAbroad whether any of it is payable outside India untaxed, which bars all of it
     */
    record DividendInterest(Money base, Money claimed, Money claimedOnDividends, Money limit, boolean untaxedAbroad) {

        /** The interest allowed: nil where barred, else the claim within the limit. */
        Money allowed() {
            return untaxedAbroad ? Money.ZERO : Money.min(claimed, limit);
        }
    }

    static DividendInterest dividendInterest(NodeContext ctx, List<OtherSourceItem> items) {
        Money base = Money.ZERO;
        Money claimed = Money.ZERO;
        Money claimedOnDividends = Money.ZERO;
        boolean untaxedAbroad = false;
        for (OtherSourceItem item : items) {
            if (item.kind() == OtherSourceKind.DIVIDEND || item.kind() == OtherSourceKind.UNIT_INCOME) {
                base = base.plus(item.received());
                claimed = claimed.plus(item.interestExpense());
                untaxedAbroad |= item.payableOutsideIndiaUntaxed() && item.interestExpense().isGreaterThan(Money.ZERO);
                if (item.kind() == OtherSourceKind.DIVIDEND) {
                    claimedOnDividends = claimedOnDividends.plus(item.interestExpense());
                }
            }
        }
        Money limit = TemporallyApportionedCap.apply(ctx.rule(RuleIds.OS_DIVIDEND_INTEREST), ctx.inputs().temporal(),
                CapContext.of(DIVIDEND, base)).value();
        return new DividendInterest(base, claimed, claimedOnDividends, limit, untaxedAbroad);
    }

    /**
     * S2 Schedule II Sl. 3(a), 4(a) with S4 r.277; 1961 s.10(11), (12) provisos with Rules 1962 r.9D: the interest
     * accrued in the fund's taxable contribution account, all of it income, and this year's contribution beyond the
     * threshold, which is what entered that account this year.
     */
    private static Money providentFundInterest(Lines lines, int position, ProvidentFundAccount account) {
        String where = "provident fund " + position;
        Money excess = lines.providentFundThreshold().excess(account.contributed(), account.employerContributes());
        lines.add(RuleIds.OS_PF_TAXABLE_INTEREST, StepKind.MEASURE, account.contributed(), excess,
                where + ": the contribution beyond the threshold enters the taxable contribution account");
        lines.add(RuleIds.OS_PF_TAXABLE_INTEREST, StepKind.INCOME, account.taxableAccountInterest(),
                account.taxableAccountInterest(), where + ": interest accrued in the taxable contribution account");
        return account.taxableAccountInterest();
    }

    /**
     * S2 s.92(2)(m), (3); 1961 s.56(2)(x) and its proviso: the year's gifts. The sums received are tested together,
     * as is the other property received free ("the total of which", "the aggregate fair market value"); each
     * immovable property, and each other property bought below its value, is tested on its own.
     */
    private static Money giftIncome(NodeContext ctx, Lines lines, List<GiftReceived> gifts) {
        GiftThreshold threshold = lines.giftThreshold();
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
                    lines.add(RuleIds.OS_GIFT, StepKind.EXEMPTION, gift.value(), gift.value(),
                            where + ": excluded, " + ground);
                }
                continue;
            }
            switch (gift.kind()) {
                case SUM_OF_MONEY -> sums = sums.plus(gift.value());
                case IMMOVABLE_PROPERTY -> charged = charged.plus(lines.chargedGift(where + ": immovable property",
                        gift.value(), threshold.immovablePropertyCharged(gift.consideration(), gift.value())));
                case OTHER_PROPERTY -> {
                    if (gift.consideration().equals(Money.ZERO)) {
                        propertyFree = propertyFree.plus(gift.value());
                    } else {
                        charged = charged.plus(lines.chargedGift(where + ": property bought below its value",
                                gift.value(), threshold.propertyShortfallCharged(gift.consideration(), gift.value())));
                    }
                }
            }
        }
        sums = sums.plus(covidDeathSumsBeyondTheLimit(lines, covidDeathSums));
        charged = charged.plus(lines.chargedGift("sums of money received without consideration", sums,
                threshold.isExceededBy(sums) ? sums : Money.ZERO));
        return charged.plus(lines.chargedGift("other property received without consideration", propertyFree,
                threshold.isExceededBy(propertyFree) ? propertyFree : Money.ZERO));
    }

    /** 1961 s.56(2)(x) proviso (XIII)(B): such sums are excluded only up to the limit; the rest are sums like any. */
    private static Money covidDeathSumsBeyondTheLimit(Lines lines, Money covidDeathSums) {
        if (covidDeathSums.equals(Money.ZERO)) {
            return Money.ZERO;
        }
        Money excluded = Money.min(covidDeathSums, lines.applied(RuleIds.OS_GIFT_COVID_DEATH, GIFT, covidDeathSums));
        lines.add(RuleIds.OS_GIFT_COVID_DEATH, StepKind.EXEMPTION, covidDeathSums, excluded,
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
        if (!ctx.pack().rules().containsKey(RuleIds.OS_GIFT_COVID_DEATH)) {
            throw new IllegalArgumentException(where + ": " + ground + " is a ground of the Income-tax Act, 1961 s.56(2)(x)"
                    + " proviso (XII), (XIII) only; the Income-tax Act, 2025 s.92(3) has no such exclusion");
        }
        if (ground == GiftExclusion.COVID_DEATH_FROM_OTHERS && gift.kind() != GiftKind.SUM_OF_MONEY) {
            throw new IllegalArgumentException(where + ": proviso (XIII)(B) excludes sums of money only, not "
                    + gift.kind());
        }
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

        /** The one row of {@code rows} the regime applies; the rows are keyed by regime, so exactly one does. */
        RuleId applying(List<RuleId> rows) {
            List<RuleId> applying = rows.stream()
                    .filter(row -> ctx.inRegime(row, version(row)).applies())
                    .toList();
            if (applying.size() != 1) {
                throw new IllegalStateException(applying.size() + " of " + rows + " apply in the "
                        + ctx.regime().code().value() + " regime; exactly one must");
            }
            return applying.getFirst();
        }

        FamilyPensionShare familyPensionShare(RuleId row) {
            return RuleDefinitions.require(version(row).definition(), FamilyPensionShare.class, row);
        }

        GiftThreshold giftThreshold() {
            return RuleDefinitions.require(version(RuleIds.OS_GIFT).definition(), GiftThreshold.class,
                    RuleIds.OS_GIFT);
        }

        /** One INCOME line for a gift or a total of gifts: what was received, what of it is charged. */
        Money chargedGift(String what, Money received, Money chargedAmount) {
            if (received.isGreaterThan(Money.ZERO)) {
                add(RuleIds.OS_GIFT, StepKind.INCOME, received, chargedAmount, what);
            }
            return chargedAmount;
        }

        ProvidentFundThreshold providentFundThreshold() {
            return RuleDefinitions.require(version(RuleIds.OS_PF_TAXABLE_INTEREST).definition(),
                    ProvidentFundThreshold.class, RuleIds.OS_PF_TAXABLE_INTEREST);
        }

        private RuleVersion version(RuleId row) {
            return TemporallyApportionedCap.governingVersion(ctx.rule(row), temporal);
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
