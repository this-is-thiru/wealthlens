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
