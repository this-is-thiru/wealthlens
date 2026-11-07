package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.thiru.wealthlens.portfolio.migration.TradeOutcomeStoredTermRemoval;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The stored term removed from {@code trade_outcomes}, over a real database (W-160c, D-365). */
class TradeOutcomeStoredTermRemovalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TradeOutcomeStoredTermRemoval removal;

    /** A row as the old writers left it — raw, since the entity no longer maps either field. */
    private void insert(String id, Document storedTerm) {
        Document row = new Document("_id", id).append("email", "terms@example.com").append("stock_code", "INFY")
                .append("holding_period_days", 400L);
        row.putAll(storedTerm);
        mongoTemplate.getCollection("trade_outcomes").insertOne(row);
    }

    /** Rows "both" (term and reason), "reason-only" and "neither". */
    private void insertThreeRows() {
        insert("both", new Document("capital_gains_type", "LONG_TERM")
                .append("classification_reason", "held beyond 12 months"));
        insert("reason-only", new Document("classification_reason", "held beyond 12 months"));
        insert("neither", new Document());
    }

    private Document stored(String id) {
        return mongoTemplate.getCollection("trade_outcomes").find(new Document("_id", id)).first();
    }

    private static boolean carriesTheTerm(Document row) {
        return row.containsKey("capital_gains_type") || row.containsKey("classification_reason");
    }

    @Test
    void removeStoredTerms_whenADryRun_reportsTheRowsAndChangesNone() {
        // Given
        insertThreeRows();

        // When
        long found = removal.removeStoredTerms(true, "test").rows();

        // Then — "both" and "reason-only" carry a field
        assertThat(found).isEqualTo(2);
        assertThat(stored("both").getString("capital_gains_type")).isEqualTo("LONG_TERM");
        assertThat(stored("reason-only").getString("classification_reason")).isEqualTo("held beyond 12 months");
        assertThat(stored("both").containsKey("audit_metadata")).isFalse();
    }

    @Test
    void removeStoredTerms_whenWritten_removesBothFieldsAndNothingElse() {
        // Given
        insertThreeRows();

        // When
        long removed = removal.removeStoredTerms(false, "test").rows();

        // Then
        assertThat(removed).isEqualTo(2);
        for (String id : List.of("both", "reason-only", "neither")) {
            Document row = stored(id);
            assertThat(carriesTheTerm(row)).isFalse();
            assertThat(row.getString("stock_code")).isEqualTo("INFY");
            assertThat(row.getLong("holding_period_days")).isEqualTo(400L);
        }
        assertThat(stored("both").get("audit_metadata", Document.class).getString("updatedBy")).isEqualTo("test");
        assertThat(stored("reason-only").get("audit_metadata", Document.class).getString("updatedBy"))
                .isEqualTo("test");
        assertThat(stored("neither").containsKey("audit_metadata")).isFalse();
    }

    @Test
    void removeStoredTerms_whenRunAgain_changesNothing() {
        // Given
        insertThreeRows();
        removal.removeStoredTerms(false, "first");

        // When
        long second = removal.removeStoredTerms(false, "second").rows();

        // Then — no row matched, so none restamped
        assertThat(second).isZero();
        assertThat(stored("both").get("audit_metadata", Document.class).getString("updatedBy")).isEqualTo("first");
    }

    @Test
    void removeStoredTerms_whenARowWasLeftPartWay_finishesIt() {
        // Given — a row a stopped run left with the term but not the reason
        insert("part-way", new Document("capital_gains_type", "SHORT_TERM"));
        insert("done", new Document());

        // When
        long removed = removal.removeStoredTerms(false, "test").rows();

        // Then
        assertThat(removed).isEqualTo(1);
        assertThat(carriesTheTerm(stored("part-way"))).isFalse();
    }

    @Test
    void endpoint_whenWritten_removesAndRecordsTheCaller() {
        // Given
        insertThreeRows();

        // When / Then
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "SUPER_USER"))
                .when().post("/trade-outcomes/stored-terms/remove?dryRun=false")
                .then().statusCode(200).body("dryRun", equalTo(false)).body("rows", equalTo(2));
        assertThat(carriesTheTerm(stored("both"))).isFalse();
        assertThat(stored("both").get("audit_metadata", Document.class).getString("updatedBy"))
                .isEqualTo("admin@example.com");
    }

    @Test
    void endpoint_whenNotASuperUser_isForbidden() {
        // Given
        insertThreeRows();

        // When / Then
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "USER"))
                .when().post("/trade-outcomes/stored-terms/remove?dryRun=false").then().statusCode(403);
        assertThat(stored("both").getString("capital_gains_type")).isEqualTo("LONG_TERM");
    }
}
