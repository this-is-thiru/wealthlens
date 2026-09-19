# 10 — Extensibility: N regimes, open vocabularies, changing rule shapes

**This document corrects the design in `03`, `05`, `06`, `07` and `08`.** Where it disagrees with
them, this document wins; the contradicting passages in those files have been amended to point here.

## Why it exists

The brief requires that *a new tax year, a Budget change, a new allowance or deduction, or a new
income head must be data or a new class — never an edit to existing logic.* The first draft met that
for **years** and **values**, and quietly failed it for **regimes** and **vocabulary**. It assumed
exactly two regimes, named them in an enum, and named every component in another enum.

That assumption is not merely unsafe against a hypothetical future. **It is already false today.**

---

## E1 — The statute already ships parallel regimes

`FACT (S2 — Income-tax Act 2025, gazette CG-DL-E-22082025-265620, read this session)`: Chapter XIII
contains a family of alternative-rate regime sections that are **concurrently in force**, each with
its own rate, its own opt-in mechanics and its own eligible person types:

| Section | Regime | Person type | Rate |
|---|---|---|---|
| **s.200** | Concessional domestic-company regime (115BAA successor) | domestic company | 22%, at the option of the person |
| **s.201** | New manufacturing domestic company (115BAB successor) | domestic company | concessional, conditions in s.205(2) |
| **s.202** | Individual / HUF / AOP / BOI / AJP — **the one this product computes** | individual and others | slab table in s.202(1) |
| **s.203** | Concessional co-operative society (115BAD successor) | resident co-op | 22%, at the option of the person |
| **s.204** | New manufacturing co-operative society (115BAE successor) | resident co-op | 15% / 22% by income class |

`FACT (S2, s.205(1))`: those regimes **share a disallowance list**. Section 205(1) opens *"For the
purposes of sections 199(1)(c)(i)(C), 200(1)(a)(iii), 201(3)(a)(iii), 203(1)(a)(ii) and
204(3)(a)(ii), the total income shall be computed without any deduction or exemption, under the
following provisions"* and then lists ss.33(8), 45(3)(a)/(b)/(c), 46, 47(1)(a), 48, 49 and 144.

`FACT (S2, s.205(2))`: they also **share eligibility conditions** — s.205(2) supplies one condition
set used by **both** s.201 and s.204 (business not formed by splitting up or reconstruction, limits
on previously-used plant, and so on).

`FACT (S3 — Finance Act 2026, s.3(4)(b) Table, read this session)`: the Finance Act prices them
separately, with distinct surcharge rows per regime — Sl. No. 9 for *"200 or 201"*, Sl. Nos. 10 and 11
for *"202"*, Sl. No. 12 for *"203 or 204"*, and the residual Part I-B Paragraph F for everyone else.

### What that tells us about the right shape

`INFERENCE (from s.205)`: the Act does **not** model a regime as a monolith. It factors one into
three reusable parts:

```
   regime  =  a rate table
           +  a disallowance set   (shared across regimes — s.205(1))
           +  an eligibility condition set (shared across regimes — s.205(2))
           +  opt-in / opt-out mechanics and lock-in rules
```

`RECOMMENDATION`: **mirror that factoring exactly.** A design whose data model has the same joints as
the statute absorbs a statutory change as a data edit, because the change arrives pre-shaped to fit.
A design that collapses all four parts into one enum constant does not.

`INFERENCE`: my first draft collapsed five statutory regimes into a two-valued enum by scoping to
s.202 and then treating "opted out of s.202(1)" as if it were a peer regime rather than the **residual
case**. That is defensible as a *scope* decision (D1 and D3 are individual-only) and indefensible as a
*type* decision, because it hard-codes the count.

---

## E2 — Honest audit: where the binary assumption leaked in

