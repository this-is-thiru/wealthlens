package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.money.Rate;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The year's law a salary certificate is checked against, read from the shipped pack (W-128c, D-750). */
class SalaryCertificateLawTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(ShippedRulePacks.compile("rulepacks",
            Set.of(TaxYearId.of(2025), TaxYearId.of(2026))));

    @Test
    void salaryCertificateLaw_whenTaxYear2026_27_givesEachRegimesStandardDeductionAndTheCess() {
        // When
        SalaryCertificateLaw law = CALCULATOR.salaryCertificateLaw(TaxYearId.of(2026));

        // Then — 01-rulebook.md:540 – :541: ₹75,000 under the new regime (SAL-STDDED-001), ₹50,000 in any other case
        // (SAL-STDDED-002); the cess 4% (the form prints "Health and education cess @ 4%")
        assertThat(law.defaultRegime()).isEqualTo(new RegimeCode("NEW_TAX_REGIME"));
        assertThat(law.standardDeductionCeilings()).isEqualTo(Map.of(new RegimeCode("NEW_TAX_REGIME"), Money.of("75000"),
                new RegimeCode("FIRST_SCHEDULE_RATES"), Money.of("50000")));
        assertThat(law.cessRate()).isEqualTo(Rate.percent("4"));
    }
}
