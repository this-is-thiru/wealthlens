# 04 — Hardcoded values, tests, git history, TODOs, observations

Paths relative to `backend/src/main/java/com/thiru/wealthlens/` for Java, and to
`backend/src/main/resources/` for data, unless stated otherwise.

---

## 1. Hardcoded values inventory

### 1.1 In Java source

| Value | Meaning | File:line | Hardcoded or configurable | Tied to a tax year? |
|---|---|---|---|---|
| `30` | `DAYS_PER_MONTH` — declared, **never referenced** | `taxplanning/engine/HraExemptionCalculator.java:14` | Hardcoded | No |
| `12` | `MONTHS_PER_YEAR` | `HraExemptionCalculator.java:15` | Hardcoded | No |
| `"Mumbai","Delhi","Kolkata","Chennai","Hyderabad","Bengaluru","Pune","Ahmedabad"` | Metro city list — declared here, **never referenced** | `HraExemptionCalculator.java:16-19` | Hardcoded | No |
| `100_000` | Annual rent above which a landlord-PAN warning is emitted | `HraExemptionCalculator.java:57` | Hardcoded | Yes |
| `50.0` | Metro HRA cap, % of basic | `HraExemptionCalculator.java:62` | Hardcoded | Yes |
| `40.0` | Non-metro HRA cap, % of basic | `HraExemptionCalculator.java:63` | Hardcoded | Yes |
| `10.0` | Rent less 10% of basic | `HraExemptionCalculator.java:67` | Hardcoded | Yes |
| `0.10` | Same, in the unused `calculateFull` | `HraExemptionCalculator.java:112` | Hardcoded | Yes |
| `50 : 40` | Same, in the unused `calculateFull` | `HraExemptionCalculator.java:114` | Hardcoded | Yes |
| `"Mumbai","Delhi","Kolkata","Chennai","Hyderabad","Bengaluru","Pune","Ahmedabad"` | Metro city list — **the copy that is actually used** | `taxplanning/salary/dto/SalaryProfileResponse.java:53-56` | Hardcoded | No |
| `4.0` | Cess % fallback when `cessPercentage` is null | `taxplanning/engine/AbstractTaxEngine.java:202` | Fallback for DB value | Yes |
| `12` | Months, car perquisite annualisation | `AbstractTaxEngine.java:214` | Hardcoded | No |
| `12` | Months, driver perquisite annualisation | `AbstractTaxEngine.java:221` | Hardcoded | No |
| `75_000L` | Standard deduction fallback, new regime | `taxplanning/engine/NewRegimeTaxEngine.java:58` | Fallback for DB value | **Yes** |
| `200` | Per-meal exemption fallback | `NewRegimeTaxEngine.java:112` | Fallback | Yes |
| `2` | Meals per day fallback | `NewRegimeTaxEngine.java:114` | Fallback | Yes |
| `22` | Working days per month fallback | `NewRegimeTaxEngine.java:116` | Fallback | Yes |
| `12L` | Months | `NewRegimeTaxEngine.java:117` | Hardcoded | No |
| `15_000L` | Gift voucher annual cap fallback | `NewRegimeTaxEngine.java:138` | Fallback | Yes |
| `"2025-26"` | The only tax year the new-regime engine declares support for | `NewRegimeTaxEngine.java:53` | Hardcoded | **Yes** |
| `1_00_000` | Annual rent threshold in a warning | `taxplanning/engine/OldRegimeTaxEngine.java:248` | Hardcoded | Yes |
| `12` | Rent monthly → annual | `OldRegimeTaxEngine.java:270` | Hardcoded | No |
| `2` | Max children fallback, education allowance | `OldRegimeTaxEngine.java:306` | Fallback | Yes |
| `12` | Months, education allowance | `OldRegimeTaxEngine.java:306,308` | Hardcoded | No |
| `2` | Max children fallback, hostel allowance | `OldRegimeTaxEngine.java:324` | Fallback | Yes |
| `12` | Months, hostel allowance | `OldRegimeTaxEngine.java:324,326` | Hardcoded | No |
| `"2025-26"` | Old-regime engine's declared supported year | `OldRegimeTaxEngine.java:61` | Hardcoded | **Yes** |
| `96000L` | Car ≤1600cc with driver, **annual** fallback (unused method) | `taxplanning/engine/PerquisiteValuationService.java:29` | Fallback | Yes |
| `120000L` | Car >1600cc with driver, annual fallback (unused method) | `PerquisiteValuationService.java:32` | Fallback | Yes |
| `60000L` | Car ≤1600cc no driver, annual fallback (unused method) | `PerquisiteValuationService.java:37` | Fallback | Yes |
| `84000L` | Car >1600cc no driver, annual fallback (unused method) | `PerquisiteValuationService.java:40` | Fallback | Yes |
| `3000L` | Driver monthly fallback (unused method) | `PerquisiteValuationService.java:54` | Fallback | Yes |
| `12` | Months (unused method) | `PerquisiteValuationService.java:56,57` | Hardcoded | No |
| `200L` | Per-meal exemption fallback | `PerquisiteValuationService.java:66,161` | Fallback | Yes |
| `2L` | Meals per day fallback | `PerquisiteValuationService.java:67,162` | Fallback | Yes |
| `22L` | Working days/month fallback | `PerquisiteValuationService.java:69,164` | Fallback | Yes |
| `12` | Months | `PerquisiteValuationService.java:72,73,167` | Hardcoded | No |
| `8000L` | Car ≤1600cc with driver, **monthly** fallback | `PerquisiteValuationService.java:116` | Fallback | Yes |
| `10000L` | Car >1600cc with driver, monthly fallback | `PerquisiteValuationService.java:119` | Fallback | Yes |
| `5000L` | Car ≤1600cc no driver, monthly fallback | `PerquisiteValuationService.java:124` | Fallback | Yes |
| `7000L` | Car >1600cc no driver, monthly fallback | `PerquisiteValuationService.java:127` | Fallback | Yes |
| `3000L` | Driver monthly fallback | `PerquisiteValuationService.java:147` | Fallback | Yes |
| `15000L` | Gift annual limit fallback | `PerquisiteValuationService.java:183` | Fallback | Yes |
| `5000000L` | `SALARY_THRESHOLD_FOR_ITR2` — ₹50L gross above which ITR-2 is advised (unused class) | `taxplanning/engine/ItrFormAdvisor.java:11` | Hardcoded | Yes |
| `150000` | 80C ceiling used to compute "unclaimed 80C" | `taxplanning/recommendation/RegimeAdvisor.java:32,33` | Hardcoded | **Yes** |
| `0.30` | Assumed marginal rate for the 80C saving estimate | `RegimeAdvisor.java:34` | Hardcoded | Yes |
| `25000` | 80D ceiling in a switch-trigger string | `RegimeAdvisor.java:43` | Hardcoded | **Yes** |
| `0.30` | Assumed marginal rate for the break-even estimate | `RegimeAdvisor.java:69` | Hardcoded | Yes |
| `12` | Rent monthly → annual, formula variables | `taxplanning/recommendation/RestructuringEngine.java:211` | Hardcoded | No |
| `12` | Months, car perquisite | `RestructuringEngine.java:232` | Hardcoded | No |
| `12` | Months, driver perquisite | `RestructuringEngine.java:233` | Hardcoded | No |
| `0.30` | Marginal-rate fallback when slabs are absent | `RestructuringEngine.java:240,248` | Hardcoded | Yes |
| `1`,`2`,`3`,`4`,`5` | Allowance restructuring priorities | `RestructuringEngine.java:196-200` | Hardcoded | No |
| `0` / `10` | `numberOfChildren` accepted range | `taxplanning/salary/service/SalaryProfileService.java:185` | Hardcoded | No |
| `"2025-26"` | Seeding target year — four separate local variables | `taxplanning/policy/service/PolicySeederService.java:54,73,91,118` | Hardcoded | **Yes** |
| `"data/tax-policies/slab-policy-2025-26-new.json"` | Seed path | `PolicySeederService.java:48` | Hardcoded | **Yes** |
| `"data/tax-policies/slab-policy-2025-26-old.json"` | Seed path | `PolicySeederService.java:49` | Hardcoded | **Yes** |
| `"data/tax-policies/perquisite-policy-2025-26.json"` | Seed path | `PolicySeederService.java:80` | Hardcoded | **Yes** |
| `"data/tax-policies/allowance-catalogue-2025-26.json"` | Seed path | `PolicySeederService.java:98` | Hardcoded | **Yes** |
| `"data/tax-policies/allowance-limits-2025-26.json"` | Seed path | `PolicySeederService.java:125` | Hardcoded | **Yes** |
| `"This is an indicative computation only. Consult a qualified CA for advice."` | PDF disclaimer | `taxplanning/document/DocumentGenerationService.java:33` | Hardcoded | No |
| `"taxplanning/tax-comparison-report"` | Thymeleaf template name | `DocumentGenerationService.java:34` | Hardcoded | No |
| `"Income Tax Act 2025 and IT Rules 2026"` | Act citation in the HR letter (unused class) | `taxplanning/recommendation/HrLetterContentBuilder.java:16` | Hardcoded | **Yes** |

