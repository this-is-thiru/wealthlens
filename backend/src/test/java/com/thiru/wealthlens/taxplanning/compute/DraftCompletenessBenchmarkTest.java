package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.TaxCalculator;
import com.thiru.wealthlens.taxengine.input.EngineInputs;
import com.thiru.wealthlens.taxengine.rules.timeline.ClasspathRulePackProvider;
import com.thiru.wealthlens.taxengine.rules.timeline.ShippedRulePacks;
import java.util.Arrays;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Completeness is at most eight whole computations (W-115, D-670): the deduction rooms in five — the draft as it stands,
 * Schedule XV, the assessee's and the parents' health cover, own NPS — and rent in three, the draft as it stands and one
 * per HRA location band with {@code ANY_OTHER_PLACE}. Held to eight of {@code 01-PRD.md} §5 N1's 50 ms. Timings depend on
 * the machine, so this runs only under {@code -Pbenchmark}, as {@code DraftCalendarBenchmarkTest} does.
 */
@Log4j2
@Tag("benchmark")
class DraftCompletenessBenchmarkTest {

    private static final int WARM_UP = 100;
    private static final int TIMED = 500;
    private static final double EIGHT_COMPUTES_MILLIS = 8 * 50;

    @Test
    void items_whenHraAndEveryRoomArePriced_staysUnderEightComputesAtP99() {
        // Given — basic and HRA in the old regime with nothing entered: every room and both HRA locations take a run
        DraftCompletenessService service = new DraftCompletenessService(null, null, new TaxCalculator(
                ShippedRulePacks.compile("rulepacks", ClasspathRulePackProvider.shippedYears("rulepacks"))), null);
        EngineInputs inputs = DraftCompletenessServiceTest.inputs("FIRST_SCHEDULE_RATES");
        for (int i = 0; i < WARM_UP; i++) {
            service.items(inputs);
        }

        // When
        long[] nanos = new long[TIMED];
        for (int i = 0; i < TIMED; i++) {
            long start = System.nanoTime();
            service.items(inputs);
            nanos[i] = System.nanoTime() - start;
        }
        Arrays.sort(nanos);
        double p99 = nanos[(int) Math.ceil(TIMED * 0.99) - 1] / 1e6;
        log.info("W-115 completeness: p50 {} ms, p99 {} ms over {} runs", "%.3f".formatted(nanos[TIMED / 2] / 1e6),
                "%.3f".formatted(p99), TIMED);

        // Then
        assertThat(p99).as("completeness p99 in ms").isLessThan(EIGHT_COMPUTES_MILLIS);
    }
}
