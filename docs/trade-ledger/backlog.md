# Trade ledger — backlog

Things found during TL-1..TL-7 that are real, are not blocking, and were deliberately not done.
Each says what it is, why it was left, and what "done" would look like.

---

## B-1 — The new tax-critical code has no coverage floor

**What.** Two JaCoCo gates exist, both scoped to `brokercharges`: `engine*` and `service.*`, each at
90% line / 85% branch. Everything TL-4 through TL-7 added to `portfolio` is outside both. Measured
against that same bar on the run at `ecd8025`:

| Class | Line | Branch |
|---|---|---|
| `TradeClassifier` | 100% | 100% |
| `HoldingPeriodService` | 100% | 100% |
| `TransactionService` | 97% | 92% |
| `HoldingPeriodResolver` | 97% | **82%** |
| `HoldingPeriodPolicy` | **80%** | **81%** |
| `TradeOutcomeRecorder` | 98% | **62%** |

`ChargeDeductibilityService` (100% / 94%) is already gated, being in `brokercharges.service`.

**Why it matters.** `TradeOutcomeRecorder` assembles the row a tax return is filed from, and at 62%
branch it is the least-covered thing in this work. The charges engine got a gate precisely because
"adding a charge is a data change" only holds if the code enforcing it is covered; the same argument
now applies to holding-period classification and the recorder.

Mutation testing has the same gap — PIT targets `brokercharges.engine.*`,
`brokercharges.service.*` and `taxplanning.engine.*`, so none of the new classifier or recorder code
is mutated. Per the project's own convention, PIT rather than JaCoCo is the real gate.

**Done looks like.** A third JaCoCo rule over `com.thiru.wealthlens.portfolio.holding.*` and
`TradeOutcomeRecorder` at the same 90/85, the uncovered branches filled to reach it, and
`com.thiru.wealthlens.portfolio.*` added to the PIT target classes.

**Why not now.** It is a quality gate over code that is already tested, not a correctness fix, and it
would have grown a branch that was already too large to review. Deliberate, recorded, not forgotten.

---

## B-2 — `trade_outcomes` holds two financial-year formats

`TradeMatchingService` (the migration) writes `FY2023-24`. The live V1 and V2 paths write
`2023-2024`. Both are in the same collection.

Note the irony: the migration's boundary logic (`month <= 3`) was always **correct**, while the three
live derivations were a day out on 31 March until they were fixed and pointed at
`TLocalDate.financialYear`. It is only the format that differs.

**Done looks like.** The migration uses `TLocalDate.financialYear` too, and existing `FY`-prefixed
rows are rewritten. Harmless until something groups by the field.

---

## B-4 — `profit_and_loss` and `assets` cannot be rebuilt from `transactions`

`transactions` is the source of truth. `assets` (holdings) and `profit_and_loss` are derived from it,
and there is no way to recompute either — so any correction to them is a careful manual edit against
a nested document.

`trade_outcomes` already has exactly this: drop the collection, restart, and
`TradeOutcomeMigrationRunner` rebuilds it from raw transactions. That is why, of the three derived
collections, it is the only one that is easy to correct.

**Why it matters.** It surfaced writing the duplicate-remediation section of
[`migration.md`](migration.md) §3a. Unwinding one double-applied trade currently means hand-editing
a holding's quantity and charges, then hand-adding two profit-and-loss hierarchies together across
yearly, monthly and half-month levels. With a recompute it would be: delete the bad transaction row,
rebuild.

**Done looks like.** A `POST /portfolio/user/{email}/rebuild` that recomputes `assets` and
`profit_and_loss` from `transactions`, behind `SUPER_USER`, with a dry-run that reports what would
change before it changes it. Note that ADR-32 forbids re-driving transactions through the *charges
engine* — this rebuilds holdings and totals from the charges already recorded, which is a different
thing and should say so explicitly wherever it lands.

**Why not now.** Nothing needs it today: the 31 March check returned 0 and the duplicate checks are
expected to return nothing. It becomes urgent the first time they do not.

---

## B-9 — `TestController` mutates state without saving the lots

