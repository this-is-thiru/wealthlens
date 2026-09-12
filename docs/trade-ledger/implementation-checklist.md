# Trade Ledger — implementation checklist

**Purpose:** the build tracker for making a trade record you can file taxes from. If context is
lost, resume from the first unticked box.

**Branch:** `feature/charges-engine` (this work continues on it; the charges engine itself is
complete — see `../charges-engine/README.md`).
**Status:** 8 of 8 decided. **TL-8 is decided; its implementation is the only work left.**
**Last updated:** 2026-09-12 — 907 tests green (unit + integration), both JaCoCo gates passing,
surefire XML gate clean, spotless clean.
**Analysis:** [`holding-period-analysis.md`](holding-period-analysis.md) works TL-4's rules through per asset type.
**Backlog:** [`backlog.md`](backlog.md) — found, real, deliberately not done.
**Deploying:** [`migration.md`](migration.md) — two mandatory data steps and three checks. Read it before any deploy.

---

## Why this exists

The charges engine computes what a trade costs. This work makes that cost *usable*: it puts the
figures onto the realised-trade record, classified the way tax actually turns on them.

The trigger was reading `TradeOutcomeEntity` and finding that it **cannot express the distinctions
tax depends on** — which charges are deductible, which holding-period rule applies, and whether the
trade is capital gains at all. Plus a set of defects found alongside it.

`trade_outcomes` is one row per matched buy-lot ↔ sell pair: the itemised capital-gains ledger.
`profit_and_loss` holds the aggregates; this holds the evidence behind them. A sell of 4 units
against lots of 2 and 3 produces **two rows**, each with its own holding period and therefore
potentially its own tax treatment — one LTCG, one STCG, from a single sell. That classification is
recorded nowhere else and cannot be reconstructed once the lots are consumed.

---

## Decisions already taken — do not re-litigate

