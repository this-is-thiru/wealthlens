# 14 — Operational design

The non-functional side, at the level an enterprise deployment needs. Extends `07-architecture.md`.
Everything is `RECOMMENDATION` unless it carries a rule ID or a `FACT` citation.

---

## O1 — Personal data

This module holds more sensitive data than anything else in the codebase: PAN, TAN, salary structure,
health-insurance spend, disability certificates, **the landlord's PAN** (a third party's identifier),
lender details, and scanned tax documents.

### O1.1 The classification, and what follows from it

| Class | Fields | Treatment |
|---|---|---|
| **Identifier** | PAN, Aadhaar-linked status, TAN, landlord PAN, lender PAN | encrypted at rest with a field-level key; **masked in every log and every error message**; never a map key |
| **Sensitive financial** | all `Money` fields, salary structure, capital gains | encrypted at rest; never logged at value level |
| **Special category** | disability status and certificates, health-insurance and medical-expenditure claims, family composition | encrypted; access recorded in the audit log even for reads |
| **Third-party** | **landlord PAN, name and address**; lender details | the user supplies data about **someone who is not the user**. Retention is the shortest defensible, and it is never included in any export the user shares |
| **Operational** | timestamps, pack versions, rule IDs, trace structure | not sensitive; safe to log in full |

`RECOMMENDATION`: the trace is the one place where structure and money meet. Log **trace shape**
(sequence, rule ID, `limitedBy`) freely; log **trace values** never. That keeps the observability
story in `O7` intact without turning application logs into a salary database.

### O1.2 The problem with the repo's URL convention

`FACT (CLAUDE.md, repo conventions)`: REST paths are user-scoped under `/{resource}/user/{email}`.

`INFERENCE`: that places an email address in the request line — which lands in access logs, proxy
logs, browser history, referrer headers and any APM trace. For `portfolio` that is already true; for
`taxplanning` the same URL will sit next to disability and medical claims.

`RECOMMENDATION`: keep the convention for consistency — breaking it in one module is its own kind of
harm — but add three mitigations that cost nothing:

1. an **opaque `profileId`** (UUID) is the key used in `11-filing-drafts.md`'s draft and workspace
   paths, so the deep, most sensitive paths carry no email;
2. **access-log scrubbing** for the `/user/{email}` segment at the ingress, configured as part of this
   module's deployment rather than assumed;
3. `Referrer-Policy: no-referrer` on any page that can link outward.

Recorded as a risk rather than silently accepted, because it is a pre-existing convention this module
inherits rather than a choice it makes.

---

## O2 — Authorisation

`07-architecture.md` X3.4 reserved the shape. Concretely:

```java
AccessPolicy.canAccess(principal, profileId, Action)     // Action: READ | WRITE | COMPUTE | EXPORT | DELETE
```

| Rule | Now | With D1's advisor |
|---|---|---|
| Authenticated | yes | yes |
| Subject owns the profile | required | **or** holds a live `AdvisorMandate` |
| `AdvisorMandate` | not built | `{advisorPrincipal, profileId, grantedAt, expiresAt, revokedAt, scopes}` |
| Scope granularity | n/a | `READ`, `WRITE`, `COMPUTE`, `EXPORT` — separable; `DELETE` **never** delegable |
| Audit | owner email | **acting principal** and, when acting under a mandate, the mandate id |

`RECOMMENDATION`: build `AccessPolicy` on day one with the trivial body. Every controller calls it.
Retro-fitting an authorisation seam across eighteen endpoints is the change most likely to leave one
endpoint behind, and the one left behind is the breach.

`RECOMMENDATION`: `SUPER_USER` — which the repo already uses for charge seeding — has **no** role in
this module. `D-IMMUTABLE` removes the only thing it would have been for.

---

## O3 — Idempotency and concurrency

| Operation | Mechanism |
|---|---|
| Draft patch | `If-Match` on `currentRevision`, three-way merge (`11-filing-drafts.md` F5) |
| Document upload | idempotent on **SHA-256** (`M7`); the same bytes twice returns the first `documentId` |
| Compute | idempotent on `(draftId, inputsFingerprint, packVersion, portfolioAsOf)` — an identical request returns the stored `Computation`, it does not recompute |
| Status transition | `Idempotency-Key` header; a repeated transition is a no-op, not an error |
| Parse | a new `ParseAttempt` every time by design — attempts are the history |

