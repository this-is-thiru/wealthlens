package com.thiru.wealthlens.portfolio.controller;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.dto.ProfitAndLossResponse;
import com.thiru.wealthlens.portfolio.dto.enums.HoldingType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.service.PortfolioService;
import com.thiru.wealthlens.portfolio.service.TemporaryService;
import com.thiru.wealthlens.portfolio.service.TransactionService;
import com.thiru.wealthlens.shared.dto.BulkGetRequest;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

    private final PortfolioService portfolioService;
    private final TransactionService transactionService;
    private final TemporaryService temporaryService;

    @PostMapping("/transaction")
    public String addTransaction(@PathVariable String email, @RequestBody AssetRequest assetRequest) {
        return portfolioService.addTransaction(UserMail.from(email), assetRequest, new ArrayList<>());
    }

    @PostMapping("/transaction/v2")
    public ResponseEntity<String> addTransactionV2(@PathVariable String email, @RequestBody AssetRequest assetRequest) {
        return ResponseEntity.ok(portfolioService.addTransactionV2(UserMail.from(email), assetRequest, new ArrayList<>()));
    }

    @PostMapping("/upload-transactions")
    public String uploadTransactions(@PathVariable String email, @RequestParam("quarter") String quarter,
                                                     @RequestParam("file") MultipartFile file) {
        return portfolioService.uploadTransactions(UserMail.from(email), quarter, file);
    }

    // @PostMapping("/insurance")
    // public String> addInsurance(@PathVariable String email,
    // @RequestBody AssetRequest assetRequest) {
    // return
    // portfolioService.addTransaction(UserMail.from(email),
    // assetRequest));
    // }

    @GetMapping("/profit-and-loss")
    public ProfitAndLossResponse getProfitAndLoss(@PathVariable String email,
                                                                  @RequestParam String financialYear) {
        return portfolioService.getProfitAndLoss(UserMail.from(email), financialYear);
    }

    @PostMapping("/clear/all")
    public String deleteAllRecords(@PathVariable String email) {
        return portfolioService.clearAllRecordsForCustomer(UserMail.from(email));
    }

    @GetMapping("/stocks/all")
    public List<AssetResponse> getAllStocks(@PathVariable String email) {
        return portfolioService.getAllStocks(UserMail.from(email));
    }

    @GetMapping("/mfs")
    public List<AssetResponse> getAllMutualFunds(@PathVariable String email) {
        return portfolioService.getMutualFunds(UserMail.from(email));
    }

    @PostMapping("/stocks")
    public List<AssetResponse> getStocks(@PathVariable String email,
                                                         @RequestBody BulkGetRequest bulkGetRequest) {

        LocalDate startDate = bulkGetRequest.getDateRange().getStartDate();
        LocalDate endDate = bulkGetRequest.getDateRange().getEndDate();
        return portfolioService.getStocksWithDateRange(UserMail.from(email), startDate, endDate);
    }

    @GetMapping("/stock/{stockCode}/all")
    public List<AssetResponse> getAllStocks(@PathVariable String email,
                                                            @PathVariable String stockCode) {
        return portfolioService.getStockQuantity(UserMail.from(email), stockCode);
    }

    @GetMapping("/all/transactions")
    public List<TransactionEntity> allTransactions(@PathVariable String email) {

        UserMail.from(email);
        return transactionService.allTransactions();
    }

    @GetMapping("/assets/holding/{holdingType}")
    public List<AssetResponse> allAssets(@PathVariable String email, @PathVariable String holdingType) {
        return portfolioService.getAssets(UserMail.from(email), HoldingType.valueOf(holdingType));
    }

    @GetMapping("/assets/holding/{holdingType}/excel")
    public ResponseEntity<InputStreamResource> downloadAssets(@PathVariable String email, @PathVariable String holdingType) {
        Pair<InputStreamResource, String> resourcePair = portfolioService.downloadTermAssets(UserMail.from(email), HoldingType.valueOf(holdingType));
        String mediaType = "application/vnd.ms-excel";
        String headerValue = "attachment; filename=" + resourcePair.getSecond();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                .contentType(MediaType.parseMediaType(mediaType)).body(resourcePair.getFirst());
    }

//    @GetMapping("/stocks/download")
//    public InputStreamResource> downloadPortfolioStocks1(@PathVariable String email) {
//
//        Pair<InputStreamResource, String> resourcePair = portfolioService.downloadPortfolioStocks(UserMail.from(email));
//        FileStream fileStream = FileStream.from(resourcePair.getSecond(), resourcePair.getFirst(), FileType.XLSX);
//        return FileHelper.sendFileAsAttachment(fileStream);
//    }

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
        return portfolioService.searchAssets(UserMail.from(email), bulkGetRequest.getQueryFilters());
    }

//    @GetMapping("/assets/purchase/before/{stockCode}/{date}")
//    public List<Asset>> testRequest(@PathVariable String email, @PathVariable String stockCode, @PathVariable String date) {
//        List<Asset> assets = portfolioService.testMethod(UserMail.from(email), stockCode, TLocaleDate.convertToDate(date));
//        return assets);
//    }

//    @GetMapping("/assets/purchase/before/{stockCode}/{date}")
//    public List<Transaction>> testRequest1(@PathVariable String email, @PathVariable String stockCode, @PathVariable String date) {
//        List<Transaction> assets = transactionService.testMethod(UserMail.from(email), stockCode, TLocaleDate.convertToDate(date));
//        return assets);
//    }

}
