package com.thiru.wealthlens.shared.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thiru.wealthlens.shared.util.time.TLocalDate;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class TLocalDateFinancialYearTest {

    @Test
    @DisplayName("31 March is the last day of the financial year, not the first day of the next")
    void financialYear_onTheBoundary_belongsToTheYearEnding() {
        // Given / When / Then -- this is the one all three previous copies got wrong
        assertEquals("2023-2024", TLocalDate.financialYear(LocalDate.of(2024, 3, 31)));
    }

    @Test
    @DisplayName("the days either side of the boundary")
    void financialYear_aroundTheBoundary_isCorrect() {
        // Given / When / Then
        assertEquals("2023-2024", TLocalDate.financialYear(LocalDate.of(2024, 3, 30)));
        assertEquals("2024-2025", TLocalDate.financialYear(LocalDate.of(2024, 4, 1)));
    }

    @Test
    @DisplayName("the year opens on 1 April and runs through the following January to March")
    void financialYear_acrossTheYear_isCorrect() {
        // Given / When / Then
        assertEquals("2024-2025", TLocalDate.financialYear(LocalDate.of(2024, 4, 1)));
        assertEquals("2024-2025", TLocalDate.financialYear(LocalDate.of(2024, 12, 31)));
        assertEquals("2024-2025", TLocalDate.financialYear(LocalDate.of(2025, 1, 1)));
        assertEquals("2024-2025", TLocalDate.financialYear(LocalDate.of(2025, 3, 31)));
        assertEquals("2025-2026", TLocalDate.financialYear(LocalDate.of(2025, 4, 1)));
    }

    @Test
    @DisplayName("a leap day is nothing special here")
    void financialYear_onALeapDay_isCorrect() {
        // Given / When / Then
        assertEquals("2023-2024", TLocalDate.financialYear(LocalDate.of(2024, 2, 29)));
    }
}
