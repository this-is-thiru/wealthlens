package com.thiru.wealthlens.portfolio.dto.enums;

/**
 * Which of the two live trade flows received a trade: {@code V1} ({@code POST …/transaction} and the upload) or
 * {@code V2} ({@code POST …/transaction/v2}).
 *
 * <p>Recorded on a trade held back by a corporate action, so that its redrive records it the way it would have been
 * recorded had it not waited — a lot per buy and the charge engine for V2, a merged same-day lot for V1 (W-270,
 * D-555); and on every stored trade, so a rebuild of {@code assets} gives it the same lot shape (W-283a1, D-580). Set
 * by the server from the route, never by the caller.
 */
public enum TradeFlow {
    V1,
    V2
}
