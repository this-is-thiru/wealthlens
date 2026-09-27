# 08 — Golden tests

**`D-GOLDEN`: every expected value below is derived from `01-rulebook.md` by arithmetic shown step by
step. None was produced by running code.** O-3 is the reason: the placeholder had two surcharge tests
asserting `0` with comments acknowledging the value was wrong, because the expectations came from the
implementation.

**`PROVISIONAL`** marks a case that depends on a rule with status `NEEDS VERIFICATION`. It still runs
and still asserts, but a failure is reported as a **rule-verification** failure, not an engine
regression (`X5.1`).

**Rounding convention used throughout** — `RATE-ROUND-002` (s.516): ignore paise, then round to the
nearest multiple of ₹10, up if the last figure is 5 or more, down if less.

**Slab abbreviations.** New regime (`RATE-SLAB-NEW-2025` = `RATE-SLAB-NEW-2026`, identical): the first
six bands of ₹4,00,000 each at Nil/5/10/15/20/25%, then 30%. Cumulative tax at each ceiling:

| Income | 4L | 8L | 12L | 16L | 20L | 24L |
|---|---|---|---|---|---|---|
| cumulative tax | 0 | 20,000 | 60,000 | 1,20,000 | 2,00,000 | 3,00,000 |

Old regime under 60 (`RATE-SLAB-OLD-2025-A` = `RATE-SLAB-OLD-2026-A`): cumulative ₹12,500 at ₹5,00,000
and ₹1,12,500 at ₹10,00,000.

---

## T1 — Slab and rebate boundaries

Applies identically to **TY 2025-26 and TY 2026-27** — `RATE-SLAB-NEW-2025` ≡ `RATE-SLAB-NEW-2026`,
`RATE-SLAB-OLD-2025` ≡ `RATE-SLAB-OLD-2026`, `REBATE-NEW-001` and `REBATE-OLD-001` unchanged. **All
`VERIFIED`.** Each case is run four times: (2025-26, 2026-27) × (the regime named).

### T1-N1 — New regime, at the rebate ceiling. TI ₹12,00,000

```
RATE-SLAB-NEW: 0–4L Nil                                       =        0
               4L–8L  @5%   4,00,000 × 0.05                   =   20,000
               8L–12L @10%  4,00,000 × 0.10                   =   40,000
                                                     tax      =   60,000
REBATE-NEW-001: TI ≤ 12,00,000 → min(60,000 , 60,000)         =   60,000
tax after rebate                                              =        0
RATE-CESS-001: 4% × 0                                         =        0
RATE-ROUND-002                                                =        0
```
**Expected: ₹0.** Status `VERIFIED`.

### T1-N2 — New regime, one rounding step above. TI ₹12,00,010

```
tax = 60,000 + 10 × 0.15                                      = 60,001.50
REBATE-NEW-001 unavailable: TI > 12,00,000
REBATE-NEW-MR-001: excess = 12,00,010 − 12,00,000             =       10
                   tax (60,001.50) > excess (10)  → relief applies
                   rebate = 60,001.50 − 10                    = 59,991.50
REBATE-NEW-MR-002 cap: 59,991.50 ≤ 60,001.50                  →      ok
tax after rebate                                              =    10.00
cess 4% × 10                                                  =     0.40
                                                     total    =    10.40
RATE-ROUND-002: ignore paise → 10; already a multiple of 10   =       10
```
**Expected: ₹10.** `INFERENCE`: ₹10 more income costs ₹10 more tax — the relief band's effective
marginal rate is exactly 100%. Status `VERIFIED`.

### T1-N3 — New regime, well inside the relief band. TI ₹12,50,000

```
tax = 60,000 + 50,000 × 0.15                                  =   67,500
excess = 50,000 ; 67,500 > 50,000 → rebate = 67,500 − 50,000  =   17,500
tax after rebate                                              =   50,000
cess 4%                                                       =    2,000
                                                     total    =   52,000
```
**Expected: ₹52,000.** Status `VERIFIED`.

### T1-N4 — New regime, the crossover where `REBATE-NEW-MR-001` stops binding

Relief binds while `60,000 + 0.15x > x`, i.e. `x < 60,000 / 0.85 = 70,588.24`, where
`x = TI − 12,00,000`.

```
TI = 12,70,580 :  x = 70,580
  tax = 60,000 + 70,580 × 0.15                                = 70,587.00
  70,587.00 > 70,580 → rebate = 7.00                          =     7.00
  tax after rebate                                            = 70,580.00
  cess 4%                                                     =  2,823.20
  total                                                       = 73,403.20
  round: → 73,403 → last figure 3 < 5 → down                  =   73,400

TI = 12,70,590 :  x = 70,590
  tax = 60,000 + 70,590 × 0.15                                = 70,588.50
  70,588.50 > 70,590 ?  NO  → rebate = 0
  tax after rebate                                            = 70,588.50
  cess 4%                                                     =  2,823.54
  total                                                       = 73,412.04
  round: → 73,412 → last figure 2 < 5 → down                  =   73,410
```
**Expected: ₹73,400 and ₹73,410.** `INFERENCE`: this pair pins the exact step at which the relief
stops applying — the highest-value single assertion in T1, because an implementation that applies
relief unconditionally above ₹12,00,000 passes T1-N2 and T1-N3 and fails only here. Status `VERIFIED`.

### T1-O1 — Old regime, at the rebate ceiling. TI ₹5,00,000, age under 60

```
RATE-SLAB-OLD-A: 0–2.5L Nil                                   =        0
                 2.5L–5L @5%  2,50,000 × 0.05                 =   12,500
REBATE-OLD-001: TI ≤ 5,00,000 → min(12,500 , 12,500)          =   12,500
tax after rebate                                              =        0
cess                                                          =        0
```
**Expected: ₹0.** Status `VERIFIED`.

### T1-O2 — Old regime, one rounding step above. TI ₹5,00,010 — **the `H-3` test**

