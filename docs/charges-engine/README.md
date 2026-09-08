# Charges Engine — Start Here

**Purpose of this file:** the single entry point. If you are resuming this work — new session, new person, lost context — read this first and trust nothing about the codebase that is not stated here or verified from the code.

**Last verified against the repository:** 2026-09-08, branch `feature/charges-engine`, **Phase A complete** (Chunks 1–7 and 9), with the two seed-data items Chunk 6 left open now closed. Full suite green: 779 tests, unit and integration.

---

## 1. Status at a glance

| | |
|---|---|
| **Branch** | `feature/charges-engine`, rebased onto `master` after PR #59 (test framework) and PR #60 (D10 fix) |
| **Commits beyond master** | 33 — twenty-two code (15 `feat`, 3 `test`, 2 `fix`, 1 `ci`, 1 `chore`) and eleven documentation. Counted with `git rev-list master..HEAD --count`; the figure here previously read 25 against an actual 31, so trust the command over this cell |
| **Phase** | A (standalone engine) — **complete**. Chunks 1–7 and 9 done |
| **Next action** | Review the Phase A results, then Chunk 8 (Phase B, shadow recording). See §11 |
| **Blocking questions** | None. **AC-2** is the only acceptance criterion still open and it is **not a merge blocker** — the owner has scheduled the rate verification for staging, after this branch merges (ADR-18) |

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

`ChargeSimulationRequest` gained `lots`, so `POST /charges/simulate` can price a redemption per FIFO lot — without it a `perLot` rule evaluated zero times and exit load answered ₹0 through the API however the profile was written. A lot set that does not describe its disposal is rejected rather than priced: every way of getting it wrong makes the charge smaller rather than making the call fail.

`ChargeSeederService` gained `seedInstruments()` (last, after the catalogue and the cards; idempotent by scheme **and start date**, a profile having no code of its own) and now evicts `ChargeInstrumentResolver` alongside the schedule resolver's cache. `ChargeScheduleValidator` gained `validate(ChargeInstrumentEntity)`: the same window and rule checks, because validating only the broker's card left exit load — the one charge a rate card cannot express — entirely unchecked.

Tests: `ChargeSeederServiceTest` 14 → 24, `ChargeScheduleValidatorTest` 31 → 35, `ChargeSimulationServiceTest` 14 → 27, four new golden fixtures, four new cases in `ChargesIntegrationTest`.

### Written by Chunk 7

`entity/model/ChargeSummaryReport` and its `YearlyChargeSummary` / `MonthlyChargeSummary` forms — a map keyed by charge code replacing six fixed columns, summed in `BigDecimal` with the total recomputed from the parts rather than accumulated beside them. `ChargeSummaryReportTest`, 11 cases. Nothing writes one yet: `ProfitAndLossService` keeps the old `BrokerChargesReport` until Phase C.

### Written by Chunk 9

Four controllers — `ChargeScheduleController` (publish, fetch, list, close, unverified, catalogue), `ChargeSimulationController` (`POST /charges/simulate`), `UserChargesController` (history, contract note, gaps) and `ChargeAccountController` (register, list, AMC cycle). Two services behind them: `ChargeSimulationService` (holds the engine and no repository, so a dry run cannot write) and `ChargeCatalogueService`. `UserChargeService.findHistory` gained a date range and asset-type filter. `ChargesIntegrationTest` (24 cases) and `ChargeExtensibilityTest` (3, Tier J). An eleven-request `Charges Engine` folder in `api-collection/`.

`AuthConfig` gained the five new prefixes. It needed them: the chain ends in `anyRequest().permitAll()`, so every charges endpoint was public. Publishing a rate card and running the AMC cycle additionally require `SUPER_USER`.

### NOT written yet — do not assume any of it exists

**Nothing in the live path calls any of this.** The simulate endpoint and the user-charges endpoints are the first callers, and they stand beside the existing flow rather than in it. Phase B is what puts the engine in the trade path; Phase C is what makes it authoritative.

### The old implementation is fully intact and untouched

