package com.thiru.wealthlens.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.ProfitAndLossRepository;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

/**
 * What happens to documents written before this branch added fields to them.
 *
 * <p>These are not feature tests. They exist to answer one operational question — whether deploying
 * needs a data migration — against a real MongoDB rather than by reasoning about Spring Data's
 * behaviour.
 */
class LegacyDocumentUpgradeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProfitAndLossRepository profitAndLossRepository;

    @Autowired
    private TradeOutcomeRepository tradeOutcomeRepository;

    @Test
    @DisplayName("a profit_and_loss document written before @Version cannot be updated -- migration required")
    void profitAndLoss_writtenWithoutAVersionField_failsToSave() {
        // Given -- exactly what production holds: no `version` key at all
        mongoTemplate.getCollection("profit_and_loss").insertOne(new Document()
                .append("_id", "pnl-legacy-1")
                .append("email", "legacy@example.com")
                .append("financial_year", "2023-2024"));

        ProfitAndLossEntity loaded = profitAndLossRepository
                .findByEmailAndFinancialYear("legacy@example.com", "2023-2024")
                .orElseThrow();

        // When / Then -- a null version reads as "new", so Spring Data inserts instead of updating.
        // Every trade for an existing user in an existing year would fail this way. This test
        // exists to keep the migration honest: if someone makes it pass without the backfill, the
        // migration entry in the runbook should go with it.
        assertThrows(DuplicateKeyException.class, () -> profitAndLossRepository.save(loaded));
    }

    @Test
    @DisplayName("after the documented version backfill, the same document updates normally")
    void profitAndLoss_afterTheVersionBackfill_savesAsAnUpdate() {
        // Given
        mongoTemplate.getCollection("profit_and_loss").insertOne(new Document()
                .append("_id", "pnl-legacy-2")
                .append("email", "legacy@example.com")
                .append("financial_year", "2024-2025"));

        // When -- the migration exactly as the runbook states it
        mongoTemplate.getCollection("profit_and_loss").updateMany(
                new Document("version", new Document("$exists", false)),
                new Document("$set", new Document("version", 0L)));

        ProfitAndLossEntity loaded = profitAndLossRepository
                .findByEmailAndFinancialYear("legacy@example.com", "2024-2025")
                .orElseThrow();
        ProfitAndLossEntity saved = profitAndLossRepository.save(loaded);

        // Then
        assertNotNull(saved.getVersion());
        assertEquals(1, mongoTemplate.getCollection("profit_and_loss").countDocuments(),
                "the save must update the document, not insert a duplicate");
    }

    @Test
    @DisplayName("a trade_outcomes row written before the charge-breakup fields reads back safely")
    void tradeOutcome_writtenWithoutTheNewMaps_doesNotReadBackAsNull() {
        // Given -- TradeOutcomeEntity carries @AllArgsConstructor, which is the ADR-27 trap:
        // the mapper can bypass field initialisers, leaving the new maps null rather than empty.
        mongoTemplate.getCollection("trade_outcomes").insertOne(new Document()
                .append("_id", "outcome-legacy-1")
                .append("email", "legacy@example.com")
                .append("stock_code", "INFY"));

        // When
        TradeOutcomeEntity loaded = tradeOutcomeRepository.findById("outcome-legacy-1").orElseThrow();

        // Then
        assertNotNull(loaded.getBuyChargeBreakup(), "buy breakup read back null on a legacy row");
        assertNotNull(loaded.getSellChargeBreakup(), "sell breakup read back null on a legacy row");
    }
}
