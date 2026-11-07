# 20 — Product and feature gaps

A pass over the **feature surface**, not the plumbing. The question: given the engine `01`–`19`
describes, what does the user actually get, and what is missing from it being the best product of its
kind?

**The finding in one line:** the engine is over-built relative to the product. Almost everything below
is a **projection of things that already exist** — the trace, scenarios, `D-MARGINAL` pricing, the
packs — and costs a week or two each, yet none of them is in `04-product-spec.md`.

---

## P1 — The journey, and where it breaks

| Stage | What exists | What is missing |
|---|---|---|
| **Land** | — | nothing gets a user to a first number in under a minute |
| **Enter** | F4 manual entry, F11 Form 16, F28 Excel | all three are all-or-nothing. 30 components before any answer |
| **Understand** | F2 computation, F10 PDF, the trace | no plain-language layer; no "how complete is this" |
| **Decide** | F3 regime comparison, F13 gaps, F14 optimiser | no **"what do I do this month"**; no life-event framing |
| **Act** | F17 HR letter | no calendar, no reminders, no employer-window awareness |
| **Reconcile** | F11 reconciliation | **no refund/payable forecast** — the one number everyone wants |
| **File** | F8 ITR suggestion | figures are "schedule-ready" in prose only; nothing is copy-paste ready |
| **Return next year** | F18 scenarios, rollover | no year-on-year story; no multi-year view of the 4-, 5- and 8-year rules |

`INFERENCE`: six of the eight stages have a gap, and the two most valuable — *Reconcile* and
*Act* — have the largest ones.

---

## F29 — Refund or payable forecast *(the home-screen number)*

**The job.** *"Will I get money back in July, or will I owe?"* Everything else the product computes is
in service of this, and it is not a feature anywhere in `04-product-spec.md`.

**Why it is possible now.** It is a subtraction between two things already designed:

```
employerWillDeduct  = G4's TDS projection, full year                 (F12)
actualLiability     = the computation, incl. interest and fees       (F2, C1)
otherCredits        = advance tax paid + TDS/TCS elsewhere           (TDS-CREDIT-001)

forecast = actualLiability − employerWillDeduct − otherCredits
           > 0  → payable, and by when (ADV-INSTAL-001, s.425(4) carve-out)
           < 0  → refund, after filing
```

**What makes it good rather than obvious:** the **why**, drawn from the trace diff.

> Expected **refund of ₹47,300**.
> Your employer is deducting on the default regime; opting out saves ₹52,000 on your figures, but
> they cannot apply that — it is exercised in the return (`REG-OPTOUT-FORM-001`). The gap is mostly
> that ₹1,10,000 of your 80C is declared but not evidenced with your employer (`TDS-SAL-12BB-001`).

| | |
|---|---|
| Acceptance | updates on every input change; shows the three components separately; names the top three drivers by rule ID; distinguishes *refund after filing* from *payable now* |
| Rules | `TDS-PROJ-001`, `TDS-CREDIT-001`, `ADV-*`, `INT-*` |
| Reuses | F12's projection, the computation, `ScenarioDiff` |
| RICE | R 1.0 · I 3 · C 0.9 · **E 2** → **1.35** |
| Tier | **MVP** |

`RECOMMENDATION`: this is the product's home screen. Ship it in MVP even if F12's projection is
initially coarse — a forecast with a stated confidence beats no forecast.

---

## F30 — The tax calendar *(what to do, this month)*

**The job.** The right action in September is not the right action in January. Nothing in the design is
time-aware from the user's point of view.

**Every date is already derivable from rules that exist:**

| Event | Derived from |
|---|---|
| Advance-tax instalments — 15 Jun / 15 Sep / 15 Dec / 15 Mar | `ADV-INSTAL-001` |
| Capital-gains shortfall relief — pay by the next instalment or **31 March** | **`INT-DEFER-CARVEOUT-001`** (s.425(4)) |
| Employer proof cut-off | `Employment.payrollCalendar` + `TDS-SAL-12BB-001` |
| Last date for 80C / 80D investment — 31 March | `DED-80C-001`, `DED-80D-001` |
| **LTA block expiry** — 31 Dec 2029 for the current block | `SAL-LTA-BLOCK-001` |
| Return due date — 31 July | `ITR-DUE-*` |
| Revised-return window — 12 months from year end | `s.263(5)` |
| Late-filing interest and fee start | `INT-LATEFILE-001`, `FEE-LATEFILE-001` |
| Regime election — in the return | `REG-OPTOUT-FORM-001` |

**What makes it good:** each entry carries **the rupee consequence of missing it**, measured by
recomputation (`D-MARGINAL`), not a generic reminder.

> **15 December — advance tax instalment.** ₹31,200 due on your December share sale.
> Missing it costs ₹940 in interest under s.425. You can also avoid it entirely by paying the full
> amount on that gain by **31 March** (`INT-DEFER-CARVEOUT-001`).

