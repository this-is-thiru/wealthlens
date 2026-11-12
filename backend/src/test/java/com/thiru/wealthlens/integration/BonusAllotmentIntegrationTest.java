package com.thiru.wealthlens.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.thiru.wealthlens.corporate.dto.CorporateActionPerformDto;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.service.CorporateActionService;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A bonus allotment carries what a bonus is — a quantity, an allotment date and nil cost — and nothing copied from the
 * holding it was computed from (W-291, D-589). Before, the bonus lot kept the first lot's charges, allocated charges,
 * sales and actions, so a sale of bonus shares charged buy charges paid once already; and the bonus transaction kept
 * the first buy's charges and submission identifiers, two of them under unique indexes.
 */
class BonusAllotmentIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "bonus-owner@example.com";

    @Autowired
    private CorporateActionService corporateActionService;

    private AssetEntity held;
    private TransactionEntity buy;

    @BeforeEach
    void holdingAndABonus() {
        // A 1:1 bonus on RELIANCE recorded 15 Jan 2024, allotted 17 Jan; 100 held with UPSTOX, bought 10 Jan
        CorporateActionEntity bonus = new CorporateActionEntity();
        bonus.setStockCode("RELIANCE");
        bonus.setStockName("Reliance Industries");
        bonus.setType(CorporateActionType.BONUS);
        bonus.setAssetType(AssetType.EQUITY);
        bonus.setRecordDate(LocalDate.of(2024, 1, 15));
        bonus.setExDate(LocalDate.of(2024, 1, 20));
        bonus.setAllotmentDate(LocalDate.of(2024, 1, 17));
        bonus.setRatio("1:1");
        bonus.setPriority(1);
        mongoTemplate.save(bonus);

        held = new AssetEntity();
        held.setEmail(EMAIL);
        held.setStockCode("RELIANCE");
        held.setStockName("Reliance Industries");
        held.setBrokerName(BrokerName.UPSTOX);
        held.setAssetType(AssetType.EQUITY);
        held.setQuantity(100.0);
        held.setPrice(2500.0);
        held.setTransactionDate(LocalDate.of(2024, 1, 10));
        held.setTransactionType(TransactionType.BUY);

        buy = new TransactionEntity();
        buy.setEmail(EMAIL);
        buy.setStockCode("RELIANCE");
        buy.setStockName("Reliance Industries");
        buy.setBrokerName(BrokerName.UPSTOX);
        buy.setAssetType(AssetType.EQUITY);
        buy.setQuantity(100.0);
        buy.setPrice(2500.0);
        buy.setTransactionType(TransactionType.BUY);
        buy.setTransactionDate(LocalDate.of(2024, 1, 10));
    }

    @Test
    void performPendingCorporateActions_whenTheHoldingCarriesChargesAndASale_allotsABonusCarryingNeither() {
        // Given -- the held lot carries ₹20 + ₹5 misc, ₹8 already allocated, a sale and an earlier action; its buy
        // carries ₹20 and an order id
        held.setBrokerCharges(20);
        held.setMiscCharges(5);
        held.setAllocatedBuyCharges(8);
        held.getSellTransactionIds().add("sell-0");
        held.getCorporateActions().add(new CorporateActionEntity());
        mongoTemplate.save(held);
        buy.setBrokerCharges(20);
        buy.setOrderId("order-1");
        mongoTemplate.save(buy);

        // When
        perform();

        // Then -- bonus shares cost nothing and were never ordered: nil charges, no sales, only the bonus as action
        AssetEntity bonusLot = mongoTemplate.findAll(AssetEntity.class).stream()
                .filter(asset -> asset.getCorporateActionType() == CorporateActionType.BONUS).findFirst().orElseThrow();
        assertEquals(100.0, bonusLot.getQuantity());
        assertEquals(0.0, bonusLot.getBrokerCharges());
        assertEquals(0.0, bonusLot.getMiscCharges());
        assertEquals(0.0, bonusLot.getAllocatedBuyCharges());
        assertEquals(List.of(), bonusLot.getSellTransactionIds());
        assertEquals(List.of(CorporateActionType.BONUS),
                bonusLot.getCorporateActions().stream().map(CorporateActionEntity::getType).toList());
        TransactionEntity bonusBuy = bonusTransaction();
        assertEquals(0.0, bonusBuy.getBrokerCharges());
        assertNull(bonusBuy.getOrderId());
    }

    @Test
    void performPendingCorporateActions_whenTheFirstBuyCarriedAClientKey_stillAllotsTheBonus() {
        // Given -- the buy was submitted with an idempotency key, a unique sparse index (TransactionEntity:126)
        mongoTemplate.save(held);
        buy.setIdempotencyKey("client-key-1");
        mongoTemplate.save(buy);

        // When
        perform();

        // Then -- the bonus is allotted, and its transaction carries no key of its own
        assertNull(bonusTransaction().getIdempotencyKey());
    }

    private void perform() {
        corporateActionService.performPendingCorporateActions(EMAIL,
                new CorporateActionPerformDto("JANUARY", 2024, BrokerName.UPSTOX), false);
    }

    private TransactionEntity bonusTransaction() {
        return mongoTemplate.findAll(TransactionEntity.class).stream()
                .filter(txn -> txn.getCorporateActionType() == CorporateActionType.BONUS).findFirst().orElseThrow();
    }
}