| # | Location in the first draft | The assumption | Severity |
|---|---|---|---|
| **A1** | `05-domain-model.md` M1.2 — `RegimeApplicability regimes, // OLD \| NEW \| BOTH` | A rule applies to one regime or "both". With four regimes, "applies to {A, C, D}" is inexpressible | **High** — it is in the persisted rule-pack schema, so it is the most expensive one to change later |
| **A2** | `05-domain-model.md` M1.2 — `Map<Regime, PipelineSpec> pipelines` with `Regime` a Java enum | Adding a regime is a recompile | **High** |
| **A3** | `05-domain-model.md` M1.4 — the pack layout ships exactly `pipeline-old.json` and `pipeline-new.json` | The file layout encodes the count | Medium |
| **A4** | `03-advisory.md` A1.1 — `old = compute(...); new = compute(...)` | The comparison is a hard-coded pair | **High** — it is the product's core feature |
| **A5** | `03-advisory.md` A1.3 — `f(d) = totalTax(OLD, …) − totalTax(NEW, …)` | Break-even is defined as a difference between two named regimes | Medium |
| **A6** | `03-advisory.md` A2.1 — `regime* = argmin over {OLD, NEW}` | The optimiser's objective enumerates a literal pair | Medium |
| **A7** | `07-architecture.md` X2.3 — `RegimeStrategy.regime()` returning a `Regime` | The SPI is extensible in *behaviour* but its key type is closed | Medium |
| **A8** | `08-golden-tests.md` coverage map — a literal four-column "year × regime" grid | The test-completeness rule is written against a fixed shape | Low — the *rule* generalises, the table's rendering does not |
| **A9** | `05-domain-model.md` M2 — `public enum ComponentCode { … }`, a closed Java enum | **A new allowance or deduction requires editing an existing file**, which is precisely what the brief forbids | **High** — this one contradicts the brief directly, not just a hypothetical |
| **A10** | `06-algorithms.md` G3 — the two marginal reliefs written as two bespoke functions | A third relief formula, or a change to the `W = U + V` shape, is a code edit | Medium |

`A9` deserves a plain statement: **I justified the closed enum by O-7, O-15 and O-25 — the two
vocabularies, the `EMPLOR_PF` typo, the switch on a non-existent code — and the enum does solve those.
But it solves them by making the vocabulary compile-time, which makes "a new allowance is a data
change" false.** The two goals are not actually in conflict; I reached for the wrong instrument. E4
resolves it.

---

## E3 — Regimes as data

### E3.1 `RegimeCode` is a validated value, not an enum

```java
public record RegimeCode(String value) {                 // ^[A-Z][A-Z0-9_]{1,31}$
    // "S202_DEFAULT", "S202_OPTED_OUT", "S200", "S203", "S204", ...
}
```

Same discipline the repo already applies to broker-charge codes, which CLAUDE.md records as
`[A-Z][A-Z0-9_]*` because a code becomes a Mongo field name. One convention, two modules.

### E3.2 `RegimeSpec` — the statute's own four parts

> **Amended by `17-regime-lifecycle.md`.** `RegimeSpec` gains a **`RegimeLifecycle`** (an entry
> window and an in-force window, which are *not* the same interval — `FACT (S2, s.201(2))`: that
> regime is closed to new entrants and still in force for incumbents) and an optional
> **`BehaviourId`**. `ElectionMechanics` below is replaced by `ElectionProtocol` + `BindingSpec`,
> because `lockIn` as a three-valued enum cannot express s.202(4)(a)(iv)'s permanent ineligibility
> with a revival edge.

```java
public record RegimeSpec(
        RegimeCode          code,
        String              displayName,        // "Default regime (s.202(1))" — shown to users
        Provision           provision1961,      // s.115BAC(1A)  — may be ABSENT_IN_THIS_ACT
        Provision           provision2025,      // s.202(1)

        Set<PersonType>     appliesTo,          // INDIVIDUAL, HUF, AOP, BOI, AJP, DOMESTIC_COMPANY, CO_OP
        RuleId              rateTable,          // → a SlabLadder or FlatRate rule row
        Set<DisallowanceSetId> disallowances,   // the s.205(1) pattern — SHARED, referenced by id
        Set<ConditionSetId>    eligibility,     // the s.205(2) pattern — SHARED, referenced by id

        ElectionMechanics   election,           // below
        boolean             isDefault,          // exactly one per (taxYear, PersonType)
        PipelineSpec        pipeline,           // ordered RuleIds for THIS regime
        Provenance          provenance) {}

public record ElectionMechanics(
        ElectionKind   kind,            // DEFAULT_NO_ACTION | OPT_IN | OPT_OUT
        ElectionVehicle vehicle,        // IN_THE_RETURN | PRESCRIBED_FORM | NONE
        Optional<FormRef> form,         // Form 10-IEA for TY 2025-26; NONE for TY 2026-27 (r.136)
        Optional<Deadline> deadline,    // due date under s.263(1), or "along with the return"
        LockIn lockIn) {}               // NONE | ONCE_EXERCISED_APPLIES_TO_SUBSEQUENT_YEARS
                                        //      | WITHDRAWABLE_ONCE_THEN_PERMANENTLY_INELIGIBLE
```

