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

## B-5 — V1 writes uncanonicalised amounts into the same fields

TL-8 canonicalises the V2 accumulation in `ProfitAndLossService` (`InternalContext`, the
`update*TransactionReport` family). The V1 family (`InternalTransactionContext`,
`updateReportMetadata` and `updateFortnightReport`) still accumulates with raw `+=`, because it is
the live V1 sell path and is not to be touched.

So `profit_and_loss` now holds canonical amounts where V2 wrote and drifted ones where V1 did, in
the same fields. That is strictly better than uniform drift — each write is independently more
correct — but it is not uniform, and it should be said out loud rather than discovered.

**Done looks like.** The V1 family uses `TMoney.add` too, taken with whatever else retires V1.

---

## B-6 — Pro-rating loses a paisa across lots

Splitting a ₹100 sell charge across three lots gives ₹33.33 each once canonicalised: ₹99.99
recorded against ₹100.00 charged. Found by the TL-8 test that made pro-rated amounts canonical.

Before TL-8 the three rows held `33.33333333333333`, which summed correctly and was not an amount of
money. Now they hold real paise that sum a paisa short. The second is more honest and still wrong.

**Why it matters.** The sum of a sell's `trade_outcomes` charges no longer equals its
`user_charges` total, so anything reconciling those two will see a one-paisa gap per split sell.

**Done looks like.** Largest-remainder allocation in `TradeOutcomeRecorder.share` — floor every
share, then hand the leftover paise to the largest fractional parts, so the parts sum to the whole.
Standard, small, and needs a test that the allocation is stable rather than order-dependent.

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

## Closed by checking

- **Trades dated 31 March.** Three financial-year derivations filed a trade made *on* 31 March into
  the following year. Fixed in code. `db.transactions.countDocuments({transaction_date: /-03-31$/})`
  returned **0** on production (checked 2026-09-12), so there is no historical data to correct.
