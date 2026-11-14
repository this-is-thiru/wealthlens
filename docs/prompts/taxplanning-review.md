# TASK: Expert review of the `taxplanning` module — law audit, engineering review, product roadmap

## ROLES
Work in all of these roles and tag every finding with the role that raised it:
- **[TAX]** Senior Indian income-tax researcher: statute, rules, CBDT circulars and notifications, case law.
- **[CA]** Chartered Accountant: computation accuracy, Form 16 / ITR mapping, employer compliance.
- **[CWM]** Chartered Wealth Manager: salary structuring, after-tax outcomes, decision quality for the user.
- **[ENG]** Senior backend engineer: correctness, null-safety, precision, testability.
- **[ARCH]** Senior backend architect: module boundaries, rule versioning, extensibility.
- **[PM]** Product manager: used only in Phase 7.

## INPUTS
- The context pack in `docs/taxplanning-context/` (files `00` through `05`) was produced at commit `55024ad`.
- Treat it as a map, not as truth:
    - Before relying on any claim from it, re-verify the claim against the current source.
    - If the source has changed since that commit, the source wins. Note the drift.
- It contains 36 Observations (O-1 to O-36) and one UNKNOWN (U-1). Reuse those IDs when you refer to them.

## RULES
1. **No production code changes.** Do not edit anything under `src/main`, the seed JSON, `pom.xml`, or any config.
2. **Throwaway tests are allowed**, to prove or disprove a behaviour:
    - Put them under `src/test/java/com/thiru/wealthlens/taxplanning/_review/`.
    - Run them, record the command and the output, then delete the folder.
    - At the end, `git status` must show only `docs/taxplanning-review/`.
    - Do not touch `git stash`.
3. **Cite code.** Every code claim needs a `path:line` reference. Every behavioural claim needs either a test run as evidence or the label `static reading only`.
4. **Cite law.** Every legal claim needs four parts:
    - the provision (section / rule / circular),
    - which Act it comes from (**Income-tax Act 1961** or **Income-tax Act 2025**),
    - the applicable tax year,
    - a status: `VERIFIED (source)`, `BELIEVED`, or `NEEDS VERIFICATION`.
5. **Check both regimes of law.** Today is September 2026:
    - Tax year **2025-26** falls under the 1961 Act and Rules.
    - Tax year **2026-27** falls under the Income-tax Act 2025 and its Rules.

   Check every rule against **both years**. Wherever the numbers or section references differ, record the value for each year.
6. **Verify recent law.** Anything that changed after your training cutoff must be checked against official sources (incometaxindia.gov.in, CBDT notifications, the Finance Act / Act text). Draft vs final Rules must be distinguished explicitly.
7. **Show the arithmetic.** Every tax-number finding must include a worked rupee example: the input, what the code produces, the correct figure, and the difference.

## PHASE 0 — Re-verify and close the unknown
1. Resolve **U-1 / O-1** by execution. Write a throwaway test that evaluates each seeded `limitFormula` through the real `FormulaEvaluator` and the real engine path, then record the result.
    - If employer-NPS 80CCD(2) comes out as ₹0, quantify the impact for three salaries: basic ₹6L, ₹12L and ₹24L, each with 10% and 14% NPS contributions.
2. Re-confirm O-3, O-11, O-14, O-27 and O-35 by execution in the same way.
3. Produce a short table: `Observation | Still true? | Evidence`.

## PHASE 1 — Law and computation audit [TAX][CA]
For each row below, give:
- status: `CORRECT` / `WRONG` / `PARTIAL` / `MISSING`,
- code evidence,
- the correct rule for tax year 2025-26 and for 2026-27,
- a worked rupee impact,
- severity.

**Hypotheses to confirm or refute.** These are my suspicions, not facts. Verify each one:

