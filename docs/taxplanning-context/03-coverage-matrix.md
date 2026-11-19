# 03 — Domain coverage matrix

Status values: `IMPLEMENTED` / `PARTIAL` / `NOT FOUND`. Formulas are copied verbatim from the
source. Paths are relative to `backend/src/main/java/com/thiru/wealthlens/`.

No assessment is made here of whether any computation is legally correct. Where the code's behaviour
differs from what a comment or Javadoc in the same file claims, that is recorded factually and
cross-referenced to `04-constants-tests-history.md` §5 (Observations).

---

## 1. Salary and CTC

| Item | Status | Evidence |
|---|---|---|
| CTC → gross → taxable salary breakup | `IMPLEMENTED` | `engine/AbstractTaxEngine.java:52,69` |
| Basic | `PARTIAL` — read by code `"BASIC"`, no validation it exists | `engine/OldRegimeTaxEngine.java:88,268,336`; `NewRegimeTaxEngine.java:72,173,204` |
| DA | `PARTIAL` — only as a formula variable, never summed separately | `OldRegimeTaxEngine.java:337`; `NewRegimeTaxEngine.java:205` |
| HRA | `IMPLEMENTED` (old regime only) | `OldRegimeTaxEngine.java:262-284` |
| Special allowance | `PARTIAL` — no tax treatment; only the restructuring pool | `recommendation/RestructuringEngine.java:66,184-192` |
| LTA | `PARTIAL` — exempted in full, no cap, no block tracking | `OldRegimeTaxEngine.java:286-293` |
| Bonus / variable pay | `NOT FOUND` — no code, no handling; `ParsedCtcDto` names them but is orphaned | `parsing/ParsedCtcDto.java:30-31` |
| Employer PF | `IMPLEMENTED` — capped at a policy rate × BASIC | `OldRegimeTaxEngine.java:87-98`; `NewRegimeTaxEngine.java:71-82` |
| Gratuity | `NOT FOUND` in code; catalogue-only | `allowance-catalogue-2025-26.json` `GRATUITY_RECEIVED`; no engine reader |
| Employer NPS — 80CCD(2) | `PARTIAL` — wired, but the seeded formula does not evaluate (see §8) | `OldRegimeTaxEngine.java:100-114`; `NewRegimeTaxEngine.java:84-98` |
| Reimbursements | `NOT FOUND` as a concept; `BOOKS_PERIODICALS`, `TELEPHONE`, `UNIFORM` are exempted in full | `OldRegimeTaxEngine.java:140-159` |
| Meal / food cards | `IMPLEMENTED` | `NewRegimeTaxEngine.java:100-128`; `OldRegimeTaxEngine.java:116-122` |
| Other allowances | `NOT FOUND` — any component code the engines do not name is summed into gross and never exempted | `AbstractTaxEngine.java:138-146` |
| Perquisites — car | `IMPLEMENTED` | `engine/PerquisiteValuationService.java:94-130` |
| Perquisites — driver | `IMPLEMENTED` | `AbstractTaxEngine.java:217-222` |
| Perquisites — accommodation | `NOT FOUND` — three rate fields stored, no reader | `policy/entity/PerquisitePolicyEntity.java:64-71` |
| Perquisites — concessional loan | `NOT FOUND` — field stored, no reader | `PerquisitePolicyEntity.java:61-62` |
| Perquisites — employer school fees | `NOT FOUND` — field stored, no reader | `PerquisitePolicyEntity.java:73-74` |
| ESOP / RSU perquisite valuation | `NOT FOUND` — a warning string only, and its trigger field has no production writer | `OldRegimeTaxEngine.java:239-241`; `NewRegimeTaxEngine.java:168-170` |
| Notice pay | `NOT FOUND` | grep `notice` over the module returns nothing |
| Monthly vs annual handling | `PARTIAL` — all amounts are annual; `monthlyRentPaid` and `driverSalaryMonthly` are the only monthly inputs, each ×12 at the point of use | `OldRegimeTaxEngine.java:270`; `PerquisiteValuationService.java:57` |
| Mid-year joining | `NOT FOUND` — no join date on the profile | `salary/entity/SalaryProfileEntity.java` has no date field except `ltaBlockStart` (a `String`) |
| Salary revisions | `NOT FOUND` — `SalaryComponentEntity.isCurrent` exists but is never read by any engine | `salary/entity/SalaryComponentEntity.java:19-20`; grep `getIsCurrent` finds only `SalaryProfileResponse.java:105` |
| Arrears | `NOT FOUND` | — |

### Gross salary — verbatim

`engine/AbstractTaxEngine.java:138-146`:

```java
protected long computeGrossSalary(SalaryProfileEntity profile, PerquisitePolicyEntity perquisitePolicy) {
    long base = profile.getComponents() == null ? 0L :
            profile.getComponents().stream()
                    .mapToLong(SalaryComponentEntity::getAnnualAmount)
                    .sum();
    long car = computeCarPerquisiteAnnual(profile, perquisitePolicy);
    long driver = computeDriverPerquisiteAnnual(profile, perquisitePolicy);
    return base + car + driver;
}
```

### Component lookup — verbatim

`engine/AbstractTaxEngine.java:226-242`:

```java
protected long getComponentAmount(SalaryProfileEntity profile, String code) {
    if (profile.getComponents() == null) {
        return 0L;
    }
    return profile.getComponents().stream()
            .filter(c -> code.equals(c.getAllowanceCode()))
            .mapToLong(c -> c.getAnnualAmount() != null ? c.getAnnualAmount() : 0L)
            .sum();
}

protected boolean hasComponent(SalaryProfileEntity profile, String code) {
    if (profile.getComponents() == null) {
        return false;
    }
    return profile.getComponents().stream()
            .anyMatch(c -> code.equals(c.getAllowanceCode()));
}
```

`hasComponent` has **no caller**.

### Component codes the engines actually recognise

| Code | Where read |
|---|---|
| `BASIC` | `AbstractTaxEngine` (via subclasses), `OldRegimeTaxEngine.java:88,268,336`, `NewRegimeTaxEngine.java:72,173,204`, `RestructuringEngine.java:205` |
| `DA` | `OldRegimeTaxEngine.java:337`, `NewRegimeTaxEngine.java:205`, `RestructuringEngine.java:206` |
| `HRA` | `OldRegimeTaxEngine.java:263`, `RestructuringEngine.java:212-213` |
| `LTA` | `OldRegimeTaxEngine.java:287` |
| `EMPLOYER_PF` | `OldRegimeTaxEngine.java:87,89`, `NewRegimeTaxEngine.java:71,73` |
| `NPS_EMPLOYER` | `OldRegimeTaxEngine.java:101,105,338`, `NewRegimeTaxEngine.java:85,89,172,174,206` |
| `MEAL_VOUCHER` | `OldRegimeTaxEngine.java:117`, `NewRegimeTaxEngine.java:101,105` |
| `GIFT_VOUCHER` | `OldRegimeTaxEngine.java:125`, `NewRegimeTaxEngine.java:131,135` |
| `BOOKS_PERIODICALS` | `OldRegimeTaxEngine.java:141` |
| `TELEPHONE` | `OldRegimeTaxEngine.java:148` |
| `UNIFORM` | `OldRegimeTaxEngine.java:155` |
| `CHILDREN_EDUCATION` | `OldRegimeTaxEngine.java:302` (limit lookup, not a component) |
| `HOSTEL_ALLOWANCE` | `OldRegimeTaxEngine.java:320` (limit lookup) |
| `PROFESSIONAL_TAX` | `OldRegimeTaxEngine.java:223`, `AbstractTaxEngine.java:101` |
| `EMPLOYEE_PF` | `AbstractTaxEngine.java:100` (net take-home only) |
| `SPECIAL_ALLOWANCE` | `RestructuringEngine.java:189`, `engine/FbpOptimizer.java:21` |

