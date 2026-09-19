# 01 — Product Requirements

Granular, numbered, and testable. Every acceptance criterion is written so that a test can decide it.
Design rationale lives in `docs/taxplanning-design/`; this file is the **contract**.

---

## 1. Glossary — read once, use these words only

| Term | Meaning | Never call it |
|---|---|---|
| **Tax year** | 1 April to 31 March. TY 2026-27 = 1 Apr 2026 to 31 Mar 2027 | financial year, assessment year, FY, AY |
| **Regime** | A set of rates and disallowances a taxpayer is computed under. There are **N**, not two | "old/new" in code or types |
| **Default regime** | The regime that applies with no action. For an individual it is `S202_DEFAULT` | "new regime" |
| **Rule** | One statutory provision, identified by a stable `RuleId` like `SAL-HRA-001` | "the section" |
| **Rule pack** | The immutable, checksummed data for one tax year, in four layers | "config", "seed data" |
| **Node** | One step in the computation graph. Declares `requires()` / `produces()` | "step", "stage", "calculator" |
| **Trace** | The ordered list of `TraceLine`s a computation produces. **It is the result** | "log", "audit" |
| **Draft** | One candidate filing. A person may have many per tax year | "profile", "return" |
| **Workspace** | Exactly one per (person, tax year). Holds drafts and cross-year ledgers | — |
| **Component** | One line of pay or payment, identified by a `ComponentCode` | "field", "head" |
| **`Money`** | `BigDecimal`-backed, scale 2, in `..taxengine.money..` | `double`, `TMoney`, `long` |
| **`Rate`** | A statutory percentage. `Rate.percent("30")` | `BigDecimal`, `double` |

---

## 2. Scope

**In (decisions D1–D7, `docs/taxplanning-design/00-SUMMARY.md`):**
individual salaried resident; tax years 2025-26 and 2026-27; income heads salary, house property,
other sources; capital gains **consumed** from `portfolio`, never recomputed; Form 16 / Form No. 130
ingestion; monthly TDS projection; JSON API, PDF, ITR suggestion with schedule-ready figures.

**Out:** e-filing; business income computation; NRI; multi-client advisor workspaces; AIS/26AS import;
investment product recommendations; runtime-editable tax rules.

---

## 3. Personas

| ID | Persona | Defining facts | Fixture |
|---|---|---|---|
| **PR-1** | **Ravi** — salaried, one employer | 31, Bengaluru, ₹28 LPA, rents at ₹42,000/month, holds equities in `portfolio`, sold some this year | `Fixtures.ravi()` |
| **PR-2** | **Priya** — changed jobs in October | two employers, two Form 16s, both granted a full standard deduction | `Fixtures.priya()` |
| **PR-3** | **Anand** — 62, retired | pension, bank interest, no business income | `Fixtures.anand()` |
| **PR-4** | **Meera** — high earner | above ₹50,00,000, surcharge and marginal relief, significant capital gains | `Fixtures.meera()` |

---

## 4. Features

Format: **ID · Story · Acceptance criteria (numbered, testable) · Depends on · Design ref · Tier**

### Tier MVP — the engine

#### F1 — Tax-year rule packs
*As the system, I load an immutable rule pack per tax year so a computation can never use the wrong year's rules.*

| # | Acceptance criterion |
|---|---|
| F1.1 | Two packs load at startup: `2025-26` and `2026-27` |
| F1.2 | A pack whose checksum does not match **fails application startup** with a message naming the pack and both checksums |
| F1.3 | A missing required pack **fails application startup** |
| F1.4 | Every rule lookup takes a `TaxYearId`; **no overload omits it** |
| F1.5 | A pack is never written at runtime; there is no admin write endpoint |
| F1.6 | Every rule row carries `sourceUrl`, `sourceDescription`, `draftOrFinal`, `accessedOn`, `status` |
| F1.7 | `status != VERIFIED` requires a non-blank `whatToCheck`, or the pack fails to load |
| F1.8 | The pack has four layers — `law/`, `exec/`, `forms/`, `content/` — each separately versioned and checksummed |
| F1.9 | Perturbing every value in `content/` and `forms/` leaves every computed `netPayable()` byte-identical |

Design: `05-domain-model.md` M1 · `10-extensibility.md` E3, E4 · `23-architecture-consolidation.md` A2

#### F2 — Core computation
*As Ravi I enter my figures and get tax under each regime with a full line-by-line trace.*

| # | Acceptance criterion |
|---|---|
| F2.1 | Golden tests `T1`–`T6` pass for **every** (tax year × regime) cell |
| F2.2 | Every trace line names its `RuleId`(s) |
| F2.3 | No node returns 0 by swallowing an exception; `taxengine` contains no `catch (Exception` |
| F2.4 | The trace contains **exactly two** `ROUNDING` lines — total income, and final payable |
| F2.5 | Slab bands partition the rupee line: `Σ` per-band tax `==` tax on the whole, exactly |
| F2.6 | Identical `EngineInputs` produce a byte-identical `TaxComputation` |
| F2.7 | A relief unavailable in a regime produces a trace line with `out = 0` **and a stated reason**, not an absent line |
| F2.8 | `taxengine` depends on no repository, no Spring Data, no `taxplanning` type, and calls no clock |

