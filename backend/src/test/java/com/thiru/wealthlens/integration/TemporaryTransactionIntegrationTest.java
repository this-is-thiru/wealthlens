package com.thiru.wealthlens.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.entity.LastlyPerformedCorporateAction;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeFlow;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.portfolio.service.TemporaryTransactionService;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TemporaryTransactionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TemporaryTransactionService temporaryTransactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void filterOutTransaction_whenCaExists_shouldSaveTemporary() {
        // GIVEN
        String email = "test@example.com";
        UserMail userMail = UserMail.from(email);
        String stockCode = "RELIANCE";
        LocalDate txnDate = LocalDate.of(2024, 4, 15);

        CorporateActionEntity ca = createCorporateAction(stockCode, CorporateActionType.BONUS, LocalDate.of(2024, 4, 10));
        corporateActionRepository.save(ca);

        AssetRequest request = createBuyRequest(email, stockCode, txnDate);

        // WHEN
        String tempTxnId = temporaryTransactionService.filterOutTransaction(userMail, request, TradeFlow.V1);

        // THEN
        assertNotNull(tempTxnId);
        TransactionEntity saved = transactionRepository.findById(tempTxnId).orElse(null);
        assertNotNull(saved);
        assertEquals(TransactionStatus.TEMPORARY, saved.getStatus());
        assertEquals(stockCode, saved.getStockCode());
    }

    @Test
    void filterOutTransaction_whenNoCa_shouldReturnNull() {
        // GIVEN
        String email = "test@example.com";
        UserMail userMail = UserMail.from(email);
        String stockCode = "INFY";
        LocalDate txnDate = LocalDate.of(2024, 5, 20);

        AssetRequest request = createBuyRequest(email, stockCode, txnDate);

        // WHEN
        String tempTxnId = temporaryTransactionService.filterOutTransaction(userMail, request, TradeFlow.V1);

        // THEN
        assertNull(tempTxnId);
    }

    @Test
    void hasTemporaryTransactions_whenExists_shouldReturnTrue() {
        // GIVEN
        String email = "test@example.com";
        UserMail userMail = UserMail.from(email);

        TransactionEntity tempTxn = new TransactionEntity();
        tempTxn.setEmail(email);
        tempTxn.setStockCode("TEST");
        tempTxn.setStatus(TransactionStatus.TEMPORARY);
        tempTxn.setTransactionType(TransactionType.BUY);
        tempTxn.setAssetType(AssetType.EQUITY);
        transactionRepository.save(tempTxn);

        // WHEN
        boolean result = temporaryTransactionService.hasTemporaryTransactions(userMail);

        // THEN
        assertTrue(result);
    }

    @Test
    void hasTemporaryTransactions_whenNone_shouldReturnFalse() {
        // GIVEN
        String email = "notemp@example.com";
        UserMail userMail = UserMail.from(email);

        // Ensure no temp transactions
        transactionRepository.deleteByEmailAndStatus(email, TransactionStatus.TEMPORARY);

        // WHEN
        boolean result = temporaryTransactionService.hasTemporaryTransactions(userMail);

        // THEN
        assertFalse(result);
    }

    @Test
    void filterOutTransaction_whenCheckFalse_shouldReturnFalse() {
        // GIVEN
        String email = "test@example.com";
        UserMail userMail = UserMail.from(email);
        String stockCode = "TCS";
        LocalDate txnDate = LocalDate.of(2024, 6, 15);

        AssetRequest request = createBuyRequest(email, stockCode, txnDate);
        boolean checkCorporateAction = false;

        // WHEN
        boolean result = temporaryTransactionService.filterOutTransaction(userMail, request, checkCorporateAction);

        // THEN
        assertFalse(result);
    }

    @Test
    void filterOutTransaction_whenCheckTrueCaPresent_shouldBlock() {
        // GIVEN
        String email = "test@example.com";
        UserMail userMail = UserMail.from(email);
        String stockCode = "HDFC";
        LocalDate txnDate = LocalDate.of(2024, 4, 20);

        CorporateActionEntity ca = createCorporateAction(stockCode, CorporateActionType.STOCK_SPLIT, LocalDate.of(2024, 4, 15));
        corporateActionRepository.save(ca);

        AssetRequest request = createBuyRequest(email, stockCode, txnDate);
        boolean checkCorporateAction = true;

        // WHEN
        boolean result = temporaryTransactionService.filterOutTransaction(userMail, request, checkCorporateAction);

        // THEN
        assertTrue(result);
    }

    @Test
    void anyCorporateActionToPerform_whenCaNotPerformed_shouldReturnTrue() {
        // GIVEN
        String email = "test@example.com";
        UserMail userMail = UserMail.from(email);
        String stockCode = "SBIN";
        LocalDate txnDate = LocalDate.of(2024, 4, 25);

        CorporateActionEntity ca = createCorporateAction(stockCode, CorporateActionType.BONUS, LocalDate.of(2024, 4, 20));
        corporateActionRepository.save(ca);

        // No lastly performed CA exists

        // WHEN
        boolean result = temporaryTransactionService.anyCorporateActionToPerform(userMail, stockCode, txnDate, BrokerName.ZERODHA);

        // THEN
        assertTrue(result);
    }

    @Test
    void anyCorporateActionToPerform_whenCaAlreadyPerformed_shouldReturnFalse() {
        // GIVEN
        String email = "test@example.com";
        UserMail userMail = UserMail.from(email);
        String stockCode = "SBIN";
        LocalDate txnDate = LocalDate.of(2024, 5, 25);

        LocalDate performedDate = LocalDate.of(2024, 4, 20);
        LastlyPerformedCorporateAction performedCa = createLastlyPerformedCa(email, stockCode, CorporateActionType.BONUS, performedDate);
        lastlyPerformedCorporateActionRepo.save(performedCa);

        CorporateActionEntity ca = createCorporateAction(stockCode, CorporateActionType.BONUS, performedDate);
        corporateActionRepository.save(ca);

        // WHEN
        boolean result = temporaryTransactionService.anyCorporateActionToPerform(userMail, stockCode, txnDate, BrokerName.ZERODHA);

        // THEN
        assertFalse(result);
    }

    @Test
    void anyCorporateActionToPerform_whenNoCaInQuarter_shouldReturnFalse() {
        // GIVEN
        String email = "test@example.com";
        UserMail userMail = UserMail.from(email);
        String stockCode = "NOTHAVE";
        LocalDate txnDate = LocalDate.of(2024, 5, 15);

        // No corporate actions exist in this quarter

        // WHEN
        boolean result = temporaryTransactionService.anyCorporateActionToPerform(userMail, stockCode, txnDate, BrokerName.ZERODHA);

        // THEN
        assertFalse(result);
    }

    @Test
    void deleteTemporaryTransaction_whenCalled_shouldDeleteTempAndLastPerformed() {
        // GIVEN
        String email = "todelete@example.com";

        TransactionEntity tempTxn = new TransactionEntity();
        tempTxn.setEmail(email);
        tempTxn.setStockCode("DELTEST");
        tempTxn.setStatus(TransactionStatus.TEMPORARY);
        tempTxn.setTransactionType(TransactionType.BUY);
        tempTxn.setAssetType(AssetType.EQUITY);
        transactionRepository.save(tempTxn);

        LastlyPerformedCorporateAction performedCa = createLastlyPerformedCa(email, "DELTEST", CorporateActionType.BONUS, LocalDate.now());
        lastlyPerformedCorporateActionRepo.save(performedCa);

        UserMail userMail = UserMail.from(email);

        // Verify exists before delete
        assertFalse(transactionRepository.findByEmail(email).isEmpty());
        assertTrue(lastlyPerformedCorporateActionRepo.findAll().stream().anyMatch(r -> email.equals(r.getEmail())));

        // WHEN
        temporaryTransactionService.deleteTemporaryTransaction(userMail);

        // THEN
        assertTrue(transactionRepository.findByEmailAndStatus(email, TransactionStatus.TEMPORARY).isEmpty());
        assertFalse(lastlyPerformedCorporateActionRepo.findAll().stream().anyMatch(r -> email.equals(r.getEmail())));
    }

    @Test
    void findTempTransactionsBefore_shouldReturnMatching() {
        // GIVEN
        String email = "findtest@example.com";
        String stockCode = "FINDTEST";
        AssetType assetType = AssetType.EQUITY;
        LocalDate recordDate = LocalDate.of(2024, 5, 15);

        TransactionEntity before = createTempTransaction(email, stockCode, assetType, LocalDate.of(2024, 4, 10));
        TransactionEntity before2 = createTempTransaction(email, stockCode, assetType, LocalDate.of(2024, 5, 1));
        TransactionEntity after = createTempTransaction(email, stockCode, assetType, LocalDate.of(2024, 6, 1));

        transactionRepository.save(before);
        transactionRepository.save(before2);
        transactionRepository.save(after);

        // WHEN
        List<TransactionEntity> result = temporaryTransactionService.findTempTransactionsBefore(email, stockCode, assetType, recordDate);

        // THEN
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(t -> t.getTransactionDate().isBefore(recordDate)));
    }

    @Test
    void findTempTransactionsAfter_shouldReturnOrdered() {
        // GIVEN
        String email = "aftertest@example.com";
        String stockCode = "AFTERTEST";
        AssetType assetType = AssetType.EQUITY;
        LocalDate recordDate = LocalDate.of(2024, 4, 30);

        TransactionEntity before = createTempTransaction(email, stockCode, assetType, LocalDate.of(2024, 4, 10));
        TransactionEntity after1 = createTempTransaction(email, stockCode, assetType, LocalDate.of(2024, 5, 15));
        TransactionEntity after2 = createTempTransaction(email, stockCode, assetType, LocalDate.of(2024, 6, 20));

        transactionRepository.save(before);
        transactionRepository.save(after1);
        transactionRepository.save(after2);

        // WHEN
        List<TransactionEntity> result = temporaryTransactionService.findTempTransactionsAfter(email, stockCode, assetType, recordDate);

        // THEN
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(t -> t.getTransactionDate().isAfter(recordDate)));
        // Verify ordering by transactionDate ascending
        assertTrue(result.get(0).getTransactionDate().isBefore(result.get(1).getTransactionDate()));
    }

    // Helper methods

    private AssetRequest createBuyRequest(String email, String stockCode, LocalDate date) {
        AssetRequest request = new AssetRequest();
        request.setEmail(email);
        request.setStockCode(stockCode);
        request.setStockName(stockCode);
        request.setExchangeName("NSE");
        request.setBrokerName(BrokerName.ZERODHA);
        request.setAssetType(AssetType.EQUITY);
        request.setPrice(100.0);
        request.setQuantity(10.0);
        request.setTransactionType(TransactionType.BUY);
        request.setAccountType(AccountType.SELF);
        request.setAccountHolder(email);
        request.setTransactionDate(date);
        request.setBrokerCharges(10.0);
        request.setMiscCharges(5.0);
        return request;
    }

    private CorporateActionEntity createCorporateAction(String stockCode, CorporateActionType type, LocalDate recordDate) {
        CorporateActionEntity ca = new CorporateActionEntity();
        ca.setStockCode(stockCode);
        ca.setStockName(stockCode);
        ca.setType(type);
        ca.setAssetType(AssetType.EQUITY);
        ca.setRecordDate(recordDate);
        ca.setExDate(recordDate.plusDays(1));
        ca.setDescription("Test CA");
        ca.setPriority(1);
        return ca;
    }

    private LastlyPerformedCorporateAction createLastlyPerformedCa(String email, String stockCode, CorporateActionType type, LocalDate actionDate) {
        return LastlyPerformedCorporateAction.builder()
                .email(email)
                .stockCode(stockCode)
                .assetType(AssetType.EQUITY)
                .actionType(type)
                .actionDate(actionDate)
                .brokerName(BrokerName.ZERODHA)
                .build();
    }

    private TransactionEntity createTempTransaction(String email, String stockCode, AssetType assetType, LocalDate txnDate) {
        TransactionEntity txn = new TransactionEntity();
        txn.setEmail(email);
        txn.setStockCode(stockCode);
        txn.setStockName(stockCode);
        txn.setAssetType(assetType);
        txn.setStatus(TransactionStatus.TEMPORARY);
        txn.setTransactionType(TransactionType.BUY);
        txn.setTransactionDate(txnDate);
        txn.setPrice(100.0);
        txn.setQuantity(10.0);
        txn.setBrokerName(BrokerName.ZERODHA);
        txn.setExchangeName("NSE");
        txn.setAccountType(AccountType.SELF);
        txn.setAccountHolder(email);
        return txn;
    }
}