```
tax = 12,500 + 10 × 0.20                                      =   12,502
REBATE-OLD-001: TI > 5,00,000 → unavailable                   =        0
REBATE-NEW-MR-001: DOES NOT APPLY.  s.156(2) opens
   "where the total income ... is chargeable to tax under
    section 202(1)" — the new regime.  The old-regime limb,
    s.156(1), has no marginal-relief clause at all.
tax after rebate                                              =   12,502
cess 4%                                                       =   500.08
                                                     total    = 13,002.08
round: → 13,002 → last figure 2 < 5 → down                    =   13,000
```
**Expected: ₹13,000.** `INFERENCE`: ₹10 more income costs ₹13,000 more tax. That cliff **is** the old
regime, and an implementation that shares a marginal-relief step across regimes (O-11) returns roughly
₹10 here and passes every other case in T1. Status `VERIFIED`.

### T1-O3 — Old regime, ₹50,000 above. TI ₹5,50,000

```
tax = 12,500 + 50,000 × 0.20                                  =   22,500
rebate                                                        =        0
cess 4%                                                       =      900
                                                     total    =   23,400
```
**Expected: ₹23,400.** Status `VERIFIED`.

### T1-A1 — Old regime, age variants at the basic exemption

`RATE-SLAB-OLD-2026-B` (resident, 60 to under 80) and `-C` (resident, 80+). **The new regime has no
age variants (`RATE-SLAB-AGE-002`)** — the same TI must produce the same tax at any age.

| Case | Age | Regime | TI | Working | Expected |
|---|---|---|---|---|---|
| T1-A1a | 45 | OLD | ₹3,00,000 | 50,000 × 5% = 2,500; rebate 2,500; cess 0 | **₹0** |
| T1-A1b | 65 | OLD | ₹3,00,000 | below ₹3,00,000 threshold ⇒ tax 0 | **₹0** |
| T1-A1c | 82 | OLD | ₹5,00,000 | below ₹5,00,000 threshold ⇒ tax 0 | **₹0** |
| T1-A1d | 82 | OLD | ₹5,00,010 | 10 × 20% = 2; rebate 0 (TI > 5,00,000); cess 0.08; total 2.08 → 2 → **down to 0** | **₹0** |
| T1-A1e | 82 | **NEW** | ₹5,00,000 | `RATE-SLAB-AGE-002`: no age variant, so the ₹4,00,000 band applies. 1,00,000 × 5% = 5,000; `REBATE-NEW-001` min(5,000, 60,000) = 5,000 | **₹0** |
| T1-A1f | 82 | **NEW** | ₹13,00,000 | 60,000 + 1,00,000×15% = 75,000; excess 1,00,000; 75,000 > 1,00,000? **no** → rebate 0; cess 3,000 | **₹78,000** |

`INFERENCE`: T1-A1e and T1-A1f are the guard against applying an old-regime age table in the new
regime. Status `VERIFIED`.

---

## T2 — Surcharge entry and marginal relief

All `VERIFIED` — the surcharge tables and both marginal-relief formulae were read directly from the
Finance Act 2026. Run for both years; the tables are identical.

### T2-1 — New regime, at the surcharge threshold. TI ₹50,00,000

```
RATE-SLAB-NEW cumulative at 24,00,000 (see the table above)   = 3,00,000
24,00,000 → 50,00,000 @30% : 26,00,000 × 0.30                 = 7,80,000
                                                     tax      =10,80,000
RATE-SUR-NEW-2026: TI does not EXCEED 50,00,000 → no surcharge=        0
cess 4% × 10,80,000                                           =   43,200
                                                     total    =11,23,200
```
**Expected: ₹11,23,200.**

### T2-2 — New regime, one rounding step above. TI ₹50,00,010 — **the `H-1` test**

```
tax = 10,80,000 + 10 × 0.30                                   =10,80,003
RATE-SUR-NEW-2026: exceeds 50,00,000 → 10%
  surcharge = 0.10 × 10,80,003                                = 1,08,000.30
  unrelieved tax + surcharge                                  =11,88,003.30

RATE-SUR-MR-001, threshold C = 50,00,000 :
  U = tax + surcharge at exactly 50,00,000 = 10,80,000 + 0    =10,80,000
  V = 50,00,010 − 50,00,000                                   =       10
  ceiling W = U + V                                           =10,80,010
  11,88,003.30 > 10,80,010 → relief = 1,07,993.30
  tax + surcharge after relief                                =10,80,010

cess 4% × 10,80,010                                           = 43,200.40
                                                     total    =11,23,210.40
round: → 11,23,210 ; already a multiple of 10                 =11,23,210
```
**Expected: ₹11,23,210.** `INFERENCE`: ₹10 more income costs ₹10.40 more tax. Without
`RATE-SUR-MR-001` it would cost ₹1,12,323 — the cliff H-1 describes. Status `VERIFIED`.

### T2-3 — New regime, the crossover where surcharge relief stops binding

Relief binds while `1.10 × (10,80,000 + 0.30x) > 10,80,000 + x`, i.e. `1,08,000 > 0.67x`, i.e.
`x < 1,61,194.03`, where `x = TI − 50,00,000`.

```
TI = 51,61,190 :  x = 1,61,190
  tax = 10,80,000 + 1,61,190 × 0.30                           =11,28,357
  surcharge 10%                                               = 1,12,835.70
  unrelieved                                                  =12,41,192.70
  ceiling = 10,80,000 + 1,61,190                              =12,41,190
  12,41,192.70 > 12,41,190 → relief = 2.70                    →  binds
  tax + surcharge                                             =12,41,190
  cess 4%                                                     =   49,647.60
  total                                                       =12,90,837.60
  round: → 12,90,837 → last figure 7 ≥ 5 → up                  =12,90,840

TI = 51,61,200 :  x = 1,61,200
  tax = 10,80,000 + 1,61,200 × 0.30                           =11,28,360
  surcharge 10%                                               = 1,12,836
  unrelieved                                                  =12,41,196
  ceiling = 10,80,000 + 1,61,200                              =12,41,200
  12,41,196 > 12,41,200 ?  NO → no relief
  tax + surcharge                                             =12,41,196
  cess 4%                                                     =   49,647.84
  total                                                       =12,90,843.84
  round: → 12,90,843 → last figure 3 < 5 → down                =12,90,840
```
**Expected: ₹12,90,840 for both.** `INFERENCE`: the two agree after rounding, which is itself the
assertion — the relief and the no-relief branches must **meet continuously** at the crossover.
A discontinuity here means the ladder's threshold is wrong. Status `VERIFIED`.

