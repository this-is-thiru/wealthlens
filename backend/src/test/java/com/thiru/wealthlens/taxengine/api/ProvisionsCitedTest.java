package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import org.junit.jupiter.api.Test;

/**
 * {@link TaxCalculator#provisionsCited()} over the shipped packs (W-236). Each expected citation is read by hand from the
 * rule's file under {@code rulepacks/timeline/law/rules/}.
 */
class ProvisionsCitedTest {

    private static final TaxCalculator CALCULATOR = new TaxCalculator(
            ShippedRulePacks.compile("rulepacks", ClasspathRulePackProvider.shippedYears("rulepacks")));

    @Test
    void provisionsCited_whenTheShippedPacksAreRead_namesEachRulesSectionsInBothActs() {
        // When
        var cited = CALCULATOR.provisionsCited();

        // Then — "s.19(1) Table Sl. 2(a)" (2025) and "s.16(ia) and its proviso" (1961); "s.10(10)(i)" (1961);
        // "s.2(42A), first proviso" (1961); DED-SCHEDULE-XV's "ss.80C, 80CCC, 80CCD(1), 80CCE; excluded by s.115BAC(2)(i)"
        assertThat(cited).contains(
                new ProvisionCitation("2025", "19", "SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME"),
                new ProvisionCitation("1961", "16", "SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME"),
                new ProvisionCitation("1961", "10", "OS-DEATH-GRATUITY"),
                new ProvisionCitation("1961", "2", "CG-TERM-LISTED-SECURITY"),
                new ProvisionCitation("1961", "80CCD", "DED-SCHEDULE-XV"),
                new ProvisionCitation("1961", "115BAC", "DED-SCHEDULE-XV"));
        assertThat(cited).doesNotHaveDuplicates();
    }
}
