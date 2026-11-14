package com.thiru.wealthlens.taxengine.rules.definition;

import com.thiru.wealthlens.taxengine.rules.money.Money;

/**
 * The amount above which a claim needs particulars the taxpayer must give the employer — S4 r.205(2) Table Sl. 1's
 * landlord's name, address and PAN "where the aggregate rent paid during the tax year exceeds ₹ 100000" (1961: r.26C(2)
 * Table Sl. 1; D-167). A definition because it limits no amount: crossing it calls for a notice, never a different
 * figure.
 *
 * @param exceeds particulars are due where the amount is more than this; exactly this does not call for them
 */
public record ParticularsThreshold(Money exceeds) implements RuleDefinition {

    public ParticularsThreshold {
        if (exceeds == null) {
            throw new IllegalArgumentException("A particulars threshold must state the amount it is exceeded above");
        }
    }

    /** Whether {@code amount} is above the threshold — "exceeds", so equal is not. */
    public boolean isExceededBy(Money amount) {
        return amount.isGreaterThan(exceeds);
    }
}
