# Charges Engine — Start Here

**Purpose of this file:** the single entry point. If you are resuming this work — new session, new person, lost context — read this first and trust nothing about the codebase that is not stated here or verified from the code.

**Last verified against the repository:** 2026-09-09, branch `feature/charges-engine`. **Complete.** All twelve chunks done, every acceptance criterion signed off, the superseded implementation deleted and its collections dropped. 877 tests, 99% mutation score. Full suite green: **881 tests**, unit and integration, both JaCoCo gates passing, 99% mutation score (577/578 at last measurement, before the segment work).

---

## 1. Status at a glance

| | |
|---|---|
| **Branch** | `feature/charges-engine`, rebased onto `master` after PR #59 (test framework) and PR #60 (D10 fix) |
| **Commits beyond master** | **46**, of which **11 are unpushed** (`git log origin/feature/charges-engine..HEAD`). Counted with `git rev-list master..HEAD --count` — trust the command over this cell, which has been wrong before |
| **Phase** | A, B and **C complete**. Chunk 11 deleted 25 files; V1 buy/sell is untouched and still live |
| **Next action** | **Raise the PR.** All twelve chunks are done and every acceptance criterion is signed off |
| **Blocking questions** | **Two, both non-urgent** — see §11.2. (1) Does `YearlyChargeSummary` sit beside `BrokerChargesReport` or replace it? (2) Keep or drop `userChargeId` on `TransactionEntity`? Neither blocks reading the code |

---

## 2. What actually exists in code right now

**This list is exhaustive.** Everything else in this documentation is *design*, not code.

### Written by this feature (commit `67ec107`)

14 enums in `backend/src/main/java/com/thiru/wealthlens/brokercharges/dto/enums/`:

```
AggregatorType      ChargeCategory      ChargeSide        PlanType
AmountBasis         ChargeEvent         DedupeScope       RoundingPolicy
ChargeBasis         ChargeResolution    FundCategory      SlabBandBasis
                    ChargeRuleSource                      TradeSegment
```

Plus documentation: this file and the five listed in §3.

### Landed on `master` via PR #60 (D10 — a defect found while designing this)

The DP dedupe query omitted `accountHolder`, so a user tracking more than one account holder was charged once for what were two separate demat debits. Fixed in the **existing** implementation rather than waiting for Phase C, because users were being undercharged now. `BrokerChargeContext` and `UserBrokerCharges` gained `accountHolder`; the dedupe query became an `exists` returning `boolean`; AMC entries pass `dematAccountId`.

### Landed on `master` via PR #59 (test framework hardening)

- `backend/src/test/java/com/thiru/wealthlens/testsupport/MoneyAssert.java` — `assertMoney`, `assertNoCharge`, `assertBreakdown`
- `backend/src/test/java/com/thiru/wealthlens/architecture/` — `ArchitectureTest` (8 ArchUnit rules) + `SnakeCaseFieldCondition`
- `backend/src/test/resources/junit-platform.properties` — parallel execution config
- `AbstractIntegrationTest` — `@Tag("integration")`, `@Isolated`, dynamic `cleanDatabase()`
- Root `pom.xml` — JaCoCo 0.8.14, `-Punit` / `-Pintegration` / `-Pmutation` profiles, `maven.test.failure.ignore` defaulting to `false`
- `.github/workflows/cicd.yaml` — spotless folded into the main invocation, minimum-report gate, `jacoco.xml` artifact

Eight entities in `entity/`: `ChargeSlab`, `ChargeRule`, `ChargeLine`, `ChargeScheduleEntity`, `ChargeInstrumentEntity`, `UserChargeEntity`, `ChargeCatalogueEntity`, `ChargeAccountEntity`.

Five repositories in `repository/`: `ChargeScheduleRepository`, `ChargeInstrumentRepository`, `UserChargeRepository`, `ChargeCatalogueRepository`, `ChargeAccountRepository`.

`config/ChargeIndexInitializer` — creates the declared indexes, because `auto-index-creation` is off application-wide and the annotations would otherwise create nothing.

`ChargeRepositoryIntegrationTest` — 14 tests proving the documents map and the queries execute.

### Written by Chunk 3

In `brokercharges/engine/`: `ChargeRounding`, `ChargeAccumulator`, `ChargeFormulaEvaluator`, `ChargeCalculator`, `ChargeCalculatorRegistry`, `ChargeEngine`, and `ChargeScheduleResolver` **as an interface only**. In `dto/context/`: `ChargeContext`, `ChargeComputation`, `LotSlice`.

Tests: `ChargeEngineTest` (42), `ChargeRoundingTest` (20), `ChargeFormulaEvaluatorTest` (17), `ChargeAccumulatorTest` (9), `ChargeCalculatorRegistryTest` (6).

### Written by Chunk 4

Seven calculators in `brokercharges/engine/`, one per `ChargeBasis`, all `@Component`: `TurnoverChargeCalculator`, `FlatChargeCalculator`, `PerUnitChargeCalculator`, `SlabChargeCalculator`, `ScopedFlatChargeCalculator`, `DerivedChargeCalculator`, `FormulaChargeCalculator`. Plus `ChargeAmounts`, the shared guards, and `ChargeRule.effectiveSlabBandBasis()`.

`ChargeCalculatorRegistry` is now a `@Component` — every basis is served, so its completeness check can pass. `ChargeEngine` is still not one; it needs a `ChargeScheduleResolver` bean, which is Chunk 5.

Tests: 81 across seven classes, plus `testsupport/LogCapture`.

### Written by Chunk 5

`ChargeScheduleResolver` (specificity ranking, tie-breaks, cache) and `ChargeInstrumentResolver`, both `@Component`. `ChargeEngine` is now a `@Service` and merges instrument-sourced rules into one ordered evaluation, publishing the scheme's attributes to eligibility predicates.

Tests: `ChargeScheduleResolverTest` (14), `ChargeInstrumentResolverTest` (7), `ChargeEngineTest` grown to 56, `ApplicationContextIntegrationTest` (3).

Five services in `brokercharges/service/`: `ChargeScheduleValidator`, `ChargeScheduleService`, `UserChargeService`, `ChargeAccountService`, `AmcChargeService`. Tests: 31, 17, 20, 8 and 12 respectively, plus `ChargeInvariantTest` (10 properties over 200 generated cards each) and `testsupport/LogCapture`.

