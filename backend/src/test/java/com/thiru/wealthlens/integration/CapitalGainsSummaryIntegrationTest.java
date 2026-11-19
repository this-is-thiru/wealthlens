package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The finder the capital-gains read-model reads by (D-238), over a real database: {@code Between} excludes both
 * ends, which is what lets the service pass the days either side of a financial year and get exactly the year.
 */
class CapitalGainsSummaryIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "gains@example.com";

    @Autowired
    private TradeOutcomeRepository tradeOutcomeRepository;

    private static TradeOutcomeEntity soldOn(String id, LocalDate sellDate) {
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setId(id);
        row.setEmail(EMAIL);
        row.setSellDate(sellDate);
        return row;
    }

    @Test
    void findByEmailAndSellDateBetween_whenGivenTheDaysEitherSideOfAYear_returnsExactlyThatYear() {
        // Given — the last day before 2025-2026, its first and last days, and the first day after it
        tradeOutcomeRepository.saveAll(List.of(soldOn("before", LocalDate.of(2025, 3, 31)),
                soldOn("first", LocalDate.of(2025, 4, 1)), soldOn("last", LocalDate.of(2026, 3, 31)),
                soldOn("after", LocalDate.of(2026, 4, 1))));

        // When
        List<TradeOutcomeEntity> rows = tradeOutcomeRepository.findByEmailAndSellDateBetween(EMAIL,
                LocalDate.of(2025, 3, 31), LocalDate.of(2026, 4, 1));

        // Then
        assertThat(rows).extracting(TradeOutcomeEntity::getId).containsExactlyInAnyOrder("first", "last");
    }
}
