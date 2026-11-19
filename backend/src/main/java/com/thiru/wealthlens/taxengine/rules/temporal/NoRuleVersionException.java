package com.thiru.wealthlens.taxengine.rules.temporal;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import java.time.LocalDate;

/**
 * A rule exists but has no version in force on the date it was read.
 *
 * <p>A gap in a rule's version list, which the pack validator should have caught at load time. It is
 * still worth throwing here: a gap that reaches a computation means the law said nothing on that
 * date, and there is no defensible amount to return.
 */
public class NoRuleVersionException extends RuntimeException {

    public NoRuleVersionException(RuleId rule, TemporalAxis axis, LocalDate on) {
        super("Rule " + rule + " has no version in force on " + on + " (read against " + axis + ")");
    }
}
