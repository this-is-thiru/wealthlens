package com.thiru.wealthlens.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
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
 * A trade naming an exchange its asset type does not list is refused before anything is written, on the JSON route and
 * the Excel upload (W-353, D-826, D-828). The lists are bound from the application's own yaml, so these refusals also
 * prove the binding.
 */
class TradeExchangeRefusalIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "exchange-check@example.com";

    private static final String[] HEADERS = {"EMAIL", "STOCK CODE", "STOCK NAME", "EXCHANGE NAME", "BROKER NAME",
            "ASSET TYPE", "MATURITY DATE", "PRICE", "QUANTITY", "TRANSACTION TYPE", "TRANSACTION DATE",
            "BROKER CHARGES", "MISC CHARGES", "COMMENTS"};

    private static URI uri(String path) {
        return URI.create("http://localhost:" + RestAssured.port + "/api/v1/me/portfolio" + path);
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

    private static AssetRequest equityBuyOn(String exchange) {
        AssetRequest request = new AssetRequest();
        request.setEmail(EMAIL);
        request.setStockCode("RELIANCE");
        request.setStockName("Reliance Industries");
        request.setExchangeName(exchange);
        request.setBrokerName(BrokerName.ZERODHA);
        request.setAssetType(AssetType.EQUITY);
        request.setPrice(2500.0);
        request.setQuantity(10.0);
        request.setTransactionType(TransactionType.BUY);
        request.setAccountType(AccountType.SELF);
        request.setAccountHolder("Self");
        request.setTransactionDate(LocalDate.now());
        request.setBrokerCharges(0.0);
        request.setMiscCharges(0.0);
        request.setTimezoneId("Asia/Kolkata");
        return request;
    }

    private void assertRefusedBeforeAnyWrite(ResponseEntity<String> response, String expected) {
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatusCode().value(), response.getBody());
        assertTrue(response.getBody().contains(expected), response.getBody());
        assertEquals(0, mongoTemplate.count(new Query(Criteria.where("email").is(EMAIL)), TransactionEntity.class,
                "transactions"), "the refusal must come before any write");
    }

    /** The quarter's sheet the upload reads — the parser takes the sheet the request's quarter names. */
    private static String quarterOf(LocalDate date) {
        return "Q" + ((date.getMonthValue() - 1) / 3 + 1);
    }

    /** A workbook of four quarter sheets with one fund buy on `BMF` on {@code quarter}'s, row 1 after the header. */
    private static byte[] workbookWithABmfFundBuy(String quarter) throws IOException {
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
            String[] text = {EMAIL, "AXISBLUECHIP", "Axis Bluechip Fund", "BMF", "ZERODHA", "MUTUAL_FUND"};
            for (int column = 0; column < text.length; column++) {
                row.createCell(column).setCellValue(text[column]);
            }
            // MATURITY DATE: a blank cell — the parser reads the column as a date and maps cells to headers by count
            row.createCell(6);
            row.createCell(7).setCellValue(50.0);
            row.createCell(8).setCellValue(10.0);
            row.createCell(9).setCellValue("BUY");
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-MM-dd"));
            row.createCell(10).setCellValue(java.sql.Date.valueOf(LocalDate.now()));
            row.getCell(10).setCellStyle(dateStyle);
            row.createCell(11).setCellValue(0.0);
            row.createCell(12).setCellValue(0.0);
            row.createCell(13).setCellValue("");
            workbook.write(out);
        }
        return out.toByteArray();
    }

    @Test
    void addTransactionV2_whenAnEquityNamesLowerCaseNse_expected400BeforeAnyWrite() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(EMAIL));
        headers.setContentType(MediaType.APPLICATION_JSON);

        // When
        ResponseEntity<String> response = restTemplate().exchange(uri("/transaction/v2"), HttpMethod.POST,
                new HttpEntity<>(equityBuyOn("nse"), headers), String.class);

        // Then
        assertRefusedBeforeAnyWrite(response, "is not accepted for a trade of type EQUITY; name one of [NSE, BSE]");
    }

    @Test
    void uploadTransactions_whenAFundRowNamesBmf_expected400NamingTheTrade() throws IOException {
        // Given — the fund on the current quarter's sheet, and that quarter requested, so the row is read
        String quarter = quarterOf(LocalDate.now());
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(workbookWithABmfFundBuy(quarter)) {
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

        // Then — the JSON body escapes the quotes around the value, so the value and the rule are matched apart
        assertRefusedBeforeAnyWrite(response, "Trade 1: Exchange ");
        assertRefusedBeforeAnyWrite(response, "BMF");
        assertRefusedBeforeAnyWrite(response,
                "is not accepted for a trade of type MUTUAL_FUND; name one of [NSE, BSE]");
    }
}
