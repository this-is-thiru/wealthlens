# 03 — Advisory logic

Everything here is `RECOMMENDATION` unless it carries a rule ID. Rule IDs come from
`01-rulebook.md`; nothing in this file restates a statutory number.

---

## A1 — Regime comparison and the break-even deduction level

### A1.1 What the comparison actually is

`INFERENCE (from REG-OPTOUT-YEARLY-001)`: for a salaried user the regime is a **per-tax-year
decision made at return time**, not a standing preference. So the output is not "you are a new-regime
person"; it is **"for TY 2026-27, on these facts, X is cheaper by ₹Y"**, recomputed whenever the facts
change.

`D-SCENARIO` (from `02-lessons.md` L1): a comparison is a set of **materialised scenarios**, never a
scalar. Each is a full `Computation` with a full trace, so the user can see *where* the difference
arises, not only that it exists.

> **Amended by `10-extensibility.md` E6.1.** The pair below is a special case of an N-way comparison
> over the regimes the pack declares and the taxpayer is eligible for. `FACT (S2, ss.200–204)`: the
> 2025 Act already carries five parallel regime sections. The shipped form is E6.1's; the two-regime
> rendering is what it produces at N = 2.

```
compareRegimes(taxYear, taxpayer, inputs):
    candidates = eligibleRegimes(taxYear, taxpayer, inputs)     # 10-extensibility.md E3.4
    results    = candidates.map(r -> compute(taxYear, r.code(), inputs))
    baseline   = results.find(r -> r.regime().isDefault())      # invariant I5: exactly one
    return RegimeComparison(
        scenarios   = results,
        recommended = results.minBy(totalTax),
        baseline    = baseline,
        pairwise    = results.map(r -> ScenarioDiff(baseline, r)),
        drivers     = attributeDifference(baseline.trace, results.minBy(totalTax).trace)
    )
```

### A1.2 `attributeDifference` — where the gap comes from

`RECOMMENDATION`: the two traces are line-for-line comparable because both are emitted by the same
`ComputationNode` protocol keyed by rule ID. Attribution is a **full join on rule ID**:

| Case | Meaning | Example |
|---|---|---|
| present in OLD, absent in NEW | a deduction the new regime disallows | `SAL-HRA-001`, `DED-80C-001`, `SAL-PTAX-001` |
| present in NEW, absent in OLD | rare, but real | — |
| present in both, different amount | a value that differs by regime | `SAL-STDDED-001` vs `-002`, `DED-NPS-EMP-002` (14% vs 10%), `OS-FAMPEN-001` (₹25,000 vs ₹15,000) |
| present in both, same amount | not a driver | slab-independent income lines |
| rate-level difference | the slab tables themselves | `RATE-SLAB-NEW-2026` vs `RATE-SLAB-OLD-2026` |

The drivers are then sorted by absolute rupee contribution and reported as a waterfall. `INFERENCE`:
this is the only honest way to answer "why" — a hand-written explanation would drift from the engine
the first time a rule pack changes.

### A1.3 The break-even deduction level

**The question.** "How much would I need to be claiming under the old regime for it to beat the new
regime?"

`RECOMMENDATION`: define it precisely, because the loose version is misleading.

> **Break-even deduction level** `B(G, taxYear, against, target)` — for a given **gross salary** `G`,
> tax year, and an ordered pair of regimes, the total of *target-only* reliefs at which
> `totalTax(target) = totalTax(against)`, holding every other input fixed. At N = 2 with
> `against = default`, this is the familiar "how much do I need to claim for the old regime to win".
> See `10-extensibility.md` E6.2 — the relief set is derived from `RegimeApplicability`, not hard-coded.

"Target-only reliefs" means, in the shipped two-regime case, the set that `REG-NEW-DISALLOW-001`
removes: `SAL-HRA-001`,
`SAL-LTA-001`, `SAL-PTAX-001`, the whole of Chapter VIII except `DED-NPS-EMP-001`/`-002`,
`DED-AGNIVEER-001`(2) and s.146, plus `HP-NEW-001`'s self-occupied interest.

