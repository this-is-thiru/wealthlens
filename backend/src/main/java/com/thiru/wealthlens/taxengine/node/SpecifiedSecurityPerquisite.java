package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerShareAllotment;
import com.thiru.wealthlens.taxengine.input.FairMarketValueBasis;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.definition.DayWindow;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Specified securities and sweat equity shares as a perquisite, for {@link PerquisiteNode} (S2 s.17(1)(d), (4)(h);
 * S4 r.15(6)–(8); 1961 s.17(2)(vi); Rules 1962 r.3(8)–(9); D-121).
 *
 * <p>The fair market value on the exercise date — the average of the day's opening and closing prices, the closest
 * preceding close where the share did not trade, or a merchant banker's value on a date within the Rule's window
 * before exercise — less what the employee paid; every employee, the current or a former employer. The value is income
 * of the tax year the shares were allotted in; an eligible start-up's deferral changes when the tax is paid, not this.
 */
final class SpecifiedSecurityPerquisite {

    static final Set<RuleId> RULES = Set.of(RuleIds.PERQ_SPECIFIED_SECURITY, RuleIds.PERQ_SPECIFIED_DATE);

    private static final BaseId SECURITY_VALUE = new BaseId("SECURITY_VALUE");

    private SpecifiedSecurityPerquisite() {
    }

    /** One line per allotment, numbered from {@code firstSequence}. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<TraceLine> lines = new ArrayList<>();
        TemporalContext t = ctx.inputs().temporal();
        for (EmployerShareAllotment allotment : ctx.inputs().entered().shareAllotments()) {
            PerquisiteNode.requireEnteredEmployment(ctx, allotment.employment(), "share allotment");
            if (!t.taxYear().contains(allotment.allottedOn())) {
                throw new IllegalArgumentException("Shares allotted on " + allotment.allottedOn() + " are income of"
                        + " the tax year they were allotted in, not " + t.taxYear().label());
            }
            Money fairMarketValue = fairMarketValue(ctx, allotment);
            Money paid = allotment.pricePaidPerUnit().times(allotment.units());
            Money value = TemporallyApportionedCap.apply(ctx.rule(RuleIds.PERQ_SPECIFIED_SECURITY), t,
                    CapContext.of(SECURITY_VALUE, fairMarketValue.minus(paid).floorAtZero())).value();
            lines.add(TraceLine.of(firstSequence + lines.size(), NodeIds.SALARY_PERQUISITES,
                            RuleIds.PERQ_SPECIFIED_SECURITY, StepKind.INCOME, MoneyValue.of(fairMarketValue),
                            MoneyValue.of(value), TemporallyApportionedCap.governingVersion(
                                    ctx.rule(RuleIds.PERQ_SPECIFIED_SECURITY), t).provenance())
                    .withLimitedBy(allotment.units() + " at a fair market value of " + fairMarketValue + " in all, "
                            + describe(allotment.basis()) + ", less " + paid + " paid"));
        }
        return lines;
    }

    /** The fair market value of all the units on the exercise date, by the entered basis. */
    private static Money fairMarketValue(NodeContext ctx, EmployerShareAllotment allotment) {
        int units = allotment.units();
        return switch (allotment.basis()) {
            case FairMarketValueBasis.ListedTraded traded ->
                    traded.openingPrice().plus(traded.closingPrice()).times(units).apportioned(1, 2);
            case FairMarketValueBasis.ListedNotTraded untraded -> untraded.closingPrice().times(units);
            case FairMarketValueBasis.MerchantBankerValue banker -> {
                requireWithinWindow(ctx, allotment, banker);
                yield banker.valuePerUnit().times(units);
            }
        };
    }

    /** A merchant banker's specified date lies no more than the Rule's window of days before the exercise date. */
    private static void requireWithinWindow(NodeContext ctx, EmployerShareAllotment allotment,
            FairMarketValueBasis.MerchantBankerValue banker) {
        int days = window(ctx).days();
        long before = ChronoUnit.DAYS.between(banker.specifiedDate(), allotment.exercisedOn());
        if (before > days) {
            throw new IllegalArgumentException("A merchant banker's specified date may be no more than " + days
                    + " days before the exercise of the option (S4 r.15(8)(l); Rules 1962 r.3(8)(iv)(e)); "
                    + banker.specifiedDate() + " is " + before + " days before " + allotment.exercisedOn());
        }
    }

    private static DayWindow window(NodeContext ctx) {
        RuleId ruleId = RuleIds.PERQ_SPECIFIED_DATE;
        return RuleDefinitions.require(ctx.rule(ruleId).versionAt(ctx.inputs().temporal()).definition(), DayWindow.class, ruleId);
    }

    private static String describe(FairMarketValueBasis basis) {
        return switch (basis) {
            case FairMarketValueBasis.ListedTraded traded -> "the average of the opening " + traded.openingPrice()
                    + " and the closing " + traded.closingPrice();
            case FairMarketValueBasis.ListedNotTraded untraded -> "the closing " + untraded.closingPrice() + " on "
                    + untraded.onDate();
            case FairMarketValueBasis.MerchantBankerValue banker -> "a merchant banker's " + banker.valuePerUnit()
                    + " on " + banker.specifiedDate();
        };
    }
}