| | |
|---|---|
| Acceptance | entries generated from the user's own facts, never a static list; each carries a measured cost of inaction; past entries show what actually happened |
| RICE | R 0.9 · I 3 · C 0.9 · **E 2** → **1.22** |
| Tier | **MVP** |

---

## F31 — Offer comparison *(after-tax, not CTC)*

**The job.** *"Offer A is ₹32L at Company X in Pune. Offer B is ₹30L where I am, Bengaluru, better
structure. Which is actually better?"* For the D1 persona — a software engineer — this is the highest-stakes
tax question they will ask all year, and no product answers it well.

**Why it is possible now.** It is two `Inputs`, N regimes each, one `ScenarioDiff`. The engine already
handles every difference that matters:

| Difference | Rule that makes it non-obvious |
|---|---|
| City | `SAL-HRA-METRO-2026` — 50% vs 40% of salary |
| Employer type | `DED-NPS-EMP-001` — 14% for government, 10% otherwise |
| Regime interaction | employer NPS is **14% in the default regime** (`DED-NPS-EMP-002`) |
| Employer contributions | `PF-AGGCAP-001` — the ₹7,50,000 aggregate turns the excess into **income** |
| Non-cash components | `D-CASHFLAG` — a ₹96,000 car perquisite is taxed and is **not** take-home (`H-15`) |
| Joining mid-year | `G4`'s mid-year join, **and** the double standard deduction risk (`T8-1`, ₹2,14,500 on the worked figures) |
| Basic-linked PF | `A2`'s C8 — a higher basic is not free |

**The output is three numbers, not one:** annual **cash in hand**, annual **tax**, and annual
**deferred** (PF, NPS, superannuation — real money the user cannot spend this year).

| | |
|---|---|
| Acceptance | compares ≥2 structures; shows cash / tax / deferred separately; regime chosen per offer; flags the joining-year double-deduction exposure; states the break-even CTC at which the offers are equal |
| RICE | R 0.5 · I 3 · C 0.85 · **E 3** → **0.43** |
| Tier | **Next** — and the product's best differentiator |

---

## F32 — Life events

**The job.** A user does not think *"I should revisit s.126(8)(a)"*. They think *"my mother turned
60"*.

Each event is a **pre-built scenario delta** over the current draft:

| Event | What changes | Rules |
|---|---|---|
| **Parent turns 60** | 80D ceiling ₹25,000 → ₹50,000 | `DED-80D-007` |
| **Child born** | CEA, hostel, LTA child count | `SAL-ALW-CEA-2026`, `SAL-LTA-CHILD-001` |
| **Bought a house** | self-occupied election, interest cap, pre-construction start, **HRA may stop**, 80EE/80EEA sanction windows | `HP-*`, `DED-80EE*`, `SAL-HRA-002` |
| **Moved city** | HRA metro percentage, **per period** | `SAL-HRA-METRO-*`, `13` R-11 |
| **Changed jobs** | two Form 16s, double standard deduction, previous-employer declaration | `T8-1`, `TDS-SAL-12B-001` |
| **Started freelancing** | business income → **regime lock-in** and ITR-3 | `s.202(4)(a)`, `ITR-3-ELIG-001` |
| **Sold shares** | capital gains, advance tax, the s.425(4) carve-out | D3, `INT-DEFER-CARVEOUT-001` |
| **Retiring** | gratuity, leave encashment, commuted pension, VRS once-in-a-lifetime | `RET-*` |
| **Disability certified** | 80U / 80DD, and the mutual-exclusion between them | `DED-80U-001`, `DED-80DD-001` |

`INFERENCE`: **"Started freelancing" is the one with a trap.** `FACT (S2, s.202(4)(a))`: once a person
has business income, the regime option *"once exercised, shall apply to subsequent tax years"*,
*"may be withdrawn only once"*, and after withdrawal they are *"never eligible"* except if business
income ceases. A salaried user who takes ₹50,000 of freelance work has silently changed their regime
rights **for every future year**. No product warns about this.

| | |
|---|---|
| Acceptance | ≥8 events; each produces a real `ScenarioDiff` with a measured rupee effect; the freelancing lock-in warning is explicit and cites s.202(4)(a) |
| RICE | R 0.8 · I 3 · C 0.9 · **E 3** → **0.72** |
| Tier | **Next** |

---

## F33 — Completeness, weighted by what it is worth

**The job.** *"Is this number trustworthy yet?"* — and the answer must be actionable, not a percentage.

**Why it is better than a progress bar:** the weight is **measured**, not assigned.

```
for each rule the user's regime allows but has no input for:
    upperBound = the rule's CapShape evaluated at the user's own facts     # the S of 12 C2
    worth      = tax(current) − tax(current + upperBound)                  # D-MARGINAL
rank by worth, show the top five
```