**Why a closed-form answer is wrong.** `INFERENCE (from REBATE-NEW-MR-001, RATE-SUR-MR-001, SAL-STDDED-001/002, DED-NPS-EMP-002)`:
`totalTax(OLD) − totalTax(NEW)` is **not** monotone and **not** piecewise-linear in a simple way:

- the standard deduction differs by regime (`SAL-STDDED-001` ₹75,000 vs `-002` ₹50,000), so the two
  curves do not start from the same taxable income;
- employer NPS is deductible at 14% in the new regime and 10% in the old (`DED-NPS-EMP-002`), so the
  new regime has a deduction the old one lacks;
- rebate marginal relief (`REBATE-NEW-MR-001`) makes the new-regime curve **flat at 100% marginal
  rate** in a band above ₹12,00,000 — the function has a plateau there;
- surcharge marginal relief (`RATE-SUR-MR-001`) creates the same plateau at each of ₹50,00,000,
  ₹1,00,00,000, ₹2,00,00,000 and — in the old regime only — ₹5,00,00,000;
- the old regime has a 37% surcharge band the new regime does not (`RATE-SUR-OLD-2026` vs
  `RATE-SUR-NEW-2026`), so at very high incomes the gap widens sharply.

**Algorithm.** Because `totalTax` is a pure function (`D-PURE`) and cheap, solve numerically:

```
breakEven(G, taxYear, fixedInputs):
    f(d) = totalTax(OLD, fixedInputs with oldOnlyReliefs = d)
         − totalTax(NEW, fixedInputs)

    # f is monotone non-increasing in d: every extra rupee of old-regime relief
    # can only reduce the old-regime tax, and cannot change the new-regime tax.
    # It is however *piecewise constant* wherever a relief is capped or a
    # marginal-relief plateau is in play, so a root may be an interval.

    lo = 0
    hi = G                                   # more relief than gross is impossible
    if f(0)  <= 0:  return ALREADY_OLD_BETTER      # old wins with no reliefs at all
    if f(hi) >  0:  return NEVER                   # new wins at every achievable level

    binary search on d to the nearest ₹10 (RATE-ROUND-002 granularity)
    then widen to the maximal interval [d_lo, d_hi] where |f| is within ₹10
    return BreakEven(point = d_lo, plateau = [d_lo, d_hi])
```

**Complexity.** `O(log(G/10))` scenario evaluations — about 17 for a ₹10,00,000 gross, 21 for
₹5,00,00,000. Each evaluation is a pure in-memory pipeline run.

`RECOMMENDATION` — **report the plateau, not just the point.** Where the answer is an interval
(which it is whenever a cap or a marginal-relief plateau straddles the root), saying "you need
₹4,37,500" implies a precision the tax system does not have. Say "anywhere from ₹4,30,000 to
₹4,50,000 leaves you within ₹10 either way; below ₹4,30,000 the new regime is clearly cheaper."

### A1.4 What the comparison must **not** do

| Trap | Why | Rule |
|---|---|---|
| Compare using **only** the reliefs the user has already entered | Understates the old regime for a user who has not finished data entry. Report completeness alongside the verdict | `A3` |
| Treat a disallowed house-property loss in the new regime as deferred | `SETOFF-NEW-002` — it is **destroyed**, not carried forward | `SETOFF-NEW-001` |
| Ignore professional tax | `SAL-PTAX-002` — deductible in old, not in new. Small, but it biases **toward** the new regime | `SAL-PTAX-002` |
| Ignore that employer NPS is **better** in the new regime | `DED-NPS-EMP-002` — 14% vs 10%. This biases **toward** the old regime, and is the one large deduction that survives | `DED-NPS-EMP-003` |
| Use the previous year's rule pack | 24 rows differ between TY 2025-26 and TY 2026-27 | `D-YEARKEY` |
| Present a single number without its scenario | `D-SCENARIO`, O-21 | — |

---

## A2 — Salary structuring optimiser

### A2.1 The problem, stated precisely

`RECOMMENDATION`:

> Given a fixed **cost to company** `C`, a set of allocatable components, and the employer's
> structural constraints, choose an allocation `x` that **minimises total tax** while keeping cash
> take-home at or above a floor the user sets.

**Decision variables.** `x_k` = annual rupee amount allocated to component code `k`, for each
`k ∈ AllocatableComponents`.

**Objective.**
```
minimise   totalTax(taxYear, regime*, inputs(x))
where      regime* = argmin over eligibleRegimes(taxYear, taxpayer, inputs(x))
                     of totalTax(...)                            # A1 runs inside; see E6.3
```
`INFERENCE`: the regime choice is **inside** the objective, not outside it, because restructuring can
flip which regime wins — HRA-heavy structuring only pays off in the old regime
(`SAL-HRA-005`), and the old regime's lower standard deduction (`SAL-STDDED-002`) has to be earned back.

### A2.2 Constraints

| # | Constraint | Form | Source |
|---|---|---|---|
| C1 | **Budget** | `Σ_k x_k + employerCosts(x) = C` | employer's CTC definition |
| C2 | **Non-negativity** | `x_k ≥ 0` | — |
| C3 | **Cash floor** | `Σ_{k : isCash(k)} x_k − statutoryDeductions(x) − totalTax ≥ floor` | `D-CASHFLAG`, user input |
| C4 | **Statutory sub-caps** | `x_NPS_EMPLOYER ≤ 14% or 10% of salaryBase(x)` | `DED-NPS-EMP-001`, `-002` |
| C5 | **Aggregate employer cap** | `x_PF_EMPLOYER + x_NPS_EMPLOYER + x_SUPERANN ≤ ₹7,50,000` — or accept the excess as a perquisite | `PF-AGGCAP-001` |
| C6 | **HRA is bounded by rent actually paid** | allocating HRA above `rent − 10% × salaryBase` yields no further exemption | `SAL-HRA-001` limb (b) |
| C7 | **HRA is bounded by the metro percentage** | limb (c) | `SAL-HRA-METRO-2026` |
| C8 | **Employer PF follows basic** | `x_PF_EMPLOYER = 12% × x_BASIC` in the usual case — **not** free | EPF scheme, outside the tax rulebook |
| C9 | **Reimbursements require actual expenditure** | an LTA or uniform allocation is worth its exemption **only if spent** | `SAL-LTA-002`, `SAL-ALW-DUTY-001` |
| C10 | **Employer feasibility** | each component carries an `HrSupportLikelihood`; the optimiser reports it but does not veto | product |
| C11 | **Retirement components are illiquid** | NPS and superannuation reduce cash today; C3 is what stops the optimiser from driving cash to zero | product |

### A2.3 Why this is not a linear program

`INFERENCE (from the rulebook)`: the objective is **not linear**, **not convex**, and **not
continuous** in `x`:

- `SAL-HRA-001` is a `min` of three terms, two of which depend on `x` — **piecewise-linear concave**
  in the HRA allocation, and one of the terms (limb b) depends on `salaryBase(x)`, which itself
  depends on `x_BASIC`. So HRA and basic are **coupled**.
- `DED-80C-001` is a cap at ₹1,50,000 over an aggregate — **a kink**.
- `DED-80C-NPS-CAP-001` applies a sub-cap **before** that cap — **a kink inside a kink**.
- `PF-AGGCAP-001` turns the excess into **income**, so the objective **increases** past that point —
  a change of sign in the gradient.
- `REBATE-NEW-MR-001` and `RATE-SUR-MR-001` are **plateaux**: regions where the derivative of tax
  with respect to income is exactly 1 and 0 respectively on either side.
- The regime `argmin` makes the objective the **lower envelope of two such functions** — so it has
  a non-differentiable crease wherever the two cross.

`RECOMMENDATION`: **do not** attempt a general non-linear solver. Exploit the structure instead.

### A2.4 The algorithm — structured enumeration with exact pricing

