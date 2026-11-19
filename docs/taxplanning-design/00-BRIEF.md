# 00 — The brief

**Read this before any other document in this folder.** It is the founding statement of what this
module is for, who it serves and what it must survive. Every other design document is an elaboration
of it; where one disagrees with this brief, **this brief wins** and the other document is wrong.

| | |
|---|---|
| Written | 2026-09-23, from the product owner's own statement of intent (session 4) |
| Owner | the product owner (repository owner) |
| Supersedes | the scope statements in `00-SUMMARY.md` (D1, D7), `04-product-spec.md` P1, `01-PRD.md` §2 and §7 — those stay in place for history; each carries an amendment pointing here |
| Why it exists | the original brief (`docs/prompts/taxplanning-design.md`) is **empty in git** (0 lines, commit `15d36e8`). Twenty-five design documents cite "the brief" for their central requirement, and nothing on disk records it. This file is that record |
| Decisions it records | D-033 … D-047 (`docs/taxplanning-build/STATE.md`) |

---

## 1. The problem

Three layers, from the person inward.

**1.1 The taxpayer's problem.** A person (or a family, or later a business) needs to know, for a
tax year: *what do I owe, under which regime, which form do I file, what have I missed, and what do
I have to do by when?* Today the answers come from a blog calculator with last year's slabs, a
Form 16 read from the last page, or a CA at filing time — too late to change anything.

**1.2 Why the existing module cannot answer it.** The current `taxplanning` code (56 classes, still
serving `/tax-planning/**`) was audited (`02-lessons.md`, `01-rulebook.md` R24). It computes
**plausible, silently wrong tax**: surcharge from ₹5 Cr instead of ₹50 L (H-1); rebate marginal
relief in the old regime (H-3); slab bands one rupee short and no ₹10 rounding (H-5); HRA without
DA (H-4); LTA exempt in full (H-8); an employer-NPS exemption that probably always evaluates to ₹0
(O-1). In a tax calculator a wrong answer and a right one look identical, so none of these fail
visibly.

**1.3 Why this is hard, and what the design must survive.** Indian tax law changes faster and more
irregularly than code is usually built for:

| Fact | Consequence for the design |
|---|---|
| Two Acts in force at once — TY 2025-26 under the 1961 Act, TY 2026-27 under the Income-tax Act 2025, with different section numbers for the same idea | a rule's identity cannot be its section number |
| A year runs **several regimes concurrently** (the 2025 Act carries five regime sections, ss.200–205) | regimes are data, never a fixed pair |
| A regime lives **across many years**, and some are closed to new entrants while still in force | a regime has a lifetime independent of any one year |
| Rules are amended **inside** the year they govern (Rules 2026 amended at least four times) | versions are dated, not per-year |
| Amendments can be retrospective (Finance Act 2026: deemed from 1 June 2007) | a version records both the deemed and the actual date |
| Some rules key on a date other than the tax year (acquisition date, s.197(3)) | each rule declares which clock it reads |
| Some provisions are enacted and not yet in force, with a stated interim position | "pending notification" is a first-class state |

---

## 2. Who it is for

**Any assessee, for any kind of return.** The design must never assume the taxpayer is an
individual, salaried, resident, or without business income.

| Tier | Assessee types | Meaning |
|---|---|---|
| **Built first** | Individual — resident, all age bands | the first thing that must be correct end to end |
| **Built second** | **Hindu undivided family (HUF)** | the named second type; the model is proven extensible when HUF lands without restructuring |
| **Designed for, built later** | Individual non-resident and RNOR; firm; LLP; company (domestic, foreign); AOP / BOI; local authority; artificial juridical person; trust | the model must admit each **as data plus new managers**, never as a schema rewrite |

| Tier | Returns |
|---|---|
| **Built first** | the forms an individual without business income files (ITR-1, ITR-2), original and revised |
| **Designed for, built later** | ITR-3 (business income), ITR-4 (presumptive), ITR-5/6/7; belated and updated returns |

The primary persona remains "Ravi" (`04-product-spec.md` P1) because he is the first user whose
answers must be right. He is **not** the boundary of the model.

---

## 3. The concept model

```
Assessee  ─ one of a closed family: Individual │ Huf │ Firm │ Company │ …   (each with its own facts)
   │
   └─ files, for a TaxYear, a Return  ─ kind: original │ revised │ belated │ updated
         │
         ├─ under ONE Regime chosen from those in force that year and open to this assessee
         │     Regime ── has a lifetime (entry window, in-force window) spanning many years
         │           ── is COMPOSED of Rules by reference (rates, disallowance sets, eligibility)
         │
         ├─ computed by Managers, each evaluating the Rules of its kind
         │     Rule ──── a stable identity (a concept, not a year and not a section)
         │          └── a timeline of RuleVersions, each dated, sourced and carrying three statuses
         │
         └─ on a Form selected by eligibility, carrying a set of shared Schedules
```

### 3.1 Two corrections to the intuitive model (agreed 2026-09-23)

