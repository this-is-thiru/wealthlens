package com.thiru.wealthlens.taxengine.rules.relief;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.reason.Reason;
import com.thiru.wealthlens.taxengine.rules.reason.ReasonCodes;
import java.util.Map;

/** No relief — stated, so a regime without one says so rather than leaving a gap in the trace. */
public record NoRelief() implements ReliefShape {

    @Override
    public ReliefResult apply(ReliefContext ctx) {
        return new ReliefResult(Money.ZERO, "no relief applies", new Reason(ReasonCodes.RELIEF_NONE, Map.of()));
    }
}
