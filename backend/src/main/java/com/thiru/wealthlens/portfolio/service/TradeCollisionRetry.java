package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.shared.dto.RedriveResult;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.exception.TradeCollisionException;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import java.time.Duration;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Records a trade again when its transaction collided with another trade being recorded at the same moment.
 *
 * <p>Every trade rewrites the user's one profit-and-loss row for the year, and may merge into an existing lot in
 * {@code assets}. A collision (W-337e, D-949 C5a)
 * surfaces as one of three things: an
 * {@link OptimisticLockingFailureException} from {@code ProfitAndLossEntity} or {@code AssetEntity}'s own
 * {@code @Version} check (an update raced another update); a lock timeout or deadlock
 * ({@link CannotAcquireLockException}, Postgres SQLState {@code 40P01}); or, when two attempts both found no
 * {@code profit_and_loss} row yet for the user's year and both tried to create it, a
 * {@link DataIntegrityViolationException} on {@code pnl_user_year_idx} (a create raced another create — retrying
 * finds the row the other attempt just inserted and updates it instead). An aborted attempt left nothing behind, so
 * the trade is attempted again, from a fresh copy of its request because an attempt changes the request it is
 * given. Any other failure is answered at once. When every attempt collides, the caller is told the trade was not
 * recorded (W-269, D-554).
 *
 * <p>Sits outside {@link PortfolioService}'s transactions, so each attempt is a transaction of its own.
 */
@Component
@RequiredArgsConstructor
public class TradeCollisionRetry {

    /**
     * Ten trades at once need at most nine re-runs between them; the longest wait, 20 + 40 + 80 + 160 + 320 ms, stays
     * far inside a client's timeout. A choice, not a sourced figure (W-269 plan).
     */
    private static final RetryPolicy ON_COLLISION = RetryPolicy.builder()
            .maxRetries(5)
            .delay(Duration.ofMillis(20))
            .multiplier(2)
            .maxDelay(Duration.ofMillis(500))
            .jitter(Duration.ofMillis(10))
            .predicate(TradeCollisionRetry::isCollision)
            .build();

    static final String NOT_RECORDED = "Another trade was being recorded at the same moment; this one was not recorded"
            + " — send it again";

    private final RetryTemplate retryTemplate = new RetryTemplate(ON_COLLISION);
    private final PortfolioService portfolioService;

    public String addTransaction(UserMail userMail, AssetRequest assetRequest) {
        return attempt(() -> portfolioService.addTransaction(userMail, copyOf(assetRequest), new ArrayList<>()));
    }

    public String addTransactionV2(UserMail userMail, AssetRequest assetRequest) {
        return attempt(() -> portfolioService.addTransactionV2(userMail, copyOf(assetRequest), new ArrayList<>()));
    }

    public String uploadTransactions(UserMail userMail, String quarter, MultipartFile file) {
        return attempt(() -> portfolioService.uploadTransactions(userMail, quarter, file));
    }

    public RedriveResult redriveTemporaryTransactions(UserMail userMail) {
        return attempt(() -> portfolioService.redriveTemporaryTransactions(userMail));
    }

    /** The one unique index two concurrent first-writers can both race to insert against. */
    private static final String PNL_CREATE_RACE_CONSTRAINT = "pnl_user_year_idx";

    /** True when the database labelled the failure, or one it was caused by, safe to run again as a whole. */
    static boolean isCollision(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockingFailureException || cause instanceof CannotAcquireLockException) {
                return true;
            }
            if (cause instanceof ConstraintViolationException constraintViolation
                    && PNL_CREATE_RACE_CONSTRAINT.equals(constraintViolation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private <R> R attempt(Retryable<R> trade) {
        try {
            return retryTemplate.execute(trade);
        } catch (RetryException stopped) {
            Throwable last = stopped.getCause();
            if (isCollision(last)) {
                throw new TradeCollisionException(NOT_RECORDED, last);
            }
            if (last instanceof RuntimeException unchecked) {
                throw unchecked;
            }
            if (last instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("A trade failed with a checked exception, which none declares", last);
        }
    }

    private static AssetRequest copyOf(AssetRequest assetRequest) {
        return TJsonMapper.copy(assetRequest, AssetRequest.class);
    }
}
