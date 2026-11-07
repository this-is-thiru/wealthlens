package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerGift;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gifts, vouchers and tokens from an employer as a perquisite, for {@link PerquisiteNode} (S4 r.15(5) Table IV Sl. 4;
 * Rules 1962 r.3(7)(iv); D-117).
 *
 * <p>All or nothing: an employer's gifts in the year, added up, are nil while "below" the limit and salary in full
 * once they reach it — not the excess (PERQ-GIFT-BOUNDARY-001). They are added up per employer, the Rules reading
 * "from the employer" (D-117 C4a).
 */
final class GiftPerquisite {

    private GiftPerquisite() {
    }

    /** One line per employer that gave anything, numbered from {@code firstSequence}. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        Map<Integer, Money> byEmployer = new TreeMap<>();
        for (EmployerGift gift : ctx.inputs().entered().gifts()) {
            PerquisiteNode.requireEnteredEmployment(ctx, gift.employment(), "gift");
            byEmployer.merge(gift.employment(), gift.amount(), Money::plus);
        }
        TemporalContext t = ctx.inputs().temporal();
        RuleRow rule = ctx.ruleOf(RuleSetIds.RS_PERQ_GIFT);
        Money limit = TemporallyApportionedCap.apply(rule, t, new CapContext(Map.of())).value();
        List<TraceLine> lines = new ArrayList<>();
        for (Map.Entry<Integer, Money> employer : byEmployer.entrySet()) {
            Money given = employer.getValue();
            boolean belowTheLimit = given.isLessThan(limit);
            lines.add(TraceLine.of(firstSequence + lines.size(), NodeIds.SALARY_PERQUISITES, ctx.ruleIdOf(RuleSetIds.RS_PERQ_GIFT),
                            StepKind.INCOME, MoneyValue.of(given), MoneyValue.of(belowTheLimit ? Money.ZERO : given),
                            TemporallyApportionedCap.governingVersion(rule, t).provenance())
                    .withLimitedBy("employment " + employer.getKey() + "'s gifts " + given
                            + (belowTheLimit ? " are below " : " reach ") + limit
                            + (belowTheLimit ? ", so nil" : ", so all of them")));
        }
        return lines;
    }
}
