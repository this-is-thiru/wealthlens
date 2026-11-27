# Module `portfolio` — trades, holdings and what they earned

> W-357b7a (D-911), written from the code at `review/taxplanning` on 2026-11-22. Every claim cites the line it was read from; a bare
> path is under `backend/src/main/java/com/thiru/wealthlens/portfolio/`. Template: `docs/taxplanning-build/plans/W-357b.md` C2a. This
> doc was written in two parts (D-910): **trades and holdings** (W-357b7a, D-911) and **outcomes and reports** — `profit_and_loss`,
> `trade_outcomes`, analytics, the export, the property sale and their rebuilds (W-357b7b, D-912). It also holds the hand-worked
> figures `ProfitAndLossGapAuditIntegrationTest` asserts (§5), moved here from W-264d's plan, which W-357b7b removed with its review
> (both readable in git history, e.g. `git show a382d351:docs/taxplanning-build/plans/W-264d.md`). The trade ledger's design notes,
> `docs/trade-ledger/`, were deleted by W-357b7c (D-913) and are readable at tag `docs-history-m1` (their last form at `b72507b9`); the
> code cites that design by name (TL-6 – TL-8, B-4 – B-8), and §10 says what each name means.

## 1. Purpose

`portfolio` is the domain centre: a user's trades (`transactions`) and the lots they leave behind (`assets`). A trade enters through one
of two live flows, V1 or V2, or through a spreadsheet upload. It is checked, held back if a corporate action has to be applied first, and
de-duplicated, then it changes the user's lots. A buy makes or grows a lot; a sale consumes lots oldest first. The charge engine
(`brokercharges`) prices it on the way. A sale leaves **outcome rows** (`trade_outcomes`), one per lot it consumed, and folds into the
user's yearly **P&L** (`profit_and_loss`). The outcome rows are also what the tax side reads, as the capital-gains summary.

## 2. Boundaries

- `OPEN` module, `allowedDependencies = {"shared", "auth", "corporate", "brokercharges", "helper"}`
  (`backend/src/main/java/com/thiru/wealthlens/portfolio/package-info.java:1` – `:5`).
- **It calls** `corporate`'s repositories to hold trades (`service/TemporaryTransactionService.java:37` – `:40`) and its split and demerger
  steps to rebuild lots (`service/LotLedger.java:132` – `:147`); `brokercharges` through `ChargeRecordingGateway`
  (`service/PortfolioService.java:70`, `:480`, `:663`); `brokercharges`' `UserChargeService` and `ChargeAccountService` on erasure
  (`service/UserRecordsErasureService.java:50` – `:51`).
