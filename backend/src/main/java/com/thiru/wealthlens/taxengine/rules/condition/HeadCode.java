package com.thiru.wealthlens.taxengine.rules.condition;

/**
 * The five heads under which income is classified.
 *
 * <p>Four of them are what this product computes ({@code ITR-1-ELIG-001} lists salaries, house
 * property, other sources and capital gains). The fifth is here for a reason that has nothing to do
 * with computing it: {@code REG-OPTOUT-YEARLY-001} establishes that a salaried person may switch
 * regime <b>every year</b>, and that s.202(4)(a)'s lock-in and one-time-withdrawal rules apply only
 * to a person <b>having</b> business or professional income.
 *
 * <p>So {@code BUSINESS_OR_PROFESSION} is the head whose mere presence changes which regime rules
 * apply — the trap {@code 20-product-gaps.md} F32 names: taking &#8377;50,000 of freelance work puts
 * the taxpayer under a regime lock-in for every future year. A product that cannot represent the
 * head cannot warn about it.
 */
public enum HeadCode {
    SALARIES,
    HOUSE_PROPERTY,
    BUSINESS_OR_PROFESSION,
    CAPITAL_GAINS,
    OTHER_SOURCES
}
