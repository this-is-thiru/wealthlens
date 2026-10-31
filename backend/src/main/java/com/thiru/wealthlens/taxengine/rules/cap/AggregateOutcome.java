package com.thiru.wealthlens.taxengine.rules.cap;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;

/**
 * Everything an aggregate cap computed, not only what it allowed.
 *
 * <p>{@link CapResult} carries a value and the name of what bound it, which is enough for every
 * other shape. It is not enough here, for two reasons that {@code 08-golden-tests.md} states as
 * separate cases.
 *
 * <p><b>Downstream rules spend the residue.</b> In T5-1 the employee's national pension contribution
 * is sub-capped from &#8377;1,50,000 to &#8377;1,00,000, and the &#8377;50,000 that did not fit is
 * exactly what the separate s.124(3) deduction is computed on. The residue is not diagnostic
 * information; it is an input.
 *
 * <p><b>The aggregate is advice even when the deduction is not.</b> T5-2 omits employee provident
 * fund from the Schedule XV set: the deduction is identical because the ceiling binds either way,
 * and the reported aggregate differs by &#8377;1,20,000. Telling a taxpayer they have
 * &#8377;1,20,000 of unused headroom when they have none is, in that case's own words, the most
 * damaging output this product can produce — and it is invisible in the tax figure.
 *
 * @param allowed what the cap permits, after sub-caps and the ceiling
 * @param limitedBy {@code "aggregate ceiling"} where the ceiling bound, else {@code "actual amounts"}
 * @param aggregate the sum of the sub-capped contributions, before the ceiling
 * @param capped each contribution after its own sub-cap
 * @param subCapResidue per contributor, what its own sub-cap excluded — {@code raw - capped}
 * @param headroom what remains of the ceiling, never negative
 */
public record AggregateOutcome(
        Money allowed,
        String limitedBy,
        Money aggregate,
        Map<ComponentCode, Money> capped,
        Map<ComponentCode, Money> subCapResidue,
        Money headroom) {

    public AggregateOutcome {
        capped = Map.copyOf(capped);
        subCapResidue = Map.copyOf(subCapResidue);
    }

    /** The subset of this outcome that every {@link CapShape} caller sees. */
    public CapResult asCapResult() {
        return new CapResult(allowed, limitedBy);
    }
}
