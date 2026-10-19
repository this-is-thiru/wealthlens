package com.thiru.wealthlens.taxengine.node;

import com.thiru.wealthlens.taxengine.input.Assessee;
import com.thiru.wealthlens.taxengine.input.Huf;
import com.thiru.wealthlens.taxengine.input.Individual;
import com.thiru.wealthlens.taxengine.pipeline.ComputationNode;
import com.thiru.wealthlens.taxengine.pipeline.HeadsAfterSetOff;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeContext;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SpecialRateId;
import com.thiru.wealthlens.taxengine.pipeline.TaxByBasis;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.cap.CapShape;
import com.thiru.wealthlens.taxengine.rules.cap.SlabLadder;
import com.thiru.wealthlens.taxengine.rules.condition.ItemFacts;
import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.definition.RuleDefinitions;
import com.thiru.wealthlens.taxengine.rules.definition.SpecialRateShortfallOrder;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleSetIds;
import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.RuleSetId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.pack.RuleRow;
import com.thiru.wealthlens.taxengine.rules.pack.TemporallyApportionedCap;
import com.thiru.wealthlens.taxengine.rules.temporal.RuleVersion;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Tax on the rounded total income, split by basis: the special-rate capital gains at their own rates, the rest through
 * the ladder the regime names ({@code DR 16} S2 step 5, D-071; S2 ss.196–198, 1961 ss.111A, 112, 112A; D-244).
 *
 * <p>The ladder is the regime's {@code rateTable}, not a rule fixed here, so a regime that
 * arrives as a pack row brings its own rates with no change to this class — unless a conditional
 * ladder, such as the First Schedule's for a resident of sixty or eighty, applies instead (D-072).
 *
 * <p>Each special-rate part {@code setoff} leaves is charged by its own row, and the ladder takes total income less
 * them (s.196(1)(ii), s.197(1)(a), s.198(2)(b)). For a resident individual or HUF, the part of the ladder's nil band
 * the rest leaves unused first reduces the parts, in {@code RATE-CG-SHORTFALL-ORDER}'s order (s.196(2), s.197(2),
 * s.198(3)). The figures are not rounded: rounding happens in exactly two places, neither of them a band
 * ({@code RATE-ROUND-003}).
 */
public final class TaxByBasisNode implements ComputationNode {

    @Override
    public NodeId produces() {
        return NodeIds.TAX_BYBASIS;
    }

    @Override
    public Set<NodeId> requires() {
        return Set.of(NodeIds.ROUND_TOTALINCOME, NodeIds.SETOFF);
    }

    /** What a capital-gains part's special-rate row is chosen by; a part with no row is charged at slab rates (D-253). */
    private static final ItemFactId CAPITAL_GAINS_PART = new ItemFactId("capitalgains.part");

    @Override
    public Set<RuleSetId> ruleSets() {
        return Set.of(RuleSetIds.RS_RATE_CG_SPECIAL, RuleSetIds.RS_RATE_SLAB, RuleSetIds.RS_RATE_CG_SHORTFALL_ORDER);
    }

    @Override
    public NodeResult evaluate(NodeContext ctx) {
        Money totalIncome = ctx.get(NodeIds.ROUND_TOTALINCOME, MoneyValue.class).amount();
        Map<CapitalGainsPart, Money> gains = specialRateGains(ctx, ctx.get(NodeIds.SETOFF, HeadsAfterSetOff.class));
        // The slab ladder: each regime's own, unless an age ladder replaces it — the rows' conditions say which (D-252)
        RuleRow row = ctx.ruleOf(RuleSetIds.RS_RATE_SLAB);
        RuleVersion version = TemporallyApportionedCap.governingVersion(row, ctx.inputs().temporal());
        Optional<CapShape> shape = version.cap();
        if (!(shape.isPresent() && shape.get() instanceof SlabLadder ladder)) {
            throw new IllegalStateException("Rate table " + row.id() + " for regime " + ctx.regime().code()
                    + " is " + shape.map(cap -> "a " + cap.shapeName()).orElse("not a ladder") + ", not a slab ladder");
        }
        if (gains.isEmpty()) {
            TaxByBasis value = TaxByBasis.atSlabRatesOnly(ladder.apply(CapContext.of(ladder.base(), totalIncome)).value());
            return NodeResult.of(value, TraceLine.of(
                    1, produces(), row.id(), StepKind.TAX, MoneyValue.of(totalIncome), value, version.provenance()));
        }
        Money atSpecialRates = gains.values().stream().reduce(Money.ZERO, Money::plus);
        Money atSlab = totalIncome.minus(atSpecialRates).floorAtZero();
        TaxByBasis value = TaxByBasis.atSlabRatesOnly(ladder.apply(CapContext.of(ladder.base(), atSlab)).value());
        List<TraceLine> lines = new ArrayList<>();
        lines.add(TraceLine.of(1, produces(), row.id(), StepKind.TAX, MoneyValue.of(atSlab), value, version.provenance())
                .withLimitedBy("total income " + totalIncome + " less the capital gains at special rates "
                        + atSpecialRates));
        Map<CapitalGainsPart, Money> charged = shiftShortfall(ctx, gains, nilBandCeiling(ladder).minus(atSlab), lines);
        for (Map.Entry<CapitalGainsPart, Money> part : charged.entrySet()) {
            RuleId rate = specialRate(ctx, part.getKey()).orElseThrow().id();
            RuleVersion rateVersion = TemporallyApportionedCap.governingVersion(ctx.rule(rate), ctx.inputs().temporal());
            Money tax = TemporallyApportionedCap.apply(ctx.rule(rate), ctx.inputs().temporal(),
                    CapContext.of(((SlabLadder) rateVersion.cap().orElseThrow()).base(), part.getValue())).value();
            value = value.plusSpecialRate(new SpecialRateId(rate.value().replace('-', '_')), tax);
            lines.add(TraceLine.of(lines.size() + 1, produces(), rate, StepKind.TAX, MoneyValue.of(part.getValue()),
                    MoneyValue.of(tax), rateVersion.provenance()).withLimitedBy(part.getKey().toString()));
        }
        return new NodeResult(value, lines);
    }

