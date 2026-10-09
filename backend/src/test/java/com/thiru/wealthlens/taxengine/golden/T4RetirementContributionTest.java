package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The built half of {@code DR 08} T4 — the employer's retirement contributions — run through the shipped pipeline to
 * income under "Salaries" (D-128): T4-1 and T4-2's excess added to salary; T4-3's aggregate adding nothing and its
 * twelve per cent charge ₹30,000 in TY 2025-26 (T4-3a) and nothing in TY 2026-27 (T4-3b, the Finance Act, 2026
 * having removed it; D-151). The deductions and T4-4 are {@code W-074b}'s. The figures are copied, never computed; PROVISIONAL
 * runs are reported separately.
 */
@Log4j2
class T4RetirementContributionTest {

    private static final String DIRECTORY = "taxengine/golden/T4";
    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    static Stream<Arguments> everyCaseInEveryYear() {
        return SalaryGoldenCase.loadAll(DIRECTORY).stream()
                .flatMap(golden -> golden.taxYears().stream().map(year -> Arguments.of(golden, year)));
    }

    private static Money charged(String id, TaxYearId year) {
        SalaryGoldenCase golden = SalaryGoldenCase.loadAll(DIRECTORY).stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(id + " is not among the T4 cases"));
        return RUNNER.run(golden, year).result().valueOf(NodeIds.EMPLOYER_CONTRIB_CHARGES).asMoney();
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("everyCaseInEveryYear")
    void run_whenTheCaseIsComputed_addsExactlyWhatDr08StatesToSalary(SalaryGoldenCase golden, TaxYearId year) {
        // Given
        Money expected = golden.expectedEmployerContributionCharges().orElseThrow();
        Money pay = golden.salary().pay().stream().map(SalaryComponent::annualAmount).reduce(Money.ZERO, Money::plus);
        Money pensionContribution = golden.salary().employerContributions().stream()
                .filter(contribution -> contribution.fund() == EmployerFund.NOTIFIED_PENSION_SCHEME)
                .map(EmployerFundContribution::amount)
                .reduce(Money.ZERO, Money::plus);

        // When
        GoldenCaseRunner.SalaryRun run = RUNNER.run(golden, year);

        // Then — the charge, and gross salary above the pay by that and the employer's pension contribution, which
        // "salary" includes (S2 s.16(k); D-140 erratum to D-128 C4): T4-1, T4-2 74,000 + 3,36,000 = 4,10,000
        assertThat(run.result().valueOf(NodeIds.EMPLOYER_CONTRIB_CHARGES))
                .as("%s in %s — %s", golden.id(), year.label(), golden.source())
                .isEqualTo(MoneyValue.of(expected));
        assertThat(run.result().valueOf(NodeIds.SALARY_GROSS).asMoney().minus(pay))
                .as("%s in %s — gross less pay", golden.id(), year.label())
                .isEqualTo(expected.plus(pensionContribution));
    }

    @Test
    void run_whenEveryCaseIsComputed_onlyTheTaxYear2025RunsAreProvisional() {
        // When — TY 2025-26 runs cite UNVERIFIED versions (W-078j), though DR 08 marks no T4-1/T4-2 ⓟ
        Map<Boolean, List<String>> byProvisional = everyCaseInEveryYear()
                .map(arguments -> {
                    SalaryGoldenCase golden = (SalaryGoldenCase) arguments.get()[0];
                    TaxYearId year = (TaxYearId) arguments.get()[1];
                    boolean provisional = RUNNER.run(golden, year).provisional();
                    if (provisional) {
                        log.warn("{} in {} is PROVISIONAL: it cites an UNVERIFIED rule version", golden.id(),
                                year.label());
                    }
                    return Map.entry(provisional, golden.id() + " " + year.label());
                })
                .collect(Collectors.partitioningBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        // Then — reported separately: every TY 2025-26 run, and no TY 2026-27 run
        assertThat(byProvisional.get(true)).allSatisfy(run -> assertThat(run).endsWith("2025-26"))
                .contains("T4-1 2025-26", "T4-2 2025-26");
        assertThat(byProvisional.get(false)).allSatisfy(run -> assertThat(run).endsWith("2026-27"))
                .contains("T4-1 2026-27", "T4-2 2026-27");
    }

    @Test
    void run_whenT4TwoIsComputed_addsWhatT4OneDoes() {
        // Then — DR 08 T4-2: "PF-AGGCAP-001 excess is unchanged"
        for (TaxYearId year : List.of(TaxYearId.of(2025), TaxYearId.of(2026))) {
            assertThat(charged("T4-2", year)).as(year.label()).isEqualTo(charged("T4-1", year));
        }
    }
}
