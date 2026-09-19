# 03 — Work items

The ordered backlog. **Do them in order. One at a time. Explain, get approval, code, verify, report,
wait for review.** See `00-START-HERE.md` §1.

`DR` = design reference, always under `docs/taxplanning-design/`.
`TS` = `02-TECH-SPEC.md`. `PRD` = `01-PRD.md`.

Statuses live in `STATE.md`, **not here**. This file is read-only once written.

**On dependencies.** This backlog deliberately encodes **none**. The ordering *is* the dependency
graph: assume every item depends on every item before it. So there is no such thing as "the next
independent item" — when something is `BLOCKED`, **stop and ask** (rule R6). Do not look for
something else to do.

---

## Phase 0 — Make the build accept the design (3 items)

### W-001 — Create the four module skeletons
- [ ] **Produces**
  `backend/src/main/java/com/thiru/wealthlens/taxrules/package-info.java`
  `backend/src/main/java/com/thiru/wealthlens/taxengine/package-info.java`
  `backend/src/main/java/com/thiru/wealthlens/taxdocs/package-info.java`
  and **modify** `backend/src/main/java/com/thiru/wealthlens/taxplanning/package-info.java`
- **Spec** TS T1
- **Do** four `@ApplicationModule(type = Type.CLOSED, allowedDependencies = {...})` declarations, exactly as TS T1 lists them. Add one placeholder class per new package so the package exists.
- **Verify** `./mvnw test -pl backend -Dtest=WealthLensModulithTest`
- **Done when** `modulithStructureIsValid()` passes with four modules declared.

### W-002 — Amend the money ArchUnit rule
- [ ] **Produces** modify `backend/src/test/java/com/thiru/wealthlens/architecture/ArchitectureTest.java`
- **Spec** `DR 19-engineering-foundations.md` E1 · TS T10
- **Do** amend `money_rounding_lives_in_one_place` to also permit `..taxengine.money..`, keeping the existing `because(...)` text and appending the TL-8 §7 justification. Add `taxengine_does_not_use_TMoney`. Add `only_the_adapter_converts`.
- **Verify** `./mvnw test -pl backend -Dtest=ArchitectureTest`
- **Done when** all existing rules still pass and three new rules are present.
- ⚠ **This item exists because the design would otherwise fail the build on the first commit.**

### W-003 — Scratch directory for rule packs
- [ ] **Produces** `backend/src/main/resources/rulepacks/.gitkeep`
- **Do** create the directory only.
- **Verify** `test -d backend/src/main/resources/rulepacks`

---

## Phase 1 — Foundational types (9 items)

### W-004 — The engine ArchUnit rules
- [ ] **Produces** modify `ArchitectureTest.java`
- **Spec** TS T10 rows 4–8 and 11 · `DR 23` A3, A6
- **Do** add: `taxengine` depends on no `..repository..` / `org.springframework.data..` / `..taxplanning..`; no `LocalDate.now` / `Instant.now` / `Clock`; no `catch (Exception|RuntimeException)`; no `BigDecimal` field outside `..taxengine.money..`. Add a test that asserts the string `ComputationStep` appears nowhere under `backend/src`.
- **Verify** `./mvnw test -pl backend -Dtest=ArchitectureTest`

### W-005 — `Money`
- [ ] **Produces** `taxengine/money/Money.java`
- **Spec** **TS T3 — copy it verbatim**
- **Verify** `./mvnw test -pl backend -Dtest=ArchitectureTest` (compiles, rule passes)

### W-006 — `MoneyTest`
- [ ] **Produces** `backend/src/test/java/com/thiru/wealthlens/taxengine/money/MoneyTest.java`
- **Spec** `DR 08-golden-tests.md` T9-14 · `DR 13-edge-cases.md` R-9
- **Do** test: construction rounds to scale 2 · `plus`/`minus` exact · `floorAtZero` · `min`/`max` · **`roundToNearestTenPerSection516`** for 1234 → 1230, 1235 → 1240, 1234.99 → 1230, and the negative of each · null rejected.
- **Verify** `./mvnw test -pl backend -Dtest=MoneyTest`
- ⚠ Expected values come from `DR 01-rulebook.md` `RATE-ROUND-002`. **Do not derive them from your code.**

