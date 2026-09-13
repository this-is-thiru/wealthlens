package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.brokercharges.service.ChargeAccountService;
import com.thiru.wealthlens.brokercharges.service.UserChargeService;
import com.thiru.wealthlens.portfolio.dto.ErasureReport;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases everything one user owns.
 *
 * <p>Lifted out of {@code PortfolioService}, which is where it grew and not where it belonged: the
 * list of collections a wipe is responsible for is a fact about the <em>user's records</em>, not
 * about the portfolio, and burying it among the trade paths is how two collections came to be
 * missed when the charges engine added them. One class, one list, one place to add to.
 *
 * <h2>Every delete is attempted, and every failure is reported</h2>
 * The deletes are independent without a working {@code MongoTransactionManager}, so stopping at the
 * first failure leaves strictly more behind than carrying on does. But carrying on used to mean
 * catching the failure, logging it, and answering "deleted successfully" anyway — telling a user
 * their data was gone while it was still there. Both halves matter: continue, and say what did not
 * work. With {@code app.mongodb.transactions-enabled=true} (Atlas replica set) the
 * {@link Transactional} annotation makes the whole wipe atomic and the question moot.
 *
 * <h2>What is not erased here</h2>
 * Insurance policies, salary profiles and tax computations carry this user's email too. They live
 * in modules {@code portfolio} may not depend on, so reaching them needs an application event
 * rather than a call, and each module would own its own erasure. Out of scope deliberately —
 * recorded as CE-5 in {@code docs/charges-engine/backlog.md} rather than left to be rediscovered.
 * The login credential in {@code auth} is also untouched: this clears a user's records, it does not
 * close their account.
 */
@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class UserRecordsErasureService {

    private final PortfolioRepository portfolioRepository;
    private final TradeOutcomeService tradeOutcomeService;
    private final TransactionService transactionService;
    private final ProfitAndLossService profitAndLossService;
    private final TemporaryTransactionService temporaryTransactionService;
    private final UserChargeService userChargeService;
    private final ChargeAccountService chargeAccountService;

    /**
     * Clears every collection this user owns, and reports what happened.
     *
     * <p>Ordered holdings first and charges last, which is the order they were written in. Nothing
     * depends on the order — each delete stands alone — but a partial wipe reads more sensibly when
     * what survives is the derived data rather than the source.
     */
    public ErasureReport erase(UserMail userMail) {
        String email = userMail.getEmail();
        log.info("Initiated deletion of all records of user: {}", email);

        Map<String, Runnable> deletes = new LinkedHashMap<>();
        deletes.put("assets", () -> portfolioRepository.deleteByEmail(email));
        deletes.put("trade_outcomes", () -> tradeOutcomeService.deleteByEmail(userMail));
        deletes.put("transactions", () -> transactionService.deleteTransactions(userMail));
        deletes.put("profit_and_loss", () -> profitAndLossService.deleteProfitAndLoss(userMail));
        // Not a collection of its own: temporary transactions are `transactions` rows carrying
        // status = TEMPORARY. This also clears lastly_performed_corporate_action, which would
        // otherwise suppress corporate actions when the same data is uploaded again.
        deletes.put("temporary_transactions", () -> temporaryTransactionService.deleteTemporaryTransaction(userMail));
        deletes.put("user_charges", () -> userChargeService.deleteByEmail(email));
        deletes.put("charge_accounts", () -> chargeAccountService.deleteByEmail(email));

        ErasureReport report = new ErasureReport();
        deletes.forEach((collection, delete) -> erase(collection, delete, email, report));

        if (report.isComplete()) {
            log.info("Deleted all records of user: {}", email);
        } else {
            log.error("Deletion of user {} left {} behind", email, report.getFailed());
        }
        return report;
    }

    private static void erase(String collection, Runnable delete, String email, ErasureReport report) {
        try {
            delete.run();
            report.recordCleared(collection);
            log.info("Deleted all {} for user: {}", collection, email);
        } catch (RuntimeException e) {
            report.recordFailed(collection);
            log.error("Failed to delete {} for user: {} — continuing with the remaining collections",
                    collection, email, e);
        }
    }
}
