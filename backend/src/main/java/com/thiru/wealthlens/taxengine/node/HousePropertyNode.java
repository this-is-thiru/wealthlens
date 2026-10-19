package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.HouseOccupancy;
import com.thiru.wealthlens.taxengine.input.HouseProperty;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.definition.OwnResidenceHouses;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Income from house property (S2 ss.20–24; 1961 ss.22–26; {@code DR 01} R16; {@code DR 06} G1.4; D-155, D-156): for
 * each property its annual value — nil for a specified own residence, else the higher of what it might let for and
 * the rent, the rent where a vacancy brought it below, less the local taxes paid — less thirty per cent, with arrears
 * of rent less thirty per cent, the assessee's share of it, less the interest on what the assessee borrowed for it
 * ({@link HousePropertyInterest}); the head is their sum, less the own residences' interest within its limits.
 *
 * <p>A property below nil is set off against the others under the head, as S2 s.108(1) and 1961 s.70 set off a
 * loss from one source against another under the same head; the head itself below nil is a loss, which {@link
 * SetOffNode} sets off against the other heads (D-217).
 */
public final class HousePropertyNode implements ComputationNode {

    private static final BaseId ANNUAL_VALUE = new BaseId("ANNUAL_VALUE");
    private static final BaseId ARREARS = new BaseId("ARREARS");
    private static final BaseId UNREALISED_RENT = new BaseId("UNREALISED_RENT");

    @Override
    public NodeId produces() {
        return NodeIds.HOUSEPROPERTY_INCOME;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of();
    }

    @Override
    public Set<RuleSetId> ruleSets() {
        Set<RuleSetId> rules = new HashSet<>(Set.of(RuleSetIds.RS_HP_ANNUAL_VALUE, RuleSetIds.RS_HP_OWN_RESIDENCE,
                RuleSetIds.RS_HP_STANDARD_DEDUCTION, RuleSetIds.RS_HP_ARREARS, RuleSetIds.RS_HP_UNREALISED_RENT));
        rules.addAll(HousePropertyInterest.RULES);
        return Set.copyOf(rules);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        List<HouseProperty> properties = ctx.inputs().entered().houseProperties();
        if (properties.isEmpty()) {
            return NodeResult.untraced(MoneyValue.of(Money.ZERO));
        }
        TemporalContext t = ctx.inputs().temporal();
        requireOwnResidencesWithinLimit(ctx, t, properties);
        HousePropertyInterest.requireChapterEightShareMatches(properties, ctx.inputs().entered().purposeLoanInterest());
        List<TraceLine> lines = new ArrayList<>();
        HousePropertyInterest interest = new HousePropertyInterest(ctx, lines);
        Money head = Money.ZERO;
        for (int position = 0; position < properties.size(); position++) {
            HouseProperty property = properties.get(position);
            head = head.plus(income(ctx, t, position, property, lines)).minus(interest.deductedFor(position, property));
        }
        head = head.minus(interest.ownResidenceAllowed());
        return new NodeResult(MoneyValue.of(head), lines);
    }

