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
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class RowMappingTest {

    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private static final TargetTable WORKSPACES = new TargetTable("tax_year_workspaces", "id", Map.of("id", "character varying",
            "profile_id", "character varying", "tax_year_start", "integer", "primary_draft_id", "text", "created_by", "text",
            "updated_by", "text", "created_at", "timestamp without time zone", "updated_at", "timestamp without time zone"));
    private static final CollectionCopy COPY = new CollectionCopy("tax_year_workspaces", "tax_year_workspaces",
            Map.of("tax_year", "tax_year_start"), List.of());
    private static final ChildCopy FACTOR_COPY = ChildCopy.values("factors", "refresh_token_factors", "refresh_token_id", "factor");
    private static final CollectionCopy TOKENS = new CollectionCopy("refresh_tokens", "refresh_tokens", Map.of(), List.of(FACTOR_COPY));
    private static final TargetTable TOKENS_TABLE = new TargetTable("refresh_tokens", "id", Map.of("id", "character varying"));
    private static final ChildTable FACTORS = new ChildTable(FACTOR_COPY, Map.of("refresh_token_id", "character varying",
            "factor", "text"));
    private static final ChildCopy POLICY_COPY = ChildCopy.documents("policyDetails", "insurance_policy_details", "insurance_id_fk");
    private static final CollectionCopy INSURANCES = new CollectionCopy("insurances", "insurances", Map.of(), List.of(POLICY_COPY));
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
    void snakeCase_whenCamelOrSnake_isSnake() {
        // When / Then
        assertThat(RowMapping.snakeCase("lastSignedInAt")).isEqualTo("last_signed_in_at");
        assertThat(RowMapping.snakeCase("last_signed_in_at")).isEqualTo("last_signed_in_at");
    }
}
