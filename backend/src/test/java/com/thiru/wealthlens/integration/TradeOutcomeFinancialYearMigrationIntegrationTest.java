package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.migration.TradeOutcomeFinancialYearMigration;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The FY-form rows rewritten from their sell dates, over a real database (D-239). */
class TradeOutcomeFinancialYearMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TradeOutcomeRepository tradeOutcomeRepository;

    @Autowired
    private TradeOutcomeFinancialYearMigration migration;

    private static TradeOutcomeEntity row(String id, String financialYear, LocalDate sellDate) {
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setId(id);
        row.setEmail("years@example.com");
        row.setFinancialYear(financialYear);
        row.setSellDate(sellDate);
        return row;
    }

    private String yearOf(String id) {
        return tradeOutcomeRepository.findById(id).orElseThrow().getFinancialYear();
    }

    @Test
    void rewriteFinancialYears_whenRowsCarryTheFyForm_recomputesThemFromTheSellDate() {
        // Given — two FY rows, one already in portfolio's form, one the matcher never dated
        tradeOutcomeRepository.saveAll(List.of(row("june", "FY2024-25", LocalDate.of(2024, 6, 10)),
                row("year-end", "FY2025-26", LocalDate.of(2026, 3, 31)),
                row("standard", "2025-2026", LocalDate.of(2025, 5, 1)), row("unknown", "UNKNOWN", null)));

        // When
        int rewritten = migration.rewriteFinancialYears();

        // Then
        assertThat(rewritten).isEqualTo(2);
        assertThat(yearOf("june")).isEqualTo("2024-2025");
        assertThat(yearOf("year-end")).isEqualTo("2025-2026");
        assertThat(yearOf("standard")).isEqualTo("2025-2026");
        assertThat(yearOf("unknown")).isEqualTo("UNKNOWN");
    }

    @Test
    void rewriteFinancialYears_whenRunAgain_changesNothing() {
        // Given
        tradeOutcomeRepository.save(row("june", "FY2024-25", LocalDate.of(2024, 6, 10)));
        migration.rewriteFinancialYears();

        // When
        int second = migration.rewriteFinancialYears();

        // Then
        assertThat(second).isZero();
        assertThat(yearOf("june")).isEqualTo("2024-2025");
    }

    @Test
    void rewriteFinancialYears_whenTheStringDisagreesWithTheSellDate_followsTheSellDate() {
        // Given — a row whose stored year is wrong for its own sale
        tradeOutcomeRepository.save(row("mislabelled", "FY2024-25", LocalDate.of(2026, 3, 31)));

        // When
        migration.rewriteFinancialYears();

        // Then — reshaping the string would give 2024-2025
        assertThat(yearOf("mislabelled")).isEqualTo("2025-2026");
    }

    @Test
    void rewriteFinancialYears_whenAnFyRowHasNoSellDate_isRefusedByItsId() {
        // Given
        tradeOutcomeRepository.save(row("undated", "FY2024-25", null));

        // When / Then
        assertThatThrownBy(() -> migration.rewriteFinancialYears())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("undated");
    }
}
