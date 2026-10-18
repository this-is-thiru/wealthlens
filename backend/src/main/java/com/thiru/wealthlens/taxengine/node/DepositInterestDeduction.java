package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.input.OtherSourceKind;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.RegimeVerdict;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapResult;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * s.153 — interest on deposits with a bank, a co-operative bank or a Post Office — for {@link ChapterEightNode} (S2;
 * 1961 ss.80TTA, 80TTB; D-195). A senior citizen deducts the interest on any such deposit up to the senior row's
 * limit, and the savings row is not reached (s.153(2)(b)); anyone else, a HUF included, the savings-account interest
 * up to the savings row's, and a time deposit's interest is a nil line (s.153(2)(a)). It runs before s.133, whose
 * adjusted gross total income is net of it.
 *
 * <p>The interest is what gross total income includes — each item net of the expenditure the head deducted against
 * it ({@link OtherSourcesNode#interestIncluded}), the items together floored at nil (OQ-25, D-228, D-231).
 */
final class DepositInterestDeduction {

    /** The rows, and the head's rows the net interest is read through. */
    static final Set<RuleId> RULES = Set.of(RuleIds.DED_SAVINGS_INTEREST, RuleIds.DED_DEPOSIT_INTEREST_SENIOR,
            RuleIds.OS_INTEREST, RuleIds.OS_BANKER_COMMISSION, RuleIds.OS_OTHER_EXPENDITURE);

    private static final BaseId INTEREST = new BaseId("INTEREST");

    private DepositInterestDeduction() {
    }

    /** The lines, numbered from {@code firstSequence}; none where no deposit interest is entered. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<OtherSourceItem> deposits = deposits(ctx);
        if (deposits.isEmpty()) {
            return List.of();
        }
        List<OtherSourceItem> savingsItems = deposits.stream()
                .filter(item -> item.kind() == OtherSourceKind.SAVINGS_DEPOSIT_INTEREST).toList();
        List<OtherSourceItem> depositItems = deposits.stream()
                .filter(item -> item.kind() == OtherSourceKind.DEPOSIT_INTEREST).toList();
        List<TraceLine> lines = new ArrayList<>();
        if (isSenior(ctx)) {
            add(ctx, RuleIds.DED_DEPOSIT_INTEREST_SENIOR, deposits, firstSequence, lines);
            return lines;
        }
        Money other = included(ctx, depositItems);
        if (!savingsItems.isEmpty() && included(ctx, savingsItems).isGreaterThan(Money.ZERO)) {
            add(ctx, RuleIds.DED_SAVINGS_INTEREST, savingsItems, firstSequence, lines);
        }
        if (other.isGreaterThan(Money.ZERO)) {
            lines.add(line(ctx, RuleIds.DED_SAVINGS_INTEREST, firstSequence + lines.size(), other, Money.ZERO,
                    "s.153(2)(a) is savings-account interest only, time deposits excluded; deposit interest is"
                            + " deducted only for a senior citizen (s.153(2)(b); 1961 ss.80TTA, 80TTB)"));
        }
        return lines;
    }

    /** Whether any interest on a deposit with a bank, a co-operative bank or a Post Office is entered. */
    static boolean entered(NodeContext ctx) {
        return !deposits(ctx).isEmpty();
    }

    /**
     * A senior citizen: an individual resident in India of sixty or more at any time in the year (S2 s.2(100); 1961
     * s.80TTB Explanation) — the senior row's conditions. A HUF has no age, so it is never one.
     */
    private static boolean isSenior(NodeContext ctx) {
        return ctx.inputs().assessee() instanceof Individual
                && version(ctx, RuleIds.DED_DEPOSIT_INTEREST_SENIOR).conditions().stream()
                        .allMatch(condition -> condition.holds(ctx.conditions()));
    }

    private static void add(NodeContext ctx, RuleId row, List<OtherSourceItem> items, int firstSequence,
            List<TraceLine> lines) {
        Money interest = included(ctx, items);
        RegimeVerdict verdict = ctx.inRegime(row, version(ctx, row));
        if (!verdict.applies()) {
            lines.add(line(ctx, row, firstSequence + lines.size(), interest, Money.ZERO, verdict.whyNot()));
            return;
        }
        CapResult allowed = TemporallyApportionedCap.apply(ctx.rule(row), ctx.inputs().temporal(),
                CapContext.of(INTEREST, interest));
        String why = interest.isLessThan(received(items)) ? allowed.limitedBy() + "; interest net of the expenditure"
                + " deducted against it (S2 s.93(1)(e); 1961 s.57(iii); D-228)" : allowed.limitedBy();
        lines.add(line(ctx, row, firstSequence + lines.size(), interest, allowed.value(), why));
    }

    /** What gross total income includes of these items: each net of its expenditure, together floored at nil. */
    private static Money included(NodeContext ctx, List<OtherSourceItem> items) {
        return items.stream().map(item -> OtherSourcesNode.interestIncluded(ctx, item)).reduce(Money.ZERO, Money::plus)
                .floorAtZero();
    }

    private static TraceLine line(NodeContext ctx, RuleId row, int sequence, Money interest, Money allowed,
            String limitedBy) {
        return TraceLine.of(sequence, NodeIds.CHAPTERVIII, row, StepKind.DEDUCTION, MoneyValue.of(interest),
                        MoneyValue.of(allowed), version(ctx, row).provenance())
                .withLimitedBy(limitedBy);
    }

    private static List<OtherSourceItem> deposits(NodeContext ctx) {
        return ctx.inputs().entered().otherSourceItems().stream()
                .filter(item -> item.kind() == OtherSourceKind.SAVINGS_DEPOSIT_INTEREST
                        || item.kind() == OtherSourceKind.DEPOSIT_INTEREST)
                .toList();
    }

    private static Money received(List<OtherSourceItem> items) {
        return items.stream().map(OtherSourceItem::received).reduce(Money.ZERO, Money::plus);
    }

    private static RuleVersion version(NodeContext ctx, RuleId row) {
        return TemporallyApportionedCap.governingVersion(ctx.rule(row), ctx.inputs().temporal());
    }
}
