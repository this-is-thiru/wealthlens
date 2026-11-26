package com.thiru.wealthlens.shared.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class RowMappingTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final TargetTable WORKSPACES = new TargetTable("tax_year_workspaces", "id", Map.of("id", "character varying",
            "profile_id", "character varying", "tax_year_start", "integer", "primary_draft_id", "text", "created_by", "text",
            "updated_by", "text", "created_at", "timestamp without time zone", "updated_at", "timestamp without time zone"));
    private static final CollectionCopy COPY = new CollectionCopy("tax_year_workspaces", "tax_year_workspaces",
            Map.of("tax_year", "tax_year_start"), Set.of(), List.of());
    private static final ChildCopy FACTOR_COPY = ChildCopy.values("factors", "refresh_token_factors", "refresh_token_id", "factor");
    private static final CollectionCopy TOKENS = new CollectionCopy("refresh_tokens", "refresh_tokens", Map.of(), Set.of(),
            List.of(FACTOR_COPY));
    private static final TargetTable TOKENS_TABLE = new TargetTable("refresh_tokens", "id", Map.of("id", "character varying"));
    private static final ChildTable FACTORS = new ChildTable(FACTOR_COPY, Map.of("refresh_token_id", "character varying",
            "factor", "text"));
    private static final ChildCopy LINES_COPY = ChildCopy.orderedDocuments("lines", "user_charge_lines", "user_charge_id",
            "line_order");
    private static final ChildCopy AMOUNTS_COPY = ChildCopy.entries("amount_by_code", "user_charge_amounts", "user_charge_id",
            "charge_code", "amount");
    private static final CollectionCopy CHARGES = new CollectionCopy("user_charges", "user_charges", Map.of(), Set.of(),
            List.of(LINES_COPY, AMOUNTS_COPY));
    private static final TargetTable CHARGES_TABLE = new TargetTable("user_charges", "id", Map.of("id", "character varying"));
    private static final ChildTable LINES = new ChildTable(LINES_COPY, Map.of("user_charge_id", "character varying",
            "line_order", "integer", "code", "text"));
    private static final ChildTable AMOUNTS = new ChildTable(AMOUNTS_COPY, Map.of("user_charge_id", "character varying",
            "charge_code", "text", "amount", "double precision"));
    private static final ChildCopy DEMERGED_COPY = ChildCopy.documents("demerger_stocks", "corporate_action_demerger_stocks",
            "corporate_action_id");
    private static final CollectionCopy ACTIONS = new CollectionCopy("corporate_action", "corporate_action", Map.of(),
            Set.of("demerger_detail"), List.of(DEMERGED_COPY));
    private static final TargetTable ACTIONS_TABLE = new TargetTable("corporate_action", "id", Map.of("id", "character varying",
            "demerger_ratio", "text", "demerger_price_ratio", "text", "main_stock_code", "text"));
    private static final ChildTable DEMERGED = new ChildTable(DEMERGED_COPY, Map.of("corporate_action_id", "character varying",
            "stock_code", "text", "stock_name", "text"));
    private static final ChildCopy EVENTS_COPY = ChildCopy.documents("events", "tax_regime_election_events", "history_id");
    private static final CollectionCopy ELECTIONS = new CollectionCopy("tax_regime_election_history", "tax_regime_election_history",
            Map.of(), Set.of(), List.of(EVENTS_COPY));
    private static final TargetTable ELECTIONS_TABLE = new TargetTable("tax_regime_election_history", "id",
            Map.of("id", "character varying"));
    private static final ChildTable EVENTS = new ChildTable(EVENTS_COPY, Map.of("history_id", "character varying", "tax_year_start",
            "integer", "source_draft", "text"));
    private static final ChildCopy POLICY_COPY = ChildCopy.documents("policyDetails", "insurance_policy_details", "insurance_id_fk");
    private static final CollectionCopy INSURANCES = new CollectionCopy("insurances", "insurances", Map.of(), Set.of(),
            List.of(POLICY_COPY));
    private static final TargetTable INSURANCES_TABLE = new TargetTable("insurances", "id", Map.of("id", "character varying"));
    private static final ChildTable POLICY_DETAILS = new ChildTable(POLICY_COPY, Map.of("insurance_id_fk", "character varying",
            "policy_id", "text", "premium", "double precision", "start_date", "date", "notes", "text"));

    @Test
    void of_whenARenamedKeyAndAuditMetadata_fillsTheirColumns() {
        // Given — a workspace as m1.6.1 stored it
        Document stored = new Document("_id", new ObjectId("65a1b2c3d4e5f60718293a4b")).append("_class", "TaxYearWorkspace")
                .append("profile_id", "profile-1").append("tax_year", 2026).append("primary_draft_id", null)
                .append("audit_metadata", new Document("createdBy", "sealed-email").append("createdAt",
                        Date.from(Instant.parse("2026-01-31T18:30:00Z"))));

        // When
        SourceRow row = RowMapping.of(stored, COPY, WORKSPACES, List.of(), KOLKATA);

        // Then — auditing held Kolkata's local 2026-02-01T00:00, stored as its true instant 18:30Z, which is the UTC time;
        // _class and the null key leave no column
        assertThat(row.id()).isEqualTo("65a1b2c3d4e5f60718293a4b");
        assertThat(row.values()).containsExactlyInAnyOrderEntriesOf(Map.of("id", "65a1b2c3d4e5f60718293a4b", "profile_id", "profile-1",
                "tax_year_start", 2026, "created_by", "sealed-email", "created_at", LocalDateTime.of(2026, 1, 31, 18, 30)));
    }

    @Test
    void of_whenATimeTheApplicationHeldInUtc_isThatUtcTime() {
        // Given — JobContext.nowUtc() held 2026-02-01T00:00 UTC; Spring Data stored it as Kolkata's midnight, 18:30Z
        TargetTable failures = new TargetTable("tax_job_failures", "id", Map.of("id", "character varying", "failed_at",
                "timestamp without time zone"));
        Document stored = new Document("_id", "failure-1").append("failed_at", Date.from(Instant.parse("2026-01-31T18:30:00Z")));

        // When
        SourceRow row = RowMapping.of(stored, CollectionCopy.sameName("tax_job_failures"), failures, List.of(), KOLKATA);

        // Then
        assertThat(row.values()).containsEntry("failed_at", LocalDateTime.of(2026, 2, 1, 0, 0));
    }

    @Test
    void of_whenAKeyHasNoColumn_isRefusedNamingIt() {
        // Given
        Document stored = new Document("_id", "ws-1").append("profile_id", "profile-1").append("legacy_flag", true);

        // When / Then
        assertThatThrownBy(() -> RowMapping.of(stored, COPY, WORKSPACES, List.of(), KOLKATA)).isInstanceOf(IllegalStateException.class)
                .hasMessage("tax_year_workspaces ws-1: tax_year_workspaces has no column legacy_flag for a stored key");
    }

    @Test
    void of_whenTwoKeysFillOneColumn_isRefused() {
        // Given — the renamed key and the column's own name both stored
        Document stored = new Document("_id", "ws-1").append("tax_year", 2026).append("taxYearStart", 2027);

        // When / Then
        assertThatThrownBy(() -> RowMapping.of(stored, COPY, WORKSPACES, List.of(), KOLKATA)).isInstanceOf(IllegalStateException.class)
                .hasMessage("tax_year_workspaces ws-1: two stored keys fill tax_year_start");
    }

    @Test
    void of_whenAValueTheColumnCannotHold_isRefusedNamingTheColumn() {
        // Given
        Document stored = new Document("_id", "ws-1").append("tax_year", "2026");

        // When / Then
        assertThatThrownBy(() -> RowMapping.of(stored, COPY, WORKSPACES, List.of(), KOLKATA)).isInstanceOf(IllegalStateException.class)
                .hasMessage("tax_year_workspaces ws-1, tax_year_start: A String cannot be stored in a integer column");
    }

    @Test
    void of_whenAListOfValues_fillsItsChildTableAndNoParentColumn() {
        // Given — a refresh token as m1.6.1 stored it: factors a list of strings
        Document stored = new Document("_id", "token-1").append("factors", List.of("FACTOR_PASSWORD", "FACTOR_OTP"));

        // When
        SourceRow row = RowMapping.of(stored, TOKENS, TOKENS_TABLE, List.of(FACTORS), KOLKATA);

        // Then
        assertThat(row.values()).containsExactlyInAnyOrderEntriesOf(Map.of("id", "token-1"));
        assertThat(row.children()).containsExactly(Map.entry("refresh_token_factors",
                List.of(Map.of("factor", "FACTOR_PASSWORD"), Map.of("factor", "FACTOR_OTP"))));
    }

    @Test
    void of_whenAListOfSubDocuments_mapsEachKeyToItsColumn() {
        // Given — an insurance's policyDetails (no @Field at m1.6.1, so the Java name), one detail with a type hint and a null
        Document stored = new Document("_id", "insurance-1").append("policyDetails", List.of(new Document("_class", "PolicyDetails")
                .append("policy_id", "sealed-policy").append("premium", 1200.5).append("notes", null)
                .append("start_date", Date.from(Instant.parse("2026-04-01T00:00:00Z")))));

        // When
        SourceRow row = RowMapping.of(stored, INSURANCES, INSURANCES_TABLE, List.of(POLICY_DETAILS), KOLKATA);

        // Then
        assertThat(row.children()).containsExactly(Map.entry("insurance_policy_details", List.of(Map.of("policy_id", "sealed-policy",
                "premium", 1200.5, "start_date", LocalDate.of(2026, 4, 1)))));
    }

    @Test
    void of_whenASubDocumentKeyHasNoColumn_isRefusedNamingIt() {
        // Given
        Document stored = new Document("_id", "insurance-1").append("policyDetails", List.of(new Document("premium", 1.0)
                .append("legacy_flag", true)));

        // When / Then
        assertThatThrownBy(() -> RowMapping.of(stored, INSURANCES, INSURANCES_TABLE, List.of(POLICY_DETAILS), KOLKATA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("insurances insurance-1: insurance_policy_details has no column legacy_flag for a stored key");
    }

    @Test
    void of_whenAListHoldsNull_isRefused() {
        // Given
        Document stored = new Document("_id", "token-1").append("factors", Arrays.asList("FACTOR_PASSWORD", null));

        // When / Then
        assertThatThrownBy(() -> RowMapping.of(stored, TOKENS, TOKENS_TABLE, List.of(FACTORS), KOLKATA))
                .isInstanceOf(IllegalStateException.class).hasMessage("refresh_tokens token-1: factors holds a null");
    }

    @Test
    void of_whenAnOrderedList_writesEachEntrysPosition() {
        // Given — a user charge's lines as m1.6.1 stored them, in the order the engine evaluated its rules
        Document stored = new Document("_id", "uc-1").append("lines", List.of(new Document("code", "BROKERAGE"),
                new Document("code", "STT"), new Document("code", "GST")));

        // When
        SourceRow row = RowMapping.of(stored, CHARGES, CHARGES_TABLE, List.of(LINES, AMOUNTS), KOLKATA);

        // Then — positions 0, 1, 2, as Hibernate's @OrderColumn numbers them
        assertThat(row.children().get("user_charge_lines")).containsExactly(Map.of("code", "BROKERAGE", "line_order", 0),
                Map.of("code", "STT", "line_order", 1), Map.of("code", "GST", "line_order", 2));
    }

    @Test
    void of_whenAMap_writesOneRowPerEntry() {
        // Given — amount_by_code, a sub-document keyed by charge code
        Document stored = new Document("_id", "uc-1").append("amount_by_code", new Document("BROKERAGE", 20.0).append("STT", 10));

        // When
        SourceRow row = RowMapping.of(stored, CHARGES, CHARGES_TABLE, List.of(LINES, AMOUNTS), KOLKATA);

        // Then — the whole number stored as an int is widened to the column's double
        assertThat(row.children().get("user_charge_amounts")).containsExactly(Map.of("charge_code", "BROKERAGE", "amount", 20.0),
                Map.of("charge_code", "STT", "amount", 10.0));
    }

    @Test
    void of_whenAMapValueTheColumnCannotHold_isRefusedNamingTheColumn() {
        // Given
        Document stored = new Document("_id", "uc-1").append("amount_by_code", new Document("STT", "ten"));

        // When / Then
        assertThatThrownBy(() -> RowMapping.of(stored, CHARGES, CHARGES_TABLE, List.of(LINES, AMOUNTS), KOLKATA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("user_charges uc-1, amount: A String cannot be stored in a double precision column");
    }

    @Test
    void of_whenAnEmbeddedSubDocument_fillsTheParentsColumnsAndItsList() {
        // Given — a demerger's detail as m1.6.1 stored it
        Document stored = new Document("_id", "ca-1").append("demerger_detail", new Document("demerger_ratio", "1:1")
                .append("main_stock_code", "MAIN").append("demerger_price_ratio", null)
                .append("demerger_stocks", List.of(new Document("stock_code", "NEW1").append("stock_name", "New One"))));

        // When
        SourceRow row = RowMapping.of(stored, ACTIONS, ACTIONS_TABLE, List.of(DEMERGED), KOLKATA);

        // Then
        assertThat(row.values()).containsExactlyInAnyOrderEntriesOf(Map.of("id", "ca-1", "demerger_ratio", "1:1",
                "main_stock_code", "MAIN"));
        assertThat(row.children()).containsExactly(Map.entry("corporate_action_demerger_stocks",
                List.of(Map.of("stock_code", "NEW1", "stock_name", "New One"))));
    }

    @Test
    void of_whenAnEmbeddedKeyIsNotASubDocument_isRefused() {
        // Given
        Document stored = new Document("_id", "ca-1").append("demerger_detail", "1:1");

        // When / Then
        assertThatThrownBy(() -> RowMapping.of(stored, ACTIONS, ACTIONS_TABLE, List.of(DEMERGED), KOLKATA))
                .isInstanceOf(IllegalStateException.class).hasMessage("corporate_action ca-1: demerger_detail is not a sub-document");
    }

    @Test
    void of_whenASubDocumentKeyIsItsJavaName_fillsItsSnakeCaseColumn() {
        // Given — an election history's events: ElectionEvent is a record with no @Field, so m1.6.1 stored the Java names
        Document stored = new Document("_id", "history-1").append("events", List.of(new Document("taxYearStart", 2025)
                .append("sourceDraft", "draft-1")));

        // When
        SourceRow row = RowMapping.of(stored, ELECTIONS, ELECTIONS_TABLE, List.of(EVENTS), KOLKATA);

        // Then
        assertThat(row.children()).containsExactly(Map.entry("tax_regime_election_events",
                List.of(Map.of("tax_year_start", 2025, "source_draft", "draft-1"))));
    }

    @Test
    void of_whenASubDocumentHoldsBothSpellingsOfAKey_isRefused() {
        // Given
        Document stored = new Document("_id", "history-1").append("events", List.of(new Document("taxYearStart", 2025)
                .append("tax_year_start", 2026)));

        // When / Then
        assertThatThrownBy(() -> RowMapping.of(stored, ELECTIONS, ELECTIONS_TABLE, List.of(EVENTS), KOLKATA))
                .isInstanceOf(IllegalStateException.class).hasMessage("tax_regime_election_history history-1: two stored keys fill tax_year_start");
    }

    @Test
    void snakeCase_whenCamelOrSnake_isSnake() {
        // When / Then
        assertThat(RowMapping.snakeCase("lastSignedInAt")).isEqualTo("last_signed_in_at");
        assertThat(RowMapping.snakeCase("last_signed_in_at")).isEqualTo("last_signed_in_at");
    }
}
