# 05 — Domain model

Optimised for Open/Closed as the brief requires: **a new tax year, a Budget change, a new allowance
or deduction, or a new income head must be data or a new class — never an edit to existing logic.**

Everything is `RECOMMENDATION` unless it carries a rule ID. Design rules (`D-*`) come from
`02-lessons.md`.

---

## M0 — The four things that change, and how each is absorbed

| What changes | Frequency | Absorbed as | Never requires |
|---|---|---|---|
| A **value** (a slab, a cap, a percentage) | every Budget | a row in a rule pack JSON | a recompile |
| A **year** (new Act, new Rules, new Finance Act) | annually | a new rule pack directory | a code change (D2) |
| A **rule shape** (a cap the five typed shapes cannot express) | rarely | a new `CapShape` implementation | editing an existing shape |
| An **income head** or a **computation step** | rarely | a new `ComputationNode` registered in the pack's pipeline | editing the pipeline runner |

`INFERENCE (from the R1–R24 diff)`: 19 of 24 differences between TY 2025-26 and TY 2026-27 are
**re-identification** — the same idea under a different section, rule or form number. So the primary
extension axis is **rule identity**, not rule value, and identity must be first-class data.

---

## M1 — Tax-year registry and versioned rule packs

### M1.1 The registry

```java
public record TaxYearId(int startYear) {                  // 2026 → "TY 2026-27"
    public String label()      { return startYear + "-" + ((startYear + 1) % 100); }
    public LocalDate startsOn(){ return LocalDate.of(startYear, 4, 1); }
    public LocalDate endsOn()  { return LocalDate.of(startYear + 1, 3, 31); }
}

public record GoverningInstruments(
        Instrument act,          // Income-tax Act 1961  |  Income-tax Act 2025
        Instrument financeAct,   // Finance Act 2026
        Instrument rules) {}     // Income-tax Rules 1962 | Income-tax Rules 2026

public record Instrument(
        String citation,         // "Income-tax Act, 2025 (30 of 2025)"
        LocalDate assentedOn,    // 2025-08-21
        LocalDate inForceFrom,   // 2026-04-01
        String gazetteRef,       // "CG-DL-E-22082025-265620"
        URI sourceUrl,
        VerificationStatus status) {}

> **Erratum (W-010, 2026-09-19):** `assentedOn` cannot be mandatory. Subordinate legislation is
> **notified, not assented** — the Income-tax Rules 2026 have a gazette reference (G.S.R. 198(E),
> 20-03-2026) and a commencement date, but no assent date. The pack manifest in
> `16-getting-started.md` §S2 step 4 already omits it for the `rules` instrument, so this record and
> that JSON disagree. Corrected to `Optional<LocalDate> assentedOn`, with two factory methods —
> `Instrument.assented(...)` for an Act and `Instrument.notified(...)` for Rules — so the call site
> states which kind it is rather than passing a null.
> Verified by: `docs/taxplanning-design/16-getting-started.md` §S2, the `rules` block of
> `manifest.json`, which has `inForceFrom` and `gazetteRef` and no `assentedOn`.

public record TaxYearRegistryEntry(
        TaxYearId taxYear,
        GoverningInstruments instruments,
        Regime defaultRegime,           // NEW, for both years — REG-DEFAULT-001
        RulePackRef rulePack) {}
```

`YR-ACT-002` / `-003` / `-004` require **three** instruments per year, not one. The placeholder's
`TaxYearRegistryEntity` had Act and Rules; the Finance Act is where the surcharge tables and the
marginal-relief formulae actually live (`RATE-SUR-*`, `RATE-SUR-MR-001`), so omitting it means the
provenance of the highest-risk rules cannot be rendered.

`D-REGISTRY`: **no computation begins without resolving a registry entry.** This is the mechanism
that makes O-4 ("modelled and never used") structurally impossible.

### M1.2 Rule packs

