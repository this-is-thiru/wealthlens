package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * One person's part of a journey on leave, entered where the concession's two-children limit can leave a child's
 * travel out (S4 r.278(5)–(6); Rules 1962 r.2B(4); D-212). The limit falls on children, not journeys, so a journey
 * whose cost cannot be split by traveller cannot be held to it.
 *
 * @param familyMember the traveller's position in {@link FamilySnapshot#members()}; empty for the employee, who is in
 *     no family list
 * @param amountIncurred what was actually spent on this person's travel
 * @param fareAdmissible the fare the Rules admit for this person, as the taxpayer states it; empty exactly where the
 *     journey is priced per kilometre
 */
public record JourneyTraveller(Optional<Integer> familyMember, Money amountIncurred, Optional<Money> fareAdmissible) {

    public JourneyTraveller {
        if (familyMember == null || amountIncurred == null || fareAdmissible == null) {
            throw new IllegalArgumentException("A traveller needs an amount; pass Optional.empty() for the employee or"
                    + " a fare not given");
        }
        if (familyMember.filter(position -> position < 0).isPresent()) {
            throw new IllegalArgumentException("A traveller's family position is none or more, but was "
                    + familyMember.get());
        }
        if (amountIncurred.isLessThan(Money.ZERO) || fareAdmissible.filter(fare -> fare.isLessThan(Money.ZERO))
                .isPresent()) {
            throw new IllegalArgumentException("A traveller's amount incurred and fare may not be negative");
        }
    }
}