- **Called by** `corporate` (it rewrites lots and transactions on a perform), `brokercharges` (it reads `transactions` for the
  reconciliation) and `shared`, which depends back on it for the asset and transaction prototypes (CLAUDE.md's module table).
- **Read by** `taxplanning`, which takes a year's capital gains from `CapitalGainsSummaryService.summaryFor`
  (`taxplanning/portfolio/CapitalGainsAdapter.java:46`, `:51`).
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
| `POST /api/v1/me/portfolio/assets/search` | me | `BulkGetRequest` (`queryFilters`) → matching lots | `PortfolioController.java:99` – `:103`; `PortfolioQueryService.searchAssets`, a JPA `Specification` built by `QueryBuilder` (W-337e) |
| `GET /api/v1/me/portfolio/transactions` | me | → a page of `TransactionResponse`, newest first, each with its charges | `controller/TransactionController.java:32` – `:35`; `service/TransactionService.java:128` – `:163` |
| `POST /api/v1/me/portfolio/transactions/search` | me | `BulkGetRequest` → a page of matches | `TransactionController.java:26` – `:30` |
| `GET /api/v1/me/portfolio/temporary-transactions` | me | → the held trades | `controller/TemporaryTransactionsController.java:26` – `:30` |
| `POST /api/v1/me/portfolio/temporary-transactions/redrive` | me | → `RedriveResult` (`succeeded`, `failed`, `stillFiltered`, `filteredOut`, `message`) | `TemporaryTransactionsController.java:39` – `:42`; `PortfolioService.java:334` – `:391` |
| `GET /api/v1/admin/portfolio/users/{email}/stocks`, `…/mutual-funds` | admin | one user's holdings | `controller/HoldingsAdminController.java:21` – `:37` |
| `POST /api/v1/admin/portfolio/users/{email}/assets/rebuild?dryRun=true` | admin | → `AssetsRebuildReport`; writes only with `dryRun=false` | `controller/AssetsRebuildController.java:26` – `:31` |
| `GET /api/v1/me/portfolio/profit-and-loss?financialYear=` | me | → `ProfitAndLossResponse` for that year, empty when none | `PortfolioController.java:53` – `:57`; `service/ProfitAndLossService.java:239` – `:249` |
| `GET /api/v1/me/portfolio/analytics/portfolio-summary`, `…/asset-allocation`, `…/performance-metrics` | me | → the summary, the split by asset type, the win and loss figures | `controller/AnalyticsController.java:30` – `:43`; `service/AnalyticsService.java:49` – `:200` |
| `POST /api/v1/me/portfolio/analytics/xirr` | me | `XirrRequest` (`currentPrices`) → `XirrResponse` | `AnalyticsController.java:45` – `:48`; `AnalyticsService.java:207` – `:278` |
| `POST /api/v1/me/portfolio/stocks/download` | me | `EntityExportRequest` (`entityName`, `selectedColumns`, `queryFilters`) → an `.xlsx` | `controller/EntityExportController.java:25` – `:30`; `service/EntityExportService.java:45` – `:79` |
| `POST /api/v1/me/portfolio/properties/sales` | me | `PropertySaleRequest` → text "Property sale recorded: …" | `controller/PropertySaleController.java:22` – `:25`; `service/PropertySaleService.java:49` – `:59` |
| `POST /api/v1/admin/portfolio/users/{email}/profit-and-loss/rebuild?dryRun=true` | admin | → `ProfitAndLossRebuildReport` | `controller/ProfitAndLossRebuildController.java:27` – `:32` |
| `POST /api/v1/admin/portfolio/trade-outcomes/rebuild?dryRun=true` | admin | → `TradeOutcomeRebuildReport`, for every user with no rows | `controller/TradeOutcomeRebuildController.java:32` – `:36` |

The admin routes carry `@PreAuthorize("hasRole('SUPER_USER')")` on top of the zone's rule (`HoldingsAdminController.java:27`, `:33`;
`AssetsRebuildController.java:26`; `ProfitAndLossRebuildController.java:27`; `TradeOutcomeRebuildController.java:32`).

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
| A P&L year not written as stored (`2025-26`) | the form `2025-2026` named (`FinancialYear.parse`) | `ProfitAndLossService.java:243` |
| A V2 sale whose rows do not match its lots; a sale neither side of which records an asset type | "The sale of … has n trade-outcome rows for m lots"; "Neither the sale of … nor the lot it was sold from records an asset type, …" | `ProfitAndLossService.java:366` – `:369`, `:442` – `:446` |
| XIRR with one stock priced twice | "currentPrices names … more than once; give one price per stock" | `AnalyticsService.java:243` – `:247` |
| Export with no entity, or an unknown one | "Name the entity to export: one of assets, transactions, transactions-template"; "Invalid entity name: …" | `EntityExportService.java:48` – `:63` |
| A property sale: no name; no dates, or sold on or before bought; cost or price ≤ 0; an expense code not a `PropertyExpense`, or negative; posted twice | "A property sale needs the property's name", …, "The expense … is not one of …", "The sale of … is already recorded" | `PropertySaleService.java:52` – `:92` |
| A capital-gains year with a disposal whose broker charges have no breakdown (read by tax planning) | "n disposal(s) bore broker charges but kept no breakdown of them, …" | `service/CapitalGainsSummaryService.java:81` – `:93` |
| A P&L rebuild finding a charge row whose transaction is gone | "Charge rows name transactions that are not stored: …" | `service/ProfitAndLossRebuild.java:118` – `:129` |
| Every attempt collided with another trade | 409 "Another trade was being recorded at the same moment; this one was not recorded — send it again" | `service/TradeCollisionRetry.java:47` – `:48`, `:80` – `:96` |

## 4. Stored data

