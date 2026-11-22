# Module `portfolio` — trades, holdings and what they earned

> W-357b7a (D-911), written from the code at `review/taxplanning` on 2026-11-22. Every claim cites the line it was read from; a bare
> path is under `backend/src/main/java/com/thiru/wealthlens/portfolio/`. Template: `docs/taxplanning-build/plans/W-357b.md` C2a. This
> doc is written in two parts (D-910): **trades and holdings** (here, W-357b7a) and **outcomes and reports** — `profit_and_loss`,
> `trade_outcomes`, analytics, the export, the property sale and their rebuilds — which W-357b7b adds.

## 1. Purpose

`portfolio` is the domain centre: a user's trades (`transactions`) and the lots they leave behind (`assets`). A trade enters through one
of two live flows, V1 or V2, or through a spreadsheet upload. It is checked, held back if a corporate action has to be applied first, and
de-duplicated, then it changes the user's lots. A buy makes or grows a lot; a sale consumes lots oldest first. The charge engine
(`brokercharges`) prices it on the way.

## 2. Boundaries

- `OPEN` module, `allowedDependencies = {"shared", "auth", "corporate", "brokercharges", "helper"}`
  (`backend/src/main/java/com/thiru/wealthlens/portfolio/package-info.java:1` – `:5`).
- **It calls** `corporate`'s repositories to hold trades (`service/TemporaryTransactionService.java:37` – `:40`) and its split and demerger
  steps to rebuild lots (`service/LotLedger.java:132` – `:147`); `brokercharges` through `ChargeRecordingGateway`
  (`service/PortfolioService.java:70`, `:480`, `:663`); `brokercharges`' `UserChargeService` and `ChargeAccountService` on erasure
  (`service/UserRecordsErasureService.java:50` – `:51`).
- **Called by** `corporate` (it rewrites lots and transactions on a perform), `brokercharges` (it reads `transactions` for the
  reconciliation) and `shared`, which depends back on it for the asset and transaction prototypes (CLAUDE.md's module table).
- URL segment `portfolio` (`shared/web/ApiZones.java:26` – `:27`).

## 3. Routes

| Method and path | Zone | Body → answer | Where |
|---|---|---|---|
| `POST /api/v1/me/portfolio/transaction` | me | `AssetRequest` → text | `controller/PortfolioController.java:37` – `:40`; V1 (§5) |
| `POST /api/v1/me/portfolio/transaction/v2` | me | `AssetRequest` → text | `PortfolioController.java:42` – `:45`; V2 (§5) |
| `POST /api/v1/me/portfolio/transactions/uploads?quarter=` | me | an Excel `file` → text: recorded, repeated, held back | `PortfolioController.java:47` – `:51`; `service/PortfolioService.java:276` – `:305` |
| `DELETE /api/v1/me/portfolio` | me | → text: every record erased, or which collections could not be | `PortfolioController.java:66` – `:74`; `UserRecordsErasureService.java:60` – `:85` |
| `GET /api/v1/me/portfolio/stocks` | me | → `AssetResponse` per holding (stock, broker, holder), with charges | `PortfolioController.java:76` – `:79`; `service/PortfolioQueryService.java:62` – `:87` |
| `GET /api/v1/me/portfolio/mutual-funds` | me | → the same, mutual funds only | `PortfolioController.java:81` – `:84`; `PortfolioQueryService.java:181` – `:188` |
| `POST /api/v1/me/portfolio/stocks/search` | me | `BulkGetRequest` (`dateRange`) → holdings bought in the range | `PortfolioController.java:86` – `:90`; `PortfolioQueryService.java:89` – `:103` |
| `GET /api/v1/me/portfolio/stocks/{stockCode}` | me | → that stock's lots, oldest first, each with its charges | `PortfolioController.java:92` – `:96`; `PortfolioQueryService.java:44` – `:60` |
| `POST /api/v1/me/portfolio/assets/search` | me | `BulkGetRequest` (`queryFilters`) → matching lots | `PortfolioController.java:99` – `:103`; `service/MongoTemplateService.java:28` – `:44` |
| `GET /api/v1/me/portfolio/transactions` | me | → a page of `TransactionResponse`, newest first, each with its charges | `controller/TransactionController.java:32` – `:35`; `service/TransactionService.java:128` – `:163` |
| `POST /api/v1/me/portfolio/transactions/search` | me | `BulkGetRequest` → a page of matches | `TransactionController.java:26` – `:30` |
| `GET /api/v1/me/portfolio/temporary-transactions` | me | → the held trades | `controller/TemporaryTransactionsController.java:26` – `:30` |
| `POST /api/v1/me/portfolio/temporary-transactions/redrive` | me | → `RedriveResult` (`succeeded`, `failed`, `stillFiltered`, `filteredOut`, `message`) | `TemporaryTransactionsController.java:39` – `:42`; `PortfolioService.java:334` – `:391` |
| `GET /api/v1/admin/portfolio/users/{email}/stocks`, `…/mutual-funds` | admin | one user's holdings | `controller/HoldingsAdminController.java:21` – `:37` |
| `POST /api/v1/admin/portfolio/users/{email}/assets/rebuild?dryRun=true` | admin | → `AssetsRebuildReport`; writes only with `dryRun=false` | `controller/AssetsRebuildController.java:26` – `:31` |

`GET /api/v1/me/portfolio/profit-and-loss` (`PortfolioController.java:53` – `:57`) belongs to the outcomes part (W-357b7b). The admin
routes carry `@PreAuthorize("hasRole('SUPER_USER')")` on top of the zone's rule (`HoldingsAdminController.java:27`, `:33`;
`AssetsRebuildController.java:26`).

