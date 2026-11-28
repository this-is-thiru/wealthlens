# Module `brokercharges` — what a trade, an account or a redemption costs, priced from rate cards

> W-357b6a (D-908), written from the code at `review/taxplanning` on 2026-11-22. Every claim cites the line it was read from; a bare
> path is under `backend/src/main/java/com/thiru/wealthlens/brokercharges/`. Template: `docs/taxplanning-build/plans/W-357b.md` (tag `docs-history-m3`) C2a.
> This doc also holds the hand-worked figures `ChargeGapAuditIntegrationTest` asserts (§5), moved here from W-264b's review
> (`reviews/2026-11-13-charge-engine-gap-audit.md` §2, deleted by W-357b6b and readable at tag `docs-history-m1`). The design notes it replaces, `docs/charges-engine/`, were deleted by W-357b6b
> (D-909) and are still readable at tag `docs-history-m1`, e.g. `git show docs-history-m1:docs/charges-engine/decisions.md`. The code
> cites that design by name (ADR-n, AC-n, FR-8, OD-8, CE-5, D1 – D10, the PRD, the tech spec); §10 says what each name it cites means.

## 1. Purpose

A broker, the exchange, the regulator, the state and a fund each take a cut of a trade. This module prices those cuts from **rate cards**,
which are data, not code: a card is a list of rules, and adding or repricing a charge is a new or edited rule, not a Java change
(`entity/ChargeRule.java:21` – `:28`). It records what each trade cost, line by line, so a contract note can be rebuilt from one document
(`entity/UserChargeEntity.java:31` – `:42`), and `portfolio` uses that figure as the cost basis on a V2 buy.

## 2. Boundaries

- `OPEN` module, `allowedDependencies = {"shared", "portfolio", "corporate"}`
  (`backend/src/main/java/com/thiru/wealthlens/brokercharges/package-info.java:1` – `:5`).
- **`portfolio` depends on it** (11 classes, grep): `ChargeRecordingGateway` is declared in `portfolio`
  (`portfolio/service/ChargeRecordingGateway.java:8`) and implemented here (`service/ChargeRecordingGatewayImpl.java:49`).
  `PortfolioService` prices each V2 buy and sale through it (`portfolio/service/PortfolioService.java:480`, `:663`); `ProfitAndLossService`
  prices a V1 trade when it is handed no computation (`portfolio/service/ProfitAndLossService.java:527` – `:532`) and folds a V2 computation
  into the P&L charge summary (`:551` – `:558`); erasure deletes `user_charges` and `charge_accounts`
  (`portfolio/service/UserRecordsErasureService.java:73` – `:74`).
- **It reads `portfolio`'s** `TransactionRepository` for the reconciliation (`service/ChargeReconciliationService.java:48`), and
  `corporate`'s `CorporateActionType` on a simulated trade (`dto/request/ChargeSimulationRequest.java:63`).
- URL segment `charges` (`shared/web/ApiZones.java:26`).

## 3. Routes

| Method and path | Zone | Body → answer | Where |
|---|---|---|---|
| `POST /api/v1/me/charges/simulate` | me | `ChargeSimulationRequest` → `ChargeBreakdownResponse`; writes nothing | `controller/ChargeSimulationController.java:37` – `:40`; `service/ChargeSimulationService.java:60` – `:75` |
| `GET /api/v1/me/charges/charge-schedules/{scheduleCode}` | me | → `ChargeScheduleResponse` | `controller/ChargeScheduleController.java:34` – `:37` |
| `GET /api/v1/me/charges/charge-schedules?broker=` | me | → every card of that broker, newest start first | `ChargeScheduleController.java:39` – `:42`; `repository/ChargeScheduleRepository.java:52` |
| `GET /api/v1/me/charges/charge-schedules/unverified` | me | → cards never checked, or checked more than 90 days ago | `ChargeScheduleController.java:51` – `:54`; `service/ChargeScheduleService.java:54`, `:114` – `:117` |
| `GET /api/v1/me/charges/charge-catalogue` | me | → the active codes, sorted by code | `ChargeScheduleController.java:56` – `:59`; `service/ChargeCatalogueService.java:30` – `:34` |
| `POST /api/v1/me/charges/charge-accounts` | me | `ChargeAccountRequest` → `ChargeAccountResponse`; registers or updates by broker and demat id | `controller/ChargeAccountController.java:30` – `:33`; `service/ChargeAccountService.java:34` – `:54` |
| `GET /api/v1/me/charges/charge-accounts` | me | → the caller's accounts | `ChargeAccountController.java:35` – `:38` |
| `GET /api/v1/me/charges/user-charges?from=&to=&assetType=` | me | → a page of `UserChargeResponse`, newest trade date first, the id breaking a tie | `controller/UserChargesController.java:45` – `:53`; `service/UserChargeService.java:78` – `:94` |
| `GET /api/v1/me/charges/user-charges/transaction/{transactionId}` | me | → that trade's contract note | `UserChargesController.java:56` – `:59` |
| `GET /api/v1/me/charges/user-charges/gaps` | me | → rows the engine could not fully price | `UserChargesController.java:68` – `:71`; `UserChargeService.java:97` – `:99` |
| `GET /api/v1/me/charges/user-charges/reconciliation` | me | → computed against entered, per trade | `UserChargesController.java:84` – `:87` |
| `POST /api/v1/admin/charges/charge-schedules` | admin | `ChargeScheduleRequest` → `ChargeScheduleResponse`; publishes, closing the incumbent | `controller/ChargeSchedulePublishingController.java:41` – `:45` |
| `PATCH /api/v1/admin/charges/charge-schedules/{scheduleCode}/close?endDate=` | admin | → the closed card | `ChargeSchedulePublishingController.java:48` – `:53` |
| `POST /api/v1/admin/charges/seed` | admin | → `ChargeSeedReport` | `ChargeSchedulePublishingController.java:69` – `:73` |
| `GET /api/v1/admin/charges/charge-schedules/drift` | admin | → `ChargeScheduleDrift` per shipped card that differs or is absent | `ChargeSchedulePublishingController.java:88` – `:92` |
| `GET /api/v1/admin/charges/charge-accounts/due?frequency=&billedThrough=` | admin | → the accounts a cycle would bill; writes nothing | `controller/AmcCycleController.java:40` – `:46` |
| `POST /api/v1/admin/charges/amc/impose?frequency=&billedThrough=` | admin | → the accounts it billed | `AmcCycleController.java:56` – `:62` |
| `GET /api/v1/admin/charges/user-charges/users/{email}/transaction/{transactionId}`, `…/gaps`, `…/reconciliation` | admin | the three `/me` reads, for one named user | `controller/UserChargesAdminController.java:23` – `:46` |