There is **no enum or constant class** for these — every one is a string literal at the use site.

---

## 2. Exemptions and deductions

| Item | Status | Evidence |
|---|---|---|
| Standard deduction — new regime | `IMPLEMENTED` | `NewRegimeTaxEngine.java:56-59` |
| Standard deduction — old regime | `IMPLEMENTED` | `OldRegimeTaxEngine.java:64-67` |
| Professional tax | `PARTIAL` — deducted in old regime only; subtracted from net take-home in **both** | `OldRegimeTaxEngine.java:222-227`; `AbstractTaxEngine.java:101-102` |
| HRA exemption, metro vs non-metro | `IMPLEMENTED` | `engine/HraExemptionCalculator.java:41-78` |
| LTA | `PARTIAL` — full amount exempted, no block-of-four-years logic, no trip count, despite three profile fields for it | `OldRegimeTaxEngine.java:286-293` vs `SalaryProfileEntity.java:102-109` |
| 80C | `IMPLEMENTED` | `OldRegimeTaxEngine.java:170-181` |
| 80CCD(1B) | `IMPLEMENTED` | `OldRegimeTaxEngine.java:196-207` |
| 80CCD(2) | `PARTIAL` — see §8 | `OldRegimeTaxEngine.java:100-114`; `NewRegimeTaxEngine.java:84-98` |
| 80D | `PARTIAL` — a single flat cap; no self/parent split, no senior-citizen tier, no preventive-checkup sub-limit | `OldRegimeTaxEngine.java:183-194` |
| 80E | `NOT FOUND` in code — catalogue entry `SECTION_80E` exists with no profile field and no engine reader | `allowance-catalogue-2025-26.json` |
| 80G | `NOT FOUND` in code — catalogue entry `SECTION_80G` only | `allowance-catalogue-2025-26.json` |
| 80TTA / 80TTB | `NOT FOUND` — no catalogue entry, no code | grep returns nothing |
| Home loan interest 24(b) | `PARTIAL` — one flat cap, no self-occupied vs let-out distinction | `OldRegimeTaxEngine.java:209-220` |
| Other house property income or loss | `NOT FOUND` | — |
| Children education allowance | `IMPLEMENTED` — derives the child cap from the stored limits | `OldRegimeTaxEngine.java:295-311` |
| Hostel allowance | `IMPLEMENTED` — same shape | `OldRegimeTaxEngine.java:313-329` |
| Transport allowance / disability | `NOT FOUND` in code — `isPhysicallyDisabled` is stored and never read by an engine | `SalaryProfileEntity.java:99-100`; catalogue `TRANSPORT_ALLOWANCE` has no reader |

### Standard deduction — verbatim

`engine/OldRegimeTaxEngine.java:64-67`:

```java
@Override
protected long getStandardDeduction(long gross, TaxSlabPolicyEntity slabPolicy) {
    return Math.min(slabPolicy.getStandardDeduction(), gross);
}
```

`engine/NewRegimeTaxEngine.java:56-59`:

```java
@Override
protected long getStandardDeduction(long gross, TaxSlabPolicyEntity slabPolicy) {
    return Math.min(getOrDefault(slabPolicy.getStandardDeduction(), 75_000L), gross);
}
```

The old-regime version unboxes `getStandardDeduction()` with no null guard; the new-regime version
defaults to `75_000L`.

### HRA exemption — verbatim

`engine/HraExemptionCalculator.java:41-78`:

```java
public HraResult computeHraExemption(
        long actualHraReceivedAnnual,
        long basicSalaryAnnual,
        long rentPaidAnnual,
        boolean isMetroCity,
        boolean isPayingRent
) {
    List<String> warnings = new ArrayList<>();

    // Condition 1: Not paying rent → no exemption
    if (!isPayingRent || rentPaidAnnual == 0) {
        warnings.add("HRA exemption is Rs. 0 because the employee is not paying rent or rent amount is zero.");
        return new HraResult(0L, warnings);
    }

    // Condition 2: Rent > ₹1,00,000 with no landlord PAN (Phase 1.5 enhancement)
    if (rentPaidAnnual > 100_000) {
        warnings.add("Annual rent exceeds Rs. 1,00,000. Ensure landlord PAN is collected and submitted to employer for HRA claim.");
    }

    // Metro: 50% of basic salary, Non-metro: 40%
    long metroHra = Math.round(basicSalaryAnnual * 50.0 / 100.0);
    long nonMetroHra = Math.round(basicSalaryAnnual * 40.0 / 100.0);
    long selectedPercentageHra = isMetroCity ? metroHra : nonMetroHra;

    // Actual rent condition: rent paid minus 10% of basic
    long rentCondition = Math.max(0L, rentPaidAnnual - Math.round(basicSalaryAnnual * 10.0 / 100.0));

    long exemptAmount = Math.min(
            Math.min(
                    Math.min(actualHraReceivedAnnual, selectedPercentageHra),
                    rentCondition
            ),
            actualHraReceivedAnnual
    );

    return new HraResult(exemptAmount, warnings);
}
```

The 50%/40% split and the 10% figure are **hardcoded literals in Java**, not policy data. The
`basicSalaryAnnual` argument receives `getComponentAmount(profile, "BASIC")` only — DA is not added
(`OldRegimeTaxEngine.java:268,274-276`). `actualHraReceivedAnnual` appears twice in the `min` chain.

The caller (`OldRegimeTaxEngine.java:262-284`):

```java
private long computeHraExemption(SalaryProfileEntity profile, List<String> applied) {
    long actualHraAnnual = getComponentAmount(profile, "HRA");
    if (actualHraAnnual == 0) {
        return 0L;
    }

    long basicSalaryAnnual = getComponentAmount(profile, "BASIC");
    long monthlyRent = getOrDefault(profile.getMonthlyRentPaid(), 0L);
    long rentPaidAnnual = monthlyRent * 12;
    boolean isMetro = Boolean.TRUE.equals(profile.getIsMetroCity());
    boolean isPayingRent = Boolean.TRUE.equals(profile.getIsPayingRent());

    HraExemptionCalculator.HraResult hraResult = hraExemptionCalculator.computeHraExemption(
            actualHraAnnual, basicSalaryAnnual, rentPaidAnnual, isMetro, isPayingRent
    );

    applied.add("HRA Exemption: Rs. " + hraResult.getExemptionAmount());
    if (hraResult.getWarnings() != null) {
        // warnings are handled in computeWarnings
    }

    return hraResult.getExemptionAmount();
}
```

