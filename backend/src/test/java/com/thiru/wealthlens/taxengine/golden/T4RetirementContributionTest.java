package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.EmployerFund;
import com.thiru.wealthlens.taxengine.input.EmployerFundContribution;
import com.thiru.wealthlens.taxengine.input.SalaryComponent;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
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
 * {@code DR 08} T4 — PF and NPS — run through the shipped pipeline (D-128, D-209): T4-1 and T4-2's excess added to
 * salary and the employer's pension-scheme contribution deducted under Chapter VIII (₹3,36,000 in the new regime,
 * ₹2,40,000 in the old); T4-3's aggregate adding nothing and its twelve per cent charge ₹30,000 in TY 2025-26 (T4-3a)
 * and nothing in TY 2026-27 (T4-3b, the Finance Act, 2026 having removed it; D-151); T4-4's own contribution of
 * ₹6,00,000 measured ₹3,50,000 beyond the threshold, its taxable-account interest — nil, the case stating none — the
 * whole charge (DR 08's W-074b erratum). The figures are copied, never computed; PROVISIONAL runs are reported
 * separately.
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
        GoldenCaseRunner.ShippedPipelineRun run = RUNNER.run(golden, year);

        // Then — the charge, and gross salary above the pay by that and the employer's pension contribution, which
        // "salary" includes (S2 s.16(k); D-140 erratum to D-128 C4): T4-1, T4-2 74,000 + 3,36,000 = 4,10,000
        assertThat(run.result().valueOf(NodeIds.EMPLOYER_CONTRIB_CHARGES))
                .as("%s in %s — %s", golden.id(), year.label(), golden.source())
                .isEqualTo(MoneyValue.of(expected));
        assertThat(run.result().valueOf(NodeIds.SALARY_GROSS).asMoney().minus(pay))
                .as("%s in %s — gross less pay", golden.id(), year.label())
                .isEqualTo(expected.plus(pensionContribution));
        golden.expectedChapterEightDeduction().ifPresent(deduction -> assertThat(
                        run.result().valueOf(NodeIds.CHAPTERVIII))
                .as("%s in %s — Chapter VIII", golden.id(), year.label())
                .isEqualTo(MoneyValue.of(deduction)));
        golden.expectedOtherSourcesIncome().ifPresent(income -> assertThat(
                        run.result().valueOf(NodeIds.OTHERSOURCES_INCOME))
                .as("%s in %s — income from other sources", golden.id(), year.label())
                .isEqualTo(MoneyValue.of(income)));
        golden.expectedProvidentFundExcess().ifPresent(excess -> assertThat(run.result().trace().stream()
                        .filter(line -> line.rule().equals(RuleIds.OS_PF_TAXABLE_INTEREST))
                        .filter(line -> line.kind() == StepKind.MEASURE)
                        .map(TraceLine::out)
                        .toList())
                .as("%s in %s — the contribution beyond the threshold", golden.id(), year.label())
                .containsExactly(MoneyValue.of(excess)));
    }

    @Test
    void run_whenEveryCaseIsComputed_noRunIsProvisional() {
        // When — DR 08 marks some TY 2025-26 halves PROVISIONAL; the sourcing round (D-170 – D-181) has checked every
        // version these runs cite, so none is now
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

        // Then — no run in either year cites an UNVERIFIED version; a new one would be reported here
        assertThat(byProvisional.get(true)).isEmpty();
    }

    @Test
    void run_whenT4TwoIsComputed_addsWhatT4OneDoes() {
        // Then — DR 08 T4-2: "PF-AGGCAP-001 excess is unchanged"
        for (TaxYearId year : List.of(TaxYearId.of(2025), TaxYearId.of(2026))) {
            assertThat(charged("T4-2", year)).as(year.label()).isEqualTo(charged("T4-1", year));
        }
    }
}
