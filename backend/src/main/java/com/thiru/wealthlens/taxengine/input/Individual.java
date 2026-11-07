package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import java.time.LocalDate;
import java.util.Optional;

/**
 * An individual assessee.
 *
 * <h2>Date of birth, not an age</h2>
 *
 * <p>Decision <b>D-025</b>. {@code 15-review-findings.md} F2 states the senior-citizen test as
 * <i>"at any time during the previous year"</i>, which <b>an age cannot answer</b>: someone who
 * turns sixty in November is a senior citizen for that year, and an age computed at any single
 * instant says otherwise for half the calendar. So the projection carries the date and the
 * derivation happens against {@code TemporalContext}'s {@code asOf} — an input, so
 * {@code taxengine_has_no_clock} still holds.
 *
 * <p>The built {@code ConditionContext} takes {@code Map<AgeSubject, Integer> ages}, so something
 * downstream collapses this date to an integer. That collapse is where the "at any time during"
 * rule is applied, and it belongs to the item that assembles a {@code ConditionContext} — not here,
 * where the fact would be silently discarded.
 *
 * @param dateOfBirth drives every age-dependent rule; see the class note on why it is not an age
 * @param residence three states, not a boolean — see {@link Assessee.ResidentialStatus}
 * @param disability the certified severity, where one is certified. {@code DED-80U-001} and
 *     {@code DED-80DD-001} read the severity and nothing else about the certificate
 * @param dateOfDeath {@code 13-edge-cases.md} B1-8 — carried, and not yet read by any rule
 */
public record Individual(
        LocalDate dateOfBirth,
        ResidentialStatus residence,
        Optional<DisabilitySeverity> disability,
        Optional<LocalDate> dateOfDeath)
        implements Assessee {

    public Individual {
        if (dateOfBirth == null) {
            throw new IllegalArgumentException(
                    "An Individual must carry a date of birth; every age-dependent rule reads it");
        }
        if (residence == null) {
            throw new IllegalArgumentException("An Individual must state a residential status");
        }
        if (disability == null || dateOfDeath == null) {
            throw new IllegalArgumentException(
                    "Individual optionals may not be null; pass Optional.empty() where the fact is absent");
        }
        if (dateOfDeath.isPresent() && dateOfDeath.get().isBefore(dateOfBirth)) {
            throw new IllegalArgumentException(
                    "Individual.dateOfDeath " + dateOfDeath.get() + " precedes dateOfBirth " + dateOfBirth);
        }
    }

    @Override
    public PersonType type() {
        return PersonType.INDIVIDUAL;
    }
}
