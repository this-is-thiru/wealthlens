package com.thiru.wealthlens.taxengine.result;

import com.thiru.wealthlens.taxengine.pipeline.CapitalGainsHead;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
import com.thiru.wealthlens.taxengine.pipeline.LossesAcrossYears;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeValue;
import com.thiru.wealthlens.taxengine.pipeline.PipelineRunner;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.condition.ReturnFacts;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A run's {@link ReturnFacts}: what a return form reads, taken from what the run already holds (W-090c1b1, D-527).
 *
 * <p>The heads are the four {@code SetOffNode} reads, before set-off; business income is not computed, so it is
 * absent. The capital gains are {@code capitalgains.income}'s parts. A deduction allowed is a {@code DEDUCTION} line
 * whose {@code out}, the amount allowed, is above nil — the trace being the result, a fact is read from it rather than
 * computed twice. The losses across years are {@code setoff}'s ({@link LossesAcrossYears}; W-090c1b2, D-528).
 */
final class ReturnFactsProjection {

    private ReturnFactsProjection() {
    }

    static ReturnFacts of(PipelineRunner.Result run) {
        CapitalGainsHead capitalGains = (CapitalGainsHead) run.valueOf(NodeIds.CAPITALGAINS_INCOME);
        Map<HeadCode, Money> heads = new EnumMap<>(HeadCode.class);
        heads.put(HeadCode.SALARIES, money(run, NodeIds.SALARY_INCOME));
        heads.put(HeadCode.HOUSE_PROPERTY, money(run, NodeIds.HOUSEPROPERTY_INCOME));
        heads.put(HeadCode.OTHER_SOURCES, money(run, NodeIds.OTHERSOURCES_INCOME));
        heads.put(HeadCode.CAPITAL_GAINS, capitalGains.total());
        LossesAcrossYears losses = ((HeadsAfterSetOff) run.valueOf(NodeIds.SETOFF)).losses();
        return ReturnFacts.of(heads, capitalGains.parts(), deductionsAllowed(run), losses.broughtForward(),
                losses.toCarryForward());
    }

    private static Money money(PipelineRunner.Result run, NodeId node) {
        NodeValue value = run.valueOf(node);
        if (!(value instanceof MoneyValue amount)) {
            throw new IllegalArgumentException(node + " holds a " + value.getClass().getSimpleName() + ", not an amount");
        }
        return amount.asMoney();
    }

    private static Set<RuleId> deductionsAllowed(PipelineRunner.Result run) {
        Set<RuleId> rows = new LinkedHashSet<>();
        for (TraceLine line : run.trace()) {
            if (line.kind() == StepKind.DEDUCTION && line.out() instanceof MoneyValue allowed
                    && allowed.asMoney().isGreaterThan(Money.ZERO)) {
                rows.add(line.rule());
            }
        }
        return rows;
    }
}
