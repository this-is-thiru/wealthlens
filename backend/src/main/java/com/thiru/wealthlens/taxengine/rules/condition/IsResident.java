package com.thiru.wealthlens.taxengine.rules.condition;

/**
 * The assessee is resident.
 *
 * <p>Carried by {@code DED-80U-001} (s.154(1)) among others, and by the higher old-regime basic
 * exemptions: {@code RATE-SLAB-AGE-001} is explicit that the senior and very-senior bands apply
 * only to a <b>resident</b> individual.
 *
 * <p>A person "not ordinarily resident" is a resident here, because the Act scopes such a person inside "a person, who
 * is a resident" (S2 s.5(1)(c)); {@link IsOrdinarilyResident} is the narrower test.
 */
public record IsResident() implements Condition {

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.residence() != ResidentialStatus.NON_RESIDENT;
    }

    @Override
    public String describe() {
        return "the assessee is resident";
    }
}