Every field is drawn from something read this session, not invented:

- `appliesTo` — `FACT (S2, s.202(1)(a)–(e))`: the section lists individual, HUF, AOP other than a
  co-operative society, BOI, and the s.2(77)(g) artificial juridical person. `FACT (S2, ss.200, 203)`:
  the sibling regimes name domestic company and resident co-operative society.
- `disallowances` as a **shared, referenced set** — the s.205(1) pattern, verbatim.
- `eligibility` as a **shared, referenced set** — the s.205(2) pattern, verbatim.
- `election.lockIn` — `FACT (S2, s.202(4)(a)(ii)–(iv))`: for a person **with** business income the
  option *"once exercised, shall apply to subsequent tax years"*, *"may be withdrawn only once"*, and
  after withdrawal the person *"shall never be eligible to exercise the option"*. `FACT (S2,
  s.202(4)(b))`: for a person **without** business income the option is exercised *"along with the
  return of income"*, with no lock-in. **One regime, two election mechanics, selected by a condition.**
  The first draft modelled this as a footnote; it is a field.
- `election.vehicle` — `FACT (S4, Rules 2026 r.136)`: for TY 2026-27 the option *"shall be in the
  return of income"*, i.e. `IN_THE_RETURN` with **no form**. For TY 2025-26 the 1961-Act vehicle is
  `NEEDS VERIFICATION` (Form 10-IEA, unopened). **The vehicle differs between the two years the
  product ships with**, which is by itself sufficient reason for it to be data.

### E3.3 `RegimeApplicability` — a set, never a tri-state

Replacing `A1`:

```java
public sealed interface RegimeApplicability {
    boolean appliesIn(RegimeCode regime);

    record AllRegimes()                       implements RegimeApplicability {}
    record OnlyIn(Set<RegimeCode> regimes)    implements RegimeApplicability {}
    record ExceptIn(Set<RegimeCode> regimes)  implements RegimeApplicability {}
}
```

`ExceptIn` is not redundant with `OnlyIn`. `FACT (S2, s.202(2)(a)(xii))`: the new regime disallows
*"Chapter VIII other than the provisions of sections 124(1) and 124(2), or 125(2) or 146"* — the
statute expresses this as an **exception**, and a rule pack that must restate it as an enumeration
has to be rewritten every time a regime is added. Keeping both forms means the pack can say what the
section says.

### E3.4 Resolution, and what "parallel" actually means

> **Amended by `17-regime-lifecycle.md` L3.** The shipped form is **lifecycle- and history-aware**
> and returns `RegimeOffer` — `available` / `continuing` / `barred(reason)` / `lapsed(policy)` —
> rather than a filtered list. A barred regime with a stated reason is an answer; an absent one is not.

```
eligibleRegimes(taxYear, taxpayer, inputs):          # simplified — see 17 L3 for the shipped form
    pack = rulePacks.load(taxYear)
    return pack.regimes.values()
        .filter(r -> r.appliesTo().contains(taxpayer.personType()))
        .filter(r -> r.lifecycle().applicable(taxYear))
        .filter(r -> r.eligibility().allSatisfiedBy(taxpayer, inputs, pack))
        .sorted(by isDefault first, then displayName)
```

