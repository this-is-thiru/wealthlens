# Charges Engine — Implementation Checklist

**Purpose:** the build tracker. If context is lost mid-implementation, resume from the first unticked box.
**Read first:** `README.md` (where things stand), then `decisions.md` (why), `tech-spec.md` (what), `test-plan.md` (how it is verified). This file is *only* the sequence.

**Branch:** `feature/charges-engine`
**Status:** Chunks -1 through 7 and 9 complete — **all of Phase A**. **Resume at the Phase A gate below**, then Chunk 8 (Phase B).
**Last updated:** 2026-09-07 — 744 tests green across both tiers, `spotless:check` clean, both JaCoCo gates passing, `brokercharges.engine` at 99% mutation score.

Four boxes in the completed chunks are deliberately left unticked rather than quietly dropped. Each says why on its own line:

| Box | Why it is open |
|---|---|
| `ChargeEngineProperties` (Chunk 3) | Nothing reads a flag yet. Belongs with Chunk 8 |
| AMC rate card (Chunk 6) | Not on the original list, and it would be the broker's second unscoped card |
| Rate verification (Chunk 6) | Needs a human against the broker's page. **Blocks AC-2** |
| `BrokerageAggregatorType` deletion (Chunk 1) | Phase C, once its last usage is gone |

---

## Phasing

| Phase | Scope | Touches `portfolio`? |
|---|---|---|
| **A** | Standalone engine — schedules, rules, seeds, calculators, simulate API, golden tests | **No.** Purely additive. |
| **B** | Shadow recording — engine computes and persists alongside the existing flow; reconciliation report | One new interface + its injection |
| **C** | Cutover — computed total drives cost basis, manual charge input retired, old code deleted | Yes |

Chunks 1–7 and 9 are Phase A. Chunk 8 is Phase B. Chunks 10–11 are Phase C.
**Phase A exit criterion:** `git diff master --stat -- backend/src/main/java/com/thiru/wealthlens/portfolio/` is empty.

---

## Ground rules for every chunk

- **Test-driven.** The test comes first and must be seen to fail before any implementation exists. Chunks 1 and 2 each surfaced a defect that only appeared once something ran — a Mongo query whose parameter binding could have failed silently, and index annotations that created nothing. Both would have been caught at specification time by a test written first.

- Conventions per `CLAUDE.md`: `@RequiredArgsConstructor` + `private final`; services `@Service @Log4j2 @Transactional`; entities `@Data @AllArgsConstructor @NoArgsConstructor @Document @Field`; snake_case Mongo fields; `@MongoId`; implement `AuditableEntity`; 4-space indent; no star imports.
- Tests: `@ExtendWith(MockitoExtension.class)`, `// Given` `// When` `// Then`, `methodName_whenCondition_expectedResult`.
- **Surefire has `testFailureIgnore=true`** — a green exit code means nothing. After every chunk:
  ```bash
  ./mvnw test -pl backend -Dtest='Charge*Test'
  grep -l 'failures="[1-9]"\|errors="[1-9]"' backend/target/surefire-reports/TEST-*.xml
  ```
  The grep must print nothing.
- `./mvnw spotless:apply` before each commit (not bound to a phase).
- Everything in this module is **new code**. No compatibility obligation to the old `broker_charges` / `user_broker_charges` documents — they are dropped in Phase C, not migrated.
- Nothing is imported from `taxplanning`. The charges module owns its own evaluator, resolver and seeder.

---

## Chunk -1 — Test framework prerequisites

From `../testing/test-framework-audit.md`. These land **before** engine code, because ~176 engine tests are written against them. Roughly one day.

- [x] **T3** `-Punit` / `-Pintegration` profiles + `@Tag("integration")` — 161 unit tests in 6.8s without Docker
- [x] **T5** AssertJ on the backend classpath
- [x] **T4** `testsupport/MoneyAssert` — `assertMoney`, `assertNoCharge`, `assertBreakdown`
- [x] **T1** `maven.test.failure.ignore` defaults `false`; CI passes it explicitly
- [x] **T2** JaCoCo 0.8.14, gate scoped to `brokercharges.engine*` (90% line / 85% branch)
- [x] **T9** PIT via `-Pmutation`, scoped to the calculation engines, threshold 85
- [x] **T7** `cleanDatabase()` enumerates collections and preserves a seeded whitelist
- [x] **T8** `ArchitectureTest` — 8 ArchUnit rules encoding CLAUDE.md, all passing
- [x] **T10** parallel execution by class; integration classes `@Isolated`
- [x] **T12** `spotless:check` in CI; **T13** CI report-count gate; **T14** `Student.java` moved to `testsupport/`
- [x] **T11** charge-specific test fixture builders — `ChargeFixtures` (public, shared across the engine and service test packages) and `testsupport/LogCapture`
- [x] **Design:** engine arithmetic in `BigDecimal` internally, `double` only at the persistence boundary (test-plan §2.1)

---

## Chunk 0 — Decisions locked before coding ✅

