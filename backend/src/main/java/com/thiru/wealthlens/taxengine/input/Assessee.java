package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.condition.PersonType;

/**
 * Who is being assessed, as far as the rules are concerned: one record per assessee type, each
 * carrying only the facts that type has (D-039, {@code 00-BRIEF.md} §8). An HUF has no date of birth
 * and a person has one type, which the single {@code TaxpayerFacts} it replaced could not say.
 *
 * <h2>A projection, not the profile</h2>
 *
 * <p>{@code DR 23} A3 and decision <b>D-024</b>. These are {@code taxengine}'s own records, and
 * {@code taxplanning} maps its {@code TaxpayerProfile} ({@code 15-review-findings.md} F1) into one
 * when assembling {@code EngineInputs}. The engine may not see a {@code taxplanning} type — the
 * module forbids it and {@code ArchitectureTest.taxengine_fetches_nothing} enforces it — and
 * inverting that would make the engine's purity a convention rather than a rule.
 *
 * <p>Projecting rather than sharing also keeps the persistence shape free to change: none of it
 * reaches here unless a rule needs the fact.
 *
 * <h2>No PAN</h2>
 *
 * <p>A3 describes the record as "PAN, DOB, residence". There is no PAN. {@code Pan} is
 * {@code W-096}'s type in {@code taxplanning}; <b>no rule computes on a PAN</b>; and {@code O1.1}
 * requires it masked wherever it is logged. A number the engine cannot use and must not print does
 * not belong in the engine's input.
 *
 * <p>Rules select a type through {@code HasPersonType} and {@code RegimeSpec.appliesTo}; nothing
 * else needs to know the family exists.
 */
public sealed interface Assessee permits Individual, Huf {

    /** The type the record is, fixed by the record rather than stored — an assessee has exactly one. */
    PersonType type();

    ResidentialStatus residence();

    /**
     * Residence, as {@code 05-domain-model.md:379} states it.
     *
     * <p>Three states, deliberately. {@code ConditionContext} collapses them to
     * {@code boolean resident}, which is right for the predicates built so far but is <b>lossy</b>:
     * a not-ordinarily-resident assessee is resident for some provisions and not others. The
     * projection keeps what the profile knew; the collapse happens at the point of use, where it
     * can be argued about.
     */
    enum ResidentialStatus {
        RESIDENT,
        RNOR,
        NON_RESIDENT
    }
}