> **Your computation is missing three things worth up to ₹64,000.**
> Rent paid — up to ₹47,600 (`SAL-HRA-001`) · Health insurance — up to ₹7,800 (`DED-80D-001`) ·
> Savings interest — this will *increase* your tax by about ₹3,100, and omitting it is a mismatch
> against data the department already has (`rule 237(6)`).

`INFERENCE`: including the items that **raise** tax is what separates this from a sales funnel. It is
the same honesty `12-claim-integrity.md` insists on, applied to onboarding.

| | |
|---|---|
| Acceptance | weights measured by recomputation; items that increase tax are shown alongside those that reduce it; drives the onboarding order in F34 |
| RICE | R 1.0 · I 2 · C 0.9 · **E 2** → **0.90** |
| Tier | **MVP** |

---

## F34 — Progressive onboarding

**The job.** A first number in under a minute, then refinement — instead of a 30-field form.

```
Step 1  Gross CTC + city + age                → a coarse estimate, both regimes, stated as coarse
Step 2  Basic / HRA split + rent              → HRA becomes real; the estimate tightens
Step 3  Employer PF and NPS                   → PF-AGGCAP-001 and DED-NPS-EMP-002 engage
Step 4  Investments and insurance             → Chapter VIII
Step 5  Other income and property             → the remaining heads
   … at every step, F33 says what the next step is worth
```

`RECOMMENDATION`: **every step must produce a valid computation**, with its own trace and an explicit
confidence band. `D-NOSILENT` applies to the product as well as the engine — a coarse estimate
labelled coarse is honest; a coarse estimate presented as final is not.

| | |
|---|---|
| Acceptance | a number after step 1; each step re-prices; confidence band shown and narrows; a user may stop at any step and the result is labelled accordingly |
| RICE | R 1.0 · I 3 · C 0.85 · **E 3** → **0.85** |
| Tier | **MVP** |

---

## F35 — The multi-year view

**The job.** Six entitlements in the rulebook span years, and **not one of them is visible anywhere in
the product**:

| Entitlement | Span | Rule |
|---|---|---|
| LTA journeys | 4 **calendar** years — block 2026-2029 | `SAL-LTA-BLOCK-001`, `SAL-LTA-CARRY-001` |
| Pre-construction interest | 5 tax years | `HP-DED-PRECON-001` |
| 80E education-loan interest | 8 years from first payment | `DED-80E-002` |
| Carried-forward HP loss | 8 years — **and destroyed by a regime switch** | `SETOFF-CF-HP-001`, `SETOFF-NEW-002` |
| Carried-forward capital loss | 8 years | `SETOFF-CF-CG-001` |
| VRS deduction | once in a lifetime | `RET-VRS-002` |

> **Your tax timeline**
> LTA — 1 of 2 journeys used in block 2026-2029; the unused one expires **31 Dec 2029**
> Pre-construction interest — instalment **3 of 5**; ₹80,000 remaining over TY 2027-28 and 2028-29
> Carried-forward house-property loss — **₹3,00,000, and it will be destroyed permanently** if you
> compute TY 2027-28 in the default regime (`SETOFF-NEW-002`)

`INFERENCE`: the third line is the single most valuable sentence the product can produce, and it is
only possible because the ledgers in `M3.4` are append-only facts rather than counters. Nothing else
in this market surfaces it.

| | |
|---|---|
| Acceptance | all six entitlements; expiry dates; the regime-switch destruction warning is first-class, not a footnote |
| RICE | R 0.6 · I 3 · C 0.9 · **E 2** → **0.81** |
| Tier | **Next** |

---

## F36 — ITR schedule-ready export

**The job.** D6 promises *"an ITR-form suggestion with schedule-ready figures"*. `F8` decides the form.
Nothing makes the figures transferable.

`RECOMMENDATION`: per-schedule blocks, each with the trace lines behind it, copyable one schedule at a
time — because that is how the utility is filled, one schedule at a time.

```
Schedule S — Salary
  1  Gross salary (s.16)                          21,00,000     ← trace #02
  2  Allowances exempt (Sch. III)                  2,28,000     ← trace #07  SAL-HRA-001
  3  Deductions u/s 19(1)                            77,400     ← trace #10, #11
  4  Income chargeable under Salaries             17,94,600
                                                          [ copy ]
```

| | |
|---|---|
| Acceptance | a **fold over `ItrSchedule` pack data** (`21-itr-forms.md` I5), not a per-form mapper; each figure traceable to a rule or node; a cell the computation cannot produce says so rather than being blank; per-schedule copy; versioned JSON export |
| RICE | R 0.9 · I 2 · C 0.8 · **E 3** → **0.48** |
| Tier | **MVP** — it is the last mile of a promise already made |

