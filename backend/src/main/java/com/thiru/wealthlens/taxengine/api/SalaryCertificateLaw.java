package com.thiru.wealthlens.taxengine.api;

import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import java.util.Map;

/**
 * What a salary certificate's figures are checked against in a year's law (W-128c, D-750; {@code DR 06} G5.2), read from the
 * year's pack, never written in code.
 *
 * @param defaultRegime the regime a "No" to "Whether opting out of taxation under section 202(1)?" keeps
 * @param standardDeductionCeilings each regime's standard deduction before the salary limb of its "whichever is less"
 * @param cessRate the health and education cess, as a share of tax and surcharge
 */
public record SalaryCertificateLaw(RegimeCode defaultRegime, Map<RegimeCode, Money> standardDeductionCeilings, Rate cessRate) {

    public SalaryCertificateLaw {
        standardDeductionCeilings = Map.copyOf(standardDeductionCeilings);
    }
}