```java
public record RulePack(
        TaxYearId taxYear,
        String packVersion,              // "2026-27.1" — bumped on any change
        String checksum,                 // SHA-256 over the canonicalised JSON
        GoverningInstruments instruments,
        Map<RuleId, RuleRow> rules,
        Map<ComponentCode, ComponentDefinition> components,   // see 10-extensibility.md E4
        Map<RegimeCode, RegimeSpec> regimes) {}               // see 10-extensibility.md E3
                                                              // each RegimeSpec carries its own PipelineSpec

public record RuleId(String value) {}     // "SAL-HRA-001" — matches ^[A-Z]{2,8}(-[A-Z0-9]{1,12}){1,3}$

> **Amended by `18-temporal-versioning.md` V4.** A `RuleRow` is a **stable ID plus an ordered list of
> `RuleVersion`s**, each with its own `Validity`, cap, conditions and the `AmendmentRef` that
> introduced it — because a value can change *inside* a tax year (`FACT (S6)`: the Fourth Amendment
> Rules 2026 commenced partly on 1 April 2026 and partly on 17 September 2026). The row also declares a
> **`TemporalAxis`**: `FACT (S2, s.197(3))` keys on an asset's **acquisition date**, not on the tax year.

```j
> **Erratum (W-027, 2026-09-19):** a **single** `packVersion` *"bumped on any change"* plus one
> `checksum` is superseded by `23-architecture-consolidation.md` A2, which versions the pack's four
> layers separately: `Map<RulePackLayer, RulePackRef> layers`.
>
> This is not tidying. With one version, correcting a typo in the claim catalogue bumps the pack and
> invalidates **every cached computation** — which is precisely the cost A2 exists to remove (G-11).
> The compute cache keys on `law` and `exec` only, so seven editorial revisions cost nothing, and
> `forms/` keeps its own version because CBDT notifies ITR forms on a different date from the Finance
> Act and revised them again mid-year (G.S.R. 822(E) substituted Forms 169 and 171 in September).
> Verified by: `23-architecture-consolidation.md` A2;
> `./mvnw test -pl backend -Dtest=RulePackLayerTest` — `sevenEditorialRevisionsInvalidateNothing`.
ava
public record RuleRow(
        RuleId id,
        Provision provision1961,          // may be ABSENT — SAL-ENTMT-001 has no 2025-Act counterpart
        Provision provision2025,
        RegimeApplicability regimes,      // AllRegimes | OnlyIn(set) | ExceptIn(set)
                                          //   — see 10-extensibility.md E3.3.
                                          //   NOT a tri-state: the count of regimes is data
        CapShape cap,
        List<Condition> conditions,
        Provenance provenance) {}

public record Provision(String instrumentCitation, String reference, Presence presence) {}
//   presence: PRESENT | ABSENT_IN_THIS_ACT | NOT_YET_VERIFIED

public record Provenance(
        URI sourceUrl,
        String sourceDescription,         // "Gazette CG-DL-E-20032026-271092, rule 279(1)(c) Table"
        DraftOrFinal draftOrFinal,        // DRAFT | FINAL | UNKNOWN
        LocalDate accessedOn,
        VerificationStatus status,        // VERIFIED | NEEDS_VERIFICATION | STATUS_UNCLEAR
        String whatToCheck) {}            // non-blank whenever status != VERIFIED