**(a) Forms: "ITR-1 is a subset of ITR-2" is true for *capability* and false for *eligibility*.**

- *Capability* — what a form can report — is nested: ITR-2 can carry everything ITR-1 can, and more.
  Modelled by **shared schedules**: ITR-1 and ITR-2 reference the same salary schedule rather than
  each declaring one (`21-itr-forms.md` I5).
- *Eligibility* — who may file which — is **mutually exclusive**. r.164(4) defines ITR-2 as for
  *"an individual not being an individual to whom sub-rule (2) applies"*: whoever can file ITR-1
  must file ITR-1. Modelled as a **deterministic cascade** in the Rules' own order (`21` I2, I4).
- The chain is not linear anyway: ITR-4 admits non-LLP firms (ITR-3 does not) but only presumptive
  income. **A form is (eligibility conditions) + (set of schedules)**, never a subclass of another.

**(b) Forms are not variations of a regime.** They are an independent axis. The form is selected
by **assessee type + income profile + return kind**; the regime is a choice **declared inside** the
return (r.136: for TY 2026-27 the option is exercised *in the return of income*). Coupling the two
would make adding either one an edit to the other.

---

## 4. Principles — non-negotiable

| # | Principle | Enforced by |
|---|---|---|
| **BP-1** | **Correct before complete.** Expected values in tests are derived by hand from the statute, never by running the code | golden suites (`08-golden-tests.md`), rule R3 |
| **BP-2** | **Law is data; arithmetic is code.** A new year, Budget change, allowance, deduction, regime or form is a data change. A new *kind* of arithmetic is a new manager | §6; `23` A5 |
| **BP-3** | **Nothing silent.** A rule that cannot be evaluated fails the computation. An unavailable relief produces a line with a reason, never an absent line or a zero | ArchUnit (no `catch (Exception`), `P5` |
| **BP-4** | **Every number explains itself.** The trace is the result; each line names its rule IDs, version and source | `TraceLine` |
| **BP-5** | **Reproducible forever.** A computation pins the exact rule pack it used; identical inputs + rule pack ⇒ byte-identical output | fingerprint (`W-038`), the pack's lock checksum |
| **BP-6** | **The engine is pure.** It reads no clock, fetches nothing, and depends on no persistence | ArchUnit `taxengine_fetches_nothing` |
| **BP-7** | **Every case is accounted for.** Every section of the law is classified — modelled, deliberately not modelled, or not yet — and CI fails on an unclassified one | §7, the coverage register |
| **BP-8** | **Status is visible.** An unverified rule is usable only with its status shown to the user | §5.3 |
| **BP-9** | **Extensible by the same path every time.** One decision ladder (§6.1) decides whether a change is data, a variant, or a new manager | review |

---

## 5. Rules as managed data (D-034, D-036, D-037)

### 5.1 One timeline, frozen per year — replaces "copy the pack every year"

Today a new year is created by `cp -r rulepacks/2026-27 rulepacks/2027-28` (`07-architecture.md`
X4.1). A regime that lives ten years is then ten copies, free to drift; a rule that changes once
in ten years is re-typed nine times.

**Decision D-034:** the law is authored **once**, as a timeline — one file per identity, grouped
by **layer**, because the layer decides whether a file may change a computed number (TS T4; the
cache key covers `law` + `exec` only; property P3 proves `forms` and `content` cannot):

```
rulepacks/
  timeline/
    law/rules/<RuleId>.json            ← one file per rule identity; its versions inside
        versions: [ v(2024-04-01 → 2025-03-31), v(2025-04-01 → …) ]   ← unchanged law = one version spanning years
    law/regimes/<RegimeCode>.json      ← one file per regime; lifetime inside it
    exec/pipelines/…  exec/components/<ComponentCode>.json
    forms/itr/<ItrFormCode>.json       ← one file per form; versions inside it
    content/sections/<SectionKey>.json ← the section catalogue (§6.3)
    content/coverage.json              ← the coverage register (§7.1) — content, so it can change no number
  2026-27.lock.json                    ← the compiled rule pack's identity: version ids + per-layer checksums
```

and each tax year's **rule pack** is **compiled** from it — frozen and checksummed, holding every
`PUBLISHED` version whose validity touches that year. "Rule pack" stays the one word for that
compiled, per-year artefact (TS T2, `W-027`); the timeline is its source:

```
timeline  ──RulePackCompiler(taxYear)──►  rule pack 2026-27  (immutable; checked against 2026-27.lock.json)
                                                   ▲
                                   every computation pins this, forever
```

The word **snapshot is not used** for rule packs: in this codebase it already names an input
projection taken at compute time (`FamilySnapshot`, `LedgerSnapshot`, `RegimeElectionSnapshot`).

What is kept from the current design: immutability, checksums, reproducibility, the pack as the
unit a computation pins (`W-027`). What goes: copying, and the drift it permits. Only 2 rule rows
exist today, so the change is cheap **now** and gets more expensive with every row.