- **H-1 Surcharge thresholds.** The seed has bands starting at ₹5 Cr / 10 Cr / 20 Cr / 50 Cr. Verify the correct thresholds, the 25% cap in the new regime, and surcharge marginal relief (not implemented, even though `marginalReliefApplicable` is stored).
- **H-2 Rules 2026 values filed under 2025-26.** Check whether each value below belongs to the 1961-Act Rules for 2025-26 or to the Income-tax Rules 2026:
    - the 8-city HRA metro list,
    - meal ₹200 per meal,
    - gift ₹15,000,
    - children education allowance ₹3,000/month and hostel ₹9,000/month,
    - car perquisite ₹5k / 8k / 7k / 10k,
    - driver ₹3,000.

  Build a comparison table: `Item | 2025-26 value | 2026-27 value | Seed value | Verdict`.
- **H-3 Marginal relief in the old regime.** Check whether 87A marginal relief is legally available in the old regime (O-11).
- **H-4 HRA salary definition.** Check whether "salary" for HRA should include DA (where it forms part of retirement benefits) and commission on turnover. The code uses BASIC only.
- **H-5 Slab boundaries and rounding.** Check the one-rupee band error (O-14), per-slab rounding, and rounding of income and tax to the nearest ₹10 (Sections 288A and 288B, or their 2025-Act equivalents).
- **H-6 Employer PF.**
    - Treatment of employer PF above 12% of salary (basic + DA).
    - The aggregate ₹7.5L cap on employer PF + NPS + superannuation under 17(2)(vii).
    - Taxability of interest on the excess under 17(2)(viia).
- **H-7 80C interactions.**
    - Employee PF does not flow into 80C automatically. Check the double-count or under-count risk against the `investment80c` input.
    - The 80CCE aggregate ceiling.
    - 80CCD(1) inside 80C.
    - 80CCD(2) at 10% vs 14% for private-sector employers, by regime.
- **H-8 LTA.** The code exempts the full amount. Check the actual rule: travel cost only, 2 journeys per block, the current block years, carry-over, and non-availability in the new regime.
- **H-9 Uncapped allowances.** Books, telephone and uniform are exempted at 100% with no cap. Check allowance vs reimbursement treatment, 10(14) vs perquisite rules, and the requirement of actual expenditure.
- **H-10 Transport allowance.** The catalogue shows ₹19,200. Check the current eligibility (specially-abled only?) and the correct amount. Also note `isPhysicallyDisabled` is never read.
- **H-11 80D.** Check the self/family vs parents split, senior citizen limits, the preventive health check-up sub-limit, and the cash-payment restriction.
- **H-12 House property.**
    - 24(b) self-occupied vs let-out.
    - The ₹2L set-off limit on house property loss.
    - New-regime treatment of let-out property interest.
    - Pre-construction interest.
- **H-13 Professional tax.** Check the ₹2,500 cap and treatment by regime. The code deducts it in the old regime only, but subtracts it from take-home in both.
- **H-14 Retirement benefits.** Check gratuity, leave encashment and VRS exemption limits (the catalogue shows leave encashment as `null`), and whether these belong in an annual CTC model at all.
- **H-15 Net take-home.** It includes notional perquisites (O-13).
- **H-16 Regime choice.**
    - Whether the choice is binding for salaried users.
    - Default-regime rules.
    - Filing deadlines for opting into the old regime.
    - Whether `NOT_DECIDED` computing silently is acceptable (O-35).

**Then audit everything else in scope:** 87A for both years, standard deduction by year, cess, the full 13-step template in order, and every catalogue item's `itSection` against the correct 1961-Act and 2025-Act provisions.

## PHASE 2 — Advisory quality [CWM]
- **Regime advice.** Is the recommendation sound? Consider:
    - the break-even deduction level,
    - the hardcoded 30% marginal rate in `RegimeAdvisor`,
    - the fact that "annual saving" is not measured against the user's current regime (O-21).
- **Restructuring.** Is the advice sound? Consider:
    - pricing always at the new-regime marginal rate (O-23),
    - the code-vocabulary mismatch that empties the eligible set (O-7),
    - the fixed priority list,
    - ignoring employer feasibility (whether the employer offers a flexible benefits plan, FBP).
- **Missing high-value levers.** Check for:
    - employer NPS as the main lever in the new regime,
    - VPF vs NPS,
    - meal and phone reimbursement structuring,
    - timing of the regime choice.
