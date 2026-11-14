package com.thiru.wealthlens.taxengine.input;

import com.thiru.wealthlens.taxengine.rules.definition.MovableAssetKind;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.Optional;

/**
 * A movable asset belonging to or hired by the employer that the employee or a household member used (S4 r.15(5)
 * Table IV Sl. 7; Rules 1962 r.3(7)(vii); D-119).
 *
 * <p>Owned, it is valued at ten per cent a year of its cost, apportioned to the months used (D-119 C3); hired, at the
 * rent the employer paid for the period — exactly one of the two is entered.
 *
 * @param employment which entered employment lent it, by position
 * @param kind the class the year's exclusion reads
 * @param cost the actual cost of an asset the employer owns
 * @param rentPaid the rent or charge the employer paid for a hired asset, for the period used
 * @param months the months in the tax year it was used, 0 to 12
 * @param recovered what the employee paid or had recovered for the use
 */
public record EmployerAssetUse(
        int employment,
        MovableAssetKind kind,
        Optional<Money> cost,
        Optional<Money> rentPaid,
        int months,
        Money recovered) {

    public EmployerAssetUse {
        if (kind == null || cost == null || rentPaid == null || recovered == null) {
            throw new IllegalArgumentException("An asset lent needs its kind and recovery; pass Optional.empty() for"
                    + " the cost or the rent not given");
        }
        if (cost.isPresent() == rentPaid.isPresent()) {
            throw new IllegalArgumentException("Enter the cost of an owned asset or the rent of a hired one, not "
                    + (cost.isPresent() ? "both" : "neither"));
        }
        if (employment < 0 || months < 0 || months > 12) {
            throw new IllegalArgumentException("An employment position may not be negative, and months lie in 0 to 12");
        }
        if (recovered.isLessThan(Money.ZERO) || cost.or(() -> rentPaid).orElseThrow().isLessThan(Money.ZERO)) {
            throw new IllegalArgumentException("Asset amounts may not be negative");
        }
    }
}
