package com.thiru.wealthlens.service;

import static org.junit.jupiter.api.Assertions.*;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.CapitalGainsType;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService.BuyLot;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService.MatchedTrade;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService.SellRequest;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradeMatchingServiceTest {

    @InjectMocks
    private TradeMatchingService tradeMatchingService;

    @Test
    void shouldMergeSameDayBuys() {
        // Given: Two buys of same stock on same day -> one lot with averaged price
        String email = "test@example.com";
        String stockCode = "RELIANCE";
        BrokerName brokerName = BrokerName.ZERODHA;
        LocalDate buyDate = LocalDate.of(2023, 1, 15);
        String accountHolder = "main";

        TransactionEntity buy1 = createBuyTransaction(email, stockCode, "Reliance Industries", BrokerName.ZERODHA,
                AccountType.SELF, accountHolder, 100.0, 5.0, 10.0, 5.0, buyDate);
        TransactionEntity buy2 = createBuyTransaction(email, stockCode, "Reliance Industries", BrokerName.ZERODHA,
                AccountType.SELF, accountHolder, 120.0, 5.0, 15.0, 5.0, buyDate);

        List<TransactionEntity> buyTransactions = List.of(buy1, buy2);

        // When
        List<BuyLot> lots = tradeMatchingService.buildLotsFromBuys(buyTransactions);

        // Then
        assertEquals(1, lots.size());
        BuyLot lot = lots.get(0);
        assertEquals(10.0, lot.getQuantity(), 0.01);
        // Weighted average: (5*100 + 5*120) / 10 = 110.0
        assertEquals(110.0, lot.getBuyPrice(), 0.01);
        // Sum of charges
        assertEquals(25.0, lot.getBrokerCharges(), 0.01);
        assertEquals(10.0, lot.getMiscCharges(), 0.01);
        // Should have 2 transaction IDs
        assertEquals(2, lot.getBuyTransactionIds().size());
    }

    @Test
    void shouldMatchSellAgainstSingleLot() {
        // Given: Buy 10@100, Sell 5@150 -> one matched trade with qty=5
        String email = "test@example.com";
        String stockCode = "RELIANCE";
        BrokerName brokerName = BrokerName.ZERODHA;
        String accountHolder = "main";

        LocalDate buyDate = LocalDate.of(2023, 1, 15);
        LocalDate sellDate = LocalDate.of(2023, 6, 15); // ~150 days = short term

        // Build a buy lot
        List<TransactionEntity> buyTransactions = List.of(
                createBuyTransaction(email, stockCode, "Reliance Industries", brokerName,
                        AccountType.SELF, accountHolder, 100.0, 10.0, 20.0, 10.0, buyDate)
        );
        List<BuyLot> lots = tradeMatchingService.buildLotsFromBuys(buyTransactions);

        // Create sell request
        SellRequest sellRequest = SellRequest.builder()
                .id("sell-txn-1")
                .email(email)
                .stockCode(stockCode)
                .stockName("Reliance Industries")
                .exchangeName("NSE")
                .brokerName(brokerName)
                .assetType(AssetType.EQUITY)
                .accountType(AccountType.SELF)
                .accountHolder(accountHolder)
                .sellPrice(150.0)
                .quantity(5.0)
                .brokerCharges(15.0)
                .miscCharges(5.0)
                .sellDate(sellDate)
                .build();

        // When
        List<MatchedTrade> matchedTrades = tradeMatchingService.matchSellToLots(sellRequest, lots);

        // Then
        assertEquals(1, matchedTrades.size());
        MatchedTrade trade = matchedTrades.get(0);
        assertEquals(5.0, trade.getBuyQuantity(), 0.01);
        assertEquals(5.0, trade.getSellQuantity(), 0.01);
        // Pro-rated charges: (20/10)*5 = 10 for broker, (10/10)*5 = 5 for misc
        assertEquals(10.0, trade.getBuyBrokerCharges(), 0.01);
        assertEquals(5.0, trade.getBuyMiscCharges(), 0.01);
        // Sell charges: (15/5)*5 = 15, (5/5)*5 = 5
        assertEquals(15.0, trade.getSellBrokerCharges(), 0.01);
        assertEquals(5.0, trade.getSellMiscCharges(), 0.01);
        // Total buy: (100*5) + 10 + 5 = 515
        assertEquals(515.0, trade.getTotalBuyValue(), 0.01);
        // Total sell: (150*5) - 15 - 5 = 730
        assertEquals(730.0, trade.getTotalSellValue(), 0.01);
        // Net profit: 730 - 515 = 215
        assertEquals(215.0, trade.getNetProfit(), 0.01);
        // Remaining lot should have 5 units
        assertEquals(5.0, lots.get(0).getQuantity(), 0.01);
    }

    @Test
    void shouldMatchSellAcrossMultipleLots() {
        // Given: Buy 5@100 (Jan), Buy 5@120 (Feb), Sell 7@150 -> two matched trades (5+2)
        String email = "test@example.com";
        String stockCode = "RELIANCE";
        BrokerName brokerName = BrokerName.ZERODHA;
        String accountHolder = "main";

        LocalDate buyDate1 = LocalDate.of(2023, 1, 15);
        LocalDate buyDate2 = LocalDate.of(2023, 2, 15);
        LocalDate sellDate = LocalDate.of(2023, 6, 15);

        List<TransactionEntity> buyTransactions = List.of(
                createBuyTransaction(email, stockCode, "Reliance Industries", brokerName,
                        AccountType.SELF, accountHolder, 100.0, 5.0, 10.0, 5.0, buyDate1),
                createBuyTransaction(email, stockCode, "Reliance Industries", brokerName,
                        AccountType.SELF, accountHolder, 120.0, 5.0, 10.0, 5.0, buyDate2)
        );
        List<BuyLot> lots = tradeMatchingService.buildLotsFromBuys(buyTransactions);

        SellRequest sellRequest = SellRequest.builder()
                .id("sell-txn-1")
                .email(email)
                .stockCode(stockCode)
                .stockName("Reliance Industries")
                .exchangeName("NSE")
                .brokerName(brokerName)
                .assetType(AssetType.EQUITY)
                .accountType(AccountType.SELF)
                .accountHolder(accountHolder)
                .sellPrice(150.0)
                .quantity(7.0)
                .brokerCharges(21.0)
                .miscCharges(7.0)
                .sellDate(sellDate)
                .build();

        // When
        List<MatchedTrade> matchedTrades = tradeMatchingService.matchSellToLots(sellRequest, lots);

        // Then
        assertEquals(2, matchedTrades.size());

        // First trade: 5 from lot 1 (buy price 100)
        MatchedTrade trade1 = matchedTrades.get(0);
        assertEquals(5.0, trade1.getBuyQuantity(), 0.01);
        assertEquals(100.0, trade1.getOriginalBuyPrice(), 0.01);
        assertEquals(buyDate1, trade1.getBuyDate());

        // Second trade: 2 from lot 2 (buy price 120)
        MatchedTrade trade2 = matchedTrades.get(1);
        assertEquals(2.0, trade2.getBuyQuantity(), 0.01);
        assertEquals(120.0, trade2.getOriginalBuyPrice(), 0.01);
        assertEquals(buyDate2, trade2.getBuyDate());

        // Verify lots remaining
        // Lot 1 should be fully consumed
        assertEquals(0.0, lots.get(0).getQuantity(), 0.01);
        // Lot 2 should have 3 remaining
        assertEquals(3.0, lots.get(1).getQuantity(), 0.01);
    }

    @Test
    void shouldProRateChargesCorrectly() {
        // Given: Buy 10@100 with charges=50, Sell 5@150 with charges=30
        // Buy charges = 50/10*5 = 25
        // Sell charges = 30/10*5 = 15
        String email = "test@example.com";
        String stockCode = "RELIANCE";
        BrokerName brokerName = BrokerName.ZERODHA;
        String accountHolder = "main";

        LocalDate buyDate = LocalDate.of(2023, 1, 15);
        LocalDate sellDate = LocalDate.of(2023, 6, 15);

        List<TransactionEntity> buyTransactions = List.of(
                createBuyTransaction(email, stockCode, "Reliance Industries", brokerName,
                        AccountType.SELF, accountHolder, 100.0, 10.0, 30.0, 20.0, buyDate)
        );
        List<BuyLot> lots = tradeMatchingService.buildLotsFromBuys(buyTransactions);

        SellRequest sellRequest = SellRequest.builder()
                .id("sell-txn-1")
                .email(email)
                .stockCode(stockCode)
                .stockName("Reliance Industries")
                .exchangeName("NSE")
                .brokerName(brokerName)
                .assetType(AssetType.EQUITY)
                .accountType(AccountType.SELF)
                .accountHolder(accountHolder)
                .sellPrice(150.0)
                .quantity(5.0)
                .brokerCharges(15.0)
                .miscCharges(15.0)
                .sellDate(sellDate)
                .build();

        // When
        List<MatchedTrade> matchedTrades = tradeMatchingService.matchSellToLots(sellRequest, lots);

        // Then
        assertEquals(1, matchedTrades.size());
        MatchedTrade trade = matchedTrades.get(0);

        // Buy charges pro-rated: (30/10)*5 = 15 broker, (20/10)*5 = 10 misc
        assertEquals(15.0, trade.getBuyBrokerCharges(), 0.01);
        assertEquals(10.0, trade.getBuyMiscCharges(), 0.01);

        // Sell charges pro-rated: (15/5)*5 = 15 broker, (15/5)*5 = 15 misc
        assertEquals(15.0, trade.getSellBrokerCharges(), 0.01);
        assertEquals(15.0, trade.getSellMiscCharges(), 0.01);

        // Verify computed values
        // Total buy: (100*5) + 15 + 10 = 525
        assertEquals(525.0, trade.getTotalBuyValue(), 0.01);
        // Total sell: (150*5) - 15 - 15 = 720
        assertEquals(720.0, trade.getTotalSellValue(), 0.01);
        // Net profit: 720 - 525 = 195
        assertEquals(195.0, trade.getNetProfit(), 0.01);
    }

    @Test
    void shouldHandlePartialLotSell() {
        // Given: Buy 10@100, Sell 3@150 -> lot remaining qty=7
        String email = "test@example.com";
        String stockCode = "RELIANCE";
        BrokerName brokerName = BrokerName.ZERODHA;
        String accountHolder = "main";

        LocalDate buyDate = LocalDate.of(2023, 1, 15);
        LocalDate sellDate = LocalDate.of(2023, 6, 15);

        List<TransactionEntity> buyTransactions = List.of(
                createBuyTransaction(email, stockCode, "Reliance Industries", brokerName,
                        AccountType.SELF, accountHolder, 100.0, 10.0, 10.0, 5.0, buyDate)
        );
        List<BuyLot> lots = tradeMatchingService.buildLotsFromBuys(buyTransactions);

        SellRequest sellRequest = SellRequest.builder()
                .id("sell-txn-1")
                .email(email)
                .stockCode(stockCode)
                .stockName("Reliance Industries")
                .exchangeName("NSE")
                .brokerName(brokerName)
                .assetType(AssetType.EQUITY)
                .accountType(AccountType.SELF)
                .accountHolder(accountHolder)
                .sellPrice(150.0)
                .quantity(3.0)
                .brokerCharges(9.0)
                .miscCharges(3.0)
                .sellDate(sellDate)
                .build();

        // When
        List<MatchedTrade> matchedTrades = tradeMatchingService.matchSellToLots(sellRequest, lots);

        // Then
        assertEquals(1, matchedTrades.size());
        MatchedTrade trade = matchedTrades.get(0);
        assertEquals(3.0, trade.getBuyQuantity(), 0.01);
        assertEquals(3.0, trade.getSellQuantity(), 0.01);

        // Lot should have 7 remaining
        assertEquals(7.0, lots.get(0).getQuantity(), 0.01);
    }

    @Test
    void shouldMatchNextLotAfterConsumingFullLot() {
        // Given: Buy 5@100, Buy 5@120, Sell 7@150
        // First trade: qty=5 from lot1
        // Second trade: qty=2 from lot2, lot2 remaining=3
        String email = "test@example.com";
        String stockCode = "RELIANCE";
        BrokerName brokerName = BrokerName.ZERODHA;
        String accountHolder = "main";

        LocalDate buyDate1 = LocalDate.of(2023, 1, 15);
        LocalDate buyDate2 = LocalDate.of(2023, 2, 15);
        LocalDate sellDate = LocalDate.of(2023, 6, 15);

        List<TransactionEntity> buyTransactions = List.of(
                createBuyTransaction(email, stockCode, "Reliance Industries", brokerName,
                        AccountType.SELF, accountHolder, 100.0, 5.0, 5.0, 2.0, buyDate1),
                createBuyTransaction(email, stockCode, "Reliance Industries", brokerName,
                        AccountType.SELF, accountHolder, 120.0, 5.0, 6.0, 3.0, buyDate2)
        );
        List<BuyLot> lots = tradeMatchingService.buildLotsFromBuys(buyTransactions);

        SellRequest sellRequest = SellRequest.builder()
                .id("sell-txn-1")
                .email(email)
                .stockCode(stockCode)
                .stockName("Reliance Industries")
                .exchangeName("NSE")
                .brokerName(brokerName)
                .assetType(AssetType.EQUITY)
                .accountType(AccountType.SELF)
                .accountHolder(accountHolder)
                .sellPrice(150.0)
                .quantity(7.0)
                .brokerCharges(21.0)
                .miscCharges(7.0)
                .sellDate(sellDate)
                .build();

        // When
        List<MatchedTrade> matchedTrades = tradeMatchingService.matchSellToLots(sellRequest, lots);

        // Then
        assertEquals(2, matchedTrades.size());

        // First lot fully consumed
        assertEquals(0.0, lots.get(0).getQuantity(), 0.01);
        // Second lot has 3 remaining
        assertEquals(3.0, lots.get(1).getQuantity(), 0.01);

        // First trade from lot 1
        MatchedTrade trade1 = matchedTrades.get(0);
        assertEquals(5.0, trade1.getBuyQuantity(), 0.01);
        assertEquals(100.0, trade1.getOriginalBuyPrice(), 0.01);

        // Second trade from lot 2
        MatchedTrade trade2 = matchedTrades.get(1);
        assertEquals(2.0, trade2.getBuyQuantity(), 0.01);
        assertEquals(120.0, trade2.getOriginalBuyPrice(), 0.01);
    }

    @Test
    void shouldIdentifyCaDerivedBuys() {
        // Given: A bonus buy (price=0)
        String email = "test@example.com";
        String stockCode = "RELIANCE";
        BrokerName brokerName = BrokerName.ZERODHA;
        String accountHolder = "main";

        LocalDate buyDate = LocalDate.of(2023, 1, 15);

        TransactionEntity bonusBuy = createBuyTransaction(email, stockCode, "Reliance Industries", brokerName,
                AccountType.SELF, accountHolder, 0.0, 10.0, 10.0, 5.0, buyDate);
        bonusBuy.setCorporateActionType(CorporateActionType.BONUS);

        List<TransactionEntity> buyTransactions = List.of(bonusBuy);

        // When
        List<BuyLot> lots = tradeMatchingService.buildLotsFromBuys(buyTransactions);

        // Then
        assertEquals(1, lots.size());
        assertTrue(lots.get(0).isCaDerived());
    }

    @Test
    void shouldConvertToTradeOutcomeEntity() {
        // Given
        MatchedTrade trade = MatchedTrade.builder()
                .email("test@example.com")
                .stockCode("RELIANCE")
                .stockName("Reliance Industries")
                .exchangeName("NSE")
                .brokerName(BrokerName.ZERODHA)
                .assetType(AssetType.EQUITY)
                .accountType(AccountType.SELF)
                .accountHolder("main")
                .originalBuyPrice(100.0)
                .caAdjustedBuyPrice(100.0)
                .buyQuantity(10.0)
                .buyDate(LocalDate.of(2023, 1, 15))
                .buyBrokerCharges(10.0)
                .buyMiscCharges(5.0)
                .sellPrice(150.0)
                .sellQuantity(10.0)
                .sellDate(LocalDate.of(2023, 6, 15))
                .sellBrokerCharges(15.0)
                .sellMiscCharges(5.0)
                .totalBuyValue(1015.0)
                .totalSellValue(1480.0)
                .netProfit(465.0)
                .profitPercentage(45.81)
                .holdingPeriodDays(151)
                .capitalGainsType(CapitalGainsType.SHORT_TERM)
                .financialYear("FY2023-24")
                .sourceSellTransactionId("sell-123")
                .sourceBuyLotId("lot-RELIANCE-2023-01-15")
                .isCaDerived(false)
                .appliedCorporateActions(null)
                .build();

        // When
        TradeOutcomeEntity entity = tradeMatchingService.toTradeOutcomeEntity(trade);

        // Then
        assertEquals("test@example.com", entity.getEmail());
        assertEquals("RELIANCE", entity.getStockCode());
        assertEquals("Reliance Industries", entity.getStockName());
        assertEquals(BrokerName.ZERODHA, entity.getBrokerName());
        assertEquals(AssetType.EQUITY, entity.getAssetType());
        assertEquals(100.0, entity.getOriginalBuyPrice(), 0.01);
        assertEquals(100.0, entity.getCaAdjustedBuyPrice(), 0.01);
        assertEquals(10.0, entity.getBuyQuantity(), 0.01);
        assertEquals(LocalDate.of(2023, 1, 15), entity.getBuyDate());
        assertEquals(150.0, entity.getSellPrice(), 0.01);
        assertEquals(10.0, entity.getSellQuantity(), 0.01);
        assertEquals(LocalDate.of(2023, 6, 15), entity.getSellDate());
        assertEquals(465.0, entity.getNetProfit(), 0.01);
        assertEquals(151, entity.getHoldingPeriodDays());
        assertEquals(CapitalGainsType.SHORT_TERM, entity.getCapitalGainsType());
        assertEquals("FY2023-24", entity.getFinancialYear());
        assertEquals("sell-123", entity.getSourceSellTransactionId());
        assertEquals("lot-RELIANCE-2023-01-15", entity.getSourceBuyLotId());
        assertFalse(entity.getIsCaDerived());
        assertNotNull(entity.getAuditMetadata());
    }

    @Test
    void shouldConvertTransactionToSellRequest() {
        // Given
        TransactionEntity sellTxn = new TransactionEntity();
        sellTxn.setId("txn-123");
        sellTxn.setEmail("test@example.com");
        sellTxn.setStockCode("RELIANCE");
        sellTxn.setStockName("Reliance Industries");
        sellTxn.setExchangeName("NSE");
        sellTxn.setBrokerName(BrokerName.ZERODHA);
        sellTxn.setAssetType(AssetType.EQUITY);
        sellTxn.setAccountType(AccountType.SELF);
        sellTxn.setAccountHolder("main");
        sellTxn.setPrice(150.0);
        sellTxn.setQuantity(10.0);
        sellTxn.setBrokerCharges(15.0);
        sellTxn.setMiscCharges(5.0);
        sellTxn.setTransactionDate(LocalDate.of(2023, 6, 15));

        // When
        SellRequest request = tradeMatchingService.toSellRequest(sellTxn);

        // Then
        assertEquals("txn-123", request.getId());
        assertEquals("test@example.com", request.getEmail());
        assertEquals("RELIANCE", request.getStockCode());
        assertEquals(150.0, request.getSellPrice(), 0.01);
        assertEquals(10.0, request.getQuantity(), 0.01);
        assertEquals(LocalDate.of(2023, 6, 15), request.getSellDate());
    }

    private TransactionEntity createBuyTransaction(String email, String stockCode, String stockName,
                                                    BrokerName brokerName, AccountType accountType,
                                                    String accountHolder, double price, double quantity,
                                                    double brokerCharges, double miscCharges, LocalDate date) {
        TransactionEntity txn = new TransactionEntity();
        txn.setEmail(email);
        txn.setStockCode(stockCode);
        txn.setStockName(stockName);
        txn.setExchangeName("NSE");
        txn.setBrokerName(brokerName);
        txn.setAssetType(AssetType.EQUITY);
        txn.setAccountType(accountType);
        txn.setAccountHolder(accountHolder);
        txn.setPrice(price);
        txn.setQuantity(quantity);
        txn.setBrokerCharges(brokerCharges);
        txn.setMiscCharges(miscCharges);
        txn.setTransactionDate(date);
        txn.setCorporateActions(new ArrayList<>());
        return txn;
    }
}
