# 06 — Algorithms

Pseudocode, not Java. Every step cites rule IDs from `01-rulebook.md`; no number is restated here.
Design rules (`D-*`) are from `02-lessons.md`; types are from `05-domain-model.md`.

---

## G1 — The computation pipeline

### G1.0 Shape

`D-PURE`: `compute` is a pure function. No I/O, no clock, no persistence.

```
compute(taxYear, regime, inputs) -> Computation
```

`D-REGIMESTEP`: the **step list itself** comes from the rule pack, carried on each `RegimeSpec`
(`10-extensibility.md` E3.2). There is no `if (regime == NEW)` inside a shared base class — that is
what let rebate marginal relief leak into the old regime (O-11, `H-3`) — and no fixed number of
regimes either.

```
compute(taxYear, regimeCode, inputs):
    # ---- Step 0: resolve, or fail ----                               D-REGISTRY, D-FAILFAST
    entry  = registry.entryFor(taxYear)               # throws if absent
    pack   = rulePacks.load(entry.rulePack)           # checksum-verified at startup
    regime = pack.regimes[regimeCode]                 # throws if absent
    spec   = regime.pipeline()                        # the ordered steps for THIS regime

    ctx   = Context(pack, inputs, trace = [])

    # ---- Steps 1..N: run the ordered specification ----
    for stepRef in spec.steps:
        node = nodeRegistry.resolve(nodeRef)            # ComputationNode SPI — 15 Part B
        step.apply(ctx, stepRef.ruleIds)               # appends TraceLines, never returns null
                                                       # D-NOSILENT: any failure propagates
    return ctx.toComputation()
```

`D-NOSILENT` is enforced structurally: `ComputationNode.evaluate` appends to the trace and throws on failure.
There is no return value to substitute a zero for, and the runner has **no** `try/catch`.

### G1.1 The ordered steps

The order follows `DOC-F130-ROWS-001` — the statutory form's own row order — which is itself the
order the Act computes in. Rows in **bold** exist in only one regime.

| # | Step | Rule IDs | Notes |
|---|---|---|---|
| 1 | Partition components into heads | `D-CLOSEDSUM`, `M2` | unknown code ⇒ error, never a silent addend (O-15) |
| 2 | Gross salary | `SAL-CHARGE-001`, `SAL-DEF-001` | salary **due or paid**, including a former employer |
| 3 | Value perquisites | `PERQ-*` | each output flagged `NON_CASH` (`D-CASHFLAG`, `H-15`) |
| 4 | Employer-contribution charges | `PF-AGGCAP-001`, `PF-AGGCAP-002`, `PF-RPF12-001` | these **add** to salary income (`H-6`) |
| 5 | Profits in lieu of salary | s.18 via `RET-NOTICE-001` | |
| 6 | **LTA exemption** | `SAL-LTA-001`…`SAL-LTA-CHILD-001` | old regime only (`SAL-LTA-NEW-001`) |
| 7 | **HRA exemption** | `SAL-HRA-001`…`-004` | old regime only (`SAL-HRA-005`); G1.3 |
| 8 | Special-allowance exemptions | `SAL-ALW-DUTY-001`, `SAL-ALW-PERS-001`, `SAL-ALW-LIMITS-*` | new regime keeps only the prescribed subset (`SAL-ALW-NEW-SUBSET-001`, **`STATUS UNCLEAR`**) |
| 9 | Retirement and termination deductions | `RET-*` | reads `M3.4` cross-year ledgers |
| 10 | **Professional tax** | `SAL-PTAX-001` | old regime only (`SAL-PTAX-002`, `H-13`) |
| 11 | Standard deduction | `SAL-STDDED-001` / `-002` / `-003` | amount differs by regime; capped at salary |
| 12 | **Income under "Salaries"** | — | `DOC-F130-ROWS-001` row 6 |
| 13 | House property, per property | `HP-AV-001`…`HP-CAP-SELFOCC-003` | G1.4 |
| 14 | Other sources | `OS-*` | `OS-DIV-DED-2026` allows **nothing** against dividends from TY 2026-27 |
| 15 | Capital gains, **read in** | D3, `ITR-SPEC-001` | never recomputed; `UNCLASSIFIED` blocks with a notice |
| 16 | Intra-head set-off | `SETOFF-INTRA-001`, `SETOFF-INTRA-CG-001` | |
| 17 | Inter-head set-off | `SETOFF-HP-001`, `SETOFF-PGBP-001`, `SETOFF-CG-001` | **new regime: no HP set-off at all** (`SETOFF-NEW-001`) |
| 18 | Brought-forward set-off | `SETOFF-CF-HP-001`, `SETOFF-CF-CG-001` | **new regime: not available, and destroyed** (`SETOFF-NEW-002`) |
| 19 | **Gross total income** | `DED-GTI-DEF-001` | row 9 |
| 20 | Chapter VIII deductions | R15, `G2` | new regime: only `DED-NPS-EMP-001/-002`, `DED-AGNIVEER-001`(2), s.146 (`DED-NEWREGIME-001`) |
| 21 | Chapter VIII GTI ceiling | `DED-GTI-CAP-001` | aggregate ≤ GTI |
| 22 | **Total income** | — | row 12 |
| 23 | **Round total income** | `RATE-ROUND-002` | **rounding point 1 of 2** |
| 24 | Slab tax, partitioned by rate | `RATE-SLAB-*` | special-rate slices kept separate for step 26 |
| 25 | Rebate and rebate marginal relief | `REBATE-OLD-001` / `REBATE-NEW-001`, **`REBATE-NEW-MR-001` new regime only**, `REBATE-NEW-MR-002`, `REBATE-CAP-001` | G3 |
| 26 | Surcharge, two-bucket | `RATE-SUR-*`, `RATE-SUR-SPLIT-001` | G3 |
| 27 | Surcharge marginal relief | `RATE-SUR-MR-001` | G3 |
| 28 | Cess | `RATE-CESS-001` | on (tax after rebate) + surcharge — `REBATE-ORDER-001` |
| 29 | Relief for arrears | `REL-ARREARS-001` | out of MVP (`F20`); step present, no-op without inputs |
| 30 | Less TDS and TCS | `TDS-CREDIT-001` | row 21 |
| 31 | **Round the payable/refundable** | `RATE-ROUND-002` | **rounding point 2 of 2** |