Every admin route also carries `@PreAuthorize("hasRole('SUPER_USER')")` on top of the zone's rule (`AmcCycleController.java:40`, `:56`;
`ChargeSchedulePublishingController.java:41`, `:48`, `:69`, `:88`; `UserChargesAdminController.java:30`, `:36`, `:42`).

**Refusals (400 unless said):**

| When | Message | Where |
|---|---|---|
| The engine switched off: simulate, due, impose | 503 "The charges engine is disabled (app.charges.engine-enabled=false); no charge was computed or recorded" | `service/ChargeEngineSwitch.java:24` – `:34`; `ChargeSimulationService.java:64`; `service/AmcChargeService.java:65`, `:71` |
| Simulate: no broker, asset type, event or date; quantity ≤ 0; no price or a negative one | "brokerName is required to pick a rate card", "assetType is required …", "event is required: …", "transactionDate is required: …", "quantity must be greater than zero", "price is required; it may be 0 for a free allotment", "price must not be negative" | `ChargeSimulationService.java:81` – `:89` |
| Simulate: a BUY or SELL with no stock code | "stockCode is required on a BUY or a SELL: it finds the scheme's own charges, such as an exit load" | `:98` – `:103` |
| Simulate: a lot with no date, acquired after the sale, quantity ≤ 0, negative price; lots not adding up to the quantity within 0.0001 | "every lot needs an acquisitionDate: …", "a lot acquired on … cannot be disposed of on …", …, "the lots supplied account for … units but the trade disposes of …" | `:113` – `:138` |
| A rule with a rate and a flat amount and no aggregator, at pricing | "Charge rule … declares both a rate and a flat amount but no aggregator, …" | `engine/ChargeEngine.java:380` – `:386` |
| The broker's card and the statutory card both declare a code | "Charge code … is declared on both the broker's card … and the statutory card …" | `ChargeEngine.java:277` – `:283` |
| A derived rule ordered before a base line from another source | "Charge rule … (order …) taxes …, which is priced after it …" | `ChargeEngine.java:237` – `:253` |
| Two cards equally specific with the same start date; two instrument profiles with the same start date | "More than one charge schedule is equally specific for …" / "More than one instrument profile is in force for …" | `engine/ChargeScheduleResolver.java:148` – `:155`; `engine/ChargeInstrumentResolver.java:74` – `:79` |
| A negative rule amount; an expression that will not parse, will not evaluate, or gives a negative amount | "Charge rule … has a negative …. A charge cannot pay the user."; "Invalid charge expression: …"; "Could not evaluate …"; "Charge expression produced a negative amount: …" | `engine/ChargeAmounts.java:25` – `:31`; `engine/ChargeFormulaEvaluator.java:60` – `:63`, `:112` – `:133` |
| Publish: any card defect, all listed at once | "Charge schedule … is invalid: …; …" | `service/ChargeScheduleValidator.java:66` – `:78` (§5) |
| Publish: a card starting on or before the open card it would replace | "Charge schedule … starts on …, which is not after the open schedule …" | `ChargeScheduleService.java:138` – `:144` |
| Read or close an unknown card; close before its start | "No charge schedule with code …"; "Cannot close charge schedule … on …: that end date precedes its start date of …" | `ChargeScheduleService.java:83` – `:95` |
| History with one date bound, or `from` after `to` | "A date range needs both from and to, or neither"; "Date range starts after it ends: … is after …" | `UserChargeService.java:79` – `:84` |
| A contract note for a trade with no row | "No charges recorded for transaction …" | `UserChargeService.java:112` – `:116` |
| Register an account with no broker, demat id or frequency | the field named (W-358d4) | `dto/request/ChargeAccountRequest.java:17` – `:18` |
| A caller without `SUPER_USER` on an admin route | 403 | the zone; the `@PreAuthorize` above |

