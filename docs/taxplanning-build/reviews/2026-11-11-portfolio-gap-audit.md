# W-264a — gap audit: portfolio

Session 84, 2026-11-11. Plan rev 1 approved as recommended (D-535: C1a, C2a, C3a, C4 after W-264a).
Driven through real HTTP and the real filter chain on the Testcontainers harness (`mongo:8.0.4` replica set,
`integration-test` profile, `app.mongodb.transactions-enabled: true`, charge flags as `application.yaml`: all on;
no rate card seeded, so no V2 trade here was priced). Nothing ran against a real database. Fixes nothing.

**Status:** complete. Part 1 (scenarios 1 – 19) stopped at a clean boundary; the owner chose to continue in W-264a
("continue the rest in W-264a"), and part 2 (scenarios 20 – 37) covered the rest of the plan's list.

## 1. Scenarios run

Each part run twice, same result both times: part 1, 19 run, the same 11 failed; part 2, 18 new scenarios run with the 7 kept (25 tests), the same 5 failed. Expected values were written in each
test before it ran; they are arithmetic on the scenario's own inputs.

| # | Scenario (test) | Expected (by hand) | Actual | Verdict |
|---|---|---|---|---|
| 1 | V2: buy 10 @ 100 (5 Jan), 10 @ 120 (5 Feb), sell 15 @ 150 (5 Mar) | one lot left, 5 @ 120 dated 5 Feb | as expected | pass, kept |
| 2 | V2 then `GET /stocks/all` | one row, quantity 5, price 5 × 120 / 5 = ₹120 | as expected | pass, kept |
| 3 | V2 sale dated before the only lot | 400, lot keeps 10 | as expected | pass, kept |
| 4 | V2 sell 11 of 10 | 400, lot keeps 10 | as expected | pass, kept |
| 5 | V2 buy with no `price` | 400 | **500**, `Cannot invoke "java.lang.Double.doubleValue()" because … getPrice() is null` | **G-1** (PS-2) |
| 6 | V2 MF units 0.1 (5 Jan) + 0.7 (5 Feb), sell 0.8 | 200, nothing left | **400** `Not enough stocks to sell for: INFY` (0.1 + 0.7 = 0.7999999999999999) | **G-2** (PS-4) |
| 7 | V2 same trade posted twice within 60 s, no key | one transaction, one lot of 10, answer says "repeat" | as expected | pass, kept |
| 8 | V1 sale dated 5 Jan before the only lot (bought 5 Feb) | 400, lot keeps 10 | **200** `Stock sell updated in portfolio …` | **G-3** (new) |
| 9 | V1 two buys of 10 on one day (100, 120) | one lot of 20 @ ₹110; order times sum to 20 | lot right; order times sum to **10** | **G-4** (PS-3) |
| 10 | V1 MF units 0.1 + 0.2, sell 0.3 | 200, nothing held | 200, a lot of **2.7755575615628914E-17** units left | **G-5** (PS-5) |
| 11 | two V2 buys on 5 Jan, then a V1 buy on 5 Jan | 200, 30 held | **500** `… returned non unique result` | **G-6** (new) |
| 12 | V2: 10 @ 100 for holder "Self", 10 @ 200 for "Spouse", same stock and broker; `GET /stocks/all` | two rows | **one row**, holder "Self", quantity 20, price ₹150 | **G-7** (new) |
| 13 | V2 lot of 10 on 5 Jan; a stored temporary buy of 10 @ 120 on 5 Jan; redrive | two lots (V2 keeps a lot per buy) | **one lot of 20 @ ₹110**, merged by V1's buy | **G-8** (new) |
| 14 | pending temporary buy, then a V2 buy and a V1 buy | observed | V2 **200**, V1 400 | PS-8 — question Q-1 |
| 15 | upload: two identical rows (10 @ 100, 5 Jan) | 2 transactions, 20 held | answer `All transactions uploaded successfully …`; **1 transaction** | **G-9** (new) |
| 16 | upload: an equity row whose maturity-date and comment cells were never written | 200, 10 held | **400** `Cannot get a NUMERIC value from a STRING cell` | **G-10** (new) |
| 17 | upload: a file that is not a workbook | 400 | as expected | pass, kept |
| 18 | OWNER's token on OTHER's `/stocks/all` | 401 or 403 | as expected | pass, kept |
| 19 | OTHER's lot stored with no asset type; OWNER (role USER) calls `POST /portfolio/user/{OWNER}/update` | OTHER's lot untouched | OTHER's lot **rewritten to `MUTUAL_FUND`** | **G-11** (new) |
| 20 | one trade each for OWNER and OTHER; OWNER calls `GET /portfolio/user/{OWNER}/all/transactions` | OWNER's 1 | **2**, OTHER's included | **G-12** (new) |
| 21 | buy, buy, sell for OWNER, one buy for OTHER; `GET /transactions/user/{OWNER}` | 3 rows, 3 distinct ids, all OWNER's | as expected | pass, kept |
| 22 | after scenario 1, `POST /stocks/download` `{"entityName":"assets"}` | one data row, QUANTITY 5 (as `/stocks/all`) | as expected | pass, kept |
| 23 | download asking an unknown column | 400 | as expected | pass, kept |
| 24 | download with no `entityName` | 400 | **500** `Cannot invoke "String.hashCode()" …` | **G-14** (new) |
| 25 | V2 trade re-posted after its `submitted_at` is moved 2 min back (window 60 s) | 2 transactions, 20 held | as expected | pass, kept |
| 26 | V2 trade re-posted at ₹101 instead of ₹100, within the window | 20 held | as expected | pass, kept |
| 27 | identical V2 trades under idempotency keys k1, k1, k2 | 2 transactions, 20 held | as expected | pass, kept |
| 28 | OWNER `POST /clear/all` with OTHER holding records | OWNER empty; OTHER keeps 7 and 1 transaction | as expected | pass, kept |
| 29 | `POST /stocks` with `{}` (no date range) | 400 | **500** `… getDateRange() is null` | **G-14** (new) |
| 30 | V2 body not JSON; quantity `"ten"`; transaction type `HOLD` | 400, 400, 400 | as expected | pass, kept |
| 31 | `GET /stock/NOPE/all` with nothing held | 400 | as expected | pass, kept |
| 32 | `POST /request2` filtering on `password` | 400 | as expected | pass, kept |
| 33 | upload with quarter `Q5`; with no quarter | 400, 400 | as expected | pass, kept |
| 34 | redrive of a stored temporary sale of a stock not held (PS-6) | reported failed; no transaction row left for it | as expected | pass, kept (PS-6 not reproduced) |
| 35 | redrive of a good temporary buy beside that failing sale | Succeeded 1, Failed 1; 10 held; the good temporary PROCESSED | as expected | pass, kept |
| 36 | ten V1 buys of 1 … 10 units on 5 Jan posted at once (PS-7) | ten 200s, 55 held | **nine 500s**, one 200 (`WriteConflict`, `TransientTransactionError`) | **G-13** (new) |
| 37 | ten V2 buys of 1 … 10 units on ten days posted at once | ten 200s, 55 held | **nine 500s**, one 200 (`WriteConflict`) | **G-13** |