> **Amendment (W-071c, 2026-09-28, D-113):** step 3 also needs a figure the steps after it produce: whether the
> employee is a *specified employee*, whose salary "by way of monetary payment" — the head's income without the
> perquisites — exceeds the prescribed amount (S2 s.17(1)(c)(ii); 1961 s.17(2)(iii)(c)). The engine measures it
> before valuing perquisites (`salary.monetaryincome`): the pay, less steps 6–9's exemptions and retirement
> deductions and steps 10–11 applied to that pay. Step 9 is therefore computed before step 3; the form row order is
> unaffected.
>
> **Amendment (W-071b, 2026-09-28, D-112):** one step-3 perquisite cannot be valued at step 3. The employer's
> spending on **travel** abroad for medical treatment is excluded only where gross total income *"as computed
> before including therein the said expenditure"* does not exceed the prescribed amount (S2 s.17(3)(b); 1961
> s.17(2) proviso (vi)(B)), and that income is step 19's. The engine therefore values medical treatment abroad
> (`PERQ-MEDABROAD-STAY`, `-TRAVEL`) after the income before it (`salary.incomebeforetravel`) and adds it into
> step 12 (`salary.income`). While the salary head is the only head built, that income is the salary head's; once
> steps 13–18 exist the test must read their total, and the travel must still reach the salary head. The form
> row order is unaffected.

### G1.2 The ordering decision that is not obvious

`REBATE-ORDER-001`: steps 25 → 26 → 28. Rebate reduces income-tax before surcharge and cess, because
`s.155(1)` allows it *"from income-tax"* and `FA 2026 s.3(15)` charges cess on *"such income-tax and
surcharge"*. `INFERENCE`: rebate and surcharge can never co-apply (rebate needs TI ≤ ₹12,00,000;
surcharge starts at ₹50,00,000), so the 25→26 order is untestable — but **cess on post-rebate tax is
testable**, and `T1` covers it.

### G1.3 HRA (step 7)

```
hraExemption(ctx):
    for each period where accommodation was occupied:            # SAL-HRA-003
        if property is owned by the assessee:  continue          # SAL-HRA-002
        if rent actually incurred == 0:        continue          # SAL-HRA-002

        base   = salaryBaseFor(SAL-DEF-HRA-001, period)          # basic + DA-if-provided  (H-4)
        limbA  = hraReceivedInPeriod
        limbB  = rentPaidInPeriod − 10% × base
        pct    = pack.rule(taxYear, SAL-HRA-METRO-<year>)        # D-YEARKEY  (H-2)
                     .percentFor(locationOfAccommodation)
        limbC  = pct × base

        result = LeastOf([("received", limbA),
                          ("rent less 10% of salary", limbB),
                          ("percentage of salary", limbC)])
        emit TraceLine(SAL-HRA-001, out = max(result.value, 0),
                       limitedBy = result.winningLimbName)        # A4.4's third sentence

        if annualRentPaid > landlordPanThreshold(SAL-HRA-006):
            emit Notice(ACTION_REQUIRED, "landlord PAN required", [SAL-HRA-006, TDS-SAL-12BB-002])
```