**Refusals (400 unless said)**, all in `PortfolioService.validateAssetRequest` unless named (`PortfolioService.java:189` – `:269`), so the JSON routes, every
upload row and a redrive all meet them:

| When | Message | Where |
|---|---|---|
| A V1 trade or an upload while a held trade waits | "There are pending temporary transactions. Please redrive them before …" | `PortfolioService.java:83` – `:85`, `:278` – `:280` |
| A body `email` other than the caller's | "Email does not match" | `:190` – `:193` |
| No stock code, broker, side or date | "A trade must name its stock code" / "… its broker" / "… whether it is a BUY or a SELL" / "… carry its transaction date; it is not taken to be today" | `:240` – `:253` |
| Quantity absent or ≤ 0; price absent or negative | "Quantity must be greater than zero, but was: …"; "Price is required; it may be 0 for a free allotment"; "Price must not be negative, but was: …" | `:197` – `:207` |
| A stock code with an outer space | "Stock code \"…\" has a leading or trailing space; …" | `:209` – `:214` |
| A date after today | "A trade cannot be dated in the future: …" | `:216` – `:222` |
| A `GOLD_BOND` with no maturity date; a `LAND_OR_BUILDING` | "A GOLD_BOND needs its …; it is not assumed"; `AssetType.NOT_A_BROKER_TRADE` | `:224` – `:231` |
| No asset type; no exchange; an exchange its type does not list | "A trade must name its asset type"; "A trade of type … must name its exchange, one of …"; "Exchange \"…\" is not accepted for a trade of type …" | `:255` – `:269` |
| A sale with no lot, or more than is held on its date | "Stock not found with code: …"; "Not enough stocks to sell for: …" | `:561` – `:572` |
| An upload row the rules refuse | "Trade n: …", n its place among the sheet's trades; a bad cell "Row n, …: …" | `:311` – `:317`; `service/parser/AssetRequestParser.java:122` – `:155` |
| A date range missing a bound (`/stocks/search`) | "A date range is required: dateRange with startDate and endDate" | `PortfolioQueryService.java:90` – `:92` |
| `/stocks/{stockCode}` for a stock with no lot | "Stock not found" | `PortfolioQueryService.java:50` – `:52` |
| Every attempt collided with another trade | 409 "Another trade was being recorded at the same moment; this one was not recorded — send it again" | `service/TradeCollisionRetry.java:47` – `:48`, `:80` – `:96` |

