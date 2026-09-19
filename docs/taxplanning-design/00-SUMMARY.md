# 00 — Summary

Greenfield research and design for the `taxplanning` module. Nine documents plus `PROGRESS.md`, all
under `docs/taxplanning-design/`. **No source code, tests or build files were changed.**

| File | What it settles |
|---|---|
| `01-rulebook.md` | 24 rule groups, ~200 rule IDs, both tax years, both Acts; verdicts on H-1..H-16; a TY 2025-26 vs TY 2026-27 diff table |
| `02-lessons.md` | 36 placeholder observations → 25 design rules (`D-*`); 15 ideas kept, 2 rejected |
| `03-advisory.md` | Regime comparison, break-even, structuring optimiser, gap analysis, language standards |
| `04-product-spec.md` | 4 personas, 7 jobs, 22 features with RICE, MVP/Next/Later, 12 out-of-scope items |
| `05-domain-model.md` | Tax-year registry, rule packs, 6 cap shapes, component codes, documents, trace, money, 14 collections |
| `06-algorithms.md` | 31-step pipeline, cap aggregator, both marginal reliefs, TDS projection, Form 16 parsing, optimiser, trace format |
| `07-architecture.md` | 4 modules, 5 SPIs, 18 endpoints, rule-pack rollover, testing strategy, 3 Mermaid diagrams |
| `08-golden-tests.md` | 60+ cases with arithmetic shown; 9 marked `PROVISIONAL` |
| `09-build-plan.md` | 11 slices, each independently shippable; a dated deletion order for the placeholder |
| `10-extensibility.md` | **N parallel regimes as data**; an open component vocabulary; relief formulas as an extensible family. Corrects `03`, `05`, `06`, `07`, `08` |
| `11-filing-drafts.md` | Stored entries, **unlimited drafts per person per year**, revisions, concurrency, rollover. Corrects `05` |
| `12-claim-integrity.md` | Statutory vs evidenced vs declared amounts; the documentation register; what the department already sees |
| `13-edge-cases.md` | 15 reframed decisions and a 77-case edge catalogue from an adversarial second pass |
| `14-operational-design.md` | PII, authorisation, idempotency, audit, retention, document safety, observability, capacity, failure modes |
| `15-review-findings.md` | **Third pass, with code**: 18 defects; the DAG architecture; interest/fee, election, proration, pack-lint and boundary-generator engines; 7 new entities; the `Condition` algebra |
| `16-getting-started.md` | Day 0 decisions, the Day 1 walking skeleton with real pack JSON, a 24-PR sequence, three parallel tracks |
| `17-regime-lifecycle.md` | **Regime lifetime**: entry vs in-force windows, sunset and grandfathering, the per-taxpayer election state machine, introduction/withdrawal playbooks, the three-tier "manager per regime" escalation |
| `18-temporal-versioning.md` | **Rules, clauses, limits and components over time**: `D-TEMPORALKEY`, versioned rules, mid-year apportionment, component lifecycle, the amendment ledger, and why not to poll |
| `19-engineering-foundations.md` | Pass from **inside this repo**: the ArchUnit money rule that breaks the design, pack authoring toolchain, build-time pack compilation with generated constants, metamorphic tests and an independent oracle, error catalogue, computation receipt, jobs, reuse inventory |
| `20-product-gaps.md` | Pass over the **feature surface**: eleven features the engine already supports and the spec never had — refund forecast, tax calendar, offer comparison, life events, completeness, onboarding, multi-year view, ITR export, explanations |
| `21-itr-forms.md` | **ITR forms as pack data**: nine forms, per-year with historical sets retained, eligibility in the `Condition` algebra, shared schedules, `FieldSource`-driven export |
| `22-claim-catalogue.md` | **How to actually claim each rule**: five claim channels, per-rule playbooks with steps and counterparties, "not in my Form 16 — can I still claim it?", and materiality |
| `23-architecture-consolidation.md` | **The tenth pass**: pack layered by change rate, the engine input boundary, absent-vs-zero resolved, the extension taxonomy, determinism and `asOf`, error mapping, trace layering, read models |

---

## Second pass — what the first draft got wrong

Three questions were put to the first draft after it was written: *does this survive the government
running three or four regimes in parallel?*, *can a person keep any number of stored drafts?*, and
*how much can actually be claimed safely?* All three found real defects.

