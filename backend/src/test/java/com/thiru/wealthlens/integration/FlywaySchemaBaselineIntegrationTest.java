package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the W-337b baseline (D-939) and W-337f's follow-on (D-951) apply cleanly against a real PostgreSQL 18
 * Testcontainer: every script in every module folder (V1 - V10) runs without error, in order, once each.
 */
class FlywaySchemaBaselineIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void flyway_whenTheAppStarts_appliesEveryModuleScriptExactlyOnce() {
        // When
        JdbcTemplate jdbc = new JdbcTemplate(postgresDataSource);
        List<String> appliedScripts = jdbc.queryForList(
                "SELECT description FROM flyway_schema_history WHERE success = true ORDER BY installed_rank", String.class);

        // Then — V10 shares V5's derived description ("taxplanning"), both from the folder name
        assertThat(appliedScripts).containsExactly("auth and insurance", "corporate", "privacy", "charge catalogue",
                "taxplanning", "insurances audit", "corporate action", "brokercharges", "portfolio", "taxplanning", "unique nulls not distinct", "pnl unique nulls not distinct");
    }

    @Test
    void entityManagerFactory_whenStarted_validatesEveryEntityAgainstItsTable() {
        // When
        Object schemaAction = entityManagerFactory.getProperties().get("hibernate.hbm2ddl.auto");

        // Then — the app refuses to start when an entity and its table disagree (W-405, D-963)
        assertThat(schemaAction).isEqualTo("validate");
    }

    @Test
    void uniqueIndexes_whenTheNullableColumnIsNullTwice_refuseTheSecondRow() {
        // Given — MongoDB's non-sparse unique indexes refused a second missing (null) value; PostgreSQL must too (W-407c G-1, W-407d)
        JdbcTemplate jdbc = new JdbcTemplate(postgresDataSource);
        jdbc.update("INSERT INTO charge_accounts (id, email, broker_name) VALUES ('a1', 'e', 'ZERODHA')");
        jdbc.update("INSERT INTO user_charges (id, email) VALUES ('u1', 'e')");
        jdbc.update("INSERT INTO charge_instruments (id, stock_code) VALUES ('i1', 'FUND')");
        jdbc.update("INSERT INTO profit_and_loss (id, email) VALUES ('p1', 'e')");

        // When / Then
        assertThatThrownBy(() -> jdbc.update("INSERT INTO charge_accounts (id, email, broker_name) VALUES ('a2', 'e', 'ZERODHA')"))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO user_charges (id, email) VALUES ('u2', 'e')"))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO charge_instruments (id, stock_code) VALUES ('i2', 'FUND')"))
                .isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO profit_and_loss (id, email) VALUES ('p2', 'e')"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void schema_whenBuilt_everyNewTableAndItsChildTableExist() {
        // When
        JdbcTemplate jdbc = new JdbcTemplate(postgresDataSource);
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
                String.class);

        // Then — one per module, main tables and their child tables
        assertThat(tables).contains("lastly_performed_corporate_action", "corporate_action",
                "corporate_action_demerger_stocks", "privacy_erasure_requests", "privacy_consents",
                "privacy_breach_intimations", "privacy_processing_log", "privacy_grievances", "privacy_breach_notices",
                "privacy_breach_notice_affected", "charge_catalogue", "charge_schedules", "charge_instruments",
                "charge_accounts", "charge_account_billing_events", "user_charges", "user_charge_lines",
                "user_charge_amounts", "assets", "transactions", "trade_outcomes", "profit_and_loss",
                "tax_gazette_cursor", "tax_gazette_leads",
                "tax_gazette_lead_matched_terms", "tax_gazette_lead_possibly_affected", "tax_regime_election_history",
                "tax_regime_election_events", "tax_year_workspaces", "tax_access_log", "tax_employers",
                "tax_family_members", "tax_bank_accounts", "tax_profile_erasures", "tax_profiles", "tax_documents",
                "tax_parse_attempts", "tax_document_confirmations", "tax_document_confirmation_rejected_fields",
                "tax_job_leases", "tax_job_failures", "tax_lenders", "tax_landlords",
                "tax_draft_computations", "tax_draft_computation_law_and_exec", "tax_draft_revisions",
                "tax_filing_drafts", "tax_document_bytes");
    }
}
