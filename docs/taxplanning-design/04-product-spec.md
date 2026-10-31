# 04 — Product spec

Scope is fixed by D1–D7. This file decides **what to build, in what order, and how to know it works**.
Everything is `RECOMMENDATION` unless it carries a rule ID.

---

## P1 — Personas and jobs to be done

### The primary persona (D1)

**Ravi — salaried, one employer, some investments.** Software engineer, 31, Bengaluru. ₹28 LPA CTC.
Rents at ₹42,000/month. Employer offers a flexible benefit plan he has never configured. Holds
equities tracked in this product's `portfolio` module; sold some this year. Has a ULIP he bought
because a relative sold it to him. Has never opened Form 16 beyond the last page.

**What he actually needs, in his words:** *"Which regime, and did I miss anything?"*

### Secondary personas — designed for, not built for

| Persona | Difference that matters | Decision |
|---|---|---|
| **Priya — switched jobs in October** | Two employers, two Form No. 130s, double-counted exemptions, under-deducted TDS | **In MVP.** `DOC-F130-MULTI-001` makes this a *document-ingestion* requirement, not an edge case, and the under-deduction is the single most expensive thing this product can catch |
| **Anand — 62, retired, pension + interest** | `RATE-SLAB-OLD-2025-B`, `DED-80TTB-001`, `ADV-SENIOR-001`, `DED-80D-007` | **Next.** All the rules exist in the pack; it is UI and test coverage, not new engine work |
| **Meera — CA with 40 clients** | Multi-tenant, bulk, client switching | **Later (D1).** Designed for in `X3`'s authorisation model; not built |
| **Sunil — NRI** | Residence determination, DTAA, different heads | **Out (D7).** Must not be *blocked*: the residence attribute exists on the profile from day one and every rule that depends on it (`RATE-SLAB-AGE-001`, `REBATE-OLD-001`, `DED-80DDB-001`, `DED-80U-001`) reads it, so adding NRI is new rules, not a schema change |

### Jobs to be done

| ID | Job | Today's alternative | Why this product can be better |
|---|---|---|---|
| **JTBD-1** | *Decide my regime for this tax year* | A blog's calculator with last year's slabs | It is an **annual** decision (`REG-OPTOUT-YEARLY-001`), it depends on facts the product already holds, and it needs both years' rule packs live at once (`YR-TRANSIT-001`) |
| **JTBD-2** | *Understand what my Form 16 actually says* | Squinting at a PDF | `DOC-F130-ROWS-001` is a 21-row structure that maps one-to-one onto the computation. Parsing it is the fastest path from zero to a complete picture |
| **JTBD-3** | *Check my employer's TDS is right and won't surprise me in March* | Nothing | `TDS-PROJ-001` — a running re-estimate the employee can mirror. The failure mode (proofs not filed, previous employer not declared) is common and expensive |
| **JTBD-4** | *Restructure my salary before the FBP window closes* | An HR portal with no tax view | `A2` — measured marginal value across a constrained allocation, with the regime choice inside the objective |
| **JTBD-5** | *Find the deductions I am leaving unused* | Memory | `A3` — headroom computed from the trace, with employee PF correctly consuming 80C (`H-7`) |
| **JTBD-6** | *Know what I owe and by when* | A due-date I half-remember | `ADV-INSTAL-001`, `ITR-DUE-*`. The D3 user with capital gains genuinely may owe advance tax |
| **JTBD-7** | *Get figures I can type into my ITR* | Re-deriving everything at filing time | D6 — `ITR-1-ELIG-001`/`ITR-1-BAR-001` decide the form; the trace supplies schedule-ready figures |

---

## P2 — Features

RICE is `(Reach × Impact × Confidence) ÷ Effort`. Reach is a share of the D1 persona (0–1). Impact is
1 (minor) to 3 (decisive). Confidence 0–1, and it is **lowered by unverified rules**, which is what
makes it useful here. Effort is person-weeks.

