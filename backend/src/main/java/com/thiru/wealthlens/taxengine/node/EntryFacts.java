package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.RetirementPayment;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapShape;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.CommutedPensionShare;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which facts a retirement payment carries beyond its amount, as its entry's rows read them (D-336 R6; W-155c2,
 * D-341): a fact the rows read is required, and one they do not read is refused, since it would be ignored.
 *
 * <p>Each fact feeds one thing a row can read: the separation the base {@link #AVERAGE_SALARY_BY_COMPLETED_YEARS}, the
 * leave figures {@link #LEAVE_SALARY_AT_CREDIT} and {@link #AVERAGE_MONTHLY_SALARY}, the statutory amount
 * {@link #AMOUNT_UNDER_STATUTE}, the commuted value a {@link CommutedPensionShare}, and arrears relief an
 * {@code RS-RET-BAR} row. Which entry reads which is the rows' to say, so no entry is named here.
 */
final class EntryFacts {

    static final BaseId AVERAGE_SALARY_BY_COMPLETED_YEARS = new BaseId("AVERAGE_SALARY_BY_COMPLETED_YEARS");
    static final BaseId LEAVE_SALARY_AT_CREDIT = new BaseId("LEAVE_SALARY_AT_CREDIT");
    static final BaseId AVERAGE_MONTHLY_SALARY = new BaseId("AVERAGE_MONTHLY_SALARY");
    static final BaseId AMOUNT_UNDER_STATUTE = new BaseId("AMOUNT_UNDER_STATUTE");

    private EntryFacts() {
    }

    /**
     * Refuses a payment lacking a fact its entry row reads, or carrying one no row reads.
     *
     * @param row the payment's entry row
     * @param version that row's version governing the run
     */
    static void requireWhatTheRowsRead(NodeContext ctx, RetirementPayment payment, RuleId row, RuleVersion version) {
        Set<BaseId> read = version.cap().map(CapShape::amountsRead).orElse(Set.of());
        require(payment, row, read.contains(AVERAGE_SALARY_BY_COMPLETED_YEARS), payment.separation().isPresent(),
                "separation");
        require(payment, row, read.contains(LEAVE_SALARY_AT_CREDIT) || read.contains(AVERAGE_MONTHLY_SALARY),
                payment.leave().isPresent(), "leave figures");
        require(payment, row, read.contains(AMOUNT_UNDER_STATUTE), payment.statutory().isPresent(),
                "amount under the statute");
        require(payment, row, version.definition().filter(CommutedPensionShare.class::isInstance).isPresent(),
                payment.commutedValueOfWholePension().isPresent(), "commuted value of the whole pension");
        if (payment.arrearsReliefAllowed() && !arrearsReliefIsRead(ctx, payment)) {
            throw new IllegalArgumentException(payment.component() + ": no " + RuleSetIds.RS_RET_BAR + " row reads"
                    + " relief for arrears for " + payment.entry() + "; it would be ignored, so it is refused");
        }
    }

    private static void require(RetirementPayment payment, RuleId row, boolean read, boolean given, String fact) {
        if (read && !given) {
            throw new IllegalArgumentException(payment.component() + ": " + payment.entry() + " is measured on the "
                    + fact + " (" + row + "); enter it");
        }
        if (!read && given) {
            throw new IllegalArgumentException(payment.component() + ": " + payment.entry() + " does not read a "
                    + fact + " (" + row + "); it would be ignored, so it is refused");
        }
    }

    /** Whether some bar row reaches the payment with relief allowed and not without it — whether the fact is read. */
    private static boolean arrearsReliefIsRead(NodeContext ctx, RetirementPayment payment) {
        ItemFacts facts = RetirementNode.factsOf(ctx, payment);
        List<RuleRow> bars = ctx.rulesOf(RuleSetIds.RS_RET_BAR);
        return !reaching(ctx, bars, withArrearsRelief(facts, true))
                .equals(reaching(ctx, bars, withArrearsRelief(facts, false)));
    }

    private static List<RuleId> reaching(NodeContext ctx, List<RuleRow> bars, ItemFacts facts) {
        return bars.stream().filter(row -> RowReach.holdsFor(ctx, row, facts)).map(RuleRow::id).toList();
    }

    private static ItemFacts withArrearsRelief(ItemFacts facts, boolean allowed) {
        Map<ItemFactId, String> codes = new HashMap<>(facts.codes());
        codes.put(RetirementNode.ARREARS_RELIEF_ALLOWED, Boolean.toString(allowed));
        return new ItemFacts(codes, facts.quantities(), facts.dates());
    }
}