| # | Defect | Severity | Fixed in |
|---|---|---|---|
| **1** | **Two regimes hard-coded.** `Regime` was a Java enum; `RegimeApplicability` was a tri-state `OLD \| NEW \| BOTH`; the pack layout shipped exactly two pipeline files; comparison, break-even and the optimiser all named a literal pair | **High** — and already wrong today: `FACT (S2, ss.200–204)` the 2025 Act carries **five** concurrently-in-force regime sections, and `FACT (S2, s.205)` they **share** disallowance and eligibility sets by reference | `10-extensibility.md` E3, E6 |
| **2** | **`ComponentCode` a closed Java enum.** A new allowance therefore meant editing an existing file — exactly what the brief forbids. The enum was defending against O-7, O-15 and O-25, and it only actually solved one of them | **High** | `10-extensibility.md` E4 |
| **3** | **One input set per person per year** — `tax_year_contexts` carried a unique index on `{email, tax_year}`, foreclosing multiple drafts outright | **High** | `11-filing-drafts.md` F1, F8 |
| **4** | **Concurrent employment asserted away.** `G5.3` asserted that employment periods do not overlap. Dual employment is lawful, and it is the case where the TDS shortfall is **largest** | **High** | `13-edge-cases.md` R-4 |
| **5** | **The rebate cap was stated and not designed for.** `FACT (S2, s.156(3))`: the rebate cannot exceed tax at s.202(1) **slab** rates. With D3's capital-gains feed, a ₹12,00,000 total income that is mostly long-term gains would have been computed as **nil tax**. The pipeline must carry tax partitioned by rate basis, not a scalar | **High — the most dangerous single defect found** | `13-edge-cases.md` R-5 |
| **6** | **Self-occupied houses treated as a fact, not an election.** `FACT (S2, s.21(7)(a))`: nil annual value applies to *"two of such houses **as specified by the assessee**"*. Which two is a choice, and a house with large interest is often better **not** specified | Medium | `13-edge-cases.md` R-6 |
| **7** | **Inter-head set-off order unspecified**, though absorbing a house-property loss against slab income versus special-rate income changes the surcharge and therefore the total | Medium | `13-edge-cases.md` R-7 |
| **8** | **Retrospective amendments unhandled.** `FACT (S3, s.4)`: Finance Act 2026 inserts a provision *"deemed to have been inserted with effect from 1 June 2007"*. Pinning the pack makes an old answer reproducible; it does not make it right | Medium | `13-edge-cases.md` R-8 |
| **9** | Nine further reframings: rounding of negative amounts, who computes tax on capital gains, HRA's metro percentage being per-period, the LTA calendar block straddling the tax year, the 1 April birthday, advance-tax carve-outs, mid-stream rule changes on multi-year entitlements | Mixed | `13-edge-cases.md` R-9 … R-15 |

`INFERENCE`: defects 1, 2 and 3 are all **rule-pack or persistence schema**, which is the one part of
this design that is expensive to change once a pack ships or a draft exists. `09-build-plan.md` Slice 1
authors both packs — so all three must land before it, and none of them changes the slice order.

### On "how much can I claim without a notice"

`FACT (S4, rule 237(6))`: capital gains on listed securities and mutual-fund units, dividends, and
interest paid or credited are reported to the department for **all transactions — no threshold**.
`FACT (S2, s.133(6))`: an 80G deduction is allowable *only* on the basis of what the institution itself
reported.

`INFERENCE`: for this product's primary user, essentially every income figure is already known to the
department before the return is filed. So there is no threshold to sit under, and the useful product
is not a threshold calculator — it is **statutory maximum vs evidenced maximum vs declared amount**,
with the gap between the last two made explicit and the evidence that closes it named.
`12-claim-integrity.md` specifies that; it also records plainly that CBDT's scrutiny selection criteria
are **UNKNOWN**, unpublished, and were not sourced, so no figure is offered for them.

---

## Third pass — 18 more defects, and one architecture change

A third adversarial pass (`15-review-findings.md`) found eighteen. The five that matter:

| # | Defect | Why it matters |
|---|---|---|
| **1** | **`TaxpayerProfile` had no PAN** | `13-edge-cases.md` B9-73 specified "reject a Form 16 whose PAN differs from the profile PAN" — against a field that did not exist |
| **2** | **No interest or fee engine at all** | ss.**423** (late filing, `I = 1% × A × T`), **424** (advance tax below 90%), **425** (deferment, with the s.425(4) capital-gains carve-out), **426** (excess refund) and **428** (late fee: ₹1,000 if total income ≤ ₹5,00,000, else ₹5,000) were never designed for. "What do I owe" was answering with tax but not with what is owed |
| **3** | **`Condition` was declared and never defined** — while `D-NOFORMULA` forbade a string DSL. Resolved as a **sealed algebra of typed predicates**, which also delivers rule-level effective dating |
| **4** | **The pipeline was a linear list of 31 hand-ordered steps.** It is a **DAG**. Each node now declares `requires()` / `produces()` and the order is derived — so a new step is a new class, not an insertion into a numbered sequence |
| **5** | **No election concept**, though five statutory choices exist — which two houses (s.21(7)(a) says *"as specified by the assessee"*), which two children, set-off absorption order, the regime, and gratuity allocation |

The DAG change pays for itself twice: it makes the pensioner-with-no-employer case work for free, and
**incremental recomputation** — re-evaluating only nodes downstream of what changed — cuts the
optimiser from ~24,000 full pipeline runs to roughly 6,000 node-equivalents, exactly.

Also added: reference entities (`Employer`, `Landlord`, `Lender`, `BankAccount`), a `FamilyMember`
aggregate without which `DED-80D-008` is not expressible at all, a `Rate` type so "30" and "0.30"
stop being the same type, a corrected `inputsFingerprint` (it omitted the cross-year ledgers, so the
compute cache could serve a stale result), a **rule-pack linter** that catches the H-2 defect class at
authoring time, and a **boundary-case generator** that derives golden inputs from the pack's own
ladders.

---

## Fourth pass — regime lifetime

`10-extensibility.md` made the regime **count** data and left its **lifetime** implicit. That was a
real gap, and the Act already exercises it:

| Shape | Provision | Evidence |
|---|---|---|
| **Closed entry, incumbents continue** | **s.201(2)**, **s.204** | entry required being set up on or after a date **and** commencing manufacture *"on or before the 31st March, 2024"*. **As of TY 2026-27 these regimes are closed to new entrants and still in force for those in them** |
| **Retroactive invalidation** | **s.203(2)** | on failing the conditions the option becomes invalid *"in respect of the said tax year **and subsequent tax years**"* and the Act applies *"**as if the option had not been exercised**"* — a multi-year restatement |
| **One-shot entry, binding forward** | **s.204(2)** | exercisable only with *"the **first** of the returns of income"*, and *"once exercised, shall apply to subsequent tax years"* |
| **Withdrawal, permanent bar, revival edge** | **s.202(4)(a)(iii)–(iv)** | withdrawable *"only once"*; thereafter *"never eligible"* — *"**except where such person ceases to have any income from business or profession**"* |