```

Four invariants, enforced at load (`D-FAILFAST`):

| # | Invariant | Prevents |
|---|---|---|
| I1 | `status != VERIFIED` ⟹ `whatToCheck` is non-blank | an unverified rule with no follow-up (`A4.2`) |
| I2 | every `RuleId` referenced by a `PipelineSpec` exists in `rules` | O-2's orphaned implementation |
| I3 | every `RuleId` in `rules` is reachable from at least one `PipelineSpec` | O-19's dead code |
| I4 | `checksum` matches the canonicalised content | O-16's mutated reference data |

`D-IMMUTABLE`: packs are classpath resources under `resources/rulepacks/<taxYear>/`, loaded once,
never written. There is no admin write path (`P3` item 11).

`D-YEARKEY`: the only accessor is

```java
RuleRow rule(TaxYearId year, RuleId id);      // throws MissingRuleException, never returns null
```

There is **no** overload without `year`, so O-33 cannot recur — forgetting the year does not compile.

### M1.3 `CapShape` — the five shapes, and why five is enough

`D-NOFORMULA`. Every cap in R1–R24 is one of these. Each is a sealed implementation of a single
interface, so a sixth shape is a **new class**, never an edit.

```java
public sealed interface CapShape
        permits Fixed, PercentOfBase, LeastOf, AggregateWithCeiling, SlabLadder, PerUnitPerPeriod {

    CapResult apply(CapContext ctx);      // returns value + the reason it was limited
}
```

| Shape | Fields | Rules that use it |
|---|---|---|
| `Fixed` | `Money amount` | `SAL-STDDED-001/-002`, `DED-80C-001`, `DED-NPS-SELF-001`, `RET-VRS-001`, `HP-CAP-SELFOCC-001`, `SETOFF-HP-001`, `ADV-THRESHOLD-001` |
| `PercentOfBase` | `BigDecimal percent`, `SalaryBaseId base` | `DED-NPS-EMP-001/-002`, `DED-80C-NPS-CAP-001`, `HP-DED-30-001`, `PF-RPF12-001`, `PERQ-ACCOM-LEASE-2026` (10%), `PERQ-ACCOM-HOTEL-2026` (24%) |
| `LeastOf` | `List<CapShape> limbs`, each **named** | `SAL-HRA-001` (3 limbs), `RET-GRAT-OTHER-001` (3), `RET-LEAVE-OTHER-001` (4), `RET-RETRENCH-001` (3), `DED-80GG-001` (3) |
| `AggregateWithCeiling` | `Set<RuleId> contributors`, `Money ceiling`, `Optional<CapShape> subCapPerContributor` | `DED-80C-001` (ceiling ₹1.5L, sub-cap on the NPS limb), `PF-AGGCAP-001` (₹7.5L over three), `DED-80D-006` (two ceilings), `HP-CAP-SELFOCC-003`, `RET-GRAT-AGG-001` |
| `SlabLadder` | `List<Band(lowerExclusive, upperInclusive, rate)>` | `RATE-SLAB-*`, `RATE-SUR-*` |
| `PerUnitPerPeriod` | `Money amount`, `Unit unit`, `Period period`, `int maxUnits` | `SAL-ALW-CEA-2026` (₹3,000 / month / child, max 2), `SAL-ALW-HOSTEL-2026`, `PERQ-CAR-2026` (per calendar month), `DED-80GG-001` limb (b) (₹5,000/month) |

> **Erratum (W-013, 2026-09-19):** this table has a gap, and it is not the one first reported.
>
> `SAL-STDDED-001`/`-002` **are** `Fixed` — ₹75,000 and ₹50,000 as the statute states them
> (`01-rulebook.md:373-374`). The "whichever is less" in their text is carried by a **third rule**,
> `SAL-STDDED-003` (`:375`, VERIFIED): *"capped at the salary itself, so it cannot create or enlarge
> a loss under the head."*
>
> The defect is that **`SAL-STDDED-003` appears in no row of this table**, so a VERIFIED cap has no
> shape. `D-NOFORMULA` says every cap in R1–R24 is one of these six, so it cannot be applied as a
> loose `min()` in step 11 without breaking that rule. It is a **`LeastOf`** with two named limbs:
>
> | limb | shape | names itself as |
> |---|---|---|
> | (a) | `Fixed` — the regime's amount, `SAL-STDDED-001` or `-002` | `"statutory amount"` |
> | (b) | the salary under the head, as a base | `"salary"` |
>
> Limb (b) binding is what tells a part-year joiner that their deduction was cut by their salary and
> not by the statute — the same reason `LeastOf` names its winning limb at all.
>
> `06-algorithms.md:61` step 11 already lists all three rules together with *"capped at salary"*, so
> the algorithm expects this; only the shape assignment was missing. `06-algorithms.md:508` cites
> `-003` for something different — the Form 16 multi-employer double-count check (`G5.3`) — which is
> a reconciliation use, not the computation.
>
> Verified by: `01-rulebook.md:373-375`; `06-algorithms.md:61`; this table's `Fixed` and `LeastOf`
> rows name neither `-003` nor any salary cap.

`LeastOf` returning a **named winning limb** is what makes `A4.4`'s third sentence possible — *"paying
more rent would help; increasing HRA alone would not"* falls out of knowing limb (b) bound, not (c).

`PerUnitPerPeriod` computes its own annual equivalent, so O-10's `annualLimitFixed / monthlyLimitFixed / 12`
divide-by-zero has no place to happen (`D-CAPTYPE`).

`SlabLadder.Band` stores `lowerExclusive`, and the arithmetic bound **is** `lowerExclusive`
(`RATE-SLAB-BAND-002`). A pack row written as `400001` fails a load-time validation that asserts
`band[i].lowerExclusive == band[i-1].upperInclusive` — so H-5's first limb is caught by the loader,
not by a test.

### M1.4 On-disk shape

```
backend/src/main/resources/rulepacks/
  2025-26/
    manifest.json          # taxYear, packVersion, checksum, instruments
    regimes.json           # RegimeSpec[] incl. election mechanics and per-regime pipelines  (E3)
    disallowances.json     # DisallowanceSet[] — shared, referenced by id   (the s.205(1) pattern)
    conditions.json        # ConditionSet[]    — shared, referenced by id   (the s.205(2) pattern)
    components.json        # the component-code registry                    (E4)
    rates.json             # RATE-SLAB-*, RATE-SUR-*, RATE-CESS-*, RATE-ROUND-*
    salary.json            # SAL-*
    perquisites.json       # PERQ-*
    retirement.json        # RET-*
    deductions.json        # DED-*
    houseproperty.json     # HP-*
    othersources.json      # OS-*
    setoff.json            # SETOFF-*
    tds.json               # TDS-*
    filing.json            # ADV-*, REL-*
    claim-catalogue.json   # ClaimCatalogueEntry[] — how to claim each rule           (22 C4)
    itr-forms.json         # ItrFormSpec[] — per tax year, historical sets retained  (21 I3)
    itr-schedules.json     # ItrSchedule[] — shared by reference across forms     (21 I5)
  2026-27/
    ... same files ...