PostgreSQL tables (W-337e, D-949; Flyway `db/migration/portfolio/V9__portfolio.sql`). Primary keys are caller-assigned `VARCHAR(255)`
with `@PrePersist` random-UUID default — not time-ordered the way a Mongo `ObjectId` was (see gaps 1 and 3). Nested, never-queried-into
structures (`order_time_quantities`, `corporate_actions`, `realised_profits`, `out_sourced_realised_profits`, request/charge maps) are
`TEXT` JSON columns via `AttributeConverter`s (`entity/JsonColumnConverter.java`; `CorporateActionListConverter`,
`OrderTimeQuantityListConverter`, `RealisedProfitsConverter`, `StringListConverter`, `StringDoubleMapConverter`, `AssetRequestConverter`).
A converter's `empty()` is the value for a blank column: `List.of()`/`Map.of()` for a collection column, but `null` for
`RealisedProfitsConverter` — a null `realised_profits`/`out_sourced_realised_profits` means that track was never folded into, which
`ProfitAndLossService` already tells apart from a zero `RealisedProfits` (`RealisedProfitsConverter.java:25` – `:31`).

**`assets`** (`entity/AssetEntity.java:45` – `:187`), one lot:
- **Who and what:** `email` and `account_holder` (encrypted, searchable), `account_type`, `stock_code`, `stock_name`, `exchange_name`,
  `broker_name`, `asset_type`, `segment` (`DELIVERY` when absent).
- **The lot:** `transaction_date`, `price`, `quantity`, `broker_charges`, `misc_charges`, and how much of each earlier sales have taken,
  `allocated_buy_charges` and `allocated_buy_misc_charges`.
- **Its history:** `buy_transaction_ids`, `sell_transaction_ids` (`StringListConverter`), `corporate_action_type` (the action that created
  it, e.g. a bonus), `corporate_actions_json` (the actions applied to it, `CorporateActionListConverter`), `order_id`,
  `order_execution_time` (`TODO(W-214)`), `order_time_quantities_json` (`OrderTimeQuantityListConverter`), `timezone_id`, `comment`,
  `audit_metadata`, `version` (optimistic locking, added at the port).
- `OrderTimeQuantity` compares by value (`@EqualsAndHashCode`, W-337e), needed for Hibernate's converter dirty-check: without it, a lot
  with a non-empty `order_time_quantities` is rewritten — audit trail and all — on any flush that merely loaded it, since a freshly
  deserialized list is never `.equals()` to the one it was loaded from.
- No index is declared on it; the "oldest lot of a day" finder orders by `audit_metadata.created_at`, not id (W-337e, D-949;
  `repository/PortfolioRepository.java`), since the id carries no time order here.

**`transactions`** (`entity/TransactionEntity.java`), one trade as received, or a held one:
- The same trade facts as a lot, plus `total_value`, `transaction_type`, `comment` and `order_execution_time`.
- **De-duplication:** `idempotency_key` (unique, sparse), `trade_fingerprint` (indexed, not unique), `submitted_at`, and
  `source_temp_transaction_id` (unique, sparse) linking a redriven trade to the held one it came from.
- **State:** `status` (`TEMPORARY`, `PROCESSED`, `FAILED`; `dto/enums/TransactionStatus.java:3` – `:7`), `asset_request` (the original
  request, `AssetRequestConverter`, encrypting PII on a held trade), and `trade_flow` (`V1` or `V2`, set by the server from the route).
- Indexes are declared on the Flyway migration itself, not created at start: `PortfolioIndexInitializer`, which used to build them on
  startup and carry on when one could not, is gone (W-337e) — a migration either lands cleanly or fails the deploy.

**`profit_and_loss`** (`entity/ProfitAndLossEntity.java`), one user's financial year:
- `email` (encrypted, searchable) and `financial_year` (`2025-2026`), unique together on `pnl_user_year_idx`.
- **`version`**: optimistic locking, because every trade reads, changes and writes the whole row. Under MongoDB this was pre-empted in
  practice by the database's own write-conflict detection on the replica set; under PostgreSQL there is no document-level equivalent, so
  `version` is now the only place two concurrent trades for the same user and year are caught (`TradeCollisionRetry` retries on it).
- **`realised_profits_json`** for the user's own account (`SELF`) and **`out_sourced_realised_profits_json`** for any other account
  type, each a `RealisedProfits` (nullable — see above) holding two hierarchies:
  - **Capital gains**, `gains_by_asset_and_segment`: per asset type and segment a `FinancialReport` → `capital_gains` per month
    (`MonthlyReport`) → `first_half_report` (days 1 – 15) and `second_half_report`. Every level carries `purchase_amount`, `sell_amount`,
    `profit`, `misc_charges` and `brokerage` (`entity/model/ReportModel.java`; `FinancialReport.java`; `MonthlyReport.java`).
  - **Charges**, `yearly_charge_summary` → months → halves, each `amount_by_code` and `total_charges` (`brokercharges`'
    `ChargeSummaryReport`).