- **Language risk.** Does any text in recommendations, the HR letter or the PDF overstate certainty, or cite the wrong Act?

## PHASE 3 — Engineering review [ENG]
Cover at minimum:
- all null-unboxing sites (O-9, O-12),
- divide-by-zero (O-10),
- exception swallowing that yields ₹0 silently (O-1, O-18, O-27),
- a GET that writes to the database (O-8),
- entities leaking through the API (O-30),
- no Bean Validation (O-29),
- `updateAllowance` dropping fields (O-17),
- test pollution of seeded collections (O-16),
- the misspelled fixture (O-15),
- dead code (O-19),
- two marginal-relief implementations (O-2),
- string-literal component codes with no enum or registry,
- `long` rupees vs `BigDecimal` and where rounding happens,
- concurrency on the computation upsert,
- the authorization edge for SUPER_USER cross-user access,
- PIT/JaCoCo gaps (O-22).

Rank each item by blast radius.

## PHASE 4 — Architecture review [ARCH]
- **Extensibility test.** For each change below, count the files you would touch today:
    1. a new tax year (2026-27),
    2. a Budget change to one slab rate,
    3. a new allowance,
    4. a new income head (house property or other sources),
    5. Form 16 ingestion,
    6. monthly TDS projection,
    7. switching section citations to the 2025 Act.
- **Rule data design.** Assess `tax_year_registry` (unused), `effectiveDate` (never populated), catalogue vs limits duplication with conflicting numbers, catalogue resolution that ignores the tax year (O-33), and year-rollover via the seeder.
- **Modulith boundaries.** Assess `package-info` and what a future `portfolio` capital-gains feed would require.

## PHASE 5 — Findings
1. **Praise.** List every edge case and design choice done well, with evidence and why it matters. Examples to check:
    - the Template Method engine,
    - policy-as-data,
    - running both regimes every time,
    - the SpEL sandbox hardening in `5eaeb98`,
    - supersession,
    - the three-way HRA minimum,
    - 87A marginal relief for the new regime.

   Be specific; generic praise is not allowed.
2. **Findings table.** Columns: `ID | Role | Severity | Area | Finding | Code evidence | Correct rule (2025-26 / 2026-27, with status) | ₹ impact example | Fix effort S/M/L`.
    - **Critical** = a wrong tax figure shown to the user, or a legal misstatement.
    - **High** = silent ₹0 or a crash on a realistic input.

## PHASE 6 — Golden test matrix
Write Given/When/Then cases with exact rupee inputs and expected outputs, computed by hand with the working shown, for both tax years where they differ. At minimum, cover:
- income exactly at, ₹1 above, and ₹50k above the 87A limit, in each regime,
- surcharge entry at ₹50L+ with marginal relief,
- metro vs non-metro HRA, with and without DA,
- employer NPS at 10% and 14%,
- employer PF above 12%,
- aggregate above the 17(2)(vii) cap,
- LTA,
- 80C with employee PF,
- let-out property loss,
- `NOT_DECIDED` profile,
- null component amount,
- missing policy row,
- every slab boundary.

Mark each case: `currently passes` / `currently fails` / `not testable today`.

## PHASE 7 — Product roadmap [PM]
For each feature, give:
- the problem and the persona,
- the user story,
- acceptance criteria,
- the algorithm or approach,
- the data needed,
- the legal basis (with status),
- the extensible design,
- dependencies,
- a RICE score,
- a phase: Now / Next / Later.

Cover at least:
1. **Tax-year engine.** Support 2026-27 under the Income-tax Act 2025:
    - a year registry as the root of all rules,
    - a section-citation mapping between the 1961 and 2025 Acts,
    - a rollover procedure.
2. **Form 16 ingestion.** Decide between Part A only, Part B only, or both:
    - PDF/text parsing strategy (traditional TRACES format),
    - validation,
    - reconciliation against the computed tax.

   Also decide whether to revive `ParsedCtcDto` for CTC and offer-letter parsing.
3. **Monthly TDS projection under Section 192** (or its 2025-Act equivalent):
    - mid-year joining,
    - salary revisions,
    - arrears with Section 89 relief,
    - multiple employers (Form 12B).
