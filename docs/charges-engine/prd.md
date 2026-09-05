# Charges Engine — Product Requirements Document

**Date:** 2026-09-05
**Status:** Draft for review
**Supersedes:** the `brokercharges` module as built in ITS-15 / commit `52000e1`
**Start at** `README.md` — current state and how to resume. **Rationale** lives in `decisions.md`.
**Branch:** `feature/charges-engine`

---

## 1. Problem Statement

The current broker-charges implementation models a rate card as a **fixed set of Java fields**. Every real-world charge that exists today occupies a named column on `BrokerCharges`, `UserBrokerCharges`, and `BrokerChargesReport` simultaneously. Three consequences follow:

1. **Adding one charge is an eight-file change** spanning two modules — entity, request DTO, mapper, calculator method, per-user entity, GST string parser, P&L report model, P&L aggregator.
2. **Non-equity assets accrue no charges at all.** There is no asset-type dimension on the rate card, so the call sites gate on `assetType == EQUITY` (`ProfitAndLossService:333`, `:361`). Mutual funds, bonds and gold bonds silently cost the user ₹0.
3. **The charge structure itself is wrong for anything but plain equity delivery** — no intraday, no F&O, no exchange differentiation, no per-unit or slab-based charges, no holding-period-dependent charges (MF exit load).

The system is being extended to cover mutual funds and other instruments, and brokers add or reprice charges every few quarters (exchange transaction charge revisions, IPFT, MTF interest, physical CMR fees). The current design makes each of those a code deployment.

### 1.1 Defects to be corrected as part of this work

| # | Defect | Location | Impact |
|---|---|---|---|
| D1 | GST is applied over `govtCharges`, which lumps STT + SEBI + stamp duty into one number. Indian GST applies to brokerage, exchange transaction charges, SEBI turnover fees and DP — **not** to STT or stamp duty. | `UserBrokerChargeService:132` | User is over-charged GST; the merged field makes it unfixable without a schema change. |
| D2 | GST policy is a CSV mini-DSL (`"18%-brokerage,18%-dp_charges,18%-stt"`) parsed at runtime; unknown component names log an error and silently contribute 0. No write-time validation. | `UserBrokerChargeService:118-141` | Typos in a rate card produce silently wrong money. |
| D3 | Exchange transaction charges, IPFT and clearing charges are not modelled at all. | `BrokerCharges` | Every computed charge total is understated. |
| D4 | `exchangeName` is carried through `BrokerChargeContext` and never read. NSE and BSE transaction charges differ. | `BrokerChargeContext:14` | Dead parameter marking a known gap. |
| D5 | `BrokerChargeTransactionType` mixes trade sides (BUY/SELL) with billing occasions (AMC, ACCOUNT_OPENING); `toBrokerChargeTransactionType` can never emit half its own enum, and account-opening charges are commented-out dead code. | `UserBrokerChargeService:76,152-167` | Two concepts in one enum forces duplicated builder methods. |
| D6 | `toEntity` sets `endDate = startDate.plusYears(100)` and `addBrokerCharge` rejects any overlap, so publishing a new rate card requires manually calling `changeEndDate` first or it throws. | `BrokerChargeService:70` | No supersede flow; operationally booby-trapped. |
| D7 | `getBrokerage` returns 0 when `brokerageAggregator` is null, rather than failing loudly. | `UserBrokerChargeService:171` | Misconfiguration presents as "free trading". |
| D8 | Charge amounts are **typed in by the user** as `AssetRequest.brokerCharges` and flow into cost basis, trade outcome and net P&L; the engine's computed figure feeds only the charges report and is never reconciled against it. | `PortfolioService:290,569`; `ProfitAndLossService:200` | Manual entry is error-prone, cannot produce a per-component breakdown, and does not scale. **Removing this input is the primary motivation for the whole effort.** |
| D9 | `findTopSellTxnByBrokerNameAndStockCodeAndTransactionDate` is named "findTop" but returns a `List`, and DP dedupe depends on read-your-own-write ordering inside the transaction. | `UserBrokerChargesRepository:12` | Fragile first-sell-of-day detection. |

---

## 2. Goals

**G1 — Data-driven rate cards.** Adding a new charge type, or repricing an existing one, must require **zero Java changes**: a new rule document in a versioned seed JSON or an admin API call.

**G2 — Correct charges per instrument.** Charges must be resolvable per broker × asset type × trade segment × exchange × plan, with a deterministic most-specific-match rule.

**G3 — Auditability.** A stored charge record must reproduce a broker contract note line by line — every component named, with its rate, base and computed amount — not a set of merged buckets.

**G4 — Correct GST.** GST must be computed over an explicitly declared set of taxable components, validated when the rate card is written, not parsed from a string at compute time.

**G5 — Replace manual charge entry.** Today the user types the charge figure per transaction. The engine's computed total must become the number that flows into cost basis, trade outcome and realised P&L, so the input field can be retired.

**G6 — Extensible reporting.** The P&L charge hierarchy must aggregate by charge code, so a new charge appears in reports automatically.