```

`D2` is then literally true: adding TY 2027-28 is `cp -r 2026-27 2027-28`, edit values, update
provenance, bump the manifest. `X4` describes the authoring process.

---

## M2 — Component-code registry

> **Amended by `10-extensibility.md` E4.** The closed Java enum below was the wrong instrument: it
> makes "a new allowance is a data change" false, which is the brief's central requirement. The
> vocabulary is now a **registry loaded from the rule pack** (`ComponentDefinition`), with typo-safety
> from load-time validation and the declared partition rather than from the compiler. **Read E4 for
> the shape that ships.** The list below remains accurate as the *initial contents* of that registry
> and as the documentation of what each code means.

`D-ONECODE`. One registry, used by rule packs, engine, API and documents alike. O-7 (two
vocabularies), O-15 (a typo that still summed) and O-25 (a switch on a non-existent code) are all
load errors or request errors.

```java
// Initial contents of components.json, rendered as Java for readability.
// The shipped type is ComponentDefinition (10-extensibility.md E4.1), not an enum.
enum ComponentCode_InitialContents {
    // ---- cash salary ----
    BASIC              (SALARY,      CASH,     TAXABLE),
    DEARNESS_ALLOWANCE (SALARY,      CASH,     TAXABLE),
    HRA                (SALARY,      CASH,     TAXABLE),   // exemption via SAL-HRA-001
    LTA                (SALARY,      CASH,     TAXABLE),   // exemption via SAL-LTA-001
    SPECIAL_ALLOWANCE  (SALARY,      CASH,     TAXABLE),
    BONUS              (SALARY,      CASH,     TAXABLE),
    COMMISSION         (SALARY,      CASH,     TAXABLE),
    CHILDREN_EDUCATION_ALLOWANCE (SALARY, CASH, TAXABLE),  // SAL-ALW-CEA-2026
    HOSTEL_ALLOWANCE   (SALARY,      CASH,     TAXABLE),   // SAL-ALW-HOSTEL-2026
    UNIFORM_ALLOWANCE  (SALARY,      CASH,     TAXABLE),   // SAL-ALW-DUTY-001
    CONVEYANCE_ALLOWANCE(SALARY,     CASH,     TAXABLE),
    TRANSPORT_ALLOWANCE_DISABLED (SALARY, CASH, TAXABLE),  // SAL-ALW-TRANSPORT-DIS-2026

    // ---- non-cash perquisites: PERQ-* ----
    PERQ_ACCOMMODATION (PERQUISITE,  NON_CASH, TAXABLE),
    PERQ_MOTOR_CAR     (PERQUISITE,  NON_CASH, TAXABLE),
    PERQ_CHAUFFEUR     (PERQUISITE,  NON_CASH, TAXABLE),
    PERQ_MEALS         (PERQUISITE,  NON_CASH, TAXABLE),
    PERQ_GIFTS         (PERQUISITE,  NON_CASH, TAXABLE),
    PERQ_ESOP          (PERQUISITE,  NON_CASH, TAXABLE),
    PERQ_EMPLOYER_CONTRIB_EXCESS (PERQUISITE, NON_CASH, TAXABLE),  // PF-AGGCAP-001
    PERQ_EMPLOYER_CONTRIB_ACCRETION (PERQUISITE, NON_CASH, TAXABLE), // PF-AGGCAP-002
    PERQ_RPF_EXCESS_12PC (PERQUISITE, NON_CASH, TAXABLE),          // PF-RPF12-001

    // ---- employer retirement contributions ----
    EMPLOYER_PF        (EMPLOYER_CONTRIBUTION, NON_CASH, DEFERRED),
    EMPLOYER_NPS       (EMPLOYER_CONTRIBUTION, NON_CASH, DEFERRED),
    EMPLOYER_SUPERANNUATION (EMPLOYER_CONTRIBUTION, NON_CASH, DEFERRED),

    // ---- employee contributions and payments ----
    EMPLOYEE_PF        (EMPLOYEE_CONTRIBUTION, CASH_OUTFLOW, DEDUCTIBLE), // PF-EEDED-001 → DED-80C-001
    EMPLOYEE_NPS       (EMPLOYEE_CONTRIBUTION, CASH_OUTFLOW, DEDUCTIBLE),
    PROFESSIONAL_TAX   (STATUTORY_DEDUCTION,   CASH_OUTFLOW, DEDUCTIBLE), // SAL-PTAX-001

    // ---- retirement and termination receipts: RET-* ----
    GRATUITY, LEAVE_ENCASHMENT, COMMUTED_PENSION, VRS_COMPENSATION, RETRENCHMENT_COMPENSATION,

    // ---- other heads ----
    HP_RENT_RECEIVED, HP_MUNICIPAL_TAX, HP_INTEREST, HP_PRECONSTRUCTION_INTEREST,
    OS_SAVINGS_INTEREST, OS_TIME_DEPOSIT_INTEREST, OS_DIVIDEND, OS_FAMILY_PENSION,

    // ---- capital gains, fed from portfolio (D3) ----
    CG_SHORT_TERM, CG_LONG_TERM, CG_SPECULATIVE, CG_UNCLASSIFIED;

    private final ComponentKind  kind;
    private final CashNature     cashNature;    // D-CASHFLAG
    private final TaxTreatment   treatment;

    public boolean isCash()    { return cashNature == CASH; }
}
```

### Why `isCash()` is on the enum and not on a DTO

`H-15` / O-13: take-home was gross-minus-deductions, where gross included annualised car and driver
perquisite values. `D-CASHFLAG` makes this a **type-level** fact: `takeHome()` sums only components
where `code.isCash()`, and there is no path by which `PERQ_MOTOR_CAR` reaches it. `CASH_OUTFLOW` is a
third state — `EMPLOYEE_PF` reduces cash but is not salary income — so the three cannot be conflated.

### Code → rule mapping lives in the pack, not the enum

```json
{ "componentCode": "HRA",
  "feedsInto":     ["SAL-HRA-001"],
  "salaryBaseFor": ["SAL-DEF-HRA-001"] }
