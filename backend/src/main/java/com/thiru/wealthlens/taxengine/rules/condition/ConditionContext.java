package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Map;
import java.util.Optional;
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
 * <p><b>One context describes one claim, not one taxpayer.</b> {@link PaidOtherThanInCash} takes no
 * argument — it asks how <em>this</em> payment was made — so a context is built per rule evaluation.
 * Caching one per user and reusing it across claims would make that predicate answer about whichever
 * payment happened to be loaded first. {@link EvidenceRecorded} and {@link ComponentPresent} read
 * the same way.
 *
 * @param resident whether the assessee is resident
 * @param personType the kind of assessee
 * @param ages age in completed years, per subject — a subject absent here is a fact not supplied
 * @param disabilities certified severity, per subject; absent means no certificate was recorded,
 *     which is a legitimate negative rather than a missing fact
 * @param nodeValues computed amounts this condition may compare against
 * @param headsWithIncome the heads under which the assessee has income
 * @param componentsPresent the pay lines present in this computation
 * @param evidenceRecorded the artefacts furnished for the claim this context describes
 * @param paidOtherThanInCash how the payment under this claim was made; empty where the claim
 *     involves no payment, so a rule asking about payment mode fails rather than guessing
 */
public record ConditionContext(
        boolean resident,
        PersonType personType,
        Map<AgeSubject, Integer> ages,
        Map<AgeSubject, DisabilitySeverity> disabilities,
        Map<NodeId, Money> nodeValues,
        Set<HeadCode> headsWithIncome,
        Set<ComponentCode> componentsPresent,
        Set<EvidenceType> evidenceRecorded,
        Optional<Boolean> paidOtherThanInCash) {

    public ConditionContext {
        if (personType == null) {
            throw new IllegalArgumentException("A ConditionContext must state the person type");
        }
        if (ages == null || disabilities == null) {
            throw new IllegalArgumentException("ConditionContext maps may not be null");
        }
        if (nodeValues == null || headsWithIncome == null || componentsPresent == null
                || evidenceRecorded == null || paidOtherThanInCash == null) {
            throw new IllegalArgumentException("ConditionContext collections may not be null");
        }
        ages = Map.copyOf(ages);
        disabilities = Map.copyOf(disabilities);
        nodeValues = Map.copyOf(nodeValues);
        headsWithIncome = Set.copyOf(headsWithIncome);
        componentsPresent = Set.copyOf(componentsPresent);
        evidenceRecorded = Set.copyOf(evidenceRecorded);
    }

    /** The person facts only — for a rule that asks nothing about the computation. */
    public ConditionContext(
            boolean resident,
            PersonType personType,
            Map<AgeSubject, Integer> ages,
            Map<AgeSubject, DisabilitySeverity> disabilities) {
        this(resident, personType, ages, disabilities,
                Map.of(), Set.of(), Set.of(), Set.of(), Optional.empty());
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

    /**
     * A computed amount.
     *
     * @throws IllegalArgumentException if that node has not been computed — a threshold compared
     *     against a value nobody produced is a pipeline ordering bug, not a taxpayer who earns zero
     */
    public Money valueOf(NodeId node) {
        Money value = nodeValues.get(node);
        if (value == null) {
            throw new IllegalArgumentException(
                    "No value for node " + node + "; the context holds " + nodeValues.keySet());
        }
        return value;
    }

    /**
     * Whether the payment under this claim was made otherwise than in cash.
     *
     * @throws IllegalArgumentException where the claim records no payment mode — s.126(9) and
     *     s.133(5) turn on it, and assuming either answer silently allows or denies a deduction
     */
    public boolean wasPaidOtherThanInCash() {
        return paidOtherThanInCash.orElseThrow(() -> new IllegalArgumentException(
                "No payment mode recorded for this claim; s.126(9) and s.133(5) turn on it"));
    }
}
