package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
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
 * {@code DR 08} T5 — the s.123 aggregate with employee PF and the pension sub-cap, run on to Chapter VIII, and the
 * leave travel concession with its variants, run to income under "Salaries" (D-153). The figures are copied, never
 * computed; the facts {@code DR 08} leaves out are named in each case and in the erratum below T5. PROVISIONAL runs
 * are reported separately.
 */
@Log4j2
class T5LeaveTravelAndInvestmentTest {

    private static final String DIRECTORY = "taxengine/golden/T5";
    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    static Stream<Arguments> everyCaseInEveryYear() {
        return SalaryGoldenCase.loadAll(DIRECTORY).stream()
                .flatMap(golden -> golden.taxYears().stream().map(year -> Arguments.of(golden, year)));
    }

    private static SalaryGoldenCase golden(String id) {
        return SalaryGoldenCase.loadAll(DIRECTORY).stream()
                .filter(golden -> golden.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(id + " is not among the T5 cases"));
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("everyCaseInEveryYear")
    void run_whenTheCaseIsComputed_givesExactlyWhatDr08States(SalaryGoldenCase golden, TaxYearId year) {
        // When
        GoldenCaseRunner.SalaryRun run = RUNNER.run(golden, year);

        // Then
        String described = golden.id() + " in " + year.label() + " — " + golden.source();
        golden.expectedChapterEightDeduction().ifPresent(deduction -> assertThat(
                        run.result().valueOf(NodeIds.CHAPTERVIII)).as(described).isEqualTo(MoneyValue.of(deduction)));
        golden.expectedScheduleXvAggregate().ifPresent(aggregate -> assertThat(run.result().trace())
                .filteredOn(line -> line.rule().equals(RuleIds.DED_SCHEDULE_XV))
                .singleElement()
                .satisfies(line -> assertThat(line.in()).as(described).isEqualTo(MoneyValue.of(aggregate))));
        golden.expectedExemption().ifPresent(exemption -> assertThat(
                        run.result().valueOf(NodeIds.SALARY_EXEMPTIONS)).as(described).isEqualTo(MoneyValue.of(exemption)));
        golden.expectedLimitedBy().ifPresent(limit -> assertThat(run.result().trace())
                .filteredOn(line -> line.rule().equals(RuleIds.SAL_LEAVE_TRAVEL_CONCESSION))
                .singleElement()
                .satisfies(line -> assertThat(line.limitedBy().orElseThrow()).as(described).contains(limit)));
    }

    @Test
    void run_whenT5TwoIsComputed_capsTheAggregateThatCountsEmployeePf() {
        // Then — DR 08 T5-2: "the deduction is the same" — 3,10,000 capped at 1,50,000 on the s.123 line
        SalaryGoldenCase golden = golden("T5-2");
        for (TaxYearId year : golden.taxYears()) {
            List<TraceLine> trace = RUNNER.run(golden, year).result().trace();
            assertThat(trace).filteredOn(line -> line.rule().equals(RuleIds.DED_SCHEDULE_XV))
                    .singleElement()
                    .satisfies(line -> assertThat(line.out()).as(year.label())
                            .isEqualTo(MoneyValue.of(Money.of("150000"))));
        }
    }

    @Test
    void run_whenEveryCaseIsComputed_reportsTheTaxYear2025LeaveTravelRunsAsProvisional() {
        // When — DR 08 marks T5-4's TY 2025-26 half PROVISIONAL; every TY 2025-26 run cites the salary bases,
        // of which SAL-SALARY-FOR-GRATUITY (W-078j4) is still UNVERIFIED
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

        // Then — reported separately: TY 2025-26 runs only, T5-4's among them; no TY 2026-27 run
        assertThat(byProvisional.get(true)).allSatisfy(run -> assertThat(run).endsWith("2025-26"))
                .contains("T5-4b 2025-26", "T5-4e1 2025-26");
        assertThat(byProvisional.get(false)).allSatisfy(run -> assertThat(run).endsWith("2026-27"))
                .contains("T5-1 2026-27", "T5-4a 2026-27");
    }
}