`POST /test/user/{email}/transact/sell` (authenticated — it is in `AuthConfig`'s list) takes
`stockEntities` from the request body and calls the V2 sell helper, but never saves them.

So it writes profit and loss, charge records and trade outcomes, while discarding the lot mutations
— including the quantity decrement and, since B-7, `allocatedBuyCharges`. The next real sell of
those lots would re-deduct the buy charge, which is the bug B-7 just closed.

**Done looks like.** Either the endpoint saves what it mutates, or it goes. It reads as a debugging
aid that outlived its purpose; nothing in the repository calls it.

---

## B-3 — Money is stored as `double` (this is TL-8, not really backlog)

The engine computes in `BigDecimal` and rounds once, which is right. Every *stored* amount is a
`double`. `ChargeSummaryReport.merge` re-derives its total in `BigDecimal` on every write to work
around the drift, which is the symptom rather than the fix.

**Decided and implemented, 2026-09-12.** `double` stays; every stored amount is canonicalised to
paise through `TMoney`. See [`money-representation-analysis.md`](money-representation-analysis.md)
for the measurements, and its §7 for the five triggers that would make a full `BigDecimal`
migration the answer after all. This entry stays as the pointer to that decision.

---

## Closed by fixing

- **B-5 — V1 accumulated with raw `+=`.** *(2026-09-12)* Canonicalised through `TMoney` alongside
  V2, with the owner's agreement that it changes no logic: the same amounts fold into the same
  fields in the same order, and only the last bits of the stored double change.
- **B-6 — pro-rating lost a paisa across lots.** *(2026-09-12)* Cumulative allocation per charge
  code: each lot receives the running total up to it minus what has already been handed out, so the
  parts sum to the whole by construction rather than by a correction pass. Chosen over
  largest-remainder, which needs a sort and a tie-break rule. Each row's lumped total is derived
  from its own allocated lines.
- **B-8 — V1 had the identical over-allocation.** *(2026-09-12)* Fixed with the owner's agreement.
  V1 computes the share for two records — the trade outcome and the profit-and-loss entry — so a
  naive fix would have deducted twice; it is now computed **once per sell** and handed to both.
  `LotChargeAllocator` is shared by V1 and V2 rather than copied, on the same reasoning that
  collapsed three copies of paise rounding into `TMoney`.

  **Still open, and it is a data question:** lots already partially sold carry over-deducted amounts
  in `trade_outcomes` and `profit_and_loss` that the code fix does not correct, and
  `allocated_buy_charges` reads as zero on every existing lot — so the next sell of a lot already
  partially sold will allocate from its full charge once more. Deciding what to do about that needs
  the same treatment as the 31 March question.
- **B-7 — a lot's buy charge was re-charged on every partial sell.** *(2026-09-12)* Worse than the
  rounding residue this entry originally claimed: `MatchedLot.originalQuantity` is the quantity
  remaining *before* a sell, and nothing decremented the stored charge, so a 3-unit lot carrying ₹10
  sold one unit at a time deducted ₹3.33, ₹5.00 and ₹10.00 — **₹18.33 against ₹10.00 paid**,
  inflating the cost base and understating the gain. `AssetEntity` now carries
  `allocatedBuyCharges`, and each sell takes a share of what is left, so the last one necessarily
  takes the remainder. Still live in V1 — see B-8.
- **A zero-quantity lot wrote `Infinity` into profit and loss.** *(2026-09-12)*
  `ProfitAndLossContext.from` divided a charge by the lot's quantity with no guard, so a
  zero-quantity asset produced `10.0 / 0.0`. It never threw, and the test covering it was named
  `shouldHandleGracefully` — but the value was accumulated and stored, and `Infinity + anything`
  stays `Infinity`, so one such asset poisoned every later total in that document permanently.
  **Found only because TL-8 routed accumulation through `TMoney`, which refuses a non-finite
  amount.** That is the case for canonicalising in one place rather than adding doubles inline.

## Closed by checking

- **Trades dated 31 March.** Three financial-year derivations filed a trade made *on* 31 March into
  the following year. Fixed in code. `db.transactions.countDocuments({transaction_date: /-03-31$/})`
  returned **0** on production (checked 2026-09-12), so there is no historical data to correct.
