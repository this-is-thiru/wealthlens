package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * How one allowance was used in the year, as its exemption needs to know (S4 r.280; Rules 1962 r.2BB; D-101).
 *
 * <p>A duty allowance (Sch. III Table Sl. 12) is exempt only "to the extent … actually incurred", so the amount
 * spent is entered. A prescribed allowance (Sl. 13) is exempt up to an amount per month per child, so the children
 * and the months it covered are entered — as counts the taxpayer states, which leaves no proration basis for the
 * engine to choose. Which of the two an allowance needs is its rule's business; this record only refuses an entry
 * that carries neither.
 *
 * @param allowance the allowance component the use is for
 * @param amountIncurred the amount actually spent for the allowance's purpose
 * @param childrenCovered how many children the allowance was paid for
 * @param monthsCovered how many months of the year it was paid for
 */
public record AllowanceUse(
        ComponentCode allowance,
        Optional<Money> amountIncurred,
        Optional<Integer> childrenCovered,
        Optional<Integer> monthsCovered) {

    public AllowanceUse {
        if (allowance == null || amountIncurred == null || childrenCovered == null || monthsCovered == null) {
            throw new IllegalArgumentException("An allowance use needs its allowance; pass Optional.empty() for a fact"
                    + " not given");
        }
        if (childrenCovered.isPresent() != monthsCovered.isPresent()) {
            throw new IllegalArgumentException(allowance + ": children and months covered go together");
        }
        if (amountIncurred.isEmpty() && childrenCovered.isEmpty()) {
            throw new IllegalArgumentException(allowance + " needs the amount actually incurred, or the children and"
                    + " months it covered");
        }
        if (amountIncurred.filter(amount -> amount.isLessThan(Money.ZERO)).isPresent()) {
            throw new IllegalArgumentException(allowance + ": the amount incurred may not be negative");
        }
        if (childrenCovered.filter(children -> children < 1).isPresent()) {
            throw new IllegalArgumentException(allowance + ": at least one child must be covered");
        }
        if (monthsCovered.filter(months -> months < 1 || months > 12).isPresent()) {
            throw new IllegalArgumentException(allowance + ": months covered must be from 1 to 12, but was "
                    + monthsCovered.get());
        }
    }

    /** A duty allowance and what was actually spent for its purpose. */
    public static AllowanceUse spent(ComponentCode allowance, Money amountIncurred) {
        return new AllowanceUse(allowance, Optional.ofNullable(amountIncurred), Optional.empty(), Optional.empty());
    }

    /** A prescribed allowance and the children and months it covered. */
    public static AllowanceUse forChildren(ComponentCode allowance, int children, int months) {
        return new AllowanceUse(allowance, Optional.empty(), Optional.of(children), Optional.of(months));
    }
}