`hraResult.getWarnings()` is fetched and discarded inside an empty `if` block (`:279-281`); the
warnings the calculator produces never reach `TaxResult.warnings`.

Two further HRA methods exist with **no caller anywhere** —
`HraExemptionCalculator.calculate` (`:84-101`) and `calculateFull` (`:106-121`). Their bodies are in
`05-core-code.md` §4.

### LTA — verbatim

`engine/OldRegimeTaxEngine.java:286-293`:

```java
private long computeLtaExemption(SalaryProfileEntity profile, List<String> applied) {
    long actualLta = getComponentAmount(profile, "LTA");
    if (actualLta == 0) {
        return 0L;
    }
    applied.add("LTA Exemption: Rs. " + actualLta);
    return actualLta;
}
```

`ltaBlockStart`, `ltaTripsClaimedInBlock` and `ltaCarryForwardAvailable` are stored on the profile
(`SalaryProfileEntity.java:102-109`), accepted on the request
(`SalaryProfileRequest.java:38-40`), persisted (`SalaryProfileService.java:73-75,128-130`) — and
read by nothing.

### Chapter VI-A — verbatim (old regime)

`engine/OldRegimeTaxEngine.java:165-230` in full:

```java
@Override
protected DeductionResult computeDeductions(SalaryProfileEntity profile, List<String> applied) {
    long total = 0L;
    String taxYear = profile.getTaxYear();
    EmployerType employer = profile.getEmployerType();

    // ---- Section 80C ----
    long investment80c = getOrDefault(profile.getInvestment80c(), 0L);
    Long section80cLimit = resolveAnnualLimit("80C", taxYear, getRegime(), employer);
    if (section80cLimit == null) {
        throw new IllegalArgumentException("No active policy for 80C in taxYear=" + taxYear);
    }
    long effective80cLimit = section80cLimit;
    long section80c = Math.min(investment80c, effective80cLimit);
    if (section80c > 0) {
        total += section80c;
        applied.add("Section 80C: Rs. " + section80c);
    }

    // ---- Section 80D ----
    long investment80d = getOrDefault(profile.getInvestment80d(), 0L);
    Long section80dLimit = resolveAnnualLimit("80D", taxYear, getRegime(), employer);
    if (section80dLimit == null) {
        throw new IllegalArgumentException("No active policy for 80D in taxYear=" + taxYear);
    }
    long effective80dLimit = section80dLimit;
    long section80d = Math.min(investment80d, effective80dLimit);
    if (section80d > 0) {
        total += section80d;
        applied.add("Section 80D: Rs. " + section80d);
    }

    // ---- Section 80CCD(1B) ----
    long npsSelf80ccd1b = getOrDefault(profile.getNpsSelf80ccd1b(), 0L);
    Long section80ccd1bLimit = resolveAnnualLimit("80CCD_1B", taxYear, getRegime(), employer);
    if (section80ccd1bLimit == null) {
        throw new IllegalArgumentException("No active policy for 80CCD_1B in taxYear=" + taxYear);
    }
    long effective80ccd1bLimit = section80ccd1bLimit;
    long section80ccd1b = Math.min(npsSelf80ccd1b, effective80ccd1bLimit);
    if (section80ccd1b > 0) {
        total += section80ccd1b;
        applied.add("Section 80CCD(1B) NPS Self: Rs. " + section80ccd1b);
    }

    // ---- Section 24(b) Home Loan Interest ----
    long homeLoanInterest = getOrDefault(profile.getHomeLoanInterest(), 0L);
    Long section24bLimit = resolveAnnualLimit("24B", taxYear, getRegime(), employer);
    if (section24bLimit == null) {
        throw new IllegalArgumentException("No active policy for 24B in taxYear=" + taxYear);
    }
    long effective24bLimit = section24bLimit;
    long section24b = Math.min(homeLoanInterest, effective24bLimit);
    if (section24b > 0) {
        total += section24b;
        applied.add("Section 24(b) Home Loan Interest: Rs. " + section24b);
    }

    // ---- Professional Tax ----
    long professionalTax = getComponentAmount(profile, "PROFESSIONAL_TAX");
    if (professionalTax > 0) {
        total += professionalTax;
        applied.add("Professional Tax: Rs. " + professionalTax);
    }

    return new DeductionResult(total);
}
```

New regime (`engine/NewRegimeTaxEngine.java:153-157`):

```java
@Override
protected DeductionResult computeDeductions(SalaryProfileEntity profile, List<String> applied) {
    // New regime has NO additional Chapter VI-A deductions (80C, 80D, etc.)
    return new DeductionResult(0L);
}
```

### Children education and hostel — verbatim

`engine/OldRegimeTaxEngine.java:295-329`:

```java
private long computeChildrenEducationExemption(SalaryProfileEntity profile, List<String> applied) {
    int numChildren = intValueOrDefault(profile.getNumberOfChildren(), 0);
    if (numChildren == 0) {
        return 0L;
    }
    String taxYear = profile.getTaxYear();
    AllowanceLimitEntity eduLimit = resolutionService
            .resolve("CHILDREN_EDUCATION", taxYear, getRegime(), profile.getEmployerType())
            .getLimit();
    long edMonthly = getOrDefault(eduLimit.getMonthlyLimitFixed(), 0L);
    int maxChildren = getOrDefault(eduLimit.getAnnualLimitFixed(), 0L) > 0
            ? (int)(eduLimit.getAnnualLimitFixed() / edMonthly / 12) : 2;
    int cappedChildren = Math.min(numChildren, maxChildren);
    long exemptAmount = cappedChildren * edMonthly * 12;
    applied.add("Children Education Exemption: Rs. " + exemptAmount + " (" + cappedChildren + " child(ren))");
    return exemptAmount;
}

private long computeHostelExemption(SalaryProfileEntity profile, List<String> applied) {
    int numChildren = intValueOrDefault(profile.getNumberOfChildren(), 0);
    if (numChildren == 0) {
        return 0L;
    }
    String taxYear = profile.getTaxYear();
    AllowanceLimitEntity hostelLimit = resolutionService
            .resolve("HOSTEL_ALLOWANCE", taxYear, getRegime(), profile.getEmployerType())
            .getLimit();
    long hostelMonthly = getOrDefault(hostelLimit.getMonthlyLimitFixed(), 0L);
    int maxChildren = getOrDefault(hostelLimit.getAnnualLimitFixed(), 0L) > 0
            ? (int)(hostelLimit.getAnnualLimitFixed() / hostelMonthly / 12) : 2;
    int cappedChildren = Math.min(numChildren, maxChildren);
    long exemptAmount = cappedChildren * hostelMonthly * 12;
    applied.add("Hostel Expenditure Exemption: Rs. " + exemptAmount + " (" + cappedChildren + " child(ren))");
    return exemptAmount;
}
```

Both are driven **only by `numberOfChildren`** — no `CHILDREN_EDUCATION` or `HOSTEL_ALLOWANCE`
salary component is required, and the exemption is granted at the full monthly cap × 12 × capped
children regardless of what the employer actually pays. With the seeded values
(`allowance-limits-2025-26.json:6-7` — monthly 3000 / annual 72000, and monthly 9000 /
annual 216000), `maxChildren` evaluates to `72000/3000/12 = 2` and `216000/9000/12 = 2`.