| # | Decision | Settled as |
|---|---|---|
| Brokers | Seed cards for which brokers | **Zerodha only.** Prove the engine end-to-end on one broker; Upstox and Fyers are data-only additions afterwards |
| Cards | Which schedules | **Three: EQUITY/DELIVERY, EQUITY/INTRADAY, MUTUAL_FUND.** Intraday proves the `TradeSegment` dimension resolves; MF proves `FORMULA` + `eligibility` (exit load on holding period) |
| Rates | How rate values are sourced | **Placeholders, clearly marked.** Structurally valid cards with `verifiedOn: null`. See the caveat below |
| Cadence | Review points | **After every chunk.** |
| Commits | Granularity | **One commit per chunk**, local to this branch |
| OD-1 | Module rename `brokercharges` → `charges` | Deferred to Phase C, as a separate mechanical commit |
| OD-2 | Engine vs manual entry for cost basis | Engine replaces manual entry — that is the point of the work |
| OD-3 | F&O | Model-only. `amountBasis`, `lotSize`, `orderId` carried; no seed cards |
| OD-5 | `planCode` | Dimension exists on the schedule; not populated in Phase A |
| OD-9 | AMC accounts | New `ChargeAccountEntity`; `AssetManagementDetails` untouched until Phase C |

### ⚠️ Consequence of placeholder rates

Golden contract-note fixtures (test-plan Tier E) will assert against **fictional numbers**. They still do real work — they pin the engine's arithmetic, rounding and GST base, and they fail loudly if any of that regresses. What they do **not** yet prove is that the output matches a real Zerodha contract note.

Therefore:
- **AC-2 ("matches a real contract note to ₹0.01") is BLOCKED** until real rates are supplied. It is the one acceptance criterion Phase A cannot close.
- Every seeded card carries `verifiedOn: null` and a `PLACEHOLDER` marker in `notes`.
- `ChargeSeederServiceTest` asserts that a card with `verifiedOn: null` logs a startup WARN, so unverified rates cannot go unnoticed in a running system.
- Replacing the rates later changes JSON only — the golden fixtures are regenerated from the simulate endpoint and re-verified once.

---

# PHASE A — standalone engine

## Chunk 1 — Enums and value objects ✅

- [x] `ChargeBasis` — TURNOVER, FLAT, PER_UNIT, SLAB, SCOPED_FLAT, DERIVED, FORMULA
- [x] `ChargeCategory` — BROKERAGE, STATUTORY, EXCHANGE, REGULATORY, DEPOSITORY, TAX, SUBSCRIPTION, FUND
- [x] `ChargeEvent` — BUY, SELL, ACCOUNT_OPENING, AMC_CYCLE, CALL_AND_TRADE, AUTO_SQUARE_OFF, PLEDGE
- [x] `ChargeSide` — BUY, SELL, BOTH
- [x] `DedupeScope` — NONE, PER_SCRIP_PER_DAY, PER_ORDER, PER_DAY
- [x] `AggregatorType` — MIN, MAX
- [x] `RoundingPolicy` — NONE, HALF_UP_2, CEILING_2, HALF_UP_0
- [x] `AmountBasis` — TURNOVER, NOTIONAL, PREMIUM, INTRINSIC, PRINCIPAL *(only TURNOVER used in Phase A)*
- [x] `TradeSegment` — DELIVERY, INTRADAY, FUTURES, OPTIONS, NA *(charges-owned in Phase A; promoted to `portfolio` at cutover)*
- [x] `SlabBandBasis` — TURNOVER, HOLDING_DAYS, QUANTITY
- [x] `ChargeRuleSource` — SCHEDULE, INSTRUMENT
- [x] `FundCategory` — EQUITY, DEBT, HYBRID, LIQUID, ELSS, INDEX, ETF, FUND_OF_FUNDS, OTHER
- [x] `PlanType` — DIRECT, REGULAR *(decides whether a distributor transaction fee can apply)*
- [x] `ChargeResolution` — RESOLVED, NO_MATCHING_RULES, NO_SCHEDULE, NO_INSTRUMENT_PROFILE, PROVISIONAL
- [ ] Delete `BrokerageAggregatorType` *(Phase C, once its last usage is gone)*

**Verified:** `./mvnw compile -pl backend -am` BUILD SUCCESS; `spotless:check` clean. Commit `62d864f`.

### Design change absorbed in this chunk

Mutual fund charges differ **per scheme**, not per broker — exit load is an AMC attribute, so two funds bought through the same broker on the same day carry different loads. Forcing that into `ChargeScheduleEntity` would mean one schedule document per fund. Three additions cover it (tech-spec §4.6, §5.7, §6):

- **`ChargeInstrumentEntity`** (`charge_instruments`) as a second rule source, merged into one ordered evaluation so tax bases and rounding are unaffected. Also the only home for `equityOriented`, without which *"STT on equity MF but not debt MF"* cannot be expressed at all.
- **`ChargeSlab.slabBandBasis`** — graded exit loads taper by holding days (liquid funds: 0.0070% day 1 → nil by day 7), not by trade size.
- **`ChargeRule.perLot` + `ChargeContext.lots`** — exit load applies per FIFO lot. Transaction-level averaging can be wrong by the entire charge: 100 units held 22 months plus 50 held 1 month averages to ~15 months and computes **zero**, where the correct answer is 1% on 50 units.
- **`PlanType`** + the rule-ownership principle (tech-spec §4.6.1). The MF distributor transaction fee has *three* sources at once — the broker sets the amount, the scheme's DIRECT/REGULAR status decides whether it applies, and AMFI caps it at ₹150 for a first-time investor versus ₹100 thereafter. Resolved by: **a rule lives where its rate is decided, and reads the other sources through its eligibility predicate.** This requires instrument attributes to be injected into the evaluation context for *every* rule, not only instrument-sourced ones.
- **Precedence** (tech-spec §4.6.2): when both sources declare the same charge code, the instrument wins and the schedule rule is skipped. Applying both would double-charge silently.