### W-007 — `Rate` and `RateTest`
- [ ] **Produces** `taxengine/money/Rate.java`, `.../RateTest.java`
- **Spec** TS T3 verbatim
- **Do** test `percent("30").of(Money.of("100"))` is `30.00`; `percent("12.5")`; `asPercentLabel()`.
- **Verify** `./mvnw test -pl backend -Dtest=RateTest`

### W-008 — Identifier types
- [ ] **Produces** `taxrules/id/{RuleId,NodeId,ComponentCode,RegimeCode,ItrFormCode,ScheduleCode,TaxYearId}.java`
- **Spec** TS T3 — patterns verbatim
- **Verify** `./mvnw test -pl backend -Dtest=IdentifierTest`

### W-009 — `IdentifierTest`
- [ ] **Produces** `.../taxrules/id/IdentifierTest.java`
- **Do** one valid and one invalid example per type; `TaxYearId(2026).label()` is `"2026-27"`, `startsOn()` is `2026-04-01`, `endsOn()` is `2027-03-31`.
- **Verify** `./mvnw test -pl backend -Dtest=IdentifierTest`

### W-010 — `Provenance`, `Instrument`, `GoverningInstruments`
- [ ] **Produces** `taxrules/registry/{Provenance,Instrument,GoverningInstruments,VerificationStatus,DraftOrFinal}.java`
- **Spec** `DR 05-domain-model.md` M1.2 · `DR 18` V8 (`AmendmentRef`)
- **Do** records only. `VerificationStatus` = `VERIFIED | NEEDS_VERIFICATION | STATUS_UNCLEAR`.
- **Verify** compiles

### W-011 — `DateRange`, `Validity`, `TemporalAxis`
- [ ] **Produces** `taxrules/temporal/{DateRange,Validity,TemporalAxis}.java`
- **Spec** `DR 18-temporal-versioning.md` V4 — the three `Validity` variants verbatim
- **Verify** `./mvnw test -pl backend -Dtest=ValidityTest`

### W-012 — `ValidityTest`
- [ ] **Produces** `.../taxrules/temporal/ValidityTest.java`
- **Do** `Between` containment incl. both boundaries · `Retrospective` keeps both dates and `contains` uses the deemed date · `PendingNotification.contains` is **always false**.
- **Verify** `./mvnw test -pl backend -Dtest=ValidityTest`

---

## Phase 2 — Cap shapes and conditions (10 items)

### W-013 — `CapShape` sealed interface + `CapResult`, `CapContext`
- [ ] **Produces** `taxrules/cap/{CapShape,CapResult,CapContext}.java`
- **Spec** `DR 05-domain-model.md` M1.3
- **Do** `sealed interface CapShape permits Fixed, PercentOfBase, LeastOf, AggregateWithCeiling, SlabLadder, PerUnitPerPeriod`. `CapResult(Money value, String limitedBy)`.

### W-014 — `Fixed` and `PercentOfBase`
- [ ] **Produces** `taxrules/cap/{Fixed,PercentOfBase}.java`
- **Verify** `./mvnw test -pl backend -Dtest=CapShapeTest`

### W-015 — `SlabLadder` with band-continuity validation
- [ ] **Produces** `taxrules/cap/SlabLadder.java`
- **Spec** `DR 16-getting-started.md` S2 step 3 — **copy the implementation** · `DR 01-rulebook.md` `RATE-SLAB-BAND-002`
- ⚠ The arithmetic lower bound **is** `lowerExclusive`. A band written `400001` must fail `validate()`.
- **Verify** `./mvnw test -pl backend -Dtest=SlabLadderTest`

