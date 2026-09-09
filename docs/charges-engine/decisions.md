# Charges Engine — Architectural Decision Log

Every decision that shaped this design, with the reasoning that produced it. **`tech-spec.md` says what the design is; this file says why.** When a future session is tempted to "simplify" something here, the rationale is the defence.

Format: Context → Decision → Why → Consequences. Status is `Accepted` unless stated.

---

## ADR-1 — A rate card is a list of rules, not a set of fields

**Context.** `BrokerCharges` has one Java field per charge: `stt`, `sebiCharges`, `stampDuty`, `dpChargesPerScrip`, `amcChargesAnnually`, `accountOpeningCharges`, `brokerageCharges`. Adding one charge touches eight files across two modules — entity, request DTO, mapper, calculator method, per-user entity, GST parser, report model, P&L aggregator.

**Decision.** `ChargeScheduleEntity` holds `List<ChargeRule>`. Each rule declares its own code, basis, rate and applicability.

**Why.** Charges are open-ended: exchange transaction charges, IPFT, MTF interest, physical CMR fees all arrived after the original schema was written, and more will. A fixed schema makes each one a deployment. Every routine change must be data.

**Consequences.** Rate cards become documents, seeded from versioned JSON and reviewable as diffs. Validation moves to write time, because a typo in data cannot be caught by the compiler.

---

## ADR-2 — Computation is a strategy per basis, not a method per charge

**Context.** `UserBrokerChargeService` computes each charge in a dedicated private static method, with GST dispatched through an `if/else if` chain on charge name.

**Decision.** `ChargeCalculator` interface, one implementation per `ChargeBasis`, registered into `ChargeCalculatorRegistry` by Spring collection injection.

**Why.** Separates the two axes of change. A new *charge* is a rule (data). A new *kind of arithmetic* is one new `@Component` that no existing class knows about. Each calculator becomes independently unit-testable, which the private statics were not.

**Consequences.** Modifier application (aggregator → min/max → rounding) belongs in the orchestrator, not in calculators, so it stays uniform.

---

## ADR-3 — Charge codes are catalogued strings, not a Java enum

**Decision.** `ChargeRule.code` is a `String`, validated against a seeded `charge_catalogue` collection.

**Why.** An enum would reintroduce a Java change for every new charge, defeating ADR-1 entirely. The catalogue gives validation without compilation. This mirrors `AllowanceCatalogueEntity` in `taxplanning`.

**Consequences.** An unknown code is caught by `ChargeScheduleValidator` at seed time, not at trade time.

---

## ADR-4 — Delivered in three phases; the engine is built standalone first

**Decision.** Phase A builds the engine with **zero changes under `portfolio/`**. Phase B runs it in shadow. Phase C cuts over. Driven by `app.charges.{engine-enabled, shadow-recording, authoritative}`.

**Why.** Explicit instruction from the repository owner: *"free to create new flow and we can plug this later (so we can be more cautious)"*. It also makes the cutover reversible — a flag flip rather than a rewrite.

**Consequences.** Phase A is verifiable only through `POST /charges/simulate` and tests, which is why that endpoint is not optional. The exit criterion is checked literally with `git diff master --stat -- .../portfolio/`.

---

## ADR-5 — The engine replaces manual charge entry outright

**Context.** Charges are recorded twice today and never reconciled: the user types `AssetRequest.brokerCharges`, which drives cost basis, trade outcome and net P&L, while the engine computes `UserBrokerCharges`, which feeds only the charges report.

**Decision.** The computed total becomes authoritative. `AssetRequest.brokerCharges` is removed at Phase C.

**Why.** Stated by the repository owner: *"This is old flow, we are considering from the input, To replace that entering from the user we are doing this effort."* Removing the manual field is the goal, not a side effect.

**Consequences.** Phase B exists specifically to compare computed against entered on real data before trusting it. A reconciliation endpoint reports the per-transaction delta.

---

## ADR-6 — The charges module owns its own expression evaluator

**Context.** `taxplanning.engine.FormulaEvaluator` is a SpEL wrapper solving a similar problem, but `brokercharges` is not in `taxplanning`'s allowed dependencies.

**Decision.** Write `ChargeFormulaEvaluator` inside `brokercharges/engine`. Do not move, share or import the tax one.

**Why.** Directed by the repository owner, who was right that this was never a blocker: *"you can create new evaluator, so there will not be ambiguous"*. It is also better suited — it returns `double` for money to two decimals where the tax evaluator returns `long` whole rupees, and it exposes charge-specific variables and the live accumulator.

**Consequences.** Two small SpEL wrappers exist in the codebase. That is cheaper than a shared abstraction serving two different numeric contracts.

---

## ADR-7 — A rule names which amount its rate applies to

**Context.** The first draft carried a single `turnover` on the context.

**Decision.** `ChargeContext.baseAmounts` is a `Map<AmountBasis, Double>`; each rule declares an `amountBasis`, defaulting to `TURNOVER`.