### 1.2 In seed data — `resources/data/tax-policies/slab-policy-2025-26-new.json`

| Value | Meaning | File:line | Hardcoded or configurable | Tied to a tax year? |
|---|---|---|---|---|
| `"2025-26"` | Tax year | `:2` | Data | Yes |
| `75000` | Standard deduction, new regime | `:6` | Data (DB) | Yes |
| `1200000` | 87A rebate income limit | `:7` | Data | Yes |
| `60000` | 87A rebate maximum | `:8` | Data | Yes |
| `400000` | Basic exemption limit — **stored, never read** | `:9` | Data | Yes |
| `0–400000 @ 0.0%` | Slab 1 | `:11` | Data | Yes |
| `400001–800000 @ 5.0%` | Slab 2 | `:12` | Data | Yes |
| `800001–1200000 @ 10.0%` | Slab 3 | `:13` | Data | Yes |
| `1200001–1600000 @ 15.0%` | Slab 4 | `:14` | Data | Yes |
| `1600001–2000000 @ 20.0%` | Slab 5 | `:15` | Data | Yes |
| `2000001–2400000 @ 25.0%` | Slab 6 | `:16` | Data | Yes |
| `2400001–null @ 30.0%` | Slab 7 | `:17` | Data | Yes |
| `50000000–100000000 @ 10.0%` | Surcharge band 1 (₹5 crore, not ₹50 lakh) | `:20` | Data | Yes |
| `100000001–200000000 @ 15.0%` | Surcharge band 2 | `:21` | Data | Yes |
| `200000001–500000000 @ 25.0%` | Surcharge band 3 | `:22` | Data | Yes |
| `500000001–null @ 25.0%` | Surcharge band 4 | `:23` | Data | Yes |
| `4.0` | Cess % | `:25` | Data | Yes |

### 1.3 `slab-policy-2025-26-old.json`

| Value | Meaning | File:line | Hardcoded or configurable | Tied to a tax year? |
|---|---|---|---|---|
| `50000` | Standard deduction, old regime | `:6` | Data | Yes |
| `500000` | 87A rebate income limit | `:7` | Data | Yes |
| `12500` | 87A rebate maximum | `:8` | Data | Yes |
| `250000` | Basic exemption limit — stored, never read | `:9` | Data | Yes |
| `0–250000 @ 0.0%` | Slab 1 | `:11` | Data | Yes |
| `250001–500000 @ 5.0%` | Slab 2 | `:12` | Data | Yes |
| `500001–1000000 @ 20.0%` | Slab 3 | `:13` | Data | Yes |
| `1000001–null @ 30.0%` | Slab 4 | `:14` | Data | Yes |
| `50000000–100000000 @ 10.0%` | Surcharge band 1 | `:17` | Data | Yes |
| `100000001–200000000 @ 15.0%` | Surcharge band 2 | `:18` | Data | Yes |
| `200000001–500000000 @ 25.0%` | Surcharge band 3 | `:19` | Data | Yes |
| `500000001–null @ 37.0%` | Surcharge band 4 | `:20` | Data | Yes |
| `4.0` | Cess % | `:22` | Data | Yes |

The old-regime slab table has **no 10% band and no 15% band** — it jumps 5% → 20% at ₹5,00,001
(`:12-13`).

### 1.4 `perquisite-policy-2025-26.json`

| Value | Meaning | File:line | Hardcoded or configurable | Tied to a tax year? | Read by code? |
|---|---|---|---|---|---|
| `5000` | Car ≤1600cc/EV, no driver — monthly | `:5` | Data | Yes | Yes |
| `8000` | Car ≤1600cc/EV, with driver — monthly | `:6` | Data | Yes | Yes |
| `7000` | Car >1600cc, no driver — monthly | `:7` | Data | Yes | Yes |
| `10000` | Car >1600cc, with driver — monthly | `:8` | Data | Yes | Yes |
| `3000` | Driver perquisite — monthly | `:9` | Data | Yes | Yes |
| `200` | Per-meal exempt amount | `:10` | Data | Yes | Yes |
| `2` | Meals per day | `:11` | Data | Yes | Yes |
| `22` | Working days per month | `:12` | Data | Yes | Yes |
| `15000` | Gift annual limit | `:13` | Data | Yes | Yes |
| `200000` | Concessional loan exempt limit | `:14` | Data | Yes | **No reader** |
| `10.0` | Accommodation, metro % | `:15` | Data | Yes | **No reader** |
| `7.5` | Accommodation, large city % | `:16` | Data | Yes | **No reader** |
| `5.0` | Accommodation, other % | `:17` | Data | Yes | **No reader** |
| `3000` | Employer school fees, monthly per child | `:18` | Data | Yes | **No reader** |
| `10.0` | Car depreciation rate % | `:19` | Data | Yes | **No reader** |

### 1.5 `allowance-limits-2025-26.json` — the 14 rows the engines actually read

| Code | Regime | Employer | annualLimitFixed | monthlyLimitFixed | ratePercent | limitFormula | Line |
|---|---|---|---|---|---|---|---|
| `80C` | OLD | — | `150000` | — | — | `min(actual_investment, 150000)` | `:2` |
| `80D` | OLD | — | `25000` | — | — | — | `:3` |
| `80CCD_1B` | OLD | — | `50000` | — | — | — | `:4` |
| `24B` | OLD | — | `200000` | — | — | — | `:5` |
| `CHILDREN_EDUCATION` | OLD | — | `72000` | `3000` | — | — | `:6` |
| `HOSTEL_ALLOWANCE` | OLD | — | `216000` | `9000` | — | — | `:7` |
| `EMPLOYER_PF` | NEW | — | — | — | `12.0` | — | `:8` |
| `EMPLOYER_PF` | OLD | — | — | — | `12.0` | — | `:9` |
| `NPS_EMPLOYER` | NEW | GOVT | — | — | `14.0` | `min(0.14 * (basic + da), actual_nps)` | `:10` |
| `NPS_EMPLOYER` | NEW | PSU | — | — | `14.0` | `min(0.14 * (basic + da), actual_nps)` | `:11` |
| `NPS_EMPLOYER` | NEW | PRIVATE | — | — | `14.0` | `min(0.14 * (basic + da), actual_nps)` | `:12` |
| `NPS_EMPLOYER` | OLD | GOVT | — | — | `14.0` | `min(0.14 * (basic + da), actual_nps)` | `:13` |
| `NPS_EMPLOYER` | OLD | PSU | — | — | `14.0` | `min(0.14 * (basic + da), actual_nps)` | `:14` |
| `NPS_EMPLOYER` | OLD | PRIVATE | — | — | `10.0` | `min(0.10 * (basic + da), actual_nps)` | `:15` |

No row carries `effectiveDate`, `financialYear`, `assessmentYear`, `notifiedDate` or
`sourceReference`, although all three resolution queries order by `effectiveDate` descending
(`taxplanning/policy/repository/AllowanceLimitRepository.java:13,16,19`).

### 1.6 `allowance-catalogue-2025-26.json` — 39 items

Numeric limits appearing on catalogue items (none of which any engine reads — see
`03-coverage-matrix.md` §2.5):

