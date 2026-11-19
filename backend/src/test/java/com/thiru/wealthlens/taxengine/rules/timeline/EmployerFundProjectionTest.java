package com.thiru.wealthlens.taxengine.rules.timeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.id.EmployerFundCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A fund row as each year sees it (W-238e): the compiler keeps only the rules the year's pack holds, as it does for a
 * component. {@code PERQ-RPF-CONTRIBUTION}'s one version runs 2025-04-01 to 2026-03-31; the aggregate limit and its
 * accretion have a version in both years.
 */
class EmployerFundProjectionTest {

    private static final TaxYearId TY_2025_26 = TaxYearId.of(2025);
    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final ShippedRulePacks PACKS = ShippedRulePacks.compile("rulepacks", Set.of(TY_2025_26, TY_2026_27));
    private static final EmployerFundCode RPF = new EmployerFundCode("RECOGNISED_PROVIDENT_FUND");

    @Test
    void compile_whenTheProvidentFundsOwnRuleIsInTheYear_keepsIt() {
        // Given / When
        Set<RuleId> feeds = PACKS.load(TY_2025_26).funds().get(RPF).feedsInto();

        // Then
        assertThat(feeds).containsExactlyInAnyOrder(new RuleId("PERQ-EMPLOYER-ACCRETION"),
                new RuleId("PERQ-EMPLOYER-CONTRIBUTION"), new RuleId("PERQ-RPF-CONTRIBUTION"));
    }

    @Test
    void compile_whenTheProvidentFundsOwnRuleHasLapsed_dropsIt() {
        // Given / When
        Set<RuleId> feeds = PACKS.load(TY_2026_27).funds().get(RPF).feedsInto();

        // Then
        assertThat(feeds).containsExactlyInAnyOrder(new RuleId("PERQ-EMPLOYER-ACCRETION"),
                new RuleId("PERQ-EMPLOYER-CONTRIBUTION"));
    }
}
