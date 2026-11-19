package com.thiru.wealthlens.taxplanning.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.taxengine.rules.money.Money;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The employer's part of tax at source is a part of it (W-113, D-664 C1a). */
class DraftFilingTest {

    private static DraftInputs.Filing filing(String taxAtSource, String byEmployer) {
        return new DraftInputs.Filing(null, null, List.of(), taxAtSource == null ? null : Money.of(taxAtSource), List.of(),
                Map.of(), null, byEmployer == null ? null : Money.of(byEmployer));
    }

    @Test
    void filing_whenTheEmployersPartIsMoreThanTaxAtSource_isRefused() {
        // Given / When / Then
        assertThatThrownBy(() -> filing("75000", "75000.01")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/filing/ofWhichByEmployer");
    }

    @Test
    void filing_whenTheEmployersPartIsStatedWithNoTaxAtSource_isRefused() {
        // Given / When / Then
        assertThatThrownBy(() -> filing(null, "1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filing_whenTheEmployersPartIsBelowNil_isRefused() {
        // Given / When / Then
        assertThatThrownBy(() -> filing("75000", "-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void filing_whenTheEmployersPartIsAllOfTaxAtSource_isKept() {
        // Given / When
        DraftInputs.Filing filing = filing("75000", "75000");

        // Then
        assertThat(filing.byEmployer()).contains(Money.of("75000"));
    }

    @Test
    void filing_whenTheEmployersPartIsNotStated_isEmpty() {
        // Given / When / Then
        assertThat(filing("75000", null).byEmployer()).isEmpty();
    }
}
