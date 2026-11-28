package com.thiru.wealthlens.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * What a row reads back as when a JSON converter column was never set.
 *
 * <p>Narrowed from {@code LegacyDocumentUpgradeIntegrationTest} by W-337e (D-949): the two {@code profit_and_loss}
 * tests it held (a document written before {@code @Version} existed; the documented backfill that fixes it) have
 * no PostgreSQL analogue — {@code version} is a {@code NOT NULL} column from the table's first row, so there is no
 * way to insert one without it. The one test that survives is still live: {@code TradeOutcomeEntity} carries
 * {@code @AllArgsConstructor}, the ADR-27 trap, so a row whose {@code buy_charge_breakup_json} /
 * {@code sell_charge_breakup_json} columns are {@code NULL} must still read back as an empty map, not a null one.
 */
class NullJsonColumnIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TradeOutcomeRepository tradeOutcomeRepository;

    @Test
    @DisplayName("a trade_outcomes row with no charge-breakup JSON reads back as empty maps, not null")
    void tradeOutcome_whenTheChargeBreakupColumnsAreNull_doesNotReadBackAsNull() {
        // Given -- TradeOutcomeEntity carries @AllArgsConstructor, which is the ADR-27 trap:
        // the mapper can bypass field initialisers, leaving the new maps null rather than empty.
        new JdbcTemplate(postgresDataSource).update(
                "INSERT INTO trade_outcomes (id, email, stock_code) VALUES (?, ?, ?)",
                "outcome-legacy-1", "legacy@example.com", "INFY");

        // When
        TradeOutcomeEntity loaded = tradeOutcomeRepository.findById("outcome-legacy-1").orElseThrow();

        // Then
        assertNotNull(loaded.getBuyChargeBreakup(), "buy breakup read back null on a row with no JSON");
        assertNotNull(loaded.getSellChargeBreakup(), "sell breakup read back null on a row with no JSON");
    }
}
