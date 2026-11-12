package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.corporate.dto.SplitRatio;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.service.CorporateActionService;
import com.thiru.wealthlens.portfolio.dto.OrderTimeQuantity;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeFlow;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * One user's lots replayed in memory, event by event, by the rules the live trade paths apply — the heart of the
 * {@code assets} rebuild (W-283a2, D-582; {@code docs/trade-ledger/assets-rebuild.md}).
 *
 * <p>Nothing here reads or writes a store, so a replay can be compared with what is stored before anything changes.
 * Every rule that also runs live is called, not copied: a sale's allocation ({@link LotChargeAllocator}), the sale
 * tolerances ({@link PortfolioService#takesWholeLot}), a split ({@link CorporateActionService#splitLots}) and a
 * demerger (passed in, {@link CorporateActionService#demergeLots}). The ordering of events is the caller's.
 */
final class LotLedger {

    private final List<AssetEntity> lots = new ArrayList<>();
    private final BiFunction<CorporateActionEntity, List<AssetEntity>, List<AssetEntity>> demerger;

    LotLedger(BiFunction<CorporateActionEntity, List<AssetEntity>, List<AssetEntity>> demerger) {
        this.demerger = demerger;
    }

    /**
     * A buy, shaped as its flow shaped it live: V1 merges into the lot of the same stock, broker, account holder and
     * date at a weighted-average price, adding its charges and order time ({@code PortfolioService.buyStock}); V2
     * gives it its own lot ({@code buyStockV2}).
     *
     * @param brokerCharges the lot's broker charge when it is not the one entered: a V2 buy's computed total
     */
    void buy(TransactionEntity buy, TradeFlow lotShape, OptionalDouble brokerCharges) {
        if (lotShape == TradeFlow.V1) {
            AssetEntity sameDay = lots.stream().filter(lot -> sameHolding(lot, buy)
                    && Objects.equals(lot.getSegment(), buy.getSegment())
                    && Objects.equals(lot.getTransactionDate(), buy.getTransactionDate())).findFirst().orElse(null);
            if (sameDay != null) {
                mergeInto(sameDay, buy);
                return;
            }
        }
        AssetEntity lot = lotOf(buy);
        lot.setBrokerCharges(brokerCharges.orElse(buy.getBrokerCharges()));
        if (lotShape == TradeFlow.V1) {
            lot.getOrderTimeQuantities().add(orderTime(buy));
        }
        lot.getBuyTransactionIds().add(buy.getId());
        lots.add(lot);
    }

    /**
     * A bonus allotment, built as {@code CorporateActionService.processBonusShares} builds it: a copy of the first lot
     * of the stock and broker held before the record date, at price 0, dated by the allotment, with nil cost, no sales
     * and no earlier actions ({@code asAllotment}, W-291); every such lot and the new one marked with the action. With
     * no such lot, the allotment is its own lot.
     *
     * @return whether a held lot was found to copy
     */
    boolean allot(TransactionEntity bonus, CorporateActionEntity action) {
        List<AssetEntity> held = heldBefore(action.getStockCode(), bonus.getBrokerName(), action.getRecordDate());
        if (held.isEmpty()) {
            buy(bonus, TradeFlow.V2, OptionalDouble.empty());
            return false;
        }
        AssetEntity allotted = TJsonMapper.copy(held.getFirst(), AssetEntity.class);
        allotted.setId(null);
        allotted.setQuantity(bonus.getQuantity());
        allotted.setPrice(0);
        allotted.setTransactionDate(bonus.getTransactionDate());
        allotted.setOrderTimeQuantities(new ArrayList<>());
        allotted.setCorporateActionType(CorporateActionType.BONUS);
        allotted.setBuyTransactionIds(new ArrayList<>(List.of(bonus.getId())));
        CorporateActionService.asAllotment(allotted);
        held.add(allotted);
        held.forEach(lot -> lot.getCorporateActions().add(TJsonMapper.copy(action, CorporateActionEntity.class)));
        lots.add(allotted);
        return true;
    }

    /**
     * A sale, FIFO over the lots of its stock, broker and account holder dated on or before it, oldest first and in
     * the order they were made on one date. Every sale records its share of each lot's buy charges on the lot — V1 in
     * {@code allocateSale}, V2 in {@code TradeOutcomeRecorder.rowFigures} (W-293, D-588). A lot with nothing left is
     * removed, as live.
     *
     * @throws IllegalArgumentException if the lots hold less than the sale, naming it and the shortfall
     */
    void sell(TransactionEntity sale) {
        List<AssetEntity> eligible = lots.stream()
                .filter(lot -> sameHolding(lot, sale) && !lot.getTransactionDate().isAfter(sale.getTransactionDate()))
                .sorted(Comparator.comparing(AssetEntity::getTransactionDate))
                .toList();
        double held = eligible.stream().mapToDouble(AssetEntity::getQuantity).sum();
        if (held < sale.getQuantity() && !PortfolioService.takesWholeLot(held, sale.getQuantity())) {
            throw new IllegalArgumentException("Sale " + sale.getId() + " of " + sale.getQuantity() + " "
                    + sale.getStockCode() + " on " + sale.getTransactionDate() + " finds " + held + " held; "
                    + (sale.getQuantity() - held) + " short");
        }
        double left = sale.getQuantity();
        for (AssetEntity lot : eligible) {
            if (!PortfolioService.isLeftToSell(left)) {
                break;
            }
            double lotQuantity = lot.getQuantity();
            double taken = PortfolioService.takesWholeLot(left, lotQuantity) ? lotQuantity : left;
            lot.getSellTransactionIds().add(sale.getId());
            LotChargeAllocator.deductBroker(lot, taken, lotQuantity);
            LotChargeAllocator.deductMisc(lot, taken, lotQuantity);
            lot.setQuantity(taken == lotQuantity ? 0D : lotQuantity - taken);
            left -= taken;
        }
        lots.removeIf(PortfolioService::hasNothingLeft);
    }

    /** A performed split, on the lots of its stock with this broker dated before its record date. */
    void split(CorporateActionEntity action, BrokerName brokerName) {
        CorporateActionService.splitLots(action, SplitRatio.parse(action.getRatio()),
                heldBefore(action.getStockCode(), brokerName, action.getRecordDate()));
    }

    /** A performed demerger, on the same lots; the demerged lots join the ledger after them, as they are saved live. */
    void demerge(CorporateActionEntity action, BrokerName brokerName) {
        List<AssetEntity> held = heldBefore(action.getStockCode(), brokerName, action.getRecordDate());
        if (held.isEmpty()) {
            return;
        }
        Set<AssetEntity> known = Collections.newSetFromMap(new IdentityHashMap<>());
        known.addAll(lots);
        demerger.apply(action, held).stream().filter(lot -> !known.contains(lot)).forEach(lots::add);
    }

    /** The lots as they stand, in the order they were made. */
    List<AssetEntity> lots() {
        return List.copyOf(lots);
    }

    private List<AssetEntity> heldBefore(String stockCode, BrokerName brokerName, LocalDate recordDate) {
        return new ArrayList<>(lots.stream().filter(lot -> stockCode.equals(lot.getStockCode())
                && brokerName == lot.getBrokerName() && lot.getTransactionDate().isBefore(recordDate)).toList());
    }

    private static boolean sameHolding(AssetEntity lot, TransactionEntity trade) {
        return Objects.equals(lot.getStockCode(), trade.getStockCode()) && lot.getBrokerName() == trade.getBrokerName()
                && Objects.equals(lot.getAccountHolder(), trade.getAccountHolder());
    }

    private static void mergeInto(AssetEntity lot, TransactionEntity buy) {
        double quantity = lot.getQuantity() + buy.getQuantity();
        lot.setPrice((lot.getQuantity() * lot.getPrice() + buy.getPrice() * buy.getQuantity()) / quantity);
        lot.setQuantity(quantity);
        lot.setBrokerCharges(buy.getBrokerCharges() + lot.getBrokerCharges());
        lot.setMiscCharges(buy.getMiscCharges() + lot.getMiscCharges());
        lot.getOrderTimeQuantities().add(orderTime(buy));
        lot.getBuyTransactionIds().add(buy.getId());
    }

    /** The lot {@code AssetRequest.asAsset} builds, from what the transaction kept of the request. */
    private static AssetEntity lotOf(TransactionEntity trade) {
        AssetEntity lot = new AssetEntity();
        lot.setStockCode(trade.getStockCode());
        lot.setEmail(trade.getEmail());
        lot.setExchangeName(trade.getExchangeName());
        lot.setStockName(trade.getStockName());
        lot.setBrokerName(trade.getBrokerName());
        lot.setPrice(trade.getPrice());
        lot.setQuantity(trade.getQuantity());
        lot.setMiscCharges(trade.getMiscCharges());
        lot.setAssetType(trade.getAssetType());
        lot.setMaturityDate(trade.getMaturityDate());
        lot.setOrderId(trade.getOrderId());
        lot.setTransactionDate(trade.getTransactionDate());
        lot.setSegment(trade.getSegment());
        lot.setAccountType(trade.getAccountType());
        lot.setAccountHolder(trade.getAccountHolder());
        lot.setOrderTimeQuantities(new ArrayList<>());
        lot.setTransactionType(trade.getTransactionType());
        lot.setTimezoneId(trade.getTimezoneId());
        lot.setComment(trade.getComment());
        return lot;
    }

    private static OrderTimeQuantity orderTime(TransactionEntity trade) {
        OrderTimeQuantity orderTime = new OrderTimeQuantity();
        orderTime.setOrderExecutionTime(trade.getOrderExecutionTime());
        orderTime.setQuantity(trade.getQuantity());
        return orderTime;
    }
}
