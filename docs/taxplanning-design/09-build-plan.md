# 09 — Build plan

**There is no migration.** No users, no data, no backward compatibility (the brief). So the plan is
ordered by **risk retired per slice**, not by how the placeholder is arranged.

Each slice is independently shippable and carries its own golden tests. The definition of done is the
same for all of them and is stated once, at the end.

---

## B1 — The slices

### Slice 0 — Verify what is unverified (**before writing any code**)

| | |
|---|---|
| **Scope** | Open the sources that were blocked this session and close out the `NEEDS VERIFICATION` rows |
| **Rule IDs** | `SAL-HRA-METRO-2025`, `SAL-ALW-LIMITS-2025`, `PERQ-CAR-2025`, `PERQ-ACCOM-OWNED-2026`, `PERQ-LOAN-2026`, `PERQ-PHONE-2026`, `PERQ-SPECEMP-001`, `PERQ-MEDABROAD-001`, `REBATE-OLD-001`, `RET-GRAT-OTHER-001(b)`, `RET-RETRENCH-001(c)`, `RET-LEAVE-OTHER-001(c)`, `PF-AGGCAP-002`, `PF-EEINT-001`, `DED-80G-001`, `DED-80DDB-001`, `REL-ARREARS-CALC-001`, `SAL-LTA-003`, `SAL-DEF-COMM-001`, `SAL-ALW-NEW-SUBSET-001`, `REG-OPTOUT-LATE-001`, `ITR-1-BAR-002` |
| **What to open** | **Income-tax Act 1961** base text (ss.16, 17, 10(5), 10(13A), 10(14), 23, 24, 57, 80C–80U, 87A, 89, 115BAC, 192, 288A, 288B); **Income-tax Rules 1962** rules 2A, 2B, 2BB, 3, 3B, 9D, 12, 21A, 26C; the CBDT notifications fixing the **gratuity** and **leave-encashment** ceilings; the **Rules 2026 amendment chain** between G.S.R. 198(E) and G.S.R. 656(E) of 24-07-2026 |
| **Where** | `incometaxindia.gov.in` returned HTTP 403 to every request this session (S10) and `indiacode.nic.in` returned 404 (S11). Try: a different network egress; the e-filing portal's *Tax Laws & Rules*; the eGazette originals of the amending notifications, which **did** work (`egazette.gov.in/WriteReadData/<year>/<id>.pdf`); a library or subscription database |
| **Tests** | none — this slice produces **rule-pack rows with `status = VERIFIED` and a `sourceUrl`** |
| **Done when** | every `PROVISIONAL` case in `08-golden-tests.md` has become `VERIFIED`, **or** the item is explicitly accepted as unverified with the risk recorded in `00-SUMMARY.md` |
| **Why first** | `INFERENCE`: nine golden cases are currently `PROVISIONAL`, all TY 2025-26, all from one blocked source. Building against them means the first real regression will be indistinguishable from a rule that was never right. This slice is cheap, needs no code, and removes the largest single source of uncertainty in the whole design |

### Slice 1 — `taxrules`: registry, packs, component codes

| | |
|---|---|
| **Scope** | `TaxYearRegistry`, `RulePack`, `RuleId`, `Provision`, `Provenance`, the six `CapShape` implementations, the `ComponentCode` enum, `ClasspathRulePackProvider`, load-time validation. **Two packs authored: 2025-26 and 2026-27.** No computation |
| **Rule IDs** | all of R1; every rule row is *authored*, none is *evaluated* |
| **Tests** | pack load and checksum; invariants I1–I4; slab continuity (`T9-7`); `T9-1` … `T9-6`; **`T9-21` the synthetic TY 2099-00 pack**; `D-YEARKEY` architecture test |
| **Done when** | both packs load, both validate, startup fails on a corrupt pack, and the synthetic year loads without a production source change |
| **Why here** | Everything downstream reads from here. `INFERENCE`: getting the *shape* of a rule row wrong is the only mistake in this design that is expensive to correct later, because every step and every test references it |

### Slice 2 — `taxengine`: pipeline, trace, money, slabs, rebate, surcharge, cess, rounding

