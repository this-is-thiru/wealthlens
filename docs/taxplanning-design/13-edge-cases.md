# 13 — Edge cases and reframed decisions

A second, adversarial pass over `01`–`12`, hunting for cases the first pass handled wrongly, silently,
or not at all. **Part A reframes decisions that were wrong.** Part B is the catalogue. Part C is the
test delta.

Where this disagrees with an earlier document, this wins.

---

# Part A — Decisions reframed

## R-1 … R-3 — already reframed

| | Was | Now | Where |
|---|---|---|---|
| **R-1** | Two regimes, a closed enum | N regimes as pack data | `10-extensibility.md` E3 |
| **R-2** | `ComponentCode` a closed Java enum | a registry loaded from the pack | `10-extensibility.md` E4 |
| **R-3** | One input set per person per year (unique index) | a workspace of N drafts | `11-filing-drafts.md` F1 |

## R-4 — Concurrent employment is legal, and I asserted it away

**Was.** `06-algorithms.md` G5.3: `assert no two (tan, period) ranges overlap`.

**Wrong.** Nothing in the Act prevents a person holding two employments at once. `FACT (S2, s.15(1))`:
salary is chargeable when *"due from **an** employer"* — the section is written per-employer and has
no exclusivity. `FACT (S2, s.19(1) Table Sl. 2)`: the standard deduction is capped at *"the salary"*,
a single annual figure, which is precisely the machinery you need **because** there can be more than
one employer at a time.

**Now.**

```
OVERLAP is legal. Classify, do not reject:
  SEQUENTIAL   periods do not overlap                     → normal job change
  CONCURRENT   periods overlap                            → moonlighting / dual employment
  GAP          an uncovered interval inside the tax year  → unemployment, or a missing document
  REJOIN       same TAN, two non-adjacent periods         → left and returned

CONCURRENT changes three things:
  1. the standard-deduction double-count check (T8-1) still applies, and is MORE likely to bite
  2. neither employer knows the other's salary, so BOTH under-deduct — the shortfall is larger
     than in the sequential case, and TDS-SAL-12B-001's Form No. 122 route is the only fix
  3. HRA's "relevant period" (SAL-HRA-003) may be covered by two salary bases at once;
     the base for a period is the SUM of concurrent salaries, not one of them
```

`INFERENCE`: the sequential assumption did not merely miss a case — it would have thrown on the most
under-served user in the persona set. `RECOMMENDATION`: `Employment.overlapClass` is derived and
stored, and `G5.3`'s assertion becomes a classification with a notice per class.

## R-5 — The rebate cap and special-rate income: a rule I stated and then did not design for

**Was.** `06-algorithms.md` G3.1 applies `REBATE-NEW-MR-002` as `rebate = min(rebate, taxAtNewRegimeRates)`
with no explanation of how that differs from `taxBeforeRebate`.

**The rule.** `FACT (S2, s.156(3))`: *"The deduction under sub-section (2) shall not exceed income-tax
payable as per the rates provided in section 202(1)."* Section 202(1) is the **slab table**. So the
rebate cannot be set against tax charged at a **special rate** — capital-gains tax under ss.196/197/198
(1961 Act: ss.111A/112/112A).

**Why it matters, and why D3 makes it live.** The primary persona has capital gains fed from
`portfolio`. Consider TY 2026-27, default regime:

```
Salary income after standard deduction                              =  1,00,000
Long-term capital gains under s.198                                 = 11,00,000
Total income                                                        = 12,00,000

Naive (wrong) computation:
  "total income 12,00,000 ≤ 12,00,000 → REBATE-NEW-001 wipes out the tax"   → tax NIL

Correct:
  tax at s.202(1) slab rates on 1,00,000                            =          0
  tax at the special rate on 11,00,000                              =  <special>
  REBATE-NEW-001 available up to ₹60,000 …
  REBATE-NEW-MR-002 caps it at tax payable AT s.202(1) RATES        =          0
  ⇒ rebate is ZERO, and the whole special-rate tax stands
```

`INFERENCE`: the error is invisible in any test built on salary alone, produces a **nil tax bill** on a
₹12,00,000 income, and only appears once capital gains are wired in. It is the single most dangerous
interaction between D3's portfolio feed and the rebate rules.

**Now.** The pipeline must carry **tax partitioned by rate basis** from step 24 to step 28, not a
scalar:

