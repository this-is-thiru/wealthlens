package com.thiru.wealthlens.brokercharges.service;

import com.thiru.wealthlens.brokercharges.config.ChargeEngineProperties;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeContext;
import com.thiru.wealthlens.brokercharges.dto.context.LotSlice;
import com.thiru.wealthlens.brokercharges.dto.enums.AmountBasis;
import com.thiru.wealthlens.brokercharges.dto.enums.ChargeEvent;
import com.thiru.wealthlens.brokercharges.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.dto.context.BuyContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitLossContext;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.service.ChargeRecordingGateway;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Phase B's adapter between the trade path and the engine.
 *
 * <p>The trade path hands over a {@link ProfitLossContext} and ignores what comes back. Nothing here
 * feeds cost basis; the point is to accumulate computed charges beside the user-entered ones so the
 * two can be compared on real data before the engine is trusted with the number (PRD OD-8).
 *
 * <p>Three properties are load-bearing and each is asserted rather than assumed:
 *
 * <ul>
 *   <li><b>Off by default.</b> Recording happens only when {@code app.charges.shadow-recording} is
 *       true. The flag is what makes this phase reversible without a revert.
 *   <li><b>No asset-type gate.</b> Every asset type reaches the engine (FR-8). The
 *       {@code assetType == EQUITY} gate in {@code ProfitAndLossService} guards the <em>superseded</em>
 *       implementation, which resolves a rate card by broker and date with no asset-type dimension
 *       at all — removing it would price a mutual fund as equity. It stays until Phase C deletes the
 *       path behind it. A non-equity trade with no card of its own is not silently free: it records
 *       a resolution saying so, and the gaps report lists it.
 *   <li><b>Nothing escapes.</b> Every failure is caught and logged. A shadow record exists to be
 *       compared later; a trade that failed to save because its shadow copy could not be priced
 *       would be strictly worse than having no shadow copy.
 * </ul>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ChargeRecordingGatewayImpl implements ChargeRecordingGateway {

    /**
     * {@code ProfitLossContext} carries no segment; the field arrives on the portfolio types in
     * Phase C. Until then every trade is priced as delivery, which is what the existing flow already
     * assumes — it has no intraday concept either.
     */
    private static final TradeSegment ASSUMED_SEGMENT = TradeSegment.DELIVERY;

    /** Cash-segment instruments trade in units of one; derivatives do not reach this path yet. */
    private static final int CASH_SEGMENT_LOT_SIZE = 1;

    private final ChargeEngineProperties chargeEngineProperties;
    private final UserChargeService userChargeService;

    @Override
    public Optional<ChargeComputation> record(UserMail userMail, ProfitLossContext profitLossContext) {
        // The master switch outranks the phase flag, so switching the engine off does not also
        // require finding and clearing shadow-recording. Unlike every other entry point this one
        // refuses silently rather than throwing: it sits in the trade path, and the trade must save.
        if (!chargeEngineProperties.engineEnabled() || !chargeEngineProperties.shadowRecording()) {
            return Optional.empty();
        }
        if (userMail == null || profitLossContext == null) {
            return Optional.empty();
        }

        ChargeEvent event = toEvent(profitLossContext.transactionType());
        if (event == null) {
            log.error("Cannot shadow-record transaction {}: unsupported transaction type {}",
                    profitLossContext.transactionId(), profitLossContext.transactionType());
            return Optional.empty();
        }

        try {
            ChargeComputation computation = userChargeService.computeAndRecord(toContext(userMail, profitLossContext, event));
            log.debug("Shadow-recorded {} {} on {}: {} across {} lines, resolution {}",
                    event, profitLossContext.stockCode(), profitLossContext.date(),
                    computation.total(), computation.lines().size(), computation.resolution());
            return Optional.of(computation);
        } catch (RuntimeException e) {
            // Deliberately swallowed. The live trade must complete whatever the engine does, and a
            // missing shadow row is visible in the reconciliation report as an absent computation.
            log.error("Shadow charge recording failed for transaction {} ({} {} on {}); the trade is unaffected",
                    profitLossContext.transactionId(), event, profitLossContext.stockCode(),
                    profitLossContext.date(), e);
            return Optional.empty();
        }
    }

    private static ChargeEvent toEvent(TransactionType transactionType) {
        if (transactionType == null) {
            return null;
        }
        return switch (transactionType) {
            case BUY -> ChargeEvent.BUY;
            case SELL -> ChargeEvent.SELL;
        };
    }

    private static ChargeContext toContext(UserMail userMail, ProfitLossContext context, ChargeEvent event) {
        return new ChargeContext(
                userMail.getEmail(),
                context.transactionId(),
                null,
                context.stockCode(),
                context.accountHolder(),
                context.brokerName(),
                context.assetType(),
                ASSUMED_SEGMENT,
                context.exchangeName(),
                null,
                event,
                context.date(),
                context.actionType(),
                context.quantity(),
                context.price(),
                CASH_SEGMENT_LOT_SIZE,
                baseAmounts(context),
                lots(context),
                new HashMap<>());
    }

    private static Map<AmountBasis, Double> baseAmounts(ProfitLossContext context) {
        Map<AmountBasis, Double> baseAmounts = new EnumMap<>(AmountBasis.class);
        baseAmounts.put(AmountBasis.TURNOVER, context.price() * context.quantity());
        return baseAmounts;
    }

    /**
     * A disposal consumes lots; a purchase creates one and consumes none.
     *
     * <p>{@code buyContexts} is populated only on the sell path, where {@code PortfolioService}
     * already walks the holdings FIFO. That walk is the same one a holding-period charge needs, so
     * the lots are carried across rather than recomputed — an independently derived second opinion
     * on which lots a sell consumed is a way for the two to disagree.
     */
    private static List<LotSlice> lots(ProfitLossContext context) {
        List<BuyContext> buyContexts = context.buyContexts();
        if (buyContexts == null || buyContexts.isEmpty()) {
            return List.of();
        }
        return buyContexts.stream()
                .map(buy -> new LotSlice(buy.quantity(), buy.date(), buy.price()))
                .toList();
    }
}
