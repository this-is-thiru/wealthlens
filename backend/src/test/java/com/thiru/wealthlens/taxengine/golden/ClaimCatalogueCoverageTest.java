package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.rules.catalogue.ClaimCatalogue;
import com.thiru.wealthlens.taxengine.rules.catalogue.NotAClaim;
import com.thiru.wealthlens.taxengine.rules.catalogue.NotAClaimReason;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.timeline.TimelineReader;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * W-117d C1a's check of the hand: the claim catalogue places every rule by hand, and here every golden case of every
 * year is run through the shipped pipeline. A rule that writes a deduction or an exemption must be catalogued, not yet
 * catalogued, or placed as applying without a claim — never as "not a deduction or exemption".
 */
class ClaimCatalogueCoverageTest {

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    private static final Set<StepKind> CLAIMED_KINDS = Set.of(StepKind.DEDUCTION, StepKind.EXEMPTION);

    /** Every line the golden cases write, over every year each case runs in. */
    private static Stream<TraceLine> everyLine() {
        Stream<TraceLine> given = Stream.of("taxengine/golden/T1", "taxengine/golden/T2")
                .flatMap(directory -> GoldenCase.loadAll(directory).stream())
                .flatMap(golden -> golden.taxYears().stream().map(year -> RUNNER.run(golden, year)))
                .flatMap(run -> run.result().trace().stream());
        Stream<TraceLine> salary = Stream.of("taxengine/golden/T3", "taxengine/golden/T4", "taxengine/golden/T5")
                .flatMap(directory -> SalaryGoldenCase.loadAll(directory).stream())
                .flatMap(golden -> golden.taxYears().stream().map(year -> RUNNER.run(golden, year)))
                .flatMap(run -> run.result().trace().stream());
        Stream<TraceLine> house = HouseAndOtherSourcesGoldenCase.loadAll("taxengine/golden/T6").stream()
                .flatMap(golden -> golden.taxYears().stream().map(year -> RUNNER.run(golden, year)))
                .flatMap(run -> run.result().trace().stream());
        return Stream.of(given, salary, house).flatMap(lines -> lines);
    }

    /** The rules the goldens write a deduction or an exemption for. */
    static Set<String> claimedInTheGoldens() {
        return everyLine().filter(line -> CLAIMED_KINDS.contains(line.kind())).map(line -> line.rule().value())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void notAClaim_whenTheGoldensAreRun_holdsNoRuleThatWritesADeductionOrExemption() {
        // Given
        ClaimCatalogue catalogue = ClaimCatalogue.of(TimelineReader.read(Path.of("src/main/resources/rulepacks/timeline")));
        Set<String> neverDeducts = catalogue.notAClaim().stream()
                .filter(placed -> placed.because() == NotAClaimReason.NOT_A_DEDUCTION_OR_EXEMPTION)
                .map(NotAClaim::rule).map(RuleId::value).collect(Collectors.toSet());

        // When
        Set<String> claimed = claimedInTheGoldens();

        // Then — the goldens reach claims (T3's house rent allowance, T5's investments, every salary case's standard
        // deduction), and none is placed as never deducting
        assertThat(claimed).contains("SAL-HOUSE-RENT-ALLOWANCE", "DED-SCHEDULE-XV", "SAL-STANDARD-DEDUCTION-ANY-OTHER-CASE");
        assertThat(claimed).doesNotContainAnyElementsOf(neverDeducts);
    }
}