### T2-4 — The regime divergence at ₹5,00,00,000 — **the "no 37% band" test**

```
OLD REGIME  (RATE-SLAB-OLD-2026-A, RATE-SUR-OLD-2026)
  tax = 1,12,500 + (5,00,00,000 − 10,00,000) × 0.30
      = 1,12,500 + 4,90,00,000 × 0.30                         =1,48,12,500
  surcharge: exceeds 2,00,00,000, does not exceed 5,00,00,000 → 25%
      0.25 × 1,48,12,500                                      =  37,03,125
  tax + surcharge                                             =1,85,15,625
  RATE-SUR-MR-001 threshold 2,00,00,000 :
      U = tax+sur at 2,00,00,000
        = [1,12,500 + 1,90,00,000 × 0.30] = 58,12,500 ; surcharge at exactly
          2,00,00,000 is 15% (does not exceed) = 8,71,875 ; U = 66,84,375
      V = 3,00,00,000 ; ceiling = 3,66,84,375 → far above → no relief
  cess 4% × 1,85,15,625                                       =   7,40,625
                                                     total    =1,92,56,250

NEW REGIME  (RATE-SLAB-NEW-2026, RATE-SUR-NEW-2026)
  tax = 10,80,000 + (5,00,00,000 − 50,00,000) × 0.30
      = 10,80,000 + 4,50,00,000 × 0.30                        =1,45,80,000
  surcharge: exceeds 2,00,00,000 → 25%.  THERE IS NO 37% BAND.
      0.25 × 1,45,80,000                                      =  36,45,000
  tax + surcharge                                             =1,82,25,000
  RATE-SUR-MR-001 threshold 2,00,00,000 : ceiling far above → no relief
  cess 4% × 1,82,25,000                                       =   7,29,000
                                                     total    =1,89,54,000
```
**Expected: ₹1,92,56,250 (old) and ₹1,89,54,000 (new).** Status `VERIFIED`.

### T2-5 — Old regime, the 37% band entry. TI ₹5,00,00,010

```
tax = 1,48,12,500 + 10 × 0.30                                 =1,48,12,503
RATE-SUR-OLD-2026: exceeds 5,00,00,000 → 37%
  surcharge = 0.37 × 1,48,12,503                              =  54,80,626.11
  unrelieved                                                  =2,02,93,129.11
RATE-SUR-MR-001, threshold C = 5,00,00,000 :
  U = tax + surcharge at exactly 5,00,00,000 (T2-4)           =1,85,15,625
  V = 10 ;  ceiling                                           =1,85,15,635
  2,02,93,129.11 > 1,85,15,635 → relief = 17,77,494.11
  tax + surcharge after relief                                =1,85,15,635
cess 4%                                                       =   7,40,625.40
                                                     total    =1,92,56,260.40
round: → 1,92,56,260 ; already a multiple of 10               =1,92,56,260
```
**Expected: ₹1,92,56,260** — exactly ₹10 above T2-4's old-regime figure. **The same income in the new
regime stays at 25%, so no 37% relief arises there at all.** Status `VERIFIED`.

### T2-6 — The 15% cap on the special-rate slice (`RATE-SUR-SPLIT-001`)

`PROVISIONAL` — depends on the `portfolio` read-model's split, and on the exact rate applicable to the
s.196/197/198 slice, which is outside this rulebook's scope (D3: capital gains are consumed, not
computed).

```
Old regime, TI ₹2,50,00,000, of which ₹50,00,000 is long-term capital gains
under s.198 taxed at a special rate, and ₹2,00,00,000 is slab income.

RATE-SUR-OLD-2026 clause (iii): TI EXCLUDING the special slice = 2,00,00,000,
  which does NOT exceed 2,00,00,000 → the 25% band is not entered.
Clause (v): TI INCLUDING the special slice exceeds 2,00,00,000 and is not
  covered by (iii) or (iv) → 15%.
Clause (vi): the rate on the tax attributable to the special slice
  shall not exceed 15%.

⇒ a single rate of 15% applies here, but the TEST is that an implementation
  which reads only clause (iii) and applies 25% to everything fails.
```
**Assertion: the surcharge rate selected is 15%, and the trace records clause (v) as the reason.**

---

## T3 — HRA

### T3-1 — TY 2026-27, Bengaluru, old regime. `VERIFIED`

Reproduced from `01-rulebook.md` R9. Basic ₹12,00,000; DA ₹1,20,000 (terms provide); HRA ₹6,00,000;
rent ₹30,000/month for 12 months.

```
SAL-HRA-004 base = 12,00,000 + 1,20,000                       =13,20,000
limb (a) received                                             = 6,00,000
limb (b) 3,60,000 − 10% × 13,20,000 = 3,60,000 − 1,32,000     = 2,28,000
limb (c) SAL-HRA-METRO-2026: Bengaluru is in the 50% list
         0.50 × 13,20,000                                     = 6,60,000
exemption = least                                             = 2,28,000
limitedBy = "rent less 10% of salary"
```
**Expected exemption ₹2,28,000; `limitedBy` = limb (b).**

### T3-2 — TY 2025-26, Bengaluru, old regime. **`PROVISIONAL`**

Identical facts. `SAL-HRA-METRO-2025` is `NEEDS VERIFICATION`; on the inference in `01-rulebook.md` R9
the TY 2025-26 list is four cities and Bengaluru would take 40%.

```
limb (c) 0.40 × 13,20,000                                     = 5,28,000
exemption = least(6,00,000 , 2,28,000 , 5,28,000)             = 2,28,000
```
**Expected exemption ₹2,28,000 — unchanged, because limb (b) still binds.** `INFERENCE`: this case
deliberately **cannot** distinguish the two metro lists, and that is the point — it documents that the
metro percentage is only load-bearing when rent is high relative to salary. T3-3 is the case that
distinguishes them.

### T3-3 — The case where the metro list changes the answer. **`PROVISIONAL`**

Basic ₹8,00,000; no DA; HRA ₹5,00,000; rent ₹55,000/month in **Pune**.