### 5.2 Rule identity names a concept (D-035)

A `RuleId` names **what the rule is**, never the year it applies to and never "old/new":

| Today | Problem | Becomes |
|---|---|---|
| `RATE-SLAB-NEW-2026` + `RATE-SLAB-NEW-2025` | the year is in the ID, so an unchanged law needs a new ID every year (the rulebook says these two are *identical*); "NEW" breaks the glossary (`01-PRD.md` §1) | **one** rule, named for its concept, with one version spanning both years |
| `RATE-SLAB-OLD-2026` | same | one rule, named for its concept, versions per year and per age band |

The year lives in the **version**. The section lives in the **provision** (`provision1961`,
`provision2025`). Neither belongs in the identity — and the same holds for **regime codes**
(D-046 B4): one regime continuing from the 1961 Act into the 2025 Act is one identity.

**The concept names are taken from the Acts' wording and chosen by the owner (D-051, OQ-4 closed).**
The 2025 Act heads s.202 "New tax regime for individuals, Hindu undivided family and others." — so
"new" in `NEW_TAX_REGIME` / `RATE-SLAB-NEW-TAX-REGIME` is the **statute's word**, not a relative
label, and is the one place it is allowed. The opted-out regime has no name in either Act; it is
`FIRST_SCHEDULE_RATES` / `RATE-SLAB-FIRST-SCHEDULE`, after where its rates are. The rulebook's old →
new mapping is an erratum under `01-rulebook.md` R3 (`W-045d`) and `W-045d2`.

### 5.3 Three statuses, not one (D-036)

One `status` field is currently asked to mean three different things. They are separated:

| Axis | Values | Who sets it | What it gates |
|---|---|---|---|
| **Legal status** — what the law says about this version | `BILL` · `ENACTED` · `NOTIFIED` · `PENDING_NOTIFICATION` · `IN_FORCE` · `WITHDRAWN` · `STRUCK_DOWN` | the rule author, from the source | which versions apply on a date |
| **Verification** — how sure we are we transcribed it right | `UNVERIFIED` · `SOURCE_CHECKED` (read against the gazette) · `PROFESSIONALLY_REVIEWED` (a CA signed it) | a reviewer | what the user is told (BP-8) |
| **Publication** — where the version is in our workflow | `DRAFT` · `IN_REVIEW` · `PUBLISHED` · `SUPERSEDED` | the publish workflow | **only `PUBLISHED` versions enter a rule pack** |

A `DRAFT` version of a Budget proposal can power a clearly labelled "if the Finance Bill passes"
preview without ever touching a real computation.

### 5.4 Updatable over time — the publish gate (D-037)

Rules must be maintainable as the law moves, by a person, without a developer re-deriving the
design. The mechanism:

```
author a version (DRAFT)
   → validate  (shape, invariants, linter — 15 C4)
   → diff      (PackDiff: what numbers change, for which golden cases)
   → golden suite runs against the candidate rule pack
   → reviewer approves (IN_REVIEW → PUBLISHED)          ← the sign-off D-006 asks for
   → new rule pack compiled and locked; every earlier rule pack kept forever
```

**Where versions are authored is a `RuleSource` behind the existing `RulePackProvider` SPI.**

- **Now:** files in the repository, reviewed as a pull request. The gate above is CI.
- **Later:** an admin console that writes versions to a store and runs **the same gate server-side**
  before publishing.

The old module's admin-editable policies are a recorded failure (O-16) **because they had no gate**.
Editability without the gate is not permitted in either mode.

---

## 6. Managers — the extension contract

A **manager** is the code that knows how to evaluate **one kind of rule**. Rules are data; managers
are the only code. Adding a rule of an existing kind is data. Adding a new kind of rule is a new
manager — added deliberately, reviewed, and registered.

**Naming, so this does not become a second vocabulary:** "manager" is the product-level word and
the index in §6.2. In code a manager is one of the design's existing constructs — a group of
`ComputationNode`s, a sealed variant family (`CapShape`, `ReliefShape`, `Condition`, …), or an SPI —
and keeps that construct's name. `23-architecture-consolidation.md` A5: *a new word for something
that exists → nothing; use the existing word.*

### 6.1 The decision ladder — which kind of change is this?

```
Is it a VALUE the law states (a limit, rate, date, list)?     → a new RuleVersion        (data)
Is it a new COMBINATION of existing predicates / shapes?      → a new Rule               (data)
Is it a new SHAPE of arithmetic or of eligibility?            → a variant in a family    (small class, one-line justification naming the section)
Is it a new STEP in the computation?                          → a ComputationNode        (class, registered)
Is it a new KIND of thing altogether (a head, a person type)? → a new manager            (class(es) + registration + reviewer)
Is it a new source of data from outside?                      → an SPI implementation    (class + registration + reviewer)
```

**Startup refuses to run** if: data names a rule kind no manager handles; a regime references a
manager that is not registered; or a manager claims an assessee type the rules never mention.

