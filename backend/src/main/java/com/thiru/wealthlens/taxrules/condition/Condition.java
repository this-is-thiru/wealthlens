package com.thiru.wealthlens.taxrules.condition;

/**
 * A yes/no test a rule attaches to itself — residency, age, whether a component is present.
 *
 * <p>A closed algebra of typed objects, deliberately, rather than a string expression language. The
 * difference shows up in three places:
 *
 * <ul>
 *   <li><b>Closed.</b> A condition the algebra cannot express is a new record — a new class — never
 *       new syntax. There is no parser, so there is nothing to get wrong at load time and no
 *       expression that means one thing in a pack and another in the engine.
 *   <li><b>Inspectable.</b> The failing condition is an object with a name, which is what lets the
 *       advisory layer say <em>&ldquo;unavailable because you are not a resident&rdquo;</em> rather
 *       than reporting a nil deduction and leaving the taxpayer to guess.
 *   <li><b>Testable in isolation.</b> Each record is a unit test of a few lines. Observation O-1's
 *       failure — the evaluator throws and the caller substitutes zero — has no analogue, because
 *       there is no evaluator to throw.
 * </ul>
 *
 * <p><b>Dating is not in this algebra.</b> {@code EffectiveBetween} was withdrawn by
 * {@code 18-temporal-versioning.md} V-2: a rule whose limit changes mid-year must keep <b>one</b>
 * rule id, because the id is the contract. The change is a second {@code RuleVersion} with its own
 * {@code Validity}, not a different rule guarded by a date predicate.
 *
 * <p><b>The clause is complete.</b> All thirteen predicates of {@code 15-review-findings.md} Part E
 * exist — four person facts, two amounts, four structure, three composition — so the algebra is
 * closed as designed: a switch over a condition that misses a case does not compile. A fourteenth
 * predicate is a new class and a new permit, never an edit to an existing one.
 */
public sealed interface Condition
        permits IsResident,
                AgeAtLeast,
                HasPersonType,
                IsCertifiedDisabled,
                IncomeAtMost,
                IncomeAtLeast,
                HasIncomeUnderHead,
                ComponentPresent,
                EvidenceRecorded,
                PaidOtherThanInCash,
                AllOf,
                AnyOf,
                Not {

    /** Whether this condition is met by the taxpayer the context describes. */
    boolean holds(ConditionContext ctx);

    /** How this condition names itself when it is the reason a claim was refused. */
    String describe();
}