```
base                                                          = 8,00,000
limb (a)                                                      = 5,00,000
limb (b) 6,60,000 − 80,000                                    = 5,80,000
limb (c) TY 2026-27, Pune is in SAL-HRA-METRO-2026 → 50%      = 4,00,000
         TY 2025-26, on the unverified four-city reading → 40%= 3,20,000

TY 2026-27 exemption = least(5,00,000, 5,80,000, 4,00,000)    = 4,00,000  limb (c)
TY 2025-26 exemption = least(5,00,000, 5,80,000, 3,20,000)    = 3,20,000  limb (c)
difference                                                    =   80,000
```
**Expected: ₹4,00,000 for TY 2026-27 (`VERIFIED`), ₹3,20,000 for TY 2025-26 (`PROVISIONAL`).**
`INFERENCE`: an ₹80,000 exemption difference on the same facts, purely from the year. This is `H-2`
made numeric, and it is the single clearest demonstration of why `D-YEARKEY` exists.

### T3-4 — Part-year occupation (`SAL-HRA-003`) and the new regime (`SAL-HRA-005`)

```
T3-4a  same as T3-1 but the accommodation was occupied for 7 months only.
       "Relevant period" is 7 months, so EVERY limb is computed on 7 months:
         base for the period = (12,00,000 + 1,20,000) × 7/12  = 7,70,000
         limb (a) 6,00,000 × 7/12                             = 3,50,000
         limb (b) (30,000 × 7) − 10% × 7,70,000
                  = 2,10,000 − 77,000                         = 1,33,000
         limb (c) 0.50 × 7,70,000                             = 3,85,000
       exemption                                              = 1,33,000
T3-4b  same as T3-1 but regime = NEW.
       SAL-HRA-005: Schedule III Sl. 11 is disallowed by s.202(2)(a)(i)
       exemption                                              =         0
       and the trace MUST carry a line saying so — not an absent line
```
**Expected: ₹1,33,000 and ₹0-with-a-reason.** T3-4b guards `D-NOSILENT`: a zero with a stated reason,
not a missing step.

> **Erratum (W-070, 2026-09-27, D-104).** **T3-4a:** the 7/12 above assumes the relevant period is measured in
> months; S4 r.279(2)(a) ("the period during which the said accommodation was occupied") names no unit, and the
> engine apportions by days (D-099), the one basis that splits a mid-month move exactly. Occupied 1 Apr – 31 Oct
> 2026, 214 of 365 days: salary 13,20,000 × 214/365 = 7,73,917.81; limb (b) 2,10,000 − 77,391.78 =
> **₹1,32,608.22**, which is the expected figure; months vs days is **OQ-8**. **T3-4b** stands as written: the
> engine now gives the nil line with its reason (D-104 C2a).

### T3-5 — DA excluded — **the `H-4` regression test**

Same as T3-1, but the implementation omits DA from the base.

```
wrong base = 12,00,000
limb (b) 3,60,000 − 1,20,000                                  = 2,40,000
limb (c) 0.50 × 12,00,000                                     = 6,00,000
wrong exemption                                               = 2,40,000
```
**Assertion: the correct answer is ₹2,28,000, not ₹2,40,000.** `INFERENCE`: omitting DA makes the
exemption ₹12,000 **larger**, i.e. it understates tax. Errors that reduce tax are the ones a user never
reports.

---

## T4 — PF and NPS, including the aggregate cap

### T4-1 — `PF-AGGCAP-001` bites, new regime, TY 2026-27. `VERIFIED`

Basic ₹24,00,000, no DA. Private employer. Employer PF 12%, employer NPS 14%, superannuation
₹2,00,000.

```
employer PF  0.12 × 24,00,000                                 = 2,88,000
employer NPS 0.14 × 24,00,000                                 = 3,36,000
superannuation                                                = 2,00,000
aggregate                                                     = 8,24,000
PF-AGGCAP-001 ceiling                                         = 7,50,000
excess → PERQ_EMPLOYER_CONTRIB_EXCESS, added to SALARY        =   74,000

PF-RPF12-001: employer PF is exactly 12% of salary → no further charge =  0

DED-NPS-EMP-002 (new regime reads 10% as 14%):
  limit 0.14 × 24,00,000 = 3,36,000 ; contributed 3,36,000
  deduction (survives the new regime, DED-NPS-EMP-003)        = 3,36,000
```
**Expected: ₹74,000 added to salary income *and* ₹3,36,000 deducted under Chapter VIII.**
`INFERENCE`: the same ₹3,36,000 appears on both sides. A test that asserts only a net figure cannot
tell a correct implementation from one that nets them, which is `H-6`'s failure mode.

### T4-2 — The same facts in the **old** regime

```
DED-NPS-EMP-001(b): private employer, old regime → 10% of salary
  limit 0.10 × 24,00,000                                      = 2,40,000
  contributed 3,36,000 → deduction capped at                  = 2,40,000
  the uncapped ₹96,000 is NOT deductible and remains income
PF-AGGCAP-001 excess is unchanged                             =   74,000
```
**Expected: ₹74,000 added; ₹2,40,000 deducted.** `INFERENCE`: employer NPS is worth ₹96,000 more of
deduction in the **new** regime. That is the one large deduction that favours the new regime, and
`A1.4` requires it in the comparison.

### T4-3 — `PF-RPF12-001`, independent of the aggregate cap

Basic ₹10,00,000; employer PF ₹1,50,000 (15% of salary); no NPS, no superannuation.

```
PF-AGGCAP-001: aggregate 1,50,000 ≤ 7,50,000 → no excess      =         0
PF-RPF12-001:  12% of 10,00,000 = 1,20,000
               employer contribution above 12%                =    30,000  → taxable
```
**Expected: ₹30,000 added to salary, with `PF-AGGCAP-001` contributing nothing.** `INFERENCE`: two
independent charges. A single "employer PF exemption" concept produces ₹0 here.

### T4-4 — `PF-EEINT-001`, the employee side. **`PROVISIONAL`**

Employee's own RPF contribution ₹6,00,000 in the year; employer also contributes.

