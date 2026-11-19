package com.thiru.wealthlens.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import io.restassured.RestAssured;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * A land or building refused on the three broker trade paths — V1, V2 and the Excel upload — before anything is written
 * (W-088g5, D-433): no rate card prices a plot, a V2 buy's entered charges would be replaced by the engine's nothing,
 * and the capital-gains summary reads only coded charges, so a plot's purchase expenses could never reach its cost.
 */
class LandTradeRefusalIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "landowner@example.com";

    private static final String[] HEADERS = {"EMAIL", "STOCK CODE", "STOCK NAME", "EXCHANGE NAME", "BROKER NAME",
            "ASSET TYPE", "MATURITY DATE", "PRICE", "QUANTITY", "TRANSACTION TYPE", "TRANSACTION DATE",
            "BROKER CHARGES", "MISC CHARGES", "COMMENTS"};

    private static URI uri(String path) {
        return URI.create("http://localhost:" + RestAssured.port + "/portfolio/user/" + EMAIL + path);
    }

    private static RestTemplate restTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return rt;
    }

    /** A buy of one plot for 40,00,000 with 2,40,000 of entered charges, otherwise a well-formed trade. */
    private static AssetRequest plotBuy() {
        AssetRequest request = new AssetRequest();
        request.setEmail(EMAIL);
        request.setStockCode("PLOT-1");
        request.setStockName("A plot");
        request.setExchangeName("NSE");
        request.setBrokerName(BrokerName.ZERODHA);
        request.setAssetType(AssetType.LAND_OR_BUILDING);
        request.setPrice(4000000.0);
        request.setQuantity(1.0);
        request.setTransactionType(TransactionType.BUY);
        request.setAccountType(AccountType.SELF);
        request.setAccountHolder("Self");
        request.setTransactionDate(LocalDate.now());
        request.setBrokerCharges(240000.0);
        request.setMiscCharges(0.0);
        request.setTimezoneId("Asia/Kolkata");
        return request;
    }

    private long stored(Class<?> type, String collection) {
        return mongoTemplate.count(new Query(Criteria.where("email").is(EMAIL)), type, collection);
    }

    private void assertRefusedBeforeAnyWrite(ResponseEntity<String> response, String expected) {
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value(), response.getBody());
        assertTrue(response.getBody().contains(expected), response.getBody());
        assertEquals(0, stored(TransactionEntity.class, "transactions"), "the refusal must come before any write");
        assertEquals(0, stored(AssetEntity.class, "assets"), "no holding may be written");
    }

    private ResponseEntity<String> postJson(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(EMAIL));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate().exchange(uri(path), HttpMethod.POST, new HttpEntity<>(plotBuy(), headers),
                String.class);
    }

    /** The quarter's sheet the upload reads — the parser takes the sheet the request's quarter names. */
    private static String quarterOf(LocalDate date) {
        return "Q" + ((date.getMonthValue() - 1) / 3 + 1);
    }

    /** A workbook of four quarter sheets with one plot buy on {@code quarter}'s, row 1 after the header. */
    private static byte[] workbookWithAPlot(String quarter) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Workbook workbook = new XSSFWorkbook()) {
            for (String sheet : new String[] {"Q1", "Q2", "Q3", "Q4"}) {
                workbook.createSheet(sheet);
            }
            Sheet sheet = workbook.getSheet(quarter);
            Row header = sheet.createRow(0);
            for (int column = 0; column < HEADERS.length; column++) {
                header.createCell(column).setCellValue(HEADERS[column]);
            }
            Row row = sheet.createRow(1);
            String[] text = {EMAIL, "PLOT-1", "A plot", "NSE", "ZERODHA", "LAND_OR_BUILDING"};
            for (int column = 0; column < text.length; column++) {
                row.createCell(column).setCellValue(text[column]);
            }
            // MATURITY DATE: a blank cell — the parser reads the column as a date and maps cells to headers by count
            row.createCell(6);
            row.createCell(7).setCellValue(4000000.0);
            row.createCell(8).setCellValue(1.0);
            row.createCell(9).setCellValue("BUY");
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-MM-dd"));
            row.createCell(10).setCellValue(java.sql.Date.valueOf(LocalDate.now()));
            row.getCell(10).setCellStyle(dateStyle);
            row.createCell(11).setCellValue(240000.0);
            row.createCell(12).setCellValue(0.0);
            row.createCell(13).setCellValue("");
            workbook.write(out);
        }
        return out.toByteArray();
    }

    @Test
    void addTransaction_landOrBuildingBuy_expected400BeforeAnyWrite() {
        // Given / When — V1
        ResponseEntity<String> response = postJson("/transaction");

        // Then
        assertRefusedBeforeAnyWrite(response, AssetType.NOT_A_BROKER_TRADE);
    }

    @Test
    void addTransactionV2_landOrBuildingBuy_expected400BeforeAnyWrite() {
        // Given / When — V2, where the charge engine would otherwise replace the 2,40,000 entered with nil
        ResponseEntity<String> response = postJson("/transaction/v2");

        // Then
        assertRefusedBeforeAnyWrite(response, AssetType.NOT_A_BROKER_TRADE);
    }

    @Test
    void uploadTransactions_landOrBuildingRow_expected400NamingTheRow() throws IOException {
        // Given — the plot on the current quarter's sheet, and that quarter requested, so the row is read
        String quarter = quarterOf(LocalDate.now());
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(workbookWithAPlot(quarter)) {
            @Override
            public String getFilename() {
                return "transactions.xlsx";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(EMAIL));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // When
        ResponseEntity<String> response = restTemplate().exchange(uri("/upload-transactions?quarter=" + quarter),
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        // Then
        assertRefusedBeforeAnyWrite(response, "Row 1: " + AssetType.NOT_A_BROKER_TRADE);
    }
}