```
optimise(C, constraints, taxYear, inputs):
  # Step 1 — partition the components
  #   FIXED     : basic and anything the employer will not move
  #   DERIVED   : components whose value is a function of others (C8: employer PF from basic)
  #   BOUNDED   : components with a statutory or evidential ceiling (C4..C7, C9)
  #   RESIDUAL  : the taxable balancing component ("special allowance")

  # Step 2 — for each BOUNDED component, compute its *saturation point*:
  #   the smallest allocation beyond which the marginal exemption is zero.
  #   HRA          : min( rent − 10%·salaryBase , pct·salaryBase )     SAL-HRA-001
  #   NPS employer : pct · salaryBase                                  DED-NPS-EMP-001/-002
  #   LTA          : documented travel expenditure for the block       SAL-LTA-002, SAL-LTA-BLOCK-001
  #   uniform etc. : documented expenditure                            SAL-ALW-DUTY-001
  #   CEA / hostel : per-child monthly cap × children × 12             SAL-ALW-CEA-2026, -HOSTEL-2026
  #   Each saturation point is a *candidate breakpoint*.

  # Step 3 — build the candidate grid.
  #   Candidates per component = {0, saturationPoint} plus any cap boundary that
  #   falls below it (e.g. PF-AGGCAP-001 crossing, DED-80C-001 crossing).
  #   |candidates| per component is small — 2 to 4 in every case in R1..R24.

  # Step 4 — order components by *exact marginal value*, measured not assumed:
  #     for each component k, for each candidate step Δ:
  #         value(k, Δ) = totalTax(inputs) − totalTax(inputs with x_k += Δ, residual −= Δ)
  #   This is D-MARGINAL. It captures caps, kinks, plateaux and regime flips for free.

  # Step 5 — greedy fill with exact re-pricing:
  #     while budget remains and some (k, Δ) has value > 0:
  #         take the (k, Δ) with the highest value per rupee
  #         apply it, re-run step 4 for the affected components only
  #     (re-pricing is required because DED-80C-001 and PF-AGGCAP-001 make the
  #      components interact: filling one changes another's marginal value)

  # Step 6 — verify with a bounded exhaustive check:
  #     the grid from step 3 is small enough to enumerate exactly.
  #     Enumerate, confirm the greedy result is the true minimum, and if it is not,
  #     return the exhaustive optimum and record the discrepancy as a trace notice.
```

### A2.5 Complexity

Let `n` = number of allocatable components (realistically 8–12: basic, HRA, LTA, special allowance,
employer PF, employer NPS, superannuation, CEA, hostel, meal, uniform, telephone) and `c` = candidates
per component (2–4).

- Step 4 pricing: `O(n · c)` scenario evaluations per round, `O(n)` rounds → `O(n² c)` ≈ 12² × 4 ≈ **576**.
- Step 6 exhaustive: `O(cⁿ)` in the worst case, which is **not** acceptable at `n = 12`, `c = 4`
  (1.7 × 10⁷). `RECOMMENDATION`: restrict the exhaustive verification to the **top 6 components by
  step-4 value**, with the rest pinned at their greedy result — `4⁶ = 4096` evaluations, which at a
  pure in-memory pipeline is milliseconds. Record in the trace that verification was bounded to 6.
- Each scenario evaluation is `O(steps)` ≈ 40 trace lines. No I/O.

`INFERENCE`: total ≈ 5,000 pipeline runs, no database access, entirely deterministic. That is a
sub-second synchronous endpoint, which is why `D-PURE` matters beyond cleanliness.

### A2.6 What the optimiser outputs

`RECOMMENDATION`: not a structure, a **list of changes with their prices and their conditions**:

```
Recommendation
  componentCode          NPS_EMPLOYER
  currentAmount          0
  suggestedAmount        336000
  annualTaxSaving        104832        # measured, not modelled
  regimeAssumed          NEW
  ruleIds                [DED-NPS-EMP-001, DED-NPS-EMP-002, DED-NPS-EMP-003, PF-AGGCAP-001]
  conditions             ["Requires employer to offer NPS under the notified scheme",
                          "Counts toward the ₹7,50,000 aggregate employer cap (PF-AGGCAP-001):
                           your current headroom is ₹1,62,000"]
  cashImpact             −336000       # D-CASHFLAG: this is not take-home
  liquidity              LOCKED_UNTIL_RETIREMENT
  hrSupportLikelihood    HIGH
  traceRef               <pointer into the scenario diff>
```

