package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.brokercharges.config.ChargeEngineProperties;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.service.UserChargeService;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.service.CorporateActionService;
import com.thiru.wealthlens.portfolio.dto.AssetsRebuildReport;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeFlow;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replays one user's transactions into lots, compares them with the stored {@code assets} and, unless a dry run,
 * replaces the stored lots with them (W-283a2, D-582; W-283a3, D-586; {@code docs/trade-ledger/assets-rebuild.md}).
 *
 * <p>Events run by date; on one date the corporate actions come first, because a trade dated on a record date is held
 * until the action is performed. A buy takes the flow it recorded (W-283a1); one stored before that is shaped from
 * the stored lot that lists it, and every such reading is listed in the report. A sale needs no flow: both record
 * their share of a lot's buy charges the same way (W-293, D-588).
 */
@Service
@Log4j2
@Transactional
@RequiredArgsConstructor
public class AssetsRebuild {

    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final UserChargeService userChargeService;
    private final PerformedCorporateActions performedCorporateActions;
    private final CorporateActionService corporateActionService;
    private final ChargeEngineProperties chargeEngineProperties;

    /** One event of the replay; {@code rank} 0 is a corporate action, 1 a trade, so actions go first on a date. */
    private record Event(LocalDate date, int rank, String order, Runnable apply) {
    }

    /** What the replay read from somewhere other than the trade, listed in the report. */
    private record Readings(List<String> shapeFromStoredLots, List<String> shapeNotFound) {

        static Readings empty() {
            return new Readings(new ArrayList<>(), new ArrayList<>());
        }
    }

    /** The stored rows that shape a buy without a recorded flow, and a V2 buy's computed charges. */
    private record Sources(Map<String, AssetEntity> storedLotByBuy, Map<String, UserChargeEntity> buyCharges) {
    }

    /**
     * @param dryRun whether to compare only; a real run replaces the user's lots in this transaction (C1a: even when
     *     the replay listed trades it could not confirm — the dry run named them)
     * @throws IllegalArgumentException if a sale finds less held than it sold, or a split's ratio does not parse
     */
    public AssetsRebuildReport rebuild(UserMail userMail, boolean dryRun) {
        String email = userMail.getEmail();
        List<TransactionEntity> all = transactionRepository.findByEmail(email);
        List<TransactionEntity> trades = all.stream().filter(trade -> trade.getStatus() != TransactionStatus.TEMPORARY)
                .toList();
        List<AssetEntity> stored = portfolioRepository.findByEmail(email);
        Sources sources = sourcesFor(email, trades, stored);
        PerformedCorporateActions.Replay actions = performedCorporateActions.of(email, brokersByStock(trades));

        LotLedger ledger = new LotLedger(corporateActionService::demergeLots);
        Readings readings = Readings.empty();
        replayAll(ledger, actions, trades, sources, readings);

        List<LotComparisons.LotPair> pairs = LotComparisons.pairs(stored, ledger.lots());
        List<AssetsRebuildReport.LotComparison> lots = LotComparisons.of(pairs);
        int differing = (int) lots.stream().filter(lot -> !lot.differingFields().isEmpty()).count();
        int kept = (int) pairs.stream().filter(pair -> pair.stored().isPresent() && pair.rebuilt().isPresent()).count();
        List<String> newLots = pairs.stream().filter(pair -> pair.stored().isEmpty())
                .map(pair -> labelOf(pair.either())).toList();
        int written = dryRun ? 0 : write(email, pairs);
        return new AssetsRebuildReport(email, dryRun, differing, written, kept, newLots, lots,
                readings.shapeFromStoredLots(), readings.shapeNotFound(),
                actions.performed().stream().map(PerformedCorporateActions.BrokerAction::label).toList(),
                actions.pending().stream().map(PerformedCorporateActions.BrokerAction::label).toList(),
                all.size() - trades.size());
    }

    /** Every event, by date, corporate actions first on a date. */
    private void replayAll(LotLedger ledger, PerformedCorporateActions.Replay actions, List<TransactionEntity> trades,
                           Sources sources, Readings readings) {
        List<Event> events = new ArrayList<>();
        for (PerformedCorporateActions.BrokerAction performed : actions.performed()) {
            CorporateActionEntity action = performed.action();
            events.add(new Event(action.getRecordDate(), 0, action.getId(), () -> {
                if (action.getType() == CorporateActionType.STOCK_SPLIT) {
                    ledger.split(action, performed.brokerName());
                } else {
                    ledger.demerge(action, performed.brokerName());
                }
            }));
        }
        for (TransactionEntity trade : trades) {
            events.add(new Event(trade.getTransactionDate(), 1, trade.getId(),
                    () -> replay(ledger, trade, sources, readings)));
        }
        events.sort(Comparator.comparing(Event::date).thenComparingInt(Event::rank).thenComparing(Event::order));
        events.forEach(event -> event.apply().run());
    }

