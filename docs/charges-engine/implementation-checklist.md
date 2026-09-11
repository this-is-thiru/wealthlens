# Charges Engine — Implementation Checklist

**Purpose:** the build tracker. If context is lost mid-implementation, resume from the first unticked box.
**Read first:** `README.md` (where things stand), then `decisions.md` (why), `tech-spec.md` (what), `test-plan.md` (how it is verified). This file is *only* the sequence.

**Branch:** `feature/charges-engine`
**Status:** **All twelve chunks done.** Phases A, B and C complete; the superseded implementation is deleted and its collections dropped. **One technical item is outstanding — `toTradeOutcomeContext` pro-rating, see Chunk 10b — and two are open by decision.**
**Last updated:** 2026-09-11 — 877 tests green across both tiers, `spotless:check` clean, both JaCoCo gates passing, 99% mutation score (597/598) across the engine and the charges services.

Everything that was once deliberately left unticked is now closed:

| Box | Outcome |
|---|---|
| ~~`ChargeEngineProperties` (Chunk 3)~~ | **Done** in Chunk 8; it is also a real kill switch since ADR-30 |
| ~~AMC rate card (Chunk 6)~~ | **Done 2026-09-08.** `ZERODHA_MAINTENANCE_2025_04`, unscoped so the cycle can resolve it |
| ~~Rate verification (Chunk 6)~~ | **Done 2026-09-08.** All eleven cards verified; AC-2 closed |
| ~~`BrokerageAggregatorType` deletion (Chunk 1)~~ | **Done** in Chunk 11 with the rest of the cluster |

**What is genuinely still open** — three things, and only the first is code:

1. **`toTradeOutcomeContext` pro-rating (`PortfolioService:621`).** The buy side pro-rates
   `assetEntity.getBrokerCharges()`, which under `authoritative` is the engine's figure — correct.
   The **sell** side pro-rates `assetRequest.getBrokerCharges()`, the deprecated user-entered field,
   which is not. So a trade outcome carries a computed buy-side charge beside a user-entered
   sell-side one, and since that field holds ₹0.01 in practice, `trade_outcomes.sell_broker_charges`
   is effectively zero. **This was on the original Chunk 10 list and was missed** when the chunk was
   split into 10a and 10b.
2. **`YearlyChargeSummary` accumulates** rather than being derived from `user_charges`, so
   reprocessing one trade twice counts it twice. Recorded, accepted, and worth revisiting now that
   the old hierarchy it matched is gone.
3. **Deriving the summary from `user_charges`** would fix (2) and give per-broker and per-asset-type
   breakdowns the summary cannot express today.

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

*This section described the position until 2026-09-08 and is kept because the reasoning still applies to any newly authored card.*

Golden contract-note fixtures (test-plan Tier E) originally asserted against **fictional numbers**. They still did real work — they pinned the engine's arithmetic, rounding and GST base — but they did not prove the output matched a real contract note.

**Resolved 2026-09-08.** Every rate was compared against the brokers' published pages, the fixtures were recomputed from the corrected cards, and AC-2 closed. What the episode showed, and what applies to the next card anybody writes:

- Replacing rates changed **JSON only**, exactly as predicted. No Java moved.
- The two brokerage defects were invisible at ₹1,00,000 because a ₹20 cap binds whatever the percentage says. **A fixture at one trade size tests one trade size.** Small-trade fixtures now exist for both brokers.
- Two rates had changed *inside* the shipped window, so the correction was successor cards rather than edits — the temporal model's first real use.
- `verifiedOn` is the mechanism: null puts a card on `GET /charge-schedules/unverified`, and Tier G now asserts it is *populated* rather than null.

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
- [x] Delete `BrokerageAggregatorType` — **done in Chunk 11**

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

