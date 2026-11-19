package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Interest payable in the tax year on a loan from a financial institution for a house or an electric vehicle, with
 * the facts its section tests (S2 s.130–132; D-139).
 *
 * @param purpose what the loan was for
 * @param interestPayable the interest payable in the tax year
 * @param sanctionedOn when the loan was sanctioned
 * @param amountSanctioned the amount of the loan sanctioned
 * @param propertyValue the value of the house, for s.130(3)(c)
 * @param stampDutyValue the stamp duty value of the house, for s.131(2)(b)
 * @param ownedResidentialHouseOnSanction whether the assessee owned a residential house on the date of sanction
 */
public record PurposeLoanInterest(
        LoanPurpose purpose,
        Money interestPayable,
        LocalDate sanctionedOn,
        Money amountSanctioned,
        Optional<Money> propertyValue,
        Optional<Money> stampDutyValue,
        boolean ownedResidentialHouseOnSanction) {

    public PurposeLoanInterest {
        if (purpose == null || interestPayable == null || sanctionedOn == null || amountSanctioned == null
                || propertyValue == null || stampDutyValue == null) {
            throw new IllegalArgumentException("Loan interest needs its purpose, interest, sanction date and amount;"
                    + " Optional.empty() for a value not known");
        }
        if (interestPayable.isLessThan(Money.ZERO) || amountSanctioned.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("Loan interest and the amount sanctioned may not be negative");
        }
    }
}