| | |
|---|---|
| **Scope** | `Money`, `Computation`, `TraceLine`, `Notice`, `ComputationNode` SPI, `RegimeProvider`, the pipeline runner, and steps 23–28 and 31 of `G1.1` — the **tax** end of the pipeline |
| **Rule IDs** | `RATE-SLAB-*`, `REBATE-*`, `RATE-SUR-*`, `RATE-SUR-MR-001`, `RATE-SUR-SPLIT-001`, `RATE-CESS-001`, `RATE-ROUND-002`, `RATE-ROUND-003` |
| **Tests** | **T1 in full** (all four year × regime cells), **T2 in full**, `T9-10`, `T9-15`, `T9-16`, `T9-20`; the slab-continuity, monotonicity and two-rounding-lines property tests from `X5.2` |
| **Done when** | T1 and T2 pass for all four cells, and the old-regime pipeline provably contains no rebate-marginal-relief step |
| **Why here** | `INFERENCE`: this slice retires **H-1, H-3 and H-5** — three of the four confirmed defects that produce silently wrong tax at a boundary. It needs no salary model, no profile and no database, so it is testable entirely in the `-Punit` tier |

### Slice 3 — Salary head: components, gross, standard deduction, professional tax

| | |
|---|---|
| **Scope** | the `SALARIES` node group — `IncomeHead` is a **label**, not an SPI (`23-architecture-consolidation.md` A4); `salaryBaseFor(ruleId, period)`; steps 1, 2, 10, 11, 12 of `G1.1`. Minimal `TaxpayerProfile` and `TaxYearContext` persistence |
| **Rule IDs** | `SAL-CHARGE-001`, `SAL-CHARGE-002`, `SAL-DEF-001`, `SAL-DEF-GRAT-001`, `SAL-DEF-NPS-001`, `SAL-STDDED-001`…`-003`, `SAL-PTAX-001`, `SAL-PTAX-002`, `SAL-ENTMT-001` |
| **Tests** | `T9-8`, `T9-9`, `T9-17`; professional tax present in the old-regime pipeline and absent from the new (`H-13`); the standard deduction capped at salary |
| **Done when** | a salary-only user computes correctly in both regimes and both years, end to end through the API |
| **Why here** | First slice with a persisted model and an HTTP surface. Retires **H-13** and establishes `D-CASHFLAG` before any perquisite exists to violate it |

### Slice 4 — Exemptions: HRA, LTA, allowances

| | |
|---|---|
| **Scope** | Steps 6, 7, 8 of `G1.1`; `LeastOf` with named limbs; the LTA calendar-block ledger; `SAL-HRA-003`'s relevant-period arithmetic |
| **Rule IDs** | `SAL-HRA-001`…`-006`, `SAL-HRA-METRO-2025/2026`, `SAL-LTA-001`…`SAL-LTA-NEW-001`, `SAL-ALW-*` |
| **Tests** | **T3 in full**, **T5-4** and its four variants; the `H-4` regression (T3-5) |
| **Done when** | T3 and T5-4 pass; `limitedBy` names the winning limb on every HRA line |
| **Why here** | Retires **H-2, H-4, H-8, H-9, H-10** — five confirmed defects, all in one body of rules. `INFERENCE`: it depends on Slice 1's year-keyed lookup, which is exactly what H-2 was a failure of, so shipping it earlier would mean shipping it wrong |

### Slice 5 — Perquisites and employer contributions

| | |
|---|---|
| **Scope** | Steps 3, 4 of `G1.1`; the perquisite valuation tables; the aggregate-cap aggregator |
| **Rule IDs** | `PERQ-*`, `PF-AGGCAP-001`, `PF-AGGCAP-002`, `PF-RPF12-001`, `PF-EEINT-001` |
| **Tests** | **T4 in full**; `T9-18`, `T9-19` (the gift boundary); `T9-17` re-asserted with a real car perquisite |
| **Done when** | T4 passes; every perquisite line is `NON_CASH`; `takeHome` is provably unaffected by any of them |
| **Why here** | Retires **H-6 and H-15**. `INFERENCE`: `PF-AGGCAP-001` produces *income* while `DED-NPS-EMP-001` produces a *deduction* from the same rupees, and that pairing is only testable once both sides exist — so Slice 5 and Slice 6 should land in the same release even though they are separate slices |