`limitedBy` is the whole reason `LeastOf` names its limbs (`M1.3`): it turns a number into advice.

### G1.4 House property (step 13), per property

```
for each property:
    if occupancy == SELF_OCCUPIED and it was not let at any time:   # HP-SELFOCC-002
        annualValue = 0                                             # HP-SELFOCC-001
        check at most two such properties are so treated            # HP-SELFOCC-002
    else:
        annualValue = max(expectedLettingValue, actualRent)          # HP-AV-001
        if vacant and actualRent < expectedLettingValue:
            annualValue = actualRent                                 # HP-AV-VACANCY-001
        annualValue -= municipalTaxesActuallyPaid                    # HP-AV-TAX-001

    standardAllowance = 30% × annualValue                            # HP-DED-30-001

    interest = interestForYear                                       # HP-DED-INT-001
    preCon   = preConstructionTotal ÷ 5, instalments 1..5            # HP-DED-PRECON-001

    if occupancy == SELF_OCCUPIED:
        if regime == NEW:
            interestAllowed = 0                                      # HP-NEW-001
        else:
            cap = completionWithinFiveYears and lenderCertificateHeld
                    ? cap2L : cap30k                                 # HP-CAP-SELFOCC-001
            interestAllowed = min(interest + preCon, cap)            # HP-CAP-SELFOCC-002 (FA 2026 s.38)
    else:
        interestAllowed = interest + preCon                          # uncapped

    income = annualValue − standardAllowance − interestAllowed
    apply co-ownership share                                         # HP-COOWN-001

after all properties:
    assert Σ self-occupied interestAllowed ≤ cap2L                   # HP-CAP-SELFOCC-003
```

> **Erratum (W-078b, 2026-10-08):** `interestAllowed = 0` for a self-occupied property in the new regime takes the
> pre-construction instalment too, which holds for TY 2025-26 (1961 s.115BAC(2)(i) removes the whole of s.24(b), its
> Explanation included) but not for TY 2026-27 — S2 s.202(2)(a)(v) removes only "section 22(1)(b), in respect of
> properties referred to in section 21(6)", the instalments are the separate s.22(1)(c), and the Finance Act, 2026
> brought (c) under the s.22(2) cap (s.38) without extending s.202(2)(a)(v) (s.56 omits only (iii)) — so from TY 2026-27
> the new regime allows the instalment within the cap (D-156 C1(a)). Also, the cap is not per property: the houses
> failing the s.22(2)(a) test are limited together to ₹30,000, those passing together to ₹2,00,000, and all to
> ₹2,00,000 (1961 s.24(b) provisos; S2 s.22(2), (5)).
> Verified by: Gazette 265620 pp.47–48, 227; Gazette 271439 pp.41, 46; Gazette 244830 p.34; thc 1961 p.147;
> `HousePropertyInterestTest.evaluate_whenTheNewRegimeAppliesInTaxYear2026_allowsOnlyTheOwnResidencesInstalment`.

`H-12`: `occupancy` drives four different rules. It is a required field on `HouseProperty` (`M3.2`),
not an inference from whether rent is zero.

---

## G2 — Deduction-cap aggregator

This is a separate algorithm because `H-7` and `H-11` both come from treating aggregation as addition.

### G2.1 The general form

```
AggregateWithCeiling {
    contributors            # Set<RuleId> or Set<ComponentCode>
    ceiling                 # Money
    subCapPerContributor    # Optional<Map<contributor, CapShape>>
}

evaluate(agg, ctx):
    # 1. Apply each contributor's OWN cap first
    capped = {}
    for c in agg.contributors:
        raw = ctx.amountFor(c)
        capped[c] = agg.subCapPerContributor.containsKey(c)
                      ? min(raw, agg.subCapPerContributor[c].apply(ctx))
                      : raw

    # 2. Sum, then apply the ceiling
    total   = Σ capped.values()
    allowed = min(total, agg.ceiling)

    # 3. Record the residue per contributor — downstream rules need it
    emit TraceLine(..., out = allowed,
                   limitedBy = total > ceiling ? "aggregate ceiling" : "actual amounts",
                   detail = capped, residue = total − allowed)
```

