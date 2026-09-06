# Charges Engine — Start Here

**Purpose of this file:** the single entry point. If you are resuming this work — new session, new person, lost context — read this first and trust nothing about the codebase that is not stated here or verified from the code.

**Last verified against the repository:** 2026-09-06, branch `feature/charges-engine`, Chunk 3 complete. Full suite green: 451 tests, unit and integration.

---

## 1. Status at a glance

| | |
|---|---|
| **Branch** | `feature/charges-engine`, rebased onto `master` after PR #59 (test framework) and PR #60 (D10 fix) |
| **Commits beyond master** | 11 — five code commits and six documentation commits |
| **Phase** | A (standalone engine). Chunks 1–3 complete. |
| **Next action** | Chunk 4 — the seven calculators. See §11 |
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

Tests: `ChargeEngineTest` (42), `ChargeRoundingTest` (20), `ChargeFormulaEvaluatorTest` (17), `ChargeAccumulatorTest` (9), `ChargeCalculatorRegistryTest` (6), `ApplicationContextIntegrationTest` (1). Engine package: 97.4% line, 93.9% branch, 99.2% mutation.

### NOT written yet — do not assume any of it exists

No calculator, service, controller or seed data has been written. Specifically absent: every `*Calculator` implementation, the `ChargeScheduleResolver` *implementation*, `ChargeInstrumentResolver`, `ChargeScheduleValidator`, `ChargeScheduleService`, `ChargeSeederService`, `UserChargeService`, `AmcChargeService`, every controller, and everything under `resources/data/charges/`.

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

10. **Nothing in `engine/` is a Spring bean yet, and that is deliberate.** `ChargeCalculatorRegistry` refuses to construct unless every `ChargeBasis` is served; `ChargeEngine` depends on it and on a `ChargeScheduleResolver` that has no implementation before Chunk 5. Annotating any of the three takes application startup down. The annotations go on in Chunk 4, together. `ApplicationContextIntegrationTest` is what tells you if one goes on early.

11. **`ChargeCalculatorRegistry` is deliberately not a `@Component`.** Its constructor refuses to build unless every `ChargeBasis` is served, so annotating it before the Chunk 4 calculators exist takes the whole application down at startup. The annotation goes on when the last basis is served. Do not add it back early — it was added once, and nothing caught it because unit tests construct the registry directly.

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

## 11. Resume point — Chunk 3 complete

**Paused:** 2026-09-06. Build green: **451 tests** (unit + integration), `spotless:check` clean, JaCoCo gate passing, mutation score 99.2% on the engine package.

### Committed

- `d13281f` — `ChargeRounding`, `ChargeAccumulator`, `ChargeFormulaEvaluator`, `ChargeContext`, `ChargeComputation`, `LotSlice` (Chunk 3, part 1)
- `ddd3afc` — `ChargeCalculator`, `ChargeCalculatorRegistry`, `ApplicationContextIntegrationTest` (Chunk 3, part 2)
- *this commit* — `ChargeEngine` and `ChargeScheduleResolver` as an interface (Chunk 3, part 3)

Nothing is left uncommitted.

### Deviations from the spec, decided while building

1. **`compute(ChargeContext)`, not `compute(UserMail, ChargeContext)`** as tech-spec §5.4 has it. A calculator receives `(rule, context, accumulator)` and cannot see a separate parameter, so threading identity that way would leave the engine holding an argument it never reads. When the Chunk 4 scoped calculator needs an email for its dedupe key, it joins `ChargeContext` — which already carries four of the five parts of that key.
2. **`ChargeRule.effectiveAmountBasis()`**, added because a null `amountBasis` means turnover and *every* percentage calculator has to know that. Defaulting it in the engine alone left the calculators reading a raw null and pricing on zero. Found by a failing test, not by review.
3. **`ChargeScheduleResolver` is an interface only.** The engine is written against it; the specificity ranking, validity window and cache are Chunk 5.
4. **Instrument-sourced rules are not merged yet.** `ChargeComputation.instrumentId` stays null and `NO_INSTRUMENT_PROFILE` is unused until `ChargeInstrumentResolver` lands. `ChargeLine.source` is already populated and honours a rule that declares `INSTRUMENT`, so the merge is additive.

### What the quality gates caught, beyond the tests

- **`ChargeRounding.normaliseNegativeZero` was dead code.** `BigDecimal` has no signed zero, so a value rounding to zero from below is already `0.00` — the method could never change a value and its javadoc claimed the opposite. A surviving mutant is what exposed it; removed, and the test kept, restated as a property `BigDecimal` gives us rather than one the class enforces.
- **No test proved a `side: BUY` rule is actually levied on a purchase.** Only the negative case was asserted, so a side filter that rejected everything would have looked correct.
- **The mutation profile had never run on this toolchain.** pitest 1.17.0 cannot read Java 25 bytecode — `Unsupported class file major version 69`. Bumped to 1.20.3. See §9 item 7 for what it then reported about `taxplanning`.

### Then: Chunk 4 — the calculators

Seven implementations of `ChargeCalculator`, one per `ChargeBasis`, test-first per §6. When the last one lands, `@Component` goes on all seven, on `ChargeCalculatorRegistry` and on `ChargeEngine` in the same commit — that is the point at which startup can succeed, and `ApplicationContextIntegrationTest` proves it.

`ScopedFlatChargeCalculator` is the one carrying design left over from this chunk: its dedupe key needs the user's email, per §5.9 and deviation 1 above.

### What TDD has caught so far, worth continuing for

1. `#charges['CODE']` threw `EL1027E` — SpEL indexes `Map`, and the lookup was backed by a record. Implementation-first, this would have surfaced later inside the engine with a murkier trace.
2. `20.00 * 0.18` returned `3.5999999999999996`. The failing assertion turned out to be the *test's* error: ADR-15 settles that rounding happens once in the orchestrator, so an unrounded return is correct. A test written afterwards would have been shaped to whatever the code did.
3. `ChargeCalculatorRegistry` annotated `@Component` broke application startup, with all 213 unit tests green. Applying test-first to the *integration* tier is what turned "the app is broken and no test knows" into a two-minute red-green.
4. The null `amountBasis` default (deviation 2). One engine test failed for a reason that was neither the test's fault nor the engine's — it was a contract missing from the rule itself, which would have been copied into all seven calculators.