## 4. Stored data

**`assets`** (`entity/AssetEntity.java:33` – `:158`), one lot:
- **Who and what:** `email` and `account_holder` (encrypted, searchable), `account_type`, `stock_code`, `stock_name`, `exchange_name`,
  `broker_name`, `asset_type`, `segment` (`DELIVERY` when absent, `:69` – `:73`), `maturity_date`.
- **The lot:** `transaction_date`, `price`, `quantity`, `broker_charges`, `misc_charges`, and how much of each earlier sales have taken,
  `allocated_buy_charges` and `allocated_buy_misc_charges` (`:81` – `:97`).
- **Its history:** `buy_transaction_ids`, `sell_transaction_ids`, `corporate_action` (the action that created it, e.g. a bonus),
  `corporate_actions` (the actions applied to it), `order_id`, `order_execution_time` (`TODO(W-214)`), `order_time_quantities`,
  `timezone_id`, `comments`, `audit_metadata`.
- No index is declared on it.

**`transactions`** (`entity/TransactionEntity.java:31` – `:175`), one trade as received, or a held one:
- The same trade facts as a lot, plus `total_value`, `transaction_type`, `comment` and `order_execution_time`.
- **De-duplication:** `idempotency_key` (unique, sparse), `trade_fingerprint` (indexed, not unique), `submitted_at`, and
  `source_temp_transaction_id` (unique, sparse) linking a redriven trade to the held one it came from (`:119` – `:154`).
- **State:** `status` (`TEMPORARY`, `PROCESSED`, `FAILED`; `dto/enums/TransactionStatus.java:3` – `:7`), `asset_request` (the original
  request, on a held trade), and `trade_flow` (`V1` or `V2`, set by the server from the route, `TransactionEntity.java:162` – `:169`).
- Its indexes are created at start by `PortfolioIndexInitializer`, which logs and carries on when one cannot be built
  (`config/PortfolioIndexInitializer.java:33` – `:74`).

## 5. Behaviour

**Recording a trade** (`TransactionService.java:59` – `:105`). Before a trade changes a lot it is recognised as a repeat, in this order:
1. **An `idempotencyKey`** this user already used: the earlier trade, unchanged.
2. **A `tempTransactionId`** some processed trade of this user came from: the earlier trade.
3. **The same fingerprint** within `app.portfolio.idempotency.window-seconds` (60). The fingerprint is a SHA-256 of email, stock, broker,
   holder, asset type, side, quantity, price and date (`service/TradeFingerprint.java:26` – `:38`).

A repeat answers "Transaction already recorded; this submission was a repeat and changed nothing" (`PortfolioService.java:61` – `:62`).
An upload gives each row the key `upload:{digest of user, quarter and file}:{row}`, so the same file sent again repeats every row, while two
identical rows in one file are two trades (`:285` – `:296`, `:320` – `:327`).

**Held back for a corporate action** (`TemporaryTransactionService.java:48` – `:124`). A trade is stored `TEMPORARY`, with its request
and flow, and changes no lot when either of these is true for its stock and broker:
- a split, bonus or demerger recorded in the trade's quarter, up to the trade's date, has not been performed for this user and broker;
- an earlier one not performed reaches a lot this user holds with that broker, one bought before its record date.

"Performed" means a guard row of that type whose `action_date` is on or after the action's record date (`:111` – `:124`; `corporate`'s
gap W-381 (1)). **Redrive** replays each held trade through the flow that received it, a held trade from before W-270 through V1
(`PortfolioService.java:334` – `:400`). One still blocked is left as it is. A refusal fails only its own item; any other failure rolls the
whole redrive back. The ones that ran are marked `PROCESSED`.

**The two flows** (`PortfolioService.java:82` – `:156`). V1 refuses a trade while one is held; V2 does not.