**The order matters and is not interchangeable.** Sub-cap first, then aggregate.

> **Erratum (W-018, 2026-09-19):** this pseudocode emits one residue, `total − allowed`, but both
> golden cases that consume a residue use a **different** one: `raw − capped`, what a contributor's
> own sub-cap excluded. T5-1's ₹50,000 is `1,50,000 − 1,00,000` and T5-3's ₹1,10,000 is
> `1,50,000 − 40,000`, and in T5-3 the ceiling does not bind at all, so `total − allowed` is zero
> there. The two coincide only when exactly one contributor exists and the ceiling binds.
>
> `AggregateOutcome` therefore exposes both, named apart: `subCapResidue` per contributor
> (`raw − capped`, which s.124(3) spends) and `headroom` (`ceiling − allowed`, which A3 reports).
> T5-2 needs a third figure this pseudocode does not emit at all — `aggregate`, the sum before the
> ceiling — because that case's whole point is that the deduction is identical and the reported
> aggregate differs by ₹1,20,000.
> Verified by: `08-golden-tests.md:472-474, 490, 499-501`; `./mvnw test -pl backend -Dtest=AggregateWithCeilingTest`.

### G2.2 `DED-80C-001` — the s.123 ceiling

```
contributors         = every ComponentCode mapped to Schedule XV in this year's pack
                       — including EMPLOYEE_PF (Sch. XV para 1(f))            H-7
                       — including EMPLOYEE_NPS (Sch. XV para 1(y))
                       — including superannuation (Sch. XV para 1(g))
                       — including annuity plans (Sch. XV para 1(x))          DED-MAP-002
subCapPerContributor = { EMPLOYEE_NPS : PercentOfBase(DED-80C-NPS-CAP-001) }
ceiling              = Fixed(DED-80C-001)
```

Then, and only then:

```
DED-NPS-SELF-001 (s.124(3)):
    eligible = employeeNpsContribution − amountOfEmployeeNpsAllowedUnderS123
    allowed  = min(eligible, cap)                                  # s.124(5), s.124(10)
```

`INFERENCE`: `DED-MAP-021` — the 2025 Act has **no 80CCE section** because the aggregation is
structural in s.123. Modelling it the 1961-Act way ("80C + 80CCC + 80CCD(1), then cap") is how the
aggregate gets forgotten. Here the ceiling **is** the deduction, so it cannot be.

`DED-80C-001`'s `contributors` set is **pack data**, so adding a qualifying investment to Schedule XV
in a future year is a JSON edit (`M0`).

### G2.3 `DED-80D-001`…`-011` — four interacting limits

`H-11`. Two `AggregateWithCeiling` instances, plus a cross-cutting sub-limit and two conditions:

```
selfFamily = AggregateWithCeiling(
    contributors = { insurancePremiumSelfFamily,          # DED-80D-001, cap 25k or 50k
                     preventiveCheckupSelfFamily,         # inside DED-80D-001
                     medicalExpenditureSelfFamily },      # DED-80D-003, cap 50k
    ceiling      = Fixed(DED-80D-006))                    # 50k

parents = AggregateWithCeiling(
    contributors = { insurancePremiumParents,             # DED-80D-002
                     preventiveCheckupParents,
                     medicalExpenditureParents },         # DED-80D-004
    ceiling      = Fixed(DED-80D-006))                    # 50k, independent

# cross-cutting, applied BEFORE either aggregate:
preventiveTotal = min(preventiveSelfFamily + preventiveParents, DED-80D-005)   # 5k
    then allocated back to the two buckets in the ratio claimed

# senior-citizen substitution, applied to the insurance sub-cap:
if insuredPersonIsSeniorCitizen: insuranceSubCap = 50k else 25k                 # DED-80D-007

# exclusivity:
if medicalExpenditureClaimedForSeniorCitizen and anyPremiumPaidForThatPerson:
    reject the medical expenditure with a Notice                                # DED-80D-008

# payment mode:
if paidInCash and not preventiveCheckup: reject with a Notice                   # DED-80D-010

# multi-year premium:
allowed_thisYear = lumpSum × (1 / numberOfRelevantTaxYears)                     # DED-80D-009
```

`INFERENCE`: seven distinct rules, one number out. Any single flat cap is wrong; the question is only
by how much.

### G2.4 `PF-AGGCAP-001` — the employer aggregate, which works the other way

