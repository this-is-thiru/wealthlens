package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.config.IdempotencyProperties;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.TransactionResponse;
import com.thiru.wealthlens.portfolio.dto.context.TransactionRecord;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.entity.query.QueryFilter;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing TransactionEntity records. Class-level @{@link Transactional}
 * ensures that methods touching multiple collections are atomic. Safe only when the
 * MongoDB replica set + {@code app.mongodb.transactions-enabled=true} is set.
 */
@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    private final IdempotencyProperties idempotencyProperties;
    private final MongoTemplateService mongoTemplateService;

    /**
     * Records a trade, or recognises that this submission already did.
     *
     * <p>Three de-duplication routes, in order of authority: the client's own idempotency key
     * (exact, no window), the {@code sourceTempTransactionId} of a redrive, and a derived
     * fingerprint of the trade's fields within a short window. The first two are exact; the last is
     * a heuristic and says so.
     *
     * <p>Callers <b>must</b> honour {@link TransactionRecord#replay()} and skip applying the trade.
     */
    public TransactionRecord recordTransaction(UserMail userMail, AssetRequest assetRequest) {
        if (assetRequest.getTransactionDate() == null) {
            assetRequest.setTransactionDate(LocalDate.now());
        }
        String email = userMail.getEmail();

        Optional<TransactionEntity> byKey = Optional.ofNullable(assetRequest.getIdempotencyKey())
                .flatMap(key -> transactionRepository.findByEmailAndIdempotencyKey(email, key));
        if (byKey.isPresent()) {
            log.info("Idempotency key {} already recorded as transaction {}; returning it unchanged",
                    assetRequest.getIdempotencyKey(), byKey.get().getId());
            return TransactionRecord.replayOf(byKey.get().getId());
        }

        Optional<TransactionEntity> byTemp = Optional.ofNullable(assetRequest.getTempTransactionId())
                .flatMap(id -> transactionRepository.findByEmailAndSourceTempTransactionId(email, id));
        if (byTemp.isPresent()) {
            log.warn("Temporary transaction {} was already redriven as {}; not applying it again",
                    assetRequest.getTempTransactionId(), byTemp.get().getId());
            return TransactionRecord.replayOf(byTemp.get().getId());
        }

        String fingerprint = TradeFingerprint.of(email, assetRequest);
        if (assetRequest.getIdempotencyKey() == null && idempotencyProperties.fingerprintFallbackEnabled()) {
            LocalDateTime since = LocalDateTime.now().minus(idempotencyProperties.window());
            Optional<TransactionEntity> recent = transactionRepository
                    .findFirstByEmailAndTradeFingerprintAndSubmittedAtAfterOrderBySubmittedAtDesc(
                            email, fingerprint, since);
            if (recent.isPresent()) {
                log.warn("An identical trade was submitted within the last {}s and recorded as {};"
                                + " treating this as a retry. Supply an idempotencyKey to record two"
                                + " genuinely identical trades this close together",
                        idempotencyProperties.windowSeconds(), recent.get().getId());
                return TransactionRecord.replayOf(recent.get().getId());
            }
        }

        TransactionEntity entity = assetRequest.asTransaction();
        entity.setEmail(email);
        entity.setIdempotencyKey(assetRequest.getIdempotencyKey());
        entity.setTradeFingerprint(fingerprint);
        entity.setSubmittedAt(LocalDateTime.now());
        log.info("Transaction: {}, Stock: '{}' on '{}' noted successfully for: {}", entity.getTransactionType(),
                entity.getStockName(), entity.getTransactionDate(), email);
        return TransactionRecord.created(transactionRepository.save(entity).getId());
    }

    /**
     * @deprecated the id alone cannot tell a caller that nothing was written, so a caller that acts
     *     on it applies a replayed trade to the portfolio a second time. Use
     *     {@link #recordTransaction} and honour its {@code replay} flag. Retained because the V1
     *     flow is in live use.
     */
    @Deprecated
    public String addTransaction(UserMail userMail, AssetRequest assetRequest) {

        if (assetRequest.getTransactionDate() == null) {
            assetRequest.setTransactionDate(LocalDate.now());
        }
        if (assetRequest.getTempTransactionId() != null) {
            Optional<TransactionEntity> existing = transactionRepository
                .findByEmailAndSourceTempTransactionId(userMail.getEmail(), assetRequest.getTempTransactionId());
            if (existing.isPresent()) {
                log.warn("Duplicate transaction suppressed for sourceTempTransactionId {}", assetRequest.getTempTransactionId());
                return existing.get().getId();
            }
        }
        TransactionEntity transactionEntity = assetRequest.asTransaction();
        transactionEntity.setEmail(userMail.getEmail());

        log.info("Transaction: {}, Stock: '{}' on '{}' noted successfully for: {}", transactionEntity.getTransactionType(),
                transactionEntity.getStockName(), transactionEntity.getTransactionDate(), userMail.getEmail());
        TransactionEntity savedTransactionEntity = transactionRepository.save(transactionEntity);
        return savedTransactionEntity.getId();
    }

    public List<TransactionEntity> transactionsForCorporateActions(double quantity, String stockCode, LocalDate recordDate) {

        List<TransactionEntity> transactionEntities = transactionRepository.findByStockCodeAndTransactionDateBeforeOrderByTransactionDateDesc(stockCode, recordDate);

        List<TransactionEntity> transactionsToConsider = new ArrayList<>();
        for (TransactionEntity transactionEntity : transactionEntities) {
            if (quantity <= 0) {
                break;
            }

            transactionsToConsider.add(transactionEntity);
            quantity -= transactionEntity.getQuantity();
        }

        if (quantity > 0) {
            throw new IllegalArgumentException("Invalid transactions");
        }
        return transactionsToConsider;
    }

    public List<TransactionEntity> transactionsForCorporateActions(String stockCode, LocalDate recordDate) {
        return transactionRepository.findByStockCodeAndTransactionDateBeforeOrderByTransactionDateDesc(stockCode, recordDate);
    }

    public List<TransactionEntity> testTransactionsForCorporateActions(String email, String stockCode, BrokerName brokerName, LocalDate recordDate) {
        return transactionRepository.findByEmailAndStockCodeAndBrokerNameAndTransactionDateBeforeOrderByTransactionDateDesc(email, stockCode, brokerName, recordDate);
    }

    public List<String> saveCorporateActionProcessedTransactions(List<TransactionEntity> transactionEntities) {
        List<TransactionEntity> savedTransactions = transactionRepository.saveAll(transactionEntities);
        return TCollectionUtil.map(savedTransactions, TransactionEntity::getId);
    }

    public List<TransactionResponse> userTransactions(UserMail userMail, List<QueryFilter> queryFilters) {
        List<TransactionEntity> transactions = getUserTransactions(userMail, queryFilters);
        return transactions.stream().map(transaction -> TJsonMapper.copy(transaction, TransactionResponse.class)).toList();
    }

    public List<TransactionEntity> getUserTransactions(UserMail userMail, List<QueryFilter> queryFilters) {
        return mongoTemplateService.getDocuments(userMail, queryFilters, TransactionEntity.class);
    }

    public List<TransactionEntity> getUserTransactions(UserMail userMail) {
        return transactionRepository.findByEmail(userMail.getEmail());
    }

    public List<TransactionResponse> getAllUserTransactions(UserMail userMail) {
        List<TransactionEntity> transactionEntities = transactionRepository.findByEmail(userMail.getEmail());
        return transactionEntities.stream().map(transaction -> TJsonMapper.copy(transaction, TransactionResponse.class)).toList();
    }


//    public List<Transaction> testMethod(UserMail userMail, String stockCode, LocalDate recordDate) {
//
//        String email = userMail.getEmail();
//        return transactionRepository.findByEmailAndStockCodeAndTransactionDateBeforeOrderByTransactionDateDesc(email, stockCode, recordDate);
//    }

    public void updateTransactions() {
        List<TransactionEntity> transactionEntities = transactionRepository.findAll();

        transactionEntities.forEach(transaction -> {
                    AssetType assetType = transaction.getAssetType();
                    transaction.setAssetType(assetType == null ? AssetType.MUTUAL_FUND : assetType);
                }
        );
        transactionRepository.saveAll(transactionEntities);
    }

    public void deleteTransactions(UserMail userMail) {
        transactionRepository.deleteByEmail(userMail.getEmail());
    }

    public List<TransactionEntity> allTransactions() {
        return transactionRepository.findAll();
    }
}