## 3. Non-Goals

- **Not** a re-derivation of historical charges. There is no backward-compatibility requirement and no data migration: existing `broker_charges` / `user_broker_charges` documents are disposable and will be dropped and reseeded.
- **Not** live rate-card scraping from broker websites. Rate cards are seeded from versioned JSON and maintained by hand.
- **Not** MF expense ratio / NAV-embedded costs (accrued inside NAV, never billed as a line item).
- **Not** a change to the capital-gains tax computation. Charges feed the *cost basis*; tax treatment stays where it is.
- **Not** a rewrite of `addTransaction` v1 vs `addTransactionV2`. Both call sites get the new engine; consolidating them is separate work.

---

## 4. Domain Requirements

The engine must express, without code changes, at least the following real-world structures. **Rates below are illustrative and must be verified against each broker's live rate card before seeding.**

### 4.1 Equity — Delivery
| Component | Structure | Side |
|---|---|---|
| Brokerage | 0% (discount) or % of turnover with min/max floor-cap | Both |
| STT | % of turnover | Both |
| Exchange transaction charge | % of turnover, **differs by exchange** (NSE ≠ BSE) | Both |
| SEBI turnover fee | % of turnover (₹ per crore) | Both |
| IPFT | % of turnover, NSE only | Both |
| Stamp duty | % of turnover | **Buy only** |
| DP charge | Flat per scrip, **once per scrip per day** | **Sell only** |
| GST | % over (brokerage + exchange txn + SEBI + IPFT + DP) | Both |

### 4.2 Equity — Intraday
Same components, different rates, plus: STT **sell side only**, no DP charge, lower stamp duty. *Expressible only if trade segment is a rate-card dimension.*

### 4.3 F&O — Futures / Options
Flat-per-order brokerage; STT sell-side; options charges computed on **premium, not notional turnover**; per-lot rather than per-share quantities. *Requires the turnover basis to be supplied by the caller rather than always `price × quantity`.*

### 4.4 Mutual Funds
| Component | Structure |
|---|---|
| STT | % on redemption only, equity MF only, nil for debt |
| Stamp duty | % on purchase (units issued) |
| Exit load | % of redemption value, **conditional on holding period** (e.g. 1% if held < 365 days) |
| Transaction fee | Flat, regular plans only, often threshold-gated (orders ≥ ₹10,000) |

*Exit load is the acid test for the design: its rate depends on a runtime attribute (holding days) that no fixed field can express. It requires a formula/predicate escape hatch.*

### 4.5 Bonds, G-Sec, SGB
Brokerage plus GST; no STT; no stamp duty on secondary-market SGB. Mostly an exercise in **omitting** rules that equity has — which the current fixed schema cannot do, since every field exists on every rate card.

### 4.6 Non-trade charges
Account opening (one-off), AMC (quarterly or annual cycle), call & trade, auto square-off, pledge/unpledge, physical statement. These are billing **occasions**, not trade sides — they must not share an enum with BUY/SELL.

---

## 5. Functional Requirements

### FR-1 — Rate card authoring
- A rate card is a `ChargeSchedule`: a validity-windowed set of `ChargeRule`s scoped to broker × asset type × segment × exchange × plan.
- Any scope dimension may be left unset, meaning *"applies to all"*.
- Publishing a new schedule for an already-covered scope **auto-closes** the previous one at the new start date (fixes D6). No manual `changeEndDate` step.
- Schedules are seeded at startup from `resources/data/charges/*.json`, versioned in git, following the `PolicySeederService` precedent in `taxplanning`.

### FR-2 — Rate card validation (write time)
A schedule is rejected — at seed and at API write — if:
- two rules share a `code`;
- a `DERIVED` rule references a `baseCode` that does not exist in the schedule, or that evaluates at a later `order`;
- a rule's `basis` requires a parameter it does not carry (e.g. `TURNOVER` with no `rate`);
- `aggregator` is set without both operands, or is unset where two operands are present (fixes D7);
- a `FORMULA` rule's expression fails to parse.

Validation failures surface as `BadRequestException` with a human-readable message, per repo convention.

### FR-3 — Charge resolution
Given (broker, asset type, segment, exchange, plan, date), exactly one schedule is selected by **most-specific match**: candidates whose every declared dimension matches are ranked by a specificity score, highest wins, ties broken by latest `startDate`. If none matches, the engine returns an empty computation and logs at WARN — it must never silently return zero without a signal.

### FR-4 — Charge computation
- Rules are filtered by the `ChargeEvent` in scope, then by an optional eligibility predicate.
- Rules evaluate in ascending `order`; each writes a named line item into an accumulator.
- `DERIVED` rules (GST) read the accumulator, summing only the codes they declare (fixes D1/D2).
- Deduplicated rules (DP per scrip per day) consult prior charge records within the dedupe scope.
- Output is a `ChargeComputation`: an ordered list of named lines plus a total.

