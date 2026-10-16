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

    /** Any other interest — deposits, savings — charged by the residual rule (S2 s.92(1); 1961 s.56(1)). */
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

    /** Winnings from lotteries, races, games or betting (S2 s.92(2)(b)): refused, their rate not built. */
    WINNINGS,

    /** Any other income under the head — S2 s.92(2)(c), (d), (f)–(h), (j)–(l) and the residue: refused until W-079g. */
    OTHER
}
