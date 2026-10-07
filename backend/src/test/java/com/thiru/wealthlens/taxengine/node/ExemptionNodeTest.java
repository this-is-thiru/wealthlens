package com.thiru.wealthlens.taxengine.node;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.input.EmployerKind;
import com.thiru.wealthlens.taxengine.input.Employment;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.input.EnteredFacts;
import com.thiru.wealthlens.taxengine.input.Tenancy;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet;
import com.thiru.wealthlens.taxengine.pipeline.ComponentSet.Line;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.pipeline.NodeResult;
import com.thiru.wealthlens.taxengine.pipeline.SalaryBases;
import com.thiru.wealthlens.taxengine.rules.condition.HeadCode;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.rules.temporal.DateRange;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The HRA exemption, per period of occupation (D-099; S4 r.279; Rules 1962 r.2A; {@code DR 13} R-11). Each figure
 * is worked by hand in its comment, rounding to paise where {@code Money} does; the first case is the rulebook's R9
 * worked example.
 */
class ExemptionNodeTest {

    private static final RegimeCode FIRST_SCHEDULE_RATES = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW_TAX_REGIME = new RegimeCode("NEW_TAX_REGIME");

    private static DateRange between(String from, String to) {
        return DateRange.between(LocalDate.parse(from), LocalDate.parse(to));
    }

    private static Employment oneEmployment() {
        return new Employment(List.of(), true, EmployerKind.OTHER, false, Optional.empty());
    }

    private static NodeResult run(int year, RegimeCode regime, List<Employment> employments, List<Tenancy> tenancies,
            String hraReceived, String salaryForHra) {
        TaxYearId taxYear = TaxYearId.of(year);
        RulePack pack = new ClasspathRulePackProvider(
                        "rulepacks", Set.of(taxYear), new JsonPackAssembler(LockedInstruments.of(taxYear)))
                .load(taxYear);
        EngineInputs plain = NodeHarness.plainTaxpayer();
        EngineInputs inputs = new EngineInputs(taxYear, regime,
                TemporalContext.forTaxYear(taxYear, LocalDate.of(year, 9, 21)),
                new EnteredFacts(employments, tenancies, List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty()), plain.assessee(), plain.family(), plain.ledgers(),
                plain.elections(), plain.capitalGains(), plain.filing());
        return NodeHarness.with(pack)
                .regime(regime)
                .inputs(inputs)
                .given(NodeIds.INPUTS_COMPONENTS, new ComponentSet(Map.of(HeadCode.SALARIES,
                        List.of(new Line(0, ComponentCodes.HRA, Money.of(hraReceived))))))
                .given(NodeIds.SALARY_BASE, new SalaryBases(Map.of(RuleIds.SAL_SALARY_FOR_HRA,
                        List.of(Money.of(salaryForHra)))))
                .run(new ExemptionNode());
    }

    @Test
    void evaluate_whenTheRulebookExample_isTheRentLimb() {
        // Given — R9: TY 2026-27, Bengaluru all year, rent 3,60,000, HRA 6,00,000, salary 12,00,000 + 1,20,000
        List<Tenancy> bengaluru =
                List.of(new Tenancy(between("2026-04-01", "2027-03-31"), "BENGALURU", false, Money.of("360000")));

        // When
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, List.of(oneEmployment()), bengaluru, "600000", "1320000");

