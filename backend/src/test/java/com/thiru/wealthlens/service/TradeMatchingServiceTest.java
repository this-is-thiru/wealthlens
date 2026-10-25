package com.thiru.wealthlens.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.portfolio.dto.Acquisition;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeSegment;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService.BuyLot;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService.MatchedTrade;
import com.thiru.wealthlens.portfolio.service.TradeMatchingService.SellRequest;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.testsupport.MoneyAssert;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradeMatchingServiceTest {

    private final TradeMatchingService tradeMatchingService = new TradeMatchingService();

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
    void shouldIdentifyCorporateActionDerivedBuys() {
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
        assertTrue(lots.get(0).isCorporateActionDerived());
    }

    @Test
    void matchSellToLots_whenSoldEitherSideOfTheYearEnd_writesTheYearAsPortfolioDoes() {
        // Given — one lot of 10; sells of 5 on 31 March 2024 and 5 on 1 April 2024
        List<BuyLot> lots = tradeMatchingService.buildLotsFromBuys(List.of(createBuyTransaction("test@example.com",
                "RELIANCE", "Reliance Industries", BrokerName.ZERODHA, AccountType.SELF, "main", 100.0, 10.0, 0.0,
                0.0, LocalDate.of(2023, 1, 15))));

        // When
        String lastDay = tradeMatchingService.matchSellToLots(sellOfFive("sell-1", LocalDate.of(2024, 3, 31)), lots)
                .getFirst().getFinancialYear();
        String firstDay = tradeMatchingService.matchSellToLots(sellOfFive("sell-2", LocalDate.of(2024, 4, 1)), lots)
                .getFirst().getFinancialYear();

        // Then — FinancialYear.label()'s form, as every other portfolio writer uses (D-239), not FY2023-24
        assertEquals("2023-2024", lastDay);
        assertEquals("2024-2025", firstDay);
    }

    @Test
    void buildLotsFromBuys_whenBuysShareADayButNotAHolder_mergesOnlyTheSameKey() {
        // Given — two buys on 2024-02-05 under one key, one that day for another holder, one a day earlier
        LocalDate day = LocalDate.of(2024, 2, 5);
        TransactionEntity first = buy("b-1", "main", day, 100.0, 4.0, 2.0, 0.5);
        TransactionEntity second = buy("b-2", "main", day, 110.0, 6.0, 3.0, 0.5);
        TransactionEntity otherHolder = buy("b-3", "spouse", day, 90.0, 2.0, 1.0, 0.0);
        TransactionEntity earlier = buy("b-4", "main", day.minusDays(1), 80.0, 1.0, 0.0, 0.0);

        // When
        List<BuyLot> lots = tradeMatchingService.buildLotsFromBuys(List.of(first, otherHolder, second, earlier));

        // Then — three lots, sorted by buy date; the two of 2024-02-05 told apart by holder, not position
        assertEquals(3, lots.size());
        assertEquals(LocalDate.of(2024, 2, 4), lots.get(0).getBuyDate());
        assertEquals(List.of("b-4"), lots.get(0).getBuyTransactionIds());
        BuyLot merged = lots.stream().filter(lot -> "main".equals(lot.getAccountHolder()) && day.equals(lot.getBuyDate()))
                .findFirst().orElseThrow();
        assertEquals("lot-TCS-2024-02-05", merged.getId());
        assertEquals("lots@example.com", merged.getEmail());
        assertEquals("TCS", merged.getStockCode());
        assertEquals("Tata Consultancy", merged.getStockName());
        assertEquals("BSE", merged.getExchangeName());
        assertEquals(BrokerName.UPSTOX, merged.getBrokerName());
        assertEquals(AssetType.EQUITY, merged.getAssetType());
        assertEquals(AccountType.OUTSOURCED, merged.getAccountType());
        assertEquals(day, merged.getBuyDate());
        // (4 × 100 + 6 × 110) ÷ 10 = 1,060 ÷ 10 = 106
        MoneyAssert.assertMoney(106.0, merged.getBuyPrice());
        assertEquals(10.0, merged.getQuantity(), 0.0);
        MoneyAssert.assertMoney(5.0, merged.getBrokerCharges());
        MoneyAssert.assertMoney(1.0, merged.getMiscCharges());
        assertEquals(List.of("b-1", "b-2"), merged.getBuyTransactionIds());
        assertNull(merged.getCorporateActions());
        assertFalse(merged.isCorporateActionDerived());
        assertEquals(Acquisition.of(null), merged.getAcquisition());
        BuyLot spouse = lots.stream().filter(lot -> "spouse".equals(lot.getAccountHolder())).findFirst().orElseThrow();
        assertEquals(List.of("b-3"), spouse.getBuyTransactionIds());
        assertEquals(2.0, spouse.getQuantity(), 0.0);
    }

    @Test
    void matchSellToLots_whenOneSellSpansTwoLots_writesEveryField() {
        // Given — L1 and L2 eligible; L3 at another broker and L4 for another holder are not
        BuyLot first = lot("L1", BrokerName.ZERODHA, "main", LocalDate.of(2024, 1, 10), 100.0, 20.0, 5.0);
        BuyLot second = lot("L2", BrokerName.ZERODHA, "main", LocalDate.of(2024, 3, 1), 120.0, 10.0, 0.0);
        BuyLot otherBroker = lot("L3", BrokerName.UPSTOX, "main", LocalDate.of(2023, 12, 1), 90.0, 0.0, 0.0);
        BuyLot otherHolder = lot("L4", BrokerName.ZERODHA, "spouse", LocalDate.of(2023, 12, 1), 90.0, 0.0, 0.0);
        SellRequest sell = SellRequest.builder()
                .id("sell-9")
                .email("match@example.com")
                .stockCode("INFY")
                .stockName("Sell Co")
                .exchangeName("NSE")
                .brokerName(BrokerName.ZERODHA)
                .assetType(AssetType.EQUITY)
                .accountType(AccountType.SELF)
                .accountHolder("main")
                .sellPrice(150.0)
                .quantity(15.0)
                .brokerCharges(30.0)
                .miscCharges(3.0)
                .sellDate(LocalDate.of(2025, 6, 2))
                .build();

        // When
        List<MatchedTrade> trades = tradeMatchingService.matchSellToLots(sell,
                List.of(otherBroker, otherHolder, second, first));

        // Then — L1 first (earlier), all 10; then 5 of L2
        assertEquals(2, trades.size());
        assertFirstLotsTrade(trades.get(0));
        assertSecondLotsTrade(trades.get(1));
        assertEquals(0.0, first.getQuantity(), 0.0);
        assertEquals(5.0, second.getQuantity(), 0.0);
        assertEquals(10.0, otherBroker.getQuantity(), 0.0);
        assertEquals(10.0, otherHolder.getQuantity(), 0.0);
    }

    /** All of L1 (10 @ ₹100, broker 20, misc 5, a bonus lot) against 10 of the sell. */
    private static void assertFirstLotsTrade(MatchedTrade fromFirst) {
        assertSellSide(fromFirst);
        assertEquals("L1", fromFirst.getSourceBuyLotId());
        MoneyAssert.assertMoney(100.0, fromFirst.getOriginalBuyPrice());
        MoneyAssert.assertMoney(100.0, fromFirst.getCorporateActionAdjustedBuyPrice());
        assertEquals(LocalDate.of(2024, 1, 10), fromFirst.getBuyDate());
        assertEquals(10.0, fromFirst.getBuyQuantity(), 0.0);
        assertEquals(10.0, fromFirst.getSellQuantity(), 0.0);
        MoneyAssert.assertMoney(20.0, fromFirst.getBuyBrokerCharges());     // 20 ÷ 10 × 10
        MoneyAssert.assertMoney(5.0, fromFirst.getBuyMiscCharges());        // 5 ÷ 10 × 10
        MoneyAssert.assertMoney(20.0, fromFirst.getSellBrokerCharges());    // 30 ÷ 15 × 10
        MoneyAssert.assertMoney(2.0, fromFirst.getSellMiscCharges());       // 3 ÷ 15 × 10
        MoneyAssert.assertMoney(1025.0, fromFirst.getTotalBuyValue());      // 100 × 10 + 20 + 5
        MoneyAssert.assertMoney(1478.0, fromFirst.getTotalSellValue());     // 150 × 10 − 20 − 2
        MoneyAssert.assertMoney(453.0, fromFirst.getNetProfit());
        assertEquals(453.0 / 1025.0 * 100, fromFirst.getProfitPercentage(), 1e-9);
        // 2024-01-10 → 2025-01-10 is 366 days (2024 is a leap year); then 21 + 28 + 31 + 30 + 31 + 2 = 143
        assertEquals(509L, fromFirst.getHoldingPeriodDays());
        assertTrue(fromFirst.isCorporateActionDerived());
        assertEquals(List.of(), fromFirst.getAppliedCorporateActions());
        assertEquals(Acquisition.of(CorporateActionType.BONUS), fromFirst.getAcquisition());
    }

    /** 5 of L2 (10 @ ₹120, broker 10, misc 0, a purchase) against the sell's last 5. */
    private static void assertSecondLotsTrade(MatchedTrade fromSecond) {
        assertSellSide(fromSecond);
        assertEquals("L2", fromSecond.getSourceBuyLotId());
        MoneyAssert.assertMoney(120.0, fromSecond.getOriginalBuyPrice());
        assertEquals(LocalDate.of(2024, 3, 1), fromSecond.getBuyDate());
        assertEquals(5.0, fromSecond.getBuyQuantity(), 0.0);
        MoneyAssert.assertMoney(5.0, fromSecond.getBuyBrokerCharges());     // 10 ÷ 10 × 5
        MoneyAssert.assertMoney(0.0, fromSecond.getBuyMiscCharges());
        MoneyAssert.assertMoney(10.0, fromSecond.getSellBrokerCharges());   // 30 ÷ 15 × 5
        MoneyAssert.assertMoney(1.0, fromSecond.getSellMiscCharges());      // 3 ÷ 15 × 5
        MoneyAssert.assertMoney(605.0, fromSecond.getTotalBuyValue());      // 120 × 5 + 5 + 0
        MoneyAssert.assertMoney(739.0, fromSecond.getTotalSellValue());     // 150 × 5 − 10 − 1
        MoneyAssert.assertMoney(134.0, fromSecond.getNetProfit());
        assertEquals(134.0 / 605.0 * 100, fromSecond.getProfitPercentage(), 1e-9);
        // 2024-03-01 → 2025-03-01 is 365 days; then 31 + 30 + 31 + 1 = 93
        assertEquals(458L, fromSecond.getHoldingPeriodDays());
        assertFalse(fromSecond.isCorporateActionDerived());
        assertNull(fromSecond.getAppliedCorporateActions());
        assertEquals(Acquisition.of(null), fromSecond.getAcquisition());
    }

    /** The fields a matched trade takes from the sell, which the lots' own values differ from. */
    private static void assertSellSide(MatchedTrade trade) {
        assertEquals("match@example.com", trade.getEmail());
        assertEquals("INFY", trade.getStockCode());
        assertEquals("Sell Co", trade.getStockName());
        assertEquals("NSE", trade.getExchangeName());
        assertEquals(BrokerName.ZERODHA, trade.getBrokerName());
        assertEquals(AssetType.EQUITY, trade.getAssetType());
        assertEquals(AccountType.SELF, trade.getAccountType());
        assertEquals("main", trade.getAccountHolder());
        MoneyAssert.assertMoney(150.0, trade.getSellPrice());
        assertEquals(LocalDate.of(2025, 6, 2), trade.getSellDate());
        assertEquals("sell-9", trade.getSourceSellTransactionId());
        assertEquals("2025-2026", trade.getFinancialYear());
    }

    /** A lot of 10 INFY, with lot-side values that differ from the sell's; L1 is a bonus lot, the rest purchases. */
    private static BuyLot lot(String id, BrokerName broker, String holder, LocalDate buyDate, double price,
            double brokerCharges, double miscCharges) {
        boolean bonus = "L1".equals(id);
        return BuyLot.builder()
                .id(id)
                .email("match@example.com")
                .stockCode("INFY")
                .stockName("Lot Co")
                .exchangeName("BSE")
                .brokerName(broker)
                .assetType(AssetType.EQUITY)
                .accountType(AccountType.OUTSOURCED)
                .accountHolder(holder)
                .buyPrice(price)
                .quantity(10.0)
                .brokerCharges(brokerCharges)
                .miscCharges(miscCharges)
                .buyDate(buyDate)
                .buyTransactionIds(List.of(id + "-buy"))
                .corporateActions(bonus ? List.of() : null)
                .corporateActionDerived(bonus)
                .acquisition(Acquisition.of(bonus ? CorporateActionType.BONUS : null))
                .build();
    }

    /** A buy of TCS at Upstox on BSE, outsourced, under {@code holder}. */
    private static TransactionEntity buy(String id, String holder, LocalDate date, double price, double quantity,
            double brokerCharges, double miscCharges) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setEmail("lots@example.com");
        transaction.setStockCode("TCS");
        transaction.setStockName("Tata Consultancy");
        transaction.setExchangeName("BSE");
        transaction.setBrokerName(BrokerName.UPSTOX);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setAccountType(AccountType.OUTSOURCED);
        transaction.setAccountHolder(holder);
        transaction.setPrice(price);
        transaction.setQuantity(quantity);
        transaction.setBrokerCharges(brokerCharges);
        transaction.setMiscCharges(miscCharges);
        transaction.setTransactionDate(date);
        return transaction;
    }

    @Test
    void matchSellToLots_whenALotIsSoldInTwoSells_chargesItsBuyChargesOnce() {
        // Given — one lot of 10 @ 100 carrying broker 10 and misc 4; sold 5, then 5
        List<BuyLot> lots = tradeMatchingService.buildLotsFromBuys(List.of(createBuyTransaction("test@example.com",
                "RELIANCE", "Reliance Industries", BrokerName.ZERODHA, AccountType.SELF, "main", 100.0, 10.0, 10.0,
                4.0, LocalDate.of(2024, 1, 15))));

        // When
        MatchedTrade firstSell = tradeMatchingService.matchSellToLots(sellOf("sell-1", 5.0, LocalDate.of(2025, 2, 3)),
                lots).getFirst();
        MatchedTrade secondSell = tradeMatchingService.matchSellToLots(sellOf("sell-2", 5.0, LocalDate.of(2025, 3, 3)),
                lots).getFirst();

        // Then — 10 × 5 ÷ 10 = 5, then (10 − 5) × 5 ÷ 5 = 5: the ₹10 paid, once (misc 2 + 2 = 4)
        MoneyAssert.assertMoney(5.0, firstSell.getBuyBrokerCharges());
        MoneyAssert.assertMoney(5.0, secondSell.getBuyBrokerCharges());
        MoneyAssert.assertMoney(2.0, firstSell.getBuyMiscCharges());
        MoneyAssert.assertMoney(2.0, secondSell.getBuyMiscCharges());
        MoneyAssert.assertMoney(507.0, secondSell.getTotalBuyValue());  // 100 × 5 + 5 + 2
    }

    @Test
    void matchSellToLots_whenAThreeUnitLotIsSoldOneAtATime_chargesExactlyWhatWasPaid() {
        // Given — B-8's example: a lot of 3 carrying broker ₹10, sold one unit at a time
        List<BuyLot> lots = tradeMatchingService.buildLotsFromBuys(List.of(createBuyTransaction("test@example.com",
                "RELIANCE", "Reliance Industries", BrokerName.ZERODHA, AccountType.SELF, "main", 100.0, 3.0, 10.0,
                0.0, LocalDate.of(2024, 1, 15))));

        // When
        double charged = 0;
        for (int day = 1; day <= 3; day++) {
            charged += tradeMatchingService.matchSellToLots(sellOf("sell-" + day, 1.0, LocalDate.of(2025, 2, day)),
                    lots).getFirst().getBuyBrokerCharges();
        }

        // Then — each sell a share of what is left, the last the remainder: ₹10.00, not 3.33 + 5.00 + 10.00
        MoneyAssert.assertCanonical("the lot's broker charge across its sells", 10.00, charged);
    }

    /** A sell of {@code quantity} RELIANCE at Zerodha, with no charges of its own. */
    private static SellRequest sellOf(String id, double quantity, LocalDate sellDate) {
        return SellRequest.builder()
                .id(id)
                .email("test@example.com")
                .stockCode("RELIANCE")
                .stockName("Reliance Industries")
                .exchangeName("NSE")
                .brokerName(BrokerName.ZERODHA)
                .assetType(AssetType.EQUITY)
                .accountType(AccountType.SELF)
                .accountHolder("main")
                .sellPrice(150.0)
                .quantity(quantity)
                .brokerCharges(0.0)
                .miscCharges(0.0)
                .sellDate(sellDate)
                .build();
    }

    private static SellRequest sellOfFive(String id, LocalDate sellDate) {
        return SellRequest.builder()
                .id(id)
                .email("test@example.com")
                .stockCode("RELIANCE")
                .stockName("Reliance Industries")
                .exchangeName("NSE")
                .brokerName(BrokerName.ZERODHA)
                .assetType(AssetType.EQUITY)
                .accountType(AccountType.SELF)
                .accountHolder("main")
                .sellPrice(150.0)
                .quantity(5.0)
                .brokerCharges(0.0)
                .miscCharges(0.0)
                .sellDate(sellDate)
                .build();
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
                .corporateActionAdjustedBuyPrice(100.0)
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
                .financialYear("FY2023-24")
                .sourceSellTransactionId("sell-123")
                .sourceBuyLotId("lot-RELIANCE-2023-01-15")
                .corporateActionDerived(false)
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
        assertEquals(100.0, entity.getCorporateActionAdjustedBuyPrice(), 0.01);
        assertEquals(10.0, entity.getBuyQuantity(), 0.01);
        assertEquals(LocalDate.of(2023, 1, 15), entity.getBuyDate());
        assertEquals(150.0, entity.getSellPrice(), 0.01);
        assertEquals(10.0, entity.getSellQuantity(), 0.01);
        assertEquals(LocalDate.of(2023, 6, 15), entity.getSellDate());
        assertEquals(465.0, entity.getNetProfit(), 0.01);
        assertEquals(151, entity.getHoldingPeriodDays());
        assertEquals("FY2023-24", entity.getFinancialYear());
        assertEquals("sell-123", entity.getSourceSellTransactionId());
        assertEquals("lot-RELIANCE-2023-01-15", entity.getSourceBuyLotId());
        assertFalse(entity.getCorporateActionDerived());
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

    /** A transaction of INFY at Zerodha, created by {@code corporateActionType} where one is given. */
    private static TransactionEntity infy(String id, LocalDate date, double quantity, double price,
            CorporateActionType corporateActionType) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setEmail("test@example.com");
        transaction.setStockCode("INFY");
        transaction.setBrokerName(BrokerName.ZERODHA);
        transaction.setAssetType(AssetType.EQUITY);
        transaction.setTransactionDate(date);
        transaction.setQuantity(quantity);
        transaction.setPrice(price);
        transaction.setCorporateActionType(corporateActionType);
        return transaction;
    }

    private List<Acquisition> acquisitionsOf(List<TransactionEntity> buys, TransactionEntity sell) {
        List<BuyLot> lots = tradeMatchingService.buildLotsFromBuys(buys);
        return tradeMatchingService.matchSellToLots(tradeMatchingService.toSellRequest(sell), lots).stream()
                .map(tradeMatchingService::toTradeOutcomeEntity)
                .map(row -> Acquisition.stored(row.getAcquisitionMode(), row.getAcquisitionCorporateAction()))
                .toList();
    }

    @Test
    void toTradeOutcomeEntity_whenTheSellSpansABonusAndAPurchase_recordsEachLotsAcquisition() {
        // Given -- 10 bonus shares, then 10 bought; 15 sold, FIFO takes the bonus lot first (W-088f)
        List<TransactionEntity> buys = List.of(
                infy("buy-1", LocalDate.of(2020, 1, 10), 10, 0, CorporateActionType.BONUS),
                infy("buy-2", LocalDate.of(2021, 1, 10), 10, 100, null));

        // When
        List<Acquisition> acquisitions = acquisitionsOf(buys,
                infy("sell-1", LocalDate.of(2025, 6, 10), 15, 200, null));

        // Then
        assertEquals(List.of(Acquisition.of(CorporateActionType.BONUS), Acquisition.of(null)), acquisitions);
    }

    @Test
    void toTradeOutcomeEntity_whenSameDayBuysOfTwoModesMerge_recordsTheLotAsUnrecorded() {
        // Given -- a bonus and a purchase on one day become one lot, which is neither
        List<TransactionEntity> buys = List.of(
                infy("buy-1", LocalDate.of(2020, 1, 10), 10, 0, CorporateActionType.BONUS),
                infy("buy-2", LocalDate.of(2020, 1, 10), 10, 100, null));

        // When
        List<Acquisition> acquisitions = acquisitionsOf(buys,
                infy("sell-1", LocalDate.of(2025, 6, 10), 5, 200, null));

        // Then
        assertEquals(List.of(Acquisition.UNRECORDED), acquisitions);
    }

    @Test
    void toTradeOutcomeEntity_whenTheLotAndTheSaleWereTradedApart_recordsEachSidesVenue() {
        // Given -- one buy on BSE for delivery; the sale on NSE intraday (D-295)
        TransactionEntity buy = infy("buy-1", LocalDate.of(2021, 1, 10), 10, 100, null);
        buy.setExchangeName("BSE");
        buy.setSegment(TradeSegment.DELIVERY);
        TransactionEntity sell = infy("sell-1", LocalDate.of(2025, 1, 10), 10, 150, null);
        sell.setExchangeName("NSE");
        sell.setSegment(TradeSegment.INTRADAY);

        // When
        TradeOutcomeEntity row = rowsOf(List.of(buy), sell).getFirst();

        // Then
        assertEquals("BSE", row.getBuyExchangeName());
        assertEquals(TradeSegment.DELIVERY, row.getBuySegment());
        assertEquals("NSE", row.getSellExchangeName());
        assertEquals(TradeSegment.INTRADAY, row.getSellSegment());
    }

    @Test
    void toTradeOutcomeEntity_whenSameDayBuysWereOnDifferentExchanges_recordsNoExchangeForTheLot() {
        // Given -- two same-day buys merged into one lot, one on NSE and one on BSE, both delivery
        TransactionEntity first = infy("buy-1", LocalDate.of(2021, 1, 10), 5, 100, null);
        first.setExchangeName("NSE");
        TransactionEntity second = infy("buy-2", LocalDate.of(2021, 1, 10), 5, 100, null);
        second.setExchangeName("BSE");
        TransactionEntity sell = infy("sell-1", LocalDate.of(2025, 1, 10), 10, 150, null);

        // When
        TradeOutcomeEntity row = rowsOf(List.of(first, second), sell).getFirst();

        // Then -- the exchange they disagree on is not recorded; the segment they share is
        assertNull(row.getBuyExchangeName());
        assertEquals(TradeSegment.DELIVERY, row.getBuySegment());
    }

    private List<TradeOutcomeEntity> rowsOf(List<TransactionEntity> buys, TransactionEntity sell) {
        List<BuyLot> lots = tradeMatchingService.buildLotsFromBuys(buys);
        return tradeMatchingService.matchSellToLots(tradeMatchingService.toSellRequest(sell), lots).stream()
                .map(tradeMatchingService::toTradeOutcomeEntity)
                .toList();
    }
}