`INFERENCE`: this is the same function whether the answer has one element, two, or five. The
**number** of regimes stops being structural and becomes a property of the pack. "Three regimes run in
parallel from TY 2028-29" is then three `RegimeSpec` rows and three pipelines in one JSON file.

Two invariants, checked at load:

| | Invariant | Why |
|---|---|---|
| I5 | Exactly **one** `RegimeSpec` per `(taxYear, PersonType)` has `isDefault = true` — **strengthened by I11 (`17-regime-lifecycle.md` L6) to count only regimes *applicable* in that year** | `REG-DEFAULT-001` — there is always precisely one no-action outcome |
| I6 | Every `RuleId` in every regime's `pipeline` exists, and every rule's `RegimeApplicability` resolves against **only** regime codes declared in that pack | catches a regime renamed in one place and not another — O-7's failure mode at the regime level |

### E3.5 The pack layout, corrected

Replacing `A3`:

```
resources/rulepacks/2026-27/
  manifest.json
  regimes.json           # RegimeSpec[] — the regimes, their election mechanics, their pipelines
  disallowances.json     # DisallowanceSet[] — shared, referenced by id (the s.205(1) pattern)
  conditions.json        # ConditionSet[]    — shared, referenced by id (the s.205(2) pattern)
  components.json        # the component-code registry — see E4
  rates.json  salary.json  perquisites.json  retirement.json
  deductions.json  houseproperty.json  othersources.json
  setoff.json  tds.json  filing.json
```

No `pipeline-old.json` / `pipeline-new.json`. Pipelines live inside their `RegimeSpec`, so their
count is data.

---

## E4 — An open component vocabulary that is still typo-proof

Resolving `A9`. The first draft's closed enum made "a new allowance is a data change" false. But the
three bugs it was defending against (O-7, O-15, O-25) are **not** actually solved by the compiler —
look at what each one needs:

| Bug | What it actually needs | Does a closed enum supply it? |
|---|---|---|
| **O-7** two vocabularies (`80C` vs `SECTION_80C`) | **one registry** that both the limits and the catalogue resolve against | An enum supplies it, but so does a single registry |
| **O-15** `EMPLOR_PF` typo still summed into gross | gross to be a sum over a **declared partition**, so an unrecognised code is an error rather than an addend (`D-CLOSEDSUM`) | **No.** The enum would have caught the *typo*, but the underlying defect was that `computeGrossSalary` summed every component regardless of code. With the partition rule, a valid-but-unpartitioned code is still an error |
| **O-25** switch on a non-existent code | **no switches on codes at all** — semantics declared as attributes, not branched on in Java | **No.** An enum makes the switch compile; it does not stop you writing one |

`INFERENCE`: the enum was solving one of three problems, and paying for it with the brief's central
requirement. The registry solves all three.

### E4.1 The registry

```java
public record ComponentCode(String value) {}          // ^[A-Z][A-Z0-9_]{1,63}$

public record ComponentDefinition(          // + ComponentLifecycle — 18-temporal-versioning.md V6
        ComponentCode code,
        String        displayName,
        HeadCode      head,               // which income head it belongs to
        ComponentKind kind,               // SALARY | PERQUISITE | EMPLOYER_CONTRIBUTION | ...
        CashNature    cashNature,         // CASH | NON_CASH | CASH_OUTFLOW   — D-CASHFLAG, as DATA
        TaxTreatment  treatment,
        Set<RuleId>   feedsInto,          // which rules consume it
        Set<RuleId>   salaryBaseFor,      // which salary definitions include it
        Provenance    provenance) {}
```

`cashNature` moving from a Java enum field to a **pack attribute** is the important move. `H-15` and
O-13 are enforced by `takeHome` filtering on `definition.cashNature() == CASH`, which works for a
component added next year without the filter changing.

### E4.2 Where typo-safety now comes from