| | V1 | V2 |
|---|---|---|
| A buy | merges into the oldest lot of that stock, broker, holder, segment and date at the weighted price, charges added (`:415` – `:464`; `repository/PortfolioRepository.java:22` – `:24`) | always a lot of its own (`PortfolioService.java:471` – `:488`) |
| A buy's charges | priced and recorded by the engine; the lot keeps the entered figure (`PortfolioService.java:623` – `:629`) | priced first; with `authoritative` on and a figure, the computed total is the lot's cost (`PortfolioService.java:479` – `:508`) |
| A sale | lots oldest first; each lot's buy charges taken once, in proportion (`PortfolioService.java:538` – `:621`) | the same lots; the matched lots handed to the trade-outcome recorder and the P&L (`PortfolioService.java:518` – `:536`, `:631` – `:672`) |

**A sale's lots** are those of the stock, broker and holder dated on or before the sale, sorted by date (`PortfolioRepository.java:26` –
`:30`; see gaps 2 and 3). A lot is taken whole when the sale needs at least all of it, within `DoubleUtil`'s tolerance. A lot left with
nothing is deleted (`PortfolioService.java:526` – `:535`, `:795` – `:810`).

**A buy charge taken once** (`service/LotChargeAllocator.java:36` – `:72`): each sale takes `round(paid − already taken) × sold ÷ held
before this sale`, to paise, and records it on the lot. So the last sale takes exactly what is left.

**Collisions** (`TradeCollisionRetry.java:38` – `:100`). Every trade rewrites the user's yearly P&L document, so two trades at once can
abort one with Mongo's `TransientTransactionError`. The trade is then run again from a fresh copy of its request: up to 5 more times,
waiting 20 ms and doubling to at most 500 ms, with 10 ms of jitter.

**The views** (`PortfolioQueryService.java:62` – `:179`). A holding is the lots of one stock, broker and account holder with something
left:
- **Quantity** is their sum, and **price** the value-weighted average of their prices.
- **Charges** are summed.
- **`transactionQuantities`** gives the quantity per buy date, and **`daysHeld`** the days from each buy date to today.

**The assets rebuild** (`service/AssetsRebuild.java:78` – `:222`; `LotLedger.java:33` – `:205`) replays one user's stored,
non-temporary trades into lots in memory, using the same rules the live paths use. Events run by date, and on one date the performed
splits and demergers come before the trades. Each buy takes the shape of the flow it recorded. A buy stored before flows were recorded
is shaped from the stored lot that lists it, and the report names each such buy. The rebuilt lots are compared with the stored ones.
Only `dryRun=false` replaces them: a paired lot keeps its id, order times, corporate actions and creation stamp (`AssetsRebuild.java:132` – `:154`).

**Erasure** (`UserRecordsErasureService.java:60` – `:97`) deletes, in this order:
- `assets`, `trade_outcomes`, `transactions` and `profit_and_loss`;
- the held trades and `lastly_performed_corporate_action`;
- `user_charges` and `charge_accounts`.

Each delete runs even when an earlier one failed, and the report names what failed. Account erasure (W-237d) calls it and fails if
anything is left (`service/PortfolioRecordErasure.java:25` – `:31`).

### Worked examples (by hand)

**A V2 sale across two lots.** V2 buys of 10 at ₹100 on 2 Jun 2025 (lot A) and 5 at ₹120 on 3 Jun 2025 (lot B); a sale of 12 on 4 Jun 2025.
- **Eligible:** both lots, oldest first.
- **Lot A:** 12 ≥ 10, so it is taken whole. 10 are matched, and A, left with 0, is deleted.
- **Lot B:** 2 are left to sell, so 2 of its 5 are matched, and B keeps 3 at ₹120.

**V1's same-day merge.** V1 buys of 10 at ₹100 and 10 at ₹110 on one date, same stock, broker, holder and segment, make one lot: 20 at
(1,000 + 1,100) ÷ 20 = ₹105, with both buys' charges added.