### Defect found and corrected in this chunk

**Superseding a rate card would have broken backfilled transactions** (tech-spec §14.1). `EntityStatus` carries `SUPERSEDED`, and the existing resolver query filters `status: 'ACTIVE'`. Supersede a 2024 card in 2025, upload a 2024 transaction in 2026, and nothing resolves — the charge computes as zero, silently.

Date validity and record legitimacy are two orthogonal concepts sharing one field. Corrected by:
- superseding sets `endDate` only and **never** touches `status`;
- the resolver filters `status != INACTIVE` rather than `== ACTIVE`, so it survives a future maintainer setting `SUPERSEDED`;
- `INACTIVE` means retracted-in-error — unusable for *any* date, not merely expired;
- currency is `endDate == null`, never a status.

Three consequences, all specced in §14.2–14.4: unresolved charges are **persisted with a `ChargeResolution`** rather than only logged, so backfill gaps are queryable; order-sensitive rules (`#firstTimeInvestor`, per-lot exit load) are marked `PROVISIONAL`; and recomputation **rebuilds** P&L charge aggregates from `user_charges` instead of accumulating deltas, which incremental merging cannot survive.

---

## Chunk 2 — Entities and repositories ✅

**Eight documents** in `brokercharges/entity/`:
- [x] `ChargeSlab` — embedded band; the quantity banded is named by the owning rule's `slabBandBasis`
- [x] `ChargeRule` — embedded; the unit of extensibility. Carries `perLot`, `appliesToCorporateActions`, `amountBasis`, `slabBandBasis`, `baseCodes`, `eligibility`, `formula`
- [x] `ChargeLine` — embedded computed line, carrying `rate` and `baseAmount` so a stored charge is re-derivable rather than taken on trust
- [x] `ChargeScheduleEntity` — `charge_schedules`, with `requiresInstrumentProfile`, `sourceUrl`, `verifiedOn`
- [x] `ChargeInstrumentEntity` — `charge_instruments`, the second rule source; holds `equityOriented` and `planType`
- [x] `UserChargeEntity` — `user_charges`, source of truth; `resolution`, `computedOn`, `accountHolder`, `amountByCode`
- [x] `ChargeCatalogueEntity` — `charge_catalogue`, the code registry standing in for a compiler
- [x] `ChargeAccountEntity` — `charge_accounts`, with `lastBilledThrough` and embedded `BillingEvent`

**Five repositories** in `brokercharges/repository/`:
- [x] `ChargeScheduleRepository` — `findCandidates` filters `status != INACTIVE`, handles null `end_date`
- [x] `ChargeInstrumentRepository` — same validity semantics for AMC exit-load revisions
- [x] `UserChargeRepository` — three `exists` dedupe queries, gaps query, out-of-sequence probe
- [x] `ChargeCatalogueRepository`, `ChargeAccountRepository` (`findDueForAmc`)

**Indexes:** unique on `schedule_code` and `code`; compound unique on `{email, transaction_id}`; dedupe on `{email, account_holder, broker_name, stock_code, transaction_date}`; history on `{email, transaction_date desc}`; unique on `{email, broker_name, demat_account_id}`.

### Defect found: index annotations were doing nothing

`spring.data.mongodb.auto-index-creation` is not set, and Spring Data MongoDB has defaulted it to **false** since 3.0. `@Indexed` and `@CompoundIndex` were therefore decorative — the unique index meant to make a re-uploaded quarter idempotent would not have existed, and every dedupe lookup would have been a collection scan. **The existing `@Indexed` annotations in `taxplanning` are non-functional for the same reason.**

Enabling the setting globally would build indexes for every entity in the application at startup, including collections this module knows nothing about. So `config/ChargeIndexInitializer` applies the annotations for the charges collections only, via `MongoPersistentEntityIndexResolver`. The annotations stay the single declaration; `createIndex` is idempotent, so it is a no-op after the first start.

### Verification
- [x] `ChargeRepositoryIntegrationTest` — **14 tests, all passing** against real MongoDB
- [x] The superseded-card regression (ADR-12): a card closed in 2025 still prices a 2024 trade
- [x] `status: SUPERSEDED` still resolves; `status: INACTIVE` resolves nothing at any date
- [x] `existsChargeForScripOnDate` — proves the parameter substituted into the field path (`amount_by_code.?5`) actually binds, which would otherwise fail silently
- [x] Different `accountHolder` does not suppress a depository charge (D10 semantics carried into the new model)
- [x] Duplicate `transaction_id` rejected — which also proves the index initializer works
- [x] Full suite **356 tests green**; ArchUnit's snake_case `@Field` rule passes across ~80 new annotations
- [x] Phase A isolation intact: `git diff master --stat -- .../portfolio/` empty

---

## Chunk 3 — Engine core ✅