| Code | Regime | annualLimitFixed | monthlyLimitFixed | itSection |
|---|---|---|---|---|
| `HRA` | OLD | null | null | Section 10(13A) |
| `LTA` | OLD | null | null | Section 10(5) |
| `NPS_EMPLOYER` | NEW / OLD | null | null | Section 80CCD(2) |
| `NPS_SELF` | OLD | `50000` | null | Section 80CCD(1B) |
| `MEAL_VOUCHER` | NEW / OLD | `105600` | `8800` | Section 10(17) |
| `CAR_PERQUISITE` | NEW / OLD | null | null | Rule 3 |
| `TELEPHONE_MOBILE` | NEW / OLD | null | null | Section 10(14) |
| `BOOKS_PERIODICALS` | OLD | null | null | Section 10(14) |
| `PROFESSIONAL_DEVELOPMENT` | OLD | null | null | Section 10(14) |
| `UNIFORM_ALLOWANCE` | OLD | null | null | Section 10(14) |
| `COMMUTED_PENSION` | NEW / OLD | null | null | Section 10(10A) |
| `GRATUITY_RECEIVED` | NEW / OLD | `2000000` | null | Section 10(10) |
| `LEAVE_ENCASHMENT` | NEW / OLD | null | null | Section 10(10AA) |
| `VRS_RECEIVED` | NEW / OLD | `500000` | null | Section 10(10C) |
| `CHILDREN_EDUCATION` | OLD | `9600` | null | Section 10(14) |
| `HOSTEL_ALLOWANCE` | OLD | `18000` | null | Section 10(14) |
| `TRANSPORT_ALLOWANCE` | OLD | `19200` | null | Section 10(14) |
| `MEDICAL_ALLOWANCE` | OLD | null | null | Section 10(14) |
| `GIFTS` | NEW / OLD | `15000` | null | Section 17(2) |
| `INTEREST_CONCESSION` | NEW / OLD | null | null | Section 10(14) |
| `RELOCATION_ALLOWANCE` | NEW / OLD | null | null | Section 10(14) |
| `NEW_TAX_REGIME_DEFAULT` | NEW | `75000` | null | Section 16 |
| `OLD_TAX_REGIME_STANDARD_DEDUCTION` | OLD | `50000` | null | Section 16 |
| `SECTION_80C` | OLD | `150000` | null | Section 80C |
| `SECTION_80D` | OLD | null | null | Section 80D |
| `SECTION_80E` | OLD | null | null | Section 80E |
| `SECTION_24` | OLD | `200000` | null | Section 24 |
| `SECTION_80G` | OLD | null | null | Section 80G |

`CHILDREN_EDUCATION` reads `9600` here and `72000`/`3000` in `allowance-limits`; `HOSTEL_ALLOWANCE`
reads `18000` here and `216000`/`9000` in `allowance-limits`. The engines use the
`allowance-limits` figures (`03-coverage-matrix.md` §2).

---

## 2. Tests inventory

### 2.1 `taxplanning/engine/HraExemptionCalculatorTest.java` — 5 tests, no mocks

| Test | Scenario | Inputs | Expected |
|---|---|---|---|
| `metro_highRent_cap2Binding` (`:49-66`) | Metro; the 50%-of-basic cap binds | hra `2_70_000`, basic `5_00_000`, rent `3_20_000`, metro `true`, payingRent `true` | `exemptionAmount == 2_50_000` |
| `metro_lowRent_cap3Binding` (`:72-83`) | Metro; rent−10%-of-basic binds | hra `2_40_000`, basic `5_00_000`, rent `1_80_000`, metro `true`, payingRent `true` | `exemptionAmount == 1_30_000` |
| `nonMetro_mediumRent_cap1Binding` (`:100-116`) | Non-metro; actual HRA binds | hra `60_000`, basic `5_00_000`, rent `1_20_000`, metro `false`, payingRent `true` | `exemptionAmount == 60_000` |
| `notPayingRent_returnsZero` (`:120-133`) | Not paying rent | hra `2_40_000`, basic `5_00_000`, rent `1_80_000`, metro `true`, payingRent `false` | `0` and a warning containing `"not paying rent"` |
| `annualRentAbove1L_landlordPanWarning` (`:137-151`) | Rent above ₹1L | hra `2_40_000`, basic `5_00_000`, rent `1_50_000`, metro `true`, payingRent `true` | a warning containing `"landlord PAN"`, and `exemptionAmount > 0` |

Lines `:17-48` and `:85-99` of this file are a long running commentary in which the author works out,
in comments, which cap binds — including `"Wait, test name says cap2 ... Let me re-read..."`
(`:23`) and `"Hmm this is tricky. Let me re-think."` (`:39`). Reproduced verbatim in §4.

### 2.2 `taxplanning/engine/MarginalReliefCalculatorTest.java` — 7 tests, no mocks

Tests a class with **no production caller** (`03-coverage-matrix.md` §3).

| Test | Scenario | Inputs | Expected |
|---|---|---|---|
| `rebateMarginalRelief_12L10K` (`:23-35`) | Income ₹10k over the 12L limit | taxableIncome `12_10_000`, computedTax `61_500`, rebateLimit `12_00_000`, rebateAmount `60_000` | `reliefApplied == true`, `relievedTax == 10_000`, `reliefAmount == 51_500` |
| `rebateMarginalRelief_12L75K` (`:43-56`) | Upper edge of the relief band | `12_75_000`, `71_250`, `12_00_000`, `60_000` | `reliefApplied == false`, `relievedTax == 71_250`, `reliefAmount == 0` |
| `surchargeMarginalRelief_50L1L` (`:64-91`) | Re-uses the rebate method at a ₹50L boundary | `50_01_000`, `10_000`, `50_00_000`, `0` | `reliefApplied == true`, `relievedTax == 1_000`, `reliefAmount == 9_000` |
| `noRelief_whenIncomeBelowLimit` (`:97-110`) | Zero tax below the limit | `11_00_000`, `0`, `12_00_000`, `60_000` | `reliefApplied == false`, `relievedTax == 0` |
| `noRelief_whenTaxBelowMaxTax` (`:114-139`) | Tax already under the cap | `12_01_000`, `500`, `12_00_000`, `60_000` | `reliefApplied == false`, `relievedTax == 500` |
| `applyMarginalRelief_basic` (`:143-155`) | Direct `applyMarginalRelief` | taxBeforeRebate `80_000`, taxableIncome `12_10_000`, rebateLimit `12_00_000` | returns `10_000` |
| `isMarginalReliefApplicable` (`:159-164`) | Three predicate cases | (`80_000`,`12_10_000`,`12_00_000`) / (`5_000`,`12_10_000`,`12_00_000`) / (`80_000`,`11_00_000`,`12_00_000`) | `true` / `false` / `false` |

Test 3's own comments record the confusion: `"This isn't the right method for surcharge"` (`:72`)
and `"applyMarginalRelief is different from computeRebateMarginalRelief"` (`:74`). Test 4's comment
(`:94-96`) describes an edge case it then does not exercise.

### 2.3 `taxplanning/engine/PerquisiteValuationServiceTest.java` — 10 tests, no mocks

Fixture policy (`:161-174`): car `5_000`/`8_000`/`7_000`/`10_000`, driver `3_000`, meal
`200`×`2`×`22`, gift `15_000`.

| Test | Scenario | Inputs | Expected |
|---|---|---|---|
| `carLeq1600cc_noDriver_5000` (`:24-36`) | Small engine, no driver | `EMPLOYER_OWNED`, `LEQ_1600CC_OR_EV`, driver `false`, personal `true` | `5_000` |
| `carLeq1600cc_withDriver_8000` (`:40-52`) | Small engine, driver | `EMPLOYER_OWNED`, `LEQ_1600CC_OR_EV`, driver `true`, personal `true` | `8_000` |
| `carGt1600cc_noDriver_7000` (`:56-68`) | Large engine, no driver | `EMPLOYER_OWNED`, `GT_1600CC`, driver `false`, personal `true` | `7_000` |
| `carOnlyOfficialUse_zero` (`:72-84`) | Official use only | `EMPLOYER_OWNED`, `LEQ_1600CC_OR_EV`, driver `false`, personal `false` | `0` |
| `carEmployeeOwned_zero` (`:88-100`) | Employee owns the car | `EMPLOYEE_OWNED`, `GT_1600CC`, driver `true`, personal `true` | `0` |
| `driverOnly_noCar` (`:104-113`) | Driver provided | `true`, policy | `3_000` |
| `noDriver_zeroDriverPerquisite` (`:117-125`) | No driver | `false`, policy | `0` |
| `mealVoucher_withinLimit_fullExemption` (`:129-136`) | Below the cap | annual `1_00_000` | `1_00_000` |
| `mealVoucher_exceedsLimit_capped` (`:140-147`) | Above the cap | annual `1_20_000` | `1_05_600` |
| `giftVoucher_withinLimit_fullExemption` (`:151-157`) | Below the cap | annual `10_000` | `10_000` |

`CarOwnership.LEASED` and `computeCarPerquisiteMonthly` with a large-engine + driver combination are
untested. `calculateCarPerquisite`, `calculateDriverPerquisite` and `calculateMealPerquisite` (the
three unused methods) have **no tests**.

### 2.4 `taxplanning/engine/NewRegimeTaxEngineTest.java` — 12 tests

Mocks `FormulaEvaluator`, `AllowanceResolutionService`, `PerquisiteValuationService` (`:29-36`).
`resolutionService.resolve(...)` is stubbed by `createResolvedAllowance` (`:362-380`), which returns
limits with **no `limitFormula`** — so `evaluateLimit` takes the `formula == null` branch and
returns `Long.MAX_VALUE` (`NewRegimeTaxEngine.java:200-202`). The real seeded formulas are therefore
never exercised by this suite.

