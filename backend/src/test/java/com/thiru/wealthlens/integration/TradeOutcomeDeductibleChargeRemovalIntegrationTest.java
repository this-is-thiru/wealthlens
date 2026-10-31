package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.thiru.wealthlens.portfolio.migration.TradeOutcomeDeductibleChargeRemoval;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The stored deductible charges removed from {@code trade_outcomes}, over a real database (W-162b, D-377). */
class TradeOutcomeDeductibleChargeRemovalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TradeOutcomeDeductibleChargeRemoval removal;

    /** A row as the old writers left it — raw, since the entity no longer maps either field. */
    private void insert(String id, Document storedCharges) {
        Document row = new Document("_id", id).append("email", "charges@example.com").append("stock_code", "INFY")
                .append("holding_period_days", 400L);
        row.putAll(storedCharges);
        mongoTemplate.getCollection("trade_outcomes").insertOne(row);
    }

    /** Rows "both" (buy and sell), "sell-only" and "neither". */
    private void insertThreeRows() {
        insert("both", new Document("deductible_buy_charges", 5.0).append("deductible_sell_charges", 7.0));
        insert("sell-only", new Document("deductible_sell_charges", 3.0));
        insert("neither", new Document());
    }

    private Document stored(String id) {
        return mongoTemplate.getCollection("trade_outcomes").find(new Document("_id", id)).first();
    }

    private static boolean carriesACharge(Document row) {
        return row.containsKey("deductible_buy_charges") || row.containsKey("deductible_sell_charges");
    }

    @Test
    void removeDeductibleCharges_whenADryRun_reportsTheRowsAndChangesNone() {
        // Given
        insertThreeRows();

        // When
        long found = removal.removeDeductibleCharges(true, "test").rows();

        // Then — "both" and "sell-only" carry a field
        assertThat(found).isEqualTo(2);
        assertThat(stored("both").getDouble("deductible_buy_charges")).isEqualTo(5.0);
        assertThat(stored("sell-only").getDouble("deductible_sell_charges")).isEqualTo(3.0);
        assertThat(stored("both").containsKey("audit_metadata")).isFalse();
    }

    @Test
    void removeDeductibleCharges_whenWritten_removesBothFieldsAndNothingElse() {
        // Given
        insertThreeRows();

        // When
        long removed = removal.removeDeductibleCharges(false, "test").rows();

        // Then
        assertThat(removed).isEqualTo(2);
        for (String id : List.of("both", "sell-only", "neither")) {
            Document row = stored(id);
            assertThat(carriesACharge(row)).isFalse();
            assertThat(row.getString("stock_code")).isEqualTo("INFY");
            assertThat(row.getLong("holding_period_days")).isEqualTo(400L);
        }
        assertThat(stored("both").get("audit_metadata", Document.class).getString("updatedBy")).isEqualTo("test");
        assertThat(stored("sell-only").get("audit_metadata", Document.class).getString("updatedBy"))
                .isEqualTo("test");
        assertThat(stored("neither").containsKey("audit_metadata")).isFalse();
    }

    @Test
    void removeDeductibleCharges_whenRunAgain_changesNothing() {
        // Given
        insertThreeRows();
        removal.removeDeductibleCharges(false, "first");

        // When
        long second = removal.removeDeductibleCharges(false, "second").rows();

        // Then — no row matched, so none restamped
        assertThat(second).isZero();
        assertThat(stored("both").get("audit_metadata", Document.class).getString("updatedBy")).isEqualTo("first");
    }

    @Test
    void removeDeductibleCharges_whenARowWasLeftPartWay_finishesIt() {
        // Given — a row a stopped run left with the buy figure but not the sell
        insert("part-way", new Document("deductible_buy_charges", 2.0));
        insert("done", new Document());

        // When
        long removed = removal.removeDeductibleCharges(false, "test").rows();

        // Then
        assertThat(removed).isEqualTo(1);
        assertThat(carriesACharge(stored("part-way"))).isFalse();
    }

    @Test
    void endpoint_whenWritten_removesAndRecordsTheCaller() {
        // Given
        insertThreeRows();

        // When / Then
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "SUPER_USER"))
                .when().post("/trade-outcomes/deductible-charges/remove?dryRun=false")
                .then().statusCode(200).body("dryRun", equalTo(false)).body("rows", equalTo(2));
        assertThat(carriesACharge(stored("both"))).isFalse();
        assertThat(stored("both").get("audit_metadata", Document.class).getString("updatedBy"))
                .isEqualTo("admin@example.com");
    }

    @Test
    void endpoint_whenNotASuperUser_isForbidden() {
        // Given
        insertThreeRows();

        // When / Then
        given().header("Authorization", "Bearer " + generateToken("admin@example.com", "USER"))
                .when().post("/trade-outcomes/deductible-charges/remove?dryRun=false").then().statusCode(403);
        assertThat(stored("both").getDouble("deductible_buy_charges")).isEqualTo(5.0);
    }
}