| # | Decision | Taken |
|---|---|---|
| D1 | **Intraday is classified, not dropped.** `CapitalGainsType` gains `SPECULATIVE`. Intraday equity is speculative business income, not capital gains; the row is still written and still itemised, but classified so a report never folds it into the capital-gains total | 2026-09-12 |
| D2 | **Holding-period rules are data-driven**, validity-windowed per asset type — like rate cards. A rule change becomes a data change, and a backdated trade keeps the rule in force when it happened. Hardcoding today's thresholds was rejected for the same reason ADR-12 rejected editing rate cards | 2026-09-12 |
| D3 | **Deductibility lives on the charge catalogue.** `deductibleForCapitalGains` per code, so each charge declares it once and the trade outcome sums only the deductible ones. Keeps "adding a charge is a data change" true | 2026-09-12 |
| D4 | **Idempotency: client key if present, derived hash as fallback.** The client's key is authoritative when supplied; otherwise a hash of the trade's fields within a short window. Protects existing clients immediately and lets new ones do it properly | 2026-09-12 |
| D6 | **"CA" means corporate action, never chartered accountant.** Java identifiers spell it out — `corporateActionAdjustedBuyPrice`, `corporateActionDerived`. The stored `@Field` names keep their abbreviated spelling so live documents are not orphaned; the divergence is documented on the fields | 2026-09-12 |
| D5 | **No scheduler for AMC.** A due-query instead, so the gap is visible without anything billing money on a timer | 2026-09-11 |
| D7 | **The tax sub-class comes from the instrument registry (ADR-29's, in the priced-portfolio epic) — never from `ChargeInstrumentEntity`.** TL-6 threads the field through and stores it; funds and bonds stay unknown until the registry exists. `ChargeInstrumentEntity` was the obvious candidate and is the wrong one: it is keyed on a code real holdings do not match, its absence means "no exit load" rather than "unknown category", and — decisively — `equityOriented` + `FundCategory` **cannot express the three-way split** the rules need | 2026-09-12 |
| D8 | **The third bucket is a map, added beside the two fields rather than replacing them.** `RealisedProfits` gains `gainsByClassification` as `Map<CapitalGainsType, FinancialReport>`, written by the V2 sell only. The two named fields stay, written exactly as today, and are deleted when V1 goes — the same cutover shape `yearlyChargeSummary` already uses beside `yearlyBrokerCharges` in this very class | 2026-09-12 |

---

## Done

- [x] **TL-1 — Trade request validation** *(2026-09-12, commit `34dec71`)*
      `validateAssetRequest` returned early when the request carried no email, skipping the only
      other check there was. Quantity must now be **positive** — the old `equal(quantity, 0)` let
      negatives through, and a negative buy creates a negative holding while a negative sell
      increases one. Price cannot be negative; a trade cannot be future-dated. Zero price stays
      legal, with a test saying why: bonus and split allotments are issued free.
- [x] **TL-2 — `trade_outcomes` indexed** *(2026-09-12, `34dec71`)*
      `{email, sell_date}` descending. Every read is one user's realised trades and was scanning the
      collection. Created through `PortfolioIndexInitializer`, because `auto-index-creation` is off
      and the annotation alone creates nothing.
- [x] **TL-3 — `GET /charge-accounts/due`** *(2026-09-12, `34dec71`)*
      What an AMC cycle would bill, without billing it (D5). A read: touches no charge, moves no
      watermark, safe to poll. Honours the kill switch.

---

## Remaining

### TL-4 — Holding-period policy *(done 2026-09-12)*

- [x] Validity-windowed policy per asset type, **persisted** in `holding_period_policies` and seeded
      from `data/holding-periods/holding-period-policies.json` (D2). Eleven policies ship
- [x] `HoldingPeriodResolver` — one classifier, database-backed, cached and evicted on seed
- [x] All four hardcoded sites replaced: `PortfolioService`, `TradeMatchingService`, and both in
      `ProfitAndLossService`. They did not even agree with each other — two compared
      `plusYears(1)` and two counted 365 days, which differ across a leap year
- [x] The result type allows `ALWAYS_SHORT_TERM` and `NOT_CAPITAL_GAINS`, not just a month count
- [x] Each policy declares whether its window is keyed on **acquisition** or **transfer**
- [x] `CapitalGainsType` gains `NOT_CAPITAL_GAINS` and `UNCLASSIFIED`
- [x] Seeded by the existing `POST /charges/seed`, so one call still prepares a fresh database
- [x] Lives in `portfolio`, **not** `taxplanning` — that module is salary-regime advisory and
      unrelated
- [x] **Settled 2026-09-12 as D8: the aggregate gets a map, not a third named field.** `UNCLASSIFIED`
      and `NOT_CAPITAL_GAINS` are counted short-term today and logged. Short-term is the
      higher-taxed direction so it does not understate a liability, but a fixed deposit's interest
      does not belong in a capital-gains report at all. What the shape has to be:
      - **Additive, because `RealisedProfits` is shared.** The two `calculateProfitDetails` methods
        are already forked — V1's takes `InternalTransactionContext`, V2's takes `InternalContext`,
        and only the V2 sell reaches the latter. But the *class* holding the fields is written by
        both, so replacing the fields breaks V1. V2 writes the map **and** keeps writing the pair
        exactly as today, so no existing reader changes and the regression risk is nil
      - **A map, but not for the reason the charge side is one.** Charge codes are open-ended data,
        so `amount_by_code` makes adding a charge a data change. `CapitalGainsType` is a closed enum
        and a new classification needs code regardless — that argument does not transfer. The real
        reason is that the bug *is* the binary: an `if/else` cannot hold five values, and a map
        keyed by the enum has no branch to get wrong
      - **The keys are not peers, and that is a trap.** `SHORT_TERM`, `LONG_TERM` and `SPECULATIVE`
        are income buckets; `UNCLASSIFIED` is a data-quality state and `NOT_CAPITAL_GAINS` is a
        different head of income entirely. One map is still right — the partition is complete, so
        the amounts reconcile against total proceeds — but summing it yields a wrong
        capital-gains total. `CapitalGainsType` carries a predicate saying which keys count, added
        with the map rather than when a reader first needs it, because the moment the map exists
        summing it is the obvious thing to do and nothing would stop it
- [x] **Settled 2026-09-12 as D7: the sub-class is blocked on the instrument registry, not on TL-6.**
      Passing `null` is already *correct* for the majority of trades — `EQUITY_LISTED` and `FD_INTEREST`
      carry no sub-class, so equity and fixed deposits classify right today. The gap is
      `MUTUAL_FUND`, `BOND`, `GOLD_BOND` and `INSURANCE`, and for each of them `UNCLASSIFIED` is the
      honest answer rather than a defect:
      - **MUTUAL_FUND** — the real exposure, 43 of 319 transactions on `it-staging` across 8 schemes.
        `ChargeInstrumentEntity` cannot supply it: `equityOriented=true` gives `EQUITY_ORIENTED`, but
        `false` has to split into `SPECIFIED` and `OTHER` on a ">65% debt and money-market" test, and
        `FundCategory` only answers that for `DEBT` and `LIQUID`. `INDEX`, `ETF`, `FUND_OF_FUNDS` and
        `OTHER` are ambiguous — and those are exactly the categories the 2024 amendment moved *out* of
        s.50AA. The enum is ambiguous precisely where the answer is contested
      - **BOND** — defaulting to `LISTED` would return long-term where an unlisted bond is deemed
        short-term, understating the liability. `UNCLASSIFIED` counts short-term in the P&L stopgap,
        which errs in the safe direction
      - **GOLD_BOND** — `UNCLASSIFIED` is the *designed* answer, not a gap. Redemption is exempt and a
        market sale is taxable, and nothing on a sell request says which happened
      - **INSURANCE** — no policy ships, deliberately; too conditional to encode from `AssetType`

### TL-5 — Charge deductibility on the catalogue *(done 2026-09-12)*

- [x] `deductibleForCapitalGains` on `ChargeCatalogueEntity` (D3). A `Boolean`, not a `boolean`:
      undeclared has to be distinguishable from declared-false, because silently defaulting to
      deductible is the failure the field exists to prevent
- [x] Declared on all 12 shipped codes. **Three are not deductible** — more than the one first
      identified:
      - **STT**, expressly disallowed, and the largest charge on a delivery sell (about ₹100 on
        ₹1,00,000 against ₹20 of brokerage), so treating it as deductible inflates the cost base
      - **AMC** and **ACCOUNT_OPENING**, which are account-level and not incurred in connection with
        any particular transfer. They also never reach a trade's breakdown, since they arise from
        `AMC_CYCLE` and `ACCOUNT_OPENING` events rather than a buy or sell — but the flag belongs on
        the code, not the event, so it is declared either way
- [x] `ChargeCodes.requireDeductibilityDeclared` refuses an undeclared code at seed time, beside the
      existing field-name check

### TL-6 — The trade-outcome rework *(done 2026-09-12)*

Everything here touches the same row, so it ships together.

- [x] `CapitalGainsType.SPECULATIVE`, and `segment` on `TradeOutcomeEntity` (D1) — intraday equity
      is speculative business income, taxed at slab rate, not capital gains at all
- [x] **`RealisedProfits.gainsByClassification`** as `Map<CapitalGainsType, FinancialReport>` (D8),
      written by the V2 sell beside the existing pair, plus the matching field on
      `RealisedProfitsResponse`. `outSourcedRealisedProfits` gets it for free — same class
- [x] **V2 classifies through `HoldingPeriodService.classify`, not `isShortTerm`.** The two-way
      facade stops being a stopgap and becomes the deliberate adapter for the legacy pair: it is
      the *correct* answer for a two-bucket model, and its javadoc should say that instead of
      calling itself TL-6's problem
- [x] `buyChargeBreakup` / `sellChargeBreakup` as `Map<String, Double>`, keeping the lumped totals
      for display. Deductible cost becomes a sum over codes rather than a hardcoded total
- [x] Sell-side charges from the **engine**, not `assetRequest.getBrokerCharges()`. Today the buy
      side uses the computed figure (under `authoritative`) and the sell side the deprecated
      user-entered one — mixed provenance in one row
- [x] `originalBuyPrice` differs from `corporateActionAdjustedBuyPrice`. They are assigned identically today
      (`// Will improve later with source transaction data`), so the corporate-action adjustment the
      field exists to record is never recorded
- [x] Holding-period classification through TL-4's resolver
- [x] **The sub-class stored on the row, behind one seam** (D7). TL-6 owns the *field* and the single
      place that supplies it; it does not own the *data*. Ship that supplier returning unknown for
      funds and bonds, so the registry has exactly one method to fill in later rather than a
      call site to hunt for. Storing it explicitly also distinguishes "classified as unknown" from
      "written before the field existed", which a null cannot
- [x] **Wire V2 to write trade outcomes at all.** `sellStockV2` →
      `updateQuantityBySavingReportAndProfitAndLoss1`, which never calls `saveTradeOutcome`. Only
      the V1 sell path populates the collection, and V2 is the live path — so today a V2 user has
      aggregated totals and **no itemised capital-gains record to file from**

> **V1 is in live use and must not be touched.** `toTradeOutcomeContext` as it stands is V1-only
> code; V2 gets its own path, the same way `buyStockV2` did in Chunk 10a.

**How it shipped.** A new `TradeClassifier` composes segment and holding period in one place, and
carries the D7 sub-class seam. A new `TradeOutcomeRecorder` builds the rows for a V2 sell, one per
consumed lot, pro-rating the engine's sell charge and its per-code breakup by quantity.
`ChargeDeductibilityService` resolves the deductible part against the catalogue (D3).

The change to `PortfolioService` is **nine added lines and no deleted ones**, all inside
`updateQuantityBySavingReportAndProfitAndLoss1`. `buyStock`, `sellStock`,
`updateQuantityBySavingReportAndProfitAndLoss` and `toTradeOutcomeContext` are byte-identical, and a
test drives V1's `sellStock` and asserts `verifyNoInteractions(tradeOutcomeRecorder)` — because if
V1 ever reached the new path, every V1 sell would be recorded twice.

**Also fixed, found while testing the response DTO:** `ReportModelResponse` names its fields
`purchasePrice` / `sellPrice` / `brokerCharges` while `ReportModel` names them `purchaseAmount` /
`sellAmount` / `brokerage`, and `TJsonMapper.safeCopy` maps by name — so the profit-and-loss endpoint
returned **`0.0` for every purchase, sell and brokerage figure**, at all three levels of the
hierarchy, for both realised and out-sourced profits. Only `profit` and `miscCharges` ever came
through, which is why it reported a profit with no turnover behind it. Fixed with `@JsonAlias`
rather than a rename: an alias applies on deserialization only, so the copy picks the entity's name
up while the emitted JSON keeps the name clients already read. A test asserts the alias does not
leak into the response, so nobody later "simplifies" it into the rename that would break consumers.

**Both follow-ups are now done too.**

*The response field names were renamed, not aliased.* `ReportModelResponse` now spells its fields
`purchaseAmount` / `sellAmount` / `brokerage`, matching `ReportModel` exactly. An alias would have
worked, but `purchasePrice` was also simply the wrong word for an amount aggregated over a period,
and aliasing preserves a wrong name permanently in the surface a tax report reads from. Safe because
the DTO is never persisted — **no database change** — and because nothing in the repository reads
those getters. The guard test inverted: it now asserts the old names are *gone*, so the divergence
cannot come back.

*The 31 March boundary is fixed in all three derivations.* They compared `isBefore(March 31)`, which
filed a trade made **on** 31 March into the following year — one day wrong every year, on the single
heaviest day for tax-loss harvesting. All three now delegate to `TLocalDate.financialYear`, so they
cannot drift apart again. V1's `deriveFinancialYear` keeps its name, signature and every call site;
only its body became a one-line delegation.

**Still open — a data question, not a code one.** Any trade already recorded on a 31 March sits in
the wrong `profit_and_loss` document and the wrong `trade_outcomes` row. The code is right from now
on; existing rows need a decision. Separately, the migration's `TradeMatchingService` still writes
`FY2023-24` into `trade_outcomes` while the live paths write `2023-2024` — its *boundary* was always
correct (`month <= 3`), only its format differs, so that collection can hold two formats.

### TL-7 — Trade idempotency *(done 2026-09-12)*

- [x] Idempotency key on `AssetRequest`, unique-indexed on `TransactionEntity` (D4)
- [x] Derived-hash fallback within a short window when no key is supplied
- [x] A replay returns the original result rather than repeating the work
- [x] Two genuinely identical trades on one day must still both be accepted — they happen, and this
      is why a pure hash was rejected

It did, and worse than the original note said. `TransactionService.addTransaction` already deduped
on `sourceTempTransactionId` — found the row, logged *"Duplicate transaction suppressed"*, returned
its id — and `processTransaction` then **applied the trade to the portfolio anyway**. The duplicate
row was suppressed; the holding was added twice and P&L updated twice, under a log line that read as
handled. Reachable through redrive, which flips status to `PROCESSED` in a batch *after* the loop, so
two concurrent redrives both see the same `TEMPORARY` set.

Fixed by making the outcome say what happened: `recordTransaction` returns
`TransactionRecord(transactionId, replay)`, and both orchestrators return early on a replay.
`buyStock`, `sellStock`, `buyStockV2` and `sellStockV2` are all untouched — the guard returns before
reaching any of them. The old `addTransaction` is deprecated rather than deleted, because returning
an id alone is what allowed this.

Three de-duplication routes in order of authority: the client's key (exact, no window), a redrive's
`sourceTempTransactionId` (exact), and a derived fingerprint within
`app.portfolio.idempotency.window-seconds` (default 60, a heuristic that says so). The fingerprint is
**never** a unique index — two identical trades on one day are legitimate; it is the fingerprint plus
the window that marks a retry, and a client key overrides it for the bulk-upload case.

**Deployment hazard, documented in the charges production runbook:** covering `transactions` in
`PortfolioIndexInitializer` applies the `source_temp_transaction_id` unique index for the first time
— it has never existed, because `auto-index-creation` is off and the collection was not covered. If
prod holds duplicates, creation fails. Index creation is now caught and logged at ERROR rather than
blocking startup, so grep for `Could not create index` after deploying.

### TL-8 — Money representation *(decide, then schedule)*

- [x] **Decided 2026-09-12: `double` stays, and every stored amount is canonicalised to paise.**
      Analysis in [`money-representation-analysis.md`](money-representation-analysis.md)
- [ ] Implement: `TMoney` utility, the 31 write sites, an ArchUnit rule, then tighten `MoneyAssert`

The engine computes in `BigDecimal` and rounds once, which is right — but every *stored* amount is a
`double`. `ChargeSummaryReport.merge` already re-derives its total in `BigDecimal` on every write to
work around the drift, which is the symptom. This row is where the number reaches a tax filing. The
decision gets harder every month the data grows.

---

## Ground rules

- **Test-driven.** The test comes first and must be seen to fail against real assertions, not a
  compile error.
- **V1 `buyStock` / `sellStock` are never touched.** They are in live use. V2 gets its own path.
- **Present before committing**, per the repository owner's working agreement.
- `./mvnw spotless:apply` before each commit; `./mvnw clean test verify` must be green and the
  surefire XML gate must print nothing.

## Traps carried over

1. **`auto-index-creation` is off.** An `@Indexed` or `@CompoundIndex` annotation creates nothing
   unless the entity is listed in `PortfolioIndexInitializer` or `ChargeIndexInitializer`.
2. **`@AllArgsConstructor` on an entity is the ADR-27 trap** — Lombok's `@ConstructorProperties`
   makes a mapper bypass field initialisers, and an `auditMetadata` that arrives null is never
   filled.
3. **`ProfitAndLossEntity` now carries `@Version`.** A concurrent write fails loudly rather than
   losing data, and there is deliberately no retry — the service is `@Transactional` at class level,
   so retrying needs the transaction boundary moved first.
4. **A charge code is a MongoDB field name.** `ChargeCodes` restricts it to `[A-Z][A-Z0-9_]*`.
