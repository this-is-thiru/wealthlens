package com.thiru.wealthlens.taxengine.rules.relief;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/** No relief — stated, so a regime without one says so rather than leaving a gap in the trace. */
public record NoRelief() implements ReliefShape {

    @Override
    public ReliefResult apply(ReliefContext ctx) {
        return new ReliefResult(Money.ZERO, "no relief applies");
    }
}