**Why.** A charge is a percentage *of something*, and in derivatives that something is not one number. Options STT is levied on premium; futures charges on notional; STT on an exercised option on intrinsic value. One field makes those inexpressible.

**Consequences.** Every rule seeded in Phase A uses `TURNOVER` and every context supplies only that key. Three unused fields now (`amountBasis`, `lotSize`, `orderId`) against a schema change plus reseed later — see ADR-8's Tier framework.

---

## ADR-8 — Extensibility is measured in tiers, and Tier 3 must stay empty

**Decision.** Classify every foreseeable change:

| Tier | Change | Cost |
|---|---|---|
| 1 | New charge code or reprice | Edit JSON |
| 2 | New charge arithmetic | One new `@Component` |
| 3 | New dimension of variation | Schema change + reseed + resolver change |

**Why.** It converts "is this extensible?" from opinion into a test. The whole F&O rate card was walked against the model; every line lands in Tier 1 or 2, *provided* `amountBasis`, `lotSize` and `orderId` exist from day one.

**Consequences.** Justifies carrying unused fields. Also names the honest limits (§9 of README) rather than pretending the model covers everything.

---

## ADR-9 — Charges have two sources: the broker's card and the instrument itself

**Context.** Raised by the repository owner: *"for each MF the charges may be differ."* Correct, and the design did not handle it.

**Decision.** Add `ChargeInstrumentEntity` (`charge_instruments`) as a second rule source, keyed on `stockCode`, versioned exactly like `ChargeScheduleEntity`. Both sources merge into **one ordered evaluation**.

**Why.** Exit load belongs to the scheme, not the broker — HDFC Flexi Cap charges 1% under 365 days, an index fund charges nil, ELSS none at all. Forcing it into the broker schedule means one schedule document per fund: thousands of near-identical documents differing in one rule.

It is also not optional machinery. The rule *"STT applies to equity-oriented mutual funds but not debt funds"* needs `equityOriented` to live somewhere; without this collection it cannot be expressed at all.

**Consequences.** One merged rule list preserves GST bases, ordering and rounding regardless of origin. Each line records `ChargeRuleSource`. A second resolver is needed, reusing the schedule resolver's validity and supersede semantics.

---

## ADR-10 — A rule lives where its rate is decided, and reads other sources through eligibility

**Context.** The mutual fund distributor transaction fee has *three* determinants at once: the broker decides whether to levy it and how much; the scheme's DIRECT/REGULAR status decides whether it can apply at all; AMFI caps it at ₹150 for a first-time investor and ₹100 thereafter.

**Decision.** The rule lives on the source that sets its **rate** — here the broker schedule — and reads the others through its `eligibility` predicate:

```
"eligibility": "#planType == 'REGULAR' and #turnover >= 10000 and #firstTimeInvestor"
```

**Why.** "Broker-level or instrument-level?" is the wrong question when three sources contribute. Placing by rate-ownership is unambiguous and needs no new mechanism.

**Consequences.** **Instrument attributes must be injected into the evaluation context for every rule, not only instrument-sourced ones** — otherwise a schedule rule cannot read `planType`. `#firstTimeInvestor` is derived by `UserChargeService` from prior purchase records. If a platform genuinely varies the fee per scheme, the same rule moves to the instrument with no engine change.

---

## ADR-11 — When both sources declare the same code, the instrument wins

**Decision.** The instrument rule applies; the schedule rule is skipped; the line records `source: INSTRUMENT`; the override is logged at DEBUG.

**Why.** Consistent with the resolver's specificity philosophy — more specific overrides more general. Applying both would double-charge silently, which is the worst possible default for money.

---

## ADR-12 — Superseding closes a date window; it never changes status

**Context.** `EntityStatus` carries `ACTIVE`, `INACTIVE`, `SUPERSEDED`, and the existing `BrokerChargesRepository` query filters `status: 'ACTIVE'`. Surfaced by the repository owner asking about active/inactive and late uploads of past transactions.

**The defect.** Supersede a 2024 card in 2025. In 2026 the user uploads a 2024 transaction. The query requires `ACTIVE`; the card is `SUPERSEDED`; nothing resolves; **the charge computes as zero, silently.**

**Decision.** Two orthogonal concepts get two homes:

| Concept | Expressed by |
|---|---|
| Which card applies on a date | `startDate` / `endDate` **alone** |
| Whether the record is legitimate | `status` — `ACTIVE` vs retracted-in-error |
| Whether a card is *current* | `endDate == null` — never a status |

Superseding sets `endDate = newStartDate.minusDays(1)` and leaves `status` untouched. `INACTIVE` means entered in error, unusable for **any** date. The resolver filters `status != INACTIVE`, **not** `== ACTIVE`.

**Why the `!=` form.** Defensive. If a future maintainer sets `SUPERSEDED` believing it correct, backfill still resolves instead of silently returning nothing.