```
aggregate = EMPLOYER_PF + EMPLOYER_NPS + EMPLOYER_SUPERANNUATION
excess    = max(aggregate − ceiling, 0)                            # PF-AGGCAP-001

if excess > 0:
    emit PERQ_EMPLOYER_CONTRIB_EXCESS = excess                      # adds to INCOME
    accretion = prescribedFormula(excess, interestCredited)         # PF-AGGCAP-002
    emit PERQ_EMPLOYER_CONTRIB_ACCRETION = accretion                # NEEDS VERIFICATION
```

`INFERENCE`: this is an `AggregateWithCeiling` whose **residue is the output**, not the allowed
amount. `H-6`. And `DED-NPS-EMP-001`/`-002` deducts the *same* `EMPLOYER_NPS` amount elsewhere in the
pipeline (step 20) — the two rules are on opposite sides and must **never** be netted (`R13` worked
example).

---

## G3 — Marginal relief: one implementation each

`D-ONEIMPL`. Two **different** reliefs, each implemented once, deliberately **not** sharing code —
their shapes genuinely differ, and the placeholder's failure (O-11) was treating them as one concept.

> **Amended by `10-extensibility.md` E5.** Both are `ReliefShape` implementations resolved from the
> rule row, not two bespoke functions the pipeline calls by name. `ExcessOverThreshold` covers
> `REBATE-NEW-MR-001`; `ThresholdLadderCeiling` covers all four statements of `RATE-SUR-MR-001`.
> A third relief formula is a new sealed implementation, which the brief permits.

### G3.1 Rebate marginal relief — `REBATE-NEW-MR-001`

```
rebateWithMarginalRelief(totalIncome, taxBeforeRebate, pack, regime):
    if regime != NEW:                                       # H-3, s.156(2) opens "chargeable under 202(1)"
        return plainRebate(REBATE-OLD-001)                  # NO marginal-relief limb exists

    threshold = pack.rule(taxYear, REBATE-NEW-001).incomeCeiling
    flat      = pack.rule(taxYear, REBATE-NEW-001).maxRebate

    if totalIncome <= threshold:
        rebate = min(taxBeforeRebate, flat)                 # REBATE-NEW-001
    else:
        excess = totalIncome − threshold
        if taxBeforeRebate > excess:
            rebate = taxBeforeRebate − excess               # REBATE-NEW-MR-001
        else:
            rebate = 0

    rebate = min(rebate, taxAtNewRegimeRates)               # REBATE-NEW-MR-002
    rebate = min(rebate, taxBeforeRebate)                   # REBATE-CAP-001
    return rebate
```

**This function is not in the old-regime pipeline at all** (`D-REGIMESTEP`), which is the structural
fix for O-11 — the old-regime pipeline uses `plainRebate` and has no path to the relief branch.

### G3.2 Surcharge marginal relief — `RATE-SUR-MR-001`

`FACT (S3)`: the formula is `W = U + V` — `U` = tax + surcharge on the threshold, `V` = income above
the threshold. It appears four times with four different threshold ladders (old/new × 1961/2025 Act),
so it is **one generic function parameterised by the pack's ladder**.

```
surchargeWithMarginalRelief(totalIncome, taxBeforeSurcharge, pack, regime, specialRateSlices):

    # -- two buckets, because of RATE-SUR-SPLIT-001 --
    normalTax  = tax on income other than dividend / special-rate gains
    specialTax = tax on the dividend and ss.111A/112/112A (or 196/197/198) slices

    band       = pack.surchargeLadder(taxYear, regime).bandFor(totalIncome)
    normalRate = band.rate
    specialRate= min(band.rate, cap15)                       # RATE-SUR-SPLIT-001

    surcharge  = normalRate × normalTax + specialRate × specialTax
    unrelieved = taxBeforeSurcharge + surcharge

    threshold  = band.marginalReliefThreshold                # the band's lower bound, from Table 2
    if threshold is present:
        U = taxAndSurchargeOn(exactly threshold)             # recursive call, no relief applied
        V = totalIncome − threshold
        ceiling = U + V
        if unrelieved > ceiling:
            relief    = unrelieved − ceiling
            surcharge = surcharge − relief
            emit TraceLine(RATE-SUR-MR-001, limitedBy = "marginal relief at ₹" + threshold)

    return surcharge
```

Four ladders in the packs — note that the new-regime ladders **have no ₹5 Cr band**, because there is
no 37% rate to relieve into (`RATE-SUR-NEW-2025`, `RATE-SUR-NEW-2026`). `H-1`.