- [ ] `config/ChargeEngineProperties.java` — **not built.** Nothing reads a flag yet; the engine has no live caller. Belongs with Chunk 8, where `shadowRecording` first means something
- [x] `engine/ChargeAccumulator.java` — holds lines, `sumOf(List<String> codes)`, `amountOf(code)`
- [x] `engine/ChargeCalculator.java` — the strategy interface
- [x] `engine/ChargeRounding.java` — `RoundingPolicy` application
- [x] `engine/ChargeFormulaEvaluator.java` — charges-owned SpEL wrapper (tech-spec §5.6): `evaluate`, `matches`, `validate`, `referencedVariables`
- [x] `engine/ChargeCalculatorRegistry.java` — `List<ChargeCalculator>` → `Map<ChargeBasis, …>`, fails fast on duplicate or missing basis
- [x] `dto/context/ChargeContext.java` — record. **No `forTrade` / `forAmcCycle` factories:** every caller so far builds the record directly, and a factory nothing calls is a guess about the caller. `AmcChargeService` builds its own cycle context. Also gained `email`, which the scoped dedupe key needs
- [x] `dto/context/ChargeComputation.java` — record + `empty(resolution)`, `amountOf`, `amountByCode`. `empty()` takes a resolution: an empty computation must say *why*
- [x] `engine/ChargeEngine.java` — resolve → filter → sort → dispatch → modifiers → assemble (tech-spec §5.4, §5.5)
- [x] `ChargeEngineTest` — test-plan Tier B, 56 cases
- [x] `ChargeFormulaEvaluatorTest` — variable exposure, accumulator access, `validate` rejects bad syntax
- [x] `ApplicationContextIntegrationTest` — **added, not planned.** A `@Component` on the registry broke startup with every unit test green; nothing asserted the application starts

**Done when:** the engine prices a trade from a stubbed schedule. ✅

---

## Chunk 4 — Calculators ✅ *(one class each, independently testable)*

**All seven are built.** An earlier draft deferred `PER_UNIT` and `SLAB` as unused. That was wrong on two counts: `SlabBandBasis` exists specifically so graded exit loads can band on holding days, so deferring `SlabChargeCalculator` would leave that enum dead code; and a `ChargeBasis` constant with no registered calculator is a runtime trap for whoever first writes a rule using it. Each is ~30–50 lines.

All seven live in `engine/`, not `engine/calculator/` — a flat package, so both quality gates (`brokercharges.engine*`) cover them without a pom change.

- [x] `TurnoverChargeCalculator` — reads `rule.effectiveAmountBasis()` from `context.baseAmounts()`; warns and charges nothing when the context lacks that amount
- [x] `FlatChargeCalculator`
- [x] `PerUnitChargeCalculator` — amount × quantity. **Per-lot derivatives pricing is not implemented:** nothing in the context distinguishes a quantity of lots from one of units, and Phase A seeds no F&O card
- [x] `SlabChargeCalculator` — bands by `slabBandBasis`: TURNOVER, HOLDING_DAYS or QUANTITY, with the rate applied to `amountBasis` rather than to the banded quantity
- [x] `ScopedFlatChargeCalculator` — dedupe via `UserChargeRepository`, keyed on the account holder (D10)
- [x] `DerivedChargeCalculator` — **sums only `baseCodes`; fixes D1**
- [x] `FormulaChargeCalculator` — delegates to `ChargeFormulaEvaluator`
- [x] `ChargeCalculatorRegistry` fails fast at startup if any `ChargeBasis` constant has no calculator — and is now a `@Component`, verified wired by `ApplicationContextIntegrationTest`
- [x] One `*CalculatorTest` per calculator — 81 tests. **Min/max/aggregator and rounding are asserted in `ChargeEngineTest`, not here.** ADR-15 puts those in the orchestrator; a calculator that applied them too would round twice
- [x] `DerivedChargeCalculatorTest` includes the **STT-excluded-from-GST-base** case that pins D1

**Done when:** every basis is served and the application starts. ✅

---

## Chunk 5 — Resolution, validation, services ✅

- [x] `service/ChargeScheduleValidator.java` — every FR-2 rule, and each reported together rather than one per run:
  - [x] duplicate `code` within a schedule
  - [x] `DERIVED` referencing an unknown `baseCode`
  - [x] `DERIVED` whose `baseCode` has `order >=` its own
  - [x] basis missing its required parameter
  - [x] `rate` + `flatAmount` present without `aggregator`, and an `aggregator` without both operands (**fixes D7**)
  - [x] `formula` / `eligibility` that fails `ChargeFormulaEvaluator.validate`
  - [x] `code` absent from `charge_catalogue`
  - [x] expression naming a variable outside the known vocabulary (**ADR-24**, `#equityOrientd`)
  - [x] slab bands that overlap or leave a gap; negative amounts; a card with no rules; an end date before its start