| ID | Feature | User story | Acceptance criteria | Rule IDs | R | I | C | E | RICE | Tier |
|---|---|---|---|---|---|---|---|---|---|---|
| **F1** | **Tax-year rule packs** | As the system, I load an immutable, checksummed rule pack per (tax year, Act) so a computation can never use the wrong year's rules | Two packs (TY 2025-26, TY 2026-27) load at startup; a missing or checksum-mismatched pack **fails startup** (`D-FAILFAST`); every rule lookup takes a `TaxYear` (`D-YEARKEY`); packs are immutable at runtime (`D-IMMUTABLE`); every row carries provenance and status (`D-PROVENANCE`) | `YR-ACT-001`…`-005`, all of R1 | 1.0 | 3 | 1.0 | 3 | **1.00** | **MVP** |
| **F2** | **Core computation, both regimes, both years** | As Ravi I enter salary, HP and other-sources figures and get tax under each regime with a full line-by-line trace | Golden tests `T1`–`T6` pass for (2 years × 2 regimes); every output line names its rule IDs; no step can return 0 by swallowing an exception (`D-NOSILENT`); rounding happens exactly twice (`RATE-ROUND-003`) | R3–R18 | 1.0 | 3 | 0.85 | 8 | **0.32** | **MVP** |
| **F3** | **Regime comparison with drivers** | As Ravi I see which regime costs less and exactly which rules drive the gap | Two materialised scenarios (`D-SCENARIO`); drivers attributed by rule-ID join; the six `A1.4` traps are covered by tests | `A1`, `REG-*` | 1.0 | 3 | 0.9 | 2 | **1.35** | **MVP** |
| **F4** | **Manual salary entry against a closed component vocabulary** | As Ravi I enter my CTC components and the system knows what each one is | Every component is a `ComponentCode` enum value (`D-ONECODE`); an unknown code is a 400, not a silent addend (`D-CLOSEDSUM`); each code declares `isCash()` (`D-CASHFLAG`) | `M2` | 1.0 | 3 | 1.0 | 2 | **1.50** | **MVP** |
| **F5** | **HRA, LTA, allowances and perquisites** | As Ravi I get the exemptions I am actually entitled to, with the limb that bound them named | `SAL-HRA-001` reports its winning limb; DA is in the salary base (`H-4`); LTA respects all four caps (`H-8`); allowance limits come from the year's pack (`H-2`); perquisites are flagged non-cash (`H-15`) | `SAL-HRA-*`, `SAL-LTA-*`, `SAL-ALW-*`, `PERQ-*` | 0.9 | 3 | **0.6** | 5 | **0.32** | **MVP** |
| **F6** | **Chapter VIII / Chapter VI-A with correct aggregation** | As Ravi I see my 80C headroom **after** employee PF, and my 80D as four interacting limits | `DED-80C-001` includes employee PF (`H-7`); the NPS sub-cap binds before the ₹1.5L ceiling; 80D has two ceilings, a preventive sub-limit, a senior substitution and the exclusivity rule (`H-11`); `DED-GTI-CAP-001` holds | R15, `G2` | 1.0 | 3 | 0.9 | 4 | **0.68** | **MVP** |
| **F7** | **Surcharge, rebate and both marginal reliefs** | As a high earner I am not told I owe a cliff-edge amount | Entry at ₹50,00,000 not ₹5 Cr (`H-1`); no 37% band in the new regime; both marginal reliefs implemented once each (`D-ONEIMPL`); rebate marginal relief **only** in the new regime (`H-3`) | `RATE-SUR-*`, `REBATE-*`, `G3` | 0.25 | 3 | 1.0 | 2 | **0.38** | **MVP** |
| **F8** | **ITR form suggestion with schedule-ready figures** | As Ravi I am told which ITR to file and given the numbers to type | Driven by `ItrFormSpec` pack data (`21-itr-forms.md`), not code; returns **every** form's verdict with reasons, not one form; deterministic cascade per r.164; speculative income routes to ITR-3 (`ITR-SPEC-001`); `ITR-1-BAR-002` flagged on the individual bar | R22, `ITR-*`, `21` | 0.9 | 2 | 0.8 | 3 | **0.48** | **MVP** |
| **F9** | **Capital-gains feed from `portfolio`** | As Ravi my realised gains appear in my tax picture without re-entry | Consumes a published read-model, not portfolio entities (`X1`); honours `countsAsCapitalGains()`; `SPECULATIVE` is surfaced as business income, not folded in; `UNCLASSIFIED` blocks with a message rather than defaulting | D3, `ITR-SPEC-001`, `RATE-SUR-SPLIT-001` | 0.7 | 3 | 0.85 | 3 | **0.60** | **MVP** |
| **F10** | **PDF report** | As Ravi I get a shareable document | Rendered from the trace (`D-VIEWFROMTRACE`); every figure carries rule IDs and status; `A4.3` disclaimer present; no unreachable section (O-20) | D6, `A4` | 0.8 | 2 | 0.95 | 2 | **0.76** | **MVP** |
| **F11** | **Form No. 130 / Form 16 ingestion — Part A and Part B** | As Ravi I upload my Form 16 and my figures are filled in | `DOC-F130-ROWS-001` fields extracted; a persisted, **user-confirmable** parse artefact with confidence and unmapped items (`K-13`); multi-employer documents keyed on `(TAN, period)` (`DOC-F130-MULTI-001`); reconciliation report vs the computation | D4, R21, `G5`, `M4` | 0.85 | 3 | 0.65 | 8 | **0.21** | **MVP** |
| **F12** | **Monthly s.192 / s.392 TDS projection** | As Ravi I see what will be deducted each month and whether March will hurt | Running re-estimate, not a schedule (`TDS-PROJ-001`); handles mid-year join, revision, previous employer and declaration→proof; respects the `TDS-SAL-REDUCE-001` asymmetry | D5, R20, `G4` | 0.7 | 3 | 0.75 | 6 | **0.26** | **Next** |
| **F13** | **Deduction gap analysis and next-best actions** | As Ravi I see what I have not used and what to do about it | Headroom from the trace, not a tally; ranked by **measured** marginal value (`D-MARGINAL`); the seven non-investment actions in `A3.3` are included | `A3` | 0.95 | 2 | 0.9 | 3 | **0.57** | **Next** |
| **F14** | **Salary structuring optimiser** | As Ravi I am told how to reconfigure my FBP before the window closes | Constraints C1–C11; measured marginal value; regime choice inside the objective; bounded exhaustive verification recorded in the trace | `A2`, `G6` | 0.6 | 3 | 0.7 | 6 | **0.21** | **Next** |
| **F15** | **Break-even deduction level** | As Ravi I am told how much old-regime relief I would need for the old regime to win | Numeric solve to ₹10; **plateau reported, not a false point** (`A1.3`) | `A1` | 0.8 | 2 | 0.85 | 2 | **0.68** | **Next** |
| **F16** | **Advance-tax schedule** | As Ravi with capital gains I am told what to pay and when | `ADV-SENIOR-001` exemption honoured; `ADV-THRESHOLD-001`; the four instalments with cumulative percentages; the declare-to-employer alternative offered (`A3.3`) | R23 | 0.5 | 2 | 0.9 | 2 | **0.45** | **Next** |
| **F17** | **HR letter generator** | As Ravi I get the text to send my HR to action a restructuring | Derived from the optimiser's recommendations; carries conditions and rule citations; no figure that is not in the trace | `A2`, `K-11` | 0.4 | 2 | 0.9 | 1 | **0.72** | **Next** |
| **F18** | **Scenario save and compare** | As Ravi I keep "current", "if I restructure" and "if I switch regime" side by side | Scenarios are first-class and immutable; any two are diffable by rule ID | `M5` | 0.5 | 2 | 0.95 | 3 | **0.32** | **Next** |
| **F19** | **Retirement and termination events** | As someone retiring I get gratuity, leave encashment, VRS and commuted pension right | Each of R14's rules implemented; the cross-year aggregate limits (`RET-GRAT-AGG-001`, `RET-LEAVE-AGG-001`) and the VRS once-in-a-lifetime rule (`RET-VRS-002`) enforced from stored history | R14 | 0.15 | 3 | **0.5** | 4 | **0.17** | **Later** |
| **F20** | **s.157 / s.89 arrears relief** | As someone who received arrears I get the relief computed | `REL-ARREARS-CALC-001` verified first; prior-year rule packs loaded; `REL-ARREARS-002` exclusivity with VRS enforced | R19 | 0.1 | 3 | **0.35** | 5 | **0.07** | **Later** |
| **F21** | **Multi-client advisor access** | As Meera I switch between clients | D1 says design, don't build. `X3` reserves the authorisation shape | D1 | — | — | — | — | — | **Later** |
| **F22** | **NRI / residence determination** | — | D7. Not blocked: the residence attribute exists from day one | D7 | — | — | — | — | — | **Later** |


