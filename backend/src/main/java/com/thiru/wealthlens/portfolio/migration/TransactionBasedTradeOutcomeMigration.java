package com.thiru.wealthlens.portfolio.migration;

import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService.BuyLot;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService.MatchedTrade;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService.SellRequest;
import com.thiru.wealthlens.shared.config.TradeOutcomeMigrationRunner;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Transaction-based migration that rebuilds TradeOutcomeEntity directly from
 * TransactionEntity BUY/SELL records via FIFO matching.
 * <p>
 * Triggered on app startup via {@link TradeOutcomeMigrationRunner} (CommandLineRunner).
 * Runs once only when 'trade_outcomes' collection is empty.
 * Do NOT trigger from client-facing code.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionBasedTradeOutcomeMigration {

    private final MongoTemplate mongoTemplate;
    private final TradeOutcomeRepository tradeOutcomeRepository;
    private final TradeMatchingService tradeMatchingService;

    public void migrateTransactionsToTradeOutcomes() {
        log.info("Starting transaction-based migration to TradeOutcomeEntity");
        LocalDateTime startTime = LocalDateTime.now();

        // Step 1: Get all distinct emails from transactions
        Set<String> distinctEmails = getDistinctEmails();
        log.info("Found {} distinct users with transactions", distinctEmails.size());

        int totalUsers = 0;
        int totalTradesCreated = 0;
        int skippedUsers = 0;

        for (String email : distinctEmails) {
            try {
                int tradesCreated = migrateUser(email);
                totalTradesCreated += tradesCreated;
                totalUsers++;

                if (totalUsers % 100 == 0) {
                    log.info("Migration progress: processed {} users, created {} trade outcomes",
                            totalUsers, totalTradesCreated);
                }
            } catch (Exception e) {
                log.error("Failed to migrate user {}: {}", email, e.getMessage(), e);
                skippedUsers++;
            }
        }

        LocalDateTime endTime = LocalDateTime.now();
        long durationSeconds = ChronoUnit.SECONDS.between(startTime, endTime);

        log.info("Migration completed: users={}, tradeOutcomes={}, skippedUsers={}, duration={}s",
                totalUsers, totalTradesCreated, skippedUsers, durationSeconds);
    }

    private int migrateUser(String email) {
        // Step 2a: Read all BUY transactions for this user
        List<TransactionEntity> buyTransactions = findByEmailAndTransactionType(email, TransactionType.BUY);
        if (buyTransactions.isEmpty()) {
            log.debug("No BUY transactions for user {}, skipping", email);
            return 0;
        }

        // Step 2b: Read all SELL transactions for this user
        List<TransactionEntity> sellTransactions = findByEmailAndTransactionType(email, TransactionType.SELL);
        if (sellTransactions.isEmpty()) {
            log.debug("No SELL transactions for user {}, skipping", email);
            return 0;
        }

        // Sort sells by date
        sellTransactions.sort(Comparator.comparing(TransactionEntity::getTransactionDate));

        // Step 2c: Build BuyLots from BUYs
        List<BuyLot> buyLots = tradeMatchingService.buildLotsFromBuys(buyTransactions);

        // Step 2d: For each SELL, match against lots
        int tradeCount = 0;
        List<TradeOutcomeEntity> tradeOutcomes = new ArrayList<>();

        for (TransactionEntity sellTxn : sellTransactions) {
            SellRequest sellRequest = tradeMatchingService.toSellRequest(sellTxn);
            List<MatchedTrade> matchedTrades = tradeMatchingService.matchSellToLots(sellRequest, buyLots);

            for (MatchedTrade matchedTrade : matchedTrades) {
                TradeOutcomeEntity outcome = tradeMatchingService.toTradeOutcomeEntity(matchedTrade);
                tradeOutcomes.add(outcome);
                tradeCount++;
            }
        }

        // Batch save
        if (!tradeOutcomes.isEmpty()) {
            tradeOutcomeRepository.saveAll(tradeOutcomes);
            log.debug("Saved {} trade outcomes for user {}", tradeOutcomes.size(), email);
        }

        return tradeCount;
    }

    private Set<String> getDistinctEmails() {
        Query query = new Query();
        query.fields().include("email");
        List<Document> results = mongoTemplate.find(query, Document.class, "transactions");

        return results.stream()
                .map(doc -> doc.getString("email"))
                .filter(email -> email != null && !email.isBlank())
                .collect(Collectors.toSet());
    }

    private List<TransactionEntity> findByEmailAndTransactionType(String email, TransactionType transactionType) {
        Query query = new Query();
        query.addCriteria(Criteria.where("email").is(email));
        query.addCriteria(Criteria.where("transaction_type").is(transactionType.name()));
        query.with(Sort.by(Sort.Direction.ASC, "transaction_date"));

        return mongoTemplate.find(query, TransactionEntity.class, "transactions");
    }
}