### 6.2 The catalogue — every kind of rule the law contains

Twenty-eight managers, grouped by the role they play in a computation. **Status** — ✅ built ·
◐ designed (doc named) · ○ new in this brief. Sections are cited only where `01-rulebook.md` already
cites them (rule R2); the coverage register (§7) completes the mapping.

**A. Identity and scope**

| # | Manager | Handles | Sections (as cited) | Code | Status |
|---|---|---|---|---|---|
| M-01 | Tax-year resolver | tax year, Act in force, rule-pack selection | s.3(1) | `TaxYearRegistry` | ✅ |
| M-02 | Residence resolver | resident / RNOR / non-resident from facts | — (to source) | — | ○ later |
| M-03 | Assessee profile | per-type facts (§8) | — | `TaxpayerFacts` → `Assessee` family | ○ **now** |

**B. Income, head by head**

| # | Manager | Handles | Sections (as cited) | Code | Status |
|---|---|---|---|---|---|
| M-04 | Salary head | salary definition, gross, standard deduction, professional tax | s.16, s.19 | Phase 7 nodes | ◐ |
| M-05 | Perquisite valuer | car, accommodation, loans, ESOP, gifts, meals | s.17(1) (1961) / R12 | Phase 8 | ◐ — may need a **table-lookup** cap shape (valuation keyed on city band × engine size × who bears cost) |
| M-06 | House property head | annual value, self-occupied election, interest cap, pre-construction | s.21, s.22 | Phase 9 | ◐ |
| M-07 | Business / profession head | presumptive income (ITR-4), books-based (ITR-3) | s.58 (presumptive, as cited by `ITR-4-ELIG-001`) | — | ○ later |
| M-08 | Capital gains head | rate basis by asset, holding period, acquisition date; exemptions by reinvestment | s.197(3), s.198 | feed from `portfolio` only | ◐ **gap:** gains on assets `portfolio` does not hold (property, unlisted) have no manager |
| M-09 | Other sources head | interest, dividends, family pension, lottery | ss.92–94 | Phase 9 | ◐ |
| M-10 | Exemption manager | exempt income and allowances: HRA, LTA, special allowances, retirement benefits | Schedules; s.19(1) Table | `CapShape` family | ✅ shapes · ◐ nodes |

**C. Across heads and across years**

| # | Manager | Handles | Sections (as cited) | Code | Status |
|---|---|---|---|---|---|
| M-11 | Clubbing manager | income of spouse / minor / converted property added to another assessee | — (to source) | — | ○ later — the first manager that spans **two assessees** |
| M-12 | Set-off manager | intra-head, inter-head, house-property loss cap, absorption order | s.108 | Phase 9 | ◐ |
| M-13 | Loss ledger | losses carried forward, per assessee, across years | — | `LedgerSnapshot` | ◐ |

**D. Deductions**

| # | Manager | Handles | Sections (as cited) | Code | Status |
|---|---|---|---|---|---|
| M-14 | Deduction manager | Chapter VIII: investment-linked aggregate ceiling, payment-linked, income-linked, disability; sub-caps before ceiling; cap at gross total income; no deduction against special-rate income; regime disallowance sets | s.123, s.124, s.133, s.153 … | `AggregateWithCeiling`, `DisallowanceSet` | ✅ shapes · ✅ Chapter VIII node (salaried scope, both years) · ✅ catalogue (W-076a/b); s.153 W-075c5; ss.135, 137, 152 deferred |

**E. Tax**

| # | Manager | Handles | Sections (as cited) | Code | Status |
|---|---|---|---|---|---|
| M-15 | Rate manager | slab ladders by regime × assessee type × age band; special rates; flat rates → tax split by rate basis | s.202(1) | `SlabLadder`, `TaxByBasis` | ✅ types · ◐ nodes (W-046) |
| M-16 | Rebate and relief manager | rebate with marginal relief; arrears relief; later foreign-tax relief | s.156, s.157 | `ReliefShape` | ◐ W-048/49 |
| M-17 | Surcharge and cess manager | surcharge ladders with marginal relief, capped surcharge on special income, cess | R5, R6 | Phase 6 | ◐ |
| M-18 | Alternative minimum tax manager | a parallel computation whose higher result applies, for assessees claiming specified deductions (firms, companies) | — (to source) | — | ○ later |
| M-19 | Rounding | income and payable to ₹10 — exactly two points | s.516 | W-050 | ◐ |

**F. After tax**

| # | Manager | Handles | Sections (as cited) | Code | Status |
|---|---|---|---|---|---|
| M-20 | Credits manager | TDS, TCS, advance tax, self-assessment tax, certificates | s.392, s.395, ss.403–404 | Phase 9/12 | ◐ |
| M-21 | Interest and fee manager | late filing, shortfall, deferment (with the capital-gains carve-out), excess refund, late fee | ss.423–428 | `15` C1 | ◐ |

**G. Choices and time**