Both quality gates now cover `brokercharges.service` as well as the engine — the JaCoCo rule per class, so a new service cannot be carried by its neighbours. Engine 98.5% line / 91.9% branch; every charges class at 100% mutation but `ChargeFormulaEvaluator`'s one equivalent mutant.

### Written by Chunk 6

`resources/data/charges/`: `charge-catalogue.json` (12 codes) and six rate cards — Zerodha delivery, intraday, mutual fund and **maintenance**; Upstox and Fyers delivery. `service/ChargeSeederService` (`@PostConstruct`, catalogue first, idempotent by code, validating before persisting, failing fast). `ChargeSeederServiceTest` — 14 tests, test-plan Tier G, run against the real files. `ChargeGoldenFileTest` — 16 frozen contract notes in `src/test/resources/charges/golden/`, test-plan Tier E, priced through the real resolver, engine and calculators against the shipped cards.

### Written after Chunk 9, closing what Chunk 6 left open

**The AMC rate card**, `zerodha-maintenance-2025-04-01.json`. Unscoped on purpose — `assetType`, `segment`, `exchange` and `planCode` all unset — because the cycle context carries none of them and a card declaring any is disqualified by the resolver. Both its rules declare `AMC_CYCLE` and nothing else: an unscoped card is the fallback for every asset type no specific card covers, so a rule of its own reaching BUY or SELL would price ordinary trades as maintenance. Golden fixture `zerodha-maintenance-amc-cycle`.

**Two scheme profiles**, in `resources/data/charges/instruments/` — a graded exit load banded on `HOLDING_DAYS`, and one expressed as the predicate `#holdingDays < 7`, both `perLot`. This is what closes AC-6. They live in their own directory because the schedule pattern does not descend into it, so a profile can never be read as a rate card with every field null.

**Seeding is an operation, not a startup hook (ADR-27).** `POST /charges/seed` — `SUPER_USER` — is the only way shipped data enters a database; `@PostConstruct` is gone, so no deployment writes rate cards as a side effect of booting. Every document it writes records the authenticated caller — through Spring Data's auditing, with no audit field assigned anywhere. That needed one fix in the seeder: it parsed cards into instances Jackson constructed, whose `auditMetadata` is null because Lombok's `@ConstructorProperties` makes `@AllArgsConstructor` a creator and the field initialiser never runs. Auditing fills an `AuditMetadata`; it cannot create one. Parsing into a pre-built instance (`readerForUpdating`) is the whole change.

`ChargeScheduleService.findUnverified()` now returns cards whose `verifiedOn` is older than 90 days as well as those with none, so verification is a standing obligation rather than a one-off (ADR-26). `ChargeSeederService.findDrift()` and `GET /charge-schedules/drift` report where the shipped files and the database disagree — the seeder skips a card already on file, correctly, and used to do it silently.

`ChargeSimulationRequest` gained `lots`, so `POST /charges/simulate` can price a redemption per FIFO lot — without it a `perLot` rule evaluated zero times and exit load answered ₹0 through the API however the profile was written. A lot set that does not describe its disposal is rejected rather than priced: every way of getting it wrong makes the charge smaller rather than making the call fail.

`ChargeSeederService` gained `seedInstruments()` (last, after the catalogue and the cards; idempotent by scheme **and start date**, a profile having no code of its own) and now evicts `ChargeInstrumentResolver` alongside the schedule resolver's cache. `ChargeScheduleValidator` gained `validate(ChargeInstrumentEntity)`: the same window and rule checks, because validating only the broker's card left exit load — the one charge a rate card cannot express — entirely unchecked.

Tests: `ChargeSeederServiceTest` 14 → 24, `ChargeScheduleValidatorTest` 31 → 35, `ChargeSimulationServiceTest` 14 → 27, four new golden fixtures, four new cases in `ChargesIntegrationTest`.

### Written by Chunk 8 — Phase B, shadow recording

**The engine now sees every trade the live flow processes, and changes none of it.**

`config/ChargeEngineProperties` — `app.charges.{engine-enabled,shadow-recording,authoritative}`,
declared in all three profile yamls with **shadow recording off**. Turning it on is what gives the
reconciliation report data; turning it off is the whole rollback.

`portfolio/service/ChargeRecordingGateway` — an interface owned by `portfolio`, returning
`Optional<ChargeComputation>`. `brokercharges/service/ChargeRecordingGatewayImpl` implements it:
it maps a `ProfitLossContext` onto a `ChargeContext` and calls the existing
`UserChargeService.computeAndRecord`. Every failure inside it is caught and logged — a shadow record
exists to be compared later, and a trade that failed to save because its shadow copy could not be
priced would be strictly worse than having no shadow copy.

`ProfitAndLossService` calls it in both handlers and **ignores what comes back**. Phase B's entire
footprint in `portfolio/` is that one file, fifteen inserted lines, plus the interface.

`service/ChargeReconciliationService` and `GET /user-charges/user/{email}/reconciliation` — computed
against entered, per trade, with the delta. Sums are `BigDecimal`; two kinds of row are listed with a
note and **left out of the totals**, because subtracting either produces a figure that reads as a
defect and is not one: a computation that did not resolve, and a row whose transaction is gone.

`ChargeResolution.UNRESOLVED` and `isUnresolved()` — promoted onto the enum from a private constant
in `UserChargeService`, because the gaps report and the reconciliation report must agree on what
"unresolved" means and a duplicated list is how they start not to.

Tests: `ChargeRecordingGatewayImplTest` (15), `ChargeReconciliationServiceTest` (7),
`ProfitAndLossServiceTest` 11 → 15 with **no existing assertion changed**,
`ShadowRecordingIntegrationTest` (4) and four reconciliation cases in `ChargesIntegrationTest`
(33 → 37). `LogCapture` gained `errors()` alongside `warnings()`.

**Two decisions are recorded as ADR-28 and are the ones to read before touching this.** The
`assetType == EQUITY` gate **stays** — it guards the superseded implementation, which has no
asset-type dimension at all, so removing it as the checklist originally said would have priced mutual
funds as equity and written that into the P&L. And only the **V2** flow is instrumented, the V1
`addTransaction` path being **in live use** and deliberately left alone.