Design: `06-algorithms.md` G1 · `15-review-findings.md` Part B · `23` A3, A4, A6

#### F3 — Regime comparison
| # | Acceptance criterion |
|---|---|
| F3.1 | Returns one materialised scenario per **eligible** regime, not a fixed pair |
| F3.2 | Each scenario is diffed against the **default** regime |
| F3.3 | Drivers are attributed by a full outer join on `RuleId` |
| F3.4 | The six traps in `03-advisory.md` A1.4 each have a passing test |
| F3.5 | An ineligible regime is returned as `barred` **with its reason**, not omitted |

Design: `03-advisory.md` A1 · `10-extensibility.md` E6 · `17-regime-lifecycle.md` L3

#### F4 — Component entry
| # | Acceptance criterion |
|---|---|
| F4.1 | Every component code resolves in the pack's registry, or the request is **400** |
| F4.2 | Gross for a head is a sum over a **declared partition**; an unpartitioned code is an error, never a silent addend |
| F4.3 | Each code declares `cashNature`; `takeHome` is unaffected by any non-cash component |

Design: `10-extensibility.md` E4 · `15-review-findings.md` Part F

#### F5 — Exemptions: HRA, LTA, allowances
| # | Acceptance criterion |
|---|---|
| F5.1 | HRA is the least of three limbs and the trace records **which limb bound** |
| F5.2 | The HRA salary base includes DA where the terms of employment provide for it |
| F5.3 | HRA is computed **per occupation period**, with the metro percentage resolved per period |
| F5.4 | LTA respects: actual expenditure, fare class, two journeys per four-**calendar**-year block, and regime availability |
| F5.5 | Allowance limits come from the requested year's pack |
| F5.6 | Landlord PAN is demanded where annual rent exceeds the threshold in `SAL-HRA-006` |

Design: `01-rulebook.md` R9–R11 · `06-algorithms.md` G1.3 · `13-edge-cases.md` R-11

#### F6 — Chapter VIII deductions
| # | Acceptance criterion |
|---|---|
| F6.1 | Employee PF consumes 80C headroom |
| F6.2 | A contributor's own sub-cap is applied **before** the aggregate ceiling |
| F6.3 | 80D has two independent ceilings, a preventive sub-limit, a senior substitution and the exclusivity rule |
| F6.4 | Aggregate Chapter VIII never exceeds gross total income |

Design: `01-rulebook.md` R15 · `06-algorithms.md` G2

#### F7 — Surcharge, rebate, both marginal reliefs
| # | Acceptance criterion |
|---|---|
| F7.1 | Surcharge entry is at ₹50,00,000, not ₹5,00,00,000 |
| F7.2 | The default regime has **no 37% band** |
| F7.3 | Rebate marginal relief exists **only** in the default regime |
| F7.4 | The rebate is capped at tax computed at **slab rates only**, excluding special-rate income |
| F7.5 | Surcharge marginal relief is one implementation parameterised by a ladder |
| F7.6 | `T1` and `T2` pass in full |

Design: `01-rulebook.md` R4, R5 · `06-algorithms.md` G3 · `13-edge-cases.md` R-5

#### F8 — ITR form suggestion
| # | Acceptance criterion |
|---|---|
| F8.1 | Forms come from `forms/itr-forms.json`, not from code |
| F8.2 | Selection is a **deterministic cascade** in the Rules' own order |
| F8.3 | **Every** form's verdict is returned with its reasons, not only the winner |
| F8.4 | Speculative income routes to ITR-3 with an explanation |
| F8.5 | An updated return routes to ITR-UN regardless of income profile |
| F8.6 | Furnishing options include paper only where the age condition holds |

Design: `21-itr-forms.md`

#### F9 — Capital gains feed
| # | Acceptance criterion |
|---|---|
| F9.1 | Consumes a published read-model; imports no `portfolio` entity |
| F9.2 | Non-zero `UNCLASSIFIED` returns **409**, never a default |
| F9.3 | `SPECULATIVE` is excluded from capital gains and routes to ITR-3 |
| F9.4 | The summary is **pinned** to the draft; a newer `asOf` shows a stale notice, never a silent recompute |
| F9.5 | An unrecognised rate-basis hint is a blocking error, not a slab fallback |

Design: `07-architecture.md` X1.3 · `13-edge-cases.md` R-10 · `11-filing-drafts.md` F4.1

#### F10 — PDF report · F11 — Form 16 ingestion · F28 — Excel import
See `04-product-spec.md` and `19-engineering-foundations.md` E9.1. Acceptance criteria in
`03-WORK-ITEMS.md` at `W-118`–`W-132`.

### Tier MVP — the product surface

#### F29 — Refund or payable forecast *(home screen)*
| # | Acceptance criterion |
|---|---|
| F29.1 | Shows **three** figures separately: actual liability, projected employer deduction, other credits |
| F29.2 | States whether the result is a refund **after filing** or an amount **payable now** |
| F29.3 | Names the top three drivers by `RuleId` |
| F29.4 | Recomputes on every input change and on a portfolio `asOf` change |
| F29.5 | Derived from `FilingPosition`, not from a full pipeline run |

