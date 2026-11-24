# Module `corporate` — corporate actions: recorded, then applied per user and broker

> W-357b5 (D-906), written from the code at `review/taxplanning` on 2026-11-22. Every claim cites the line it was read from. Template:
> `docs/taxplanning-build/plans/W-357b.md` C2a. This doc also holds the hand-worked figures `StockSplitIntegrationTest` and
> `CorporateActionGapAuditIntegrationTest` assert (§5), moved here from W-213's and W-264c's plans, which W-357b5 removed (W-357b C1a); both
> are still readable, e.g. `git show docs-history-m1:docs/taxplanning-build/plans/W-213.md`.

## 1. Purpose

A corporate action is what a company does to its shares: a bonus, a split, a demerger, a rights issue, a buyback, a dividend, or a change of
name or symbol. An admin **records** an action once, for everyone. Each user then **performs** a quarter's actions on their own lots, broker by
broker. While an action that would change a holding is pending, a new trade on that stock is held `TEMPORARY` (`portfolio`), so it is
neither priced nor counted against lots the action has not reached yet.

## 2. Boundaries

- `OPEN` module, `allowedDependencies = {"shared", "portfolio"}` (`backend/src/main/java/com/thiru/wealthlens/corporate/package-info.java:1` – `:5`).
- It reads and writes `portfolio`'s lots and transactions through `PortfolioService`, `TransactionService` and
  `TemporaryTransactionService` (`service/CorporateActionService.java:17` – `:19`, `:50` – `:52`).
- `portfolio` lists `corporate` (CLAUDE.md's table) and reads `CorporateActionType.FILTERABLE_CORPORATE_ACTIONS` to hold trades.
- URL segment `corporate-actions` (`shared/web/ApiZones.java:26` – `:27`).

## 3. Routes

| Method and path | Zone | Body → answer | Where |
|---|---|---|---|
| `POST /api/v1/admin/corporate-actions` | admin | `CorporateActionDto` → text "Corporate action: … added successfully …" | `controller/CorporateActionAdminController.java:27` – `:33`; `service/CorporateActionService.java:57` – `:79` |
| `PATCH /api/v1/admin/corporate-actions/{id}` | admin | `CorporateActionPriority` (`priority`) → text | `CorporateActionAdminController.java:35` – `:41`; `CorporateActionService.java:89` – `:103` |
| `DELETE /api/v1/admin/corporate-actions/{id}` | admin | — | `CorporateActionAdminController.java:43` – `:47` |
| `PUT /api/v1/admin/corporate-actions/perform` | admin | `CorporateActionDto` → text; only `NAME_OR_SYMBOL_CHANGE` | `CorporateActionAdminController.java:49` – `:55`; `CorporateActionService.java:230` – `:242` |
| `GET /api/v1/me/corporate-actions/{id}` | me | → `CorporateActionResponse` | `controller/CorporateActionController.java:24` – `:27` |
| `GET /api/v1/me/corporate-actions?ids=…` | me | → a page of `CorporateActionResponse`, latest record date first | `CorporateActionController.java:29` – `:35`; `CorporateActionService.java:118` – `:122` |
| `PUT /api/v1/me/corporate-actions/perform?allBrokers=` | me | `CorporateActionPerformDto` (`month`, `year`, `brokerName`) → the same body | `controller/UserCorporateActionController.java:20` – `:25`; `CorporateActionService.java:129` – `:169` |

The admin routes also carry `@PreAuthorize` for `SUPER_USER`, on top of the zone's rule (`CorporateActionAdminController.java:27`, `:35`,
`:43`, `:49`).

**Refusals (400 unless said):**