### Written after Chunk 8 — the backfill

`service/ChargeBackfillService` and `POST /charges/backfill/user/{email}` (`SUPER_USER`). It prices a
user's existing transactions and records one `user_charges` row each — nothing else is touched, and a
row is keyed on `{email, transactionId}` and replaced, so a re-run reprices rather than duplicates.

**The FIFO reconstruction is the part that is not a loop over a repository.** A `TransactionEntity`
for a sell does not record the lots it consumed: the live path is handed them by `PortfolioService`'s
walk over open holdings, and that walk is destructive, so by the time a backfill runs the holdings
are gone or changed. Replaying the buys in date order, per scrip *and* broker *and* account holder,
is the only way to recover them. Without lots a `perLot` rule evaluates zero times, so a fund
redeemed inside its exit-load window would be backfilled as free — and it would read as the engine
disagreeing with the user rather than as missing input. `sellsWithNoLotsFound` on the report is how
far that reconstruction got, and it caps how much the exit-load figures can be trusted.

Tests: `ChargeBackfillServiceTest` (16) and five integration cases. Two of the unit tests exist
because mutation testing found the FIFO walk's edges unguarded — a sell consuming a lot *exactly*
must remove it rather than leave a zero-quantity husk for the next sell to draw from, and a sell
exceeding what is open must take the remainder rather than the amount asked for.

### The configuration flags, and which of them do anything

`app.charges.*`, declared in all three profile yamls. **ADR-30** is the entry to read.

| Flag | Ships | Effect |
|---|---|---|
| `engine-enabled` | `true` | **Master switch.** `false` → simulate, backfill and the AMC cycle answer **503**; shadow recording records nothing whatever `shadow-recording` says. Reads stay up |
| `shadow-recording` | `false` | `true` → every V2 buy and sell is priced and a `user_charges` row written, result ignored. No effect when the engine is disabled |
| `authoritative` | `false` | **Read by nothing.** Becomes real in Chunk 10; setting it today changes nothing |

Two things about this that are easy to get wrong later:

- **The kill switch is checked at the entry points, never inside `ChargeEngine.compute`.** A disabled
  engine returning an empty computation would be recorded by `UserChargeService` as a row
  indistinguishable from "no rate card on file", so switching the engine off would quietly fill the
  database with rows the gaps report blames on the seed data. A disabled engine must write nothing.
- **`app.mongodb.transactions-enabled` reads backwards.** It is
  `@ConditionalOnProperty(matchIfMissing = true)`, so transactions are **on when the property is
  absent** — it is a switch to turn them off. Staging and prod omit it and are correct. With the
  `MongoTransactionManager` bean gone, every `@Transactional` silently becomes a no-op, which is why
  `TransactionSafetyAuditor` warns at startup.

### Written by Chunk 10a and 10b part 1 — Phase C so far

**`PortfolioService` is authoritative for cost basis when the flag says so.** `buyStockV2` computes
the charge before writing the lot and, under `app.charges.authoritative`, stores the engine's total
instead of the entered one (AC-10). It has its own path now — V1 `buyStock` still calls the shared
`updateBrokerChargesAndProfitAndLoss` and is byte-for-byte unchanged.
`ProfitAndLossService.updateProfitAndLoss(userMail, context, precomputed)` is the 3-arg overload that
uses a computation it is handed, so the engine runs once per trade.

**`TradeSegment` now lives in `portfolio/dto/enums`** and is a field on `AssetRequest`,
`TransactionEntity`, `AssetEntity` and `ProfitLossContext`, defaulting `DELIVERY`.
`ChargeRecordingGatewayImpl` and `ChargeBackfillService` read it rather than assuming delivery.

**`AssetRequest.brokerCharges` is `@Deprecated`** — accepted and stored, no longer read once
`authoritative` is on.

Tests: `PortfolioServiceTest` 6 → 11, `ChargeRecordingGatewayImplTest` → 17,
`ChargeBackfillServiceTest` → 19, `ChargesIntegrationTest` → 43.

### Written by Chunk 7

`entity/model/ChargeSummaryReport` and its `YearlyChargeSummary` / `MonthlyChargeSummary` forms — a map keyed by charge code replacing six fixed columns, summed in `BigDecimal` with the total recomputed from the parts rather than accumulated beside them. `ChargeSummaryReportTest`, 11 cases. Nothing writes one yet: `ProfitAndLossService` keeps the old `BrokerChargesReport` until Phase C.

### Written by Chunk 9

Four controllers — `ChargeScheduleController` (publish, fetch, list, close, unverified, catalogue), `ChargeSimulationController` (`POST /charges/simulate`), `UserChargesController` (history, contract note, gaps) and `ChargeAccountController` (register, list, AMC cycle). Two services behind them: `ChargeSimulationService` (holds the engine and no repository, so a dry run cannot write) and `ChargeCatalogueService`. `UserChargeService.findHistory` gained a date range and asset-type filter. `ChargesIntegrationTest` (24 cases) and `ChargeExtensibilityTest` (3, Tier J). An eleven-request `Charges Engine` folder in `api-collection/`.

`AuthConfig` gained the five new prefixes. It needed them: the chain ends in `anyRequest().permitAll()`, so every charges endpoint was public. Publishing a rate card and running the AMC cycle additionally require `SUPER_USER`.

### NOT written yet — do not assume any of it exists

**Since Chunk 10a the live path *can* depend on this, but does not by default.** With
`authoritative: false` — which is what ships — a V2 buy still stores the entered charge, exactly as
before. The paragraph below describes the shipped default.

**Nothing in the live path *depends* on any of this.** Since Chunk 8 the trade path does call the engine — `ProfitAndLossService` hands every V2 buy and sell to `ChargeRecordingGateway` — but only when `app.charges.shadow-recording` is on, and it ignores the result. No cost basis, no P&L figure and no stored transaction reads a computed charge. Phase C is what makes it authoritative.

### The old implementation is fully intact and untouched