### W-016 — `SlabLadderTest`
- [ ] **Produces** `.../taxrules/cap/SlabLadderTest.java`
- **Do** the TY 2026-27 default ladder from `DR 01-rulebook.md` `RATE-SLAB-NEW-2026`: assert tax at ₹4,00,000 = ₹0; ₹8,00,000 = ₹20,000; ₹12,00,000 = ₹60,000; ₹16,00,000 = ₹1,20,000; ₹20,00,000 = ₹2,00,000; ₹24,00,000 = ₹3,00,000; ₹50,00,000 = ₹10,80,000. Assert a `400001` band fails `validate()`. Assert `Σ` per-band `==` whole.
- ⚠ **Every number above is in `DR 01-rulebook.md` R3 and `DR 08-golden-tests.md` T2-1. Copy, do not compute.**
- **Verify** `./mvnw test -pl backend -Dtest=SlabLadderTest`

### W-017 — `LeastOf` with named limbs
- [ ] **Produces** `taxrules/cap/LeastOf.java`
- ⚠ `CapResult.limitedBy` must name the **winning limb**. `DR 06-algorithms.md` G1.3 depends on it.

### W-018 — `AggregateWithCeiling`
- [ ] **Produces** `taxrules/cap/AggregateWithCeiling.java`
- **Spec** `DR 06-algorithms.md` G2.1
- ⚠ **Sub-cap first, then the ceiling.** Getting this order wrong is ₹60,000 too generous (`DR 08` T5-3).

### W-019 — `PerUnitPerPeriod`
- [ ] **Produces** `taxrules/cap/PerUnitPerPeriod.java`
- **Spec** `DR 15-review-findings.md` C3 — it computes its own annual equivalent; **no caller divides**.

### W-020 — `CapShapeTest`
- [ ] **Produces** `.../taxrules/cap/CapShapeTest.java`
- **Do** `DR 08-golden-tests.md` **T5-3** verbatim (sub-cap ordering: correct ₹90,000, wrong ₹1,50,000) and **T5-1**.

### W-021 — `Condition` sealed algebra
- [ ] **Produces** `taxrules/condition/Condition.java` + the 16 records
- **Spec** `DR 15-review-findings.md` Part E — **the list verbatim, minus `EffectiveBetween`**
- ⚠ `EffectiveBetween` was **withdrawn** by `DR 18` V-2. Do not add it.

### W-022 — `ConditionTest`
- [ ] **Produces** `.../taxrules/condition/ConditionTest.java`
- **Do** one positive and one negative case per record; `AllOf`/`AnyOf`/`Not` composition.

---

## Phase 3 — Rule pack loading (12 items)

| Item | Produces | Spec | Verify |
|---|---|---|---|
| **W-023** | `taxrules/temporal/{RuleVersion,AmendmentRef}.java` | `DR 18` V4 | compiles |
| **W-024** | `taxrules/pack/{RuleRow,RegimeApplicability}.java` | `DR 18` V4, `DR 10` E3.3 | compiles |
| **W-025** | `taxrules/component/{ComponentDefinition,ComponentLifecycle,DeprecationPolicy,CashNature,ComponentKind}.java` | `DR 10` E4, `DR 18` V6 | compiles |
| **W-026** | `taxrules/regime/{RegimeSpec,RegimeLifecycle,ElectionProtocol,BindingSpec,AfterWithdrawal,ContinuationPolicy,InvalidationPolicy}.java` | `DR 17` L2, L3 | compiles |
| **W-027** | `taxrules/pack/{RulePack,RulePackLayer,RulePackRef}.java` | `DR 23` A2 — **four layers, separate versions** | compiles |
| **W-028** | `taxrules/pack/{RulePackProvider,ClasspathRulePackProvider}.java` | `DR 10` E3.5, `DR 23` A2 | `W-034` |
| **W-029** | `taxrules/validate/PackValidator.java` — invariants I1–I7 | TS T5 | `W-034` |
| **W-030** | `taxrules/validate/PackValidator.java` — invariants I10, I13–I16, I18–I23, I27 | TS T5 | `W-034` |
| **W-031** | `taxrules/registry/{TaxYearRegistry,TaxYearRegistryEntry}.java` | `DR 05` M1.1 | `W-034` |
| **W-032** | pack JSON: `rulepacks/2026-27/manifest.json` + `law/rates.json` + `exec/pipelines.json` + `law/regimes.json` | `DR 16` S2 step 4 — **copy the JSON verbatim** | `W-034` |
| **W-033** | `TJsonMapper`-based pack deserialiser | `DR 19` E9 — reuse `shared/util/collection/TJsonMapper` | `W-034` |
| **W-034** | `.../taxrules/pack/RulePackLoadTest.java` | `DR 08` T9-1…T9-7 | `-Dtest=RulePackLoadTest` |

