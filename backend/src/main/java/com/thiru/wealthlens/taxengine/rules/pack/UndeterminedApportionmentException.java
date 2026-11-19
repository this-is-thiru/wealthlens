package com.thiru.wealthlens.taxengine.rules.pack;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import java.util.List;

/**
 * A rule changed inside the year and nobody has said how its versions combine (D-098).
 *
 * <p>Guessing would give a plausible figure, only in the year the rule moved, that no golden test written
 * before the change would catch; so the computation stops instead. Invariant I14 refuses such a pack at load,
 * and this is the same refusal where a pack reaches the engine unvalidated. {@code W-105} re-parents it under
 * the engine's exception hierarchy.
 */
public class UndeterminedApportionmentException extends RuntimeException {

    public UndeterminedApportionmentException(RuleId rule, List<DateRange> segments) {
        super("Rule " + rule + " changes inside the year " + segments + " and its midYearCombination is"
                + " UNDETERMINED; the pack must state how its versions combine before it can be applied");
    }
}