Fixture slab policy (`:311-335`): std deduction `75_000`, 87A limit `1_200_000`, 87A amount
`60_000`, cess `4.0`, the seven new-regime slabs, four surcharge bands starting at `50_000_000`.
Every profile sets `EmployerType.PRIVATE` and `CarOwnership.EMPLOYEE_OWNED` (`:299-305`).

| Test | Scenario | Inputs | Expected |
|---|---|---|---|
| `taxableIncome_12Lakhs_fullRebate_zeroTax` (`:54-74`) | Full 87A rebate | BASIC `900_000`, SPECIAL_ALLOWANCE `300_000` (gross `12,00,000`) | `basicTaxBeforeRebate == 52_500`, `rebate87aApplied == 52_500`, `taxAfterRebate == 0`, `surcharge == 0`, `cess == 0`, `totalTax == 0` |
| `taxableIncome_12L10K_marginalRelief` (`:78-95`) | Relief just over 12L | BASIC `10_00_000`, SPECIAL_ALLOWANCE `2_85_000` (taxable `12,10,000`) | `marginalReliefApplied == true`, `taxAfterRebate == 10_000`, `surcharge == 0`, `cess == 400`, `totalTax == 10_400` |
| `taxableIncome_12L75K_upperEdgeMarginalRelief` (`:99-114`) | Upper edge | BASIC `10_50_000`, SPECIAL_ALLOWANCE `3_00_000` | `taxableIncome == 12_75_000`, `basicTaxBeforeRebate == 71_250`, `rebate87aApplied == 0`, `marginalReliefApplied == false` |
| `taxableIncome_12L76K_noMarginalRelief` (`:118-134`) | Above the band | BASIC `11_00_000`, SPECIAL_ALLOWANCE `2_76_000` | `taxableIncome == 13_01_000`, `marginalReliefApplied == false`, `surcharge == 0` |
| `taxableIncome_15Lakhs_correctSlabs` (`:138-154`) | Mid-range slabs | BASIC `12_00_000`, SPECIAL_ALLOWANCE `3_75_000` | `basicTaxBeforeRebate == 1_05_000`, `rebate87aApplied == 0`, `taxAfterRebate == 1_05_000`, `cess == 4_200`, `surcharge == 0`, `totalTax == 1_09_200` |
| `taxableIncome_8Lakhs_employerPfExempt` (`:158-179`) | NPS exemption | BASIC `6_25_000`, NPS_EMPLOYER `75_000`, SPECIAL_ALLOWANCE `1_00_000` | `basicTaxBeforeRebate == 12_500`, `taxableIncome == 6_50_000`, `totalTax == 0` |
| `employerNps_14pctOfBasic_fullExemption` (`:183-201`) | NPS at 14% of basic | BASIC `5_00_000`, NPS_EMPLOYER `70_000`, SPECIAL_ALLOWANCE `5_00_000` | `totalDeductions >= 70_000` |
| `employerNps_15pctOfBasic_capAt14pct` (`:205-224`) | NPS at 15% of basic | BASIC `5_00_000`, NPS_EMPLOYER `75_000`, SPECIAL_ALLOWANCE `5_00_000` | `taxableIncome == 9_25_000` |
| `mealVoucher_8800Monthly_fullExemption` (`:228-242`) | Meal at the cap | BASIC `10_00_000`, MEAL_VOUCHER `1_05_600` | `totalExemptions >= 1_05_600` |
| `mealVoucher_10000Monthly_capped` (`:246-262`) | Meal above the cap | BASIC `10_00_000`, MEAL_VOUCHER `1_20_000` | `warnings == null || warnings.isEmpty()` |
| `surcharge_at51L_10pct` (`:266-280`) | ₹51L taxable | BASIC `40_00_000`, SPECIAL_ALLOWANCE `11_75_000` | `basicTaxBeforeRebate == 11_10_000`, **`surcharge == 0`** with the comment `// taxable 51L < 5Cr threshold` |
| `esopDetected_warningGenerated` (`:284-295`) | ESOP present | BASIC `10_00_000`, ESOP `5_00_000`, `esopPresent = true` | `result != null`, `grossSalary >= 15_00_000` — **the warning itself is not asserted** |

Test 7 asserts `totalDeductions >= 70_000`; new-regime `totalDeductions` is the standard deduction
(`75_000`) plus zero Chapter VI-A (`NewRegimeTaxEngine.java:153-157`), so the assertion passes
without the NPS exemption contributing. Test 10's name says "capped" but asserts only that no
warnings were produced. Test 11's name says `10pct` and asserts `0`.

### 2.5 `taxplanning/engine/OldRegimeTaxEngineTest.java` — 8 tests

Mocks `FormulaEvaluator`, `AllowanceResolutionService`, `HraExemptionCalculator`,
`PerquisiteValuationService` (`:28-38`). Fixture slab policy (`:213-234`): std deduction `50_000`,
87A limit `500_000`, 87A amount `12_500`, cess `4.0`, the four old-regime slabs, four surcharge
bands starting at `50_000_000`.

| Test | Scenario | Inputs | Expected |
|---|---|---|---|
| `hraExemption_metro_3wayMinimum` (`:54-74`) | Metro HRA (calculator stubbed) | BASIC `5_00_000`, HRA `2_40_000`, metro `true`, rent `15_000`/mo, payingRent `true`; stub returns `1_30_000` | `totalExemptions == 1_30_000`, `appliedExemptions` contains `"HRA Exemption"` |
| `hraExemption_nonMetro_40pct` (`:78-95`) | Non-metro (stubbed) | same components, metro `false`; stub returns `1_50_000` | `totalExemptions == 1_50_000` |
| `hra_zeroWhenNotPayingRent` (`:99-117`) | Not paying rent (stubbed) | same, payingRent `false`; stub returns `0` with a warning | `totalExemptions == 0` |
| `section80c_exact150K` (`:121-132`) | 80C exactly at the cap | BASIC `10_00_000`, `investment80c = 1_50_000` | `taxableIncome == 8_00_000` |
| `section80c_above150K_capped` (`:136-147`) | 80C above the cap | BASIC `10_00_000`, `investment80c = 2_00_000` | `taxableIncome == 8_00_000` |
| `section24b_homeLoan210K_cappedAt200K` (`:151-162`) | 24(b) above the cap | BASIC `15_00_000`, `homeLoanInterest = 2_10_000` | `taxableIncome == 12_50_000` |
| `rebate87A_at5L_exact12500` (`:166-180`) | Full 87A rebate | BASIC `5_50_000` | `basicTaxBeforeRebate == 12_500`, `rebate87aApplied == 12_500`, `taxAfterRebate == 0`, `totalTax == 0` |
| `surcharge_above5Cr_oldRegime_37pct` (`:184-197`) | Named for a ₹5.07 crore case | BASIC `40_00_000`, SPECIAL_ALLOWANCE `10_75_000` (gross `50,75,000`) | **`surcharge == 0`** with the comment `// taxable 50.25L < 5Cr threshold` |

Test 8's inputs do not produce the income its name and comments describe (`:186-187` say
"taxableIncome = 5,07,00,000 (5.07Cr)"; the components sum to `50,75,000`).

### 2.6 `taxplanning/engine/FormulaEvaluatorSandboxTest.java` — 3 tests + 1 parameterized (3 values)

Tagged `@Tag("unit")` (`:22`) — the only tagged test in the module. Uses a real `FormulaEvaluator`.

| Test | Scenario | Inputs | Expected |
|---|---|---|---|
| `evaluate_whenFormulaReachesOutsideTheVocabulary_refuses` (`:34-35`) | Sandbox escape attempts | `"T(java.lang.Runtime).getRuntime().availableProcessors()"`, `"T(java.lang.System).getProperty('user.name').length()"`, `"new java.lang.String('x').length()"`, each with `Map.of("basic", 100L)` | `BadRequestException` |
| `evaluate_whenFormulaUsesTheVariables_stillWorks` (`:41-43`) | Arithmetic over a variable | `"#basic * 10 / 100"`, `basic = 500000` | `50000L` |
| `evaluate_whenHraFormula_stillWorks` (`:47-50`) | HRA-shaped expression | `"#rentPaid - (#basic * 10 / 100)"`, `rentPaid = 90000`, `basic = 500000` | `40000L` |
| `evaluate_whenFormulaIsBlank_isZero` (`:54-55`) | Blank | `"  "`, empty map | `0L` |

Both working examples use the `#name` syntax. **No test evaluates any formula string that is
actually stored in `allowance-limits-2025-26.json`.**

### 2.7 `integration/TaxPlanningControllerIntegrationTest.java` — 14 tests