| Layer | Mechanism |
|---|---|
| **Pack load** | every `ComponentCode` used by any rule must exist in `components.json` — invariant I7. `EMPLOR_PF` in a pack fails startup |
| **Request validation** | every code in a request body must resolve in the registry for that tax year — 400 otherwise (`T9-8`, unchanged) |
| **Partition** | gross for a head is the sum over `components.filter(head == SALARIES)`, a **declared** partition. A code outside it is an error, not a silent addend (`D-CLOSEDSUM`) |
| **Reachability** | invariant I3 already requires every `RuleId` to be reachable; extend it — every `ComponentCode` must be `feedsInto` at least one rule, or be explicitly marked `informationalOnly` |

`INFERENCE`: this is **stronger** than the enum, not weaker. The enum caught typos in Java source. The
registry catches them in Java source *and* in rule packs *and* in request bodies *and* catches the
different, worse bug of a well-spelled code that no rule consumes.

### E4.3 The handful of codes Java genuinely names

A few codes appear in engine logic — `EMPLOYEE_PF` feeding the s.123 aggregate, the three employer
contributions feeding `PF-AGGCAP-001`. `RECOMMENDATION`: **do not hard-code them.** Each is already
expressible as a rule-pack relationship:

```json
{ "ruleId": "PF-AGGCAP-001",
  "cap": { "type": "AggregateWithCeiling",
           "contributors": ["EMPLOYER_PF", "EMPLOYER_NPS", "EMPLOYER_SUPERANNUATION"],
           "ceiling": { "type": "Fixed", "amount": "750000" } } }
```

The aggregator reads its contributor list from the rule. It never names a component in Java. So
`FACT (S2, s.17(1)(h))`'s three-way aggregate becoming a four-way aggregate in some future year is one
array element.

### E4.4 `HeadCode` — closed, and here is why that is defensible

`FACT (S2, s.13)`: the 2025 Act enumerates exactly five heads. `RECOMMENDATION`: keep `HeadCode` as a
registry entry like everything else, **but** note that adding a head still requires a **new
`IncomeHead` implementation class** — which is a *new class*, explicitly permitted by the brief, not
an edit to existing logic. The pipeline runner, the set-off engine and the trace do not change.

---

## E5 — Relief and rate formulas as an extensible family

Resolving `A10`. The first draft wrote the two marginal reliefs as two bespoke functions in `G3`.
That is fine until a Finance Act introduces a third, or changes the shape of one.

```java
public sealed interface ReliefShape permits
        FlatWithCeiling,          // REBATE-OLD-001 / REBATE-NEW-001
        ExcessOverThreshold,      // REBATE-NEW-MR-001 : rebate = tax − (TI − threshold)
        ThresholdLadderCeiling,   // RATE-SUR-MR-001  : W = U + V, per band
        NoRelief {

    ReliefResult apply(ReliefContext ctx);   // value + the reason, for the trace
}
```

Each of the four formula statements read this session maps onto one of these:

| Statement | Source | Shape |
|---|---|---|
| ₹12,500 / ₹5,00,000 rebate | S2, s.156(1) | `FlatWithCeiling` |
| ₹60,000 / ₹12,00,000 rebate | S2, s.156(2)(a) | `FlatWithCeiling` |
| `rebate = tax − (TI − 12,00,000)` | S2, s.156(2)(b) | `ExcessOverThreshold` |
| `Wo = Uo + Vo`, `To = Ro + So`, `Wn = Un + Vn`, `Tn = Rn + Sn` | S3, four places | `ThresholdLadderCeiling`, one shape, four parameterisations |

`INFERENCE`: the Finance Act 2026 states the surcharge relief **four times with identical structure
and different ladders**. That is the statute telling you the ladder is the parameter and the formula
is the shape. `G3` becomes: resolve the rule's `ReliefShape`, apply it. A new relief is a new sealed
implementation — a **new class**, which the brief permits — and the pipeline does not change.

