package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.OtherSourceItem;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.definition.FamilyPensionShare;
import com.thiru.wealthlens.taxengine.rules.definition.ProvidentFundThreshold;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
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
import java.util.Optional;

/**
 * The other-sources head's trace lines, numbered as they are added, each citing the version of its row that governs.
 * Shared by {@link OtherSourcesNode}, {@link GiftIncome}, {@link DividendInterestAllowance} and {@link LifePolicySums},
 * so the head's lines are one numbered sequence whichever concern writes them (D-270).
 */
final class OtherSourcesLines {

    /** Why an expense on income payable outside India untaxed is nil, said wherever one is refused. */
    static final String UNTAXED_ABROAD = ": payable outside India untaxed (S2 s.94(1)(b), (c); 1961"
            + " s.58(1)(a)(ii), (iii))";

    private final NodeContext ctx;
    private final TemporalContext temporal;
    private final List<TraceLine> lines = new ArrayList<>();

    OtherSourcesLines(NodeContext ctx, TemporalContext temporal) {
        this.ctx = ctx;
        this.temporal = temporal;
    }

    NodeContext ctx() {
        return ctx;
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

    ProvidentFundThreshold providentFundThreshold() {
        return RuleDefinitions.require(version(ctx.ruleIdOf(RuleSetIds.RS_OS_PF_TAXABLE_INTEREST)).definition(),
                ProvidentFundThreshold.class, ctx.ruleIdOf(RuleSetIds.RS_OS_PF_TAXABLE_INTEREST));
    }

    RuleVersion version(RuleId row) {
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
