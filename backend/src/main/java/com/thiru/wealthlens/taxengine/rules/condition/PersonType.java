package com.thiru.wealthlens.taxengine.rules.condition;

/**
 * The kind of assessee a rule, regime or return form applies to.
 *
 * <p>Only {@code INDIVIDUAL} exists, because that is the whole of the product's scope — decision D1
 * is an individual salaried taxpayer — and the design attests no other value:
 * {@code 21-itr-forms.md:73} writes {@code "appliesTo": ["INDIVIDUAL"]} and
 * {@code 17-regime-lifecycle.md:379} tests two regimes both default for {@code INDIVIDUAL}.
 *
 * <p>A one-valued enum looks redundant and is not. It is what makes
 * {@code exactly one default regime per (taxYear, PersonType)} (invariant I11) and
 * {@code at least one form per (PersonType, ReturnKind)} (I19) expressible now, so that widening to
 * a Hindu undivided family later adds a constant and a pack row rather than a dimension the rules
 * never had.
 */
public enum PersonType {
    INDIVIDUAL
}
