package com.thiru.wealthlens.portfolio.service;

import static com.thiru.wealthlens.testsupport.MoneyAssert.assertMoney;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeFlow;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The replay's lot rules, each expected figure worked by hand from the trades (W-283a2, D-582). */
@Tag("unit")
class LotLedgerTest {

    private static final LocalDate JAN_5 = LocalDate.of(2026, 1, 5);
    private static final LocalDate FEB_10 = LocalDate.of(2026, 2, 10);

    private final LotLedger ledger = new LotLedger((action, lots) -> lots);

    @Test
    void sell_whenTheDesignsWorkedExample_leavesNineAndFive() {
        // Given -- assets-rebuild.md's example: buy 10 @ 100 (5 Jan), 6 @ 110 (20 Jan), sell 4 (1 Feb)
        ledger.buy(trade("a", TransactionType.BUY, 10, 100, JAN_5), TradeFlow.V2, OptionalDouble.empty());
        ledger.buy(trade("b", TransactionType.BUY, 6, 110, LocalDate.of(2026, 1, 20)), TradeFlow.V2,
                OptionalDouble.empty());
        ledger.sell(trade("s1", TransactionType.SELL, 4, 130, LocalDate.of(2026, 2, 1)), TradeFlow.V2);

        // When -- split 2:1 recorded 10 Feb, then buy 5 @ 60 on 10 Feb, then sell 15 on 15 Feb
        ledger.split(split(FEB_10), BrokerName.ZERODHA);
        ledger.buy(trade("c", TransactionType.BUY, 5, 60, FEB_10), TradeFlow.V2, OptionalDouble.empty());
        ledger.sell(trade("s2", TransactionType.SELL, 15, 70, LocalDate.of(2026, 2, 15)), TradeFlow.V2);

        // Then -- A 6 → 12 @ 50, B 6 → 12 @ 55; C (dated R) not split; 15 sold: A 12, B 3 → B 9 @ 55, C 5 @ 60
        List<AssetEntity> lots = ledger.lots();
        assertEquals(2, lots.size());
        assertEquals(List.of("b"), lots.get(0).getBuyTransactionIds());
        assertEquals(9.0, lots.get(0).getQuantity(), 1e-9);
        assertMoney(55.0, lots.get(0).getPrice());
        assertEquals(List.of("c"), lots.get(1).getBuyTransactionIds());
        assertEquals(5.0, lots.get(1).getQuantity(), 1e-9);
        assertMoney(60.0, lots.get(1).getPrice());
    }

    @Test
    void buy_whenTwoV1BuysShareADay_mergesThemAtTheAveragePrice() {
        // When -- 10 @ 100 with ₹10 charges, then 10 @ 120 with ₹15, both V1 on 5 Jan
        TransactionEntity first = trade("a", TransactionType.BUY, 10, 100, JAN_5);
        first.setBrokerCharges(10);
        TransactionEntity second = trade("b", TransactionType.BUY, 10, 120, JAN_5);
        second.setBrokerCharges(15);
        ledger.buy(first, TradeFlow.V1, OptionalDouble.empty());
        ledger.buy(second, TradeFlow.V1, OptionalDouble.empty());

        // Then -- one lot of 20 @ (1000 + 1200) / 20 = 110, charges 10 + 15 = 25, two order times
        AssetEntity lot = ledger.lots().getFirst();
        assertEquals(1, ledger.lots().size());
        assertEquals(20.0, lot.getQuantity(), 1e-9);
        assertMoney(110.0, lot.getPrice());
        assertMoney(25.0, lot.getBrokerCharges());
        assertEquals(List.of("a", "b"), lot.getBuyTransactionIds());
        assertEquals(2, lot.getOrderTimeQuantities().size());
    }

    @Test
    void buy_whenV2_givesEachBuyItsOwnLotWithTheComputedCharge() {
        // When -- two V2 buys on one day; the first priced at ₹23.60
        ledger.buy(trade("a", TransactionType.BUY, 10, 100, JAN_5), TradeFlow.V2, OptionalDouble.of(23.60));
        ledger.buy(trade("b", TransactionType.BUY, 10, 120, JAN_5), TradeFlow.V2, OptionalDouble.empty());

        // Then
        assertEquals(2, ledger.lots().size());
        assertMoney(23.60, ledger.lots().getFirst().getBrokerCharges());
    }

    @Test
    void sell_whenV1_recordsTheSoldShareOfTheLotsCharges() {
        // Given -- 3 units carrying ₹30
        TransactionEntity buy = trade("a", TransactionType.BUY, 3, 100, JAN_5);
        buy.setBrokerCharges(30);
        ledger.buy(buy, TradeFlow.V1, OptionalDouble.empty());

        // When -- 1 sold through V1
        ledger.sell(trade("s", TransactionType.SELL, 1, 120, FEB_10), TradeFlow.V1);

        // Then -- ₹30 × 1 / 3 = ₹10 allocated; the charge itself stays whole
        AssetEntity lot = ledger.lots().getFirst();
        assertMoney(10.0, lot.getAllocatedBuyCharges());
        assertMoney(30.0, lot.getBrokerCharges());
        assertEquals(2.0, lot.getQuantity(), 1e-9);
    }

