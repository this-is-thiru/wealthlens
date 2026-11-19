package com.thiru.wealthlens.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.taxplanning.profile.TaxpayerProfile;
import io.restassured.RestAssured;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The held lots by term over the real routes and the shipped packs (W-262). Every lot is a share on NSE, so
 * {@code CG-TERM-LISTED-SECURITY} reads it: twelve months, long-term only when held for more than that (D-303). Read on
 * 6 November 2026, in TY 2026-27, which ends 31 March 2027; the application ships no pack for TY 2027-28.
 *
 * <pre>
 * lot  acquired    + 12 months  long-term from  on 2026-11-06  date given
 * A 10 2025-06-01  2026-06-01   2026-06-02      LONG_TERM      2026-06-02
 * B 5  2026-03-01  2027-03-01   2027-03-02      SHORT_TERM     2027-03-02 (TY 2026-27)
 * C 3  2026-05-01  2027-05-01   2027-05-02      SHORT_TERM     none: TY 2027-28 ships no pack
 * D 2  2025-01-01  no exchange recorded, so no row: no term
 * </pre>
 */
class HeldAssetsIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "holder-it@example.com";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private <T> ResponseEntity<T> get(String path, Class<T> as) {
        RestTemplate client = new RestTemplate();
        client.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(OWNER));
        return client.exchange(URI.create("http://localhost:" + RestAssured.port + "/tax/user/" + OWNER + path), HttpMethod.GET,
                new HttpEntity<>(null, headers), as);
    }

    private String profile(boolean holdsPortfolio) {
        RestTemplate client = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(OWNER));
        headers.setContentType(MediaType.APPLICATION_JSON);
        client.exchange(URI.create("http://localhost:" + RestAssured.port + "/tax/user/" + OWNER + "/profiles"), HttpMethod.POST,
                new HttpEntity<>("""
                        {"name": "Self", "pan": "ABCDE1234F", "kind": "INDIVIDUAL", "dateOfBirth": "1985-06-01",
                         "residentialStatus": "RESIDENT", "holdsPortfolio": %s}""".formatted(holdsPortfolio), headers), String.class);
        return mongoTemplate.findAll(TaxpayerProfile.class).getFirst().getId();
    }

    private void lot(String acquiredOn, double quantity, String exchange) {
        AssetEntity lot = new AssetEntity();
        lot.setEmail(OWNER);
        lot.setStockCode("HELD");
        lot.setAssetType(AssetType.EQUITY);
        lot.setExchangeName(exchange);
        lot.setBrokerName(BrokerName.ZERODHA);
        lot.setAccountHolder("Asha");
        lot.setQuantity(quantity);
        lot.setPrice(100);
        lot.setTransactionDate(LocalDate.parse(acquiredOn));
        mongoTemplate.save(lot);
    }

    private String holdings() {
        String id = profile(true);
        lot("2025-06-01", 10, "NSE");
        lot("2026-03-01", 5, "NSE");
        lot("2026-05-01", 3, "NSE");
        lot("2025-01-01", 2, null);
        return id;
    }

    private static JsonNode lotAcquired(JsonNode view, String acquiredOn) {
        for (JsonNode lot : view.get("lots")) {
            if (lot.get("acquiredOn").asString().equals(acquiredOn)) {
                return lot;
            }
        }
        throw new AssertionError("No lot acquired on " + acquiredOn + " in " + view);
    }

    @Test
    void heldAssets_whenNoTermIsAsked_givesEveryLotItsTermDateAndTheStocksTotals() {
        // Given
        String id = holdings();

        // When
        ResponseEntity<String> response = get("/profiles/" + id + "/held-assets?asOf=2026-11-06", String.class);

        // Then
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode view = JSON.readTree(response.getBody());
        assertThat(view.get("asOf").asString()).isEqualTo("2026-11-06");
        assertThat(view.get("lots")).hasSize(4);

        JsonNode a = lotAcquired(view, "2025-06-01");
        assertThat(a.get("term").asString()).isEqualTo("LONG_TERM");
        assertThat(a.get("termRow").asString()).isEqualTo("CG-TERM-LISTED-SECURITY");
        assertThat(a.get("monthsRead").asInt()).isEqualTo(12);
        assertThat(a.get("longTermFrom").asString()).isEqualTo("2026-06-02");
        assertThat(a.get("broker").asString()).isEqualTo("ZERODHA");
        assertThat(a.get("accountHolder").asString()).isEqualTo("Asha");
        assertThat(a.get("quantity").asDouble()).isEqualTo(10.0);

        JsonNode b = lotAcquired(view, "2026-03-01");
        assertThat(b.get("term").asString()).isEqualTo("SHORT_TERM");
        assertThat(b.get("longTermFrom").asString()).isEqualTo("2027-03-02");
        assertThat(b.get("note").isNull()).isTrue();

        JsonNode c = lotAcquired(view, "2026-05-01");
        assertThat(c.get("term").asString()).isEqualTo("SHORT_TERM");
        assertThat(c.get("longTermFrom").isNull()).isTrue();
        assertThat(c.get("note").asString())
                .isEqualTo("TY 2027-28 decides when this lot turns long-term and no rule pack ships for it");

        JsonNode d = lotAcquired(view, "2025-01-01");
        assertThat(d.get("term").isNull()).isTrue();
        assertThat(d.get("note").asString()).contains("RS-CG-TERM");

        // long 10; short 5 + 3 = 8; no term 2
        assertThat(view.get("totals")).hasSize(1);
        JsonNode total = view.get("totals").get(0);
        assertThat(total.get("stockCode").asString()).isEqualTo("HELD");
        assertThat(total.get("longTermQuantity").asDouble()).isEqualTo(10.0);
        assertThat(total.get("shortTermQuantity").asDouble()).isEqualTo(8.0);
        assertThat(total.get("quantityWithoutTerm").asDouble()).isEqualTo(2.0);
    }

    @Test
    void heldAssets_whenShortTermIsAsked_listsOnlyTheShortTermLots() {
        // Given
        String id = holdings();

        // When
        JsonNode view = JSON.readTree(get("/profiles/" + id + "/held-assets?asOf=2026-11-06&term=SHORT_TERM", String.class).getBody());

        // Then — lots B and C
        assertThat(view.get("lots")).hasSize(2);
        assertThat(view.get("lots")).allSatisfy(lot -> assertThat(lot.get("term").asString()).isEqualTo("SHORT_TERM"));
        assertThat(view.get("totals").get(0).get("longTermQuantity").asDouble()).isEqualTo(10.0);
    }

    @Test
    void heldAssets_whenTheTermIsNotOne_expected400ListingTheTerms() {
        // Given
        String id = holdings();

        // When
        ResponseEntity<String> response = get("/profiles/" + id + "/held-assets?term=MEDIUM", String.class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(JSON.readTree(response.getBody()).get("message").asString())
                .isEqualTo("Parameter 'term' must be one of [SHORT_TERM, LONG_TERM], but was 'MEDIUM'");
    }

    @Test
    void heldAssets_whenTheProfileDoesNotHoldThePortfolio_expected400() {
        // Given
        String id = profile(false);

        // When / Then
        assertThat(get("/profiles/" + id + "/held-assets", String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void heldAssetsWorkbook_whenLongTermIsAsked_downloadsItsLotAndTheStocksTotals() throws IOException {
        // Given
        String id = holdings();

        // When
        ResponseEntity<byte[]> response = get("/profiles/" + id + "/held-assets/excel?asOf=2026-11-06&term=LONG_TERM", byte[].class);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=held-assets-2026-11-06.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getBody()))) {
            Sheet lots = workbook.getSheet("LOTS");
            assertThat(lots.getLastRowNum()).isEqualTo(1);
            assertThat(lots.getRow(1).getCell(5).getStringCellValue()).isEqualTo("LONG_TERM");
            assertThat(lots.getRow(1).getCell(8).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 6, 2));
            Sheet totals = workbook.getSheet("TOTALS");
            assertThat(totals.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(10.0);
            assertThat(totals.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(8.0);
            assertThat(totals.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(2.0);
        }
    }
}