Extends `AbstractIntegrationTest` (Testcontainers Mongo, `@ActiveProfiles("integration-test")`).
Users `usera@example.com` / `userb@example.com` (`:29-30`).

Shared fixture `buildSalaryProfileRequest` (`:50-74`): profileName `"Test Profile"`, type `CURRENT`,
employer `"TestCorp"`, taxYear `"2025-26"`, cityTier `METRO_8`, cityName `"Bengaluru"`,
employerType `PRIVATE`, components `BASIC 600000`, `HRA 240000`, `SPECIAL_ALLOWANCE 360000`,
`NPS_EMPLOYER 70000` (`:66`), `EMPLOYEE_PF 72000` (`:67`), **`EMPLOR_PF 72000`** (`:68`),
carProvided `false`,
investment80c `100000`, investment80d `25000`, monthlyRentPaid `15000`, isPayingRent `true`,
numberOfChildren `1`, isPhysicallyDisabled `false`.

| # | Test | Scenario | Assertions |
|---|---|---|---|
| 1 | `createProfile_success` (`:93`) | POST profile | `200`; body email `== usera@example.com` |
| 2 | `getProfiles_success` (`:117`) | POST then GET list | both `200` |
| 3 | `getProfile_success` (`:149`) | POST then GET by id | `200`; id and email match |
| 4 | `updateProfile_success` (`:181`) | PUT with regime `OLD_REGIME` | `200`; `regimeType == OLD_REGIME` |
| 5 | `deleteProfile_success` (`:214`) | DELETE then GET | `204`; subsequent GET is `400` or `404` |
| 6 | `computeTax_success` (`:252`) | POST compute | `200`; `newRegimeResult`, `oldRegimeResult`, `recommendedRegime` all non-null |
| 7 | `restructure_success` (`:288`) | POST restructure | `200`; `recommendations` is a `List`, `totalOptimizedSaving` is a `Number`, `restructuredProfile` non-null |
| 8 | `generateTaxReport_success` (`:333`) | GET tax-report | `200`; `Content-Type == application/pdf` |
| 9 | `getAllowances_public_noAuth` (`:365`) | GET public allowances with no token | `200` |
| 10 | `getAllowanceByCode_success` (`:382`) | GET public allowance by code | `200` |
| 11 | `security_userCannotAccessOtherUserProfile` (`:397`) | User B reads User A's profile | `403` or `401` |
| 12 | `security_superUserCanAccessAnyProfile` (`:430`) | SUPER_USER reads User A's profile | `200`; id matches |
| 13 | `admin_createSlabPolicy_superUserOnly` (`:462`) | SUPER_USER POSTs a slab policy — taxYear `"2025-26"`, `NEW_REGIME`, stdDeduction `75000`, rebate87aLimit `700000`, rebate87aAmount `25000`, basicExemptionLimit `300000`, cess `4.0`, **no slabs** | `200` or `201` |
| 14 | `admin_createSlabPolicy_regularUserForbidden` (`:493`) | Regular user POSTs the same | `403` |

**No integration test asserts a computed tax figure.** Test 6 checks only that the three fields are
present.

### 2.8 Flows with zero test coverage

| Flow / class | Coverage |
|---|---|
| `AbstractTaxEngine` `computeSurcharge` with a non-zero result | **None** — every surcharge assertion in the suite expects `0` |
| `AbstractTaxEngine` `computeMarginalRelief` under the **old** regime | **None** |
| `AbstractTaxEngine.hasComponent` | **None** (no caller either) |
| `AbstractTaxEngine.resolveAnnualLimit` / `resolveRatePercent` failure branch | **None** — the mocks always resolve |
| `OldRegimeTaxEngine.computeChildrenEducationExemption` | **None** — no test sets `numberOfChildren` on a unit-test profile |
| `OldRegimeTaxEngine.computeHostelExemption` | **None** |
| `OldRegimeTaxEngine` books / telephone / uniform exemptions | **None** |
| `OldRegimeTaxEngine` meal and gift voucher paths | **None** (only the new-regime versions are tested) |
| `OldRegimeTaxEngine.computeWarnings` | **None** |
| `OldRegimeTaxEngine.evaluateLimit` with a real formula | **None** — the evaluator is mocked |
| `NewRegimeTaxEngine.computeWarnings` | **None** — `esopDetected_warningGenerated` asserts gross, not warnings |
| `NewRegimeTaxEngine` meal/gift **fallback** catch branches | **None** |
| `RestructuringEngine` | **No unit test.** Only integration test 7, which asserts types not values |
| `RegimeAdvisor` | **None** |
| `HrLetterContentBuilder` | **None** |
| `ItrFormAdvisor` | **None** |
| `FbpOptimizer` | **None** |
| `TaxComputationService` | **No unit test**; integration test 6 asserts only field presence |
| `SalaryProfileService` — all seven `validateRequest` rules | **None** |
| `PolicyService` supersession | **None** |
| `PolicySeederService` | **None** |
| `AllowanceResolutionService` three-tier fallback | **None** |
| `DocumentGenerationService` / template rendering | Only integration test 8's content-type check |
| PDF restructuring section | **Unreachable** (`03-coverage-matrix.md` §9, Observation O-20) |
| `HraExemptionCalculator.calculate` / `calculateFull` | **None** |
| `PerquisiteValuationService.calculateCarPerquisite` / `calculateDriverPerquisite` / `calculateMealPerquisite` | **None** |
| `TaxEngineFactory` | **None** — including the `NOT_DECIDED` throw |
| Every `@Deprecated`-free error path mapping to HTTP 400/500 | **None** |

Coverage gating: `pom.xml:250,268` scope both JaCoCo rules to `brokercharges`, so **no coverage
threshold applies to `taxplanning`**. PIT does target `com.thiru.wealthlens.taxplanning.engine.*`
(`pom.xml:399`) with test package `com.thiru.wealthlens.taxplanning.*` (`pom.xml:403`).

---

## 3. Git history

`git log --oneline` scoped to the module's main sources returns **7 commits** in total.

| Commit | Date | Author | Subject |
|---|---|---|---|
| `5eaeb98` | 2026-09-13 | Peramasani Thirumala Yadav | `fix(security): stop stored formulas from reaching outside their vocabulary` |
| `75e8a64` | 2026-09-13 | Peramasani Thirumala Yadav | `Revert "Feature/charges engine core (#62)"` |
| `d3e098c` | 2026-09-13 | Peramasani Thirumala Yadav | `Feature/charges engine core (#62)` |
| `4f4962d` | 2026-09-05 | Peramasani Thirumala Yadav | `chore: harden test framework before the charges engine build (#59)` |
| `5d96fb5` | 2026-08-17 | Peramasani Thirumala Yadav | `model chnages` *(sic)* |
| `ace7a13` | 2026-08-17 | Thirumal | `Feature/taxplanning module (#58)` |
| `95b2f9c` | 2026-06-21 | Thirumal | `Feature/wealthlens modulith refactor (#57)` |

Most-changed files in the module:

| Changes | File |
|---|---|
| 4 | `taxplanning/engine/FormulaEvaluator.java` |
| 3 | `taxplanning/recommendation/AllowanceRecommendation.java` |
| 3 | `taxplanning/policy/entity/AllowanceCatalogueEntity.java` |
| 2 | `taxplanning/package-info.java` |
| 2 | `taxplanning/controller/TaxPlanningController.java` |
| 2 | `taxplanning/TaxPlanningModulePlaceholder.java` *(no longer present in the tree)* |
| 1 | every other file |

### Reading of the history

- The module arrived essentially whole in `ace7a13` (2026-08-17) and has had **one** functional
  change since.
- `95b2f9c` (2026-06-21) predates the module's content — it is the modulith refactor that created
  `package-info.java` and a placeholder class.
- `5d96fb5` ("model chnages", same day as `ace7a13`) touched the entities.
- `4f4962d` and `d3e098c`/`75e8a64` are charges-engine work that brushed the module incidentally.
- **`5eaeb98` is the only commit whose message names a defect**, and it is a security fix, not a tax
  logic fix. Its subject: *"stop stored formulas from reaching outside their vocabulary."* The
  context is recorded at length in `docs/charges-engine/backlog.md` under **CE-1**, which states
  that `taxplanning/engine/FormulaEvaluator` had the identical `StandardEvaluationContext` hole as
  the charges engine's evaluator, reachable through `PUT /tax-planning/admin/allowances/{code}`
  because that endpoint's request body carries `limitFormula`; and that an
  `evaluate(String, EvaluationContext)` overload allowing a caller-supplied context existed with no
  callers and was removed. The fix introduced `shared/util/expression/SafeExpressions` as a shared
  floor for both evaluators.

No commit message in the module's history mentions a tax-computation bug, a rate correction, or a
tax-year rollover.