### FR-5 — Persistence
Each computation is stored as one `UserChargeEntity` with the full line-item breakdown, the schedule id it was computed from, and a `code → amount` map for aggregation. A contract note is reconstructible from a single document (G3).

### FR-6 — P&L integration
`BrokerChargesReport` and its yearly/monthly/fortnight subclasses aggregate `Map<String, Double> amountByCode` plus a total, replacing the six fixed columns. New charge codes appear in reports without code changes (G6).

### FR-7 — Cost basis integration
The computed total becomes the authoritative charge on the asset lot and transaction, retiring the user-entered `AssetRequest.brokerCharges` (fixes D8).

Delivered in three phases so the cutover is reversible (tech-spec §9):
- **Phase A** — the engine is built standalone. Nothing in `portfolio` changes. Verified through `POST /charges/simulate` and golden-file tests.
- **Phase B (shadow)** — the engine computes and persists alongside the existing flow, changing no behaviour. Computed vs user-entered totals are comparable on real data.
- **Phase C (cutover)** — the computed total drives cost basis; the manual input field is removed; the old implementation is deleted.

### FR-8 — Coverage
The `assetType == EQUITY` gate is removed. Every asset type flows through the engine; assets with no matching schedule simply produce no lines.

---

## 6. Acceptance Criteria

| # | Criterion | Verified by |
|---|---|---|
| AC-1 | A new charge type (e.g. `MTF_INTEREST`) can be added by editing seed JSON only — no `.java` file changes. | Add the rule in a test fixture; assert it appears in the computation and in the P&L report. |
| AC-2 | An equity delivery buy of ₹1,00,000 on NSE via a seeded discount-broker card produces line items matching a real contract note within ₹0.01. | Golden-file unit test. |
| AC-3 | An equity delivery sell produces STT on the sell side, DP charge once, and no stamp duty. | Unit test. |
| AC-4 | A second sell of the **same scrip on the same day** produces no second DP charge; a different scrip does. | Unit test + integration test. |
| AC-5 | GST equals 18% of (brokerage + exchange txn + SEBI + IPFT + DP) and excludes STT and stamp duty. | Unit test asserting the exact taxable base. |
| AC-6 | A mutual fund redemption held < 365 days attracts exit load; the same fund held > 365 days does not. | Unit test exercising the formula basis. |
| AC-7 | An intraday equity trade attracts STT on sell only, no DP, and the intraday stamp rate. | Unit test on a segment-scoped schedule. |
| AC-8 | Publishing a schedule for a scope that already has an open one closes the old one; both are retrievable and the older applies to earlier dates. | Service test. |
| AC-9 | A schedule whose GST rule references an unknown base code is rejected at seed time with a readable message. | Seeder test. |
| AC-10 | A buy transaction's asset lot cost basis reflects the engine-computed total, not a user-supplied number, unless override is set. | Integration test. |
| AC-11 | `WealthLensModulithTest.modulithStructureIsValid()` passes. | Existing test. |
| AC-12 | No charge is computed and no exception is thrown when no schedule matches; a WARN is logged. | Unit test. |

---

## 7. Out-of-scope Risks to Note

- **Rate accuracy is a data problem, not a code problem.** The engine will faithfully compute whatever is seeded. Seed values must be verified against live broker rate cards; a `sourceUrl` + `verifiedOn` field is included on each schedule for this reason.
- **Trade segment does not exist in the portfolio model today** (`HoldingType` is only SHORT_TERM/LONG_TERM). The engine takes `TradeSegment` on its own input record from Phase A, so no `portfolio` type changes until Phase C, where the field is added defaulting to `DELIVERY`.
- **Options premium vs notional** requires the caller to pass turnover explicitly. The engine accepts it; the portfolio module does not yet produce F&O trades, so this stays latent but designed-for.

---

## 8. Open Decisions

Tracked in the tech spec §12; summarised here for the review conversation.

| # | Decision | Recommendation |
|---|---|---|
| OD-1 | Rename module `brokercharges` → `charges`? | Yes, but as a separate mechanical commit after the engine lands. |
| OD-2 | ~~Engine vs user input as cost-basis source?~~ | **Settled:** the engine replaces manual entry outright — that is the point of the work. Sequenced through the shadow phase (FR-7). |
| OD-3 | ~~Model F&O now?~~ | **Settled:** design-for-later. Phase A seeds EQUITY/DELIVERY only. The context carries `amountBasis`, `lotSize` and `orderId` from day one so F&O lands as data, not a schema change (tech-spec §13.2). |
| OD-4 | Should `ChargeCode` be an enum or a free string? | Free string, validated against a seeded `ChargeCatalogue` — mirrors `AllowanceCatalogueEntity`, keeps G1 intact. |
| OD-5 | Per-user negotiated rates (a user's own brokerage slab)? | Model as `planCode` on the user's broker account; out of scope for phase 1 but the dimension exists. |
| OD-8 | Does Phase B (shadow) run before cutover, or go straight to Phase C? | Run it — it is the only way to check computed totals against what users actually typed. |