What changed:

- **`RegimeLifecycle` carries two independent windows** — `entryWindow` and `inForceWindow`. Collapsing
  them into one was the mistake; the grandfathering case is precisely where they differ.
- **`ElectionMechanics`'s three-valued `lockIn` enum is gone**, replaced by `ElectionProtocol` +
  `BindingSpec`. A `bindingAppliesWhen` condition lets **one** `RegimeSpec` serve both s.202(4)(a)
  (lock-in, for a person with business income) and s.202(4)(b) (a free annual choice, without) —
  which is what the section actually says.
- **A per-taxpayer `RegimeElectionHistory`**, append-only and workspace-independent, with an explicit
  state machine including the revival edge. Eligibility is now history-aware, and
  `inputsFingerprint` includes it — without which a prior-year withdrawal would not invalidate the
  compute cache.
- **`eligibleRegimes` returns `RegimeOffer`**, not a filtered list: `available` / `continuing` /
  `barred(reason)` / `lapsed(policy)`. A barred regime with its reason is an answer; an absent one is not.
- **Four playbooks** — introduce, withdraw, close-entry-only, flip-the-default — each ending in "no
  code" for the normal case.
- **"A manager per regime" resolved as a three-tier escalation**: pure data (covers all five regimes
  in the 2025 Act) → a new `ComputationNode` or `CapShape` → a `RegimeBehaviour` SPI. Ships with
  **zero** behaviours beyond the default, and a behaviour that no pack references is a load error.

---

## Fifth pass — rules change over time, and not only at year boundaries

The pack-per-year model handled a limit changing **between** years. It could not express a limit
changing **inside** one, or a rule keyed on a date that is not the tax year. Both happen:

| # | Instance | Evidence |
|---|---|---|
| 1 | **A mid-year change, permanently embedded** | `FACT (S2, s.197(3))`: for land or building *"acquired **before the 23rd July, 2024**"*, the excess of tax at 12.5% over tax at 20% **with indexation** is ignored (`E = A − B`). The July-2024 change survives as a clause **keyed on the asset's acquisition date — not the tax year** — and a TY 2045-46 computation will still need it |
| 2 | **One instrument, two commencement dates** | `FACT (S6)`: the Fourth Amendment Rules 2026 — *"rules 2 to 4 shall be **deemed to have come into force on the 1st day of April, 2026**; and rules 5 to 8 shall come into force **on the date of their publication**"* (17 September 2026) |
| 3 | **Four amendments in six months** | The Income-tax Rules 2026 moved at least four times **inside** the tax year they govern |
| 4 | **A nineteen-year retrospective insertion** | `FACT (S3, s.4)`: deemed inserted *"with effect from the 1st day of June, 2007"* |
| 5 | **Enacted, not yet effective, with a stated interim position** | `FACT (S3, s.123)`: Sch. III Sl. 38A *"shall apply on or after such date as may be notified"*, and *"**pending such notification**, the entire disability pension … shall be exempt"* |

What changed:

- **`D-YEARKEY` was necessary and not sufficient.** The sharper rule is **`D-TEMPORALKEY`**: every
  lookup takes a temporal coordinate and **each rule declares which clock it reads** —
  `TAX_YEAR`, `EVENT_DATE`, `ACQUISITION_DATE`, `SANCTION_DATE`, `PAYMENT_DATE` or `FILING_DATE`.
  A node that evaluates an acquisition-date rule without supplying the date **throws**; it does not
  fall back to the tax year.
- **A rule is a stable ID with an ordered list of `RuleVersion`s.** The ID stays the contract that
  every test and every trace line references; the version carries the cap, the conditions, the regime
  applicability and the `AmendmentRef` that introduced it. `Condition.EffectiveBetween` — bolted into
  the predicate algebra last pass — is **withdrawn**: validity gates a *value*, not *applicability*.
- **`Validity` has three shapes**, including `Retrospective` (which keeps both the deemed and the
  actual date, because they differ) and `PendingNotification` (which names the interim rule that
  applies meanwhile) — the only honest representation of instance 5.
- **Mid-year changes are apportioned by a *declared* combination rule.** An undeclared one is
  `UNDETERMINED` and **fails the computation**. Guessing here is the most dangerous kind of silent
  error: plausible, confined to the year the rule moved, and invisible to every golden test written
  before it.
- **Components have lifecycles.** A draft referencing a withdrawn component is **blocked** with an
  `UpcastProblem`, never silently dropped — that would be the placeholder's silent zero in a new costume.
- **No polling.** Discovering a change is automatable and worth automating; **adopting** one is a
  reviewed change with a diff and a golden-test run. Conflating the two is how an engine quietly
  starts computing a different answer.

---

## Sixth and seventh passes — the repo, and the product

### From inside the repository (`19`)

Reading the codebase rather than the statute turned up four things designed against conventions I had
not checked. The one that matters:

