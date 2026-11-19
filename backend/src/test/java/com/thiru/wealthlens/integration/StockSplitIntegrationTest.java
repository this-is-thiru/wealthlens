package com.thiru.wealthlens.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thiru.wealthlens.corporate.dto.CorporateActionDto;
import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.corporate.entity.LastlyPerformedCorporateAction;
import com.thiru.wealthlens.corporate.service.CorporateActionService;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TradeOutcomeEntity;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.testsupport.MoneyAssert;
import io.restassured.RestAssured;
import java.net.URI;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

/**
 * A stock split changes the lots it reaches and nothing else, and a malformed one is refused (W-213, W-216, D-438). Every figure
 * is the table in {@code plans/W-213.md}, by hand.
 */
class StockSplitIntegrationTest extends AbstractIntegrationTest {

    private static final String EMAIL = "splitter@example.com";

    private static final LocalDate RECORD_DATE = LocalDate.of(2024, 1, 15);

    @Autowired
    private CorporateActionService corporateActionService;

    private AssetEntity lot(BrokerName broker, double quantity, double price, LocalDate bought) {
        AssetEntity lot = new AssetEntity();
        lot.setEmail(EMAIL);
        lot.setStockCode("RELIANCE");
        lot.setStockName("Reliance Industries");
        lot.setBrokerName(broker);
        lot.setAssetType(AssetType.EQUITY);
        lot.setExchangeName("NSE");
        lot.setAccountType(AccountType.SELF);
        lot.setAccountHolder("Self");
        lot.setQuantity(quantity);
        lot.setPrice(price);
        lot.setBrokerCharges(20);
        lot.setTransactionDate(bought);
        return mongoTemplate.save(lot);
    }

    private static CorporateActionEntity split(String ratio) {
        CorporateActionEntity action = new CorporateActionEntity();
        action.setStockCode("RELIANCE");
        action.setStockName("Reliance Industries");
        action.setType(CorporateActionType.STOCK_SPLIT);
        action.setAssetType(AssetType.EQUITY);
        action.setRecordDate(RECORD_DATE);
        action.setExDate(RECORD_DATE.plusDays(5));
        action.setRatio(ratio);
        action.setPriority(1);
        return action;
    }

    private AssetEntity reread(AssetEntity lot) {
        return mongoTemplate.findById(lot.getId(), AssetEntity.class);
    }

    private long performedRows() {
        return mongoTemplate.count(new Query(Criteria.where("email").is(EMAIL)), LastlyPerformedCorporateAction.class);
    }

    @Test
    void processStockSplit_whenTwoToOne_splitsThisBrokersLotsAndKeepsTheirCost() {
        // Given
        AssetEntity first = lot(BrokerName.ZERODHA, 100, 1000, LocalDate.of(2024, 1, 10));
        AssetEntity second = lot(BrokerName.ZERODHA, 30, 900, LocalDate.of(2023, 6, 1));

        // When
        corporateActionService.processStockSplit(EMAIL, split("2:1"), BrokerName.ZERODHA);

        // Then — 100 @ 1,000 → 200 @ 500; 30 @ 900 → 60 @ 450; date, charges and cost as they were
        AssetEntity after = reread(first);
        assertEquals(200.0, after.getQuantity());
        MoneyAssert.assertMoney(500, after.getPrice());
        MoneyAssert.assertMoney(20, after.getBrokerCharges());
        assertEquals(LocalDate.of(2024, 1, 10), after.getTransactionDate());
        assertEquals(1, after.getCorporateActions().size());
        assertEquals(60.0, reread(second).getQuantity());
        MoneyAssert.assertMoney(450, reread(second).getPrice());
        assertEquals(1, performedRows());
    }

    @Test
    void processStockSplit_whenAnotherBrokerHoldsALot_leavesIt() {
        // Given
        lot(BrokerName.ZERODHA, 100, 1000, LocalDate.of(2024, 1, 10));
        AssetEntity other = lot(BrokerName.UPSTOX, 40, 800, LocalDate.of(2024, 1, 10));

        // When
        corporateActionService.processStockSplit(EMAIL, split("2:1"), BrokerName.ZERODHA);

        // Then
        assertEquals(40.0, reread(other).getQuantity());
        MoneyAssert.assertMoney(800, reread(other).getPrice());
    }