**Consequences.** Applies identically to `ChargeInstrumentEntity` when an AMC revises exit load. A regression test pins it: *a superseded card still resolves for a date inside its historical window.*

---

## ADR-13 — An unresolved charge is persisted, not merely logged

**Decision.** A `UserChargeEntity` is written even when nothing is computed, carrying a `ChargeResolution` (`RESOLVED`, `NO_MATCHING_RULES`, `NO_SCHEDULE`, `NO_INSTRUMENT_PROFILE`, `PROVISIONAL`). `GET /user-charges/user/{email}/gaps` lists the failures.

**Why.** Backfilling years of history will cross periods with no rate card on file. A WARN in a log scrolls away and the gap becomes invisible; a row is queryable and fixable by seeding the card and recomputing.

---

## ADR-14 — `user_charges` is the source of truth; the P&L charge hierarchy is a derived projection

**Context.** The design accumulated charge aggregates incrementally (`report.merge(...)` per transaction). Recomputation cannot survive that — the old contribution is already folded into a sum and cannot be reliably subtracted.

**Decision.** The live path stays incremental for speed. **Recomputation rebuilds the affected financial year's charge aggregates wholesale from `user_charges`.**

**Why.** Recompute is needed for three real situations: a corrected rate, an out-of-sequence batch, and a `NO_SCHEDULE` period that later gains a card. Without a rebuild path, each one silently drifts the reports.

**Consequences.** Stating the direction of truth explicitly is what stops the two representations diverging.

---

## ADR-15 — Engine arithmetic is `BigDecimal` internally

**Decision.** `ChargeAccumulator` and every calculator compute in `BigDecimal` with explicit scale and `RoundingMode`. `ChargeLine.amount` is exposed as `double` at the persistence boundary only.

**Why.** GST is a percentage of a sum of already-rounded line items. Per-line rounding then re-summing in `double` drifts into visible paise against a real contract note. Without this, golden-file tests to ₹0.01 go flaky and tolerances get widened until they stop meaning anything.

**Related.** Rounding is applied **once**, by the orchestrator, after every other modifier — never inside a calculator.

---

## ADR-16 — Uploads are quarterly and chronological, but the guarantee is guarded

**Context.** Stated by the repository owner: *"every upload will be quarterly and in sequence."*

**Decision.** Rely on it — `#firstTimeInvestor` and per-lot exit load are correct under sequential arrival — but keep `PROVISIONAL` and add a guard: before processing a batch, compare its earliest transaction date against the latest already recorded for that user; if it reaches back, flag the affected computations.

**Why.** Sequencing is a *process convention*, not something the system enforces. Someone will eventually re-run a quarter or load a forgotten file. A system that assumes an unenforced convention produces silently wrong money when it breaks. The guard is one indexed query per batch.

**Consequences — four batch properties that do not follow from single-transaction correctness:**
1. **Idempotent re-upload** — unique index on `{email, transaction_id}`; recompute replaces, never appends.
2. **Intra-batch dedupe** — two same-day sells of one scrip in one batch yield one DP charge, so the batch runs in a single MongoDB transaction with read-your-own-writes, processed in order. `app.mongodb.transactions-enabled=true` is a prerequisite, not an optimisation.
3. **AMC not double-billed** — `ChargeAccountEntity.lastBilledThrough` makes a re-run a no-op.
4. **Resolution amortised** — the resolver cache turns hundreds of transactions into one lookup per distinct scope.

---

## ADR-17 — `equityOriented` is explicit, never inferred from `FundCategory`

**Decision.** A separate `Boolean` on `ChargeInstrumentEntity`.

**Why.** Whether a scheme is equity-oriented for STT depends on its actual equity allocation, not its marketing category. An index fund, an ELSS and a plain equity fund can all qualify. Inferring it from a category enum would be wrong in exactly the cases nobody checks.

---

## ADR-18 — Phase A seeds placeholder rates, and AC-2 is blocked until they are real

**Decision.** Cards are structurally valid with clearly-marked placeholder rates, `verifiedOn: null`, and a `PLACEHOLDER` marker in `notes`. The seeder logs a startup WARN for any card with `verifiedOn: null`.

**Why.** Chosen by the repository owner. The engine's arithmetic, rounding and GST base can be pinned without real rates; only reality-matching cannot.

**Consequences.** **AC-2 ("matches a real contract note to ₹0.01") cannot be closed in Phase A** — the one acceptance criterion that stays open. Golden fixtures still do real work: they fail loudly if the arithmetic regresses. Replacing rates later is a JSON change plus one re-verification.

**Closed on this branch, 2026-09-08.** Scheduled first for post-merge staging, then done here at the repository owner's direction. Every rate was compared against Zerodha's, Upstox's and Fyers' published pages; five defects were found, five successor cards added for two documented rate changes, and all eleven cards now carry `verifiedOn`. Evidence in `ac2-rate-verification.md`.

