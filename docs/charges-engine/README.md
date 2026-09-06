# Charges Engine — Start Here

**Purpose of this file:** the single entry point. If you are resuming this work — new session, new person, lost context — read this first and trust nothing about the codebase that is not stated here or verified from the code.

**Last verified against the repository:** 2026-09-06, branch `feature/charges-engine`, Chunk 5 complete. Full suite green: 558 tests, unit and integration.

---

## 1. Status at a glance

| | |
|---|---|
| **Branch** | `feature/charges-engine`, rebased onto `master` after PR #59 (test framework) and PR #60 (D10 fix) |
| **Commits beyond master** | 14 — seven code commits and seven documentation commits |
| **Phase** | A (standalone engine). Chunks 1–5 complete. The engine can price a trade end to end. |
| **Next action** | Chunk 6 — validator, seeds and `ChargeScheduleService`. See §11 |
| **Blocking questions** | None. All Chunk 0 decisions are settled (§7). |

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

Tests: `ChargeScheduleResolverTest` (14), `ChargeInstrumentResolverTest` (7), `ChargeEngineTest` grown to 54, `ApplicationContextIntegrationTest` (3). Engine package: 98.5% line, 91.9% branch, 99.6% mutation — every class at 100% but one equivalent mutant.

### NOT written yet — do not assume any of it exists

No service, controller or seed data has been written. Specifically absent: `ChargeScheduleValidator`, `ChargeScheduleService`, `ChargeSeederService`, `UserChargeService`, `AmcChargeService`, every controller, and everything under `resources/data/charges/`.

Nothing calls `ChargeEngine` yet either — it is a bean, it is complete, and no production code invokes it. The simulate endpoint in Chunk 7 is its first caller.

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
./mvnw test -pl backend -am -Punit      # unit tier, no Docker — confirms the framework works
./mvnw verify -pl backend               # full suite plus the JaCoCo gate; needs Docker

# Mutation score for the charges engine. Scoped to this package on purpose: the profile's
# own target list also covers taxplanning, whose score is deferred (§9 item 7), so the
# unscoped invocation fails on the aggregate and tells you nothing about this work.
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage -Pmutation -pl backend \
  '-DtargetClasses=com.thiru.wealthlens.brokercharges.engine.*' \
  '-DtargetTests=com.thiru.wealthlens.brokercharges.*'