- [x] `config/ChargeEngineProperties.java` — **built in Chunk 8**, and made a real kill switch in ADR-30
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
- [x] **Seeding moved off startup (ADR-27)** — the repository owner's proposal while resolving ADR-26: an authenticated `POST /charges/seed` rather than `@PostConstruct`, so a deployment never writes rate cards as a side effect of booting and every seeded document names who asked. Building it turned up that seeded cards carried no audit metadata at all: Jackson constructs them through Lombok's `@AllArgsConstructor`, which leaves `auditMetadata` null, and Spring Data's auditing fills that object rather than creating one. Parsing into a pre-built instance fixes it, and nothing assigns an audit field by hand. AC-9's guarantee moved from startup to the endpoint and did not weaken: `ChargeSeederServiceTest` validates every shipped file at build time, which was always the stronger check
- [x] **Rate-card lifecycle for production (ADR-26)** — raised by the repository owner while reviewing the AC-2 work: seeding is idempotent by `scheduleCode`, so an edited file never reaches a database that already seeded it, and nothing said so. The rule is that a deployed card is superseded rather than edited, which the generation-stamped `scheduleCode` already supports. Two guards added: the seeder warns when a card on file differs from its shipped file and `GET /charge-schedules/drift` lists the differences, and `findUnverified()` now ages out a `verifiedOn` after 90 days so the worklist refills. The third piece, `POST /charges/recompute`, is designed in tech-spec §14.4 and belongs with Phase C
- [x] `ChargeSimulationRequest.lots` — added so AC-6 is visible from the API and not only from the suite. Without it `ChargeSimulationService` passed an empty lot list and a `perLot` rule evaluated zero times, so `/charges/simulate` answered ₹0 exit load whatever the profile said. A lot set that does not account for its disposal, carries no `acquisitionDate`, or postdates the trade is rejected rather than priced: each of those makes the charge *smaller* rather than making the call fail, which is the failure mode this endpoint must not have
- [x] `service/ChargeSeederService.java` — `@PostConstruct`, catalogue first, idempotent by code, validates before persisting, **fails fast** on a bad card
- [x] `ChargeSeederServiceTest` — test-plan Tier G, 14 cases against the real files
- [x] `ChargeGoldenFileTest` + fixtures — test-plan Tier E, 12 contract notes including the D1 regression fixture, verified non-vacuous
- [x] ⚠️ Every rate verified against the broker's live charges page; `sourceUrl` + `verifiedOn` filled — **done 2026-09-08.** Tier G's assertion was inverted with it: it required a null `verifiedOn` on every card, which was correct until the day it was not. `noShippedRuleStillCallsItselfAPlaceholder` now fails the build if a rule calls itself a placeholder on a card claiming to be verified

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

- [x] **AC-1 through AC-9 and AC-12 pass** — every one signed off with named evidence below,
      **including AC-2 and AC-6, both closed on 2026-09-08.** AC-6 by seeding the two scheme
      profiles; AC-2 by verifying all eleven cards against the brokers' published pages. Every Phase A
      acceptance criterion is now signed off with named evidence
- [x] **Line coverage ≥ 90%, branch ≥ 85%** — both JaCoCo rules pass under `mvn verify`
- [x] **Mutation score ≥ 85% on `brokercharges.engine.**`** — **99%**, 256 of 257 mutants killed, 0
      uncovered. The charges services score 99% on the same scoping (211/212); the aggregate
      `-Pmutation` invocation still fails on the deferred `taxplanning` score, which is why the
      scoped command in `README.md` §6 is the one that gates this work
