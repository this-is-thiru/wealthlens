package com.thiru.wealthlens.taxengine.rules.cap;

import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonFact;
import java.util.Map;
import java.util.Optional;

/**
 * "The amount by which" one figure "exceeds" a threshold, and never less than nil (D-099).
 *
 * <p>HRA's second limb is stated this way — rent paid "exceeds one-tenth of the amount of salary" (S4 r.279(1)(b);
 * Rules 1962 r.2A(b)) — and the one-tenth is law, so it is a threshold shape rather than arithmetic in a node.
 * A threshold above the amount gives nil: nothing exceeds, and a negative limb would win every least-of.
 *
 * @param amount the figure that may exceed, such as rent paid
 * @param threshold what it must exceed, such as one-tenth of salary
 */
public record AmountInExcess(BaseId amount, CapShape threshold) implements CapShape {

    public AmountInExcess {
        if (amount == null || threshold == null) {
            throw new IllegalArgumentException("An AmountInExcess needs the amount and the threshold it exceeds");
        }
    }

    @Override
    public CapResult apply(CapContext ctx) {
        Money above = threshold.apply(ctx).value();
        Money excess = ctx.base(amount).minus(above);
        return new CapResult(excess.floorAtZero(), shapeName(),
                Optional.of(new Reason(ReasonCodes.CAP_IN_EXCESS, Map.of("threshold", new ReasonFact.MoneyFact(above)))));
    }

    @Override
    public String shapeName() {
        return amount + " in excess of " + threshold.shapeName();
    }
}
