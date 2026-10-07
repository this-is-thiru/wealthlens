package com.thiru.wealthlens.taxengine.rules.pack;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.cap.AggregateOutcome;
import com.thiru.wealthlens.taxengine.rules.cap.AggregateWithCeiling;
import com.thiru.wealthlens.taxengine.rules.cap.CapContext;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.BaseId;
import com.thiru.wealthlens.taxengine.rules.id.ComponentCode;
import com.thiru.wealthlens.taxengine.rules.id.RegimeCode;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.json.JsonPackAssembler;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.temporal.TemporalContext;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.test.LockedInstruments;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The s.123 family and the pension-scheme rows of Chapter VIII, TY 2026-27 (S2 ss.122–124, Schedule XV; D-130),
 * applied directly before {@code W-076} reads them (D-129 C4). Figures by hand from {@code DR 01} R15's worked example
 * (its life insurance premium left out — clause (a) is held) and {@code DR 08} T5-3 and T4-1/T4-2's facts.
 */
class InvestmentAndPensionDeductionTest {

    private static final TaxYearId TY_2026 = TaxYearId.of(2026);
    private static final BaseId SALARY = new BaseId("SALARY");
    private static final BaseId GROSS_TOTAL_INCOME = new BaseId("GROSS_TOTAL_INCOME");
    private static final RegimeCode OLD = new RegimeCode("FIRST_SCHEDULE_RATES");
    private static final RegimeCode NEW = new RegimeCode("NEW_TAX_REGIME");
    private static final RulePack PACK = load(TY_2026);
    private static final TemporalContext DURING_THE_YEAR = TemporalContext.forTaxYear(TY_2026, LocalDate.of(2026, 9, 21));

    private static RulePack load(TaxYearId year) {
        return new ClasspathRulePackProvider("rulepacks", Set.of(year), new JsonPackAssembler(LockedInstruments.of(year)))
                .load(year);
    }

    private static AggregateOutcome aggregate(RuleId rule, Map<ComponentCode, Money> entered, Money salary) {
        AggregateWithCeiling cap = (AggregateWithCeiling) PACK.rule(rule).versionAt(DURING_THE_YEAR).cap();
        Map<ComponentCode, Money> contributions = new HashMap<>();
        cap.contributors().forEach(code -> contributions.put(code, Money.ZERO));
        contributions.putAll(entered);
        return cap.applyDetailed(new CapContext(Map.of(SALARY, salary), contributions, Map.of()));
    }

    private static Money onSalary(RuleId rule, String salary) {
        return TemporallyApportionedCap.apply(PACK.rule(rule), DURING_THE_YEAR, CapContext.of(SALARY, Money.of(salary)))
                .value();
    }

    private static boolean appliesIn(RuleId rule, RegimeCode regime) {
        return PACK.rule(rule).versionAt(DURING_THE_YEAR).regimes().appliesIn(regime);
    }

    @Test
    void scheduleXv_whenR15sExampleIsEntered_allowsTheCeilingAndLeavesTheNpsBeyondItsSubCap() {
        // Given — R15: salary 10,00,000; RPF 1,20,000; tuition 60,000; NPS 1,50,000 (LIC 30,000 held, D-130 C1a)
        Map<ComponentCode, Money> entered = Map.of(ComponentCodes.EMPLOYEE_PF, Money.of("120000"),
                ComponentCodes.TUITION_FEES, Money.of("60000"), ComponentCodes.EMPLOYEE_NPS, Money.of("150000"));

        // When
        AggregateOutcome outcome = aggregate(RuleIds.DED_SCHEDULE_XV, entered, Money.of("1000000"));

        // Then — NPS sub-capped at 10% × 10,00,000 = 1,00,000 first; 1,20,000 + 60,000 + 1,00,000 = 2,80,000 → 1,50,000
        assertThat(outcome.aggregate()).isEqualTo(Money.of("280000"));
        assertThat(outcome.allowed()).isEqualTo(Money.of("150000"));
        assertThat(outcome.subCapResidue().get(ComponentCodes.EMPLOYEE_NPS)).isEqualTo(Money.of("50000"));
    }

    @Test
    void npsOwn_whenR15sResidueIsClaimed_allowsItWithinFiftyThousand() {
        // When — the 50,000 s.123 did not allow (s.124(5), (10))
        AggregateOutcome outcome = aggregate(RuleIds.DED_NPS_OWN,
                Map.of(ComponentCodes.EMPLOYEE_NPS, Money.of("50000")), Money.ZERO);

        // Then
        assertThat(outcome.allowed()).isEqualTo(Money.of("50000"));
    }

    @Test
    void scheduleXvThenNpsOwn_whenT5ThreesNpsIsEntered_allowsNinetyThousandInAll() {
        // Given — T5-3: salary 4,00,000, NPS 1,50,000, nothing else
        AggregateOutcome schedule = aggregate(RuleIds.DED_SCHEDULE_XV,
                Map.of(ComponentCodes.EMPLOYEE_NPS, Money.of("150000")), Money.of("400000"));

        // When
        AggregateOutcome own = aggregate(RuleIds.DED_NPS_OWN,
                Map.of(ComponentCodes.EMPLOYEE_NPS, schedule.subCapResidue().get(ComponentCodes.EMPLOYEE_NPS)),
                Money.ZERO);

        // Then — 10% × 4,00,000 = 40,000 under s.123; residue 1,10,000 → 50,000 under s.124(3); 90,000 in all
        assertThat(schedule.allowed()).isEqualTo(Money.of("40000"));
        assertThat(schedule.subCapResidue().get(ComponentCodes.EMPLOYEE_NPS)).isEqualTo(Money.of("110000"));
        assertThat(own.allowed()).isEqualTo(Money.of("50000"));
        assertThat(schedule.allowed().plus(own.allowed())).isEqualTo(Money.of("90000"));
    }

