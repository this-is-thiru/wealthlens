package com.thiru.wealthlens.taxengine.pipeline;

import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.pack.DisallowanceSet;

/**
 * Whether a rule's version applies in the regime being computed, and if not, why (D-165).
 *
 * <p>Two different things keep a rule out of a regime, and a taxpayer is owed the difference. A row may be written
 * for some regimes only — the standard deduction's "any other case" amount is keyed on the regime by s.19 itself — or
 * the regime may <b>forbid</b> it: S2 s.202(2)(a) computes the new regime's income "without any exemption or
 * deduction under" a list. The first is {@link ExcludedByRow}; the second is {@link Disallowed}, and names the set
 * and the provision it rests on, so the explanation is "disallowed by s.202(2)(a)", not a silent nil.
 */
public sealed interface RegimeVerdict {

    /** Whether the rule is applied in this regime. */
    boolean applies();

    /**
     * What a nil line says where the rule is not applied: the provision that forbids it, or that the row is written
     * for other regimes (D-166). Refused for a rule that applies, which has no such line.
     */
    String whyNot();

    /** The rule applies. */
    record Applies() implements RegimeVerdict {
        @Override
        public boolean applies() {
            return true;
        }

        @Override
        public String whyNot() {
            throw new IllegalStateException("A rule that applies in the regime has no reason for not applying");
        }
    }

    /** The row is written for other regimes. */
    record ExcludedByRow(RegimeCode regime) implements RegimeVerdict {
        @Override
        public boolean applies() {
            return false;
        }

        @Override
        public String whyNot() {
            return "not in the " + regime.value() + " regime";
        }
    }

    /** The regime forbids the rule, by a set resting on a provision. */
    record Disallowed(RegimeCode regime, DisallowanceSet set) implements RegimeVerdict {
        @Override
        public boolean applies() {
            return false;
        }

        /** The regime, the set's statement of its provision, and the basis row. */
        @Override
        public String whyNot() {
            return "disallowed in the " + regime.value() + " regime: " + set.displayName() + " (" + set.basis() + ")";
        }
    }
}
