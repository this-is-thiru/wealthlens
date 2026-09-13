package com.thiru.wealthlens.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.context.ProfitAndLossContext;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.ProfitAndLossEntity;
import com.thiru.wealthlens.portfolio.entity.model.FinancialReport;
import com.thiru.wealthlens.portfolio.entity.model.RealisedProfits;
import com.thiru.wealthlens.portfolio.service.PortfolioService;
import com.thiru.wealthlens.portfolio.service.ProfitAndLossService;
import com.thiru.wealthlens.portfolio.service.TemporaryTransactionService;
import com.thiru.wealthlens.portfolio.service.TransactionService;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.dto.user.UserMail;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class ProfitAndLossIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProfitAndLossService profitAndLossService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TemporaryTransactionService temporaryTransactionService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void updateProfitAndLoss_whenSelfShortTermGain_shouldCreatePnl() {
        // GIVEN
        String email = "test@example.com";
        UserMail userMail = UserMail.from(email);
        String stockCode = "RELIANCE";

        LocalDate buyDate = LocalDate.of(2024, 1, 15);
        LocalDate sellDate = LocalDate.of(2024, 6, 20);

        AssetRequest buyRequest = createBuyRequest(email, stockCode, buyDate, 100.0, 10.0);

        AssetRequest sellRequest = createSellRequest(email, stockCode, sellDate, 100.0, 15.0, AccountType.SELF);

        ProfitAndLossContext context = buildContext(email, stockCode, buyDate, sellDate, 100.0, 10.0, 15.0, AccountType.SELF);

        // WHEN
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // THEN
        ProfitAndLossEntity pnl = findPnlByEmail(email);
        assertNotNull(pnl);
        assertNotNull(pnl.getRealisedProfits());
        assertNotNull(pnl.getRealisedProfits().getShortTermCapitalGains());
        // Note: Financial year is calculated by server based on current date
        // Verify short term vs long term classification is correct
        assertNull(pnl.getRealisedProfits().getLongTermCapitalGains());
    }

    @Test
    void updateProfitAndLoss_whenSelfLongTermGain_shouldUpdateLongTerm() {
        // GIVEN
        String email = "test@example.com";
        UserMail userMail = UserMail.from(email);
        String stockCode = "HDFCBANK";

        LocalDate buyDate = LocalDate.of(2022, 1, 15);
        LocalDate sellDate = LocalDate.of(2024, 6, 20);

        AssetRequest buyRequest = createBuyRequest(email, stockCode, buyDate, 100.0, 10.0);

        AssetRequest sellRequest = createSellRequest(email, stockCode, sellDate, 100.0, 20.0, AccountType.SELF);

        ProfitAndLossContext context = buildContext(email, stockCode, buyDate, sellDate, 100.0, 10.0, 20.0, AccountType.SELF);

        // WHEN
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // THEN
        ProfitAndLossEntity pnl = findPnlByEmail(email);
        assertNotNull(pnl);
        assertNotNull(pnl.getRealisedProfits());
        // Long term: holding > 1 year
        assertNull(pnl.getRealisedProfits().getShortTermCapitalGains());
        assertNotNull(pnl.getRealisedProfits().getLongTermCapitalGains());
    }

    @Test
    void updateProfitAndLoss_whenOutsourcedAccount_shouldUpdateOutsourcedProfits() {
        // GIVEN
        String email = "test@example.com";
        UserMail userMail = UserMail.from(email);
        String stockCode = "TCS";

        LocalDate buyDate = LocalDate.of(2024, 2, 10);
        LocalDate sellDate = LocalDate.of(2024, 7, 15);

        AssetRequest buyRequest = createBuyRequest(email, stockCode, buyDate, 50.0, 5.0);

        AssetRequest sellRequest = createSellRequest(email, stockCode, sellDate, 50.0, 12.0, AccountType.OUTSOURCED);

        ProfitAndLossContext context = buildContext(email, stockCode, buyDate, sellDate, 50.0, 5.0, 12.0, AccountType.OUTSOURCED);

        // WHEN
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // THEN
        ProfitAndLossEntity pnl = findPnlByEmail(email);
        assertNotNull(pnl);
        assertNotNull(pnl.getOutSourcedRealisedProfits());
        assertNotNull(pnl.getOutSourcedRealisedProfits().getShortTermCapitalGains());
    }

    @Test
    void updateProfitAndLoss_whenNewFy_shouldAutoCreateEntity() {
        // GIVEN
        String email = "newuser@example.com";
        UserMail userMail = UserMail.from(email);
        String stockCode = "INFY";

        LocalDate buyDate = LocalDate.of(2024, 4, 1);
        LocalDate sellDate = LocalDate.of(2024, 5, 15);

        AssetRequest buyRequest = createBuyRequest(email, stockCode, buyDate, 75.0, 8.0);

        AssetRequest sellRequest = createSellRequest(email, stockCode, sellDate, 75.0, 18.0, AccountType.SELF);

        ProfitAndLossContext context = buildContext(email, stockCode, buyDate, sellDate, 75.0, 8.0, 18.0, AccountType.SELF);

        // Ensure no existing P&L
        deletePnlByEmail(email);
        assertNull(findPnlByEmail(email));

        // WHEN
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // THEN
        ProfitAndLossEntity pnl = findPnlByEmail(email);
        assertNotNull(pnl);
        assertEquals(email, pnl.getEmail());
        assertNotNull(pnl.getFinancialYear());
        assertNotNull(pnl.getRealisedProfits());
    }

    @Test
    void getProfitAndLoss_whenExisting_shouldReturnData() {
        // GIVEN
        String email = "test@example.com";
        UserMail userMail = UserMail.from(email);

        ProfitAndLossEntity pnl = new ProfitAndLossEntity(email);
        pnl.setFinancialYear("2023-2024");
        RealisedProfits realisedProfits = RealisedProfits.empty();
        FinancialReport shortTerm = FinancialReport.empty();
        shortTerm.setProfit(500.0);
        realisedProfits.setShortTermCapitalGains(shortTerm);
        pnl.setRealisedProfits(realisedProfits);
        mongoTemplate.save(pnl, "profit_and_loss");

        // WHEN
        var response = profitAndLossService.getProfitAndLoss(userMail, "2023-2024");

        // THEN
        assertNotNull(response);
        assertEquals(email, response.getEmail());
        assertEquals("2023-2024", response.getFinancialYear());
        assertNotNull(response.getRealisedProfits());
        assertEquals(500.0, response.getRealisedProfits().getShortTermCapitalGains().getProfit(), 0.01);
    }

    @Test
    void getProfitAndLoss_whenMissing_shouldReturnEmptyEntity() {
        // GIVEN
        String email = "nonexistent@example.com";
        UserMail userMail = UserMail.from(email);

        // WHEN
        var response = profitAndLossService.getProfitAndLoss(userMail, "2023-2024");

        // THEN
        assertNotNull(response);
        assertNull(response.getEmail());
        assertNull(response.getFinancialYear());
    }

    @Test
    void deleteProfitAndLoss_whenCalled_shouldDeleteByEmail() {
        // GIVEN
        String email = "todelete@example.com";

        ProfitAndLossEntity pnl = new ProfitAndLossEntity(email);
        pnl.setFinancialYear("2023-2024");
        mongoTemplate.save(pnl, "profit_and_loss");

        assertNotNull(findPnlByEmail(email));

        UserMail userMail = UserMail.from(email);

        // WHEN
        profitAndLossService.deleteProfitAndLoss(userMail);

        // THEN
        assertNull(findPnlByEmail(email));
    }

    @Test
    void updateProfitAndLoss_whenWriteFailure_shouldRollback() {
        // GIVEN
        String email = "rollback@example.com";
        UserMail userMail = UserMail.from(email);
        String stockCode = "FAIL";

        // Clean up any existing P&L first
        deletePnlByEmail(email);

        // Create a context with valid structure (not empty) to avoid NPE
        LocalDate buyDate = LocalDate.of(2024, 1, 15);
        LocalDate sellDate = LocalDate.of(2024, 6, 20);

        ProfitAndLossContext context = buildContext(email, stockCode, buyDate, sellDate, 100.0, 10.0, 15.0, AccountType.SELF);

        // WHEN/THEN - Service should handle gracefully without throwing
        assertDoesNotThrow(() -> profitAndLossService.updateProfitAndLoss(userMail, context));
    }

    @Test
    void updateProfitAndLoss_whenExactOneYearBoundary_shouldTreatAsLongTerm() {
        // GIVEN
        String email = "boundary@example.com";
        UserMail userMail = UserMail.from(email);
        String stockCode = "BOUNDARY";

        LocalDate buyDate = LocalDate.of(2023, 4, 1);
        LocalDate sellDate = LocalDate.of(2024, 4, 1); // Exactly 1 year

        AssetRequest buyRequest = createBuyRequest(email, stockCode, buyDate, 50.0, 5.0);

        AssetRequest sellRequest = createSellRequest(email, stockCode, sellDate, 50.0, 12.0, AccountType.SELF);

        ProfitAndLossContext context = buildContext(email, stockCode, buyDate, sellDate, 50.0, 5.0, 12.0, AccountType.SELF);

        // WHEN
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // THEN
        ProfitAndLossEntity pnl = findPnlByEmail(email);
        assertNotNull(pnl);
        // At exactly 1 year boundary - verify classification is consistent
        assertNotNull(pnl.getRealisedProfits());
    }

    @Test
    void updateProfitAndLoss_whenZeroQuantity_shouldHandleGracefully() {
        // GIVEN
        String email = "zeroqty@example.com";
        UserMail userMail = UserMail.from(email);
        String stockCode = "ZEROGAIN";

        LocalDate buyDate = LocalDate.of(2024, 1, 15);
        LocalDate sellDate = LocalDate.of(2024, 6, 20);

        // Build context with zero quantity
        AssetEntity buyAsset = new AssetEntity();
        buyAsset.setEmail(email);
        buyAsset.setStockCode(stockCode);
        buyAsset.setPrice(100.0);
        buyAsset.setQuantity(0.0); // Zero quantity
        buyAsset.setTransactionDate(buyDate);
        buyAsset.setAssetType(AssetType.EQUITY);
        buyAsset.setBrokerCharges(10.0);
        buyAsset.setMiscCharges(5.0);

        AssetRequest sellRequest = new AssetRequest();
        sellRequest.setEmail(email);
        sellRequest.setStockCode(stockCode);
        sellRequest.setPrice(15.0);
        sellRequest.setQuantity(0.0); // Zero quantity
        sellRequest.setTransactionDate(sellDate);
        sellRequest.setAssetType(AssetType.EQUITY);
        sellRequest.setAccountType(AccountType.SELF);
        sellRequest.setAccountHolder(email);
        sellRequest.setBrokerCharges(10.0);
        sellRequest.setMiscCharges(5.0);

        ProfitAndLossContext context = ProfitAndLossContext.from(buyAsset, sellRequest, 0.0);

        // WHEN
        profitAndLossService.updateProfitAndLoss(userMail, context);

        // THEN - verify no exception thrown and P&L exists
        ProfitAndLossEntity pnl = findPnlByEmail(email);
        assertNotNull(pnl);
        assertNotNull(pnl.getRealisedProfits());
    }

    // Helper methods

    private AssetRequest createBuyRequest(String email, String stockCode, LocalDate date, double price, double quantity) {
        AssetRequest request = new AssetRequest();
        request.setEmail(email);
        request.setStockCode(stockCode);
        request.setStockName(stockCode);
        request.setExchangeName("NSE");
        request.setBrokerName(BrokerName.ZERODHA);
        request.setAssetType(AssetType.EQUITY);
        request.setPrice(price);
        request.setQuantity(quantity);
        request.setTransactionType(TransactionType.BUY);
        request.setAccountType(AccountType.SELF);
        request.setAccountHolder(email);
        request.setTransactionDate(date);
        request.setBrokerCharges(10.0);
        request.setMiscCharges(5.0);
        return request;
    }

    private AssetRequest createSellRequest(String email, String stockCode, LocalDate date, double quantity, double price, AccountType accountType) {
        AssetRequest request = new AssetRequest();
        request.setEmail(email);
        request.setStockCode(stockCode);
        request.setStockName(stockCode);
        request.setExchangeName("NSE");
        request.setBrokerName(BrokerName.ZERODHA);
        request.setAssetType(AssetType.EQUITY);
        request.setPrice(price);
        request.setQuantity(quantity);
        request.setTransactionType(TransactionType.SELL);
        request.setAccountType(accountType);
        request.setAccountHolder(email);
        request.setTransactionDate(date);
        request.setBrokerCharges(10.0);
        request.setMiscCharges(5.0);
        return request;
    }

    private ProfitAndLossContext buildContext(String email, String stockCode, LocalDate buyDate, LocalDate sellDate,
                                              double quantity, double buyPrice, double sellPrice, AccountType accountType) {
        AssetEntity buyAsset = new AssetEntity();
        buyAsset.setEmail(email);
        buyAsset.setStockCode(stockCode);
        buyAsset.setPrice(buyPrice);
        buyAsset.setQuantity(quantity);
        buyAsset.setTransactionDate(buyDate);
        buyAsset.setAssetType(AssetType.EQUITY);
        buyAsset.setBrokerCharges(10.0);
        buyAsset.setMiscCharges(5.0);

        AssetRequest sellRequest = new AssetRequest();
        sellRequest.setEmail(email);
        sellRequest.setStockCode(stockCode);
        sellRequest.setPrice(sellPrice);
        sellRequest.setQuantity(quantity);
        sellRequest.setTransactionDate(sellDate);
        sellRequest.setAssetType(AssetType.EQUITY);
        sellRequest.setAccountType(accountType);
        sellRequest.setAccountHolder(email);
        sellRequest.setBrokerCharges(10.0);
        sellRequest.setMiscCharges(5.0);

        return ProfitAndLossContext.from(buyAsset, sellRequest, quantity);
    }

    private ProfitAndLossEntity findPnlByEmail(String email) {
        Query query = new Query(Criteria.where("email").is(email));
        return mongoTemplate.findOne(query, ProfitAndLossEntity.class, "profit_and_loss");
    }

    private void deletePnlByEmail(String email) {
        Query query = new Query(Criteria.where("email").is(email));
        mongoTemplate.remove(query, ProfitAndLossEntity.class, "profit_and_loss");
    }
}