- [x] `engine/ChargeScheduleResolver.java` — specificity scoring (tech-spec §6), caching including misses, eviction on write. **Lives in `engine/`, not `service/`:** the engine depends on it, and both quality gates already cover that package. A class, not an interface — a second implementation was never coming
- [x] `engine/ChargeInstrumentResolver.java` — **not on the original list.** The engine merges instrument-sourced rules, so something had to resolve them
- [x] `service/ChargeScheduleService.java` — publish with **auto-supersede** (fixes D6), fetch, list, close, and resolver-cache eviction on every write
- [x] `service/UserChargeService.java` — `computeAndRecord`, batch with the out-of-sequence guard, history, gaps, `deleteByEmail`
- [x] `service/ChargeAccountService.java` — CRUD over `charge_accounts`, preserving billing history across re-registration
- [x] `service/AmcChargeService.java` — AMC cycle billing via `ChargeEvent.AMC_CYCLE`, idempotent per account and period
- [x] `ChargeScheduleResolverTest` — test-plan Tier C, 14 cases. **The date and status rows are not re-tested here:** they belong to the repository query and are asserted against a real Mongo in `ChargeRepositoryIntegrationTest`. Tier C's `status != ACTIVE → not selected` row contradicts ADR-12 and is wrong; a superseded card must still price a backdated trade
- [x] `ChargeInstrumentResolverTest` — 7 cases
- [x] `ChargeScheduleValidatorTest` — test-plan Tier D, 31 cases, each asserting the *message*
- [x] `ChargeScheduleServiceTest` (17), `UserChargeServiceTest` (20), `ChargeAccountServiceTest` (8), `AmcChargeServiceTest` (12)
- [x] `ChargeInvariantTest` — test-plan Tier F, 10 properties over 200 generated cards each, verified non-vacuous by reintroducing D1 and watching two properties fail

**Done when:** publishing over an open schedule closes the incumbent rather than throwing (AC-8). ✅

**Found while building this:** the engine applied *both* sources' version of a shared charge code, double-charging silently against tech-spec §4.6.2. Fixed with a failing test first. And `NO_MATCHING_RULES` was missing from the gaps report, so a trade priced by a catch-all card charged zero invisibly.

---

## Chunk 6 — Seed data ✅ *(bar the rate verification, which is not ours to do)*

- [x] `resources/data/charges/charge-catalogue.json` — all 12 codes
- [x] `zerodha-equity-delivery-2025-04-01.json`
- [x] `zerodha-equity-intraday-2025-04-01.json` — proves the segment dimension and the MIN aggregator
- [x] `zerodha-mutual-fund-2025-04-01.json` — proves `requiresInstrumentProfile` and eligibility on a scheme attribute. **Exit load is not on it:** exit load belongs to the instrument profile, and no profile is seeded
- [x] `upstox-equity-delivery-2025-04-01.json`
- [x] `fyers-equity-delivery-2025-04-01.json`
- [x] `zerodha-maintenance-2025-04-01.json` — **the AMC rate card**, added after the chunk. It leaves `assetType`, `segment`, `exchange` and `planCode` unset, because the cycle context carries none of them and a card declaring any is disqualified by the resolver. That makes it Zerodha's only unscoped card, which `atMostOneShippedCardPerBrokerIsUnscoped` now guards. Both its rules declare `AMC_CYCLE` alone: as the fallback for every asset type no specific card covers, a rule of its own reaching BUY or SELL would price ordinary trades as maintenance
- [x] `instruments/parag-parikh-flexi-cap-2025-04-01.json` and `instruments/hdfc-liquid-fund-2025-04-01.json` — **the scheme profiles**, added after the chunk to close AC-6. One graded exit load (`SLAB` banded on `HOLDING_DAYS`, tapering to a declared nil band) and one expressed as a predicate (`#holdingDays < 7`), both `perLot`. Their own directory: the schedule pattern does not descend into it, so a profile can never be parsed as a rate card with every field null
- [x] `ChargeSeederService.seedInstruments()` — profiles seeded last, since they name catalogue codes and are what the mutual fund card's `requiresInstrumentProfile` refers to. Idempotent by scheme **and start date**, a profile having no code of its own; validated before persisting; `ChargeInstrumentResolver.evictAll()` now called alongside the schedule resolver's
- [x] `ChargeScheduleValidator.validate(ChargeInstrumentEntity)` — the same window and rule checks against a profile. Validating only the broker's card left exit load, the one charge a rate card cannot express, entirely unchecked
- [x] `ChargeSimulationRequest.lots` — added so AC-6 is visible from the API and not only from the suite. Without it `ChargeSimulationService` passed an empty lot list and a `perLot` rule evaluated zero times, so `/charges/simulate` answered ₹0 exit load whatever the profile said. A lot set that does not account for its disposal, carries no `acquisitionDate`, or postdates the trade is rejected rather than priced: each of those makes the charge *smaller* rather than making the call fail, which is the failure mode this endpoint must not have
- [x] `service/ChargeSeederService.java` — `@PostConstruct`, catalogue first, idempotent by code, validates before persisting, **fails fast** on a bad card
- [x] `ChargeSeederServiceTest` — test-plan Tier G, 14 cases against the real files
- [x] `ChargeGoldenFileTest` + fixtures — test-plan Tier E, 12 contract notes including the D1 regression fixture, verified non-vacuous
- [ ] ⚠️ Every rate verified against the broker's live charges page; `sourceUrl` + `verifiedOn` filled — **`sourceUrl` is populated on every card; `verifiedOn` is null by design (ADR-18) and Tier G asserts it. This box needs a human and blocks AC-2. Scheduled for staging after the merge, so it stays open across the merge rather than holding it up**

---

## Chunk 7 — Charge summary reporting model ✅ *(new types, old ones untouched)*