        // Then — least of 6,00,000; 3,60,000 − 1,32,000 = 2,28,000; 50% × 13,20,000 = 6,60,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("228000")));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.SAL_HOUSE_RENT_ALLOWANCE);
            assertThat(line.kind()).isEqualTo(StepKind.EXEMPTION);
            assertThat(line.limitedBy()).contains("rent less one-tenth of salary");
        });
    }

    @Test
    void evaluate_whenTheTaxpayerMovesMidYear_worksEachPeriodWithItsOwnPercentageAndLimb() {
        // Given — Pune 1 Apr – 30 Sep 2026 (183 days), Nashik 1 Oct 2026 – 31 Mar 2027 (182 days); HRA 9,00,000,
        // salary 13,20,000 for the year
        List<Tenancy> moved = List.of(
                new Tenancy(between("2026-04-01", "2026-09-30"), "PUNE", false, Money.of("400000")),
                new Tenancy(between("2026-10-01", "2027-03-31"), "ANY_OTHER_PLACE", false, Money.of("100000")));

        // When
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, List.of(oneEmployment()), moved, "900000", "1320000");

        // Then — Pune: HRA 9,00,000 × 183/365 = 4,51,232.88; salary 13,20,000 × 183/365 = 6,61,808.22;
        //   rent 4,00,000 − 66,180.82 = 3,33,819.18; 50% × 6,61,808.22 = 3,30,904.11 → least 3,30,904.11
        // Nashik: HRA 4,48,767.12; salary 6,58,191.78; rent 1,00,000 − 65,819.18 = 34,180.82;
        //   40% × 6,58,191.78 = 2,63,276.71 → least 34,180.82
        // Together 3,65,084.93 — annualising first would have given one limb for the whole year
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("365084.93")));
        assertThat(result.trace()).extracting(line -> line.out().asMoney())
                .containsExactly(Money.of("330904.11"), Money.of("34180.82"));
        assertThat(result.trace()).extracting(TraceLine::limitedBy).containsExactly(
                java.util.Optional.of("percentage of salary"), java.util.Optional.of("rent less one-tenth of salary"));
    }

    @Test
    void evaluate_whenTheYearIs2025_uses1962sFourCities() {
        // Given — Mumbai all of TY 2025-26: 50% under r.2A; rent high enough that limb (c) binds
        List<Tenancy> mumbai =
                List.of(new Tenancy(between("2025-04-01", "2026-03-31"), "MUMBAI", false, Money.of("900000")));

        // When
        NodeResult result = run(2025, FIRST_SCHEDULE_RATES, List.of(oneEmployment()), mumbai, "800000", "1320000");

        // Then — least of 8,00,000; 9,00,000 − 1,32,000 = 7,68,000; 50% × 13,20,000 = 6,60,000
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.of("660000")));
    }

    @Test
    void evaluate_whenBengaluruIsEnteredFor2025_isRefusedBecauseItWasNotYetListed() {
        // Given — H-2: Bengaluru joined the 50% list with the Rules, 2026
        List<Tenancy> bengaluru =
                List.of(new Tenancy(between("2025-04-01", "2026-03-31"), "BENGALURU", false, Money.of("360000")));

        // When / Then
        assertThatThrownBy(() -> run(2025, FIRST_SCHEDULE_RATES, List.of(oneEmployment()), bengaluru, "600000",
                        "1320000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BENGALURU")
                .hasMessageContaining("ANY_OTHER_PLACE");
    }

    @Test
    void evaluate_whenTheHomeIsOwned_givesANilLineNamingWhy() {
        // Given
        List<Tenancy> owned =
                List.of(new Tenancy(between("2026-04-01", "2027-03-31"), "BENGALURU", true, Money.of("360000")));

        // When
        NodeResult result = run(2026, FIRST_SCHEDULE_RATES, List.of(oneEmployment()), owned, "600000", "1320000");

        // Then — Sch. III Table Sl. 11(c): no exemption for a home the taxpayer owns
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement().satisfies(line ->
                assertThat(line.limitedBy()).hasValueSatisfying(why -> assertThat(why).contains("owned")));
    }

    @Test
    void evaluate_whenInTheNewRegime_exemptsNothingAndSaysWhy() {
        // Given
        List<Tenancy> bengaluru =
                List.of(new Tenancy(between("2026-04-01", "2027-03-31"), "BENGALURU", false, Money.of("360000")));

        // When
        NodeResult result = run(2026, NEW_TAX_REGIME, List.of(oneEmployment()), bengaluru, "600000", "1320000");

        // Then — s.202(2)(a)(i); DR 08 T3-4b: "a zero with a stated reason, not a missing step" (D-104)
        assertThat(result.value()).isEqualTo(MoneyValue.of(Money.ZERO));
        assertThat(result.trace()).singleElement().satisfies(line -> {
            assertThat(line.rule()).isEqualTo(RuleIds.SAL_HOUSE_RENT_ALLOWANCE);
            assertThat(line.in()).isEqualTo(MoneyValue.of(Money.of("600000")));
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO));
            assertThat(line.limitedBy()).contains("not in the NEW_TAX_REGIME regime");
        });
    }

    @Test
    void evaluate_whenInTheNewRegimeWithNoHomeEntered_saysNothing() {
        // When — nothing claimed, so nothing to refuse
        NodeResult result = run(2026, NEW_TAX_REGIME, List.of(oneEmployment()), List.of(), "600000", "1320000");

        // Then
        assertThat(result.trace()).isEmpty();
    }

    @Test
    void evaluate_whenTwoEmploymentsAndARentedHome_isRefused() {
        // Given
        List<Tenancy> bengaluru =
                List.of(new Tenancy(between("2026-04-01", "2027-03-31"), "BENGALURU", false, Money.of("360000")));

        // When / Then — an annual HRA cannot be matched to the months each employer paid it
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES, List.of(oneEmployment(), oneEmployment()),
                        bengaluru, "600000", "1320000"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("employment");
    }

    @Test
    void evaluate_whenATenancyRunsOutsideTheYear_isRefused() {
        // Given — rent entered for a window the year does not contain
        List<Tenancy> straddling =
                List.of(new Tenancy(between("2026-01-01", "2026-06-30"), "BENGALURU", false, Money.of("180000")));

        // When / Then
        assertThatThrownBy(() -> run(2026, FIRST_SCHEDULE_RATES, List.of(oneEmployment()), straddling, "600000",
                        "1320000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-01-01");
    }
}
