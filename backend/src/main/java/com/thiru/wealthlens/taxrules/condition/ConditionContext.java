package com.thiru.wealthlens.taxrules.condition;

import java.util.Map;
import java.util.Set;

/**
 * The taxpayer facts a {@link Condition} is allowed to ask about.
 *
 * <p>The same bargain {@code CapContext} strikes for amounts: a condition is data loaded from a rule
 * pack, so it knows that a deduction needs a resident aged sixty but not who this taxpayer is. A
 * condition reads what its rule declares, and nothing else.
 *
 * <p>Every lookup for a fact the context does not hold <b>throws</b> rather than answering false.
 * Answering false would silently deny a deduction the taxpayer is entitled to, and a denial that
 * looks like a legitimate result is worse than an error — {@code D-NOSILENT}. Returning
 * {@code INCOMPLETE_INPUTS_FOR_RULE} to the caller is the point; guessing is not.
 *
 * @param resident whether the assessee is resident
 * @param personType the kind of assessee
 * @param ages age in completed years, per subject — a subject absent here is a fact not supplied
 * @param disabilities certified severity, per subject; absent means no certificate was recorded,
 *     which is a legitimate negative rather than a missing fact
 */
public record ConditionContext(
        boolean resident,
        PersonType personType,
        Map<AgeSubject, Integer> ages,
        Map<AgeSubject, DisabilitySeverity> disabilities) {

    public ConditionContext {
        if (personType == null) {
            throw new IllegalArgumentException("A ConditionContext must state the person type");
        }
        if (ages == null || disabilities == null) {
            throw new IllegalArgumentException("ConditionContext maps may not be null");
        }
        ages = Map.copyOf(ages);
        disabilities = Map.copyOf(disabilities);
    }

    /** A resident individual of the given age, with nothing else recorded. */
    public static ConditionContext residentIndividualAged(int years) {
        return new ConditionContext(
                true, PersonType.INDIVIDUAL, Map.of(AgeSubject.SELF, years), Map.of());
    }

    /**
     * Age in completed years.
     *
     * @throws IllegalArgumentException if the age of that subject was never supplied
     */
    public int age(AgeSubject subject) {
        Integer years = ages.get(subject);
        if (years == null) {
            throw new IllegalArgumentException(
                    "No age recorded for " + subject + "; the context holds ages for " + ages.keySet());
        }
        return years;
    }

    /** The certified severity for a subject, or empty where no certificate was recorded. */
    public java.util.Optional<DisabilitySeverity> disability(AgeSubject subject) {
        return java.util.Optional.ofNullable(disabilities.get(subject));
    }

    /** Whether the assessee is one of the kinds a rule applies to. */
    public boolean isAnyOf(Set<PersonType> types) {
        return types.contains(personType);
    }
}