## 4. Stored data

PostgreSQL tables (W-337d2, D-947; Flyway `db/migration/brokercharges/V4__charge_catalogue.sql` and `V8__brokercharges.sql`). Primary keys are caller-assigned `VARCHAR(255)` with `@PrePersist` UUID default; all 5 tables embed `AuditColumns` (`created_by`, `updated_by`, `created_at`, `updated_at`).

**`charge_schedules`** (`entity/ChargeScheduleEntity.java:36` – `:111`), one rate card:
- **Key:** `schedule_code`, unique (`charge_schedules_schedule_code_idx`, `:44` – `:45`).
- **Scope:** `broker_name` (indexed; null on a statutory card), `asset_type`, `segment`, `exchange`, `plan_code`. Each left null matches anything (`:47` – `:66`).
- **Window:** `start_date` and `end_date`, both inclusive; a null end is the current card (`:68` – `:74`).
- **`status`:** `INACTIVE` means entered in error and unusable for any date; closing a card sets its end date and never its status (`:76` – `:78`).
- **`requires_instrument_profile`**, **`rules_json`** (`TEXT` via `ChargeRuleListConverter`, D-947 C1a), **`source_url`**, **`verified_on`**, and `AuditColumns` (`:80` – `:104`).

**A rule** (`entity/ChargeRule.java:32` – `:149`), serialized inside `rules_json` on a card or instrument profile: `code` (in the catalogue), `display_name`, `category`, `basis`, `side` (`BUY`, `SELL` or `BOTH`), `events`, `amount_basis` (turnover if absent, `:159` – `:161`), `rate`, `flat_amount`, `per_unit_amount`, `slabs[]` (`from_value` inclusive, `to_value` exclusive, `rate` or `flat_amount`; `entity/ChargeSlab.java:20` – `:34`), `slab_band_basis`, `base_codes`, `formula`, `eligibility`, `min_amount`, `max_amount`, `aggregator` (`MIN` or `MAX`), `dedupe_scope`, `rounding`, `per_lot`, `applies_to_corporate_actions`, `order`, `taxable`, `active`, `notes`.

**`charge_instruments`** (`entity/ChargeInstrumentEntity.java:37` – `:107`), a scheme's own charges and facts: `stock_code` and `start_date` unique together (`charge_instrument_scheme_idx`), `isin` (partial index where non-null), `name`, `asset_type`, `fund_category`, `equity_oriented`, `plan_type`, `amc`, `end_date`, `status`, `rules_json` (`ChargeRuleListConverter`), `source_url`, `verified_on`, `AuditColumns`.

**`charge_catalogue`** (`entity/ChargeCatalogueEntity.java:30` – `:69`): `code` (unique `charge_catalogue_code_idx`), `display_name`, `category`, `description`, `statutory_reference`, `status`, `AuditColumns`. Codes match `[A-Z][A-Z0-9_]*` (`service/ChargeCodes.java:25`, `:34` – `:48`).

**`user_charges`** + child tables **`user_charge_lines`** and **`user_charge_amounts`** (`entity/UserChargeEntity.java:46` – `:161`), one row per priced event:
- **Who:** `email` and `account_holder`, both encrypted and searchable (`SearchableColumn`, `:53` – `:61`).
- **The trade:** `broker_name`, `asset_type`, `segment`, `exchange`, `stock_code`, `transaction_id`, `order_id`, `event`, `transaction_date`, `turnover`, `quantity`.
- **The pricing:** `computed_on`, `resolution`, `schedule_id` / `schedule_code`, `statutory_schedule_id` / `statutory_schedule_code`, `instrument_id`, `lines` (eager `@ElementCollection` in `user_charge_lines`; `entity/ChargeLine.java:21` – `:56`), `amount_by_code` (eager `@ElementCollection` map in `user_charge_amounts`, D-947 C2a), `total_charges`, `AuditColumns`.
- **Indexes:** `user_charge_txn_idx (email, transaction_id)` unique; `user_charge_dedupe_idx (email, account_holder, broker_name, stock_code, transaction_date)`; `user_charge_history_idx (email, transaction_date DESC)` (`V8__brokercharges.sql:92` – `:94`).

**`charge_accounts`** + child table **`charge_account_billing_events`** (`entity/ChargeAccountEntity.java:42` – `:129`), one demat account: `email`, `account_holder` and `demat_account_id` (all three encrypted and searchable via `SearchableColumn`), `broker_name`, `plan_code`, `opened_on`, `amc_frequency` (`ANNUALLY` or `QUARTERLY`), `last_billed_through`, `billing_events` (eager `@ElementCollection` in `charge_account_billing_events`), `status`, `AuditColumns`. Unique index `charge_account_idx (email, broker_name, demat_account_id)`; `charge_account_amc_due_idx (amc_frequency, status, last_billed_through)` (`V8__brokercharges.sql:57` – `:58`).

