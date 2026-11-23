package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import java.util.List;
import java.util.stream.StreamSupport;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Asserts the declared indexes exist: PostgreSQL {@code pg_indexes} for {@code brokercharges} tables (W-337d2, D-947)
 * and MongoDB for {@code profit_and_loss} until W-337e moves {@code portfolio}.
 */
class ChargeIndexIntegrationTest extends AbstractIntegrationTest {

    @Test
    void profitAndLoss_isUniquePerUserAndFinancialYear() {
        assertThat(mongoIndexNamesOf("profit_and_loss")).contains("pnl_user_year_idx");

        Document idx = StreamSupport
                .stream(mongoTemplate.getCollection("profit_and_loss").listIndexes().spliterator(), false)
                .filter(index -> "pnl_user_year_idx".equals(index.getString("name")))
                .findFirst().orElseThrow();
        assertThat(idx.getBoolean("unique", false)).isTrue();
    }

    @Test
    void profitAndLoss_refusesAStaleWriteRatherThanLosingIt() {
        // Given — one document, read twice, as two concurrent trades would
        ProfitAndLossEntity seed = new ProfitAndLossEntity("locking@wealthlens.test", "2025-2026");
        mongoTemplate.save(seed);

        ProfitAndLossEntity first = mongoTemplate.findById(seed.getId(), ProfitAndLossEntity.class);
        ProfitAndLossEntity second = mongoTemplate.findById(seed.getId(), ProfitAndLossEntity.class);

        // When — the first write succeeds and moves the version on
        first.setEmail("locking@wealthlens.test");
        mongoTemplate.save(first);

        // Then — the second is working from a version that no longer exists
        second.setEmail("locking@wealthlens.test");
        assertThatThrownBy(() -> mongoTemplate.save(second))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private List<String> mongoIndexNamesOf(String collection) {
        return StreamSupport.stream(mongoTemplate.getCollection(collection).listIndexes().spliterator(), false)
                .map(index -> index.getString("name"))
                .toList();
    }

    private List<String> pgIndexNamesOf(String table) {
        return new JdbcTemplate(postgresDataSource).queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?",
                String.class, table);
    }

    private boolean isPgIndexUnique(String table, String indexName) {
        String def = new JdbcTemplate(postgresDataSource).queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = ? AND indexname = ?",
                String.class, table, indexName);
        return def != null && def.startsWith("CREATE UNIQUE INDEX");
    }

    @Test
    void userCharges_carriesTheUniquenessThatMakesARecordAnUpsert() {
        // Given / When
        List<String> indexes = pgIndexNamesOf("user_charges");

        // Then — (email, transaction_id) being unique makes record() replace rather than append
        assertThat(indexes).contains("user_charge_txn_idx", "user_charge_dedupe_idx",
                "user_charge_history_idx");
        assertThat(indexes).doesNotContain("user_charge_schedule_idx");
        assertThat(isPgIndexUnique("user_charges", "user_charge_txn_idx")).isTrue();
    }

    @Test
    void chargeAccounts_carriesTheAmcDueIndex() {
        assertThat(pgIndexNamesOf("charge_accounts"))
                .contains("charge_account_idx", "charge_account_amc_due_idx");
        assertThat(isPgIndexUnique("charge_accounts", "charge_account_idx")).isTrue();
    }

    @Test
    void chargeInstruments_enforcesOneProfilePerSchemeAndStartDate() {
        assertThat(pgIndexNamesOf("charge_instruments")).contains("charge_instrument_scheme_idx");
        assertThat(isPgIndexUnique("charge_instruments", "charge_instrument_scheme_idx")).isTrue();
    }

    @Test
    void chargeSchedulesAndCatalogue_carryTheirUniqueCodes() {
        assertThat(pgIndexNamesOf("charge_schedules")).anyMatch(name -> name.contains("schedule_code"));
        assertThat(pgIndexNamesOf("charge_catalogue")).anyMatch(name -> name.contains("code"));
    }
}
