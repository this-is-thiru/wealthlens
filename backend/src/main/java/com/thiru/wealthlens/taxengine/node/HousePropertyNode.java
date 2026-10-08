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
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Income from house property, before interest (S2 ss.20–24; 1961 ss.22–26; {@code DR 01} R16; {@code DR 06} G1.4;
 * D-155): for each property its annual value — nil for a specified own residence, else the higher of what it might
 * let for and the rent, the rent where a vacancy brought it below, less the local taxes paid — less thirty per cent,
 * with arrears of rent less thirty per cent, the assessee's share of it; the head is their sum.
 *
 * <p>Interest on borrowed capital, and with it any loss, is {@code W-078b}; until then the head is never below nil,
 * and a property whose local taxes exceed its value is refused rather than carried as a loss nothing yet sets off.
 */
public final class HousePropertyNode implements ComputationNode {

    private static final BaseId ANNUAL_VALUE = new BaseId("ANNUAL_VALUE");
    private static final BaseId ARREARS = new BaseId("ARREARS");

    @Override
    public NodeId produces() {
        return NodeIds.HOUSEPROPERTY_INCOME;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of();
    }

    @Override
    public Set<RuleId> rules() {
        return Set.of(RuleIds.HP_ANNUAL_VALUE, RuleIds.HP_OWN_RESIDENCE, RuleIds.HP_STANDARD_DEDUCTION,
                RuleIds.HP_ARREARS);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        List<HouseProperty> properties = ctx.inputs().entered().houseProperties();
        if (properties.isEmpty()) {
            return NodeResult.untraced(MoneyValue.of(Money.ZERO));
        }
        TemporalContext t = ctx.inputs().temporal();
        requireOwnResidencesWithinLimit(ctx, t, properties);
        List<TraceLine> lines = new ArrayList<>();
        Money head = Money.ZERO;
        for (int position = 0; position < properties.size(); position++) {
            head = head.plus(income(ctx, t, position, properties.get(position), lines));
        }
        return new NodeResult(MoneyValue.of(head), lines);
    }

    /** One property's income: annual value less thirty per cent, arrears less thirty per cent, the share of it. */
    private static Money income(NodeContext ctx, TemporalContext t, int position, HouseProperty property,
            List<TraceLine> lines) {
        Money annualValue = annualValue(ctx, t, position, property, lines);
        Money allowance = TemporallyApportionedCap.apply(ctx.rule(RuleIds.HP_STANDARD_DEDUCTION), t,
                CapContext.of(ANNUAL_VALUE, annualValue)).value();
        lines.add(line(ctx, t, lines, RuleIds.HP_STANDARD_DEDUCTION, StepKind.DEDUCTION, annualValue, allowance)
                .withLimitedBy("property " + position));
        Money income = annualValue.minus(allowance);
        if (property.arrearsReceived().isGreaterThan(Money.ZERO)) {
            Money deduction = TemporallyApportionedCap.apply(ctx.rule(RuleIds.HP_ARREARS), t,
                    CapContext.of(ARREARS, property.arrearsReceived())).value();
            lines.add(line(ctx, t, lines, RuleIds.HP_ARREARS, StepKind.INCOME, property.arrearsReceived(),
                            property.arrearsReceived().minus(deduction))
                    .withLimitedBy("property " + position + ": arrears less " + deduction));
            income = income.plus(property.arrearsReceived().minus(deduction));
        }
        return property.ownershipShare().of(income);
    }

    /**
     * S2 s.21(1)–(3), (6); 1961 s.23(1)–(2): nil for an own residence; else the higher of the expected letting value and
     * the rent — the rent where the property was let, vacant, and the rent fell below — less the local taxes paid.
     */
    private static Money annualValue(NodeContext ctx, TemporalContext t, int position, HouseProperty property,
            List<TraceLine> lines) {
        if (property.occupancy() == HouseOccupancy.OWN_RESIDENCE) {
            lines.add(line(ctx, t, lines, RuleIds.HP_OWN_RESIDENCE, StepKind.MEASURE, property.expectedLettingValue(),
                            Money.ZERO)
                    .withLimitedBy("property " + position + ": the owner's own residence, nil"));
            return Money.ZERO;
        }
        Money expected = property.expectedLettingValue();
        Money rent = property.rentReceivable();
        boolean let = property.occupancy() == HouseOccupancy.LET;
        Money gross = !let ? expected
                : property.vacantPartOfYear() && rent.isLessThan(expected) ? rent : Money.max(expected, rent);
        Money annualValue = gross.minus(property.localTaxesPaid());
        if (annualValue.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("Property " + position + ": local taxes of " + property.localTaxesPaid()
                    + " exceed its value of " + gross + "; a loss from house property waits on interest and set-off"
                    + " (W-078b, W-081)");
        }
        Money value = TemporallyApportionedCap.apply(ctx.rule(RuleIds.HP_ANNUAL_VALUE), t,
                CapContext.of(ANNUAL_VALUE, annualValue)).value();
        lines.add(line(ctx, t, lines, RuleIds.HP_ANNUAL_VALUE, StepKind.INCOME, gross, value)
                .withLimitedBy("property " + position + ": " + (let ? "let, rent " + rent : "not let")
                        + ", expected " + expected + ", less local taxes " + property.localTaxesPaid()));
        return value;
    }

    /** S2 s.21(7)(a); 1961 s.23(4)(a): only as many own residences as the row allows, until the election chooses. */
    private static void requireOwnResidencesWithinLimit(NodeContext ctx, TemporalContext t,
            List<HouseProperty> properties) {
        RuleVersion version = TemporallyApportionedCap.governingVersion(ctx.rule(RuleIds.HP_OWN_RESIDENCE), t);
        int limit = RuleDefinitions.require(version.definition(), OwnResidenceHouses.class, RuleIds.HP_OWN_RESIDENCE)
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