13 files under `brokercharges/` still implement the superseded design: `BrokerCharges`, `UserBrokerCharges`, `BrokerChargeService`, `UserBrokerChargeService`, both repositories, both controllers, `BrokerChargeContext`, `BrokerChargesRequest`, `BrokerageChargesDto`, `AssetManagementDetailsRequest`, `package-info`. Three old enums also remain: `AmcChargeFrequency`, `BrokerChargeTransactionType`, `BrokerageAggregatorType`.

**It is deleted in Phase C, not before.** Nothing in `portfolio/` has been modified, and that is a hard rule for Phase A (§5).

---

## 3. Document map

Read in this order:

| # | Document | What it answers | Lines |
|---|---|---|---|
| 1 | **README.md** *(this file)* | Where things stand; how to resume | 171 |
| 2 | **decisions.md** | *Why* the design is the way it is. 31 decisions, each with context, rationale and consequences. **ADR-26 before deploying; ADR-28 before touching the trade path** | 380 |
| 3 | **prd.md** | Requirements, 9 catalogued defects in the old code, 12 acceptance criteria | 187 |
| 4 | **tech-spec.md** | The design. Entities, engine contracts, algorithms, seed format, extensibility analysis (§13), temporal semantics (§14) | 912 |
| 5 | **test-plan.md** | How it is verified. ~190 tests across 11 tiers, with gates | 347 |
| 6 | **implementation-checklist.md** | The build tracker. Resume from the first unticked box | 339 |
| 7 | **staging-runbook.md** | Every endpoint as a runnable curl, with the figure each should return | 460 |
| 8 | **ac2-rate-verification.md** | The AC-2 evidence: every shipped rate against the broker's page, what was wrong, and what closing it changed | 180 |
| 9 | **phase-b-reconciliation-findings.md** | The Phase B exit artifact: the backfill and reconciliation run against 319 real transactions, what it proved, and why the deltas could not mean what the gate assumed | 143 |
| — | `reseed-staging.js` | One-off, for an environment seeded before 2026-09-08. Checks before it deletes | 39 |
| — | **[`../epics/priced-portfolio.md`](../epics/priced-portfolio.md)** | **Not this branch.** The epic that makes a *real* portfolio fully priced — instrument identity and historical rate coverage. Read it to understand what the engine still cannot do, and why that is data rather than code | 138 |

**ADR-26 is the one to read before deploying anything.** It states the rule that keeps rate cards deployable — a deployed card is never edited, only superseded — and what to do in the two cases where that is not enough.
| — | `../testing/test-framework-audit.md` | The framework work that preceded this, and why | 221 |

**If you change the design, update `decisions.md` and `tech-spec.md` together.** A decision recorded in only one of them will be lost.

---

## 4. The problem, in one paragraph

The existing broker-charges implementation models a rate card as a **fixed set of Java fields** — one named column per charge, repeated across `BrokerCharges`, `UserBrokerCharges` and `BrokerChargesReport`. Adding one charge is an eight-file change across two modules. There is no asset-type dimension, so `ProfitAndLossService` gates on `assetType == EQUITY` and mutual funds, bonds and gold bonds accrue nothing. GST is parsed from a CSV string and applied over a merged `govtCharges` bucket that includes STT and stamp duty, which is wrong — roughly ₹17 of overcharge on a ₹1,00,000 sell. The replacement makes a rate card a **list of rules**, evaluated by **strategies chosen per basis**, so that adding or repricing a charge is a data change.

---

## 5. The three phases

| Phase | Scope | Touches `portfolio`? |
|---|---|---|
| **A** *(done)* | Standalone engine: schedules, instruments, rules, calculators, seeds, simulate API | **No.** Hard rule. |
| **B** *(current)* | Shadow: engine computes and persists alongside the live flow, result ignored; reconciliation report | One interface + injection — and that is literally all it was: `ChargeRecordingGateway.java` plus 15 lines in `ProfitAndLossService.java` |
| **C** | Cutover: computed total drives cost basis, manual charge entry retired, old code deleted | Yes |

**Phase A exit criterion, checked literally:**
```bash
git diff master --stat -- backend/src/main/java/com/thiru/wealthlens/portfolio/
```
must be **empty**. If it is not, scope has leaked and the cutover is no longer reversible.

---

## 6. How to resume

```bash
git checkout feature/charges-engine
git log --oneline master..HEAD          # what has been committed on this branch

# 1. Where you are. The checklist is the tracker; resume at its first unticked box.
sed -n '1,25p' docs/charges-engine/implementation-checklist.md

# 2. Confirm the branch is still green before changing anything.
./mvnw verify -pl backend               # full suite + both JaCoCo gates; needs Docker
./mvnw test -pl backend -am -Punit      # fast inner loop, no Docker

# 3. Surefire's exit code is trustworthy, but the checklist's own gate is cheap:
grep -l 'failures="[1-9]"\|errors="[1-9]"' backend/target/surefire-reports/TEST-*.xml
# must print nothing.

# 4. Mutation score for the charges work. Scoped on purpose: the profile's own
#    target list also covers taxplanning, whose score is deferred (§9 item 7), so
#    the unscoped invocation fails on the aggregate and says nothing about this.
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage -Pmutation -pl backend \
  '-DtargetClasses=com.thiru.wealthlens.brokercharges.engine.*,com.thiru.wealthlens.brokercharges.service.*' \
  '-DtargetTests=com.thiru.wealthlens.*'
# Reports 96% (546/566). That figure is not this work's score: `service.*` also matches
# BrokerChargeService and UserBrokerChargeService — the superseded implementation, which carries
# 17 of the 20 survivors and is deleted in Chunk 11. Neither file nor its tests has been touched
# on this branch, so those survivors are pre-existing. For the score that gates this work:
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage -Pmutation -pl backend \
  '-DtargetClasses=com.thiru.wealthlens.brokercharges.engine.*,com.thiru.wealthlens.brokercharges.service.Charge*,com.thiru.wealthlens.brokercharges.service.UserChargeService,com.thiru.wealthlens.brokercharges.service.AmcChargeService' \
  '-DtargetTests=com.thiru.wealthlens.*'
# → 99%, 475/476. The survivor is ChargeFormulaEvaluator's known equivalent mutant.

# 5. Phase A's exit criterion — must be empty, or the cutover stops being reversible.
git diff master --stat -- backend/src/main/java/com/thiru/wealthlens/portfolio/

./mvnw spotless:apply -pl backend        # before every commit; not bound to a phase
```