| When | Message | Where |
|---|---|---|
| No `type` | "Field 'type' must not be null" | `dto/CorporateActionDto.java:21` – `:23` |
| No stock code; a code with an outer space (stock, to-stock, demerger stocks) | "Stock code is missing"; "Stock code \"…\" has a leading or trailing space …" | `CorporateActionService.java:588` – `:599`, `:577` – `:582` |
| No record date / ex-date | "Record date is invalid" / "Ex date is invalid" | `:603` – `:608` |
| A split ratio not two positive whole numbers | "A stock split's ratio is new shares to old shares, two whole numbers above nil such as 2:1, but it was …" | `:59` – `:61`; `dto/SplitRatio.java:22` – `:29` |
| A demerger: no detail; a ratio not two positive whole numbers; ratio, stocks and price ratio of different lengths | "Demerger data is missing"; "A demerger's ratio is new shares to shares held …"; "Demerger ratios must have one more element …"; "Demerger ratios and price ratios must have the same length" | `:616` – `:637` |
| A bonus ratio without one `:` | "Invalid bonus ratio format" | `:639` – `:644` |
| The same type already recorded for that stock and record date | "Corporate actions is already present id: …" | `:64` – `:70` |
| Another action that stock and date already has this priority | "Update the priorities for the corporate actions with Ids: …" | `:72` – `:75` |
| Perform: no broker and not `allBrokers` | "Invalid action perform details: …" | `:131` – `:133` |
| Perform: a month that does not start a quarter | "A quarter starts in one of [JANUARY, APRIL, JULY, OCTOBER], but the month was '…'" | `:171` – `:178` |
| Perform: a bonus with no allotment date | "The bonus on … has no allotment date; a bonus share's holding period runs from its allotment (…)" | `:273` – `:278` |
| Admin perform of a split | "A stock split applies per user and broker, through PUT /api/v1/me/corporate-actions/perform" | `:235` – `:236` |
| A caller without `SUPER_USER` on an admin route | 403 | the zone; `CorporateActionGapAuditIntegrationTest` |

## 4. Stored data

PostgreSQL tables (W-337d1, D-945; Flyway `db/migration/corporate/V2__corporate.sql` and `V7__corporate_action.sql`). Primary keys are caller-assigned `VARCHAR(255)` with `@PrePersist` UUID default.

**`corporate_action`** + child table **`corporate_action_demerger_stocks`** (`entity/CorporateActionEntity.java:29` – `:98`), one row per recorded action:
- **Plain columns:** `stock_code`, `stock_name`, `to_stock_code`, `to_stock_name`, `description`, `action_price`, `ratio` (`TEXT`).
- **`type` and `asset_type`:** `@Enumerated(EnumType.STRING)` (`:52` – `:58`).
- **Dates:** `record_date`, `ex_date`, `allotment_date` (`DATE`, `:63` – `:72`).
- **`priority`** (`INT`, `:74` – `:75`).
- **`demerger_detail`** (`entity/model/DemergerDetail.java:14` – `:45`): `demerger_ratio`, `demerger_price_ratio`, `main_stock_code`, `main_stock_name` embedded on `corporate_action`, and `demerger_stocks` (`stock_code`, `stock_name`) as an eager `@ElementCollection` in `corporate_action_demerger_stocks` (`corporate_action_id` FK).
- **`created_by`, `updated_by`, `created_at`, `updated_at`** (`AuditColumns`, `:87` – `:89`).
- **Indexes:** `corporate_action_stock_record_idx (stock_code, record_date)`, `corporate_action_type_record_idx (type, record_date)`, `corporate_action_demerger_stocks_ca_idx (corporate_action_id)`.

Every email in it is an actor's, inside `AuditColumns`.

**`lastly_performed_corporate_action`** (`entity/LastlyPerformedCorporateAction.java:36` – `:79`) has one row per user, stock, asset type, action type and broker: `email` (encrypted, searchable via `SearchableColumn`, indexed `lastly_performed_corporate_action_email_idx`), `stock_code`, `asset_type`, `action_type`, `action_date`, `broker_name`, and `AuditColumns`. It is the duplicate guard (§5), read by `findLastPerformedCA` (`repository/LastlyPerformedCorporateActionRepo.java:16` – `:21`).

A performed action is also copied into each lot's `corporate_actions` and each touched transaction's (`portfolio`).

## 5. Behaviour

**Which actions apply.**
- `BONUS`, `STOCK_SPLIT` and `DEMERGER` hold trades and are performed (`dto/enums/CorporateActionType.java:16` – `:23`;
  `CorporateActionService.java:215` – `:220`).
- `RIGHTS_ISSUANCE`, `BUYBACK` and `DIVIDEND` are only recorded (W-316).
- `NAME_OR_SYMBOL_CHANGE` is applied only by the admin perform (`:244` – `:267`). It renames every user's lots and transactions of that
  stock dated **before** the record date, for all users at once (`portfolio/service/PortfolioService.java:783` – `:785`,
  `portfolio/service/TransactionService.java:107` – `:109`), and is not itself stored.

