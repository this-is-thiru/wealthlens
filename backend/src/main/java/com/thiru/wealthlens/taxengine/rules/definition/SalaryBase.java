package com.thiru.wealthlens.taxengine.rules.definition;

/**
 * The amount is a narrow "salary" — basic, and dearness allowance where the terms of employment provide, but
 * no other allowance or perquisite (S4 r.279(2)(b); S2 s.19(2)(b), s.124(13)(b), Sch. XV para 1(y); D-092).
 *
 * <p>It carries nothing: which components count, and which count only where the terms provide, is the
 * registry's ({@code salaryBaseFor}), so a row can state the definition before any component exists and a
 * component joins a base without the row changing. The rulebook's H-4 is why this is one shape and not five:
 * the same words recur for HRA, gratuity, leave encashment and both pension limits.
 */
public record SalaryBase() implements RuleDefinition {
}