**Read in this order when context is lost:** this file §1 and §11, then `implementation-checklist.md` for the sequence, then `decisions.md` only if you intend to change the design.

**Working agreement, as instructed by the repository owner:**
- **Test-driven, including integration and module tests.** Write the failing test first, watch it fail, then implement. In Java the first red is usually a compile error, which proves nothing — create minimal skeletons so the test compiles, then show real assertion failures before implementing. Pure data carriers with no branch or calculation are exempt.
- **The repository owner reviews before every commit.** Present the work, wait, then commit. Approval to continue building is not approval to commit.
- **One commit per chunk**, local to this branch; not pushed until asked.
- Build quality-first — no compromises taken for speed.
- **Tick the checklist as you go.** It is the tracker, and this file is only the narrative.

---

## 7. Settled decisions (do not re-litigate)

Full rationale in `decisions.md`. Summary:

| Topic | Settled as |
|---|---|
| Broker seed cards | **Zerodha only** in Phase A |
| Schedules to seed | **Three**: EQUITY/DELIVERY, EQUITY/INTRADAY, MUTUAL_FUND |
| Rate values | **Placeholders**, `verifiedOn: null`, marked `PLACEHOLDER`. Consequence: **AC-2 is blocked** until real rates arrive |
| F&O | Model-only. `amountBasis`, `lotSize`, `orderId` carried; no seed cards |
| Manual charge entry | The engine replaces it — that is the point of the work |
| Module rename `brokercharges` → `charges` | Deferred to Phase C |
| `planCode` dimension | Exists on the schedule; not populated in Phase A |
| AMC accounts | New `ChargeAccountEntity`; `AssetManagementDetails` untouched until Phase C |
| Upload model | **Quarterly batches, chronological** — with a guard, because the guarantee is operational not enforced |

---

## 8. Facts that are easy to get wrong

Each of these was established by investigation or corrected after being got wrong once. Re-deriving them incorrectly will produce broken work.

1. **`FormulaEvaluator` in `taxplanning` must not be reused.** The charges module owns `ChargeFormulaEvaluator` outright. The two solve a similar problem; neither depends on the other. Returns `double` (money, two decimals), not `long` (whole rupees).

2. **Superseding a rate card must never set `status`.** It closes `endDate` only. The resolver filters `status != INACTIVE`, *not* `status == ACTIVE`. Otherwise a 2024 transaction uploaded in 2026 finds no card and silently computes zero. See `decisions.md` ADR-12.

3. **Exit load is per FIFO lot**, not per transaction. Averaging can be wrong by the entire charge, not a rounding error.

4. **GST base must be declared explicitly.** It never includes STT or stamp duty. This is the D1 defect and has a dedicated golden fixture.

5. **Engine arithmetic is `BigDecimal` internally**, `double` only at the persistence boundary. Otherwise golden tests go flaky and tolerances creep.

6. **Surefire's exit code is trustworthy now** (PR #59), but CI passes `-Dmaven.test.failure.ignore=true` on its own invocation. Locally, a failing test fails the build.

7. **`AbstractIntegrationTest` is `@Isolated` and must stay so.** RestAssured's `baseURI`/`port` are static globals and all integration classes share one Mongo container.

8. **New collections must be nothing.** `cleanDatabase()` now enumerates collections dynamically against a seeded whitelist, so new charge collections need no change to it — but if a new collection must *survive* between tests, it has to be added to `SEEDED_REFERENCE_COLLECTIONS`.

9. **`portfolio` already produces FIFO lots.** `PortfolioService.updateQuantityBySavingReportAndProfitAndLoss1` builds `List<BuyContext>` (quantity, date, price). Phase C consumes it; Phase A supplies lots through the simulate endpoint instead.

10. **Both resolvers cache, and both must be evicted when a card is written.** `ChargeScheduleResolver.evictAll()` and `ChargeInstrumentResolver.evictAll()` exist and nothing calls them yet — `ChargeScheduleService` does, in Chunk 6. Until then a published card is invisible to an already-warm resolver. Misses are cached too, deliberately: backfilling a period with no card must not re-query per trade.

11. **Logging is Logback, not Log4j2.** `@Log4j2` is Lombok's API annotation; the implementation behind it is Logback via `log4j-to-slf4j`, and `log4j-core` is not on the classpath. `testsupport/LogCapture` binds to Logback for that reason. `CLAUDE.md` says "Log4j2", which is true of the annotation and misleading about the backend.

12. **Scheme profiles are seeded from `data/charges/instruments/`, a subdirectory.** The schedule pattern is `data/charges/*.json` and does not descend, which is the only thing stopping a profile from being parsed as a rate card with every field null — a card that would then resolve for everything and price it at zero. A new profile goes in that directory or it is a rate card.

13. **The maintenance card is the only unscoped card, and that is load-bearing twice over.** It must leave every trade dimension unset or the AMC cycle — which carries no scrip, quantity or asset type — cannot resolve it at all. And because an unscoped card agrees with every trade, it is Zerodha's fallback wherever no specific card exists: such a trade now resolves to it and reads `NO_MATCHING_RULES` rather than `NO_SCHEDULE`. Both are gaps and both are reported; they are different sentences in the gaps report. Its rules declare `AMC_CYCLE` alone so that the fallback prices nothing rather than billing maintenance on a trade.

14. **`AssetEntity` has no ISIN or scheme code** — only `stockCode` and `stockName`. `ChargeInstrumentEntity` is keyed on `stockCode` for that reason, with `isin` stored for later.

---

## 9. Open items

Everything design-level is settled. **One** acceptance criterion remains open and it is not code work; the rest below are closed or recorded.

