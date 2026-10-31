package com.thiru.wealthlens.taxengine.input;

/**
 * What an item of income from other sources is, as its deductions turn on it (S2 ss.92–94; 1961 ss.56–58;
 * {@code DR 01} R17; D-194).
 *
 * <p>Some kinds are entered only so they can be refused by name rather than slipping in under a kind that would deduct
 * from them: winnings are taxed at a rate of their own, deferred in the coverage register (1961 s.115BB); employee
 * contributions received and the letting of machinery, plant or furniture take the business head's deductions, which
 * are deferred with it (D-202). Life-policy sums are a {@link LifePolicyReceipt}, not a kind (D-205).
 */
public enum OtherSourceKind {

    /** Interest on securities (S2 s.92(2)(e); 1961 s.56(2)(id)), against which a banker's commission is allowed. */
    INTEREST_ON_SECURITIES,

    /**
     * Interest on a deposit in a savings account, not a time deposit, with a bank, a co-operative society engaged in
     * banking or a Post Office — the interest s.153(2)(a) reaches (S2 s.153(1)(i)–(iii); 1961 s.80TTA(1); D-195).
     */
    SAVINGS_DEPOSIT_INTEREST,

    /**
     * Interest on any other deposit with a bank, a co-operative society engaged in banking or a Post Office, time
     * deposits included — which only a senior citizen's s.153(2)(b) reaches (1961 s.80TTB(1); D-195).
     */
    DEPOSIT_INTEREST,

    /**
     * Any other interest charged by the residual rule (S2 s.92(1); 1961 s.56(1)) — a company deposit, a loan — and
     * never a deposit with a bank, a co-operative bank or a Post Office, which is one of the two kinds above. A
     * deposit held for a firm, an association of persons or a body of individuals is not the assessee's own interest
     * (S2 s.153(3), (4)) and is not entered.
     */
    OTHER_INTEREST,

    /** A dividend, other than a deemed one (S2 s.92(2)(a); 1961 s.56(2)(i)). */
    DIVIDEND,

    /**
     * A dividend deemed by S2 s.2(40)(f) / 1961 s.2(22)(f), against which nothing is deducted (S2 s.93(2); 1961 s.57
     * second proviso).
     */
    DEEMED_DIVIDEND,

    /** Income from units of a specified Mutual Fund or of the specified company (S2 s.93(2); 1961 s.57 proviso). */
    UNIT_INCOME,

    /** Interest on compensation or enhanced compensation (S2 s.92(2)(i); 1961 s.56(2)(viii)): half is deducted. */
    INTEREST_ON_COMPENSATION,

    /**
     * A family pension — a regular monthly amount payable by the employer to a family member of an employee upon the
     * employee's death (S2 s.93(1)(d); 1961 s.57(iia) Explanation; D-196). A gallantry awardee's family pension and an
     * armed-forces family pension for a death in operational duties are excluded from total income (S2 Schedule III
     * Sl. 15, 16; 1961 s.10(18)(ii), (19)) and are not entered.
     */
    FAMILY_PENSION,

    /**
     * Commutation of a pension received from a fund set up by the Life Insurance Corporation of India on or after
     * 1 August 1996, or by another insurer, under a pension scheme (S2 s.93(1)(g), Schedule VII Sl. 3; 1961
     * s.10(10A)(iii), s.10(23AAB); D-196).
     */
    COMMUTED_FUND_PENSION,

    /**
     * A death-cum-retirement gratuity under the Government's pension rules or a similar scheme (S2 s.19(2)(g); 1961
     * s.10(10)(i)), received on the employee's death (S2 s.93(1)(h); D-196). Another employer's gratuity paid on a
     * death has no clause of its own here and is {@link #OTHER}.
     */
    DEATH_GRATUITY,

    /**
     * A sum received under a Keyman insurance policy, bonus included, that is neither salary — an employee's is profits
     * in lieu of salary (S2 s.18(1)(c)(iii); 1961 s.17(3)(iii)) — nor business income (S2 s.92(2)(d); 1961
     * s.56(2)(iv); D-203).
     */
    KEYMAN_POLICY,

    /**
     * An advance received during negotiations for the transfer of a capital asset, forfeited when the negotiations did
     * not end in a transfer (S2 s.92(2)(h); 1961 s.56(2)(ix)).
     */
    FORFEITED_ADVANCE,

    /**
     * Compensation in connection with the termination of employment or a change in its terms, from someone other than
     * the employer or a former employer — whose compensation is profits in lieu of salary (S2 s.18(1)(a); 1961
     * s.17(3)(i)) — (S2 s.92(2)(j); 1961 s.56(2)(xi)).
     */
    TERMINATION_COMPENSATION,

    /** Winnings from lotteries, races, games or betting (S2 s.92(2)(b)): refused, their rate not built. */
    WINNINGS,

    /**
     * Sums received from employees as contributions to their funds (S2 s.92(2)(c); 1961 s.56(2)(ic)): refused, their
     * deduction being the business head's (S2 s.29(1)(e); 1961 s.36(1)(va)), deferred with it (D-202).
     */
    EMPLOYEE_CONTRIBUTIONS,

    /**
     * Income from machinery, plant or furniture let on hire (S2 s.92(2)(f); 1961 s.56(2)(ii)): refused, its deductions
     * — repairs, insurance, depreciation — being the business head's (S2 ss.28, 33; 1961 ss.30–32), deferred with it.
     */
    MACHINERY_LETTING,

    /**
     * Income from letting machinery, plant or furniture with buildings whose letting is inseparable from it (S2
     * s.92(2)(g); 1961 s.56(2)(iii)): refused, as {@link #MACHINERY_LETTING}.
     */
    MACHINERY_AND_BUILDING_LETTING,

    /**
     * Any other income chargeable under the head and under no other — S2 s.92(1), 1961 s.56(1) — the person's claim
     * that it belongs to none of the kinds above and to no other head (D-203).
     */
    OTHER
}
