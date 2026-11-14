package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.definition.CommutingCity;
import com.thiru.wealthlens.taxengine.rules.definition.TransportDisability;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * A disabled employee's claim to the transport allowance exemption (S4 r.280(2) Table Sl. 10; Rules 1962 r.2BB(2)
 * Table Sl. 11; D-102).
 *
 * <p>Apart from {@link AllowanceUse} because it fits neither of that record's shapes: the limit turns on the
 * disability, and from TY 2026-27 on the city class and the dearness allowance paid on the allowance — two terms
 * the Rules leave undefined (OQ-7), so the taxpayer states them as the employer applies them. Which of these facts a
 * year reads is the rule's business; this record only refuses what no year could accept.
 *
 * @param condition the disability, in the Rules' own categories
 * @param monthsCovered how many months of the year the allowance was paid for
 * @param city whether the place counts as a metro city, where the year's limit depends on it
 * @param dearnessAllowanceThereon the dearness allowance paid on the allowance, per month, where the year adds it
 */
public record DisabledTransportClaim(
        TransportDisability condition,
        int monthsCovered,
        Optional<CommutingCity> city,
        Optional<Money> dearnessAllowanceThereon) {

    public DisabledTransportClaim {
        if (condition == null || city == null || dearnessAllowanceThereon == null) {
            throw new IllegalArgumentException("A transport claim needs its condition; pass Optional.empty() for a"
                    + " fact not given");
        }
        if (monthsCovered < 1 || monthsCovered > 12) {
            throw new IllegalArgumentException("Months covered must be from 1 to 12, but was " + monthsCovered);
        }
        if (dearnessAllowanceThereon.filter(amount -> amount.isLessThan(Money.ZERO)).isPresent()) {
            throw new IllegalArgumentException("The dearness allowance thereon may not be negative");
        }
    }
}
