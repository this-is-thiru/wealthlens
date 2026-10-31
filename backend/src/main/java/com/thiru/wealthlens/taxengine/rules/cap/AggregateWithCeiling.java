package com.thiru.wealthlens.taxengine.rules.cap;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Several contributions, each clipped by its own sub-cap, then summed and clipped by a shared
 * ceiling — the shape behind the s.123 deduction, the &#8377;7,50,000 employer-contribution cap and
 * the two medical-insurance ceilings.
 *
 * <h2>The order is not a preference</h2>
 *
 * <p><b>Sub-cap first, then the ceiling.</b> Reversing it is not a rounding difference. On
 * {@code 08-golden-tests.md} T5-3 — basic &#8377;4,00,000, employee national pension contribution
 * &#8377;1,50,000, nothing else — the correct order sub-caps the contribution to ten per cent of
 * salary, &#8377;40,000, which falls well under the &#8377;1,50,000 ceiling; the residue of
 * &#8377;1,10,000 then feeds the separate s.124(3) deduction, capped at &#8377;50,000, for a total
 * of &#8377;90,000.
 *
 * <p>Applying the ceiling first gives {@code min(1,50,000, 1,50,000) = 1,50,000}, leaves no residue,
 * and totals &#8377;1,50,000 — <b>&#8377;60,000 too generous</b>, on a single contribution, with no
 * step that looks wrong in isolation.
 *
 * @param contributors in the order the statute lists them
 * @param ceiling the shared limit on their sum
 * @param subCaps each contributor's own limit; a contributor absent from this map is uncapped
 */
public record AggregateWithCeiling(
        List<ComponentCode> contributors, Money ceiling, Map<ComponentCode, CapShape> subCaps)
        implements CapShape {

    public AggregateWithCeiling {
        if (contributors == null || contributors.isEmpty()) {
            throw new IllegalArgumentException("An aggregate cap must have at least one contributor");
        }
        if (ceiling == null) {
            throw new IllegalArgumentException("An aggregate cap must have a ceiling");
        }
        if (subCaps == null) {
            throw new IllegalArgumentException("subCaps may not be null; pass an empty map instead");
        }
        contributors = List.copyOf(contributors);
        subCaps = Map.copyOf(subCaps);

        if (contributors.size() != Set.copyOf(contributors).size()) {
            throw new IllegalArgumentException(
                    "A contributor is listed twice in " + contributors + "; it would be counted twice");
        }
        for (ComponentCode code : subCaps.keySet()) {
            if (!contributors.contains(code)) {
                throw new IllegalArgumentException(
                        "Sub-cap declared for " + code + ", which is not a contributor; it would never apply");
            }
        }
    }

    @Override
    public CapResult apply(CapContext ctx) {
        return applyDetailed(ctx).asCapResult();
    }

    /**
     * The full outcome, including per-contributor residues that downstream rules consume.
     *
     * @see AggregateOutcome
     */
    public AggregateOutcome applyDetailed(CapContext ctx) {
        Map<ComponentCode, Money> capped = new LinkedHashMap<>();
        Map<ComponentCode, Money> residue = new LinkedHashMap<>();
        Money aggregate = Money.ZERO;

        // 1. Each contributor's OWN cap first. G2.1 step 1.
        for (ComponentCode code : contributors) {
            Money raw = ctx.contribution(code);
            CapShape subCap = subCaps.get(code);
            Money allowed = subCap == null ? raw : Money.min(raw, subCap.apply(ctx).value());

            capped.put(code, allowed);
            residue.put(code, raw.minus(allowed));
            aggregate = aggregate.plus(allowed);
        }

        // 2. Sum, then the ceiling. G2.1 step 2.
        boolean ceilingBound = aggregate.isGreaterThan(ceiling);
        Money allowed = ceilingBound ? ceiling : aggregate;

        return new AggregateOutcome(
                allowed,
                ceilingBound ? "aggregate ceiling" : "actual amounts",
                aggregate,
                capped,
                residue,
                ceiling.minus(allowed).floorAtZero());
    }

    @Override
    public String shapeName() {
        return "aggregate of " + contributors.size() + " capped at " + ceiling;
    }
}