**What the decision cost, in hindsight.** Placeholders were the right call — the engine was built and proved without waiting on rate research, and replacing them touched no Java, which is the extensibility claim demonstrated rather than asserted. The cost was subtler than "the numbers are wrong": two invented brokerage rates were *plausible*, agreed with reality at the one trade size every fixture used, and would have shipped. A placeholder that is obviously fake is safe; one that looks right is not. Fixtures at a second trade size are what caught them, and are cheap enough that new cards should ship with them from the start.

**Two consequences that outlived the decision.** `verifiedOn` earns its place — it is the only thing separating a checked rate from an invented one, and Tier G now asserts it is populated rather than null. And seeding is a first-run convenience, not a deployment mechanism: the seeder is idempotent by `scheduleCode`, so corrected cards never reach a database that already seeded the old ones.

---

## ADR-19 — Zerodha only, three schedules

**Decision.** Seed `EQUITY/DELIVERY`, `EQUITY/INTRADAY` and `MUTUAL_FUND` for Zerodha alone.

**Why.** Chosen by the repository owner. One broker proves the engine end-to-end; the other two are data-only additions afterwards. Intraday proves the `TradeSegment` dimension actually resolves; the MF card proves `FORMULA` + `eligibility`, the escape hatch the entire Tier-1 claim rests on.

---

## ADR-20 — `TradeSegment` lives in the charges module during Phase A

**Decision.** `brokercharges/dto/enums/TradeSegment.java` now; promoted to `portfolio/dto/enums` at Phase C.

**Why.** Phase A permits no changes under `portfolio/` (ADR-4). The engine takes the segment on its own context record; the simulate endpoint supplies it.

---

## ADR-21 — Renaming the module to `charges` is deferred to Phase C

**Decision.** Keep the package `brokercharges` for now.

**Why.** The name is a misnomer once the module covers scheme-level and account-level charges, but renaming touches `package-info`, modulith `allowedDependencies` and every import. Mechanical churn is better isolated from design change.

---

## ADR-22 — The test framework was hardened before the engine was built

**Decision.** All 14 findings of `../testing/test-framework-audit.md` were fixed and merged as PR #59 before any engine code was written.

**Why.** ~190 engine tests, nearly all asserting money, were about to land in a suite that could not measure coverage, whose exit code was meaningless, that asserted rupee amounts with exact `double` equality, and that required Docker for a pure unit test. Fixing that afterwards would have meant rewriting the suite.

**Consequences.** The gates that make QA-replacement credible now exist: JaCoCo scoped to `brokercharges.engine*` at 90% line / 85% branch, and PIT mutation testing at 85% on the calculation engines. **Mutation score, not coverage, is the gate that matters** — coverage proves a line ran; mutation proves a test would have caught it being wrong.

---

## ADR-23 — Corporate-action transactions are exempt from charges by default

**Context.** Bonus shares, split allotments and demerger entitlements are issued free. `AssetEntity` and `TransactionEntity` both carry `corporateActionType`, so such records are already marked. The superseded `BrokerChargeContext` carried the field too — but no calculator ever read it, and an earlier draft of `ChargeContext` dropped it entirely.

There is also a live asymmetry in `ProfitAndLossService.updateProfitAndLoss:312`: the SELL path guards `actionType == null`, the **BUY path does not**. Both current call sites happen to pass `null`, so nothing is wrong today — but a corporate-action BUY would reach the charge path unguarded the moment Phase C wires the engine in.

**Decision.** `ChargeContext` carries `corporateActionType`. When it is non-null, a rule is evaluated only if it declares `appliesToCorporateActions: true` (default `false`). With no opt-in the computation is empty with `resolution: CORPORATE_ACTION_EXEMPT`.

**Why default-deny.** The failure modes are not symmetric. Charging brokerage and STT on free shares takes money the user never spent. Missing a charge on a buyback understates a cost, which reconciliation catches. A blanket exemption would be wrong too — a buyback tender genuinely attracts brokerage and STT, and a rights subscription involves payment — so the opt-in exists rather than a hard exclusion. Corporate actions are rare relative to trades, so per-rule opt-in costs almost nothing.

**Why an explicit resolution value.** A zero charge on a corporate action must be distinguishable from a zero charge caused by a missing rate card. Both would otherwise be an empty computation.

**Consequences.** Phase C must also close the BUY/SELL guard asymmetry in `updateProfitAndLoss`; until then the engine's own default is the protection. A test asserts that a bonus-share BUY produces `CORPORATE_ACTION_EXEMPT` and zero lines.

---

## ADR-24 — A missing instrument profile is recorded, never fatal

**Context.** A mutual fund transaction whose scheme has no `ChargeInstrumentEntity`. Open item 4.