`FACT` (`backend/src/test/java/com/thiru/wealthlens/architecture/ArchitectureTest.java:102-107`): an
ArchUnit rule already forbids `BigDecimal.setScale(int, RoundingMode)` **outside
`..shared.util.money..`**. `M6` and `15` Part G specified a `BigDecimal` `Money` whose two rounding
methods both call it. **The design would have failed the existing build on the first commit.**

The conclusion stands — `taxplanning` needs exact decimal, and `docs/trade-ledger/money-representation-analysis.md`
§7 anticipates it precisely: *"A regulator or auditor requires exact decimal storage. For a personal
tracker this is remote; **for anything filed on someone else's behalf it is not**."* Two of its five
triggers fire, and the ~170-declaration migration cost that made `BigDecimal` premature for the ledger
does not exist in a greenfield module. So the rule is **amended deliberately**, with three new rules
keeping the two money representations apart and a single named conversion seam — and that amendment
becomes **PR 0**, before anything else.

Also in `19`: the rule-pack **authoring toolchain** (the design rests on packs being writable, and
several thousand lines of cross-referenced JSON by hand is the largest practical risk); **build-time
pack compilation with generated constants**, which resolves E4's data-vs-compile-safety tension better
than either option I had weighed; **metamorphic properties** and an **independent oracle** so golden
expectations scale past hand-derivation; a closed **error catalogue**; and the **computation receipt**,
the cheapest high-value thing in the whole design.

### Over the feature surface (`20`)

The engine is **over-built relative to the product**. Eleven features are missing from
`04-product-spec.md`, and almost every one is a *projection of machinery that already exists*:

| | Feature | Why it matters |
|---|---|---|
| **F29** | **Refund or payable forecast** | *"Will I get money back, or owe?"* — the one number everyone wants, and it is a subtraction between the TDS projection and the computation. **The home screen** |
| **F30** | **Tax calendar** | every date is already derivable from `ADV-INSTAL-001`, `INT-DEFER-CARVEOUT-001`, `SAL-LTA-BLOCK-001`, `ITR-DUE-*`. Each entry carries the **measured** rupee cost of missing it |
| **F31** | **Offer comparison, after tax** | the persona's highest-stakes question of the year. City changes the HRA percentage, employer type changes NPS 14% vs 10%, the ₹7.5L aggregate turns excess into income, and a mid-year join risks the ₹2,14,500 double standard deduction |
| **F32** | **Life events** | including the trap nobody warns about: taking ₹50,000 of freelance work puts the user under `s.202(4)(a)`'s regime lock-in **for every future year** |
| **F33** | **Completeness weighted by measured worth** | *"missing three things worth up to ₹64,000"* — and it lists the items that **increase** tax too, which is what separates it from a sales funnel |
| **F35** | **Multi-year view** | six entitlements span years and none is visible anywhere — above all, that a carried-forward house-property loss is **destroyed permanently** by computing the next year in the default regime |
| **F36, F37** | **ITR export, plain-language explanations** | the last mile of promises D6 and `A4.4` already made |

`INFERENCE`: F29, F30, F33, F34 and F37 are roughly eleven person-weeks and need **no new engine
capability, no new entity and no schema change**. That they are all already supported is the strongest
evidence the foundations are right; that none was specified is the strongest evidence the product
surface was under-designed relative to them.

---

## Eighth pass — ITR forms

`R22` had ITR applicability as **rulebook rows** and `F8`/`F36` as **features**, with no model — forms
were implicitly code. The Rules answer the extensibility question more plainly than I could:

| | Evidence (`S4`, Income-tax Rules 2026, rule 164) |
|---|---|
| The form set is **scoped to one tax year** | r.164(1): *"…**relating to the tax year commencing on the 1st April, 2026**, shall be in accordance with the provisions of this rule"* |
| **Historical form sets stay live** | r.164(14): an earlier year's return is furnished in *"**the appropriate form as applicable in that tax year**"* |
| The set is **nine** forms, not four | ITR-1..7 plus **ITR-V** and **ITR-UN** (updated return, r.165) |
| A **second dimension** exists | r.164(12): manner of furnishing — DSC, EVC, ITR-V, and **paper for an individual aged 80+ filing ITR-1 or ITR-4** |

What changed: `ItrFormSpec` and `ItrSchedule` are **pack data**, one file per year. Eligibility reuses
the existing **`Condition` algebra unchanged** — the payoff for having made it typed and sealed.
Schedules are **shared by reference** the way the Act shares disallowance sets (s.205(1), one list
referenced by five regime sections). And `FieldSource` turns `F36`'s export into a **fold over data**
rather than a mapper per form, so a new form reusing Schedule S needs no export code at all. Forms
resolve on `FILING_DATE`, not tax year — a form revised mid-year is a second version.

**And a correction.** I had assumed ITR-2 is a superset of ITR-1 and that selection means "pick the
simplest eligible form". `FACT (S4, r.164(4))`: ITR-2 is for *"an individual **not being an individual
to whom sub-rule (2) applies**"*. The forms are **mutually exclusive by construction**, so selection is
a deterministic cascade in the Rules' own order — simpler and more testable than what I had assumed,
and it removed a product decision I was about to invent.

---

## Ninth pass — the claim catalogue

The rulebook said **what the law allows**; `12` said **how much you can prove**. Neither said **what
the user does** — the steps, whom to ask, and whether a claim survives being absent from Form 16.

Three findings, all verified:

**1. Filing late does not forfeit your deductions.** `FACT (S2, s.122(5))`: the late-return bar applies
**only to Part C** of Chapter VIII. `FACT (S2, part headings)`: Part B is *"deductions in respect of
certain payments"* — 80C, 80D, 80DD, 80E, 80G, house-loan interest; Part D is 80TTA/80TTB; Part E is
80U. **Essentially everything a salaried person claims is outside the bar.** Late filing costs interest
and a fee; it does not cost the deduction. This is widely misunderstood.

