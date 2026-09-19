package com.thiru.wealthlens.taxrules.regime;

import com.thiru.wealthlens.taxrules.id.RegimeCode;
import com.thiru.wealthlens.taxrules.id.RuleId;

/**
 * What happens to a taxpayer already in a regime when that regime stops applying.
 *
 * <p>Sunset is not the same as closing to new entrants, and a design that has only one window
 * cannot tell them apart. Sections 201 and 204 are in this shape <b>today</b>: closed to anyone
 * who did not elect in by a past date, and still applying in full to everyone who did.
 *
 * <p>Each branch names the {@code RuleId} it rests on, because &ldquo;your regime ended and you
 * moved to the default&rdquo; is a statement about the law that a user may reasonably challenge.
 */
public sealed interface ContinuationPolicy {

    /** The rule this policy is stated by, or empty where the regime simply continues. */
    /** The provision this policy rests on, where one ends the regime or the election. */
    java.util.Optional<RuleId> citedRule();

    /** The regime ends; incumbents fall to the default for their person type. */
    record FallToDefault(RuleId basis) implements ContinuationPolicy {

        public FallToDefault {
            if (basis == null) {
                throw new IllegalArgumentException("FallToDefault must name the rule that ends the regime");
            }
        }

        @Override
        public java.util.Optional<RuleId> citedRule() {
            return java.util.Optional.of(basis);
        }
    }

    /** Incumbents move to a named successor, carrying their election date with them. */
    record MigrateTo(RegimeCode successor, RuleId basis) implements ContinuationPolicy {

        public MigrateTo {
            if (successor == null || basis == null) {
                throw new IllegalArgumentException("MigrateTo must name both a successor and its basis");
            }
        }

        @Override
        public java.util.Optional<RuleId> citedRule() {
            return java.util.Optional.of(basis);
        }
    }

    /**
     * Incumbents keep applying it indefinitely — entry closed, application open.
     *
     * <p>What s.201 does today. There is no rule to cite because nothing ends.
     */
    record ContinueIndefinitely() implements ContinuationPolicy {

        @Override
        public java.util.Optional<RuleId> citedRule() {
            return java.util.Optional.empty();
        }
    }
}
