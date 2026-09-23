package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.condition.DisabilitySeverity;
import com.thiru.wealthlens.taxengine.rules.condition.PersonType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

/**
 * Who the taxpayer is, as far as the rules are concerned.
 *
 * <h2>A projection, not the profile</h2>
 *
 * <p>{@code DR 23} A3 and decision <b>D-024</b>. This is {@code taxengine}'s own record, and
 * {@code taxplanning} maps its {@code TaxpayerProfile} ({@code 15-review-findings.md} F1) into it
 * when assembling {@code EngineInputs}. The engine may not see a {@code taxplanning} type — the
 * module forbids it and {@code ArchitectureTest.taxengine_fetches_nothing} enforces it — and
 * inverting that would make the engine's purity a convention rather than a rule.
 *
 * <p>Projecting rather than sharing also keeps the persistence shape free to change. A profile
 * gains a field, a stored draft is upcast, an identifier changes type: none of it reaches here
 * unless a rule needs the fact.
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
 * <h2>No PAN</h2>
 *
 * <p>A3 describes this record as "PAN, DOB, residence". There is no PAN. {@code Pan} is
 * {@code W-096}'s type in {@code taxplanning}; <b>no rule computes on a PAN</b>; and {@code O1.1}
 * requires it masked wherever it is logged. A number the engine cannot use and must not print does
 * not belong in the engine's input.
 *
 * @param dateOfBirth drives every age-dependent rule; see the class note on why it is not an age
 * @param residentialStatus three states, not a boolean — see {@link ResidentialStatus}
 * @param personTypes drives regime eligibility ({@code 10-extensibility.md} E3.2); never empty
 * @param disability the certified severity, where one is certified. {@code DED-80U-001} and
 *     {@code DED-80DD-001} read the severity and nothing else about the certificate
 * @param dateOfDeath {@code 13-edge-cases.md} B1-8 — carried, and not yet read by any rule
 */
public record TaxpayerFacts(
        LocalDate dateOfBirth,
        ResidentialStatus residentialStatus,
        Set<PersonType> personTypes,
        Optional<DisabilitySeverity> disability,
        Optional<LocalDate> dateOfDeath) {

    /**
     * Residence, as {@code 05-domain-model.md:379} states it.
     *
     * <p>Three states, deliberately. {@code ConditionContext} collapses them to
     * {@code boolean resident}, which is right for the predicates built so far but is <b>lossy</b>:
     * a not-ordinarily-resident taxpayer is resident for some provisions and not others. The
     * projection keeps what the profile knew; the collapse happens at the point of use, where it
     * can be argued about.
     */
    public enum ResidentialStatus {
        RESIDENT,
        RNOR,
        NON_RESIDENT
    }

    public TaxpayerFacts {
        if (dateOfBirth == null) {
            throw new IllegalArgumentException(
                    "TaxpayerFacts must carry a date of birth; every age-dependent rule reads it");
        }
        if (residentialStatus == null) {
            throw new IllegalArgumentException("TaxpayerFacts must state a residential status");
        }
        if (personTypes == null || personTypes.isEmpty()) {
            throw new IllegalArgumentException(
                    "TaxpayerFacts must state at least one PersonType; regime eligibility is"
                            + " decided by it, so an empty set makes every regime inapplicable");
        }
        if (disability == null || dateOfDeath == null) {
            throw new IllegalArgumentException(
                    "TaxpayerFacts optionals may not be null; pass Optional.empty() where the fact"
                            + " is absent");
        }
        if (dateOfDeath.isPresent() && dateOfDeath.get().isBefore(dateOfBirth)) {
            throw new IllegalArgumentException(
                    "TaxpayerFacts.dateOfDeath " + dateOfDeath.get() + " precedes dateOfBirth "
                            + dateOfBirth);
        }
        personTypes = Set.copyOf(personTypes);
    }
}