```
PF-EEINT-001: employer DOES contribute → threshold is 2,50,000
excess contribution                                           = 3,50,000
interest accrued on that excess is NOT exempt
computation "in such manner as may be prescribed"             → NEEDS VERIFICATION
```
**Assertion: the excess is identified as ₹3,50,000 and the computation emits an
`UNVERIFIED_RULE` notice rather than a number.** `D-NOSILENT` applied to an unverified rule: name the
gap, do not fabricate through it.

---

## T5 — LTA, 80C with employee PF, and the s.123 aggregate

### T5-1 — `DED-80C-001` with `DED-80C-NPS-CAP-001`. `VERIFIED`

Reproduced from `01-rulebook.md` R15. Basic ₹10,00,000, no DA. Employee PF ₹1,20,000. LIC ₹30,000.
Tuition ₹60,000 (two children). Employee NPS ₹1,50,000.

```
Schedule XV contributions, each after its own sub-cap:
  para 1(f) employee RPF                                      = 1,20,000
  para 1(a) LIC premium                                       =   30,000
  para 1(q) tuition fees, two children                        =   60,000
  para 1(y) employee NPS, sub-capped at 10% of salary
            DED-80C-NPS-CAP-001: min(1,50,000 , 1,00,000)     = 1,00,000
                                            raw aggregate     = 3,10,000
DED-80C-001 ceiling                                           = 1,50,000
s.123 deduction                                               = 1,50,000

DED-NPS-SELF-001 (s.124(3)), after s.124(5)/(10):
  employee NPS not allowed under s.123 = 1,50,000 − 1,00,000   =   50,000
  capped at                                                    =   50,000
  deduction                                                    =   50,000
```
**Expected: ₹1,50,000 + ₹50,000 = ₹2,00,000.**

### T5-2 — The `H-7` regression: employee PF omitted from 80C

Same facts, but employee PF is not treated as a Schedule XV item.

```
raw aggregate = 30,000 + 60,000 + 1,00,000                    = 1,90,000
s.123 deduction = min(1,90,000 , 1,50,000)                    = 1,50,000
```
**Assertion: the *deduction* is the same, but `A3`'s reported 80C headroom differs by ₹1,20,000.**
`INFERENCE`: this is why the test must assert the **trace**, not just the total. The bug is invisible
in the tax figure and decisive in the advice — "you have ₹1,20,000 of unused 80C" when the user has
none is the most damaging output this product can produce.

### T5-3 — Sub-cap ordering. `VERIFIED`

Basic ₹4,00,000. Employee NPS ₹1,50,000. Nothing else.

```
CORRECT:  sub-cap first — 10% × 4,00,000 = 40,000
          aggregate 40,000 ; ceiling 1,50,000 → s.123 deduction  =   40,000
          DED-NPS-SELF-001 residue = 1,50,000 − 40,000 = 1,10,000
                                           capped at            =   50,000
          total                                                 =   90,000

WRONG:    ceiling first — min(1,50,000 , 1,50,000) = 1,50,000
          residue = 0 → DED-NPS-SELF-001                        =        0
          total                                                 = 1,50,000
```
**Expected: ₹90,000.** `INFERENCE`: the wrong order is ₹60,000 too generous. `G2.1`'s "sub-cap first,
then aggregate" is not a stylistic preference.

### T5-4 — LTA. **`PROVISIONAL` for TY 2025-26; `VERIFIED` for TY 2026-27**

LTA received ₹1,20,000. Journey: Bengaluru → Delhi, flown, actual expenditure ₹68,000 for the family.
One journey already claimed in calendar 2027 within the 2026-2029 block.

```
SAL-LTA-002 : exclusion cannot exceed expenditure actually incurred  = 68,000
SAL-LTA-003 : air fare limb — NEEDS VERIFICATION (r.278(1)(i) not read in full)
SAL-LTA-BLOCK-001 : block 2026-2029, two journeys. This is the 2nd  → allowed
exemption                                                            = 68,000
taxable LTA = 1,20,000 − 68,000                                      = 52,000
```
**Expected exemption ₹68,000, taxable ₹52,000.** Variants that must also pass:

| Variant | Expected |
|---|---|
| Third journey in the same block | exemption **₹0**, notice citing `SAL-LTA-BLOCK-001` |
| No expenditure evidence | exemption **₹0**, notice citing `SAL-LTA-002` and `TDS-SAL-12BB-002` |
| New regime | exemption **₹0** with a stated reason, citing `SAL-LTA-NEW-001` |
| Journey in calendar 2026, none taken in the 2022-2025 block | carried-over journey allowed and **not** counted against the block's two — `SAL-LTA-CARRY-001` |

---

## T6 — House property and other sources

### T6-1 — Let-out property. `VERIFIED`

Reproduced from `01-rulebook.md` R16. Rent ₹45,000/month; municipal taxes ₹18,000; interest
₹3,20,000; pre-construction ₹2,00,000, instalment 3 of 5.

```
HP-AV-001     annual value = actual rent 5,40,000
HP-AV-TAX-001 less municipal taxes paid                       =   18,000
                                          annual value        = 5,22,000
HP-DED-30-001 30% × 5,22,000                                  = 1,56,600
HP-DED-INT-001 interest, uncapped (let out)                   = 3,20,000
HP-DED-PRECON-001 2,00,000 ÷ 5                                =   40,000
income from house property                                    =    5,400
```
**Expected: ₹5,400.**

### T6-2 — The same property, self-occupied, old regime, TY 2026-27. `VERIFIED`

```
HP-SELFOCC-001 annual value                                   =        0
HP-DED-30-001  30% × 0                                        =        0
HP-CAP-SELFOCC-002 (FA 2026 s.38): the cap covers s.22(1)(b) AND (c)
  3,20,000 + 40,000 = 3,60,000, capped                        = 2,00,000
income from house property                                    = −2,00,000
SETOFF-HP-001 set-off against other heads, capped             = 2,00,000
```
**Expected: a ₹2,00,000 loss, fully set off.**

### T6-3 — The same property, self-occupied, **new** regime. `VERIFIED`