```

`INFERENCE`: this is the separation O-7 got wrong. The **code** is compile-time (a typo fails to
compile); the **mapping from code to rule** is year-specific data (`SAL-ALW-CEA-2026` vs its TY
2025-26 counterpart are different rule IDs for the same code). One enum, two packs.

### `D-CLOSEDSUM`

Gross salary is `Σ` over a **declared partition** of codes — the pack names which codes are in the
"Salaries" head. A component whose code is not in that partition is a **load error on the pack** or a
**400 on the request**, never a silent addend (O-15).

---

## M3 — Profile, employments, income sources, declarations and proofs

> **Amended by `11-filing-drafts.md`.** `TaxYearContext` below is not a per-year singleton; its contents become `DraftInputs` inside a `FilingDraft`, of which a person may hold any number per tax year. The cross-year ledgers in M3.4 stay at the **workspace** level, so two drafts cannot each consume the same once-in-a-lifetime deduction (invariant I8).

### M3.1 Why the profile is not the shape of the placeholder's

`FACT`: `SalaryProfileEntity` in the placeholder has **no date fields at all**
(`docs/taxplanning-context/00-INDEX.md`, open question 3, read this session). `INFERENCE (from D5,
DOC-F130-MULTI-001, SAL-HRA-003, SAL-LTA-BLOCK-001, RET-VRS-002)`: at least five rules need dates —
mid-year joining, the HRA "relevant period", the LTA calendar block, the once-in-a-lifetime VRS
deduction, and the monthly TDS projection. A dateless profile cannot express any of them.

### M3.2 Aggregates

```java
// ---------- the person ----------
TaxpayerProfile
    email                       // tenancy key, consistent with the repo's /{resource}/user/{email}
    dateOfBirth                 // RATE-SLAB-AGE-001 — "at any time during the tax year"
    residentialStatus           // RESIDENT | RNOR | NON_RESIDENT   (D7: present, not yet used)
    disabilityStatus            // DED-80U-001
    seniorCitizenDerived()      // derived from dateOfBirth + taxYear, never stored

// ---------- one per (profile, taxYear) ----------
TaxYearContext
    taxYear                     // TaxYearId — D-YEARKEY
    chosenRegime                // Optional<Regime> — REG-OPTOUT-YEARLY-001: per year, never on the profile
    employments                 // List<Employment>, ordered by periodFrom
    houseProperties             // List<HouseProperty>
    otherSources                // List<OtherSourceItem>
    capitalGainsSnapshot        // Optional<CapitalGainsSummary>  (D3, from portfolio)
    declarations                // List<DeclaredClaim>
    proofs                      // List<ProofOfClaim>
    ltaBlockHistory             // LtaBlockState — SAL-LTA-BLOCK-001, spans calendar years
    lifetimeEvents              // LifetimeEventLog — RET-VRS-002, RET-GRAT-AGG-001, RET-LEAVE-AGG-001

// ---------- one per employer, per period ----------
Employment
    employerName, employerTan, employerPan
    employerType                // GOVERNMENT | OTHER  — DED-NPS-EMP-001 (14% vs 10%)
    periodFrom, periodTo        // DOC-F130-A-001 "Period with the employer"
    isPrevious                  // TDS-SAL-12B-001: declared to the current employer via Form No. 122
    components                  // List<SalaryComponent>
    salaryRevisions             // List<SalaryRevision> — G4
    payrollCalendar             // pay dates, for the monthly projection

SalaryComponent
    code                        // ComponentCode — D-ONECODE
    annualAmount                // Money, non-null — D-MONEY
    monthlyAmount               // Optional<Money>; PerUnitPerPeriod caps need the monthly view
    daFormsPartOfRetirementBenefits  // boolean — SAL-DEF-HRA-001, SAL-DEF-GRAT-001, SAL-DEF-NPS-001

// ---------- house property ----------
HouseProperty
    occupancy                   // SELF_OCCUPIED | LET_OUT | DEEMED_LET_OUT | STOCK_IN_TRADE
                                //   H-12: drives HP-SELFOCC-001, HP-CAP-SELFOCC-001, HP-NEW-001
    coOwnershipSharePercent     // HP-COOWN-001
    rentReceived, municipalTaxesPaid, expectedLettingValue
    loans                       // List<HousingLoan>
    vacancyPeriods              // HP-AV-VACANCY-001

HousingLoan
    sanctionedOn                // DED-80EE-001 / DED-80EEA-001 window tests
    interestForYear
    preConstructionInterestTotal, preConstructionInstalmentsClaimed   // HP-DED-PRECON-001: 1..5
    completionWithinFiveYears   // HP-CAP-SELFOCC-001(i): ₹2,00,000 vs ₹30,000
    lenderCertificateHeld       // HP-CAP-SELFOCC-001(ii)