Kept (C2a): 1 – 4, 7, 17, 18, 21 – 23, 25 – 28, 30 – 35 in `PortfolioGapAuditIntegrationTest` (20 tests, green). Scenario 14 passes but
freezes an unsettled answer, so it is quoted (§6), not kept. *Not done:* a probe per kept test; the 11 failures show
the harness fails, not that each kept assertion can.

## 2. Gaps

Each says what the holder meets; evidence is the run above plus the line read.

- **G-1 — a trade with no price answers 500.** `PortfolioService.java:193` unboxes `getPrice()`. Both flows (V1
  shares the method). PS-2, still open.
- **G-2 — a holding of fractional units cannot be sold in full.** `validateTransaction` compares the double sum
  exactly (`:482`); 0.1 + 0.7 units refuse a sale of 0.8. The loops at `:606`, `:658` still have no `hasNext()`
  guard (PS-4's crash half, by reading). Both flows. PS-4, still open.
- **G-3 — V1 sells lots bought after the sale date.** `sellStock` reads every lot of the stock
  (`:454-465`, `findBy…OrderByTransactionDate`) where V2 reads only lots dated on or before the sale. A V1 sale
  dated before any purchase is accepted and consumes a later lot. New.
- **G-4 — a V1 same-day merge loses the second buy's order time.** `:364` adds to the request, not the lot. PS-3.
- **G-5 — a V1 fractional sale leaves a near-zero lot held.** `:467` deletes on `== 0`; a lot of 2.8e-17 units
  stays and shows in holdings. PS-5. (PS-11's divide-by-zero needs an all-zero group; not reproduced — §4.)
- **G-6 — a V1 buy fails with 500 once V2 has bought the same stock twice that day.** `buyStock`'s merge finder
  returns `Optional` (`:341`, `PortfolioRepository.java:17`); V2 writes a lot per buy, so two V2 lots on one date
  make it non-unique. Both flows are live (CLAUDE.md). New.
- **G-7 — holdings merge different account holders into one row.** The grouping key is stock + broker only
  (`:828-830`); the row carries the first lot's holder, so "Self" is shown holding the spouse's 10 at an averaged
  price. Hits `/stocks/all`, `/mfs`, `POST /stocks`, the export (all through `groupedWithCharges`). New; PS-14 is
  the same key's delimiter problem.
- **G-8 — redrive applies every temporary trade through V1.** The stored temporary holds the request but not the
  flow (`TemporaryTransactionService.java:39-44`); redrive calls `processTransaction` (`:281`), V1's path. A V2 trade
  that waited on a corporate action comes back merged into a same-day lot, without the charge engine, and — by G-3 —
  a redriven sell ignores lot dates. New. Corporate-action blocking itself is W-264c's.
- **G-9 — an upload silently drops a row identical to an earlier one.** Upload rows carry no idempotency key, so
  the fingerprint window (`TransactionService.java:73-74`, 60 s) treats the second as a retry; the answer still
  says all were uploaded. Two equal fills of one order on one day are ordinary. New.
- **G-10 — an upload row with an empty cell is misread.** `AssetRequestParserImpl.java:80-89` maps cells to
  headers by iteration order; an untouched cell is skipped, every later value shifts one column left, and the
  answer names a cell type, not the row or column. A spreadsheet program does not write an empty cell. New.
  Related: `PortfolioIntegrationTest.uploadTransactions_allSucceed_whenValidExcel_expected200` writes its row to the
  sheet of today's quarter but uploads `quarter=Q1`, so since October it asserts 200 on an empty sheet.
- **G-11 — any signed-in user can rewrite every user's holdings and transactions.**
  `SpecialController` maps `POST /portfolio/user/{email}/update` (`:17-20`) to `updateTransactions`, which loads
  every asset and every transaction of every user and sets a missing asset type to `MUTUAL_FUND`
  (`PortfolioService.java:847-857`, `TransactionService.updateTransactions`). The path check passes because the
  caller uses their own email. A one-time migration left reachable (PS-10 by reading). New as a reachability finding.
- **G-12 — any signed-in user can read every user's transactions.** `PortfolioController` `GET /all/transactions`
  calls `UserMail.from(email)` and discards it, then returns `transactionService.allTransactions()`
  (`TransactionService.java:194-196`, `findAll()`). The existing `TransactionIntegrationTest.allTransactions_viaGETEndpoint`
  seeds one user only, so it never saw this. New.
- **G-13 — trades posted at the same time mostly fail with 500.** With transactions on, concurrent writes to the same
  documents abort with Mongo `WriteConflict` (`TransientTransactionError`, "Please retry your operation"); nothing
  retries it and `ControllerAdviser` answers 500. Nine of ten failed on V1 (same lot) and on V2 (different lots; the
  shared writes are elsewhere — not traced). PS-7's lost update does not occur in this configuration: the conflict
  refuses instead. New.
- **G-14 — two more caller mistakes answer 500.** `POST /stocks` without `dateRange` (`PortfolioController`
  `getStocks` dereferences it) and `POST /stocks/download` without `entityName` (`EntityExportService.export`
  switches on null). W-263's reach.
- **Also seen:** the assets export has no account-holder column, so G-7's merged row is indistinguishable there too;
  the transactions export ignores the request's `queryFilters` (`EntityExportService`, `"transactions"` branch).

## 3. Question for the owner

- **Q-1 (PS-8).** V1 and the upload refuse a trade while a temporary transaction is pending; V2 accepts it (scenario
  14). Should V2 refuse too, or is a lot per buy reason enough not to?

## 4. PS-1 – PS-15 and B-1 – B-4, B-9 against today's code (C3a)

| Entry | Status | Evidence |
|---|---|---|
| PS-1 upload and redrive skip validation | still open | `processTransaction` (`:92-93`) calls only `sanitizeAssetRequest`; `AssetRequestParser.java:126-135` casts and assigns |
| PS-2 NPE on null price | still open, reproduced | G-1 |
| PS-3 V1 merge order time | still open, reproduced | G-4 |
| PS-4 sell loop / exact compare | still open, reproduced (refusal half) | G-2 |
| PS-5 V1 `== 0` delete | still open, reproduced | G-5 |
| PS-6 redrive catch not isolating | not reproduced | scenarios 34 – 35: the failed item left no row and the success was kept, though the log shows the sale's row was saved before the failure; the mechanism is not traced. The `catch (Exception e)` (`:290`) still breaks CLAUDE.md §2 — in the hygiene row |
| PS-7 no `@Version` on V1 merge | superseded | scenario 36: the lost update does not occur with transactions on; the conflict answers 500 instead (G-13) |
| PS-8 V2 no temporary guard | observed | Q-1 |
| PS-9 class does four jobs | still open | 863 lines |
| PS-10 dead code | partly fixed | commented-out lines and `UserBrokerChargeService` gone; `addTransactionInternal` (`:487`) and the quoted log line (`:849`) remain; G-11 |
| PS-11 unguarded divide | still open, not reproduced | `:581`; needs a group of only zero lots, which G-5's residue does not make |
| PS-12 naming | still open | `updateQuantityBySavingReportAndProfitAndLoss1` (`:652`) |
| PS-13 V1 drops segment | still open, by reading | `:645-649` uses the 13-argument context; its effect is on charge-card selection, which W-264b drives |
| PS-14 key without delimiter | still open | `:828-830`; see G-7 |
| PS-15 small things | still open | `buildRedriveMessage` (`:308`), no blank line after `package` |
| B-1 no coverage floor on trade-ledger code | still open | `pom.xml` gates: charges engine, taxengine, taxengine.rules only |
| B-2 two financial-year formats | code fixed; stored rows unknown | `TradeMatchingService.java:204` uses `FinancialYear.containing(…).label()`; rows already written are not visible here |
| B-4 no rebuild of assets / P&L | still open | no rebuild route for `assets` or `profit_and_loss` |
| B-9 `TestController` sells without saving | still open | `TestController` `POST /test/user/{email}/transact/sell` unchanged; any USER may call it on their own path |

## 5. Not covered

PS-13 by request (a charge-card question, W-264b's). Every plan scenario was run. No probe per kept test.

## 5a. Fix rows placed (C3a, C4: after W-264a, before W-264b, correctness first)

| Row | Gaps / entries |
|---|---|
| W-268 | G-11, G-12, B-9 — maintenance and test routes reach other users' data |
| W-269 | G-13 — concurrent trades answer 500 |
| W-270 | G-8 — redrive applies every trade through V1 |
| W-271 | G-3 — V1 sells lots bought after the sale |
| W-272 | G-6 — V1 buy fails after two V2 buys that day |
| W-273 | G-7, PS-14 — holdings merge account holders |
| W-274 | G-2, G-5, PS-4, PS-11 — fractional quantities compared exactly |
| W-275 | G-9 — upload drops identical rows |
| W-276 | G-10 — upload misreads a row with an empty cell; the hollow upload test |
| W-277 | PS-1 — upload and redrive skip validation |
| W-278 | G-1, G-14 — caller mistakes answering 500 |
| W-279 | G-4, PS-3 — V1 merge loses an order time |
| W-280 | PS-13 — V1 records every trade as delivery |
| W-281 | PS-9, PS-10, PS-12, PS-15, the redrive `catch (Exception e)` — `PortfolioService` hygiene |
| W-282 | B-1 — coverage floor on trade-ledger code |
| W-283 | B-4 — rebuild `assets` and `profit_and_loss` from `transactions` |
| W-284 | B-2 — check stored `trade_outcomes` rows for the old year format |

Q-1 (PS-8) is a question, not a row.

## 6. The failing scenarios, in full

Each becomes its fix's first test. They use the kept class's helpers; part 1's also use `asRequest`, `postV1`,
`fundTrade` and `workbook`, quoted after them, and part 2's concurrency scenarios use `done`, quoted after them.

Part 1:

```java
@Test
void addTransactionV2_whenFractionalLotsSumBelowTheirTrueTotal_sellsTheWholeHolding() {
    // Given — units 0.1 and 0.7 bought on two days: 0.8 units held (PS-4's converse)
    postV2(OWNER, fundTrade(TransactionType.BUY, 0.1, 100.0, JAN_5));
    postV2(OWNER, fundTrade(TransactionType.BUY, 0.7, 100.0, FEB_5));

    // When — sell all 0.8
    ResponseEntity<String> sold = postV2(OWNER, fundTrade(TransactionType.SELL, 0.8, 110.0, MAR_5));

    // Then — sold, nothing left
    assertEquals(200, sold.getStatusCode().value(), sold.getBody());
    assertEquals(0, lotsOf(OWNER).size());
}


@Test
void addTransactionV2_whenPriceIsMissing_answers400() {
    // Given — a buy with no price (PS-2)
    Map<String, Object> body = trade(TransactionType.BUY, 10.0, 100.0, JAN_5);
    body.remove("price");

    // When
    ResponseEntity<String> answer = postV2(OWNER, body);

    // Then — the caller's mistake: 400, nothing stored
    assertEquals(400, answer.getStatusCode().value(), answer.getBody());
    assertEquals(0, lotsOf(OWNER).size());
}


@Test
void addTransaction_whenFractionalSaleEmptiesTheLots_leavesNoHolding() {
    // Given — V1 units 0.1 on 5 Jan and 0.2 on 5 Feb
    postV1(OWNER, fundTrade(TransactionType.BUY, 0.1, 100.0, JAN_5));
    postV1(OWNER, fundTrade(TransactionType.BUY, 0.2, 100.0, FEB_5));

    // When — sell all 0.3 (PS-5)
    ResponseEntity<String> sold = postV1(OWNER, fundTrade(TransactionType.SELL, 0.3, 110.0, MAR_5));

    // Then — nothing is held
    assertEquals(200, sold.getStatusCode().value(), sold.getBody());
    assertEquals(0, lotsOf(OWNER).size(), () -> "lots left: " + lotsOf(OWNER));
}


@Test
void addTransaction_whenSaleIsDatedBeforeTheOnlyLot_answers400AndChangesNothing() {
    // Given — V1 buy on 5 Feb
    postV1(OWNER, trade(TransactionType.BUY, 10.0, 100.0, FEB_5));

    // When — a V1 sale dated 5 Jan
    ResponseEntity<String> sold = postV1(OWNER, trade(TransactionType.SELL, 5.0, 150.0, JAN_5));

    // Then — nothing was held on 5 Jan: 400, the lot keeps 10
    assertEquals(400, sold.getStatusCode().value(), sold.getBody());
    assertEquals(10.0, lotsOf(OWNER).getFirst().getQuantity());
}


@Test
void addTransaction_whenTwoBuysMergeOnOneDay_recordsBothOrderTimes() {
    // Given — V1 buy 10 on 5 Jan
    postV1(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));

    // When — another V1 buy of 10 on 5 Jan, merged into the same lot (PS-3)
    postV1(OWNER, trade(TransactionType.BUY, 10.0, 120.0, JAN_5));

    // Then — one lot of 20 at (1000 + 1200) / 20 = ₹110, whose order times account for all 20
    AssetEntity lot = lotsOf(OWNER).getFirst();
    assertEquals(20.0, lot.getQuantity());
    MoneyAssert.assertMoney(110.0, lot.getPrice());
    double recorded = lot.getOrderTimeQuantities().stream().mapToDouble(o -> o.getQuantity()).sum();
    assertEquals(20.0, recorded);
}


@Test
void addTransaction_whenV2BoughtTwiceThatDay_addsAThirdBuy() {
    // Given — two V2 buys on 5 Jan: two lots of 10
    postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
    postV2(OWNER, trade(TransactionType.BUY, 10.0, 110.0, JAN_5));

    // When — a V1 buy on 5 Jan; both flows are live
    ResponseEntity<String> bought = postV1(OWNER, trade(TransactionType.BUY, 10.0, 120.0, JAN_5));

    // Then — the buy is recorded: 30 held
    assertEquals(200, bought.getStatusCode().value(), bought.getBody());
    assertEquals(30.0, lotsOf(OWNER).stream().mapToDouble(AssetEntity::getQuantity).sum());
}


@Test
void getAllStocks_whenTwoAccountHoldersHoldOneStockAtOneBroker_showsARowEach() {
    // Given — 10 @ 100 for "Self", 10 @ 200 for "Spouse", same stock and broker
    postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
    Map<String, Object> spouse = trade(TransactionType.BUY, 10.0, 200.0, JAN_5);
    spouse.put("accountHolder", "Spouse");

    postV2(OWNER, spouse);

    // When
    AssetResponse[] rows = get(OWNER, "/portfolio/user/" + OWNER + "/stocks/all", AssetResponse[].class).getBody();

    // Then — two holdings, two rows: 10 @ 100 and 10 @ 200
    assertEquals(2, rows.length, () -> "rows: " + List.of(rows));
}


@Test
void redrive_whenATradeWaitedOnASameDayV2Lot_keepsItAsItsOwnLot() {
    // Given — a V2 lot of 10 on 5 Jan, and a stored temporary buy of 10 on 5 Jan (as V2's filter stores it)
    postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
    TransactionEntity temp = new TransactionEntity();
    temp.setEmail(OWNER);
    temp.setStockCode(STOCK);
    temp.setBrokerName(BrokerName.ZERODHA);
    temp.setStatus(TransactionStatus.TEMPORARY);
    temp.setAssetRequest(asRequest(trade(TransactionType.BUY, 10.0, 120.0, JAN_5)));
    mongoTemplate.save(temp, "transactions");

    // When
    ResponseEntity<String> redriven = post(OWNER, "/temporary-transactions/user/" + OWNER + "/redrive", null);

    // Then — a V2 buy keeps a lot per buy: two lots, 10 @ 100 and 10 @ 120
    assertEquals(200, redriven.getStatusCode().value(), redriven.getBody());
    assertEquals(2, lotsOf(OWNER).size(), () -> "lots: " + lotsOf(OWNER));
}


@Test
void update_whenAnOrdinaryUserCallsIt_leavesOtherUsersRecordsAlone() {
    // Given — OTHER's lot stored with no asset type
    postV2(OTHER, trade(TransactionType.BUY, 10.0, 100.0, JAN_5));
    AssetEntity lot = lotsOf(OTHER).getFirst();
    lot.setAssetType(null);
    mongoTemplate.save(lot, "assets");

    // When — OWNER, an ordinary user, calls the update on OWNER's own path
    post(OWNER, "/portfolio/user/" + OWNER + "/update", null);

    // Then — OTHER's lot is not rewritten
    assertEquals(null, lotsOf(OTHER).getFirst().getAssetType());
}


@Test
void uploadTransactions_whenAnEquityRowLeavesItsOptionalCellsUntouched_recordsIt() throws IOException {
    // Given — one equity buy of 10 whose maturity date and comment cells were never written
    byte[] sheet = workbook(JAN_5, 1, false);

    // When
    ResponseEntity<String> uploaded = upload(OWNER, sheet, "Q1");

    // Then — an equity trade has no maturity date: recorded, 10 held
    assertEquals(200, uploaded.getStatusCode().value(), uploaded.getBody());
    assertEquals(10.0, lotsOf(OWNER).stream().mapToDouble(AssetEntity::getQuantity).sum(), uploaded.getBody());
}


@Test
void uploadTransactions_whenTwoRowsAreTheSameTrade_recordsBoth() throws IOException {
    // Given — a sheet with two identical buys of 10 on 5 Jan: two orders, filled alike
    byte[] sheet = workbook(JAN_5, 2, true);

    // When
    ResponseEntity<String> uploaded = upload(OWNER, sheet, "Q1");

    // Then — both rows recorded: two transactions, 20 held
    assertEquals(200, uploaded.getStatusCode().value(), uploaded.getBody());
    assertEquals(2, transactionsOf(OWNER).size(), uploaded.getBody());
    assertEquals(20.0, lotsOf(OWNER).stream().mapToDouble(AssetEntity::getQuantity).sum());
}


private static Map<String, Object> fundTrade(TransactionType type, double quantity, double price, LocalDate date) {
    Map<String, Object> body = trade(type, quantity, price, date);
    body.put("assetType", AssetType.MUTUAL_FUND.name());
    return body;
}


/**
 * A sheet of {@code rows} identical buys of 10 INFY @ ₹100 on {@code date}, on the Q1 sheet. The maturity date and
 * comment are written as blank cells, or left untouched as a spreadsheet program leaves an empty cell.
 */
private static byte[] workbook(LocalDate date, int rows, boolean blankOptionalCells) throws IOException {
    String[] header = {"EMAIL", "STOCK CODE", "STOCK NAME", "EXCHANGE NAME", "BROKER NAME", "ASSET TYPE",
        "MATURITY DATE", "PRICE", "QUANTITY", "TRANSACTION TYPE", "TRANSACTION DATE", "BROKER CHARGES",
        "MISC CHARGES", "COMMENTS"};
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (Workbook book = new XSSFWorkbook()) {
        List.of("Q1", "Q2", "Q3", "Q4").forEach(book::createSheet);
        Sheet sheet = book.getSheet("Q1");
        Row top = sheet.createRow(0);
        for (int i = 0; i < header.length; i++) {
            top.createCell(i).setCellValue(header[i]);
        }
        CellStyle dateStyle = book.createCellStyle();
        dateStyle.setDataFormat(book.getCreationHelper().createDataFormat().getFormat("yyyy-MM-dd"));
        for (int r = 1; r <= rows; r++) {
            Row row = sheet.createRow(r);
            row.createCell(0).setCellValue(OWNER);
            row.createCell(1).setCellValue(STOCK);
            row.createCell(2).setCellValue("Infosys");
            row.createCell(3).setCellValue("NSE");
            row.createCell(4).setCellValue("ZERODHA");
            row.createCell(5).setCellValue("EQUITY");
            if (blankOptionalCells) {
                row.createCell(6);
            }
            row.createCell(7).setCellValue(100.0);
            row.createCell(8).setCellValue(10.0);
            row.createCell(9).setCellValue("BUY");
            row.createCell(10).setCellValue(java.sql.Date.valueOf(date));
            row.getCell(10).setCellStyle(dateStyle);
            row.createCell(11).setCellValue(0.0);
            row.createCell(12).setCellValue(0.0);
            if (blankOptionalCells) {
                row.createCell(13);
            }
        }
        book.write(out);
    }
    return out.toByteArray();
}

@Test
void addTransactionV2_whenATemporaryTransactionIsPending_isObservedAccepted() {
    // Given — a pending temporary buy (PS-8: recorded as observed, a question for the owner)
    TransactionEntity temp = new TransactionEntity();
    temp.setEmail(OWNER);
    temp.setStockCode(STOCK);
    temp.setStatus(TransactionStatus.TEMPORARY);
    temp.setAssetRequest(asRequest(trade(TransactionType.BUY, 10.0, 120.0, JAN_5)));
    mongoTemplate.save(temp, "transactions");

    // When
    ResponseEntity<String> bought = postV2(OWNER, trade(TransactionType.BUY, 10.0, 100.0, FEB_5));
    ResponseEntity<String> boughtV1 = postV1(OWNER, trade(TransactionType.BUY, 10.0, 100.0, MAR_5));

    // Then — V2 accepts, V1 refuses
    assertEquals(200, bought.getStatusCode().value(), bought.getBody());
    assertEquals(400, boughtV1.getStatusCode().value(), boughtV1.getBody());
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


private ResponseEntity<String> postV1(String email, Map<String, Object> body) {
    return post(email, "/portfolio/user/" + email + "/transaction", body);
}
```

Part 2:

```java
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
void allTransactions_whenAnotherUserHasTraded_listsOnlyTheCallersOwn() {
    // Given — one trade each
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
void downloadPortfolio_whenNoEntityIsNamed_answers400() {
    // When — the body names no entity
    ResponseEntity<String> answer = post(OWNER, "/portfolio/user/" + OWNER + "/stocks/download", Map.of());

    // Then — the caller's mistake
    assertEquals(400, answer.getStatusCode().value(), answer.getBody());
}


@Test
void getStocks_whenTheBodyHasNoDateRange_answers400() {
    // When
    ResponseEntity<String> answer = post(OWNER, "/portfolio/user/" + OWNER + "/stocks", Map.of());

    // Then
    assertEquals(400, answer.getStatusCode().value(), answer.getBody());
}


private static Integer done(Future<Integer> status) {
    try {
        return status.get();
    } catch (InterruptedException | ExecutionException failure) {
        throw new IllegalStateException(failure);
    }
}
```
