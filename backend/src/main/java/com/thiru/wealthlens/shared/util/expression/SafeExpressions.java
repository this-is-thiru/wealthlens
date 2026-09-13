package com.thiru.wealthlens.shared.util.expression;

import com.thiru.wealthlens.shared.exception.BadRequestException;
import java.util.regex.Pattern;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * The safety floor for every SpEL expression this application stores as data.
 *
 * <h2>What this prevents</h2>
 * Two modules evaluate expressions that arrive as data — a broker's rate card, a tax allowance
 * limit — and both endpoints that write them are {@code SUPER_USER}. Evaluated with SpEL's
 * unrestricted {@code StandardEvaluationContext}, such an expression can reach any class on the
 * classpath: {@code T(java.lang.Runtime).getRuntime().exec(...)} is valid SpEL. That turned "may
 * edit rate cards" into "may run code in the JVM" — the JVM holding the database credentials and
 * the JWT signing key — and it was invisible, because the validators inspected {@code #variable}
 * names and an expression reaching a type contains none.
 *
 * <h2>Two layers, deliberately</h2>
 * {@link #readOnlyContext()} is the guarantee: without a type locator, bean resolver or constructor
 * resolver, the hostile forms cannot resolve however they are spelled. {@link #reject} is the
 * second layer, and exists for a different reason — it refuses the expression when the card is
 * <em>written</em>, naming what is wrong, rather than letting it sit in the database until a trade
 * resolves to it and fails.
 *
 * <p>The second layer is a pattern match and pattern matches can be evaded. That is acceptable
 * precisely because it is not the thing standing in the way: the context is.
 *
 * <h2>Why this is shared when the two evaluators are not</h2>
 * {@code ChargeFormulaEvaluator} and the tax planning {@code FormulaEvaluator} stay separate on
 * purpose — different vocabularies, different return contracts, neither depending on the other. The
 * safety floor is the one part that must not be duplicated, because a second copy of a security
 * control is how one of them comes to be missed. Which is exactly what happened here: the charges
 * evaluator was found first, and the tax one had the identical hole.
 */
public final class SafeExpressions {

    /** Type references, constructors and bean references — the three ways out of the sandbox. */
    private static final Pattern UNSAFE = Pattern.compile("\\bT\\s*\\(|\\bnew\\s+[A-Za-z_$]|@[A-Za-z_$]");

    private SafeExpressions() {
    }

    /**
     * An evaluation context that can read the variables it is given and nothing else.
     *
     * <p>{@code forReadOnlyDataBinding} supplies no type locator, no bean resolver and no
     * constructor resolver, so {@code T(...)}, {@code @bean} and {@code new ...} fail to resolve.
     * Variables, arithmetic, comparison, the ternary and map indexing — everything the stored
     * formulas actually use — are unaffected.
     */
    public static EvaluationContext readOnlyContext() {
        return SimpleEvaluationContext.forReadOnlyDataBinding().build();
    }

    /**
     * Refuses an expression that tries to leave the vocabulary.
     *
     * @throws BadRequestException naming the expression, so the card's author can see what was
     *                             rejected rather than being told only that something was
     */
    public static void reject(String expression) {
        if (expression == null || expression.isBlank()) {
            return;
        }
        if (UNSAFE.matcher(expression).find()) {
            throw new BadRequestException("Expression is not allowed to reference types, constructors"
                    + " or beans: " + expression + ". A formula may use only the published variables"
                    + " and arithmetic over them.");
        }
    }
}
