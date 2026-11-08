package com.thiru.wealthlens.taxengine.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.AllocationSpace;
import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.AllowanceUse;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import com.thiru.wealthlens.taxengine.test.Fixtures;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The engine's performance budgets ({@code 01-PRD.md} §5 N1 – N3; {@code DR 14} O8). N1 and N2 are timed on T13-1's inputs
 * — salary and listed gains through every head (W-143); the joint pricing of the regime and the set-off order (W-084a) is
 * held to N3's bound too. N3 itself (W-238a) is timed on an optimisation T13-1 cannot give, since its pay is basic alone
 * and nothing can move: two exemptions that flip the regime only together, so the greedy fill, both regimes' saturation
 * searches and the exhaustive check all run. An optimisation takes hundreds of whole runs, so N3 is timed over fewer
 * repetitions. Timings depend on the machine, so this runs only under {@code -Pbenchmark}, without the coverage agent.
 */
@Log4j2
@Tag("benchmark")
class EngineBenchmarkTest {

    private static final int WARM_UP = 500;
    private static final int TIMED = 2000;
    private static final int OPTIMISER_WARM_UP = 20;
    private static final int OPTIMISER_TIMED = 200;
    private static final TaxYearId TY_2026_27 = TaxYearId.of(2026);
    private static final TaxCalculator CALCULATOR = new TaxCalculator(ShippedRulePacks.compile("rulepacks",
            Set.of(TY_2026_27)));
    private static final EngineInputs T13 = T13RebateAndSpecialRateTest.inputs(T13RebateAndSpecialRateTest.basicPay("175000"),
            T13RebateAndSpecialRateTest.gains("1600000", "500000"));

    /** p50, p99 and the slowest of {@code timed} runs after {@code warmUp}, in milliseconds. */
    private record Timing(double p50, double p99, double max) {

        static Timing of(Runnable operation, int warmUp, int timed) {
            for (int i = 0; i < warmUp; i++) {
                operation.run();
            }
            long[] nanos = new long[timed];
            for (int i = 0; i < timed; i++) {
                long start = System.nanoTime();
                operation.run();
                nanos[i] = System.nanoTime() - start;
            }
            Arrays.sort(nanos);
            return new Timing(nanos[timed / 2] / 1e6, nanos[(int) Math.ceil(timed * 0.99) - 1] / 1e6, nanos[timed - 1] / 1e6);
        }
    }

    private static Timing timed(String what, Runnable operation) {
        return timed(what, operation, WARM_UP, TIMED);
    }

    private static Timing timed(String what, Runnable operation, int warmUp, int runs) {
        Timing timing = Timing.of(operation, warmUp, runs);
        log.info("W-143 {}: p50 {} ms, p99 {} ms, max {} ms over {} runs", what, "%.3f".formatted(timing.p50()),
                "%.3f".formatted(timing.p99()), "%.3f".formatted(timing.max()), runs);
        return timing;
    }

    @Test
    void compute_whenT13IsComputed_staysUnderFiftyMillisecondsAtP99() {
        // When
        Timing timing = timed("N1 single compute", () -> CALCULATOR.compute(T13));

        // Then — PRD §5 N1
        assertThat(timing.p99()).as("N1 p99 in ms").isLessThan(50);
    }

    @Test
    void compareRegimes_whenBothRegimesArePriced_staysUnderOneHundredMillisecondsAtP99() {
        // When
        Timing timing = timed("N2 regime comparison, N = 2", () -> CALCULATOR.compareRegimes(T13));

        // Then — PRD §5 N2
        assertThat(timing.p99()).as("N2 p99 in ms").isLessThan(100);
    }

    @Test
    void resolveTogether_whenTheRegimeAndSetOffOrderArePricedJointly_staysUnderThreeSecondsAtP99() {
        // Given
        GoldenCaseRunner runner = new GoldenCaseRunner();
        Money interest = Money.of("1500000");

        // When
        Timing timing = timed("joint pricing of regime and set-off order (W-084a; not N3)",
                () -> runner.resolveRegimeAndSetOffOrder(TY_2026_27, interest));

        // Then — held to N3's synchronous budget, the bound W-143a names for it
        assertThat(timing.p99()).as("joint pricing p99 in ms").isLessThan(3000);
    }

    @Test
    void optimise_whenTwoExemptionsFlipTheRegimeOnlyTogether_staysUnderThreeSecondsAtP99() {
        // Given
        AllocationSpace space = new AllocationSpace(Fixtures.rentingInMumbai("1600000", "820000", "910000",
                List.of(new AllowanceUse(ComponentCodes.UNIFORM_ALLOWANCE, Optional.of(Money.of("60000")), Optional.empty(),
                        Optional.empty()))), Set.of(ComponentCodes.HRA, ComponentCodes.UNIFORM_ALLOWANCE), Set.of(),
                ComponentCodes.SPECIAL_ALLOWANCE, Money.ZERO, 6);
        log.info("W-238a N3 optimisation: {} whole runs", CALCULATOR.optimise(space).pipelineRuns());

        // When
        Timing timing = timed("N3 optimiser (W-238a)", () -> CALCULATOR.optimise(space), OPTIMISER_WARM_UP,
                OPTIMISER_TIMED);

        // Then — PRD §5 N3
        assertThat(timing.p99()).as("N3 p99 in ms").isLessThan(3000);
    }
}
