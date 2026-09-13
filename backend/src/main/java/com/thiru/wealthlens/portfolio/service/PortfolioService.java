package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.dto.OrderTimeQuantity;
import com.thiru.wealthlens.portfolio.dto.ProfitAndLossResponse;
import com.thiru.wealthlens.portfolio.dto.context.BuyContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitAndLossContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitLossContext;
import com.thiru.wealthlens.portfolio.dto.context.TradeOutcomeContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.dto.enums.HoldingType;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.repository.PortfolioRepository;
import com.thiru.wealthlens.portfolio.repository.TransactionRepository;
import com.thiru.wealthlens.portfolio.service.parser.AssetRequestParser;
import com.thiru.wealthlens.shared.dto.RedriveResult;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.entity.query.QueryFilter;
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import com.thiru.wealthlens.shared.util.math.DoubleUtil;
import com.thiru.wealthlens.shared.util.parser.ExcelBuilder;
import com.thiru.wealthlens.shared.util.parser.ExcelParser;
import com.thiru.wealthlens.shared.util.time.TLocalDate;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final TransactionService transactionService;
    private final PortfolioRepository portfolioRepository;
    private final ProfitAndLossService profitAndLossService;
    private final MongoTemplateService mongoTemplateService;
    private final TradeOutcomeService tradeOutcomeService;
    private final TransactionRepository transactionRepository;
