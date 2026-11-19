package com.thiru.wealthlens.taxengine.rules.temporal;

import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.registry.Provenance;
import java.time.LocalDate;
import java.util.Optional;

/**
 * When one version of a rule is in force.
 *
 * <p>A rule is a stable identity with an ordered list of versions, not a single value. Values change
 * inside a tax year as well as between them — the Income-tax Rules 2026 were amended at least four
 * times within the year they govern, and one amending instrument commenced partly on 1 April and
 * partly on the date of its own publication.
 */
public sealed interface Validity {

    boolean contains(LocalDate date);

    /**
     * The dates this version occupies, where it occupies any.
     *
     * <p>{@code PendingNotification} has none on purpose: it is never in force, so it can neither
     * overlap another version nor be one of two that make a rule change mid-year.
     */
    Optional<DateRange> window();

    /** The ordinary case: in force from a date, until another or indefinitely. */
    record Between(LocalDate from, Optional<LocalDate> to) implements Validity {

        public Between {
            if (from == null) {
                throw new IllegalArgumentException("Validity.Between.from may not be null");
            }
            to = to == null ? Optional.empty() : to;
        }

        public static Between fromOnwards(LocalDate from) {
            return new Between(from, Optional.empty());
        }

        @Override
        public boolean contains(LocalDate date) {
            return !date.isBefore(from) && to.map(end -> !date.isAfter(end)).orElse(true);
        }

        @Override
        public Optional<DateRange> window() {
            return Optional.of(new DateRange(from, to));
        }
    }

    /**
     * Enacted on one date, deemed effective from an earlier one.
     *
     * <p>Both dates are kept because they differ and both matter: the deemed date decides what the
     * law is, the enactment date explains why a stored computation made before it said something
     * else. The Finance Act 2026 inserted a provision deemed effective from 1 June 2007.
     */
    record Retrospective(LocalDate deemedEffectiveFrom, Optional<LocalDate> to,
                         LocalDate actuallyEnactedOn) implements Validity {

        public Retrospective {
            if (deemedEffectiveFrom == null || actuallyEnactedOn == null) {
                throw new IllegalArgumentException("Validity.Retrospective needs both dates");
            }
            to = to == null ? Optional.empty() : to;
            if (actuallyEnactedOn.isBefore(deemedEffectiveFrom)) {
                throw new IllegalArgumentException(
                        "Validity.Retrospective is not retrospective: enacted " + actuallyEnactedOn
                                + " takes effect from " + deemedEffectiveFrom
                                + ". Use Validity.Between instead.");
            }
        }

        @Override
        public boolean contains(LocalDate date) {
            return !date.isBefore(deemedEffectiveFrom)
                    && to.map(end -> !date.isAfter(end)).orElse(true);
        }

        @Override
        public Optional<DateRange> window() {
            return Optional.of(new DateRange(deemedEffectiveFrom, to));
        }
    }

    /**
     * In the statute, not yet in force, awaiting a notification that has not been made — with a
     * stated interim position that applies meanwhile.
     *
     * <p>No verification level describes this: the rule is certain and the date is absent. Its legal
     * status is {@code PENDING_NOTIFICATION}, which must agree with this validity (D-052). Schedule III entry 38A is the live case: the narrower rule applies from a date to be
     * notified, and pending that notification the whole of the disability pension is exempt.
     *
     * <p>{@link #contains} is always false. The rule never applies; the interim rule does.
     */
    record PendingNotification(RuleId interimRule, String interimDescription, Provenance basis)
            implements Validity {

        public PendingNotification {
            if (interimRule == null) {
                throw new IllegalArgumentException(
                        "Validity.PendingNotification must name the interim rule that applies until"
                                + " the notification is made (invariant I16)");
            }
            if (interimDescription == null || interimDescription.isBlank()) {
                throw new IllegalArgumentException(
                        "Validity.PendingNotification must describe the interim position, so a user"
                                + " can be told what applies instead");
            }
        }

        @Override
        public boolean contains(LocalDate date) {
            return false;
        }

        @Override
        public Optional<DateRange> window() {
            return Optional.empty();
        }
    }
}