**2. Only two of five claim channels can actually be lost.**

| Channel | Can it be lost? |
|---|---|
| **E** — employer, optional (80C, 80D, HRA, LTA, house-loan interest) | **No** — s.392(4)(a) says the employer route is *"at his option"*. Fully claimable in the return |
| **R** — return only (regime election, 80TTA/80TTB, s.157 relief) | No — never the employer's to apply |
| **R+** — return, certificate retained (80U, 80DD, 80DDB) | No, provided the certificate exists |
| **T** — third-party reported (**80G**) | **Yes.** s.133(6): allowable *"**only** on the basis of the information … furnished by such institution"*. **Your receipt is not enough** |
| **P** — employer-provided (**employer NPS**) | **Yes** — there is nothing to claim if they did not contribute |

**3. Nothing is attached to the return.** `FACT (S4, r.164(11))`: the return is not accompanied by
*"any document … required to be attached … under any of the provisions of the Act"*. That resolves the
apparent conflict with s.127(6)'s *"along with the return"* — certificates are **retained and produced
on demand**, which is the case for the document vault.

Plus **materiality**, for small claims: a claim whose entire tax effect is under ₹5 cannot move the
final figure, because s.516 rounds to ₹10. But that only justifies skipping a **deduction** — small
**income** is still reported, because it is matched against third-party data regardless of size
(r.237(6): interest and dividends are reported for *all* transactions). The tax effect is **measured**
by recomputation, not estimated at a notional rate — a claim "worth ₹78 at 30%" is worth **₹0** inside
a rebate plateau.

Shipped as **`F40` — Claim playbooks**, MVP, RICE 1.00.

---

## Tenth pass — consolidation

Nine passes added capability without checking that it still composed. This one found **drift, not
defects** — which is the useful result.

| # | What had drifted | Fix |
|---|---|---|
| **G-1** | **Two vocabularies for one concept.** `15` replaced the linear `ComputationStep` with the DAG `ComputationNode`; `ComputationStep` survived in **ten documents**. I spent `02` L1 warning that this is how O-7 happened, then did it myself | Swept, plus **I28** — a `grep` gate in CI |
| **G-2** | **The engine reached a repository.** `17` L3 called `history.of(profileId, …)` inside regime resolution, which `X1.2` says is pure | **`EngineInputs`** — everything readable is *passed in*, never fetched. Enforced by ArchUnit |
| **G-3** | **The pack did four unrelated jobs** — legal rules, execution wiring, editorial content, form mappings — one version, one reviewer | **Four layers** by change rate: `law/`, `exec/`, `forms/`, `content/` |
| **G-5** | **Computation was no longer a pure function of its inputs.** `18`'s `FILING_DATE` rules and `21`'s form selection made the answer depend on *when you ask* | Split into **`TaxComputation`** (date-independent, cached forever) and **`FilingPosition`** (thin, date-dependent overlay) |
| **G-4** | *"Disallowed in this regime"* was modelled two ways | **`D-REGIMEZERO`**: prefer a trace line with `out = 0` **and a stated reason** over an absent line |

Two of these pay for themselves immediately. **`EngineInputs`** finally justifies the fingerprint —
it is a hash of *one object*, so a new field is in the cache key automatically rather than by anyone
remembering. And the **core/overlay split** is what makes `F29`'s refund forecast and `F30`'s calendar
affordable as MVP: recomputing 31 nodes on every page load to show a countdown would not have been.

The **layering** has a property worth stating: `content/` — explanations and claim playbooks — **cannot
change a number**, and that is a property test (**I27**), not a convention. It also means seven
editorial revisions invalidate zero cached computations.

Five properties now hold and are tested: **purity**, **determinism**, **layer isolation**, **derived
order**, and **explained zeros**.

---

## The five decisions that shape everything else

### 1. The unit of versioning is **rule identity**, not rule value

`INFERENCE (from the R1–R24 diff table)`: of the 24 material differences between TY 2025-26 and
TY 2026-27, **19 are re-identification** — the same idea under a different section, rule or form
number (80C → s.123; Form 16 → Form No. 130; s.89 → s.157; Rules 1962 r.2A → Rules 2026 r.279) — and
only **5 are substantive value changes**.

So `RuleRow` carries *both* `provision1961` and `provision2025`, with a `Presence` that can say
`ABSENT_IN_THIS_ACT` (which `SAL-ENTMT-001`, entertainment allowance, actually needs). The **rule ID is
stable across Acts**; the provision is data. A design that versioned only values would not have
survived this particular year boundary.

### 2. Rule packs are immutable classpath artefacts, not Mongo collections

`D-IMMUTABLE`. There is no admin endpoint that writes a slab policy. This is the direct structural fix
for O-16 — an integration test that POSTed an ACTIVE slab policy with **no slabs**, superseding the
seeded one, in a collection the test harness deliberately protects from cleanup. Data a test can
corrupt for every subsequent test is a correctness hazard with no offsetting benefit for a product
that has no users.

### 3. The regimes differ in **which steps exist**, not in values inside shared steps

`D-REGIMESTEP`. The pipeline is composed per regime **from the pack**, so the old-regime pipeline
simply **does not contain** the rebate-marginal-relief step. That is the structural fix for O-11 and
for **H-3**: `s.156(1)` (old regime) has no marginal-relief clause, while `s.156(2)` — which does —
opens *"where the total income ... is chargeable to tax under section 202(1)"*.

