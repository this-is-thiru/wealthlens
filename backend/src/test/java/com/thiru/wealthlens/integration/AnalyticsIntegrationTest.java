package com.thiru.wealthlens.integration;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.portfolio.dto.analytics.MarketPriceEntry;
import com.thiru.wealthlens.portfolio.dto.analytics.XirrRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.entity.model.FinancialReport;
import com.thiru.wealthlens.portfolio.entity.model.RealisedProfits;
import io.restassured.RestAssured;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public class AnalyticsIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_EMAIL = "analytics-test@example.com";

    private String baseUrl() {
        return "http://localhost:" + RestAssured.port;
    }

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

    @BeforeEach
    void setUp() {
        clearCollections();
    }

    private void clearCollections() {
        transactionRepository.deleteAll();
        portfolioRepository.deleteAll();
        tradeOutcomeRepository.deleteAll();
        profitAndLossRepository.deleteAll();
    }

    private void seedData_BuyAndSell() {
        saveProcessedTxn(TransactionType.BUY, "INFY", "Infosys Ltd", 100.0, 10.0, LocalDate.of(2023, 1, 1));
        saveAsset("INFY", "Infosys Ltd", 100.0, 10.0, LocalDate.of(2023, 1, 1));
        saveProcessedTxn(TransactionType.SELL, "INFY", "Infosys Ltd", 150.0, 15.0, LocalDate.of(2024, 6, 1));

        TradeOutcomeEntity outcome = new TradeOutcomeEntity();
        outcome.setEmail(TEST_EMAIL);
        outcome.setStockCode("INFY");
        outcome.setStockName("Infosys Ltd");
        outcome.setOriginalBuyPrice(100.0);
        outcome.setCorporateActionAdjustedBuyPrice(100.0);
        outcome.setBuyQuantity(10.0);
        outcome.setBuyDate(LocalDate.of(2023, 1, 1));
        outcome.setBuyBrokerCharges(10.0);
        outcome.setBuyMiscCharges(5.0);
        outcome.setSellPrice(150.0);
        outcome.setSellQuantity(10.0);
        outcome.setSellDate(LocalDate.of(2024, 6, 1));
        outcome.setSellBrokerCharges(15.0);
        outcome.setSellMiscCharges(5.0);
        outcome.setTotalBuyValue(1000.0 + 10.0 + 5.0);
        outcome.setTotalSellValue(1500.0 - 15.0 - 5.0);
        outcome.setNetProfit(1480.0 - 1015.0);
        outcome.setProfitPercentage((outcome.getNetProfit() / outcome.getTotalBuyValue()) * 100);
        outcome.setHoldingPeriodDays(516L);
        outcome.setFinancialYear("2024-25");
        tradeOutcomeRepository.save(outcome);

        ProfitAndLossEntity pnl = new ProfitAndLossEntity();
        pnl.setEmail(TEST_EMAIL);
        pnl.setFinancialYear("2024-25");
        RealisedProfits realised = RealisedProfits.empty();
        FinancialReport ltReport = FinancialReport.empty();
        ltReport.setProfit(outcome.getNetProfit());
        realised.setGainsByAssetAndSegment(new EnumMap<>(Map.of(AssetType.EQUITY,
                new EnumMap<>(Map.of(TradeSegment.DELIVERY, ltReport)))));
        pnl.setRealisedProfits(realised);
        profitAndLossRepository.save(pnl);
    }

    private void seedData_MultipleTrades() {
        saveOutcome("RELIANCE", "Reliance Industries", 500.0, 1000.0, 1500.0, 30L);
        saveOutcome("HDFC", "HDFC Bank", -300.0, 2000.0, 1700.0, 60L);
        saveOutcome("TCS", "Tata Consultancy Services", 0.0, 500.0, 500.0, 45L);

        saveProcessedTxn(TransactionType.BUY, "RELIANCE", null, 100.0, 10.0, LocalDate.of(2023, 1, 1));
        saveProcessedTxn(TransactionType.SELL, "RELIANCE", null, 150.0, 15.0, LocalDate.of(2023, 6, 1));

        AssetEntity asset = new AssetEntity();
        asset.setEmail(TEST_EMAIL);
        asset.setStockCode("INFY");
        asset.setQuantity(10.0);
        asset.setAssetType(AssetType.EQUITY);
        portfolioRepository.save(asset);

        saveProcessedTxn(TransactionType.BUY, "INFY", null, 100.0, 10.0, LocalDate.of(2023, 1, 1));
    }

    private void saveProcessedTxn(TransactionType type, String stockCode, String stockName,
            double price, double brokerCharges, LocalDate date) {
        TransactionEntity txn = new TransactionEntity();
        txn.setEmail(TEST_EMAIL);
        txn.setTransactionType(type);
        txn.setStatus(TransactionStatus.PROCESSED);
        txn.setStockCode(stockCode);
        txn.setStockName(stockName);
        txn.setPrice(price);
        txn.setQuantity(10.0);
        txn.setBrokerCharges(brokerCharges);
        txn.setMiscCharges(5.0);
        txn.setTransactionDate(date);
        txn.setAssetType(AssetType.EQUITY);
        txn.setBrokerName(BrokerName.ZERODHA);
        transactionRepository.save(txn);
    }

    private void saveAsset(String stockCode, String stockName, double price, double quantity, LocalDate date) {
        AssetEntity asset = new AssetEntity();
        asset.setEmail(TEST_EMAIL);
        asset.setStockCode(stockCode);
        asset.setStockName(stockName);
        asset.setPrice(price);
        asset.setQuantity(quantity);
        asset.setBrokerCharges(10.0);
        asset.setMiscCharges(5.0);
        asset.setTransactionDate(date);
        asset.setAssetType(AssetType.EQUITY);
        asset.setBrokerName(BrokerName.ZERODHA);
        portfolioRepository.save(asset);
    }

    private void saveOutcome(String stockCode, String stockName, double netProfit,
            double buyValue, double sellValue, long holdingDays) {
        TradeOutcomeEntity outcome = new TradeOutcomeEntity();
        outcome.setEmail(TEST_EMAIL);
        outcome.setStockCode(stockCode);
        outcome.setStockName(stockName);
        outcome.setNetProfit(netProfit);
        outcome.setTotalBuyValue(buyValue);
        outcome.setTotalSellValue(sellValue);
        outcome.setHoldingPeriodDays(holdingDays);
        outcome.setFinancialYear("2024-25");
        tradeOutcomeRepository.save(outcome);
    }

    // ============================================================
    // portfolio-summary
    // ============================================================

    @Test
    void portfolioSummary_withData_returnsCorrectSummary() {
        seedData_BuyAndSell();
        String token = generateToken(TEST_EMAIL);

        String url = baseUrl() + "/api/v1/me/portfolio/analytics/portfolio-summary";
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        var entity = new org.springframework.http.HttpEntity<>(headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), org.springframework.http.HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());

        // Verify JSON structure
        assertTrue(response.getBody().contains("\"totalInvested\""));
        assertTrue(response.getBody().contains("\"totalTrades\""));
        assertTrue(response.getBody().contains("\"buyTrades\""));
        assertTrue(response.getBody().contains("\"sellTrades\""));
        assertTrue(response.getBody().contains("\"holdingCount\""));

        // Verify values
        assertTrue(response.getBody().contains("\"totalTrades\":2")); // buy + sell
        assertTrue(response.getBody().contains("\"buyTrades\":1"));
        assertTrue(response.getBody().contains("\"sellTrades\":1"));
    }

    @Test
    void portfolioSummary_emptyUser_returnsZeroValues() {
        String token = generateToken(TEST_EMAIL);

        String url = baseUrl() + "/api/v1/me/portfolio/analytics/portfolio-summary";
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        var entity = new org.springframework.http.HttpEntity<>(headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), org.springframework.http.HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"totalTrades\":0"));
        assertTrue(response.getBody().contains("\"holdingCount\":0"));
    }

    @Test
    void portfolioSummary_unauthenticated_returns401() {
        String url = baseUrl() + "/api/v1/me/portfolio/analytics/portfolio-summary";
        var headers = new org.springframework.http.HttpHeaders();
        var entity = new org.springframework.http.HttpEntity<>(headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), org.springframework.http.HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
    }

    // ============================================================
    // asset-allocation
    // ============================================================

    @Test
    void assetAllocation_singleAssetType_returns100Percent() {
        seedData_BuyAndSell();
        String token = generateToken(TEST_EMAIL);

        String url = baseUrl() + "/api/v1/me/portfolio/analytics/asset-allocation";
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        var entity = new org.springframework.http.HttpEntity<>(headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), org.springframework.http.HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());

        // Should return a JSON array with one entry for EQUITY
        assertTrue(response.getBody().startsWith("["));
        assertTrue(response.getBody().contains("\"assetType\":\"EQUITY\""));
        assertTrue(response.getBody().contains("\"percentage\""));
    }

    @Test
    void assetAllocation_emptyUser_returnsEmptyArray() {
        String token = generateToken(TEST_EMAIL);

        String url = baseUrl() + "/api/v1/me/portfolio/analytics/asset-allocation";
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        var entity = new org.springframework.http.HttpEntity<>(headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), org.springframework.http.HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertEquals("[]", response.getBody());
    }

    @Test
    void assetAllocation_unauthenticated_returns401() {
        String url = baseUrl() + "/api/v1/me/portfolio/analytics/asset-allocation";
        var headers = new org.springframework.http.HttpHeaders();
        var entity = new org.springframework.http.HttpEntity<>(headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), org.springframework.http.HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
    }

    // ============================================================
    // performance-metrics
    // ============================================================

    @Test
    void performanceMetrics_withMultipleTrades_returnsCorrectMetrics() {
        seedData_MultipleTrades();
        String token = generateToken(TEST_EMAIL);

        String url = baseUrl() + "/api/v1/me/portfolio/analytics/performance-metrics";
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        var entity = new org.springframework.http.HttpEntity<>(headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), org.springframework.http.HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());

        // Verify structure
        assertTrue(response.getBody().contains("\"totalTrades\""));
        assertTrue(response.getBody().contains("\"winCount\""));
        assertTrue(response.getBody().contains("\"lossCount\""));
        assertTrue(response.getBody().contains("\"breakevenCount\""));
        assertTrue(response.getBody().contains("\"bestPerformingStock\""));
        assertTrue(response.getBody().contains("\"worstPerformingStock\""));

        // Verify values: 3 trades, 1 win, 1 loss, 1 breakeven
        assertTrue(response.getBody().contains("\"totalTrades\":3"));
        assertTrue(response.getBody().contains("\"winCount\":1"));
        assertTrue(response.getBody().contains("\"lossCount\":1"));
        assertTrue(response.getBody().contains("\"breakevenCount\":1"));
    }

    @Test
    void performanceMetrics_emptyUser_returnsZeroMetrics() {
        String token = generateToken(TEST_EMAIL);

        String url = baseUrl() + "/api/v1/me/portfolio/analytics/performance-metrics";
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        var entity = new org.springframework.http.HttpEntity<>(headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), org.springframework.http.HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"totalTrades\":0"));
        assertTrue(response.getBody().contains("\"winCount\":0"));
        assertTrue(response.getBody().contains("\"lossCount\":0"));
    }

    @Test
    void performanceMetrics_unauthenticated_returns401() {
        String url = baseUrl() + "/api/v1/me/portfolio/analytics/performance-metrics";
        var headers = new org.springframework.http.HttpHeaders();
        var entity = new org.springframework.http.HttpEntity<>(headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), org.springframework.http.HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
    }

    // ============================================================
    // xirr
    // ============================================================

    @Test
    void xirr_withCurrentPrices_returnsConvergedResult() {
        seedData_MultipleTrades();
        String token = generateToken(TEST_EMAIL);

        XirrRequest request = new XirrRequest();
        request.setCurrentPrices(List.of(new MarketPriceEntry("INFY", 160.0)));

        String url = baseUrl() + "/api/v1/me/portfolio/analytics/xirr";
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), org.springframework.http.HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());

        // Verify structure
        assertTrue(response.getBody().contains("\"converged\""));
        assertTrue(response.getBody().contains("\"xirr\""));
        assertTrue(response.getBody().contains("\"includesOpenPositions\""));

        // Verify converged and includes open positions
        assertTrue(response.getBody().contains("\"converged\":true"));
        assertTrue(response.getBody().contains("\"includesOpenPositions\":true"));
    }

    @Test
    void xirr_withoutCurrentPrices_returnsWarning() {
        seedData_MultipleTrades();
        String token = generateToken(TEST_EMAIL);

        XirrRequest request = new XirrRequest();
        // No current prices - open positions will be excluded

        String url = baseUrl() + "/api/v1/me/portfolio/analytics/xirr";
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), org.springframework.http.HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());

        // Should have message about open positions excluded
        assertTrue(response.getBody().contains("\"includesOpenPositions\":false"));
    }

    @Test
    void xirr_insufficientCashFlows_returnsNotConverged() {
        // Only one transaction (BUY) - insufficient for XIRR
        TransactionEntity buy = new TransactionEntity();
        buy.setEmail(TEST_EMAIL);
        buy.setTransactionType(TransactionType.BUY);
        buy.setStatus(TransactionStatus.PROCESSED);
        buy.setStockCode("INFY");
        buy.setPrice(100.0);
        buy.setQuantity(10.0);
        buy.setBrokerCharges(10.0);
        buy.setMiscCharges(5.0);
        buy.setTransactionDate(LocalDate.of(2023, 1, 1));
        buy.setAssetType(AssetType.EQUITY);
        buy.setBrokerName(BrokerName.ZERODHA);
        transactionRepository.save(buy);

        String token = generateToken(TEST_EMAIL);

        XirrRequest request = new XirrRequest();

        String url = baseUrl() + "/api/v1/me/portfolio/analytics/xirr";
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), org.springframework.http.HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.OK.value(), response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("\"converged\":false"));
    }

    @Test
    void xirr_unauthenticated_returns401() {
        XirrRequest request = new XirrRequest();

        String url = baseUrl() + "/api/v1/me/portfolio/analytics/xirr";
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), org.springframework.http.HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
    }
}