| # | Manager | Handles | Sections (as cited) | Code | Status |
|---|---|---|---|---|---|
| M-22 | Election manager | regime choice, which houses are self-occupied, set-off order, and other statutory choices | s.202(4), s.21(7)(a) | `ElectionState`, `15` C2 | ◐ |
| M-23 | Regime manager | regime lifecycle, eligibility, composition; `RegimeBehaviour` as last resort | ss.200–205 | `RegimeSpec`, `RegimeBehaviourRegistry` | ✅ |
| M-24 | Temporal apportionment | a value that changed mid-year, combined by a declared rule | — | `15` C3, W-066b | ◐ |

**H. Filing and evidence**

| # | Manager | Handles | Sections (as cited) | Code | Status |
|---|---|---|---|---|---|
| M-25 | Form selector | form by assessee type, income profile, return kind — cascade | r.164, s.263 | `21` I4 | ◐ |
| M-26 | Schedule projector | form cells derived from the trace | — | `21` I5, `FieldSource` | ◐ |
| M-27 | Evidence manager | proofs, landlord PAN threshold, payment mode, claim channels, what the department already sees | s.133(6) | `EvidenceRecorded`, `PaidOtherThanInCash`, `22` | ◐ |
| M-28 | Document ingestion | Form 16 / Form No. 130, later AIS | s.395 | `DocumentParser` SPI | ◐ |

**Read the ○ rows as the design's honest gaps.** M-03 is needed **now** (§8). M-08's gap matters as
soon as a user sells a flat. M-02, M-07, M-11 and M-18 arrive with the assessee types that need
them.

### 6.3 The section catalogue — every section, explained (D-042)

Rules are for the engine; people need something else. For every section of the law, a person
must be able to read: **what it is, who can use it, the different ways to qualify, the process to
claim it, and what disqualifies them** — in plain language, for the tax year they are in.

`22-claim-catalogue.md` already does part of this, but **per rule** and **only for claimable
rules**. The section catalogue is the layer above it: **one entry per section**, for every section
the coverage register (§7.1) marks `MODELLED` or `PLANNED`, referencing the per-rule claim entries
rather than repeating them.

#### The entry

```
SectionCatalogueEntry
  key                 SEC-INVESTMENT-DEDUCTION          ← a concept, like a RuleId (§5.2)
  provisions          { 1961: "s.80C", 2025: "s.123" }  ← both Acts; the year picks which is shown
  plainName           "Investments and payments deduction (the ₹1.5 lakh bucket)"
  summary             two or three sentences, no section numbers, no jargon
  whoCanUse           assessee types · residence · age bands        ← checked against the rules' Conditions
  availability        per regime, per tax year                      ← DERIVED from the rules, never typed
  limits              "{{DED-INVEST-AGG.ceiling}}"                  ← placeholders, rendered from the rule
  routes[]            the different ways to qualify                 ← see below
  process             claim channel (E / R / R+ / T / P) + ordered steps with deadlines   ← reuses 22 C4 ClaimStep
  disqualifiers[]     what stops it, and when                       ← see below
  evidence[]          what to keep, who issues it, how long to keep it
  interactions[]      sections it shares a limit with, or excludes
  commonMistakes[]
  history             what changed, year by year                    ← DERIVED from the rule's versions
  statuses            legal · verification · publication            ← §5.3, per entry
```

> **Erratum (W-045k, 2026-09-25):** the example `plainName` "(the ₹1.5 lakh bucket)" types a rupee
> figure, which SC-2 below forbids; `provisions` is not always a section in both Acts (HRA is
> Sch. III Table Sl. 11 in the 2025 Act) — a figure in a name comes only from a placeholder, and an
> entry's provisions are `Provision`s, not sections (D-060).
> Verified by: `SectionCatalogueTest.verify_whenTextTypesAFigureRateOrDate_isRefusedNamingTheField`; `01-rulebook.md` `SAL-HRA-001`.

**Routes — the different ways to achieve it.** Many sections can be satisfied in several ways,
each with its own conditions. Each route carries a name, a plain description, **its own
conditions** (as rule or `Condition` references), any lock-in or holding period, the evidence it
needs, and whom to ask. For the investments deduction, for instance, the routes are the eligible
instruments and payments, each sourced from the Act's own list (Schedule XV for the 2025 Act,
`01-rulebook.md` `DED-MAP-*`).

**Disqualifiers — what stops it, typed by when it bites.**

| Kind | When it bites | Example of the shape |
|---|---|---|
| `NOT_ELIGIBLE` | before anything is done — wrong assessee type, residence, age, or regime | a deduction the default regime disallows (s.202(2)) |
| `CONDITION_FAILED` | at the time of the claim — a condition of the route is not met | payment made in cash where the law requires otherwise (`PaidOtherThanInCash`) |
| `PROCESS_MISSED` | at filing — a step was not done, or not done in time | an election not made in the return (r.136); a certificate not obtained (`R+`) |
| `THIRD_PARTY_MISSING` | outside the person's control — the counterparty did not report or act | a donation the institution did not report (s.133(6), channel `T`) |
| `LATER_REVERSAL` | in a **later** year — a lock-in or holding condition is broken and the benefit is taken back as income | the shape of a lock-in breach; each instance sourced before it is written |