`edMonthly` is the divisor at `:306` and is `0` whenever `monthlyLimitFixed` is null while
`annualLimitFixed` is positive — an `ArithmeticException: / by zero`. Same shape at `:324`.

### Full exemptions (no cap) in the old regime

`engine/OldRegimeTaxEngine.java:140-159`:

```java
// ---- Books & Periodicals ----
long booksAmount = getComponentAmount(profile, "BOOKS_PERIODICALS");
if (booksAmount > 0) {
    total += booksAmount;
    applied.add("Books & Periodicals: Rs. " + booksAmount);
}

// ---- Telephone/Mobile ----
long telephoneAmount = getComponentAmount(profile, "TELEPHONE");
if (telephoneAmount > 0) {
    total += telephoneAmount;
    applied.add("Telephone/Mobile: Rs. " + telephoneAmount);
}

// ---- Uniform Allowance ----
long uniformAmount = getComponentAmount(profile, "UNIFORM");
if (uniformAmount > 0) {
    total += uniformAmount;
    applied.add("Uniform Allowance: Rs. " + uniformAmount);
}
```

---

## 3. Tax computation

| Item | Status | Where the data lives | Evidence |
|---|---|---|---|
| Old vs new slab tables | `IMPLEMENTED` | **DB** (`tax_slab_policies`), seeded from JSON at startup | `policy/entity/TaxSlabPolicyEntity.java:51-52`; `data/tax-policies/slab-policy-2025-26-*.json` |
| Regime comparison logic | `IMPLEMENTED` — two independent implementations | code | `service/TaxComputationService.java:65-66`; `recommendation/RegimeAdvisor.java:22-27` |
| Section 87A rebate | `IMPLEMENTED` | DB (`rebate87a_limit`, `rebate87a_amount`) | `AbstractTaxEngine.java:166-173` |
| Marginal relief | `PARTIAL` — an inline implementation in `AbstractTaxEngine`; the dedicated `MarginalReliefCalculator` is **not wired in** | code | `AbstractTaxEngine.java:175-182`; `engine/MarginalReliefCalculator.java` has no production caller |
| Surcharge | `IMPLEMENTED` | DB (`surcharge_slabs`) | `AbstractTaxEngine.java:184-199` |
| Cess | `IMPLEMENTED` | DB (`cess_percentage`), defaulting to `4.0` | `AbstractTaxEngine.java:92,201-203` |
| Rounding | `IMPLEMENTED` — `Math.round` per slab, per surcharge, per cess | code | `AbstractTaxEngine.java:92,159,195` |
| Relief under 89(1) for arrears | `NOT FOUND` | — | grep `89` / `arrear` returns nothing |

### The 13-step template — verbatim

`engine/AbstractTaxEngine.java:39-111` — the whole `compute` method is reproduced in
`05-core-code.md` §1. The arithmetic core:

```java
// ---- Step 5: Taxable Income ----
long totalDeductionsAll = standardDeduction + deductionResult.total;
result.setTotalDeductions(totalDeductionsAll);
long taxableIncome = gross - standardDeduction - exemptionResult.total - deductionResult.total;
result.setTaxableIncome(Math.max(0, taxableIncome));
```

```java
// ---- Step 10: Cess ----
long cess = Math.round((taxAfterRebate + surcharge) * getCessRate(slabPolicy) / 100.0);
result.setCess(cess);

// ---- Step 11: Total Tax ----
long totalTax = taxAfterRebate + surcharge + cess;
result.setTotalTax(totalTax);

// ---- Step 12: Net Take Home ----
long employeePf = getComponentAmount(profile, "EMPLOYEE_PF");
long professionalTax = getComponentAmount(profile, "PROFESSIONAL_TAX");
result.setNetTakeHome(gross - employeePf - professionalTax - totalTax);
```

`gross` includes the car and driver perquisite values (`:143-145`), which are notional and never
received in cash, so `netTakeHome` includes them.

### Progressive slab tax — verbatim

`engine/AbstractTaxEngine.java:148-164`:

```java
protected long computeProgressiveTax(long taxableIncome, TaxSlabPolicyEntity slabPolicy) {
    if (taxableIncome <= 0 || slabPolicy.getSlabs() == null) {
        return 0L;
    }
    long tax = 0L;
    for (TaxSlabPolicyEntity.TaxSlab slab : slabPolicy.getSlabs()) {
        long from = getOrDefault(slab.getFromAmount(), 0L);
        long to = slab.getToAmount() != null ? slab.getToAmount() : Long.MAX_VALUE;
        if (taxableIncome > from) {
            long taxableInSlab = Math.min(taxableIncome, to) - from;
            if (taxableInSlab > 0) {
                tax += Math.round(taxableInSlab * slab.getRatePercent() / 100.0);
            }
        }
    }
    return tax;
}
```

The seeded slabs start each band at `previous_to + 1` (e.g. `400001`), so `taxableInSlab` is one
rupee short of the band width — ₹400,001..₹800,000 yields `800000 - 400001 = 399999`. Rounding is
applied per slab and the results summed.

### Rebate 87A — verbatim

`engine/AbstractTaxEngine.java:166-173`:

```java
protected long computeRebate87A(long taxableIncome, long basicTax, TaxSlabPolicyEntity slabPolicy) {
    long rebateLimit = getOrDefault(slabPolicy.getRebate87aLimit(), 0L);
    long rebateAmount = getOrDefault(slabPolicy.getRebate87aAmount(), 0L);
    if (taxableIncome <= rebateLimit && basicTax > 0) {
        return Math.min(basicTax, rebateAmount);
    }
    return 0L;
}
```

### Marginal relief — verbatim (the one that runs)

`engine/AbstractTaxEngine.java:175-182`:

```java
protected long computeMarginalRelief(long taxableIncome, long taxAfterRebate, TaxSlabPolicyEntity slabPolicy) {
    long rebateLimit = getOrDefault(slabPolicy.getRebate87aLimit(), 0L);
    long excess = taxableIncome - rebateLimit;
    if (excess > 0 && taxAfterRebate > excess) {
        return taxAfterRebate - excess;
    }
    return 0L;
}
```

This runs for **both** regimes, keyed off `rebate87aLimit` — `12,00,000` for the new regime and
`5,00,000` for the old (`slab-policy-2025-26-new.json:7`, `slab-policy-2025-26-old.json:7`). No
surcharge-threshold marginal relief exists anywhere, despite `marginalReliefApplicable` being
stored `true` on every surcharge slab.

The dedicated `MarginalReliefCalculator` — three public methods, seven tests — is **never
constructed by any production class**. Its body is in `05-core-code.md` §5.

### Surcharge — verbatim

`engine/AbstractTaxEngine.java:184-203`:

```java
protected long computeSurcharge(long taxableIncome, long taxAfterRelief, TaxSlabPolicyEntity slabPolicy) {
    if (slabPolicy.getSurchargeSlabs() == null) {
        return 0L;
    }
    for (TaxSlabPolicyEntity.SurchargeSlab slab : slabPolicy.getSurchargeSlabs()) {
        if (!matchesRegime(slab)) {
            continue;
        }
        long from = getOrDefault(slab.getFromAmount(), 0L);
        long to = slab.getToAmount() != null ? slab.getToAmount() : Long.MAX_VALUE;
        if (taxableIncome >= from && taxableIncome <= to) {
            return Math.round(taxAfterRelief * slab.getRatePercent() / 100.0);
        }
    }
    return 0L;
}

protected double getCessRate(TaxSlabPolicyEntity slabPolicy) {
    return slabPolicy.getCessPercentage() != null ? slabPolicy.getCessPercentage() : 4.0;
}
```

Regime matching (`NewRegimeTaxEngine.java:194-197`, `OldRegimeTaxEngine.java:255-258`):

```java
@Override
protected boolean matchesRegime(TaxSlabPolicyEntity.SurchargeSlab slab) {
    return slab.getRegimeType() == RegimeType.NEW_REGIME;   // OLD_REGIME in OldRegimeTaxEngine
}
```

The seeded surcharge bands begin at `fromAmount: 50000000` — five crore, not fifty lakh — in both
files (`slab-policy-2025-26-new.json:20`, `slab-policy-2025-26-old.json:17`). Both engine Javadocs
state "Surcharge from ₹50L onwards" (`NewRegimeTaxEngine.java:29`, `OldRegimeTaxEngine.java:33`).
Two tests assert `0L` surcharge with comments acknowledging the gap
(`NewRegimeTaxEngineTest.java:279`, `OldRegimeTaxEngineTest.java:196`).

### Regime comparison — verbatim

`service/TaxComputationService.java:64-69`:

```java
// 7. Determine recommended regime
RegimeType recommendedRegime = newResult.getTotalTax() <= oldResult.getTotalTax()
        ? RegimeType.NEW_REGIME : RegimeType.OLD_REGIME;

// 8. Compute saving vs current regime
long annualSaving = Math.abs(oldResult.getTotalTax() - newResult.getTotalTax());
```

`recommendation/RegimeAdvisor.java:22-27` — the second, independent implementation:

```java
long newTax = newRegimeResult.getTotalTax();
long oldTax = oldRegimeResult.getTotalTax();
long savingIfNew = oldTax - newTax;

RegimeType recommended = savingIfNew >= 0 ? RegimeType.NEW_REGIME : RegimeType.OLD_REGIME;
long absoluteSaving = Math.abs(savingIfNew);
```

Note that despite the field name `annualSavingVsCurrent`
(`TaxComputationEntity.java:44-45`), neither computation consults `profile.getRegimeType()` — the
figure is the gap between the two regimes, not a saving against the user's current one.

`TaxEngineFactory.getEngine` throws on `NOT_DECIDED` (`engine/TaxEngineFactory.java:16-22`), but
`TaxComputationService` never calls it — it calls `getNewRegimeEngine()` and `getOldRegimeEngine()`
directly (`:55,60`), so a `NOT_DECIDED` profile computes without complaint.

---

## 4. Employer / TDS side

| Item | Status | Evidence |
|---|---|---|
| TDS under Section 192 | `NOT FOUND` | grep `192`, `tds` over `backend/src` `*.java` returns **zero hits** |
| Monthly TDS projection | `NOT FOUND` | — |
| TDS re-computation | `NOT FOUND` | — |
| Declarations vs proofs (Form 12BB) | `NOT FOUND` | grep `12BB` returns zero hits |
| Previous-employer income (Form 12B / multiple Form 16s) | `NOT FOUND` | grep `12B` returns zero hits |
| Investment proofs | `PARTIAL` — descriptive only. `AllowanceCatalogueEntity.documentsRequired` / `documentsToKeep` / `requiresBills` / `requiresRentReceipt` carry proof guidance surfaced in recommendations; nothing tracks or validates an actual proof | `policy/entity/AllowanceCatalogueEntity.java:48-52,93-97`; `recommendation/AllowanceRecommendation.java:30-31` |

The only TDS references in the whole repository are free-text strings inside
`allowance-catalogue-2025-26.json` — e.g. `"Automatically considered by employer in TDS"` (line
1260), `"Declare investments to employer for TDS adjustment, or claim directly in ITR"` (1330),
`"Cannot be claimed through employer TDS; must claim in ITR"` (1409, 1448, 1487). These are
`howToAvailViaHr` values displayed to the user, not logic.

---

## 5. Form 16

| Item | Status | Evidence |
|---|---|---|
| Part A handling | `NOT FOUND` | — |
| Part B handling | `NOT FOUND` | — |
| Field mapping | `NOT FOUND` | — |
| Validation | `NOT FOUND` | — |
| Reconciliation with computed tax | `NOT FOUND` | — |

`grep -rn -i "form16\|form 16\|form-16"` over `backend/src` `*.java` returns **zero hits**. The
seven matches in the repository are all free-text strings inside
`allowance-catalogue-2025-26.json` (lines 125, 165, 192, 1265, 1300, 1339, 1379) — entries in
`documentsToKeep` arrays such as `"Form 16"`, and a `commonMistakes` entry
`"Not reconciling with Form 16"`.

There is no entity, collection, endpoint, DTO, parser or template touching Form 16. The nearest
thing to an intake path is `ParsedCtcDto` (`parsing/ParsedCtcDto.java`), which describes a parsed
**CTC/offer letter** rather than a Form 16 and has no producer or consumer.

---

## 6. Other income heads

| Item | Status | Evidence |
|---|---|---|
| Interest income | `NOT FOUND` | No field, no code |
| Dividends | `NOT FOUND` | — |
| House property income or loss | `NOT FOUND` — only `homeLoanInterest` as a 24(b) deduction input | `SalaryProfileEntity.java:81-82` |
| Income from other sources | `NOT FOUND` | — |
| Business / professional income | `NOT FOUND` | — |
| Capital gains | `NOT FOUND` as computation. Two inert touchpoints: `TaxComputationEntity.capitalGainsResult` (`Object`, **never written** — grep `setCapitalGainsResult` returns nothing) and `ItrFormAdvisor.recommendItrForm(profile, hasCapitalGains)`, which has no production caller | `salary/entity/TaxComputationEntity.java:47-48`; `engine/ItrFormAdvisor.java:18-27` |

Taxable income is therefore **salary only**. `TaxResult.taxableIncome` is
`gross − standardDeduction − exemptions − deductions` and admits no other head
(`AbstractTaxEngine.java:69`).

### The capital-gains touchpoint — verbatim

`engine/ItrFormAdvisor.java:11,18-27`:

```java
private static final long SALARY_THRESHOLD_FOR_ITR2 = 5000000L; // ₹50 Lakhs

public String recommendItrForm(SalaryProfileEntity profile, boolean hasCapitalGains) {
    long grossSalary = getGrossSalary(profile);

    if (hasCapitalGains || grossSalary > SALARY_THRESHOLD_FOR_ITR2) {
        log.debug("ITR-2 recommended: hasCapitalGains={} grossSalary={}", hasCapitalGains, grossSalary);
        return "ITR-2";
    }

    return "ITR-1";
}
```