**Decision.** Three parts:
1. `ChargeScheduleEntity.requiresInstrumentProfile` declares when a profile is expected. The mutual fund card sets it; the equity cards do not, because equity has no scheme-level charges.
2. When expected and absent, broker-level charges are computed anyway and the row records `resolution: NO_INSTRUMENT_PROFILE`, surfacing in the gaps report.
3. `ChargeScheduleValidator` checks every `#variable` in an `eligibility` or `formula` expression against an allow-list of known context variables.

**Why not fatal.** Blocking the transaction would stop a legitimate quarterly upload because reference data is missing. That is the wrong trade — the charge is incomplete, not the trade invalid.

**Why not merely a log line.** A missing profile silently disables a *statutory* charge, not just exit load. The STT rule's eligibility reads `#equityOriented`; with no profile that variable is null, `null == true` is false, and STT is quietly not charged. A warning would let a government levy go unbilled invisibly. Recording it makes the gap queryable and fixable by seeding the profile and recomputing.

**Why the allow-list.** Rate cards are data, so no compiler sees them. A typo such as `#equityOrientd` parses cleanly, evaluates to null, silently disables its rule, and stays broken forever. The validator is the only place this can be caught.

**Consequences.** One field on the schedule, one validator rule, and one extra `ChargeResolution` value already present.

---

## ADR-25 — `AccountType` does not change charges, but `accountHolder` belongs in the dedupe key

**Context.** `AccountType` is `{SELF, OUTSOURCED}` and `accountHolder` is a separate `String` partitioning holdings — `findEligibleHoldingsForSell(email, stockCode, brokerName, accountHolder, date)`. Open item 5.

**Decision.** Charges do not vary by `AccountType`: the broker levies the same amounts regardless of beneficial owner, and the distinction affects only which P&L bucket the result lands in.

**But every dedupe scope is keyed per account holder.** `PER_SCRIP_PER_DAY`, `PER_ORDER` and `PER_DAY` all include `accountHolder`.

**Why — this is a live defect (D10).** The existing query is:

```java
@Query("{ 'email': ?0, 'broker_name': ?1, 'stock_code': ?2, 'transaction_date': ?3, 'type': 'SELL' }")
```

`accountHolder` is absent. A depository charge is levied **per demat account**. A user tracking holdings for more than one person who sells the same scrip on the same day in two accounts incurs two separate demat debits and therefore two charges — but only one is recorded. The design inherited this key before the omission was noticed.

**Consequences.** The dedupe index becomes `{email, account_holder, broker_name, stock_code, transaction_date}`. `ChargeAccountEntity` is likewise keyed per demat account, since each account attracts its own AMC. A test asserts that two same-day sells of one scrip under *different* account holders produce two DP charges, while two under the *same* holder produce one.

**Outcome.** The defect was also **fixed in the live implementation** and merged as PR #60, rather than waiting for the Phase C cutover — users were being undercharged now, and the engine is months away. That fix threaded `accountHolder` through `BrokerChargeContext`, persisted it on `UserBrokerCharges`, converted the dedupe query to an `exists` returning `boolean` (also addressing D9's shape), and passed `dematAccountId` for AMC entries. Pre-existing rows have `account_holder` unset and group under `null`, which reproduces today's behaviour; no migration was performed and no historical charge was recomputed.

---

## ADR-26 — A deployed rate card is never edited; it is superseded

**Decision.** Once a card has been deployed, its seed file is immutable. A rate change ships as a
**new generation** with a new `scheduleCode` and a start date, never as an edit to the card in force.
Editing a shipped file is reserved for cards that have not yet reached any environment, and for
correcting a card that was wrong from the day it shipped — which is a different operation with a
different cost, below.

**Why.** Raised by the repository owner on 2026-09-08: *"this is not sustainable, what do I do when
deploying to prod?"* The answer had three parts and only one of them was in the design.

`ChargeSeederService` is idempotent by `scheduleCode`, so a card already on file is never overwritten.
That is correct — an operator who corrects a rate through the API must not lose it on the next
restart — but it means an edited seed file simply never arrives. The AC-2 corrections of 2026-09-08
demonstrated it: the running application still reported six unverified cards after the files beside
it had been fixed, and the only way to apply them was to delete the documents and restart.

Deleting documents is not a deployment mechanism. But nothing needs to be: because `scheduleCode`
carries a generation (`ZERODHA_EQ_DELIVERY_2026_03`), **a rate change is always a new code**, so the
seeder applies it on the next deploy with no manual step and no drift. The mechanism already worked;
what was missing was the rule that keeps it working.

**The three operations, which must not be confused.**

| Situation | Operation | Mechanism | Cost |
|---|---|---|---|
| A rate changed on a date | New generation | New `scheduleCode`; seeder applies it on deploy | A seed file, and a golden fixture in the new window |
| A card was wrong from the start | Amend, then recompute | `POST /charge-schedules`, then `POST /charges/recompute` over its `scheduleId` (tech-spec §14.4) | Every charge it priced must be re-derived |
| Somebody edited a deployed file anyway | Detect it | `GET /charge-schedules/drift` | A human deciding which side is right |

