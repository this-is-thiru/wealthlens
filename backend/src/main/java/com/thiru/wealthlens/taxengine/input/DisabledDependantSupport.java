package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * What the assessee spent on, or deposited for, a dependant with a disability in the tax year (S2 s.127; D-138): the
 * medical treatment, nursing, training and rehabilitation, or a scheme for the dependant's maintenance.
 *
 * @param familyMember the {@link FamilySnapshot} member, by position — a dependant spouse, child, parent, brother or
 *     sister with a certified disability (s.127(9))
 * @param spentOrDeposited the amount spent or deposited in the tax year
 * @param dependantClaimsSection154 whether the dependant claims s.154 for themselves, which excludes them (s.127(8))
 */
public record DisabledDependantSupport(int familyMember, Money spentOrDeposited, boolean dependantClaimsSection154) {

    public DisabledDependantSupport {
        if (spentOrDeposited == null) {
            throw new IllegalArgumentException("Support for a disabled dependant needs its amount");
        }
        if (familyMember < 0 || spentOrDeposited.isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("Support for a disabled dependant needs a valid member and amount");
        }
    }
}