    @Test
    void processStockSplit_whenALotIsBoughtOnOrAfterTheRecordDate_leavesIt() {
        // Given — the existing query reads strictly before the record date
        lot(BrokerName.ZERODHA, 100, 1000, LocalDate.of(2024, 1, 10));
        AssetEntity onTheDay = lot(BrokerName.ZERODHA, 10, 1000, RECORD_DATE);
        AssetEntity after = lot(BrokerName.ZERODHA, 10, 1000, RECORD_DATE.plusDays(1));

        // When
        corporateActionService.processStockSplit(EMAIL, split("2:1"), BrokerName.ZERODHA);

        // Then
        assertEquals(10.0, reread(onTheDay).getQuantity());
        assertEquals(10.0, reread(after).getQuantity());
    }

    @Test
    void processStockSplit_whenAStoredRatioIsMalformed_refusesAndWritesNoPerformedRow() {
        // Given
        AssetEntity held = lot(BrokerName.ZERODHA, 100, 1000, LocalDate.of(2024, 1, 10));

        // When / Then
        assertThrows(IllegalArgumentException.class,
                () -> corporateActionService.processStockSplit(EMAIL, split("two:one"), BrokerName.ZERODHA));
        assertEquals(0, performedRows());
        assertEquals(100.0, reread(held).getQuantity());
    }

    @Test
    void performPendingCorporateAction_whenPerformedTwice_appliesOnce() {
        // Given
        AssetEntity held = lot(BrokerName.ZERODHA, 100, 1000, LocalDate.of(2024, 1, 10));
        CorporateActionEntity action = split("2:1");

        // When
        corporateActionService.performPendingCorporateAction(EMAIL, action, BrokerName.ZERODHA);
        corporateActionService.performPendingCorporateAction(EMAIL, action, BrokerName.ZERODHA);

        // Then — 200, not 400
        assertEquals(200.0, reread(held).getQuantity());
    }

    @Test
    void addTransactionV2_whenSellingAfterASplit_costsTheSoldPartAtTheSplitPrice() {
        // Given
        lot(BrokerName.ZERODHA, 100, 1000, LocalDate.of(2024, 1, 10));
        corporateActionService.performPendingCorporateAction(EMAIL, split("2:1"), BrokerName.ZERODHA);
        AssetRequest sale = new AssetRequest();
        sale.setEmail(EMAIL);
        sale.setStockCode("RELIANCE");
        sale.setStockName("Reliance Industries");
        sale.setExchangeName("NSE");
        sale.setBrokerName(BrokerName.ZERODHA);
        sale.setAssetType(AssetType.EQUITY);
        sale.setPrice(700.0);
        sale.setQuantity(50.0);
        sale.setTransactionType(TransactionType.SELL);
        sale.setAccountType(AccountType.SELF);
        sale.setAccountHolder("Self");
        sale.setTransactionDate(RECORD_DATE.plusDays(30));
        sale.setBrokerCharges(0.0);
        sale.setMiscCharges(0.0);
        sale.setTimezoneId("Asia/Kolkata");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(EMAIL));
        headers.setContentType(MediaType.APPLICATION_JSON);

        // When
        new RestTemplate().exchange(URI.create("http://localhost:" + RestAssured.port + "/portfolio/user/" + EMAIL
                + "/transaction/v2"), HttpMethod.POST, new HttpEntity<>(sale, headers), String.class);

        // Then — 50 × 500 = 25,000, and the lot's own date, so the holding period starts where it did
        TradeOutcomeEntity row = mongoTemplate.findOne(new Query(Criteria.where("email").is(EMAIL)),
                TradeOutcomeEntity.class);
        assertEquals(50.0, row.getBuyQuantity());
        MoneyAssert.assertMoney(25000, row.getCorporateActionAdjustedBuyPrice() * row.getBuyQuantity());
        assertEquals(LocalDate.of(2024, 1, 10), row.getBuyDate());
    }

    @Test
    void addCorporateAction_whenASplitsRatioIsMalformed_refusesAndStoresNothing() {
        // Given
        CorporateActionDto dto = new CorporateActionDto();
        dto.setStockCode("RELIANCE");
        dto.setStockName("Reliance Industries");
        dto.setType(CorporateActionType.STOCK_SPLIT);
        dto.setAssetType(AssetType.EQUITY);
        dto.setRecordDate(RECORD_DATE);
        dto.setExDate(RECORD_DATE.plusDays(5));
        dto.setRatio("2:0");

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> corporateActionService.addCorporateAction(dto));
        assertEquals(0, mongoTemplate.count(new Query(), CorporateActionEntity.class));
    }
}