    @Test
    void npsOwn_whenAMinorsDepositIsAdded_sharesTheFiftyThousandCeiling() {
        // When — own 30,000 + minor 40,000 = 70,000 (s.124(4): "(3) and this sub-section shall not exceed ₹50000")
        AggregateOutcome outcome = aggregate(RuleIds.DED_NPS_OWN, Map.of(ComponentCodes.EMPLOYEE_NPS,
                Money.of("30000"), ComponentCodes.NPS_MINOR_DEPOSIT, Money.of("40000")), Money.ZERO);

        // Then
        assertThat(outcome.allowed()).isEqualTo(Money.of("50000"));
    }

    @Test
    void npsEmployer_whenT4sContributionIsOnTwentyFourLakh_allowsByEmployerAndRegime() {
        // Then — T4-1/T4-2: 14% × 24,00,000 = 3,36,000; 10% × 24,00,000 = 2,40,000
        assertThat(onSalary(RuleIds.DED_NPS_EMPLOYER_GOVT, "2400000")).isEqualTo(Money.of("336000"));
        assertThat(onSalary(RuleIds.DED_NPS_EMPLOYER_OTHER, "2400000")).isEqualTo(Money.of("240000"));
        assertThat(onSalary(RuleIds.DED_NPS_EMPLOYER_NEW_TAX_REGIME, "2400000")).isEqualTo(Money.of("336000"));
    }

    @Test
    void withinGrossTotal_whenGrossTotalIncomeIsThreeLakh_capsTheDeductionsAtIt() {
        // When
        Money cap = TemporallyApportionedCap.apply(PACK.rule(RuleIds.DED_WITHIN_GROSS_TOTAL), DURING_THE_YEAR,
                CapContext.of(GROSS_TOTAL_INCOME, Money.of("300000"))).value();

        // Then — s.122(2)
        assertThat(cap).isEqualTo(Money.of("300000"));
    }

    @Test
    void regimes_whenEachRowIsAskedAboutBothRegimes_keepsOnlyWhatSection202Keeps() {
        // Then — s.202(2)(a)(xii): the new regime keeps s.124(1)–(2) of these, and s.122(2) binds everywhere
        assertThat(appliesIn(RuleIds.DED_SCHEDULE_XV, OLD)).isTrue();
        assertThat(appliesIn(RuleIds.DED_SCHEDULE_XV, NEW)).isFalse();
        assertThat(appliesIn(RuleIds.DED_NPS_OWN, OLD)).isTrue();
        assertThat(appliesIn(RuleIds.DED_NPS_OWN, NEW)).isFalse();
        assertThat(appliesIn(RuleIds.DED_NPS_EMPLOYER_GOVT, OLD)).isTrue();
        assertThat(appliesIn(RuleIds.DED_NPS_EMPLOYER_GOVT, NEW)).isTrue();
        assertThat(appliesIn(RuleIds.DED_NPS_EMPLOYER_OTHER, OLD)).isTrue();
        assertThat(appliesIn(RuleIds.DED_NPS_EMPLOYER_OTHER, NEW)).isFalse();
        assertThat(appliesIn(RuleIds.DED_NPS_EMPLOYER_NEW_TAX_REGIME, OLD)).isFalse();
        assertThat(appliesIn(RuleIds.DED_NPS_EMPLOYER_NEW_TAX_REGIME, NEW)).isTrue();
        assertThat(appliesIn(RuleIds.DED_WITHIN_GROSS_TOTAL, OLD)).isTrue();
        assertThat(appliesIn(RuleIds.DED_WITHIN_GROSS_TOTAL, NEW)).isTrue();
    }

    @Test
    void taxYear2025Pack_whenItsEmployerRowsMeetTenLakh_allowSection80CcdTwosShares() {
        // Given — D-144: the TY 2025-26 versions under 1961 s.80CCD(2) (F(No.2)A 2019 s.24, FA 2022 s.20, F(No.2)A 2024 s.25)
        TaxYearId year = TaxYearId.of(2025);
        RulePack pack = load(year);
        TemporalContext during = TemporalContext.forTaxYear(year, LocalDate.of(2025, 9, 21));
        CapContext salary = CapContext.of(SALARY, Money.of("1000000"));

        // When / Then — 14%, 10%, 14% of 10,00,000
        assertThat(TemporallyApportionedCap.apply(pack.rule(RuleIds.DED_NPS_EMPLOYER_GOVT), during, salary).value())
                .isEqualTo(Money.of("140000"));
        assertThat(TemporallyApportionedCap.apply(pack.rule(RuleIds.DED_NPS_EMPLOYER_OTHER), during, salary).value())
                .isEqualTo(Money.of("100000"));
        assertThat(TemporallyApportionedCap.apply(pack.rule(RuleIds.DED_NPS_EMPLOYER_NEW_TAX_REGIME), during, salary)
                .value()).isEqualTo(Money.of("140000"));
    }
}