    @Test
    void sell_whenV2_leavesTheLotsChargesUnallocated() {
        // Given
        TransactionEntity buy = trade("a", TransactionType.BUY, 3, 100, JAN_5);
        buy.setBrokerCharges(30);
        ledger.buy(buy, TradeFlow.V2, OptionalDouble.empty());

        // When
        ledger.sell(trade("s", TransactionType.SELL, 1, 120, FEB_10), TradeFlow.V2);

        // Then -- V2 works its share out from the original quantity instead (allocateSaleV2)
        assertMoney(0.0, ledger.lots().getFirst().getAllocatedBuyCharges());
    }

    @Test
    void sell_whenTheLotsHoldTooLittle_isRefusedNamingTheShortfall() {
        // Given -- 5 held
        ledger.buy(trade("a", TransactionType.BUY, 5, 100, JAN_5), TradeFlow.V2, OptionalDouble.empty());

        // When / Then -- 6 sold: 1 short
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> ledger.sell(trade("s", TransactionType.SELL, 6, 120, FEB_10), TradeFlow.V2));
        assertTrue(refused.getMessage().contains("Sale s") && refused.getMessage().contains("1.0 short"),
                refused.getMessage());
    }

    @Test
    void allot_whenABonusFollowsAHolding_copiesTheFirstLotAtPriceZero() {
        // Given -- 10 @ 100 carrying ₹20, bought 5 Jan; a 1:1 bonus recorded 15 Jan, allotted 17 Jan
        TransactionEntity buy = trade("a", TransactionType.BUY, 10, 100, JAN_5);
        buy.setBrokerCharges(20);
        ledger.buy(buy, TradeFlow.V2, OptionalDouble.empty());
        CorporateActionEntity bonus = action(CorporateActionType.BONUS, LocalDate.of(2026, 1, 15));

        // When
        boolean copied = ledger.allot(trade("bonus", TransactionType.BUY, 10, 0, LocalDate.of(2026, 1, 17)), bonus);

        // Then -- 10 @ 0 dated by the allotment; the copy keeps the first lot's ₹20, as live does
        AssetEntity allotted = ledger.lots().get(1);
        assertTrue(copied);
        assertEquals(10.0, allotted.getQuantity(), 1e-9);
        assertMoney(0.0, allotted.getPrice());
        assertEquals(LocalDate.of(2026, 1, 17), allotted.getTransactionDate());
        assertEquals(CorporateActionType.BONUS, allotted.getCorporateActionType());
        assertEquals(List.of("bonus"), allotted.getBuyTransactionIds());
        assertMoney(20.0, allotted.getBrokerCharges());
    }

    @Test
    void demerge_whenTheStepAddsALot_addsItOnceAfterTheParents() {
        // Given -- a demerger step that renames nothing and adds one lot of 5
        AssetEntity child = new AssetEntity();
        child.setQuantity(5.0);
        LotLedger demerging = new LotLedger((action, lots) -> {
            List<AssetEntity> all = new ArrayList<>(lots);
            all.add(child);
            return all;
        });
        demerging.buy(trade("a", TransactionType.BUY, 10, 100, JAN_5), TradeFlow.V2, OptionalDouble.empty());

        // When
        demerging.demerge(action(CorporateActionType.DEMERGER, FEB_10), BrokerName.ZERODHA);

        // Then
        assertEquals(2, demerging.lots().size());
        assertEquals(5.0, demerging.lots().get(1).getQuantity(), 1e-9);
    }

    private static TransactionEntity trade(String id, TransactionType type, double quantity, double price,
                                           LocalDate date) {
        TransactionEntity trade = new TransactionEntity();
        trade.setId(id);
        trade.setStockCode("INFY");
        trade.setBrokerName(BrokerName.ZERODHA);
        trade.setAccountHolder("Self");
        trade.setTransactionType(type);
        trade.setQuantity(quantity);
        trade.setPrice(price);
        trade.setTransactionDate(date);
        return trade;
    }

    private static CorporateActionEntity split(LocalDate recordDate) {
        CorporateActionEntity split = action(CorporateActionType.STOCK_SPLIT, recordDate);
        split.setRatio("2:1");
        return split;
    }

    private static CorporateActionEntity action(CorporateActionType type, LocalDate recordDate) {
        CorporateActionEntity action = new CorporateActionEntity();
        action.setType(type);
        action.setStockCode("INFY");
        action.setRecordDate(recordDate);
        return action;
    }
}
