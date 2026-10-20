package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;

import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.migration.TradeOutcomeFinancialYearMigration;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import java.time.LocalDate;
import java.util.List;
import org.bson.Document;
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
        int rewritten = migration.rewriteFinancialYears(false, "test").rows();

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
        migration.rewriteFinancialYears(false, "test");

        // When
        int second = migration.rewriteFinancialYears(false, "test").rows();

        // Then
        assertThat(second).isZero();
        assertThat(yearOf("june")).isEqualTo("2024-2025");
    }

    @Test
    void rewriteFinancialYears_whenTheStringDisagreesWithTheSellDate_followsTheSellDate() {
        // Given — a row whose stored year is wrong for its own sale
        tradeOutcomeRepository.save(row("mislabelled", "FY2024-25", LocalDate.of(2026, 3, 31)));

        // When
        migration.rewriteFinancialYears(false, "test");

        // Then — reshaping the string would give 2024-2025
        assertThat(yearOf("mislabelled")).isEqualTo("2025-2026");
    }

    @Test
    void rewriteFinancialYears_whenAnFyRowHasNoSellDate_isRefusedByItsId() {
        // Given
        tradeOutcomeRepository.save(row("undated", "FY2024-25", null));

        // When / Then
        assertThatThrownBy(() -> migration.rewriteFinancialYears(false, "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("undated");
    }

    @Test
    void rewriteFinancialYears_whenADryRun_reportsTheRowsAndRewritesNone() {
        // Given
        tradeOutcomeRepository.saveAll(List.of(row("june", "FY2024-25", LocalDate.of(2024, 6, 10)),
                row("year-end", "FY2025-26", LocalDate.of(2026, 3, 31))));

        // When
        int found = migration.rewriteFinancialYears(true, "test").rows();

        // Then
        assertThat(found).isEqualTo(2);
        assertThat(yearOf("june")).isEqualTo("FY2024-25");
        assertThat(yearOf("year-end")).isEqualTo("FY2025-26");
    }

    @Test
    void endpoint_whenWritten_rewritesAndRecordsTheCaller() {
        // Given
        tradeOutcomeRepository.save(row("june", "FY2024-25", LocalDate.of(2024, 6, 10)));

        // When / Then
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "SUPER_USER"))
                .when().post("/trade-outcomes/financial-years/rewrite?dryRun=false")
                .then().statusCode(200).body("dryRun", equalTo(false)).body("rows", equalTo(1));
        assertThat(yearOf("june")).isEqualTo("2024-2025");
        assertThat(mongoTemplate.getCollection("trade_outcomes").find(new Document("_id", "june")).first()
                .get("audit_metadata", Document.class).getString("updatedBy")).isEqualTo("admin@example.com");
    }

    @Test
    void endpoint_whenNotASuperUser_isForbidden() {
        // Given
        tradeOutcomeRepository.save(row("june", "FY2024-25", LocalDate.of(2024, 6, 10)));

        // When / Then
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "USER"))
                .when().post("/trade-outcomes/financial-years/rewrite?dryRun=false").then().statusCode(403);
        assertThat(yearOf("june")).isEqualTo("FY2024-25");
    }
}