//    private final UserBrokerChargeService userBrokerChargeService;
    private final TemporaryTransactionService temporaryTransactionService;

    /**
     * Multi-document write: creates a TransactionEntity and, for BUY/SELL, also
     * upserts an AssetEntity. Safe only when MongoDB replica set +
     * {@code app.mongodb.transactions-enabled=true} is set.
     */
    @Transactional
    public String addTransaction(UserMail userMail, AssetRequest assetRequest, List<String> filteredOutTransactions) {
        validateAssetRequest(userMail, assetRequest);
        if (temporaryTransactionService.hasTemporaryTransactions(userMail)) {
            throw new BadRequestException("There are pending temporary transactions. Please redrive them before adding a new transaction.");
        }
        return processTransaction(userMail, assetRequest, filteredOutTransactions);
    }

    private String processTransaction(UserMail userMail, AssetRequest assetRequest, List<String> filteredOutTransactions) {
        sanitizeAssetRequest(assetRequest);

        String filteredOutTransactionId = temporaryTransactionService.filterOutTransaction(userMail, assetRequest);
        if (filteredOutTransactionId != null) {
            filteredOutTransactions.add(filteredOutTransactionId);
            return "Transaction stored as temporary transaction, needs to perform after corporate action ..!";
        }

        TransactionType transactionType = assetRequest.getTransactionType();
        // Add transaction
        String transactionId = addTransactionInternal(userMail, assetRequest);

        return switch (transactionType) {
            case BUY -> {
                buyStock(userMail, transactionId, assetRequest);
                yield "Stock buy added to portfolio";
            }
            case SELL -> {
                sellStock(userMail, transactionId, assetRequest);
                yield "Stock sell updated in portfolio and profit and loss updated in profit and loss";
            }
        };
    }

    @Transactional
    public String addTransactionV2(UserMail userMail, AssetRequest assetRequest, List<String> filteredOutTransactions) {
        validateAssetRequest(userMail, assetRequest);
        sanitizeAssetRequest(assetRequest);

        String filteredOutTransaction = temporaryTransactionService.filterOutTransaction(userMail, assetRequest);
        if (filteredOutTransaction != null) {
            filteredOutTransactions.add(filteredOutTransaction);
            return "Transaction stored as temporary transaction, needs to perform after corporate action ..!";
        }

        TransactionType transactionType = assetRequest.getTransactionType();
        // Add transaction
        String transactionId = addTransactionInternal(userMail, assetRequest);

        return switch (transactionType) {
            case BUY -> {
                buyStockV2(userMail, transactionId, assetRequest);
                yield "Stock buy added to portfolio";
            }
            case SELL -> {
                sellStockV2(userMail, transactionId, assetRequest);
                yield "Stock sell updated in portfolio and profit and loss updated in profit and loss";
            }
        };
    }

    private static void validateAssetRequest(UserMail userMail, AssetRequest assetRequest) {
        if (null == assetRequest.getEmail() || assetRequest.getEmail().isBlank()) {
            return;
        }
        if (!userMail.getEmail().equals(assetRequest.getEmail())) {
            throw new BadRequestException("Email does not match");
        }

        if (DoubleUtil.equal(assetRequest.getQuantity(), 0)) {
            throw new BadRequestException("Invalid Quantity: " + assetRequest.getQuantity());
        }
    }

    private static void sanitizeAssetRequest(AssetRequest assetRequest) {
        if (assetRequest.getTransactionDate() == null) {
            assetRequest.setTransactionDate(LocalDate.now());
        }
    }

    /**
     * Batch multi-document write: parses a spreadsheet and calls addTransaction
     * for each row. Safe only when MongoDB replica set +
     * {@code app.mongodb.transactions-enabled=true} is set.
     */
    @Transactional
    public String uploadTransactions(UserMail userMail, String quarter, MultipartFile file) {
        if (temporaryTransactionService.hasTemporaryTransactions(userMail)) {
            throw new BadRequestException("There are pending temporary transactions. Please redrive them before uploading transactions. Some transactions may be blocked due to pending corporate actions during upload.");
        }

        List<String> errors = new ArrayList<>();
        AssetRequestParser assetRequestParser = new AssetRequestParser(quarter);
        List<AssetRequest> assetRequests = assetRequestParser.parse(file, errors);

        if (!errors.isEmpty()) {
            return "No transaction uploaded. Errors: " + errors;
        }

        List<String> filteredOutTransactions = new ArrayList<>();
        TCollectionUtil.map(assetRequests, assetRequest -> processTransaction(userMail, assetRequest, filteredOutTransactions));

        if (!filteredOutTransactions.isEmpty()) {
            return String.format("Filtered out transactions: %s", filteredOutTransactions);
        }
        return "All transactions uploaded successfully from: " + file.getOriginalFilename() + ", quarter: " + quarter;
    }

    /**
     * Reads all TEMPORARY transactions for a user and re-processes them.
     * Per-item try/catch (Chunk 1) makes this resilient to individual failures;
     * the @{@link Transactional} annotation is a best-effort wrapper.
     */
    @Transactional
    public RedriveResult redriveTemporaryTransactions(UserMail userMail) {

        List<TransactionEntity> userTemporaryTransactions = temporaryTransactionService.getAllTemporaryTransactions(userMail);

        List<String> succeeded = new ArrayList<>();
        Map<String, String> failed = new HashMap<>();
        List<String> stillFiltered = new ArrayList<>();
        List<String> filteredOut = new ArrayList<>();

        for (TransactionEntity tempTxn : userTemporaryTransactions) {
            AssetRequest assetRequest = tempTxn.getAssetRequest();
            if (assetRequest == null) {
                log.warn("Temporary transaction {} has no asset request, skipping", tempTxn.getId());
                failed.put(tempTxn.getId(), "No asset request stored");
                continue;
            }
            if (temporaryTransactionService.filterOutTransaction(userMail, assetRequest, true)) {
                log.info("Transaction {} still blocked by corporate action, skipping", tempTxn.getId());
                stillFiltered.add(tempTxn.getId());
                continue;
            }

            try {
                log.info("Redriving transaction: {}", tempTxn.getId());
                List<String> itemFiltered = new ArrayList<>();
                assetRequest.setTempTransactionId(tempTxn.getId());
                processTransaction(userMail, assetRequest, itemFiltered);

                if (!itemFiltered.isEmpty()) {
                    // Transaction was re-filtered during redrive; a new temp record was created.
                    // Do not update the original — it is still the source of truth.
                    filteredOut.add(tempTxn.getId());
                } else {
                    succeeded.add(tempTxn.getId());
                }
            } catch (Exception e) {
                log.error("Failed to redrive temporary transaction {}: {}", tempTxn.getId(), e.getMessage(), e);
                failed.put(tempTxn.getId(), e.getMessage());
            }
        }

        if (!succeeded.isEmpty()) {
            List<TransactionEntity> toUpdate = transactionRepository.findAllById(succeeded);
            for (TransactionEntity entity : toUpdate) {
                entity.setStatus(TransactionStatus.PROCESSED);
            }
            transactionRepository.saveAll(toUpdate);
        }

        String message = buildRedriveMessage(succeeded, failed, stillFiltered, filteredOut);
        return new RedriveResult(succeeded, failed, stillFiltered, filteredOut, message);
    }

    private static String buildRedriveMessage(List<String> succeeded, Map<String, String> failed, List<String> stillFiltered, List<String> filteredOut) {
        if (succeeded.isEmpty() && failed.isEmpty() && stillFiltered.isEmpty() && filteredOut.isEmpty()) {
            return "No temporary transactions to redrive";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Redrive complete. ");
        if (!succeeded.isEmpty()) {
            sb.append("Succeeded: ").append(succeeded.size());
        }
        if (!failed.isEmpty()) {
            if (!succeeded.isEmpty()) sb.append(", ");
            sb.append("Failed: ").append(failed.size());
        }
        if (!stillFiltered.isEmpty()) {
            if (!succeeded.isEmpty() || !failed.isEmpty()) sb.append(", ");
            sb.append("Still filtered: ").append(stillFiltered.size());
        }
        if (!filteredOut.isEmpty()) {
            if (!succeeded.isEmpty() || !failed.isEmpty() || !stillFiltered.isEmpty()) sb.append(", ");
            sb.append("Re-filtered: ").append(filteredOut.size());
        }
        return sb.toString();
    }

    public void buyStock(UserMail userMail, String transactionId, AssetRequest assetRequest) {

        String email = userMail.getEmail();
        assetRequest.setEmail(email);
        String stockCode = assetRequest.getStockCode();
        BrokerName brokerName = assetRequest.getBrokerName();
        String accountHolder = assetRequest.getAccountHolder();
        LocalDate transactionDate = assetRequest.getTransactionDate();

        Optional<AssetEntity> optionalStock = portfolioRepository.findByEmailAndStockCodeAndBrokerNameAndAccountHolderAndTransactionDate(email, stockCode, brokerName, accountHolder, transactionDate);
        AssetEntity assetEntity;
        if (optionalStock.isPresent()) {
            assetEntity = optionalStock.get();
            double existingQuantity = assetEntity.getQuantity();
            double newQuantity = existingQuantity + assetRequest.getQuantity();

            double existingTotalValue = assetEntity.getQuantity() * assetEntity.getPrice();
            double totalValueOfTransaction = getTotalValue(assetRequest);
            double newTotalValue = (existingTotalValue + totalValueOfTransaction);
            double newPrice = newTotalValue / newQuantity;
            double existingBrokerCharge = assetEntity.getBrokerCharges();
            double existingMiscCharges = assetEntity.getMiscCharges();

            assetEntity.setBrokerCharges(assetRequest.getBrokerCharges() + existingBrokerCharge);
            assetEntity.setMiscCharges(assetRequest.getMiscCharges() + existingMiscCharges);
            assetEntity.setPrice(newPrice);
            assetEntity.setQuantity(newQuantity);

            OrderTimeQuantity orderTimeQuantity = new OrderTimeQuantity();
            orderTimeQuantity.setOrderExecutionTime(assetRequest.orderExecutionDateTime());
            orderTimeQuantity.setQuantity(assetRequest.getQuantity());

            assetRequest.getOrderTimeQuantities().add(orderTimeQuantity);
        } else {
            assetEntity = assetRequest.asAsset();

            OrderTimeQuantity orderTimeQuantity = new OrderTimeQuantity();
            orderTimeQuantity.setOrderExecutionTime(assetRequest.orderExecutionDateTime());
            orderTimeQuantity.setQuantity(assetRequest.getQuantity());

            assetEntity.getOrderTimeQuantities().add(orderTimeQuantity);
        }

        // Update the broker charges entry
        updateBrokerChargesAndProfitAndLoss(userMail, transactionId, assetRequest);

        assetEntity.getBuyTransactionIds().add(transactionId);
        portfolioRepository.save(assetEntity);
    }

    public void buyStockV2(UserMail userMail, String transactionId, AssetRequest assetRequest) {

        String email = userMail.getEmail();
        assetRequest.setEmail(email);
        AssetEntity assetEntity = assetRequest.asAsset();
        double totalValueOfTransaction = getTotalValue(assetRequest);
//        assetEntity.setTotalValue(totalValueOfTransaction);

        // Update the broker charges entry
        updateBrokerChargesAndProfitAndLoss(userMail, transactionId, assetRequest);

        assetEntity.getBuyTransactionIds().add(transactionId);
        portfolioRepository.save(assetEntity);
    }

    public void sellStockV2(UserMail userMail, String transactionId, AssetRequest assetRequest) {

        String email = userMail.getEmail();
        String stockCode = assetRequest.getStockCode();
        BrokerName brokerName = assetRequest.getBrokerName();
        String accountHolder = assetRequest.getAccountHolder();
        LocalDate transactionDate = assetRequest.getTransactionDate();

        List<AssetEntity> stockEntities = portfolioRepository.findEligibleHoldingsForSell(email, stockCode, brokerName, accountHolder, transactionDate);
        validateTransaction(stockEntities, assetRequest);
        updateQuantityBySavingReportAndProfitAndLoss1(userMail, transactionId, stockEntities, assetRequest);

        List<String> updatedStockEntities = TCollectionUtil.applyMap(stockEntities, asset -> DoubleUtil.equal(0, asset.getQuantity()), AssetEntity::getId);
        portfolioRepository.saveAll(stockEntities);

        if (!updatedStockEntities.isEmpty()) {
            portfolioRepository.deleteAllById(updatedStockEntities);
        }
    }

    public void sellStock(UserMail userMail, String transactionId, AssetRequest assetRequest) {

        String email = userMail.getEmail();
        String stockCode = assetRequest.getStockCode();
        BrokerName brokerName = assetRequest.getBrokerName();
        String accountHolder = assetRequest.getAccountHolder();

        List<AssetEntity> stockEntities = portfolioRepository.findByEmailAndStockCodeAndBrokerNameAndAccountHolderOrderByTransactionDate(email, stockCode, brokerName, accountHolder);

        validateTransaction(stockEntities, assetRequest);

        updateQuantityBySavingReportAndProfitAndLoss(userMail, transactionId, stockEntities, assetRequest);

        List<String> updatedStockEntities = TCollectionUtil.applyMap(stockEntities, asset -> asset.getQuantity() == 0, AssetEntity::getId);
        portfolioRepository.saveAll(stockEntities);

        if (!updatedStockEntities.isEmpty()) {
            portfolioRepository.deleteAllById(updatedStockEntities);
        }
    }

    private static void validateTransaction(List<AssetEntity> stockEntities, AssetRequest assetRequest) {
        if (stockEntities.isEmpty()) {
            throw new IllegalArgumentException("Stock not found with code: " + assetRequest.getStockCode());
        }

        double existingQuantity = TCollectionUtil.mapToDouble(stockEntities, AssetEntity::getQuantity).sum();

        if (existingQuantity < assetRequest.getQuantity()) {
            throw new IllegalArgumentException("Not enough stocks to sell for: " + assetRequest.getStockCode());
        }
    }

    private String addTransactionInternal(UserMail userMail, AssetRequest assetRequest) {
        return transactionService.addTransaction(userMail, assetRequest);
    }

    public List<AssetResponse> getStockQuantity(UserMail userMail, String stockCode) {

        String email = userMail.getEmail();

        List<AssetEntity> stockEntities = portfolioRepository.findByEmailAndStockCodeOrderByTransactionDate(email, stockCode);

        if (stockEntities.isEmpty()) {
            throw new IllegalArgumentException("Stock not found");
        }

        return TCollectionUtil.map(stockEntities, asset -> TJsonMapper.copy(asset, AssetResponse.class));
    }

    public List<AssetResponse> getAllStocks(UserMail userMail) {

        List<AssetEntity> stockEntities = portfolioRepository.findByEmail(userMail.getEmail());
        Map<String, List<AssetEntity>> stockEntityMap = TCollectionUtil.groupingBy(stockEntities, stockWithCodeAndBroker());
        List<AssetResponse> responseEntities = TCollectionUtil.map(stockEntityMap.values(), PortfolioService::combineAllDetailsOfEntities);

        log.info("Fetching portfolio stocks of {}", userMail.getEmail());
        return responseEntities;
    }

    public List<AssetResponse> getStocksWithDateRange(UserMail userMail, LocalDate startDate, LocalDate endDate) {

        String email = userMail.getEmail();
        List<AssetEntity> stockEntities = portfolioRepository.findByEmailAndTransactionDateBetween(email, startDate, endDate);

        Map<String, List<AssetEntity>> stockEntityMap = TCollectionUtil.groupingBy(stockEntities, stockWithCodeAndBroker());
        List<AssetResponse> responseEntities = TCollectionUtil.map(stockEntityMap.values(), PortfolioService::combineAllDetailsOfEntities);

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
        assetResponse.setTransactionDate(null);
        return assetResponse;
    }

    private static double getTotalValue(AssetRequest assetRequest) {
        return assetRequest.getPrice() * assetRequest.getQuantity();
    }

    @SuppressWarnings({"removal"})
    private void updateQuantityBySavingReportAndProfitAndLoss(UserMail userMail, String transactionId, List<AssetEntity> stockEntities, AssetRequest assetRequest) {

        double sellQuantity = assetRequest.getQuantity();
        Iterator<AssetEntity> stockEntitiesIterator = stockEntities.iterator();
        while (sellQuantity > 0) {

            AssetEntity assetEntity = stockEntitiesIterator.next();
            assetEntity.getSellTransactionIds().add(transactionId);
            Double assetQuantity = assetEntity.getQuantity();

            TradeOutcomeContext tradeOutcomeContext;
            ProfitAndLossContext profitAndLossContext;
            double sellQty;
            if (sellQuantity >= assetQuantity) {
                tradeOutcomeContext = toTradeOutcomeContext(userMail.getEmail(), assetEntity, assetRequest, assetQuantity, transactionId);
                profitAndLossContext = ProfitAndLossContext.from(assetEntity, assetRequest, assetQuantity);
                sellQty = assetQuantity;

                assetEntity.setQuantity(0D);
                sellQuantity = sellQuantity - assetQuantity;
            } else {
                tradeOutcomeContext = toTradeOutcomeContext(userMail.getEmail(), assetEntity, assetRequest, sellQuantity, transactionId);
                profitAndLossContext = ProfitAndLossContext.from(assetEntity, assetRequest, sellQuantity);
                sellQty = sellQuantity;

                double remainingQuantity = assetQuantity - sellQuantity;
                assetEntity.setQuantity(remainingQuantity);
                sellQuantity = 0;
            }

            tradeOutcomeService.saveTradeOutcome(userMail, tradeOutcomeContext);
            profitAndLossService.updateProfitAndLoss(userMail, profitAndLossContext);
        }
    }

    public void updateBrokerChargesAndProfitAndLoss(UserMail userMail, String transactionId, AssetRequest assetRequest) {
        var profitLossContext = new ProfitLossContext(transactionId, assetRequest.getQuantity(), assetRequest.getTransactionDate(), assetRequest.getPrice(),
                assetRequest.getStockCode(), assetRequest.getBrokerName(), assetRequest.getExchangeName(), assetRequest.getAssetType(), TransactionType.BUY,
                null, assetRequest.getAccountType(), assetRequest.getAccountHolder(), Collections.emptyList());
        profitAndLossService.updateProfitAndLoss(userMail, profitLossContext);
    }

    public void updateQuantityBySavingReportAndProfitAndLoss1(UserMail userMail, String transactionId, List<AssetEntity> stockEntities, AssetRequest assetRequest) {

        double sellQuantity = assetRequest.getQuantity();
        Iterator<AssetEntity> stockEntitiesIterator = stockEntities.iterator();
        List<BuyContext> buyContexts = new ArrayList<>();
        while (sellQuantity > 0) {
            AssetEntity assetEntity = stockEntitiesIterator.next();
            assetEntity.getSellTransactionIds().add(transactionId);
            Double assetQuantity = assetEntity.getQuantity();

            if (sellQuantity >= assetQuantity) {
                buyContexts.add(new BuyContext(assetEntity.getQuantity(), assetEntity.getTransactionDate(), assetEntity.getPrice()));

                assetEntity.setQuantity(0D);
//                assetEntity.setTotalValue(0);
                sellQuantity = sellQuantity - assetQuantity;
            } else {
                buyContexts.add(new BuyContext(sellQuantity, assetEntity.getTransactionDate(), assetEntity.getPrice()));

                double remainingQuantity = assetQuantity - sellQuantity;
                assetEntity.setQuantity(remainingQuantity);
//                assetEntity.setTotalValue(remainingQuantity * assetEntity.getPrice());
                sellQuantity = 0;
            }
        }
        var profitLossContext = toProfitLossContext(assetRequest, buyContexts, transactionId);
        profitAndLossService.updateProfitAndLoss(userMail, profitLossContext);
    }

    private static ProfitLossContext toProfitLossContext(AssetRequest assetRequest, List<BuyContext> buyContexts, String transactionId) {

        double sellQuantity = assetRequest.getQuantity();
        LocalDate sellDate = assetRequest.getTransactionDate();
        double price = assetRequest.getPrice();
        AccountType accountType = assetRequest.getAccountType();
        String accountHolder = assetRequest.getAccountHolder();
        String stockCode = assetRequest.getStockCode();
        BrokerName brokerName = assetRequest.getBrokerName();

        return new ProfitLossContext(transactionId, sellQuantity, sellDate, price, stockCode, brokerName, assetRequest.getExchangeName(),
                assetRequest.getAssetType(), TransactionType.SELL, null, accountType, accountHolder, buyContexts);
    }

    private TradeOutcomeContext toTradeOutcomeContext(String email, AssetEntity assetEntity, AssetRequest assetRequest,
                                                       double sellQuantity, String transactionId) {

        LocalDate buyDate = assetEntity.getTransactionDate();
        LocalDate sellDate = assetRequest.getTransactionDate();

        // Pro-rate buy-side charges based on sell quantity
        double assetQuantity = assetEntity.getQuantity() != null ? assetEntity.getQuantity() : 1.0;
        double buyBrokerCharges = (assetEntity.getBrokerCharges() / assetQuantity) * sellQuantity;
        double buyMiscCharges = (assetEntity.getMiscCharges() / assetQuantity) * sellQuantity;

        // Pro-rate sell-side charges based on sell quantity
        double sellReqQuantity = assetRequest.getQuantity() != null ? assetRequest.getQuantity() : 1.0;
        double sellBrokerCharges = (assetRequest.getBrokerCharges() / sellReqQuantity) * sellQuantity;
        double sellMiscCharges = (assetRequest.getMiscCharges() / sellReqQuantity) * sellQuantity;

        // Buy side values
        double caAdjustedBuyPrice = assetEntity.getPrice();
        double originalBuyPrice = caAdjustedBuyPrice; // Will improve later with source transaction data
        double buyQty = sellQuantity;

        // Sell side values
        double sellPrice = assetRequest.getPrice();
        double sellQty = sellQuantity;

        // Computed values
        double totalBuyValue = (caAdjustedBuyPrice * sellQuantity) + buyBrokerCharges + buyMiscCharges;
        double totalSellValue = (sellPrice * sellQuantity) - sellBrokerCharges - sellMiscCharges;
        double netProfit = totalSellValue - totalBuyValue;
        double profitPercentage = totalBuyValue > 0 ? (netProfit / totalBuyValue) * 100 : 0.0;

        // Holding period and capital gains type
        long holdingPeriodDays = ChronoUnit.DAYS.between(buyDate, sellDate);
        CapitalGainsType capitalGainsType = holdingPeriodDays > 365 ? CapitalGainsType.LONG_TERM : CapitalGainsType.SHORT_TERM;

        // Financial year derivation (Indian FY: April - March)
        String financialYear = deriveFinancialYear(sellDate);

        // Check if CA-derived (bonus stock or price=0)
        boolean isCaDerived = (assetEntity.getCorporateActions() != null && !assetEntity.getCorporateActions().isEmpty())
                || (assetEntity.getPrice() == 0 && assetEntity.getCorporateActionType() != null);

        // Build context
        TradeOutcomeContext context = TradeOutcomeContext.builder()
                .email(email)
                .stockCode(assetEntity.getStockCode())
                .stockName(assetEntity.getStockName())
                .exchangeName(assetEntity.getExchangeName())
                .brokerName(assetEntity.getBrokerName())
                .assetType(assetEntity.getAssetType())
                .accountType(assetRequest.getAccountType())
                .accountHolder(assetRequest.getAccountHolder())
                .originalBuyPrice(originalBuyPrice)
                .caAdjustedBuyPrice(caAdjustedBuyPrice)
                .buyQuantity(buyQty)
                .buyDate(buyDate)
                .buyBrokerCharges(buyBrokerCharges)
                .buyMiscCharges(buyMiscCharges)
                .sellPrice(sellPrice)
                .sellQuantity(sellQty)
                .sellDate(sellDate)
                .sellBrokerCharges(sellBrokerCharges)
                .sellMiscCharges(sellMiscCharges)
                .totalBuyValue(totalBuyValue)
                .totalSellValue(totalSellValue)
                .netProfit(netProfit)
                .profitPercentage(profitPercentage)
                .holdingPeriodDays(holdingPeriodDays)
                .capitalGainsType(capitalGainsType)
                .financialYear(financialYear)
                .sourceSellTransactionId(transactionId)
                .sourceBuyLotId(assetEntity.getId())
                .isCaDerived(isCaDerived)
                .appliedCorporateActions(assetEntity.getCorporateActions())
                .build();

        return context;
    }

    private static String deriveFinancialYear(LocalDate transactionDate) {
        int transactionYear = transactionDate.getYear();
        LocalDate financialYearEnd = LocalDate.of(transactionYear, Month.MARCH, 31);

        if (transactionDate.isBefore(financialYearEnd)) {
            return (transactionYear - 1) + "-" + transactionYear;
        }

        return transactionYear + "-" + (transactionYear + 1);
    }

    public ProfitAndLossResponse getProfitAndLoss(UserMail userMail, String financialYear) {
        return profitAndLossService.getProfitAndLoss(userMail, financialYear);
    }

    public List<AssetEntity> searchAssets(UserMail userMail, List<QueryFilter> queryFilters) {
        return mongoTemplateService.getDocuments(userMail, queryFilters, AssetEntity.class);
    }

    public List<AssetResponse> getExportEntities(UserMail userMail, List<QueryFilter> queryFilters) {
        List<AssetEntity> entities = mongoTemplateService.getDocuments(userMail, queryFilters, AssetEntity.class);
        Map<String, List<AssetEntity>> stockEntityMap = TCollectionUtil.groupingBy(entities, stockWithCodeAndBroker());
        return TCollectionUtil.map(stockEntityMap.values(), PortfolioService::combineAllDetailsOfEntities);
    }

    public List<AssetEntity> stocksForCorporateActions(String stockCode, LocalDate recordDate) {
        return portfolioRepository.findByStockCodeAndTransactionDateBefore(stockCode, recordDate);
    }

    public List<AssetEntity> stocksForCorporateActions(String email, String stockCode, LocalDate recordDate) {
        return portfolioRepository.findByEmailAndStockCodeAndTransactionDateBefore(email, stockCode, recordDate);
    }

    public void saveCorporateActionProcessedStocks(List<AssetEntity> stocks) {
        portfolioRepository.saveAll(stocks);
    }

    /**
     * WARNING — deletes from 5 collections (portfolio, reports, transactions,
     * profitAndLoss, temporaryTransactions). Without a working MongoTransactionManager
     * a crash after deleting some collections but before others leaves orphaned data.
     * Each delete is wrapped in its own try/catch so one failure does not silently
     * abort the rest. With {@code app.mongodb.transactions-enabled=true} (Atlas replica
     * set) the @{@link Transactional} annotation guarantees atomicity.
     */
    @Transactional
    public String clearAllRecordsForCustomer(UserMail userMail) {

        log.info("Initiated deletion of all records of user: {}", userMail.getEmail());

        try {
            portfolioRepository.deleteByEmail(userMail.getEmail());
            log.info("Deleted all portfolio stocks for user: {}", userMail.getEmail());
        } catch (Exception e) {
            log.error("Failed to delete portfolio stocks for user: {} — continuing with remaining collections", userMail.getEmail(), e);
        }

        try {
            tradeOutcomeService.deleteByEmail(userMail);
            log.info("Deleted all trade outcomes for user: {}", userMail.getEmail());
        } catch (Exception e) {
            log.error("Failed to delete trade outcomes for user: {} — continuing with remaining collections", userMail.getEmail(), e);
        }

        try {
            transactionService.deleteTransactions(userMail);
            log.info("Deleted all transactions for user: {}", userMail.getEmail());
        } catch (Exception e) {
            log.error("Failed to delete transactions for user: {} — continuing with remaining collections", userMail.getEmail(), e);
        }

        try {
            profitAndLossService.deleteProfitAndLoss(userMail);
            log.info("Deleted all profit and loss reports for user: {}", userMail.getEmail());
        } catch (Exception e) {
            log.error("Failed to delete profit and loss for user: {} — continuing with remaining collections", userMail.getEmail(), e);
        }

        try {
            temporaryTransactionService.deleteTemporaryTransaction(userMail);
            log.info("Deleted all temporary transactions for user: {}", userMail.getEmail());
        } catch (Exception e) {
            log.error("Failed to delete temporary transactions for user: {}", userMail.getEmail(), e);
        }

        return "User: " + userMail.getEmail() + ", records and transactions deleted successfully";
    }

    public List<AssetResponse> getAssets(UserMail userMail, HoldingType holdingType) {

        String oneYearBeforeDate = TLocalDate.lastYearSameDateInString();

        List<AssetEntity> assetEntities = switch (holdingType) {
            case LONG_TERM -> getLongTermHeldAssets(userMail, oneYearBeforeDate);
            case SHORT_TERM -> getShortTermHeldAssets(userMail, oneYearBeforeDate);
        };

        Map<String, List<AssetEntity>> resultedAssetsMap = TCollectionUtil.groupingBy(assetEntities, stockWithCodeAndBroker());
        List<AssetResponse> resultedAssets = TCollectionUtil.map(resultedAssetsMap.values(), PortfolioService::combineAllDetailsOfEntities);

        List<String> stockCodes = TCollectionUtil.map(resultedAssets, AssetResponse::getStockCode);
        Map<String, Double> assetQuantityMap = TCollectionUtil.toMap(resultedAssets, stockCodeWithBroker(), AssetResponse::getQuantity);

        List<AssetResponse> allAssets = getStockEntities(userMail, new HashSet<>(stockCodes));

        Function<AssetResponse, AssetResponse> quantityUpdater = assetResponse -> {
            double quantity = assetResponse.getQuantity();
            assetResponse.setTotalQuantity(quantity);
            String stockWithBroker = assetResponse.getStockCode() + assetResponse.getBrokerName();
            assetResponse.setQuantity(assetQuantityMap.getOrDefault(stockWithBroker, 0.0));
            return assetResponse;
        };

        return TCollectionUtil.mapAndApply(allAssets, quantityUpdater, assetResponse -> assetResponse.getQuantity() > 0);
    }

    private Function<AssetEntity, String> stockWithCodeAndBroker() {
        return asset -> asset.getStockCode() + asset.getBrokerName().name();
    }

    private Function<AssetResponse, String> stockCodeWithBroker() {
        return assetResponse -> assetResponse.getStockCode() + assetResponse.getBrokerName();
    }

    private List<AssetResponse> getStockEntities(UserMail userMail, Collection<String> stockCodes) {

        List<AssetEntity> stockEntities = portfolioRepository.findByEmailAndStockCodeIn(userMail.getEmail(), stockCodes);
        Map<String, List<AssetEntity>> stockEntityMap = TCollectionUtil.groupingBy(stockEntities, stockWithCodeAndBroker());
        return TCollectionUtil.map(stockEntityMap.values(), PortfolioService::combineAllDetailsOfEntities);
    }

    private List<AssetEntity> getLongTermHeldAssets(UserMail userMail, String oneYearBeforeDate) {

        QueryFilter queryFilter = QueryFilter.builder().filterKey("transaction_date").value(oneYearBeforeDate).operation(QueryFilter.FilterOperation.LESSER_THAN).isDateField(true).build();

        List<QueryFilter> queryFilters = new ArrayList<>();
        queryFilters.add(queryFilter);

        return searchAssets(userMail, queryFilters);
    }

    private List<AssetEntity> getShortTermHeldAssets(UserMail userMail, String oneYearBeforeDate) {

        QueryFilter queryFilter = QueryFilter.builder().filterKey("transaction_date").value(oneYearBeforeDate).operation(QueryFilter.FilterOperation.GREATER_THAN).isDateField(true).build();

        List<QueryFilter> queryFilters = new ArrayList<>();
        queryFilters.add(queryFilter);

        return searchAssets(userMail, queryFilters);
    }

    public List<AssetResponse> getMutualFunds(UserMail userMail) {

        List<AssetEntity> mutualFunds = portfolioRepository.findByEmailAndAssetType(userMail.getEmail(), AssetType.MUTUAL_FUND);
        Map<String, List<AssetEntity>> fundsMap = TCollectionUtil.groupingBy(mutualFunds, stockWithCodeAndBroker());
        List<AssetResponse> responseEntities = TCollectionUtil.map(fundsMap.values(), PortfolioService::combineAllDetailsOfEntities);

        log.info("Fetch holding Mutual Funds of {}", userMail.getEmail());
        return responseEntities;
    }

    public void updateTransactions() {

        log.info("Initiated update of all records of user: {}", "userMail.getEmail()");

        List<AssetEntity> all = portfolioRepository.findAll();
        all.forEach(transaction -> {
            AssetType assetType = transaction.getAssetType();
            transaction.setAssetType(assetType == null ? AssetType.MUTUAL_FUND : assetType);
        });
        portfolioRepository.saveAll(all);

        log.info("Updated all portfolio stocks");
        log.info("ReportService.updateReports() removed — TradeOutcomeEntity uses typed schema");
        transactionService.updateTransactions();
        log.info("Updated all transactions");
    }

    public Pair<InputStreamResource, String> downloadTermAssets(UserMail userMail, HoldingType holdingType) {

        List<AssetResponse> assets = this.getAssets(userMail, holdingType);
        String fileName = ExcelParser.HOLDINGS_FILE_NAME;
        ByteArrayInputStream inputStream = ExcelBuilder.downloadAssets(assets, true);
        InputStreamResource resource = new InputStreamResource(inputStream);

        return Pair.of(resource, fileName);
    }
}
