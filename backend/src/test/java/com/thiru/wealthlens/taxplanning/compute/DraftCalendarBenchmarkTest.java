package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A calendar is at most fifteen whole computations: the draft as it stands, one per instalment, the relief day, the
 * return on time and a day late (W-114, D-665; first counted as seven, one short — D-667), Schedule XV's room priced
 * in two (W-114a, D-666), the assessee's and the parents' health cover in one each (W-114c, D-668), and the regime
 * election in three, the two regimes priced and the facts its route reads (W-114b, D-667). Held to fifteen of
 * {@code 01-PRD.md} §5 N1's 50 ms. Timings depend on the machine, so this
 * runs only under {@code -Pbenchmark}, as {@code EngineBenchmarkTest} does.
 */
@Log4j2
@Tag("benchmark")
class DraftCalendarBenchmarkTest {

    private static final int WARM_UP = 100;
    private static final int TIMED = 500;
    private static final double FIFTEEN_COMPUTES_MILLIS = 15 * 50;

    @Test
    void entries_whenT13IsMeasured_staysUnderFifteenComputesAtP99() {
        // Given — T13-1, sold 1-9-2026, nothing paid: every instalment and the relief day take a run
        DraftCalendarService service = new DraftCalendarService(null, null, new TaxCalculator(
                ShippedRulePacks.compile("rulepacks", ClasspathRulePackProvider.shippedYears("rulepacks"))), null);
        EngineInputs inputs = DraftCalendarServiceTest.inputs(LocalDate.of(2026, 9, 1), Optional.empty());
        LocalDate due = inputs.filing().dueDate();
        for (int i = 0; i < WARM_UP; i++) {
            service.entries(inputs, due);
        }

        // When
        long[] nanos = new long[TIMED];
        for (int i = 0; i < TIMED; i++) {
            long start = System.nanoTime();
            service.entries(inputs, due);
            nanos[i] = System.nanoTime() - start;
        }
        Arrays.sort(nanos);
        double p99 = nanos[(int) Math.ceil(TIMED * 0.99) - 1] / 1e6;
        log.info("W-114 calendar: p50 {} ms, p99 {} ms over {} runs", "%.3f".formatted(nanos[TIMED / 2] / 1e6),
                "%.3f".formatted(p99), TIMED);

        // Then
        assertThat(p99).as("calendar p99 in ms").isLessThan(FIFTEEN_COMPUTES_MILLIS);
    }
}