### Feature register — all 40, and where each is defined

> **Read this first.** This document defines **F1–F22**. Sixteen later features are defined in other
> design documents, and two — **F28** and **F40** — are defined **here**, below, because they had no
> home at all. This table is the single index; without it, 18 of 40 features are invisible to anyone
> who opens "the product spec".

| ID | Feature | Tier | Defined in |
|---|---|---|---|
| F1–F22 | the table above | MVP / Next / Later | **this document** |
| F23 | Claim integrity panel (statutory / evidenced / declared) | MVP | `12-claim-integrity.md` C6 |
| F24 | Statutory validity checks | MVP | `12-claim-integrity.md` C6 |
| F25 | Documentation checklist | MVP | `12-claim-integrity.md` C6 |
| F26 | "What the department already sees" | Next | `12-claim-integrity.md` C6 |
| F27 | Year-on-year self-comparison | Next | `12-claim-integrity.md` C6 |
| **F28** | **Excel import of the salary structure** | **MVP** | **below** |
| F29 | Refund or payable forecast *(home screen)* | MVP | `20-product-gaps.md` F29 |
| F30 | Tax calendar | MVP | `20-product-gaps.md` F30 |
| F31 | Offer comparison, after tax | Next | `20-product-gaps.md` F31 |
| F32 | Life events | Next | `20-product-gaps.md` F32 |
| F33 | Completeness weighted by measured worth | MVP | `20-product-gaps.md` F33 |
| F34 | Progressive onboarding | MVP | `20-product-gaps.md` F34 |
| F35 | Multi-year view | Next | `20-product-gaps.md` F35 |
| F36 | ITR schedule-ready export | MVP | `20-product-gaps.md` F36 |
| F37 | Plain-language explanations | MVP | `20-product-gaps.md` F37 |
| F38 | Rent versus buy | Later | `20-product-gaps.md` F38 |
| F39 | Document vault | Next | `20-product-gaps.md` F39 |
| **F40** | **Claim playbooks** | **MVP** | **below** |

