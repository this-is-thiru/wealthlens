package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the W-337b baseline (D-939) applies cleanly against a real PostgreSQL 18 Testcontainer: every script in
 * every module folder (V2 - V5, beside W-337a's V1) runs without error, in order, once each. No entity reads these
 * 25 tables yet — that is the port items' job (W-337c - f) — so this checks the DDL itself, not round-tripping data
 * through it.
 */
class FlywaySchemaBaselineIntegrationTest extends AbstractIntegrationTest {

    @Test
    void flyway_whenTheAppStarts_appliesEveryModuleScriptExactlyOnce() {
        // When
        JdbcTemplate jdbc = new JdbcTemplate(postgresDataSource);
        List<String> appliedScripts = jdbc.queryForList(
                "SELECT description FROM flyway_schema_history WHERE success = true ORDER BY installed_rank", String.class);

        // Then
        assertThat(appliedScripts).containsExactly("auth and insurance", "corporate", "privacy", "charge catalogue",
                "taxplanning", "insurances audit", "corporate action", "brokercharges");
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
                "user_charge_amounts", "tax_gazette_cursor", "tax_gazette_leads",
                "tax_gazette_lead_matched_terms", "tax_gazette_lead_possibly_affected", "tax_regime_election_history",
                "tax_regime_election_events", "tax_year_workspaces", "tax_access_log", "tax_employers",
                "tax_family_members", "tax_bank_accounts", "tax_profile_erasures", "tax_profiles", "tax_documents",
                "tax_parse_attempts", "tax_document_confirmations", "tax_document_confirmation_rejected_fields",
                "tax_job_leases", "tax_job_failures", "tax_lenders", "tax_landlords");
    }
}