    /**
     * Replaces the user's lots with the rebuilt ones. A lot paired with a stored one keeps its id, so the
     * {@code trade_outcomes} naming it still do (C3a of D-582), and what the replay cannot rebuild: its order times, its
     * list of corporate actions and when it was created (C2a of D-586). Its figures are the replay's.
     */
    private int write(String email, List<LotComparisons.LotPair> pairs) {
        List<AssetEntity> lots = new ArrayList<>();
        for (LotComparisons.LotPair pair : pairs) {
            if (pair.rebuilt().isEmpty()) {
                continue;
            }
            AssetEntity rebuilt = pair.rebuilt().get();
            pair.stored().ifPresent(stored -> keepFrom(stored, rebuilt));
            lots.add(rebuilt);
        }
        portfolioRepository.deleteByEmail(email);
        portfolioRepository.saveAll(lots);
        log.info("Rebuilt assets for {}: {} lots written", email, lots.size());
        return lots.size();
    }

    private static void keepFrom(AssetEntity stored, AssetEntity rebuilt) {
        rebuilt.setId(stored.getId());
        rebuilt.setOrderTimeQuantities(stored.getOrderTimeQuantities());
        rebuilt.setCorporateActions(stored.getCorporateActions());
        rebuilt.getAuditMetadata().setCreatedAt(stored.getAuditMetadata().getCreatedAt());
        rebuilt.getAuditMetadata().setCreatedBy(stored.getAuditMetadata().getCreatedBy());
    }

    private static String labelOf(AssetEntity lot) {
        return lot.getStockCode() + " " + lot.getTransactionDate() + " " + lot.getBuyTransactionIds();
    }

    private Sources sourcesFor(String email, List<TransactionEntity> trades, List<AssetEntity> stored) {
        Map<String, AssetEntity> storedLotByBuy = new HashMap<>();
        stored.forEach(lot -> lot.getBuyTransactionIds().forEach(buyId -> storedLotByBuy.put(buyId, lot)));
        List<String> buyIds = trades.stream().filter(trade -> trade.getTransactionType() == TransactionType.BUY)
                .map(TransactionEntity::getId).toList();
        return new Sources(storedLotByBuy, userChargeService.findForTransactions(email, buyIds));
    }

    private static Map<String, Set<BrokerName>> brokersByStock(List<TransactionEntity> trades) {
        Map<String, Set<BrokerName>> brokers = new TreeMap<>();
        trades.forEach(trade -> brokers.computeIfAbsent(trade.getStockCode(), stock -> EnumSet.noneOf(BrokerName.class))
                .add(trade.getBrokerName()));
        return brokers;
    }

    private void replay(LotLedger ledger, TransactionEntity trade, Sources sources, Readings readings) {
        if (trade.getTransactionType() == TransactionType.SELL) {
            ledger.sell(trade);
            return;
        }
        Optional<CorporateActionEntity> bonus = bonusOf(trade);
        if (bonus.isPresent()) {
            if (!ledger.allot(trade, bonus.get())) {
                readings.shapeNotFound().add(trade.getId());
            }
            return;
        }
        if (trade.getTradeFlow() != null) {
            ledger.buy(trade, trade.getTradeFlow(),
                    trade.getTradeFlow() == TradeFlow.V2 ? computedCharges(trade, sources) : OptionalDouble.empty());
            return;
        }
        AssetEntity storedLot = sources.storedLotByBuy().get(trade.getId());
        if (storedLot == null) {
            readings.shapeNotFound().add(trade.getId());
            ledger.buy(trade, TradeFlow.V2, OptionalDouble.empty());
        } else if (storedLot.getBuyTransactionIds().size() > 1) {
            readings.shapeFromStoredLots().add(trade.getId());
            ledger.buy(trade, TradeFlow.V1, OptionalDouble.empty());
        } else {
            readings.shapeFromStoredLots().add(trade.getId());
            ledger.buy(trade, TradeFlow.V2, OptionalDouble.of(storedLot.getBrokerCharges()));
        }
    }

    /** A V2 buy's lot carries the computed total when the engine priced it and it is the cost basis (W-223). */
    private OptionalDouble computedCharges(TransactionEntity buy, Sources sources) {
        UserChargeEntity row = sources.buyCharges().get(buy.getId());
        boolean hasFigure = row != null && (row.getTotalCharges() != 0
                || row.getResolution() != null && !row.getResolution().isUnresolved());
        return chargeEngineProperties.authoritative() && hasFigure
                ? OptionalDouble.of(row.getTotalCharges()) : OptionalDouble.empty();
    }

    /** A bonus allotment is a BUY marked BONUS, carrying the action it came from (CorporateActionService:327 – 345). */
    private static Optional<CorporateActionEntity> bonusOf(TransactionEntity trade) {
        if (trade.getCorporateActionType() != CorporateActionType.BONUS) {
            return Optional.empty();
        }
        return trade.getCorporateActions().stream()
                .filter(action -> action.getType() == CorporateActionType.BONUS)
                .reduce((first, last) -> last);
    }
}