| # | Item | State |
|---|---|---|
| 1 | ~~**AC-2 cannot be closed in Phase A**~~ | **Closed 2026-09-08.** Verified against all three brokers' published pages on this branch rather than post-merge. Five rate defects found and fixed, five successor cards added for two documented rate changes, all eleven cards carry `verifiedOn`. `ac2-rate-verification.md` holds the evidence. One operational caveat survives — see §10 |
| 2 | ~~**`ChargeAccountEntity` shape**~~ | **Closed.** Built in Chunk 2 and exercised by `ChargeAccountService` in Chunk 5; billing history survives re-registration |
| 3 | ~~**`charge_catalogue` initial code list**~~ | **Closed.** Chunk 6 seeded 12 codes; `GET /charge-catalogue` lists them and the validator rejects any rule naming one that is absent |
| 4 | ~~Missing instrument profile: error or warning?~~ | **Settled — ADR-24.** Recorded, never fatal. Gated by `requiresInstrumentProfile`; validator checks expression variables against an allow-list |
| 5 | ~~Does `AccountType` affect charges?~~ | **Settled — ADR-25.** No rate impact, but `accountHolder` joins every dedupe key. Uncovered defect D10: DP charges are undercounted across account holders |
| 7 | **`taxplanning` mutation score is 34.4%** — 133 of 387 mutants killed, with `FormulaEvaluator`, `FbpOptimizer`, `ItrFormAdvisor` and `TaxEngineFactory` at zero. Pre-existing, and invisible until pitest was bumped to a version that runs on Java 25 | **Deferred by the repository owner** to the full layer, 2026-09-06. Not a defect to re-raise. Consequence: `-Pmutation` fails on the aggregate, so the charges engine is gated by running the profile scoped to its own package (see §6) |
| 8 | ~~**AC-6 cannot be closed yet**~~ | **Closed 2026-09-08.** Two scheme profiles seeded — one graded, one predicate-based — and three golden fixtures plus two integration cases exercise the predicate end to end. The zero-charge fixture asserts `NO_MATCHING_RULES`, so a profile that fails to load fails it rather than passing as a free redemption |
| 9 | **Instrument identity has no single source of truth** — real mutual fund holdings key on the full scheme name, shipped charge profiles on short codes, and `ChargeInstrumentEntity` is keyed on `stockCode`, so they can only meet by coincidence. Found by the Phase B backfill: 43 of 319 real transactions | **Decided 2026-09-09 — ADR-29.** Delivery is the [priced-portfolio epic](../epics/priced-portfolio.md), not this branch. One registry of every recognised instrument; upload rejects what it does not carry; charge profiles key off the canonical code. Makes the mismatch impossible by construction rather than by convention |
| 10 | **No rate cards before 2025-04-01**, so 71% of a real history resolves `NO_SCHEDULE` | Rate archaeology, not code. Ships as new generations under ADR-26, never as edits. Delivery is the [priced-portfolio epic](../epics/priced-portfolio.md) |
| 6 | **`exchangeName` is `"NSE"` everywhere in tests and the API collection** — plain uppercase codes, so the schedule's `exchange` dimension matches directly. BSE is untested | Low risk, noted |

### Verified non-issues

- **Corporate actions** — closed as ADR-23. Exempt by default, with per-rule opt-in for buybacks and rights.

---

## 10. Known limits, stated deliberately

Not oversights — decisions with reasons, recorded so nobody rediscovers them as bugs.

