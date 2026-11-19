# 00 — Index: `taxplanning` module context pack

Read-only discovery pass over the `taxplanning` module, 2026-09-18, at commit `55024ad` on branch
`master`. Produced for a later AI review-and-extend pass.

**Scope covered:** CTC structure and salary breakup, Form 16, deductions and exemptions, tax regime
choice, and income heads other than capital gains. STCG/LTCG were out of scope; where the module
touches them, that is recorded in `03-coverage-matrix.md` §6 and nowhere else.

**Nothing in the source tree was modified.** `git status` on completion shows exactly one entry —
the untracked `docs/taxplanning-context/` folder holding these six files. `HEAD` is unchanged at
`55024ad`. (At the start of this pass the working tree also carried an unstaged modification to
`backend/src/main/java/com/thiru/wealthlens/portfolio/dto/AssetRequest.java`, unrelated to this
module; it was parked in `stash@{0}` during the session by something other than this pass, which ran
no git write commands.)

---

## The files

| File | Lines | Contents |
|---|---|---|
| `00-INDEX.md` | this file | Navigation, scan totals, every `UNKNOWN`, and the open questions only the developer can answer |
| `01-overview.md` | 390 | Detected stack with evidence; full annotated file tree; module dependencies in both directions including the non-import couplings (security, response wrapping, test-harness collection protection, PIT/JaCoCo); a Mermaid architecture graph; Mermaid sequence diagram of the compute flow and flowchart of the restructure flow; a plain-English summary of what the module does today |
| `02-domain-and-api.md` | 635 | Verbatim copies of all 9 enums plus `EntityStatus`; field-by-field tables for all 7 entities with Mongo field names, types, line numbers, indexes and whether each field has a reader; the complete 14-endpoint API surface with auth and validation; the authentication/authorization mechanics verbatim; configuration findings; external-input analysis (§5 — how salary data enters); outputs including the PDF template's section-by-section contents; events/schedulers; transaction boundaries |
| `03-coverage-matrix.md` | 1094 | The full Step-3 coverage matrix across all eight requested domain areas, each row carrying `IMPLEMENTED`/`PARTIAL`/`NOT FOUND` plus `file:line`; verbatim formula snippets for gross salary, component lookup, standard deduction, HRA, LTA, Chapter VI-A, children education and hostel, progressive slab tax, 87A rebate, marginal relief, surcharge, cess, regime comparison, formula evaluation, allowance resolution, restructuring eligibility and priority, suggested amount and estimated saving, perquisite offset, regime switch triggers; the code-vocabulary mismatch table |
| `04-constants-tests-history.md` | ~885 | Every hardcoded value in Java and in all five seed JSON files, as a `Value / Meaning / File:line / Hardcoded-or-configurable / Tied-to-a-tax-year` table; a test-by-test inventory with the actual input and expected numbers; an explicit list of flows with zero coverage; the git history and what it shows; all placeholder comments verbatim (there are no `TODO`/`FIXME`/`HACK` markers in the module); **36 numbered Observations** |
| `05-core-code.md` | 1888 | Verbatim, complete method bodies — extracted mechanically from source, not retyped — for `AbstractTaxEngine`, `NewRegimeTaxEngine`, `OldRegimeTaxEngine`, `HraExemptionCalculator`, `MarginalReliefCalculator`, `FormulaEvaluator`, `AllowanceResolutionService`, `PerquisiteValuationService`, `TaxComputationService`, `RegimeAdvisor`, `RestructuringEngine`, plus §12 documenting the absence of Form 16 parsing and TDS projection with the grep evidence, and the orphaned `ParsedCtcDto` in full |

No file exceeded 3,000 lines, so none was split.

---

## Files scanned

| Category | Count |
|---|---|
| Java source in `taxplanning/` (main) | 56 |
| Java test files covering the module | 7 |
| Seed data JSON (`resources/data/tax-policies/`) | 5 |
| Thymeleaf template (`resources/templates/taxplanning/`) | 1 |
| Supporting files read outside the module for evidence | 10 — `pom.xml`, `backend/pom.xml`, `auth/config/AuthConfig.java`, `auth/filter/AuthFilter.java`, `auth/service/UserValidator.java`, `shared/util/expression/SafeExpressions.java`, `shared/dto/enums/EntityStatus.java`, `shared/entity/helper/AuditMetadata.java`, `shared/dto/user/UserMail.java`, `integration/AbstractIntegrationTest.java` |
| **Total** | **79** |

---

## Headline findings

Five things a reviewer should know before opening any other file:

1. **The module is a leaf.** No production code outside `taxplanning` imports it — the only
   inbound references in the whole repository are 8 imports in one integration test
   (`01-overview.md` §3). It is reachable solely through its two HTTP controllers.
