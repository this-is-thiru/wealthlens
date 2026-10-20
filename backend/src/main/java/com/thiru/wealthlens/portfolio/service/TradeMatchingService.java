package com.thiru.wealthlens.portfolio.service;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.portfolio.dto.Acquisition;
import com.thiru.wealthlens.portfolio.dto.context.TradeOutcomeContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.holding.HoldingPeriodService;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Service that extracts and encapsulates FIFO matching logic for trades.
 * Does NOT write to any repository — only matches buys to sells and returns MatchedTrade results.
 * <p>
 * Same-day buy merging uses weighted average price: newPrice = (Σ qty_i * price_i) / Σ qty_i
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class TradeMatchingService {

    private final HoldingPeriodService holdingPeriodService;

    /**
     * Builds virtual BuyLots from BUY TransactionEntity records.
     * Merges same-day buys (same stock, broker, holder, date) with weighted average price,
     * exactly like PortfolioService.buyStock().
     */
    public List<BuyLot> buildLotsFromBuys(List<TransactionEntity> buyTransactions) {
        // Group by composite key: email + stockCode + brokerName + accountHolder + transactionDate
        Map<String, List<TransactionEntity>> grouped = buyTransactions.stream()
                .collect(Collectors.groupingBy(txn -> buildCompositeKey(txn)));

        List<BuyLot> lots = new ArrayList<>();
        for (Map.Entry<String, List<TransactionEntity>> entry : grouped.entrySet()) {
            List<TransactionEntity> txns = entry.getValue();
            if (txns.isEmpty()) continue;
            lots.add(lotOf(txns));
        }

        // Sort by buyDate
        lots.sort(Comparator.comparing(BuyLot::getBuyDate));
        return lots;
    }

    /** One lot from one key's same-day buys: their quantities summed, the price weighted, the charges summed. */
    private static BuyLot lotOf(List<TransactionEntity> txns) {
        TransactionEntity first = txns.getFirst();
        SameDayBuys merged = new SameDayBuys();
        txns.forEach(merged::add);

        double newPrice = merged.totalQuantity > 0 ? merged.weightedPriceSum / merged.totalQuantity : 0;

        // Synthetic ID for the lot
        String lotId = "lot-" + first.getStockCode() + "-" + first.getTransactionDate();

        return BuyLot.builder()
                .id(lotId)
                .email(first.getEmail())
                .stockCode(first.getStockCode())
                .stockName(first.getStockName())
                .exchangeName(first.getExchangeName())
                .brokerName(first.getBrokerName())
                .assetType(first.getAssetType())
                .accountType(first.getAccountType())
                .accountHolder(first.getAccountHolder())
                .buyPrice(newPrice)
                .quantity(merged.totalQuantity)
                .brokerCharges(merged.totalBrokerCharges)
                .miscCharges(merged.totalMiscCharges)
                .buyDate(first.getTransactionDate())
                .buyTransactionIds(merged.buyTransactionIds)
                .corporateActions(merged.corporateActions.isEmpty() ? null : merged.corporateActions)
                .corporateActionDerived(merged.corporateActionDerived)
                .acquisition(Acquisition.common(merged.acquisitions))
                .build();
    }

    /** What one key's same-day buys add up to, accumulated in the order they are read. */
    private static final class SameDayBuys {
        private double totalQuantity = 0;
        private double weightedPriceSum = 0;
        private double totalBrokerCharges = 0;
        private double totalMiscCharges = 0;
        private final List<String> buyTransactionIds = new ArrayList<>();
        private boolean corporateActionDerived = false;
        private final List<Acquisition> acquisitions = new ArrayList<>();
        private final List<CorporateActionEntity> corporateActions = new ArrayList<>();

        void add(TransactionEntity txn) {
            double qty = txn.getQuantity() != null ? txn.getQuantity() : 0.0;
            double price = txn.getPrice();
            double brokerCharges = txn.getBrokerCharges();
            double miscCharges = txn.getMiscCharges();

            totalQuantity += qty;
            weightedPriceSum += qty * price;
            totalBrokerCharges += brokerCharges;
            totalMiscCharges += miscCharges;
            buyTransactionIds.add(txn.getId());
            acquisitions.add(Acquisition.of(txn.getCorporateActionType()));

            // Check if CA-derived
            if (txn.getCorporateActionType() != null || price == 0) {
                corporateActionDerived = true;
            }

            // Collect corporate actions
            if (txn.getCorporateActions() != null) {
                corporateActions.addAll(txn.getCorporateActions());
            }
        }
    }

    private String buildCompositeKey(TransactionEntity txn) {
        return txn.getEmail() + "|"
                + txn.getStockCode() + "|"
                + txn.getBrokerName() + "|"
                + txn.getAccountHolder() + "|"
                + txn.getTransactionDate();
    }

    /**
     * Matches a sell request against available BuyLots via FIFO.
     * Updates the lots in-place (remaining quantities reduced).
     * Returns one MatchedTrade per matched portion.
     */
    public List<MatchedTrade> matchSellToLots(SellRequest sell, List<BuyLot> lots) {
        List<MatchedTrade> matchedTrades = new ArrayList<>();

        double sellRemaining = sell.getQuantity();

        for (BuyLot lot : eligibleLots(sell, lots)) {
            if (sellRemaining <= 0) break;

            double lotRemainingQty = lot.getQuantity();
            if (lotRemainingQty <= 0) continue;

            double matchedQty = Math.min(lotRemainingQty, sellRemaining);
            // Deducted once per match, from what earlier sells left (B-8), before the quantity falls
            double buyBrokerCharges = LotChargeAllocator.deductBroker(lot, matchedQty, lotRemainingQty);
            double buyMiscCharges = LotChargeAllocator.deductMisc(lot, matchedQty, lotRemainingQty);
            matchedTrades.add(matchedTrade(sell, lot, matchedQty, buyBrokerCharges, buyMiscCharges));

            // Update lot remaining quantity
            lot.setQuantity(lot.getQuantity() - matchedQty);
            sellRemaining -= matchedQty;
        }

        return matchedTrades;
    }

    /** The lots a sell may consume — same email, stock, broker and holder — oldest first. */
    private static List<BuyLot> eligibleLots(SellRequest sell, List<BuyLot> lots) {
        return lots.stream()
                .filter(lot -> lot.getEmail().equals(sell.getEmail()))
                .filter(lot -> lot.getStockCode().equals(sell.getStockCode()))
                .filter(lot -> lot.getBrokerName() == sell.getBrokerName())
                .filter(lot -> Objects.equals(lot.getAccountHolder(), sell.getAccountHolder()))
                .sorted(Comparator.comparing(BuyLot::getBuyDate))
                .toList();
    }

    /** One matched portion: the identity from the sell, the buy side from the lot. */
    private MatchedTrade matchedTrade(SellRequest sell, BuyLot lot, double matchedQty, double buyBrokerCharges,
            double buyMiscCharges) {
        TradeFigures figures = figures(sell, lot, matchedQty, buyBrokerCharges, buyMiscCharges);
        return MatchedTrade.builder()
                .email(sell.getEmail())
                .stockCode(sell.getStockCode())
                .stockName(sell.getStockName())
                .exchangeName(sell.getExchangeName())
                .brokerName(sell.getBrokerName())
                .assetType(sell.getAssetType())
                .accountType(sell.getAccountType())
                .accountHolder(sell.getAccountHolder())
                .originalBuyPrice(lot.getBuyPrice())
                .corporateActionAdjustedBuyPrice(lot.getBuyPrice())
                .buyQuantity(matchedQty)
                .buyDate(lot.getBuyDate())
                .buyBrokerCharges(figures.buyBrokerCharges())
                .buyMiscCharges(figures.buyMiscCharges())
                .sellPrice(sell.getSellPrice())
                .sellQuantity(matchedQty)
                .sellDate(sell.getSellDate())
                .sellBrokerCharges(figures.sellBrokerCharges())
                .sellMiscCharges(figures.sellMiscCharges())
                .totalBuyValue(figures.totalBuyValue())
                .totalSellValue(figures.totalSellValue())
                .netProfit(figures.netProfit())
                .profitPercentage(figures.profitPercentage())
                .holdingPeriodDays(ChronoUnit.DAYS.between(lot.getBuyDate(), sell.getSellDate()))
                .capitalGainsType(capitalGainsType(sell, lot))
                .financialYear(TLocalDate.financialYear(sell.getSellDate()))
                .sourceSellTransactionId(sell.getId())
                .sourceBuyLotId(lot.getId())
                .corporateActionDerived(lot.isCorporateActionDerived())
                .appliedCorporateActions(lot.getCorporateActions())
                .acquisition(lot.getAcquisition())
                .build();
    }

    /**
     * Capital gains type, resolved rather than counted: 365 days is the listed-equity rule and was being applied to
     * mutual funds, bonds and gold bonds too.
     */
    private CapitalGainsType capitalGainsType(SellRequest sell, BuyLot lot) {
        return holdingPeriodService
                .classify(lot.getAssetType(), null, lot.getBuyDate(), sell.getSellDate())
                .capitalGainsType();
    }

    /** The buy charges as allocated, the sell's pro-rated to the matched quantity, and the portion's values. */
    private static TradeFigures figures(SellRequest sell, BuyLot lot, double matchedQty, double buyBrokerCharges,
            double buyMiscCharges) {
        // Pro-rate sell charges
        double sellBrokerCharges = (sell.getBrokerCharges() / sell.getQuantity()) * matchedQty;
        double sellMiscCharges = (sell.getMiscCharges() / sell.getQuantity()) * matchedQty;

        double totalBuyValue = (lot.getBuyPrice() * matchedQty) + buyBrokerCharges + buyMiscCharges;
        double totalSellValue = (sell.getSellPrice() * matchedQty) - sellBrokerCharges - sellMiscCharges;
        double netProfit = totalSellValue - totalBuyValue;
        double profitPercentage = totalBuyValue > 0 ? (netProfit / totalBuyValue) * 100 : 0.0;
        return new TradeFigures(buyBrokerCharges, buyMiscCharges, sellBrokerCharges, sellMiscCharges,
                totalBuyValue, totalSellValue, netProfit, profitPercentage);
    }

    /**
     * Converts a MatchedTrade to a TradeOutcomeContext.
     */
    public TradeOutcomeContext toTradeOutcomeContext(MatchedTrade trade) {
        return TradeOutcomeContext.builder()
                .email(trade.getEmail())
                .stockCode(trade.getStockCode())
                .stockName(trade.getStockName())
                .exchangeName(trade.getExchangeName())
                .brokerName(trade.getBrokerName())
                .assetType(trade.getAssetType())
                .accountType(trade.getAccountType())
                .accountHolder(trade.getAccountHolder())
                .originalBuyPrice(trade.getOriginalBuyPrice())
                .corporateActionAdjustedBuyPrice(trade.getCorporateActionAdjustedBuyPrice())
                .buyQuantity(trade.getBuyQuantity())
                .buyDate(trade.getBuyDate())
                .buyBrokerCharges(trade.getBuyBrokerCharges())
                .buyMiscCharges(trade.getBuyMiscCharges())
                .sellPrice(trade.getSellPrice())
                .sellQuantity(trade.getSellQuantity())
                .sellDate(trade.getSellDate())
                .sellBrokerCharges(trade.getSellBrokerCharges())
                .sellMiscCharges(trade.getSellMiscCharges())
                .totalBuyValue(trade.getTotalBuyValue())
                .totalSellValue(trade.getTotalSellValue())
                .netProfit(trade.getNetProfit())
                .profitPercentage(trade.getProfitPercentage())
                .holdingPeriodDays(trade.getHoldingPeriodDays())
                .capitalGainsType(trade.getCapitalGainsType())
                .financialYear(trade.getFinancialYear())
                .sourceSellTransactionId(trade.getSourceSellTransactionId())
                .sourceBuyLotId(trade.getSourceBuyLotId())
                .corporateActionDerived(trade.isCorporateActionDerived())
                .appliedCorporateActions(trade.getAppliedCorporateActions())
                .acquisitionMode(trade.getAcquisition().mode())
                .acquisitionCorporateAction(trade.getAcquisition().corporateAction().orElse(null))
                .build();
    }

    /**
     * Converts a MatchedTrade to a TradeOutcomeEntity.
     */
    public TradeOutcomeEntity toTradeOutcomeEntity(MatchedTrade trade) {
        TradeOutcomeEntity entity = new TradeOutcomeEntity();

        entity.setEmail(trade.getEmail());
        entity.setStockCode(trade.getStockCode());
        entity.setStockName(trade.getStockName());
        entity.setExchangeName(trade.getExchangeName());
        entity.setBrokerName(trade.getBrokerName());
        entity.setAssetType(trade.getAssetType());
        entity.setAccountType(trade.getAccountType());
        entity.setAccountHolder(trade.getAccountHolder());
        entity.setOriginalBuyPrice(trade.getOriginalBuyPrice());
        entity.setCorporateActionAdjustedBuyPrice(trade.getCorporateActionAdjustedBuyPrice());
        entity.setBuyQuantity(trade.getBuyQuantity());
        entity.setBuyDate(trade.getBuyDate());
        entity.setBuyBrokerCharges(trade.getBuyBrokerCharges());
        entity.setBuyMiscCharges(trade.getBuyMiscCharges());
        entity.setSellPrice(trade.getSellPrice());
        entity.setSellQuantity(trade.getSellQuantity());
        entity.setSellDate(trade.getSellDate());
        entity.setSellBrokerCharges(trade.getSellBrokerCharges());
        entity.setSellMiscCharges(trade.getSellMiscCharges());
        entity.setTotalBuyValue(trade.getTotalBuyValue());
        entity.setTotalSellValue(trade.getTotalSellValue());
        entity.setNetProfit(trade.getNetProfit());
        entity.setProfitPercentage(trade.getProfitPercentage());
        entity.setHoldingPeriodDays(trade.getHoldingPeriodDays());
        entity.setCapitalGainsType(trade.getCapitalGainsType());
        entity.setFinancialYear(trade.getFinancialYear());
        entity.setSourceSellTransactionId(trade.getSourceSellTransactionId());
        entity.setSourceBuyLotId(trade.getSourceBuyLotId());
        entity.setCorporateActionDerived(trade.isCorporateActionDerived());
        entity.setAppliedCorporateActions(trade.getAppliedCorporateActions());
        entity.setAcquisitionMode(trade.getAcquisition().mode());
        entity.setAcquisitionCorporateAction(trade.getAcquisition().corporateAction().orElse(null));


        return entity;
    }

    /**
     * Convenience method: converts a SELL TransactionEntity to SellRequest.
     */
    public SellRequest toSellRequest(TransactionEntity sellTxn) {
        return SellRequest.builder()
                .id(sellTxn.getId())
                .email(sellTxn.getEmail())
                .stockCode(sellTxn.getStockCode())
                .stockName(sellTxn.getStockName())
                .exchangeName(sellTxn.getExchangeName())
                .brokerName(sellTxn.getBrokerName())
                .assetType(sellTxn.getAssetType())
                .accountType(sellTxn.getAccountType())
                .accountHolder(sellTxn.getAccountHolder())
                .sellPrice(sellTxn.getPrice())
                .quantity(sellTxn.getQuantity() != null ? sellTxn.getQuantity() : 0.0)
                .brokerCharges(sellTxn.getBrokerCharges())
                .miscCharges(sellTxn.getMiscCharges())
                .sellDate(sellTxn.getTransactionDate())
                .build();
    }

    @Data
    @Builder
    public static class BuyLot {
        private String id; // a synthetic ID or the buy transaction ID
        private String email;
        private String stockCode;
        private String stockName;
        private String exchangeName;
        private BrokerName brokerName;
        private AssetType assetType;
        private AccountType accountType;
        private String accountHolder;
        private double buyPrice;
        private double quantity;
        private double brokerCharges;
        private double miscCharges;
        private LocalDate buyDate;
        private List<String> buyTransactionIds;
        private List<CorporateActionEntity> corporateActions;
        private boolean corporateActionDerived;
        /** How the lot was acquired; a lot built without saying is unrecorded. */
        @Builder.Default
        private Acquisition acquisition = Acquisition.UNRECORDED;
        /** What earlier sells have already charged of {@code brokerCharges} ({@link LotChargeAllocator}). */
        private double allocatedBrokerCharges;
        /** What earlier sells have already charged of {@code miscCharges}. */
        private double allocatedMiscCharges;
    }

    @Data
    @Builder
    public static class SellRequest {
        private String id; // sell transaction ID
        private String email;
        private String stockCode;
        private String stockName;
        private String exchangeName;
        private BrokerName brokerName;
        private AssetType assetType;
        private AccountType accountType;
        private String accountHolder;
        private double sellPrice;
        private double quantity;
        private double brokerCharges;
        private double miscCharges;
        private LocalDate sellDate;
    }

    @Data
    @Builder
    public static class MatchedTrade {
        private String email;
        private String stockCode;
        private String stockName;
        private String exchangeName;
        private BrokerName brokerName;
        private AssetType assetType;
        private AccountType accountType;
        private String accountHolder;

        private double originalBuyPrice;
        private double corporateActionAdjustedBuyPrice;
        private double buyQuantity;
        private LocalDate buyDate;
        private double buyBrokerCharges;
        private double buyMiscCharges;

        private double sellPrice;
        private double sellQuantity;
        private LocalDate sellDate;
        private double sellBrokerCharges;
        private double sellMiscCharges;

        private double totalBuyValue;
        private double totalSellValue;
        private double netProfit;
        private double profitPercentage;
        private long holdingPeriodDays;
        private CapitalGainsType capitalGainsType;
        private String financialYear;

        private String sourceSellTransactionId;
        private String sourceBuyLotId;
        private boolean corporateActionDerived;
        private List<CorporateActionEntity> appliedCorporateActions;
        /** How the lot was acquired; a lot built without saying is unrecorded. */
        @Builder.Default
        private Acquisition acquisition = Acquisition.UNRECORDED;
    }
}
