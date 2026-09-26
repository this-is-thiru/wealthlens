package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.InputsFingerprint;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.NodeId;
import com.thiru.wealthlens.taxengine.rules.id.RuleId;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.pack.RulePack;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The first milestone: every {@code DR 08} T1 case, run end to end in both tax years, matches the figure
 * {@code DR 08} states (D-083). The expected values are copied into the case files, never computed; if
 * one fails, the code is wrong (rule R3).
 */
@Log4j2
class T1SlabAndRebateTest {

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    static Stream<Arguments> everyCaseInEveryYear() {
        return GoldenCase.loadAll("taxengine/golden/T1").stream()
                .flatMap(golden -> golden.taxYears().stream().map(year -> Arguments.of(golden, year)));
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("everyCaseInEveryYear")
    void run_whenTheCaseIsComputed_paysExactlyWhatDr08States(GoldenCase golden, TaxYearId year) {
        // When
        GoldenCaseRunner.Run run = RUNNER.run(golden, year);

        // Then — DR 08's figures are rounded, so they are compared with round.netpayable (D-081)
        if (run.provisional()) {
            log.warn("{} in {} is PROVISIONAL: it cites an UNVERIFIED rule version", golden.id(), year.label());
        }
        assertThat(run.result().valueOf(NodeIds.ROUND_NETPAYABLE))
                .as("%s in %s — %s", golden.id(), year.label(), golden.source())
                .isEqualTo(MoneyValue.of(golden.expectedPayable()));
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("everyCaseInEveryYear")
    void run_whenTheCaseIsComputed_roundsInExactlyTwoPlaces(GoldenCase golden, TaxYearId year) {
        // When
        GoldenCaseRunner.Run run = RUNNER.run(golden, year);

        // Then — RATE-ROUND-003, PRD F2.4
        assertThat(run.result().trace().stream().filter(line -> line.kind() == StepKind.ROUNDING).map(TraceLine::rule))
                .containsExactly(RuleIds.RATE_ROUND_TOTAL_INCOME, RuleIds.RATE_ROUND_PAYABLE);
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("everyCaseInEveryYear")
    void run_whenTheSameCaseIsComputedTwice_projectsCharacterForCharacterTheSame(GoldenCase golden, TaxYearId year) {
        // When — T23-4, property P2: identical inputs, the same id
        String first = InputsFingerprint.canonicalFormOf(RUNNER.run(golden, year).computation());
        String second = InputsFingerprint.canonicalFormOf(RUNNER.run(golden, year).computation());

        // Then
        assertThat(second).isEqualTo(first);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenT1O2IsComputed_explainsTheUnavailableRebateAndHasNoMarginalRelief(int year) {
        // Given — T23-8, property P5; DR 08 T1-O2: "REBATE-OLD-001: TI > 5,00,000 → unavailable". The
        // surcharge ceiling explains its nil too (D-086); there is no rebate marginal relief in this regime
        GoldenCase t1o2 = GoldenCase.load("taxengine/golden/T1/T1-O2.json");

        // When
        List<TraceLine> reliefs = RUNNER.run(t1o2, TaxYearId.of(year)).result().trace().stream()
                .filter(line -> line.kind() == StepKind.RELIEF)
                .toList();

        // Then
        assertThat(reliefs).extracting(TraceLine::rule)
                .containsExactly(RuleIds.REBATE_RESIDENT_INDIVIDUAL, RuleIds.RATE_SUR_FIRST_SCHEDULE_CEILING);
        assertThat(reliefs).allSatisfy(line -> {
            assertThat(line.out()).isEqualTo(MoneyValue.of(Money.ZERO));
            assertThat(line.limitedBy()).isPresent();
        });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {2025, 2026})
    void registry_whenCheckedAgainstTheShippedPack_evaluatesEveryRule(int year) {
        // Given — invariant I3 (D-043), with no exception left: W-056 evaluates the Phase 6 rows (D-086)
        RulePack pack = RUNNER.pack(TaxYearId.of(year));

        // When
        Set<RuleId> unevaluated = RUNNER.registry().rulesNotEvaluatedByAnyNode(pack.rules().keySet());

        // Then
        assertThat(unevaluated).isEmpty();
    }

    @Test
    void pipeline_whenEnteredAtTotalIncome_dropsNoShippedNode() {
        // When
        List<NodeId> dropped = RUNNER.droppedFromShippedPipeline();

        // Then — C1: a later pipeline change cannot silently shorten the run; since W-061 every shipped
        // node is built (D-091)
        assertThat(dropped).isEmpty();
    }
}
