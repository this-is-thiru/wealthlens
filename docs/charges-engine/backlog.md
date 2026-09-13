# Charges engine — backlog

Things found after the twelve chunks closed, while wiring charges into the transaction and asset
responses. Each is real, none is blocking, and each was deliberately not done.

Numbered `CE-n` so as not to collide with the trade ledger's `B-n`
([`../trade-ledger/backlog.md`](../trade-ledger/backlog.md)) or this module's `D-n` defects and
`ADR-n` decisions.

Format follows the trade ledger's: what it is, why it matters, what done looks like, why not now.

---

## CE-1 — A rate card can execute arbitrary code

**Severity: highest item in this file.** Everything else here is correctness or tidiness.

**What.** `ChargeFormulaEvaluator` evaluates rate-card formulas and eligibility predicates with
Spring's `StandardEvaluationContext`, which is the unrestricted one. `ChargeScheduleValidator`
checks only the `#variable` names an expression references, against a fixed vocabulary — so type
references, constructors and bean lookups pass unexamined, because they contain no `#`.

Verified against the real evaluator rather than reasoned about:

```
expression: T(java.lang.System).getProperty('java.version').length()
referencedVariables() → []        ← nothing for the vocabulary check to reject
validate()            → passes
evaluate()            → 2

T(java.lang.Runtime).getRuntime().availableProcessors() → 8
```

`getRuntime()` resolving means `.exec(...)` is one method call away.

**Why it matters.** Writing the expression needs `SUPER_USER` on `POST /charge-schedules`, or direct
Mongo write access — so this is not an open door from the internet. What it is, is a silent
privilege escalation: *"may edit rate cards"* becomes *"may run code in the JVM"*. Rate-card editing
is precisely the permission you would want to hand to a finance or operations person, and ADR-26's
model of shipping rate changes as new generations means cards get written fairly often.

The blast radius is also wider than the engine: the expression evaluates inside the application's
own JVM, with its Mongo credentials and its JWT signing key in reach.

**Done looks like.** `SimpleEvaluationContext.forReadOnlyDataBinding()` in place of
`StandardEvaluationContext`, an explicit rejection of `T(`, `new ` and `@` in
`ChargeScheduleValidator` so a hostile card is refused when written rather than when a trade is
priced, and a regression test pinning the probe above as *rejected*.

Compatibility was checked before pausing and looks clear. The only two expressions in the shipped
seed data are `#equityOriented == true` and `#holdingDays < 7`. The evaluator's own test suite uses
arithmetic, comparison, `and`, and `#charges['CODE']` map indexing — all of which
`SimpleEvaluationContext` supports. What still needs confirming is whether any test relies on method
invocation, which `forReadOnlyDataBinding()` disallows.

**Why not now.** Interrupted mid-change; nothing is committed. It wants its own commit and a run of
`-Pmutation` scoped to the engine, since `ChargeFormulaEvaluator` is inside the mutation gate.

---

## CE-2 — Two Excel export frameworks ✅ CLOSED

**Closed 2026-09-13.** One framework. `ExcelBuilder` and `portfolio/service/export/{writer,processor}/**`
are deleted; everything goes through `ExcelColumn` → `ExcelSheet` → `ExcelWorkbooks`, with every
column declared once in `PortfolioExportColumns`.

**What it was.** Two independent mechanisms, ~110 and ~660 lines, both writing `AssetResponse`, both
separating a column's heading from its value — and both therefore capable of producing a spreadsheet
whose headings did not describe the data beneath them. Both did. Neither had a test.

The selective export took `selectedColumns` from the request body, built its header row from every
declared column and its data at indices numbered from the caller's selection: asking for two of
thirteen put the stock code under EMAIL. An unrecognised column name failed as a 500. The breakdown
sheet declared `BROKER NAME` twice and wrote the transaction date into the second one. And three of
the four faults fixed in `ExcelBuilder` were still present in its twin a day later — a `CellStyle`
per cell (218 for 200 rows, against a 64k limit), a bare `RuntimeException`, and a type switch whose
default threw `IllegalArgumentException`, surfacing a programming error as a 400.

**Why the duplication was the actual defect.** Every one of those was fixable in place, and the
first round of fixes went into one copy only, because nothing connected them. Two mechanisms for one
job is not a tidiness problem; it is the mechanism by which a fix fails to arrive.

**What closing it changed.** `field` names — the API surface in `selectedColumns` — are preserved
exactly. The computed charge columns now exist in **both** downloads rather than one. The
`/stocks/download` default column order changed to the canonical list, since one column set cannot
keep two orders; a caller passing explicit `selectedColumns` is unaffected, as the order follows
the request. Recorded here because it is a change to a published format.

