package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RuleSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Each salary base, per employment (D-092): the narrow "salary" HRA, gratuity, leave encashment and the
 * pension limits are measured against.
 *
 * <p>Per employment and never summed across them, because HRA and gratuity are measured against one
 * employer's salary; {@code DR 15} Part D's single amount per rule would be wrong for anyone with two.
 * Apportioning a base to a period is its consumer's, by a named basis ({@code W-066}).
 *
 * @param byRule each base rule's amount for every entered employment, in the order entered
 */
public record SalaryBases(Map<RuleId, List<Money>> byRule) implements NodeValue {

    public SalaryBases {
        if (byRule == null) {
            throw new IllegalArgumentException("Salary bases may not be null; pass an empty map");
        }
        Map<RuleId, List<Money>> copied = new LinkedHashMap<>();
        byRule.forEach((rule, amounts) -> copied.put(rule, List.copyOf(amounts)));
        byRule = Map.copyOf(copied);
    }

    /** One base for one employment; a base that was not evaluated, or an employment not entered, is refused. */
    public Money of(RuleId rule, int employment) {
        List<Money> amounts = byRule.get(rule);
        if (amounts == null) {
            throw new IllegalArgumentException("No salary base " + rule + " was evaluated; the bases are "
                    + byRule.keySet());
        }
        if (employment < 0 || employment >= amounts.size()) {
            throw new IllegalArgumentException("Salary base " + rule + " has no employment " + employment
                    + "; " + amounts.size() + " were entered");
        }
        return amounts.get(employment);
    }

    /**
     * One base for one employment, named by its rule set — the row the set gave this year, which is the key the base was
     * evaluated under (D-252). A reader names the set, not the row.
     */
    public Money of(RuleSet base, int employment) {
        RuleId row = base.members().stream().filter(byRule::containsKey).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No salary base of " + base.id()
                        + " was evaluated; the bases are " + byRule.keySet()));
        return of(row, employment);
    }

    /** Every base added up — the trace's summary column only; it mixes bases, so nothing sums it. */
    @Override
    public Money asMoney() {
        return byRule.values().stream().flatMap(List::stream).reduce(Money.ZERO, Money::plus);
    }
}