**`trade_outcomes`** (`entity/TradeOutcomeEntity.java`), one row per lot a sale consumed, or one per property sale:
- **Who and what:** `email` and `account_holder` (encrypted), `account_type`, `stock_code`, `stock_name`, `exchange_name`, `broker_name`,
  `asset_type`, `segment`, `instrument_sub_class` (null until an instrument registry exists).
- **The buy side:** `original_buy_price`, `ca_adjusted_buy_price`, `buy_quantity`, `buy_date`, `buy_broker_charges`, `buy_misc_charges`,
  and `buy_charge_breakup` by charge code.
- **The sell side:** `sell_price`, `sell_quantity`, `sell_date`, the same charges, and `sell_charge_breakup`.
- **Figures:** `total_buy_value`, `total_sell_value`, `net_profit`, `profit_percentage`, `holding_period_days`, `financial_year`.
- **Lineage:** `source_sell_transaction_id`, `source_buy_lot_id`, `is_ca_derived`, `applied_corporate_actions`, `acquisition_mode`,
  `acquisition_corporate_action`, `buy_exchange_name`, `buy_segment`, `sell_exchange_name`, `sell_segment`, `audit_metadata`.
- **Indexes:** `{email, sell_date}` and `{email, source_buy_lot_id}`, declared on the migration.

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

**Collisions** (`TradeCollisionRetry.java`). Every trade rewrites the user's one `profit_and_loss` row for the year. Under PostgreSQL
(W-337e, D-949 C5a) a collision surfaces as one of three things, none of them Mongo's `TransientTransactionError`: an
`OptimisticLockingFailureException` from `ProfitAndLossEntity` or `AssetEntity`'s own `@Version` check; a lock timeout or deadlock
(`CannotAcquireLockException`, Postgres `40P01`); or two attempts both creating the user's first `profit_and_loss` row for the year at
once, a `DataIntegrityViolationException` on `pnl_user_year_idx`. The trade is then run again from a fresh copy of its request: up to 5
more times, waiting 20 ms and doubling to at most 500 ms, with 10 ms of jitter.

**The views** (`PortfolioQueryService.java:62` – `:179`). A holding is the lots of one stock, broker and account holder with something
left:
- **Quantity** is their sum, and **price** the value-weighted average of their prices.
- **Charges** are summed.
- **`transactionQuantities`** gives the quantity per buy date, and **`daysHeld`** the days from each buy date to today.

**The assets rebuild** (`service/AssetsRebuild.java:78` – `:222`; `LotLedger.java:33` – `:205`) replays one user's stored,
non-temporary trades into lots in memory, using the same rules the live paths use. Events run by date, and on one date the performed
splits and demergers come before the trades. Each buy takes the shape of the flow it recorded. A buy stored before flows were recorded
is shaped from the stored lot that lists it, and the report names each such buy. The rebuilt lots are compared with the stored ones.
With `dryRun=false`, a lot whose figures match its stored pair is left untouched — no delete, no reinsert, its audit trail undisturbed
(W-337e); a lot that differs or is new is written, keeping its paired stored lot's id, order times, corporate actions and creation stamp;
a stored lot with no rebuilt pair is deleted. `report.lotsWritten` still counts every paired-or-new lot, whether or not its row was
actually touched — it tracks replay coverage, not persisted rows (`AssetsRebuild.java`).

**Erasure** (`UserRecordsErasureService.java:60` – `:97`) deletes, in this order:
- `assets`, `trade_outcomes`, `transactions` and `profit_and_loss`;
- the held trades and `lastly_performed_corporate_action`;
- `user_charges` and `charge_accounts`.

Each delete runs even when an earlier one failed, and the report names what failed. Account erasure (W-237d) calls it and fails if
anything is left (`service/PortfolioRecordErasure.java:25` – `:31`).

**A V2 sale's outcome rows** (`service/TradeOutcomeRecorder.java:60` – `:177`), one per matched lot:
- **The sale's charges** are split across the lots by quantity, per charge code. Each lot takes the running total up to it, less
  what earlier lots took, so the parts sum exactly to the sale's charge (`:105` – `:123`). With no engine figure, the entered
  `brokerCharges` is split the same way, outside the breakdown (`:72` – `:75`).
- **The lot's buy charge** is taken once (`LotChargeAllocator`, `:166` – `:167`). The buy charges by code come from the engine's row for
  the lot's first buy, pro-rated (`:129` – `:131`, `:239` – `:255`; gap 10).