`INFERENCE`: making **compute** idempotent on a four-part key is what lets the optimiser fire
thousands of scenario evaluations (`G6.2`) without a cache-invalidation problem: the key contains
everything that can change the answer, so a stale result is not representable.

### Mongo transactions

`FACT (CLAUDE.md)`: multi-document `@Transactional` writes need a replica set **and**
`app.mongodb.transactions-enabled=true`.

`RECOMMENDATION`: design the write paths so that **only two** need a multi-document transaction:

1. draft status → `ACTED_ON`, which must atomically check and consume the cross-year ledger
   (invariant **I8**, `11-filing-drafts.md` F8);
2. document confirmation, which writes a `DocumentConfirmation` and seeds draft fields together.

Everything else is a single-document write. `INFERENCE`: a module that needs transactions in twenty
places is a module that will be wrong wherever the flag is off. Two places can each carry an explicit
fallback — a compensating check on read — and be verified.

---

## O4 — Auditability and reproducibility

The design already stamps every `Computation` with `packVersion`, `checksum`, `inputsFingerprint` and
the pinned portfolio `asOf`. Completing it:

| Requirement | Mechanism |
|---|---|
| Reproduce any past computation exactly | pinned pack + replayable draft revision (`11-filing-drafts.md` F2) + pinned portfolio snapshot |
| Explain what the user saw on a given date | `DraftRevision` replay to the revision current at that timestamp |
| Detect that the law moved under a stored result | `RulePack.supersedes` + staleness sweep (`13-edge-cases.md` R-8) |
| Know who changed what | `DraftRevision.changedBy` = principal, plus mandate id under delegation |
| Prove the rules were not tampered with | pack checksum verified at startup; the checksum recorded on every computation |
| Reads of special-category data | recorded — `O1.1` |

`RECOMMENDATION`: audit records are **append-only and separately retained**. They must survive a
user-initiated deletion of the underlying draft, because the question "what did this product tell
this person in November" outlives the draft. `O5` reconciles that with erasure.

---

## O5 — Retention and erasure

Two obligations pull in opposite directions, and the design must state which wins where.

| Pressure | Direction |
|---|---|
| A user asks for their data to be deleted | erase |
| Tax records must survive long enough to be defensible if the return is examined; `FACT (S2, s.263(5) as substituted by FA 2026 s.66(b))`: a revised return may be furnished **within twelve months from the end of the relevant tax year**, or before assessment completes — and reassessment windows are longer still | retain |

`RECOMMENDATION`:

| Data | Default retention | On erasure request |
|---|---|---|
| `DRAFT` / `READY` drafts and their revisions | 18 months after last activity, then archive; purge 12 months later | **erased** |
| `ACTED_ON` drafts and their computations | **8 years** from the end of the tax year — chosen to cover `SETOFF-CF-HP-001` and `SETOFF-CF-CG-001`, which permit carry-forward for **eight** tax years, so a current-year computation may need to explain an eight-year-old loss | **retained**, with a documented explanation; the user is told why and what is kept |
| Uploaded documents | 8 years, same reason | **erased on request** — the document is the user's own copy of something the employer also holds, and the computation survives without it |
| Cross-year ledgers (LTA block, lifetime events, carry-forwards) | 8 years | retained — erasing them silently re-opens a once-in-a-lifetime deduction (`RET-VRS-002`) |
| Audit log | 8 years | retained, **pseudonymised**: the profile id survives, the identifiers do not |
| **Landlord and lender PAN** (third-party data) | **the tax year plus 2 years** — the shortest that still supports a query about the return | erased with the draft |

`INFERENCE`: the eight-year figure is not arbitrary and should not be a magic number in code. It is
`max(carry-forward periods in the pack)`, derivable from `SETOFF-CF-HP-001` and `SETOFF-CF-CG-001`, and
it should be **computed from the rule pack** so that a change in the carry-forward period changes the
retention policy with it.

---

## O6 — Document handling