### F28 — Excel import of the salary structure

*As Ravi I upload the CTC spreadsheet HR already sent me, instead of typing thirty components.*

| # | Acceptance criterion |
|---|---|
| F28.1 | Reuses `shared/util/parser/{ExcelParser,ExcelSheet,ExcelColumn}` — no new parsing code |
| F28.2 | Every imported label resolves to a `ComponentCode`, or is **returned for mapping** |
| F28.3 | An unmapped label is **never guessed and never dropped** — the same discipline as `18-temporal-versioning.md` V6's `DraftUpcaster` |
| F28.4 | Imported values land as `EntryProvenance.source = IMPORTED`, reviewable before use |

RICE: R 0.8 · I 2 · C 0.9 · **E 1** → **1.44**. Design: `19-engineering-foundations.md` E9.1.

### F40 — Claim playbooks

*As Ravi I am told not just how much I can claim, but how to claim it, whom to ask, and whether I can still claim it when it is missing from my Form 16.*

| # | Acceptance criterion |
|---|---|
| F40.1 | Every claimable rule has a catalogue entry, or the pack **fails to load** (invariant I23) |
| F40.2 | The five claim channels (**E / R / R+ / T / P**) are distinguished |
| F40.3 | 80G says *"only allowable if the institution reported it"* (s.133(6)); employer NPS says *"nothing to claim"* |
| F40.4 | Materiality uses **measured** tax effect, never a notional marginal rate |
| F40.5 | Materiality **never** suppresses an item that increases income |
| F40.6 | Each step names the counterparty **and the exact artefact** to ask for |

