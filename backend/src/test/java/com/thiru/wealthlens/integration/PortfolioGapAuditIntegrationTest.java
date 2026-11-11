package com.thiru.wealthlens.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thiru.wealthlens.corporate.dto.enums.CorporateActionType;
import com.thiru.wealthlens.corporate.entity.CorporateActionEntity;
import com.thiru.wealthlens.portfolio.dto.AssetRequest;
import com.thiru.wealthlens.portfolio.dto.AssetResponse;
import com.thiru.wealthlens.portfolio.dto.TransactionResponse;
import com.thiru.wealthlens.portfolio.dto.enums.AssetType;
import com.thiru.wealthlens.portfolio.dto.enums.BrokerName;
import com.thiru.wealthlens.portfolio.dto.enums.TradeFlow;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionStatus;
import com.thiru.wealthlens.portfolio.dto.enums.TransactionType;
import com.thiru.wealthlens.portfolio.entity.AssetEntity;
import com.thiru.wealthlens.portfolio.entity.TransactionEntity;
import com.thiru.wealthlens.shared.dto.enums.AccountType;
import com.thiru.wealthlens.shared.util.transaction.ExcelHeaders;
import com.thiru.wealthlens.testsupport.MoneyAssert;
import io.restassured.RestAssured;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * W-264a's scenarios: the portfolio module driven through its HTTP API, each expected answer worked by hand
 * from the scenario's own inputs before the run (D-535 C1a, C2a). Only scenarios whose expected answer the
 * code already gives are kept here; each one that exposed a gap is quoted in the audit report instead.
 */
class PortfolioGapAuditIntegrationTest extends AbstractIntegrationTest {

    private static final String OWNER = "audit-owner@example.com";
    private static final String OTHER = "audit-other@example.com";
    private static final String STOCK = "INFY";
    private static final LocalDate JAN_5 = LocalDate.of(2026, 1, 5);
    private static final LocalDate FEB_5 = LocalDate.of(2026, 2, 5);
    private static final LocalDate MAR_5 = LocalDate.of(2026, 3, 5);

    private final RestTemplate rest = lenientRestTemplate();