| Concern | Decision |
|---|---|
| Size | 20 MB per file, 50 pages — a Form No. 130 is a handful of pages; anything larger is a mis-upload |
| Types | PDF only at launch. Images are a later parser (a **new class**, `13-edge-cases.md` B9-72) |
| Malware | **Scan before storage**, not before parse. A file that fails the scan is never written to durable storage |
| Encryption | at rest with a per-tenant key; the object store is not readable without the application |
| Password-protected | prompt; **never persist the password**; a failure is `outcome = FAILED` with a clear reason |
| Parsing isolation | the parser runs with **no network egress** and a hard CPU and wall-clock budget — a PDF is untrusted input from an unknown generator |
| Storage location | outside Mongo (GridFS or an object store); Mongo holds the metadata and the SHA-256 |
| PAN mismatch | rejected before any field is consumed (`13-edge-cases.md` B9-73) |
| Retention | `O5` |

`INFERENCE`: the parser is the module's only attack surface that consumes arbitrary third-party bytes.
Isolating it is worth more than any amount of input validation elsewhere.

---

## O7 — Observability

| Signal | Content |
|---|---|
| Metric `taxplanning.compute.duration` | tagged by `taxYear`, `regimeCode`, `stepCount` — **not** by user |
| Metric `taxplanning.compute.failures` | tagged by the **rule ID** that failed. `D-NOSILENT` means failures are loud; this is where they land |
| Metric `taxplanning.rulepack.loaded` | pack version and checksum at startup — the first thing to check when numbers look wrong |
| Metric `taxplanning.parse.confidence` | histogram per document type and tax year. A drift downward is `DOC-F130-PREFILL-001`'s layout change arriving |
| Metric `taxplanning.reconciliation.verdict` | counts by verdict. A rise in `CANNOT_COMPARE` is a data-quality regression |
| Metric `taxplanning.unverified_rule.used` | **tagged by rule ID** — how often a `NEEDS VERIFICATION` rule actually affects a user's number. This is the prioritisation input for Slice 0 |
| Log | rule IDs, sequence numbers, durations, pack version. **No money values, no identifiers** (`O1.1`) |
| Trace (distributed) | one span per pipeline run; the optimiser's thousands of runs are one span with a count, not thousands of spans |

`RECOMMENDATION`: `taxplanning.unverified_rule.used` is the one metric here that is unusual and the one
most worth having. It converts the `NEEDS VERIFICATION` backlog from a document into a ranked,
evidence-based queue.

---

## O8 — Performance and capacity

| Operation | Budget | Basis |
|---|---|---|
| Single compute | **< 50 ms** p99 | ~31 pure in-memory steps, no I/O (`D-PURE`) |
| Regime comparison, N = 2 | < 100 ms | two computes |
| Regime comparison, N = 5 | < 250 ms | `10-extensibility.md` E6.3 |
| Break-even | < 400 ms | ~21 computes (`A1.3`) |
| Optimiser | **< 3 s** p99, synchronous | ~9,600 computes at N = 2 (`G6.2`); ~24,000 at N = 5 |
| Document parse | **asynchronous**, target < 30 s | unbounded input; never block a request on it |
| PDF report | asynchronous | `X1.4`'s `ComputationCompleted` event |
| Rule-pack load | startup only | `D-IMMUTABLE`; never on the request path |

