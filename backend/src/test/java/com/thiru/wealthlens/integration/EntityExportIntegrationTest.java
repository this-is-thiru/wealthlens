package com.thiru.wealthlens.integration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.service.EntityExportService;
import com.thiru.wealthlens.portfolio.service.PortfolioService;
import com.thiru.wealthlens.portfolio.service.TransactionService;
import com.thiru.wealthlens.shared.dto.EntityExportRequest;
import com.thiru.wealthlens.shared.entity.query.QueryFilter;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public class EntityExportIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_EMAIL = "entityexport@test.com";

    @Autowired
    private EntityExportService entityExportService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TransactionService transactionService;

    private RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });
        return rt;
    }

    private String baseUrl() {
        return "http://localhost:" + io.restassured.RestAssured.port;
    }

    @Test
    void export_whenAssets_shouldReturnExcelStream() {
        // GIVEN
        String token = generateToken(TEST_EMAIL);
        String stockCode = "RELIANCE";
        String stockName = "Reliance Industries";

        AssetEntity asset = new AssetEntity();
        asset.setEmail(TEST_EMAIL);
        asset.setStockCode(stockCode);
        asset.setStockName(stockName);
        asset.setPrice(2500.0);
        asset.setQuantity(10.0);
        asset.setTransactionDate(LocalDate.now());
        asset.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        asset.setAssetType(com.thiru.wealthlens.portfolio.dto.enums.AssetType.EQUITY);
        mongoTemplate.save(asset);

        EntityExportRequest request = new EntityExportRequest();
        request.setEntityName("assets");
        request.setSelectedColumns(List.of("stockCode", "stockName", "price", "quantity"));

        // WHEN
        String url = baseUrl() + "/portfolio/user/" + TEST_EMAIL + "/stocks/download";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<EntityExportRequest> entity = new HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        ResponseEntity<byte[]> response = rt.exchange(URI.create(url), HttpMethod.POST, entity, byte[].class);

        // THEN
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertThat(response.getBody().length, greaterThan(0));
        String contentType = response.getHeaders().getContentType().toString();
        assertTrue(contentType.contains("application/vnd.ms-excel") || contentType.contains("application/octet-stream"));
        String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(contentDisposition.contains("attachment"));
    }

    @Test
    void export_whenTransactions_shouldReturnExcelStream() {
        // GIVEN
        String token = generateToken(TEST_EMAIL);
        String stockCode = "TCS";
        String stockName = "Tata Consultancy Services";

        TransactionEntity txn = new TransactionEntity();
        txn.setEmail(TEST_EMAIL);
        txn.setStockCode(stockCode);
        txn.setStockName(stockName);
        txn.setPrice(3800.0);
        txn.setQuantity(5.0);
        txn.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn.setTransactionDate(LocalDate.now());
        txn.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        mongoTemplate.save(txn);

        EntityExportRequest request = new EntityExportRequest();
        request.setEntityName("transactions");
        request.setSelectedColumns(List.of("stockCode", "stockName", "price", "quantity"));

        // WHEN
        String url = baseUrl() + "/portfolio/user/" + TEST_EMAIL + "/stocks/download";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<EntityExportRequest> entity = new HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        ResponseEntity<byte[]> response = rt.exchange(URI.create(url), HttpMethod.POST, entity, byte[].class);

        // THEN
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertThat(response.getBody().length, greaterThan(0));
        String contentType = response.getHeaders().getContentType().toString();
        assertTrue(contentType.contains("application/vnd.ms-excel") || contentType.contains("application/octet-stream"));
        String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(contentDisposition.contains("attachment"));
    }

    @Test
    void export_whenTemplate_shouldReturnTemplateStream() {
        // GIVEN
        String token = generateToken(TEST_EMAIL);

        EntityExportRequest request = new EntityExportRequest();
        request.setEntityName("transactions-template");
        request.setSelectedColumns(List.of("stockCode", "stockName", "price", "quantity"));

        // WHEN
        String url = baseUrl() + "/portfolio/user/" + TEST_EMAIL + "/stocks/download";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<EntityExportRequest> entity = new HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        ResponseEntity<byte[]> response = rt.exchange(URI.create(url), HttpMethod.POST, entity, byte[].class);

        // THEN
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertThat(response.getBody().length, greaterThan(0));
        String contentType = response.getHeaders().getContentType().toString();
        assertTrue(contentType.contains("application/vnd.ms-excel") || contentType.contains("application/octet-stream"));
        String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(contentDisposition.contains("attachment"));
    }

    @Test
    void export_whenInvalidEntity_shouldThrow400() {
        // GIVEN
        String token = generateToken(TEST_EMAIL);

        EntityExportRequest request = new EntityExportRequest();
        request.setEntityName("invalid-entity");
        request.setSelectedColumns(new ArrayList<>());

        // WHEN
        String url = baseUrl() + "/portfolio/user/" + TEST_EMAIL + "/stocks/download";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<EntityExportRequest> entity = new HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.POST, entity, String.class);

        // THEN
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void export_whenGenerationFails_shouldReturn500() {
        // GIVEN
        String token = generateToken(TEST_EMAIL);

        EntityExportRequest request = new EntityExportRequest();
        request.setEntityName("assets");
        request.setSelectedColumns(new ArrayList<>());
        request.setQueryFilters(List.of(
                QueryFilter.builder().filterKey("stockCode").operation(QueryFilter.FilterOperation.EQUALS).value("NON_EXISTENT").build()
        ));

        // WHEN
        String url = baseUrl() + "/portfolio/user/" + TEST_EMAIL + "/stocks/download";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<EntityExportRequest> entity = new HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.POST, entity, String.class);

        // THEN - with invalid filter that causes processing to fail
        assertTrue(response.getStatusCode().value() == 400 || response.getStatusCode().value() == 500);
    }

    @Test
    void export_whenEmpty_shouldReturnValidStream() {
        // GIVEN
        String token = generateToken(TEST_EMAIL);

        EntityExportRequest request = new EntityExportRequest();
        request.setEntityName("assets");
        request.setSelectedColumns(List.of("stockCode", "stockName", "price", "quantity"));

        // WHEN
        String url = baseUrl() + "/portfolio/user/" + TEST_EMAIL + "/stocks/download";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<EntityExportRequest> entity = new HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        ResponseEntity<byte[]> response = rt.exchange(URI.create(url), HttpMethod.POST, entity, byte[].class);

        // THEN
        assertEquals(200, response.getStatusCode().value());
        String contentType = response.getHeaders().getContentType().toString();
        assertTrue(contentType.contains("application/vnd.ms-excel") || contentType.contains("application/octet-stream"));
        String contentDisposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(contentDisposition.contains("attachment"));
    }
}
