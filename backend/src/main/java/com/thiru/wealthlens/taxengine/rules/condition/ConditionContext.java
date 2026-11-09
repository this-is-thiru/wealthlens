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
 * <p><b>One context describes one claim, not one taxpayer.</b> {@link EvidenceRecorded} asks what was
 * furnished for <em>this</em> claim, so a context is built per rule evaluation. Caching one per user
 * and reusing it across claims would make that predicate answer about whichever claim happened to be
 * loaded first. {@link ComponentPresent} reads the same way.
 *
 * @param residence the assessee's residential status, whole: {@link IsResident} and {@link IsOrdinarilyResident} each say
 *     which states they mean (W-090c1a)
 * @param personType the kind of assessee
 * @param ages age in completed years, per subject — a subject absent here is a fact not supplied
 * @param disabilities certified severity, per subject; absent means no certificate was recorded,
 *     which is a legitimate negative rather than a missing fact
 * @param nodeValues computed amounts this condition may compare against
 * @param headsWithIncome the heads under which the assessee has income
 * @param componentsPresent the pay lines present in this computation
 * @param evidenceRecorded the artefacts furnished for the claim this context describes
 * @param item the facts of the item a node is choosing a row for; {@link ItemFacts#NONE} where it chooses for none
 * @param declarations the person's answers to the year's declarations; {@link Declarations#NONE} where none was given
 * @param ownedHouseProperties how many house properties the assessee owns, a share in one counting as owning it
 *     (r.164(2)(b), D-524 C2a); empty where not supplied, and then reading it throws
 */
public record ConditionContext(
        ResidentialStatus residence,
        PersonType personType,
        Map<AgeSubject, Integer> ages,
        Map<AgeSubject, DisabilitySeverity> disabilities,
        Map<NodeId, Money> nodeValues,
        Set<HeadCode> headsWithIncome,
        Set<ComponentCode> componentsPresent,
        Set<EvidenceType> evidenceRecorded,
        ItemFacts item,
        Declarations declarations,
        Optional<Integer> ownedHouseProperties) {

    public ConditionContext {
        if (residence == null || personType == null) {
            throw new IllegalArgumentException("A ConditionContext must state the residential status and the person type");
        }
        if (ownedHouseProperties == null) {
            throw new IllegalArgumentException(
                    "ConditionContext.ownedHouseProperties may not be null; pass Optional.empty() where not supplied");
        }
        ownedHouseProperties.filter(count -> count < 0).ifPresent(count -> {
            throw new IllegalArgumentException("A person cannot own " + count + " house properties");
        });
        if (ages == null || disabilities == null) {
            throw new IllegalArgumentException("ConditionContext maps may not be null");
        }
        if (nodeValues == null || headsWithIncome == null || componentsPresent == null
                || evidenceRecorded == null || item == null || declarations == null) {
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
            ResidentialStatus residence,
            PersonType personType,
            Map<AgeSubject, Integer> ages,
            Map<AgeSubject, DisabilitySeverity> disabilities) {
        this(residence, personType, ages, disabilities,
                Map.of(), Set.of(), Set.of(), Set.of(), ItemFacts.NONE, Declarations.NONE, Optional.empty());
    }

    /** The same facts, about the given item. */
    public ConditionContext about(ItemFacts facts) {
        return new ConditionContext(residence, personType, ages, disabilities, nodeValues, headsWithIncome,
                componentsPresent, evidenceRecorded, facts, declarations, ownedHouseProperties);
    }

    /** The same facts, with the person's answers to the year's declarations. */
    public ConditionContext answering(Declarations answers) {
        return new ConditionContext(residence, personType, ages, disabilities, nodeValues, headsWithIncome,
                componentsPresent, evidenceRecorded, item, answers, ownedHouseProperties);
    }

    /** The same facts, with the number of house properties the assessee owns (r.164(2)(b)). */
    public ConditionContext owning(int houseProperties) {
        return new ConditionContext(residence, personType, ages, disabilities, nodeValues, headsWithIncome,
                componentsPresent, evidenceRecorded, item, declarations, Optional.of(houseProperties));
    }

    /** A resident individual of the given age, with nothing else recorded. */
    public static ConditionContext residentIndividualAged(int years) {
        return new ConditionContext(
                ResidentialStatus.RESIDENT, PersonType.INDIVIDUAL, Map.of(AgeSubject.SELF, years), Map.of());
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

    /**
     * The number of house properties the assessee owns.
     *
     * @throws IllegalArgumentException if it was never supplied — answering nought would let a form's limit pass for a
     *     person whose properties nobody counted
     */
    public int housePropertiesOwned() {
        return ownedHouseProperties.orElseThrow(() -> new IllegalArgumentException(
                "No count of house properties owned was supplied to this context"));
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
}
