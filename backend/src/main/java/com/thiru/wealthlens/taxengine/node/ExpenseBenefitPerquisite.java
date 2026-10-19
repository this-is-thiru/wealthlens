package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.EmployerExpenseBenefit;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Holidays, credit-card and club bills, and other benefits at arm's-length cost, for {@link PerquisiteNode} (S4 r.15(5)
 * Table IV Sl. 2, 5, 6, 9; Rules 1962 r.3(7)(ii), (v), (vi), (ix); D-119).
 *
 * <p>Each is its amount less the recovery. A credit card bill wholly for official purposes with the employer's records
 * is nil. A club bill is nil on the facts the year's rows name: in TY 2025-26 either business use with records or
 * uniform health-club facilities; in TY 2026-27 all of them together (OQ-12). Telephones are nil in both years.
 */
final class ExpenseBenefitPerquisite {

    static final Set<RuleSetId> RULES = Set.of(RuleSetIds.RS_PERQ_HOLIDAY, RuleSetIds.RS_PERQ_CREDIT_CARD, RuleSetIds.RS_PERQ_CLUB,
            RuleSetIds.RS_PERQ_CLUB_BUSINESS, RuleSetIds.RS_PERQ_CLUB_UNIFORM, RuleSetIds.RS_PERQ_CLUB_BUSINESS_UNIFORM,
            RuleSetIds.RS_PERQ_OTHER_BENEFIT);

    private static final BaseId BENEFIT_VALUE = new BaseId("BENEFIT_VALUE");

    private ExpenseBenefitPerquisite() {
    }

    /** One line per entry, numbered from {@code firstSequence}. */
    static List<TraceLine> lines(NodeContext ctx, int firstSequence) {
        List<TraceLine> lines = new ArrayList<>();
        for (EmployerExpenseBenefit benefit : ctx.inputs().entered().expenseBenefits()) {
            PerquisiteNode.requireEnteredEmployment(ctx, benefit.employment(), "expense benefit");
            int sequence = firstSequence + lines.size();
            lines.add(switch (benefit.kind()) {
                case HOLIDAY, HOLIDAY_OWN_FACILITY_AT_PUBLIC_VALUE, FAMILY_ON_OFFICIAL_TOUR, TOUR_EXTENDED_AS_VACATION ->
                        valued(ctx, ctx.ruleIdOf(RuleSetIds.RS_PERQ_HOLIDAY), benefit, sequence, benefit.kind().name());
                case CREDIT_CARD -> benefit.whollyForWork() && benefit.officialUse().journeysAndCertificateKept()
                        ? nil(ctx, ctx.ruleIdOf(RuleSetIds.RS_PERQ_CREDIT_CARD), benefit, sequence,
                                "wholly for official purposes, with the employer's details and certificate")
                        : valued(ctx, ctx.ruleIdOf(RuleSetIds.RS_PERQ_CREDIT_CARD), benefit, sequence, "credit card");
                case CLUB -> clubExclusion(ctx, benefit)
                        .map(ruleId -> nil(ctx, ruleId, benefit, sequence, "excluded by " + ruleId))
                        .orElseGet(() -> valued(ctx, ctx.ruleIdOf(RuleSetIds.RS_PERQ_CLUB), benefit, sequence, "club"));
                case OTHER -> valued(ctx, ctx.ruleIdOf(RuleSetIds.RS_PERQ_OTHER_BENEFIT), benefit, sequence, "at arm's-length cost");
                case TELEPHONE -> nil(ctx, ctx.ruleIdOf(RuleSetIds.RS_PERQ_OTHER_BENEFIT), benefit, sequence,
                        "expenses on telephones, a mobile phone's included, are excepted");
            });
        }
        return lines;
    }

    /** The row whose facts make a club bill nil this year, if they hold — by which rows the year's pack carries. */
    private static Optional<RuleId> clubExclusion(NodeContext ctx, EmployerExpenseBenefit club) {
        boolean businessWithRecords = club.whollyForWork() && club.officialUse().journeysAndCertificateKept();
        Optional<RuleId> businessUniform = ctx.optionalRuleOf(RuleSetIds.RS_PERQ_CLUB_BUSINESS_UNIFORM).map(RuleRow::id);
        if (businessUniform.isPresent()) {
            return businessWithRecords && club.healthClubUniformForAll() ? businessUniform : Optional.empty();
        }
        Optional<RuleId> business = ctx.optionalRuleOf(RuleSetIds.RS_PERQ_CLUB_BUSINESS).map(RuleRow::id);
        if (businessWithRecords && business.isPresent()) {
            return business;
        }
        Optional<RuleId> uniform = ctx.optionalRuleOf(RuleSetIds.RS_PERQ_CLUB_UNIFORM).map(RuleRow::id);
        if (club.healthClubUniformForAll() && uniform.isPresent()) {
            return uniform;
        }
        return Optional.empty();
    }

    private static TraceLine valued(NodeContext ctx, RuleId ruleId, EmployerExpenseBenefit benefit, int sequence,
            String what) {
        Money net = benefit.amount().minus(benefit.recovered()).floorAtZero();
        Money value = TemporallyApportionedCap.apply(ctx.rule(ruleId), ctx.inputs().temporal(),
                CapContext.of(BENEFIT_VALUE, net)).value();
        return ctx.traceLine(sequence, ruleId, StepKind.INCOME, benefit.amount(), value)
                .withLimitedBy(what + ", less the recovery " + benefit.recovered());
    }

    private static TraceLine nil(NodeContext ctx, RuleId ruleId, EmployerExpenseBenefit benefit, int sequence,
            String why) {
        return ctx.traceLine(sequence, ruleId, StepKind.INCOME, benefit.amount(), Money.ZERO)
                .withLimitedBy("not a perquisite: " + why);
    }

}