    @Test
    void addTransactionV2_whenSellTakesFifoAcrossTwoLots_leavesTheLaterLotPartlyHeld() {
        // Given — buy 10 @ 100 on 5 Jan, 10 @ 120 on 5 Feb
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 120.0, FEB_5));

        // When — sell 15 @ 150 on 5 Mar: FIFO takes all 10 of January, then 5 of February
        ResponseEntity<String> sold = postV2(OWNER, trade(TransactionType.SELL, 15.0, 150.0, MAR_5));

        // Then — one lot left: 5 @ 120 dated 5 Feb; the January lot is gone
        assertEquals(200, sold.getStatusCode().value(), sold.getBody());
        List<AssetEntity> lots = lotsOf(OWNER);
        assertEquals(1, lots.size());
        assertEquals(FEB_5, lots.getFirst().getTransactionDate());
        assertEquals(5.0, lots.getFirst().getQuantity());
        MoneyAssert.assertMoney(120.0, lots.getFirst().getPrice());
    }

    @Test
    void getAllStocks_afterAFifoSale_showsTheRemainingQuantityAtItsOwnPrice() {
        // Given — the same three trades: 5 left, all from the ₹120 lot
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 120.0, FEB_5));
        postV2(OWNER, trade(TransactionType.SELL, 15.0, 150.0, MAR_5));

        // When
        AssetResponse[] rows = get(OWNER, "/portfolio/user/" + OWNER + "/stocks/all", AssetResponse[].class).getBody();

        // Then — one row: quantity 5, average price 5 × 120 / 5 = ₹120
        assertEquals(1, rows.length);
        assertEquals(5.0, rows[0].getQuantity());
        MoneyAssert.assertMoney(120.0, rows[0].getPrice());
    }

    @Test
    void addTransactionV2_whenSaleIsDatedBeforeTheOnlyLot_answers400AndChangesNothing() {
        // Given — the only lot is bought 5 Feb
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, FEB_5));

        // When — a sale dated 5 Jan, before it
        ResponseEntity<String> sold = postV2(OWNER, trade(TransactionType.SELL, 5.0, 150.0, JAN_5));

        // Then — no lot is eligible on 5 Jan, so 400; the lot keeps 10
        assertEquals(400, sold.getStatusCode().value(), sold.getBody());
        assertEquals(10.0, lotsOf(OWNER).getFirst().getQuantity());
    }

    @Test
    void addTransactionV2_whenSellingMoreThanHeld_answers400AndChangesNothing() {
        // Given
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));

        // When — 11 asked of 10 held
        ResponseEntity<String> sold = postV2(OWNER, trade(TransactionType.SELL, 11.0, 150.0, FEB_5));

        // Then
        assertEquals(400, sold.getStatusCode().value(), sold.getBody());
        assertEquals(10.0, lotsOf(OWNER).getFirst().getQuantity());
    }

    @Test
    void addTransactionV2_whenTheSameTradeIsPostedTwiceWithinTheWindow_appliesItOnce() {
        // Given
        Map<String, Object> buy = trade(TransactionType.BUY, 10.0, 100.0, JAN_5);
        postV2(OWNER, buy);

        // When — the same body again, seconds later, with no idempotency key
        ResponseEntity<String> retry = postV2(OWNER, buy);

        // Then — treated as a retry: one transaction, one lot of 10
        assertEquals(200, retry.getStatusCode().value());
        assertTrue(retry.getBody().contains("repeat"), retry.getBody());
        assertEquals(1, transactionsOf(OWNER).size());
        assertEquals(10.0, lotsOf(OWNER).getFirst().getQuantity());
    }

    @Test
    void uploadTransactions_whenTheFileIsNotAWorkbook_answers400() {
        // When
        ResponseEntity<String> uploaded = upload(OWNER, "not a workbook".getBytes(), "Q1");

        // Then
        assertEquals(400, uploaded.getStatusCode().value(), uploaded.getBody());
    }

    @Test
    void getAllStocks_whenTheTokenIsAnotherUsers_isRefused() {
        // Given — OTHER holds a lot
        postV2(OTHER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));

        // When — OWNER's token on OTHER's path
        ResponseEntity<String> answer = rest.exchange(URI.create(url("/portfolio/user/" + OTHER + "/stocks/all")),
                HttpMethod.GET, new HttpEntity<>(headers(OWNER)), String.class);

        // Then — refused (401 or 403), nothing of OTHER's shown
        assertTrue(answer.getStatusCode().value() == 401 || answer.getStatusCode().value() == 403,
                () -> answer.getStatusCode() + " " + answer.getBody());
    }

    @Test
    void getUserTransaction_afterThreeTrades_listsEachOnceForTheCallerOnly() {
        // Given — buy, buy, sell for OWNER; one buy for OTHER
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 120.0, FEB_5));
        postV2(OWNER, trade(TransactionType.SELL, 15.0, 150.0, MAR_5));
        postV2(OTHER, trade(TransactionType.BUY, 7.0, 100.0, JAN_5));

        // When
        TransactionResponse[] listed = get(OWNER, "/transactions/user/" + OWNER, TransactionResponse[].class).getBody();

        // Then — three rows, three distinct ids, all OWNER's
        assertEquals(3, listed.length);
        assertEquals(3, Arrays.stream(listed).map(TransactionResponse::getTransactionId).distinct().count());
        assertTrue(Arrays.stream(listed).allMatch(row -> OWNER.equals(row.getEmail())));
    }

    @Test
    void downloadPortfolio_whenAssetsAreAsked_matchesTheHoldingsView() throws IOException {
        // Given — 5 left at ₹120 after the FIFO sale
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 120.0, FEB_5));
        postV2(OWNER, trade(TransactionType.SELL, 15.0, 150.0, MAR_5));

        // When
        ResponseEntity<byte[]> file = rest.exchange(URI.create(url("/portfolio/user/" + OWNER + "/stocks/download")),
                HttpMethod.POST, new HttpEntity<>(Map.of("entityName", "assets"), headers(OWNER)), byte[].class);

        // Then — one data row whose QUANTITY is 5, as /stocks/all says
        assertEquals(200, file.getStatusCode().value());
        try (Workbook book = new XSSFWorkbook(new ByteArrayInputStream(file.getBody()))) {
            Sheet sheet = book.getSheetAt(0);
            assertEquals(1, sheet.getLastRowNum());
            int quantityColumn = columnOf(sheet.getRow(0), ExcelHeaders.QUANTITY);
            assertEquals(5.0, sheet.getRow(1).getCell(quantityColumn).getNumericCellValue());
        }
    }

    @Test
    void downloadPortfolio_whenAColumnIsUnknown_answers400() {
        // When
        ResponseEntity<String> answer = post(OWNER, "/portfolio/user/" + OWNER + "/stocks/download",
                Map.of("entityName", "assets", "selectedColumns", List.of("quantity", "notAColumn")));

        // Then
        assertEquals(400, answer.getStatusCode().value(), answer.getBody());
    }

    @Test
    void addTransactionV2_whenTheSameTradeComesAfterTheWindow_appliesBoth() {
        // Given — a buy of 10 recorded two minutes ago (the window is 60 s)
        Map<String, Object> buy = trade(TransactionType.BUY, 10.0, 100.0, JAN_5);
        postV2(OWNER, buy);
        TransactionEntity first = transactionsOf(OWNER).getFirst();
        first.setSubmittedAt(first.getSubmittedAt().minusMinutes(2));
        mongoTemplate.save(first, "transactions");

        // When — the same body again
        postV2(OWNER, buy);

        // Then — a second real trade: two transactions, 20 held
        assertEquals(2, transactionsOf(OWNER).size());
        assertEquals(20.0, lotsOf(OWNER).stream().mapToDouble(AssetEntity::getQuantity).sum());
    }

    @Test
    void addTransactionV2_whenTwoTradesDifferOnlyInPrice_appliesBoth() {
        // Given
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));

        // When — the same trade at ₹101, within the window
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 101.0, JAN_5));

        // Then — not a retry: 20 held
        assertEquals(20.0, lotsOf(OWNER).stream().mapToDouble(AssetEntity::getQuantity).sum());
    }

    @Test
    void addTransactionV2_whenIdempotencyKeysDecide_appliesOncePerKey() {
        // Given — identical trades under keys k1, k1, k2
        Map<String, Object> first = trade(TransactionType.BUY, 10.0, 100.0, JAN_5);
        first.put("idempotencyKey", "k1");
        Map<String, Object> second = new HashMap<>(first);
        second.put("idempotencyKey", "k2");

        // When
        postV2(OWNER, first);
        postV2(OWNER, first);
        postV2(OWNER, second);

        // Then — k1 once, k2 once: 20 held
        assertEquals(2, transactionsOf(OWNER).size());
        assertEquals(20.0, lotsOf(OWNER).stream().mapToDouble(AssetEntity::getQuantity).sum());
    }

    @Test
    void clearAll_whenTwoUsersHoldRecords_clearsOnlyTheCallers() {
        // Given
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
        postV2(OTHER, trade(TransactionType.BUY, 7.0, 100.0, JAN_5));

        // When
        ResponseEntity<String> cleared = post(OWNER, "/portfolio/user/" + OWNER + "/clear/all", null);

        // Then — OWNER has nothing; OTHER keeps the lot of 7 and its transaction
        assertEquals(200, cleared.getStatusCode().value(), cleared.getBody());
        assertEquals(0, lotsOf(OWNER).size());
        assertEquals(0, transactionsOf(OWNER).size());
        assertEquals(7.0, lotsOf(OTHER).getFirst().getQuantity());
        assertEquals(1, transactionsOf(OTHER).size());
    }

    @Test
    void addTransactionV2_whenTheBodyIsMalformed_answers400() {
        // Given — not JSON, a quantity that is not a number, a transaction type that does not exist
        Map<String, Object> wordQuantity = trade(TransactionType.BUY, 10.0, 100.0, JAN_5);
        wordQuantity.put("quantity", "ten");
        Map<String, Object> unknownType = trade(TransactionType.BUY, 10.0, 100.0, JAN_5);
        unknownType.put("transactionType", "HOLD");

        // When
        int notJson = post(OWNER, "/portfolio/user/" + OWNER + "/transaction/v2", "{not json").getStatusCode().value();
        int word = postV2(OWNER, wordQuantity).getStatusCode().value();
        int unknown = postV2(OWNER, unknownType).getStatusCode().value();

        // Then
        assertEquals(List.of(400, 400, 400), List.of(notJson, word, unknown));
    }

    @Test
    void getStockQuantity_whenNothingIsHeld_answers400() {
        // When
        ResponseEntity<String> answer = get(OWNER, "/portfolio/user/" + OWNER + "/stock/NOPE/all", String.class);

        // Then
        assertEquals(400, answer.getStatusCode().value(), answer.getBody());
    }

    @Test
    void searchAssets_whenAFilterNamesAFieldNotAllowed_answers400() {
        // When
        ResponseEntity<String> answer = post(OWNER, "/portfolio/user/" + OWNER + "/request2",
                Map.of("queryFilters", List.of(Map.of("filterKey", "password", "operation", "EQUALS", "value", "x"))));

        // Then
        assertEquals(400, answer.getStatusCode().value(), answer.getBody());
    }

    @Test
    void uploadTransactions_whenTheQuarterIsMissingOrUnknown_answers400() {
        // When
        int unknown = upload(OWNER, "x".getBytes(), "Q5").getStatusCode().value();
        HttpHeaders headers = headers(OWNER);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("x".getBytes()) {
            @Override
            public String getFilename() {
                return "trades.xlsx";
            }
        });
        int missing = rest.exchange(URI.create(url("/portfolio/user/" + OWNER + "/upload-transactions")), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class).getStatusCode().value();

        // Then
        assertEquals(List.of(400, 400), List.of(unknown, missing));
    }

    @Test
    void redrive_whenAnItemFailsAfterItsTransactionIsRecorded_leavesNoTransactionForIt() {
        // Given — a stored temporary sale of a stock not held (PS-6)
        TransactionEntity temp = new TransactionEntity();
        temp.setEmail(OWNER);
        temp.setStockCode(STOCK);
        temp.setBrokerName(BrokerName.ZERODHA);
        temp.setTransactionType(TransactionType.SELL);
        temp.setStatus(TransactionStatus.TEMPORARY);
        temp.setAssetRequest(asRequest(trade(TransactionType.SELL, 5.0, 150.0, JAN_5)));
        mongoTemplate.save(temp, "transactions");

        // When
        ResponseEntity<String> redriven = post(OWNER, "/temporary-transactions/user/" + OWNER + "/redrive", null);

        // Then — the item failed, and the only transaction is still the temporary one
        assertTrue(redriven.getBody().contains("Failed: 1"), redriven.getBody());
        assertEquals(1, transactionsOf(OWNER).size(), () -> "transactions: " + transactionsOf(OWNER));
    }

    @Test
    void redrive_whenOneItemFailsBesideOneThatSucceeds_keepsTheSuccess() {
        // Given — a stored temporary buy of 10 (good) and a stored temporary sale of a stock not held (fails)
        TransactionEntity good = new TransactionEntity();
        good.setEmail(OWNER);
        good.setStockCode(STOCK);
        good.setBrokerName(BrokerName.ZERODHA);
        good.setTransactionType(TransactionType.BUY);
        good.setStatus(TransactionStatus.TEMPORARY);
        good.setAssetRequest(asRequest(trade(TransactionType.BUY, 10.0, 100.0, JAN_5)));
        mongoTemplate.save(good, "transactions");
        TransactionEntity bad = new TransactionEntity();
        bad.setEmail(OWNER);
        bad.setStockCode("TCS");
        bad.setBrokerName(BrokerName.ZERODHA);
        bad.setTransactionType(TransactionType.SELL);
        bad.setStatus(TransactionStatus.TEMPORARY);
        Map<String, Object> sale = trade(TransactionType.SELL, 5.0, 150.0, FEB_5);
        sale.put("stockCode", "TCS");
        bad.setAssetRequest(asRequest(sale));
        mongoTemplate.save(bad, "transactions");

        // When
        ResponseEntity<String> redriven = post(OWNER, "/temporary-transactions/user/" + OWNER + "/redrive", null);

        // Then — reported as one succeeded, one failed; the buy's 10 are held and its temporary is PROCESSED
        assertTrue(redriven.getBody().contains("Succeeded: 1") && redriven.getBody().contains("Failed: 1"),
                redriven.getBody());
        assertEquals(10.0, lotsOf(OWNER).stream().mapToDouble(AssetEntity::getQuantity).sum(),
                () -> redriven.getBody() + " lots: " + lotsOf(OWNER));
        assertEquals(TransactionStatus.PROCESSED, mongoTemplate.findById(good.getId(), TransactionEntity.class,
                "transactions").getStatus());
    }

    @Test
    void update_whenAnOrdinaryUserCallsIt_leavesOtherUsersRecordsAlone() {
        // Given — OTHER's lot stored with no asset type (W-268, G-11)
        postV2(OTHER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
        AssetEntity lot = lotsOf(OTHER).getFirst();
        lot.setAssetType(null);
        mongoTemplate.save(lot, "assets");

        // When — OWNER, an ordinary user, calls the former backfill on OWNER's own path
        ResponseEntity<String> answer = post(OWNER, "/portfolio/user/" + OWNER + "/update", null);

        // Then — no such route, and OTHER's lot is not rewritten
        assertEquals(404, answer.getStatusCode().value(), answer.getBody());
        assertEquals(null, lotsOf(OTHER).getFirst().getAssetType());
    }

    @Test
    void allTransactions_whenAnotherUserHasTraded_listsOnlyTheCallersOwn() {
        // Given — one trade each (W-268, G-12)
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
        postV2(OTHER, trade(TransactionType.BUY, 7.0, 100.0, JAN_5));

        // When
        TransactionEntity[] listed = get(OWNER, "/portfolio/user/" + OWNER + "/all/transactions", TransactionEntity[].class)
                .getBody();

        // Then — OWNER's one trade, none of OTHER's
        assertEquals(1, listed.length);
        assertEquals(OWNER, listed[0].getEmail());
    }

    @Test
    void addTransaction_whenTenSameDayBuysArriveTogether_holdsEveryUnit() throws InterruptedException {
        // Given — ten V1 buys on 5 Jan of 1, 2, … 10 units, posted at once (PS-7): 55 units in all
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Callable<Integer>> buys = IntStream.rangeClosed(1, 10)
                .mapToObj(units -> (Callable<Integer>) () -> post(OWNER, "/portfolio/user/" + OWNER + "/transaction",
                        trade(TransactionType.BUY, units, 100.0, JAN_5)).getStatusCode().value())
                .toList();

        // When
        List<Integer> statuses = pool.invokeAll(buys).stream().map(PortfolioGapAuditIntegrationTest::done).toList();
        pool.shutdown();

        // Then — every buy answered 200 and all 55 units are held
        assertEquals(List.of(200, 200, 200, 200, 200, 200, 200, 200, 200, 200), statuses);
        assertEquals(55.0, lotsOf(OWNER).stream().mapToDouble(AssetEntity::getQuantity).sum(),
                () -> "lots: " + lotsOf(OWNER));
    }

    @Test
    void addTransactionV2_whenTenBuysArriveTogether_holdsEveryUnit() throws InterruptedException {
        // Given — ten V2 buys of 1, 2, … 10 units on ten different days in January, posted at once: 55 units
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Callable<Integer>> buys = IntStream.rangeClosed(1, 10)
                .mapToObj(units -> (Callable<Integer>) () -> postV2(OWNER,
                        trade(TransactionType.BUY, units, 100.0, JAN_5.plusDays(units))).getStatusCode().value())
                .toList();

        // When
        List<Integer> statuses = pool.invokeAll(buys).stream().map(PortfolioGapAuditIntegrationTest::done).toList();
        pool.shutdown();

        // Then
        assertEquals(List.of(200, 200, 200, 200, 200, 200, 200, 200, 200, 200), statuses);
        assertEquals(55.0, lotsOf(OWNER).stream().mapToDouble(AssetEntity::getQuantity).sum());
    }

    private static Integer done(Future<Integer> status) {
        try {
            return status.get();
        } catch (InterruptedException | ExecutionException failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Test
    void addTransactionV2_whenACorporateActionHoldsItBack_storesItWithTheV2Flow() {
        // Given — a bonus recorded on 5 Jan, not yet performed for OWNER (W-270)
        bonusRecordedOn(JAN_5);

        // When — a V2 buy dated 5 Jan
        ResponseEntity<String> held = postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));

        // Then — held back, and the temporary says V2 received it
        assertTrue(held.getBody().contains("temporary"), held.getBody());
        assertEquals(TradeFlow.V2, transactionsOf(OWNER).getFirst().getTradeFlow());
    }

    @Test
    void addTransaction_whenACorporateActionHoldsItBack_storesItWithTheV1Flow() {
        // Given
        bonusRecordedOn(JAN_5);

        // When — a V1 buy dated 5 Jan
        ResponseEntity<String> held = post(OWNER, "/portfolio/user/" + OWNER + "/transaction",
                trade(TransactionType.BUY, 10.0, 100.0, JAN_5));

        // Then
        assertTrue(held.getBody().contains("temporary"), held.getBody());
        assertEquals(TradeFlow.V1, transactionsOf(OWNER).getFirst().getTradeFlow());
    }

    @Test
    void redrive_whenAV2BuyWaitedBesideASameDayV2Lot_keepsItAsItsOwnLot() {
        // Given — a V2 lot of 10 @ 100 on 5 Jan, and a waiting V2 buy of 10 @ 120 on 5 Jan (W-264a scenario 13, G-8)
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
        waiting(trade(TransactionType.BUY, 10.0, 120.0, JAN_5), TradeFlow.V2);

        // When
        ResponseEntity<String> redriven = post(OWNER, "/temporary-transactions/user/" + OWNER + "/redrive", null);

        // Then — a lot per buy: 10 @ 100 and 10 @ 120
        assertTrue(redriven.getBody().contains("Succeeded: 1"), redriven.getBody());
        List<AssetEntity> lots = lotsOf(OWNER);
        assertEquals(2, lots.size(), () -> "lots: " + lots);
        assertEquals(List.of(100.0, 120.0), lots.stream().map(AssetEntity::getPrice).sorted().toList());
    }

    @Test
    void redrive_whenAV2SaleIsDatedBeforeTheOnlyLot_leavesTheLotAlone() {
        // Given — a V2 lot of 10 bought 5 Feb, and a waiting V2 sale of 5 dated 5 Jan
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, FEB_5));
        waiting(trade(TransactionType.SELL, 5.0, 150.0, JAN_5), TradeFlow.V2);

        // When
        ResponseEntity<String> redriven = post(OWNER, "/temporary-transactions/user/" + OWNER + "/redrive", null);

        // Then — nothing was held on 5 Jan: the item fails and the lot keeps 10
        assertTrue(redriven.getBody().contains("Failed: 1"), redriven.getBody());
        assertEquals(10.0, lotsOf(OWNER).getFirst().getQuantity());
    }

    @Test
    void redrive_whenATemporaryRecordsNoFlow_recordsItThroughV1AsBefore() {
        // Given — a V2 lot of 10 @ 100 on 5 Jan, and a waiting buy of 10 @ 120 on 5 Jan stored before W-270 (D-555 C2a)
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
        waiting(trade(TransactionType.BUY, 10.0, 120.0, JAN_5), null);

        // When
        post(OWNER, "/temporary-transactions/user/" + OWNER + "/redrive", null);

        // Then — V1 merges it into the same-day lot: one lot of 20 @ (1000 + 1200) / 20 = ₹110
        List<AssetEntity> lots = lotsOf(OWNER);
        assertEquals(1, lots.size(), () -> "lots: " + lots);
        assertEquals(20.0, lots.getFirst().getQuantity());
        MoneyAssert.assertMoney(110.0, lots.getFirst().getPrice());
    }

    @Test
    void addTransaction_whenSaleIsDatedBeforeTheOnlyLot_answers400AndChangesNothing() {
        // Given — a V1 lot bought 5 Feb (W-271, G-3; W-264a scenario 8)
        post(OWNER, "/portfolio/user/" + OWNER + "/transaction", trade(TransactionType.BUY, 10.0, 100.0, FEB_5));

        // When — a V1 sale dated 5 Jan
        ResponseEntity<String> sold = post(OWNER, "/portfolio/user/" + OWNER + "/transaction",
                trade(TransactionType.SELL, 5.0, 150.0, JAN_5));

        // Then — nothing was held on 5 Jan: 400, the lot keeps 10
        assertEquals(400, sold.getStatusCode().value(), sold.getBody());
        assertEquals(10.0, lotsOf(OWNER).getFirst().getQuantity());
    }

    @Test
    void addTransaction_whenALaterLotExists_sellsOnlyLotsHeldOnTheSaleDate() {
        // Given — V1 lots of 10 on 5 Jan, 10 on 5 Feb and 10 on 5 Apr
        String path = "/portfolio/user/" + OWNER + "/transaction";
        LocalDate apr5 = LocalDate.of(2026, 4, 5);
        post(OWNER, path, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
        post(OWNER, path, trade(TransactionType.BUY, 10.0, 120.0, FEB_5));
        post(OWNER, path, trade(TransactionType.BUY, 10.0, 130.0, apr5));

        // When — a V1 sale of 25 on 5 Mar, when only 10 + 10 = 20 were held
        ResponseEntity<String> sold = post(OWNER, path, trade(TransactionType.SELL, 25.0, 150.0, MAR_5));

        // Then — refused; all three lots keep 10, April's included
        assertEquals(400, sold.getStatusCode().value(), sold.getBody());
        List<AssetEntity> lots = lotsOf(OWNER);
        assertEquals(List.of(JAN_5, FEB_5, apr5), lots.stream().map(AssetEntity::getTransactionDate).toList());
        assertEquals(List.of(10.0, 10.0, 10.0), lots.stream().map(AssetEntity::getQuantity).toList());
    }

    @Test
    void addTransaction_whenV2BoughtTwiceThatDay_mergesIntoTheOldestLot() {
        // Given — two V2 buys on 5 Jan: 10 @ 100 (first stored) and 10 @ 140 (W-272, G-6; W-264a scenario 11)
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
        postV2(OWNER, trade(TransactionType.BUY, 10.0, 140.0, JAN_5));

        // When — a V1 buy of 10 @ 120 on 5 Jan
        ResponseEntity<String> bought = post(OWNER, "/portfolio/user/" + OWNER + "/transaction",
                trade(TransactionType.BUY, 10.0, 120.0, JAN_5));

        // Then — recorded into the oldest lot: 20 @ (1000 + 1200) / 20 = ₹110; the other keeps 10 @ ₹140
        assertEquals(200, bought.getStatusCode().value(), bought.getBody());
        List<AssetEntity> lots = lotsOf(OWNER).stream().sorted(Comparator.comparing(AssetEntity::getId)).toList();
        assertEquals(List.of(20.0, 10.0), lots.stream().map(AssetEntity::getQuantity).toList());
        MoneyAssert.assertMoney(110.0, lots.get(0).getPrice());
        MoneyAssert.assertMoney(140.0, lots.get(1).getPrice());
    }

    private void bonusRecordedOn(LocalDate recordDate) {
        CorporateActionEntity bonus = new CorporateActionEntity();
        bonus.setStockCode(STOCK);
        bonus.setStockName("Infosys");
        bonus.setType(CorporateActionType.BONUS);
        bonus.setRecordDate(recordDate);
        bonus.setExDate(recordDate);
        bonus.setPriority(1);
        bonus.setAssetType(AssetType.EQUITY);
        mongoTemplate.save(bonus, "corporate_action");
    }

    private void waiting(Map<String, Object> trade, TradeFlow flow) {
        TransactionEntity temp = new TransactionEntity();
        temp.setEmail(OWNER);
        temp.setStockCode(STOCK);
        temp.setBrokerName(BrokerName.ZERODHA);
        temp.setTransactionType(TransactionType.valueOf((String) trade.get("transactionType")));
        temp.setStatus(TransactionStatus.TEMPORARY);
        temp.setTradeFlow(flow);
        temp.setAssetRequest(asRequest(trade));
        mongoTemplate.save(temp, "transactions");
    }

    private static int columnOf(Row header, String name) {
        for (Cell cell : header) {
            if (name.equals(cell.getStringCellValue())) {
                return cell.getColumnIndex();
            }
        }
        throw new IllegalStateException("No column " + name);
    }

    private static AssetRequest asRequest(Map<String, Object> body) {
        AssetRequest request = new AssetRequest();
        request.setStockCode((String) body.get("stockCode"));
        request.setStockName((String) body.get("stockName"));
        request.setExchangeName((String) body.get("exchangeName"));
        request.setBrokerName(BrokerName.valueOf((String) body.get("brokerName")));
        request.setAssetType(AssetType.valueOf((String) body.get("assetType")));
        request.setPrice((Double) body.get("price"));
        request.setQuantity((Double) body.get("quantity"));
        request.setTransactionType(TransactionType.valueOf((String) body.get("transactionType")));
        request.setAccountHolder((String) body.get("accountHolder"));
        request.setTransactionDate(LocalDate.parse((String) body.get("transactionDate")));
        return request;
    }

    private static RestTemplate lenientRestTemplate() {
        RestTemplate template = new RestTemplate();
        template.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return template;
    }

    private static Map<String, Object> trade(TransactionType type, double quantity, double price, LocalDate date) {
        Map<String, Object> body = new HashMap<>();
        body.put("stockCode", STOCK);
        body.put("stockName", "Infosys");
        body.put("exchangeName", "NSE");
        body.put("brokerName", BrokerName.ZERODHA.name());
        body.put("assetType", AssetType.EQUITY.name());
        body.put("price", price);
        body.put("quantity", quantity);
        body.put("transactionType", type.name());
        body.put("accountType", AccountType.SELF.name());
        body.put("accountHolder", "Self");
        body.put("transactionDate", date.toString());
        return body;
    }

    private String url(String path) {
        return "http://localhost:" + RestAssured.port + path;
    }

    private HttpHeaders headers(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateToken(email));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<String> post(String email, String path, Object body) {
        return rest.exchange(URI.create(url(path)), HttpMethod.POST, new HttpEntity<>(body, headers(email)), String.class);
    }

    private ResponseEntity<String> postV2(String email, Map<String, Object> body) {
        return post(email, "/portfolio/user/" + email + "/transaction/v2", body);
    }

    private <T> ResponseEntity<T> get(String email, String path, Class<T> type) {
        return rest.exchange(URI.create(url(path)), HttpMethod.GET, new HttpEntity<>(headers(email)), type);
    }

    private ResponseEntity<String> upload(String email, byte[] content, String quarter) {
        HttpHeaders headers = headers(email);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "trades.xlsx";
            }
        });
        String path = "/portfolio/user/" + email + "/upload-transactions?quarter=" + quarter;
        return rest.exchange(URI.create(url(path)), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private List<AssetEntity> lotsOf(String email) {
        return mongoTemplate.findAll(AssetEntity.class, "assets").stream()
                .filter(lot -> email.equals(lot.getEmail()))
                .sorted(Comparator.comparing(AssetEntity::getTransactionDate))
                .toList();
    }

    private List<TransactionEntity> transactionsOf(String email) {
        return mongoTemplate.findAll(TransactionEntity.class, "transactions").stream()
                .filter(transaction -> email.equals(transaction.getEmail()))
                .toList();
    }

}
