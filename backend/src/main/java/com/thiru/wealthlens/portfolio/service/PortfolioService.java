package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.brokercharges.config.ChargeEngineProperties;
import com.thiru.wealthlens.brokercharges.dto.context.ChargeComputation;
import com.thiru.wealthlens.portfolio.dto.Acquisition;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.OrderTimeQuantity;
import com.thiru.wealthlens.portfolio.dto.context.BuyContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitAndLossContext;
import com.thiru.wealthlens.portfolio.dto.context.ProfitLossContext;
import com.thiru.wealthlens.portfolio.dto.context.TradeOutcomeContext;
import com.thiru.wealthlens.portfolio.dto.context.TransactionRecord;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeFlow;
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
import com.thiru.wealthlens.shared.exception.BadRequestException;
import com.thiru.wealthlens.shared.util.collection.TCollectionUtil;
import com.thiru.wealthlens.shared.util.math.DoubleUtil;
import com.thiru.wealthlens.shared.util.time.FinancialYear;
import com.thiru.wealthlens.shared.util.transaction.ExcelHeaders;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Log4j2
@Service
@RequiredArgsConstructor
public class PortfolioService {

    /**
     * Returned when a submission was recognised as one already applied. Deliberately not an error:
     * a retry that reaches this has got exactly what it asked for.
     */
    private static final String REPLAYED_MESSAGE =
            "Transaction already recorded; this submission was a repeat and changed nothing";

    private final TransactionService transactionService;
    private final PortfolioRepository portfolioRepository;
    private final ProfitAndLossService profitAndLossService;
    private final TradeOutcomeService tradeOutcomeService;
    private final TransactionRepository transactionRepository;
    private final TemporaryTransactionService temporaryTransactionService;
    private final ChargeRecordingGateway chargeRecordingGateway;
    private final ChargeEngineProperties chargeEngineProperties;

    private final TradeOutcomeRecorder tradeOutcomeRecorder;

    /**
     * Multi-document write: creates a TransactionEntity and, for BUY/SELL, also
     * upserts an AssetEntity. Safe only when MongoDB replica set +
     * {@code app.mongodb.transactions-enabled=true} is set.
     */
    @Transactional
    public String addTransaction(UserMail userMail, AssetRequest assetRequest, List<String> filteredOutTransactions) {
        if (temporaryTransactionService.hasTemporaryTransactions(userMail)) {
            throw new BadRequestException("There are pending temporary transactions. Please redrive them before adding a new transaction.");
        }
        return processTransaction(userMail, assetRequest, filteredOutTransactions);
    }

    /** V1's processing of one trade: the V1 route, every upload row and a V1 redrive pass here, so it checks the trade (W-277). */
    private String processTransaction(UserMail userMail, AssetRequest assetRequest, List<String> filteredOutTransactions) {
        validateAssetRequest(userMail, assetRequest);
        sanitizeAssetRequest(assetRequest);

        String filteredOutTransactionId = temporaryTransactionService.filterOutTransaction(userMail, assetRequest, TradeFlow.V1);
        if (filteredOutTransactionId != null) {
            filteredOutTransactions.add(filteredOutTransactionId);
            return "Transaction stored as temporary transaction, needs to perform after corporate action ..!";
        }

        TransactionType transactionType = assetRequest.getTransactionType();
        // Add transaction
        TransactionRecord record = transactionService.recordTransaction(userMail, assetRequest, TradeFlow.V1);
        if (record.replay()) {
            // This submission was already applied. Before TL-7 the duplicate transaction row was
            // suppressed here and the trade was then applied to the portfolio a second time anyway,
            // adding the holding twice. buyStock and sellStock are unchanged -- this returns before
            // reaching them.
            return REPLAYED_MESSAGE;
        }
        String transactionId = record.transactionId();

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
        return processTransactionV2(userMail, assetRequest, filteredOutTransactions);
    }