**Embedded elsewhere:** `ChargeSummaryReport` (`amount_by_code`, `total_charges`, `last_updated_time`) sits in `portfolio`'s P&L document; `merge` folds one trade's amounts in and recomputes the total from the per-code amounts (`entity/model/ChargeSummaryReport.java:28` – `:72`).

## 5. Behaviour

**Pricing one event** (`engine/ChargeEngine.java:74` – `:111`):
1. **The broker's card.** Candidates are this broker's cards whose window holds the trade date and whose status is not `INACTIVE`
   (`repository/ChargeScheduleRepository.java:27` – `:29`). A card **agrees** when every scope field it sets equals the trade's and it has
   an active rule for the trade's event (`engine/ChargeScheduleResolver.java:160` – `:172`; W-304). Among those, the highest
   **specificity** wins: plan 8, exchange 4, segment 2, asset type 1 (`:56` – `:59`, `:178` – `:193`); a tie goes to the later start
   date, and a remaining tie refuses (`:132` – `:157`). None agreeing: `NO_SCHEDULE`, no lines (`ChargeEngine.java:79` – `:86`).
2. **The statutory card**, the one naming no broker, chosen the same way (`ChargeScheduleRepository.java:36` – `:38`;
   `ChargeScheduleResolver.java:85` – `:116`).
3. **The instrument profile**, looked up only when the broker's card `requires_instrument_profile`; the latest start date in force wins
   (`ChargeEngine.java:117` – `:132`; `ChargeInstrumentResolver.java:53` – `:81`). Its `equityOriented`, `fundCategory`, `planType` and
   `amc` become variables a rule can read (`ChargeEngine.java:142` – `:162`).
4. **The rules that apply**, from all three sources: active, naming the event, on the trade's side, and — for a corporate-action trade —
   only those with `applies_to_corporate_actions` (`:207` – `:229`, `:297` – `:318`). One code from both the profile and a card: the
   profile's wins (`:269` – `:291`). Then **sorted by `order`**, the code breaking a tie (`:59` – `:61`).
5. **Each rule**, in that order: its eligibility predicate (absent means it applies, `ChargeFormulaEvaluator.java:75` – `:80`), then its
   basis's calculator, then **aggregator, then minimum, then maximum, then the rule's rounding** (`ChargeEngine.java:338` – `:373`). A
   per-lot rule runs once per lot (`:324` – `:336`).
6. **The total** is the sum of the rounded lines, rounded half-up to paise (`:103`). The resolution is `RESOLVED`, or names what was
   missing: `NO_STATUTORY_SCHEDULE` before `NO_INSTRUMENT_PROFILE`; with no lines, `CORPORATE_ACTION_EXEMPT` for a corporate action, else
   `NO_MATCHING_RULES` (`:178` – `:198`). The gaps report lists every resolution but `RESOLVED` and `CORPORATE_ACTION_EXEMPT`
   (`dto/enums/ChargeResolution.java:71` – `:72`).

**The seven bases** (`dto/enums/ChargeBasis.java:10` – `:43`), one calculator each; a basis with none, or with two, stops the start
(`engine/ChargeCalculatorRegistry.java:27` – `:50`). The shipped cards use TURNOVER (89 rules), FLAT (17), SCOPED_FLAT (7), DERIVED (7) and
SLAB (1) (grep of `data/charges/`).

| Basis | Amount | Where |
|---|---|---|
| `TURNOVER` | rate % × the context amount the rule names (turnover by default) | `engine/TurnoverChargeCalculator.java:33` – `:46` |
| `FLAT` | the flat amount | `engine/FlatChargeCalculator.java:27` – `:29` |
| `PER_UNIT` | per-unit amount × quantity | `engine/PerUnitChargeCalculator.java:28` – `:31` |
| `SLAB` | the band holding the band value (turnover, quantity or holding days; lower bound in, upper out): its flat amount, or its rate % × the amount basis | `engine/SlabChargeCalculator.java:38` – `:85` |
| `SCOPED_FLAT` | the flat amount, unless a row with that code above 0 already exists for the scope: per scrip per day, per day, or per order, always within one email, account holder (and broker) | `engine/ScopedFlatChargeCalculator.java:44` – `:85`; `repository/UserChargeRepository.java:27` – `:41` |
| `DERIVED` | rate % × the sum of the lines named in `base_codes` so far | `engine/DerivedChargeCalculator.java:37` – `:49`; `engine/ChargeAccumulator.java:41` – `:49` |
| `FORMULA` | a SpEL expression's value, in a context with no type, bean or constructor access | `engine/FormulaChargeCalculator.java:33` – `:35`; `ChargeFormulaEvaluator.java:142` – `:166` |