2. **There is no Form 16 handling and no TDS anything.** Not partial — entirely absent. No parser,
   no entity, no endpoint, no upload path, no PDF-reading library. Every figure the module computes
   with is typed in by the caller (`03-coverage-matrix.md` §4–§5, `05-core-code.md` §12).
3. **Only salary income is modelled.** No interest, dividends, house property, other sources or
   business income. The one capital-gains field, `TaxComputationEntity.capitalGainsResult`, is typed
   `Object` and never written (`03-coverage-matrix.md` §6).
4. **Only tax year `2025-26` exists**, hardcoded in the seeder in four places and in both engines.
   The entity that would model FY/AY, governing act and effective dates —
   `TaxYearRegistryEntity` — is never read or written (`03-coverage-matrix.md` §7, Observation O-4).
5. **Five whole classes and roughly twenty methods/fields have no production caller**, including
   `MarginalReliefCalculator` — which has seven passing tests while the relief that actually runs is
   a different inline implementation (Observations O-2, O-19).

The 36 Observations in `04-constants-tests-history.md` §5 are recorded without judgement and
without action. Two are worth flagging here as most likely to change a reviewer's plan:

- **O-1** — the `limitFormula` strings seeded into `allowance_limits` use bare identifiers and a
  `min(...)` call, while the evaluator is SpEL (which reads variables as `#name` and has no `min`).
  Every engine call site catches the failure and substitutes `0`. This is the one finding in the
  pack whose runtime consequence was **not verified by execution** — see the `UNKNOWN` below.
- **O-16** — an integration test supersedes the seeded new-regime slab policy in a collection the
  test harness deliberately protects from cleanup.

---

## Everything marked `UNKNOWN`

Exactly one item in this pack is marked `UNKNOWN`.

| # | What | Where | What was checked | What would resolve it |
|---|---|---|---|---|
| U-1 | The **runtime** outcome of evaluating the seeded `limitFormula` strings — e.g. `min(0.14 * (basic + da), actual_nps)` — and therefore whether the employer-NPS 80CCD(2) exemption is always `0` in production | `04-constants-tests-history.md` §5, Observation O-1 | Statically: `FormulaEvaluator` uses `SpelExpressionParser` with variables bound through `context::setVariable` (`engine/FormulaEvaluator.java:29,43-44`); `FormulaEvaluatorSandboxTest.java:42,48` shows working formulas use the `#name` form; SpEL has no built-in `min`; both engines catch every exception from `evaluate` and return `0L` (`OldRegimeTaxEngine.java:339-344`, `NewRegimeTaxEngine.java:207-212`); no test exercises a formula string that is actually seeded, because every unit test mocks the resolver to return a limit with a **null** formula | Run `new FormulaEvaluator().evaluate("min(0.14 * (basic + da), actual_nps)", Map.of("basic", 1L, "da", 1L, "actual_nps", 1L))` and observe whether it throws. I did not execute it, because doing so was outside the read-only brief and the result would have been an inference either way without a test in the tree |

Everything else in the pack is asserted from source with a `file:line` citation, or is explicitly
recorded as `NOT FOUND` with the grep that established the absence.

---

## Open questions — only the developer can answer these

Grouped by what the answer would unblock. None of these is answerable from the code.

### Intended scope

1. **Is Form 16 in scope at all, and if so which part?** The brief for this pass named Form 16, but
   the module has no trace of it. `ParsedCtcDto` (`parsing/ParsedCtcDto.java`) describes a parsed
   **CTC or offer letter**, not a Form 16 — different document, different fields. Which was
   intended: Part A (TDS/challan reconciliation), Part B (the salary breakup), or CTC ingestion that
   was mislabelled?
2. **Was `ParsedCtcDto` written for an AI/LLM parse path that was then abandoned, or one not yet
   built?** Its `confidence`, `unmappedComponents` and `parsingNotes` fields
   (`parsing/ParsedCtcDto.java:43-45`), together with the orphaned `sourceText` and `parsedByAi`
   fields on `SalaryProfileEntity` (`:114-118`), suggest a specific design. Knowing which would
   determine whether extending it means reviving a plan or starting fresh.
3. **Is employer-side TDS (Section 192 projection, Form 12BB, previous-employer income) a planned
   direction, or deliberately out of scope?** The module today is purely employee-facing and
   annual. Adding a monthly projection would change the shape of `SalaryProfileEntity`, which has no
   date fields at all.
4. **Are the other income heads — interest, dividends, house property, other sources — planned?**
   `TaxResult.taxableIncome` is currently `gross − standardDeduction − exemptions − deductions`
   (`AbstractTaxEngine.java:69`) with no aggregation point for another head.
