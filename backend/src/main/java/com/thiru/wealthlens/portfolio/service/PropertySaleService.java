package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.dto.PropertySaleRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AcquisitionMode;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.PropertyExpense;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.repository.TradeOutcomeRepository;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.money.TMoney;
import com.thiru.wealthlens.shared.util.time.FinancialYear;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the sale of a land or building as the one {@code trade_outcomes} row the capital-gains summary reads (W-088g9,
 * D-434). A property is not a broker trade ({@link AssetType#NOT_A_BROKER_TRADE}): no holding, lot or P&L entry is
 * written, and the row's expenses are kept by code so that the tax side's rows, not this class, decide what each one is.
 *
 * <p>A purchase only: a gift or inheritance would need a previous owner's cost and date, which this entry does not carry.
 * The row's id is a digest of the sale's identity, so the same sale posted twice is refused rather than taxed twice.
 */
@Service
@Log4j2
@Transactional
@RequiredArgsConstructor
public class PropertySaleService {

    private static final List<String> EXPENSE_CODES = Arrays.stream(PropertyExpense.values()).map(Enum::name).toList();

    private final TradeOutcomeRepository tradeOutcomeRepository;

    /**
     * Saves the sale's row.
     *
     * @return a sentence naming what was recorded
     * @throws BadRequestException if the sale is malformed or already recorded
     */
    public String recordSale(UserMail userMail, PropertySaleRequest request) {
        requireWellFormed(request);
        TradeOutcomeEntity row = rowOf(userMail.getEmail(), request);
        if (tradeOutcomeRepository.existsById(row.getId())) {
            throw new BadRequestException("The sale of " + row.getStockCode() + " bought on " + row.getBuyDate()
                    + " and sold on " + row.getSellDate() + " is already recorded");
        }
        tradeOutcomeRepository.save(row);
        log.info("Recorded a property sale for {} sold on {}", userMail.getEmail(), row.getSellDate());
        return "Property sale recorded: " + row.getStockCode();
    }

    private static void requireWellFormed(PropertySaleRequest request) {
        if (request.getPropertyName() == null || request.getPropertyName().isBlank()) {
            throw new BadRequestException("A property sale needs the property's name");
        }
        if (request.getAcquiredOn() == null || request.getTransferredOn() == null) {
            throw new BadRequestException("A property sale needs both the date it was bought and the date it was sold");
        }
        if (!request.getTransferredOn().isAfter(request.getAcquiredOn())) {
            throw new BadRequestException("A property is sold after it is bought, but it was bought on "
                    + request.getAcquiredOn() + " and sold on " + request.getTransferredOn());
        }
        if (request.getCostRupees() <= 0 || request.getConsiderationRupees() <= 0) {
            throw new BadRequestException("A property sale needs a cost and a sale price above nil");
        }
        requireExpenses(request.getAcquisitionExpenses());
        requireExpenses(request.getTransferExpenses());
    }

    private static void requireExpenses(Map<String, Double> expenses) {
        if (expenses == null) {
            throw new BadRequestException("A property sale's expenses are a map of code to rupees; leave it out or"
                    + " empty if there were none");
        }
        expenses.forEach((code, rupees) -> {
            if (!EXPENSE_CODES.contains(code)) {
                throw new BadRequestException("The expense " + code + " is not one of " + EXPENSE_CODES);
            }
            if (rupees == null || rupees < 0) {
                throw new BadRequestException("The expense " + code + " needs an amount of nil or more");
            }
        });
    }

    private static TradeOutcomeEntity rowOf(String email, PropertySaleRequest request) {
        String name = request.getPropertyName().strip();
        LocalDate acquiredOn = request.getAcquiredOn();
        LocalDate transferredOn = request.getTransferredOn();
        TradeOutcomeEntity row = new TradeOutcomeEntity();
        row.setId("property-sale:" + TradeFingerprint.sha256(String.join("|", email, name, acquiredOn.toString(),
                transferredOn.toString())));
        row.setEmail(email);
        row.setStockCode(name);
        row.setStockName(name);
        row.setAssetType(AssetType.LAND_OR_BUILDING);
        row.setAccountType(AccountType.SELF);
        row.setFinancialYear(FinancialYear.containing(transferredOn).label());
        row.setHoldingPeriodDays(ChronoUnit.DAYS.between(acquiredOn, transferredOn));
        row.setAcquisitionMode(AcquisitionMode.PURCHASE);
        row.setCorporateActionDerived(false);
        buySide(row, request);
        sellSide(row, request);
        figures(row);
        return row;
    }

    private static void buySide(TradeOutcomeEntity row, PropertySaleRequest request) {
        double cost = TMoney.scale(request.getCostRupees());
        Map<String, Double> expenses = scaled(request.getAcquisitionExpenses());
        row.setOriginalBuyPrice(cost);
        row.setCorporateActionAdjustedBuyPrice(cost);
        row.setBuyQuantity(1.0);
        row.setBuyDate(request.getAcquiredOn());
        row.setBuyChargeBreakup(expenses);
        row.setBuyBrokerCharges(TMoney.sum(expenses.values()));
    }

    private static void sellSide(TradeOutcomeEntity row, PropertySaleRequest request) {
        Map<String, Double> expenses = scaled(request.getTransferExpenses());
        row.setSellPrice(TMoney.scale(request.getConsiderationRupees()));
        row.setSellQuantity(1.0);
        row.setSellDate(request.getTransferredOn());
        row.setSellChargeBreakup(expenses);
        row.setSellBrokerCharges(TMoney.sum(expenses.values()));
    }

    /** The product's own totals, as a broker trade's row carries them; the tax side does not read these. */
    private static void figures(TradeOutcomeEntity row) {
        double totalBuy = TMoney.add(row.getCorporateActionAdjustedBuyPrice(), row.getBuyBrokerCharges());
        double totalSell = TMoney.add(row.getSellPrice(), -row.getSellBrokerCharges());
        double netProfit = TMoney.add(totalSell, -totalBuy);
        row.setTotalBuyValue(totalBuy);
        row.setTotalSellValue(totalSell);
        row.setNetProfit(netProfit);
        row.setProfitPercentage(netProfit / totalBuy * 100);
    }

    private static Map<String, Double> scaled(Map<String, Double> expenses) {
        Map<String, Double> scaled = new LinkedHashMap<>();
        expenses.forEach((code, rupees) -> scaled.put(code, TMoney.scale(rupees)));
        return scaled;
    }
}
