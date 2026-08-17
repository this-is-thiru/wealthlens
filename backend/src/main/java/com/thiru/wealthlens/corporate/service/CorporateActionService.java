package com.thiru.wealthlens.corporate.service;
import com.thiru.wealthlens.corporate.dto.CorporateActionDto;
import com.thiru.wealthlens.corporate.dto.CorporateActionPerformDto;
import com.thiru.wealthlens.corporate.dto.context.DemergedStockContext;
import com.thiru.wealthlens.corporate.dto.context.DemergerContext;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.entity.LastlyPerformedCorporateAction;
import com.thiru.wealthlens.corporate.repository.CorporateActionRepository;
import com.thiru.wealthlens.corporate.repository.LastlyPerformedCorporateActionRepo;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.service.PortfolioQueryService;
import com.thiru.wealthlens.portfolio.service.PortfolioService;
import com.thiru.wealthlens.portfolio.service.TemporaryTransactionService;
import com.thiru.wealthlens.portfolio.service.TransactionService;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.collection.TJsonMapper;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class CorporateActionService {

    private static final int ONE = 1;
    private static final List<Month> QUARTER_START_MONTHS = List.of(Month.JANUARY, Month.APRIL, Month.JULY, Month.OCTOBER);

    private final PortfolioQueryService portfolioQueryService;
    private final PortfolioService portfolioService;
    private final TransactionService transactionService;
    private final TemporaryTransactionService temporaryTransactionService;
    private final CorporateActionRepository corporateActionRepository;
    private final LastlyPerformedCorporateActionRepo lastlyPerformedCorporateActionRepo;


    public String addCorporateAction(CorporateActionDto actionWrapper) {
        validateCorporateActionData(actionWrapper);

        CorporateActionEntity corporateActionEntity = actionWrapper.getAsEntity();
        List<CorporateActionEntity> actions = corporateActionRepository.findByStockCodeAndRecordDateAndOrderByPriorityAsc(actionWrapper.getStockCode(), actionWrapper.getRecordDate());
        CorporateActionEntity actionWithSamePriority = TCollectionUtil.findFirst(actions, action -> action.getPriority() == corporateActionEntity.getPriority());
        CorporateActionEntity actionForSameStock = TCollectionUtil.findFirst(actions, action -> action.getType() == corporateActionEntity.getType());

        if (actionForSameStock != null) {
            throw new IllegalArgumentException("Corporate actions is already present id: " + actionForSameStock.getId());
        }

        if (actionWithSamePriority != null) {
            List<String> actionIds = TCollectionUtil.map(actions, CorporateActionEntity::getId);
            throw new IllegalArgumentException("Update the priorities for the corporate actions with Ids: " + actionIds);
        }

        corporateActionRepository.save(corporateActionEntity);
        return "Corporate action: " + actionWrapper.getType() + " added successfully for stock: " + actionWrapper.getStockCode();
    }

    public CorporateActionEntity getCorporateActionDetails(String id) {
        Optional<CorporateActionEntity> corporateAction = corporateActionRepository.findById(id);
        if (corporateAction.isEmpty()) {
            throw new IllegalArgumentException("No corporate action found with id: " + id);
        }
        return corporateAction.get();
    }

    public String updateCorporateActionPriority(String id, int priority) {

        Optional<CorporateActionEntity> existingAction = corporateActionRepository.findById(id);
        if (existingAction.isEmpty()) {
            throw new IllegalArgumentException("No corporate actions found with id: " + id);
        }
        CorporateActionEntity entity = existingAction.get();
        int existingPriority = entity.getPriority();
        if (existingPriority == priority) {
            throw new IllegalArgumentException("Existing priority and new priority is same.");
        }
        entity.setPriority(priority);
        corporateActionRepository.save(entity);
        return "Priority has been updated for the action: " + id + " stock: " + entity.getStockCode() + " from: " + existingPriority + " to: " + priority;
    }

    public List<CorporateActionDto> getCorporateActions(List<String> ids) {
        List<CorporateActionEntity> actions = corporateActionRepository.findAllById(ids);
        return TCollectionUtil.map(actions, corporateAction -> TJsonMapper.copy(corporateAction, CorporateActionDto.class));
    }

//    public void performQuarterlyCorporateActions(String email, int year, int quarter) {
//
//        Month quarterStart = TLocalDate.firstMonthOfQuarter(quarter);
//        LocalDate fromDate = LocalDate.of(year, quarterStart, ONE);
//        LocalDate toDate = LocalDate.of(year, quarterStart.plus(3), ONE).minusDays(ONE);
//        List<CorporateActionEntity> corporateActions = corporateActionRepository.findByTypeInAndRecordDateBetween(CorporateActionType.FILTERABLE_CORPORATE_ACTIONS, fromDate, toDate);
//        for (CorporateActionEntity corporateAction : corporateActions) {
//            performQuarterlyCorporateAction(email, corporateAction);
//        }
//        log.info(corporateActions);
//    }
//
//    public void performQuarterlyCorporateAction(String email, CorporateActionEntity corporateAction) {
//
//        CorporateActionType action = corporateAction.getType();
//        if (Objects.requireNonNull(action) == CorporateActionType.BONUS) {
//            processBonusShares(email, corporateAction);
//        } else {
//            throw new IllegalArgumentException("Invalid action type" + action);
//        }
//        log.info("Quarterly corporate action: {} noted successfully for stock: {}", action, corporateAction.getStockCode());
//    }

    @Transactional
    public void deleteCorporateActions(String id) {
        corporateActionRepository.deleteById(id);
    }

    public List<CorporateActionEntity> getAllCorporateActions() {
        return corporateActionRepository.findAll();
    }

    /**
     * Iterates over corporate actions in the current quarter and processes them.
     * Each action may write to portfolio, transactions, and the performed-CAs table.
     * Safe only when MongoDB replica set + {@code app.mongodb.transactions-enabled=true} is set.
     */
    @Transactional
    public void performPendingCorporateActions(String email, CorporateActionPerformDto actionPerformDto, boolean allBrokers) {
        if (actionPerformDto.brokerName() == null && !allBrokers) {
            throw new IllegalArgumentException("Invalid action perform details: " + actionPerformDto);
        }

        if (actionPerformDto.brokerName() != null) {
            log.info("Performing corporate actions for broker: {}, for CA: {}", actionPerformDto.brokerName(), actionPerformDto);
            performPendingCorporateActions(email, actionPerformDto, actionPerformDto.brokerName());
            return;
        }

        for (BrokerName value : BrokerName.values()) {
            log.info("Performing corporate actions for all brokers for CA: {}", actionPerformDto);
            performPendingCorporateActions(email, actionPerformDto, value);
        }
    }

    private void performPendingCorporateActions(String email, CorporateActionPerformDto actionPerformDto, BrokerName brokerName) {

        Month quarterStart = Month.valueOf(actionPerformDto.month());
        if (!QUARTER_START_MONTHS.contains(quarterStart)) {
            throw new IllegalArgumentException("Invalid month: " + actionPerformDto.month());
        }

        LocalDate fromDate = LocalDate.of(actionPerformDto.year(), quarterStart, ONE);
        LocalDate toDate;
        if (Month.OCTOBER == quarterStart) {
            toDate = LocalDate.of(actionPerformDto.year() + 1, quarterStart.plus(3), ONE).minusDays(ONE);
        } else {
            toDate = LocalDate.of(actionPerformDto.year(), quarterStart.plus(3), ONE).minusDays(ONE);
        }

        List<CorporateActionEntity> corporateActions = getCurrentQuarterCorporateActions(fromDate, toDate);

        for (CorporateActionEntity corporateAction : corporateActions) {
            if (skipPendingCorporateAction(email, corporateAction)) {
                continue;
            }
            performPendingCorporateAction(email, corporateAction, brokerName);
        }

        log.info(corporateActions);
    }

    private List<CorporateActionEntity> getCurrentQuarterCorporateActions(LocalDate start, LocalDate transactionDate) {
        return corporateActionRepository.findByTypeInAndRecordDateBetween(CorporateActionType.FILTERABLE_CORPORATE_ACTIONS, start, transactionDate);
    }

    private boolean skipPendingCorporateAction(String email, CorporateActionEntity corporateAction) {

        String stockCode = corporateAction.getStockCode();
        AssetType assetType = corporateAction.getAssetType();
        LocalDate recordDate = corporateAction.getRecordDate();

        List<TransactionEntity> beforeTemporaryTransactions = temporaryTransactionService
                .findTempTransactionsBefore(email, stockCode, assetType, recordDate);

        return !beforeTemporaryTransactions.isEmpty();
    }

    public void performPendingCorporateAction(String email, CorporateActionEntity corporateAction, BrokerName brokerName) {

        String stockCode = corporateAction.getStockCode();
        CorporateActionType corporateActionType = corporateAction.getType();
        AssetType assetType = corporateAction.getAssetType();
        LocalDate recordDate = corporateAction.getRecordDate();

        var lastlyPerformedCA = lastlyPerformedCorporateActionRepo.findLastPerformedCA(email, stockCode, assetType, corporateActionType, brokerName);

        if (lastlyPerformedCA.isPresent()) {
            LastlyPerformedCorporateAction lastlyPerformedCorporateAction = lastlyPerformedCA.get();
            if (lastlyPerformedCorporateAction.getActionDate().isAfter(corporateAction.getRecordDate().minusDays(ONE))) {
                log.warn("Corporate action: {} for stock: {}, and broker: {} is already performed with record date: {}", corporateActionType, stockCode, brokerName, recordDate);
                return;
            }
        }

        CorporateActionType action = corporateAction.getType();

        switch (action) {
            case BONUS -> processBonusShares(email, corporateAction, brokerName);
            case DEMERGER -> processDemergerOfShares(email, corporateAction, brokerName);
            case STOCK_SPLIT -> processStockSplit(email, corporateAction, brokerName);
            default -> throw new IllegalArgumentException("Invalid action type" + action);
        }

        log.info("Corporate action: {} noted successfully for stock: {}", action, corporateAction.getStockCode());
    }

    /**
     * Processes a single corporate action (stock-split or name-change) by writing
     * to portfolio and transaction collections. Safe only when MongoDB replica set
     * + {@code app.mongodb.transactions-enabled=true} is set.
     */
    @Transactional
    public String performCorporateAction(CorporateActionDto actionWrapper) {

        CorporateActionType action = actionWrapper.getType();
        switch (action) {
            case STOCK_SPLIT -> processStockSplit(actionWrapper);
            case NAME_OR_SYMBOL_CHANGE -> processNameOrSymbolChange(actionWrapper);
            default -> throw new IllegalArgumentException("Invalid action type" + action);
        }

        return "Corporate action: " + action + " noted successfully for stock: " + actionWrapper.getStockCode();
    }

    // TODO: Remove - Doesn't work, need to fix
    @Deprecated
    private void processStockSplit(CorporateActionDto actionWrapper) {

        String stockCode = actionWrapper.getStockCode();
        LocalDate recordDate = actionWrapper.getRecordDate();
        String[] splitRatio = actionWrapper.getRatio().split(":");
        double multiplier = Integer.parseInt(splitRatio[0]);
        double ratio = Double.parseDouble(splitRatio[1]);

        double quantityMultiplier = multiplier / ratio;
        double priceMultiplier = 1 / quantityMultiplier;

        List<AssetEntity> stockEntities = portfolioService.stocksForCorporateActions(stockCode, recordDate);

        double quantity = 0;
        for (AssetEntity assetEntity : stockEntities) {
            double previousQuantity = assetEntity.getQuantity();

            assetEntity.setQuantity(previousQuantity * quantityMultiplier);
            assetEntity.setPrice(assetEntity.getPrice() * priceMultiplier);

            CorporateActionEntity corporateActionDto = actionWrapper.getAsEntity();
            assetEntity.getCorporateActions().add(corporateActionDto);
            quantity += previousQuantity;
        }
        portfolioService.saveCorporateActionProcessedStocks(stockEntities);

        List<TransactionEntity> transactionEntities = transactionService.transactionsForCorporateActions(quantity, stockCode, recordDate);

        for (TransactionEntity transactionEntity : transactionEntities) {
            double previousQuantity = transactionEntity.getQuantity();

            transactionEntity.setQuantity(previousQuantity * quantityMultiplier);
            transactionEntity.setPrice(transactionEntity.getPrice() * priceMultiplier);

            CorporateActionEntity corporateActionDto = actionWrapper.getAsEntity();
            transactionEntity.getCorporateActions().add(corporateActionDto);
        }
        transactionService.saveCorporateActionProcessedTransactions(transactionEntities);
    }

    private void processNameOrSymbolChange(CorporateActionDto actionWrapper) {

        String stockCode = actionWrapper.getStockCode();
        LocalDate recordDate = actionWrapper.getRecordDate();

        List<AssetEntity> stockEntities = portfolioService.stocksForCorporateActions(stockCode, recordDate);
        for (AssetEntity assetEntity : stockEntities) {
            assetEntity.setStockCode(actionWrapper.getToStockCode());
            assetEntity.setStockName(actionWrapper.getToStockName());
            CorporateActionEntity corporateActionDto = actionWrapper.getAsEntity();
            assetEntity.getCorporateActions().add(corporateActionDto);
        }
        portfolioService.saveCorporateActionProcessedStocks(stockEntities);

        List<TransactionEntity> transactionEntities = transactionService.transactionsForCorporateActions(stockCode, recordDate);
        for (TransactionEntity transactionEntity : transactionEntities) {
            transactionEntity.setStockCode(actionWrapper.getToStockCode());
            transactionEntity.setStockName(actionWrapper.getToStockName());
            CorporateActionEntity corporateActionDto = actionWrapper.getAsEntity();
            transactionEntity.getCorporateActions().add(corporateActionDto);
        }
        transactionService.saveCorporateActionProcessedTransactions(transactionEntities);
        log.info("Processed name or symbol change for stock: {} to: {}", stockCode, actionWrapper.getToStockCode());
    }

    public void processBonusShares(String email, CorporateActionEntity corporateAction, BrokerName brokerName) {

        String stockCode = corporateAction.getStockCode();
        LocalDate recordDate = corporateAction.getRecordDate();

        List<AssetEntity> stockEntities = portfolioService.stocksForCorporateActions(email, stockCode, recordDate);
        if (stockEntities.isEmpty()) {
            handleEmptyAssetsForCorporateAction(email, corporateAction, brokerName);
        }

        Map<BrokerName, List<AssetEntity>> brokerNameAndStocksMap = TCollectionUtil.groupingBy(stockEntities, AssetEntity::getBrokerName);
        for (Map.Entry<BrokerName, List<AssetEntity>> entry : brokerNameAndStocksMap.entrySet()) {
            if (!brokerName.equals(entry.getKey())) {
                log.info("CA skipping for broker name {} and stock {}", entry.getKey(), stockCode);
                continue;
            }
            processBonusShares(email, corporateAction, entry.getKey(), entry.getValue());
            updateLastlyPerformedCorporateAction(email, stockCode, corporateAction.getAssetType(), corporateAction.getType(), corporateAction.getExDate(), entry.getKey());
        }

    }

    public void processBonusShares(String email, CorporateActionEntity corporateAction, BrokerName brokerName, List<AssetEntity> stockEntities) {
        String stockCode = corporateAction.getStockCode();

        int totalShares = TCollectionUtil.map(stockEntities, assetEntity -> assetEntity.getQuantity().intValue()).stream().reduce(0, Integer::sum);
        String[] splitRatio = corporateAction.getRatio().split(":");
        int numerator = Integer.parseInt(splitRatio[0]);
        int denominator = Integer.parseInt(splitRatio[1]);
        int bonusSharesCount = totalShares * numerator / denominator;

        List<String> newTransactionIds = processBonusSharesTxns(email, bonusSharesCount, corporateAction, brokerName);
        if (newTransactionIds.size() != 1) {
            log.warn("Bonus shares txns: {} added for symbol: {} in Broker: {} with multiple transactions", newTransactionIds, stockCode, brokerName);
            throw new IllegalArgumentException("Multiple transactions added for bonus shares: " + newTransactionIds);
        }

        // Portfolio stock
        AssetEntity firstAsset = stockEntities.getFirst();
        AssetEntity bonusSharesEntity = TJsonMapper.copy(firstAsset, AssetEntity.class);
        bonusSharesEntity.setId(null);
        bonusSharesEntity.setQuantity((double) bonusSharesCount);
        bonusSharesEntity.setPrice(0);
        bonusSharesEntity.setTransactionDate(corporateAction.getExDate());
        bonusSharesEntity.setOrderTimeQuantities(List.of());
        bonusSharesEntity.setCorporateActionType(CorporateActionType.BONUS);
        bonusSharesEntity.setBuyTransactionIds(newTransactionIds);
        stockEntities.add(bonusSharesEntity);

        for (AssetEntity assetEntity : stockEntities) {

            CorporateActionEntity action = TJsonMapper.copy(corporateAction, CorporateActionEntity.class);
            assetEntity.getCorporateActions().add(action);
        }
        portfolioService.saveCorporateActionProcessedStocks(stockEntities);

        log.info("Bonus shares: {} added for symbol: {} in Broker: {}", newTransactionIds.getFirst(), stockCode, brokerName);
    }

    public List<String> processBonusSharesTxns(String email, int bonusSharesCount, CorporateActionEntity corporateAction, BrokerName brokerName) {

        String stockCode = corporateAction.getStockCode();
        LocalDate recordDate = corporateAction.getRecordDate();

        // Transaction update
        List<TransactionEntity> transactionEntities = transactionService.testTransactionsForCorporateActions(email, stockCode, brokerName, recordDate);
        List<String> existingTransactionIds = TCollectionUtil.map(transactionEntities, TransactionEntity::getId);

        TransactionEntity first = TCollectionUtil.filter(transactionEntities, transaction -> TransactionType.BUY.equals(transaction.getTransactionType())).getFirst();
        TransactionEntity bonusSharesTransaction = TJsonMapper.copy(first, TransactionEntity.class);
        bonusSharesTransaction.setId(null);
        bonusSharesTransaction.setPrice(0);
        bonusSharesTransaction.setQuantity((double) bonusSharesCount);
        bonusSharesTransaction.setTotalValue(0);
        bonusSharesTransaction.setTransactionDate(corporateAction.getExDate());
        bonusSharesTransaction.setCorporateActionType(CorporateActionType.BONUS);
        transactionEntities.add(bonusSharesTransaction);

        for (TransactionEntity transactionEntity : transactionEntities) {
            CorporateActionEntity action = TJsonMapper.copy(corporateAction, CorporateActionEntity.class);
            transactionEntity.getCorporateActions().add(action);
        }
        List<String> newTransactionIds = transactionService.saveCorporateActionProcessedTransactions(transactionEntities);
        newTransactionIds.removeAll(existingTransactionIds);

        return newTransactionIds;
    }

    private void updateLastlyPerformedCorporateAction(String email, String stockCode, AssetType assetType, CorporateActionType actionType, LocalDate exDate, BrokerName brokerName) {

        Optional<LastlyPerformedCorporateAction> lpcaOptional = lastlyPerformedCorporateActionRepo
                .findLastPerformedCA(email, stockCode, assetType, actionType, brokerName);
        LastlyPerformedCorporateAction lastlyPerformedCorporateAction = lpcaOptional.orElse(LastlyPerformedCorporateAction.builder()
                .email(email).stockCode(stockCode).assetType(assetType).actionType(actionType).brokerName(brokerName).actionDate(exDate).build());

        lastlyPerformedCorporateAction.setActionDate(exDate);
        lastlyPerformedCorporateActionRepo.save(lastlyPerformedCorporateAction);
    }

    public void processDemergerOfShares(String email, CorporateActionEntity corporateAction, BrokerName brokerName) {

        String stockCode = corporateAction.getStockCode();
        LocalDate recordDate = corporateAction.getRecordDate();

        List<AssetEntity> stockEntities = portfolioService.stocksForCorporateActions(email, stockCode, recordDate);
        if (stockEntities.isEmpty()) {
            handleEmptyAssetsForCorporateAction(email, corporateAction, brokerName);
            return;
        }

        Map<BrokerName, List<AssetEntity>> brokerNameAndStocksMap = TCollectionUtil.groupingBy(stockEntities, AssetEntity::getBrokerName);
        for (Map.Entry<BrokerName, List<AssetEntity>> entry : brokerNameAndStocksMap.entrySet()) {
            if (!brokerName.equals(entry.getKey())) {
                log.info("CA skipping for broker name {} and stock {}", entry.getKey(), stockCode);
                continue;
            }
            processDemergerOfShares(corporateAction, entry.getKey(), entry.getValue());
            updateLastlyPerformedCorporateAction(email, stockCode, corporateAction.getAssetType(), corporateAction.getType(), corporateAction.getExDate(), entry.getKey());
        }
    }

    public void processStockSplit(String email, CorporateActionEntity corporateAction, BrokerName brokerName) {

        String stockCode = corporateAction.getStockCode();
        LocalDate recordDate = corporateAction.getRecordDate();

        List<AssetEntity> stockEntities = portfolioService.stocksForCorporateActions(email, stockCode, recordDate);
        if (stockEntities.isEmpty()) {
            handleEmptyAssetsForCorporateAction(email, corporateAction, brokerName);
            return;
        }

        Map<BrokerName, List<AssetEntity>> brokerNameAndStocksMap = TCollectionUtil.groupingBy(stockEntities, AssetEntity::getBrokerName);
        for (Map.Entry<BrokerName, List<AssetEntity>> entry : brokerNameAndStocksMap.entrySet()) {
            if (!brokerName.equals(entry.getKey())) {
                log.info("CA skipping for broker name {} and stock {}", entry.getKey(), stockCode);
                continue;
            }
            processStockSplit(corporateAction, entry.getKey(), entry.getValue());
            updateLastlyPerformedCorporateAction(email, stockCode, corporateAction.getAssetType(), corporateAction.getType(), corporateAction.getExDate(), entry.getKey());
        }
    }

    private void processStockSplit(CorporateActionEntity corporateAction, BrokerName brokerName, List<AssetEntity> stockEntities) {

        String stockCode = corporateAction.getStockCode();

        int totalShares = TCollectionUtil.map(stockEntities, assetEntity -> assetEntity.getQuantity().intValue()).stream().reduce(0, Integer::sum);
        String[] splitRatio = corporateAction.getRatio().split(":");
        int numerator = Integer.parseInt(splitRatio[0]);
        int denominator = Integer.parseInt(splitRatio[1]);
        int bonusSharesCount = totalShares * numerator / denominator;
//
//        String[] demergerPriceRatio = demergerDetail.getDemergerPriceRatio().split(":");
//        double mainStockPriceRatio = Double.parseDouble(demergerPriceRatio[0]);
//        double secondaryStockPriceRatio = Double.parseDouble(demergerPriceRatio[1]);
//
//        if (demergerDetail.getDemergerStocks().size() != 1) {
//            throw new IllegalArgumentException("Invalid demerger ratio format");
//        }
//
//        var newDemergerStock = demergerDetail.getDemergerStocks().getFirst();
//        var demergedStockContext = new DemergedStockContext(newDemergerStock.getStockCode(), newDemergerStock.getStockName(), secondaryStockPriceRatio, secondaryStockRatio);
//
//        List<AssetEntity> finalDemergedStocks = new ArrayList<>();
//        for (AssetEntity assetEntity : stockEntities) {
//            var demergerContext = new DemergerContext(demergerDetail.getMainStockCode(), demergerDetail.getMainStockName(), mainStockPriceRatio, mainStockRatio, assetEntity, new ArrayList<>(Collections.singleton(demergedStockContext)));
//            List<AssetEntity> demergedStocks = processStockSplitForEachAssetEntry(demergerContext);
//
//            CorporateActionEntity action = TJsonMapper.copy(corporateAction, CorporateActionEntity.class);
//            demergedStocks.parallelStream().forEach(a -> a.getCorporateActions().add(action));
//            finalDemergedStocks.addAll(demergedStocks);
//        }
//
//        portfolioService.saveCorporateActionProcessedStocks(finalDemergedStocks);
//        log.info("Demerger of shares added for symbol: {} in Broker: {}", stockCode, brokerName);
    }

    public List<AssetEntity> processStockSplitForEachAssetEntry(DemergerContext demergerContext) {
//        double mainStockPricePercentage = demergerContext.pricePercentage();
//        AssetEntity entity = demergerContext.entity();
//
//        List<AssetEntity> demergedStocks = new ArrayList<>();
//        for (var demergerStockContext : demergerContext.demergedStocks()) {
//            AssetEntity asNewEntity = asNewEntity(demergerStockContext, entity);
//            demergedStocks.add(asNewEntity);
//        }
//
//        var oldPrice = entity.getPrice();
//        double newPrice = oldPrice * (mainStockPricePercentage / 100);
//        entity.setPrice(newPrice);
//        entity.setStockCode(demergerContext.stockCode());
//        entity.setStockName(demergerContext.stockName());
//        demergedStocks.add(entity);
//
        return null;
    }

    private void processDemergerOfShares(CorporateActionEntity corporateAction, BrokerName brokerName, List<AssetEntity> stockEntities) {

        String stockCode = corporateAction.getStockCode();
        var demergerDetail = corporateAction.getDemergerDetail();
        String[] demergerRatio = demergerDetail.getDemergerRatio().split(":");
        int mainStockRatio = Integer.parseInt(demergerRatio[0]);
        int secondaryStockRatio = Integer.parseInt(demergerRatio[1]);

        String[] demergerPriceRatio = demergerDetail.getDemergerPriceRatio().split(":");
        double mainStockPriceRatio = Double.parseDouble(demergerPriceRatio[0]);
        double secondaryStockPriceRatio = Double.parseDouble(demergerPriceRatio[1]);

        if (demergerDetail.getDemergerStocks().size() != 1) {
            throw new IllegalArgumentException("Invalid demerger ratio format");
        }

        var newDemergerStock = demergerDetail.getDemergerStocks().getFirst();
        var demergedStockContext = new DemergedStockContext(newDemergerStock.getStockCode(), newDemergerStock.getStockName(), secondaryStockPriceRatio, secondaryStockRatio);

        List<AssetEntity> finalDemergedStocks = new ArrayList<>();
        for (AssetEntity assetEntity : stockEntities) {
            var demergerContext = new DemergerContext(demergerDetail.getMainStockCode(), demergerDetail.getMainStockName(), mainStockPriceRatio, mainStockRatio, assetEntity, new ArrayList<>(Collections.singleton(demergedStockContext)));
            List<AssetEntity> demergedStocks = processDemergerForEachAssetEntry(demergerContext);

            CorporateActionEntity action = TJsonMapper.copy(corporateAction, CorporateActionEntity.class);
            demergedStocks.parallelStream().forEach(a -> a.getCorporateActions().add(action));
            finalDemergedStocks.addAll(demergedStocks);
        }

        portfolioService.saveCorporateActionProcessedStocks(finalDemergedStocks);
        log.info("Demerger of shares added for symbol: {} in Broker: {}", stockCode, brokerName);
    }

    public List<AssetEntity> processDemergerForEachAssetEntry(DemergerContext demergerContext) {
        double mainStockPricePercentage = demergerContext.pricePercentage();
        AssetEntity entity = demergerContext.entity();

        List<AssetEntity> demergedStocks = new ArrayList<>();
        for (var demergerStockContext : demergerContext.demergedStocks()) {
            AssetEntity asNewEntity = asNewEntity(demergerStockContext, entity);
            demergedStocks.add(asNewEntity);
        }

        var oldPrice = entity.getPrice();
        double newPrice = oldPrice * (mainStockPricePercentage / 100);
        entity.setPrice(newPrice);
        entity.setStockCode(demergerContext.stockCode());
        entity.setStockName(demergerContext.stockName());
        demergedStocks.add(entity);

        return demergedStocks;
    }

    private static AssetEntity asNewEntity(DemergedStockContext demergerContext, AssetEntity entity) {
        var newEntity = new AssetEntity();
        newEntity.setEmail(entity.getEmail());
        newEntity.setExchangeName(entity.getExchangeName());
        newEntity.setBrokerName(entity.getBrokerName());
        newEntity.setAssetType(entity.getAssetType());
        newEntity.setTransactionDate(entity.getTransactionDate());
        newEntity.setOrderTimeQuantities(entity.getOrderTimeQuantities());
        newEntity.setAccountType(entity.getAccountType());
        newEntity.setAccountHolder(entity.getAccountHolder());
        newEntity.setTransactionType(TransactionType.BUY);

        newEntity.setStockCode(demergerContext.stockCode());
        newEntity.setStockName(demergerContext.stockName());
        newEntity.setQuantity(demergerContext.quantityRatio() * entity.getQuantity());
        newEntity.setPrice(demergerContext.pricePercentage() * (entity.getPrice() / 100));
        newEntity.setCorporateActionType(CorporateActionType.DEMERGER);
        return newEntity;
    }

    private void validateCorporateActionData(CorporateActionDto actionWrapper) {
        if (actionWrapper == null) {
            throw new IllegalArgumentException("Corporate action data is missing");
        }
        if (StringUtils.isBlank(actionWrapper.getStockCode())) {
            throw new IllegalArgumentException("Stock code is missing");
        }
        if (actionWrapper.getType() == null) {
            throw new IllegalArgumentException("Invalid corporate action type");
        }
        if (actionWrapper.getRecordDate() == null) {
            throw new IllegalArgumentException("Record date is invalid");
        }
        if (actionWrapper.getExDate() == null) {
            throw new IllegalArgumentException("Ex date is invalid");
        }

        switch (actionWrapper.getType()) {
            case DEMERGER -> validateDemergerData(actionWrapper.getDemergerDetail());
            case BONUS -> validateBonusData(actionWrapper);
        }
    }

    private void validateDemergerData(CorporateActionDto.DemergerDetailDto demergerDetail) {
        if (demergerDetail == null) {
            throw new IllegalArgumentException("Demerger data is missing");
        }
        String demergerRatio = demergerDetail.demergerRatio();
        String demergerPriceRatio = demergerDetail.demergerPriceRatio();
        String[] demergerRatios = demergerRatio.split(":");
        String[] demergerPriceRatios = demergerPriceRatio.split(":");

        // TODO: come to this for uneven ratios
        if (demergerRatios.length != 2) {
            throw new IllegalArgumentException("Invalid demerger ratio format");
        }

        if (demergerRatios.length != demergerDetail.demergerStocks().size() + 1) {
            throw new IllegalArgumentException("Demerger ratios must have one more element than the number of demerger stocks");
        }

        if (demergerPriceRatios.length != demergerRatios.length) {
            throw new IllegalArgumentException("Demerger ratios and price ratios must have the same length");
        }
    }

    private void validateBonusData(CorporateActionDto corporateActionDto) {
        String[] splitRatio = corporateActionDto.getRatio().split(":");
        if (splitRatio.length != 2) {
            throw new IllegalArgumentException("Invalid bonus ratio format");
        }
    }

    private void handleEmptyAssetsForCorporateAction(String email, CorporateActionEntity corporateAction, BrokerName brokerName) {
        String stockCode = corporateAction.getStockCode();
        LocalDate recordDate = corporateAction.getRecordDate();

        log.info("No stock found for corporate action: {} for stock: {} on record date: {}", corporateAction.getType(), stockCode, recordDate);
        updateLastlyPerformedCorporateAction(email, stockCode, corporateAction.getAssetType(), corporateAction.getType(), corporateAction.getExDate(), brokerName);
    }
}