**Rounding** (`engine/ChargeRounding.java:36` – `:48`): `HALF_UP_2` (the default), `CEILING_2`, `HALF_UP_0` (to the rupee: STT and stamp
duty on the shipped cards), `NONE`. Amounts are `BigDecimal` inside the engine and stored as `double`.

**Per lot** (`ChargeEngine.java:405` – `:423`): on a disposal, a per-lot rule sees each lot's quantity, a turnover of that quantity at the
**sale** price, and `holdingDays` from the lot's acquisition date (W-303). The trade path passes the FIFO lots `portfolio` already matched
(`service/ChargeRecordingGatewayImpl.java:139` – `:147`).

**Validating a card** (`service/ChargeScheduleValidator.java:66` – `:339`), on publish and on seeding, every problem reported at once: a
start date, an end not before it, at least one rule, codes unique and catalogued, each basis's own parameter present, no negative amount, an
aggregator exactly when both a rate and a flat amount are set, bands without overlap or gap, a derived rule's bases declared earlier on the
card (on a statutory card, any catalogued code), and every expression parsing and naming only known variables (`:341` – `:349`). A
statutory card may not name a plan or require a profile (`:105` – `:114`).

**Publishing** closes the open card of the same scope on the day before the new one starts, in one transaction; republishing the open
card's own code replaces it in place; either evicts both resolver caches (`ChargeScheduleService.java:65` – `:81`, `:119` – `:150`).

**Seeding** (`service/ChargeSeederService.java:124` – `:299`) runs only on `POST …/seed`, never at start. In order: the catalogue
(`data/charges/charge-catalogue.json`, 13 codes), the cards (39 files), then the instrument profiles (`data/charges/instruments/`, 2 files).
A code or card already on file is **left alone**, and a stored card differing from its file is logged and listed as drift (`:174` – `:188`,
`:213` – `:231`). Shipped cards with overlapping windows in one scope, an invalid card, or an unknown JSON key stop the run
(`service/ChargeScheduleWindows.java:34` – `:60`; `ChargeSeederService.java:102` – `:105`, `:194` – `:199`). A second run writes
nothing.

**Recording** (`service/UserChargeService.java:139` – `:172`): price, then write the row for `{email, transactionId}` — a new one, or the
existing one overwritten — **even when nothing was charged**, so the gap shows.

**The trade path** (`ChargeRecordingGatewayImpl.java:58` – `:90`): records only while `engine-enabled` and `shadow-recording` are both on.
A failure is logged and swallowed, so the trade still saves. On a V2 buy with `authoritative` on, a computation that has a figure
replaces the entered charges as the lot's cost (`portfolio/service/PortfolioService.java:502` – `:508`; `dto/context/ChargeComputation.java:37`
– `:39`).

**The AMC cycle** (`AmcChargeService.java:69` – `:160`): every `ACTIVE` account of the frequency whose `last_billed_through` is before
`billedThrough`, minus accounts held for erasure (`:87` – `:90`; `repository/ChargeAccountRepository.java:24` – `:26`). Each is priced as an
`AMC_CYCLE` event with no scrip and no turnover under the id `AMC-{dematAccountId}-{billedThrough}`. Only a `RESOLVED` result advances
the watermark and appends a billing event; any other leaves the period billable later (`AmcChargeService.java:102` – `:118`). See gap 6.

**The reconciliation** (`ChargeReconciliationService.java:50` – `:95`) pairs each row with its transaction's entered `brokerCharges`.
Rows that did not resolve, and rows whose transaction is gone, are listed with a note and left out of the totals. It also counts
transactions with no row.

### Worked examples (by hand; the tests assert these)

The shipped cards `zerodha-equity-delivery-2025-04-01.json` and `statutory-equity-delivery-2025-04-01.json`, rates in percent. Each line is
rounded by its own policy, and GST is 18 % of the rounded taxable lines.

| Line (order) | Rule | Delivery buy, 10 × ₹1,000 (turnover ₹10,000) | Delivery sale, 10 × ₹1,100 (turnover ₹11,000) |
|---|---|---|---|
| BROKERAGE (10) | FLAT 0 | 0.00 | 0.00 |
| STT (20) | 0.1 %, to the rupee | 10 | 11 |
| EXCHANGE_TXN (30) | 0.00297 % | 0.297 → 0.30 | 0.3267 → 0.33 |
| SEBI_FEE (40) | 0.0001 % | 0.01 | 0.011 → 0.01 |
| IPFT (50) | 0.0001 % | 0.01 | 0.011 → 0.01 |
| STAMP_DUTY (60) | 0.015 %, buy only, to the rupee | 1.5 → 2 | — |
| DP (70) | ₹13.50 per scrip per day, sale only | — | 13.50 |
| GST (100) | 18 % of BROKERAGE, EXCHANGE_TXN, SEBI_FEE, IPFT, DP | 18 % × 0.32 = 0.0576 → 0.06 | 18 % × 13.85 = 2.493 → 2.49 |
| **Total** | | **₹12.38** | **₹27.34** |

The other figures `ChargeGapAuditIntegrationTest` asserts:

