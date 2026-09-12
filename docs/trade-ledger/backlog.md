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

## B-3 — Money is stored as `double` (this is TL-8, not really backlog)

The engine computes in `BigDecimal` and rounds once, which is right. Every *stored* amount is a
`double`. `ChargeSummaryReport.merge` re-derives its total in `BigDecimal` on every write to work
around the drift, which is the symptom rather than the fix.

Still the last open item on the implementation checklist. It is a decision before it is a build, and
it gets harder every month the data grows.

---

## Closed by checking

- **Trades dated 31 March.** Three financial-year derivations filed a trade made *on* 31 March into
  the following year. Fixed in code. `db.transactions.countDocuments({transaction_date: /-03-31$/})`
  returned **0** on production (checked 2026-09-12), so there is no historical data to correct.