13 files under `brokercharges/` still implement the superseded design: `BrokerCharges`, `UserBrokerCharges`, `BrokerChargeService`, `UserBrokerChargeService`, both repositories, both controllers, `BrokerChargeContext`, `BrokerChargesRequest`, `BrokerageChargesDto`, `AssetManagementDetailsRequest`, `package-info`. Three old enums also remain: `AmcChargeFrequency`, `BrokerChargeTransactionType`, `BrokerageAggregatorType`.

**It is deleted in Phase C, not before.** Nothing in `portfolio/` has been modified, and that is a hard rule for Phase A (§5).

---

## 3. Document map

Read in this order:

| # | Document | What it answers | Lines |
|---|---|---|---|
| 1 | **README.md** *(this file)* | Where things stand; how to resume | 171 |
| 2 | **decisions.md** | *Why* the design is the way it is. 22 decisions, each with context, rationale and consequences | 260 |
| 3 | **prd.md** | Requirements, 9 catalogued defects in the old code, 12 acceptance criteria | 187 |
| 4 | **tech-spec.md** | The design. Entities, engine contracts, algorithms, seed format, extensibility analysis (§13), temporal semantics (§14) | 912 |
| 5 | **test-plan.md** | How it is verified. ~190 tests across 11 tiers, with gates | 347 |
| 6 | **implementation-checklist.md** | The build tracker. Resume from the first unticked box | 339 |
| 7 | **staging-runbook.md** | Every endpoint as a runnable curl, with the figure each should return. Written for the AC-2 verification | 425 |
| — | `../testing/test-framework-audit.md` | The framework work that preceded this, and why | 221 |

**If you change the design, update `decisions.md` and `tech-spec.md` together.** A decision recorded in only one of them will be lost.

---

## 4. The problem, in one paragraph

The existing broker-charges implementation models a rate card as a **fixed set of Java fields** — one named column per charge, repeated across `BrokerCharges`, `UserBrokerCharges` and `BrokerChargesReport`. Adding one charge is an eight-file change across two modules. There is no asset-type dimension, so `ProfitAndLossService` gates on `assetType == EQUITY` and mutual funds, bonds and gold bonds accrue nothing. GST is parsed from a CSV string and applied over a merged `govtCharges` bucket that includes STT and stamp duty, which is wrong — roughly ₹17 of overcharge on a ₹1,00,000 sell. The replacement makes a rate card a **list of rules**, evaluated by **strategies chosen per basis**, so that adding or repricing a charge is a data change.

---

## 5. The three phases

