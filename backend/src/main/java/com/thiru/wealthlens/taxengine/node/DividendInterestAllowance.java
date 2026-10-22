package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import java.util.List;

/**
 * The interest expense allowed against the year's dividend and unit income (S2 s.93(2); 1961 s.57 proviso; D-200) — a
 * part of {@link OtherSourcesNode}, kept apart because its limit is the year's, not an item's, and the surcharge reads
 * the same figures ({@link SurchargeNode}) (D-270).
 */
final class DividendInterestAllowance {

    private static final BaseId DIVIDEND = new BaseId("DIVIDEND");

    private DividendInterestAllowance() {
    }

    /**
     * S2 s.93(2) as substituted by FA 2026 s.44(b); 1961 s.57 proviso (FA 2020 s.30(b)): the interest expense against
     * the year's dividend and unit income, within the row's share of that income — the year's, not each item's.
     */
    static Money allowed(OtherSourcesLines lines, List<OtherSourceItem> items) {
        DividendInterest interest = of(lines.ctx(), items);
        if (interest.claimed().equals(Money.ZERO)) {
            return Money.ZERO;
        }
        if (interest.untaxedAbroad()) {
            return lines.nil(lines.row(RuleSetIds.RS_OS_DIVIDEND_INTEREST), interest.claimed(), "dividend and unit income"
                    + OtherSourcesLines.UNTAXED_ABROAD);
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

    /** The year's figures over the items the row reaches — its conditions on the item (W-153b, D-320). */
    static DividendInterest of(NodeContext ctx, List<OtherSourceItem> items) {
        Money base = Money.ZERO;
        Money receivedOnDividends = Money.ZERO;
        Money claimed = Money.ZERO;
        Money claimedOnDividends = Money.ZERO;
        boolean untaxedAbroad = false;
        RuleId row = ctx.ruleIdOf(RuleSetIds.RS_OS_DIVIDEND_INTEREST);
        for (OtherSourceItem item : items) {
            if (OtherSourcesNode.reaches(ctx, row, item)) {
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
}