// ---------- claims ----------
DeclaredClaim   { ruleId, componentCode, amount, declaredOn, employmentId }
ProofOfClaim    { declaredClaimId, evidenceType, amount, receivedOn, documentRef, acceptedByEmployer }
```

### M3.3 Declarations versus proofs (D5)

`INFERENCE (from TDS-SAL-12BB-001, TDS-SAL-12BB-002, TDS-SAL-ADJUST-001)`: these are **two different
facts about the same claim**, and the gap between them is what causes March salary shock. Modelling
them as one number loses the most actionable signal the product has (`A3.3`).

| State | Meaning | Effect on TDS | Effect on the annual computation |
|---|---|---|---|
| Declared, no proof | Employer projecting on the declaration | reduces monthly TDS | claim included, **flagged unproven** |
| Declared, proof recorded, accepted | `TDS-SAL-12BB-001` satisfied | reduces monthly TDS | claim included |
| Declared, proof rejected or absent past the cut-off | employer reverses | **`TDS-SAL-ADJUST-001` recovers the shortfall in the remaining months** | claim excluded |
| Not declared, but true | user has it, never told the employer | no effect | claim included — **refund at filing** |

`r.205(2)`'s four evidence types (`TDS-SAL-12BB-002`) are the `evidenceType` enum: HRA landlord
details, LTC expenditure evidence, lender details for house-property interest, and Chapter VIII
investment or expenditure evidence.

### M3.4 Cross-year state — the part a single-year model cannot hold

| State | Why it spans years | Rule |
|---|---|---|
| `LtaBlockState` — journeys claimed per **calendar** year, current block, carry-over flag | four-calendar-year blocks, and the carried journey does not count against the new block | `SAL-LTA-BLOCK-001`, `SAL-LTA-CARRY-001` |
| `LifetimeEventLog` — VRS claimed in which tax year | once in a lifetime, and mutually exclusive with s.157 relief on the same amount | `RET-VRS-002` |
| Gratuity and leave-encashment amounts already allowed | aggregate limits are `A − B` across all earlier years and employers | `RET-GRAT-AGG-001`, `RET-LEAVE-AGG-001` |
| `preConstructionInstalmentsClaimed` | five equal instalments across five tax years | `HP-DED-PRECON-001` |
| Carried-forward house-property loss | eight years — **and destroyed, not deferred, in the new regime** | `SETOFF-CF-HP-001`, `SETOFF-NEW-002` |
| `DED-80E-002` initial tax year | eight-year window from the year interest payment started | `DED-80E-002` |

`RECOMMENDATION`: model each as an **append-only ledger keyed on (profile, rule, tax year)**, not as a
mutable counter. A recomputation must be able to reproduce any past year's answer, and a counter that
has been decremented cannot.

---

## M4 — Documents: ingestion, parse result, confidence, reconciliation

`K-13` retargeted from a CTC letter to Form No. 130 / Form 16 (D4).

```java
IngestedDocument                      // immutable, one per upload
    documentId, profileId, taxYear
    documentType                      // FORM_16 (TY 2025-26) | FORM_130 (TY 2026-27)
                                      // | FORM_123 (perquisites) | FORM_122 (previous employer)
    storageRef, sha256, uploadedAt, pageCount

ParseAttempt                          // immutable, N per document — reparsing is a new attempt
    attemptId, documentId
    parserId, parserVersion           // DocumentParser SPI — X2
    startedAt, finishedAt, outcome    // SUCCESS | PARTIAL | FAILED
    extractedFields                   // List<ExtractedField>
    unmappedText                      // K-13: what could not be mapped, kept verbatim
    parserNotes

ExtractedField
    fieldPath                         // "partC.annexureI.row2b"  — DOC-F130-ROWS-001
    rawText                           // exactly as it appeared
    interpretedValue                  // Optional<Money> or Optional<Boolean> or Optional<String>
    confidence                        // 0.0 .. 1.0
    pageNumber, boundingBox           // for "show me where this came from"
    mappedTo                          // Optional<ComponentCode> or Optional<RuleId>

DocumentConfirmation                  // the user's act — nothing is used until this exists
    attemptId, confirmedBy, confirmedAt
    acceptedFields                    // fieldPath → value the user accepted (may differ from parsed)
    rejectedFields
```

### M4.1 Confidence bands and what each does

| Band | Treatment |
|---|---|
| ≥ 0.95 | pre-filled, shown normally |
| 0.70 – 0.95 | pre-filled, **flagged for review**, highlighted in the confirmation screen |
| < 0.70 | **not** pre-filled; shown as a question with the raw text and the page image |
| absent | field listed under `unmappedText`, user asked to enter it manually |

`D-NOSILENT`: a low-confidence field is **never** silently defaulted to zero. That is O-1's failure
mode transplanted into document parsing, and it would be worse there because the number *looks*
sourced.

### M4.2 Reconciliation

```java
ReconciliationReport
    computationId, documentIds
    lines                             // List<ReconciliationLine>
    verdict                           // MATCHED | MINOR_DIFFERENCE | MATERIAL_DIFFERENCE | CANNOT_COMPARE

