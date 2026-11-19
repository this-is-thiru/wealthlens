package com.thiru.wealthlens.taxengine.rules.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.id.TaxYearId;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** An index table by financial year (D-115): a year it holds is read, any other refused. */
class IndexByYearTest {

    private static final IndexByYear TABLE = new IndexByYear(Map.of(TaxYearId.of(2023), 348, TaxYearId.of(2024), 363));

    @Test
    void of_whenTheYearIsHeld_isItsIndex() {
        // When / Then
        assertThat(TABLE.of(TaxYearId.of(2024))).isEqualTo(363);
    }

    @Test
    void of_whenTheYearIsNotHeld_isRefusedNamingTheYearsHeld() {
        // When / Then
        assertThatThrownBy(() -> TABLE.of(TaxYearId.of(2022)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2022-23")
                .hasMessageContaining("2023-24");
    }

    @Test
    void construct_whenAnIndexIsNotPositive_isRefused() {
        // When / Then
        assertThatThrownBy(() -> new IndexByYear(Map.of(TaxYearId.of(2023), 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