    /** V2's twin of {@link #processTransaction}: shared by the V2 route and the redrive of a trade V2 received (W-270). */
    private String processTransactionV2(UserMail userMail, AssetRequest assetRequest, List<String> filteredOutTransactions) {
        validateAssetRequest(userMail, assetRequest);
        sanitizeAssetRequest(assetRequest);

        String filteredOutTransaction = temporaryTransactionService.filterOutTransaction(userMail, assetRequest, TradeFlow.V2);
        if (filteredOutTransaction != null) {
            filteredOutTransactions.add(filteredOutTransaction);
            return "Transaction stored as temporary transaction, needs to perform after corporate action ..!";
        }

        TransactionType transactionType = assetRequest.getTransactionType();
        // Add transaction
        TransactionRecord record = transactionService.recordTransaction(userMail, assetRequest, TradeFlow.V2);
        if (record.replay()) {
            return REPLAYED_MESSAGE;
        }
        String transactionId = record.transactionId();

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

    /**
     * Everything a trade must satisfy before it is allowed to change a holding.
     *
     * <p>The email check is conditional — the path is authenticated and the field is optional, so it
     * only matters when the caller supplies one. <b>Everything after it is not.</b> This method used
     * to {@code return} when the email was absent, which skipped the only other check there was, so
     * a request with no email and no quantity was accepted.
     *
     * <p>Quantity must be positive rather than merely non-zero. The previous check was
     * {@code equal(quantity, 0)}, which let a negative through: a negative buy creates a negative
     * holding and a negative sell increases one, and neither is recoverable from the stored document
     * alone. Price was not checked at all.
     *
     * <p>Zero price is deliberately allowed. Bonus shares and split allotments are issued free, and
     * refusing them here would block the corporate-action flow that creates them.
     *
     * <p>A Gold Bond's maturity is a fact of the tranche it belongs to; no instrument data is held to
     * read it from, so the trade carries it rather than have one assumed (D-315 T5). The Excel upload
     * enforces the same rule per row ({@code AssetRequestParser.setMaturityDate}); this is its JSON
     * twin, at the one point both trade paths pass (D-386).
     *
     * <p>A land or building is refused outright, on the same twin footing ({@link AssetType#NOT_A_BROKER_TRADE};
     * W-088g5, D-433): the broker path cannot carry its purchase expenses into its cost, and its sale is entered through
     * {@link PropertySaleService} (W-088g9).
     *
     * <p>Runs in {@code processTransaction} and {@code processTransactionV2}, so the upload and redrive are checked as the
     * JSON routes are (W-277, PS-1).
     */
    private static void validateAssetRequest(UserMail userMail, AssetRequest assetRequest) {
        String requestEmail = assetRequest.getEmail();
        if (requestEmail != null && !requestEmail.isBlank() && !userMail.getEmail().equals(requestEmail)) {
            throw new BadRequestException("Email does not match");
        }

        Double quantity = assetRequest.getQuantity();
        if (quantity == null || quantity <= 0) {
            throw new BadRequestException("Quantity must be greater than zero, but was: " + quantity);
        }

        if (assetRequest.getPrice() == null) {
            throw new BadRequestException("Price is required; it may be 0 for a free allotment");
        }
        if (assetRequest.getPrice() < 0) {
            throw new BadRequestException("Price must not be negative, but was: " + assetRequest.getPrice());
        }

        LocalDate transactionDate = assetRequest.getTransactionDate();
        if (transactionDate != null && transactionDate.isAfter(LocalDate.now())) {
            throw new BadRequestException(
                    "A trade cannot be dated in the future: " + transactionDate + ". It would be priced "
                            + "against whichever rate card is open-ended today rather than the one in force "
                            + "when it settles");
        }

        if (assetRequest.getAssetType() == AssetType.GOLD_BOND && assetRequest.getMaturityDate() == null) {
            throw new BadRequestException(
                    "A GOLD_BOND needs its " + ExcelHeaders.MATURITY_DATE + "; it is not assumed");
        }

        if (assetRequest.getAssetType() == AssetType.LAND_OR_BUILDING) {
            throw new BadRequestException(AssetType.NOT_A_BROKER_TRADE);
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

        AssetRequestParser assetRequestParser = new AssetRequestParser(quarter);
        List<AssetRequest> assetRequests = assetRequestParser.parse(file);

        String fileDigest = uploadDigest(userMail, quarter, file);
        List<String> filteredOutTransactions = new ArrayList<>();
        int repeated = 0;
        for (int row = 0; row < assetRequests.size(); row++) {
            AssetRequest assetRequest = assetRequests.get(row);
            // The row's place in this file is its identity: two identical rows are two trades, and the same file sent
            // again replays every row instead of recording it twice (W-275). Unique across users, as the index is.
            assetRequest.setIdempotencyKey("upload:" + fileDigest + ":" + (row + 1));
            if (REPLAYED_MESSAGE.equals(uploadTrade(userMail, assetRequest, filteredOutTransactions, row + 1))) {
                repeated++;
            }
        }

        int recorded = assetRequests.size() - repeated - filteredOutTransactions.size();
        String answer = "From " + file.getOriginalFilename() + ", quarter " + quarter + ": " + recorded + " recorded, "
                + repeated + " already recorded by an earlier send of this file";
        if (!filteredOutTransactions.isEmpty()) {
            answer += "; held back for a corporate action: " + filteredOutTransactions;
        }
        return answer;
    }

    /**
     * One upload row through V1's processing; a refusal names the trade, numbered by its place among the sheet's
     * trades — the upload does not know the sheet's row (W-277 C2a).
     */
    private String uploadTrade(UserMail userMail, AssetRequest assetRequest, List<String> filteredOutTransactions, int trade) {
        try {
            return processTransaction(userMail, assetRequest, filteredOutTransactions);
        } catch (IllegalArgumentException refused) {
            throw new BadRequestException("Trade " + trade + ": " + refused.getMessage());
        }
    }

    /** The user, the quarter and the file's bytes, digested: the same file sent again by the same user digests the same. */
    private static String uploadDigest(UserMail userMail, String quarter, MultipartFile file) {
        try {
            return TradeFingerprint.sha256(String.join("\u001f", userMail.getEmail(), quarter,
                    Base64.getEncoder().encodeToString(file.getBytes())));
        } catch (IOException unreadable) {
            throw new UncheckedIOException("The uploaded file could not be read a second time", unreadable);
        }
    }

    /**
     * Reads all TEMPORARY transactions for a user and re-processes them, each through the flow that received it.
     * A trade the rules refuse fails its own item and the rest go on; any other failure fails the redrive, which rolls
     * back as one transaction (W-281 C2a).
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
            // Checked here as well as inside processing on purpose: here a still-blocked trade is left as it is ("Still
            // filtered"); inside, it would be stored again as a new temporary ("Re-filtered") — W-281 finding 3.
            if (temporaryTransactionService.filterOutTransaction(userMail, assetRequest, true)) {
                log.info("Transaction {} still blocked by corporate action, skipping", tempTxn.getId());
                stillFiltered.add(tempTxn.getId());
                continue;
            }

            try {
                log.info("Redriving transaction: {}", tempTxn.getId());
                List<String> itemFiltered = new ArrayList<>();
                assetRequest.setTempTransactionId(tempTxn.getId());
                redriveThrough(tempTxn.getTradeFlow(), userMail, assetRequest, itemFiltered);

                if (!itemFiltered.isEmpty()) {
                    // Transaction was re-filtered during redrive; a new temp record was created.
                    // Do not update the original — it is still the source of truth.
                    filteredOut.add(tempTxn.getId());
                } else {
                    succeeded.add(tempTxn.getId());
                }
            } catch (IllegalArgumentException refused) {
                // A trade the rules refuse is this item's failure. Anything else — the database, a collision — is not
                // the item's: it propagates, the redrive's transaction rolls back, and TradeCollisionRetry retries a
                // collision (W-281 C2a).
                log.warn("Redrive of temporary transaction {} refused: {}", tempTxn.getId(), refused.getMessage());
                failed.put(tempTxn.getId(), refused.getMessage());
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

    /** A temporary stored before W-270 records no flow and is redriven through V1, as it always was (D-555 C2a). */
    private void redriveThrough(TradeFlow tradeFlow, UserMail userMail, AssetRequest assetRequest, List<String> itemFiltered) {
        if (tradeFlow == TradeFlow.V2) {
            processTransactionV2(userMail, assetRequest, itemFiltered);
        } else {
            processTransaction(userMail, assetRequest, itemFiltered);
        }
    }

    private static String buildRedriveMessage(List<String> succeeded, Map<String, String> failed, List<String> stillFiltered, List<String> filteredOut) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("Succeeded", succeeded.size());
        counts.put("Failed", failed.size());
        counts.put("Still filtered", stillFiltered.size());
        counts.put("Re-filtered", filteredOut.size());
        String outcomes = counts.entrySet().stream()
                .filter(count -> count.getValue() > 0)
                .map(count -> count.getKey() + ": " + count.getValue())
                .collect(Collectors.joining(", "));
        return outcomes.isEmpty() ? "No temporary transactions to redrive" : "Redrive complete. " + outcomes;
    }

    private void buyStock(UserMail userMail, String transactionId, AssetRequest assetRequest) {

        String email = userMail.getEmail();
        assetRequest.setEmail(email);
        String stockCode = assetRequest.getStockCode();
        BrokerName brokerName = assetRequest.getBrokerName();
        String accountHolder = assetRequest.getAccountHolder();
        LocalDate transactionDate = assetRequest.getTransactionDate();

        Optional<AssetEntity> optionalStock = portfolioRepository.findFirstByEmailAndStockCodeAndBrokerNameAndAccountHolderAndTransactionDateOrderByIdAsc(email, stockCode, brokerName, accountHolder, transactionDate);
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

            assetEntity.getOrderTimeQuantities().add(orderTimeQuantity);
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

    /**
     * The V2 buy. Deliberately no longer shares {@link #updateBrokerChargesAndProfitAndLoss} with
     * V1 {@code buyStock}: the charge is computed <b>before</b> the lot is written so the computed
     * total can become the cost basis, and V1 is unused and left exactly as it was.
     */
    public void buyStockV2(UserMail userMail, String transactionId, AssetRequest assetRequest) {

        String email = userMail.getEmail();
        assetRequest.setEmail(email);
        AssetEntity assetEntity = assetRequest.asAsset();

        // Priced before the lot is written. The other order works right up until the computed total
        // becomes the cost basis, at which point the saved lot carries the figure the user typed.
        var profitLossContext = buyContext(transactionId, assetRequest);
        Optional<ChargeComputation> computation = chargeRecordingGateway.record(userMail, profitLossContext);
        applyComputedCostBasis(assetEntity, computation);

        // Handed on rather than recomputed, so the engine runs once per trade.
        profitAndLossService.updateProfitAndLoss(userMail, profitLossContext, computation);

        assetEntity.getBuyTransactionIds().add(transactionId);
        portfolioRepository.save(assetEntity);
    }

    /**
     * Replaces the user-entered charge with the engine's, once {@code app.charges.authoritative} is
     * on (AC-10).
     *
     * <p>An absent computation, or one with no figure ({@link ChargeComputation#hasFigure}, W-223), leaves
     * the entered figure alone rather than zeroing it. The engine declines for reasons that say nothing about whether the trade cost anything — no rate card for
     * the period, the kill switch, a scheme with no profile — and overwriting a real cost with zero
     * because we could not price it would be worse than keeping an estimate.
     *
     * <p>{@code brokerCharges} stays on {@code AssetRequest} deliberately: existing clients keep
     * sending it and keep working, it simply stops being read. Removing it is a later release.
     */
    private void applyComputedCostBasis(AssetEntity assetEntity, Optional<ChargeComputation> computation) {
        if (!chargeEngineProperties.authoritative()) {
            return;
        }
        computation.filter(ChargeComputation::hasFigure)
                .ifPresent(computed -> assetEntity.setBrokerCharges(computed.total()));
    }

    private static ProfitLossContext buyContext(String transactionId, AssetRequest assetRequest) {
        return new ProfitLossContext(transactionId, assetRequest.getQuantity(), assetRequest.getTransactionDate(),
                assetRequest.getPrice(), assetRequest.getStockCode(), assetRequest.getBrokerName(),
                assetRequest.getExchangeName(), assetRequest.getAssetType(), TransactionType.BUY, null,
                assetRequest.getAccountType(), assetRequest.getAccountHolder(), Collections.emptyList(),
                assetRequest.getSegment());
    }

    public void sellStockV2(UserMail userMail, String transactionId, AssetRequest assetRequest) {

        String email = userMail.getEmail();
        String stockCode = assetRequest.getStockCode();
        BrokerName brokerName = assetRequest.getBrokerName();
        String accountHolder = assetRequest.getAccountHolder();
        LocalDate transactionDate = assetRequest.getTransactionDate();

        List<AssetEntity> stockEntities = portfolioRepository.findEligibleHoldingsForSell(email, stockCode, brokerName, accountHolder, transactionDate);
        validateTransaction(stockEntities, assetRequest);
        allocateSaleV2(userMail, transactionId, stockEntities, assetRequest);

        List<String> updatedStockEntities = TCollectionUtil.applyMap(stockEntities, PortfolioService::hasNothingLeft, AssetEntity::getId);
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
        LocalDate transactionDate = assetRequest.getTransactionDate();

        // Only lots held on the sale's date, as V2 reads them: a sale cannot take shares bought after it (W-271).
        List<AssetEntity> stockEntities = portfolioRepository.findEligibleHoldingsForSell(email, stockCode, brokerName, accountHolder, transactionDate);

        validateTransaction(stockEntities, assetRequest);

        allocateSale(userMail, transactionId, stockEntities, assetRequest);

        List<String> updatedStockEntities = TCollectionUtil.applyMap(stockEntities, PortfolioService::hasNothingLeft, AssetEntity::getId);
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

        double requested = assetRequest.getQuantity();
        if (existingQuantity < requested && !DoubleUtil.equal(existingQuantity, requested)) {
            throw new IllegalArgumentException("Not enough stocks to sell for: " + assetRequest.getStockCode());
        }
    }

    private static double getTotalValue(AssetRequest assetRequest) {
        return assetRequest.getPrice() * assetRequest.getQuantity();
    }

    @SuppressWarnings({"removal"})
    private void allocateSale(UserMail userMail, String transactionId, List<AssetEntity> stockEntities, AssetRequest assetRequest) {

        double sellQuantity = assetRequest.getQuantity();
        Iterator<AssetEntity> stockEntitiesIterator = stockEntities.iterator();
        while (isLeftToSell(sellQuantity)) {

            AssetEntity assetEntity = stockEntitiesIterator.next();
            assetEntity.getSellTransactionIds().add(transactionId);
            Double assetQuantity = assetEntity.getQuantity();

            TradeOutcomeContext tradeOutcomeContext;
            ProfitAndLossContext profitAndLossContext;
            boolean wholeLot = takesWholeLot(sellQuantity, assetQuantity);
            double sellQty = wholeLot ? assetQuantity : sellQuantity;

            // Deducted ONCE per sell and handed to both records (B-8). The trade outcome and the
            // profit-and-loss entry describe the same allocation, so if each asked for its own the
            // lot would be charged twice. Before this the full charge was divided by the quantity
            // still remaining, with nothing tracking what had gone: a 3-unit lot carrying Rs.10
            // sold singly deducted 3.33, then 5.00, then 10.00 -- Rs.18.33 against Rs.10 paid,
            // inflating the cost base and understating the gain.
            double buyBrokerCharges = LotChargeAllocator.deductBroker(assetEntity, sellQty, assetQuantity);
            double buyMiscCharges = LotChargeAllocator.deductMisc(assetEntity, sellQty, assetQuantity);

            if (wholeLot) {
                tradeOutcomeContext = toTradeOutcomeContext(userMail.getEmail(), assetEntity, assetRequest, assetQuantity, transactionId, buyBrokerCharges, buyMiscCharges);
                profitAndLossContext = ProfitAndLossContext.from(assetEntity, assetRequest, assetQuantity, buyBrokerCharges, buyMiscCharges);

                assetEntity.setQuantity(0D);
                sellQuantity = sellQuantity - assetQuantity;
            } else {
                tradeOutcomeContext = toTradeOutcomeContext(userMail.getEmail(), assetEntity, assetRequest, sellQuantity, transactionId, buyBrokerCharges, buyMiscCharges);
                profitAndLossContext = ProfitAndLossContext.from(assetEntity, assetRequest, sellQuantity, buyBrokerCharges, buyMiscCharges);

                double remainingQuantity = assetQuantity - sellQuantity;
                assetEntity.setQuantity(remainingQuantity);
                sellQuantity = 0;
            }

            tradeOutcomeService.saveTradeOutcome(userMail, tradeOutcomeContext);
            profitAndLossService.updateProfitAndLoss(userMail, profitAndLossContext);
        }
    }

    private void updateBrokerChargesAndProfitAndLoss(UserMail userMail, String transactionId, AssetRequest assetRequest) {
        var profitLossContext = new ProfitLossContext(transactionId, assetRequest.getQuantity(), assetRequest.getTransactionDate(), assetRequest.getPrice(),
                assetRequest.getStockCode(), assetRequest.getBrokerName(), assetRequest.getExchangeName(), assetRequest.getAssetType(), TransactionType.BUY,
                null, assetRequest.getAccountType(), assetRequest.getAccountHolder(), Collections.emptyList(),
                assetRequest.getSegment());
        profitAndLossService.updateProfitAndLoss(userMail, profitLossContext);
    }

    public void allocateSaleV2(UserMail userMail, String transactionId, List<AssetEntity> stockEntities, AssetRequest assetRequest) {

        double sellQuantity = assetRequest.getQuantity();
        Iterator<AssetEntity> stockEntitiesIterator = stockEntities.iterator();
        List<BuyContext> buyContexts = new ArrayList<>();
        List<TradeOutcomeRecorder.MatchedLot> matchedLots = new ArrayList<>();
        while (isLeftToSell(sellQuantity)) {
            AssetEntity assetEntity = stockEntitiesIterator.next();
            assetEntity.getSellTransactionIds().add(transactionId);
            Double assetQuantity = assetEntity.getQuantity();

            if (takesWholeLot(sellQuantity, assetQuantity)) {
                buyContexts.add(new BuyContext(assetEntity.getQuantity(), assetEntity.getTransactionDate(), assetEntity.getPrice(),
                        Optional.ofNullable(assetEntity.getAssetType())));
                matchedLots.add(new TradeOutcomeRecorder.MatchedLot(assetEntity, assetQuantity, assetQuantity));

                assetEntity.setQuantity(0D);
                sellQuantity = sellQuantity - assetQuantity;
            } else {
                buyContexts.add(new BuyContext(sellQuantity, assetEntity.getTransactionDate(), assetEntity.getPrice(),
                        Optional.ofNullable(assetEntity.getAssetType())));
                matchedLots.add(new TradeOutcomeRecorder.MatchedLot(assetEntity, sellQuantity, assetQuantity));

                double remainingQuantity = assetQuantity - sellQuantity;
                assetEntity.setQuantity(remainingQuantity);
                sellQuantity = 0;
            }
        }
        // V2 sell. Priced here and handed on, so the engine runs once and the computed charge
        // reaches the period's summary. V1 sellStock takes the deprecated ProfitAndLossContext
        // overload and is not affected by any of this.
        var profitLossContext = toProfitLossContext(assetRequest, buyContexts, transactionId);
        Optional<ChargeComputation> computation = chargeRecordingGateway.record(userMail, profitLossContext);
        // The itemised capital-gains rows. Until now only the V1 sell wrote these, so a V2 user had
        // aggregate totals and nothing to file from. Deliberately after the pricing call, because
        // the sell charge has to exist before it can be pro-rated across the lots it was matched to;
        // and before the P&L, which folds these rows' charges and net profit rather than computing
        // them a second time (W-169).
        List<TradeOutcomeContext> rows =
                tradeOutcomeRecorder.record(userMail, assetRequest, transactionId, matchedLots, computation);
        profitAndLossService.recordSale(userMail, profitLossContext, computation, rows);
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
                assetRequest.getAssetType(), TransactionType.SELL, null, accountType, accountHolder, buyContexts,
                assetRequest.getSegment());
    }

    private TradeOutcomeContext toTradeOutcomeContext(String email, AssetEntity assetEntity, AssetRequest assetRequest,
                                                       double sellQuantity, String transactionId,
                                                       double buyBrokerCharges, double buyMiscCharges) {

        LocalDate buyDate = assetEntity.getTransactionDate();
        LocalDate sellDate = assetRequest.getTransactionDate();
        TradeFigures figures = v1Figures(assetEntity, assetRequest, sellQuantity, buyBrokerCharges, buyMiscCharges);

        TradeOutcomeContext.TradeOutcomeContextBuilder row = TradeOutcomeContext.builder()
                .email(email)
                .stockCode(assetEntity.getStockCode())
                .stockName(assetEntity.getStockName())
                .exchangeName(assetEntity.getExchangeName())
                .brokerName(assetEntity.getBrokerName())
                .assetType(assetEntity.getAssetType())
                .accountType(assetRequest.getAccountType())
                .accountHolder(assetRequest.getAccountHolder())
                .segment(assetRequest.getSegment());
        v1BuySide(row, assetEntity, sellQuantity, figures);
        return row
                .sellPrice(assetRequest.getPrice())
                .sellQuantity(sellQuantity)
                .sellDate(sellDate)
                .sellBrokerCharges(figures.sellBrokerCharges())
                .sellMiscCharges(figures.sellMiscCharges())
                .totalBuyValue(figures.totalBuyValue())
                .totalSellValue(figures.totalSellValue())
                .netProfit(figures.netProfit())
                .profitPercentage(figures.profitPercentage())
                .holdingPeriodDays(ChronoUnit.DAYS.between(buyDate, sellDate))
                .financialYear(deriveFinancialYear(sellDate))
                .sourceSellTransactionId(transactionId)
                .sellExchangeName(assetRequest.getExchangeName())
                .sellSegment(assetRequest.getSegment())
                .build();
    }

    /** V1's lot side of the row: the lot's price, date, charges and how it was acquired. */
    private static void v1BuySide(TradeOutcomeContext.TradeOutcomeContextBuilder row, AssetEntity assetEntity,
                                  double sellQuantity, TradeFigures figures) {
        // The original is the adjusted price until V1 reads its source transaction
        row.originalBuyPrice(assetEntity.getPrice())
                .corporateActionAdjustedBuyPrice(assetEntity.getPrice())
                .buyQuantity(sellQuantity)
                .buyDate(assetEntity.getTransactionDate())
                .buyBrokerCharges(figures.buyBrokerCharges())
                .buyMiscCharges(figures.buyMiscCharges())
                .sourceBuyLotId(assetEntity.getId())
                .corporateActionDerived(isCorporateActionDerived(assetEntity))
                .appliedCorporateActions(assetEntity.getCorporateActions())
                .acquisitionMode(Acquisition.of(assetEntity.getCorporateActionType()).mode())
                .acquisitionCorporateAction(assetEntity.getCorporateActionType())
                .buyExchangeName(assetEntity.getExchangeName())
                .buySegment(assetEntity.getSegment());
    }

    /** V1's figures: the buy charges as handed in, the sell charges pro-rated to the quantity sold. */
    private static TradeFigures v1Figures(AssetEntity assetEntity, AssetRequest assetRequest, double sellQuantity,
                                          double buyBrokerCharges, double buyMiscCharges) {
        // Pro-rate sell-side charges based on sell quantity
        double sellReqQuantity = assetRequest.getQuantity() != null ? assetRequest.getQuantity() : 1.0;
        double sellBrokerCharges = (assetRequest.getBrokerCharges() / sellReqQuantity) * sellQuantity;
        double sellMiscCharges = (assetRequest.getMiscCharges() / sellReqQuantity) * sellQuantity;

        double corporateActionAdjustedBuyPrice = assetEntity.getPrice();
        double sellPrice = assetRequest.getPrice();
        double totalBuyValue = (corporateActionAdjustedBuyPrice * sellQuantity) + buyBrokerCharges + buyMiscCharges;
        double totalSellValue = (sellPrice * sellQuantity) - sellBrokerCharges - sellMiscCharges;
        double netProfit = totalSellValue - totalBuyValue;
        double profitPercentage = totalBuyValue > 0 ? (netProfit / totalBuyValue) * 100 : 0.0;
        return new TradeFigures(buyBrokerCharges, buyMiscCharges, sellBrokerCharges, sellMiscCharges,
                totalBuyValue, totalSellValue, netProfit, profitPercentage);
    }

    /** A lot carrying corporate actions, or a zero-price lot a corporate action created (a bonus). */
    private static boolean isCorporateActionDerived(AssetEntity assetEntity) {
        return (assetEntity.getCorporateActions() != null && !assetEntity.getCorporateActions().isEmpty())
                || (assetEntity.getPrice() == 0 && assetEntity.getCorporateActionType() != null);
    }

    /**
     * V1's financial year. The body is a delegation and nothing else — the name, the signature and
     * every call site are unchanged, because this sits on the live V1 sell path.
     *
     * <p>It used to compare {@code isBefore(March 31)}, which filed a trade made <em>on</em> 31
     * March into the following year. That was wrong here, in {@code ProfitAndLossService} and in
     * the trade-outcome recorder identically; all three now share one derivation so they cannot
     * drift apart again.
     */
    private static String deriveFinancialYear(LocalDate transactionDate) {
        return FinancialYear.containing(transactionDate).label();
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
     * Quantities on the sell path are compared within {@link DoubleUtil}'s tolerance, never exactly: fractional units
     * summed as doubles drift (0.1 + 0.7 is 0.7999999999999999), which refused a full sale and left 1e-17-unit lots
     * behind (W-274).
     */
    static boolean takesWholeLot(double sellQuantity, double lotQuantity) {
        return sellQuantity >= lotQuantity || DoubleUtil.equal(sellQuantity, lotQuantity);
    }

    static boolean isLeftToSell(double sellQuantity) {
        return sellQuantity > 0 && !DoubleUtil.equal(sellQuantity, 0);
    }

    static boolean hasNothingLeft(AssetEntity lot) {
        return DoubleUtil.equal(0, lot.getQuantity());
    }

}
