package com.thiru.wealthlens.taxengine.rules.cap;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;

/**
 * The taxpayer figures a cap is allowed to read, resolved for one computation.
 *
 * <p>A cap shape is data loaded from a rule pack, so it knows that a deduction is thirty per cent of
 * the annual value but not what this taxpayer's annual value is. This carries the other half. It is
 * deliberately a closed set of maps rather than a reference to the wider computation: a shape reads
 * what its rule declares, and nothing else — no repository, no clock, no other node's output.
 *
 * <p>Three axes, because three different questions are asked of a taxpayer:
 *
 * <table>
 *   <caption>what each map answers</caption>
 *   <tr><th>map</th><th>question</th><th>read by</th></tr>
 *   <tr><td>{@code bases}</td><td>what amount is this a percentage of?</td>
 *       <td>{@link PercentOfBase}, {@link SlabLadder}</td></tr>
 *   <tr><td>{@code contributions}</td><td>how much did this pay line contribute?</td>
 *       <td>{@link AggregateWithCeiling}</td></tr>
 *   <tr><td>{@code counts}</td><td>how many children, how many months?</td>
 *       <td>{@code PerUnitPerPeriod}</td></tr>
 * </table>
 *
 * <p>Every lookup fails on an identifier the pack never defined, rather than returning zero. A
 * missing figure means a pack references something it does not define, and reading zero for it
 * produces a plausible, wrong, untraceable number — {@code D-NOSILENT}.
 *
 * @param bases amounts a percentage may be taken of
 * @param contributions amounts per pay line, for aggregate caps
 * @param counts quantities, keyed in the same namespace as {@code bases} — a count is not money, so
 *     asking for one as an amount is a pack error rather than a coincidence that happens to parse
 * @param locations where something is, for a percentage chosen by place — HRA's limb (c) (D-099)
 */
public record CapContext(
        Map<BaseId, Money> bases,
        Map<ComponentCode, Money> contributions,
        Map<BaseId, Integer> counts,
        Map<BaseId, String> locations) {

    public CapContext {
        if (bases == null || contributions == null || counts == null || locations == null) {
            throw new IllegalArgumentException("CapContext maps may not be null");
        }
        bases = Map.copyOf(bases);
        contributions = Map.copyOf(contributions);
        counts = Map.copyOf(counts);
        locations = Map.copyOf(locations);
    }

    /** A context with no location — every call site written before D-099. */
    public CapContext(Map<BaseId, Money> bases, Map<ComponentCode, Money> contributions, Map<BaseId, Integer> counts) {
        this(bases, contributions, counts, Map.of());
    }

    /** A context with bases only — the common case, and every call site written before W-018. */
    public CapContext(Map<BaseId, Money> bases) {
        this(bases, Map.of(), Map.of());
    }

    /** A context with one base, for a rule that reads a single amount. */
    public static CapContext of(BaseId id, Money amount) {
        return new CapContext(Map.of(id, amount));
    }

    /** A context with contributions only, for an aggregate cap with no sub-cap to resolve. */
    public static CapContext ofContributions(Map<ComponentCode, Money> contributions) {
        return new CapContext(Map.of(), contributions, Map.of());
    }

    /**
     * The amount for a declared base.
     *
     * @throws IllegalArgumentException if the pack never defined it — see the class note
     */
    public Money base(BaseId id) {
        return require(bases.get(id), "base", id, bases.keySet());
    }

    /**
     * The amount contributed by one pay line.
     *
     * @throws IllegalArgumentException if the pack never defined it
     */
    public Money contribution(ComponentCode code) {
        return require(contributions.get(code), "contribution", code, contributions.keySet());
    }

    /**
     * A quantity — children, months, journeys.
     *
     * @throws IllegalArgumentException if the pack never defined it
     */
    public int count(BaseId id) {
        Integer value = counts.get(id);
        if (value == null) {
            throw new IllegalArgumentException(
                    "No count for " + id + "; the context defines " + counts.keySet());
        }
        return value;
    }

    /**
     * Where something is — a location code.
     *
     * @throws IllegalArgumentException if the context was given none for {@code id}
     */
    public String location(BaseId id) {
        String value = locations.get(id);
        if (value == null) {
            throw new IllegalArgumentException(
                    "No location for " + id + "; the context defines " + locations.keySet());
        }
        return value;
    }

    private static Money require(Money value, String kind, Object id, Object defined) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "No " + kind + " for " + id + "; the context defines " + defined);
        }
        return value;
    }
}
