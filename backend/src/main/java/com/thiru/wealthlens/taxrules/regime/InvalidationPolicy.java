package com.thiru.wealthlens.taxrules.regime;

import com.thiru.wealthlens.taxrules.id.RuleId;

/**
 * What happens when a taxpayer stops meeting a regime's conditions.
 *
 * <p>The three answers are very far apart in consequence, and the statute does not use one of them
 * everywhere. s.203(2) is the severe case: the election is invalid for the year the condition failed
 * <b>and every year after it</b>, as if it had never been exercised — which can reopen returns the
 * taxpayer considered closed.
 *
 * <p>Modelling this as a boolean, or assuming the severe case everywhere, would either overstate a
 * taxpayer's exposure or hide it. Both are wrong in a way they would only discover from the
 * department.
 */
public sealed interface InvalidationPolicy {

    /** The provision this policy rests on, where one ends the regime or the election. */
    java.util.Optional<RuleId> citedRule();

    /** s.203(2): invalid for the failure year and all subsequent years, as if never exercised. */
    record RetroactiveFromFailureYear(RuleId basis) implements InvalidationPolicy {

        public RetroactiveFromFailureYear {
            if (basis == null) {
                throw new IllegalArgumentException(
                        "RetroactiveFromFailureYear must name its basis; it can reopen years the"
                                + " taxpayer considered closed, so the citation is not optional");
            }
        }

        @Override
        public java.util.Optional<RuleId> citedRule() {
            return java.util.Optional.of(basis);
        }
    }

    /** Invalid from the failure year onward only. Earlier years stand. */
    record ProspectiveOnly(RuleId basis) implements InvalidationPolicy {

        public ProspectiveOnly {
            if (basis == null) {
                throw new IllegalArgumentException("ProspectiveOnly must name its basis");
            }
        }

        @Override
        public java.util.Optional<RuleId> citedRule() {
            return java.util.Optional.of(basis);
        }
    }

    /** Failure does not invalidate the election; it merely disallows a deduction. */
    record NoInvalidation() implements InvalidationPolicy {

        @Override
        public java.util.Optional<RuleId> citedRule() {
            return java.util.Optional.empty();
        }
    }
}
