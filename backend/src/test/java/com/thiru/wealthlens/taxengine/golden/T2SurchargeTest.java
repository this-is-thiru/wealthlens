package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.input.InputsFingerprint;
import com.thiru.wealthlens.taxengine.pipeline.MoneyValue;
import com.thiru.wealthlens.taxengine.rules.generated.NodeIds;
import com.thiru.wealthlens.taxengine.rules.generated.RuleIds;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.trace.StepKind;
import com.thiru.wealthlens.taxengine.trace.TraceLine;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The second milestone: every {@code DR 08} T2 case — surcharge entry and its marginal relief — run end to
 * end in both tax years, pays the figure {@code DR 08} states (D-087). The figures, and the three things
 * {@code DR 08} asserts besides them, are copied, never computed; if one fails, the code is wrong (rule R3).
 * T2-6 needs special-rate income and is {@code W-088}'s (D-086).
 */
@Log4j2
class T2SurchargeTest {

    private static final GoldenCaseRunner RUNNER = new GoldenCaseRunner();

    static Stream<Arguments> everyCaseInEveryYear() {
        return GoldenCase.loadAll("taxengine/golden/T2").stream()
                .flatMap(golden -> golden.taxYears().stream().map(year -> Arguments.of(golden, year)));
    }

    private static GoldenCase golden(String id) {
        return GoldenCase.load("taxengine/golden/T2/" + id + ".json");
    }

    private static Money payable(String id, int year) {
        return RUNNER.run(golden(id), TaxYearId.of(year)).result().valueOf(NodeIds.ROUND_NETPAYABLE).asMoney();
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

        // Then — RATE-ROUND-003, PRD F2.4: the surcharge and its relief round nothing
        assertThat(run.result().trace().stream().filter(line -> line.kind() == StepKind.ROUNDING).map(TraceLine::rule))
                .containsExactly(RuleIds.RATE_ROUND_TOTAL_INCOME, RuleIds.RATE_ROUND_PAYABLE);
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("everyCaseInEveryYear")
    void run_whenTheSameCaseIsComputedTwice_projectsCharacterForCharacterTheSame(GoldenCase golden, TaxYearId year) {
        // When — T23-4, property P2; the recomputation for U must leave no trace in the result
        String first = InputsFingerprint.canonicalFormOf(RUNNER.run(golden, year).computation());
        String second = InputsFingerprint.canonicalFormOf(RUNNER.run(golden, year).computation());

        // Then
        assertThat(second).isEqualTo(first);
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenT2ThreeIsComputedEitherSideOfTheCrossover_paysTheSame(int year) {
        // Then — DR 08 T2-3: "the relief and the no-relief branches must meet continuously at the crossover"
        assertThat(payable("T2-3a", year)).isEqualTo(payable("T2-3b", year));
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenT2FourIsComputedInTheNewRegime_chargesTwentyFivePercentForThereIsNoThirtySevenBand(int year) {
        // When
        GoldenCaseRunner.Run run = RUNNER.run(golden("T2-4b"), TaxYearId.of(year));

        // Then — DR 08 T2-4 new: "exceeds 2,00,00,000 → 25%. THERE IS NO 37% BAND. 0.25 × 1,45,80,000 = 36,45,000"
        assertThat(run.result().trace())
                .filteredOn(line -> line.node().equals(NodeIds.TAX_SURCHARGE) && line.kind() == StepKind.TAX)
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.rule()).isEqualTo(RuleIds.RATE_SUR_NEW_TAX_REGIME_EXCLUDING);
                    assertThat(line.out()).isEqualTo(MoneyValue.of(Money.of("3645000")));
                });
    }

    @ParameterizedTest(name = "TY {0}")
    @ValueSource(ints = {2025, 2026})
    void run_whenT2FiveIsComputed_paysExactlyTenRupeesMoreThanT2FourOld(int year) {
        // Then — DR 08 T2-5: "exactly ₹10 above T2-4's old-regime figure"
        assertThat(payable("T2-5", year)).isEqualTo(payable("T2-4a", year).plus(Money.TEN));
    }
}
