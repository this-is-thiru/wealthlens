package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.definition.CapitalGainsPart;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Set;

/**
 * Every capital-gains part with a gain is one of these — r.164(2)(d)'s "only long-term capital gains under section
 * 198", with the part named by the form's data (W-090c1b1, D-527). No capital gains at all holds: "only" excludes
 * other gains, it does not require one.
 *
 * @param parts the parts a gain may be in
 */
public record CapitalGainsOnlyInParts(Set<CapitalGainsPart> parts) implements Condition {

    public CapitalGainsOnlyInParts {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("CapitalGainsOnlyInParts must name at least one part");
        }
        parts = Set.copyOf(parts);
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.returnFacts().capitalGains().entrySet().stream()
                .filter(gain -> gain.getValue().isGreaterThan(Money.ZERO))
                .allMatch(gain -> parts.contains(gain.getKey()));
    }

    @Override
    public String describe() {
        return "capital gains are only in " + parts;
    }
}