**A buy charge taken once** (the case `LotChargeAllocatorTest.deduct_acrossSuccessiveSells_sumsToWhatWasPaid` and
`PortfolioServiceTest.sellStock_v1_whenALotIsSoldInPieces_deductsTheBuyChargeOnlyOnce` assert). A lot of 3 that paid ₹10, sold one unit
at a time:

| Sale | Held before | Paid − taken | Share | Taken so far |
|---|---|---|---|---|
| 1 | 3 | 10.00 | 10.00 × 1 ÷ 3 = 3.333… → 3.33 | 3.33 |
| 2 | 2 | 6.67 | 6.67 × 1 ÷ 2 = 3.335 → 3.34 | 6.67 |
| 3 | 1 | 3.33 | 3.33 × 1 ÷ 1 = 3.33 | 10.00 |

Before B-7 / B-8 the full ₹10 was divided by what remained each time: 3.33 + 5.00 + 10.00 = ₹18.33.

## 6. Configuration

| Property | Shipped | Effect |
|---|---|---|
| `app.portfolio.idempotency.window-seconds` | 60 in all three profiles (`application.yaml:179`) | the fingerprint window; 0 turns the fingerprint fallback off; absent means 60 (`config/IdempotencyProperties.java:22` – `:35`) |
| `app.portfolio.trade-exchanges.accepted` | EQUITY and MUTUAL_FUND: NSE, BSE; BOND and GOLD_BOND: NSE, BSE, OFF_EXCHANGE; FD and INSURANCE: OFF_EXCHANGE (`application.yaml:183` – `:189`) | the exchanges a trade may name; a type not listed accepts none; absent stops the start (`config/TradeExchangeProperties.java:19` – `:34`) |
| `app.charges.*` | see `docs/modules/brokercharges.md` §6 | whether a trade is priced, and whether a V2 buy's cost is the computed total |

A multi-document trade is all-or-nothing only on a replica set with `app.mongodb.transactions-enabled` (`PortfolioService.java:76` – `:81`).

## 7. Tests

| Class | Cases |
|---|---|
| `PortfolioGapAuditIntegrationTest` | 62 |
| `PortfolioIntegrationTest` | 43 |
| `PortfolioServiceTest` | 37 |
| `CallerMistakeIntegrationTest` | 16 that reach a trade route |
| `AssetRequestParserTest` | 15 |
| `TransactionServiceIdempotencyTest` | 12 |
| `TemporaryTransactionServiceTest` | 10 |
| `AssetsRebuildIntegrationTest`, `TradeCollisionRetryTest` | 8, 7 |
| `TransactionServiceTest`, `TransactionServiceChargesTest`, `TradeFingerprintTest`, `AssetsRebuildTest` | 5 each |
| `UserRecordsErasureServiceTest`, `NoCardCostBasisIntegrationTest` | 4 each |
| `LotChargeAllocatorTest`, `EarlierQuarterHoldIntegrationTest` | the allocation; the earlier-quarter hold |

Counts are `@Test` / `@ParameterizedTest` annotations per file (grep), not executions. `corporate`'s and `brokercharges`' tests drive
these routes too.

## 8. Known gaps (placed as W-385, D-911)

1. **A caller can overwrite any stored transaction, another user's included.**
   - **How:** `AssetRequest.tempTransactionId` binds from the request body (`dto/AssetRequest.java:28` – `:33`). `asTransaction` makes it the
     new row's id and its `sourceTempTransactionId` (`:98`, `:120` – `:122`). `recordTransaction` stops only when one of the caller's
     own processed trades already came from it (`TransactionService.java:73` – `:79`), then saves, and a save by id replaces whatever has
     that id (`:96` – `:104`). The held-trade path saves the same way (`TemporaryTransactionService.java:51` – `:56`).
   - **Shown at the build** (a throwaway integration test, deleted): a V2 buy naming the caller's own TCS transaction id left that row as
     WIPRO. A second user naming the first user's id took the row over: the first user's transaction list came back empty, and the
     second user's held a WIPRO row under that id. The first user's lot still lists the lost transaction.
   - **Who can:** any signed-in user who knows or guesses a transaction id. A user sees their own ids in `/transactions`; another's are
     Mongo ObjectIds, which are ordered by time.
