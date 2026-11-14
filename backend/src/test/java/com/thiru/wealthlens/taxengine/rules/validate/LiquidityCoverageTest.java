package com.thiru.wealthlens.taxengine.rules.validate;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.component.ComponentDefinition;
import com.thiru.wealthlens.taxengine.rules.component.EmployerFundDefinition;
import com.thiru.wealthlens.taxengine.rules.component.Liquidity;
import com.thiru.wealthlens.taxengine.rules.component.WithdrawalTerm;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.EmployerFundCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.pack.Explanations;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every fund and contribution row accounts for each day of the year (W-238e1). The shipped terms, read from the Gazette
 * copies in {@code sources/2026-11-08-fund-liquidity.md}: the NPS regulations change on 15-12-2025, the issue date of the
 * 2025 amendment (C1a); the EPF Scheme, 2026 starts on 29-06-2026 and the 1952 Scheme before it is unstated (C2a).
 */
class LiquidityCoverageTest {

    private static final TaxYearId TY_2025_26 = TaxYearId.of(2025);
    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final ShippedRulePacks SHIPPED =
            ShippedRulePacks.compile("rulepacks", Set.of(TY_2025_26, TY_2026_27));
    private static final RulePack PACK = SHIPPED.load(TY_2026_27);
    private static final EmployerFundCode NPS = new EmployerFundCode("NOTIFIED_PENSION_SCHEME");
    private static final EmployerFundCode RPF = new EmployerFundCode("RECOGNISED_PROVIDENT_FUND");
    private static final ComponentCode EMPLOYEE_PF = new ComponentCode("EMPLOYEE_PF");

    private static List<String> problems(RulePack pack) {
        List<String> problems = new ArrayList<>();
        LiquidityCoverage.check(pack, problems);
        return problems;
    }

    private static RulePack withFund(EmployerFundDefinition fund) {
        Map<EmployerFundCode, EmployerFundDefinition> funds = new LinkedHashMap<>(PACK.funds());
        funds.put(fund.code(), fund);
        return new RulePack(PACK.taxYear(), PACK.layers(), PACK.instruments(), PACK.rules(), PACK.ruleSets(),
                PACK.components(), PACK.regimes(), PACK.disallowanceSets(), PACK.conditionSets(), funds, Map.of(), Map.of(), Explanations.none());
    }

    private static RulePack withComponent(ComponentDefinition component) {
        Map<ComponentCode, ComponentDefinition> components = new LinkedHashMap<>(PACK.components());
        components.put(component.code(), component);
        return new RulePack(PACK.taxYear(), PACK.layers(), PACK.instruments(), PACK.rules(), PACK.ruleSets(),
                components, PACK.regimes(), PACK.disallowanceSets(), PACK.conditionSets(), PACK.funds(), Map.of(), Map.of(), Explanations.none());
    }

    private static EmployerFundDefinition fundWith(EmployerFundDefinition row, Liquidity liquidity) {
        return new EmployerFundDefinition(row.code(), row.displayName(), row.feedsInto(), row.validity(),
                row.provenance(), liquidity);
    }

    private static ComponentDefinition componentWith(ComponentDefinition row, Liquidity liquidity) {
        return new ComponentDefinition(row.code(), row.displayName(), row.head(), row.kind(), row.cashNature(),
                row.treatment(), row.feedsInto(), row.salaryBaseFor(), row.informationalOnly(), row.lifecycle(),
                row.provenance(), liquidity);
    }

    @Test
    void check_whenTheShippedPacksAreChecked_findsNothing() {
        // Given / When / Then
        assertThat(problems(SHIPPED.load(TY_2025_26))).isEmpty();
        assertThat(problems(PACK)).isEmpty();
    }

    @Test
    void check_whenTheYearIsCompiled_keepsBothNpsVersionsIn2025And26AndTheLaterOneIn2026And27() {
        // Given — five events each side of the 2025 amendment: two exits for each sector and the partial withdrawal
        List<WithdrawalTerm> year2025 = SHIPPED.load(TY_2025_26).funds().get(NPS).liquidity().terms();
        List<WithdrawalTerm> year2026 = PACK.funds().get(NPS).liquidity().terms();

        // When / Then
        assertThat(year2025).filteredOn(term -> term.validity().to().equals(Optional.of(LocalDate.of(2025, 12, 14))))
                .hasSize(5);
        assertThat(year2025).filteredOn(term -> term.validity().from().equals(LocalDate.of(2025, 12, 15))).hasSize(5);
        assertThat(year2026).hasSize(5).allSatisfy(term ->
                assertThat(term.validity().from()).isEqualTo(LocalDate.of(2025, 12, 15)));
    }

    @Test
    void check_whenAFundStatesNoLiquidity_namesTheYearsFirstDay() {
        // Given
        RulePack pack = withFund(fundWith(PACK.funds().get(NPS), Liquidity.NONE));

        // When / Then
        assertThat(problems(pack)).containsExactly("employer fund NOTIFIED_PENSION_SCHEME states no withdrawal term and"
                + " no unstated period for 2026-04-01; each day it lives in the year needs one");
    }

    @Test
    void check_whenTheProvidentFundLosesItsUnstatedPeriod_namesTheFirstDayBeforeThe2026Scheme() {
        // Given — the 2026 Scheme's terms start on 29-06-2026; without the 1952 Scheme's unstated period nothing covers
        // 1-4-2026 to 28-6-2026
        EmployerFundDefinition row = PACK.funds().get(RPF);
        RulePack pack = withFund(fundWith(row, new Liquidity(row.liquidity().terms(), List.of())));

        // When / Then
        assertThat(problems(pack)).singleElement().asString().contains("RECOGNISED_PROVIDENT_FUND")
                .contains("2026-04-01");
    }

    @Test
    void check_whenAnEmployeesContributionStatesNoLiquidity_isRefused() {
        // Given
        RulePack pack = withComponent(componentWith(PACK.components().get(EMPLOYEE_PF), Liquidity.NONE));

        // When / Then
        assertThat(problems(pack)).singleElement().asString().startsWith("component EMPLOYEE_PF states no withdrawal term");
    }

    @Test
    void check_whenPayStatesNoLiquidity_findsNothing() {
        // Given — basic pay is in hand; the shipped row states nothing, and nothing is required of it
        ComponentDefinition basic = PACK.components().get(new ComponentCode("BASIC"));

        // When / Then
        assertThat(basic.liquidity()).isEqualTo(Liquidity.NONE);
        assertThat(problems(withComponent(basic))).isEmpty();
    }
}