`U` is computed by re-entering the tax computation at exactly the threshold with relief disabled. That
is one extra pure evaluation, and it is the only honest way to get `U` when the slab ladder,
special-rate split and rebate all bear on it.

---

## G4 — Monthly TDS projection (D5)

### G4.1 What it is

`TDS-PROJ-001`. `TDS-SAL-AVG-001` says the employer deducts at the **average rate on estimated
full-year income**. So the projection is a **running re-estimate**, not a schedule.

```
projectTds(taxYear, employments, declarations, proofs, asOfPayrollRun):
    runs = payrollRuns(taxYear, employments)    # handles mid-year join: fewer than 12 runs

    deductedSoFar = 0
    projection    = []

    for run in runs:
        # (a) re-estimate the FULL YEAR on what is known at this run
        estimate = estimateFullYearSalaryIncome(asOf = run.date)
                     + declaredOtherIncome(asOf = run.date)          # TDS-SAL-12B-001
                     − declaredHousePropertyLoss(asOf = run.date)    # TDS-SAL-REDUCE-001 (i)
                     + previousEmployerSalary(asOf = run.date)       # Form No. 122

        # (b) the employer applies claims that are DECLARED and, past the cut-off, PROVEN
        claims = effectiveClaims(declarations, proofs, asOf = run.date)   # M3.3

        # (c) full-year tax under the regime the employer is applying
        annualTax = compute(taxYear, employerRegime, estimate, claims).taxPayable
                      − declaredOtherTdsAndTcs(asOf = run.date)      # TDS-SAL-REDUCE-001 (ii)

        # (d) spread the remainder over the runs that are left      TDS-SAL-ADJUST-001
        remaining   = annualTax − deductedSoFar
        thisRun     = remaining ÷ runsRemainingIncludingThis
        deductedSoFar += thisRun

        projection.append(ProjectedDeduction(run, thisRun, annualTax, reasonForChange))

    return projection
```

### G4.2 The five events that re-shape the rest of the year

| Event | Enters at | Effect |
|---|---|---|
| **Mid-year joining** | `runs` has fewer than 12 entries | the same annual tax is spread over fewer months, so each month is larger |
| **Salary revision** | step (a) at the run after the revision | annual estimate rises; `remaining` absorbs the whole back-effect in one month unless spread |
| **Previous employer declared** | step (a), via Form No. 122 | annual estimate **jumps**; `TDS-SAL-REDUCE-001` means it can only raise the deduction |
| **Declaration → proof** | step (b) | if the proof never arrives, claims fall away at the cut-off and `remaining` spikes |
| **Bonus** | step (a) in the month it is known | one-off rise in the estimate, spread over remaining runs |

### G4.3 The asymmetry that must be encoded

`TDS-SAL-REDUCE-001`: *"the tax deductible from income under the head 'Salaries' shall not be reduced
in any case, except on account of (i) loss under the head 'Income from house property'; and (ii) the
tax deducted and collected as per other provisions."*

```
assert projectedAnnualTax >= taxOnSalaryAlone
       − declaredHousePropertyLoss.taxEffect
       − declaredOtherTdsAndTcs
```

`INFERENCE`: declaring interest income to the employer can only **increase** TDS; it can never reduce
it. So "declare other income" and "pay advance tax" are genuinely alternatives, not the same thing
(`A3.3`, `ADV-SAL-001`).

### G4.4 The output that matters

`RECOMMENDATION`: the headline is not the monthly figure. It is:

> **March shock**: if the proofs you have declared but not yet evidenced (₹X) are not filed by your
> employer's cut-off, your February and March deductions rise from ₹A to ₹B.

That number is computable in September and actionable in January, which is the whole point of `F12`.

---

## G5 — Form 16 / Form No. 130 parsing, validation and reconciliation

### G5.1 Strategy: structural first, positional never

`FACT (DOC-F130-PREFILL-001)`: *"Some of the information in the Form would be pre-filled to the extent
possible."* `INFERENCE`: layout will vary by issuing system and will change between years. A
coordinate-based or fixed-offset parser is guaranteed to break.

Four layers, each falling back to the next:

