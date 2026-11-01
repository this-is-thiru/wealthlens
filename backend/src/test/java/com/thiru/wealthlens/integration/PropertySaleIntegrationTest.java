package com.thiru.wealthlens.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.portfolio.dto.CapitalGainsBucket;
import com.thiru.wealthlens.portfolio.dto.CapitalGainsSummary;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.service.CapitalGainsSummaryService;
import com.thiru.wealthlens.testsupport.MoneyAssert;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * A property sale over HTTP and into Mongo (W-088g9, D-434): one {@code trade_outcomes} row the capital-gains summary
 * reads with its expenses by code, nothing written for a broker trade, a repeat and a malformed sale refused, the row
 * safe for the other readers of that collection, and cleared with the user's records.
 */
class PropertySaleIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "plotowner@example.com";

    private static final String SALE = """
            {
              "propertyName": "Plot 7",
              "acquiredOn": "2015-06-01",
              "costRupees": 4000000,
              "acquisitionExpenses": {"STAMP_DUTY": 280000, "REGISTRATION": 40000, "BROKERAGE": 40000},
              "transferredOn": "2025-09-01",
              "considerationRupees": 10000000,
              "transferExpenses": {"BROKERAGE": 100000, "LEGAL_FEE": 20000}
            }""";

    @Autowired
    private CapitalGainsSummaryService capitalGainsSummaryService;

    private ValidatableResponse post(String body) {
        return given().auth().oauth2(generateToken(EMAIL)).contentType(ContentType.JSON).body(body)
                .post("/properties/user/" + EMAIL + "/sales").then();
    }

    private List<TradeOutcomeEntity> rows() {
        return mongoTemplate.find(new Query(Criteria.where("email").is(EMAIL)), TradeOutcomeEntity.class);
    }

    @Test
    void recordSale_whenWellFormed_expected200AndOneRowTheSummaryReads() {
        // When
        post(SALE).statusCode(200).body(containsString("Property sale recorded: Plot 7"));

        // Then — one row as stored, read back
        List<TradeOutcomeEntity> rows = rows();
        assertEquals(1, rows.size());
        assertEquals(AssetType.LAND_OR_BUILDING, rows.getFirst().getAssetType());
        assertEquals(LocalDate.of(2025, 9, 1), rows.getFirst().getSellDate());
        assertEquals("2025-2026", rows.getFirst().getFinancialYear());

        // Then — the summary's one bucket carries the facts and both breakdowns; a gain is the tax side's to compute
        CapitalGainsSummary summary = capitalGainsSummaryService.summaryFor(EMAIL, "2025-2026");
        assertEquals(1, summary.buckets().size());
        CapitalGainsBucket bucket = summary.buckets().getFirst();
        assertEquals(AssetType.LAND_OR_BUILDING, bucket.assetType());
        assertEquals(LocalDate.of(2015, 6, 1), bucket.acquiredOn());
        MoneyAssert.assertMoney(10000000, bucket.amounts().considerationRupees());
        MoneyAssert.assertMoney(4000000, bucket.amounts().costRupees());
        MoneyAssert.assertBreakdown(Map.of("STAMP_DUTY", 280000.0, "REGISTRATION", 40000.0, "BROKERAGE", 40000.0),
                bucket.amounts().acquisition().rupeesByCode());
        MoneyAssert.assertBreakdown(Map.of("BROKERAGE", 100000.0, "LEGAL_FEE", 20000.0),
                bucket.amounts().transfer().rupeesByCode());
    }

    @Test
    void recordSale_whenWellFormed_writesNothingForABrokerTrade() {
        // When
        post(SALE).statusCode(200);

        // Then
        Query mine = new Query(Criteria.where("email").is(EMAIL));
        assertEquals(0, mongoTemplate.count(mine, TransactionEntity.class));
        assertEquals(0, mongoTemplate.count(mine, AssetEntity.class));
        assertEquals(0, mongoTemplate.count(mine, "profit_and_loss"));
        assertEquals(0, mongoTemplate.count(mine, "user_charges"));
    }

    @Test
    void recordSale_whenPostedTwice_expected400AndOneRow() {
        // Given
        post(SALE).statusCode(200);

        // When / Then
        post(SALE).statusCode(400).body(containsString("is already recorded"));
        assertEquals(1, rows().size());
    }

    @Test
    void recordSale_whenExpensesAreOmitted_expected200WithEmptyBreakdowns() {
        // When
        post("""
                {"propertyName": "Plot 8", "acquiredOn": "2015-06-01", "costRupees": 4000000,
                 "transferredOn": "2025-09-01", "considerationRupees": 10000000}""").statusCode(200);

        // Then
        assertTrue(rows().getFirst().getBuyChargeBreakup().isEmpty());
        assertTrue(rows().getFirst().getSellChargeBreakup().isEmpty());
    }

    @Test
    void recordSale_whenAnExpenseCodeIsUnknown_expected400AndNothingWritten() {
        // When / Then
        post(SALE.replace("LEGAL_FEE", "PAINTING")).statusCode(400)
                .body(containsString("The expense PAINTING is not one of"));
        assertEquals(0, rows().size());
    }

    @Test
    void recordSale_whenSoldBeforeItWasBought_expected400AndNothingWritten() {
        // When / Then
        post(SALE.replace("2025-09-01", "2010-09-01")).statusCode(400)
                .body(containsString("A property is sold after it is bought"));
        assertEquals(0, rows().size());
    }

    @Test
    void performanceMetrics_whenARowHasNoBrokerOrSegment_expected200() {
        // Given
        post(SALE).statusCode(200);

        // When / Then — W-227 decides whether it should count; here, only that the row breaks no reader
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(EMAIL));
        ResponseEntity<String> response = new RestTemplate().exchange(URI.create("http://localhost:" + RestAssured.port
                + "/analytics/user/" + EMAIL + "/performance-metrics"), HttpMethod.GET, new HttpEntity<>(headers),
                String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void clearAll_whenAPropertySaleIsStored_clearsIt() {
        // Given
        post(SALE).statusCode(200);

        // When
        given().auth().oauth2(generateToken(EMAIL)).post("/portfolio/user/" + EMAIL + "/clear/all").then()
                .statusCode(200);

        // Then
        assertEquals(0, rows().size());
    }
}
