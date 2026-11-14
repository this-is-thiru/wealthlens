package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.dto.ProfitAndLossResponse;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.shared.dto.DateRangeFilter;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.entity.query.QueryFilter;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reads of a portfolio: holdings grouped per holding with their charges, a stock's lots, a date range, a search, the
 * rows an export writes, and a year's profit and loss. Split from {@link PortfolioService}, which keeps the trades, so
 * each changes for one reason (W-286, PS-9).
 */
@Log4j2
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PortfolioQueryService {

    private final PortfolioRepository portfolioRepository;
    private final ProfitAndLossService profitAndLossService;
    private final MongoTemplateService mongoTemplateService;
    private final ChargeViewAssembler chargeViewAssembler;

    public List<AssetResponse> getStockQuantity(UserMail userMail, String stockCode) {

        String email = userMail.getEmail();

        List<AssetEntity> stockEntities = portfolioRepository.findByEmailAndStockCodeOrderByTransactionDate(email, stockCode);

        if (stockEntities.isEmpty()) {
            throw new IllegalArgumentException("Stock not found");
        }

        ChargeViewAssembler.ChargeLookup lookup = chargeViewAssembler.forLots(email, stockEntities);
        return TCollectionUtil.map(stockEntities, asset -> {
            AssetResponse assetResponse = TJsonMapper.copy(asset, AssetResponse.class);
            assetResponse.setCharges(chargeViewAssembler.assetCharges(lookup, List.of(asset)));
            return assetResponse;
        });
    }

    public List<AssetResponse> getAllStocks(UserMail userMail) {

        List<AssetEntity> stockEntities = portfolioRepository.findByEmail(userMail.getEmail());
        List<AssetResponse> responseEntities = groupedWithCharges(userMail, stockEntities);

        log.info("Fetching portfolio stocks of {}", userMail.getEmail());
        return responseEntities;
    }

    /**
     * Groups lots into the rows this view presents, and hangs each row's charges off it.
     *
     * <p>The lookup is built once for every lot in the response, not once per row: the charge rows
     * and the realised rows are each one query, whatever the size of the portfolio.
     */
    private List<AssetResponse> groupedWithCharges(UserMail userMail, List<AssetEntity> assetEntities) {
        // A lot with nothing left is not held, and a group of only such lots would price at 0 / 0 (W-274, PS-11).
        List<AssetEntity> held = TCollectionUtil.filter(assetEntities, lot -> !PortfolioService.hasNothingLeft(lot));
        Map<Holding, List<AssetEntity>> assetMap = TCollectionUtil.groupingBy(held, Holding::of);
        ChargeViewAssembler.ChargeLookup lookup = chargeViewAssembler.forLots(userMail.getEmail(), held);
        return TCollectionUtil.map(assetMap.values(), lots -> {
            AssetResponse assetResponse = combineAllDetailsOfEntities(lots);
            assetResponse.setCharges(chargeViewAssembler.assetCharges(lookup, lots));
            return assetResponse;
        });
    }

    public List<AssetResponse> getStocksWithDateRange(UserMail userMail, DateRangeFilter dateRange) {
        if (dateRange == null || dateRange.getStartDate() == null || dateRange.getEndDate() == null) {
            throw new BadRequestException("A date range is required: dateRange with startDate and endDate");
        }
        LocalDate startDate = dateRange.getStartDate();
        LocalDate endDate = dateRange.getEndDate();

        String email = userMail.getEmail();
        List<AssetEntity> stockEntities = portfolioRepository.findByEmailAndTransactionDateBetween(email, startDate, endDate);

        List<AssetResponse> responseEntities = groupedWithCharges(userMail, stockEntities);

        log.info("Fetching stocks from portfolio between {} and {}", startDate, endDate);
        return responseEntities;
    }

    /**
     * A method that combines all details of stock entities to single entity based
     * on a given stock code.
     *
     * @param stockEntities a list of stock entities to search through
     * @return the response entity that matches the provided stock code
     */
    private static AssetResponse combineAllDetailsOfEntities(List<AssetEntity> stockEntities) {
        AssetResponse assetResponse = TJsonMapper.copy(stockEntities.getFirst(), AssetResponse.class);

        double totalValue = 0;
        double totalQuantity = 0;
        double brokerCharges = 0;
        double miscCharges = 0;
        Map<String, Double> transactionDatesMap = new HashMap<>();

        for (AssetEntity entity : stockEntities) {
            double price = entity.getPrice();
            double quantity = entity.getQuantity();

            totalValue += (price * quantity);
            totalQuantity += quantity;
            brokerCharges += entity.getBrokerCharges();
            miscCharges += entity.getMiscCharges();

            Double existingQuantity = transactionDatesMap.get(TLocalDate.convertToString(entity.getTransactionDate()));
            Double latestQuantity = entity.getQuantity();
            if (existingQuantity != null) {
                latestQuantity += existingQuantity;
            }
            transactionDatesMap.put(TLocalDate.convertToString(entity.getTransactionDate()), latestQuantity);
        }

        assetResponse.setQuantity(totalQuantity);
        assetResponse.setTotalQuantity(totalQuantity);
        assetResponse.setTotalValue(totalValue);
        assetResponse.setPrice(totalValue / totalQuantity);
        assetResponse.setBrokerCharges(brokerCharges);
        assetResponse.setMiscCharges(miscCharges);
        Map<String, Double> transactionQuantities = transactionDatesMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, _) -> e1, LinkedHashMap::new));
        assetResponse.setTransactionQuantities(transactionQuantities);
        LocalDate today = TLocalDate.today();
        Map<String, Long> daysHeld = new LinkedHashMap<>();
        transactionQuantities.keySet().forEach(date ->
                daysHeld.put(date, ChronoUnit.DAYS.between(TLocalDate.convertToDate(date), today)));
        assetResponse.setDaysHeld(daysHeld);
        assetResponse.setTransactionDate(null);
        return assetResponse;
    }

    public ProfitAndLossResponse getProfitAndLoss(UserMail userMail, String financialYear) {
        return profitAndLossService.getProfitAndLoss(userMail, financialYear);
    }

    public List<AssetEntity> searchAssets(UserMail userMail, List<QueryFilter> queryFilters) {
        return mongoTemplateService.getDocuments(userMail, queryFilters, AssetEntity.class);
    }

    public List<AssetResponse> getExportEntities(UserMail userMail, List<QueryFilter> queryFilters) {
        List<AssetEntity> entities = mongoTemplateService.getDocuments(userMail, queryFilters, AssetEntity.class);
        return groupedWithCharges(userMail, entities);
    }

    /**
     * What makes lots one holding in the views: the stock, the broker and whose it is. A record rather than a joined
     * string, so no two holdings can share a key (W-273, PS-14); a missing holder is one value of its own.
     */
    private record Holding(String stockCode, BrokerName brokerName, String accountHolder) {

        private static Holding of(AssetEntity lot) {
            return new Holding(lot.getStockCode(), lot.getBrokerName(), lot.getAccountHolder());
        }
    }

    public List<AssetResponse> getMutualFunds(UserMail userMail) {

        List<AssetEntity> mutualFunds = portfolioRepository.findByEmailAndAssetType(userMail.getEmail(), AssetType.MUTUAL_FUND);
        List<AssetResponse> responseEntities = groupedWithCharges(userMail, mutualFunds);

        log.info("Fetch holding Mutual Funds of {}", userMail.getEmail());
        return responseEntities;
    }
}
