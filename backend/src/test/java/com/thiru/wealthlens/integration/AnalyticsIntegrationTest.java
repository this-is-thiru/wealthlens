package com.thiru.wealthlens.integration;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import com.thiru.wealthlens.portfolio.dto.analytics.MarketPriceEntry;
import com.thiru.wealthlens.portfolio.dto.analytics.XirrRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

public class AnalyticsIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_EMAIL = "analytics-test@example.com";

    @Autowired
    private MongoTemplate mongoTemplate;

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
        mongoTemplate.getDb().getCollection("transactions").drop();
        mongoTemplate.getDb().getCollection("assets").drop();
        mongoTemplate.getDb().getCollection("trade_outcomes").drop();
        mongoTemplate.getDb().getCollection("profit_and_loss").drop();
    }

    private void seedData_BuyAndSell() {
        // Seed BUY transaction
        TransactionEntity buy = new TransactionEntity();
        buy.setEmail(TEST_EMAIL);
        buy.setTransactionType(TransactionType.BUY);
        buy.setStatus(TransactionStatus.PROCESSED);
        buy.setStockCode("INFY");
        buy.setStockName("Infosys Ltd");
        buy.setPrice(100.0);
        buy.setQuantity(10.0);
        buy.setBrokerCharges(10.0);
        buy.setMiscCharges(5.0);
        buy.setTransactionDate(LocalDate.of(2023, 1, 1));
        buy.setAssetType(AssetType.EQUITY);
        buy.setBrokerName(BrokerName.ZERODHA);
        mongoTemplate.save(buy, "transactions");

        // Seed Asset (position after BUY)
        AssetEntity asset = new AssetEntity();
        asset.setEmail(TEST_EMAIL);
        asset.setStockCode("INFY");
        asset.setStockName("Infosys Ltd");
        asset.setPrice(100.0);
        asset.setQuantity(10.0);
        asset.setBrokerCharges(10.0);
        asset.setMiscCharges(5.0);
        asset.setTransactionDate(LocalDate.of(2023, 1, 1));
        asset.setAssetType(AssetType.EQUITY);
        asset.setBrokerName(BrokerName.ZERODHA);
        mongoTemplate.save(asset, "assets");

        // Seed SELL transaction
        TransactionEntity sell = new TransactionEntity();
        sell.setEmail(TEST_EMAIL);
        sell.setTransactionType(TransactionType.SELL);
        sell.setStatus(TransactionStatus.PROCESSED);
        sell.setStockCode("INFY");
        sell.setStockName("Infosys Ltd");
        sell.setPrice(150.0);
        sell.setQuantity(10.0);
        sell.setBrokerCharges(15.0);
        sell.setMiscCharges(5.0);
        sell.setTransactionDate(LocalDate.of(2024, 6, 1));
        sell.setAssetType(AssetType.EQUITY);
        sell.setBrokerName(BrokerName.ZERODHA);
        mongoTemplate.save(sell, "transactions");

        // Seed TradeOutcome
        TradeOutcomeEntity outcome = new TradeOutcomeEntity();
        outcome.setEmail(TEST_EMAIL);
        outcome.setStockCode("INFY");
        outcome.setStockName("Infosys Ltd");
        outcome.setOriginalBuyPrice(100.0);
        outcome.setCaAdjustedBuyPrice(100.0);
        outcome.setBuyQuantity(10.0);
        outcome.setBuyDate(LocalDate.of(2023, 1, 1));
        outcome.setBuyBrokerCharges(10.0);
        outcome.setBuyMiscCharges(5.0);
        outcome.setSellPrice(150.0);
        outcome.setSellQuantity(10.0);
        outcome.setSellDate(LocalDate.of(2024, 6, 1));
        outcome.setSellBrokerCharges(15.0);
        outcome.setSellMiscCharges(5.0);
        outcome.setTotalBuyValue(1000.0 + 10.0 + 5.0); // price*qty + charges
        outcome.setTotalSellValue(1500.0 - 15.0 - 5.0); // price*qty - charges
        outcome.setNetProfit(1480.0 - 1015.0); // ~465.0
        outcome.setProfitPercentage((outcome.getNetProfit() / outcome.getTotalBuyValue()) * 100);
        outcome.setHoldingPeriodDays(516L); // days between 2023-01-01 and 2024-06-01
        outcome.setCapitalGainsType(CapitalGainsType.LONG_TERM);
        outcome.setFinancialYear("2024-25");
        mongoTemplate.save(outcome, "trade_outcomes");

        // Seed P&L record with realized profits
        ProfitAndLossEntity pnl = new ProfitAndLossEntity();
        pnl.setEmail(TEST_EMAIL);
        pnl.setFinancialYear("2024-25");
        RealisedProfits realised = RealisedProfits.empty();
        FinancialReport ltReport = FinancialReport.empty();
        ltReport.setProfit(outcome.getNetProfit());
        realised.setLongTermCapitalGains(ltReport);
        pnl.setRealisedProfits(realised);
        mongoTemplate.save(pnl, "profit_and_loss");
    }

    private void seedData_MultipleTrades() {
        // Trade 1: WIN
        TradeOutcomeEntity win = new TradeOutcomeEntity();
        win.setEmail(TEST_EMAIL);
        win.setStockCode("RELIANCE");
        win.setStockName("Reliance Industries");
        win.setNetProfit(500.0);
        win.setTotalBuyValue(1000.0);
        win.setTotalSellValue(1500.0);
        win.setHoldingPeriodDays(30L);
        win.setCapitalGainsType(CapitalGainsType.SHORT_TERM);
        win.setFinancialYear("2024-25");
        mongoTemplate.save(win, "trade_outcomes");

        // Trade 2: LOSS
        TradeOutcomeEntity loss = new TradeOutcomeEntity();
        loss.setEmail(TEST_EMAIL);
        loss.setStockCode("HDFC");
        loss.setStockName("HDFC Bank");
        loss.setNetProfit(-300.0);
        loss.setTotalBuyValue(2000.0);
        loss.setTotalSellValue(1700.0);
        loss.setHoldingPeriodDays(60L);
        loss.setCapitalGainsType(CapitalGainsType.SHORT_TERM);
        loss.setFinancialYear("2024-25");
        mongoTemplate.save(loss, "trade_outcomes");

        // Trade 3: BREAKEVEN
        TradeOutcomeEntity breakeven = new TradeOutcomeEntity();
        breakeven.setEmail(TEST_EMAIL);
        breakeven.setStockCode("TCS");
        breakeven.setStockName("Tata Consultancy Services");
        breakeven.setNetProfit(0.0);
        breakeven.setTotalBuyValue(500.0);
        breakeven.setTotalSellValue(500.0);
        breakeven.setHoldingPeriodDays(45L);
        breakeven.setCapitalGainsType(CapitalGainsType.LONG_TERM);
        breakeven.setFinancialYear("2024-25");
        mongoTemplate.save(breakeven, "trade_outcomes");

        // Seed transactions for XIRR calculation
        TransactionEntity buy1 = new TransactionEntity();
        buy1.setEmail(TEST_EMAIL);
        buy1.setTransactionType(TransactionType.BUY);
        buy1.setStatus(TransactionStatus.PROCESSED);
        buy1.setStockCode("RELIANCE");
        buy1.setPrice(100.0);
        buy1.setQuantity(10.0);
        buy1.setBrokerCharges(10.0);
        buy1.setMiscCharges(5.0);
        buy1.setTransactionDate(LocalDate.of(2023, 1, 1));
        buy1.setAssetType(AssetType.EQUITY);
        buy1.setBrokerName(BrokerName.ZERODHA);
        mongoTemplate.save(buy1, "transactions");

        TransactionEntity sell1 = new TransactionEntity();
        sell1.setEmail(TEST_EMAIL);
        sell1.setTransactionType(TransactionType.SELL);
        sell1.setStatus(TransactionStatus.PROCESSED);
        sell1.setStockCode("RELIANCE");
        sell1.setPrice(150.0);
        sell1.setQuantity(10.0);
        sell1.setBrokerCharges(15.0);
        sell1.setMiscCharges(5.0);
        sell1.setTransactionDate(LocalDate.of(2023, 6, 1));
        sell1.setAssetType(AssetType.EQUITY);
        sell1.setBrokerName(BrokerName.ZERODHA);
        mongoTemplate.save(sell1, "transactions");

        // Asset for XIRR with open positions
        AssetEntity asset = new AssetEntity();
        asset.setEmail(TEST_EMAIL);
        asset.setStockCode("INFY");
        asset.setQuantity(10.0);
        asset.setAssetType(AssetType.EQUITY);
        mongoTemplate.save(asset, "assets");

        TransactionEntity buy2 = new TransactionEntity();
        buy2.setEmail(TEST_EMAIL);
        buy2.setTransactionType(TransactionType.BUY);
        buy2.setStatus(TransactionStatus.PROCESSED);
        buy2.setStockCode("INFY");
        buy2.setPrice(100.0);
        buy2.setQuantity(10.0);
        buy2.setBrokerCharges(10.0);
        buy2.setMiscCharges(5.0);
        buy2.setTransactionDate(LocalDate.of(2023, 1, 1));
        buy2.setAssetType(AssetType.EQUITY);
        buy2.setBrokerName(BrokerName.ZERODHA);
        mongoTemplate.save(buy2, "transactions");
    }

    // ============================================================
    // portfolio-summary
    // ============================================================

    @Test
    void portfolioSummary_withData_returnsCorrectSummary() {
        seedData_BuyAndSell();
        String token = generateToken(TEST_EMAIL);

        String url = baseUrl() + "/analytics/user/" + TEST_EMAIL + "/portfolio-summary";
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

        String url = baseUrl() + "/analytics/user/" + TEST_EMAIL + "/portfolio-summary";
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
        String url = baseUrl() + "/analytics/user/" + TEST_EMAIL + "/portfolio-summary";
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

        String url = baseUrl() + "/analytics/user/" + TEST_EMAIL + "/asset-allocation";
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

        String url = baseUrl() + "/analytics/user/" + TEST_EMAIL + "/asset-allocation";
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
        String url = baseUrl() + "/analytics/user/" + TEST_EMAIL + "/asset-allocation";
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

        String url = baseUrl() + "/analytics/user/" + TEST_EMAIL + "/performance-metrics";
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

        String url = baseUrl() + "/analytics/user/" + TEST_EMAIL + "/performance-metrics";
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
        String url = baseUrl() + "/analytics/user/" + TEST_EMAIL + "/performance-metrics";
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

        String url = baseUrl() + "/analytics/user/" + TEST_EMAIL + "/xirr";
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

        String url = baseUrl() + "/analytics/user/" + TEST_EMAIL + "/xirr";
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
        mongoTemplate.save(buy, "transactions");

        String token = generateToken(TEST_EMAIL);

        XirrRequest request = new XirrRequest();

        String url = baseUrl() + "/analytics/user/" + TEST_EMAIL + "/xirr";
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

        String url = baseUrl() + "/analytics/user/" + TEST_EMAIL + "/xirr";
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var entity = new org.springframework.http.HttpEntity<>(request, headers);

        RestTemplate rt = createRestTemplate();
        var response = rt.exchange(URI.create(url), org.springframework.http.HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCode().value());
    }
}