| Scenario | Expected |
|---|---|
| A buy on 28 Feb 2026 and on 1 Mar 2026 | `ZERODHA_EQ_DELIVERY_2025_04` (its last day, the end inclusive), then `ZERODHA_EQ_DELIVERY_2026_03`, whose 0.0030699 % gives 0.30699 → EXCHANGE_TXN 0.31 |
| An NSE-only Zerodha delivery card, brokerage flat ₹5 | on NSE it outranks the general card (exchange 4 > 0): BROKERAGE 5, GST 18 % × (5 + SEBI 0.01) = 0.9018 → 0.90; on BSE the general card |
| Intraday buy, 10 × ₹1,000, through V2 and V1 | brokerage min(0.03 % × 10,000 = 3.00, 20) = 3.00, exchange 0.30, SEBI and IPFT 0.01, stamp 0.003 % = 0.3 → 0, GST 18 % × 3.32 = 0.5976 → 0.60: **₹3.92** on `ZERODHA_EQ_INTRADAY_2025_04`; through V2 the lot costs ₹3.92 |
| A Zerodha account billed annually through 31 Mar 2026, imposed twice | ₹300 + GST 18 % × 300 = 54: **₹354**, billed once |
| The open delivery card closed 1 Jul 2026, a buy on 1 Aug 2026 | `NO_SCHEDULE`, no lines (the maintenance card has no rule for a BUY); through V2 the trade saves and is listed in `/gaps` |
| The statutory delivery card closed 1 Jul 2026, a buy on 1 Aug 2026 | `NO_STATUTORY_SCHEDULE` on `ZERODHA_EQ_DELIVERY_2026_06`, no STT line |
| History from 2026-01-01 to 2025-01-01 | 400 "Date range starts after it ends: 2026-01-01 is after 2025-01-01" |
| Fund units: 60 bought 10 Apr 2025, 40 bought 10 Jun 2025, both at ₹100; 100 redeemed at ₹110 on 1 May 2026 | the 386-day lot pays 0 (1 % below 365 days, 0 from 365); the 325-day lot pays 1 % × 40 × ₹110 = **₹44.00** |

## 6. Configuration

`app.charges.*` (`config/ChargeEngineProperties.java:5` – `:6`), shipped `true` in all three profiles (`application.yaml:195`, `:199`;
`application-staging.yaml:168`, `:172`; `application-prod.yaml:168`, `:172`; `authoritative` beside them):

| Property | Effect |
|---|---|
| `engine-enabled` | false: simulate, due and impose answer 503 and the trade path records nothing; reads still work (`ChargeEngineSwitch.java:30` – `:34`) |
| `shadow-recording` | the trade path prices and records each V2 trade and each V1 buy (`ChargeRecordingGatewayImpl.java:62` – `:64`) |
| `authoritative` | the computed total is a V2 buy's cost, only while `shadow-recording` is on too (`portfolio/service/PortfolioService.java:502` – `:508`) |

At start, with the engine on, either phase flag on and **no card on file**, `ChargeReadinessAuditor` warns that every trade will be
`NO_SCHEDULE` and charged nothing (`config/ChargeReadinessAuditor.java:37` – `:63`): seed first (deploy runbook §1B in tag `docs-history-m2`).

## 7. Tests

| Class | Cases |
|---|---|
| `ChargeEngineTest` | 66 |
| `ChargeSeederServiceTest` | 49 |
| `ChargesIntegrationTest` | 42 (routes; the scoped charge once per scrip per day, again for another scrip or another holder) |
| `ChargeScheduleValidatorTest` | 39 |
| `ChargeSimulationServiceTest` | 31 |
| `ChargeScheduleResolverTest` | 23 |
| `UserChargeServiceTest`, `ChargeScheduleServiceTest`, `AmcChargeServiceTest`, `ChargeFormulaEvaluatorTest`, `ChargeRecordingGatewayImplTest` | 21, 19, 18, 17, 17 |
| `ChargeRepositoryIntegrationTest`, `ChargeSummaryReportTest`, `ScopedFlatChargeCalculatorTest` | 13 each |
| `ChargeGapAuditIntegrationTest` | 12 (§5); with the three flag classes `ChargeAuditShadowOffIntegrationTest`, `ChargeAuditAuthoritativeOffIntegrationTest`, `ChargeAuditAuthoritativeWithoutShadowIntegrationTest` |
| the other calculators, `ChargeInvariantTest`, `ChargeAccumulatorTest`, `ChargeRoundingTest`, `ChargeInstrumentResolverTest`, `ChargeCalculatorRegistryTest`, `ChargeFormulaEvaluatorSandboxTest`, `ChargeReconciliationServiceTest`, `ChargeAccountServiceTest`, `ChargeReadinessAuditorTest`, `ChargeCatalogueServiceTest`, `ChargeGoldenFileTest` | 1 – 11 each |
| `ShadowRecordingIntegrationTest`, `ChargeEngineDisabledIntegrationTest` (503), `ChargeExtensibilityTest`, `ChargeIndexIntegrationTest` | the switches, a new charge as data, the indexes |
| others | `portfolio`'s tests of cost basis, the P&L charge summary and its rebuild |

