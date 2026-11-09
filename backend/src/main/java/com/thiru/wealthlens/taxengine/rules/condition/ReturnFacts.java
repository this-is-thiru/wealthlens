package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;
import java.util.Set;

/**
 * The year's facts a return form reads about the computed return — r.164(2)(b) – (d) and (3)(e) of the Income-tax
 * Rules, 2026 ask whether a head shows a loss, a loss brought forward or to carry forward, which capital gains there
 * are, and whether a deduction was claimed (W-090c1b1, D-527; W-090c1b2, D-528). Facts, never a form label: which head, part or row a
 * form cares about is the form's data.
 *
 * <p>{@link #NONE} holds nothing, and every read of a fact not held throws, as {@link ConditionContext} does for
 * every fact: a head the engine did not compute (business income) is unknown, never "no loss".
 *
 * @param incomeByHead each computed head's income of the year, before set-off; below nil is the year's loss. The
 *     capital-gains figure is the head's surviving income, never below nil: its loss of the year is in
 *     {@code lossToCarryForwardByHead}
 * @param capitalGainsByPart the year's capital gains by part, after the head's own netting
 * @param deductionsAllowed the rows whose deduction line allowed more than nil — the amount allowed being the line's
 *     {@code out}, which the s.93 rows' lines follow ({@code OtherSourcesLines}, {@code DividendInterestAllowance})
 * @param lossBroughtForwardByHead each computed head's loss brought forward into the year (W-090c1b2, D-528)
 * @param lossToCarryForwardByHead each computed head's loss left to carry forward (W-090c1b2, D-528)
 * @param known whether these facts were supplied at all
 */
public record ReturnFacts(
        Map<HeadCode, Money> incomeByHead,
        Map<CapitalGainsPart, Money> capitalGainsByPart,
        Set<RuleId> deductionsAllowed,
        Map<HeadCode, Money> lossBroughtForwardByHead,
        Map<HeadCode, Money> lossToCarryForwardByHead,
        boolean known) {

    /** No facts: a context for a rule, not a return; every read throws. */
    public static final ReturnFacts NONE = new ReturnFacts(Map.of(), Map.of(), Set.of(), Map.of(), Map.of(), false);

    public ReturnFacts {
        if (incomeByHead == null || capitalGainsByPart == null || deductionsAllowed == null
                || lossBroughtForwardByHead == null || lossToCarryForwardByHead == null) {
            throw new IllegalArgumentException("ReturnFacts collections may not be null; pass empty ones");
        }
        incomeByHead = Map.copyOf(incomeByHead);
        capitalGainsByPart = Map.copyOf(capitalGainsByPart);
        deductionsAllowed = Set.copyOf(deductionsAllowed);
        lossBroughtForwardByHead = Map.copyOf(lossBroughtForwardByHead);
        lossToCarryForwardByHead = Map.copyOf(lossToCarryForwardByHead);
    }

    /** The year's facts of a computed return. */
    public static ReturnFacts of(
            Map<HeadCode, Money> incomeByHead, Map<CapitalGainsPart, Money> capitalGainsByPart,
            Set<RuleId> deductionsAllowed, Map<HeadCode, Money> lossBroughtForwardByHead,
            Map<HeadCode, Money> lossToCarryForwardByHead) {
        return new ReturnFacts(incomeByHead, capitalGainsByPart, deductionsAllowed, lossBroughtForwardByHead,
                lossToCarryForwardByHead, true);
    }

    /**
     * A head's income of the year, below nil for a loss.
     *
     * @throws IllegalArgumentException if no return's facts were supplied, or the head was not computed
     */
    public Money incomeUnder(HeadCode head) {
        return under(incomeByHead, head);
    }

    /**
     * A head's loss brought forward into the year, nil or more.
     *
     * @throws IllegalArgumentException if no return's facts were supplied, or the head was not computed
     */
    public Money lossBroughtForward(HeadCode head) {
        return under(lossBroughtForwardByHead, head);
    }

    /**
     * A head's loss left to carry forward, nil or more.
     *
     * @throws IllegalArgumentException if no return's facts were supplied, or the head was not computed
     */
    public Money lossToCarryForward(HeadCode head) {
        return under(lossToCarryForwardByHead, head);
    }

    private Money under(Map<HeadCode, Money> byHead, HeadCode head) {
        requireKnown();
        Money amount = byHead.get(head);
        if (amount == null) {
            throw new IllegalArgumentException("The head " + head + " was not computed; the return computed "
                    + byHead.keySet());
        }
        return amount;
    }

    /** The year's capital gains by part, parts with no gain absent or nil. */
    public Map<CapitalGainsPart, Money> capitalGains() {
        requireKnown();
        return capitalGainsByPart;
    }

    /** Whether the row allowed a deduction above nil. */
    public boolean allowedDeductionUnder(RuleId row) {
        requireKnown();
        return deductionsAllowed.contains(row);
    }

    private void requireKnown() {
        if (!known) {
            throw new IllegalArgumentException(
                    "No computed return's facts were supplied to this context; a form's condition reads a return");
        }
    }
}
