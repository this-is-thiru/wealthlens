package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code DR 08} T6 — house property and other sources — run through the shipped pipeline to Chapter VIII, in both
 * years (D-211):
 * <ul>
 *   <li>T6-1's let house: ₹5,400;</li>
 *   <li>T6-3's own residence in the new regime in TY 2025-26: nil, both kinds of interest disallowed;</li>
 *   <li>T6-6's ₹3,04,000 in TY 2026-27 and ₹2,64,000 in TY 2025-26, with ₹10,000 deducted;</li>
 *   <li>T6-7's ₹50,000 for a person of sixty-seven, with no savings-interest line.</li>
 * </ul>
 *
 * <p>T6-2, T6-4, T6-5 and T6-3's TY 2026-27 half compute a loss under the head, which is refused until set-off; they
 * run with W-081 (T6-3's: −₹40,000, the new regime allowing the pre-construction instalment from TY 2026-27, D-156).
 * The figures are copied, never computed.
 */
class T6HousePropertyAndOtherSourcesTest {

    private static final String DIRECTORY = "taxengine/golden/T6";
    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    static Stream<Arguments> everyCaseInEveryYear() {
        return HouseAndOtherSourcesGoldenCase.loadAll(DIRECTORY).stream()
                .flatMap(golden -> golden.taxYears().stream().map(year -> Arguments.of(golden, year)));
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("everyCaseInEveryYear")
    void run_whenTheCaseIsComputed_givesExactlyWhatDr08States(HouseAndOtherSourcesGoldenCase golden,
            TaxYearId year) {
        // When
        GoldenCaseRunner.SalaryRun run = RUNNER.run(golden, year);

        // Then
        assertFigure(run, golden, year, NodeIds.HOUSEPROPERTY_INCOME, golden.expectedHousePropertyIncome());
        assertFigure(run, golden, year, NodeIds.OTHERSOURCES_INCOME, golden.expectedOtherSourcesIncome());
        assertFigure(run, golden, year, NodeIds.CHAPTERVIII, golden.expectedChapterEightDeduction());
        for (RuleId rule : golden.expectedNilRules()) {
            assertThat(lines(run, rule)).as("%s in %s — %s disallowed", golden.id(), year.label(), rule)
                    .isNotEmpty()
                    .allSatisfy(line -> assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO)));
        }
        for (RuleId rule : golden.expectedAbsentRules()) {
            assertThat(lines(run, rule)).as("%s in %s — %s absent", golden.id(), year.label(), rule).isEmpty();
        }
        assertThat(run.provisional()).as("%s in %s — provisional", golden.id(), year.label()).isFalse();
    }

    private static void assertFigure(GoldenCaseRunner.SalaryRun run, HouseAndOtherSourcesGoldenCase golden,
            TaxYearId year, NodeId node, Map<TaxYearId, Money> expected) {
        if (expected.containsKey(year)) {
            assertThat(run.result().valueOf(node)).as("%s in %s — %s", golden.id(), year.label(), node)
                    .isEqualTo(MoneyValue.of(expected.get(year)));
        }
    }

    private static List<TraceLine> lines(GoldenCaseRunner.SalaryRun run, RuleId rule) {
        return run.result().trace().stream().filter(line -> line.rule().equals(rule)).toList();
    }
}