4. **Declarations vs proofs (Form 12BB)**, including tracking of proof deadlines.
5. **Other income heads** as pluggable modules: house property, other sources (interest with 80TTA/80TTB, dividends), and a capital-gains feed from `portfolio`.
6. **Regime optimizer.** Compute the break-even deduction level, and answer: "what must you invest to make the old regime win?"
7. **Salary structuring optimizer.** Replace the greedy priority list with constrained optimization:
    - objective: maximize post-tax take-home,
    - constraints: legal caps, employer FBP menu, proof burden.

   Also persist scenarios, which covers O-14 (restructured profile never saved).
8. **ITR mapping.** ITR-1 vs ITR-2 selection and a schedule-ready export.
9. **Explainability.** A per-step computation trace with the legal citation for every line, surfaced in the API and the PDF.
10. **Advance tax and self-assessment estimates** for salaried users with other income.

## PHASE 8 — Target architecture [ARCH]
Propose a design that satisfies the Open/Closed principle for years, heads and rules:
- `TaxYear` registry as the root, with governing Act, Rules, dates and default regime.
- Versioned rule packs with `effectiveFrom`/`effectiveTo`.
- A component-code registry replacing string literals.
- An income-head SPI (e.g. `IncomeHeadCalculator`).
- The 13-step template refactored into an ordered pipeline of `ComputationStep`s, each emitting a traced line.
- A single marginal-relief implementation.
- A deduction-cap aggregator (80CCE, 17(2)(vii)).
- A strategy per regime.
- A golden-file regression harness per tax year.
- DTOs at the API edge.

Include:
- a Mermaid class diagram,
- a Mermaid sequence diagram of the new compute flow,
- a migration plan from the current code in small, independently shippable steps, each with its tests.

## OUTPUT
Write to `docs/taxplanning-review/`:

| File | Contents |
|---|---|
| `00-SUMMARY.md` | Top 10 risks, top 10 roadmap items, and a list of every `NEEDS VERIFICATION` item with the official source to check |
| `01-verification.md` | Phase 0 |
| `02-law-audit.md` | Phases 1–2, including the 2025-26 vs 2026-27 comparison table |
| `03-engineering-architecture.md` | Phases 3–4 |
| `04-findings.md` | Phase 5 |
| `05-golden-tests.md` | Phase 6 |
| `06-roadmap.md` | Phase 7 |
| `07-target-architecture.md` | Phase 8 |

Write each file as you finish its phase. At the end:
- confirm `git status` shows only `docs/taxplanning-review/`,
- confirm the `_review` test folder is deleted,
- print `00-SUMMARY.md`.


## CHECKPOINTING (rate-limit safe, mandatory)

The run may stop at any moment. All state must live on disk, never only in your context.

### Progress file
Before any analysis, create `docs/taxplanning-review/PROGRESS.md` with exactly this checklist.
Every item starts as `[ ]`:

```
# Review progress — do not delete
Commit at start: <git rev-parse --short HEAD>
Last updated: <timestamp>
Throwaway test folder exists: yes/no

## Phase 0
[ ] P0.1 U-1/O-1 formula execution + NPS ₹ impact (3 salaries × 2 rates)
[ ] P0.2 O-3 re-verified
[ ] P0.3 O-11 re-verified
[ ] P0.4 O-14 re-verified
[ ] P0.5 O-27 re-verified
[ ] P0.6 O-35 re-verified
[ ] P0.7 Observation status table written

## Phase 1
[ ] H-1  [ ] H-2  [ ] H-3  [ ] H-4  [ ] H-5  [ ] H-6  [ ] H-7  [ ] H-8
[ ] H-9  [ ] H-10 [ ] H-11 [ ] H-12 [ ] H-13 [ ] H-14 [ ] H-15 [ ] H-16
[ ] P1.17 87A both years
[ ] P1.18 Standard deduction both years
[ ] P1.19 Cess + 13-step order
[ ] P1.20 All 39 catalogue itSection values checked
[ ] P1.21 2025-26 vs 2026-27 comparison table complete

## Phase 2
[ ] P2.1 Regime advice  [ ] P2.2 Restructuring  [ ] P2.3 Missing levers  [ ] P2.4 Language risk

## Phase 3
[ ] P3.1 Null/div-zero  [ ] P3.2 Swallowed exceptions  [ ] P3.3 API/validation/entities
[ ] P3.4 Test pollution/fixtures  [ ] P3.5 Dead code  [ ] P3.6 Codes/precision/rounding
[ ] P3.7 Concurrency/authz  [ ] P3.8 PIT/JaCoCo

## Phase 4
[ ] P4.1 Extensibility file-count table (7 scenarios)
[ ] P4.2 Rule data design
[ ] P4.3 Modulith boundaries

## Phase 5
[ ] P5.1 Praise  [ ] P5.2 Findings table

## Phase 6
[ ] P6.1 87A boundary cases  [ ] P6.2 Surcharge  [ ] P6.3 HRA  [ ] P6.4 NPS/PF/17(2)(vii)
[ ] P6.5 LTA/80C/house property  [ ] P6.6 Robustness cases  [ ] P6.7 Slab boundaries

## Phase 7
[ ] F1 [ ] F2 [ ] F3 [ ] F4 [ ] F5 [ ] F6 [ ] F7 [ ] F8 [ ] F9 [ ] F10

## Phase 8
[ ] P8.1 Design  [ ] P8.2 Class diagram  [ ] P8.3 Sequence diagram  [ ] P8.4 Migration plan

## Final
[ ] Throwaway tests deleted
[ ] git status clean except docs/taxplanning-review/
[ ] 00-SUMMARY.md written

## Verification log
<!-- one line per legal source actually opened: URL | what it confirmed | date accessed -->

## Notes for resume
<!-- the half-finished item, and exactly what remains to do on it -->
```

### Working rules
1. **Work one item at a time.** Write the item's output into its target file (e.g. `02-law-audit.md`) first. Only then flip it to `[x]` in `PROGRESS.md`.
    - An item is never `[x]` unless its output is on disk.
2. **Record interruptions.** If you are mid-item and it is large, write what you have so far, mark the item `[~]`, and record exactly what remains under "Notes for resume".
3. **Log sources as you go.** Every legal source you actually open goes in the Verification log immediately.
4. **Keep throwaway tests until the end.** Do not delete `_review/` tests until the Final section. They are evidence for resume. Record in PROGRESS.md whether the folder exists.
5. **Commit-drift check.** At the start of every session, compare `git rev-parse --short HEAD` with the commit recorded in PROGRESS.md. If they differ, stop and tell me before continuing.

## ANTI-HALLUCINATION (strict, overrides everything else)
1. **Code facts need evidence.** Every code claim needs `path:line` from a file you read in *this* session, or an executed test's output. The context pack alone is not evidence.
2. **Law facts need a source you opened.** Never state a section number, rule number, circular or notification number, rupee limit, rate, or date unless you opened the source in this session and logged it.
    - Otherwise write `NEEDS VERIFICATION`, and say what to check and where.
3. **No invented 2025-Act section numbers.** If you have not opened the Act text, write `2025-Act equivalent: NEEDS VERIFICATION`.
4. **Draft vs final Rules.** Always state which one a source is. If you cannot tell, write `STATUS UNCLEAR`.
5. **No invented URLs, circulars, case names, or test results.** A URL is cited only if you actually fetched it.
6. **Show rupee arithmetic.** Every ₹ figure shows its calculation step by step. If a number cannot be derived from verified inputs, do not present it.
7. **Mark the kind of claim.** Tag each statement: `FACT (evidence)`, `INFERENCE (from what)`, or `RECOMMENDATION`. Inferences must never be phrased as facts.
8. **Report absence honestly.** If a grep or search finds nothing, report the exact command and "0 hits". Do not conclude more than that.
9. **Keep hypotheses neutral.** H-1 to H-16 are my hypotheses, not facts. Refuting one is as valuable as confirming it. Do not bend evidence to fit.
10. **Uncertainty beats guessing.** When in doubt, write `UNKNOWN` and record it in `00-SUMMARY.md`. A guess is a failure; an honest unknown is not.