⚠ **W-032 is the highest-risk item in Phase 3.** Every number in it is in `DR 16-getting-started.md`
S2 step 4 and `DR 01-rulebook.md` R3/R4. Copy character by character. Bands are `400000`, **not**
`400001`.

---

## Phase 4 — The DAG runner (10 items)

| Item | Produces | Spec | Verify |
|---|---|---|---|
| **W-035** | `taxengine/pipeline/{NodeId,NodeValue,MoneyValue,TaxByBasis}.java` | `DR 15` B2, B4 | compiles |
| **W-036** | `taxengine/trace/{TraceLine,TraceAnnotation,Notice,StepKind,Severity}.java` | `DR 23` A8 — **two separate records** | compiles |
| **W-037** | `taxengine/input/{EngineInputs,TaxpayerFacts,FamilySnapshot,LedgerSnapshot,RegimeElectionSnapshot,FilingFacts}.java` | `DR 23` A3 — **verbatim** | compiles |
| **W-038** | `taxengine/input/InputsFingerprint.java` | `DR 15` F6 + `DR 23` A3 | `W-045` |
| **W-039** | `taxengine/pipeline/{ComputationNode,NodeContext,NodeResult,NodeRegistry}.java` | `DR 15` B2 | compiles |
| **W-040** | `taxengine/pipeline/TopologicalSort.java` | `DR 15` B3 | `-Dtest=TopologicalSortTest` |
| **W-041** | `.../pipeline/TopologicalSortTest.java` | — cycle fails; order respects edges | same |
| **W-042** | `taxengine/pipeline/PipelineRunner.java` | `DR 15` B3 — **no try/catch** | `W-045` |
| **W-043** | `taxengine/result/{TaxComputation,FilingPosition,ComputationId,ComputationReceipt}.java` | `DR 23` A6 — **the split** | compiles |
| **W-044** | `taxengine/test/NodeHarness.java` (test scope) | `DR 23` A11 | `W-045` |
| **W-045** | `.../pipeline/PipelineRunnerTest.java` | `DR 08` T9-20, `DR 23` T23-6, T23-7 | `-Dtest=PipelineRunnerTest` |

---

## Phase 5 — Tax nodes: the walking skeleton (8 items)

### W-046 — `TaxByBasisNode`
- [ ] **Produces** `taxengine/node/TaxByBasisNode.java`
- **Spec** `DR 16-getting-started.md` S2 step 5 — **copy it**
- **Verify** `./mvnw test -pl backend -Dtest=TaxByBasisNodeTest`

### W-047 — `TaxByBasisNodeTest` using `NodeHarness`
- [ ] **Do** `DR 23` A11's harness example: TI ₹12,00,000 → `TaxByBasis(60000, {})`.

### W-048 — `ReliefShape` sealed family
- [ ] **Produces** `taxrules/relief/{ReliefShape,FlatWithCeiling,ExcessOverThreshold,ThresholdLadderCeiling,NoRelief}.java`
- **Spec** `DR 15` Part E

### W-049 — `RebateNode`
- [ ] **Spec** `DR 06-algorithms.md` G3.1 · `DR 13` R-5
- ⚠ **The rebate reads `TaxByBasis.atSlabRates` ONLY.** Using the total produces a nil tax bill on a ₹12,00,000 income that is mostly capital gains (`DR 08` T13-1). This is the single most dangerous line in the engine.
- ⚠ The relief branch exists **only** in the default regime.

### W-050 — `RoundTotalIncomeNode` and `RoundNetPayableNode`
- [ ] **Spec** `DR 01-rulebook.md` `RATE-ROUND-002`, `RATE-ROUND-003`
- ⚠ Exactly **two** rounding points in the whole pipeline.

### W-051 — `CessNode`
- [ ] **Spec** `DR 01-rulebook.md` `RATE-CESS-001` · `REBATE-ORDER-001` — cess is on **post-rebate** tax plus surcharge.

