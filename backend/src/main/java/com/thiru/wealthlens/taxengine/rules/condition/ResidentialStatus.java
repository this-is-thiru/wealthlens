package com.thiru.wealthlens.taxengine.rules.condition;

/**
 * Residence, as {@code 05-domain-model.md:379} states it — what the person's profile records and what a condition reads.
 *
 * <p>Three states, deliberately. A not-ordinarily-resident assessee is resident for some provisions and not others:
 * {@link IsResident} counts RNOR as resident (S2 s.5(1)(c) scopes such a person inside "a person, who is a resident"),
 * while r.164(2) asks for "a resident other than not ordinarily resident" ({@link IsOrdinarilyResident}). So the status
 * reaches the conditions whole, and each condition says which states it means (W-090c1a, D-525). It lives beside
 * {@link DisabilitySeverity}, and like it is part of the {@code input} named interface, for the same reason: the inputs
 * state it, the conditions read it, and {@code taxplanning}'s profile carries it.
 */
@org.springframework.modulith.NamedInterface("input")
public enum ResidentialStatus {
    RESIDENT,
    RNOR,
    NON_RESIDENT
}
