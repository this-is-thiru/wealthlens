package com.thiru.wealthlens.brokercharges.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeCategory;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeRuleSource;
import com.thiru.wealthlens.brokercharges.entity.ChargeLine;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The accumulator is the running state of one evaluation.
 *
 * <p>Its whole reason for existing is {@code sumOf}: a derived rule such as GST is a percentage of
 * an explicitly named set of earlier lines. Summing the wrong set is the defect this replaces —
 * the superseded implementation taxed securities transaction tax and stamp duty, neither of which
 * is taxable.
 */
class ChargeAccumulatorTest {

    @Test
    void sumOf_addsOnlyTheNamedCodes() {
        // Given
        ChargeAccumulator accumulator = new ChargeAccumulator();
        accumulator.add(line("BROKERAGE", 20.00));
        accumulator.add(line("EXCHANGE_TXN", 2.97));
        accumulator.add(line("STT", 100.00));
        accumulator.add(line("STAMP_DUTY", 15.00));

        // When — the taxable base for GST, which excludes both statutory charges
        BigDecimal base = accumulator.sumOf(List.of("BROKERAGE", "EXCHANGE_TXN"));

        // Then
        assertThat(base).isEqualByComparingTo("22.97");
    }

    @Test
    void sumOf_ignoresCodesThatWereNotCharged() {
        // Given — a rule may name a base code that its side or eligibility filtered out
        ChargeAccumulator accumulator = new ChargeAccumulator();
        accumulator.add(line("BROKERAGE", 20.00));

        // When / Then
        assertThat(accumulator.sumOf(List.of("BROKERAGE", "DP"))).isEqualByComparingTo("20.00");
    }

    @Test
    void sumOf_whenNoCodesAreNamed_isZero() {
        ChargeAccumulator accumulator = new ChargeAccumulator();
        accumulator.add(line("BROKERAGE", 20.00));

        assertThat(accumulator.sumOf(List.of())).isEqualByComparingTo("0");
        assertThat(accumulator.sumOf(null)).isEqualByComparingTo("0");
    }

    @Test
    void sumOf_addsRepeatedCodesTogether() {
        // Given — a per-lot rule emits one line per lot under a single code
        ChargeAccumulator accumulator = new ChargeAccumulator();
        accumulator.add(line("EXIT_LOAD", 12.50));
        accumulator.add(line("EXIT_LOAD", 7.25));

        // When / Then
        assertThat(accumulator.sumOf(List.of("EXIT_LOAD"))).isEqualByComparingTo("19.75");
    }

    @Test
    void amountOf_returnsTheChargeUnderACode() {
        ChargeAccumulator accumulator = new ChargeAccumulator();
        accumulator.add(line("DP", 13.50));

        assertThat(accumulator.amountOf("DP")).isEqualByComparingTo("13.50");
        assertThat(accumulator.amountOf("GST")).isEqualByComparingTo("0");
    }

    @Test
    void total_sumsEveryLine() {
        ChargeAccumulator accumulator = new ChargeAccumulator();
        accumulator.add(line("BROKERAGE", 20.00));
        accumulator.add(line("STT", 100.00));

        assertThat(accumulator.total()).isEqualByComparingTo("120.00");
    }

    @Test
    void lines_arePreservedInEvaluationOrder() {
        // Given — a contract note reads in the order charges were applied
        ChargeAccumulator accumulator = new ChargeAccumulator();
        accumulator.add(line("BROKERAGE", 20.00));
        accumulator.add(line("STT", 100.00));
        accumulator.add(line("GST", 3.60));

        // When / Then
        assertThat(accumulator.lines()).extracting(ChargeLine::getCode)
                .containsExactly("BROKERAGE", "STT", "GST");
    }

    @Test
    void lines_cannotBeMutatedFromOutside() {
        // Given — the engine owns the evaluation; a calculator must not rewrite history
        ChargeAccumulator accumulator = new ChargeAccumulator();
        accumulator.add(line("BROKERAGE", 20.00));

        // When
        List<ChargeLine> exposed = accumulator.lines();

        // Then
        assertThat(exposed).isUnmodifiable();
    }

    @Test
    void newAccumulator_isEmpty() {
        ChargeAccumulator accumulator = new ChargeAccumulator();

        assertThat(accumulator.lines()).isEmpty();
        assertThat(accumulator.total()).isEqualByComparingTo("0");
    }

    private static ChargeLine line(String code, double amount) {
        ChargeLine line = new ChargeLine();
        line.setCode(code);
        line.setCategory(ChargeCategory.BROKERAGE);
        line.setBasis(ChargeBasis.FLAT);
        line.setSource(ChargeRuleSource.SCHEDULE);
        line.setAmount(amount);
        return line;
    }
}