### W-052 — `NetPayableNode`

### W-053 — **T1 golden tests**
- [ ] **Produces** `backend/src/test/resources/taxplanning/golden/2026-27/S202_DEFAULT/T1-*.json` and `.../taxplanning/golden/GoldenCaseRunner.java`, `T1SlabAndRebateTest.java`
- **Spec** `DR 08-golden-tests.md` **T1-N1 … T1-A1f — every case, every expected value**
- ⚠ **Copy the expected values. Do not compute them.** If one fails, the code is wrong (rule R3).
- **Verify** `./mvnw test -pl backend -Dtest=T1SlabAndRebateTest`
- **This is the first real milestone: a correct calculator for slab tax and rebate.**

---

## Phase 6 — Surcharge and marginal relief (5 items)

| Item | Produces | Spec | ⚠ |
|---|---|---|---|
| **W-054** | `law/rates.json` surcharge ladders for all four (year × regime) | `DR 01` R5 | entry is **₹50,00,000**; the default regime has **no 37% band** |
| **W-055** | `taxrules/relief/ThresholdLadderCeiling.java` | `DR 06` G3.2 | `W = U + V`, one implementation, four parameterisations |
| **W-056** | `taxengine/node/SurchargeNode.java` | `DR 06` G3.2 | **two buckets** — `RATE-SUR-SPLIT-001` caps the special slice at 15% |
| **W-057** | **T2 golden tests** | `DR 08` T2-1 … T2-6 | copy expected values |
| **W-058** | metamorphic property tests | `DR 19` E4.1 | `moreIncomeNeverMeansLessTax` would have caught H-1 unaided |

---

## Phase 7 — Salary head (12 items)

| Item | Produces | Spec | ⚠ |
|---|---|---|---|
| W-059 | `components.json` for both years | `DR 10` E4, `DR 15` F-part | every code has `cashNature` |
| W-060 | `ComponentPartitionNode` | `DR 06` G1.1 step 1 | unknown code = error, never a silent addend |
| W-061 | `GrossSalaryNode` | `DR 01` R7 | |
| W-062 | `SalaryBaseNode` | `DR 01` `SAL-DEF-HRA-001` etc. | **basic + DA where the terms provide**; one function, five rules |
| W-063 | `SalaryDeductionNode` | `DR 01` R8 | professional tax **only** in the opted-out regime |
| W-064 | `salary.json` law rows | `DR 01` R7, R8 | |
| W-065 | `SalaryIncomeNode` | | |
| W-066 | `ProrationBasis` family | `DR 15` C3 | named basis per rule; **no caller divides** |
| W-067 | `ExemptionNode` — HRA | `DR 06` G1.3 | **per period**; metro % per period; `limitedBy` names the limb |
| W-068 | `ExemptionNode` — LTA | `DR 01` R10 | **calendar** blocks; four independent caps |
| W-069 | `ExemptionNode` — allowances | `DR 01` R11 | ⚠ `SAL-ALW-NEW-SUBSET-001` is **STATUS UNCLEAR** — seed from the **section**, flag visibly |
| W-070 | **T3 golden tests** | `DR 08` T3-1 … T3-5 | T3-2, T3-3 are **PROVISIONAL** — report separately |

---

## Phase 8 — Perquisites, employer contributions, Chapter VIII (10 items)

| Item | Produces | Spec | ⚠ |
|---|---|---|---|
| W-071 | `perquisites.json` both years | `DR 01` R12 | TY 2025-26 values are **NEEDS VERIFICATION** — mark them |
| W-072 | `PerquisiteNode` | `DR 01` R12 | every output is `NON_CASH` |
| W-073 | `EmployerContributionNode` | `DR 01` R13 | **four** independent charges, not one |
| W-074 | **T4 golden tests** | `DR 08` T4-1 … T4-4 | |
| W-075 | `deductions.json` both years | `DR 01` R15 | |
| W-076 | `ChapterEightNode` | `DR 06` G2.2, G2.3 | employee PF consumes 80C; 80D is **seven** rules |
| W-077 | **T5 golden tests** | `DR 08` T5-1 … T5-4 | |
| W-078 | `houseproperty.json`, `HousePropertyNode` | `DR 01` R16, `DR 06` G1.4 | occupancy drives **four** rules |
| W-079 | `othersources.json`, `OtherSourcesNode` | `DR 01` R17 | dividend deduction is **nil** from TY 2026-27 |
| W-080 | **T6 golden tests** | `DR 08` T6-1 … T6-7 | |