---

## A3 — Deduction gap analysis and next-best-action ranking

### A3.1 Gap analysis

`RECOMMENDATION`: for each deduction the user's regime allows, compute
**`headroom = statutoryCeiling − alreadyUsed`**, where `alreadyUsed` is taken **from the computation
trace**, not from a separate tally. The trace already knows which components fed which rule
(`D-TRACE`), which is exactly what O-7's two-vocabulary bug destroyed.

| Rule | Ceiling | Common invisible consumer of headroom |
|---|---|---|
| `DED-80C-001` | ₹1,50,000 | **Employee PF (`PF-EEDED-001`, Schedule XV para 1(f))** — usually 12% of basic, and the single most common reason a user's "remaining 80C" is smaller than they think. `H-7` |
| `DED-80C-001` | ₹1,50,000 | Children's tuition fees, life insurance premium, housing-loan **principal** |
| `DED-NPS-SELF-001` | ₹50,000 | Amounts already counted under `DED-80C-001` are barred (`s.124(5)`, `s.124(10)`) |
| `DED-80D-001`…`-007` | two × ₹50,000 | The **₹5,000 preventive sub-limit** cuts across both; senior-citizen status changes ₹25,000 to ₹50,000 |
| `HP-CAP-SELFOCC-003` | ₹2,00,000 aggregate | A second self-occupied house does **not** raise it |
| `SETOFF-HP-001` | ₹2,00,000 | Old regime only; in the new regime the loss is destroyed (`SETOFF-NEW-002`) |
| `PF-AGGCAP-001` | ₹7,50,000 | Employer PF **plus** NPS **plus** superannuation together |

### A3.2 Ranking next-best actions

`RECOMMENDATION`: rank by **measured marginal saving per rupee committed**, then break ties on
liquidity and effort. Never by a notional marginal rate (`D-MARGINAL`, O-23).

```
score(action) = taxSavedPerRupeeCommitted(action)        # measured by recomputation
                × liquidityFactor(action)                 # 1.0 cash-like … 0.4 locked to 60
                × effortFactor(action)                    # 1.0 one click … 0.5 needs HR
```

`RECOMMENDATION`: **show the three factors separately.** A user who needs liquidity should be able to
see that an action was down-ranked for lock-in rather than for tax.

### A3.3 The actions that are not investments

`INFERENCE (from ADV-SAL-001, TDS-SAL-12B-001, TDS-SAL-REDUCE-001)`: for a D3 user with capital gains
fed from `portfolio`, the highest-value action is often **not** an investment at all:

| Action | Trigger | Rule IDs |
|---|---|---|
| **Declare other income to the employer** so salary TDS covers it | other-head income exists and advance tax would otherwise be due | `TDS-SAL-12B-001`, `TDS-SAL-12B-002` (Form No. 122) |
| **Pay advance tax by the next instalment date** | `ADV-THRESHOLD-001` met and the employer route was not taken | `ADV-INSTAL-001`, `ADV-MAR31-001` |
| **Declare a house-property loss to the employer** — the *only* thing that can reduce salary TDS | a let-out or self-occupied property produces a loss | `TDS-SAL-REDUCE-001` |
| **Furnish landlord PAN** | annual rent exceeds ₹1,00,000 and HRA is being claimed | `SAL-HRA-006`, `TDS-SAL-12BB-002` |
| **Plan the LTA journey before the block closes** | the four-calendar-year block is ending | `SAL-LTA-BLOCK-001`, `SAL-LTA-CARRY-001` |
| **Choose the regime in the return** | always, for a salaried user | `REG-OPTOUT-FORM-001` |
| **Convert a declaration into a proof before the employer's cut-off** | a declared deduction has no evidence recorded | `TDS-SAL-12BB-001` |