**A user's perform** (`:129` – `:223`):
1. **The quarter** is that `month` (JANUARY, APRIL, JULY or OCTOBER) of `year`, to the day before the next quarter starts (`:147` – `:157`).
2. **The actions:** those three types with a record date inside it, oldest record date first, then priority (W-314;
   `repository/CorporateActionRepository.java:26` – `:28`).
3. **Skipped:** an action on a stock this user holds a `TEMPORARY` trade in from before its record date (`:184` – `:194`).
4. **Skipped:** an action already performed for this broker, where the guard row's `action_date` is on or after the record date
   (`:203` – `:211`).
5. **Applied:** the rest, to this broker's lots bought **before** the record date (`portfolio`'s `stocksForCorporateActions`). Then a guard row
   is written with the **ex-date** as its `action_date` (`:292`, `:444`, `:472`, `:651`). See gap 1.
6. **No lots:** a user with none still gets the guard row (`:646` – `:652`).

**A bonus** (`:269` – `:413`), per **account holder** at the broker (W-312, `:297` – `:317`):
- **Count:** the holder's whole shares × new ÷ old, rounded down to whole shares (`:323` – `:327`).
- **One new lot:** at price 0, dated the **allotment date**, with no charges, sales or actions of its own (`:336` – `:345`, `:363` – `:370`).
- **One new transaction:** copied from the holder's first buy, with price, value, charges and identifiers cleared (`:383` – `:402`). More than
  one new transaction refuses (`:330` – `:333`).

**A split** (`:453` – `:491`):
- Each lot becomes quantity × new ÷ old at price × old ÷ new (`dto/SplitRatio.java:31` – `:39`). Cost, date and charges are unchanged, and
  transactions are left as the record of what was traded (W-213, D-438).
- The ratio is parsed before anything changes, so a malformed stored one refuses and writes no guard row (`:455`).

**A demerger** (`:493` – `:574`):
- **The ratio** is read **new:held** (W-313; `:506` – `:509`, `dto/context/DemergedStockContext.java:3` – `:6`).
- **The price ratio** is read as two **percentages**, main first (`:511` – `:513`).
- **Exactly one** demerged stock is allowed (`:515` – `:517`).
- **The held lot** keeps its quantity and date, its price × main% ÷ 100, and is renamed to the main stock (`:544` – `:549`).
- **A new lot** of the demerged stock: quantity × new ÷ held, price × other% ÷ 100 × held ÷ new, the same date and holder (`:554` – `:574`).

The bonus, split and demerger lot steps are public and static or pure, so the `assets` rebuild (§2.12) replays exactly what a perform does
(`:363`, `:485`, `:504`).

### Worked examples (by hand; the tests assert these)

