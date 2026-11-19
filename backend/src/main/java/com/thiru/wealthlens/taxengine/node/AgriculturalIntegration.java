package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.SlabLadder;
import com.thiru.wealthlens.taxengine.rules.condition.Declarations;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.DeclarationCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonFact;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.Notice;
import com.thiru.wealthlens.taxengine.trace.Severity;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.Map;
import java.util.Optional;

/**
 * Net agricultural income taken into account for the rate — partial integration (FA 2026 s.2(2) for TY 2025-26, s.3(2)
 * for TY 2026-27; W-258, D-778). Where the declared net agricultural income exceeds the limit and the income at slab
 * rates exceeds the maximum amount not chargeable, the slab tax is the ladder's tax on the income plus the agricultural
 * income (X), less the ladder's tax on the agricultural income plus that maximum (Y). The agricultural income is never
 * charged itself; it only lifts the rate the income is charged at.
 *
 * <p>The income is total income less the income charged at special rates, as ITR-2 Part B-TI rows 13 – 15 read "total
 * income" (W-258 C4a). The limit, the maximum and whom each applies to are {@code RS-RATE-AGRICULTURAL}'s rows, so a
 * Finance Act that moves them is a new version, not a change here.
 *
 * @param income the income at slab rates
 * @param net the declared net agricultural income
 * @param row the row that holds
 * @param version the row's version governing the year
 * @param maximum the maximum amount not chargeable, the row's cap
 * @param aggregateTax X, the ladder's tax on {@code income} plus {@code net}
 * @param rebate Y, the ladder's tax on {@code net} plus {@code maximum}
 */
record AgriculturalIntegration(Money income, Money net, RuleId row, RuleVersion version, Money maximum,
        Money aggregateTax, Money rebate) {

    /** The question the rows read: the person's net agricultural income for the year. */
    static final DeclarationCode NET_AGRICULTURAL_INCOME = new DeclarationCode("NET_AGRICULTURAL_INCOME");

    /**
     * The integration of this income, where the question is answered, a row holds and the income exceeds its maximum;
     * the caller's node declares {@code RS-RATE-AGRICULTURAL}. The answer is checked before any row is tested, since a
     * row's condition throws on an unanswered amount; {@link #unanswered} says when it was not answered.
     */
    static Optional<AgriculturalIntegration> of(NodeContext ctx, SlabLadder ladder, Money income) {
        Declarations answers = ctx.conditions().declarations();
        if (!answers.isAnswered(NET_AGRICULTURAL_INCOME)) {
            return Optional.empty();
        }
        Optional<RuleRow> row = ctx.optionalRuleOf(RuleSetIds.RS_RATE_AGRICULTURAL);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        RuleVersion version = TemporallyApportionedCap.governingVersion(row.get(), ctx.inputs().temporal());
        Money maximum = version.cap().orElseThrow(() -> new IllegalStateException(
                row.get().id() + " states no maximum amount not chargeable")).apply(CapContext.of(ladder.base(), income))
                .value();
        if (!income.isGreaterThan(maximum)) {
            return Optional.empty();
        }
        Money net = answers.amount(NET_AGRICULTURAL_INCOME);
        return Optional.of(new AgriculturalIntegration(income, net, row.get().id(), version, maximum,
                taxOn(ladder, income.plus(net)), taxOn(ladder, net.plus(maximum))));
    }

    /**
     * The notice for a year that asks the question when it is not answered: the tax then assumes no net agricultural
     * income the rates take in (W-258 C3a), and says so rather than refusing the figure.
     */
    static Optional<Notice> unanswered(NodeContext ctx) {
        boolean asked = ctx.pack().declarations().containsKey(NET_AGRICULTURAL_INCOME);
        if (!asked || ctx.conditions().declarations().isAnswered(NET_AGRICULTURAL_INCOME)) {
            return Optional.empty();
        }
        return Optional.of(Notice.of(Severity.ACTION_REQUIRED, "Net agricultural income is not answered, so this tax"
                + " assumes none that the law brings into the rates; answer " + NET_AGRICULTURAL_INCOME
                + " to include it."));
    }

    /** Z: the slab tax charged, X less Y. */
    Money tax() {
        return aggregateTax.minus(rebate);
    }

    /** The aggregate income X is the tax on: the income plus the net agricultural income. */
    Money aggregateIncome() {
        return income.plus(net);
    }

    /** Why the slab line is on the aggregate income. */
    Reason aggregateReason() {
        return new Reason(ReasonCodes.TAX_ON_AGGREGATE_INCOME, Map.of(
                "income", new ReasonFact.MoneyFact(income), "agricultural", new ReasonFact.MoneyFact(net)));
    }

    /** The line taking Y off: the row, from the agricultural income plus the maximum to the tax on it. */
    TraceLine rebateLine(int sequence) {
        return TraceLine.of(sequence, NodeIds.TAX_BYBASIS, row, StepKind.RELIEF, MoneyValue.of(net.plus(maximum)),
                        MoneyValue.of(rebate), version.provenance())
                .withLimitedBy("net agricultural income " + net + " plus the maximum amount not chargeable " + maximum
                        + ", at the slab rates")
                .withReason(new Reason(ReasonCodes.AGRICULTURAL_INCOME_REBATE, Map.of(
                        "agricultural", new ReasonFact.MoneyFact(net), "maximum", new ReasonFact.MoneyFact(maximum))));
    }

    private static Money taxOn(SlabLadder ladder, Money amount) {
        return ladder.apply(CapContext.of(ladder.base(), amount)).value();
    }
}