| Phase | Scope | Touches `portfolio`? |
|---|---|---|
| **A** *(current)* | Standalone engine: schedules, instruments, rules, calculators, seeds, simulate API | **No.** Hard rule. |
| **B** | Shadow: engine computes and persists alongside the live flow, result ignored; reconciliation report | One interface + injection |
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
| 1 | **AC-2 cannot be closed in Phase A** — golden fixtures assert against placeholder rates, so they pin the arithmetic but not reality | **Scheduled, not blocking.** Owner's decision 2026-09-08: verify against Zerodha's live page in **staging, after the merge to `master`** — not on this branch. Worklist `GET /charge-schedules/unverified`; fill `verifiedOn`, then re-run the golden fixtures to see which trades moved (ADR-18) |
| 2 | ~~**`ChargeAccountEntity` shape**~~ | **Closed.** Built in Chunk 2 and exercised by `ChargeAccountService` in Chunk 5; billing history survives re-registration |
| 3 | ~~**`charge_catalogue` initial code list**~~ | **Closed.** Chunk 6 seeded 12 codes; `GET /charge-catalogue` lists them and the validator rejects any rule naming one that is absent |
| 4 | ~~Missing instrument profile: error or warning?~~ | **Settled — ADR-24.** Recorded, never fatal. Gated by `requiresInstrumentProfile`; validator checks expression variables against an allow-list |
| 5 | ~~Does `AccountType` affect charges?~~ | **Settled — ADR-25.** No rate impact, but `accountHolder` joins every dedupe key. Uncovered defect D10: DP charges are undercounted across account holders |
| 7 | **`taxplanning` mutation score is 34.4%** — 133 of 387 mutants killed, with `FormulaEvaluator`, `FbpOptimizer`, `ItrFormAdvisor` and `TaxEngineFactory` at zero. Pre-existing, and invisible until pitest was bumped to a version that runs on Java 25 | **Deferred by the repository owner** to the full layer, 2026-09-06. Not a defect to re-raise. Consequence: `-Pmutation` fails on the aggregate, so the charges engine is gated by running the profile scoped to its own package (see §6) |
| 8 | ~~**AC-6 cannot be closed yet**~~ | **Closed 2026-09-08.** Two scheme profiles seeded — one graded, one predicate-based — and three golden fixtures plus two integration cases exercise the predicate end to end. The zero-charge fixture asserts `NO_MATCHING_RULES`, so a profile that fails to load fails it rather than passing as a free redemption |
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
| **Seeded rates are placeholders** | Only a human comparing against the broker's live charges page can close AC-2 |
| **Performance under load** | Resolver cache is asserted for correctness, not latency |
| **A rate card written outside `ChargeScheduleService` is invisible** | The resolver caches by scope and date and only `publish` and `close` evict. Writing straight to `ChargeScheduleRepository` leaves the previously resolved card in memory. Found by a Chunk 9 test that did exactly that |
| **A scheme profile written outside the seeder is invisible too** | Same eviction rule, and `ChargeInstrumentResolver` has no publishing service in front of it at all. Only `ChargeSeederService` calls its `evictAll()`. A profile added at runtime needs one before the next redemption of that scheme |
| **Depository deduplication is not visible from simulate** | It checks *recorded* charges, and in Phase A nothing in the trade path records any, so a scoped charge always prices as a first occurrence unless the account already carries a row from the AMC cycle |
| **Only two schemes have profiles** | Every other mutual fund resolves to `NO_INSTRUMENT_PROFILE` and accrues no exit load. That is the designed behaviour — recorded, never fatal (ADR-24) — but it means the shipped data prices two funds and gaps the rest |
| **Scheme profiles have no publishing endpoint** | They are seeded at startup, and `ChargeInstrumentResolver.evictAll()` is called by nothing else. A profile added at runtime needs a restart — rate cards have `ChargeScheduleService` in front of them, profiles have nothing |

---

## 11. Resume point — Phase A complete

**Paused:** 2026-09-08. Build green: **779 tests** across both tiers, `spotless:check` clean, both JaCoCo gates passing, and **99% mutation score** (475/476) across the engine and the new services, the single survivor being `ChargeFormulaEvaluator`'s known equivalent mutant.

### The Phase A gate, item by item

| Item | Status |
|---|---|
| Line ≥ 90%, branch ≥ 85% | ✅ both JaCoCo rules pass under `mvn verify` |
| Mutation ≥ 85% on `brokercharges.engine` | ✅ 99%. Run it scoped — the aggregate `-Pmutation` still fails on the deferred `taxplanning` score (§9), and see the §6 caveat about what `brokercharges.service.*` sweeps in |
| 16 golden contract notes at ₹0.01 | ✅ asserted line by line and in total |
| `git diff master --stat -- .../portfolio/` empty | ✅ re-checked at the end of Chunk 9 |
| `WealthLensModulithTest` green | ✅ |
| AC-1, 3, 4, 5, 7, 8, 9, 12 | ✅ each with named evidence in the checklist |
| **AC-2** — rates match the broker's published page | ❌ **open by decision, and deliberately not a merge blocker.** Done in staging after merge; `GET /charge-schedules/unverified` is the worklist |
| **AC-6** — MF exit load under the holding-period predicate | ✅ closed 2026-09-08. Two scheme profiles seeded; three golden fixtures and two integration cases |

### What Chunk 9 found

Two defects, both of the kind only an integration test can see:

