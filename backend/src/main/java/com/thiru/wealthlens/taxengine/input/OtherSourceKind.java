package com.thiru.wealthlens.taxengine.input;

/**
 * What an item of income from other sources is, as its deductions turn on it (S2 ss.92–94; 1961 ss.56–58;
 * {@code DR 01} R17; D-194).
 *
 * <p>Winnings and the other kinds the head names are entered so they can be refused by name rather than slipping in
 * under a kind that would deduct from them: winnings are taxed at a rate of their own, deferred in the coverage
 * register (1961 s.115BB), and the remaining clauses of S2 s.92(2) are {@code W-079g}'s.
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

    /** Winnings from lotteries, races, games or betting (S2 s.92(2)(b)): refused, their rate not built. */
    WINNINGS,

    /** Any other income under the head — S2 s.92(2)(c), (d), (f)–(h), (j)–(l) and the residue: refused until W-079g. */
    OTHER
}