ReconciliationLine
    ruleId or formRow                 // e.g. DOC-F130-ROWS-001 row 6 vs the trace's SAL income line
    documentValue, computedValue, difference
    explanation                       // one of a closed set of reasons, below
```

The closed set of reasons is the design point — `INFERENCE (from R20, R21)`: **most differences are
expected and explainable**, and a reconciliation that flags them all as errors is noise.

| Reason | Rule |
|---|---|
| Claim declared to the employer but not proven, so excluded from Part C | `TDS-SAL-12BB-001`, `M3.3` |
| Deduction the user is entitled to but never declared — refund due | `TDS-SAL-12B-001` |
| Other-head income the user declared, which could only **raise** TDS | `TDS-SAL-REDUCE-001` |
| Part B "tax deducted" is tax **plus** surcharge **plus** cess; Part C separates them | `DOC-F130-TAXSUM-001` |
| Only the last employer issued Part C, so it already includes the previous employer's figures | `DOC-F130-ANNEX-001`, `DOC-F130-MULTI-001` |
| The employer used a different regime from the one now being computed | `DOC-F130-C-A` row A |
| Rounding to the nearest ₹10 | `RATE-ROUND-002` |
| Capital gains are in the computation and cannot be in Form No. 130 at all | D3 |

`CANNOT_COMPARE` is a first-class verdict: with a multi-employer year and only Part A/B blocks from
one employer, the comparison is genuinely not available, and saying so beats inventing a total.

---

## M5 — Computation, trace and scenarios

### M5.1 The computation is a value, and the trace **is** the result

```java
public record Computation(
        ComputationId id,
        TaxYearId taxYear,
        Regime regime,
        RulePackRef rulePack,            // exact pack version + checksum — reproducibility
        InputsFingerprint inputs,        // hash of the inputs, for cache and audit
        List<TraceLine> trace,           // ordered, complete — D-TRACE
        Money totalIncome,
        Money taxPayable,
        Instant computedAt) {

    Money valueOf(RuleId r) { ... }      // every figure is addressable by rule ID
}

public record TraceLine(
        int sequence,
        RuleId ruleId,
        String label,                    // human text, from the pack, not hard-coded
        StepKind kind,                   // INCOME | EXEMPTION | DEDUCTION | SET_OFF | TAX | RELIEF | ROUNDING
        List<ComponentCode> inputs,
        Money in,
        Money out,
        Optional<String> limitedBy,      // LeastOf's winning limb name, or the cap that bound
        List<Notice> notices,            // D-TRACE: notices live here and cannot throw (O-27)
        Provenance provenance) {}        // rendered by A4.2

public record Notice(Severity severity, String message, List<RuleId> relatedRules) {}
//   Severity: INFO | ATTENTION | ACTION_REQUIRED | UNVERIFIED_RULE
```

`D-TRACE`: there is no separate "warnings" collection (O-5) and notices cannot abort (O-27). A notice
is data hanging off the line that produced it.

`D-VIEWFROMTRACE`: JSON, PDF and ITR figures are all **projections** of `trace`. A PDF section with no
matching trace lines renders as nothing, visibly — O-20 becomes observable instead of silent.

### M5.2 Scenarios

```java
public record Scenario(
        ScenarioId id, String label,      // "Current", "If I restructure", "Old regime"
        TaxYearId taxYear,
        ScenarioInputs inputs,            // a full, immutable input set
        Computation result) {}

public record ScenarioDiff(
        Scenario a, Scenario b,
        List<DiffLine> lines,             // full outer join on RuleId — A1.2
        Money totalDifference) {}