### 4. Silence is the enemy, not failure

`D-NOSILENT`. `ComputationNode.evaluate` is the only entry point; the runner has no `try/catch`; no class in
`taxengine` may catch `Exception`. O-1 is the cautionary case: seeded formula strings the evaluator
could not parse, with every call site catching the failure and substituting `0` — so a ₹3,36,000
employer-NPS deduction silently became nothing and the user filed on it.

### 5. Marginal value is **measured**, never modelled

`D-MARGINAL`. Every "what would this save you" figure is `compute(A) − compute(B)` with both
scenarios materialised. That is exactly right at a cap boundary, at a rebate plateau
(`REBATE-NEW-MR-001` gives a 100% effective marginal rate in its band), at a surcharge threshold, and
across a regime flip — none of which a notional marginal rate can represent. It costs one extra pure
evaluation, which is affordable only because `D-PURE` holds.

---

## H-1 to H-16: every hypothesis confirmed, none refuted

| | Verdict | Sharpened how |
|---|---|---|
| H-1 surcharge | **CONFIRMED** | Entry is ₹50,00,000, not ₹5 Cr, in all four year × regime combinations. ₹5 Cr is only the 37% band, and **that band does not exist in the new regime in either year**. Marginal relief is stated four separate times in FA 2026 |
| H-2 Rules-2026 values mislabelled | **CONFIRMED** on all six | 8-city HRA list, ₹200/meal, ₹15,000 gift, ₹3,000 CEA, ₹9,000 hostel, ₹5,000/₹7,000 car + ₹3,000 chauffeur — every one read directly from G.S.R. 198(E), which applies **from TY 2026-27** |
| H-3 87A relief in the old regime | **CONFIRMED** | s.156(1) has no marginal-relief limb at all |
| H-4 HRA salary without DA | **CONFIRMED** | r.279(2)(b) includes DA where the terms of employment provide for it. Omitting it makes the exemption **larger**, i.e. understates tax |
| H-5 slab bands and rounding | **CONFIRMED** on both | Statutory bands partition the rupee line; `400001` is a label, not an arithmetic bound. s.516 mandates ₹10 rounding of total income **and** of the amount payable |
| H-6 employer-contribution cap | **CONFIRMED, worse than stated** | **Four** independent charges: the ₹7,50,000 aggregate (s.17(1)(h)), the accretion on the excess (s.17(1)(i)), employer RPF above 12% (Sch. XI Pt A para 6), and employee-side interest above ₹5,00,000/₹2,50,000 (Sch. II Sl. 3, 4) |
| H-7 employee PF and 80CCE | **CONFIRMED** on both | Schedule XV para 1(f) puts employee RPF inside the s.123 ceiling; the 2025 Act has **no separate 80CCE** because the aggregation is structural in s.123 |
| H-8 LTA exempt in full | **CONFIRMED** | Four independent caps: actual expenditure, fare class by shortest route, two journeys per four-**calendar**-year block, and unavailable in the new regime |
| H-9 books, phone, uniform at 100% | **CONFIRMED, more nuanced** | Uniform is exempt only to the extent actually incurred on duty. **Books and phone appear in neither prescribed list**; phone is a perquisite question, not an allowance one |
| H-10 transport allowance ₹19,200 | **CONFIRMED, worse than stated** | There is **no general transport allowance**. The only one is for a blind / deaf-and-dumb / orthopaedically handicapped employee, at ₹15,000 (metro) or ₹8,000 (other) **per month plus DA** |
| H-11 flat 80D cap | **CONFIRMED** | Two independent ₹50,000 ceilings, a ₹5,000 preventive sub-limit cutting across both, a senior-citizen substitution, a mutual-exclusivity rule, a payment-mode rule, and a multi-year spreading rule — seven rules, one number |
| H-12 self-occupied vs let-out | **CONFIRMED** | The distinction drives annual value, the interest cap, new-regime availability **and** whether pre-construction interest is inside the cap |
| H-13 professional tax | **CONFIRMED** | Entire amount in the old regime; **disallowed** in the new by s.202(2)(a)(iv). The error biases *toward* the new regime |
| H-14 retirement limits | **CONFIRMED as a risk** | Several ceilings are **notification-driven**, not in the Act; the aggregate and once-in-a-lifetime rules are cross-year and cross-employer |
| H-15 take-home with perquisites | **CONFIRMED** | A definitional error, not a tax one — and therefore the most damaging for trust |
| H-16 regime-choice rules | **CONFIRMED, more nuanced** | New regime is the default; the salaried opt-out rides on the **return**; for TY 2026-27 there is **no separate form** (r.136); lock-in applies only to business income |

---

## Top risks