| Layer | Method | Confidence contribution |
|---|---|---|
| **L1 — structural** | Extract the PDF's text with positions, detect the Part A / Part B / Part C boundaries by their **heading text**, then locate rows by their **statutory label** (`"Standard deduction under section 19(1) [Table: Sl. No.2]"`) | 0.95+ when the label matches exactly |
| **L2 — anchored numeric** | Within an identified row, take the rightmost numeric token on the line, normalising Indian digit grouping | 0.85–0.95 |
| **L3 — checksum-driven** | Use the form's **own arithmetic** to infer a missing cell: row 1(d) = 1(a)+1(b)+1(c); row 3 = 1(d)−2(f); row 6 = 3+1(e)−5; row 9 = 6+8; row 12 = 9−11 | 0.70–0.85, and **only** where the derivation is uniquely determined |
| **L4 — unmapped** | Keep the raw text in `ParseAttempt.unmappedText` and ask the user | no value pre-filled (`D-NOSILENT`) |

`RECOMMENDATION`: L3 is the layer that makes this robust, because `DOC-F130-ROWS-001` is a **closed
arithmetic system**. Every total is a stated function of its parts, so a single missing cell in a
group is recoverable and a **contradiction is detectable**.

### G5.2 Validation — the form checking itself

```
validate(extracted):
    # arithmetic identities from DOC-F130-ROWS-001
    check 1(d) == 1(a) + 1(b) + 1(c)
    check 2(f) == 2(a) + 2(b) + 2(c) + 2(e)
    check 3    == 1(d) − 2(f)
    check 5    == Σ 4(a..g)
    check 6    == 3 + 1(e) − 5
    check 8    == 7(a) − 7(b)
    check 9    == 6 + 8
    check 11   == Σ 10(a..k) deductible column
    check 12   == 9 − 11
    check 17   == 13 + 15 + 16 − 14
    check 21   == 17 − 18 − 19 − 20

    # cross-part identities
    check Part B total tax deducted ≈ row 21 + row 19 + row 20      # DOC-F130-TAXSUM-001 — tolerance ₹10
    check Part A period is inside the tax year                       # DOC-F130-A-001

    # semantic checks against the rule pack
    check row 4(b) ∈ { SAL-STDDED-001.amount, SAL-STDDED-002.amount }
    check row 4(b) matches the regime in row A                       # a mismatch is high-signal
    check row 16 ≈ 4% × (13 + 15 − 14)                               # RATE-CESS-001, REBATE-ORDER-001

    any failure ⇒ lower the confidence of every cell in the failing group,
                  and surface the contradiction to the user — never pick a side silently
```

`INFERENCE`: the `row 4(b)` check is the single most valuable one. The standard deduction is a
**two-valued** field (`SAL-STDDED-001` vs `-002`) that independently discloses the regime, so it
cross-checks row A. If they disagree, one of the two was misread, and the user can be asked a precise
question instead of a vague one.

### G5.3 Multi-employer assembly

`DOC-F130-MULTI-001` and `DOC-F130-ANNEX-001`:

```
assemble(documents for one taxYear):
    partABs = [ (tan, periodFrom, periodTo, quarterlyTdsRows) for each document ]
    partCs  = [ annexureI for each document that has one ]

    # a) periods must not overlap, and gaps are worth reporting
    assert no two (tan, period) ranges overlap
    if gaps exist: emit Notice(ATTENTION, "unemployed or undocumented period", ...)

    # b) Part C may be one (last employer) or N (each employer)      DOC-F130-ANNEX-001
    if partCs.size() == 1 and partABs.size() > 1:
        mode = LAST_EMPLOYER_CONSOLIDATED
        # row 1(e) "salary from other employer(s)" should be non-zero
        if row1e == 0: emit Notice(ACTION_REQUIRED,
              "previous employer's salary appears not to have been declared", [TDS-SAL-12B-001])
    else if partCs.size() == partABs.size():
        mode = PER_EMPLOYER
        # exemptions and the standard deduction may be DOUBLE-COUNTED across employers
        check Σ row 4(b) <= one year's standard deduction            # SAL-STDDED-003
        check Σ row 2(b) is consistent with one year of HRA          # SAL-HRA-003
    else:
        mode = MIXED  →  verdict CANNOT_COMPARE                      # M4.2
```

`INFERENCE`: `mode = PER_EMPLOYER` is where the money is. Two employers each granting a full standard
deduction and a full HRA exemption produces a genuine shortfall the user will owe at filing, and it is
detectable by summation alone. This is the highest-value single check in the whole product for the
"Priya" persona (`P1`).

### G5.4 Reconciliation against the computation

Produces `ReconciliationReport` (`M4.2`). Every difference must map to one of the closed reasons
there, or be reported as **unexplained** — never averaged away.

---

## G6 — The optimiser

Formulation, constraints and the algorithm are in `A2`. What belongs here is the engineering.

### G6.1 Why the objective is evaluated, never modelled