Counts are `@Test` / `@ParameterizedTest` annotations per file (grep), not executions.

## 8. Known gaps (placed as W-382, D-908)

1. **`/simulate`, a `/me` route, takes the user from the body.** `email` and `accountHolder` are request fields
   (`dto/request/ChargeSimulationRequest.java:39` – `:41`) put on the context as sent (`ChargeSimulationService.java:154` – `:159`), and a
   scoped charge asks `user_charges` whether that email and holder were already charged (`ScopedFlatChargeCalculator.java:59` – `:69`). So a
   signed-in user who names another user's email and holder learns from the DP line whether that user sold that scrip that day. CLAUDE.md: a
   `/me` route takes its user from the token. Read from the code; not driven.
2. **A charge priced at nothing is reported as priced.** A slab rule whose bands do not hold the value (`SlabChargeCalculator.java:51` –
   `:55`), a derived rule with no base codes (`DerivedChargeCalculator.java:41` – `:46`), and a turnover rule on an amount the trade does not
   carry (`TurnoverChargeCalculator.java:38` – `:43`) each log a warning and emit a 0 line, and the result is still `RESOLVED`, so `/gaps`
   does not list it. The validator catches the second, and bands with a gap between them, but not bands that start above the lowest value
   (`ChargeScheduleValidator.java:251` – `:273`).
3. **Broad catches.** The trade path swallows every `RuntimeException` from pricing (`ChargeRecordingGatewayImpl.java:82` – `:89`), so with
   `authoritative` on, a pricing failure leaves the entered figure as the cost with only a log line and a "without computation" count in the
   reconciliation. Index creation at start catches and logs the same way (`ChargeIndexInitializer.java:54` – `:61`). CLAUDE.md: no
   `catch (RuntimeException)`.
4. **Javadoc that is no longer true.** "Off by default" and "Nothing here feeds cost basis" (`ChargeRecordingGatewayImpl.java:26` – `:34`),
   but the flags ship on and `authoritative` makes the figure the cost. Routes and types that do not exist in `src/main` (grep):
   `/broker-charges/amc/impose` and `AssetManagementDetails` (`AmcCycleController.java:21` – `:23`), `/user-broker-charges`
   (`UserChargesController.java:26` – `:28`). "No statutory card ships until W-163b" (`ChargeScheduleResolver.java:82` – `:83`): eight ship.
   "Loads … at startup" and "The seeder runs on every startup" (`ChargeSeederService.java:37`, `:58`), against its own `:39` – `:43`.
   "Chunk 8 introduces" a properties class that exists (`ChargeScheduleService.java:50` – `:52`). Two Javadoc blocks stacked on one member,
   the first orphaned: `AmcChargeService.java:48` – `:63`, `ChargeScheduleService.java:107` – `:113`, `ChargeScheduleValidator.java:283` –
   `:291`, `ChargeInstrumentEntity.java:62` – `:63`.
5. **Dead weight.** `ChargeResolution.PROVISIONAL`, which nothing sets (`dto/enums/ChargeResolution.java:42` – `:47`).
6. **An AMC cycle bills one charge per run, whatever the span** (`AmcChargeService.java:97` – `:119`). An account opened 1 Jan 2024 and
   first billed through 31 Mar 2026 pays one ₹354 for 27 months; one opened 1 Oct 2025 pays the full ₹354 for 6 months. This is W-264b's
   question Q-2 for the owner (one charge per elapsed year? a part-year prorated?), which has no answer on file.
7. **The verification window reads the wall clock and a Java constant.** `findUnverified` uses `LocalDate.now()` and a 90-day constant
   (`ChargeScheduleService.java:54`, `:116`), not the app's `Clock` or a property; so a test cannot pin the date, and the window is not
   configurable.

8. **Six routes have no request in `api-collection/`** (grep of the gitlink at `41a7123`): `POST …/seed`, `GET …/charge-schedules/drift`,
   `GET …/charge-accounts/due` and the three `…/user-charges/users/{email}/…` reads. A manual tester cannot call them from the collection,
   and `ApiCollectionContractIntegrationTest` checks only the requests that exist.

**Limits by design**, carried from the retired design's list and checked against today's code (the ones no longer true are in D-909):
- **A charge levied once per window is not a cap on a sum.** `DedupeScope` suppresses a repeat; a "most ₹X a day across all trades"
  rule cannot be written (`dto/enums/DedupeScope.java:6` – `:8`). Nor can a charge on cumulative monthly turnover, or one that accrues
  daily on an open position (MTF interest): every charge is priced at an event.
- **A card or profile written straight to the repository is not seen** until the caches are evicted: only publishing, closing and
  seeding evict (`ChargeScheduleService.java:77`, `:99`; `ChargeSeederService.java:135` – `:136`), and nothing but the seeder writes a
  profile (grep for `ChargeInstrumentRepository`: the resolver, the seeder, the repository).