**Splits** (`StockSplitIntegrationTest`; W-213's table):

| Lot before | Ratio | Lot after | Cost before = after |
|---|---|---|---|
| ZERODHA 100 @ 1,000, charges 20 | 2:1 | 200 @ 500, charges 20 | 1,00,000 |
| ZERODHA 30 @ 900 | 2:1 | 60 @ 450 | 27,000 |
| UPSTOX 40 @ 800, performing for ZERODHA | 2:1 | unchanged | 32,000 |
| ZERODHA bought on or after the record date | 2:1 | unchanged | — |
| ZERODHA 100 @ 1,000 | 3:2 | 150 @ 666.67 | 1,00,000 |
| ZERODHA 100 @ 1,000 | 1:2 | 50 @ 2,000 | 1,00,000 |

After the 2:1 split, a V2 sale of 50 at 700 from the first lot costs 50 × 500 = ₹25,000, with the buy date unchanged.

**The gap-audit scenarios** (`CorporateActionGapAuditIntegrationTest`; Zerodha equity delivery bought through V2 at ₹0 charges; W-264c's
table):

| Scenario | Expected |
|---|---|
| Bonus 1:2 on 3 shares | +1 (3 × 1 ÷ 2 = 1.5, whole shares only) |
| Split 2:1 on 10 @ ₹500 | 20 @ ₹250; cost ₹5,000 |
| 5 bought the day before the record date, 7 on it; bonus 1:1; a held buy of 3 after it | bonus on the 5 only; after redrive 5 + 7 + 5 + 3 = 20 |
| Demerger 1:1, price 80:20, on 10 A @ ₹1,000 | A 10 @ ₹800, B 10 @ ₹200; ₹10,000 in all |
| Sell 5 B at ₹300 through V2 | recorded; gain 5 × (300 − 200) = ₹500 |
| Perform the same quarter twice | the second changes nothing |
| Split 2:1 (record 10 Jan) and bonus 1:1 (record 20 Jan), one quarter, on 10 @ ₹100 | 20 @ ₹50, then +20 @ ₹0 = 40, cost ₹1,000 |
| A month not starting a quarter | 400 naming the four months |
| Admin routes without `SUPER_USER` | 403 |
| Self 10 and Mother 6 at one broker; bonus 1:1 | Self 10 + 10, Mother 6 + 6 |
| Self 3 and Mother 3; bonus 1:2 | 1 each (pooled would give 3) |
| ITC's demerger: 1,000 ITC @ ₹400, 1:10 (new:held), price 86.49:13.51 | ITC 1,000 @ ₹345.96 (₹3,45,960); ITC Hotels 100 @ ₹540.40 (₹54,040); ₹4,00,000 kept |
| A demerger ratio not two whole numbers | 400 naming the form |
| A rights issue (15 Jan) and a bonus (20 Jan) in one quarter; a buy after the rights issue | the buy is recorded at once; the quarter's bonus applies |

## 6. Configuration

None of its own. A multi-document perform is all-or-nothing only on a replica set with `app.mongodb.transactions-enabled`
(`CorporateActionService.java:124` – `:129`).

## 7. Tests

| Class | Cases |
|---|---|
| `CorporateActionIntegrationTest` | 48 |
| `CorporateActionGapAuditIntegrationTest` | 15 (above) |
| `StockSplitIntegrationTest` | 7 (above) |
| `SplitRatioTest` | 4 |
| `BonusAllotmentIntegrationTest` | 3 |
| `EarlierQuarterHoldIntegrationTest` | 2 |
| others | `portfolio`'s and `taxplanning`'s tests read corporate actions on lots |

## 8. Known gaps (placed as W-381, D-906)

1. **A performed action is applied again when its ex-date is before its record date.** The guard row stores the **ex-date** (`:292`, `:444`,
   `:472`, `:651`), but the skip asks whether that date is on or after the **record date** (`:207`). An ex-date one day before the record date
   (the norm before T+1 settlement) is never skipped.
   - **Shown at the build** (a throwaway test, deleted): a 2:1 split with its ex-date a day before its record date, performed twice, left
     **400 shares, not 200**. With the ex-date equal to the record date the second perform changed nothing.
   - The tests use an ex-date on or after the record date (`CorporateActionGapAuditIntegrationTest` `exDate` = `recordDate`;
     `StockSplitIntegrationTest` record + 5), so none sees it.
   - **A wrong quantity on a user's holdings.** A read-only count of stored actions with `ex_date < record_date` shows which databases could be
     reached.
2. **A bonus ratio is checked only for one `:`.** `a:b` passes entry and fails at perform with Java's own words (`Integer.parseInt`, `:325` –
   `:326`), and a missing ratio is a `NullPointerException` (`:640`).
3. **A demerger's price ratio is not checked to be numbers, or to add up to 100.** It is read as percentages (`:545`, `:570`), so `4:1` would
   price the main lot at 400% of its cost. A missing ratio or stock list is a `NullPointerException` (`:622` – `:630`).
4. **The admin name change acts on every user at once and is not stored** (`:244` – `:267`). It writes no `corporate_action` row and no guard,
   so it is neither listed nor protected from a second run. It renames lots and transactions of that stock dated before the record date, whoever's
   they are (W-356 redesigns renames on SQL).
5. **The empty-holdings path of a bonus does not return** (`:281` – `:283`): it falls through to an empty loop, unlike the split's and
   demerger's (`:460` – `:463`, `:432` – `:435`). It is harmless today and a trap for an edit.
6. **The run log prints whole entities** (`:168`, `log.info(corporateActions)`), audit metadata included (emails are masked by the log
   pattern).

## 9. Not covered here

- How a trade is held `TEMPORARY` and redriven, the lots and the `assets` rebuild: `portfolio`.
- A bonus's holding period in tax: `taxengine` (D-307).
