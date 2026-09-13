package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.brokercharges.entity.UserChargeEntity;
import com.thiru.wealthlens.brokercharges.service.ChargeDeductibilityService;
import com.thiru.wealthlens.brokercharges.service.UserChargeService;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.context.TradeOutcomeContext;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.holding.TradeClassification;
import com.thiru.wealthlens.portfolio.holding.TradeClassificationQuery;
import com.thiru.wealthlens.portfolio.holding.TradeClassifier;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.util.money.TMoney;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Writes the itemised capital-gains rows for a <b>V2</b> sell — one per matched buy lot.
 *
 * <p>Until this existed, only the V1 sell populated {@code trade_outcomes}, so a V2 user had
 * aggregate totals and no itemised record to file from. V1 keeps its own path
 * ({@code PortfolioService.toTradeOutcomeContext}) untouched.
 *
 * @param asset the lot being consumed, and {@code quantity} the part of it this sell takes
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class TradeOutcomeRecorder {

    private final TradeOutcomeService tradeOutcomeService;
    private final TradeClassifier tradeClassifier;
    private final ChargeDeductibilityService chargeDeductibilityService;
    private final UserChargeService userChargeService;

    private final TransactionRepository transactionRepository;

    /** One consumed lot: the asset it came from and how much of it this sell took. */
    public record MatchedLot(AssetEntity asset, double quantity, double originalQuantity) {
    }

    public void record(UserMail userMail, AssetRequest sell, String sellTransactionId,
                       List<MatchedLot> lots, Optional<ChargeComputation> sellComputation) {
        double totalSellQuantity = lots.stream().mapToDouble(MatchedLot::quantity).sum();
        if (totalSellQuantity <= 0) {
            log.warn("A V2 sell of {} matched no quantity; no trade outcome recorded", sell.getStockCode());
            return;
        }

        List<Map<String, Double>> sellAllocation = allocate(
                sellComputation.map(ChargeComputation::amountByCode).orElseGet(Map::of),
                lots, totalSellQuantity);

        for (int i = 0; i < lots.size(); i++) {
            tradeOutcomeService.saveTradeOutcome(userMail,
                    buildRow(userMail, sell, sellTransactionId, lots.get(i), sellAllocation.get(i),
                            lots.get(i).quantity() / totalSellQuantity));
        }
    }

    /**
     * Splits each charge code across the lots so the parts sum to the whole (B-6).
     *
     * <p>Pro-rating and rounding independently loses money: ₹100 across three lots is ₹33.33 each,
     * which is ₹99.99 against ₹100.00 actually charged. A paisa per split sell, and the sum of a
     * sell's {@code trade_outcomes} charges then disagrees with its {@code user_charges} total.
     *
     * <p>So each lot gets <em>the running total up to it, minus what has already been handed
     * out</em>. The last lot's running total is the whole amount by definition, so the allocation
     * sums exactly — <b>by construction rather than by a correction pass</b>. That is why this is
     * preferred over largest-remainder, which reaches the same place but needs a sort and a
     * tie-break rule, both of which are things to get wrong.
     *
     * <p>Allocation is per <em>code</em>, not on the lumped total, so every code sums to its own
     * figure and a row's total can be derived from its own lines. Which lot receives a leftover
     * paisa depends on position — and position here is FIFO lot order, which is already the
     * ordering the domain assigns meaning to.
     */
    private static List<Map<String, Double>> allocate(Map<String, Double> breakup,
                                                      List<MatchedLot> lots, double totalQuantity) {
        List<Map<String, Double>> perLot = new ArrayList<>();
        for (int i = 0; i < lots.size(); i++) {
            perLot.add(new LinkedHashMap<>());
        }
        breakup.forEach((code, amount) -> {
            double total = amount == null ? 0.0 : amount;
            double cumulativeQuantity = 0;
            double allocated = 0;
            for (int i = 0; i < lots.size(); i++) {
                cumulativeQuantity += lots.get(i).quantity();
                double upToHere = TMoney.scale(total * cumulativeQuantity / totalQuantity);
                perLot.get(i).put(code, TMoney.scale(upToHere - allocated));
                allocated = upToHere;
            }
        });
        return perLot;
    }

    private TradeOutcomeContext buildRow(UserMail userMail, AssetRequest sell, String sellTransactionId,
                                         MatchedLot lot, Map<String, Double> sellBreakup,
                                         double sellShare) {
        AssetEntity asset = lot.asset();
        double buyShare = lot.originalQuantity() > 0 ? lot.quantity() / lot.originalQuantity() : 0.0;

        Map<String, Double> buyBreakup = share(buyBreakupFor(userMail, asset), buyShare);

        // The lumped total is derived from this row's own allocated lines rather than computed
        // separately, so "the total equals the sum of the columns beside it" holds per row as well
        // as across rows. Two figures that must agree come from one.
        double sellCharges = TMoney.sum(sellBreakup.values());
        double buyCharges = LotChargeAllocator.deductBroker(asset, lot.quantity(), lot.originalQuantity());
        double buyMiscCharges = LotChargeAllocator.deductMisc(asset, lot.quantity(), lot.originalQuantity());
        // Misc charges stay user-entered -- the engine does not produce them, so there is no
        // computed figure to prefer over this one, unlike the broker charge above.
        double sellMiscCharges = TMoney.scale(sell.getMiscCharges() * sellShare);

        TradeClassification classification = tradeClassifier.classify(new TradeClassificationQuery(
                sell.getStockCode(), sell.getAssetType(), sell.getSegment(),
                asset.getTransactionDate(), sell.getTransactionDate()));

        double totalBuyValue = TMoney.scale((asset.getPrice() * lot.quantity()) + buyCharges + buyMiscCharges);
        double totalSellValue = TMoney.scale((sell.getPrice() * lot.quantity()) - sellCharges - sellMiscCharges);
        double netProfit = TMoney.scale(totalSellValue - totalBuyValue);

        return TradeOutcomeContext.builder()
                .email(userMail.getEmail())
                .stockCode(sell.getStockCode())
                .stockName(sell.getStockName())
                .exchangeName(sell.getExchangeName())
                .brokerName(sell.getBrokerName())
                .assetType(sell.getAssetType())
                .accountType(sell.getAccountType())
                .accountHolder(sell.getAccountHolder())
                .segment(sell.getSegment())
                .originalBuyPrice(originalBuyPriceOf(asset))
                .corporateActionAdjustedBuyPrice(asset.getPrice())
                .buyQuantity(lot.quantity())
                .buyDate(asset.getTransactionDate())
                .buyBrokerCharges(buyCharges)
                .buyMiscCharges(buyMiscCharges)
                .buyChargeBreakup(buyBreakup)
                .deductibleBuyCharges(chargeDeductibilityService.deductibleTotal(buyBreakup))
                .sellPrice(sell.getPrice())
                .sellQuantity(lot.quantity())
                .sellDate(sell.getTransactionDate())
                .sellBrokerCharges(sellCharges)
                .sellMiscCharges(sellMiscCharges)
                .sellChargeBreakup(sellBreakup)
                .deductibleSellCharges(chargeDeductibilityService.deductibleTotal(sellBreakup))
                .totalBuyValue(totalBuyValue)
                .totalSellValue(totalSellValue)
                .netProfit(netProfit)
                .profitPercentage(totalBuyValue > 0 ? (netProfit / totalBuyValue) * 100 : 0.0)
                .holdingPeriodDays(ChronoUnit.DAYS.between(asset.getTransactionDate(), sell.getTransactionDate()))
                .capitalGainsType(classification.capitalGainsType())
                .instrumentSubClass(classification.subClass())
                .classificationReason(classification.reason())
                .financialYear(TLocalDate.financialYear(sell.getTransactionDate()))
                .sourceSellTransactionId(sellTransactionId)
                .sourceBuyLotId(asset.getId())
                .corporateActionDerived(asset.getCorporateActionType() != null)
                .appliedCorporateActions(asset.getCorporateActions())
                .build();
    }

    /**
     * The buy price as it was actually transacted, before any corporate-action adjustment.
     *
     * <p>{@code AssetEntity.price} is the <em>current</em> price of the lot, which a bonus or split
     * rewrites. Recording both means a row can still show what was paid; recording only the
     * adjusted figure — which is what happened until now, both fields being assigned the same
     * value — loses that permanently once the lot is consumed.
     */
    private double originalBuyPriceOf(AssetEntity asset) {
        return asset.getBuyTransactionIds().stream()
                .findFirst()
                .flatMap(transactionRepository::findById)
                .map(TransactionEntity::getPrice)
                .orElseGet(() -> {
                    log.debug("No source buy transaction for lot {}; recording the adjusted price as"
                            + " the original", asset.getId());
                    return asset.getPrice();
                });
    }

    /**
     * What the engine recorded against the buy, by code.
     *
     * <p>Empty when the buy predates the engine. ADR-32 settles that such buys are never re-driven,
     * so this is a permanent state rather than a transitional one, and the lumped total on the
     * asset is all the row can carry for them.
     */
    private Map<String, Double> buyBreakupFor(UserMail userMail, AssetEntity asset) {
        return asset.getBuyTransactionIds().stream()
                .findFirst()
                .flatMap(id -> userChargeService.findOptionalForTransaction(userMail.getEmail(), id))
                .map(UserChargeEntity::getAmountByCode)
                .orElseGet(Map::of);
    }

    /** One lot's share of a charge breakdown, pro-rated by quantity. */
    private static Map<String, Double> share(Map<String, Double> breakup, double share) {
        if (breakup == null || breakup.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> scaled = new LinkedHashMap<>();
        breakup.forEach((code, amount) -> scaled.put(code, TMoney.scale((amount == null ? 0.0 : amount) * share)));
        return scaled;
    }
}