    /** One property's income: annual value less thirty per cent, arrears less thirty per cent, the share of it. */
    private static Money income(NodeContext ctx, TemporalContext t, int position, HouseProperty property,
            List<TraceLine> lines) {
        Money annualValue = annualValue(ctx, t, position, property, lines);
        Money allowance = TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_HP_STANDARD_DEDUCTION), t,
                CapContext.of(ANNUAL_VALUE, annualValue)).value();
        lines.add(line(ctx, t, lines, ctx.ruleIdOf(RuleSetIds.RS_HP_STANDARD_DEDUCTION), StepKind.DEDUCTION, annualValue, allowance)
                .withLimitedBy("property " + position));
        Money income = annualValue.minus(allowance);
        if (property.arrearsReceived().isGreaterThan(Money.ZERO)) {
            Money deduction = TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_HP_ARREARS), t,
                    CapContext.of(ARREARS, property.arrearsReceived())).value();
            lines.add(line(ctx, t, lines, ctx.ruleIdOf(RuleSetIds.RS_HP_ARREARS), StepKind.INCOME, property.arrearsReceived(),
                            property.arrearsReceived().minus(deduction))
                    .withLimitedBy("property " + position + ": arrears less " + deduction));
            income = income.plus(property.arrearsReceived().minus(deduction));
        }
        return property.ownershipShare().of(income);
    }

    /**
     * S2 s.21(1)–(4), (6); 1961 s.23(1)–(2): nil for an own residence; else the higher of the expected letting value and
     * the rent — the rent where the property was let, vacant, and the rent fell below — less the local taxes paid. The
     * rent is net of what the owner cannot realise before either comparison (s.21(4); 1961 s.23(1) Explanation).
     */
    private static Money annualValue(NodeContext ctx, TemporalContext t, int position, HouseProperty property,
            List<TraceLine> lines) {
        if (property.occupancy() == HouseOccupancy.OWN_RESIDENCE) {
            lines.add(line(ctx, t, lines, ctx.ruleIdOf(RuleSetIds.RS_HP_OWN_RESIDENCE), StepKind.MEASURE, property.expectedLettingValue(),
                            Money.ZERO)
                    .withLimitedBy("property " + position + ": the owner's own residence, nil"));
            return Money.ZERO;
        }
        Money expected = property.expectedLettingValue();
        Money rent = actualRent(ctx, t, position, property, lines);
        boolean let = property.occupancy() == HouseOccupancy.LET;
        Money gross = !let ? expected
                : property.vacantPartOfYear() && rent.isLessThan(expected) ? rent : Money.max(expected, rent);
        Money annualValue = gross.minus(property.localTaxesPaid());
        if (annualValue.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("Property " + position + ": local taxes of " + property.localTaxesPaid()
                    + " exceed its value of " + gross + "; a negative annual value waits on set-off (W-081)");
        }
        Money value = TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_HP_ANNUAL_VALUE), t,
                CapContext.of(ANNUAL_VALUE, annualValue)).value();
        lines.add(line(ctx, t, lines, ctx.ruleIdOf(RuleSetIds.RS_HP_ANNUAL_VALUE), StepKind.INCOME, gross, value)
                .withLimitedBy("property " + position + ": " + (let ? "let, rent " + rent : "not let")
                        + ", expected " + expected + ", less local taxes " + property.localTaxesPaid()));
        return value;
    }

    /** S2 s.21(4) with Rules 2026 r.21; 1961 s.23(1) Explanation with Rules 1962 r.4: the rent less what is unrealised. */
    private static Money actualRent(NodeContext ctx, TemporalContext t, int position, HouseProperty property,
            List<TraceLine> lines) {
        if (property.unrealisedRent().equals(Money.ZERO)) {
            return property.rentReceivable();
        }
        Money unrealised = TemporallyApportionedCap.apply(ctx.ruleOf(RuleSetIds.RS_HP_UNREALISED_RENT), t,
                CapContext.of(UNREALISED_RENT, property.unrealisedRent())).value();
        Money rent = property.rentReceivable().minus(unrealised);
        lines.add(line(ctx, t, lines, ctx.ruleIdOf(RuleSetIds.RS_HP_UNREALISED_RENT), StepKind.MEASURE, property.rentReceivable(), rent)
                .withLimitedBy("property " + position + ": less unrealised rent " + unrealised));
        return rent;
    }

    /** S2 s.21(7)(a); 1961 s.23(4)(a): only as many own residences as the row allows, until the election chooses. */
    private static void requireOwnResidencesWithinLimit(NodeContext ctx, TemporalContext t,
            List<HouseProperty> properties) {
        RuleVersion version = TemporallyApportionedCap.governingVersion(ctx.ruleOf(RuleSetIds.RS_HP_OWN_RESIDENCE), t);
        int limit = RuleDefinitions.require(version.definition(), OwnResidenceHouses.class, ctx.ruleIdOf(RuleSetIds.RS_HP_OWN_RESIDENCE))
                .houses();
        long ownResidences = properties.stream()
                .filter(property -> property.occupancy() == HouseOccupancy.OWN_RESIDENCE)
                .count();
        if (ownResidences > limit) {
            throw new IllegalArgumentException(ownResidences + " houses are entered as own residences; the annual"
                    + " value is nil for only " + limit + " specified by the assessee (S2 s.21(7)(a); 1961"
                    + " s.23(4)(a)) — enter the others as not let");
        }
    }

    private static TraceLine line(NodeContext ctx, TemporalContext t, List<TraceLine> lines, RuleId rule,
            StepKind kind, Money in, Money out) {
        return TraceLine.of(lines.size() + 1, NodeIds.HOUSEPROPERTY_INCOME, rule, kind, MoneyValue.of(in),
                MoneyValue.of(out), TemporallyApportionedCap.governingVersion(ctx.rule(rule), t).provenance());
    }
}
