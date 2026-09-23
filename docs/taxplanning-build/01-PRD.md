# 01 — Product Requirements

**The contract, as currently true** (decision **D-046 B1**: rewritten 2026-09-23; history in
**Changes** at the end and in git). Every acceptance criterion is written so a test can decide it.
Read `docs/taxplanning-design/00-BRIEF.md` first — it is the why; this is the what; `02-TECH-SPEC.md`
is the how.

**Status marks** where an item is cited: ✅ `W-nnn` built · ○ `W-nnn` planned · ⚠ UNOWNED (no item
owns it yet — raise it, do not build it inside another item).

---

## 1. Glossary — use these words only

| Term | Meaning | Never call it |
|---|---|---|
| **Tax year** | 1 April to 31 March. TY 2026-27 = 1 Apr 2026 – 31 Mar 2027. A newly set-up business or newly arising source may have a **short first year** (`01-rulebook.md` `YR-MODEL-003`) | "financial year", "assessment year", "FY", "AY" — **in code** |
| **Year labels in the 1961 Act** | For a tax year governed by the **1961 Act**, the return is filed for an **assessment year**: **TY 2025-26 = FY 2025-26 = AY 2026-27** (`01-rulebook.md` `YR-MODEL-001`). The 2025 Act has one period only, the tax year (`YR-MODEL-002`). User-facing text shows the label the governing Act uses; code only ever holds a `TaxYearId` | an off-by-one year: never derive an AY by arithmetic outside this mapping |
| **Assessee** | The person a return is for: an individual, an HUF, and later firm, company, … One login (**account**) may own several assessee profiles | "user" (that is the login) |
| **Regime** | A set of rates and disallowances an assessee is computed under. There are **N**, not two; one may run across many years and both Acts | "old/new" in code or types |
| **Default regime** | The regime that applies with no action. Its **code names the concept**, chosen at `W-045d` (open question **O-4**) | "new regime" |
| **Rule** | One statutory provision, with a stable `RuleId` naming its **concept** — no year, no "old/new", no section number | "the section" |
| **Rule version** | A dated, sourced value of a rule, carrying three statuses: legal, verification, publication | — |
| **Timeline** | Where rules, regimes and forms are authored, once, with all their versions | "the packs" |
| **Rule pack** | The immutable, checksummed law for **one tax year**, compiled from the timeline's `PUBLISHED` versions, in four layers | "snapshot", "config", "seed data" |
| **Manager** | The code that evaluates one kind of rule (`00-BRIEF.md` §6) — in code, the existing construct's name | a new class suffix `Manager` (banned) |
| **Node** | One step in the computation graph. Declares `requires()` / `produces()` | "step", "stage", "calculator" |
| **Trace** | The ordered list of `TraceLine`s a computation produces. **It is the result** | "log", "audit" |
| **Draft** | One candidate filing. An assessee may have many per tax year | "profile", "return" |
| **Workspace** | Exactly one per (assessee profile, tax year). Holds drafts and cross-year ledgers | — |
| **Component** | One line of pay or payment, identified by a `ComponentCode` | "field", "head" |
| **`Money`** | `BigDecimal`-backed, scale 2, in `..taxrules.money..` | `double`, `TMoney`, `long` |
| **`Rate`** | A statutory percentage. `Rate.percent("30")` | `BigDecimal`, `double` |

---

## 2. Scope (`00-BRIEF.md` §2, §9)

| | |
|---|---|
| **Designed for** | every assessee type, every ITR form, every return kind, every regime in force, every income head |
| **Built first** | resident individuals; salary, house property, other sources, and capital gains **consumed** from `portfolio` (never recomputed); ITR-1 and ITR-2; original and revised returns; **TY 2025-26 and TY 2026-27** (D-041); Form 16 / Form No. 130 ingestion; JSON API, PDF, ITR suggestion with schedule-ready figures |
| **Built second** | HUF; capital gains on assets outside `portfolio` |
| **Built later** | non-residents; business and presumptive income; firms, LLPs, companies; clubbing; alternative minimum tax; monthly TDS projection and the other Tier Next features (§4) |
| **Not modelled** | procedure (assessment, appeals, search, prosecution); e-filing submission itself; AIS/26AS import; investment product recommendations; peer benchmarking |
| **Never** | a string formula language for rule limits; a rule edit that skips the publish gate |

