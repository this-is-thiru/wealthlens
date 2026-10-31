package com.thiru.wealthlens.taxengine.rules.condition;

/**
 * A named person is at least this many completed years old.
 *
 * <p>The subject matters and is not decoration. {@code DED-80D-007} allows the higher
 * medical-insurance limit where <em>either</em> the taxpayer <em>or</em> the person insured is sixty
 * or over — two questions about two people, written as an {@code AnyOf} of two
 * {@code AgeAtLeast(60, …)}. With no subject both limbs are the same predicate and the rule quietly
 * becomes a test of the taxpayer's own age.
 *
 * <p>{@code RATE-SLAB-AGE-001} is a reminder that the threshold is not the only subtlety: the age
 * test there is <em>&ldquo;at any time during the tax year&rdquo;</em>, not age on the last day. That
 * is resolved before it reaches here, by whoever fills the context.
 *
 * @param years completed years, as the statute states them
 * @param subject whose age is being tested
 */
public record AgeAtLeast(int years, AgeSubject subject) implements Condition {

    public AgeAtLeast {
        if (years < 0) {
            throw new IllegalArgumentException("An age threshold may not be negative, but was " + years);
        }
        if (subject == null) {
            throw new IllegalArgumentException("AgeAtLeast must name whose age it tests");
        }
    }

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.age(subject) >= years;
    }

    @Override
    public String describe() {
        return switch (subject) {
            case SELF -> "you are " + years + " or over";
            case INSURED_PERSON -> "the insured person is " + years + " or over";
        };
    }
}
