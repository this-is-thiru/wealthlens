package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * A holding of identical units of one business trust, with what is needed for the specified sum it is charged on (S2
 * s.92(2)(k); 1961 s.56(2)(xii); D-203): specified sum = A − B − C, nil where B and C together exceed A.
 *
 * <p>The figures are the holding's aggregates. For identical units the holding's specified sum is the units' sum of
 * their own, nil included, so one entry per holding gives the per-unit answer.
 *
 * @param distributed A: the aggregate the trust distributed on the units, in this tax year or earlier, other than
 *     income of the natures in S2 Schedule V Sl. 3 or 4 (1961 s.10(23FC), (23FCA)) and sums chargeable under S2
 *     s.223(2) (1961 s.115UA(2)) — as the trust reports it
 * @param issuedAt B: the amount at which the units were issued by the trust
 * @param chargedEarlier C: the amount charged under this clause in earlier tax years
 */
public record BusinessTrustUnits(Money distributed, Money issuedAt, Money chargedEarlier) {

    public BusinessTrustUnits {
        for (Money figure : new Money[] {distributed, issuedAt, chargedEarlier}) {
            if (figure == null || figure.isLessThan(Money.ZERO)) {
                throw new IllegalArgumentException("A business-trust holding needs A, B and C, none negative; enter 0"
                        + " where nil");
            }
        }
    }

    /** A − B − C, deemed nil where B and C together are greater than A — and so nil where they equal it. */
    public Money specifiedSum() {
        Money specified = distributed.minus(issuedAt).minus(chargedEarlier);
        return specified.isLessThan(Money.ZERO) ? Money.ZERO : specified;
    }
}