"Built later" means **not built yet** — never "not designed for". No type may be narrowed so that a
designed-for case becomes a schema change.

---

## 3. Personas

| ID | Persona | Defining facts | Fixture |
|---|---|---|---|
| **PR-1** | **Ravi** — salaried, one employer | 31, Bengaluru, ₹28 LPA, rents at ₹42,000/month, holds equities in `portfolio`, sold some this year | `Fixtures.ravi()` ○ `W-145` |
| **PR-2** | **Priya** — changed jobs in October | two employers, two Form 16s, both granted a full standard deduction | `Fixtures.priya()` ○ `W-145` |
| **PR-3** | **Anand** — 62, retired | pension, bank interest, no business income | `Fixtures.anand()` ○ `W-145` |
| **PR-4** | **Meera** — high earner | above ₹50,00,000, surcharge and marginal relief, significant capital gains | `Fixtures.meera()` ○ `W-145` |
| **PR-5** | **An HUF** — the second assessee type | managed by its karta, who files separately as an individual under the **same account**; income and figures **to be sourced** in the rulebook before the first HUF item (no number is invented here) | ⚠ UNOWNED — lands with the first HUF item (`03-WORK-ITEMS.md` Appendix F) |

---

## 4. Features

Format: **ID · Story · Acceptance criteria (numbered, testable) · Design ref · Tier**

### Tier MVP — the engine

#### F1 — Tax-year rule packs
*As the system, I compute every year against an immutable rule pack compiled from one timeline, so a
computation can never use the wrong year's law and an unchanged law is never re-typed.*

| # | Acceptance criterion |
|---|---|
| F1.1 | Rule packs for **`2025-26` and `2026-27`** are compiled at startup from the timeline, each checked against its `<taxYear>.lock.json` |
| F1.2 | A rule pack that does not match its lock **fails application startup**, naming the pack, the rule and both checksums |
| F1.3 | A missing required rule pack **fails application startup** |
| F1.4 | Every rule lookup takes a `TaxYearId`; **no overload omits it** |
| F1.5 | A rule pack is never written at runtime. A rule changes only as a new **version**, through the publish gate (validate · diff · golden suite · reviewer); no endpoint writes around the gate |
| F1.6 | Every rule version carries its source (`sourceUrl`, `sourceDescription`, `accessedOn`) and **three statuses**: legal, verification, publication (D-036) |
| F1.7 | Only `PUBLISHED` versions enter a rule pack; an `UNVERIFIED` version requires a non-blank `whatToCheck`, or the pack fails to load |
| F1.8 | The rule pack has four layers — `law/`, `exec/`, `forms/`, `content/` — each separately versioned and checksummed |
| F1.9 | Perturbing every value in `content/` and `forms/` leaves every computed `netPayable()` byte-identical |
| F1.10 | Every section of both Acts is classified in the coverage register; the build fails on an unclassified section (D-038) |

Design: `00-BRIEF.md` §5, §7 · `05-domain-model.md` M1 · `10-extensibility.md` E3, E4 · `23` A2

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
| F2.9 | A case the engine does not support yet (an assessee type or a combination of facts) is **refused with its reason** — `UNSUPPORTED_ASSESSEE_TYPE` / `UNSUPPORTED_CASE` — never computed approximately |