- [x] **All golden contract notes pass at ₹0.01** — 12 fixtures, asserted line by line and in total
- [x] **`git diff master --stat -- .../portfolio/` is empty** — re-checked at the end of Chunk 9
- [x] **`WealthLensModulithTest.modulithStructureIsValid()` green**
- [x] **801 tests green across both tiers** (744 at the end of Chunk 9; +56 from the AMC card, the
      scheme profiles, simulate's FIFO lots, the AC-2 rate corrections, ADR-26's two guards and
      ADR-27's seed endpoint), surefire XML gate clean, `spotless:check` clean
- [x] **Discuss results before starting Phase B** — done 2026-09-09. Two decisions came out of it: Phase B proceeds, and ADR-28 (the EQUITY gate stays, V2 only)

---

# PHASE B — shadow recording

## Chunk 8 — Parallel flow, no behaviour change

- [x] `config/ChargeEngineProperties.java` — `app.charges.{engine-enabled,shadow-recording,authoritative}`, declared in all three profile yamls with shadow recording **off**
- [x] `portfolio/service/ChargeRecordingGateway.java` — interface owned by `portfolio`, returns `Optional<ChargeComputation>`
- [x] `brokercharges/service/ChargeRecordingGatewayImpl.java` — computes + persists when `app.charges.shadow-recording=true`. Every failure is caught and logged: a trade must not fail to save because its shadow copy could not be priced
- [x] Inject into `ProfitAndLossService`; **return value ignored** — cost basis untouched
- [x] ~~Remove the `assetType == EQUITY` gate~~ — **reversed, ADR-28.** The gate guards the *superseded* implementation, which resolves a rate card by broker and date with no asset-type dimension; removing it would price a mutual fund as equity and write that into the P&L, which Chunk 8's own gate forbids. The shadow call goes **outside** the gate instead, so every asset type reaches the engine (FR-8) and nothing else changes. Gate removal moved to Chunk 10
- [x] Wired into the **V2** flow only, per the repository owner: V1 `buyStock`/`sellStock` is **in live use** and must not be touched — V2 is built out beside it. The two `updateProfitAndLoss` overloads are distinct methods, so this is exact — `ProfitLossContext` (V2) is instrumented, the `@Deprecated(forRemoval = true)` `ProfitAndLossContext` overload is untouched
- [x] `GET /user-charges/user/{email}/reconciliation` — computed vs user-entered per transaction, with delta. Rows the engine could not price, and rows whose transaction is gone, are listed with a note and **excluded from the totals**
- [x] `ChargeRecordingGatewayImplTest` (15), `ChargeReconciliationServiceTest` (7); `ProfitAndLossServiceTest` extended 11 → 15 with no existing assertion changed
- [x] `ShadowRecordingIntegrationTest` (4) — the only class running with the flag on, so the other integration classes staying green is itself the evidence recording is opt-in. Four reconciliation cases added to `ChargesIntegrationTest` (33 → 37)
- [x] Run end to end against a running application — done 2026-09-09 against a local replica set with the shipped seed data and `shadow-recording=true`. Four V2 trades through `POST /portfolio/user/{email}/transaction/v2`; every runbook §5b command run verbatim and its answer recorded as the baseline in §5b.5b. This found two documentation defects (below) and confirmed the mutual-fund coverage gap shows up as a real number
- [x] `service/ChargeBackfillService` + `POST /charges/backfill/user/{email}` (`SUPER_USER`) — **the missing piece.** Shadow recording only fires on trades after the flag goes on, so an existing database has no computed charges and the reconciliation report comes back empty. This prices the history, reconstructing the FIFO lots a sell consumed by replaying the buys in date order — without them a `perLot` rule evaluates zero times and a redemption inside its exit-load window backfills as free. `computeAndRecordBatch` had been built in Chunk 5 and had no caller until now. 16 unit tests, 5 integration
- [x] Run against real data — done 2026-09-09 on `it-staging`, 319 transactions spanning 2023-06-22 to 2026-01-12. Backfilled and reconciled; findings in `phase-b-reconciliation-findings.md`

### ✅ Phase B gate
- [x] Existing `PortfolioServiceTest`, `ProfitAndLossServiceTest`, `TradeMatchingServiceTest` green with no existing assertion changed. `ProfitAndLossServiceTest` gained a `@Mock` field and four tests; every pre-existing method is byte-identical
- [x] 836 tests green across both tiers, surefire XML gate clean, `spotless:check` clean, both JaCoCo gates passing, **99% mutation score** (538/539 — the survivor is `ChargeFormulaEvaluator`'s known equivalent mutant; every Chunk 8 class is at 100%)
- [x] `WealthLensModulithTest` green — `portfolio` → `brokercharges` and `brokercharges` → `portfolio` are both already declared
- [x] Reconciliation deltas reviewed and explained — done, **with the finding that there is no baseline to explain them against.** Entered broker charges total ₹5.32 across 49 comparable trades (37 of them exactly ₹0.01), so the ₹235.30 delta is very nearly the whole computed total measured against a field nobody filled in. The engine was therefore verified independently instead: a resolution breakdown predicted before the run and matched exactly (227 `NO_SCHEDULE` / 92 in-window), one contract note checked line by line to the paisa, and AC-4 deduplication proven on three same-day sells of one scrip. See `phase-b-reconciliation-findings.md` §4
- [x] **Gate amended — ADR-31, decided 2026-09-09.** The comparison PRD OD-8 asked for was made and returned no usable signal: the manual field was never populated. Phase B closes on that finding rather than waiting for data nobody captured. PRD OD-8 is amended in place, not deleted — running Phase B is what produced the finding
- [x] **Discussed before starting Phase C** — 2026-09-09. **Phase B is closed.** Carry two things into Chunk 10: (1) cost basis moves for *every* trade once `authoritative` flips, from effectively zero charges to real ones — user-visible in realised P&L and to be announced, not discovered; (2) there is no numerical baseline to diff the cutover against, so correctness rests on the golden fixtures, the invariants and ADR-31's verification

---

# PHASE C — cutover

## Chunk 10a — Make the engine authoritative for cost basis *(done 2026-09-09)*

**V2 only**, at the repository owner's direction: `buyStock`/`sellStock` are **in live use** and must
not be touched, so `buyStockV2` no longer shares `updateBrokerChargesAndProfitAndLoss` with V1.

- [x] `app.charges.authoritative=true` path: `assetEntity.setBrokerCharges(computation.total())` (AC-10)
- [x] `PortfolioService.buyStockV2` computes the charge **before** the lot is written, and the ordering
      is asserted with `InOrder` rather than inferred from the value
- [x] The engine runs **once** per trade: `buyStockV2` prices it and hands the result to
      `ProfitAndLossService.updateProfitAndLoss(userMail, context, precomputed)`, a new overload that
      uses what it is given instead of asking the gateway again. The 2-arg version is unchanged, so V1
      behaves exactly as before
- [x] An absent computation **leaves the entered figure alone** rather than zeroing it — no card for the
      period, the kill switch, or a scheme with no profile all say nothing about whether the trade cost
      anything, and a real cost overwritten with zero is worse than an estimate
- [x] `brokerCharges` stays on `AssetRequest` (owner's decision): clients keep sending it and keep
      working, it simply stops being read once `authoritative` is on. Removal is a later release
- [x] 5 tests in `PortfolioServiceTest` (6 → 11), every pre-existing one unchanged. Verified non-vacuous
      by breaking the assignment and watching `expected: <118.74> but was: <125.5>`

**Only the buy path.** On a sell the computed charge belongs to realised P&L, not to the holding's
cost basis, so AC-10 is a buy-path criterion. The sell side is Chunk 10b's report rewiring.

## Chunk 10b — The rest of the cutover *(part 1 done 2026-09-09)*

- [x] **Cut 1 — the superseded implementation is no longer called from the trade path** *(2026-09-09)*.
      The `assetType == EQUITY` block is gone from both handlers, so neither V1 buy nor V2 reaches
      `UserBrokerChargeService`. ADR-28 deferred this to Chunk 11 on the reasoning that removing it
      would let the old path price a mutual fund as equity — true, but moot: with no rate-card
      template `addUserBrokerChargeEntry` returns null, and production holds **zero**
      `yearly_broker_charges` documents, so the block has never written anything there. **It is not
      dead everywhere** — the integration tests seed `broker_charges` and did create rows, so any
      environment with templates was doing real work through it. Two integration assertions are
      inverted rather than deleted, so the removal stays pinned
- [x] **The computed charge reaches realised P&L** *(done 2026-09-09)*. `RealisedProfits` gains
      `yearlyChargeSummary` **beside** `yearlyBrokerCharges`; Chunk 11 deletes the old one. Written
      only when a computation is **passed in**, which is V2 — V1 reaches the two-arg overload, prices
      through the gateway exactly as before and writes no summary, so its stored data is unchanged
- [x] V2 sell prices first and hands the computation on, matching what 10a did for the buy. The four
      call sites now map exactly: V1 buy → shared helper → 2-arg (no summary); V1 sell → deprecated
      `ProfitAndLossContext` overload (untouched); V2 buy and V2 sell → 3-arg with a computation
- [x] Account split mirrors the old report — `SELF` into `realisedProfits`, anything else into
      `outSourcedRealisedProfits` — so the two can be compared bucket for bucket while both are written
- [x] The hidden `precomputedCharge` field introduced in 10a is gone; the computation is threaded as a
      parameter through `dispatch` and both handlers
- [ ] **Known limit, deliberately accepted:** the summary *accumulates*, so reprocessing one trade
      twice counts it twice. The hierarchy beside it has always had that property. Deriving the summary
      from `user_charges` instead would be idempotent by construction — **revisit when Chunk 11 deletes
      the old hierarchy**, since that is when `ChargeSummaryReport.merge` stops having a peer to match
- [x] `TradeSegment` promoted into `portfolio/dto/enums`; added to `AssetRequest`, `TransactionEntity`,
      `AssetEntity`, all defaulting `DELIVERY`. Both persisted uses store the enum's *name*, so the
      package move needed no data migration. The gateway and the backfill now read the trade's own
      segment instead of assuming delivery
- [x] Deprecate `AssetRequest.brokerCharges` in the DTO (kept, not removed — see 10a)
- [x] **A legacy document reads back as `DELIVERY`, and it is pinned by a test.** ADR-27 is the
      standing reminder that a field initialiser is not a guarantee — Lombok's `@AllArgsConstructor`
      carries `@ConstructorProperties`, and a mapper choosing it passes null and never runs the
      initialiser, which is exactly how seeded rate cards lost their audit metadata. A null segment
      would disqualify every pre-Chunk-10b trade from resolving a card
- [x] ~~`userChargeId` on `TransactionEntity`~~ — **dropped, decided 2026-09-09.** The link already
      exists and is load-bearing: `{email, transactionId}` is unique on `UserChargeEntity`, which is
      what makes `record` an upsert, the backfill re-runnable, and the reconciliation join work

- [x] Remove the `assetType == EQUITY` gate at `ProfitAndLossService` — **done as Cut 1**, ahead of
      Chunk 11 rather than with it, once production's zero `yearly_broker_charges` documents showed
      the block had never written anything there
- [x] Move charge computation ahead of the entity mutation — **done for `buyStockV2`**, asserted with
      `InOrder`. ~~`PortfolioService.buyStock` (`:311`)~~ and ~~`sellStock`~~ are **void by decision**:
      V1 is in live use and was never to be touched
- [x] `sellStockV2` / `updateQuantityBySavingReportAndProfitAndLoss1` price first and hand the
      computation on, so the engine runs once per trade
- [ ] **`toTradeOutcomeContext` pro-rating (`:621`) — OUTSTANDING, and it is a real gap.** The buy side
      pro-rates `assetEntity.getBrokerCharges()`, which under `authoritative` is the computed figure.
      The sell side pro-rates `assetRequest.getBrokerCharges()`, the deprecated user-entered field —
      so a trade outcome mixes a computed buy-side charge with a user-entered sell-side one, and that
      field holds ₹0.01 in practice. `trade_outcomes.sell_broker_charges` is therefore ~0 under
      `authoritative`. **Missed when Chunk 10 was split into 10a and 10b**; found auditing this file
      on 2026-09-11
- [x] ~~Remove `brokerCharges` from `AssetRequest`~~ — **decided against.** Deprecated and kept, so
      existing clients keep working; it is simply no longer read once `authoritative` is on
- [x] ~~Add `userChargeId` to `TransactionEntity`~~ — **decided against.** `UserChargeEntity` already
      carries `transactionId` under a unique index on `{email, transaction_id}`, which is what makes
      `record` an upsert, the backfill re-runnable and the reconciliation join work. A reverse pointer
      would be a second source of truth for one relationship
- [x] Promote `TradeSegment` into `portfolio/dto/enums` and onto `AssetRequest`, `TransactionEntity`,
      `AssetEntity` and `ProfitLossContext`, defaulting `DELIVERY` — **done in 10b part 1**
- [ ] ~~Retire `ProfitAndLossService.updateProfitAndLoss(UserMail, ProfitAndLossContext)`~~ — **void by
      decision.** V1 `sellStock` is its only caller and V1 is in live use, so the overload stays
      deprecated rather than being removed
- [x] Rewire `RealisedProfits` to `YearlyChargeSummary` — **done in 10b part 2**, and since Chunk 11
      it is the only charge hierarchy a P&L document carries
- [x] **Retire `AssetManagementDetails`** *(2026-09-09)*. No migration: it was never in production, so
      the entity, its repository, `AssetManagementService` and `AssetManagementDetailsRequest` are
      simply deleted. `ChargeAccountController` already served the whole replacement surface —
      `POST`/`GET /charge-accounts/user/{email}` and `POST /charges/amc/impose` — so there was nothing
      to repoint and no response-shape change to negotiate

## Chunk 11 — Delete the old implementation *(done 2026-09-09)*

Twenty-five files, in one pass, once Cut 1 and the AMC retirement left the cluster unreferenced.

- [x] `BrokerCharges`, `UserBrokerCharges`, their services, repositories, controllers and DTOs
- [x] `BrokerChargeTransactionType` (fixes D5), `BrokerageAggregatorType`, `BrokerageCharges`
- [x] `BrokerChargesReport`, `YearlyBrokerCharges`, `MonthlyBrokerCharges`, and
      `RealisedProfits.yearlyBrokerCharges` — the embedded report is gone, and `YearlyChargeSummary`
      is now the only charge figure a P&L document carries
- [x] `AssetManagementService`, `AssetManagementDetails`, its repository and request DTO
- [x] 106 lines of charge machinery out of `ProfitAndLossService`, including
      `updateProfitAndLossWithAmcCharges` and the whole `updateBrokerChargesReport` family
- [x] Their tests: `BrokerChargeServiceTest`, `UserBrokerChargeServiceTest`,
      `AssetManagementServiceTest`, `BrokerChargesIntegrationTest`
- [x] The six superseded requests and their folder, in `api-collection`
- [x] **Dropped the `broker_charges`, `user_broker_charges` and `asset_management_details`
      collections** — done by the repository owner, 2026-09-11
- [x] No live code references the deleted cluster; the only deprecation warnings left are the two
      deliberate ones (`AssetRequest.brokerCharges`, the V1-sell `ProfitAndLossContext` overload) and
      two pre-existing library deprecations

**What survives, deliberately:** V1 `buyStock`/`sellStock` — in live use and untouched throughout —
and the deprecated `ProfitAndLossContext` overload V1 sell depends on.

## Chunk 12 — Final verification *(done 2026-09-11)*

- [x] `./mvnw clean test verify` → **877 tests, 877 passed, 0 failed**, consolidated report generated
- [x] `grep -l 'failures="[1-9]"\|errors="[1-9]"' backend/target/surefire-reports/TEST-*.xml` prints nothing
- [x] `WealthLensModulithTest.modulithStructureIsValid()` green (**AC-11**)
- [x] `./mvnw spotless:check` clean
- [x] Both JaCoCo gates pass
- [x] **Mutation 99% — 597/598.** The one survivor is `ChargeFormulaEvaluator`'s known equivalent
      mutant. The scoped invocation no longer needs the caveat that `brokercharges.service.*` sweeps
      in the superseded services: they are deleted, so the figure now describes only this work

**Mutation testing earned its keep once more at the end.** It found the two guards added in the
schema pass — `ChargeCodes` and `ChargeScheduleWindows` — were exercised directly but never asserted
to be *called*, so deleting either call site left the suite green and the validation silently gone.
Two tests now assert the wiring rather than the rule.

**The Phase A isolation rule is now deliberately broken**, which is what Phase C means:
`git diff master --stat -- .../portfolio/` shows 16 files, +289/−409. It was empty through Phases A
and B, and the net negative is the superseded implementation leaving.

---

# Beyond this branch

Two gaps found by the Phase B run against real data are **not** charges-engine work and have been
lifted out of this tracker into an epic of their own:
**[`../epics/priced-portfolio.md`](../epics/priced-portfolio.md)**.

They are instrument identity (ADR-29) and historical rate coverage. Neither is a follow-on ticket
against this checklist — at the repository owner's direction they ship together, as one
comprehensive release, after Phase C merges. Do not start them from here.

---

## Acceptance criteria sign-off *(from PRD §6)*

Ticked only where something actually asserts it. The evidence is named so the claim can be checked rather than taken on trust.

- [x] **AC-1** new charge = JSON only, no Java *(A)* — `ChargeExtensibilityTest`. `SYNTHETIC_LEVY_FOR_TEST` exists in a catalogue row and a rate card and nowhere in Java; the three tests assert it is computed, recorded and aggregated, that a `DERIVED` rule can name it in its base, and that repricing it applies only after the boundary
- [x] **AC-2** equity delivery buy matches a real contract note to ₹0.01 *(A)* — **closed 2026-09-08**, on this branch rather than post-merge. Every rate compared against Zerodha's, Upstox's and Fyers' published pages; five defects found and fixed, five successor cards added for the NSE transaction-charge revision of 2026-03-01 and Zerodha's depository-fee cut, and all eleven cards carry `verifiedOn`. Evidence in `ac2-rate-verification.md`; the two brokerage errors were both invisible at ₹1,00,000 and are now pinned by small-trade fixtures
- [x] **AC-3** sell: STT sell-side, DP once, no stamp duty *(A)* — golden `zerodha-equity-delivery-sell-100k`
- [x] **AC-4** second sell same scrip same day → no second DP *(A)* — golden `zerodha-equity-delivery-sell-second-same-day`, plus `ScopedFlatChargeCalculatorTest`
- [x] **AC-5** GST base excludes STT and stamp duty *(A)* — golden `zerodha-equity-delivery-sell-d1-regression`, `DerivedChargeCalculatorTest`, and a Tier F property over generated cards
- [x] **AC-6** MF exit load applies only under the holding-period predicate *(A)* — golden `zerodha-mutual-fund-sell-spanning-the-exit-load-window` prices a redemption drawn from two lots of different ages through the shipped profile and charges the younger one alone; `…-liquid-within-the-load-period` and `…-liquid-after-the-load-period` are the predicate's two sides. The zero-charge fixture asserts `NO_MATCHING_RULES` rather than only ₹0, so a profile that failed to load fails it as `NO_INSTRUMENT_PROFILE` instead of passing quietly — verified by hiding the profiles and watching all three go red. `ChargesIntegrationTest` repeats it over a real document, where `perLot` and `slabBandBasis` are fields a mapping could drop
- [x] **AC-7** intraday: STT sell-only, no DP, intraday stamp rate *(A)* — golden `zerodha-equity-intraday-sell-100k` and `zerodha-equity-intraday-buy-100k`
- [x] **AC-8** publishing supersedes the incumbent schedule *(A)* — `ChargeScheduleServiceTest`
- [x] **AC-9** invalid rate card rejected at seed with a readable message *(A)* — `ChargeSeederServiceTest`, and `ChargeScheduleValidatorTest` asserts the messages themselves. Since ADR-27 the rejection surfaces from `POST /charges/seed` rather than from startup; the build-time check against the real shipped files is unchanged and is the one that matters
- [x] **AC-10** cost basis uses the computed total *(C)* — `PortfolioServiceTest.buyStockV2_whenAuthoritative_setsCostBasisFromTheComputedTotal`, and the ordering pinned with `InOrder` so the charge is computed before the lot is written. Behind `app.charges.authoritative`, which ships `false`
- [x] **AC-11** modulith verification green *(A, B, C)* — `WealthLensModulithTest`, green throughout
- [x] **AC-12** no schedule match → empty computation + WARN, no exception *(A)* — `ChargeEngineTest` and `ChargeScheduleResolverTest`, the warning asserted through `LogCapture`

---

## Known traps

1. **Surefire `testFailureIgnore=true`** — always grep the XML; the exit code lies.
2. **`cleanDatabase()` and the catalogue — every charges integration class must seed for itself.** `charge_catalogue` is whitelisted to survive `cleanDatabase()`, and this entry used to justify that with "`ChargeSeederService` is `@PostConstruct` and would never re-run". **That stopped being true at ADR-27**, which made seeding an explicit call — so nothing populates the catalogue unless a test asks, and a class that does not ask sees whatever an earlier class happened to leave. `ChargeExtensibilityTest` depended on that and passed for weeks; adding two integration classes in Chunk 10 changed the order and CI failed with `rule GST is not in the charge catalogue`. Seeding is idempotent by code, so call `chargeSeederService.seed(...)` in `@BeforeEach` and clear the shipped cards afterwards. **Check a new integration class passes on its own**, not just in a full run.
   `charge_schedules` is deliberately **not** whitelisted: tests assert over the cards they create, and shipped cards in the same collection would make those assertions depend on which class ran first.
3. **`buyStock` ordering** *(Phase C)* — the charge must be computed before the lot is mutated, or the cost basis is stale.
4. **Integration tests need Docker** (Testcontainers `mongo:7.0` replica set); `*IntegrationTest` matches the default Surefire includes, so a plain `./mvnw test` starts a container.
5. **`ResponseWrapperAdvice` is disabled under the `integration-test` profile** — those tests assert unwrapped payloads.
6. **Rounding order** — round once, at the end of modifier application, never inside a calculator, or GST drifts by paise against real contract notes.
7. **`double` drift** — GST is a percentage of a sum of already-rounded lines. Compute in `BigDecimal` internally or golden files will be flaky and tolerances will creep until they mean nothing.
8. **Phase A scope leak** — if anything under `portfolio/` changes before Phase B, the isolation guarantee is gone and the cutover stops being reversible.