```
HP-NEW-001     s.22(1)(b) disallowed for a s.21(6) property   =        0
income from house property                                    =        0
SETOFF-NEW-001 no HP set-off against any other head
SETOFF-NEW-002 and the loss is NOT carried forward — destroyed
```
**Expected: ₹0, with two trace lines stating the disallowance and the destruction.** `INFERENCE`: the
same property yields +₹5,400, −₹2,00,000 or ₹0 on three settings of two flags. `H-12`.

### T6-4 — Two self-occupied houses, the aggregate cap. `VERIFIED`

Two self-occupied houses, interest ₹1,80,000 and ₹1,50,000, both qualifying for the ₹2,00,000 limb.

```
HP-CAP-SELFOCC-001 per property: min(1,80,000, 2,00,000) = 1,80,000
                                 min(1,50,000, 2,00,000) = 1,50,000
                                                 sum     = 3,30,000
HP-CAP-SELFOCC-003 aggregate across ALL s.21(6) properties
                   min(3,30,000 , 2,00,000)              = 2,00,000
```
**Expected: ₹2,00,000, not ₹3,30,000.** A second self-occupied house does not double the cap.

### T6-5 — The ₹30,000 limb. `VERIFIED`

Self-occupied; construction completed **six** years after the end of the tax year of borrowing;
interest ₹2,50,000.

```
HP-CAP-SELFOCC-001(i) not satisfied → the ₹30,000 limb applies
deduction = min(2,50,000 , 30,000)                            =   30,000
```
**Expected: ₹30,000.**

### T6-6 — Other sources, and the dividend change between the years

```
Savings interest 14,000 ; time-deposit interest 90,000 ;
dividend 2,00,000 ; interest expense on a loan used to buy the shares 60,000.
Individual under 60, old regime.

OS-DIV-DED-2026 (TY 2026-27): FA 2026 s.44(b) — NO deduction against dividend
  other-sources income = 14,000 + 90,000 + 2,00,000           = 3,04,000
  DED-80TTA-001: savings-account interest only, cap 10,000
                 min(14,000 , 10,000)                         =   10,000

OS-DIV-DED-2025 (TY 2025-26, as the 2025-Act text stood): interest expense
  allowed up to 20% of dividend income = 0.20 × 2,00,000       =   40,000
  other-sources income = 3,04,000 − 40,000                     = 2,64,000
  (the 1961-Act position for TY 2025-26 is NEEDS VERIFICATION) → PROVISIONAL
```
**Expected: ₹3,04,000 for TY 2026-27 (`VERIFIED`); ₹2,64,000 for TY 2025-26 (`PROVISIONAL`).**
A ₹40,000 difference in income from the year alone.

### T6-7 — Senior citizen, `DED-80TTB-001`. `VERIFIED`

Same interest figures, age 67, old regime.

```
DED-80TTB-001: deposits in ANY account including time deposits, cap 50,000
  min(14,000 + 90,000 , 50,000)                               =   50,000
DED-80TTA-001 does not apply to a senior citizen
```
**Expected: ₹50,000, and `DED-80TTA-001` absent from the trace.**

---

## T7 — TDS projection

All `VERIFIED` as to the method (`TDS-SAL-001`, `TDS-SAL-AVG-001`, `TDS-SAL-ADJUST-001`,
`TDS-SAL-REDUCE-001`).

### T7-1 — Mid-year joining

Joins 1 October. Monthly salary ₹2,00,000. New regime. No other income, no declarations.

```
Salary for the year = 2,00,000 × 6                            =12,00,000
SAL-STDDED-001 (new regime)                                   =   75,000
total income                                                  =11,25,000
tax: 20,000 + (11,25,000 − 8,00,000) × 0.10 = 20,000 + 32,500 =   52,500
REBATE-NEW-001: 11,25,000 ≤ 12,00,000 → min(52,500 , 60,000)  =   52,500
tax after rebate                                              =        0
cess                                                          =        0
annual tax                                                    =        0
```
**Expected: ₹0 deducted in each of the six months.** `INFERENCE`: a naive "monthly salary × 12" annual
estimate would give ₹24,00,000 and a large deduction. The joining date is the whole test.

### T7-2 — Mid-year revision

Monthly ₹1,50,000 from April; revised to ₹2,50,000 from 1 November. New regime. 12 payroll runs.

```
Runs 1–7 (Apr–Oct): estimate = 1,50,000 × 12                  =18,00,000
  less SAL-STDDED-001                                         =   75,000
  total income                                                =17,25,000
  tax = 1,20,000 + (17,25,000 − 16,00,000) × 0.20
      = 1,20,000 + 25,000                                     = 1,45,000
  rebate 0 (above 12,00,000, and 1,45,000 > 5,25,000? no —
    REBATE-NEW-MR-001: excess = 5,25,000 ; tax 1,45,000 is NOT
    greater than 5,25,000 → no relief)                        =        0
  cess 4%                                                     =    5,800
  annual tax                                                  = 1,50,800
  per run = 1,50,800 ÷ 12                                     = 12,566.67
  deducted over 7 runs                                        = 87,966.67

Run 8 (Nov): estimate = 1,50,000 × 7 + 2,50,000 × 5           =23,00,000
  less standard deduction                                     =   75,000
  total income                                                =22,25,000
  tax = 2,00,000 + (22,25,000 − 20,00,000) × 0.25
      = 2,00,000 + 56,250                                     = 2,56,250
  cess 4%                                                     =   10,250
  annual tax                                                  = 2,66,500
  remaining = 2,66,500 − 87,966.67                            = 1,78,533.33
  runs remaining including this = 5
  per run                                                     = 35,706.67
```
**Expected: ₹12,566.67 for seven runs, then ₹35,706.67 for five.** `INFERENCE`: `TDS-SAL-ADJUST-001`
spreads the back-effect forward; it does not recover it in a single month. A model that recomputes a
flat twelfth of the new annual tax under-collects by the seven months of difference.

### T7-3 — Previous employer declared

Same as T7-2, but in December the employee files Form No. 122 disclosing ₹9,00,000 of salary and
₹40,000 of TDS from a previous employer.

