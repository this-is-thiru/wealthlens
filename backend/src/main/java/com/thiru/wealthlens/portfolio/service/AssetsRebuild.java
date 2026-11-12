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
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Replays one user's transactions into lots and compares them with the stored {@code assets}, writing nothing
 * (W-283a2, D-582; {@code docs/trade-ledger/assets-rebuild.md}). The write is W-283a3.
 *
 * <p>Events run by date; on one date the corporate actions come first, because a trade dated on a record date is held
 * until the action is performed. Each trade takes the flow it recorded (W-283a1); one stored before that is shaped
 * from what survives of it — the stored lot that lists a buy, the {@code trade_outcomes} rows of a sale (C2a) — and
 * every such reading is listed in the report.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AssetsRebuild {

    private final TransactionRepository transactionRepository;
    private final PortfolioRepository portfolioRepository;
    private final TradeOutcomeRepository tradeOutcomeRepository;
    private final UserChargeService userChargeService;
    private final PerformedCorporateActions performedCorporateActions;
    private final CorporateActionService corporateActionService;
    private final ChargeEngineProperties chargeEngineProperties;

    /** One event of the replay; {@code rank} 0 is a corporate action, 1 a trade, so actions go first on a date. */
    private record Event(LocalDate date, int rank, String order, Runnable apply) {
    }

    /** What the replay read from somewhere other than the trade, listed in the report. */
    private record Readings(List<String> shapeFromStoredLots, List<String> shapeNotFound,
                            List<String> saleFlowFromOutcomes, List<String> saleFlowUnknown) {

        static Readings empty() {
            return new Readings(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    /** The stored rows that shape a trade without a recorded flow. */
    private record Sources(Map<String, AssetEntity> storedLotByBuy, Map<String, UserChargeEntity> buyCharges,
                           Set<String> v2Sales, Set<String> salesWithRows) {
    }

    /**
     * @throws IllegalArgumentException if a sale finds less held than it sold, or a split's ratio does not parse
     */
    public AssetsRebuildReport rebuild(UserMail userMail) {
        String email = userMail.getEmail();
        List<TransactionEntity> all = transactionRepository.findByEmail(email);
        List<TransactionEntity> trades = all.stream().filter(trade -> trade.getStatus() != TransactionStatus.TEMPORARY)
                .toList();
        List<AssetEntity> stored = portfolioRepository.findByEmail(email);
        Sources sources = sourcesFor(email, trades, stored);
        PerformedCorporateActions.Replay actions = performedCorporateActions.of(email, brokersByStock(trades));

        LotLedger ledger = new LotLedger(corporateActionService::demergeLots);
        Readings readings = Readings.empty();
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

        List<AssetsRebuildReport.LotComparison> lots = LotComparisons.of(stored, ledger.lots());
        int differing = (int) lots.stream().filter(lot -> !lot.differingFields().isEmpty()).count();
        return new AssetsRebuildReport(email, differing, lots, readings.shapeFromStoredLots(), readings.shapeNotFound(),
                readings.saleFlowFromOutcomes(), readings.saleFlowUnknown(),
                actions.performed().stream().map(PerformedCorporateActions.BrokerAction::label).toList(),
                actions.pending().stream().map(PerformedCorporateActions.BrokerAction::label).toList(),
                all.size() - trades.size());
    }

    private Sources sourcesFor(String email, List<TransactionEntity> trades, List<AssetEntity> stored) {
        Map<String, AssetEntity> storedLotByBuy = new HashMap<>();
        stored.forEach(lot -> lot.getBuyTransactionIds().forEach(buyId -> storedLotByBuy.put(buyId, lot)));
        List<String> buyIds = trades.stream().filter(trade -> trade.getTransactionType() == TransactionType.BUY)
                .map(TransactionEntity::getId).toList();
        Set<String> v2Sales = tradeOutcomeRepository.findByEmailAndSellChargeBreakupExists(email, true).stream()
                .map(TradeOutcomeEntity::getSourceSellTransactionId).collect(Collectors.toSet());
        Set<String> salesWithRows = tradeOutcomeRepository.findByEmail(email).stream()
                .map(TradeOutcomeEntity::getSourceSellTransactionId).collect(Collectors.toSet());
        return new Sources(storedLotByBuy, userChargeService.findForTransactions(email, buyIds), v2Sales, salesWithRows);
    }

    private static Map<String, Set<BrokerName>> brokersByStock(List<TransactionEntity> trades) {
        Map<String, Set<BrokerName>> brokers = new TreeMap<>();
        trades.forEach(trade -> brokers.computeIfAbsent(trade.getStockCode(), stock -> EnumSet.noneOf(BrokerName.class))
                .add(trade.getBrokerName()));
        return brokers;
    }

    private void replay(LotLedger ledger, TransactionEntity trade, Sources sources, Readings readings) {
        if (trade.getTransactionType() == TransactionType.SELL) {
            ledger.sell(trade, saleFlowOf(trade, sources, readings));
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

    private static TradeFlow saleFlowOf(TransactionEntity sale, Sources sources, Readings readings) {
        if (sale.getTradeFlow() != null) {
            return sale.getTradeFlow();
        }
        if (sources.v2Sales().contains(sale.getId())) {
            readings.saleFlowFromOutcomes().add(sale.getId());
            return TradeFlow.V2;
        }
        if (sources.salesWithRows().contains(sale.getId())) {
            readings.saleFlowFromOutcomes().add(sale.getId());
            return TradeFlow.V1;
        }
        readings.saleFlowUnknown().add(sale.getId());
        return TradeFlow.V2;
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
