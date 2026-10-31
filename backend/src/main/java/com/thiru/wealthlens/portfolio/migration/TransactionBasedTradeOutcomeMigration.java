package com.thiru.wealthlens.portfolio.migration;

import com.thiru.wealthlens.portfolio.dto.TradeOutcomeRebuildReport;
import com.thiru.wealthlens.portfolio.dto.TradeOutcomeRebuildReport.SkippedUser;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService.BuyLot;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService.MatchedTrade;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService.SellRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Rebuilds {@code trade_outcomes} from raw transactions — every SELL matched FIFO against lots built from every BUY —
 * when a {@code SUPER_USER} asks ({@code POST /trade-outcomes/rebuild}).
 *
 * <p>An operation, not a startup hook (D-280): at boot it rebuilt whenever the collection was empty, as nobody, and a
 * user who failed was only logged. It stays because it is how {@code trade_outcomes} is recomputed after a correction
 * to {@code transactions} ({@code docs/trade-ledger/migration.md} §3a; D-284, so not one-time).
 *
 * <p>Idempotent per user (D-282): a user with any row, whichever writer made it, is skipped and listed, so V1 and V2
 * rows are never duplicated, a run after one that failed part-way completes the rest, and a run after a complete one
 * writes nothing. The rows record the caller through the auditing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionBasedTradeOutcomeMigration {

    private final MongoTemplate mongoTemplate;
    private final TradeOutcomeRepository tradeOutcomeRepository;
    private final TradeMatchingService tradeMatchingService;

    /** Rebuild every user with transactions and no rows, unless {@code dryRun}; {@code auditor} is who asked. */
    public TradeOutcomeRebuildReport rebuild(boolean dryRun, String auditor) {
        Set<String> users = new TreeSet<>(getDistinctEmails());
        List<SkippedUser> skipped = new ArrayList<>();
        int usersRebuilt = 0;
        int rowsWritten = 0;
        for (String email : users) {
            long existing = tradeOutcomeRepository.countByEmail(email);
            if (existing > 0) {
                skipped.add(new SkippedUser(email, existing));
                continue;
            }
            rowsWritten += rebuildUser(email, dryRun);
            usersRebuilt++;
        }
        log.info("trade_outcomes rebuild (dryRun={}, by {}): {} users, {} rebuilt, {} rows, {} skipped", dryRun,
                auditor, users.size(), usersRebuilt, rowsWritten, skipped.size());
        return new TradeOutcomeRebuildReport(dryRun, auditor, users.size(), usersRebuilt, rowsWritten, skipped);
    }

    /** One user's rows, saved unless {@code dryRun}; a failure names the user, the users before stay written. */
    private int rebuildUser(String email, boolean dryRun) {
        try {
            List<TradeOutcomeEntity> rows = rebuiltRows(email);
            if (!dryRun && !rows.isEmpty()) {
                tradeOutcomeRepository.saveAll(rows);
            }
            return rows.size();
        } catch (IllegalArgumentException | IllegalStateException | DataAccessException e) {
            throw new IllegalStateException("user " + email + ": " + e.getMessage() + " — the users before are"
                    + " written; fix and rebuild again", e);
        }
    }

    /**
     * The rows the rebuild makes for one user from their transactions, unsaved: every SELL matched FIFO against lots
     * built from every BUY. Also how {@link RebuildBuyChargeCorrection} recomputes rows the rebuild once wrote, so the
     * two cannot disagree on how a row is made.
     */
    public List<TradeOutcomeEntity> rebuiltRows(String email) {
        // Step 2a: Read all BUY transactions for this user
        List<TransactionEntity> buyTransactions = findByEmailAndTransactionType(email, TransactionType.BUY);
        if (buyTransactions.isEmpty()) {
            log.debug("No BUY transactions for user {}, skipping", email);
            return List.of();
        }

        // Step 2b: Read all SELL transactions for this user
        List<TransactionEntity> sellTransactions = findByEmailAndTransactionType(email, TransactionType.SELL);
        if (sellTransactions.isEmpty()) {
            log.debug("No SELL transactions for user {}, skipping", email);
            return List.of();
        }

        // Sort sells by date
        sellTransactions.sort(Comparator.comparing(TransactionEntity::getTransactionDate));

        // Step 2c: Build BuyLots from BUYs
        List<BuyLot> buyLots = tradeMatchingService.buildLotsFromBuys(buyTransactions);

        // Step 2d: For each SELL, match against lots
        List<TradeOutcomeEntity> tradeOutcomes = new ArrayList<>();
        for (TransactionEntity sellTxn : sellTransactions) {
            SellRequest sellRequest = tradeMatchingService.toSellRequest(sellTxn);
            for (MatchedTrade matchedTrade : tradeMatchingService.matchSellToLots(sellRequest, buyLots)) {
                tradeOutcomes.add(tradeMatchingService.toTradeOutcomeEntity(matchedTrade));
            }
        }
        return tradeOutcomes;
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