RICE: R 1.0 · I 3 · C 0.9 · **E 3** → **1.00**. Design: `22-claim-catalogue.md`.

### Why F5 and F11 have low confidence

`INFERENCE`: F5's confidence is **0.6** because every TY 2025-26 value it needs —
`SAL-HRA-METRO-2025`, `SAL-ALW-LIMITS-2025`, `PERQ-CAR-2025` — is `NEEDS VERIFICATION`
(`01-rulebook.md`, S10/S11). It is correct for TY 2026-27 and unverified for TY 2025-26. F11's is
**0.65** because `DOC-F130-PREFILL-001` guarantees layout drift and the TY 2025-26 Form 16 structure
was never opened. **Lowering confidence for an unverified rule is the point of scoring it** — it
routes effort to the verification, not to more code.

### Cut lines

- **MVP = F1–F11.** A user can compute both years, both regimes, compare them, ingest a Form 16,
  get an ITR suggestion and a PDF. ≈ 40 person-weeks.
- **Next = F12–F18.** The advisory layer. It is deliberately second because every one of these
  features **prices actions by recomputing** (`D-MARGINAL`), so they are cheap once F2 is right and
  wrong in an invisible way if it is not.
- **Later = F19–F22.**

`RECOMMENDATION`: F11 (Form 16 ingestion) is in MVP despite the lowest RICE of the MVP set, because
it is the only feature that takes a user from **zero entry** to a **complete picture**. Every other
MVP feature is gated on data the user would otherwise type by hand, and the honest estimate of how
many users will type 30 salary components by hand is: very few.

---

## P3 — Out of scope

| # | Out | Why | What must not be foreclosed |
|---|---|---|---|
| 1 | **E-filing or any submission to the department** | D6 | Output figures must be *schedule-ready*, so a future export is a mapping, not a recomputation |
| 2 | **Employer-side payroll TDS** | D5 says employee-side projection | The projection model (`G4`) is the same arithmetic; only the actor differs |
| 3 | **Capital-gains computation** | D3 — consumed from `portfolio`, never recomputed | The read-model must carry enough to apply `RATE-SUR-SPLIT-001` (the 15% cap on the special-rate slice) |
| 4 | **Business or professional income** | D3 | `SETOFF-PGBP-001` and `ITR-3-ELIG-001` exist in the pack, so detecting and routing it is possible from day one. **`ITR-SPEC-001` makes this live**: intraday equity in `portfolio` *is* business income |
| 5 | **NRI and DTAA** | D7 | Residence attribute present from day one (`P1`) |
| 6 | **Multi-client advisor workspaces** | D1 | Authorisation shape reserved in `X3` |
| 7 | **GST, TDS other than on salary, wealth or gift tax** | — | — |
| 8 | **Prior-year filing (TY 2024-25 and earlier)** | D2 | The pack mechanism supports any year; only the packs are missing. `F20` needs this anyway |
| 9 | **Automatic AIS / TIS / Form 26AS import** | Needs authenticated department access | The reconciliation model (`M4`) is source-agnostic, so a future feed is another `DocumentParser` |
| 10 | **Investment product recommendations** | Regulatory | `A3` recommends *deduction headroom and actions*, never a named product |
| 11 | **Runtime-editable tax rules via an API** | `D-IMMUTABLE`, O-16 | Rule packs change by deploy |
| 12 | **A formula DSL for rule limits** | `D-NOFORMULA`, O-1 | Five typed cap shapes cover R1–R24; a sixth can be added |
