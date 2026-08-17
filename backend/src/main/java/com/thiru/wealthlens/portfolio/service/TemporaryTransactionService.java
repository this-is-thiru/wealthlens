package com.thiru.wealthlens.portfolio.service;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.repository.CorporateActionRepository;
import com.thiru.wealthlens.corporate.repository.LastlyPerformedCorporateActionRepo;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds transactions that are blocked by a pending corporate action, to be
 * redriven after the action is processed. Class-level @{@link Transactional}
 * ensures single-method writes are atomic. Safe only when MongoDB replica set
 * + {@code app.mongodb.transactions-enabled=true} is set.
 */
@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class TemporaryTransactionService {

    private static final int ONE = 1;

    private final CorporateActionRepository corporateActionRepository;
    private final TransactionRepository transactionRepository;
    private final LastlyPerformedCorporateActionRepo lastlyPerformedCorporateActionRepo;

    public String filterOutTransaction(UserMail userMail, AssetRequest assetRequest) {
        LocalDate txnDate = assetRequest.getTransactionDate();
        if (anyCorporateActionToPerform(userMail, assetRequest.getStockCode(), txnDate, assetRequest.getBrokerName())) {
            TransactionEntity entity = assetRequest.asTransaction();
            entity.setStatus(TransactionStatus.TEMPORARY);
            entity.setAssetRequest(assetRequest);
            entity.setEmail(userMail.getEmail());
            TransactionEntity saved = transactionRepository.save(entity);
            return saved.getId();
        }
        return null;
    }

    public List<TransactionEntity> getAllTemporaryTransactions(UserMail userMail) {
        return transactionRepository.findByEmailAndStatus(userMail.getEmail(), TransactionStatus.TEMPORARY);
    }

    public boolean hasTemporaryTransactions(UserMail userMail) {
        return !getAllTemporaryTransactions(userMail).isEmpty();
    }

    public boolean filterOutTransaction(UserMail userMail, AssetRequest assetRequest, boolean checkCorporateAction) {
        if (!checkCorporateAction) {
            return false;
        }
        LocalDate txnDate = assetRequest.getTransactionDate();
        return anyCorporateActionToPerform(userMail, assetRequest.getStockCode(), txnDate, assetRequest.getBrokerName());
    }

    public boolean anyCorporateActionToPerform(UserMail userMail, String stockCode, LocalDate txnDate, BrokerName brokerName) {
        LocalDate quarterStart = getQuarterStart(txnDate);
        List<CorporateActionEntity> corporateActions = corporateActionRepository
                .findByStockCodeAndTypeInAndRecordDateBetween(stockCode, CorporateActionType.FILTERABLE_CORPORATE_ACTIONS, quarterStart, txnDate);

        for (CorporateActionEntity corporateAction : corporateActions) {
            if (isCorporateActionToPerform(userMail, corporateAction, brokerName)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCorporateActionToPerform(UserMail userMail, CorporateActionEntity corporateAction, BrokerName brokerName) {
        String stockCode = corporateAction.getStockCode();
        CorporateActionType actionType = corporateAction.getType();
        AssetType assetType = corporateAction.getAssetType();

        var lastlyPerformedActionOptional = lastlyPerformedCorporateActionRepo
                .findLastPerformedCA(userMail.getEmail(), stockCode, assetType, actionType, brokerName);
        var nextDayOfActionDay = lastlyPerformedActionOptional.map(lpa -> lpa.getActionDate().plusDays(1));

        if (nextDayOfActionDay.isPresent() && corporateAction.getRecordDate().isBefore(nextDayOfActionDay.get())) {
            return false;
        }
        return true;
    }

    public void deleteTemporaryTransaction(UserMail userMail) {
        lastlyPerformedCorporateActionRepo.deleteByEmail(userMail.getEmail());
        log.info("Deleted lastly performed corporate actions for user: {}", userMail.getEmail());
        transactionRepository.deleteByEmailAndStatus(userMail.getEmail(), TransactionStatus.TEMPORARY);
    }

    public List<TransactionEntity> findTempTransactionsBefore(String email, String stockCode, AssetType assetType, LocalDate recordDate) {
        return transactionRepository.findByEmailAndStatusAndStockCodeAndAssetTypeAndTransactionDateBefore(
                email, TransactionStatus.TEMPORARY, stockCode, assetType, recordDate);
    }

    public List<TransactionEntity> findTempTransactionsAfter(String email, String stockCode, AssetType assetType, LocalDate recordDate) {
        return transactionRepository.findByEmailAndStatusAndStockCodeAndAssetTypeAndTransactionDateAfterOrderByTransactionDateAsc(
                email, TransactionStatus.TEMPORARY, stockCode, assetType, recordDate);
    }

    private static LocalDate getQuarterStart(LocalDate transactionDate) {
        int year = transactionDate.getYear();
        Month quarterStartMonth = transactionDate.getMonth().firstMonthOfQuarter();
        return LocalDate.of(year, quarterStartMonth, ONE);
    }
}