---

## F37 — Plain-language explanations

`A4.4` specifies the *shape* — what, why, what changes it. Nothing renders it. The trace is a table of
rule IDs; a user is not a CA.

`RECOMMENDATION`: a templated layer over the trace, driven by `TraceLine.limitedBy` — which exists
precisely so the engine knows **which limb bound**:

> Your HRA exemption is **₹2,28,000** of the ₹6,00,000 you receive.
> It is capped by *rent paid minus 10% of salary* — the second of three statutory limits.
> **Paying ₹19,000 a month more in rent, with evidence, would raise it to ₹6,60,000. Increasing the
> HRA component alone would not.**

`INFERENCE`: the third sentence is the product. It is computable only because `LeastOf` names its
winning limb, and that design choice was made in `M1.3` for exactly this.

| | |
|---|---|
| Acceptance | every trace line has a template; templates live in the **rule pack** (`displayName`), so a rule change updates the explanation; no template ⇒ the rule ID is shown, never a blank |
| RICE | R 1.0 · I 2 · C 0.85 · **E 3** → **0.57** |
| Tier | **MVP** |

---

## F38 — Rent versus buy, and HRA versus home-loan interest

**The job.** *"If I buy, I lose HRA and gain an interest deduction — am I better off?"* The rules make
this genuinely hard, which is why it is worth automating:

- HRA (`SAL-HRA-001`) and self-occupied interest (`HP-CAP-SELFOCC-001`) are **both old-regime only**;
- self-occupied interest is capped at ₹2,00,000, **aggregated across all self-occupied houses**
  (`HP-CAP-SELFOCC-003`);
- a let-out property's interest is **uncapped**, but the loss set-off against other heads is capped at
  ₹2,00,000 (`SETOFF-HP-001`) and **destroyed in the default regime** (`SETOFF-NEW-002`);
- from TY 2026-27 pre-construction interest sits **inside** the cap (`HP-CAP-SELFOCC-002`);
- buying may flip which regime is cheaper, so `A1` has to run inside the comparison.

| | |
|---|---|
| Acceptance | three scenarios — rent, buy-and-occupy, buy-and-let; regime chosen per scenario; multi-year, because interest and the pre-construction run change over time |
| RICE | R 0.4 · I 3 · C 0.8 · **E 3** → **0.32** |
| Tier | **Later** |

---

## F39 — Document vault

The product already stores Form 16s, parse attempts and confirmations (`M4`). Three small additions
make it a feature: **rent receipts and proofs** attached to the claim they support, a per-tax-year
view, and a single **export for the CA**.

`INFERENCE`: this closes `12-claim-integrity.md` C2's loop. The exposure gap `D − E` is *"claimed
beyond what you can prove"*; a vault is where proving happens. Without it the gap is a reproach; with
it, it is a checklist.

| | |
|---|---|
| Acceptance | evidence attaches to a `DeclaredClaim`; the checklist drives upload; the CA export **excludes third-party PII** (`O1.1`, `O5`) |
| RICE | R 0.7 · I 2 · C 0.95 · **E 2** → **0.67** |
| Tier | **Next** |

---

## Revised tiers

| Tier | Features | Note |
|---|---|---|
| **MVP** | F1–F11 · **F29** forecast · **F30** calendar · **F33** completeness · **F34** onboarding · **F36** ITR export · **F37** explanations · F23–F25 claim integrity · **F28** Excel import | the seven additions are all projections of existing machinery |
| **Next** | F12–F18 · **F31** offers · **F32** life events · **F35** multi-year · **F39** vault · F26, F27 | |
| **Later** | F19–F22 · **F38** rent-vs-buy | |

`INFERENCE`: **F29, F30, F33, F34, F37** together are roughly 11 person-weeks and they are the
difference between a tax calculator and a product someone opens in September. None needs a new engine
capability, a new entity, or a schema change — they are all reads of the trace, `ScenarioDiff`, the
ledgers and `D-MARGINAL` pricing. That the engine already supports all of them is the strongest
evidence that `01`–`19` got the foundations right; that none of them was specified is the strongest
evidence the product surface was under-designed.

---

## What genuinely does **not** belong

| Not building | Why |
|---|---|
| Peer benchmarking ("people like you claim ₹X") | `12-claim-integrity.md` C2 — no source, and it argues for claiming what others claim rather than what your facts support |
| Investment product recommendations | `P3` item 10 — regulatory. Recommend **headroom and actions**, never a named product |
| Household or spouse planning | genuinely useful and genuinely out of D1's single-taxpayer scope. `FamilyMember` (`15` F2) does not foreclose it |
| Automatic AIS / 26AS import | needs authenticated department access. `M4` is source-agnostic, so it remains a future `DocumentParser` |
| E-filing | D6. F36 goes as far as the brief allows |
