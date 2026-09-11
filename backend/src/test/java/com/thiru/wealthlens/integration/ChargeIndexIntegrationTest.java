package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.StreamSupport;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/**
 * Asserts the declared indexes actually exist in MongoDB.
 *
 * <p>{@code ChargeIndexInitializer} exists because {@code auto-index-creation} is off application
 * wide, so an {@code @Indexed} or {@code @CompoundIndex} annotation creates nothing on its own.
 * Nothing verified that the initializer does its job — so if it stopped running, every index would
 * disappear silently: queries would still return correct answers, just by collection scan, and no
 * test would fail. The AMC cycle's selection spans users, so that one degrades from an index seek to
 * a scan of every account in the system.
 *
 * <p>Asserted by name rather than by count, so adding an index does not break this and removing one
 * does.
 */
class ChargeIndexIntegrationTest extends AbstractIntegrationTest {

    private List<String> indexNamesOf(String collection) {
        return StreamSupport.stream(mongoTemplate.getCollection(collection).listIndexes().spliterator(), false)
                .map(index -> index.getString("name"))
                .toList();
    }

    @Test
    void userCharges_carriesTheUniquenessThatMakesARecordAnUpsert() {
        // Given / When
        List<String> indexes = indexNamesOf("user_charges");

        // Then — {email, transaction_id} being unique is what makes record() replace rather than
        // append, and therefore what makes the backfill safe to re-run.
        assertThat(indexes).contains("user_charge_txn_idx", "user_charge_dedupe_idx",
                "user_charge_history_idx", "user_charge_schedule_idx");

        Document txnIdx = StreamSupport
                .stream(mongoTemplate.getCollection("user_charges").listIndexes().spliterator(), false)
                .filter(index -> "user_charge_txn_idx".equals(index.getString("name")))
                .findFirst().orElseThrow();
        assertThat(txnIdx.getBoolean("unique", false)).isTrue();
    }

    /**
     * The AMC cycle's query is the only one in the charges module that spans users rather than
     * sitting under one email, so it is the only one where a missing index costs a full scan of the
     * collection rather than of one person's rows.
     */
    @Test
    void chargeAccounts_carriesTheAmcDueIndex() {
        assertThat(indexNamesOf("charge_accounts"))
                .contains("charge_account_idx", "charge_account_amc_due_idx");
    }

    /** Seeding is idempotent by scheme and start date; the database is what enforces it. */
    @Test
    void chargeInstruments_enforcesOneProfilePerSchemeAndStartDate() {
        assertThat(indexNamesOf("charge_instruments")).contains("charge_instrument_scheme_idx");

        Document scheme = StreamSupport
                .stream(mongoTemplate.getCollection("charge_instruments").listIndexes().spliterator(), false)
                .filter(index -> "charge_instrument_scheme_idx".equals(index.getString("name")))
                .findFirst().orElseThrow();
        assertThat(scheme.getBoolean("unique", false)).isTrue();
    }

    @Test
    void chargeSchedulesAndCatalogue_carryTheirUniqueCodes() {
        assertThat(indexNamesOf("charge_schedules")).anyMatch(name -> name.contains("schedule_code"));
        assertThat(indexNamesOf("charge_catalogue")).anyMatch(name -> name.contains("code"));
    }
}
