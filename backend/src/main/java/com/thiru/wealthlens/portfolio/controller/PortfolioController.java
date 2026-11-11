package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.dto.ErasureReport;
import com.thiru.wealthlens.portfolio.dto.ProfitAndLossResponse;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.service.PortfolioQueryService;
import com.thiru.wealthlens.portfolio.service.TradeCollisionRetry;
import com.thiru.wealthlens.portfolio.service.TransactionService;
import com.thiru.wealthlens.portfolio.service.UserRecordsErasureService;
import com.thiru.wealthlens.shared.dto.BulkGetRequest;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@AllArgsConstructor
@RequestMapping("/portfolio/user/{email}")
@RestController()
public class PortfolioController {

    private final PortfolioQueryService portfolioQueryService;
    private final UserRecordsErasureService userRecordsErasureService;
    private final TransactionService transactionService;
    private final TradeCollisionRetry tradeCollisionRetry;

    @PostMapping("/transaction")
    public String addTransaction(@PathVariable String email, @RequestBody AssetRequest assetRequest) {
        return tradeCollisionRetry.addTransaction(UserMail.from(email), assetRequest);
    }

    @PostMapping("/transaction/v2")
    public ResponseEntity<String> addTransactionV2(@PathVariable String email, @RequestBody AssetRequest assetRequest) {
        return ResponseEntity.ok(tradeCollisionRetry.addTransactionV2(UserMail.from(email), assetRequest));
    }

    @PostMapping("/upload-transactions")
    public String uploadTransactions(@PathVariable String email, @RequestParam("quarter") String quarter,
                                                     @RequestParam("file") MultipartFile file) {
        return tradeCollisionRetry.uploadTransactions(UserMail.from(email), quarter, file);
    }

    @GetMapping("/profit-and-loss")
    public ProfitAndLossResponse getProfitAndLoss(@PathVariable String email,
                                                                  @RequestParam String financialYear) {
        return portfolioQueryService.getProfitAndLoss(UserMail.from(email), financialYear);
    }

    /**
     * Erases every record this user owns.
     *
     * <p>Answers which collections could not be cleared when any could not. It used to answer
     * "deleted successfully" whatever happened, because the service caught each failure and logged
     * it — telling a user their data was gone while it was still there.
     */
    @PostMapping("/clear/all")
    public String deleteAllRecords(@PathVariable String email) {
        ErasureReport report = userRecordsErasureService.erase(UserMail.from(email));
        if (report.isComplete()) {
            return "User: " + email + ", records and transactions deleted successfully";
        }
        return "User: " + email + ", records partially deleted. Could not clear: "
                + String.join(", ", report.getFailed());
    }

    @GetMapping("/stocks/all")
    public List<AssetResponse> getAllStocks(@PathVariable String email) {
        return portfolioQueryService.getAllStocks(UserMail.from(email));
    }

    @GetMapping("/mfs")
    public List<AssetResponse> getAllMutualFunds(@PathVariable String email) {
        return portfolioQueryService.getMutualFunds(UserMail.from(email));
    }

    @PostMapping("/stocks")
    public List<AssetResponse> getStocks(@PathVariable String email,
                                                         @RequestBody BulkGetRequest bulkGetRequest) {
        return portfolioQueryService.getStocksWithDateRange(UserMail.from(email), bulkGetRequest.getDateRange());
    }

    @GetMapping("/stock/{stockCode}/all")
    public List<AssetResponse> getAllStocks(@PathVariable String email,
                                                            @PathVariable String stockCode) {
        return portfolioQueryService.getStockQuantity(UserMail.from(email), stockCode);
    }

    @GetMapping("/all/transactions")
    public List<TransactionEntity> allTransactions(@PathVariable String email) {
        return transactionService.getUserTransactions(UserMail.from(email));
    }

    // Note: Below this comment is for testing purpose only

    @PostMapping("/request")
    public AssetRequest testRequest(@RequestBody AssetRequest assetRequest) {

        if (assetRequest.getTransactionDate() == null) {
            assetRequest.setTransactionDate(LocalDate.now());
        }
        return assetRequest;
    }

    @PostMapping("/request2")
    public List<AssetEntity> testRequest(@PathVariable String email,
                                                         @RequestBody BulkGetRequest bulkGetRequest) {
        return portfolioQueryService.searchAssets(UserMail.from(email), bulkGetRequest.getQueryFilters());
    }

}
