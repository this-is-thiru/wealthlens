package com.thiru.wealthlens.taxplanning.compute;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.taxengine.api.ComputedTax;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * A computation stored before W-114 has no {@code reliefPaidBy} in its advance tax, and must still be read by receipt
 * ({@code DraftComputationService.byReceipt}, whose mapper is a plain {@code JsonMapper}; D-665).
 */
class StoredComputationReadTest {

    @Test
    void advanceTax_whenStoredBeforeW114_readsAsNoReliefDay() {
        // Given — the shape every stored computation had before W-114
        String stored = """
                {"liable": true, "amount": {"amountInRupees": 87750}, "instalments": []}""";

        // When
        ComputedTax.AdvanceTax read = JsonMapper.builder().build().readValue(stored, ComputedTax.AdvanceTax.class);

        // Then
        assertThat(read.reliefPaidBy()).isEmpty();
    }
}