`D-MARGINAL`, from O-23. The marginal value of a rupee moved into a component is measured by
**recomputing**:

```
value(component, delta) = compute(inputs).taxPayable
                        − compute(inputs with component += delta,
                                          residual   −= delta).taxPayable
```

This is exactly right at a cap boundary, at a rebate plateau (`REBATE-NEW-MR-001`), at a surcharge
threshold (`RATE-SUR-MR-001`) and across a regime flip — none of which a notional marginal rate can
represent. It costs one extra pure evaluation.

### G6.2 Complexity, restated

| Phase | Evaluations | At `n=12`, `c=4` |
|---|---|---|
| Step 4 pricing, all rounds | `O(n² c)` | ≈ 576 |
| Step 5 greedy re-pricing | `O(n²)` | ≈ 144 |
| Step 6 bounded exhaustive, top 6 components | `O(c⁶)` | 4,096 |
| Regime evaluated inside the objective | ×2 | ≈ 9,600 total |

Each evaluation is `O(31)` pipeline steps with no I/O. `INFERENCE`: a synchronous sub-second endpoint,
which is only true because `D-PURE` holds.

### G6.3 Guarding the bound

`RECOMMENDATION`: record in the trace that verification was bounded to the top 6 components, and
which 6. If the greedy answer and the exhaustive answer differ, return the **exhaustive** one and emit
a `Notice(INFO, ...)` naming the discrepancy — that difference is the signal that the component
interaction model needs another breakpoint.

---

## G7 — The explainability trace

### G7.1 Format

`TraceLine` is defined in `M5.1`. Three properties make it useful rather than decorative:

1. **Complete** — every step emits, including steps that produce zero. A zero with a reason
   (*"HRA exemption nil: new regime, `SAL-HRA-005`"*) is the opposite of O-1's silent zero.
2. **Addressable** — `Computation.valueOf(ruleId)`. Reports, the ITR mapping and `ScenarioDiff` all
   read by rule ID, never by index or field name.
3. **Self-describing** — `label` and `provenance` come from the pack, so a rule-pack change updates
   the explanation without touching a renderer (`D-VIEWFROMTRACE`).

### G7.2 Rendered

```
#  rule              label                                    in          out    limited by
─────────────────────────────────────────────────────────────────────────────────────────────
02 SAL-DEF-001       Gross salary                                   28,00,000
03 PERQ-MOTOR-CAR    Motor car perquisite (≤1.6L, +chauffeur)          96,000    ₹5,000+₹3,000/month ⓘ non-cash
04 PF-AGGCAP-001     Employer contribution above the cap               74,000    aggregate ceiling
07 SAL-HRA-001       House rent allowance exemption      6,00,000  −2,28,000    rent less 10% of salary
10 SAL-PTAX-001      Professional tax                                  −2,400
11 SAL-STDDED-002    Standard deduction (old regime)                  −50,000
12 —                 Income under "Salaries"                        26,89,600
13 HP-CAP-SELFOCC-001 House property loss                           −2,00,000    ₹2,00,000 cap
19 DED-GTI-DEF-001   Gross total income                             24,89,600
20 DED-80C-001       Deductions under s.123                         −1,50,000    aggregate ceiling
23 RATE-ROUND-002    Total income, rounded                          23,39,600
24 RATE-SLAB-OLD-2026 Tax on total income                             5,14,380
25 REBATE-OLD-001    Rebate under s.156(1)                                   0    income above ₹5,00,000
28 RATE-CESS-001     Health and education cess @ 4%                    20,575
31 RATE-ROUND-002    Tax payable, rounded                            5,34,960
```

`ⓘ` marks a `Notice`. `limited by` is `LeastOf`'s winning limb or the cap that bound — the field that
turns the trace into `A4.4`'s advice.

### G7.3 Projections

| Output | Projection |
|---|---|
| JSON API | the trace, plus totals, plus provenance per line |
| PDF (D6) | grouped by `StepKind`, with `A4.3`'s disclaimer and `A4.2`'s status markers |
| ITR schedule figures (D6) | `valueOf(ruleId)` for each schedule cell, mapped by the pack |
| `ScenarioDiff` (`A1`, `A2`, `A3`) | full outer join of two traces on `ruleId` |
| Reconciliation (`G5.4`) | join of the trace against `DOC-F130-ROWS-001` |

`D-VIEWFROMTRACE`: all five read the same structure. A section with no lines renders as nothing,
visibly — so O-20's unreachable PDF sections become observable rather than silent.