Design: `06-algorithms.md` G1 · `15-review-findings.md` Part B · `23` A3, A4, A6 · `00-BRIEF.md` §7.2

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
| F4.1 | Every component code resolves in the rule pack's registry, or the request is **400** |
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
| F5.5 | Allowance limits come from the requested year's rule pack |
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
| F8.1 | Forms come from `forms/` in the rule pack, not from code |
| F8.2 | Selection is a **deterministic cascade** in the Rules' own order — forms are mutually exclusive by eligibility (r.164) and share schedules by capability (`00-BRIEF.md` §3.1) |
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
See `04-product-spec.md` and `19-engineering-foundations.md` E9.1. Owning items: F10 ○ `W-123`;
F11 ○ `W-124`–`W-132`; F28 ○ `W-133`.

### Tier MVP — the product surface

#### F29 — Refund or payable forecast *(home screen)* — ○ `W-113`
| # | Acceptance criterion |
|---|---|
| F29.1 | Shows **three** figures separately: actual liability, projected employer deduction, other credits |
| F29.2 | States whether the result is a refund **after filing** or an amount **payable now** |
| F29.3 | Names the top three drivers by `RuleId` |
| F29.4 | Recomputes on every input change and on a portfolio `asOf` change |
| F29.5 | Derived from `FilingPosition`, not from a full pipeline run |

Design: `20-product-gaps.md` F29 · `23` A6, A10

#### F30 — Tax calendar — ○ `W-114`
| # | Acceptance criterion |
|---|---|
| F30.1 | Entries are generated from the assessee's own facts, never a static list |
| F30.2 | Each entry carries the **measured** rupee cost of missing it |
| F30.3 | Includes the s.425(4) capital-gains carve-out where a gain exists |
| F30.4 | Past entries show what actually happened |

Design: `20-product-gaps.md` F30

#### F33 — Completeness weighted by measured worth — ○ `W-115`
| # | Acceptance criterion |
|---|---|
| F33.1 | Weights are measured by recomputation, never a notional marginal rate |
| F33.2 | Items that **increase** tax are shown alongside those that reduce it |
| F33.3 | Ranked, top five surfaced |

#### F34 — Progressive onboarding — ○ `W-122`
| # | Acceptance criterion |
|---|---|
| F34.1 | A valid computation exists after step 1 |
| F34.2 | Every step re-prices and narrows a stated confidence band |
| F34.3 | A coarse estimate is **labelled** coarse |

#### F36 — ITR schedule-ready export — ○ `W-093` (endpoint ⚠ UNOWNED)
| # | Acceptance criterion |
|---|---|
| F36.1 | A **fold over `ItrSchedule` pack data**, not a per-form mapper |
| F36.2 | Every figure traces to a rule or a node |
| F36.3 | A cell the computation cannot produce says so; it is never blank |
| F36.4 | Per-schedule copy |

#### F37 — Plain-language explanations — ○ `W-116`
| # | Acceptance criterion |
|---|---|
| F37.1 | Templates live in `content/`, never in code |
| F37.2 | Every trace line has a template, or the `RuleId` is shown — never blank |
| F37.3 | The explanation names what would change the outcome, using `limitedBy` |

#### F40 — Claim playbooks — ○ `W-117`, `W-118`, `W-121`
| # | Acceptance criterion |
|---|---|
| F40.1 | Every claimable rule has a catalogue entry, or the rule pack fails to load |
| F40.2 | The five claim channels are distinguished |
| F40.3 | 80G says *"only if the institution reported it"*; employer NPS says *"nothing to claim"* |
| F40.4 | Materiality uses **measured** tax effect |
| F40.5 | Materiality never suppresses an item that increases income |

#### F41 — Section explorer — ○ `W-045k` (catalogue), surface ⚠ UNOWNED
*As any assessee I can read what a section is, who can use it, the ways to qualify, how to claim it and
what disqualifies me — and, when signed in, whether it applies to me and what it is worth.*

