package com.thiru.wealthlens.taxrules.cap;

/**
 * A statutory limit, expressed as one of a closed set of shapes rather than as a formula.
 *
 * <p>Every rupee limit in the rulebook — the standard deduction, the section 80C ceiling, the house
 * rent allowance exemption, the slab ladder itself — is one of six shapes over different numbers.
 * Holding them as shapes rather than as expressions is what lets a rule pack be data: a new
 * deduction is a row, and a genuinely new <em>kind</em> of limit is a new class in this package,
 * never an edit to an existing one.
 *
 * <p>The family is sealed so the compiler can prove that set is closed. A switch over a cap shape
 * that misses a case does not compile, which is the property that makes adding the sixth shape safe.
 *
 * <p><b>The clause is now complete.</b> All six shapes of {@code 05-domain-model.md} M1.3 exist, so
 * the family is closed as designed: a switch over a cap shape that misses a case does not compile.
 * A seventh shape is a new class and a new permit — never an edit to an existing shape.
 */
public sealed interface CapShape permits Fixed, PercentOfBase, SlabLadder, LeastOf, AggregateWithCeiling, PerUnitPerPeriod {

    /** Applies the cap to the amounts this rule declares it reads. */
    CapResult apply(CapContext ctx);

    /** How this cap names itself in a trace line when nothing narrower bound the result. */
    String shapeName();
}