Design: `20-product-gaps.md` F29 · `23` A6, A10

#### F30 — Tax calendar
| # | Acceptance criterion |
|---|---|
| F30.1 | Entries are generated from the user's own facts, never a static list |
| F30.2 | Each entry carries the **measured** rupee cost of missing it |
| F30.3 | Includes the s.425(4) capital-gains carve-out where a gain exists |
| F30.4 | Past entries show what actually happened |

Design: `20-product-gaps.md` F30

#### F33 — Completeness weighted by measured worth
| # | Acceptance criterion |
|---|---|
| F33.1 | Weights are measured by recomputation, never a notional marginal rate |
| F33.2 | Items that **increase** tax are shown alongside those that reduce it |
| F33.3 | Ranked, top five surfaced |

#### F34 — Progressive onboarding
| # | Acceptance criterion |
|---|---|
| F34.1 | A valid computation exists after step 1 |
| F34.2 | Every step re-prices and narrows a stated confidence band |
| F34.3 | A coarse estimate is **labelled** coarse |

#### F36 — ITR schedule-ready export
| # | Acceptance criterion |
|---|---|
| F36.1 | A **fold over `ItrSchedule` pack data**, not a per-form mapper |
| F36.2 | Every figure traces to a rule or a node |
| F36.3 | A cell the computation cannot produce says so; it is never blank |
| F36.4 | Per-schedule copy |

#### F37 — Plain-language explanations
| # | Acceptance criterion |
|---|---|
| F37.1 | Templates live in `content/`, never in code |
| F37.2 | Every trace line has a template, or the `RuleId` is shown — never blank |
| F37.3 | The explanation names what would change the outcome, using `limitedBy` |

#### F40 — Claim playbooks
| # | Acceptance criterion |
|---|---|
| F40.1 | Every claimable rule has a catalogue entry, or the pack fails to load |
| F40.2 | The five claim channels are distinguished |
| F40.3 | 80G says *"only if the institution reported it"*; employer NPS says *"nothing to claim"* |
| F40.4 | Materiality uses **measured** tax effect |
| F40.5 | Materiality never suppresses an item that increases income |

#### F23–F25 — Claim integrity
See `12-claim-integrity.md` C2, C3. Statutory maximum, evidenced maximum, declared; statutory
overshoot **blocks**; documentation checklist names the counterparty.

### Tier Next
F12 TDS projection · F13 gap analysis · F14 optimiser · F15 break-even · F16 advance tax ·
F17 HR letter · F18 scenarios · F26 department-visibility panel · F27 year-on-year ·
F31 offer comparison · F32 life events · F35 multi-year view · F39 document vault

### Tier Later
F19 retirement events · F20 arrears relief · F21 advisor access · F22 NRI · F38 rent-vs-buy

---

## 5. Non-functional requirements

| ID | Requirement | Verified by |
|---|---|---|
| **N1** | Single compute p99 < 50 ms | benchmark in `W-140` |
| **N2** | Regime comparison (N=2) p99 < 100 ms | `W-140` |
| **N3** | Optimiser p99 < 3 s synchronous | `W-141` |
| **N4** | Document parse asynchronous, target < 30 s | `W-131` |
| **N5** | Rule packs load at startup only, never on the request path | `W-010` |
| **N6** | PAN, salary, health and disability data encrypted at rest; identifiers masked in every log | `W-133` |
| **N7** | Landlord and lender data excluded from any user-shared export | `W-134` |
| **N8** | `ACTED_ON` drafts and their computations retained 8 years, derived from the packs' carry-forward periods | `W-135` |
| **N9** | Every computation carries a receipt: `TXC-<year>-<6 chars>` | `W-107` |
| **N10** | JaCoCo: `taxengine` 95% line / 90% branch; `taxrules` 90% / 85% | build gate |
| **N11** | PIT mutation score ≥ 90% on cap shapes and marginal reliefs | build gate |
| **N12** | `./mvnw test -pl backend -Punit` under 20 s through the whole backlog | every item |

---

## 6. The five properties that must always hold

| # | Property | Test |
|---|---|---|
| **P1** | **Purity** — `taxengine` fetches nothing, reads no clock | ArchUnit, `W-004` |
| **P2** | **Determinism** — identical `EngineInputs` ⇒ identical `TaxComputation`, forever | `T23-4`, `W-045` |
| **P3** | **Layer isolation** — `content/` and `forms/` cannot change a number | `T23-1`, `W-030` |
| **P4** | **Derived order** — no hand-ordered step list; a node declares its edges | `T23-6`, `W-040` |
| **P5** | **Explained zeros** — an unavailable relief produces a line with a reason | `T23-8`, `W-062` |

---

## 7. Out of scope — do not build, do not stub

E-filing · business income computation · NRI and DTAA · advisor workspaces · AIS/26AS import ·
investment product recommendations · peer benchmarking ("people like you claim ₹X") · household or
spouse planning · runtime-editable rules · a formula DSL for rule limits.