| # | Acceptance criterion |
|---|---|
| F41.1 | Every `MODELLED` or `PLANNED` section in the coverage register has a catalogue entry (SC-1) |
| F41.2 | No figure is typed in catalogue text; limits and history render from rule versions (SC-2, SC-4) |
| F41.3 | Every disqualifier and route condition links to the rule or `Condition` that enforces it, or is marked `NOT_MODELLED` with a reason (SC-3) |
| F41.4 | Personal annotations ("applies to you", "you are using ₹X of ₹Y") come from the trace, never from catalogue text |
| F41.5 | The catalogue lives in `content/` and changes no computed number (SC-5) |

Design: `00-BRIEF.md` §6.3 · `22-claim-catalogue.md`

#### F23–F25 — Claim integrity — ○ `W-119`, `W-120`
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
| **N1** | Single compute p99 < 50 ms | ○ `W-143` |
| **N2** | Regime comparison (N=2) p99 < 100 ms | ○ `W-143` |
| **N3** | Optimiser p99 < 3 s synchronous | ○ `W-143` |
| **N4** | Document parse asynchronous, target < 30 s | ○ `W-131` |
| **N5** | Rule packs are compiled at startup only, never on the request path | ✅ `W-028` (provider); ○ `W-045g` (compiler) |
| **N6** | PAN, salary, health and disability data encrypted at rest; identifiers masked in every log | ○ `W-134` |
| **N7** | Landlord and lender data excluded from any user-shared export | ○ `W-135` |
| **N8** | `ACTED_ON` drafts and their computations retained 8 years, derived from the rule packs' carry-forward periods | ○ `W-136` |
| **N9** | Every computation carries a receipt: `TXC-<year>-<6 chars>` | ✅ `W-043` (type); ○ `W-107` (API) |
| **N10** | JaCoCo: `taxengine` 95% line / 90% branch; `taxrules` 90% / 85% | ○ `W-144` |
| **N11** | PIT mutation score ≥ 90% on cap shapes and marginal reliefs | ○ `W-144` |
| **N12** | `./mvnw test -pl backend -Punit` under 20 s through the whole backlog (9.6 s warm at `W-045a`) | every item |

---

## 6. The five properties that must always hold

| # | Property | Test | Status |
|---|---|---|---|
| **P1** | **Purity** — `taxengine` fetches nothing, reads no clock | ArchUnit (`taxengine_fetches_nothing`, `taxengine_has_no_clock`) | ✅ `W-004` |
| **P2** | **Determinism** — identical `EngineInputs` ⇒ identical `TaxComputation`, forever | `T23-4` | fingerprint ✅ `W-038`, runner ✅ `W-042`; **the T23-4 test ⚠ UNOWNED** |
| **P3** | **Layer isolation** — `content/` and `forms/` cannot change a number | `T23-1`, invariant I27 | ○ `W-146` |
| **P4** | **Derived order** — no hand-ordered step list; a node declares its edges | `T23-6` | ✅ `W-040`, `W-042` |
| **P5** | **Explained zeros** — an unavailable relief produces a line with a reason | `T23-8` | ⚠ UNOWNED |

---

## Changes

This document was append-only with errata and amendments until 2026-09-23; D-046 B1 replaced that
with current truth plus this log. Earlier text is in git (last version before the rewrite: commit
`db8a6b9`).

| Date | Change | Source |
|---|---|---|
| 2026-09-19 | `Money` lives in `..taxrules.money..` | B-001, D-012 |
| 2026-09-23 | Scope: designed for every assessee and form; built individual-first, HUF second; both tax years | D-033, D-041 |
| 2026-09-23 | **Rewritten as current truth.** Glossary: FY/AY mapping for 1961-Act years, *assessee*, *timeline*, *rule version*, *manager*; "rule pack", never "snapshot"; default-regime code deferred to O-4. F1 restated for the timeline, lock files, three statuses and the publish gate (F1.10 added). F2.9 refusals added. F41 section explorer added. PR-5 (HUF) added, figures to be sourced. §2 and §7 merged into one scope table. **Item citations corrected** — N1–N8 and P2, P3, P5 cited the wrong items (10 of 14); P2's and P5's tests marked UNOWNED | D-046; review `reviews/2026-09-23-prd-techspec-review.md` |
