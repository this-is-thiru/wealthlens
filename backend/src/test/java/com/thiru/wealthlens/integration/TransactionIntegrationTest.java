package com.thiru.wealthlens.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.portfolio.service.TransactionService;
import com.thiru.wealthlens.shared.dto.BulkGetRequest;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import io.restassured.RestAssured;
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

public class TransactionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    private String baseUrl() {
        return "http://localhost:" + RestAssured.port;
    }

    private RestTemplate createNoErrorRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });
        return rt;
    }

    @Test
    void addTransaction_nullDate_defaultsToToday() {
        // GIVEN
        String email = "datetest@test.com";
        AssetRequest request = new AssetRequest();
        request.setStockCode("RELIANCE");
        request.setStockName("Reliance Industries");
        request.setPrice(2500.0);
        request.setQuantity(10.0);
        request.setTransactionDate(null);
        request.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        request.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);

        // WHEN
        String transactionId = transactionService.addTransaction(UserMail.from(email), request);

        // THEN
        assertNotNull(transactionId);
        TransactionEntity saved = transactionRepository.findById(transactionId).orElseThrow();
        assertEquals(LocalDate.now(), saved.getTransactionDate());
    }

    @Test
    void addTransaction_withNewTempTransactionId() {
        // GIVEN
        String email = "temptxn@test.com";
        String tempId = "TEMP-" + System.currentTimeMillis();

        AssetRequest request = new AssetRequest();
        request.setTempTransactionId(tempId);
        request.setStockCode("TCS");
        request.setStockName("Tata Consultancy Services");
        request.setPrice(3500.0);
        request.setQuantity(5.0);
        request.setTransactionDate(LocalDate.now());
        request.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        request.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.UPSTOX);

        // WHEN
        String transactionId = transactionService.addTransaction(UserMail.from(email), request);

        // THEN
        assertNotNull(transactionId);
        TransactionEntity saved = transactionRepository.findById(transactionId).orElseThrow();
        assertEquals(tempId, saved.getSourceTempTransactionId());
    }

    @Test
    void addTransaction_duplicateTempTransactionId_returnsExisting() {
        // GIVEN
        String email = "dupetemp@test.com";
        String tempId = "TEMP-DUP-" + System.currentTimeMillis();

        AssetRequest request1 = new AssetRequest();
        request1.setTempTransactionId(tempId);
        request1.setStockCode("INFOSYS");
        request1.setStockName("Infosys Ltd");
        request1.setPrice(1500.0);
        request1.setQuantity(20.0);
        request1.setTransactionDate(LocalDate.now());
        request1.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        request1.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.FYERS);

        String firstId = transactionService.addTransaction(UserMail.from(email), request1);

        AssetRequest request2 = new AssetRequest();
        request2.setTempTransactionId(tempId);
        request2.setStockCode("INFOSYS");
        request2.setStockName("Infosys Ltd");
        request2.setPrice(1500.0);
        request2.setQuantity(20.0);
        request2.setTransactionDate(LocalDate.now());
        request2.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        request2.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.FYERS);

        // WHEN
        String secondId = transactionService.addTransaction(UserMail.from(email), request2);

        // THEN
        assertEquals(firstId, secondId);
        long count = transactionRepository.findByEmail(email).size();
        assertEquals(1, count);
    }

    @Test
    void transactionsForCorporateActions_sufficientQuantity() {
        // GIVEN
        String email = "corpaction@test.com";
        LocalDate recordDate = LocalDate.of(2024, 6, 1);
        double requiredQty = 15.0;

        TransactionEntity txn1 = new TransactionEntity();
        txn1.setEmail(email);
        txn1.setStockCode("HDFCBANK");
        txn1.setStockName("HDFC Bank");
        txn1.setPrice(1600.0);
        txn1.setQuantity(10.0);
        txn1.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn1.setTransactionDate(LocalDate.of(2024, 1, 15));
        txn1.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        transactionRepository.save(txn1);

        TransactionEntity txn2 = new TransactionEntity();
        txn2.setEmail(email);
        txn2.setStockCode("HDFCBANK");
        txn2.setStockName("HDFC Bank");
        txn2.setPrice(1650.0);
        txn2.setQuantity(10.0);
        txn2.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn2.setTransactionDate(LocalDate.of(2024, 3, 10));
        txn2.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        transactionRepository.save(txn2);

        // WHEN
        List<TransactionEntity> result = transactionService.transactionsForCorporateActions(requiredQty, "HDFCBANK", recordDate);

        // THEN
        assertEquals(2, result.size());
    }

    @Test
    void transactionsForCorporateActions_insufficientQuantity_throws() {
        // GIVEN
        String email = "insufficient@test.com";
        LocalDate recordDate = LocalDate.of(2024, 6, 1);
        double requiredQty = 50.0;

        TransactionEntity txn = new TransactionEntity();
        txn.setEmail(email);
        txn.setStockCode("SBIN");
        txn.setStockName("State Bank of India");
        txn.setPrice(750.0);
        txn.setQuantity(5.0);
        txn.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn.setTransactionDate(LocalDate.of(2024, 2, 1));
        txn.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.UPSTOX);
        transactionRepository.save(txn);

        // WHEN / THEN
        assertThrows(IllegalArgumentException.class, () ->
                transactionService.transactionsForCorporateActions(requiredQty, "SBIN", recordDate));
    }

    @Test
    void transactionsForCorporateActions_dateFilterOrdered() {
        // GIVEN
        String email = "datefilter@test.com";
        LocalDate recordDate = LocalDate.of(2024, 6, 1);

        TransactionEntity txn1 = new TransactionEntity();
        txn1.setEmail(email);
        txn1.setStockCode("ICICIBANK");
        txn1.setStockName("ICICI Bank");
        txn1.setPrice(1000.0);
        txn1.setQuantity(10.0);
        txn1.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn1.setTransactionDate(LocalDate.of(2024, 5, 15));
        txn1.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        transactionRepository.save(txn1);

        TransactionEntity txn2 = new TransactionEntity();
        txn2.setEmail(email);
        txn2.setStockCode("ICICIBANK");
        txn2.setStockName("ICICI Bank");
        txn2.setPrice(950.0);
        txn2.setQuantity(10.0);
        txn2.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn2.setTransactionDate(LocalDate.of(2024, 5, 10));
        txn2.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        transactionRepository.save(txn2);

        // WHEN
        List<TransactionEntity> result = transactionService.transactionsForCorporateActions("ICICIBANK", recordDate);

        // THEN
        assertEquals(2, result.size());
        assertTrue(result.get(0).getTransactionDate().isAfter(result.get(1).getTransactionDate()) ||
                result.get(0).getTransactionDate().isEqual(result.get(1).getTransactionDate()));
    }

    @Test
    void testTransactionsForCorporateActions_brokerScoped() {
        // GIVEN
        String email = "brokerfilter@test.com";
        LocalDate recordDate = LocalDate.of(2024, 6, 1);

        TransactionEntity txn1 = new TransactionEntity();
        txn1.setEmail(email);
        txn1.setStockCode("KOTAKBANK");
        txn1.setStockName("Kotak Mahindra Bank");
        txn1.setPrice(1800.0);
        txn1.setQuantity(10.0);
        txn1.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn1.setTransactionDate(LocalDate.of(2024, 3, 1));
        txn1.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        transactionRepository.save(txn1);

        TransactionEntity txn2 = new TransactionEntity();
        txn2.setEmail(email);
        txn2.setStockCode("KOTAKBANK");
        txn2.setStockName("Kotak Mahindra Bank");
        txn2.setPrice(1750.0);
        txn2.setQuantity(10.0);
        txn2.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn2.setTransactionDate(LocalDate.of(2024, 2, 1));
        txn2.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.UPSTOX);
        transactionRepository.save(txn2);

        // WHEN
        List<TransactionEntity> result = transactionService.testTransactionsForCorporateActions(
                email, "KOTAKBANK", com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA, recordDate);

        // THEN
        assertEquals(1, result.size());
        assertEquals(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA, result.get(0).getBrokerName());
    }

    @Test
    void saveCorporateActionProcessedTransactions_batchSave() {
        // GIVEN
        String email = "batchsave@test.com";

        TransactionEntity txn1 = new TransactionEntity();
        txn1.setEmail(email);
        txn1.setStockCode("AXISBANK");
        txn1.setStockName("Axis Bank");
        txn1.setPrice(1000.0);
        txn1.setQuantity(10.0);
        txn1.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn1.setTransactionDate(LocalDate.of(2024, 1, 15));
        txn1.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.FYERS);

        TransactionEntity txn2 = new TransactionEntity();
        txn2.setEmail(email);
        txn2.setStockCode("AXISBANK");
        txn2.setStockName("Axis Bank");
        txn2.setPrice(1050.0);
        txn2.setQuantity(15.0);
        txn2.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn2.setTransactionDate(LocalDate.of(2024, 2, 20));
        txn2.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.FYERS);

        List<TransactionEntity> transactions = List.of(txn1, txn2);

        // WHEN
        List<String> savedIds = transactionService.saveCorporateActionProcessedTransactions(transactions);

        // THEN
        assertEquals(2, savedIds.size());
        assertEquals(2, transactionRepository.findByEmail(email).size());
    }

    @Test
    void userTransactions_withQueryFilters_viaPostEndpoint() throws Exception {
        // GIVEN
        String email = "queryfilter@test.com";

        TransactionEntity txn = new TransactionEntity();
        txn.setEmail(email);
        txn.setStockCode("SBIN");
        txn.setStockName("State Bank of India");
        txn.setPrice(700.0);
        txn.setQuantity(25.0);
        txn.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn.setTransactionDate(LocalDate.of(2024, 4, 10));
        txn.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        transactionRepository.save(txn);

        String token = generateToken(email);

        BulkGetRequest bulkGetRequest = new BulkGetRequest();
        bulkGetRequest.setQueryFilters(new ArrayList<>());

        // WHEN / THEN
        String url = baseUrl() + "/transactions/user/" + email;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<BulkGetRequest> entity = new HttpEntity<>(bulkGetRequest, headers);
        RestTemplate rt = createNoErrorRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.POST, entity, String.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("SBIN"));
    }

    @Test
    void getAllUserTransactions_viaGETEndpoint() throws Exception {
        // GIVEN
        String email = "getall@test.com";

        TransactionEntity txn1 = new TransactionEntity();
        txn1.setEmail(email);
        txn1.setStockCode("RELIANCE");
        txn1.setStockName("Reliance Industries");
        txn1.setPrice(2500.0);
        txn1.setQuantity(10.0);
        txn1.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn1.setTransactionDate(LocalDate.of(2024, 1, 5));
        txn1.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.UPSTOX);
        transactionRepository.save(txn1);

        TransactionEntity txn2 = new TransactionEntity();
        txn2.setEmail(email);
        txn2.setStockCode("TCS");
        txn2.setStockName("Tata Consultancy Services");
        txn2.setPrice(3800.0);
        txn2.setQuantity(8.0);
        txn2.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn2.setTransactionDate(LocalDate.of(2024, 2, 15));
        txn2.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        transactionRepository.save(txn2);

        String token = generateToken(email);

        // WHEN / THEN
        String url = baseUrl() + "/transactions/user/" + email;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        RestTemplate rt = createNoErrorRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.GET, entity, String.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("RELIANCE") || response.getBody().contains("TCS"));
    }

    @Test
    void updateTransactions_backfillsNullAssetType() {
        // GIVEN
        String email = "updateasset@test.com";

        TransactionEntity txn1 = new TransactionEntity();
        txn1.setEmail(email);
        txn1.setStockCode("HDFC");
        txn1.setStockName("HDFC Ltd");
        txn1.setPrice(2800.0);
        txn1.setQuantity(5.0);
        txn1.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn1.setTransactionDate(LocalDate.of(2024, 3, 1));
        txn1.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        txn1.setAssetType(null);
        transactionRepository.save(txn1);

        TransactionEntity txn2 = new TransactionEntity();
        txn2.setEmail(email);
        txn2.setStockCode("ICICI");
        txn2.setStockName("ICICI Bank");
        txn2.setPrice(1100.0);
        txn2.setQuantity(12.0);
        txn2.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.SELL);
        txn2.setTransactionDate(LocalDate.of(2024, 3, 15));
        txn2.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.UPSTOX);
        txn2.setAssetType(com.thiru.wealthlens.portfolio.dto.enums.AssetType.EQUITY);
        transactionRepository.save(txn2);

        // WHEN
        transactionService.updateTransactions();

        // THEN
        List<TransactionEntity> updated = transactionRepository.findByEmail(email);
        assertEquals(com.thiru.wealthlens.portfolio.dto.enums.AssetType.MUTUAL_FUND,
                updated.stream().filter(t -> t.getStockCode().equals("HDFC")).findFirst().orElseThrow().getAssetType());
        assertEquals(com.thiru.wealthlens.portfolio.dto.enums.AssetType.EQUITY,
                updated.stream().filter(t -> t.getStockCode().equals("ICICI")).findFirst().orElseThrow().getAssetType());
    }

    @Test
    void deleteTransactions_deletesByEmail() {
        // GIVEN
        String email = "deletetest@test.com";

        TransactionEntity txn1 = new TransactionEntity();
        txn1.setEmail(email);
        txn1.setStockCode("WIPRO");
        txn1.setStockName("Wipro Ltd");
        txn1.setPrice(450.0);
        txn1.setQuantity(30.0);
        txn1.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn1.setTransactionDate(LocalDate.of(2024, 1, 10));
        txn1.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        transactionRepository.save(txn1);

        TransactionEntity txn2 = new TransactionEntity();
        txn2.setEmail(email);
        txn2.setStockCode("INFY");
        txn2.setStockName("Infosys Ltd");
        txn2.setPrice(1550.0);
        txn2.setQuantity(15.0);
        txn2.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn2.setTransactionDate(LocalDate.of(2024, 2, 5));
        txn2.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.FYERS);
        transactionRepository.save(txn2);

        assertEquals(2, transactionRepository.findByEmail(email).size());

        // WHEN
        transactionService.deleteTransactions(UserMail.from(email));

        // THEN
        assertEquals(0, transactionRepository.findByEmail(email).size());
    }

    @Test
    void allTransactions_viaGETEndpoint() throws Exception {
        // GIVEN
        String email = "alltxn@test.com";

        TransactionEntity txn1 = new TransactionEntity();
        txn1.setEmail(email);
        txn1.setStockCode("ADANIPORTS");
        txn1.setStockName("Adani Ports");
        txn1.setPrice(1200.0);
        txn1.setQuantity(20.0);
        txn1.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn1.setTransactionDate(LocalDate.of(2024, 1, 20));
        txn1.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.UPSTOX);
        transactionRepository.save(txn1);

        String token = generateToken(email);

        // WHEN / THEN
        String url = baseUrl() + "/portfolio/user/" + email + "/all/transactions";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        RestTemplate rt = createNoErrorRestTemplate();
        ResponseEntity<String> response = rt.exchange(URI.create(url), HttpMethod.GET, entity, String.class);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("ADANIPORTS"));
    }
}
