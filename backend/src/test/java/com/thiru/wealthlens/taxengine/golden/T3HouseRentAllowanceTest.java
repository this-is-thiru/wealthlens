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
 * The third milestone: every {@code DR 08} T3 case — the HRA exemption — run through the shipped pipeline to income
 * under "Salaries", exempts the figure {@code DR 08} states (D-104). The figures are copied, never computed, but for
 * T3-4a, whose figure is its erratum's (OQ-8). PROVISIONAL runs are reported separately, as the {@code W-070} row asks.
 */
@Log4j2
class T3HouseRentAllowanceTest {

    private static final String DIRECTORY = "taxengine/golden/T3";
    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    static Stream<Arguments> everyCaseInEveryYear() {
        return SalaryGoldenCase.loadAll(DIRECTORY).stream()
                .flatMap(golden -> golden.taxYears().stream().map(year -> Arguments.of(golden, year)));
    }

    private static SalaryGoldenCase golden(String id) {
        return SalaryGoldenCase.loadAll(DIRECTORY).stream()
                .filter(golden -> golden.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(id + " is not among the T3 cases"));
    }

    private static Money exempted(String id) {
        SalaryGoldenCase golden = golden(id);
        return RUNNER.run(golden, golden.taxYears().getFirst()).result().valueOf(NodeIds.SALARY_EXEMPTIONS).asMoney();
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("everyCaseInEveryYear")
    void run_whenTheCaseIsComputed_exemptsExactlyWhatDr08States(SalaryGoldenCase golden, TaxYearId year) {
        // When
        GoldenCaseRunner.SalaryRun run = RUNNER.run(golden, year);

        // Then
        assertThat(run.result().valueOf(NodeIds.SALARY_EXEMPTIONS))
                .as("%s in %s — %s", golden.id(), year.label(), golden.source())
                .isEqualTo(MoneyValue.of(golden.expectedExemption().orElseThrow()));
        golden.expectedLimitedBy().ifPresent(limb -> assertThat(run.result().trace())
                .filteredOn(line -> line.rule().equals(RuleIds.SAL_HOUSE_RENT_ALLOWANCE))
                .singleElement()
                .satisfies(line -> assertThat(line.limitedBy()).as(golden.id()).contains(limb)));
    }

    @Test
    void run_whenEveryCaseIsComputed_onlyTheTaxYear2025RunsAreProvisional() {
        // When — DR 08 marks T3-2 and T3-3's TY 2025-26 half PROVISIONAL; every TY 2025-26 run cites the salary bases,
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

        // Then — reported separately: every TY 2025-26 run, and no TY 2026-27 run
        assertThat(byProvisional.get(true)).allSatisfy(run -> assertThat(run).endsWith("2025-26"))
                .contains("T3-2 2025-26", "T3-3b 2025-26");
        assertThat(byProvisional.get(false)).allSatisfy(run -> assertThat(run).endsWith("2026-27"))
                .contains("T3-1 2026-27", "T3-3a 2026-27");
    }

    @Test
    void run_whenT3TwoIsComputed_exemptsWhatT3OneDoes() {
        // Then — DR 08 T3-2: "this case deliberately cannot distinguish the two metro lists"
        assertThat(exempted("T3-2")).isEqualTo(exempted("T3-1"));
    }

    @Test
    void run_whenT3ThreeIsComputedInBothYears_differsByEightyThousand() {
        // Then — DR 08 T3-3: "difference = 80,000"
        assertThat(exempted("T3-3a").minus(exempted("T3-3b"))).isEqualTo(Money.of("80000"));
    }

    @Test
    void run_whenT3FiveIsComputed_isNotTheFigureOmittingDearnessAllowanceGives() {
        // Then — DR 08 T3-5: "the correct answer is ₹2,28,000, not ₹2,40,000"
        assertThat(List.of(exempted("T3-5a"), exempted("T3-5b")))
                .allSatisfy(exemption -> assertThat(exemption).isNotEqualTo(Money.of("240000")));
    }

    @Test
    void run_whenT3FourBIsComputed_carriesALineForTheNil() {
        // Then — DR 08 T3-4b: "a zero with a stated reason, not a missing step"
        for (String id : List.of("T3-4b1", "T3-4b2")) {
            SalaryGoldenCase golden = golden(id);
            List<TraceLine> exemptions = RUNNER.run(golden, golden.taxYears().getFirst()).result().trace().stream()
                    .filter(line -> line.node().equals(NodeIds.SALARY_EXEMPTIONS))
                    .toList();
            assertThat(exemptions).as(id).singleElement()
                    .satisfies(line -> assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO)));
        }
    }
}