Each disqualifier **links to the rule or `Condition` that enforces it**, or says `NOT_MODELLED`
with a reason. That link is the point: the explanation and the computation are then the same
fact, so the text cannot tell a user they qualify while the engine says they do not.

#### Invariants

| # | Invariant | Checked by |
|---|---|---|
| SC-1 | every `MODELLED` section in the coverage register has a catalogue entry; a section once catalogued keeps one (ratchet) | CI |
| SC-2 | no rupee figure, rate or date is typed in catalogue text — only `{{RuleId.field}}` placeholders | CI (pattern scan) |
| SC-3 | every disqualifier and route condition references a rule or `Condition`, or is marked `NOT_MODELLED` with a reason | CI |
| SC-4 | `availability` and `history` are derived from rule versions, never authored | the renderer |
| SC-5 | the catalogue lives in `content/` and **cannot change a computed number** (the layer-isolation property, PRD P3) | T23-1 |

#### The product surface it enables

A **section explorer** (new feature **F41**): browse or search any section; for a signed-in
person, each entry is annotated from their own computation — *applies to you · does not apply,
because … · you are using ₹X of ₹Y · this would change your tax by ₹Z*. The personal annotations
come from the trace, never from the catalogue text.

---

## 7. Covering every case (D-038)

"Think of all cases" is made a **checked property**, not an aspiration.

### 7.1 The section coverage register

A data file, `timeline/content/coverage.json`, lists **every section of both Acts**. `01-rulebook.md` cites
**105 distinct sections** today; the register covers the whole Act. Each section carries exactly
one classification:

| Classification | Meaning | Must carry |
|---|---|---|
| `MODELLED` | at least one rule implements it | its rule IDs and manager |
| `PLANNED` | in scope, not yet built | the assessee types it affects and the backlog item |
| `NOT_COMPUTATIONAL` | procedure: assessment, appeals, search, prosecution | nothing more |
| `OUT_OF_SCOPE` | deliberately excluded | a reason and who decided |

> **Erratum (W-045o, 2026-09-25):** the four classifications leave no place for what §9 designs for
> but has not scheduled — `PLANNED` needs a backlog item and `OUT_OF_SCOPE` means *excluded* — so a
> fifth is added: **`DEFERRED`**, carrying a scope tier (`BUILT_FIRST` = in the first scope but not
> yet itemised, a backlog gap; `BUILT_SECOND`; `BUILT_LATER`) and the modelled assessee types it is
> known to affect (empty where not yet determined, or where it reaches only types not modelled yet) (D-066).
> Verified by: `Coverage.Deferred`; `CoverageRegisterTest`; `plans/W-045o.md` finding 2.

**CI fails** on an unclassified section, on a `MODELLED` section whose rules are not in any
published rule pack, and on a rule citing a section the register does not know. This is how "~100
sections, 30–40 in the new regime, maybe more" becomes a number the build reports rather than a
number anyone has to remember.

### 7.2 The case axes

Every golden-test suite and every manager is checked against these axes. A manager declares which
it supports; an unsupported combination is a **refusal with a reason**, never a guess.

| Axis | Values |
|---|---|
| Assessee type | individual, HUF, firm, LLP, company, AOP/BOI, local authority, AJP, trust |
| Residence | resident, RNOR, non-resident |
| Age band (individuals) | below 60, 60–80, 80 and above — **tested on date of birth**, since "at any time during the year" is not an age |
| Tax year × Act | 2025-26 (1961 Act), 2026-27 (2025 Act), … |
| Tax-year length | a full year (1 April – 31 March); a **short first year** for a newly set-up business or newly arising source (`YR-MODEL-003`) — `TaxYearId` today assumes the full year |
| Regime | every regime in force and open to the assessee that year |
| Income heads present | salary, house property, business/profession, capital gains, other sources — any combination |
| Special-rate income | none, some, most (the rebate trap, `13` R-5) |
| Clock | tax year, event date, acquisition date, sanction date, payment date, filing date |
| Mid-year change | none, declared combination, undeclared (must fail) |
| Retrospective or pending law | none, retrospective version, pending notification with interim rule |
| Elections | regime, houses, set-off order, … — default and each alternative |
| Employment | none, one, sequential, **concurrent** |
| Losses | none, current year, brought forward |
| Clubbing | none, spouse, minor, converted property |
| Return kind | original, revised, belated, updated |
| Evidence | proved, declared only, missing, paid in cash |

---

## 8. Assessees as a family of types (D-039)

**Today:** one record, `TaxpayerFacts(dateOfBirth, residentialStatus, Set<PersonType> personTypes,
disability, dateOfDeath)`, and `PersonType` has a single value, `INDIVIDUAL`. Two defects: the facts
are an individual's (an HUF has no date of birth and no disability), and a person has exactly one
type, so a **set** of types is a modelling error.