- **Two schemes have profiles** (`data/charges/instruments/`), keyed on a short `stock_code` (`ChargeInstrumentEntity.java:58` – `:60`);
  every other fund is `NO_INSTRUMENT_PROFILE`. **No card starts before 2025-04-01** (the file names), so an earlier trade is
  `NO_SCHEDULE`. Both are W-383.
- **Trades stored before the engine priced them keep their charges** and are never re-priced (ADR-32, §10).

Already placed, not repeated: the unbounded resolver caches (W-307) and the read-modify-write in `UserChargeService.record` (W-308), both
parked (`K`).

## 9. Not covered here

- Lots, FIFO matching, the two trade flows and the P&L hierarchies the charge summary sits in: `portfolio`.
- What each shipped rate means and where it came from: the card's own `notes` and `source_url`, and
  `docs/taxplanning-build/sources/2026-10-28-statutory-charge-rates.md` for the statutory rates.
- Seeding a database at deploy: `docs/taxplanning-build/post-deployment-runbook.md` §1B.

## 10. Names the code cites from the retired design

The design (`docs-history-m1:docs/charges-engine/`) numbered its decisions, criteria and defects. These are the ones the code still
cites, each as the design stated it, with what holds today where that differs.

| Name | What it says | Today |
|---|---|---|
| ADR-12 | Superseding a card closes its date window; it never changes its status. `INACTIVE` means entered in error; a closed card still prices a trade dated inside its window | as decided (`ChargeScheduleEntity.java:30` – `:38`) |
| ADR-15 | Engine arithmetic is `BigDecimal`; amounts are `double` only where stored | as decided (§5) |
| ADR-18 | Phase A shipped placeholder rates, so AC-2 waited until they were real | superseded: every shipped card carries `verifiedOn` and its sources in `notes` |
| ADR-24 | A missing instrument profile is recorded (`NO_INSTRUMENT_PROFILE`), never fatal; expression variables are checked against an allow-list | as decided (`ChargeEngine.java:117` – `:132`; `ChargeScheduleValidator.java:341` – `:349`) |
| ADR-27 | Seeding is an authenticated call, `SUPER_USER`, never a start-up side effect; every document names who wrote it | as decided (§5) |
| ADR-29 | One registry of every instrument the application recognises; uploads are checked against it, and charge profiles key off its canonical code | not built: profiles key on `stock_code`; W-383 |
| ADR-32 | Turning on `authoritative` changes new trades only; stored transactions keep their charges and are never re-driven (owner, 2026-09-09) | as decided |
| AC-1 | A new charge type is added by editing seed JSON only, no `.java` change | `ChargeExtensibilityTest` |
| AC-2 | A delivery trade on a seeded card matches a real contract note within ₹0.01 | the rates' sources are in each card's `notes`; re-checking is the 90-day `unverified` list (§3) |
| AC-4 | A second sale of the same scrip on the same day charges no second DP; a different scrip does | `ChargesIntegrationTest` |
| AC-6 | A fund redeemed within 365 days attracts exit load; held longer, it does not | the 1 % band in `instruments/parag-parikh-flexi-cap-2025-04-01.json`; §5's example |
| AC-8 | Publishing over an open card of the same scope closes it; both stay readable, the older for earlier dates | §5 |
| AC-9 | A card whose GST rule names an unknown base code is refused at seeding, readably | §5 |
| AC-10 | A buy's lot cost is the engine's total, not the number the user typed | `authoritative`, V2 only (§6) |
| AC-12 | No card matching is no charge and no exception, with a warning logged | `NO_SCHEDULE` (§5) |
| FR-8 | Every asset type goes through the engine; one with no card produces no lines | as stated (`ChargeRecordingGatewayImpl.java:35` – `:40`) |
| OD-8 | Run the shadow phase before the cutover, to compare computed with typed charges | ran; the typed figures held almost nothing to compare (ADR-31), so the engine was checked by other means |
| CE-5 | "Clear all records" did not erase everything a user owns | closed by the account erasure (W-237d; W-264b, D-611) |
| D1 | GST was charged on a sum that included STT and stamp duty | fixed: GST names its base codes (`DerivedChargeCalculator.java:37` – `:49`) |
| D6 | Publishing a new card threw unless the old one was closed first | fixed: publishing supersedes (§5) |
| D7 | A rule with a rate and a flat amount and no aggregator charged 0 | fixed: refused (§3) |
| D10 | The DP charge was keyed without the account holder, so two holders' debits were charged once | fixed: the holder is in every scoped key (§5) |
| PRD §4.4 | Exit load is a "% of redemption value, conditional on holding period (e.g. 1% if held < 365 days)" | as stated (W-303; §5) |
| tech-spec §4.6.2 | When a card and a profile declare one code, the profile's rule wins and the card's is skipped | as stated (`ChargeEngine.java:269` – `:291`) |
| tech-spec §5.5 | Modifiers in one order: aggregator, minimum, maximum, then rounding, applied by the engine, not the calculators | as stated (`ChargeEngine.java:351` – `:373`) |