This is the module's only connection to STCG/LTCG, and it is unreachable: `grep -rn "ItrFormAdvisor"`
across `backend/src` finds only the file itself. There is no dependency on the `portfolio` module,
which is where realised gains live (`portfolio` is not in this module's `allowedDependencies`).

---

## 7. Tax year handling

| Item | Status | Evidence |
|---|---|---|
| FY/AY representation | `PARTIAL` — `taxYear` is a free-form `String` on every entity | `SalaryProfileEntity.java:42-43`, `TaxSlabPolicyEntity.java:27-28`, etc. |
| FY↔AY mapping | `NOT FOUND` in use — `TaxYearRegistryEntity` models it and is never read or written | `policy/entity/TaxYearRegistryEntity.java:24-34` |
| Rules versioned by year | `IMPLEMENTED` — every policy lookup is keyed on `taxYear` + `status = ACTIVE` | `policy/service/PolicyService.java:28,34,40,44` |
| Rules versioned by effective date | `PARTIAL` — all three limit queries order by `effectiveDateDesc`, but `effectiveDate` is null on every seeded row | `policy/repository/AllowanceLimitRepository.java:13,16,19`; `allowance-limits-2025-26.json` has no `effectiveDate` key |
| Supersession | `IMPLEMENTED` for slab and perquisite policies | `PolicyService.java:48-56,58-66` |
| Only year supported | `2025-26` — hardcoded in four places in the seeder and two in the engines | `PolicySeederService.java:54,73,91,118`; `NewRegimeTaxEngine.java:52-54`, `OldRegimeTaxEngine.java:60-62` |
| `getSupportedTaxYear()` enforcement | **Never called** — grep finds only the interface declaration and the two overrides | `engine/TaxEngine.java:15` |
| Income-tax Act 1961 vs 2025 section numbers | **Both appear, inconsistently** — see below | |

Act references found:

| Text | Location |
|---|---|
| `"Finance Act 2025 / Income Tax Act 1961 as amended; Budget 2025"` | `slab-policy-2025-26-new.json:5`, `slab-policy-2025-26-old.json:5` |
| `"Income Tax Act 1961, Rule 3; CBDT Circulars"` | `perquisite-policy-2025-26.json:4` |
| `"Section 10(13A)"`, `"Rule 2A"`, `"Section 80C"`, `"Section 24"`, `"Section 17(2)"`, … (1961 numbering) | `allowance-catalogue-2025-26.json`, `itSection` / `itActReference` on all 39 items |
| `"under the Income Tax Act 2025 and IT Rules 2026"` | `recommendation/HrLetterContentBuilder.java:16` — **the only 2025-Act reference in the module**, in a class with no production caller |

`TaxYearRegistryEntity` carries `governingAct` and `governingRules` fields (`:42-46`) that would
resolve this, and is unused.

Supersession — verbatim (`policy/service/PolicyService.java:47-66`):

```java
@Transactional
public TaxSlabPolicyEntity createSlabPolicy(TaxSlabPolicyEntity policy) {
    List<TaxSlabPolicyEntity> existing = slabRepo.findByTaxYearAndRegimeType(policy.getTaxYear(), policy.getRegimeType());
    existing.stream().filter(p -> p.getStatus() == EntityStatus.ACTIVE).forEach(p -> {
        p.setStatus(EntityStatus.SUPERSEDED);
        slabRepo.save(p);
    });
    policy.setStatus(EntityStatus.ACTIVE);
    return slabRepo.save(policy);
}

@Transactional
public PerquisitePolicyEntity createPerquisitePolicy(PerquisitePolicyEntity policy) {
    perquisiteRepo.findByTaxYearAndStatus(policy.getTaxYear(), EntityStatus.ACTIVE).ifPresent(p -> {
        p.setStatus(EntityStatus.SUPERSEDED);
        perquisiteRepo.save(p);
    });
    policy.setStatus(EntityStatus.ACTIVE);
    return perquisiteRepo.save(policy);
}
```

There is no equivalent supersession path for `allowance_limits` — no create endpoint exists for that
collection at all, so after the initial seed the numeric limits can only be changed directly in the
database.

---

## 8. Formula evaluation — the policy-data vocabulary

`engine/FormulaEvaluator.java:38-60` (full body in `05-core-code.md` §6):

```java
public long evaluate(String formula, Map<String, Object> variables) {
    if (formula == null || formula.isBlank()) {
        return 0L;
    }
    SafeExpressions.reject(formula);
    SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
    variables.forEach(context::setVariable);
    return evaluate(formula, context);
}
```

Evaluation is SpEL. SpEL reads a context variable as `#name`
(`FormulaEvaluatorSandboxTest.java:42,48` prove this — `"#basic * 10 / 100"` and
`"#rentPaid - (#basic * 10 / 100)"` both evaluate), and SpEL has no built-in `min` function.

The formulas actually seeded use neither convention. `allowance-limits-2025-26.json:10`:

```json
{ "allowanceCode": "NPS_EMPLOYER", "taxYear": "2025-26", "regimeType": "NEW_REGIME", "employerType": "GOVT", "ratePercent": 14.0, "limitFormula": "min(0.14 * (basic + da), actual_nps)", "status": "ACTIVE" }
```

and `allowance-limits-2025-26.json:2`:

```json
{ "allowanceCode": "80C", ..., "limitFormula": "min(actual_investment, 150000)", "status": "ACTIVE" }
```

The variables supplied at the call site are `basic`, `da`, `actual_nps`
(`OldRegimeTaxEngine.java:335-338`, `NewRegimeTaxEngine.java:203-206`) — bare names, matching the
formula text but not SpEL's `#` syntax.

Both engines wrap the call in a catch-all (`OldRegimeTaxEngine.java:339-344`,
`NewRegimeTaxEngine.java:207-212`):

```java
try {
    return formulaEvaluator.evaluate(formula, vars);
} catch (Exception e) {
    log.warn("Failed to evaluate formula '{}': {}", formula, e.getMessage());
    return 0L;
}
```

and the enclosing NPS block catches again (`OldRegimeTaxEngine.java:111-113`,
`NewRegimeTaxEngine.java:95-97`). The consequence is recorded in
`04-constants-tests-history.md` §5 Observation O-1.

Many `limitFormula` values in `allowance-catalogue-2025-26.json` are prose rather than expressions —
e.g. `"actual_expense (2 trips in a block of 4 years, economy class)"` (LTA),
`"Govt employee: fully exempt. Non-govt: 1/3 exempt if gratuity received, else 1/2 exempt"`
(COMMUTED_PENSION), `"As per rules: <=1600cc no driver Rs 5000/mo, ..."` (CAR_PERQUISITE). Nothing
reads the catalogue's `limitFormula`, so these are documentation strings only.

### Allowance limit resolution — verbatim

`policy/service/AllowanceResolutionService.java:26-47`:

```java
public ResolvedAllowance resolve(String allowanceCode, String taxYear,
                                  RegimeType regime, EmployerType employer) {
    AllowanceCatalogueEntity catalogue = null;
    try {
        catalogue = catalogueRepo.findByCodeAndStatus(allowanceCode, EntityStatus.ACTIVE)
                .orElse(null);
    } catch (Exception e) {
        log.warn("Could not look up catalogue for {}: {}", allowanceCode, e.getMessage());
    }

    AllowanceLimitEntity limit = limitRepo
            .findTopByAllowanceCodeAndTaxYearAndRegimeTypeAndEmployerTypeAndStatusOrderByEffectiveDateDesc(
                    allowanceCode, taxYear, regime, employer, EntityStatus.ACTIVE)
            .or(() -> limitRepo.findTopByAllowanceCodeAndTaxYearAndRegimeTypeAndStatusOrderByEffectiveDateDesc(
                    allowanceCode, taxYear, regime, EntityStatus.ACTIVE))
            .or(() -> limitRepo.findTopByAllowanceCodeAndTaxYearAndStatusOrderByEffectiveDateDesc(
                    allowanceCode, taxYear, EntityStatus.ACTIVE))
            .orElseThrow(() -> new IllegalArgumentException(
                    "No active limit for " + allowanceCode + " regime=" + regime + " employer=" + employer + " year=" + taxYear));

    return ResolvedAllowance.builder().metadata(catalogue).limit(limit).build();
}
```

Three-tier fallback: exact `(code, year, regime, employer)` → `(code, year, regime)` →
`(code, year)`. The catalogue lookup ignores `taxYear` entirely — it uses
`findByCodeAndStatus` (`:30`), so a code present in more than one year resolves arbitrarily.

`AbstractTaxEngine.resolveAnnualLimit` and `resolveRatePercent` (`:252-283`) swallow every exception
and return `null`; the old-regime deduction path then converts that `null` into an
`IllegalArgumentException` with a different message (`OldRegimeTaxEngine.java:173-175` etc.).

### Codes: catalogue vs limits vs engines

The three sources use **different code vocabularies** for the same concepts:

| Concept | Engine looks up | `allowance_limits` has | `allowance_catalogue` has |
|---|---|---|---|
| 80C | `"80C"` | `80C` | `SECTION_80C` |
| 80D | `"80D"` | `80D` | `SECTION_80D` |
| 80CCD(1B) | `"80CCD_1B"` | `80CCD_1B` | `NPS_SELF` |
| 24(b) | `"24B"` | `24B` | `SECTION_24` |
| Telephone | `"TELEPHONE"` (component) | — | `TELEPHONE_MOBILE` |
| Gift | `"GIFT_VOUCHER"` | — | `GIFTS` |
| Uniform | `"UNIFORM"` (component) | — | `UNIFORM_ALLOWANCE` |
| Car | — | — | `CAR_PERQUISITE` |
| HRA | `"HRA"` (component) | — | `HRA` |
| Meal | `"MEAL_VOUCHER"` | — | `MEAL_VOUCHER` |
| Employer PF | `"EMPLOYER_PF"` | `EMPLOYER_PF` | — |
| Employer NPS | `"NPS_EMPLOYER"` | `NPS_EMPLOYER` | `NPS_EMPLOYER` |
| Children education | `"CHILDREN_EDUCATION"` | `CHILDREN_EDUCATION` | `CHILDREN_EDUCATION` |
| Hostel | `"HOSTEL_ALLOWANCE"` | `HOSTEL_ALLOWANCE` | `HOSTEL_ALLOWANCE` |

`allowance_limits` holds 14 rows covering 8 distinct codes; `allowance_catalogue` holds 39 items
covering 30 distinct codes; the engines reference 16 component codes and 8 limit codes. The sets
overlap only partially. This matters for `RestructuringEngine`, which intersects them —
see §9.

---

## 9. Planning features

| Item | Status | Evidence |
|---|---|---|
| Suggestions / recommendations | `IMPLEMENTED` | `recommendation/RestructuringEngine.java:42-182` |
| Optimization / what-if | `PARTIAL` — one fixed greedy strategy over `SPECIAL_ALLOWANCE`; no user-supplied scenario | `RestructuringEngine.java:66,94-146` |
| Regime switch advice | `IMPLEMENTED` | `recommendation/RegimeAdvisor.java:17-62` |
| HR letter generation | `IMPLEMENTED` but unreachable — `HrLetterContentBuilder` has no production caller | `recommendation/HrLetterContentBuilder.java:10-41` |
| ITR form advice | `IMPLEMENTED` but unreachable | `engine/ItrFormAdvisor.java:18-27` |
| FBP optimisation helpers | `IMPLEMENTED` but unreachable — `FbpOptimizer` has no production caller | `engine/FbpOptimizer.java` |
| Investment proofs | `PARTIAL` — descriptive strings only (§4) | — |
| Advance tax | `NOT FOUND` | grep `advance tax` returns nothing |

### Restructuring eligibility filter and priority — verbatim

`recommendation/RestructuringEngine.java:69-86`:

```java
List<String> existingCodes = currentProfile.getComponents().stream()
        .map(SalaryComponentEntity::getAllowanceCode)
        .toList();

Set<String> availableCodes = resolutionService.findAvailableAllowanceCodes(taxYear, currentRegime);

List<AllowanceCatalogueEntity> eligible = catalogue.stream()
        .filter(a -> a.getRegimeType() == currentRegime)
        .filter(a -> availableCodes.contains(a.getCode()))
        .filter(a -> !existingCodes.contains(a.getCode()))
        .filter(a -> a.getStatus() == EntityStatus.ACTIVE)
        .sorted(Comparator
                .comparingInt((AllowanceCatalogueEntity a) -> getPriority(a.getCode()))
                .thenComparing((AllowanceCatalogueEntity a) ->
                                a.getAnnualLimitFixed() != null ? a.getAnnualLimitFixed() : 0L,
                        Comparator.reverseOrder())
                .thenComparing(a -> Boolean.TRUE.equals(a.getRequiresBills())))
        .toList();
```

`availableCodes` comes from `allowance_limits` (`policy/service/AllowanceResolutionService.java:53-58`):

```java
public Set<String> findAvailableAllowanceCodes(String taxYear, RegimeType regime) {
    return limitRepo.findByTaxYearAndStatus(taxYear, EntityStatus.ACTIVE).stream()
            .filter(l -> l.getRegimeType() == regime)
            .map(AllowanceLimitEntity::getAllowanceCode)
            .collect(Collectors.toSet());
}
```

So `eligible` is the **intersection** of catalogue codes and limit codes for the regime. Given the
seeded data (§8), that intersection is: for `OLD_REGIME` — `CHILDREN_EDUCATION`, `HOSTEL_ALLOWANCE`,
`NPS_EMPLOYER`; for `NEW_REGIME` — `NPS_EMPLOYER`. (`80C`/`80D`/`80CCD_1B`/`24B` exist in
`allowance_limits` but the catalogue names them `SECTION_80C` etc., so they never match;
`EMPLOYER_PF` has no catalogue entry.)

Priority (`RestructuringEngine.java:194-202`):

```java
private int getPriority(String code) {
    return switch (code) {
        case "NPS_EMPLOYER", "MEAL_VOUCHER", "CAB_FACILITY", "HEALTH_CLUB" -> 1;
        case "HRA", "LTA", "CHILDREN_EDUCATION", "HOSTEL_ALLOWANCE" -> 2;
        case "FUEL_ALLOWANCE", "DRIVER_ALLOWANCE", "TELEPHONE", "BOOKS_PERIODICALS" -> 3;
        case "GIFT_VOUCHER", "UNIFORM_ALLOWANCE" -> 4;
        default -> 5;
    };
}
```

`CAB_FACILITY`, `HEALTH_CLUB`, `FUEL_ALLOWANCE` appear nowhere else in the module.

### Suggested amount and estimated saving — verbatim

`recommendation/RestructuringEngine.java:99-136`:

```java
long suggestedAmount;
AllowanceLimitEntity limit;
try {
    limit = resolutionService.resolve(
            allowance.getCode(), taxYear, currentRegime,
            currentProfile.getEmployerType() != null ? currentProfile.getEmployerType() : EmployerType.PRIVATE
    ).getLimit();
} catch (Exception e) {
    log.warn("Could not resolve limit for allowance {} in restructure, skipping: {}",
            allowance.getCode(), e.getMessage());
    continue;
}

if (limit.getAnnualLimitFixed() != null) {
    suggestedAmount = Math.min(limit.getAnnualLimitFixed(), reduciblePool);
} else if (limit.getLimitFormula() != null) {
    Map<String, Object> vars = buildFormulaVariables(currentProfile);
    try {
        suggestedAmount = Math.min(formulaEvaluator.evaluate(limit.getLimitFormula(), vars), reduciblePool);
    } catch (Exception e) {
        log.warn("Failed to evaluate formula for {}: {}", allowance.getCode(), e.getMessage());
        continue;
    }
} else {
    suggestedAmount = reduciblePool;
}

if (suggestedAmount <= 0) {
    continue;
}

reduciblePool -= suggestedAmount;

long perquisiteValue = computePerquisiteValue(allowance, suggestedAmount, currentProfile, perquisitePolicy);
long taxFreeBenefit = suggestedAmount - perquisiteValue;

double marginalRate = getMarginalRate(originalNewResult.getTaxableIncome(), currentSlabPolicy);
long estimatedSaving = Math.round(taxFreeBenefit * marginalRate);
```

`getMarginalRate` (`:238-249`):

```java
private double getMarginalRate(long taxableIncome, TaxSlabPolicyEntity slabPolicy) {
    if (slabPolicy == null || slabPolicy.getSlabs() == null) {
        return 0.30;
    }
    for (TaxSlabPolicyEntity.TaxSlab slab : slabPolicy.getSlabs()) {
        long to = slab.getToAmount() != null ? slab.getToAmount() : Long.MAX_VALUE;
        if (taxableIncome >= slab.getFromAmount() && taxableIncome <= to) {
            return slab.getRatePercent() / 100.0;
        }
    }
    return 0.30;
}
```

`marginalRate` is always taken from `originalNewResult.getTaxableIncome()` — the **new-regime**
taxable income — even when `currentRegime` is `OLD_REGIME` (`:135`).

The formula variables built here differ from those the engines build
(`RestructuringEngine.java:204-215`):

```java
private Map<String, Object> buildFormulaVariables(SalaryProfileEntity profile) {
    long basic = getComponentAmount(profile, "BASIC");
    long da = getComponentAmount(profile, "DA");
    Map<String, Object> vars = new HashMap<>();
    vars.put("basic", basic);
    vars.put("da", da);
    vars.put("basic_plus_da", basic + da);
    vars.put("rent_paid", profile.getMonthlyRentPaid() != null ? profile.getMonthlyRentPaid() * 12 : 0L);
    vars.put("hra_received", getComponentAmount(profile, "HRA"));
    vars.put("actual_hra", getComponentAmount(profile, "HRA"));
    return vars;
}
```

`actual_nps` is **absent** here but present in the engines' vocabulary — so the seeded
`NPS_EMPLOYER` formula references a variable this call site does not supply.

### Perquisite offset during restructuring — verbatim

`recommendation/RestructuringEngine.java:227-236`:

```java
private long computePerquisiteValue(AllowanceCatalogueEntity allowance, long amount,
        SalaryProfileEntity profile, PerquisitePolicyEntity policy) {
    return switch (allowance.getCode()) {
        case "CAR_COMPANY" -> perquisiteService.computeCarPerquisiteMonthly(
                profile.getCarOwnership(), profile.getCarEngineSize(),
                Boolean.TRUE.equals(profile.getDriverProvidedByEmployer()), true, policy) * 12;
        case "DRIVER_ALLOWANCE" -> perquisiteService.computeDriverPerquisiteMonthly(true, policy) * 12;
        default -> 0L;
    };
}
```

`"CAR_COMPANY"` appears nowhere else — the catalogue's car entry is `CAR_PERQUISITE`
(`allowance-catalogue-2025-26.json`). The `amount` parameter is unused.

### Regime switch triggers — verbatim

`recommendation/RegimeAdvisor.java:29-52`:

```java
List<String> switchTriggers = new ArrayList<>();

if (recommended == RegimeType.NEW_REGIME) {
    if (profile.getInvestment80c() != null && profile.getInvestment80c() < 150000) {
        long unclaimed80c = 150000 - profile.getInvestment80c();
        long saving80c = Math.round(unclaimed80c * 0.30);
        switchTriggers.add("Maximise 80C by ₹" + unclaimed80c + " → saves ₹" + saving80c + " in old regime");
    }
    if (Boolean.TRUE.equals(profile.getIsPayingRent()) && profile.getMonthlyRentPaid() != null && profile.getMonthlyRentPaid() > 0) {
        switchTriggers.add("HRA exemption available in old regime if salary restructured");
    }
    if (profile.getHomeLoanInterest() != null && profile.getHomeLoanInterest() > 0) {
        switchTriggers.add("Home loan interest ₹" + profile.getHomeLoanInterest() + " deductible only in old regime");
    }
    if (profile.getInvestment80d() != null && profile.getInvestment80d() < 25000) {
        switchTriggers.add("Health insurance premium up to ₹25,000 under 80D — old regime only");
    }
    long breakeven = estimateBreakevenDeductions(profile, newTax, oldTax);
    long currentDeductions = currentTotalDeductions(profile);
    switchTriggers.add("Old regime needs ₹" + breakeven + " deductions to break even — you have ₹" + currentDeductions);
} else {
    switchTriggers.add("New regime: simpler filing, no investment proofs needed");
    switchTriggers.add("New regime benefits: NPS Employer (80CCD(2)) + Meal Vouchers in both regimes");
}
```

`150000`, `0.30`, `25000` are hardcoded Java literals here, duplicating values that also live in
`allowance_limits` (`80C` → 150000, `80D` → 25000).

`estimateBreakevenDeductions` (`:64-70`):

```java
private long estimateBreakevenDeductions(SalaryProfileEntity profile, long newTax, long oldTax) {
    if (oldTax <= newTax) {
        return 0;
    }
    long gap = oldTax - newTax;
    return Math.round(gap / 0.30);
}
```

`gap / 0.30` is a `long` divided by a `double`, producing a `double`, then rounded — so this does
divide rather than truncate.

The branch is only reachable when `recommended == NEW_REGIME`, which implies `oldTax >= newTax`;
when they are equal the method returns `0`.