The middle row is why the first row matters. Superseding is cheap and leaves history intact; amending
rewrites charges that users have already been shown. Treating a rate change as an amendment, because
editing a file is easier than writing a new one, converts a cheap operation into an expensive one and
loses the record of what was charged and why.

**Consequences.**

- **Drift is reported, not silent.** The seeder now logs a warning naming the differing fields, and
  `GET /charge-schedules/drift` lists them on demand. It deliberately does not say which side is
  right: a rate corrected in production and a file nobody deployed look identical and need opposite
  fixes.
- **Verification expires.** `findUnverified()` returns cards with no `verifiedOn` *and* cards whose
  `verifiedOn` is older than 90 days. Without that, AC-2 closes once and the worklist stays empty
  while reality moves — which is exactly what happened between April 2025 and September 2026, when
  NSE revised the transaction charge and Zerodha cut its depository fee under cards that had been
  signed off. Verification is a standing obligation, not an event.
- **The horizon is a constant, not a property.** Nothing in this application injects configuration by
  field, and a constructor parameter would be the only other shape. It belongs on
  `ChargeEngineProperties`, which Chunk 8 introduces for the shadow-recording flag.
- **Recompute is now load-bearing.** It was designed in tech-spec §14.4 and is not built. Until it
  is, the amend row of that table has no safe mechanism, so a card that has priced anything must not
  be amended. That is fine through Phase A, where nothing prices anything, and becomes a real
  constraint the moment Phase B starts recording.

**What this does not solve.** Nothing yet promotes a verified card from staging to production; the
files travel with the deployment and the verification is repeated per environment. Worth doing when
there is more than one environment that matters.

---

## ADR-27 — Seeding is an authenticated operation, not a startup side effect

**Decision.** `ChargeSeederService.seed(auditor)` is no longer `@PostConstruct`. It is reached only
through `POST /charges/seed`, restricted to `SUPER_USER`, and every document it writes is stamped
with the caller and the time. Nothing writes rate cards at boot, in any environment.

**Why.** Proposed by the repository owner while resolving the deployment problem in ADR-26: an
endpoint is *deliberate*, and a deliberate act has an author. Booting does not.

Two things were wrong with seeding at startup:

1. **A deployment wrote to the database as a side effect of starting.** In production that means a
   restart — a rollback, a crash loop, an autoscaler — silently reapplies reference data. Idempotence
   makes that survivable, not correct.
2. **Nobody could say who seeded, or when.** There is no security context during `@PostConstruct`, so
   `SecurityAuditorAware` answers `"unknown"`, and a seeded card recorded nothing about its origin.

**A defect found underneath the second one.** Seeded cards carried *no* audit metadata at all — all
four fields null — while a transaction written through the ordinary path records who created it and
when. The first diagnosis here was that Spring Data's auditing does not descend into an embedded
document and that `AuditableEntity` was therefore decorative across the repository. **That was
wrong**, and the repository owner said so: auditing works, and has been working.

The real cause is narrower and entirely on the seeder's side. Lombok stamps
`@ConstructorProperties` onto `@AllArgsConstructor`; Jackson honours that as a creator; and a card
built through it has a **null** `auditMetadata` where one built through the no-arg constructor has an
empty one. Spring Data's auditing *fills* an `AuditMetadata` — it does not create one. Handed a null,
it has nothing to write into and silently does nothing.

The fix is one line in the seeder: parse into an instance it constructs itself, with
`readerForUpdating`, so the field initialiser runs. No audit field is assigned anywhere;
`@EnableMongoAuditing` and `SecurityAuditorAware` do the work they were already doing for every other
entity. `ChargesIntegrationTest` asserts a seeded card's `createdBy` is the authenticated caller,
against a real database.

**Worth generalising.** Any entity in this codebase deserialised from JSON by Jackson has the same
hole, and it is invisible: the document saves, the write succeeds, and only the audit trail is
missing.

**Consequences.**

- **A fresh database has no charge data until somebody asks.** That is the intended trade. The
  runbook gains a first step, and `GET /charge-schedules/drift` reports every shipped card as
  `absent from the database` until it is run — which is a useful thing for a deployment check to say.
- **AC-9's guarantee moved but did not weaken.** A malformed shipped card used to stop startup; it now
  fails the seed call. The build-time guarantee is unchanged and was always the stronger one:
  `ChargeSeederServiceTest` validates every shipped file against the real classpath, so a bad card
  fails CI long before any environment sees it.
- **Integration tests seed explicitly.** `ChargesIntegrationTest` calls the seeder in `@BeforeEach`
  for the catalogue's sake and then clears the shipped cards, because the validator rejects any rule
  whose code the catalogue does not carry.
- **No entity changed.** An earlier attempt added a `stampWrittenBy` method to the three seeded
  entities and dropped their `@Setter(AccessLevel.NONE)`. Both were reverted: no other entity carries
  such a method, and none was needed once the actual cause was found. The seeded entities are
  byte-identical to what they were.

