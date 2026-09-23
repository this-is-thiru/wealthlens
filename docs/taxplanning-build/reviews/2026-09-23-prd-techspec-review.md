# Review — `01-PRD.md` and `02-TECH-SPEC.md`, against the brief, the backlog and the code

**Date:** 2026-09-23 (session 4). **Asked for by the owner:** *"crystal clear for the agents … if it
really needs discussion we can discuss."* **Decided 2026-09-23 (D-046):** the owner accepted every recommendation — B1–B6 as recommended, A1–A7,
C1–C4. **Not yet applied**; application follows the commit of `W-045a` (rule R8), in the order at the end.

Every finding carries its evidence. Three groups:

- **A — fix without discussion** (mechanical, the evidence decides it);
- **B — needs the owner's decision**;
- **C — mistakes in my own session-4 work** that this review caught.

---

## A — Fix without discussion

### A1. 31 of 43 item citations point at the wrong item

The IDs predate the D-011 restructure and were never updated. An agent told "the item that builds
X is W-n" is sent to the wrong item.

| Where | Citations | Wrong | Examples (cited → what that id actually is) |
|---|---|---|---|
| TS **T8** API table | 18 | **18** | `compute` → W-101 (*repositories + indexes*); `compare-regimes` → W-102 (*cleanDatabase additions*); `/tax-years` → W-095 (*ArchUnit @Valid rule*) |
| TS **T10** ArchUnit table | 11 | **3** | "every node is referenced by a pack" → W-044 (*NodeHarness*, which did not build it); `@Valid` / DTO rules → W-094 (*T21 tests*) |
| PRD §5 N-table, §6 P-table | 14 | **10** | N5 "packs load at startup only" → W-010 (*Provenance records*); P2 determinism → W-045 (*RETIRED*); P5 explained zeros → W-062 (*SalaryBaseNode*); N6–N8 each shifted by one item |

Evidence: the cross-check script run in session 4 (every `W-` id in those tables resolved against
`03-WORK-ITEMS.md` titles). **Fix:** re-point each to the item that owns the work; where none does
(the T10 "node referenced by a pack" rule), assign it — proposed: `W-053`, beside I3.

### A2. T5, the invariant table, is wrong about itself