### Slice 6 — Chapter VIII, with the aggregator

| | |
|---|---|
| **Scope** | Steps 20, 21 of `G1.1`; `G2`'s `AggregateWithCeiling`; the sub-cap-before-ceiling ordering |
| **Rule IDs** | all of R15 |
| **Tests** | **T5-1, T5-2, T5-3**; `DED-GTI-CAP-001`; the aggregate-never-exceeds-ceiling property test |
| **Done when** | T5-1..3 pass, and the trace shows employee PF consuming 80C headroom |
| **Why here** | Retires **H-7 and H-11**. T5-3 (sub-cap ordering) is the assertion that most cheaply distinguishes a correct aggregator from a plausible one |

### Slice 7 — House property, other sources, set-off

| | |
|---|---|
| **Scope** | Steps 13, 14, 16, 17, 18 of `G1.1`; the `HOUSE_PROPERTY` and `OTHER_SOURCES` heads; the carry-forward ledger |
| **Rule IDs** | all of R16, R17, R18 |
| **Tests** | **T6 in full** |
| **Done when** | T6 passes; the same property yields +₹5,400, −₹2,00,000 or ₹0 across T6-1/T6-2/T6-3 |
| **Why here** | Retires **H-12**. Completes D3's income heads on the computation side |

### Slice 8 — Capital-gains feed from `portfolio`

| | |
|---|---|
| **Scope** | `CapitalGainsSummary` published read-model in `portfolio`; the `taxplanning` consumer; step 15; the modulith dependency addition |
| **Rule IDs** | D3, `RATE-SUR-SPLIT-001`, `ITR-SPEC-001` |
| **Tests** | `T9-12`, `T9-13`, `T9-14`; **T2-6**; `WealthLensModulithTest.modulithStructureIsValid()` passes with the new `allowedDependencies` |
| **Done when** | non-zero `UNCLASSIFIED` blocks with a 409; non-zero `SPECULATIVE` routes to ITR-3; the modulith test is green |
| **Why here** | `FACT (portfolio/entity/model/RealisedProfits.java:16-54, read this session)`: `RealisedProfits` is documented as mid-cutover, with two legacy fields that go when V1 goes. Placing this slice **after** the computation is stable means the read-model can be shaped by what the tax engine actually needs, rather than by what happens to be on the entity today |

### Slice 9 — Regime comparison, ITR suggestion, PDF

| | |
|---|---|
| **Scope** | `Scenario`, `ScenarioDiff`, `A1`'s comparison and driver attribution, `F8`'s ITR decision table, the Thymeleaf + Flying Saucer PDF |
| **Rule IDs** | `A1`, all of R22 |
| **Tests** | the six `A1.4` traps; the 14 `ITR-D3-001` triggers; a PDF test asserting no section renders without trace lines (O-20) |
| **Done when** | the comparison names its drivers by rule ID and the PDF carries provenance and the `A4.3` disclaimer |
| **Why here** | This is where MVP becomes a **product** rather than a calculator. It needs the whole computation, so it cannot come earlier; it needs nothing from documents, so it need not come later |

### Slice 10 — Form No. 130 / Form 16 ingestion and reconciliation

| | |
|---|---|
| **Scope** | `taxdocs` module; `DocumentParser` SPI; two parsers (Form 16 for TY 2025-26, Form No. 130 for TY 2026-27); `G5`'s four layers; `ParseAttempt`, `DocumentConfirmation`, `ReconciliationReport` |
| **Rule IDs** | all of R21, `DOC-F130-*`, `M4` |
| **Tests** | **T8 in full**; `G5.2`'s arithmetic identities; confidence banding; the multi-employer assembly modes |
| **Done when** | T8-1 (the two-employer double standard deduction, ₹2,14,500 shortfall) is detected and explained |
| **Why here** | Last in MVP because it is the largest slice and the only one whose correctness is judged against a **document format** rather than a statute. `INFERENCE`: T8-1 is the single highest-value output in the product, and it depends on the computation being right first — a reconciliation against a wrong computation is worse than none |