- **Figures:** total buy = price × quantity + buy charges; total sell = sale price × quantity − sell charges; net = sell − buy; each
  to paise (`:162` – `:177`).

V1 writes its rows in `PortfolioService` with no breakdown by code (`PortfolioService.java:690` – `:762`).

**The P&L** (`ProfitAndLossService.java:85` – `:609`):
- **A V2 sale** folds its outcome rows into its sale date's year, under the sale's asset type (else the lot's) and segment (delivery
  when absent). Each row adds its cost, sale value, net profit, brokerage and misc to the year, the month and the half
  (`:363` – `:387`, `:415` – `:511`).
- **A V1 sale** folds its own figures per lot. The fortnight's profit is the price gain less charges, and the levels above add
  what it added (`:85` – `:223`).
- **A V2 trade's computed charges**, buy or sale, fold into the charge hierarchy by code (`:551` – `:609`). V1 writes none
  (`:537` – `:539`).

**The capital-gains summary** (`CapitalGainsSummaryService.java:60` – `:179`) is what tax planning reads.
- **Rows:** those sold within the year (by sell date), grouped into buckets of equal asset type, sub-class, STT evidence on each side,
  venue on each side, acquisition, buy date and sell date.
- **What a bucket publishes:** consideration, cost and each side's charges by code plus misc, never a gain.
- **STT evidence:** `PAID` when the breakdown has STT, `NOT_PAID` when the breakdown has none, `UNKNOWN` when there is no breakdown.
- **Refusal:** a year with a disposal that bore broker charges but kept no breakdown is refused.