| # | Risk | Impact | Mitigation |
|---|---|---|---|
| **R1** | **The Income-tax Act 1961 and the Income-tax Rules 1962 were never opened.** `incometaxindia.gov.in` returned HTTP 403 to every request and `indiacode.nic.in` returned 404 | Every TY 2025-26 **detail** value — HRA metro list, allowance caps, perquisite valuations — is unverified. Nine golden cases are `PROVISIONAL`. TY 2025-26 **rate** values are unaffected and `VERIFIED` from the Finance Acts | **Slice 0 does nothing but close this**, before any code is written. The eGazette route (`egazette.gov.in/WriteReadData/<year>/<id>.pdf`) worked for every gazette tried and is the most promising alternative |
| **R2** | **Rule 280(3) of the Income-tax Rules 2026 appears to invert the regime condition** relative to s.202(2)(a)(ii): it restricts allowances for the person who **opted out**, i.e. the old-regime taxpayer | Affects which special allowances survive in the new regime for TY 2026-27 | Recorded as `SAL-ALW-NEW-SUBSET-001` with status **STATUS UNCLEAR**. The corrigendum G.S.R. 286(E) of 16-04-2026 does not correct it and the Fourth Amendment Rules of 17-09-2026 do not touch it. **Seed from the section, flag the divergence visibly** (`A4.2`) |
| **R3** | The **Rules 2026 amendment chain was not exhaustively traced** — G.S.R. 822(E) records that the Rules were "last amended by G.S.R. 656(E), dated 24 July 2026", and that intermediate chain was not opened | Any Rules-2026 value could have moved between 20 March and 24 July 2026 | Every Rules-2026 row is recorded as "as notified, amendment chain not exhaustively traced". Slice 0 closes it |
| **R4** | **Notification-driven ceilings** — gratuity, leave encashment, retrenchment — are not in either Act | They can change without a Finance Act, so a "we updated for the Budget" process misses them | `Provenance` carries an instrument and a date per **value**, not per Act. F19 is deliberately `Later` |
| **R5** | **`portfolio`'s `RealisedProfits` is documented in its own Javadoc as mid-cutover**, with two legacy fields that go when V1 goes | Binding the tax computation to it would bind it to a shape that is stated to be temporary | Consume a **published read-model** (`CapitalGainsSummary`), and schedule that as **Slice 8**, after the computation is stable, so the read-model is shaped by what the tax engine needs |
| **R6** | **Form layout drift.** Form No. 130 Note 8 states that *"some of the information in the Form would be pre-filled to the extent possible"* | A positional parser is guaranteed to break | `G5`'s four layers are structural first; layer 3 exploits the form's own closed arithmetic; layer 4 asks the user. Nothing is silently defaulted |
| **R7** | **The multi-employer double standard deduction is invisible within any single document** and produces a genuine shortfall — ₹2,14,500 on T8-1's figures | The most expensive surprise a user can get, and the "Priya" persona's whole problem | T8-1 is a first-class golden test, and the assembly logic in `G5.3` keys on `(TAN, period)` rather than assuming one document per year |
| **R9** | **Rebate interaction with special-rate income** (`s.156(3)`) produces a nil tax bill on a ₹12,00,000 income if tax is carried as a scalar rather than partitioned by rate basis | Silently wrong, and only visible once capital gains are wired in | `13-edge-cases.md` R-5. The type change lands in **Slice 2**, before any rebate code exists |
| **R10** | **Retrospective amendment** can make a stored, correctly-pinned computation legally wrong with no change in the user's facts | Reproducible ≠ right | `13-edge-cases.md` R-8 — pack supersession plus a staleness sweep, never a silent recompute |
| **R11** | **Third-party PII** — the user supplies their landlord's PAN, name and address | Data about someone who is not the user and cannot consent through this product | `14-operational-design.md` O1.1 and O5: shortest defensible retention, never included in a user-shared export |
| **R8** | The two rules with **cross-year state** — LTA calendar blocks and the once-in-a-lifetime VRS deduction — cannot be evaluated from a single year's inputs | Silently wrong the first time a user has history | `M3.4` models each as an **append-only ledger**, never a mutable counter, so any past year is reproducible |

---

## Everything marked `UNKNOWN`, `NEEDS VERIFICATION` or `STATUS UNCLEAR`

### `STATUS UNCLEAR` — 2 items

| ID | Issue | What to check |
|---|---|---|
| `SAL-ALW-NEW-SUBSET-001` | Rules 2026 r.280(3) attaches the allowance restriction to the person who exercised the s.202(4) option — the **opt-out** — inverting s.202(2)(a)(ii) | The G.S.R. 198(E) → G.S.R. 656(E) amendment chain; any CBDT clarification |
| `REG-OPTOUT-LATE-001` | Whether a **belated or updated** return can carry a valid regime opt-out | s.202(4)(b) read with s.263(4) and (6); CBDT guidance |

### `NEEDS VERIFICATION` — grouped by what to open

**Income-tax Act 1961 base text** (blocked, S10/S11): `YR-MODEL-001` (ss.2(9), 3), `YR-ACT-005` (s.4),
`REG-DEFAULT-001`/`REG-OPTOUT-SAL-001`/`REG-OPTOUT-YEARLY-001`/`REG-NEW-DISALLOW-001` (s.115BAC(1A) opening words, (2), (6)),
`REBATE-OLD-001` (s.87A main limb), `RATE-ROUND-001` (ss.288A, 288B),
`SAL-CHARGE-001`/`SAL-DEF-001` (ss.15, 17(1)), `SAL-STDDED-001`/`-002` (s.16(ia)),
`SAL-PTAX-002` (s.115BAC(2)(i)), `SAL-ENTMT-001` (s.16(ii)),
`SAL-HRA-005`/`SAL-LTA-NEW-001` (s.115BAC(2)), `HP-CAP-SELFOCC-002` (s.24(b) provisos),
`PF-AGGCAP-001` (s.17(2)(vii)), `PF-EEINT-001` (ss.10(11), 10(12) provisos),
`OS-DIV-DED-2025` (s.57(1) proviso), `OS-FAMPEN-001` (s.57(iia)),
`OS-INT-001` (the s.92(2) clause list in the **2025** Act was also not read in full),
all of R14's 1961-Act counterparts (ss.10(10), 10(10A), 10(10AA), 10(10B), 10(10C)).

