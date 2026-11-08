package com.thiru.wealthlens.taxengine.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.generated.ComponentCodes;
import com.thiru.wealthlens.taxengine.rules.money.Money;
import com.thiru.wealthlens.taxengine.test.NodeHarness;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AllocationSpaceTest {

    @Test
    void new_whenNothingIsMovable_isRefused() {
        assertThatThrownBy(() -> new AllocationSpace(NodeHarness.plainTaxpayer(), Set.of(),
                ComponentCodes.SPECIAL_ALLOWANCE, Money.ZERO, 6)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one component");
    }

    @Test
    void new_whenTheResidualIsAlsoMovable_isRefused() {
        assertThatThrownBy(() -> new AllocationSpace(NodeHarness.plainTaxpayer(), Set.of(ComponentCodes.SPECIAL_ALLOWANCE),
                ComponentCodes.SPECIAL_ALLOWANCE, Money.ZERO, 6)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot also be one of them");
    }

    @Test
    void new_whenTheFloorIsNegative_isRefused() {
        assertThatThrownBy(() -> new AllocationSpace(NodeHarness.plainTaxpayer(), Set.of(ComponentCodes.HRA),
                ComponentCodes.SPECIAL_ALLOWANCE, Money.of("-1"), 6)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void new_whenTheBreadthIsNil_isRefused() {
        assertThatThrownBy(() -> new AllocationSpace(NodeHarness.plainTaxpayer(), Set.of(ComponentCodes.HRA),
                ComponentCodes.SPECIAL_ALLOWANCE, Money.ZERO, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one component, not 0");
    }

    @Test
    void new_whenAPartIsMissing_isRefused() {
        assertThatThrownBy(() -> new AllocationSpace(null, Set.of(ComponentCodes.HRA), ComponentCodes.SPECIAL_ALLOWANCE,
                Money.ZERO, 6)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("needs the inputs");
    }
}