Phase A adds the aggregation shape without rewiring P&L. `ProfitAndLossService` still writes the old `BrokerChargesReport` until Phase C.

- [x] `entity/model/ChargeSummaryReport.java` (in `brokercharges`) — `Map<String, Double> amountByCode`, `totalCharges`, `merge(Map<String,Double>)`
- [x] `entity/model/YearlyChargeSummary.java`, `MonthlyChargeSummary.java`
- [x] `ChargeSummaryReportTest` — 11 cases; merging an unknown code surfaces it with no code change (AC-1)

### Decisions taken in this chunk

- **`merge` sums in `BigDecimal`, not `double`.** A period accumulates hundreds of paise-scale amounts, and `0.10` added ten times in `double` is `0.9999999999999999`. `merge_acrossManyIncrements_doesNotDrift` asserts exact equality rather than within-a-paisa, because within-a-paisa is precisely what this type must not need.
- **`totalCharges` is recomputed from `amountByCode`, never accumulated beside it.** The total and the columns printed next to it now cannot disagree; the test asserts the relationship rather than a number.
- **`merge` stamps `lastUpdatedTime` itself.** `@LastModifiedDate` fires for an aggregate root, not for a nested object, so the annotation the old `BrokerChargesReport` carries leaves the field null forever. Dropped it and set the value explicitly, through `TLocalDateTime.now()` — the first draft of the test compared against `LocalDateTime.now()` and failed by exactly 5:30, which is the convention working.
- **A null amount is rejected, a null map is not.** A null map is the legitimate zero-charge row written for a period with no rate card on file; a null *amount* is a caller defect, and folding it in as zero would hide it.
- **A fortnight half stays null until something is charged in it**, rather than being pre-created empty: a month with no trades in its second half should read as having none, not as having charged zero.

### Deviation from the spec

`MonthlyChargeSummary` carries `@NoArgsConstructor` alongside the `Month` constructor, which `MonthlyBrokerCharges` does not. With one constructor Spring Data must map through it; with a no-arg constructor available it materialises fields directly, which is the behaviour the rest of the module's entities rely on.

---

## Chunk 9 — Controllers and API ✅ *(still Phase A)*

- [x] `controller/ChargeScheduleController.java` (tech-spec §10) — publish, fetch, list by broker, close, unverified, catalogue
- [x] `controller/ChargeSimulationController.java` — `POST /charges/simulate`, persists nothing
- [x] `controller/UserChargesController.java` — history, per-transaction contract note, gaps
- [x] `controller/ChargeAccountController.java` — register, list, AMC cycle
- [x] `auth/config/AuthConfig` review — **this found a live gap.** The chain ends in
      `anyRequest().permitAll()`, so all five new prefixes were public. Now authenticated, and
      `chargeEndpoints_rejectAnUnauthenticatedRequest` pins it
- [x] `api-collection/` — new `Charges Engine` folder, 11 requests, each documenting its own
      rejection cases rather than only its happy path
- [x] Golden-file fixtures — **already authored in Chunk 6, through the engine rather than the
      endpoint.** Faster and identical in coverage; the simulate path is exercised by Tier I instead
- [x] `ChargesIntegrationTest extends AbstractIntegrationTest` — 24 cases
- [x] `ChargeExtensibilityTest` — test-plan Tier J, 3 cases; makes AC-1 permanent
- [x] Tier H persistence/dedupe and Tier I API cases — 24 across the two tiers, plus 3 in Tier J
- [x] `cleanDatabase()` — **no change needed, by design.** It wipes every collection *except* a
      seeded whitelist, so the four charge collections were already cleared. `charge_catalogue` is
      on the whitelist (its seeder is `@PostConstruct` and would never re-run); the one test that
      adds a code to it removes it in an `@AfterEach`

### Two defects found by writing these tests

- **Rate-card and AMC endpoints were open to any authenticated user.** Publishing a card reprices
  every user's trades and `/charges/amc/impose` bills real money across every account. Both now
  carry `@PreAuthorize("hasRole('SUPER_USER')")`, matching the tax-planning policy endpoints.
- **A zero price was rejected by a boundary nobody tested.** Mutation testing killed the claim: the
  comment said a bonus allotment is issued free and still attracts charges, `>= 0` mutated to `> 0`,
  and every test still passed. Now asserted.

### Two test expectations that were wrong, not the code

- Superseding a card **does not** set `SUPERSEDED`. It closes the window and leaves the status
  alone, because `findCandidates` excludes nothing but `INACTIVE` and a superseded card must still
  price the trades inside its own window. The test now asserts the window and says why.
- The resolver caches by scope and date, and **only the publish path evicts.** A test writing
  straight to the repository saw a stale card. That is a real operational constraint — a rate card
  written outside `ChargeScheduleService` is invisible until eviction — and is now recorded in the
  test that tripped over it.

### Deviations from the spec

- Schedules are addressed by `scheduleCode`, not Mongo id. The code is what the seed files, the
  validator and every stored charge row already carry, and the only one of the two a human can quote.
- `GET /charge-catalogue` is served by `ChargeScheduleController`, since it is the registry those
  cards are validated against. It needed a `ChargeCatalogueService` — a controller reaching for a
  repository fails `ArchitectureTest`.
