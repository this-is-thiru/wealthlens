package com.thiru.wealthlens.portfolio.dto.context;

/**
 * The outcome of recording a trade: its transaction id, and whether this submission created it.
 *
 * <p>The {@code replay} flag is the point. Returning only an id — which is what the
 * {@code sourceTempTransactionId} de-duplication did — suppresses the duplicate <em>row</em> while
 * the caller goes on to apply the trade to the portfolio again, adding the holding twice and
 * updating profit and loss twice. The caller has to be told, so it can stop.
 *
 * @param transactionId the transaction this submission refers to, new or pre-existing
 * @param replay        true when a matching transaction already existed and nothing was written
 */
public record TransactionRecord(String transactionId, boolean replay) {

    public static TransactionRecord created(String transactionId) {
        return new TransactionRecord(transactionId, false);
    }

    public static TransactionRecord replayOf(String transactionId) {
        return new TransactionRecord(transactionId, true);
    }
}