```java
record TaxByBasis(
        Money atSlabRates,          // s.202(1) / 115BAC(1A) / Part I-A Paragraph A
        Map<SpecialRateId, Money> atSpecialRates) {   // ss.196, 197, 198 / 111A, 112, 112A
    Money total() { ... }
}
```

Three rules consume the partition, and each needs a different part of it:

| Rule | Needs |
|---|---|
| `REBATE-NEW-MR-002` / `REBATE-CAP-001` | `atSlabRates` **only** |
| `RATE-SUR-SPLIT-001` — the 15% cap on the special slice | both, separately |
| `RATE-CESS-001` | the total, after rebate and surcharge |

`RECOMMENDATION`: this is a **type change**, not a calculation change, and it should land in Slice 2
before any rebate code exists. Retrofitting a partition through a pipeline that has been passing a
scalar is the kind of change that gets half-done.

## R-6 — Which two houses are self-occupied is an election, and it is optimisable

**Was.** `05-domain-model.md` M3.2 has `HouseProperty.occupancy` as a plain input.

**The rule.** `FACT (S2, s.21(7)(a))`: nil annual value *"shall apply only in respect of **two of such
houses as specified by the assessee** in this behalf"*. The assessee **chooses**.

**Why the choice is not obvious.** For a property *not* specified, the annual value is the expected
letting value, a 30% standard deduction applies, and — critically — **interest is deductible without
the ₹2,00,000 cap** (`HP-DED-INT-001` vs `HP-CAP-SELFOCC-001`). So a third house carrying ₹6,00,000 of
interest may be **better off not specified**, despite attracting a notional annual value.

```
House C:  expected letting value 4,00,000 ;  interest 6,00,000

  specified as self-occupied (only possible if it displaces another):
      AV 0 ; interest capped at 2,00,000 (shared cap!) ; contribution ≈ −2,00,000 at most
  not specified (deemed let out):
      AV 4,00,000 ; less 30% = 1,20,000 ; less interest 6,00,000
      contribution = 4,00,000 − 1,20,000 − 6,00,000                     = −3,20,000
```

**Now.** `occupancy` splits into a **fact** and an **election**:

```java
HouseProperty
    usage            // ACTUALLY_OCCUPIED | LET_OUT | VACANT_NOT_LET | STOCK_IN_TRADE   ← fact
    selfOccupiedElection  // ELECTED | NOT_ELECTED | AUTO                              ← election
```

`AUTO` means the optimiser chooses. `RECOMMENDATION`: with `k` eligible houses the election is
`C(k,2)` evaluations — 3 for k=3, 10 for k=5 — priced by recomputation (`D-MARGINAL`), which is
trivially affordable and exactly the machinery `A2` already has.

**Two hard constraints the election must respect**, both `FACT (S2, s.21(7)(b))`: the nil value
*"shall not apply, if the house or any part thereof is **actually let during any time of the tax
year**, or if the owner derives **any other benefit** from it."* A property let for a **single day**
is ineligible for election, no matter what the user ticks.

## R-7 — Inter-head set-off order changes the tax, because of the surcharge split

**Was.** `06-algorithms.md` G1.1 step 17 sets off the house-property loss against "other heads" without
saying which first.

