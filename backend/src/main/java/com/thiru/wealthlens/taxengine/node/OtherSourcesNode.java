package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.BusinessTrustUnits;
import com.thiru.wealthlens.taxengine.input.GiftExclusion;
import com.thiru.wealthlens.taxengine.input.GiftKind;
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
import com.thiru.wealthlens.taxengine.rules.definition.FamilyPensionShare;
import com.thiru.wealthlens.taxengine.rules.definition.GiftThreshold;
import com.thiru.wealthlens.taxengine.rules.definition.ProvidentFundThreshold;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
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
 * <p>Keyman policy sums, forfeited advances, compensation on termination from someone other than the employer, a
 * business trust's specified sums and the residue are income in full, the first four and the residue bearing other
 * expenditure (D-203). A life policy's sums are excluded or charged by {@link LifePolicySums} (D-205).
 *
 * <p>Winnings are refused, their own rate being deferred in the coverage register; so are employee contributions
 * received and the letting of machinery, plant or furniture, whose deductions are the business head's, deferred with
 * it (D-202), and life-policy sums ({@code W-079g2}). The head below nil is a loss, which {@link SetOffNode} sets off
 * against the other heads (D-217).
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
    private static final BaseId BUSINESS_TRUST = new BaseId("BUSINESS_TRUST");

    /** The rule set, its base and the trace's words for each kind received in full (D-203, D-252). */
    private record InFullRow(RuleSetId ruleSet, BaseId base, String what) {
    }

    private static final Map<OtherSourceKind, InFullRow> IN_FULL = Map.of(
            OtherSourceKind.KEYMAN_POLICY, new InFullRow(RuleSetIds.RS_OS_KEYMAN_POLICY, new BaseId("KEYMAN"),
                    "Keyman policy sums"),
            OtherSourceKind.FORFEITED_ADVANCE, new InFullRow(RuleSetIds.RS_OS_FORFEITED_ADVANCE, new BaseId("ADVANCE"),
                    "advance forfeited"),
            OtherSourceKind.TERMINATION_COMPENSATION, new InFullRow(RuleSetIds.RS_OS_TERMINATION_COMPENSATION,
                    new BaseId("COMPENSATION"), "compensation on termination, not from the employer"),
            OtherSourceKind.OTHER, new InFullRow(RuleSetIds.RS_OS_RESIDUAL, new BaseId("RESIDUAL"),
                    "other income under the head"));
    private static final Set<OtherSourceKind> BUSINESS_HEAD_KINDS = Set.of(OtherSourceKind.EMPLOYEE_CONTRIBUTIONS,
            OtherSourceKind.MACHINERY_LETTING, OtherSourceKind.MACHINERY_AND_BUILDING_LETTING);
    private static final Set<GiftExclusion> COVID_GROUNDS = Set.of(GiftExclusion.COVID_TREATMENT,
            GiftExclusion.COVID_DEATH_FROM_EMPLOYER, GiftExclusion.COVID_DEATH_FROM_OTHERS);

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
    public Set<RuleSetId> ruleSets() {
        return Set.of(RuleSetIds.RS_OS_INTEREST, RuleSetIds.RS_OS_DIVIDEND, RuleSetIds.RS_OS_DIVIDEND_INTEREST,
                RuleSetIds.RS_OS_DEEMED_DIVIDEND, RuleSetIds.RS_OS_BANKER_COMMISSION, RuleSetIds.RS_OS_OTHER_EXPENDITURE,
                RuleSetIds.RS_OS_COMPENSATION_INTEREST, RuleSetIds.RS_OS_FAMILY_PENSION,
                RuleSetIds.RS_OS_COMMUTED_PENSION, RuleSetIds.RS_OS_DEATH_GRATUITY, RuleSetIds.RS_OS_PF_TAXABLE_INTEREST, RuleSetIds.RS_OS_GIFT,
                RuleSetIds.RS_OS_GIFT_COVID_DEATH, RuleSetIds.RS_OS_KEYMAN_POLICY, RuleSetIds.RS_OS_FORFEITED_ADVANCE,
                RuleSetIds.RS_OS_TERMINATION_COMPENSATION, RuleSetIds.RS_OS_BUSINESS_TRUST, RuleSetIds.RS_OS_RESIDUAL,
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
        for (int position = 0; position < trusts.size(); position++) {
            head = head.plus(businessTrustIncome(lines, position, trusts.get(position)));
        }
        if (!policies.isEmpty()) {
            head = head.plus(LifePolicySums.income(ctx, lines, policies, ctx.inputs().entered().claimedLifePolicies()));
        }
        return new NodeResult(MoneyValue.of(head), lines.all());
    }

    /** One item's income less what its own kind allows; dividends' interest expense is the year's, below. */
    private static Money itemIncome(Lines lines, int position, OtherSourceItem item) {
        String where = "item " + position;
        return switch (item.kind()) {
            case INTEREST_ON_SECURITIES, SAVINGS_DEPOSIT_INTEREST, DEPOSIT_INTEREST, OTHER_INTEREST ->
                    interest(lines, where, item);
            case DIVIDEND, UNIT_INCOME -> dividend(lines, where, item, lines.row(RuleSetIds.RS_OS_DIVIDEND_INTEREST),
                    "only interest expense is deducted against dividend and unit income, within the year's limit");
            case DEEMED_DIVIDEND -> dividend(lines, where, item, lines.row(RuleSetIds.RS_OS_DEEMED_DIVIDEND),
                    "nothing is deducted against a deemed dividend");
            case INTEREST_ON_COMPENSATION -> compensationInterest(lines, where, item);
            case FAMILY_PENSION -> familyPension(lines, where, item);
            case COMMUTED_FUND_PENSION -> wholeDeducted(lines, where, item, lines.row(RuleSetIds.RS_OS_COMMUTED_PENSION), COMMUTED_PENSION,
                    "commutation of a fund pension");
            case DEATH_GRATUITY -> wholeDeducted(lines, where, item, lines.row(RuleSetIds.RS_OS_DEATH_GRATUITY), GRATUITY,
                    "gratuity received on the employee's death");
            case KEYMAN_POLICY, FORFEITED_ADVANCE, TERMINATION_COMPENSATION, OTHER -> inFull(lines, where, item);
            case WINNINGS, EMPLOYEE_CONTRIBUTIONS, MACHINERY_LETTING, MACHINERY_AND_BUILDING_LETTING ->
                    throw new IllegalStateException(item.kind() + " passed requireBuiltKinds");
        };
    }

    /** S2 s.92(1), (2)(e), s.93(1)(a), (e); 1961 s.56(1), (2)(id), s.57(i), (iii). */
    private static Money interest(Lines lines, String where, OtherSourceItem item) {
        Money income = lines.applied(lines.row(RuleSetIds.RS_OS_INTEREST), INTEREST, item.received());
        lines.add(lines.row(RuleSetIds.RS_OS_INTEREST), StepKind.INCOME, item.received(), income, where + ": " + item.kind());
        if (item.bankerCommission().isGreaterThan(Money.ZERO)) {
            if (item.kind() == OtherSourceKind.INTEREST_ON_SECURITIES) {
                lines.deduction(lines.row(RuleSetIds.RS_OS_BANKER_COMMISSION), COMMISSION, item, item.bankerCommission(), where);
            } else {
                lines.nil(lines.row(RuleSetIds.RS_OS_BANKER_COMMISSION), item.bankerCommission(), where
                        + ": a banker's commission is deducted against interest on securities only");
            }
        }
        Money expenses = item.interestExpense().plus(item.otherExpenditure());
        if (expenses.isGreaterThan(Money.ZERO)) {
            lines.deduction(lines.row(RuleSetIds.RS_OS_OTHER_EXPENDITURE), EXPENDITURE, item, expenses, where);
        }
        return interestIncluded(lines.ctx, item);
    }

    /**
     * An interest item's income as the head includes it — the interest less the banker's commission (securities only)
     * and the expenditure allowed against it, nothing where payable outside India untaxed — the figure s.153 is worked
     * on (D-228, D-231). {@link #interest} traces the same arithmetic and returns this.
     */
    static Money interestIncluded(NodeContext ctx, OtherSourceItem item) {
        TemporalContext t = ctx.inputs().temporal();
        Money income = applied(ctx, t, ctx.ruleIdOf(RuleSetIds.RS_OS_INTEREST), INTEREST, item.received());
        boolean allowed = !item.payableOutsideIndiaUntaxed();
        Money commission = allowed && item.kind() == OtherSourceKind.INTEREST_ON_SECURITIES
                && item.bankerCommission().isGreaterThan(Money.ZERO)
                ? applied(ctx, t, ctx.ruleIdOf(RuleSetIds.RS_OS_BANKER_COMMISSION), COMMISSION, item.bankerCommission()) : Money.ZERO;
        Money expenses = item.interestExpense().plus(item.otherExpenditure());
        Money expenditure = allowed && expenses.isGreaterThan(Money.ZERO)
                ? applied(ctx, t, ctx.ruleIdOf(RuleSetIds.RS_OS_OTHER_EXPENDITURE), EXPENDITURE, expenses) : Money.ZERO;
        return income.minus(commission).minus(expenditure);
    }

    private static Money applied(NodeContext ctx, TemporalContext t, RuleId rule, BaseId base, Money amount) {
        return TemporallyApportionedCap.apply(ctx.rule(rule), t, CapContext.of(base, amount)).value();
    }

    /** S2 s.92(2)(a), s.93(2); 1961 s.56(2)(i), s.57 provisos: the income, with every expense but interest refused. */
    private static Money dividend(Lines lines, String where, OtherSourceItem item, RuleId refusingRow, String why) {
        Money income = lines.applied(lines.row(RuleSetIds.RS_OS_DIVIDEND), DIVIDEND, item.received());
        lines.add(lines.row(RuleSetIds.RS_OS_DIVIDEND), StepKind.INCOME, item.received(), income, where + ": " + item.kind());
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
    private static Money familyPension(Lines lines, String where, OtherSourceItem item) {
        RuleId row = lines.row(RuleSetIds.RS_OS_FAMILY_PENSION);
        Money share = lines.familyPensionShare(row).deduction(item.received());
        Money income = item.received().minus(share);
        lines.add(row, StepKind.INCOME, item.received(), income, where + ": family pension less " + share);
        return income.minus(otherExpenditure(lines, where, item));
    }

    /**
     * S2 s.92(1), (2)(d), (h), (j), s.93(1)(e); 1961 s.56(1), (2)(iv), (ix), (xi), s.57(iii): the whole sum, less the
     * expenditure laid out for it (D-203).
     */
    private static Money inFull(Lines lines, String where, OtherSourceItem item) {
        InFullRow kind = IN_FULL.get(item.kind());
        RuleId row = lines.row(kind.ruleSet());
        Money income = lines.applied(row, kind.base(), item.received());
        lines.add(row, StepKind.INCOME, item.received(), income, where + ": " + kind.what());
        return income.minus(otherExpenditure(lines, where, item));
    }

    /**
     * S2 s.93(1)(e); 1961 s.57(iii): interest expense and other expenditure laid out for the income; a banker's
     * commission is for interest on securities only, so a nil line here.
     */
    private static Money otherExpenditure(Lines lines, String where, OtherSourceItem item) {
        if (item.bankerCommission().isGreaterThan(Money.ZERO)) {
            lines.nil(lines.row(RuleSetIds.RS_OS_BANKER_COMMISSION), item.bankerCommission(), where
                    + ": a banker's commission is deducted against interest on securities only");
        }
        Money expenses = item.interestExpense().plus(item.otherExpenditure());
        return expenses.isGreaterThan(Money.ZERO)
                ? lines.deduction(lines.row(RuleSetIds.RS_OS_OTHER_EXPENDITURE), EXPENDITURE, item, expenses, where)
                : Money.ZERO;
    }

    /** S2 s.92(2)(k); 1961 s.56(2)(xii): the holding's specified sum, A − B − C, nil where B and C exceed A. */
    private static Money businessTrustIncome(Lines lines, int position, BusinessTrustUnits units) {
        Money income = lines.applied(lines.row(RuleSetIds.RS_OS_BUSINESS_TRUST), BUSINESS_TRUST, units.specifiedSum());
        lines.add(lines.row(RuleSetIds.RS_OS_BUSINESS_TRUST), StepKind.INCOME, units.distributed(), income, "business trust holding "
                + position + ": " + units.distributed() + " distributed less " + units.issuedAt() + " issued and "
                + units.chargedEarlier() + " charged earlier, never below nil");
        return income;
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
            return lines.nil(lines.row(RuleSetIds.RS_OS_DIVIDEND_INTEREST), interest.claimed(), "dividend and unit income"
                    + UNTAXED_ABROAD);
        }
        Money allowed = interest.allowed();
        lines.add(lines.row(RuleSetIds.RS_OS_DIVIDEND_INTEREST), StepKind.DEDUCTION, interest.claimed(), allowed,
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
     * @param receivedOnDividends the part of {@code base} received as dividends
     * @param claimed the interest expense claimed against dividend and unit items
     * @param claimedOnDividends the part of {@code claimed} entered against dividend items
     * @param limit the row's share of {@code base}
     * @param untaxedAbroad whether any of it is payable outside India untaxed, which bars all of it
     */
    record DividendInterest(Money base, Money receivedOnDividends, Money claimed, Money claimedOnDividends, Money limit,
            boolean untaxedAbroad) {

        /** The interest allowed: nil where barred, else the claim within the limit. */
        Money allowed() {
            return untaxedAbroad ? Money.ZERO : Money.min(claimed, limit);
        }
    }

    static DividendInterest dividendInterest(NodeContext ctx, List<OtherSourceItem> items) {
        Money base = Money.ZERO;
        Money receivedOnDividends = Money.ZERO;
        Money claimed = Money.ZERO;
        Money claimedOnDividends = Money.ZERO;
        boolean untaxedAbroad = false;
        for (OtherSourceItem item : items) {
            if (item.kind() == OtherSourceKind.DIVIDEND || item.kind() == OtherSourceKind.UNIT_INCOME) {
                base = base.plus(item.received());
                claimed = claimed.plus(item.interestExpense());
                untaxedAbroad |= item.payableOutsideIndiaUntaxed() && item.interestExpense().isGreaterThan(Money.ZERO);
                if (item.kind() == OtherSourceKind.DIVIDEND) {
                    receivedOnDividends = receivedOnDividends.plus(item.received());
                    claimedOnDividends = claimedOnDividends.plus(item.interestExpense());
                }
            }
        }
        Money limit = TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_OS_DIVIDEND_INTEREST), ctx.inputs().temporal(),
                CapContext.of(DIVIDEND, base)).value();
        return new DividendInterest(base, receivedOnDividends, claimed, claimedOnDividends, limit, untaxedAbroad);
    }

    /**
     * S2 Schedule II Sl. 3(a), 4(a) with S4 r.277; 1961 s.10(11), (12) provisos with Rules 1962 r.9D: the interest
     * accrued in the fund's taxable contribution account, all of it income, and this year's contribution beyond the
     * threshold, which is what entered that account this year.
     */
    private static Money providentFundInterest(Lines lines, int position, ProvidentFundAccount account) {
        String where = "provident fund " + position;
        Money excess = lines.providentFundThreshold().excess(account.contributed(), account.employerContributes());
        lines.add(lines.row(RuleSetIds.RS_OS_PF_TAXABLE_INTEREST), StepKind.MEASURE, account.contributed(), excess,
                where + ": the contribution beyond the threshold enters the taxable contribution account");
        lines.add(lines.row(RuleSetIds.RS_OS_PF_TAXABLE_INTEREST), StepKind.INCOME, account.taxableAccountInterest(),
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
                    lines.add(ctx.ruleIdOf(RuleSetIds.RS_OS_GIFT), StepKind.EXEMPTION, gift.value(), gift.value(),
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

    /** Winnings and the kinds still unbuilt are refused by name rather than computed as something else. */
    private static void requireBuiltKinds(List<OtherSourceItem> items) {
        for (int position = 0; position < items.size(); position++) {
            OtherSourceKind kind = items.get(position).kind();
            if (kind == OtherSourceKind.WINNINGS) {
                throw new IllegalArgumentException("Item " + position + ": winnings from lotteries, races, games or"
                        + " betting are taxed at a rate of their own, which is deferred in the coverage register"
                        + " (Income-tax Act, 1961 s.115BB) and not built");
            }
            if (BUSINESS_HEAD_KINDS.contains(kind)) {
                throw new IllegalArgumentException("Item " + position + ": " + kind + " takes the business head's"
                        + " deductions (S2 ss.28, 29(1)(e), 33; Income-tax Act, 1961 ss.30–32, 36(1)(va)), which are"
                        + " deferred in the coverage register with that head (D-202)");
            }
        }
    }

    /** The node's trace lines, numbered as they are added, each citing the version of its row that governs. */
    static final class Lines {

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

        /** The row of {@code set} the regime applies (D-252). */
        RuleId row(RuleSetId set) {
            return ctx.ruleIdOf(set);
        }

        /** The row of a set the year may not hold. */
        Optional<RuleId> optionalRow(RuleSetId set) {
            return ctx.optionalRuleOf(set).map(RuleRow::id);
        }

        FamilyPensionShare familyPensionShare(RuleId row) {
            return RuleDefinitions.require(version(row).definition(), FamilyPensionShare.class, row);
        }

        GiftThreshold giftThreshold() {
            return RuleDefinitions.require(version(ctx.ruleIdOf(RuleSetIds.RS_OS_GIFT)).definition(), GiftThreshold.class,
                    ctx.ruleIdOf(RuleSetIds.RS_OS_GIFT));
        }

        /** One INCOME line for a gift or a total of gifts: what was received, what of it is charged. */
        Money chargedGift(String what, Money received, Money chargedAmount) {
            if (received.isGreaterThan(Money.ZERO)) {
                add(ctx.ruleIdOf(RuleSetIds.RS_OS_GIFT), StepKind.INCOME, received, chargedAmount, what);
            }
            return chargedAmount;
        }

        ProvidentFundThreshold providentFundThreshold() {
            return RuleDefinitions.require(version(ctx.ruleIdOf(RuleSetIds.RS_OS_PF_TAXABLE_INTEREST)).definition(),
                    ProvidentFundThreshold.class, ctx.ruleIdOf(RuleSetIds.RS_OS_PF_TAXABLE_INTEREST));
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