- It says **"All 30 are listed"**; the table lists **32** (I1–I32).
- It says I1–I27 and I31–I32 are **"checked at pack load"**. Not so for: **I8** and **I17** (they
  constrain *drafts* — runtime, persistence); **I1**, **I4**, **I18** (enforced by a constructor, by
  the provider, by `TemporalContext` — the table's own errata say so); **I32** (a reflection test).
- Three erratum blocks sit **between table rows**, so the table no longer renders as one table.

**Fix:** one table with a **"where enforced"** column (pack load · constructor · provider · runtime ·
build gate · test) and the errata moved below it.

### A3. T2, the package map, no longer matches the code

`Money`/`Rate` shown in `taxengine/money` (they are in `taxrules/money`, B-001); `input/` lists
`CapitalGainsSummary` (now `CapitalGainsInput`, D-024) and omits `EnteredFacts`; `condition/` says
"+ 16 records" (13 exist); the brief's `timeline/`, `coverage/`, `catalogue/` sections and the
`Assessee` family are absent. **Fix:** regenerate T2 from the source tree, marking unbuilt packages
as *planned (item)*.

### A4. Two rules that contradict each other

- TS **T12**: *"Money assertions use `MoneyAssert`, never bare `assertEquals`."* **D-010**: `Money`
  is asserted with AssertJ `isEqualTo`, because `MoneyAssert` is `double`-based and would discard
  exactness. **Fix:** T12 states both — `MoneyAssert` for the legacy `double` money, `isEqualTo` for
  `Money`.
- TS **T3** says **"Copy these exactly"** above a `Money`/`Rate` block whose **package is wrong** and
  an identifier block with **two wrong patterns** — each corrected only by an erratum beside it. An
  agent following the instruction copies the error. (See **B1** for the structural fix.)

### A5. The glossary forbids the words a 2025-26 user and form will use

PRD §1 bans "financial year" and "assessment year" but never gives the mapping, and TY 2025-26 is
under the 1961 Act, whose return is filed **for an assessment year**. `01-rulebook.md` `YR-MODEL-001`
already states it: **TY 2025-26 = PY/FY 2025-26 = AY 2026-27**. An off-by-one year is exactly the
plausible, silent error this project exists to prevent. **Fix:** add the mapping row, sourced; keep
"tax year" as the only word in code; display labels map per Act.

### A6. PRD F1 still describes the pre-brief rule storage

F1.1 "two packs load at startup", F1.5 "no admin write endpoint", F1.6 "`draftOrFinal`, `status`",
F1.8 "four layers" — each needs restating in D-034 (timeline compiled into packs), D-036 (three
statuses) and D-037 (publish gate; an admin writer only behind the gate) terms. The Money erratum
also splits the glossary table.

### A7. PRD omissions against the brief

No **HUF persona** (HUF is the named second type); no **F41 section explorer** (D-042); the case
axes (brief §7.2) have no PRD home; T9's error catalogue has **no code for the brief's refusals**
(an unsupported assessee type or case combination — brief §7.2 "a refusal with a reason").

---

## B — Needs the owner's decision

| # | Question | Evidence | Options | Recommendation |
|---|---|---|---|---|
| **B1** | Should the two **build contracts** (PRD, TECH-SPEC) stay append-only with errata, or be rewritten as the clean current truth? | TECH-SPEC carries **7** errata; T3 says "copy exactly" above wrong code; T5 and T10 tables are broken by errata blocks. Every agent must mentally apply the patches in order — the opposite of "crystal clear" | (a) keep errata (D-008/D-009 as today); (b) **rewrite PRD and TECH-SPEC as current truth**, history in a changelog section + git; the 25 design documents keep errata (they are the research record) | **(b)** for the two contracts only. An agent should read *what is true now* in one place |
| **B2** | One login, several taxpayers? | TS **T7**: `tax_profiles` unique `{email}` → one profile per login. The brief makes HUF the second type — an HUF is managed by its karta, who also files for themself; families file for parents | (a) keep one profile per login; (b) **an account (email) owns several assessee profiles**; unique `{pan}`; index `{email}` | **(b)** — otherwise HUF is a schema change, which the brief forbids |
| **B3** | API path | TS **T8** base `/api/v1/tax-planning`. **No** controller in the repo uses `/api/v1`; CLAUDE.md's convention is `/{resource}/user/{email}`; the legacy module already owns `/tax-planning` | (a) keep `/api/v1/tax-planning` (a second convention); (b) **repo convention with a distinct resource** until the legacy is deleted, e.g. `/tax/user/{email}/…` | **(b)** |
| **B4** | Regime identity across Acts | Regime code `S202_DEFAULT` names the **2025-Act** section, yet the same regime governs TY 2025-26 under the **1961 Act** (s.115BAC). D-035 already decided rule IDs name the concept, not the section | (a) keep section-named codes, map per Act; (b) **concept codes**, provision per Act as data (same as D-035) | **(b)** — one principle for rules and regimes |
| **B5** | Who owns an employer record? | TS **T7**: `tax_employers` unique `{tan}` **globally** — one shared record per employer across all users; user-entered details would collide and leak between accounts | (a) global reference data; (b) **per account**: unique `{profile_id, tan}` | **(b)** |
| **B6** | HTTP status for a refusal ("not supported yet: HUF with business income") | T9 has no such code; the repo maps `IllegalArgumentException` → 400 | (a) **400, class USER**, codes `UNSUPPORTED_ASSESSEE_TYPE` and `UNSUPPORTED_CASE`; (b) 422 | **(a)** — consistent with the repo |

---

## C — My own session-4 mistakes, caught by this review

| # | Mistake | Where | Fix |
|---|---|---|---|
| **C1** | The timeline layout **drops the four layers** (`law` / `exec` / `forms` / `content`) that T4, the cache key and property P3 depend on | `03-WORK-ITEMS.md` `W-045f`; brief §5.1 | lay the timeline out **by layer**: `timeline/law/rules/…`, `timeline/exec/pipelines/…`, `timeline/forms/…`, `timeline/content/sections/…` |
| **C2** | **A synonym for an existing word** — "snapshot" (`SnapshotCompiler`, `SnapshotDiff`, `snapshots/*.lock.json`) for what the design already calls a **rule pack**, and `SnapshotDiff` for T2's existing **`PackDiff`**. Exactly what the new CLAUDE.md §3 forbids | brief §5; `W-045g`, `W-045h`, `W-052c` | `RulePackCompiler`, `PackDiff`, `rulepacks/<taxYear>.lock.json`; "rule pack" stays the one word |
| **C3** | `coverage.json` given no layer; it must not be able to change a number | `W-045j` | place it in a non-computing layer (`content/`) |
| **C4** | Case axes omit the **short first tax year** (`YR-MODEL-003`: a new business or source starts mid-year) — irrelevant to salaried individuals, relevant to "all filings" | brief §7.2 | add the axis; `TaxYearId` assumes 1 April – 31 March |

---

| **C5** | The brief's examples `RATE-SLAB-S202-1` and `S202_DEFAULT` put a **section number** in the identity — contradicting D-035 and B4 (found while applying C1–C4) | brief §5.2, §10 | examples removed; concept names proposed at `W-045d` from the Acts' wording, owner chooses (O-4) |

**C1–C5 applied 2026-09-23 (D-047).**

## Order of application (after `W-045a` is committed)

1. **C1–C4** — correct my own Phase 4b rows and the brief (they are uncommitted-era work, cheapest now).
2. **A1–A7** — mechanical fixes, one commit.
3. **B1–B6** — as decided; B1 decides *how* A1–A7 are written (errata vs rewrite), so ask it first.