---

## Phase 9 — Set-off, elections, capital gains (9 items)

| Item | Produces | Spec | ⚠ |
|---|---|---|---|
| W-081 | `setoff.json`, `SetOffNode` | `DR 01` R18 | absorption order is **declared and traced** (`DR 13` R-7) |
| W-082 | `Election` family, `ElectionResolver` | `DR 15` C2 | priced by **recomputation**; record all alternatives |
| W-083 | `HouseElectionNode` | `DR 13` R-6 | which two houses is a **choice**, and optimisable |
| W-084 | `IncrementalRunner` | `DR 15` B5 | memo only where the graph proves nothing upstream moved |
| W-085 | `CapitalGainsSummary`, `CapitalGainsBucket`, `RateBasisHint` | `DR 13` R-10, `DR 15` F5 | gross buckets; **no tax amounts** |
| W-086 | `portfolio` published read-model | `DR 07` X1.3 | modulith test must pass with the new dependency |
| W-087 | `MoneyBridge` | `DR 19` E1 | `toTMoney` **throws** — one-way seam |
| W-088 | `CapitalGainsNode` | `DR 13` R-10 | `UNCLASSIFIED` → 409; `SPECULATIVE` excluded |
| W-089 | **T13-1 test** — rebate with special-rate income | `DR 13` R-5 | **the most dangerous interaction in the design** |

---

## Phase 10 — ITR forms and export (6 items)

| Item | Produces | Spec | ⚠ |
|---|---|---|---|
| W-090 | `ItrFormSpec`, `forms/itr-forms.json` | `DR 21` I3 | **nine** forms; eligibility in `Condition` |
| W-091 | `ItrFormSelector` | `DR 21` I4 | deterministic **cascade**; return **all** verdicts |
| W-092 | `ItrSchedule`, `FieldSource`, `forms/itr-schedules.json` | `DR 21` I5 | schedules **shared by reference** |
| W-093 | `ScheduleMapper` | `DR 21` I5 | a **fold**, not a per-form mapper |
| W-094 | **T21 tests** | `DR 21` I8 | T21-1 … T21-16 |
| W-095 | ArchUnit: `@Valid` on every `@RequestBody`; no `@Document` returned | TS T10 | |

---

## Phase 11 — Persistence and API (16 items)

| Item | Produces | Spec |
|---|---|---|
| W-096 | `TaxpayerProfile`, `Pan` | `DR 15` F1 — **PAN was missing entirely** |
| W-097 | `FamilyMember` | `DR 15` F2 |
| W-098 | `Employer`, `Landlord`, `Lender`, `BankAccount` | `DR 15` F3, F4 |
| W-099 | `TaxYearWorkspace`, `FilingDraft`, `DraftInputs` | `DR 11` F2 |
| W-100 | `DraftRevision` + JSON-Patch apply/replay | `DR 11` F2 |
| W-101 | repositories + indexes | TS T7 |
| W-102 | `cleanDatabase()` additions | TS T7 — **every** collection |
| W-103 | `EngineInputsAssembler` | `DR 23` A3 — the **only** place `EngineInputs` is built |
| W-104 | `TaxComputationService` | `DR 23` A6 — core/overlay split |
| W-105 | `TaxError`, `TaxEngineException` sealed hierarchy | TS T9, `DR 23` A7 |
| W-106 | `ControllerAdviser` extension — exhaustive switch | `DR 23` A7 |
| W-107 | `ComputationReceipt` | `DR 19` E6 |
| W-108 | draft endpoints | TS T8 |
| W-109 | compute + compare-regimes endpoints | TS T8 |
| W-110 | optimistic locking, `If-Match`, 409 merge body | `DR 11` F5 |
| W-111 | **T11 tests** | `DR 11` F10 |

---

