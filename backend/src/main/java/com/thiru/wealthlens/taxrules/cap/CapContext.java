package com.thiru.wealthlens.taxrules.cap;

import com.thiru.wealthlens.taxrules.id.BaseId;
import com.thiru.wealthlens.taxrules.money.Money;
import java.util.Map;

/**
 * The taxpayer amounts a cap is allowed to read, resolved for one computation.
 *
 * <p>A cap shape is data loaded from a rule pack, so it knows that a deduction is thirty per cent of
 * the annual value but not what this taxpayer's annual value is. This carries the second half. It is
 * deliberately a closed map rather than a reference to the wider computation: a shape can read a
 * base that its rule declares, and nothing else — no repository, no clock, no other node's output.
 *
 * <p>{@link #base} fails on an unknown identifier rather than returning zero. A missing base is a
 * pack that references something it does not define, and a cap that silently reads zero for it
 * produces a plausible, wrong, untraceable number — {@code D-NOSILENT}.
 */
public record CapContext(Map<BaseId, Money> bases) {

    public CapContext {
        if (bases == null) {
            throw new IllegalArgumentException("CapContext bases may not be null");
        }
        bases = Map.copyOf(bases);
    }

    /** A context with one base, for a rule that reads a single amount. */
    public static CapContext of(BaseId id, Money amount) {
        return new CapContext(Map.of(id, amount));
    }

    /**
     * The amount for a declared base.
     *
     * @throws IllegalArgumentException if the pack never defined it — see the class note
     */
    public Money base(BaseId id) {
        Money amount = bases.get(id);
        if (amount == null) {
            throw new IllegalArgumentException(
                    "No amount for base " + id + "; the context defines " + bases.keySet());
        }
        return amount;
    }
}