2. **A sale takes lots of any segment.**
   - **How:** a sale's lots are keyed on email, stock, broker, holder and date only (`PortfolioRepository.java:26` – `:30`), and the
     rebuild matches the same way (`LotLedger.java:105` – `:109`). A buy, by contrast, merges only within one segment (W-296).
   - **Shown at the build:** delivery 10 and intraday 5 bought, then an intraday sale of 5, left **delivery 5 and intraday 5**. The sale was
     costed from the delivery lot.
3. **One date's lots are not ordered.** A sale sorts only by `transaction_date` (`PortfolioRepository.java:28`; `LotLedger.java:108`), so
   among V2's several lots of one date the order is what Mongo returns, though `LotLedger`'s contract says "in the order they were made"
   (`:98` – `:99`). Read from the code; not driven.
4. **`AssetRequest` carries no constraint**, so its `@Valid` checks nothing, and the missing-field refusals live in the service
   (`PortfolioService.java:240` – `:253`). CLAUDE.md: a constraint on the request type refuses a missing field. It also binds what a caller
   should not set: `tempTransactionId` (gap 1), `email`, and `orderExecutionTimestamp`, whose "remove this" comment carries no tracked id
   (`AssetRequest.java:88` – `:89`).
5. **An idempotency key is unique across users but looked up per user** (`TransactionEntity.java:126` – `:128`;
   `TransactionService.java:65` – `:71`). A second user sending a key the first used misses the lookup and meets the unique index on save,
   which is not a 400. Read from the code; not driven.
6. **Unpaged lists:** `/stocks`, `/mutual-funds`, `/stocks/search`, `/stocks/{stockCode}`, `/assets/search` and `/temporary-transactions`
   answer whole lists (`PortfolioController.java:76` – `:103`; `TemporaryTransactionsController.java:26` – `:30`). CLAUDE.md: a list that
   grows answers a page. Whether a holdings view is such a list is the owner's ruling.
7. **The wall clock and dead weight.**
   - **The wall clock:** the future-date refusal uses `LocalDate.now()` (`PortfolioService.java:217`), `daysHeld` uses `TLocalDate.today()`
     (`PortfolioQueryService.java:148`), and the fingerprint window uses `LocalDateTime.now()` (`TransactionService.java:83`, `:101`). None
     uses the app's `Clock`.
   - **Dead weight:** `recordTransaction` defaults a missing date to today (`TransactionService.java:60` – `:62`), which no path can reach,
     since every path refuses one first (`PortfolioService.java:250` – `:252`).
8. **Conventions.** `@AllArgsConstructor` where CLAUDE.md asks `@RequiredArgsConstructor`: `PortfolioController.java:28`,
   `TransactionController.java:19`, `MongoTemplateService.java:22`. The erase answer prints the user's email and answers 200 even when
   partial (`PortfolioController.java:69` – `:73`). `PortfolioIndexInitializer` catches `RuntimeException` and starts without the index
   (`config/PortfolioIndexInitializer.java:51` – `:62`).
9. **Four routes have no request in `api-collection/`** (grep at `41a7123`): `GET …/mutual-funds`, `POST …/stocks/search`, and the two
   admin holdings reads.

## 9. Not covered here

- `profit_and_loss`, `trade_outcomes`, the capital-gains summary, analytics, the export, the property sale and their rebuilds:
  W-357b7b, in this doc.
- What a corporate action does to a lot: `docs/modules/corporate.md`. What a trade is charged: `docs/modules/brokercharges.md`.
- The trade ledger's design notes, `docs/trade-ledger/`: retired by W-357b7c.
