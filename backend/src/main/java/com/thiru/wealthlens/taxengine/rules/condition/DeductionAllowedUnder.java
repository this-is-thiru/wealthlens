package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import java.util.Set;

/**
 * Any of these rows allowed a deduction above nil — r.164(3)(e)'s "has claimed deduction under section 93, other than
 * … clause (d)", the rows being the form's data, because one rule set mixes s.93's clauses (W-090c1b1, D-526 C2a,
 * D-527). The code never reads a provision.
 *
 * @param rows the rows
 */
public record DeductionAllowedUnder(Set<RuleId> rows) implements Condition {

    public DeductionAllowedUnder {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("DeductionAllowedUnder must name at least one row");
        }
        rows = Set.copyOf(rows);
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return rows.stream().anyMatch(row -> ctx.returnFacts().allowedDeductionUnder(row));
    }

    @Override
    public String describe() {
        return "a deduction was allowed under one of " + rows;
    }
}