`RECOMMENDATION`: the last of these is the quiet one that hurts most. A user who declares ₹1,50,000
of 80C in April and produces no proof in January gets the whole year's under-deduction recovered in
one or two months' pay. Surfacing it in December is worth more than any investment suggestion.

---

## A4 — Language and disclaimer standards

These are **product rules with a correctness purpose**, not legal boilerplate.

### A4.1 Never say

| Do not say | Say instead | Why |
|---|---|---|
| "You will save ₹X" | "On the figures entered, TY 2026-27 tax is ₹X lower under the old regime" | The first is a promise about the future; the second is a statement about a computation |
| "Your take-home is ₹X" where `X` includes a perquisite value | "Cash in hand ₹X. Separately, ₹Y of non-cash benefits are taxed as salary" | `H-15`, `D-CASHFLAG`, O-13 |
| "Switch to the new regime" | "For TY 2026-27 the new regime costs ₹X less. You choose this in the return itself — there is no separate form (`REG-OPTOUT-FORM-001`)" | `REG-OPTOUT-YEARLY-001` — it is an annual decision, not a switch |
| "As per Section 80C" | "Under s.123 of the Income-tax Act 2025 for TY 2026-27 (s.80C of the 1961 Act for TY 2025-26)" | The section number **changed between the two years this product supports**. Naming only one is wrong half the time |
| "Tax saved: ₹X" for a locked instrument | "₹X less tax this year; ₹Y locked until 60" | `C11` |
| Nothing at all, where a rule is unverified | "This figure uses a rule we have marked `NEEDS VERIFICATION`" | `D-PROVENANCE` |

### A4.2 Provenance is rendered, not hidden

`RECOMMENDATION`: **every figure in every output carries its rule IDs, and every rule ID carries its
status.** A `NEEDS VERIFICATION` or `STATUS UNCLEAR` rule must be **visible at the point of use**, not
buried in a footnote.

`INFERENCE (from the rulebook)`: at launch this is not a rare decoration. For TY 2025-26, the HRA
metro percentage (`SAL-HRA-METRO-2025`), every allowance limit (`SAL-ALW-LIMITS-2025`) and every
perquisite valuation (`PERQ-CAR-2025`) are unverified. A user computing TY 2025-26 with HRA **will**
see the flag, and should.

Three statuses, three treatments:

| Status | Treatment |
|---|---|
| `VERIFIED` | Rule ID and provision shown on expand. No warning |
| `NEEDS VERIFICATION` | Inline marker on the figure; hovering names what to check and where. **Included** in totals, because excluding it would be a different kind of wrong |
| `STATUS UNCLEAR` | Inline marker; the computation states **which reading it used and why**. `SAL-ALW-NEW-SUBSET-001` is the live case |

### A4.3 The standing disclaimer

`RECOMMENDATION`, shown once per report and once per session, not on every figure:

> These figures are computed from the Income-tax Act 1961 and the Finance Act 2026 for tax year
> 2025-26, and from the Income-tax Act 2025, the Finance Act 2026 and the Income-tax Rules 2026 for
> tax year 2026-27, on the information you entered. They are an aid to planning, not tax advice, and
> not a filing. Figures marked **needs verification** rest on a rule we have not confirmed against a
> primary source. Check anything material with a qualified professional before acting on it.

### A4.4 What a good explanation looks like

`RECOMMENDATION`: three sentences — **what**, **why**, **what changes it** — with the rule IDs
available but not shouted.

> Your HRA exemption is **₹2,28,000** of the ₹6,00,000 you receive.
> It is limited by rent paid minus 10% of salary — the second of the three statutory limits
> (`SAL-HRA-001`). Paying ₹19,000 a month more in rent, with evidence, would raise the exemption to
> the third limit of ₹6,60,000; increasing the HRA component alone would not.

`INFERENCE`: the third sentence is the one that makes the feature worth building. "Your exemption is
₹2,28,000" is a calculator. "Increasing HRA alone would not help you" is advice, and it falls straight
out of knowing **which limb bound** — which the trace records because `SAL-HRA-001` is implemented as
a `LeastOf` that names its winning limb.