**Why it matters.** `FACT (S2, s.109(1)(b))`: the HP loss is set off *"to the extent of ₹2,00,000
against income under any other head"* — the Act does not prescribe an order. `INFERENCE (from
RATE-SUR-SPLIT-001)`: absorbing the loss against **slab-rate** income versus against **special-rate**
capital gains produces different totals, because the surcharge rate on the special slice is capped at
15% while the slab slice may bear 25%. Absorbing against the slab slice is therefore generally better
for a high earner, and against the special slice generally worse.

**Now.** `SetOffRules` carries an explicit, pack-declared **absorption order** with a documented
default and a rule ID, and the trace records which order was used:

```json
{ "ruleId": "SETOFF-HP-001",
  "absorptionOrder": ["SALARIES", "OTHER_SOURCES", "CAPITAL_GAINS_SLAB", "CAPITAL_GAINS_SPECIAL"],
  "orderStatus": "NEEDS VERIFICATION",
  "whatToCheck": "whether the Act or CBDT prescribes an order for s.109(1)(b); if not, whether the
                  assessee may choose. Until settled, the order above is applied and disclosed." }
```

`RECOMMENDATION`: mark it `NEEDS VERIFICATION` and **show the order in the trace**. This is exactly
the `A4.2` case — a defensible choice, visibly made, rather than an invisible one.

## R-8 — Retrospective amendments break "reproducible" and "correct" simultaneously

**Was.** `07-architecture.md` X4.4 said *"bump `packVersion`, never mutate in place"*, so a stored
computation stays explainable. That is right, and insufficient.

**The problem, with a verified example.** `FACT (S3 — Finance Act 2026, s.4)`: it inserts s.92CA(3AA)
into the 1961 Act and *"shall be deemed to have been inserted **with effect from the 1st day of June,
2007**"* — a nineteen-year retrospective insertion. `FACT (S3, s.5)`: it substitutes s.139
Explanation 2 *"with effect from the 1st day of March, 2026"* and *"shall be deemed to have been
substituted"*.

`INFERENCE`: a computation made on 1 March 2026 under pack `2025-26.1` may be **legally wrong** by
2 April 2026 under pack `2025-26.2`, through no change in the user's facts. Pinning the pack makes the
old answer *reproducible*; it does not make it *right*.

**Now.** Three mechanisms, not one:

| Mechanism | Purpose |
|---|---|
| `packVersion` + `checksum` pinned on every `Computation` (existing) | **reproducibility** — we can always explain what we said |
| `RulePack.supersededBy` + `RuleRow.retrospectiveFrom` | **detectability** — a pack can declare that it retrospectively replaces an earlier pack for the same tax year |
| A **staleness sweep** — on pack upgrade, every `ACTED_ON` and `READY` draft whose pinned pack is superseded is flagged | **correctness** — the user is told their figures changed, with a diff |

```java
record RulePack(..., Optional<PackRef> supersedes, LocalDate retrospectiveFrom) {}
```

`RECOMMENDATION`: never silently recompute a stored result. Flag, diff, and let the user re-run. A
user who filed on the old figures needs to know the law moved, not to find their saved report quietly
disagreeing with the copy they printed.

## R-9 — Rounding a negative amount is genuinely ambiguous

**Was.** `05-domain-model.md` M6 said *"round to the nearest ₹10"* in two places, silent on sign.

**The text.** `FACT (S2, s.516)`: *"…shall be **increased** to the next higher amount which is a
multiple of ten, if the last figure in that amount is five or more; or … **reduced** to the next lower
amount … if the last figure is less than five."*

`INFERENCE`: "higher", "lower" and "last figure" are written for a positive amount. For a **refund**
(s.516 expressly covers *"any amount payable or refundable"*) and for a **loss** carried forward, the
sign makes "higher" ambiguous — toward zero or away from it.

**Now.** Round the **magnitude** and restore the sign, i.e. symmetric about zero. `RECOMMENDATION`:
record it as a rule row with `status = STATUS UNCLEAR` and a stated reading, so it is visible and
changeable in one place:

```
ROUND-NEG-001   Rounding under s.516 is applied to |amount|, and the sign restored.
                STATUS UNCLEAR — s.516 is drafted for positive amounts.
                What to check: CBDT guidance or a decided case on s.288B for refunds.
                Impact: at most ₹9 on a refund. Recorded because a silent convention in
                money arithmetic is how ₹9 becomes ₹9,00,000 in a later change.
```

## R-10 — Who computes the tax on capital gains?

**Was.** D3 says capital gains are *"consumed from `portfolio` via a published summary, never
recomputed here"*, and `07-architecture.md` X1.3's `CapitalGainsSummary` carries
`Map<CapitalGainsType, Money>` — **amounts**, not tax.

**The gap.** `RATE-SUR-SPLIT-001` needs *"the income-tax computed in respect of that part of income"*.
Someone must apply ss.196/197/198 rates. `portfolio` has no rule pack and no tax year registry;
`taxplanning` has both.

**Now.** The boundary is drawn at **classification and quantification**, not at taxation:

| `portfolio` owns | `taxplanning` owns |
|---|---|
| which disposals happened, their dates, FIFO cost basis | which tax year they fall in |
| the five-way `CapitalGainsType` classification | whether a class `countsAsCapitalGains()` for this computation |
| the **amount** of gain per class | the **rate basis** — long-term vs short-term vs special rate |
| brought-forward loss balances | applying ss.196/197/198 and the surcharge split |
| grandfathering and indexation, where its own rules apply | nothing about indexation |

The read-model therefore carries a **rate-basis hint per bucket**, not a tax amount:

```java
record CapitalGainsBucket(
        CapitalGainsType classification,
        Money gain,
        RateBasisHint basis,        // SPECIAL_RATE_LISTED_EQUITY | SPECIAL_RATE_OTHER | SLAB
        Optional<String> portfolioRuleRef) {}
```

`INFERENCE`: this keeps D3's rule intact — nothing is recomputed — while putting rate application on
the side of the boundary that has a rule pack. `RECOMMENDATION`: a `RateBasisHint` the tax pack does
not recognise is a **blocking error**, like `UNCLASSIFIED` (`X1.3`), not a fallback to slab.

## R-11 — HRA's metro percentage is per period, not per year

**Was.** `06-algorithms.md` G1.3 loops over occupation periods but resolves
`SAL-HRA-METRO-<year>.percentFor(locationOfAccommodation)` as though location were a single value.

**Now.** A person who moves from Pune to Nashik mid-year occupies two accommodations in two
percentage bands. `FACT (S4, r.279(1)(c))`: the percentage is *"in case of an assessee employed in the
location mentioned in column B"* and `FACT (r.279(2)(a))`: the relevant period is *"the period during
which the said accommodation was occupied"*. **Per accommodation, per period.**

The three limbs are computed per period and **summed**, not computed once on annual figures:

```
exemption = Σ over periods p of
              LeastOf( hraReceivedIn(p),
                       rentPaidIn(p) − 10% × salaryBaseIn(p),
                       pct(locationOf(p)) × salaryBaseIn(p) )
```

`INFERENCE`: annualising first can change the answer in both directions, and it makes the
`limitedBy` field meaningless when different limbs bind in different periods. The trace must carry one
line **per period** with its own `limitedBy`, and a summary line.

## R-12 — LTA's calendar block straddles the tax year, in both directions

**Was.** `05-domain-model.md` M7 keyed `tax_lta_block_state` on `{email, block_start_year}` with a note
that calendar years are deliberate. Correct, and not carried into the algorithm.

**The trap.** `FACT (S4, r.278(2))`: two journeys in a **block of four calendar years commencing with
calendar year 2022** — so blocks are **2022-2025**, **2026-2029**. A journey on 10 February 2027 falls
in **tax year 2026-27** and in **calendar block 2026-2029**. A journey on 10 February 2026 falls in
tax year 2025-26 and in **block 2022-2025** — the *previous* block, even though the tax year is the
one where the exemption is claimed.

`INFERENCE`: the two shipped tax years **straddle a block boundary**. TY 2025-26 (1 Apr 2025 – 31 Mar
2026) spans the end of block 2022-2025 and the first three months of block 2026-2029. So a single tax
year can contain journeys from **two different blocks**, each with its own two-journey allowance and
its own carry-over state.

**Now.** `LtaClaim` carries the **journey date**, the block is derived from the calendar year of that
date, and the two-journey test is applied **per block**, never per tax year. `SAL-LTA-CARRY-001`'s
carry-over is evaluated against the first calendar year of the succeeding block, which may be in the
same tax year as the block it carries from.

## R-13 — Age, and the 1 April birthday

**Was.** `05-domain-model.md` M3.2: `seniorCitizenDerived()` from `dateOfBirth` and the tax year.

**The edge.** `FACT (S3, Part I-A Para A (II)/(III); Part I-B Para A (II)/(III))`: the test is *"of the
age of sixty years or more … **at any time during** the previous year / tax year"*. So someone turning
60 on 31 March 2027 qualifies for the whole of TY 2026-27. Someone born on **1 April 1967** attains 60
on 1 April 2027, one day after TY 2026-27 ends.

`STATUS UNCLEAR` — `AGE-BOUNDARY-001`: there is a long-standing position under the 1961 Act that a
person born on 1 April is treated as attaining the age on 31 March, which would pull that person into
the earlier year. **No source for it was opened this session.** What to check: CBDT guidance or a
decided case on the 1 April birthday under s.80U / the slab provisos.

`RECOMMENDATION`: implement the literal reading (anniversary date), flag a person born on 1 April with
an `UNVERIFIED_RULE` notice, and show both outcomes. The difference is one slab band of basic
exemption, which is real money and a genuinely arguable point.

## R-14 — Advance tax: two carve-outs that between them cover most of the persona

**Was.** `01-rulebook.md` R23 has both rules; `03-advisory.md` A3.3 and `04-product-spec.md` F16 did
not connect them.

`FACT (S2, s.403(3))`: advance tax does **not** apply to a resident individual who has **no** business
or professional income **and** is 60 or more at any time in the tax year. `INFERENCE`: a
senior citizen with ₹40 lakh of capital gains and no business owes **no advance tax at all** — the
exemption is not income-capped.

`FACT (S2, s.425(4))` — newly read for this pass: no interest for shortfall where the shortfall is on
account of underestimating or failing to estimate **(i) capital gains, (ii) income as per s.2(49)(n),
(iii) business or professional income accruing for the first time, and (iv) dividend income**, provided
the assessee **pays the tax on that income in full in any of the remaining instalments, or by 31 March
of the tax year.**

`INFERENCE`: this is the answer to the question the D3 user actually has — *"I sold shares in
December, have I already missed an instalment?"* — and the answer is **no, provided you pay by the
next instalment or by 31 March**. `RECOMMENDATION`: F16's output must lead with this, not with the
generic 15/45/75/100 ladder, because for a salaried user with portfolio gains the ladder is almost
never the binding constraint and s.425(4) almost always is.

## R-15 — A rule that changes shape mid-stream: pre-construction interest

**Was.** `01-rulebook.md` `HP-CAP-SELFOCC-002` records that FA 2026 s.38 brought s.22(1)(c)
(pre-construction interest) **inside** the ₹2,00,000 self-occupied cap, and marks the TY 2025-26
position `NEEDS VERIFICATION`.

**The edge.** `HP-DED-PRECON-001` spreads pre-construction interest over **five tax years**. A user who
started the five instalments in TY 2024-25 has instalments 1 and 2 under one regime of capping and
instalments 3, 4 and 5 under another.

`INFERENCE`: this is the general case of *a multi-year entitlement whose rule changes mid-entitlement*,
and `M3.4`'s carry-forward ledger stores only the **instalment count**. It must also store the
**amount** and be evaluated against **each year's own pack** — which the design already supports,
because instalments are computed per tax year from the pack, not carried as a pre-computed allowance.

`RECOMMENDATION`: add an explicit invariant — **a cross-year ledger stores facts, never pre-computed
entitlements.** `RET-GRAT-AGG-001` and `RET-LEAVE-AGG-001` obey it already (they store amounts
*allowed*, which is a fact). `HP-DED-PRECON-001` must store the **total pre-construction interest** and
the **instalments taken**, not "₹40,000 per year".

---

# Part B — Edge-case catalogue

Severity: **B** blocks or produces a materially wrong number · **W** produces a wrong number in an
uncommon case · **N** needs a decision, low money impact.

## B1 — Temporal

| # | Case | Decision | Sev |
|---|---|---|---|
| 1 | Employment period extends beyond the tax year | **Clip to the tax year** at ingestion; store the true period, compute on the clipped one | B |
| 2 | Employment starts before and ends after the tax year | Clip both ends; `relevantPeriod` = the whole tax year | W |
| 3 | Leap year in period proration | Prorate on **completed months** where the rule speaks of months (`PerUnitPerPeriod`), on **days** only where the rule speaks of days. `SAL-HRA-003` says "period", so use days and document it | W |
| 4 | A pay date falls outside the tax year for salary *due* inside it | `SAL-CHARGE-001` — salary **due** is chargeable whether paid or not. Charge on due date; pay date drives the TDS projection only | B |
| 5 | Tax year boundary vs LTA calendar block | R-12 | B |
| 6 | Person turns 60 or 80 mid-year | Qualifies for the **whole** year (`RATE-SLAB-AGE-001`) | B |
| 7 | Born 1 April | R-13, `AGE-BOUNDARY-001`, `STATUS UNCLEAR` | N |
| 8 | Death of the assessee mid-year | **Out of scope**, but must not be blocked: `TaxpayerProfile` carries an optional `dateOfDeath` and the legal-heir filing path is a later feature, not a schema change | N |
| 9 | Rule pack superseded retrospectively | R-8 | B |
| 10 | A draft computed before Budget day, viewed after | Pinned pack + staleness flag (R-8); never silent recompute | B |

## B2 — Money and arithmetic

| # | Case | Decision | Sev |
|---|---|---|---|
| 11 | Negative total income (loss) | Slab walk yields nil; rounding per R-9 | W |
| 12 | Rounding a refund or a loss | R-9, `ROUND-NEG-001`, `STATUS UNCLEAR` | N |
| 13 | Paise in inputs (rent ₹12,345.67) | Accepted; `Money` scale 2. Rounding to ₹10 happens **only** at the two statutory points | W |
| 14 | `1/3` of family pension, `1/5` of pre-construction interest, `30%` of annual value | Compute at scale 10, store at scale 2 (`M6`). Never round an intermediate to rupees | W |
| 15 | Total income exactly at a slab boundary | Half-open bands (`RATE-SLAB-BAND-002`); `T1` pins them | B |
| 16 | Total income exactly at a surcharge threshold | *"exceeds"* — at exactly the threshold, no surcharge (`T2-1`) | B |
| 17 | Gift perquisite at exactly ₹15,000 | Whole amount taxable (`PERQ-GIFT-BOUNDARY-001`, `T9-18`) | W |
| 18 | Very large income (₹10,000 crore) | `BigDecimal` throughout; no `long` anywhere in money (`D-MONEY`). Surcharge ladder is open-ended at the top | N |
| 19 | Standard deduction where salary < the deduction | Capped at salary (`SAL-STDDED-003`); cannot create a loss under the head | W |
| 20 | `PerUnitPerPeriod` over a part month | Decide **completed months** and document it in the rule row; a child in hostel for 20 days is one month or zero, not 0.65 | W |

## B3 — Regime and election

| # | Case | Decision | Sev |
|---|---|---|---|
| 21 | User has business income as well as salary | `BindingSpec.bindingAppliesWhen` holds, so the lock-in limb of s.202(4)(a) applies (`17-regime-lifecycle.md` L3). **Also** `SETOFF-PGBP-001` and `ITR-3-ELIG-001`. Detect and route; do not compute business income | B |
| 22 | Employer applied one regime, the return uses another | Expected and legal. Reconciliation reason set (`M4.2`); the difference is a refund or a payable, not an error | B |
| 23 | Default regime flips between two years | Handled — `isDefault` is per pack. But **rollover must not copy the election** (`11-filing-drafts.md` F7) | B |
| 24 | Switching regime destroys carried-forward HP loss | `SETOFF-NEW-002` — **permanent**, not deferred. First-class rollover alert (`11-filing-drafts.md` F7) | B |
| 25 | Belated return and the opt-out | `REG-OPTOUT-LATE-001`, `STATUS UNCLEAR` | B |
| 26 | Non-resident claiming the rebate | `REBATE-OLD-001` / `REBATE-NEW-001` require a **resident** individual. Check `residentialStatus` explicitly, do not assume | B |

## B4 — Salary

| # | Case | Decision | Sev |
|---|---|---|---|
| 27 | Concurrent employers | R-4 | B |
| 28 | Rejoining the same employer | Two `Employment` rows, same TAN, different `periodFrom` — the M7 key already allows it | W |
| 29 | Arrears of salary | `SAL-CHARGE-001(c)` — taxable in the year received if not charged earlier; `REL-ARREARS-001` relief is `Later` (`F20`) | W |
| 30 | Negative component (recovery) | Permitted as a negative `Money`; must **reduce** gross, and must not make a component's own cap negative | W |
| 31 | Notice pay recovered by the employer | No deduction in s.19(1). `RET-NOTICE-001`. Do **not** net it against salary silently | W |
| 32 | Employer pays tax on a non-monetary perquisite | `PERQ-TAXPAID-001` — excluded from income (Sch. III Sl. 10) **and** treated as TDS (`TDS-SAL-PERQ-001`). Both, not either | B |
| 33 | Employer deducted TDS but did not deposit it | Form No. 130 and the department's records disagree. `CANNOT_COMPARE` plus an `ACTION_REQUIRED` notice; never assume credit | B |
| 34 | Professional tax paid by the employer on the employee's behalf | A perquisite under s.17(1)(f) **and** deductible under `SAL-PTAX-001` in the old regime. Both legs | W |
| 35 | Salary in foreign currency | `TDS-SAL-FX-001` (s.392(8), r.206). Rate is prescribed; conversion is not the app's choice | N |

## B5 — HRA, LTA, allowances

| # | Case | Decision | Sev |
|---|---|---|---|
| 36 | Moves between a 50% and a 40% location mid-year | R-11 — per period | B |
| 37 | HRA received but rent paid for part of the year only | Limb (a) and limb (c) prorate to the relevant period; limb (b) uses actual rent for that period | B |
| 38 | Rent paid to a relative | Lawful; `SAL-HRA-006` relationship disclosure required; evidence expectations raised (`12-claim-integrity.md` C3.4) | W |
| 39 | Salary revised mid-year while renting | The salary base is **period-specific** (R-11); a single annual base is wrong in both directions | B |
| 40 | HRA claimed on a self-owned house | Blocked — `SAL-HRA-002` (`12-claim-integrity.md` C3.1) | B |
| 41 | Two journeys claimed in one tax year but in different LTA blocks | Legal — R-12. Each counts against its own block | W |
| 42 | LTA claimed with no travel | Exemption nil — `SAL-LTA-002`; the allowance stays fully taxable | B |
| 43 | Children education allowance for a third child | Capped at two children (`SAL-ALW-CEA-2026`) — and the **two** are the assessee's choice where amounts differ | W |
| 44 | Allowance the pack does not know | Load error on the pack, or 400 on the request (`D-CLOSEDSUM`). **Never** exempt by default | B |

## B6 — House property

| # | Case | Decision | Sev |
|---|---|---|---|
| 45 | Three or more houses occupied for own residence | R-6 — election, optimisable | B |
| 46 | Self-occupied house let for one day | Ineligible for nil AV — `HP-SELFOCC-002(b)` | B |
| 47 | Property self-occupied part-year and let part-year | Same as 46: ineligible for nil AV for the **whole** year | B |
| 48 | Co-ownership without definite shares | `HP-COOWN-001` applies only to **definite and ascertainable** shares; otherwise it is an AOP. Detect and block with an explanation | W |
| 49 | Municipal taxes paid this year for earlier years | Allowed — `HP-AV-TAX-001`, *"irrespective of when such taxes became payable"* | W |
| 50 | Municipal taxes **payable** but unpaid | Not allowed — the rule says **actually paid** | W |
| 51 | Loan refinanced | Interest on the new loan qualifies; the lender's certificate must name it (`HP-DED-CERT-001`, s.22(4)(b)) | W |
| 52 | Property sold during the pre-construction instalment run | Remaining instalments — `NEEDS VERIFICATION`. Flag; do not guess | N |
| 53 | Pre-construction spanning the FA 2026 s.38 change | R-15 — evaluate each instalment against its own year's pack | B |
| 54 | Vacancy allowance where expected letting value exceeds actual rent | `HP-AV-VACANCY-001` — annual value is the amount actually received | W |
| 55 | HP loss set off against slab vs special-rate income | R-7 | B |

## B7 — Deductions

| # | Case | Decision | Sev |
|---|---|---|---|
| 56 | NPS sub-cap and the s.123 ceiling | Sub-cap **first** (`T5-3`); the residue feeds `DED-NPS-SELF-001` | B |
| 57 | 80D lump-sum premium spanning a regime switch | The fraction is allowable each relevant year; in a year computed in the default regime it is simply disallowed, and **not** recoverable later | W |
| 58 | 80C investment made, policy lapses later | Schedule XV paras 2 and 3 carry conditions — **not read this session**. `NEEDS VERIFICATION` | N |
| 59 | 80E where interest payment starts mid-year | `DED-80E-002` — "initial tax year" is the year payment **starts**; the eight-year window runs from it | W |
| 60 | Employer NPS across two employers | Each employer's limit is a percentage of **that employer's** salary (`DED-NPS-EMP-001`, "of his salary"). Sum the two allowed amounts; do **not** apply one percentage to combined salary | B |
| 61 | `PF-AGGCAP-001` across two employers | The cap is per **tax year**, not per employer — s.17(1)(h) says *"in a tax year"*. Aggregate across employers | B |
| 62 | Chapter VIII exceeding gross total income | Capped — `DED-GTI-CAP-001` | W |
| 63 | Same interest claimed under s.22(1)(b) and s.131 | Blocked — `DED-80EX-001` | W |

## B8 — Capital gains interface

| # | Case | Decision | Sev |
|---|---|---|---|
| 64 | Rebate with special-rate income | **R-5** — the most dangerous case in this catalogue | B |
| 65 | `UNCLASSIFIED` gains present | Block with 409 (`T9-12`); never default | B |
| 66 | `SPECULATIVE` present | Business income → ITR-3, excluded from capital gains (`ITR-SPEC-001`) | B |
| 67 | `RateBasisHint` the pack does not recognise | Blocking error, not a slab fallback (R-10) | B |
| 68 | Portfolio's `asOf` advances after a draft is computed | Stale flag, not silent recompute (`11-filing-drafts.md` F4.1) | B |
| 69 | Capital loss declared to the employer to reduce TDS | Must **not** reduce it — `TDS-SAL-REDUCE-001` (`T7-4c`) | B |

## B9 — Documents

| # | Case | Decision | Sev |
|---|---|---|---|
| 70 | Same PDF uploaded twice | Idempotent on SHA-256 (`M7`) | N |
| 71 | Password-protected PDF | Prompt for the password; never store it. Failure is a clean `outcome = FAILED`, not an exception | W |
| 72 | Scanned image with no text layer | `outcome = FAILED` with "no text layer"; OCR is a later parser, a **new class** | W |
| 73 | Form 16 for a different PAN | **Reject** — compare against the profile PAN before any field is used | B |
| 74 | Form 16 for a different tax year | Reject, or offer to file it under the right workspace | W |
| 75 | Revised Form 16 issued later | New `IngestedDocument`; both retained; the later one supersedes by `certificateNo` + `lastUpdatedOn` | W |
| 76 | Part C from the last employer only | `DOC-F130-ANNEX-001` — check row 1(e) is non-zero (`G5.3`) | B |
| 77 | Two employers, both full standard deduction | `T8-1` — ₹2,14,500 on the worked figures | B |

---

# Part C — Test delta

| ID | Case | Expected |
|---|---|---|
| **T13-1** | TY 2026-27, default regime, salary ₹1,00,000 after standard deduction + LTCG ₹11,00,000 | **Rebate is NIL** (`REBATE-NEW-MR-002`, s.156(3)); the special-rate tax stands. The naive answer of "nil tax" is the failure being guarded |
| **T13-2** | Two employments with overlapping periods | Classified `CONCURRENT`, not rejected; standard-deduction double-count detected |
| **T13-3** | Three houses occupied for own residence, one carrying ₹6,00,000 interest | The optimiser elects the two that minimise tax, and the trace records **which two and why** |
| **T13-4** | Self-occupied house let for a single day | Nil annual value refused (`HP-SELFOCC-002(b)`) |
| **T13-5** | HP loss of ₹2,00,000 with both slab and special-rate income at a 25% surcharge band | Absorption order applied and **recorded in the trace**; the alternative order produces a different total, and the test asserts both |
| **T13-6** | Person moves Pune → Nashik on 1 October | HRA computed per period; two trace lines with different `limitedBy` |
| **T13-7** | LTA journeys in Feb 2026 and Aug 2026 | Counted against **different blocks** (2022-2025 and 2026-2029) |
| **T13-8** | Born 1 April 1967, TY 2026-27 | Literal reading applied; `UNVERIFIED_RULE` notice; both outcomes shown |
| **T13-9** | Senior citizen, no business income, ₹40 lakh capital gains | **No advance tax liability** (`ADV-SENIOR-001`) |
| **T13-10** | Capital gain realised in December | No shortfall interest if paid by the next instalment or 31 March (`s.425(4)`) |
| **T13-11** | Pre-construction instalments 1–2 in TY 2025-26, 3–5 in TY 2026-27, self-occupied | Each instalment evaluated against **its own year's pack**; the cap treatment differs between them |
| **T13-12** | Employer NPS from two employers | Two percentage limits on two salaries, summed — not one percentage on the total |
| **T13-13** | Employer contributions from two employers totalling ₹9,00,000 | `PF-AGGCAP-001` applied **across employers**, excess ₹1,50,000 |
| **T13-14** | Refund of ₹1,234.99 | Paise ignored → 1,234 → last figure 4 → ₹1,230. Negative-sign variant asserted per `ROUND-NEG-001` |
| **T13-15** | Pack superseded retrospectively after a draft was computed | Draft flagged stale with a diff; **no silent recompute** |
| **T13-16** | Form 16 whose PAN differs from the profile | Rejected before any field is consumed |
