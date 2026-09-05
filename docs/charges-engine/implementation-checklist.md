# Charges Engine — Implementation Checklist

**Purpose:** the build tracker. If context is lost mid-implementation, resume from the first unticked box.
**Read first:** `README.md` (where things stand), then `decisions.md` (why), `tech-spec.md` (what), `test-plan.md` (how it is verified). This file is *only* the sequence.

**Branch:** `feature/charges-engine`
**Status:** Chunk -1 merged to master (PR #59). Chunk 0 settled. Chunk 1 in progress.
**Last updated:** 2026-09-05

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
- [ ] **T11** charge-specific test fixture builders — deferred to Chunk 2, when the entities exist
- [ ] **Design:** engine arithmetic in `BigDecimal` internally, `double` only at the persistence boundary (test-plan §2.1)

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

## Chunk 2 — Entities and repositories

- [ ] `entity/ChargeRule.java` (embedded; every field per tech-spec §4.2)
- [ ] `entity/ChargeSlab.java`
- [ ] `entity/ChargeLine.java`
- [ ] `entity/ChargeScheduleEntity.java` — `@Document("charge_schedules")`, `AuditableEntity`
- [ ] `entity/UserChargeEntity.java` — `@Document("user_charges")`, `AuditableEntity`
- [ ] `entity/ChargeCatalogueEntity.java` — `@Document("charge_catalogue")`
- [ ] `entity/ChargeAccountEntity.java` — `@Document("charge_accounts")`
- [ ] `repository/ChargeScheduleRepository.java` — `findCandidates(brokerName, date)` covering open-ended `end_date: null`
- [ ] `repository/UserChargeRepository.java` — `existsBy…` for PER_SCRIP_PER_DAY dedupe (an `exists`, **not** a `List` — fixes D9), `findByEmail`, `deleteByEmail`
- [ ] `repository/ChargeCatalogueRepository.java`
- [ ] `repository/ChargeAccountRepository.java`
- [ ] Compound indexes per tech-spec §4.1 / §4.5

---

## Chunk 3 — Engine core

- [ ] `config/ChargeEngineProperties.java` — `@ConfigurationProperties("app.charges")`: `engineEnabled`, `shadowRecording`, `authoritative`, `cacheEnabled`
- [ ] `engine/ChargeAccumulator.java` — holds lines, `sumOf(List<String> codes)`, `amountOf(code)`
- [ ] `engine/ChargeCalculator.java` — the strategy interface
- [ ] `engine/ChargeRounding.java` — `RoundingPolicy` application
- [ ] `engine/ChargeFormulaEvaluator.java` — charges-owned SpEL wrapper (tech-spec §5.6): `evaluate`, `matches`, `validate`
- [ ] `engine/ChargeCalculatorRegistry.java` — `List<ChargeCalculator>` → `Map<ChargeBasis, …>`, fails fast on duplicate or missing basis
- [ ] `dto/context/ChargeContext.java` — record + `forTrade` / `forAmcCycle` factories
- [ ] `dto/context/ChargeComputation.java` — record + `empty()`, `amountOf`, `amountByCode`
- [ ] `engine/ChargeEngine.java` — resolve → filter → sort → dispatch → modifiers → assemble (tech-spec §5.4, §5.5)
- [ ] `ChargeEngineTest` — test-plan Tier B (~30 cases: selection, ordering, assembly, modifier order)
- [ ] `ChargeFormulaEvaluatorTest` — variable exposure, accumulator access, `validate` rejects bad syntax

---

## Chunk 4 — Calculators *(one class each, independently testable)*

**All seven are built.** An earlier draft deferred `PER_UNIT` and `SLAB` as unused. That was wrong on two counts: `SlabBandBasis` exists specifically so graded exit loads can band on holding days, so deferring `SlabChargeCalculator` would leave that enum dead code; and a `ChargeBasis` constant with no registered calculator is a runtime trap for whoever first writes a rule using it. Each is ~30–50 lines.

- [ ] `engine/calculator/TurnoverChargeCalculator.java` — reads `rule.amountBasis()` from `context.baseAmounts()`
- [ ] `engine/calculator/FlatChargeCalculator.java`
- [ ] `engine/calculator/PerUnitChargeCalculator.java` — amount × quantity; per share, or per lot via `lotSize`
- [ ] `engine/calculator/SlabChargeCalculator.java` — bands by `slabBandBasis`: TURNOVER, HOLDING_DAYS or QUANTITY
- [ ] `engine/calculator/ScopedFlatChargeCalculator.java` — dedupe via `UserChargeRepository.existsBy…`
- [ ] `engine/calculator/DerivedChargeCalculator.java` — **sums only `baseCodes`; fixes D1**
- [ ] `engine/calculator/FormulaChargeCalculator.java` — delegates to `ChargeFormulaEvaluator`
- [ ] `ChargeCalculatorRegistry` fails fast at startup if any `ChargeBasis` constant has no calculator
- [ ] One `*CalculatorTest` per calculator, covering min/max/aggregator boundaries and rounding
- [ ] `DerivedChargeCalculatorTest` must include the **STT-excluded-from-GST-base** case that pins D1

---

## Chunk 5 — Resolution, validation, services

- [ ] `service/ChargeScheduleValidator.java` — every FR-2 rule:
  - [ ] duplicate `code` within a schedule
  - [ ] `DERIVED` referencing an unknown `baseCode`
  - [ ] `DERIVED` whose `baseCode` has `order >=` its own
  - [ ] basis missing its required parameter
  - [ ] `rate` + `flatAmount` present without `aggregator` (**fixes D7**)
  - [ ] `formula` / `eligibility` that fails `ChargeFormulaEvaluator.validate`
  - [ ] `code` absent from `charge_catalogue`
- [ ] `service/ChargeScheduleResolver.java` — specificity scoring (tech-spec §6), caching, eviction on write
- [ ] `service/ChargeScheduleService.java` — publish with **auto-supersede** (fixes D6), fetch, list, close
- [ ] `service/UserChargeService.java` — `computeAndRecord`, history queries, `deleteByEmail`
- [ ] `service/ChargeAccountService.java` — CRUD over `charge_accounts`
- [ ] `service/AmcChargeService.java` — AMC cycle billing via `ChargeEvent.AMC_CYCLE`
- [ ] `ChargeScheduleResolverTest` — test-plan Tier C (~18 cases)
- [ ] `ChargeScheduleValidatorTest` — test-plan Tier D (~16 cases, each asserting the *message*)
- [ ] `ChargeScheduleServiceTest`, `AmcChargeServiceTest`
- [ ] `ChargeInvariantTest` — test-plan Tier F (~10 properties over generated schedules)

**Done when:** publishing over an open schedule closes the incumbent rather than throwing (AC-8).

---

## Chunk 6 — Seed data

- [ ] `resources/data/charges/charge-catalogue.json` — BROKERAGE, STT, EXCHANGE_TXN, SEBI_FEE, IPFT, STAMP_DUTY, DP, GST, AMC, ACCOUNT_OPENING, EXIT_LOAD, MF_TXN_FEE
- [ ] `zerodha-equity-delivery-2025-04-01.json`
- [ ] `zerodha-equity-intraday-2025-04-01.json` *(optional — proves the segment dimension works; skip if scope is tight)*
- [ ] `zerodha-mutual-fund-2025-04-01.json` *(optional — proves FORMULA/exit-load works)*
- [ ] `upstox-equity-delivery-2025-04-01.json`
- [ ] `fyers-equity-delivery-2025-04-01.json`
- [ ] `service/ChargeSeederService.java` — `@PostConstruct`, idempotent by `scheduleCode`, validates before persisting, **fails fast** on a bad card
- [ ] `ChargeSeederServiceTest` — test-plan Tier G (~6 cases). **Highest-value test in the plan** — catches a rate-card typo at build time
- [ ] `ChargeGoldenFileTest` + fixtures — test-plan Tier E (~12 contract notes, incl. the D1 regression fixture)
- [ ] ⚠️ Every rate verified against the broker's live charges page; `sourceUrl` + `verifiedOn` filled

---

## Chunk 7 — Charge summary reporting model *(new types, old ones untouched)*

Phase A adds the aggregation shape without rewiring P&L. `ProfitAndLossService` still writes the old `BrokerChargesReport` until Phase C.

- [ ] `entity/model/ChargeSummaryReport.java` (in `brokercharges`) — `Map<String, Double> amountByCode`, `totalCharges`, `merge(Map<String,Double>)`
- [ ] `entity/model/YearlyChargeSummary.java`, `MonthlyChargeSummary.java`
- [ ] `ChargeSummaryReportTest` — merging an unknown code surfaces it with no code change (AC-1)

---

## Chunk 9 — Controllers and API *(still Phase A)*

- [ ] `controller/ChargeScheduleController.java` (tech-spec §10)
- [ ] `controller/ChargeSimulationController.java` — `POST /charges/simulate`, persists nothing
- [ ] `controller/UserChargesController.java` — history + per-transaction contract note
- [ ] `controller/ChargeAccountController.java`
- [ ] `auth/config/AuthConfig` review — schedule admin endpoints must **not** be public
- [ ] `api-collection/` updated with the new endpoints
- [ ] Golden-file fixtures under `src/test/resources/charges/golden/`, authored through the simulate endpoint
- [ ] `ChargesIntegrationTest extends AbstractIntegrationTest`
- [ ] `ChargeExtensibilityTest` — test-plan Tier J (3 cases; makes AC-1 permanent)
- [ ] Tier H persistence/dedupe cases (~14) and Tier I API cases (~12)
- [ ] **Add `charge_schedules`, `user_charges`, `charge_catalogue`, `charge_accounts` to `cleanDatabase()`**

### ✅ Phase A gate
- [ ] AC-1 through AC-9 and AC-12 pass
- [ ] Line coverage ≥ 90%, branch ≥ 85% on `brokercharges.**`
- [ ] **Mutation score ≥ 85% on `brokercharges.engine.**`** — the gate that replaces manual QA of the maths
- [ ] All golden contract notes pass at ₹0.01
- [ ] `git diff master --stat -- backend/src/main/java/com/thiru/wealthlens/portfolio/` is **empty**
- [ ] `WealthLensModulithTest.modulithStructureIsValid()` green
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

- [ ] AC-1 new charge = JSON only, no Java *(A)*
- [ ] AC-2 equity delivery buy matches a real contract note to ₹0.01 *(A)*
- [ ] AC-3 sell: STT sell-side, DP once, no stamp duty *(A)*
- [ ] AC-4 second sell same scrip same day → no second DP *(A)*
- [ ] AC-5 GST base excludes STT and stamp duty *(A)*
- [ ] AC-6 MF exit load applies only under the holding-period predicate *(A)*
- [ ] AC-7 intraday: STT sell-only, no DP, intraday stamp rate *(A)*
- [ ] AC-8 publishing supersedes the incumbent schedule *(A)*
- [ ] AC-9 invalid rate card rejected at seed with a readable message *(A)*
- [ ] AC-10 cost basis uses the computed total *(C)*
- [ ] AC-11 modulith verification green *(A, B, C)*
- [ ] AC-12 no schedule match → empty computation + WARN, no exception *(A)*

---

## Known traps

1. **Surefire `testFailureIgnore=true`** — always grep the XML; the exit code lies.
2. **`cleanDatabase()`** — the four new collections must be registered or integration tests leak state between classes.
3. **`buyStock` ordering** *(Phase C)* — the charge must be computed before the lot is mutated, or the cost basis is stale.
4. **Integration tests need Docker** (Testcontainers `mongo:7.0` replica set); `*IntegrationTest` matches the default Surefire includes, so a plain `./mvnw test` starts a container.
5. **`ResponseWrapperAdvice` is disabled under the `integration-test` profile** — those tests assert unwrapped payloads.
6. **Rounding order** — round once, at the end of modifier application, never inside a calculator, or GST drifts by paise against real contract notes.
7. **`double` drift** — GST is a percentage of a sum of already-rounded lines. Compute in `BigDecimal` internally or golden files will be flaky and tolerances will creep until they mean nothing.
8. **Phase A scope leak** — if anything under `portfolio/` changes before Phase B, the isolation guarantee is gone and the cutover stops being reversible.