```

Then open `implementation-checklist.md` and start at the first unticked box.

**Working agreement, as instructed by the repository owner:**
- **Test-driven.** Write the failing test first, watch it fail, then implement until it passes. Not tests-alongside, and not tests-afterwards. In Java the first red is usually a compile error, which proves nothing — create minimal skeletons so the test compiles, then show real assertion failures before implementing. Pure data carriers with no branch or calculation have no behaviour to drive out and are exempt.
- Stop for review **after each chunk**.
- **One commit per chunk**, local to this branch; not pushed until asked.
- Build quality-first — no compromises taken for speed.

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

12. **`AssetEntity` has no ISIN or scheme code** — only `stockCode` and `stockName`. `ChargeInstrumentEntity` is keyed on `stockCode` for that reason, with `isin` stored for later.

---

## 9. Open items

Everything design-level is settled. Two remain open, neither blocking Chunk 2.

| # | Item | State |
|---|---|---|
| 1 | **AC-2 cannot be closed in Phase A** — golden fixtures assert against placeholder rates, so they pin the arithmetic but not reality | Decided (ADR-18). Needs real Zerodha rates, then one re-verification |
| 2 | **`ChargeAccountEntity` shape** — AMC billing cycles, `lastBilledThrough`, multiple demat accounts per broker (`AssetManagementDetails` carries `dematAccountId`) | Designed, not yet reviewed. Lands in Chunk 2 |
| 3 | **`charge_catalogue` initial code list** — the registry of valid charge codes | Drafted in tech-spec §7. Confirm the list when Chunk 6 seeds it |
| 4 | ~~Missing instrument profile: error or warning?~~ | **Settled — ADR-24.** Recorded, never fatal. Gated by `requiresInstrumentProfile`; validator checks expression variables against an allow-list |
| 5 | ~~Does `AccountType` affect charges?~~ | **Settled — ADR-25.** No rate impact, but `accountHolder` joins every dedupe key. Uncovered defect D10: DP charges are undercounted across account holders |
| 7 | **`taxplanning` mutation score is 34.4%** — 133 of 387 mutants killed, with `FormulaEvaluator`, `FbpOptimizer`, `ItrFormAdvisor` and `TaxEngineFactory` at zero. Pre-existing, and invisible until pitest was bumped to a version that runs on Java 25 | **Deferred by the repository owner** to the full layer, 2026-09-06. Not a defect to re-raise. Consequence: `-Pmutation` fails on the aggregate, so the charges engine is gated by running the profile scoped to its own package (see §6) |
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

---

## 11. Resume point — Chunk 5 complete

**Paused:** 2026-09-06. Build green: **558 tests**, `spotless:check` clean, JaCoCo gate passing, mutation score 99.6% on the engine package.

### Committed

- `d13281f`, `ddd3afc`, `d5596a7` — Chunk 3, in three parts
- `8c12945` — the seven calculators (Chunk 4)
- *this commit* — both resolvers, the instrument merge, and `ChargeEngine` as a `@Service` (Chunk 5)

Nothing is left uncommitted. **The engine can now price a trade end to end** — resolve card, resolve profile, merge, evaluate, assemble — and nothing calls it yet.

### Decisions taken while building

1. **`ChargeScheduleResolver` became a class, not an interface with one implementation.** The engine mocks it directly in tests; a second implementation was never coming.
2. **The instrument profile is looked up only when the card sets `requiresInstrumentProfile`.** Equity cards carry no scheme-level charges and this runs per transaction. The consequence is that an instrument profile attached to an equity is ignored — correct for Phase A, and worth remembering when the first non-fund profile appears.
3. **Rule provenance is not written onto the rule.** Rules live inside cached schedule and instrument documents, so stamping `source` on them would mutate shared state through the resolver cache. The engine pairs each rule with where it was read from instead.
4. **The instrument's attributes win** over anything the caller supplied under the same name. It is the source of truth for its own facts. The caller's other attributes survive.

### A conflict in the test plan, resolved

Test-plan Tier C lists `status != ACTIVE → not selected`. That contradicts ADR-12, which is settled and is what the repository query implements: the filter is `!= INACTIVE`, so a **superseded card still prices a transaction backdated into its own window**. Filtering on `== ACTIVE` would find nothing and silently charge zero for every backdated quarter — the exact failure ADR-12 exists to prevent.

`ChargeRepositoryIntegrationTest` already asserts the correct behaviour against a real Mongo, from Chunk 2. The Tier C row is wrong and the resolver's unit tests do not re-litigate validity at all — they test only the choice between cards that are already valid.

### Then: Chunk 6 — validator, seeds and the schedule service

`ChargeScheduleValidator` first: test-plan Tier D is 16 rejections, each asserting the *message*. It is what makes a rate card safe to accept, and several engine behaviours are documented as "the validator prevents this" — the D7 ambiguous rule, a `DERIVED` rule ordered before its own base, and expression variables checked against an allow-list so `#equityOrientd` cannot silently disable a rule forever.

Then `ChargeScheduleService` (publish, supersede-on-publish per FR-1, **and evicting both resolver caches**) and `ChargeSeederService` with the Zerodha placeholder cards.

### What TDD has caught so far, worth continuing for

1. `#charges['CODE']` threw `EL1027E` — SpEL indexes `Map`, and the lookup was backed by a record.
2. `20.00 * 0.18` returned `3.5999999999999996`. The failing assertion was the *test's* error: ADR-15 settles that rounding happens once in the orchestrator.
3. `ChargeCalculatorRegistry` annotated `@Component` broke application startup with all 213 unit tests green. Test-first at the *integration* tier turned that into a two-minute red-green.
4. The null `amountBasis` default — a contract missing from the rule itself, which all seven calculators would have copied.
5. Mutation testing found a dead negative-zero guard in `ChargeRounding`, five unkillable mutants that were one untested warning, and in this chunk three scheme attributes that were published but never read by any test.