### Slices 11+ — the `Next` tier

In `F`-order by RICE: **F13** gaps and next-best actions → **F15** break-even → **F16** advance tax →
**F12** TDS projection → **F14** optimiser → **F17** HR letter → **F18** scenario save.

`RECOMMENDATION`: **F12 (TDS projection) before F14 (optimiser)** despite F14's higher user appeal.
F12's `G4` re-estimation loop is the same machinery F14's `D-MARGINAL` pricing needs, and F12 has a
far simpler success criterion.

### Definition of done — the same for every slice

1. Golden tests for the slice pass in **all four (year × regime) cells**, or the empty cells are
   explicitly justified in the coverage map.
2. Every new expected value was derived by arithmetic in the design docs, not by running the code
   (`D-GOLDEN`).
3. `ApplicationModules.verify()` passes; `allowedDependencies` updated where needed.
4. `ArchitectureTest` passes, including the new `X5.3` rules.
5. JaCoCo and PIT gates for the touched packages pass (`X5.5`).
6. Every collection the slice introduces is added to `AbstractIntegrationTest.cleanDatabase()`, and
   **none** is added to `SEEDED_REFERENCE_COLLECTIONS`.
7. `./mvnw spotless:apply` run; checkstyle clean.
8. Every user-visible figure carries its rule IDs and their status (`A4.2`).
9. No `catch` of `Exception` or `RuntimeException` anywhere in `taxengine` (`D-NOSILENT`).

---

## B2 — What to delete from the placeholder, and when

### The inventory, verified first-hand this session

`FACT`: `find backend/src/main/java/com/thiru/wealthlens/taxplanning -name "*.java"` → **56 files**.
`FACT`: `backend/src/main/resources/data/tax-policies/` → **5 seed JSONs**.
`FACT`: `backend/src/test/java/com/thiru/wealthlens/taxplanning/engine/` → **6 unit test classes**,
plus `backend/src/test/java/com/thiru/wealthlens/integration/TaxPlanningControllerIntegrationTest.java`.
`FACT`: `backend/src/main/java/com/thiru/wealthlens/taxplanning/package-info.java:1` declares
`allowedDependencies = {"shared", "auth"}`.
`FACT`: there is also an empty `backend/src/test/java/com/thiru/wealthlens/taxplanning/_review`
directory.

### The rule

**Nothing is deleted until its replacement has passing golden tests.** `INFERENCE`: the placeholder is
a leaf — `docs/taxplanning-context/00-INDEX.md` records that no production code outside the module
imports it, and the only inbound references in the repository are eight imports in one integration
test. So it can coexist with the replacement at zero cost, and deleting it early buys nothing while
removing the ability to diff behaviour.

### The order