`RECOMMENDATION`: if the optimiser exceeds its budget, **reduce the exhaustive-verification breadth**
(`A2.5`'s top-6) before abandoning exact pricing. Exact pricing is the correctness property
(`D-MARGINAL`); the breadth is a tuning knob, and the trace already records which knob position was
used.

`INFERENCE`: the whole performance story rests on `D-PURE`. A single repository call inside a
`ComputationNode` turns 9,600 in-memory evaluations into 9,600 database round trips. That is why the
`X5.3` architecture test forbidding Spring Data types in `taxengine` is a **performance** control as
much as a design one.

---

## O9 — Failure modes and degradation

| Failure | Behaviour |
|---|---|
| Rule pack missing or corrupt | **Application does not start** (`D-FAILFAST`, `T9-1`, `T9-3`) |
| `portfolio` unavailable | **503** on compute for a user with capital gains; full service for one without. Never compute with zero gains (`T9-14`) |
| `portfolio` returns `UNCLASSIFIED` | **409** with `ACTION_REQUIRED` (`T9-12`) |
| Mongo unavailable | reads 503; **drafts buffer client-side** and replay by idempotency key (`11-filing-drafts.md` F5) |
| Document store unavailable | upload 503; computation unaffected — documents seed inputs, they are not inputs |
| Parser crashes or times out | `ParseAttempt.outcome = FAILED` with a reason. **The document is retained**, so a later parser version can retry |
| A `CapShape` throws | the computation fails (`D-NOSILENT`). Never a partial result |
| An unverified rule is reached | computes, emits `UNVERIFIED_RULE`, increments the metric (`O7`) |

`RECOMMENDATION`: there is no "degraded tax mode". Every degradation above either serves the full
correct answer or serves none. `INFERENCE`: a partially-correct tax figure is indistinguishable from a
correct one to the person reading it, which makes graceful degradation the wrong pattern for this
particular domain.

---

## O10 — Configuration

Following the repo's precedent of a master kill switch plus narrow feature flags:

| Key | Default | Purpose |
|---|---|---|
| `app.taxplanning.enabled` | `false` | master kill switch; false makes every endpoint 503 |
| `app.taxplanning.rulepacks.path` | classpath | override only for tests |
| `app.taxplanning.rulepacks.required-years` | `2025-26,2026-27` | startup fails if any is missing (`D-FAILFAST`) |
| `app.taxplanning.documents.enabled` | `false` | ingestion is the largest slice; ship the engine first |
| `app.taxplanning.optimiser.exhaustive-breadth` | `6` | `O8`'s tuning knob |
| `app.taxplanning.drafts.soft-cap` / `.hard-cap` | `25` / `100` | `11-filing-drafts.md` F6 |
| `app.taxplanning.advisor-access.enabled` | `false` | D1's later work; the seam exists from day one |

`RECOMMENDATION`: **no flag may change a computed number.** Flags turn features on and off; they never
select a rule, a rate or a rounding mode. Anything that would change a number belongs in the rule
pack, where it is versioned, checksummed and provenanced — a flag that alters tax is an unversioned
rule change with no audit trail.

---

## O11 — Locale, time and formatting

| Concern | Decision |
|---|---|
| Time zone | **All tax dates are IST calendar dates**, stored as `LocalDate`. `FACT (CLAUDE.md)`: the repo stores datetimes as UTC via `TLocalDateTime.atUtc()` — that is right for **event** timestamps and wrong for **tax** dates. A tax year boundary is a calendar fact, not an instant |
| Audit timestamps | UTC instants, per the repo convention |
| Currency | INR only. `Money` carries no currency code — adding one would imply a conversion the Act does not contemplate, except `TDS-SAL-FX-001`'s prescribed rate |
| Number formatting | Indian grouping (lakh, crore) in every user-facing surface, including the PDF |
| Rupee amounts in JSON | strings, never numbers (`M6`) |
| Language | English at launch; all user-facing strings externalised from day one, because the rule packs already carry `displayName` per rule and per component and those are the hard ones |

`INFERENCE`: the IST-versus-UTC distinction is a real bug generator. A salary due on 31 March 2027
23:30 IST is 18:00 UTC on 31 March — fine. A document uploaded at 00:30 IST on 1 April 2027 is
18:00 UTC on 31 March, and a naive UTC-date derivation would file it under the **wrong tax year**.

---

## O12 — What "no migration" does and does not mean

The brief says there is no migration, and that is true of **user data** — there is none. It is not
true of two other things, and both need an operational answer from the start:

| | |
|---|---|
| **Rule-pack schema evolution** | Packs are versioned artefacts inside the JAR, so a schema change ships with the code that reads it. `RECOMMENDATION`: the loader validates against a declared `schemaVersion` in `manifest.json` and refuses a pack it does not understand, rather than ignoring unknown fields |
| **Draft schema evolution** | Drafts are persisted user data and **will** outlive a release. `RECOMMENDATION`: `DraftInputs` carries a `schemaVersion`; an upcast chain runs on read; a draft is never written back in an older shape. This is the one place in the module where a genuine data migration will eventually be needed, and building the upcast seam before the first release costs almost nothing |

`INFERENCE`: the first draft of this design treated "no migration" as a permanent property. It is a
property of **launch**, and the draft store is where it stops being true the day after.