5. **Should this module read realised capital gains from `portfolio`?** `portfolio` is not in
   `allowedDependencies` (`package-info.java:1`), `capitalGainsResult` is never written, and
   `ItrFormAdvisor` takes a `hasCapitalGains` flag nobody supplies. Wiring it would require a
   modulith dependency change and would fail `WealthLensModulithTest` until declared.

### Known limitations — which of these are known and accepted?

6. **The surcharge thresholds.** Seeded at ₹5 crore while both engine Javadocs say ₹50 lakh, and
   two tests assert `0` with comments acknowledging the gap (Observation O-3). Is the seed data
   wrong, the Javadoc wrong, or is high-income surcharge simply out of scope for now?
7. **The employer-NPS formula (U-1 / O-1).** If confirmed, 80CCD(2) currently contributes nothing in
   either regime. Is this known?
8. **`MarginalReliefCalculator` versus the inline implementation** (O-2). Was the dedicated class
   meant to replace `AbstractTaxEngine.computeMarginalRelief`, or the other way round? Both exist;
   only the inline one runs; only the dedicated one is tested.
9. **Marginal relief applying to the old regime** (O-11). The shared template runs it for both
   regimes keyed off `rebate87aLimit`, which is ₹5,00,000 for the old regime. Intended?
10. **Full-value exemptions with no cap** — `BOOKS_PERIODICALS`, `TELEPHONE`, `UNIFORM` are
    exempted at 100% of whatever amount is entered (`OldRegimeTaxEngine.java:140-159`), and LTA
    likewise with no block-of-four-years logic despite three profile fields existing for it
    (`:286-293`). Placeholder, or deliberate simplification?
11. **The code-vocabulary split** (O-7). `allowance_catalogue` says `SECTION_80C`;
    `allowance_limits` says `80C`; the engines look up `80C`. Which is canonical? The answer decides
    whether `RestructuringEngine`'s eligibility filter is under-matching by design or by accident.
12. **`CityTier` versus `isMetroCity`.** Three representations of metro-ness exist —
    `CityTier.METRO_8` (stored, never read), `isMetroCity` (derived from a hardcoded 8-city list),
    and a second copy of that list in `HraExemptionCalculator` (never referenced). Which should
    survive?

### Target users and product intent

13. **Who is the intended user — an individual salaried employee, or an advisor/CA operating on
    behalf of several?** `SUPER_USER` can already read any user's profile
    (`UserValidator.java:26-28`), and the HR-letter and recommendation text
    (`AllowanceCatalogueEntity.hrAskTemplate`, `whatIfHrSaysNo`) reads as end-user-facing. The answer
    affects whether multi-profile and delegated access need first-class modelling.
14. **Is the restructuring feature meant to persist?** `RestructuringEngine.copyProfile` builds a
    detached entity that is never saved (`:281-311`), and `ProfileType.RESTRUCTURED` exists in the
    enum. Should a restructured profile become a saved scenario the user can compare against later?
15. **Should the PDF include the restructuring sections?** The template has them and the controller
    always passes `null`, so they never render (O-20). Was that a deliberate trim or an oversight?
16. **Is `HrLetterContentBuilder` meant to be exposed?** It is complete, unreferenced, and is the
    only place in the module citing "Income Tax Act 2025 and IT Rules 2026" rather than the 1961
    numbering used everywhere else (O-28).

### Operational

17. **How is policy data meant to be updated for a new tax year?** Today `PolicySeederService` runs
    at `@PostConstruct` with a hardcoded `"2025-26"` and skips if ACTIVE rows exist
    (`:54,73,91,118` and `:57-60`), and `allowance_limits` has **no** admin write endpoint at all —
    so after the initial seed those figures can only be changed directly in the database. Is a
    year-rollover process planned, and should it go through `tax_year_registry`?
18. **Should a failed seed stop the application?** All four seeders swallow their exceptions
    (O-18), so the app starts with missing policy data and the first `compute` returns a 400.
19. **What is the intended coverage bar?** PIT targets `taxplanning.engine.*` at an aggregate
    `mutationThreshold` of 85 shared with `brokercharges` (`pom.xml:399,408`), while no JaCoCo rule
    covers the module at all (`pom.xml:250,268`). Given the zero-coverage list in
    `04-constants-tests-history.md` §2.8 — `RestructuringEngine`, `RegimeAdvisor`,
    `TaxComputationService`, `SalaryProfileService.validateRequest`, `PolicySeederService` and the
    whole old-regime exemption set all have none — should a gate be added before extending?
20. **Is `NOT_DECIDED` a valid state to compute in?** `createProfile` defaults to it
    (`SalaryProfileService.java:56`), `TaxEngineFactory.getEngine` throws on it
    (`TaxEngineFactory.java:20`), and the compute path bypasses that factory method entirely so it
    never throws (O-35).
