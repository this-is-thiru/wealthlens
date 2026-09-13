package com.thiru.wealthlens.portfolio.service;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.portfolio.dto.context.TradeOutcomeContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.entity.helper.AuditMetadata;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

            TransactionEntity first = txns.getFirst();
            String email = first.getEmail();
            String stockCode = first.getStockCode();
            String stockName = first.getStockName();
            String exchangeName = first.getExchangeName();
            BrokerName brokerName = first.getBrokerName();
            AssetType assetType = first.getAssetType();
            AccountType accountType = first.getAccountType();
            String accountHolder = first.getAccountHolder();
            LocalDate buyDate = first.getTransactionDate();

            // Merge: sum quantities, weighted average price, sum charges
            double totalQuantity = 0;
            double weightedPriceSum = 0;
            double totalBrokerCharges = 0;
            double totalMiscCharges = 0;
            List<String> buyTransactionIds = new ArrayList<>();
            boolean isCaDerived = false;
            List<CorporateActionEntity> corporateActions = new ArrayList<>();

            for (TransactionEntity txn : txns) {
                double qty = txn.getQuantity() != null ? txn.getQuantity() : 0.0;
                double price = txn.getPrice();
                double brokerCharges = txn.getBrokerCharges();
                double miscCharges = txn.getMiscCharges();

                totalQuantity += qty;
                weightedPriceSum += qty * price;
                totalBrokerCharges += brokerCharges;
                totalMiscCharges += miscCharges;
                buyTransactionIds.add(txn.getId());

                // Check if CA-derived
                if (txn.getCorporateActionType() != null || price == 0) {
                    isCaDerived = true;
                }

                // Collect corporate actions
                if (txn.getCorporateActions() != null) {
                    corporateActions.addAll(txn.getCorporateActions());
                }
            }

            double newPrice = totalQuantity > 0 ? weightedPriceSum / totalQuantity : 0;

            // Synthetic ID for the lot
            String lotId = "lot-" + stockCode + "-" + buyDate;

            BuyLot lot = BuyLot.builder()
                    .id(lotId)
                    .email(email)
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .exchangeName(exchangeName)
                    .brokerName(brokerName)
                    .assetType(assetType)
                    .accountType(accountType)
                    .accountHolder(accountHolder)
                    .buyPrice(newPrice)
                    .quantity(totalQuantity)
                    .brokerCharges(totalBrokerCharges)
                    .miscCharges(totalMiscCharges)
                    .buyDate(buyDate)
                    .buyTransactionIds(buyTransactionIds)
                    .corporateActions(corporateActions.isEmpty() ? null : corporateActions)
                    .isCaDerived(isCaDerived)
                    .build();

            lots.add(lot);
        }

        // Sort by buyDate
        lots.sort(Comparator.comparing(BuyLot::getBuyDate));
        return lots;
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

        // Filter lots to those matching: stockCode, brokerName, accountHolder, email
        List<BuyLot> eligibleLots = lots.stream()
                .filter(lot -> lot.getEmail().equals(sell.getEmail()))
                .filter(lot -> lot.getStockCode().equals(sell.getStockCode()))
                .filter(lot -> lot.getBrokerName() == sell.getBrokerName())
                .filter(lot -> Objects.equals(lot.getAccountHolder(), sell.getAccountHolder()))
                .sorted(Comparator.comparing(BuyLot::getBuyDate))
                .toList();

        double sellRemaining = sell.getQuantity();

        for (BuyLot lot : eligibleLots) {
            if (sellRemaining <= 0) break;

            double lotRemainingQty = lot.getQuantity();
            if (lotRemainingQty <= 0) continue;

            double matchedQty = Math.min(lotRemainingQty, sellRemaining);

            // Pro-rate buy charges
            double buyBrokerCharges = (lot.getBrokerCharges() / lot.getQuantity()) * matchedQty;
            double buyMiscCharges = (lot.getMiscCharges() / lot.getQuantity()) * matchedQty;

            // Pro-rate sell charges
            double sellBrokerCharges = (sell.getBrokerCharges() / sell.getQuantity()) * matchedQty;
            double sellMiscCharges = (sell.getMiscCharges() / sell.getQuantity()) * matchedQty;

            // Computed values
            double totalBuyValue = (lot.getBuyPrice() * matchedQty) + buyBrokerCharges + buyMiscCharges;
            double totalSellValue = (sell.getSellPrice() * matchedQty) - sellBrokerCharges - sellMiscCharges;
            double netProfit = totalSellValue - totalBuyValue;
            double profitPercentage = totalBuyValue > 0 ? (netProfit / totalBuyValue) * 100 : 0.0;
            long holdingPeriodDays = ChronoUnit.DAYS.between(lot.getBuyDate(), sell.getSellDate());

            // Capital gains type
            CapitalGainsType capitalGainsType = holdingPeriodDays > 365
                    ? CapitalGainsType.LONG_TERM
                    : CapitalGainsType.SHORT_TERM;

            // Financial year
            String financialYear = deriveFinancialYear(sell.getSellDate());

            MatchedTrade trade = MatchedTrade.builder()
                    .email(sell.getEmail())
                    .stockCode(sell.getStockCode())
                    .stockName(sell.getStockName())
                    .exchangeName(sell.getExchangeName())
                    .brokerName(sell.getBrokerName())
                    .assetType(sell.getAssetType())
                    .accountType(sell.getAccountType())
                    .accountHolder(sell.getAccountHolder())
                    .originalBuyPrice(lot.getBuyPrice())
                    .caAdjustedBuyPrice(lot.getBuyPrice())
                    .buyQuantity(matchedQty)
                    .buyDate(lot.getBuyDate())
                    .buyBrokerCharges(buyBrokerCharges)
                    .buyMiscCharges(buyMiscCharges)
                    .sellPrice(sell.getSellPrice())
                    .sellQuantity(matchedQty)
                    .sellDate(sell.getSellDate())
                    .sellBrokerCharges(sellBrokerCharges)
                    .sellMiscCharges(sellMiscCharges)
                    .totalBuyValue(totalBuyValue)
                    .totalSellValue(totalSellValue)
                    .netProfit(netProfit)
                    .profitPercentage(profitPercentage)
                    .holdingPeriodDays(holdingPeriodDays)
                    .capitalGainsType(capitalGainsType)
                    .financialYear(financialYear)
                    .sourceSellTransactionId(sell.getId())
                    .sourceBuyLotId(lot.getId())
                    .isCaDerived(lot.isCaDerived())
                    .appliedCorporateActions(lot.getCorporateActions())
                    .build();

            matchedTrades.add(trade);

            // Update lot remaining quantity
            lot.setQuantity(lot.getQuantity() - matchedQty);
            sellRemaining -= matchedQty;
        }

        return matchedTrades;
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
                .caAdjustedBuyPrice(trade.getCaAdjustedBuyPrice())
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
                .isCaDerived(trade.isCaDerived())
                .appliedCorporateActions(trade.getAppliedCorporateActions())
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
        entity.setCaAdjustedBuyPrice(trade.getCaAdjustedBuyPrice());
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
        entity.setIsCaDerived(trade.isCaDerived());
        entity.setAppliedCorporateActions(trade.getAppliedCorporateActions());

        entity.setAuditMetadata(buildAuditMetadata());

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

    private String deriveFinancialYear(LocalDate transactionDate) {
        if (transactionDate == null) {
            return "UNKNOWN";
        }
        int year = transactionDate.getYear();
        int month = transactionDate.getMonthValue();
        if (month <= 3) {
            return String.format("FY%d-%02d", year - 1, (year % 100));
        } else {
            return String.format("FY%d-%02d", year, ((year + 1) % 100));
        }
    }

    private AuditMetadata buildAuditMetadata() {
        LocalDateTime now = LocalDateTime.now();
        AuditMetadata metadata = new AuditMetadata();
        metadata.setCreatedBy("TransactionBasedTradeOutcomeMigration");
        metadata.setCreatedAt(now);
        metadata.setUpdatedBy("TransactionBasedTradeOutcomeMigration@" + now);
        metadata.setUpdatedAt(now);
        return metadata;
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
        private boolean isCaDerived;
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
        private double caAdjustedBuyPrice;
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
        private boolean isCaDerived;
        private List<CorporateActionEntity> appliedCorporateActions;
    }
}