| When | Delete | Why then |
|---|---|---|
| **After Slice 1** | `policy/entity/TaxSlabPolicyEntity`, `AllowanceLimitEntity`, `AllowanceCatalogueEntity`, `PerquisitePolicyEntity`, `TaxYearRegistryEntity`; their five repositories; `PolicySeederService`; `PolicyService`; **all five JSONs in `resources/data/tax-policies/`**; the admin policy endpoints on `TaxPlanningController` | Rule packs now exist. Leaving the Mongo-backed policy collections alive keeps O-16's failure mode — an admin endpoint that can supersede seeded reference data in a collection the test harness protects — available for exactly as long as the code is there |
| **After Slice 1** | `engine/FormulaEvaluator` and `FormulaEvaluatorSandboxTest` | `D-NOFORMULA`. The six `CapShape` implementations cover R1–R24. Deleting the evaluator deletes O-1's silent-zero path and O-24's second vocabulary in one move |
| **After Slice 2** | `engine/AbstractTaxEngine`, `NewRegimeTaxEngine`, `OldRegimeTaxEngine`, `TaxEngine`, `TaxEngineFactory`, `MarginalReliefCalculator`, and their four test classes | The new pipeline computes tax correctly for all four cells. `D-REGIMESTEP` replaces the shared template that let marginal relief leak into the old regime (O-11), and `D-ONEIMPL` replaces the two competing marginal-relief implementations (O-2) |
| **After Slice 4** | `engine/HraExemptionCalculator` and `HraExemptionCalculatorTest` | `SAL-HRA-001` is implemented as a `LeastOf` with named limbs, and its warnings are trace notices (O-5) |
| **After Slice 5** | `engine/PerquisiteValuationService` and `PerquisiteValuationServiceTest` | The perquisite rules now come from the pack, and every value is `NON_CASH` (O-13, O-25) |
| **After Slice 6** | `enums/AllowanceCategory`, `AvailabilityPath`, `CarEngineSize`, `CarOwnership`, `CityTier`, `EmployerType`, `HrSupportLikelihood`, `ProfileType`; `policy/dto/ResolvedAllowance`; `policy/service/AllowanceResolutionService` | Superseded by `ComponentCode` and the pack's cap shapes. **`RegimeType` is kept** — it maps directly to the new `Regime` |
| **After Slice 7** | `salary/entity/SalaryProfileEntity`, `SalaryComponentEntity`, `TaxComputationEntity`; `salary/dto/*`; `salary/repository/*`; `salary/service/SalaryProfileService`; `service/TaxComputationService` | Replaced by `TaxpayerProfile`, `TaxYearContext`, `Employment`, `Computation` (`M3`, `M5`). This removes O-6's orphan fields, O-9 and O-12's nullable-`Long` unboxing, and O-26's two divergent write paths |
| **After Slice 9** | `recommendation/RegimeAdvisor`, `RegimeAdvice`, `engine/ItrFormAdvisor`; `document/DocumentGenerationService`, `TaxComparisonReportGenerator`; `controller/TaxPlanningController`, `TaxPlanningPublicController`; the Thymeleaf template under `resources/templates/taxplanning/`; `TaxPlanningControllerIntegrationTest` | The new API, comparison, ITR advisor and PDF are live. This is the last slice that touches the HTTP surface, so the old controllers can go without leaving an endpoint gap |
| **After Slice 10** | `parsing/ParsedCtcDto` | Its three good ideas — `confidence`, `unmappedComponents`, `parsingNotes` — survive as `ExtractedField.confidence`, `ParseAttempt.unmappedText` and `ParseAttempt.parserNotes` (`K-13`). The DTO itself described a CTC letter, not a Form 16 |
| **After Slice 11+** | `recommendation/RestructuringEngine`, `RestructuringResult`, `AllowanceRecommendation`, `HrLetterContentBuilder`, `engine/FbpOptimizer` | Superseded by `A2`'s optimiser and `F17`'s letter generator. Keep until then: `HrLetterContentBuilder`'s **idea** — that the deliverable is an action, not a number — is worth reading before rewriting it (`K-11`) |
| **Immediately, at any point** | the empty `backend/src/test/java/com/thiru/wealthlens/taxplanning/_review` directory | It holds nothing |

### At the end

`package-info.java` is **replaced, not deleted** — four modules now, with the dependencies in `X1.2`:

```java
// taxplanning
@ApplicationModule(type = Type.OPEN,
    allowedDependencies = {"shared", "auth", "taxrules", "taxengine", "taxdocs", "portfolio"})
```

`RECOMMENDATION`: take the opportunity to declare the four new modules as `Type.CLOSED` rather than
`OPEN`. CLAUDE.md records that `OPEN` across the codebase is *"a migration affordance from the old
flat layout, not a design goal"*. A greenfield module has no such history, and `CLOSED` is what makes
`X1.2`'s "`taxengine` cannot reach a repository" an enforced boundary rather than a convention.

### What survives from the placeholder

Nothing, as code. Fifteen ideas, as design (`02-lessons.md` L2) — and the 36 observations, which is
what produced the `D-*` rules that the replacement is built on.
