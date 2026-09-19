package com.thiru.wealthlens.taxrules.condition;

/**
 * The payment under this claim was made otherwise than in cash.
 *
 * <p>Two rules turn on it. s.126(9) permits cash for a preventive health check-up and for nothing
 * else under 80D; s.133(5) disallows an 80G donation above &#8377;2,000 paid in cash. The threshold
 * amounts are pack data — this predicate asks only about the mode.
 *
 * <p>It takes no argument, which is what fixes {@code ConditionContext}'s scope: a context is about
 * <b>one claim</b>, so that &ldquo;this payment&rdquo; has an unambiguous referent. A context reused
 * across claims would make this predicate answer about whichever payment was loaded first.
 */
public record PaidOtherThanInCash() implements Condition {

    @Override
    public boolean holds(ConditionContext ctx) {
        return ctx.wasPaidOtherThanInCash();
    }

    @Override
    public String describe() {
        return "the payment was made otherwise than in cash";
    }
}
