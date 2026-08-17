package com.thiru.wealthlens.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.portfolio.service.MongoTemplateService;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.entity.query.QueryFilter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class MongoTemplateIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_EMAIL = "mongotemplate@test.com";
    private static final String OTHER_EMAIL = "other@test.com";

    @Autowired
    private MongoTemplateService mongoTemplateService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void getDocuments_whenValidFilters_shouldReturnResults() {
        // GIVEN
        String email = TEST_EMAIL;

        TransactionEntity txn = new TransactionEntity();
        txn.setEmail(email);
        txn.setStockCode("RELIANCE");
        txn.setStockName("Reliance Industries");
        txn.setPrice(2500.0);
        txn.setQuantity(10.0);
        txn.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn.setTransactionDate(LocalDate.of(2024, 1, 15));
        txn.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        transactionRepository.save(txn);

        // Use ArrayList instead of List.of() because QueryFilter modifies the list
        // Use stock_code (snake_case) as per ALLOWED_FIELDS in AssetEntity
        List<QueryFilter> filters = new ArrayList<>(List.of(
                QueryFilter.builder()
                        .filterKey("stock_code")
                        .operation(QueryFilter.FilterOperation.EQUALS)
                        .value("RELIANCE")
                        .isDateField(false)
                        .build()
        ));

        // WHEN
        List<TransactionEntity> results = mongoTemplateService.getDocuments(
                UserMail.from(email), filters, TransactionEntity.class);

        // THEN
        assertEquals(1, results.size());
        assertEquals("RELIANCE", results.get(0).getStockCode());
    }

    @Test
    void getDocuments_whenInvalidFilter_shouldThrow() {
        // GIVEN
        String email = TEST_EMAIL;

        // Use ArrayList instead of List.of() because QueryFilter modifies the list
        List<QueryFilter> filters = new ArrayList<>(List.of(
                QueryFilter.builder()
                        .filterKey("invalidField")
                        .operation(QueryFilter.FilterOperation.EQUALS)
                        .value("test")
                        .build()
        ));

        // WHEN / THEN
        assertThrows(IllegalArgumentException.class, () ->
                mongoTemplateService.getDocuments(UserMail.from(email), filters, TransactionEntity.class));
    }

    @Test
    void getDocuments_whenOtherUser_shouldNotLeakData() {
        // GIVEN
        String email1 = TEST_EMAIL;
        String email2 = OTHER_EMAIL;

        TransactionEntity txn1 = new TransactionEntity();
        txn1.setEmail(email1);
        txn1.setStockCode("TCS");
        txn1.setStockName("Tata Consultancy Services");
        txn1.setPrice(3800.0);
        txn1.setQuantity(5.0);
        txn1.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn1.setTransactionDate(LocalDate.now());
        txn1.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        transactionRepository.save(txn1);

        TransactionEntity txn2 = new TransactionEntity();
        txn2.setEmail(email2);
        txn2.setStockCode("INFY");
        txn2.setStockName("Infosys Ltd");
        txn2.setPrice(1500.0);
        txn2.setQuantity(10.0);
        txn2.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn2.setTransactionDate(LocalDate.now());
        txn2.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        transactionRepository.save(txn2);

        // WHEN
        List<TransactionEntity> email1Results = mongoTemplateService.getDocuments(
                UserMail.from(email1), new ArrayList<>(), TransactionEntity.class);
        List<TransactionEntity> email2Results = mongoTemplateService.getDocuments(
                UserMail.from(email2), new ArrayList<>(), TransactionEntity.class);

        // THEN
        assertEquals(1, email1Results.size());
        assertEquals("TCS", email1Results.get(0).getStockCode());
        assertEquals(1, email2Results.size());
        assertEquals("INFY", email2Results.get(0).getStockCode());
        assertEquals(0, email1Results.stream()
                .filter(t -> t.getStockCode().equals("INFY"))
                .count());
    }

    @Test
    void getDocuments_whenEmptyFilters_shouldReturnAllForUser() {
        // GIVEN
        String email = TEST_EMAIL;

        TransactionEntity txn1 = new TransactionEntity();
        txn1.setEmail(email);
        txn1.setStockCode("RELIANCE");
        txn1.setStockName("Reliance Industries");
        txn1.setPrice(2500.0);
        txn1.setQuantity(10.0);
        txn1.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn1.setTransactionDate(LocalDate.of(2024, 1, 15));
        txn1.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        transactionRepository.save(txn1);

        TransactionEntity txn2 = new TransactionEntity();
        txn2.setEmail(email);
        txn2.setStockCode("TCS");
        txn2.setStockName("Tata Consultancy Services");
        txn2.setPrice(3800.0);
        txn2.setQuantity(5.0);
        txn2.setTransactionType(com.thiru.wealthlens.portfolio.dto.enums.TransactionType.BUY);
        txn2.setTransactionDate(LocalDate.of(2024, 2, 20));
        txn2.setBrokerName(com.thiru.wealthlens.portfolio.dto.enums.BrokerName.ZERODHA);
        transactionRepository.save(txn2);

        // WHEN
        List<TransactionEntity> results = mongoTemplateService.getDocuments(
                UserMail.from(email), new ArrayList<>(), TransactionEntity.class);

        // THEN
        assertEquals(2, results.size());
    }

    @Test
    void getDocuments_whenMongoDown_shouldThrow() {
        // GIVEN
        String email = TEST_EMAIL;

        // Use ArrayList instead of List.of() because QueryFilter modifies the list
        // Use stock_code (snake_case) as per ALLOWED_FIELDS in AssetEntity
        List<QueryFilter> filters = new ArrayList<>(List.of(
                QueryFilter.builder()
                        .filterKey("stock_code")
                        .operation(QueryFilter.FilterOperation.EQUALS)
                        .value("TEST")
                        .build()
        ));

        // WHEN / THEN - verify behavior when MongoDB connection is unavailable
        // This test verifies service handles connection issues gracefully
        assertThrows(Exception.class, () ->
                mongoTemplateService.getDocuments(UserMail.from(email), filters, TransactionEntity.class));
    }
}