```
Run 9 (Dec): estimate = 23,00,000 + 9,00,000                  =32,00,000
  less standard deduction (ONE per year, SAL-STDDED-003)      =   75,000
  total income                                                =31,25,000
  tax = 3,00,000 + (31,25,000 − 24,00,000) × 0.30
      = 3,00,000 + 2,17,500                                   = 5,17,500
  cess 4%                                                     =   20,700
  annual tax                                                  = 5,38,200
  TDS-SAL-REDUCE-001(ii): less TDS already deducted elsewhere  =   40,000
  net annual liability with this employer                     = 4,98,200
  deducted so far = 87,966.67 + 35,706.67                     = 1,23,673.34
  remaining                                                   = 3,74,526.66
  runs remaining including this = 4
  per run                                                     = 93,631.67
```
**Expected: ₹93,631.67 for the last four runs.** `INFERENCE`: the deduction rises roughly 2.6× in one
month. Surfacing that in **September** is `F12`'s entire value proposition.

### T7-4 — The `TDS-SAL-REDUCE-001` asymmetry

```
T7-4a  employee declares ₹3,00,000 of interest income under TDS-SAL-12B-001
       → the annual estimate RISES, and so does the deduction.  ALLOWED.
T7-4b  employee declares a ₹2,00,000 house-property loss
       → the annual estimate FALLS.  ALLOWED — the only permitted reduction.
T7-4c  employee declares a ₹3,00,000 capital LOSS
       → the deduction MUST NOT fall.  s.392(4)(b) permits reduction only for
         a house-property loss and for TDS/TCS.
```
**Assertion: T7-4c leaves the deduction unchanged, with a notice explaining why.** `INFERENCE`: this is
a rule an intuitive implementation gets wrong, because "declare your losses to reduce TDS" is what
everyone assumes.

### T7-5 — Declaration that never becomes a proof

Declares ₹1,50,000 under `DED-80C-001` in April; no evidence by the January cut-off.

```
Runs 1–9:  claims include 1,50,000 → lower annual tax, lower monthly deduction
Run 10:    TDS-SAL-12BB-001 not satisfied → the claim is withdrawn
           annual tax rises by 1,50,000 × marginal rate (+ cess)
           TDS-SAL-ADJUST-001 spreads the shortfall over runs 10, 11 and 12
```
**Assertion: the March deduction is at least twice a normal month's, and the projection
labels it "unproven declaration withdrawn".** That label is `A3.3`'s highest-value alert.

---

## T8 — Form No. 130 reconciliation

### T8-1 — Two employers, both granting the standard deduction — **the highest-value check**

Employer A, Apr–Sep, salary ₹9,00,000, Part C issued. Employer B, Oct–Mar, salary ₹12,00,000, Part C
issued. Neither knew of the other; row 1(e) is zero on both.

```
Document A row 4(b) SAL-STDDED-001                            =   75,000
Document B row 4(b) SAL-STDDED-001                            =   75,000
                                          sum claimed         = 1,50,000
SAL-STDDED-003 permits ONE per year, capped at salary         =   75,000
excess granted                                                =   75,000

Correct computation:
  salary 9,00,000 + 12,00,000                                 =21,00,000
  less one standard deduction                                 =   75,000
  total income                                                =20,25,000
  tax (new) = 2,00,000 + (20,25,000 − 20,00,000) × 0.25
            = 2,00,000 + 6,250                                = 2,06,250
  cess 4%                                                     =    8,250
  liability                                                   = 2,14,500

What the two employers between them deducted:
  A: TI = 9,00,000 − 75,000 = 8,25,000
     tax = 20,000 + 25,000 × 0.10 = 22,500
     REBATE-NEW-001: 8,25,000 ≤ 12,00,000 → min(22,500 , 60,000) = 22,500
     tax after rebate                                          =        0
  B: TI = 12,00,000 − 75,000 = 11,25,000
     tax = 20,000 + 3,25,000 × 0.10 = 52,500
     REBATE-NEW-001 → min(52,500, 60,000)                      =   52,500
     tax after rebate                                          =        0
                                          total deducted      =        0

SHORTFALL                                                     = 2,14,500
```
**Expected: a `MATERIAL_DIFFERENCE` verdict of ₹2,14,500, attributed to "previous employer's salary
not declared" (`TDS-SAL-12B-001`) and "standard deduction granted twice" (`SAL-STDDED-003`).**
`INFERENCE`: each employer's own document is internally correct and passes every `G5.2` arithmetic
check. The error is only visible across documents. This is the "Priya" persona's whole problem, and it
is a ₹2.1 lakh surprise at filing.

### T8-2 — Declared but unproven

Part C row 10(a) shows ₹1,50,000 under s.123. The user has evidence for ₹60,000.

```
Document                                                      = 1,50,000
Computation (only proven claims)                              =   60,000
difference                                                    =   90,000
```
**Expected: `MINOR_DIFFERENCE`, reason "claim declared to the employer but not proven"
(`TDS-SAL-12BB-001`, `M3.3`), with a warning that additional tax will be payable at filing.**

### T8-3 — Entitled but never declared

The user paid ₹25,000 of health insurance, never told the employer.

```
Document row 10(d) s.126                                      =        0
Computation                                                   =   25,000
```
**Expected: `MINOR_DIFFERENCE`, reason "deduction not declared to the employer — refund due".**

### T8-4 — The Part B / Part C tax split

```
Part B "amount of tax deducted" (DOC-F130-TAXSUM-001: tax + surcharge + cess)  = 2,14,500
Part C rows 13 + 15 + 16 − 14 (row 17)                                          = 2,14,500
```
**Expected: `MATCHED`.** A comparison of Part B against row 13 alone would differ by the cess and is
the mistake the reason-set exists to prevent.

### T8-5 — Regime mismatch

Part C row A says "opting out of s.202(1)? **Yes**" (old regime); row 4(b) shows ₹75,000.

```
SAL-STDDED-002 (old regime)                                   =   50,000
SAL-STDDED-001 (new regime)                                   =   75,000
row A says OLD but row 4(b) is the NEW-regime amount → contradiction
```
**Expected: `G5.2`'s semantic check fails, confidence is lowered on both cells, and the user is asked
one precise question: "which regime did your employer apply?"** Never resolved silently.

### T8-6 — `CANNOT_COMPARE`

