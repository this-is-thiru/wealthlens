# Trade Ledger — implementation checklist

**Purpose:** the build tracker for making a trade record you can file taxes from. If context is
lost, resume from the first unticked box.

**Branch:** `feature/charges-engine` (this work continues on it; the charges engine itself is
complete — see `../charges-engine/README.md`).
**Status:** 4 of 8 items done. **Resume at TL-5.**
**Last updated:** 2026-09-12 — 870 tests green, both JaCoCo gates passing, spotless clean.
**Analysis:** [`holding-period-analysis.md`](holding-period-analysis.md) works TL-4's rules through per asset type.

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
- [ ] **Carried forward to TL-6: the P&L aggregate has only two buckets.** `UNCLASSIFIED` and
      `NOT_CAPITAL_GAINS` are counted short-term there and logged, because there is nowhere else to
      put them. Short-term is the higher-taxed direction so it does not understate a liability, but
      it is a stopgap: a fixed deposit's interest does not belong in a capital-gains report at all
- [ ] **Carried forward to TL-6: the sub-class is not supplied yet.** Every call passes `null`, so
      mutual funds and bonds resolve `UNCLASSIFIED`. The data lives on `ChargeInstrumentEntity`
      (`equityOriented`, `fundCategory`) and reaching it is part of the trade-outcome rework

### TL-5 — Charge deductibility on the catalogue *(blocks TL-6)*

- [ ] `deductibleForCapitalGains` on `ChargeCatalogueEntity` (D3)
- [ ] Set it in `charge-catalogue.json` for all 12 shipped codes. **STT is the one that is not
      deductible** — and it is the largest charge on a delivery sell (₹100 on ₹1,00,000, against
      about ₹20 of brokerage), so including it inflates cost and understates the gain
- [ ] `ChargeCodes`-style validation so a new code must declare it rather than defaulting silently

### TL-6 — The trade-outcome rework *(the main piece; needs TL-4 and TL-5)*

Everything here touches the same row, so it ships together.

- [ ] `CapitalGainsType.SPECULATIVE`, and `segment` on `TradeOutcomeEntity` (D1) — intraday equity
      is speculative business income, taxed at slab rate, not capital gains at all
- [ ] `buyChargeBreakup` / `sellChargeBreakup` as `Map<String, Double>`, keeping the lumped totals
      for display. Deductible cost becomes a sum over codes rather than a hardcoded total
- [ ] Sell-side charges from the **engine**, not `assetRequest.getBrokerCharges()`. Today the buy
      side uses the computed figure (under `authoritative`) and the sell side the deprecated
      user-entered one — mixed provenance in one row
- [ ] `originalBuyPrice` to differ from `corporateActionAdjustedBuyPrice`. They are assigned identically today
      (`// Will improve later with source transaction data`), so the corporate-action adjustment the
      field exists to record is never recorded
- [ ] Holding-period classification through TL-4's resolver
- [ ] **Wire V2 to write trade outcomes at all.** `sellStockV2` →
      `updateQuantityBySavingReportAndProfitAndLoss1`, which never calls `saveTradeOutcome`. Only
      the V1 sell path populates the collection, and V2 is the live path — so today a V2 user has
      aggregated totals and **no itemised capital-gains record to file from**

> **V1 is in live use and must not be touched.** `toTradeOutcomeContext` as it stands is V1-only
> code; V2 gets its own path, the same way `buyStockV2` did in Chunk 10a.

### TL-7 — Trade idempotency

- [ ] Idempotency key on `AssetRequest`, unique-indexed on `TransactionEntity` (D4)
- [ ] Derived-hash fallback within a short window when no key is supplied
- [ ] A replay returns the original result rather than repeating the work
- [ ] Two genuinely identical trades on one day must still both be accepted — they happen, and this
      is why a pure hash was rejected

Today a client that retries on a timeout creates a second transaction, a second `AssetEntity`, a
second charge row and double-counted P&L, with nothing detecting it.

### TL-8 — Money representation *(decide, then schedule)*

- [ ] Decide: `double` stays, or money becomes `BigDecimal` / minor units end to end

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