## Phase 12 — Product surface (12 items)

| Item | Produces | Spec | Feature |
|---|---|---|---|
| W-112 | `FilingPosition` + `InterestAndFeeNode` | `DR 15` C1 | — |
| W-113 | `ForecastProjection` + endpoint | `DR 20` F29 | **F29** |
| W-114 | `CalendarProjection` + endpoint | `DR 20` F30 | **F30** |
| W-115 | `CompletenessProjection` + endpoint | `DR 20` F33 | **F33** |
| W-116 | `content/explanations.json` + renderer | `DR 20` F37 | **F37** |
| W-117 | `content/claim-catalogue.json` (12 entries) | `DR 22` C5 | **F40** |
| W-118 | claim-playbook endpoint | `DR 22` C4 | **F40** |
| W-119 | claim integrity: S / E / D + statutory overshoot | `DR 12` C2, C3.1 | **F23, F24** |
| W-120 | documentation checklist | `DR 12` C3.2, `DR 22` C7 | **F25** |
| W-121 | materiality banding | `DR 22` C6 | **F40.4** |
| W-122 | progressive onboarding steps | `DR 20` F34 | **F34** |
| W-123 | PDF report | `DR 04` F10 | **F10** |

---

## Phase 13 — Documents (10 items)

| Item | Produces | Spec |
|---|---|---|
| W-124 | `IngestedDocument`, `ParseAttempt`, `ExtractedField`, `DocumentConfirmation` | `DR 05` M4 |
| W-125 | `DocumentParser` SPI | `DR 07` X2.5 |
| W-126 | `Form130Parser` L1 structural | `DR 06` G5.1 |
| W-127 | L2 anchored numeric + L3 checksum-driven | `DR 06` G5.1 |
| W-128 | `G5.2` validation identities | `DR 06` G5.2 |
| W-129 | multi-employer assembly | `DR 06` G5.3 |
| W-130 | `ReconciliationReport` | `DR 05` M4.2 |
| W-131 | upload / parse / confirm endpoints + async job | TS T8 |
| W-132 | **T8 tests** | `DR 08` T8-1 … T8-6 |
| W-133 | Excel import (**F28**) | `DR 19` E9.1 |

---

## Phase 14 — Hardening (13 items)

| Item | Produces | Spec |
|---|---|---|
| W-134 | PII: field encryption, log masking | `DR 14` O1 |
| W-135 | third-party PII exclusion from exports | `DR 14` O1.1, O5 |
| W-136 | retention derived from pack carry-forward periods | `DR 14` O5 |
| W-137 | `TaxJob` + `JobLease` + 6 jobs | `DR 19` E8 |
| W-138 | staleness sweep | `DR 13` R-8 |
| W-139 | pack linter + cross-year diff | `DR 15` C4 |
| W-140 | boundary-case generator | `DR 15` C5 |
| W-141 | independent Python oracle + `expected.csv` | `DR 19` E4.2 |
| W-142 | trace snapshot tests | `DR 19` E4.3 |
| W-143 | performance benchmarks N1–N3 | `01-PRD.md` §5 |
| W-144 | JaCoCo + PIT gates | `DR 07` X5.5 |
| W-145 | persona fixtures | `DR 19` E10 |
| W-146 | **synthetic pack test** — three regimes, synthetic year, sunset | `DR 10` E8, `DR 17` T17-11, `DR 18` T18-13 |

⚠ **W-146 is the acceptance test for the whole architecture.** It must pass with **zero** changes to
production source. If it does not, the Open/Closed claim has failed.

---

## Milestones

| After | You have |
|---|---|
| **W-053** | a correct slab-and-rebate calculator, both years, both regimes, no database |
| **W-058** | surcharge and both marginal reliefs — H-1, H-3, H-5 retired |
| **W-080** | every income head computing; golden tests T1–T6 green |
| **W-089** | capital gains wired; the s.156(3) trap closed |
| **W-095** | ITR suggestion and export |
| **W-111** | drafts, API, persistence |
| **W-123** | the product surface — forecast, calendar, completeness, playbooks |
| **W-133** | document ingestion |
| **W-146** | hardened, and the extensibility claim proven in CI |