---

## ADR-28 — The `assetType == EQUITY` gate stays; the shadow call goes outside it

**Decision.** Phase B does **not** remove the `assetType == EQUITY` gate in
`ProfitAndLossService.handleNormalBuyCase` / `handleNormalSellCase`. The shadow recording call is
placed *outside* it instead, so every asset type reaches the engine while the superseded
implementation continues to see equity only. The gate is removed in Phase C, when the path it guards
is deleted.

**Why this reverses a written instruction.** The Chunk 8 checklist and tech-spec §9.2 both said to
remove the gate, citing FR-8. Taken literally that would have been a live behaviour change, and the
wrong one:

`UserBrokerChargeService.addUserBrokerChargeEntry` resolves a rate card by **broker and date only**.
It has no asset-type dimension anywhere in it — `getBrokerage`, `getGovtCharges` and `setTaxes` all
read the same `BrokerCharges` document whatever the instrument is. Removing the gate would therefore
have priced a mutual fund redemption and a bond purchase with equity brokerage, securities
transaction tax and stamp duty, and written those figures into the P&L through
`updateBrokerChargesReport`. That is precisely what Chunk 8's own gate forbids — "assert **no**
change to P&L numbers when shadow recording is on" — so the checklist contained two instructions that
could not both be satisfied.

**FR-8 is satisfied as written.** The PRD says "Every asset type flows through the engine". The
engine is the new one. Nothing in FR-8 requires the superseded implementation to start pricing
instruments it was never given rates for; the gate's removal was a means that had been recorded as
if it were the end.

**Raised by the repository owner, and settled with a further constraint:** V1 `addTransaction` —
`buyStock` and `sellStock` — is unused and kept only for version history. Phase B therefore targets
the V2 flow. That maps cleanly onto the two overloads, which are distinct methods rather than a
single path:

| Overload | Reached from | Shadow recording |
|---|---|---|
| `updateProfitAndLoss(UserMail, ProfitLossContext)` | `buyStockV2` → `updateBrokerChargesAndProfitAndLoss`; `sellStockV2` → `updateQuantityBySavingReportAndProfitAndLoss1` | **Yes** — both handlers |
| `updateProfitAndLoss(UserMail, ProfitAndLossContext)` | V1 `sellStock` only. Already `@Deprecated(forRemoval = true)` | **No** — untouched |

**Consequences.**

- Phase B's entire footprint in `portfolio/` is one new interface and fifteen inserted lines in one
  file. `git diff master --stat -- .../portfolio/` shows `ProfitAndLossService.java` alone.
- A non-equity trade now produces a shadow row and no P&L change. Where the shipped data has no card
  for that asset type the row records `NO_SCHEDULE` or `NO_MATCHING_RULES` rather than a silent zero,
  and the reconciliation report leaves it out of the totals rather than reporting the entered amount
  as an undercharge.
- The checklist line and tech-spec §9.2 are corrected rather than quietly skipped, and the gate
  removal is moved to Chunk 10 where it belongs — by then the branch behind it is being deleted, so
  removing it changes nothing.
- A corporate-action **sell** is not shadow-recorded, because the live flow does not process one
  either (`updateProfitAndLoss` dispatches to `handleNormalSellCase` only when `actionType == null`).
  Recording a charge for an event the P&L ignores would put a row in the reconciliation report with
  nothing to reconcile it against.

---

## ADR-29 — An instrument master is the single source of truth, and charge profiles key off it

**Status:** decided by the repository owner, 2026-09-09. **Scheduled for Milestone 2** — not built,
and not part of Phase C.

**Decision.** One registry holds every instrument the application recognises — equities and mutual
fund schemes alike. Upload validates against it: a transaction naming an instrument the registry does
not carry is rejected rather than stored. `ChargeInstrumentEntity` then keys on the registry's
canonical code instead of a free-text `stockCode`.

**What prompted it.** The Phase B backfill against real data
(`phase-b-reconciliation-findings.md` §5). 43 mutual-fund buys across 8 schemes resolved
`NO_INSTRUMENT_PROFILE`, and the reason was not that the profiles were missing — it was that they
could never have matched. Real holdings carry `stockCode` values like

```
EDELWEISS NIFTY SMALLCAP 250 INDEX FUND - DIRECT PLAN
```

— the full scheme name as the user's broker statement spells it — while the shipped profiles are
keyed on short codes such as `HDFCLIQUID`. `ChargeInstrumentEntity` is keyed on `stockCode`
(README §8.14), so the two can only meet if whoever writes a profile happens to reproduce the exact
string the portfolio stored, punctuation and spacing included.

That is a convention holding two subsystems together, and conventions of that kind fail silently
here: the redemption is priced, the exit load is simply absent, and the result is a smaller charge
rather than an error. It is the same failure shape as ADR-24's missing profile, reached by a route no
validation can see — because the profile *is* present and *is* valid, it just describes a scheme
nobody can name the same way twice.

