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

## CE-2 — Two Excel export frameworks, and only one knows about charges

**What.** The application has two independent Excel export mechanisms, both writing
`AssetResponse`, reached by two endpoints:

| Endpoint | Mechanism | Shape |
|---|---|---|
| `GET /portfolio/user/{email}/assets/holding/{type}/excel` | `shared/util/parser/ExcelBuilder` | `List<ExcelColumn<T>>` — header paired with extractor |
| `POST /portfolio/user/{email}/stocks/download` | `portfolio/service/export/**` | `AbstractExcelWorkbookWriter<T>` — string keys, header map, extractor map, plus caller-selected columns |

They are roughly 110 and 660 lines respectively, they solve the same problem, and they share only
the `ExcelHeaders` string constants.

**Why it matters.** The computed charge columns were added to the first and therefore exist in one
download and not the other, which is worse than being in neither — two exports of the same holdings
now disagree about what data a holding has. The second framework also keys its columns by
hand-written strings (`"stockCode"`, `"totalQuantity"`) mapped to headers and extractors in separate
maps, which is a differently-shaped version of the positional drift the first one was just fixed to
eliminate: a typo in a key yields a missing column rather than a compile error.

**Done looks like.** One framework. The second one's column-selection feature is the capability
worth keeping — `ExcelColumn` has no equivalent — so the likely shape is `ExcelColumn` plus a
`select(List<String>)`, with the `export/**` writers and processors deleted and
`EntityExportController` pointed at it. Charge columns then exist once, in both downloads.

**Why not now.** It is a refactor across two controllers and eleven classes with no test coverage on
the `export/**` side, which makes it a change that needs tests written before it, not during. The
narrower fix — adding the charge columns to the second writer too — is available if the divergence
needs closing before the consolidation.

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