**Income-tax Rules 1962** (blocked): `SAL-HRA-METRO-2025` (r.2A), `SAL-DEF-COMM-001` (r.2A Explanation),
`SAL-ALW-LIMITS-2025` — **every row** (r.2BB(2) Table), `PERQ-CAR-2025` and all TY 2025-26 perquisite
valuations (r.3), `PF-AGGCAP-002` prescribed formula (r.3B), `PF-EEINT-001` computation manner (r.9D),
TY 2025-26 ITR applicability (r.12), `REL-ARREARS-CALC-001` (r.21A), `TDS-SAL-12BB-002` TY 2025-26 (r.26C),
and the TY 2025-26 **Form 16 Part B** structure.

**Income-tax Rules 2026 — sections not read or not located**: `SAL-LTA-003` (r.278(1)(i), the air-fare
limb), `PERQ-ACCOM-OWNED-2026` (Table I Sl. 1 and 2(a) — employer-owned accommodation percentages),
`PERQ-LOAN-2026` and `PERQ-PHONE-2026` (not located in the extracted text),
`PERQ-SPECEMP-001` and `PERQ-MEDABROAD-001` (the amounts FA 2025 s.9 made "prescribed"),
`REL-ARREARS-CALC-001` (r.73(1) Table), plus the **rule number** of the perquisite-valuation rule
(its Tables I, II and III were read in full; the rule's own number was not captured).

**Income-tax Act 2025 — not read in full**: `DED-80G-001` (s.133's 50% categories and the
qualifying-limit percentage), `DED-80DDB-001` (s.128(2)–(4), the senior-citizen enhancement),
`SAL-DEF-RPF-001` (the salary base for Schedule XI Part A para 6), `HP-AV-UNREAL-001` (s.21(4)'s
prescribed conditions).

**Finance Act 2026 — located by wording, number not read**: `RATE-CESS-001`'s sub-section number for
the **1961-Act** charge. The 4% rate itself is `VERIFIED` from s.3(15)–(16).

**CBDT notifications never opened**: the gratuity ceiling, the leave-encashment ceiling
(`RET-GRAT-OTHER-001(b)`, `RET-LEAVE-OTHER-001(c)`, `RET-GRAT-AGG-001`, `RET-LEAVE-AGG-001`), the
retrenchment amount (`RET-RETRENCH-001(c)`), and the notified interest rate for `PF-RPF12-001`.

**Open questions flagged for professional review, not verification**: `ITR-1-BAR-002` — the ITR-1 bar
is framed on **s.93** (other-sources) deductions other than s.93(1)(d), which on its face does **not**
catch a s.153 (80TTA/80TTB) claim. Implemented exactly as written and flagged.

### `UNKNOWN` — 1 item

`SAL-ALW-BOOKS-001` — whether any CBDT circular or judicial gloss supports a "books and periodicals"
exemption under the Schedule III Sl. 12 *"wholly, necessarily and exclusively"* limb. Neither
prescribed list opened contains such an allowance.

---

## Sources opened this session

Eight primary gazette documents were downloaded and read in full or in targeted part:

| | Document | Status |
|---|---|---|
| S1 | PIB release 2248005, 01-04-2026 | final |
| S2 | **Income-tax Act, 2025 (No. 30 of 2025)** — 572 pp., gazette CG-DL-E-22082025-265620 | final, **as enacted** (not the FA-2026-amended reprint) |
| S3 | **Finance Act, 2026 (No. 4 of 2026)** — gazette CG-DL-E-31032026-271439 | final |
| S4 | **Income-tax Rules, 2026**, G.S.R. 198(E) — 2,508 pp., gazette CG-DL-E-20032026-271092 | final as notified; amendment chain not traced |
| S5 | Corrigendum G.S.R. 286(E), 16-04-2026 | final |
| S6 | Income-tax (Fourth Amendment) Rules 2026, G.S.R. 822(E), 17-09-2026 | final |
| S7 | **Finance Act, 2025 (No. 7 of 2025)** — gazette CG-DL-E-29032025-262125 | final |
| S9 | EY technical alert on the Rules 2026 | secondary; used only to corroborate the **direction** of change, never a number |

Plus six code files read first-hand for `path:line` claims (`PROGRESS.md` records them).

---

## What to do next

0. **Before Slice 1 authors either rule pack** — apply `10-extensibility.md` E3 and E4 and
   `11-filing-drafts.md` F8 to the schema, and `13-edge-cases.md` R-5's rate-basis partition to the
   computation types. All four are schema decisions, and schema is the one thing here that is
   expensive to change after a pack ships or a draft exists.
1. **Slice 0** — close the verification gap. No code. Nine `PROVISIONAL` golden cases, one blocked
   domain, one alternative route (`egazette.gov.in`) already proven to work.
2. **Slice 1** — `taxrules`: registry, packs, cap shapes, component codes. Author both packs.
3. **Slice 2** — `taxengine`: slabs, rebate, surcharge, both marginal reliefs, cess, rounding.
   This retires H-1, H-3 and H-5 — three of the four defects that produce silently wrong tax at a
   boundary — and needs no database at all.

The placeholder is a leaf that nothing outside itself imports, so it can coexist with the replacement
at zero cost. `09-build-plan.md` gives a slice-by-slice deletion order; nothing is deleted until its
replacement has passing golden tests.