**Decision:** a closed family, one record per assessee type, each carrying **only the facts that
type has**:

```java
sealed interface Assessee permits Individual, Huf /*, Firm, Company, … as they are built */ {
    PersonType type();                 // the existing word is kept (23 A5); it gains HUF
    ResidentialStatus residence();
}
record Individual(LocalDate dateOfBirth, ResidentialStatus residence,
                  Optional<DisabilitySeverity> disability, Optional<LocalDate> dateOfDeath) implements Assessee {}
record Huf(ResidentialStatus residence) implements Assessee {}
                                       // karta and members arrive with the first rule that reads them (rule B2)
```

Rules select assessee types through the existing `Condition` algebra (`HasPersonType`) and
`RegimeSpec.appliesTo`; nothing else needs to know the family exists.

What differs by type is **data**, and each row must be sourced in `01-rulebook.md` before any code
reads it (rule R1). The shape of the differences, to be verified row by row:

| Differs by type | Examples to verify |
|---|---|
| which heads can arise | an HUF does not earn a salary |
| which rates apply | slab ladders for individuals and HUFs; flat rates for firms and companies |
| age-band slabs | individuals only |
| rebate | resident individuals only (`REBATE-*`) |
| deductions | per-section eligibility — e.g. `DED-80TTA-001` names "an individual … (or an HUF)" |
| regimes open | per `RegimeSpec.appliesTo` — s.202 lists individual, HUF, AOP, BOI, AJP (`10-extensibility.md` E3) |
| forms | per `ItrFormSpec.appliesTo` — ITR-1 individual only; ITR-2/3 individual or HUF (`ITR-*-ELIG-001`) |

**Why now:** `TaxpayerFacts` is referenced by roughly ten files and no computation node. After
Phase 5 it will be read by every node.

---

## 9. Scope (D-033)