| Limit | Why it is out of scope |
|---|---|
| **MTF interest** accrues daily on an open position, not at a trade event | Needs a cycle runner like AMC, a second execution mode |
| **Aggregate caps** ("max ₹X brokerage per day across all trades") | `DedupeScope` charges *once*; it cannot cap a *sum* |
| **Volume-tiered pricing** on cumulative monthly turnover | Needs historical aggregation before the current trade can be priced |
| **A fresh database holds no charge data until `POST /charges/seed` is called** | Deliberate (ADR-27). Until then every trade resolves to `NO_SCHEDULE` and `GET /charge-schedules/drift` reports every shipped card as absent |
| **Any entity Jackson deserialises loses its audit trail** | Lombok's `@ConstructorProperties` on `@AllArgsConstructor` makes Jackson bypass the field initialiser, leaving `auditMetadata` null, and Spring Data's auditing fills an object rather than creating one. Fixed in the charges seeder; anywhere else this application parses an entity from JSON has the same silent hole |
| **Seeding does not update a card already on file** | `ChargeSeederService` is idempotent by `scheduleCode`, so an edited seed file never reaches a database that already seeded it. Deliberate — an operator's correction must survive a restart — and now **reported** rather than silent: the seeder warns and `GET /charge-schedules/drift` lists the differences. The rule that makes this a non-problem is ADR-26: a rate change ships as a new generation, never an edit |
| **Amending a card that has priced charges has no safe mechanism yet** | `POST /charges/recompute` is designed (tech-spec §14.4) and not built. Harmless through Phase A, where nothing prices anything; a real constraint once Phase B records |
| **Zerodha's depository fee revision has no published date** | 2026-06-19 comes from secondary reporting. Wrong by a few weeks misprices only the window between the real date and that one, and correcting it is a new card |
| **Performance under load** | Resolver cache is asserted for correctness, not latency |
| **Shadow recording is off in every shipped profile** | Deliberate (ADR-28's companion, `app.charges.shadow-recording: false`). Until it is turned on, the trade path calls the gateway and the gateway returns immediately, so the reconciliation report is empty and `transactionsWithoutComputation` equals the user's whole transaction count |
| **A shadow computation that fails is swallowed** | On purpose. `ChargeRecordingGatewayImpl` catches every `RuntimeException`, logs it with the transaction id, and returns empty. A trade must not fail to save because its shadow copy could not be priced — the missing row is visible in the reconciliation report as a transaction with no computation |
| **A non-equity trade with no card for its asset type records a gap, not a charge** | The shipped data covers equity delivery, equity intraday, mutual funds and maintenance. Anything else resolves to `NO_SCHEDULE` or `NO_MATCHING_RULES`, is recorded with that reason, and is excluded from the reconciliation totals rather than counted as an undercharge |
| **A corporate-action sell is not shadow-recorded** | The live flow does not process one either — `updateProfitAndLoss` reaches `handleNormalSellCase` only when `actionType == null`. Recording a charge for an event the P&L ignores would put a row in the reconciliation report with nothing to reconcile it against (ADR-28) |
| **Shadow recording is not instrumented for segment** | `ProfitLossContext` carries no `TradeSegment`, so every shadow row is priced as `DELIVERY`. The field arrives on the portfolio types in Phase C; until then an intraday trade reconciles against a delivery card and the delta will be real but explainable |
| **A rate card written outside `ChargeScheduleService` is invisible** | The resolver caches by scope and date and only `publish` and `close` evict. Writing straight to `ChargeScheduleRepository` leaves the previously resolved card in memory. Found by a Chunk 9 test that did exactly that |
| **A scheme profile written outside the seeder is invisible too** | Same eviction rule, and `ChargeInstrumentResolver` has no publishing service in front of it at all. Only `ChargeSeederService` calls its `evictAll()`. A profile added at runtime needs one before the next redemption of that scheme |
| **Depository deduplication is not visible from simulate** | It checks *recorded* charges, and in Phase A nothing in the trade path records any, so a scoped charge always prices as a first occurrence unless the account already carries a row from the AMC cycle |
| **Only two schemes have profiles** | Every other mutual fund resolves to `NO_INSTRUMENT_PROFILE` and accrues no exit load. That is the designed behaviour — recorded, never fatal (ADR-24) — but it means the shipped data prices two funds and gaps the rest. **Worse than it looks, and now scheduled:** real holdings key on the full scheme name and the profiles key on short codes, so they could never have matched. **ADR-29**, delivered by the [priced-portfolio epic](../epics/priced-portfolio.md) |
| **71% of a real history cannot be priced** | Every card starts 2025-04-01; the `it-staging` history starts 2023-06-22, so 227 of 319 trades resolve `NO_SCHEDULE`. Correct behaviour, and visible in the gaps report — but a backfilled portfolio is mostly unpriced until 2023 and 2024 card generations exist. [Priced-portfolio epic](../epics/priced-portfolio.md) |
| **Scheme profiles have no publishing endpoint** | They are seeded at startup, and `ChargeInstrumentResolver.evictAll()` is called by nothing else. A profile added at runtime needs a restart — rate cards have `ChargeScheduleService` in front of them, profiles have nothing |

---

## 11. Resume point — paused mid-Chunk-10b, 2026-09-09

**Paused deliberately, working tree clean, everything committed.** Build green: **873 tests** across
both tiers, `spotless:check` clean, both JaCoCo gates passing. The last mutation run measured
577/578 (99%) before the segment work; the single survivor is `ChargeFormulaEvaluator`'s known
equivalent mutant.

### 11.1 What to do first when you pick this up

```bash
git checkout feature/charges-engine
git log --oneline origin/feature/charges-engine..HEAD   # 11 commits not yet pushed
./mvnw clean verify -pl backend                          # needs Docker; 873 tests
sed -n '1,20p' docs/charges-engine/implementation-checklist.md
```

Then read §11.2 below — there are two open decisions, and one of them shapes the next commit.

### 11.2 The one thing to settle before Chunk 11

**Chunk 11 deletes the superseded implementation, and it was planned when V1 was believed to be
dead code. V1 is live** (clarified 2026-09-09), and the live V1 path depends on things on that
deletion list:

- V1 `buyStock` → `updateBrokerChargesAndProfitAndLoss` → `UserBrokerChargeService`
- V1 `sellStock:548` is the **last caller** of the `@Deprecated(forRemoval = true)`
  `ProfitAndLossContext` overload

Deleting `UserBrokerChargeService`, `BrokerChargeService`, their repositories or that overload would
break a flow serving users. **Either V1 moves onto the engine first, or those files stay** and
Chunk 11 shrinks to whatever is genuinely unreferenced. That is a decision, not a detail.

**A known limit carried forward from 10b**, recorded rather than fixed: `YearlyChargeSummary`
*accumulates*, so reprocessing one trade twice counts it twice. The hierarchy beside it has always
behaved that way. Deriving the summary from `user_charges` would be idempotent by construction, and
Chunk 11 is the moment to revisit it — that is when the old hierarchy goes and
`ChargeSummaryReport.merge` stops having a peer to match.

### 11.3 Environment state left behind

- **`app.charges.shadow-recording` and `authoritative` both ship `false`**, and `engine-enabled`
  ships `true`. Nothing in the trade path behaves differently from `master` until a flag is flipped.
- **`it-staging` holds 319 backfilled `user_charges` rows** written on 2026-09-09. Nothing else was
  touched — no cost basis, no P&L, no transaction documents. Dropping that collection undoes it.
- The repository owner was running the branch's JAR locally on **:8080 against `it-staging` with
  `shadow-recording=true`**. If it is still up, new trades are still being shadow-recorded. Stopping
  it, or clearing the flag, is the whole rollback.

### 11.4 What Phase C has done so far

**Chunk 10a — AC-10, the last acceptance criterion.** With `app.charges.authoritative=true` a V2 buy
stores the engine's total as its cost basis. Ships `false`. **V2 only**, at the owner's direction:
`buyStockV2` no longer shares `updateBrokerChargesAndProfitAndLoss` with V1 `buyStock`, and the
`PortfolioService` diff removes exactly one line, inside V2. The charge is computed **before** the lot
is written and the ordering is asserted with `InOrder`, because a value assertion passes just as
happily if the code reorders and applies the total by overwrite. `ProfitAndLossService` gained a
3-arg overload that uses a computation it is handed, so the engine runs once per trade; the 2-arg
version is untouched, which is what keeps V1 identical. An absent computation **leaves the entered
figure alone** rather than zeroing it.

**Chunk 10b part 1 — a trade carries its own segment.** `TradeSegment` moved to
`portfolio.dto.enums` (no data migration: both persisted uses store the enum's *name*).
`AssetRequest`, `TransactionEntity` and `AssetEntity` gained the field defaulting `DELIVERY`;
`ProfitLossContext` gained a 14th component with a 13-arg convenience constructor. The gateway and
the backfill now read the trade's segment instead of assuming delivery.
`AssetRequest.brokerCharges` is **deprecated, not removed** — clients keep working, it just stops
being read.

### 11.5 What remains

| | |
|---|---|
| **Chunk 11** | Delete the superseded implementation. **Re-scope first — V1 is live and depends on part of that list.** The `assetType == EQUITY` gate belongs here too, and is only safe to remove once the path behind it is gone (ADR-28) |
| **Chunk 12** | Final verification |
| **[Priced-portfolio epic](../epics/priced-portfolio.md)** | Instrument identity (ADR-29) and historical rate coverage. Surfaced by the real-data run, **not Phase C work**, and shipped as one comprehensive release after this branch merges — not as follow-on tickets |

### Phase B is closed (ADR-31)

**Run against real data on 2026-09-09** — `it-staging`, 319 transactions spanning 2023-06-22 to
2026-01-12. Full write-up in `phase-b-reconciliation-findings.md`.

**The engine is correct.** A resolution breakdown predicted from the data *before* running matched
exactly — 227 trades predate every shipped card and resolved `NO_SCHEDULE`, 92 fell inside one. A
contract note was checked line by line and agrees to the paisa, including STT's whole-rupee statutory
rounding and a GST base excluding STT (the D1 defect, seen fixed on a real trade). AC-4 deduplication
was proven on three same-day sells of one scrip: DP charged once, ₹12.50 then ₹0.00, ₹0.00. FIFO lots
were reconstructed for all 43 sells with none missing.

**The comparison PRD OD-8 asked for returned no usable signal, and the gate is amended to say so.**
Entered broker charges total ₹40.72 across the whole history and **₹5.32 across the 49 comparable
trades — 37 of them exactly ₹0.01**, against computed figures of ₹16 to ₹29. That is not a population
of user estimates; it is a field with a placeholder in it. The ₹235.30 delta is the computed total
measured against a blank, and **should not be cited as an engine finding**.

Running Phase B was still right: it is what produced this finding, and the backfill, the FIFO
reconstruction and ADR-29's instrument-master gap would otherwise all be undiscovered.

**Two things Chunk 10 inherits:**

1. ~~Cost basis moves for every trade once `authoritative` flips.~~ **Retired by ADR-32
   (2026-09-09): existing transactions keep their charges and are never re-driven.** The flag applies
   forward only, so a period spanning the cutover simply carries some trades charged the old way and
   some the new — worth a release note, not a migration.
2. **There is no numerical baseline to diff the cutover against.** Correctness rests on the golden
   fixtures, the invariants, and ADR-31's verification.

### What Chunk 8 decided, and why it is not what the checklist said

**ADR-28.** The checklist and tech-spec §9.2 both said to remove the `assetType == EQUITY` gate in
`ProfitAndLossService`. Doing that literally would have been a live behaviour change and the wrong
one: `UserBrokerChargeService` resolves a rate card by **broker and date only**, with no asset-type
dimension anywhere in it, so a mutual fund passed through it would be charged equity brokerage, STT
and stamp duty — and those figures would reach the P&L. Chunk 8's own gate forbids exactly that. The
shadow call went **outside** the gate instead: every asset type reaches the engine (FR-8), nothing
else changes, and the gate is removed in Chunk 10 where the branch behind it is deleted anyway.

**V2 only.** V1 `addTransaction` — `buyStock` and `sellStock` — is **in live use**, per the
repository owner, and must never be touched; V2 is built out beside it. That maps exactly onto the two `updateProfitAndLoss` overloads,
which are distinct methods rather than one path: the `ProfitLossContext` overload (V2) is
instrumented; the `@Deprecated(forRemoval = true)` `ProfitAndLossContext` overload, reached only from
V1 `sellStock`, is untouched.

### What running it actually found

Two documentation defects, both of which had already misled a reader — me:

1. **`CLAUDE.md` gave the wrong path for the live trade flow.** It said `addTransaction` is
   `POST /transactions/user/{email}/transaction`. It is `POST /portfolio/user/{email}/transaction`,
   served by `PortfolioController`; `/transactions/user/{email}` is `TransactionController`, which
   only *reads* transactions. Corrected, with the distinction spelled out.
2. **The runbook repeated the same wrong prefix** in §5b.2, so following it returned a 404. Corrected,
   and it now carries a worked `curl` rather than only a path.

Neither would have been caught by the suite — the tests call `ProfitAndLossService` directly, and a
wrong URL in prose compiles fine.

### What Chunk 8 found

- **The checklist contained two instructions that could not both be satisfied** — remove the EQUITY
  gate, and change no P&L numbers. Reading what the gate actually guards is what settled it. ADR-28.
- **Mutation testing found four guards on states that cannot occur** in `ChargeReconciliationService`
  — a null-id filter and a merge function on documents read from one collection, and a debug log
  restating a field already in the response. All four were removed rather than tested around; code
  that cannot be reached is code that cannot be right.
- **One test was symmetric enough to survive negation.** Counting transactions the engine never saw,
  with one reconciled row and one unseen row, gives the same answer whichever side of the filter you
  count. It now uses three transactions and two unseen.

### Then: Chunk 10 — Phase C, cutover

**Discuss the Phase B deltas before starting.** Phase C is where the computed total drives cost
basis and the old implementation is deleted; it is the first step that is not reversible by turning
a flag off.

### Defects found in my own earlier work, while building this

1. **The engine applied both sources' version of a charge code.** Tech-spec §4.6.2 says the instrument wins and the card's rule is skipped; it did not, so a scheme with its own exit load on a card that also carried one was charged twice, silently. Found by re-reading the spec during Chunk 5, fixed with a failing test first.
2. **The old services were assumed to fail a widened quality gate.** They do not — 100% and 97.9% line coverage — so no exemption was written into the build.

### What TDD has caught so far, worth continuing for

1. `#charges['CODE']` threw `EL1027E` — SpEL indexes `Map`, and the lookup was backed by a record.
2. `20.00 * 0.18` returned `3.5999999999999996`. The failing assertion was the *test's* error, not the code's — as was a later Mockito assertion that could not tell two `@Data` entities apart.
3. `ChargeCalculatorRegistry` annotated `@Component` broke application startup with all 213 unit tests green. Test-first at the *integration* tier turned that into a two-minute red-green.
4. The null `amountBasis` default — a contract missing from the rule itself, which all seven calculators would have copied.
5. Mutation testing found a dead negative-zero guard, five unkillable mutants that were one untested warning, three scheme attributes no test read, and several fields written but never asserted. `testsupport/LogCapture` exists because a branch that only logs is otherwise indistinguishable from one that was deleted.