- `ChargeSimulationService` was not on the list. The controller could have called the engine
  directly, but then "persists nothing" would be a comment; as a service holding the engine and no
  repository, it is a property the test asserts by reading the class's own fields.

### ✅ Phase A gate — verified 2026-09-07, one item open

- [ ] **AC-1 through AC-9 and AC-12 pass** — every one but AC-2 is signed off with named evidence
      below. **AC-6 closed on 2026-09-08** by seeding the two scheme profiles. **AC-2 remains open
      and is not code work:** it needs a human to compare each shipped rate against the broker's
      published page, and `GET /charge-schedules/unverified` is the worklist. The owner has
      scheduled it for staging after the merge, so it does **not** gate this branch
- [x] **Line coverage ≥ 90%, branch ≥ 85%** — both JaCoCo rules pass under `mvn verify`
- [x] **Mutation score ≥ 85% on `brokercharges.engine.**`** — **99%**, 256 of 257 mutants killed, 0
      uncovered. The charges services score 99% on the same scoping (211/212); the aggregate
      `-Pmutation` invocation still fails on the deferred `taxplanning` score, which is why the
      scoped command in `README.md` §6 is the one that gates this work
- [x] **All golden contract notes pass at ₹0.01** — 12 fixtures, asserted line by line and in total
- [x] **`git diff master --stat -- .../portfolio/` is empty** — re-checked at the end of Chunk 9
- [x] **`WealthLensModulithTest.modulithStructureIsValid()` green**
- [x] **779 tests green across both tiers** (744 at the end of Chunk 9; +35 from the AMC card, the
      scheme profiles and simulate's FIFO lots), surefire XML gate clean, `spotless:check` clean
- [ ] **Discuss results before starting Phase B**

---

# PHASE B — shadow recording

## Chunk 8 — Parallel flow, no behaviour change

- [ ] `portfolio/service/ChargeRecordingGateway.java` — interface owned by `portfolio`, returns `Optional<ChargeComputation>`
- [ ] `brokercharges/service/ChargeRecordingGatewayImpl.java` — computes + persists when `app.charges.shadow-recording=true`
- [ ] Inject into `ProfitAndLossService`; **ignore the return value** — cost basis untouched
- [ ] Remove the `assetType == EQUITY` gate at `ProfitAndLossService:333` and `:361` (FR-8) so non-equity trades produce shadow records
- [ ] `GET /user-charges/user/{email}/reconciliation` — computed vs user-entered per transaction, with delta
- [ ] `ChargeRecordingGatewayImplTest`; extend `ProfitAndLossServiceTest` to assert **no** change to P&L numbers when shadow recording is on
- [ ] Run against real data; review the deltas

### ✅ Phase B gate
- [ ] Existing `PortfolioServiceTest`, `ProfitAndLossServiceTest`, `TradeMatchingServiceTest` unchanged and green
- [ ] Reconciliation deltas reviewed and explained
- [ ] **Discuss before starting Phase C**

---

# PHASE C — cutover

## Chunk 10 — Make the engine authoritative

- [ ] `app.charges.authoritative=true` path: `assetEntity.setBrokerCharges(computation.total())`
- [ ] `PortfolioService.buyStock` (`:311`) — **move charge computation ahead of the entity mutation**
- [ ] Same for `buyStockV2`, `sellStockV2`, `updateQuantityBySavingReportAndProfitAndLoss1`
- [ ] Remove `brokerCharges` from `AssetRequest`; add `userChargeId` to `TransactionEntity`
- [ ] Promote `TradeSegment` into `portfolio/dto/enums`; add to `AssetRequest`, `TransactionEntity`, `AssetEntity` (default `DELIVERY`)
- [ ] Re-verify `toTradeOutcomeContext` pro-rating (`:569`) across partial sells with computed charges
- [ ] **Retire `ProfitAndLossService.updateProfitAndLoss(UserMail, ProfitAndLossContext)`** — deprecated
      `forRemoval`, and `PortfolioService` (`:507`, the V1 sell path) is its last production caller.
      Its tests are right to call it while it ships, so this is a migration, not a warning to silence.
      Surfaced as CI annotations once `setup-java@v6` added a javac problem matcher; pre-existing on
      `master`, and out of bounds for Phase A because it is `portfolio/` work
- [ ] Rewire `RealisedProfits` to `YearlyChargeSummary`; `ProfitAndLossService.updateBrokerCharges` (`:507`) → a single `merge` call
- [ ] Retire `AssetManagementDetails` in favour of `charge_accounts`

## Chunk 11 — Delete the old implementation

- [ ] `entity/BrokerCharges.java`, `entity/UserBrokerCharges.java`
- [ ] `service/BrokerChargeService.java`, `service/UserBrokerChargeService.java`
- [ ] `repository/BrokerChargesRepository.java`, `repository/UserBrokerChargesRepository.java`
- [ ] `dto/request/BrokerChargesRequest.java`, `dto/helper/BrokerageChargesDto.java`, `dto/context/BrokerChargeContext.java`
- [ ] `dto/enums/BrokerChargeTransactionType.java` (fixes D5), `dto/enums/BrokerageAggregatorType.java`
- [ ] `portfolio/entity/model/BrokerageCharges.java`
- [ ] `portfolio/entity/model/BrokerChargesReport.java`, `YearlyBrokerCharges.java`, `MonthlyBrokerCharges.java`
- [ ] `controller/BrokerChargesController.java`, `controller/UserBrokerChargesController.java`
- [ ] `BrokerChargeServiceTest`, `UserBrokerChargeServiceTest`
- [ ] Drop the `broker_charges` and `user_broker_charges` collections
- [ ] `grep -rn "BrokerCharges\|brokerCharge" backend/src` returns nothing unintended

## Chunk 12 — Final verification

- [ ] `./mvnw clean test verify` → `test-report/target/consolidated-test-report.html`
- [ ] `grep -l 'failures="[1-9]"\|errors="[1-9]"' backend/target/surefire-reports/TEST-*.xml` prints nothing
- [ ] `WealthLensModulithTest.modulithStructureIsValid()` green (AC-11)
- [ ] `./mvnw spotless:apply`

---

## Acceptance criteria sign-off *(from PRD §6)*

Ticked only where something actually asserts it. The evidence is named so the claim can be checked rather than taken on trust.

- [x] **AC-1** new charge = JSON only, no Java *(A)* — `ChargeExtensibilityTest`. `SYNTHETIC_LEVY_FOR_TEST` exists in a catalogue row and a rate card and nowhere in Java; the three tests assert it is computed, recorded and aggregated, that a `DERIVED` rule can name it in its base, and that repricing it applies only after the boundary
- [ ] **AC-2** equity delivery buy matches a real contract note to ₹0.01 *(A)* — **blocked by design, and deferred by decision.** Golden fixtures pin the arithmetic against placeholder rates; only a human comparing them to a broker's published page can close this (ADR-18). The owner has scheduled that for **staging, after this branch merges to `master`** — it does not gate the merge, and this box stays unticked until it is actually done there
- [x] **AC-3** sell: STT sell-side, DP once, no stamp duty *(A)* — golden `zerodha-equity-delivery-sell-100k`
- [x] **AC-4** second sell same scrip same day → no second DP *(A)* — golden `zerodha-equity-delivery-sell-second-same-day`, plus `ScopedFlatChargeCalculatorTest`
- [x] **AC-5** GST base excludes STT and stamp duty *(A)* — golden `zerodha-equity-delivery-sell-d1-regression`, `DerivedChargeCalculatorTest`, and a Tier F property over generated cards
- [x] **AC-6** MF exit load applies only under the holding-period predicate *(A)* — golden `zerodha-mutual-fund-sell-spanning-the-exit-load-window` prices a redemption drawn from two lots of different ages through the shipped profile and charges the younger one alone; `…-liquid-within-the-load-period` and `…-liquid-after-the-load-period` are the predicate's two sides. The zero-charge fixture asserts `NO_MATCHING_RULES` rather than only ₹0, so a profile that failed to load fails it as `NO_INSTRUMENT_PROFILE` instead of passing quietly — verified by hiding the profiles and watching all three go red. `ChargesIntegrationTest` repeats it over a real document, where `perLot` and `slabBandBasis` are fields a mapping could drop
- [x] **AC-7** intraday: STT sell-only, no DP, intraday stamp rate *(A)* — golden `zerodha-equity-intraday-sell-100k` and `zerodha-equity-intraday-buy-100k`
- [x] **AC-8** publishing supersedes the incumbent schedule *(A)* — `ChargeScheduleServiceTest`
- [x] **AC-9** invalid rate card rejected at seed with a readable message *(A)* — `ChargeSeederServiceTest`, and `ChargeScheduleValidatorTest` asserts the messages themselves
- [ ] **AC-10** cost basis uses the computed total *(C)* — Phase C, not started
- [x] **AC-11** modulith verification green *(A, B, C)* — `WealthLensModulithTest`, green throughout
- [x] **AC-12** no schedule match → empty computation + WARN, no exception *(A)* — `ChargeEngineTest` and `ChargeScheduleResolverTest`, the warning asserted through `LogCapture`

---

## Known traps

1. **Surefire `testFailureIgnore=true`** — always grep the XML; the exit code lies.
2. **`cleanDatabase()`** — `charge_catalogue` is registered as seeded reference data, because `ChargeSeederService` is `@PostConstruct` and would never re-run. `charge_schedules` deliberately is **not**: tests assert over the cards they create, and shipped cards in the same collection would make those assertions depend on which class ran first. `ChargeRepositoryIntegrationTest` clears it itself. Chunk 9 must still register `user_charges` and `charge_accounts`.
3. **`buyStock` ordering** *(Phase C)* — the charge must be computed before the lot is mutated, or the cost basis is stale.
4. **Integration tests need Docker** (Testcontainers `mongo:7.0` replica set); `*IntegrationTest` matches the default Surefire includes, so a plain `./mvnw test` starts a container.
5. **`ResponseWrapperAdvice` is disabled under the `integration-test` profile** — those tests assert unwrapped payloads.
6. **Rounding order** — round once, at the end of modifier application, never inside a calculator, or GST drifts by paise against real contract notes.
7. **`double` drift** — GST is a percentage of a sum of already-rounded lines. Compute in `BigDecimal` internally or golden files will be flaky and tolerances will creep until they mean nothing.
8. **Phase A scope leak** — if anything under `portfolio/` changes before Phase B, the isolation guarantee is gone and the cutover stops being reversible.
