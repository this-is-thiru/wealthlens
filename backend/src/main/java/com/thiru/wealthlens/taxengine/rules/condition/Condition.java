package com.thiru.wealthlens.taxengine.rules.condition;

import com.thiru.wealthlens.taxengine.rules.id.ItemFactId;
import java.util.Set;
import java.util.stream.Collectors;

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
 * predicate is a new class and a new permit, never an edit to an existing one. The fourteenth is
 * {@link ItemFactIs}: a fact of the item a node is computing, so the row for a car or a payment is chosen in data
 * (W-088m2, D-253); the fifteenth, {@link ItemFactAtMost}, its counted facts (W-088n1, D-257); the sixteenth,
 * {@link ItemDateBefore}, its dated facts (W-088f2b, D-291). That last one dates a fact of the item — when a lot was
 * acquired — to decide whether a row applies, which is what a condition is for; it does not date the rule (V-2).
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
                ItemFactIs,
                ItemFactAtMost,
                ItemDateBefore,
                AllOf,
                AnyOf,
                Not {

    /** Whether this condition is met by the taxpayer the context describes. */
    boolean holds(ConditionContext ctx);

    /** How this condition names itself when it is the reason a claim was refused. */
    String describe();

    /**
     * Whether this condition reads the item a node is computing — so a node can test a row's conditions about the
     * person once and its conditions about the item per item, the item's tested with no item throwing (W-153a, D-319).
     * A composition reads the item where any limb does.
     */
    default boolean isAboutItem() {
        return switch (this) {
            case ItemFactIs _, ItemFactAtMost _, ItemDateBefore _ -> true;
            case AllOf all -> all.all().stream().anyMatch(Condition::isAboutItem);
            case AnyOf any -> any.any().stream().anyMatch(Condition::isAboutItem);
            case Not not -> not.condition().isAboutItem();
            case IsResident _, AgeAtLeast _, HasPersonType _, IsCertifiedDisabled _, IncomeAtMost _, IncomeAtLeast _,
                    HasIncomeUnderHead _, ComponentPresent _, EvidenceRecorded _, PaidOtherThanInCash _ -> false;
        };
    }

    /**
     * The item facts this condition mentions — so a node can refuse an entered fact no row reads, since it would be
     * ignored (W-158a, D-357); the condition twin of {@code CapShape.amountsRead}. A composition reads what its limbs
     * read.
     */
    default Set<ItemFactId> itemFactsRead() {
        return switch (this) {
            case ItemFactIs is -> Set.of(is.fact());
            case ItemFactAtMost atMost -> Set.of(atMost.fact());
            case ItemDateBefore before -> Set.of(before.fact());
            case AllOf all -> all.all().stream().flatMap(limb -> limb.itemFactsRead().stream())
                    .collect(Collectors.toUnmodifiableSet());
            case AnyOf any -> any.any().stream().flatMap(limb -> limb.itemFactsRead().stream())
                    .collect(Collectors.toUnmodifiableSet());
            case Not not -> not.condition().itemFactsRead();
            case IsResident _, AgeAtLeast _, HasPersonType _, IsCertifiedDisabled _, IncomeAtMost _, IncomeAtLeast _,
                    HasIncomeUnderHead _, ComponentPresent _, EvidenceRecorded _, PaidOtherThanInCash _ -> Set.of();
        };
    }
}