**Coverage.** 15 tests where there were 6, and the framework that had none now has all of them.

---

## CE-3 — The schedule resolver cache is single-JVM and unbounded

**What.** `ChargeScheduleResolver` holds a `ConcurrentHashMap` and calls `evictAll()` in-process
from `ChargeScheduleService.publish` and `close`.

**Why it matters.** Two things, both currently latent.

*Correctness on a second instance.* Eviction is a method call on one JVM's object. Run two
instances and publishing a corrected rate card on instance A leaves instance B pricing trades with
the superseded card, indefinitely and with nothing logged. The application ships as a single
deployable JAR today, so this is correct as deployed — it is simply the first thing that breaks on
the day it is not.

*Growth.* `ScopeKey` includes `transactionDate`, so the cache holds one entry per scope per day.
Steady-state traffic is bounded by the number of distinct trading days seen; a multi-year backfill
is not, and there is no eviction policy or size bound.

Related and already recorded, so not duplicated here: a card written directly through
`ChargeScheduleRepository` is invisible to the cache, and scheme profiles have no publishing service
at all — both in [`README.md`](README.md) §10.

**Done looks like.** A bounded cache with a TTL, and eviction that crosses instances — the cheap
version being a short TTL that makes a stale card self-correct within a minute, rather than
introducing a distributed cache for this alone.

**Why not now.** Single instance. Worth fixing before the second one, not before it exists.

---

## CE-4 — `UserChargeService.record` is read-modify-write with no version

**What.** `record` does `findByEmailAndTransactionId(...).orElseGet(UserChargeEntity::new)` and then
`save`. Two concurrent calls for the same transaction id both read absent, both construct a new row,
and both save. `user_charge_txn_idx` is unique, so one gets a `DuplicateKeyException`, which nothing
catches.

**Why it matters.** The window is narrow — it needs the same transaction id priced twice
concurrently, which the trade path does not do today. It widens the moment a recompute endpoint
exists (see [`README.md`](README.md) §10) and runs alongside live trades.

**Done looks like.** `@Version` on `UserChargeEntity`, or an upsert, and a handled duplicate-key
path that re-reads rather than failing the caller.

**Why not now.** Not reachable by any current caller.

---

## CE-5 — "Clear all records" is not an account wipe

**What.** `PortfolioService.clearAllRecordsForCustomer` now clears all seven collections the
`portfolio`, `corporate` and `brokercharges` modules own. It does not clear
`InsuranceEntity`, `SalaryProfileEntity` or `TaxComputationEntity`, all three of which carry an
`email` and are unambiguously the same user's data.

**Why it matters.** The endpoint is `POST /portfolio/user/{email}/clear/all` and answers "records
and transactions deleted successfully", which reads as a full wipe and is not one. If this is ever
the mechanism behind a deletion request, the gap is a compliance problem rather than an untidiness.

**Done looks like.** A decision on what the endpoint means. If it is "clear my portfolio", the name
and the message should say so. If it is "delete my account", the wipe has to reach `insurance` and
`taxplanning` — which `portfolio` may not depend on, so it goes through a Modulith event rather
than a call, and the modules own their own erasure.

**Why not now.** It is a scope question, not a defect, and answering it wrongly deletes more than
someone asked for. Recorded in the method's javadoc as well, so the omission reads as a decision.

---

## Recorded elsewhere — cross-references, not duplicates

| Finding | Where it already lives |
|---|---|
| Money is stored as `double` rather than `Decimal128` | [`../trade-ledger/backlog.md`](../trade-ledger/backlog.md) B-3, and [`../trade-ledger/money-representation-analysis.md`](../trade-ledger/money-representation-analysis.md) |
| No safe way to re-price charges after correcting a card | [`README.md`](README.md) §10 — `POST /charges/recompute` designed in tech-spec §14.4, not built |
| A card written outside `ChargeScheduleService` is invisible to the cache | [`README.md`](README.md) §10 |
| Scheme profiles have no publishing endpoint | [`README.md`](README.md) §10 |

### Noted, not backlog

**Charge codes are Mongo field names.** `amount_by_code.BROKERAGE` is what lets reports aggregate
without unwinding, and `ChargeCodes` restricts a code to `[A-Z][A-Z0-9_]*` to keep that safe. The
permanent cost is that a code can never be renamed without a data migration, and
`UserChargeRepository` interpolates the code into `@Query` strings. A deliberate trade with a real
consequence — worth knowing, not worth changing.