1. **Every charges endpoint was public.** `AuthConfig` ends in `anyRequest().permitAll()`, so a prefix nobody lists is open — including one that reprices every user's trades and one that bills real money. Fixed, and pinned by a test that asserts an unauthenticated request is refused.
2. **Publishing a rate card and imposing AMC charges were open to any authenticated user.** Both now require `SUPER_USER`.

And two test expectations that were wrong rather than the code, both worth knowing before touching this again:

- **Superseding a card does not set `SUPERSEDED`.** It closes the window and leaves the status alone, because `findCandidates` excludes only `INACTIVE` and a superseded card must still price the trades inside its own window.
- **The resolver cache is evicted only by the publish path.** A rate card written straight to the repository is invisible to the engine until something evicts. That is an operational constraint, not merely a test detail.

Mutation testing found a third, smaller one: a zero price was documented as valid in a comment and asserted nowhere, so the `>= 0` boundary mutated cleanly.

### Then: Chunk 8 — Phase B, shadow recording

**Discuss the Phase A results before starting.** Phase B is the first change to `portfolio/`, which ends the isolation property that has made every step so far reversible.

### Defects found in my own earlier work, while building this

1. **The engine applied both sources' version of a charge code.** Tech-spec §4.6.2 says the instrument wins and the card's rule is skipped; it did not, so a scheme with its own exit load on a card that also carried one was charged twice, silently. Found by re-reading the spec during Chunk 5, fixed with a failing test first.
2. **The old services were assumed to fail a widened quality gate.** They do not — 100% and 97.9% line coverage — so no exemption was written into the build.

### Then: Chunk 6 — seed data

`charge-catalogue.json` first, since the validator rejects any code absent from it. Then the Zerodha cards, `ChargeSeederService` (`@PostConstruct`, idempotent by `scheduleCode`, validating before persisting, failing fast), and test-plan Tier G — the checklist calls it the highest-value test in the plan, because it catches a rate-card typo at build time.

**⚠️ Rates are placeholders and only a human can close that.** AC-2 stays blocked until someone compares each figure against the broker's published charges page and fills in `sourceUrl` and `verifiedOn`.

Two things the seed author needs to know, learned while building the services:

- A **maintenance card must leave `assetType` unset**. The AMC cycle context carries no scrip, quantity or asset type, so a card declaring that dimension is disqualified by the resolver and the cycle bills nothing. Not silently, to be clear — the resolver warns, the engine warns, `AmcChargeService` warns, and the billing watermark is deliberately left where it was so the period can be billed once the card is fixed.

- **Exactly one unscoped card per broker.** An unscoped maintenance card is a candidate for every trade of that broker: it declares no dimension, so it agrees with all of them. It loses on specificity wherever a real card exists, which is the intended behaviour — but two unscoped cards sharing a start date are indistinguishable and the resolver refuses both by name.

  Where no card exists for an asset type, the unscoped card wins by default and then matches none of its rules, giving `NO_MATCHING_RULES` and a zero. That is why `NO_MATCHING_RULES` is in the gaps report: a card that resolves and prices nothing is nearly always a seeding mistake, and it must not read as a free trade.
- **A new expression variable has to be added in two places** — published by the engine, and listed in `ChargeScheduleValidator`'s vocabulary. That is the price of catching `#equityOrientd`, per ADR-24.

### What TDD has caught so far, worth continuing for

1. `#charges['CODE']` threw `EL1027E` — SpEL indexes `Map`, and the lookup was backed by a record.
2. `20.00 * 0.18` returned `3.5999999999999996`. The failing assertion was the *test's* error, not the code's — as was a later Mockito assertion that could not tell two `@Data` entities apart.
3. `ChargeCalculatorRegistry` annotated `@Component` broke application startup with all 213 unit tests green. Test-first at the *integration* tier turned that into a two-minute red-green.
4. The null `amountBasis` default — a contract missing from the rule itself, which all seven calculators would have copied.
5. Mutation testing found a dead negative-zero guard, five unkillable mutants that were one untested warning, three scheme attributes no test read, and several fields written but never asserted. `testsupport/LogCapture` exists because a branch that only logs is otherwise indistinguishable from one that was deleted.