**Working-tree state.** At the start of this pass `git status` showed one unstaged modification,
`backend/src/main/java/com/thiru/wealthlens/portfolio/dto/AssetRequest.java` — unrelated to this
module and never touched here. By the end of the pass it had been moved into `stash@{0}` ("WIP on
master: 55024ad"), so the final `git status` lists only the untracked `docs/taxplanning-context/`
folder. This pass ran no git write commands; `HEAD` remained `55024ad` throughout.

---

## 4. TODO / FIXME / HACK / commented-out code — verbatim

There are **no `TODO`, `FIXME`, `HACK` or `XXX` markers anywhere in the `taxplanning` module.**
(The three `TODO`s in the repository are in `corporate/service/CorporateActionService.java:247,617`
and `portfolio/entity/AssetEntity.java:107`.)

What exists instead are self-describing placeholder comments:

`taxplanning/engine/HraExemptionCalculator.java:56`:

```java
        // Condition 2: Rent > ₹1,00,000 with no landlord PAN (Phase 1.5 enhancement)
```

`taxplanning/engine/HraExemptionCalculator.java:84-101` — the whole unused `calculate` method:

```java
    public long calculate(long hraReceived, long monthlyRent, boolean isMetro) {
        // Basic + DA from profile would be needed for accurate calculation
        // Using simplified approach: min of all three components
        // For proper calculation, we need actual basic salary which is in profile components

        long annualRent = monthlyRent * MONTHS_PER_YEAR;
        long annualHra = hraReceived;

        // Return HRA received as exemption (simplified)
        // Full implementation would compute: min(hra, rent - 10%basic, 50%/40% of basic)
        if (annualHra == 0) {
            return 0L;
        }

        // Placeholder: return HRA received up to rent paid
        // Proper implementation requires basic salary component from profile
        return Math.min(annualHra, annualRent);
    }
```

`taxplanning/engine/OldRegimeTaxEngine.java:279-281` — an empty conditional:

```java
        if (hraResult.getWarnings() != null) {
            // warnings are handled in computeWarnings
        }
```

`taxplanning/engine/NewRegimeTaxEngine.java:155`:

```java
        // New regime has NO additional Chapter VI-A deductions (80C, 80D, etc.)
```

`taxplanning/engine/NewRegimeTaxEngine.java:184-185`:

```java
        // Rebate limit comes from slabPolicy (inherited compute() method handles it via slabPolicy.getRebate87aLimit())
        // We just check if taxable income exceeds typical rebate limit for warning purposes
```

In the tests, `HraExemptionCalculatorTest.java:17-48` is 32 lines of unresolved reasoning left in
place as comments. Verbatim extract (`:22-39`):

```java
    // min(2,40,000, 2,50,000, 1,50,000) = 1,50,000 (cap3 binding)
    // Wait, test name says cap2 (50% of basic) is binding. Let me re-read...
    // Actually cap3 = rent - 10% of basic. With high rent (2L), cap3 = 1.5L and actual HRA = 2.4L.
    // min(2.4L, 2.5L, 1.5L) = 1.5L → cap3 (rent - 10% basic) is binding, not cap2.
    // The test name says "cap2" but the scenario I described has cap3 binding.
    // Let me use a scenario where 50% of basic is truly the minimum.
    // For cap2 (50% basic) to be binding: cap1 = actual HRA, cap2 = 50% basic, cap3 = rent - 10% basic
    // We need: 50% basic < actual HRA AND 50% basic < rent - 10% basic
    // For metro, basic = 5L, actual HRA = 1.8L, rent = 1.2L
    // cap1 = 1.8L, cap2 = 2.5L, cap3 = 1.2L - 0.5L = 0.7L
    // min(1.8L, 2.5L, 0.7L) = 0.7L → cap3 binding again
    // For cap2 (50% basic) to be binding:
    // actualHRA > 50%*basic AND rent - 10%*basic > 50%*basic
    // → rent > 60%*basic → rent > 60% of 5L = 3L
    // With rent = 3L, actualHRA = 2.5L, basic = 5L:
    // cap1 = 2.5L, cap2 = 2.5L, cap3 = 3L - 0.5L = 2.5L
    // min(2.5L, 2.5L, 2.5L) = 2.5L → actual HRA is binding
    // Hmm this is tricky. Let me re-think.
```

`MarginalReliefCalculatorTest.java:71-77`:

```java
        long rebateLimit = 50_00_000L; // This isn't the right method for surcharge

        // applyMarginalRelief is different from computeRebateMarginalRelief
        // Let's test computeRebateMarginalRelief at the 50L boundary
        // But rebateLimit for surcharge relief is the surcharge threshold itself
```

`MarginalReliefCalculatorTest.java:93-96`:

```java
    // ---- Test 4: noRelief_whenIncomeBelowLimit ----
    // When taxableIncome < rebateLimit, the engine's condition maxTaxAtLimit < computedTax
    // evaluates to (-1L) < 50K = true, so relief is applied (this is a known edge case)
    // In practice, the old regime code would have returned 0 tax via rebate before reaching here
```

`NewRegimeTaxEngineTest.java:279` and `OldRegimeTaxEngineTest.java:196` — comments recording that
the test does not exercise what its name says:

```java
        assertEquals(0L, result.getSurcharge()); // taxable 51L < 5Cr threshold
```

```java
        assertEquals(0L, result.getSurcharge()); // taxable 50.25L < 5Cr threshold
```

---

## 5. Observations

Recorded, not judged and not acted on. No assessment is made of legal correctness — these are
places where the code's behaviour differs from what an adjacent comment, name, test or data file
asserts, or where a code path cannot be reached.

### O-1 — The seeded `limitFormula` strings do not match the evaluator's syntax

`FormulaEvaluator` parses with `SpelExpressionParser` and binds variables via
`context.setVariable` (`engine/FormulaEvaluator.java:29,43-44`). SpEL reads such variables as
`#name`, which `FormulaEvaluatorSandboxTest.java:42,48` demonstrates (`"#basic * 10 / 100"` →
`50000`). SpEL has no built-in `min` function.

The formulas seeded into `allowance_limits` use neither convention
(`allowance-limits-2025-26.json:2,10-15`):

```
min(0.14 * (basic + da), actual_nps)
min(actual_investment, 150000)
```

Both engines pass these strings to `formulaEvaluator.evaluate(...)` inside a try/catch that returns
`0L` on any exception (`OldRegimeTaxEngine.java:339-344`, `NewRegimeTaxEngine.java:207-212`), and
the 80CCD(2) block then computes `Math.min(employerNps, npsCap)` (`OldRegimeTaxEngine.java:108`,
`NewRegimeTaxEngine.java:92`).

**Inferred, not executed:** if `evaluate` throws on these strings, `npsCap` is `0` and the employer
NPS exemption is always `0`. Every unit test mocks `AllowanceResolutionService` to return a limit
with a **null** `limitFormula` (`NewRegimeTaxEngineTest.java:362-380`,
`OldRegimeTaxEngineTest.java:250-269`), which takes the `formula == null` early return of
`Long.MAX_VALUE` (`NewRegimeTaxEngine.java:200-202`) instead — so no test exercises a real formula.
`UNKNOWN — the runtime outcome was not executed in this pass; a reviewer should run
`new FormulaEvaluator().evaluate("min(0.14 * (basic + da), actual_nps)", Map.of("basic",1L,"da",1L,"actual_nps",1L))`
to confirm.`

The same mismatch affects `RestructuringEngine.java:117`.

### O-2 — `MarginalReliefCalculator` is not wired into anything

Three public methods, seven passing tests, and **no production caller** — `grep -rn
"MarginalReliefCalculator"` across `backend/src` matches only the class and its test. The relief
that actually runs is the inline `AbstractTaxEngine.computeMarginalRelief` (`:175-182`), a different
implementation.

### O-3 — Surcharge thresholds are ₹5 crore; the Javadoc says ₹50 lakh

`NewRegimeTaxEngine.java:29` and `OldRegimeTaxEngine.java:33` both state
`- Surcharge from ₹50L onwards`. The seeded first band is `"fromAmount": 50000000`
(`slab-policy-2025-26-new.json:20`, `slab-policy-2025-26-old.json:17`) — 5,00,00,000. Two tests
named for surcharge assert `0` and carry comments acknowledging it
(`NewRegimeTaxEngineTest.java:279`, `OldRegimeTaxEngineTest.java:196`).

### O-4 — `tax_year_registry` is modelled and never used

`TaxYearRegistryEntity` declares three unique indexes and nine fields including `startDate`,
`endDate`, `governingAct`, `governingRules`, `defaultRegime` (`policy/entity/TaxYearRegistryEntity.java:24-55`).
Neither the seeder nor any service reads or writes it. It is nevertheless listed as a protected
seeded collection in `AbstractIntegrationTest.java:47`.

### O-5 — HRA warnings are computed and discarded

`HraExemptionCalculator.computeHraExemption` returns warnings (`:52,58`). The caller fetches them
into an empty `if` body (`OldRegimeTaxEngine.java:279-281`) whose comment says *"warnings are
handled in computeWarnings"*. `OldRegimeTaxEngine.computeWarnings` (`:232-253`) re-derives a
similar warning from the profile, using `1_00_000` compared against `monthlyRent * 12` — but guards
it with `!Boolean.TRUE.equals(profile.getIsPayingRent())` (`:248`), so the landlord-PAN warning
fires only when the user is **not** paying rent, which is the case in which no HRA exemption arises.

### O-6 — Three entity fields have no production writer

`esopPresent`, `sourceText`, `parsedByAi` (`salary/entity/SalaryProfileEntity.java:111-118`) are
absent from `SalaryProfileRequest` and never set by `SalaryProfileService`. Both engines branch on
`esopPresent` to emit an ESOP warning (`NewRegimeTaxEngine.java:168-170`,
`OldRegimeTaxEngine.java:239-241`), so that warning cannot fire in production.

### O-7 — The catalogue and the limits use different codes for the same sections

`allowance_limits` names them `80C`, `80D`, `80CCD_1B`, `24B`; `allowance_catalogue` names them
`SECTION_80C`, `SECTION_80D`, `NPS_SELF`, `SECTION_24`. `RestructuringEngine` intersects the two
sets (`:75-86` with `AllowanceResolutionService.java:53-58`), so none of those four can ever appear
as a restructuring recommendation. With the seeded data the eligible set reduces to
`CHILDREN_EDUCATION`, `HOSTEL_ALLOWANCE`, `NPS_EMPLOYER` (old regime) and `NPS_EMPLOYER` (new).

### O-8 — A GET endpoint recomputes and persists

`GET /user/{email}/profile/{profileId}/document/tax-report` calls
`taxComputationService.compute(...)` (`TaxPlanningController.java:93`), which writes to
`tax_computations` (`TaxComputationService.java:85`). `TaxComputationService.getComputation`
(`:92-100`) — the read-only accessor — has **no caller**.

### O-9 — `computeGrossSalary` unboxes without a null guard

`AbstractTaxEngine.java:141` uses `.mapToLong(SalaryComponentEntity::getAnnualAmount)` on a
`Long` field. `getComponentAmount` at `:232` guards the same field with
`c.getAnnualAmount() != null ? ... : 0L`. `SalaryProfileRequest` permits a null `annualAmount` —
`validateRequest` only checks `component.getAnnualAmount() != null && < 0`
(`SalaryProfileService.java:202`). `RestructuringEngine.java:190` has the same unguarded unboxing.

### O-10 — Children-education and hostel exemptions divide by a possibly-zero value

`OldRegimeTaxEngine.java:306`: `(int)(eduLimit.getAnnualLimitFixed() / edMonthly / 12)` where
`edMonthly = getOrDefault(eduLimit.getMonthlyLimitFixed(), 0L)` (`:304`). When `annualLimitFixed` is
positive and `monthlyLimitFixed` is null, this is an integer divide by zero. Same shape at `:324`.
Neither path has a test.

### O-11 — Marginal relief runs for the old regime too

`AbstractTaxEngine.computeMarginalRelief` (`:175-182`) is in the shared template and keys off
`rebate87aLimit`, which is `500000` for the old regime (`slab-policy-2025-26-old.json:7`). Nothing
in the code restricts it to the new regime. No test covers the old-regime case.

### O-12 — `OldRegimeTaxEngine.getStandardDeduction` unboxes a nullable field

`OldRegimeTaxEngine.java:66`: `Math.min(slabPolicy.getStandardDeduction(), gross)` — no null guard.
`NewRegimeTaxEngine.java:58` guards the identical call with `getOrDefault(..., 75_000L)`.
Integration test 13 (`TaxPlanningControllerIntegrationTest.java:462-490`) POSTs a slab policy with
no `slabs` list, showing that partially-populated policies are accepted by the admin endpoint.

### O-13 — `netTakeHome` includes notional perquisites

`gross` includes annualised car and driver perquisite values (`AbstractTaxEngine.java:143-145`),
which are not cash. `netTakeHome = gross - employeePf - professionalTax - totalTax` (`:102`)
therefore includes them.

### O-14 — Slab bands lose one rupee each

Seeded bands start at `previous_to + 1` (e.g. `400001`, `slab-policy-2025-26-new.json:12`), while
`computeProgressiveTax` computes `Math.min(taxableIncome, to) - from`
(`AbstractTaxEngine.java:157`). For income at or above a band ceiling the taxable width is one rupee
short. `Math.round` is applied per band and the results summed (`:159`), rather than once at the
end.

### O-15 — The integration fixture misspells `EMPLOYER_PF`

`TaxPlanningControllerIntegrationTest.java:68` — `component("EMPLOR_PF", 72000)`, sitting directly
below `component("EMPLOYEE_PF", 72000)` at `:67`. The engines look up `"EMPLOYER_PF"`
(`NewRegimeTaxEngine.java:71`, `OldRegimeTaxEngine.java:87`), so the employer-PF exemption path is
never exercised by any integration test; the amount is nevertheless summed into gross by
`computeGrossSalary`, which sums every component regardless of code. The second fixture in the same
file (`:297-298`, used by `restructure_success`) omits the misspelled component altogether, so the
two fixtures build different CTCs.

### O-16 — Integration test 13 mutates seeded reference data for the rest of the run

`admin_createSlabPolicy_superUserOnly` (`TaxPlanningControllerIntegrationTest.java:462-490`) POSTs an
ACTIVE `NEW_REGIME` slab policy for `"2025-26"` with `rebate87aLimit = 700000`,
`rebate87aAmount = 25000` and **no `slabs`**. `PolicyService.createSlabPolicy` marks the existing
ACTIVE policy `SUPERSEDED` (`policy/service/PolicyService.java:49-53`). `tax_slab_policies` is in
`SEEDED_REFERENCE_COLLECTIONS` (`AbstractIntegrationTest.java:46`), so it is **not** cleaned between
tests. Tests run in parallel by class (per CLAUDE.md) with `AbstractIntegrationTest` marked
`@Isolated`, so ordering within the class matters: any test running after this one in the same
Spring context sees the replaced policy.

### O-17 — `updateAllowance` silently discards most of the request body

`policy/service/PolicyService.java:69-78` copies only `annualLimitFixed`, `monthlyLimitFixed`,
`description` and `status` onto the existing document. The endpoint accepts a full
`AllowanceCatalogueEntity` (`TaxPlanningController.java:115`), including `limitFormula`, which is
dropped. `FormulaEvaluator`'s Javadoc names this endpoint as the route by which an expression
"may have arrived over the wire" (`engine/FormulaEvaluator.java:20-21`).

### O-18 — Seeding failures are logged and swallowed

All four `seed*` methods catch `Exception` and `log.error` without rethrowing
(`PolicySeederService.java:66-68, 84-86, 111-113, 134-136`). The application starts regardless;
the first `compute` call then fails with `"No active tax slab policy found for year=..."`
(`PolicyService.java:29-30`) — a 400, not a startup failure.

### O-19 — Classes and methods with no production caller

| Item | Evidence |
|---|---|
| `engine/FbpOptimizer.java` (whole class, 7 public methods) | grep finds only the file |
| `engine/ItrFormAdvisor.java` (whole class) | grep finds only the file |
| `engine/MarginalReliefCalculator.java` (whole class) | O-2 |
| `recommendation/HrLetterContentBuilder.java` (whole class) | grep finds only the file |
| `parsing/ParsedCtcDto.java` (whole class, 45 fields) | grep finds only the file |
| `HraExemptionCalculator.calculate` (`:84-101`) | no caller |
| `HraExemptionCalculator.calculateFull` (`:106-121`) | no caller |
| `HraExemptionCalculator.DAYS_PER_MONTH` (`:14`), `METRO_CITIES` (`:16-19`) | unreferenced fields |
| `PerquisiteValuationService.calculateCarPerquisite` (`:18-43`) | no caller |
| `PerquisiteValuationService.calculateDriverPerquisite` (`:48-59`) | no caller |
| `PerquisiteValuationService.calculateMealPerquisite` (`:65-76`) | no caller |
| `AbstractTaxEngine.hasComponent` (`:236-242`) | no caller |
| `TaxEngine.getSupportedTaxYear` (`:15`) | declared and overridden twice; never invoked |
| `TaxComputationService.getComputation` (`:92-100`) | O-8 |
| `AllowanceCatalogueRepository.findByCodeAndTaxYearAndRegimeTypeAndStatus` (`:20`), `findByTaxYearAndRegimeTypeAndStatus` used only by the public controller | partial |
| `TaxSlabPolicyEntity.basicExemptionLimit`, `SurchargeSlab.marginalReliefApplicable` | stored and seeded, never read |
| `PerquisitePolicyEntity` — 6 fields | `02-domain-and-api.md` §2.4 |
| `AllowanceLimitEntity` — `financialYear`, `assessmentYear`, `notifiedDate`, `requiresDocumentation`, `requiresEmployerAction`, `sourceReference` | no reader |
| `SalaryComponentEntity.isCurrent` | read only for DTO copying (`SalaryProfileResponse.java:105`) |
| `SalaryProfileEntity.cityTier`, `isPhysicallyDisabled`, `ltaBlockStart`, `ltaTripsClaimedInBlock`, `ltaCarryForwardAvailable` | stored, never read by an engine |
| `CarOwnership.LEASED` | never branched on (`PerquisiteValuationService.java:102`) |

### O-20 — The PDF's restructuring sections are unreachable

`TaxPlanningController.java:95` passes `null` for `restructuring`; the template guards every
restructuring block on `th:if="${restructuring != null}"`
(`templates/taxplanning/tax-comparison-report.html:119`). `DocumentGenerationService` accepts the
parameter (`:26`) and no caller supplies a non-null value.

### O-21 — `annualSavingVsCurrent` does not consider the current regime

The field name says "vs current" (`TaxComputationEntity.java:44-45`); the value is
`Math.abs(oldResult.getTotalTax() - newResult.getTotalTax())`
(`TaxComputationService.java:69`) — the gap between the two regimes. `profile.getRegimeType()` is
not consulted. The PDF surfaces this figure as *"Estimated Annual Saving"*
(`tax-comparison-report.html:114`).

### O-22 — No coverage gate on the module, though PIT targets it

`pom.xml:250,268` scope both JaCoCo rules to `brokercharges`, so no line- or branch-coverage
threshold applies to `taxplanning`. PIT does cover it: `pom.xml:399` lists
`com.thiru.wealthlens.taxplanning.engine.*` as a target class pattern, `pom.xml:403` lists
`com.thiru.wealthlens.taxplanning.*` as a target test pattern, `<excludedGroups>integration</excludedGroups>`
(`pom.xml:405-407`) drops the integration tier, and `<mutationThreshold>85</mutationThreshold>`
(`pom.xml:408`) applies to the whole PIT run — which mixes `brokercharges` and `taxplanning`
classes, so a single aggregate score gates both. The mutation profile is not bound to a phase; it
runs only via `-Pmutation` (per CLAUDE.md).

### O-23 — Restructuring always prices at the new-regime marginal rate

`RestructuringEngine.java:135` — `getMarginalRate(originalNewResult.getTaxableIncome(),
currentSlabPolicy)`. `currentSlabPolicy` is the *current regime's* slab table (`:92`) but the income
argument is always the new-regime result, even when `currentRegime == OLD_REGIME`.

### O-24 — Two different formula vocabularies

Engines supply `basic`, `da`, `actual_nps` (`OldRegimeTaxEngine.java:336-338`,
`NewRegimeTaxEngine.java:204-206`). `RestructuringEngine.buildFormulaVariables` supplies `basic`,
`da`, `basic_plus_da`, `rent_paid`, `hra_received`, `actual_hra` (`:204-215`) — **without
`actual_nps`**, which the only seeded formula references. `FormulaEvaluator` also exposes a third,
fixed vocabulary — `basic`, `da`, `rentPaid`, `hraReceived` (`:49-55`) — whose overload has no
caller in the module.

### O-25 — `computePerquisiteValue` switches on a code that does not exist

`RestructuringEngine.java:230` matches `"CAR_COMPANY"`. The catalogue's car entry is
`CAR_PERQUISITE` (`allowance-catalogue-2025-26.json`). `"CAR_COMPANY"` appears nowhere else in the
repository. The `amount` parameter of that method is unused.

### O-26 — `createProfile` and `updateProfile` treat null rent differently

Create: `entity.setMonthlyRentPaid(request.getMonthlyRentPaid() != null ? request.getMonthlyRentPaid() : 0L)`
(`SalaryProfileService.java:68`). Update: `existing.setMonthlyRentPaid(request.getMonthlyRentPaid())`
(`:123`) — a null passes through. `OldRegimeTaxEngine.java:269` guards with `getOrDefault(..., 0L)`,
so the difference does not propagate into the computation, but it does into the stored document and
the response.

### O-27 — `computeWarnings` can abort the whole computation

`NewRegimeTaxEngine.computeWarnings:174-177`:

```java
Double npsRate = resolveRatePercent("NPS_EMPLOYER", taxYear, getRegime(), employer);
if (npsRate == null) {
    throw new IllegalArgumentException("No active policy for NPS_EMPLOYER in taxYear=" + taxYear);
}
```

This is step 13 of 13 (`AbstractTaxEngine.java:105`), after every figure has been computed. A
missing NPS policy row therefore fails the request rather than producing a result without that
warning. Note also `resolveRatePercent` returns `null` on *any* exception (`:274-282`), so the
message may not describe the actual cause.

### O-28 — Two Act citations coexist

Everything in the catalogue and the slab policies cites the Income-tax Act 1961 numbering
(`slab-policy-2025-26-new.json:5`, `perquisite-policy-2025-26.json:4`, all 39 `itSection` values).
`HrLetterContentBuilder.java:16` writes *"under the Income Tax Act 2025 and IT Rules 2026"*. That
class has no production caller (O-19).

### O-29 — No Bean Validation anywhere in the module

`SalaryProfileRequest` carries no constraint annotations and no controller method uses `@Valid`
(`TaxPlanningController.java:49,64`). `taxYear` is never validated in any form; `allowanceCode` is
never checked against the catalogue.

### O-30 — Entities are returned directly on public and authenticated endpoints

`TaxPlanningPublicController.java:25,35` return `AllowanceCatalogueEntity` unauthenticated, including
`auditMetadata` and `_id`. `TaxPlanningController.java:77,103,109,115` return
`TaxComputationEntity` / `TaxSlabPolicyEntity` / `PerquisitePolicyEntity` / `AllowanceCatalogueEntity`.

### O-31 — The metro city list is duplicated

`SalaryProfileResponse.java:53-56` (used) and `HraExemptionCalculator.java:16-19` (declared,
unreferenced) hold the same eight-city list. `CityTier.METRO_8` exists as a third representation and
is never read.

### O-32 — Public catalogue lookup filters in memory

`TaxPlanningPublicController.java:39-46` loads the full catalogue for a year and filters by code in
a stream. `AllowanceCatalogueRepository.findByCodeAndTaxYear` (`:16`) exists and is unused.

### O-33 — Catalogue resolution ignores the tax year

`AllowanceResolutionService.java:30` calls `findByCodeAndStatus(allowanceCode, ACTIVE)` — no
`taxYear`. Once a second tax year is seeded, the `metadata` half of `ResolvedAllowance` resolves to
whichever document Mongo returns first.

### O-34 — `effectiveDate` orders the resolution queries and is never populated

All three limit queries end `OrderByEffectiveDateDesc`
(`AllowanceLimitRepository.java:13,16,19`). No seeded row sets `effectiveDate`
(`allowance-limits-2025-26.json`), and no code writes it.

### O-35 — `TaxEngineFactory.getEngine` is bypassed by the main flow

`TaxComputationService` calls `getNewRegimeEngine()` / `getOldRegimeEngine()` directly
(`:55,60`), never `getEngine(regimeType)`. The `NOT_DECIDED` guard in `getEngine`
(`TaxEngineFactory.java:20`) therefore does not protect the compute path, and a profile saved with
`RegimeType.NOT_DECIDED` — which `createProfile` defaults to (`SalaryProfileService.java:56`) —
computes without error. `RestructuringEngine` does use `getEngine` (`:53,57`) but coerces
`NOT_DECIDED` away first by defaulting a null `regimeType` to `NEW_REGIME` (`:44-45`) — a null
check, not a `NOT_DECIDED` check.

### O-36 — Redundant local aliases and shadowed fields

`OldRegimeTaxEngine.java:176,189,202,215` each assign `long effectiveXxxLimit = sectionXxxLimit;`
and then use the alias once. `OldRegimeTaxEngine.java:42,51` and `NewRegimeTaxEngine.java:36,43`
declare a `perquisiteValuation` field that shadows the `protected` field of the same name on
`AbstractTaxEngine.java:29`, which the superclass constructor is already given (`:49`, `:42`).