| | |
|---|---|
| **Designed for** | every assessee type, every ITR form, every return kind, every regime in force, every income head |
| **Built first** | resident individuals; salary, house property, other sources, and capital gains consumed from `portfolio`; ITR-1 and ITR-2; **TY 2025-26 and TY 2026-27 together** (D-041 — the same years `portfolio` covers, so a person's gains and their tax always line up) |
| **Built second** | HUF; capital gains on assets outside `portfolio` |
| **Built later** | non-residents; business and presumptive income; firms, LLPs, companies; clubbing; alternative minimum tax |
| **Not modelled** | procedure (assessment, appeals, search, prosecution); e-filing submission itself |
| **Never** | a string formula language for rule limits (`D-NOFORMULA`); rule edits that skip the publish gate |

"Out of scope" in older documents means **not built yet**, not **not designed for**. No type, enum
or interface may be narrowed to the built-first scope in a way that makes a designed-for case a
schema change. A one-valued enum is fine; a record whose fields assume an individual is not.

---

## 10. Naming standards (D-040)

| What | Standard | Example |
|---|---|---|
| Rule ID | the concept; no year, no "old/new" (except the statutory "New tax regime"), no section number — enforced by `RuleIdNamingTest` | `RATE-SLAB-NEW-TAX-REGIME`, `RATE-SLAB-FIRST-SCHEDULE` (D-051) |
| Version | carries the dates, the provision and the statuses | `validity: 2026-04-01 →` |
| Regime code | the concept, like a rule ID (D-046 B4); the provision is data, per Act | `NEW_TAX_REGIME`, `FIRST_SCHEDULE_RATES` (D-051) |
| Compiled per-year law | **rule pack** — never "snapshot", which names input projections | `RulePackCompiler`, `PackDiff`, `rulepacks/2026-27.lock.json` |
| Item commit | `taxplanning W-0nn: <verb> <types> — <why, briefly>` | `taxplanning W-043: add TaxComputation, FilingPosition, ComputationReceipt` |
| Cross-document references | prefixed by document, because bare IDs collide (`T1`, `B2`, `F4`, `D-17`/`D-017` each mean two or more things) | `TS-T1`, `GT-T1`, `DR15-B2`, `PRD-F4` |
| Build decisions | `D-0nn` in `STATE.md` only; design rules keep `D-WORD`; review-finding defects are cited as `DR15-D-n` | `D-034`, `D-PURE`, `DR15-D-17` |

---

## 11. What this changes

| Area | Change | Owner |
|---|---|---|
| `01-PRD.md` §2, §7 | amended to point here (D-033) | done with this brief |
| `03-WORK-ITEMS.md` | **Phase 4b** — every prerequisite of `W-046`, collected in one place (D-043): rule-ID rename, three statuses, timeline + snapshot compiler, publish diff, generated `NodeIds`, coverage register, section catalogue, `Assessee` family, 2025-26 verification, legacy-endpoint warning, and the backlog/design housekeeping | done with this brief |
| `03-WORK-ITEMS.md` | prerequisites that belong **later**, placed where they are needed: `W-052b` (result → `TaxComputation`), `W-052c` (2025-26 versions of the Phase 5 rules), I3 switched on at `W-053`, and the owed-later list (HUF sourcing, non-portfolio capital gains, admin console, legacy deletions) | done with this brief |
| Live system | the old `/tax-planning/**/compute` endpoint serves known-wrong tax (H-1, H-3, H-5) until replaced; **disable or label it** | owner's call |
| `STATE.md` | D-033 … D-040 recorded; `W-046` held until the items above exist | done with this brief |

---

## 12. Still open

| # | Question, plainly | Recommendation | Needed by |
|---|---|---|---|
| ~~OQ-1~~ | **Closed (D-163): the recommendation is the decision.** When a rule changes (a Budget raises a limit), **who checks the new number before it goes live?** The publish gate (§5.4) has a "reviewer approves" step and nobody is named. (D-006) | **You approve every change now** (verification level `SOURCE_CHECKED`); **a CA reviews a sample** of golden cases before anyone outside the family relies on it (`PROFESSIONALLY_REVIEWED`) | before the first rule change after launch |
| ~~OQ-2~~ | **Closed (D-163): the recommendation is the decision.** Rules are edited **as files in the code** and go live with a deployment. A **web screen** could let a non-developer edit them instead. **When do you want that screen?** | **Not now.** After persistence and the API exist (milestone `W-111`); files + review are enough until then | before a non-developer edits a rule |
| OQ-3 | The **old** tax-planning feature is still live and returns numbers known to be wrong — e.g. at ₹60 L income (new regime) it charges no surcharge, understating tax by roughly ₹1.4 lakh. **Turn it off, warn on it, or leave it?** | **Warn now** (a visible "known errors — being replaced" flag on the response), **remove it** when the replacement passes its golden tests (`09-build-plan.md` B2) | now |
| ~~OQ-4 (old)~~ | ~~TY 2025-26?~~ | **Decided (D-041): build both years**, matching `portfolio` | — |
| ~~OQ-5~~ | ~~the swallow-failures rule~~ | **Decided (D-049): deleted** — Checkstyle `IllegalCatch` enforces the intent | — |
| ~~OQ-4~~ | ~~concept names for rule IDs and regime codes~~ | **Decided (D-051):** `NEW_TAX_REGIME` / `FIRST_SCHEDULE_RATES`; `RATE-SLAB-NEW-TAX-REGIME` / `RATE-SLAB-FIRST-SCHEDULE`; the remaining rulebook IDs at `W-045d2` | — |

---

## Changes

Current truth above; history here (D-046 B1 applies the same rule to this brief).

| Date | Change | Why |
|---|---|---|
| 2026-09-23 | §5.1 timeline laid out **by layer**; `coverage.json` placed in `content/` | review C1, C3 — the layer is what the cache key and P3 depend on |
| 2026-09-23 | "snapshot" replaced by **rule pack** / `RulePackCompiler` / `PackDiff` / `<taxYear>.lock.json` | review C2 — one word per concept; "snapshot" already names input projections |
| 2026-09-23 | case axis **tax-year length** added | review C4 — `YR-MODEL-003` |
| 2026-09-23 | section-numbered examples (`RATE-SLAB-S202-1`, `S202_DEFAULT`) removed; names deferred to `W-045d` as OQ-4 | review C5 — they contradicted D-035 and D-046 B4 |
| 2026-09-23 | open questions `O-n` → **`OQ-n`**; principles `B-n` → **`BP-n`** | `W-045b` — they collided with the placeholder observations `O-1`…`O-36` and with blockers / backlog rules (ID glossary, START-HERE §11) |
| 2026-09-23 | OQ-5 added | `W-045b2` found the swallow rule forbids defining exceptions |
| 2026-09-23 | OQ-5 decided: rule deleted | D-049 |
| 2026-09-23 | §5.1: the shipped pack holds **2** rule rows, not 15 | `W-045c` — the earlier figure counted `VERIFIED` markers |
| 2026-09-24 | §5.2, §10: concept names chosen; OQ-4 decided | `W-045d`, D-051 — the 2025 Act's s.202 heading is "New tax regime" (S2 p.226) |
| 2026-10-07 | M-14 status: the Chapter VIII node built for the salaried scope in both years; its catalogue entries W-076a/b | `W-076`, D-149 — built across W-075a–d5 (D-131) |
| 2026-10-08 | M-14 status: the catalogue complete for every built Chapter VIII section | `W-076b`, D-152 — after `W-076a` (D-150) |
| 2026-10-09 | §6.3 SC-1: `MODELLED` sections in full, a ratchet on catalogued ones — not every `PLANNED` section | `W-078f`, D-164 C4a — the check as D-060 approved it; 147 `PLANNED` sections are not built and could not route |
| 2026-10-09 | OQ-1, OQ-2 closed on their recommendations | D-163 — the owner ratified every open question's recorded answer |
