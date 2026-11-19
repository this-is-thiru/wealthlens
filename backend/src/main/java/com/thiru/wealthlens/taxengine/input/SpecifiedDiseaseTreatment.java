package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * Treatment of a prescribed disease or ailment paid for in the tax year (S2 s.128; D-138). The disease and the
 * prescribing specialist are the Rules' (s.128(1)–(2)) — conditions of entry, not computed here.
 *
 * @param forAssessee whether the treatment was the assessee's own
 * @param familyMember the {@link FamilySnapshot} member treated, by position, where it was a dependant's (s.127(9))
 * @param paid the amount actually paid in the tax year
 * @param reimbursed what an insurer paid or an employer reimbursed for it (s.128(3))
 */
public record SpecifiedDiseaseTreatment(boolean forAssessee, Optional<Integer> familyMember, Money paid,
        Money reimbursed) {

    public SpecifiedDiseaseTreatment {
        if (familyMember == null || paid == null || reimbursed == null) {
            throw new IllegalArgumentException("Specified-disease treatment needs whom, what was paid and reimbursed");
        }
        if (forAssessee == familyMember.isPresent()) {
            throw new IllegalArgumentException("Specified-disease treatment is for the assessee or one family member");
        }
        if (paid.isLessThan(Money.ZERO) || reimbursed.isLessThan(Money.ZERO)
                || familyMember.filter(position -> position < 0).isPresent()) {
            throw new IllegalArgumentException("Specified-disease treatment's amounts and position must be valid");
        }
    }
}
