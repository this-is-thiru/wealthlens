package com.thiru.wealthlens.portfolio.service;

import com.mongodb.MongoException;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.shared.dto.RedriveResult;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.exception.TradeCollisionException;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import java.time.Duration;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Records a trade again when its transaction collided with another trade being recorded at the same moment.
 *
 * <p>Every trade rewrites the user's one profit-and-loss document for the year, so two trades at once abort one of
 * them with Mongo's {@code TransientTransactionError} — the database's word that the whole transaction may be run
 * again. An aborted attempt left nothing behind, so the trade is attempted again, from a fresh copy of its request
 * because an attempt changes the request it is given. Any other failure is answered at once. When every attempt
 * collides, the caller is told the trade was not recorded (W-269, D-554).
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

    /** True when the database labelled the failure, or one it was caused by, safe to run again as a whole. */
    static boolean isCollision(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof MongoException mongo
                    && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) {
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
