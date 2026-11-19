package com.thiru.wealthlens.taxengine.rules.regime;

import java.util.regex.Pattern;

/**
 * Names a {@code RegimeBehaviour} implementation — the escape hatch, normally unused.
 *
 * <p>{@code 17-regime-lifecycle.md} L5 sets out three ways a new regime can arrive. Two are data: a
 * new pack row, or a new rule. The third is a regime needing cross-cutting behaviour that is neither
 * a node nor a value — a novel election protocol, a bespoke eligibility evaluation, a special
 * interaction with set-off. That is one class, bound by this id.
 *
 * <p>A {@code BehaviourId} in a pack with <b>no registered implementation is a load error</b>
 * (invariant I9), not a silently ignored field. A regime whose special behaviour quietly did not
 * run would compute a plausible wrong answer for every taxpayer in it.
 *
 * @param value e.g. {@code "SPECIAL_SETOFF"}
 */
public record BehaviourId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    public BehaviourId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "A BehaviourId must be upper-case with underscores, 2-64 characters, but was \"" + value + "\"");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
