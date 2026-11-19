package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.dto.analytics.AssetAllocationResponse;
import com.thiru.wealthlens.portfolio.dto.analytics.CashFlow;
import com.thiru.wealthlens.portfolio.dto.analytics.MarketPriceEntry;
import com.thiru.wealthlens.portfolio.dto.analytics.PerformanceMetricsResponse;
import com.thiru.wealthlens.portfolio.dto.analytics.PortfolioSummaryResponse;
import com.thiru.wealthlens.portfolio.dto.analytics.StockPerformance;
import com.thiru.wealthlens.portfolio.dto.analytics.XirrRequest;
import com.thiru.wealthlens.portfolio.dto.analytics.XirrResponse;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.entity.model.FinancialReport;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.portfolio.repository.ProfitAndLossRepository;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.util.calculator.XirrCalculator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final PortfolioRepository portfolioRepository;
    private final TransactionRepository transactionRepository;
    private final ProfitAndLossRepository profitAndLossRepository;
    private final TradeOutcomeRepository tradeOutcomeRepository;

    public PortfolioSummaryResponse getPortfolioSummary(UserMail userMail) {
        String email = userMail.getEmail();

        List<AssetEntity> assets = portfolioRepository.findByEmail(email);
        List<TransactionEntity> transactions = recorded(transactionRepository.findByEmail(email));
        List<ProfitAndLossEntity> pnlList = profitAndLossRepository.findAllByEmail(email);

        double totalInvested = assets.stream()
                .mapToDouble(a -> a.getPrice() * a.getQuantity())
                .sum();

        double totalRealizedProfit = pnlList.stream()
                .map(ProfitAndLossEntity::getRealisedProfits)
                .filter(Objects::nonNull)
                .flatMap(realised -> realised.getGainsByAssetAndSegment().values().stream())
                .flatMap(bySegment -> bySegment.values().stream())
                .mapToDouble(FinancialReport::getProfit)
                .sum();

        long totalTrades = transactions.size();
        long buyTrades = transactions.stream().filter(t -> t.getTransactionType() == TransactionType.BUY).count();
        long sellTrades = transactions.stream().filter(t -> t.getTransactionType() == TransactionType.SELL).count();
        long holdingCount = assets.stream().filter(a -> a.getQuantity() != null && a.getQuantity() > 0).count();

        return PortfolioSummaryResponse.builder()
                .totalInvested(totalInvested)
                .currentPortfolioValue(null)
                .totalRealizedProfit(totalRealizedProfit)
                .totalUnrealizedProfit(null)
                .overallReturnPercentage(null)
                .totalTrades(totalTrades)
                .buyTrades(buyTrades)
                .sellTrades(sellTrades)
                .holdingCount(holdingCount)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    public List<AssetAllocationResponse> getAssetAllocation(UserMail userMail) {
        String email = userMail.getEmail();
        List<AssetEntity> assets = portfolioRepository.findByEmail(email);

        Map<AssetType, Double> investedByType = assets.stream()
                .filter(a -> a.getAssetType() != null)
                .collect(Collectors.groupingBy(
                        AssetEntity::getAssetType,
                        Collectors.summingDouble(a -> a.getPrice() * a.getQuantity())
                ));

        double total = investedByType.values().stream().mapToDouble(Double::doubleValue).sum();

        return investedByType.entrySet().stream()
                .map(e -> new AssetAllocationResponse(
                        e.getKey(),
                        e.getValue(),
                        null,
                        total > 0 ? (e.getValue() / total) * 100 : 0.0
                ))
                .collect(Collectors.toList());
    }

    public PerformanceMetricsResponse getPerformanceMetrics(UserMail userMail) {
        List<TradeOutcomeEntity> outcomes = tradeOutcomeRepository.findByEmail(userMail.getEmail());

        if (outcomes.isEmpty()) {
            return PerformanceMetricsResponse.builder()
                    .totalTrades(0L)
                    .winCount(0L)
                    .lossCount(0L)
                    .breakevenCount(0L)
                    .averageProfitPerWin(0.0)
                    .averageLossPerLoss(0.0)
                    .winLossRatio(null)
                    .averageHoldingPeriodDays(0.0)
                    .portfolioTurnover(0.0)
                    .build();
        }

        // Separate outcomes by profit category (treat null netProfit as 0)
        List<TradeOutcomeEntity> wins = outcomes.stream()
                .filter(o -> o.getNetProfit() > 0)
                .collect(Collectors.toList());
        List<TradeOutcomeEntity> losses = outcomes.stream()
                .filter(o -> o.getNetProfit() < 0)
                .collect(Collectors.toList());
        List<TradeOutcomeEntity> breakevens = outcomes.stream()
                .filter(o -> o.getNetProfit() == 0)
                .collect(Collectors.toList());

        long totalTrades = outcomes.size();
        long winCount = wins.size();
        long lossCount = losses.size();
        long breakevenCount = breakevens.size();

        // Averages
        double avgProfit = wins.stream().mapToDouble(TradeOutcomeEntity::getNetProfit).average().orElse(0.0);
        double avgLoss = losses.stream().mapToDouble(o -> Math.abs(o.getNetProfit())).average().orElse(0.0);

        // With no losses there is no ratio; 0.0 would read as no wins (W-322)
        Double winLossRatio = lossCount > 0 ? (double) winCount / lossCount : null;
        double avgHolding = outcomes.stream().mapToDouble(TradeOutcomeEntity::getHoldingPeriodDays).average().orElse(0.0);

        // Portfolio turnover = total sell value / total buy value
        double totalBuyValue = outcomes.stream().mapToDouble(TradeOutcomeEntity::getTotalBuyValue).sum();
        double totalSellValue = outcomes.stream().mapToDouble(TradeOutcomeEntity::getTotalSellValue).sum();
        double turnover = totalBuyValue > 0 ? totalSellValue / totalBuyValue : 0.0;

        // Best / worst stock by aggregated net profit
        Map<String, Double> profitByStock = outcomes.stream()
                .collect(Collectors.groupingBy(
                        TradeOutcomeEntity::getStockCode,
                        Collectors.summingDouble(TradeOutcomeEntity::getNetProfit)
                ));

        Map<String, Double> investedByStock = outcomes.stream()
                .collect(Collectors.groupingBy(
                        TradeOutcomeEntity::getStockCode,
                        Collectors.summingDouble(TradeOutcomeEntity::getTotalBuyValue)
                ));

        StockPerformance best = profitByStock.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> {
                    double invested = investedByStock.getOrDefault(e.getKey(), 1.0);
                    double pct = invested > 0 ? (e.getValue() / invested) * 100 : 0.0;
                    return new StockPerformance(e.getKey(), null, pct, e.getValue());
                })
                .orElse(null);

        StockPerformance worst = profitByStock.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(e -> {
                    double invested = investedByStock.getOrDefault(e.getKey(), 1.0);
                    double pct = invested > 0 ? (e.getValue() / invested) * 100 : 0.0;
                    return new StockPerformance(e.getKey(), null, pct, e.getValue());
                })
                .orElse(null);

        return PerformanceMetricsResponse.builder()
                .totalTrades(totalTrades)
                .winCount(winCount)
                .lossCount(lossCount)
                .breakevenCount(breakevenCount)
                .averageProfitPerWin(avgProfit)
                .averageLossPerLoss(avgLoss)
                .winLossRatio(winLossRatio)
                .averageHoldingPeriodDays(avgHolding)
                .portfolioTurnover(turnover)
                .bestPerformingStock(best)
                .worstPerformingStock(worst)
                .build();
    }

    /**
     * The return on the user's recorded trades. A holding is valued at the price the caller gives; a holding with no price
     * is left out whole, its trades as well as its value, and named, because counting what it cost and not what it is
     * worth reads as a loss of all of it (W-318). Held trades are not recorded yet, so they are not cash flows (W-319).
     */
    public XirrResponse calculateXirr(UserMail userMail, XirrRequest request) {
        String email = userMail.getEmail();
        Map<String, Double> prices = currentPrices(request);
        List<AssetEntity> holdings = portfolioRepository.findByEmail(email).stream()
                .filter(asset -> asset.getQuantity() != null && asset.getQuantity() > 0)
                .toList();
        Set<String> unpriced = holdings.stream()
                .map(AssetEntity::getStockCode)
                .filter(stockCode -> !prices.containsKey(stockCode))
                .collect(Collectors.toSet());

        List<CashFlow> cashFlows = new ArrayList<>();
        for (TransactionEntity txn : recorded(transactionRepository.findByEmail(email))) {
            if (!unpriced.contains(txn.getStockCode())) {
                cashFlowOf(txn).ifPresent(cashFlows::add);
            }
        }
        double currentValue = holdings.stream()
                .filter(asset -> prices.containsKey(asset.getStockCode()))
                .mapToDouble(asset -> prices.get(asset.getStockCode()) * asset.getQuantity())
                .sum();
        boolean includesOpenPositions = currentValue > 0;
        if (includesOpenPositions) {
            cashFlows.add(new CashFlow(currentValue, LocalDate.now()));
        }
        String message = unpriced.isEmpty() ? null : "Left out for want of a current price: " + String.join(", ", unpriced.stream().sorted().toList())
                + ". Give currentPrices for them to include them.";
        return xirrOf(cashFlows, includesOpenPositions, message);
    }

    /** One price per stock; a stock priced twice is refused, since either price would be a guess (W-321). */
    private static Map<String, Double> currentPrices(XirrRequest request) {
        Map<String, Double> prices = new HashMap<>();
        if (request == null || request.getCurrentPrices() == null) {
            return prices;
        }
        for (MarketPriceEntry entry : request.getCurrentPrices()) {
            if (prices.containsKey(entry.getStockCode())) {
                throw new IllegalArgumentException("currentPrices names " + entry.getStockCode()
                        + " more than once; give one price per stock");
            }
            prices.put(entry.getStockCode(), entry.getPrice());
        }
        return prices;
    }

    private static Optional<CashFlow> cashFlowOf(TransactionEntity txn) {
        double grossAmount = txn.getPrice() * txn.getQuantity();
        return switch (txn.getTransactionType()) {
            case BUY -> Optional.of(new CashFlow(-(grossAmount + txn.getBrokerCharges() + txn.getMiscCharges()), txn.getTransactionDate()));
            case SELL -> Optional.of(new CashFlow(grossAmount - txn.getBrokerCharges() - txn.getMiscCharges(), txn.getTransactionDate()));
            default -> Optional.empty();
        };
    }

    private static XirrResponse xirrOf(List<CashFlow> cashFlows, boolean includesOpenPositions, String message) {
        XirrResponse.XirrResponseBuilder response = XirrResponse.builder()
                .calculationDate(LocalDate.now())
                .cashFlowsCount(cashFlows.size())
                .includesOpenPositions(includesOpenPositions);
        if (cashFlows.size() < 2) {
            return response.xirr(0.0).xirrPercentage(0.0).converged(false)
                    .message(message == null ? "Insufficient cash flows for XIRR calculation"
                            : "Insufficient cash flows for XIRR calculation. " + message).build();
        }
        try {
            double xirr = XirrCalculator.calculate(cashFlows);
            return response.xirr(xirr).xirrPercentage(xirr * 100).converged(true).message(message).build();
        } catch (IllegalArgumentException e) {
            return response.xirr(0.0).xirrPercentage(0.0).converged(false).message(e.getMessage()).build();
        }
    }

    /** The trades that happened: one held behind a corporate action is not recorded yet (W-319). */
    private static List<TransactionEntity> recorded(List<TransactionEntity> transactions) {
        return transactions.stream().filter(txn -> txn.getStatus() != TransactionStatus.TEMPORARY).toList();
    }
}