    /**
     * The capital gains charged at special rates, which Chapter VIII may not reach (S2 s.196(4), s.197(5), s.198(6);
     * 1961 ss.111A(2), 112(2), 112A(5); D-244). The caller's node declares {@code RS-RATE-CG-SPECIAL}.
     */
    static Money atSpecialRates(NodeContext ctx, HeadsAfterSetOff heads) {
        return specialRateGains(ctx, heads).values().stream().reduce(Money.ZERO, Money::plus);
    }

    /** The parts holding a gain after set-off that a special-rate row charges, in the parts' order. */
    private static Map<CapitalGainsPart, Money> specialRateGains(NodeContext ctx, HeadsAfterSetOff heads) {
        Map<CapitalGainsPart, Money> gains = new EnumMap<>(CapitalGainsPart.class);
        for (CapitalGainsPart part : CapitalGainsPart.values()) {
            if (heads.capitalGains(part).isGreaterThan(Money.ZERO) && specialRate(ctx, part).isPresent()) {
                gains.put(part, heads.capitalGains(part));
            }
        }
        return gains;
    }

    /** The row charging a part at a special rate, as the rows' conditions say; none for a part taxed at slab rates. */
    private static Optional<RuleRow> specialRate(NodeContext ctx, CapitalGainsPart part) {
        return ctx.optionalRuleOf(RuleSetIds.RS_RATE_CG_SPECIAL, ItemFacts.of(CAPITAL_GAINS_PART, part.name()));
    }

    /**
     * The shortfall of the rest of total income below the maximum amount not chargeable, taken off the parts in the
     * declared order — for a resident individual or HUF only (s.196(2), s.197(2), s.198(3)); a measure line per part
     * it reduces.
     */
    private static Map<CapitalGainsPart, Money> shiftShortfall(NodeContext ctx, Map<CapitalGainsPart, Money> gains,
            Money shortfall, List<TraceLine> lines) {
        Assessee assessee = ctx.inputs().assessee();
        boolean shifts = assessee.residence() != Assessee.ResidentialStatus.NON_RESIDENT
                && (assessee instanceof Individual || assessee instanceof Huf);
        if (!shifts || !shortfall.isGreaterThan(Money.ZERO)) {
            return gains;
        }
        RuleRow orderRow = ctx.ruleOf(RuleSetIds.RS_RATE_CG_SHORTFALL_ORDER);
        RuleVersion version = TemporallyApportionedCap.governingVersion(orderRow, ctx.inputs().temporal());
        SpecialRateShortfallOrder order = RuleDefinitions.require(version.definition(), SpecialRateShortfallOrder.class,
                orderRow.id());
        Map<CapitalGainsPart, Money> charged = new LinkedHashMap<>(gains);
        Money left = shortfall;
        for (CapitalGainsPart part : order.parts()) {
            Money gain = charged.getOrDefault(part, Money.ZERO);
            Money taken = Money.min(gain, left);
            if (taken.isGreaterThan(Money.ZERO)) {
                charged.put(part, gain.minus(taken));
                lines.add(TraceLine.of(lines.size() + 1, NodeIds.TAX_BYBASIS, orderRow.id(), StepKind.MEASURE,
                                MoneyValue.of(gain), MoneyValue.of(gain.minus(taken)), version.provenance())
                        .withLimitedBy(part + " less " + taken + ": income at slab rates leaves " + shortfall
                                + " of the basic exemption unused, taken in the order " + order.parts()));
                left = left.minus(taken);
            }
        }
        return charged;
    }

    /** The maximum amount not chargeable to income-tax: the top of the ladder's nil-rate bands, nil if it has none. */
    private static Money nilBandCeiling(SlabLadder ladder) {
        Money ceiling = Money.ZERO;
        for (SlabLadder.Band band : ladder.bands()) {
            Optional<Money> upper = band.upperInclusive();
            if (band.rate().compareTo(Rate.ZERO) != 0 || upper.isEmpty()) {
                break;
            }
            ceiling = upper.get();
        }
        return ceiling;
    }

}