```

`D-SCENARIO`: a "saving" only ever exists as `ScenarioDiff.totalDifference`. There is no scalar
`annualSavingVsCurrent` field, so O-21 ("does not consider the current regime") has nowhere to live —
the baseline is an explicit operand.

`INFERENCE`: this one type serves `A1` (regime comparison), `A2` (optimiser pricing), `A3` (next-best
action ranking) and `F18` (save and compare). `D-MARGINAL` is just "build a scenario with the
component changed and diff it", which is why measuring marginal value is cheap rather than a special
subsystem.

---

## M6 — Money

`D-MONEY`, driven by O-9 and O-12 (nullable `Long` unboxing) and by CLAUDE.md's existing rule that
money is never asserted with a bare `assertEquals`.

```java
public record Money(BigDecimal amountInRupees) implements Comparable<Money> {
    public static final Money ZERO = of(0);
    // canonical scale 2, HALF_UP — see the table below
}
```

| Decision | Choice | Why |
|---|---|---|
| Representation | `BigDecimal`, **never** `double`, `Long` or `long` | The repo already treats bare-double money as a latent failure (`MoneyAssert`, CLAUDE.md). `Long` paise invites the O-9/O-12 unboxing class |
| Nullability | **non-null always**; absence is `Money.ZERO` or `Optional<Money>` at the API boundary, resolved once on ingestion | O-9, O-12 |
| Internal scale | **2 decimal places**, `RoundingMode.HALF_UP` | Rupees and paise; percentages of salary produce paise |
| Percentage arithmetic | `BigDecimal` with **scale 10** during multiplication, rounded to scale 2 on assignment to a `Money` | keeps `30% of annual value` and `10% of salary` exact enough that a golden test derived by hand matches |
| Mongo storage | `Decimal128` | exact; avoids the `double` round-trip |
| JSON | string, not number | avoids JavaScript `Number` precision loss on large rupee amounts |

### Where rounding happens — exactly two places

`RATE-ROUND-003`, from `RATE-ROUND-002` (s.516) and `RATE-ROUND-001` (ss.288A/288B):

```
1.  Total income, immediately before the slab walk       → nearest ₹10
2.  The final amount payable or refundable               → nearest ₹10
```

Nowhere else. Specifically **not** per slab band (O-14's `Math.round` per band), **not** after each
exemption, **not** after each deduction. `X5` carries a property test: for any input, the sum of the
trace's per-band tax equals the tax computed on the whole, exactly.

`Money.roundToNearestTen()` implements s.516 literally — ignore paise, then up if the last digit is
5 or more, down if less.

---

## M7 — MongoDB collections, indexes and validation

Following the repo's conventions: `snake_case` collections and `@Field` names, `@MongoId`, entities
implement `AuditableEntity`, derived queries preferred.

| Collection | Key | Indexes | Notes |
|---|---|---|---|
| `tax_profiles` | `email` | unique `{email}` | `TaxpayerProfile`. One per user |
| ~~`tax_year_contexts`~~ | — | — | **Superseded by `11-filing-drafts.md` F8.** The unique index on `{email, tax_year}` was wrong: it allowed only one input set per person per year. Replaced by `tax_year_workspaces` (unique on `{email, tax_year}` — the workspace *is* the singleton) plus `tax_filing_drafts` (**no** unique constraint) and `tax_draft_revisions` |
| `tax_employments` | `{email, tax_year, employer_tan, period_from}` | unique `{email, tax_year, employer_tan, period_from}`; `{email, tax_year}` | `DOC-F130-MULTI-001`: TAN **plus period**, because one employer can appear twice |
| `tax_house_properties` | `{email, tax_year, property_id}` | `{email, tax_year}` | |
| `tax_declarations` | `{email, tax_year, claim_id}` | `{email, tax_year, rule_id}` | `M3.3` |
| `tax_proofs` | `{email, tax_year, proof_id}` | `{email, tax_year, declared_claim_id}` | `M3.3` |
| `tax_documents` | `document_id` | unique `{sha256}`; `{email, tax_year}` | **unique on content hash** — re-uploading the same PDF is idempotent |
| `tax_parse_attempts` | `attempt_id` | `{document_id, started_at desc}` | append-only; never updated |
| `tax_document_confirmations` | `attempt_id` | unique `{attempt_id}` | nothing is used until this exists |
| `tax_computations` | `computation_id` | `{email, tax_year, computed_at desc}`; `{email, tax_year, inputs_fingerprint, rule_pack_version}` | the second index makes recomputation cacheable and **provably** tied to a pack version |
| `tax_scenarios` | `scenario_id` | `{email, tax_year}` | |
| `tax_lta_block_state` | `{email, block_start_year}` | unique `{email, block_start_year}` | `SAL-LTA-BLOCK-001` — **calendar** years, deliberately not keyed on tax year |
| `tax_lifetime_events` | `{email, event_type, tax_year}` | unique `{email, event_type}` where `event_type = VRS_CLAIMED`; `{email, event_type}` | `RET-VRS-002`, `RET-GRAT-AGG-001`, `RET-LEAVE-AGG-001`. Append-only |
| `tax_carry_forwards` | `{email, head, origin_tax_year}` | `{email, head}` | `SETOFF-CF-HP-001`, `DED-80E-002`, `HP-DED-PRECON-001` |

### Collections that deliberately do not exist

| Not a collection | Why |
|---|---|
| `tax_slab_policies`, `allowance_limits`, `allowance_catalogue`, `perquisite_policy`, `tax_year_registry` | `D-IMMUTABLE` — rule packs are classpath artefacts. This is the **direct structural fix for O-16**: a test cannot corrupt reference data that is not in the database |

### Validation

- **Mongo schema validation** on every collection, generated from the record definitions at build time.
- **Load-time validation** of rule packs (`M1.2` I1–I4, plus the `SlabLadder` continuity check).
- **Bean Validation** on request DTOs (`D-VALIDATE`, O-29), asserted by a test that every
  `@RequestBody` parameter carries `@Valid`.
- **`cleanDatabase()`**: every collection above is added to `AbstractIntegrationTest`, as CLAUDE.md
  requires. `INFERENCE`: because no rule data lives in Mongo, none of them needs to be added to
  `SEEDED_REFERENCE_COLLECTIONS` — which is what made O-16 possible.