**Why a registry rather than normalising the string.** Case-folding, trimming and punctuation-
stripping would close most of the gap and leave the interesting part open: two brokers spell the same
scheme differently, a scheme is renamed, a direct plan and a regular plan differ by one word. Every
normalisation rule is a guess about which differences are meaningful. A registry moves the question
to where it can be answered once, by a human, and then enforced.

**Why rejecting the upload is the right severity.** It contradicts ADR-24, which says a missing
instrument profile is recorded and never fatal — and the distinction is worth being precise about.
ADR-24 governs *charges*: an instrument the engine cannot fully price still produces a transaction,
because a portfolio is more than its charges. ADR-29 governs *identity*: an instrument the
application cannot name is one it cannot hold a position in, aggregate, or report on. A charge gap
degrades one number. An identity gap corrupts the holding.

**Consequences.**

- **The mismatch becomes impossible by construction.** `AssetEntity`, `TransactionEntity` and
  `ChargeInstrumentEntity` all carry the same canonical code because nothing else can be stored, so a
  profile either resolves or names an instrument that does not exist — which is a startable error
  rather than a silent zero.
- **Exit load becomes reachable for real funds.** Today only two schemes have profiles and neither is
  one anybody holds. A registry makes "write a profile for this scheme" a task somebody can do
  correctly.
- **`ChargeInstrumentEntity.isin` stops being speculative.** It is stored and unused today
  (README §8.14); the registry is what gives it something to join to.
- **Rejection needs a route back.** A user whose upload is refused because a scheme is unknown must
  be able to get that scheme added, or the validation becomes a wall. Whether that is an admin
  endpoint, a seeded catalogue refreshed from an external source, or self-service is a Milestone 2
  design question, not settled here.
- **Existing data will not satisfy it.** All 319 transactions on `it-staging` predate the registry, so
  a migration has to map what is already stored onto canonical codes — with the 8 unmatched schemes
  above as the known worklist.

---

## ADR-30 — `engine-enabled` is a real kill switch, gated at the entry points

**Decision.** `app.charges.engine-enabled` stops the charges engine computing or recording anything.
It is checked at the four entry points that invoke the engine — simulate, backfill, the AMC cycle and
the shadow-recording gateway — and **not** inside `ChargeEngine.compute`.

**Why it needed doing at all.** The flag existed from Chunk 8 and was read by nothing. It shipped as
`engine-enabled: true` with a comment stating "the engine is live", which reads as an assertion about
a live switch. It was decorative: setting it `false` changed nothing at all. That is the worst shape
a flag can have — `authoritative` is equally inert but says so, whereas this one invited an operator
to reach for it during an incident and get no effect and no error.

**Why not inside `ChargeEngine.compute`.** One check there would have covered every caller, and it is
the wrong place. The engine's contract is to return a `ChargeComputation`, and a disabled one would
have to return an empty result carrying some resolution. Every available resolution already means
something else, and `UserChargeService` would dutifully **record** it — so switching the engine off
would quietly fill `user_charges` with rows indistinguishable from "no rate card on file", the gaps
report would blame the seed data, and the damage would outlast the incident. Adding an
`ENGINE_DISABLED` resolution would avoid the ambiguity and still write rows nobody wants.

A disabled engine must write **nothing**, and only the callers know how to decline: refuse the
request, or skip the recording. So the check lives with them, behind `ChargeEngineSwitch`.

**Why 503, and a new exception type.** `ServiceUnavailableException` is added to `shared/exception`
and mapped by `ControllerAdviser`, following the pattern already there. The caller did nothing wrong
and cannot fix anything, so 400 would send them hunting for a fault in their own request; 500 would
send somebody looking for a crash that did not happen. 503 is the one status that says "an operator
turned this off". It lives in `shared` because the adviser is in `shared` and `shared` may not depend
on `brokercharges` — the modulith test enforces that.

**Why the gateway refuses silently instead.** It sits in the trade path. Throwing there would mean a
trade failing to save because a charge could not be computed, which is exactly the outcome ADR-28's
error handling exists to prevent. It returns empty, and the absence shows up as
`transactionsWithoutComputation` in the reconciliation report.

**Consequences.**

- **One flag stops everything.** `engine-enabled` outranks `shadow-recording`, so an operator does
  not have to find and clear a second flag. That is the only property that makes a kill switch usable
  when it is actually needed.
- **Reads stay up.** Charge history, gaps, the catalogue and the reconciliation report all keep
  working while the engine is off — which is what somebody needs in order to decide whether to turn
  it back on.
- **`ChargeEngineDisabledIntegrationTest` is the only class running with it false**, so every other
  integration class staying green is the evidence that it ships on.
- **`authoritative` is still inert**, and its comment now says so in capitals. It becomes real in
  Chunk 10; until then setting it changes nothing.
