package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/**
 * The one-time corrections that have run on every database are no longer served (Phase 15, D-286): each was written to put data
 * written by older code right once, and calling one again could only rewrite rows already correct. Each route answers an admin as
 * any route the app does not serve does.
 */
class RetiredOneTimeRoutesIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN = "retired-one-time@example.com";

    @Test
    void rebuildChargeCorrection_whenAnAdminPosts_answers404() {
        // Given / When / Then — W-147, runbook §2.1: the rebuild's double-counted buy charges, corrected on prod (W-334e2) and staging (W-334d3a)
        refuses("/api/v1/admin/portfolio/trade-outcomes/rebuild-charges/correct");
    }

    @Test
    void financialYearRewrite_whenAnAdminPosts_answers404() {
        // Given / When / Then — W-149, runbook §2.3: the FY-form financial years, rewritten on prod (W-334e2, 163) and staging (W-334d3a, 56)
        refuses("/api/v1/admin/portfolio/trade-outcomes/financial-years/rewrite");
    }

    @Test
    void bonusAllotmentDateCorrection_whenAnAdminPosts_answers404() {
        // Given / When / Then — W-150, runbook §2.4: the bonus lots dated from their allotment on prod (W-334e2) and staging (W-334d3a)
        refuses("/api/v1/admin/portfolio/corporate-actions/bonus/allotment-dates/correct");
    }

    @Test
    void storedTermRemoval_whenAnAdminPosts_answers404() {
        // Given / When / Then — W-168, runbook §2.5: the stored term removed on prod (W-334e2, 163) and staging (W-334d3a, 201)
        refuses("/api/v1/admin/portfolio/trade-outcomes/stored-terms/remove");
    }

    @Test
    void profitAndLossSplitReshape_whenAnAdminPosts_answers404() {
        // Given / When / Then — W-170, runbook §2.6: the stored short / long pair moved into its split on prod (W-334e2) and staging (W-334d3a), 0 sides unplaced on either
        refuses("/api/v1/admin/portfolio/profit-and-loss/split/reshape");
    }

    @Test
    void deductibleChargeRemoval_whenAnAdminPosts_answers404() {
        // Given / When / Then — W-174, runbook §2.9: the deductible charges removed from trade_outcomes on staging (W-334d3a, 1, after 0); prod had none (W-334e2)
        refuses("/api/v1/admin/portfolio/trade-outcomes/deductible-charges/remove");
    }

    private void refuses(String route) {
        given().header("Authorization", "Bearer " + generateToken(ADMIN, "SUPER_USER")).contentType(ContentType.JSON).body("{}")
                .when().post(route)
                .then().statusCode(404);
    }
}