Two employers; only one issued Part C; that Part C's row 1(e) is zero.

**Expected: verdict `CANNOT_COMPARE`, with an explanation that Part C from the last employer should
have included the previous employer's salary (`DOC-F130-ANNEX-001`) and appears not to.** No total is
invented.

---

## T9 — Robustness

| ID | Scenario | Expected | Design rule |
|---|---|---|---|
| T9-1 | Rule pack for the requested tax year is missing | **Application fails to start.** If reached at runtime, `MissingRulePackException` → 500 | `D-FAILFAST`, O-18 |
| T9-2 | Requested tax year has no registry entry | `UnsupportedTaxYearException` → 400, naming the supported years | `D-REGISTRY`, O-4 |
| T9-3 | Rule pack checksum does not match | **Startup fails**, naming the pack and both checksums | `D-IMMUTABLE`, I4, O-16 |
| T9-4 | Pack row has `status = NEEDS_VERIFICATION` and a blank `whatToCheck` | **Load fails**, invariant I1 | `A4.2` |
| T9-5 | Pack references a `RuleId` absent from `rules` | **Load fails**, invariant I2 | O-2 |
| T9-6 | A `RuleId` exists but no pipeline reaches it | **Load fails**, invariant I3 | O-19 |
| T9-7 | Slab band written as `lowerExclusive = 400001` | **Load fails** — continuity check `band[i].lowerExclusive == band[i-1].upperInclusive` | `H-5`, O-14 |
| T9-8 | Request carries component code `"EMPLOR_PF"` | `UnknownComponentCodeException` → 400. **Never summed into gross** | `D-ONECODE`, `D-CLOSEDSUM`, O-15 |
| T9-9 | `annualAmount` is null on a component | 400 from Bean Validation. `Money` is never null internally | `D-MONEY`, O-9, O-12, O-29 |
| T9-10 | A `CapShape` throws while evaluating | **The computation fails.** It must never return 0 | `D-NOSILENT`, O-1 |
| T9-11 | `PerUnitPerPeriod` with a zero period | **Load fails** at pack validation, not a divide-by-zero at runtime | `D-CAPTYPE`, O-10 |
| T9-12 | `portfolio` reports non-zero `UNCLASSIFIED` gains | `BlockingDataQualityException` → 409 with an `ACTION_REQUIRED` notice. **Never defaulted to zero or to short-term** | `X1.3` |
| T9-13 | `portfolio` reports non-zero `SPECULATIVE` gains | Computation proceeds, the amount is **excluded** from capital gains, the ITR advisor returns **ITR-3**, and a notice explains that business income is outside this module's scope | `ITR-SPEC-001` |
| T9-14 | `portfolio` read-model is unavailable | 503 with a clear message. **Never computes with zero capital gains** | `D-NOSILENT` |
| T9-15 | Old-regime pipeline is inspected for a rebate-marginal-relief step | **No such step is present** | `H-3`, O-11, `D-REGIMESTEP` |
| T9-16 | Trace is inspected for `ROUNDING` lines | **Exactly two**, no more | `RATE-ROUND-003`, O-14 |
| T9-17 | `takeHome` is computed with a ₹96,000 car perquisite present | **Unchanged** by it | `D-CASHFLAG`, `H-15`, O-13 |
| T9-18 | Gift perquisite of exactly ₹15,000 | **The whole ₹15,000 is taxable** — `PERQ-GIFT-2026` says nil only if the value is *below* ₹15,000, and the exemption is not an excess-over deduction | `PERQ-GIFT-BOUNDARY-001` |
| T9-19 | Gift perquisite of ₹14,990 | **₹0 taxable** | `PERQ-GIFT-2026` |
| T9-20 | The same inputs computed twice | Byte-identical trace, same `packVersion` and `checksum` recorded | `D-PURE` |
| T9-21 | A synthetic TY 2099-00 pack with different values in every dimension | Loads, both pipelines run, results match hand-computed expectations, **no production source file changed** | `X5.4` — the Open/Closed acceptance test |

---

## Coverage map — every case against the year × regime grid

`X5.1` requires that no cell be empty. Cases marked ⓟ are `PROVISIONAL`.

> **Amended by `10-extensibility.md` E8.** The required set of cells is the cross product
> `taxYears × regimes declared in that year's pack`, **enumerated from the packs at test time** rather
> than written out. For the two shipped years that is the four columns below; a year declaring three
> regimes yields six, with no test-code change. `T10` — the three-regime synthetic pack — is the
> acceptance test for that.

| Test | 2025-26 OLD | 2025-26 NEW | 2026-27 OLD | 2026-27 NEW |
|---|---|---|---|---|
| T1 slabs and rebate | T1-O1..O3, A1 | T1-N1..N4, A1 | T1-O1..O3, A1 | T1-N1..N4, A1 |
| T2 surcharge | T2-4, T2-5 | T2-1..T2-3 | T2-4, T2-5 | T2-1..T2-3 |
| T3 HRA | T3-2ⓟ, T3-3ⓟ, T3-5 | T3-4b (nil) | T3-1, T3-4a, T3-5 | T3-4b (nil) |
| T4 PF and NPS | T4-2, T4-3, T4-4ⓟ | T4-1 | T4-2, T4-3, T4-4ⓟ | T4-1 |
| T5 LTA and 80C | T5-1..T5-3, T5-4ⓟ | T5-4 (nil) | T5-1..T5-3, T5-4 | T5-4 (nil) |
| T6 HP and other sources | T6-1..T6-7, T6-6ⓟ | T6-3 | T6-1..T6-7 | T6-3 |
| T7 TDS projection | T7-4, T7-5 | T7-1..T7-3 | T7-4, T7-5 | T7-1..T7-3 |
| T8 reconciliation | T8-5 | T8-1..T8-4, T8-6 | T8-5 | T8-1..T8-4, T8-6 |
| T9 robustness | year- and regime-independent, run once per cell where applicable |

**Counts: 9 `PROVISIONAL` cases, all of them TY 2025-26, all traceable to the single blocked source
(`incometaxindia.gov.in`, HTTP 403 — S10) that prevented the Income-tax Rules 1962 from being opened.**