`RECOMMENDATION`: the same treatment for rate application. `SlabLadder` already covers progressive
slabs; add `FlatRate` (s.200's 22%, s.204's 15%) and `RateByIncomeClass` (s.204's table) so the same
engine can price the company and co-operative regimes if scope ever widens to them. Neither is needed
for D1, and both are half a page of pack schema.

---

## E6 — N-way advisory

### E6.1 Comparison — replacing `A4`

```
compareRegimes(taxYear, taxpayer, inputs):
    candidates = eligibleRegimes(taxYear, taxpayer, inputs)      # E3.4 — 1, 2, or N
    results    = candidates.map(r -> compute(taxYear, r.code(), inputs))

    cheapest   = results.minBy(totalTax)
    baseline   = results.find(r -> r.regime().isDefault())       # I5 guarantees exactly one

    return RegimeComparison(
        scenarios  = results,
        recommended= cheapest,
        baseline   = baseline,
        pairwise   = results.map(r -> ScenarioDiff(baseline, r)),   # N−1 diffs against the default
        drivers    = attributeDifference(baseline.trace, cheapest.trace))
```

`INFERENCE`: `ScenarioDiff` was already a **pairwise primitive** in `M5.2`, which is what makes this
change small. Comparing against the **default** rather than against "the other one" is the
generalisation that makes the output meaningful at any N: *"doing nothing costs you ₹X; option B saves
₹Y; option C saves ₹Z."* At N = 2 it renders exactly as the first draft's output.

### E6.2 Break-even — replacing `A5`

The break-even level was defined as a difference between two named regimes. Generalised, it is a
property **of a pair**, computed for each candidate against the baseline:

```
breakEven(taxYear, taxpayer, inputs, against = baselineRegime, target = someOtherRegime):
    reliefs = reliefsAllowedIn(target) \ reliefsAllowedIn(against)    # set difference, from the packs
    f(d)    = totalTax(target, inputs with reliefs = d) − totalTax(against, inputs)
    ... binary search as before, to the nearest ₹10, reporting the plateau
```

`reliefsAllowedIn(regime)` is derived from `RegimeApplicability` (E3.3), not hard-coded as
"old-regime-only reliefs". At N regimes the product surfaces the break-even for the **one or two most
promising** candidates, not all `N(N−1)/2` pairs.

### E6.3 Optimiser — replacing `A6`

```
minimise   min over r in eligibleRegimes(...) of totalTax(taxYear, r, inputs(x))
```

`A2.5`'s complexity multiplies by `N` rather than by 2. `INFERENCE`: at the ~9,600 evaluations `G6.2`
estimates for two regimes, five regimes is ~24,000 pure in-memory pipeline runs — still well inside a
synchronous request. The `D-PURE` decision is what buys this headroom, and it is worth noting that it
was the decision that made N-way affordable, not a decision about N at all.

---

## E7 — What stays closed, and why

Not everything should be data. The line worth defending:

| Concept | Open or closed | Why |
|---|---|---|
| Tax years | **data** | D2 |
| Regimes | **data** (E3) | already N in the statute |
| Component codes | **data** (E4) | the brief's "new allowance" case |
| Rule values and caps | **data** | Budget changes |
| Rule identity and provenance | **data** | 19 of 24 year-on-year differences were re-identification |
| Pipelines | **data** (per regime) | disallowance changes the step list |
| `CapShape` | **sealed — new class** | a genuinely new *kind* of arithmetic is rare and deserves review. The brief permits a new class |
| `ReliefShape` | **sealed — new class** (E5) | same |
| `IncomeHead` | **a label, not an SPI** — narrowed by `23-architecture-consolidation.md` A4; a new head is a new `ComputationNode` plus a `HeadCode` | s.13 closes the list at five; a sixth is a new Act |
| `ComputationNode` | **SPI — new class** | the extension point for genuinely new machinery |
| `Money`, `TraceLine`, `Provenance` | **closed** | these are the vocabulary the whole design is written in. Making them configurable would make nothing else checkable |

`RECOMMENDATION`: the test of the line is the **change-classification table** below. If a plausible
change lands in the "edit existing logic" column, the line is in the wrong place.

| Change | Touch |
|---|---|
| A slab, cap, rate or threshold moves | one JSON row |
| A new tax year | a new pack directory |
| A new allowance, deduction or salary component | a `components.json` entry + a rule row |
| An allowance stops being available in one regime | that rule's `RegimeApplicability` |
| A section is renumbered by a new Act | that rule's `provision*` field |
| **A third regime appears, in parallel** | a `RegimeSpec` row + its pipeline + (usually) a reference to an existing disallowance set |
| **A regime's opt-in mechanics change** | that regime's `ElectionProtocol` / `BindingSpec` (`17-regime-lifecycle.md` L3) |
| **The default regime flips** | one `isDefault` flag — invariant I11 makes a half-done flip a load error (`17` L4, Playbook D) |
| **A regime is withdrawn, or closes to new entrants while incumbents continue** | that regime's `RegimeLifecycle` windows and `ContinuationPolicy` (`17-regime-lifecycle.md` L2, L4) |
| A new *kind* of cap or relief formula | a new sealed implementation |
| A new income head | a new `ComputationNode` plus a `HeadCode` (`23` A4) |
| — | **nothing lands in "edit existing logic"** |

---

## E8 — Tests

### T10 — the three-regime synthetic pack

`X5.4`'s synthetic-year test becomes a synthetic-**year-and-regime** test, and it is the acceptance
test for this whole document.

A test-resource pack for a fictional TY 2099-00 declaring **three** regimes for `INDIVIDUAL`:

| Regime | Shape | Purpose |
|---|---|---|
| `SYN_DEFAULT` | slab ladder, `isDefault = true`, `ElectionKind.DEFAULT_NO_ACTION` | the no-action path |
| `SYN_CLASSIC` | different slab ladder, `OPT_OUT`, vehicle `PRESCRIBED_FORM`, lock-in `NONE` | a second peer |
| `SYN_FLAT` | **`FlatRate`**, `OPT_IN`, lock-in `ONCE_EXERCISED_APPLIES_TO_SUBSEQUENT_YEARS`, gated by a `ConditionSet` requiring total income below a threshold | a regime with a *different rate shape*, *different election mechanics* and *eligibility conditions* |

```
assert  the pack loads and validates, including invariants I5, I6, I7
assert  eligibleRegimes() returns 3 for a qualifying taxpayer and 2 for one who fails SYN_FLAT's condition
assert  all three compute, each against its own pipeline, matching hand-derived expectations
assert  compareRegimes() returns 3 scenarios, ranks them, and diffs each against SYN_DEFAULT
assert  a rule declared ExceptIn({SYN_FLAT}) is present in the other two pipelines and absent from SYN_FLAT's
assert  NO production source file was changed to make any of the above pass
```

`INFERENCE`: the last assertion is the one that matters. If it fails, the architecture has not met the
brief — and it fails in CI, on a synthetic pack, rather than in a Budget-day scramble.

### The coverage grid — replacing `A8`

`X5.1`'s rule is unchanged in substance and restated so that it generalises: **the required set of
golden cells is the cross product `taxYears × regimesDeclaredInThatYear's pack`, enumerated from the
packs at test time.** A test walks the packs, computes the required set, and fails on any empty cell.
For the two shipped years that is the same four cells as before; for a year declaring three regimes it
is six, with no test-code change.

---

## E9 — What this costs

`INFERENCE`: almost nothing, because the change is early and mostly in schema.

| Area | Delta |
|---|---|
| `05-domain-model.md` | `RegimeSpec`, `ElectionProtocol`, `RegimeLifecycle`, `RegimeApplicability`, `ComponentDefinition` replace one enum and one tri-state field |
| `06-algorithms.md` | `G1` resolves a pipeline by `RegimeCode` instead of by enum; `G3` dispatches on `ReliefShape` |
| `07-architecture.md` | `RegimeStrategy` → `RegimeProvider`, keyed on `RegimeCode` |
| `03-advisory.md` | three functions take a candidate list instead of a pair |
| Slice plan | **unchanged.** `09-build-plan.md`'s Slice 1 authors `regimes.json` and `components.json` alongside the rest; Slice 2 reads a pipeline by code |
| Build-plan risk | **lower** — `A1`, `A2` and `A9` are all in the persisted pack schema, and schema is the one thing in this design that is expensive to change after packs exist |

`RECOMMENDATION`: make these changes **in Slice 1, before either pack is authored**. Every one of the
high-severity items is a rule-pack schema decision, and the cost of getting them right rises the
moment there is a pack in a released JAR.
