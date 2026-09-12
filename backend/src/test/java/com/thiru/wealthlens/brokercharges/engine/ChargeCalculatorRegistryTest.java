package com.thiru.wealthlens.brokercharges.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeBasis;
import com.thiru.wealthlens.brokercharges.entity.ChargeRule;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Dispatch from a rule's basis to the implementation that serves it.
 *
 * <p>The registry's real job is failing loudly at startup. A {@code ChargeBasis} constant with no
 * calculator is a trap for whoever first writes a rule using it: the rate card would validate, the
 * application would start, and the charge would go missing at trade time.
 */
class ChargeCalculatorRegistryTest {

    @Test
    void get_returnsTheCalculatorForABasis() {
        // Given
        ChargeCalculatorRegistry registry = new ChargeCalculatorRegistry(allBases());

        // When / Then
        assertThat(registry.get(ChargeBasis.TURNOVER).basis()).isEqualTo(ChargeBasis.TURNOVER);
        assertThat(registry.get(ChargeBasis.DERIVED).basis()).isEqualTo(ChargeBasis.DERIVED);
    }

    @Test
    void construction_whenABasisHasNoCalculator_failsFastNamingIt() {
        // Given — every basis but FORMULA is served
        List<ChargeCalculator> incomplete = allBases().stream()
                .filter(calculator -> calculator.basis() != ChargeBasis.FORMULA)
                .toList();

        // When / Then — caught at startup, not when a rule using it is first priced
        assertThatThrownBy(() -> new ChargeCalculatorRegistry(incomplete))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FORMULA");
    }

    @Test
    void construction_whenTwoCalculatorsClaimOneBasis_failsFastNamingIt() {
        // Given — a duplicate would otherwise resolve arbitrarily by bean ordering
        List<ChargeCalculator> duplicated = new java.util.ArrayList<>(allBases());
        duplicated.add(new StubCalculator(ChargeBasis.FLAT));

        // When / Then
        assertThatThrownBy(() -> new ChargeCalculatorRegistry(duplicated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FLAT");
    }

    @Test
    void construction_whenNoCalculatorsAreSupplied_failsFast() {
        assertThatThrownBy(() -> new ChargeCalculatorRegistry(List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void get_whenBasisIsNull_isRejected() {
        ChargeCalculatorRegistry registry = new ChargeCalculatorRegistry(allBases());

        assertThatThrownBy(() -> registry.get(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("basis");
    }

    @Test
    void everyDeclaredBasisIsServed() {
        // Given — the guarantee stated as a test: adding a ChargeBasis constant without a
        // calculator breaks the build rather than a trade
        ChargeCalculatorRegistry registry = new ChargeCalculatorRegistry(allBases());

        // When / Then
        assertThat(Arrays.stream(ChargeBasis.values()).map(registry::get)).doesNotContainNull();
    }

    private static List<ChargeCalculator> allBases() {
        return Arrays.stream(ChargeBasis.values()).map(StubCalculator::new).map(ChargeCalculator.class::cast).toList();
    }

    private record StubCalculator(ChargeBasis basis) implements ChargeCalculator {

        @Override
        public BigDecimal compute(ChargeRule rule, ChargeContext context, ChargeAccumulator accumulator) {
            return BigDecimal.ONE;
        }
    }
}