**Analytics** (`AnalyticsService.java:49` – `:283`), from the stored rows, held trades left out:
- **The summary:** invested (price × quantity of lots), realised profit (the own account's capital gains), and trade counts.
- **The allocation:** invested by asset type and its share.
- **Performance:** wins, losses and break-evens from `trade_outcomes`; their averages; the ratio, absent with no loss; average holding
  days; sell ÷ buy value; the best and worst stock.
- **XIRR:** over buy and sale cash flows with charges. Open lots are valued at the caller's `currentPrices` today. A stock without a
  price is left out whole and named.

**The export** writes `assets` (grouped as the views group them, filtered), every transaction, or a one-row template, as `.xlsx`
(`EntityExportService.java:45` – `:102`).

**A property sale** is one `trade_outcomes` row (`PropertySaleService.java:49` – `:151`):
- quantity 1, the cost and price to paise, and the expenses by `PropertyExpense` code on each side;
- `LAND_OR_BUILDING`, `PURCHASE`, the account `SELF`;
- no lot, transaction or P&L entry;
- its id a digest of user, name and both dates, so the same sale is refused a second time.

**The P&L rebuild** (`ProfitAndLossRebuild.java`) folds the user's `trade_outcomes` and trade `user_charges` through the live
fold steps (`foldSale`, `foldCharges`) into fresh rows, one per financial year. It compares them, year by year, with the stored ones;
with `dryRun=false` a year with a stored row is updated in place, under that row's own id, rather than deleted and reinserted — both
would land in the same flush, and Hibernate runs inserts before deletes, so a blind delete-then-insert on `pnl_user_year_idx`'s
`{email, financial_year}` would collide with the row it is about to delete (W-337e). A year with no rebuilt row is deleted; a year
stored more than once (a race before the version field) keeps one row and deletes the rest, so duplicates still fold into one.

**The trade-outcome rebuild** (`migration/TransactionBasedTradeOutcomeMigration.java:53` – `:142`) works for each user with
transactions and **no** outcome row:
- buys of one stock, broker, holder, segment and date merge into one lot at the weighted price (`TradeMatchingService.java:36` – `:132`);
- every sale, by date then id, is matched oldest lot first;
- a user with any row is skipped and listed.

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

**One sale from outcome row to P&L** (V2, ₹0 charges). Buy 10 at ₹100 on 10 Jan 2026; sell 4 at ₹150 on 5 Mar 2026.
- **The row:** cost 4 × 100 = ₹400, sale value 4 × 150 = ₹600, net ₹200, 54 days held, year `2025-2026`.
- **The P&L:** `2025-2026` → EQUITY → DELIVERY → March → first half each gain purchase 400, sale 600, profit **₹200**. The lot keeps 6.

**The P&L and analytics scenarios** `ProfitAndLossGapAuditIntegrationTest` asserts (W-264d's table; Zerodha equity delivery, ₹0 charges):

| Scenario | Expected |
|---|---|
| V2: 10 @ ₹100 (10 Jan 2026), sell 4 @ ₹150 (5 Mar 2026) | ₹200 for `2025-2026`, in March's first half |
| Sales on 31 Mar 2026 and 1 Apr 2026, ₹50 gain each | ₹50 in `2025-2026`, ₹50 in `2026-2027` |
| Sales on 15 and 16 Mar | ₹50 in each half |
| The first scenario through V1 | the same ₹200 |
| The P&L read as `2025-26`; as `2027-2028` with nothing traded | 400 naming `2025-2026`; 200, empty |
| The summary after the first scenario plus a buy held by a pending bonus | `totalTrades` 2; invested 6 × ₹100 = ₹600 |
| The allocation after the first scenario | EQUITY ₹600, 100 % |
| One win of ₹200, no loss | `winLossRatio` absent, not 0.0 |
| XIRR: buy 10 @ ₹100 on 1 Jan 2025, sell 10 @ ₹110 on 1 Jan 2026 | 10 % (1,100 ÷ 1,000 − 1 over 365 days) |
| The same plus a held buy | still 10 %: the held buy is no cash flow |
| TCS and INFY held, a price for TCS only | INFY left out whole and named |
| TCS priced twice | 400 naming TCS |

## 6. Configuration

| Property | Shipped | Effect |
|---|---|---|
| `app.portfolio.idempotency.window-seconds` | 60 in all three profiles (`application.yaml:179`) | the fingerprint window; 0 turns the fingerprint fallback off; absent means 60 (`config/IdempotencyProperties.java:22` – `:35`) |
| `app.portfolio.trade-exchanges.accepted` | EQUITY and MUTUAL_FUND: NSE, BSE; BOND and GOLD_BOND: NSE, BSE, OFF_EXCHANGE; FD and INSURANCE: OFF_EXCHANGE (`application.yaml:183` – `:189`) | the exchanges a trade may name; a type not listed accepts none; absent stops the start (`config/TradeExchangeProperties.java:19` – `:34`) |
| `app.charges.*` | see `docs/modules/brokercharges.md` §6 | whether a trade is priced, and whether a V2 buy's cost is the computed total |

A trade spanning several tables is all-or-nothing under PostgreSQL's own ACID transaction (`@Transactional("postgresTransactionManager")`,
`PortfolioService.java:80`) — unconditionally, unlike the Mongo era's `app.mongodb.transactions-enabled` flag and replica-set
requirement, both gone with the port (W-337e).

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
| `ProfitAndLossServiceTest` | 40 |
| `TradeOutcomeRecorderTest` | 29 |
| `CapitalGainsSummaryServiceTest`, `CapitalGainsSummaryTest` | 23, 17 |
| `TradeMatchingServiceTest` | 20 |
| `AnalyticsServiceTest`, `AnalyticsIntegrationTest` | 14, 13 |
| `ProfitAndLossGapAuditIntegrationTest` | 12 (§5) |
| `ProfitAndLossIntegrationTest`, `PropertySaleIntegrationTest` | 10, 9 |
| `ProfitAndLossRebuildIntegrationTest`, `TradeOutcomeRebuildIntegrationTest`, `EntityExportIntegrationTest`, `PropertySaleServiceTest` | 7, 6, 6, 6 |
| others | `taxplanning`'s capital-gains adapter tests read the summary |

Counts are `@Test` / `@ParameterizedTest` annotations per file (grep), not executions. `corporate`'s and `brokercharges`' tests drive
these routes too.

## 8. Known gaps (1 – 9 placed as W-385, D-911; 10 – 17 as W-386, D-912)

1. **A caller can overwrite any stored transaction, another user's included.**
   - **How:** `AssetRequest.tempTransactionId` binds from the request body (`dto/AssetRequest.java:28` – `:33`). `asTransaction` makes it the
     new row's id and its `sourceTempTransactionId` (`:98`, `:120` – `:122`). `recordTransaction` stops only when one of the caller's
     own processed trades already came from it (`TransactionService.java:73` – `:79`), then saves, and a save by id replaces whatever has
     that id (`:96` – `:104`). The held-trade path saves the same way (`TemporaryTransactionService.java:51` – `:56`).
   - **Shown at the build** (a throwaway integration test, deleted): a V2 buy naming the caller's own TCS transaction id left that row as
     WIPRO. A second user naming the first user's id took the row over: the first user's transaction list came back empty, and the
     second user's held a WIPRO row under that id. The first user's lot still lists the lost transaction.
   - **Who can:** any signed-in user who knows or guesses a transaction id. A user sees their own ids in `/transactions`; another's are
     random UUIDs (W-337a's convention, since the port), not ordered by time the way a Mongo `ObjectId` was.
2. **A sale takes lots of any segment.**
   - **How:** a sale's lots are keyed on email, stock, broker, holder and date only (`PortfolioRepository.java:26` – `:30`), and the
     rebuild matches the same way (`LotLedger.java:105` – `:109`). A buy, by contrast, merges only within one segment (W-296).
   - **Shown at the build:** delivery 10 and intraday 5 bought, then an intraday sale of 5, left **delivery 5 and intraday 5**. The sale was
     costed from the delivery lot.
3. **One date's lots are not ordered.** A sale sorts only by `transaction_date` (`PortfolioRepository.java:28`; `LotLedger.java:108`), so
   among V2's several lots of one date the order is whatever Postgres returns with no tiebreaker — row order with no `ORDER BY` on the
   tie is unspecified there exactly as it was under Mongo — though `LotLedger`'s contract says "in the order they were made"
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
   - **The wall clock:** closed by W-402 (D-959): the future-date refusal, `daysHeld` and a dateless trade's date take "today" from
     the app's `Clock` (the business zone, D-958), and the fingerprint window and `submitted_at` are UTC from it; `ArchitectureTest`
     refuses a bare `LocalDate.now()` / `LocalDateTime.now()`.
   - **Dead weight:** `recordTransaction` defaults a missing date to today (`TransactionService.java:60` – `:62`), which no path can reach,
     since every path refuses one first (`PortfolioService.java:250` – `:252`).
8. **Conventions.** `@AllArgsConstructor` where CLAUDE.md asks `@RequiredArgsConstructor`: `PortfolioController.java:28`,
   `TransactionController.java:19`. The erase answer prints the user's email and answers 200 even when
   partial (`PortfolioController.java:69` – `:73`). `MongoTemplateService` and `PortfolioIndexInitializer` — the latter's own instance of
   this same gap, catching `RuntimeException` to start without an index — are both gone with the port to Postgres (W-337e): indexes are
   now declared on the Flyway migration, which either applies cleanly or fails the deploy outright.
9. **Four routes have no request in `api-collection/`** (grep at `41a7123`): `GET …/mutual-funds`, `POST …/stocks/search`, and the two
   admin holdings reads.

10. **A V2 sale's buy charges by code are pro-rated against what is left of the lot, not what was bought.**
    - **How:** `share = quantity sold ÷ quantity before this sale` is applied to the **whole** buy breakdown each time
      (`TradeOutcomeRecorder.java:129` – `:131`, `:248` – `:255`; the second figure is the lot before the sale, `PortfolioService.java:645`,
      `:652`). That is the B-7 mistake the rupee total no longer makes.
    - **Shown at the build** (a throwaway integration test, deleted): a V2 buy of 3 TCS at ₹1,000 cost ₹3.11 (STT 3, exchange 0.09,
      GST 0.02). Three one-share V2 sales recorded `buy_broker_charges` 1.04 + 1.04 + 1.03 = ₹3.11, which is right, but buy STT of
      **1.00 + 1.50 + 3.00 = ₹5.50** and exchange 0.03 + 0.05 + 0.09 = ₹0.17.
    - **Consequence:** the capital-gains summary hands the tax side acquisition charges by code that exceed what was paid
      (`CapitalGainsSummaryService.java:100` – `:109`). A lot sold whole in one sale is unaffected.
11. **The trade-outcome rebuild's rows differ from the live ones.**
    - **Lots:** it merges same-day **V2** buys too (`TradeMatchingService.java:36` – `:49`, "exactly like PortfolioService.buyStock()"),
      names lots `lot-{stock}-{date}` rather than the stored lot's id (`:62`), and writes no charge breakdown. So the per-holding
      charge view cannot find its rows, and a year whose rows bore charges is refused by the capital-gains summary.
    - **Matching:** it has no "bought on or before the sale" bound (`:165` – `:172`), and ignores segment as the live sale does (gap 2).
12. **The V1 P&L still adds raw doubles** at the fortnight (`ProfitAndLossService.java:212`, `:215`), the drift B-5 names. A V1 sale's
    method is `@Deprecated(forRemoval = true)` though V1 is live (`:84`).
13. **Silent branches:** a SELL with a corporate-action type, and an unknown side, reach no figure, the second with only an error log
    (`ProfitAndLossService.java:321` – `:330`).
14. **The transactions export ignores the request's filters and includes held trades** (`EntityExportService.java:55` – `:56`;
    `TransactionService.java:124` – `:126`), where the assets export applies them.
15. **Javadoc that is no longer true:** `ProfitAndLossService` links `com.thiru.wealthlens.entity.ProfitAndLossEntity`, an old package
    (`ProfitAndLossService.java:67`, `:277`); "an EQUITY gate below … nothing here may touch cost basis until Phase C" (`:343` – `:347`), where no gate follows
    and `authoritative` sets the cost; `RealisedProfits` describes a `yearlyBrokerCharges` hierarchy "deleted in Chunk 11" beside it
    (`RealisedProfits.java:18` – `:26`).
16. **The wall clock:** closed by W-402 (D-959): `calculateXirr`'s date, the export's file name and template rows take the app's
    `Clock` (the business zone's date and time); the summary's `lastUpdated` is UTC from it.
17. **Conventions:** `@AllArgsConstructor` on `ProfitAndLossService.java:49`, `EntityExportService.java:34` and
    `EntityExportController.java:18`; `PropertySaleRequest` carries no constraint, so its `@Valid` checks nothing
    (`dto/PropertySaleRequest.java:17` – `:37`).

## 9. Not covered here

- Which charges reduce a gain, the term and the tax: the tax side, `taxengine` and `taxplanning` (W-357b9, W-357b10).
- What a corporate action does to a lot: `docs/modules/corporate.md`. What a trade is charged: `docs/modules/brokercharges.md`.

## 10. Names the code cites from the retired trade-ledger design

The trade ledger's notes (`docs-history-m1:docs/trade-ledger/`) numbered their work TL-n and their findings B-n. These are the ones the
code still cites, each as the notes stated it, with what holds today.

| Name | What it says | Today |
|---|---|---|
| TL-6 | The trade-outcome rework: segment on each row, V2 writing itemised outcome rows, gains split by classification | rows carry `segment`; the P&L files gains by asset type and segment, facts only (`RealisedProfits.java:30` – `:37`) |
| TL-7 | Trade idempotency: a client key, unique-indexed; a fingerprint inside a short window when none; a repeat returns the first result; two identical trades on one day both accepted | as decided (§5 "Recording a trade") |
| TL-8 | Money stays `double`, and every stored amount is canonicalised to paise through `TMoney` (Option B); `BigDecimal` end to end (Option C) waits for a trigger | as decided (`shared/util/money/TMoney.java`). The five triggers, verbatim below |
| B-4 | `profit_and_loss` and `assets` could not be rebuilt from `transactions` | closed: both rebuilds (§5; W-283, D-575; W-283a3, D-586) |
| B-5 | V1 accumulated with raw `+=` | marked closed 2026-09-12, but the V1 fortnight's misc and profit still add raw doubles (gap 12, W-386) |
| B-6 | Pro-rating lost a paisa across lots | fixed: each code allocated cumulatively, the parts summing to the whole (`TradeOutcomeRecorder.java:105` – `:123`) |
| B-7 | A lot's buy charge was charged again on every partial sale (₹18.33 for ₹10 paid) | fixed for the rupee total (`LotChargeAllocator`); not for V2's breakdown by code (gap 10, W-386) |
| B-8 | V1 had the same over-allocation | fixed in code; **the stored rows written before the fix** were left as a data question (W-387) |

**TL-8's triggers** ("What would make Option C the answer"; any one of them, and the calculus changes):
- Sub-paise amounts ever need storing — a per-unit rate, an unrounded intermediate, a foreign currency.
- Multi-currency, which brings its own scale and rounding rules per currency.
- A regulator or auditor requires exact decimal storage. For a personal tracker this is remote; for anything filed on someone else's
  behalf it is not.
- Aggregation moves into MongoDB: database-side `$sum` over BSON `Double` reintroduces drift outside application control.
- Option B is tried and decays anyway: if the ArchUnit rule is fought rather than followed, the type should carry the guarantee instead.

`taxengine` and `taxplanning` compute money as `Money`, not `double`, because two of these triggers fire for a tax computation
(`docs/taxplanning-design/19-engineering-foundations.md`).
