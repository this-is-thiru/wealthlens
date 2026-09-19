# 19 — Engineering foundations

A pass from inside the codebase rather than from the statute. Everything here is something I would hit
in the first fortnight of building, and four of them are things the previous eighteen documents got
wrong **against this repository specifically**.

---

## E1 — Money: my design fails the existing build on day one

### The finding

`FACT` (`backend/src/test/java/com/thiru/wealthlens/architecture/ArchitectureTest.java:102-107`, read
this session):

```java
static final ArchRule money_rounding_lives_in_one_place =
        noClasses()
                .that().resideOutsideOfPackage("..shared.util.money..")
                .should().callMethod(java.math.BigDecimal.class, "setScale",
                                     int.class, java.math.RoundingMode.class)
                .because("TL-8: paise rounding belongs to TMoney; "
                       + "a second copy is how two roundings diverge");
```

> **Erratum (W-002, 2026-09-19):** this quote omits a clause the real rule carries —
> `.and().haveSimpleNameNotEndingWith("ChargeRounding")`, an exemption for the charges engine's
> configurable per-rule policy, documented there as *"a different thing from canonicalising a stored
> amount"*. Implementing from this quote alone would drop the exemption and break the charges build.
> The amendment below must **preserve** it.
> Verified by: `backend/src/test/java/com/thiru/wealthlens/architecture/ArchitectureTest.java:102-108`.

`FACT` (`backend/src/main/java/com/thiru/wealthlens/shared/util/money/TMoney.java:25-36`): the
codebase's canonical money type is a **static utility over `double`** — *"Canonical rupee amounts held
as `double` … The problem this solves is not precision — it is canonicalisation."*

`05-domain-model.md` M6 and `15-review-findings.md` Part G specified `Money` as a `BigDecimal` record
whose `ofExact` and `roundToNearestTenPerSection516` both call `setScale`. **Both would be rejected by
an ArchUnit rule that already exists and already passes.** I designed against a repository convention
I had not read.

### Does `taxplanning` actually need `BigDecimal`?

The decision document is careful and anticipates this. `FACT`
(`docs/trade-ledger/money-representation-analysis.md`, §7, read this session) lists five triggers that
would make `BigDecimal` the answer. **Two of them fire here, and one of them names this case:**

| Trigger (§7, verbatim) | Fires? |
|---|---|
| *"**A regulator or auditor requires exact decimal storage.** For a personal tracker this is remote; **for anything filed on someone else's behalf it is not**"* | **Yes.** D6 ships ITR-form figures and a PDF the user files on. TL-8 anticipated precisely this module |
| *"**Sub-paise amounts ever need storing** — a per-unit rate, an unrounded intermediate"* | **Yes.** `Rate` holds 12.5%, 30%, one-third and one-fifth; percentage-of-salary intermediates are carried at scale 12 before the two statutory rounding points of `RATE-ROUND-003` |
| Multi-currency | No — `O11` keeps INR only |
| Aggregation moves into MongoDB | No — computation is in-memory and pure (`D-PURE`) |
| Option B decays | Not applicable to a new module |

And the cost that made `BigDecimal` *premature* for the ledger does not exist here. `FACT` (§4, Option
C): *"~170 declarations … Every existing document migrates from BSON `Double` to `Decimal128` … It
would land immediately after two large branches, on code that is working and tested."* **`taxplanning`
is greenfield: zero declarations, no documents, no migration.**

### The resolution

`RECOMMENDATION`: `taxengine` uses a `BigDecimal`-backed `Money`, **and the ArchUnit rule is amended
deliberately, not excluded quietly.** The rule's stated reason is *"a second copy is how two roundings
diverge"* — so the amendment must preserve that property, not weaken it:

```java
static final ArchRule money_rounding_lives_in_one_place =
        noClasses()
                .that().resideOutsideOfPackage("..shared.util.money..")
                .and().resideOutsideOfPackage("..taxengine.money..")     // + this
                .should().callMethod(java.math.BigDecimal.class, "setScale",
                                     int.class, java.math.RoundingMode.class)
                .because("TL-8: paise rounding belongs to TMoney. taxplanning is TL-8 §7's "
                       + "'filed on someone else's behalf' trigger and uses an exact decimal Money; "
                       + "its rounding likewise lives in exactly one package.");
```

> **Erratum (W-013, 2026-09-19):** `Money` and `Rate` do **not** live in `taxengine.money` —
> they live in **`taxrules.money`**. `taxrules` may not depend on `taxengine` (T1), and a rule
> pack holds rupee figures by its nature — slab band bounds, the 80C ceiling, the standard
> deduction — so a cap shape in `taxrules.cap` cannot express a limit without them. Decided as
> option (A) of `STATE.md` **B-001**.
> Verified by: `./mvnw test -pl backend -Dtest=WealthLensModulithTest` failed with
> *"Module 'taxrules' depends on module 'taxengine' via …CapResult -> …taxengine.money.Money"*
> before the move, and passes after it.

Plus **three new rules**, because two money representations in one codebase is a hazard that must be
made structural rather than remembered:

```java
/** The two representations must never meet except at one declared seam. */
static final ArchRule taxengine_does_not_use_TMoney =
        noClasses().that().resideInAPackage("..taxengine..")
                .should().accessClassesThat().belongToAnyOf(TMoney.class)
                .because("taxengine is exact-decimal; TMoney is canonicalised double. "
                       + "Conversion happens only in the portfolio adapter.");

static final ArchRule taxengine_has_no_raw_double_money =
        noClasses().that().resideInAPackage("..taxengine..")
                .should().haveFieldOfType(double.class)          // narrowed by name predicate
                .because("D-MONEY: money is Money, rates are Rate, and nothing else is either.");

static final ArchRule only_the_adapter_converts =
        classes().that().callMethod(MoneyBridge.class, "fromTMoney", double.class)
                .should().resideInAPackage("..taxplanning.portfolio..")
                .because("one seam, named, testable — not a conversion scattered per call site.");
```

### The seam

```java
package com.thiru.wealthlens.taxplanning.portfolio;

/** The ONLY place the two money representations meet. */
public final class MoneyBridge {

    /**
     * portfolio speaks canonicalised double (TL-8 Option B); taxengine speaks exact decimal.
     * The conversion is lossless in this direction because TMoney guarantees the double is
     * already canonical to paise — that guarantee is the whole point of TL-8.
     */
    public static Money fromTMoney(double canonicalRupees) {
        return Money.ofExact(BigDecimal.valueOf(canonicalRupees));
    }

    /** Refused deliberately: taxengine figures must not re-enter a double world. */
    public static double toTMoney(Money m) {
        throw new UnsupportedOperationException(
                "A tax figure is exact by construction (TL-8 §7). Converting it back to double "
              + "would discard that guarantee at the one point it matters — the figure the user files on.");
    }
}
```

`INFERENCE`: the one-way seam is the design. Capital gains flow **in** from `portfolio` and are widened
losslessly; nothing flows back. That also settles a question `13-edge-cases.md` R-10 left implicit —
the boundary is not just about *who computes rates*, it is about *which number system is authoritative
on each side*.

---

## E2 — Rule packs are unauthorable by hand, and the whole design rests on authoring them

### The problem, sized

Two years × roughly 200 rules × temporal versions (`18`) × cross-references to `RuleId`,
`ComponentCode`, `RegimeCode`, `DisallowanceSetId`, `ConditionSetId`, `NodeId` and `AmendmentRef`.
That is several thousand lines of hand-written JSON in which **every identifier is a string that must
match another string in another file**. `10-extensibility.md` E4 removed the compiler from that job on
purpose, and put nothing in its place except load-time validation — which catches the error after it
is written, in a stack trace, at startup.

`INFERENCE`: this is the single largest practical risk in the design. Every other document assumes
packs exist and are right.

### E2.1 — A JSON Schema, generated from the records

```java
// build step: taxrules-schema-gen
@GeneratesJsonSchema(target = "rulepack-schema-v1.json")
public record RulePackDocument(String taxYear, String packVersion, int schemaVersion,
                               GoverningInstruments instruments,
                               List<RuleRowDocument> rules,
                               List<RegimeSpecDocument> regimes,
                               List<ComponentDefinitionDocument> components,
                               List<DisallowanceSetDocument> disallowances,
                               List<ConditionSetDocument> conditions) {}
```

Emitted to `backend/src/main/resources/rulepacks/rulepack-schema-v1.json` and referenced by every pack
file via `"$schema"`. `RECOMMENDATION`: this buys **IDE completion and inline errors while typing a
pack**, which is worth more than any amount of load-time validation. The sealed hierarchies
(`CapShape`, `Condition`, `Validity`, `ContinuationPolicy`) map to JSON Schema `oneOf` with a
discriminator, so the editor can offer the right fields per shape.

### E2.2 — A typed authoring DSL for the hard rules

JSON is fine for a slab table and miserable for `DED-80D-001`'s seven interacting limits. `RECOMMENDATION`:
author the awkward rules in a **Java DSL that emits the JSON**, checked in alongside it.

```java
public final class Pack2026_27 {

    static RulePack build() {
        return RulePackBuilder.forYear(2026)
            .instrument(ACT,      "Income-tax Act, 2025 (30 of 2025)", "CG-DL-E-22082025-265620")
            .instrument(FINANCE,  "Finance Act, 2026 (4 of 2026)",     "CG-DL-E-31032026-271439")
            .instrument(RULES,    "Income-tax Rules, 2026 G.S.R. 198(E)", "CG-DL-E-20032026-271092")

            .rule(RuleIds.DED_80D_001).axis(TAX_YEAR)
                .provision2025("s.126(2)(a)")
                .onlyIn(S202_OPTED_OUT)
                .cap(aggregate()
                        .contributor(INSURANCE_SELF_FAMILY)
                        .contributor(PREVENTIVE_SELF_FAMILY)
                        .ceiling(rupees("25000"))
                        .substituteCeilingWhen(
                             anyOf(ageAtLeast(60, SELF), ageAtLeast(60, INSURED_PERSON)),
                             rupees("50000"))                       // DED-80D-007
                        .subLimit(PREVENTIVE_SELF_FAMILY, rupees("5000")))   // DED-80D-005
                .verified("https://egazette.gov.in/WriteReadData/2025/265620.pdf",
                          "s.126(2)(a), (3), (8)(a)", LocalDate.of(2026, 9, 18))
            .and()
            .build();
    }
}
```

`RECOMMENDATION`: **the DSL is not the artefact.** It emits JSON, the JSON is checked in, and a test
asserts `build().toJson()` equals the checked-in file. That keeps the pack reviewable as data — which
`15-review-findings.md` C4's reviewed diff requires — while making it writable as code.

### E2.3 — A CLI for the operations that recur

```
mvn taxrules:new-year          -Dfrom=2026-27 -Dto=2027-28    # cp -r with provenance reset to NEEDS_VERIFICATION
mvn taxrules:lint              -Dyear=2027-28                 # 15 C4 + 18 V7
mvn taxrules:diff              -Dfrom=2026-27 -Dto=2027-28    # the reviewed file
mvn taxrules:unverified                                       # X4.3's generated backlog
mvn taxrules:explain           -Drule=SAL-HRA-001 -Dyear=2026-27
mvn taxrules:generate-goldens  -Dyear=2027-28                 # 15 C5 — inputs only, not answers
```

`new-year` resetting every carried value's status to `NEEDS_VERIFICATION` is the mechanical fix for
H-2. A value is `VERIFIED` for the new year only when someone re-opens the source and says so.

---

## E3 — Compile the pack; generate the constants

This resolves a tension I left open in `10-extensibility.md` E4: the vocabulary must be **data** (so a
new allowance is a data change) yet Java must reference a few codes **safely**.

`RECOMMENDATION`: a Maven plugin bound to `generate-sources` that, from the checked-in packs:

1. **validates and links** — all invariants I1–I18, across all packs, at **build** time;
2. **emits an indexed binary form** — rules resolved to array indices, so startup is a read, not a
   parse-and-validate of 2,500 rules;
3. **generates constants**:

```java
// GENERATED — do not edit. Source: rulepacks/**/*.json
public final class RuleIds {
    public static final RuleId SAL_HRA_001    = new RuleId("SAL-HRA-001");
    public static final RuleId DED_80D_001    = new RuleId("DED-80D-001");
    // …
}

public final class ComponentCodes {
    public static final ComponentCode EMPLOYER_PF  = new ComponentCode("EMPLOYER_PF");
    public static final ComponentCode EMPLOYEE_PF  = new ComponentCode("EMPLOYEE_PF");
    // …
}
```

> **Erratum (W-032b, 2026-09-19):** the constants are **committed source**, not emitted into
> `target/generated-sources`, by the developer's decision. Both properties in the table below still
> hold; the mechanism differs.
>
> - A code removed from every pack changes what `GeneratedConstantsTest` regenerates, so the test
>   fails; regenerating deletes the constant, and every Java reference then **fails to compile**.
> - A typo in a pack fails that test — and tests run in the build, so it fails the **build**, not
>   startup.
>
> Why: a build-time generator needs `exec-maven-plugin` and `build-helper-maven-plugin` added to the
> **root pom every module inherits**, on every build, for one module's convenience — and a generator
> bug then breaks `mvn` for everyone before a single test runs. Committed constants are also visible
> in the IDE and in code review, which matters: `RuleIds.RATE_SLAB_NEW_2026` appearing in a diff is
> how a reviewer sees a rule being consumed.
>
> Two details the spec does not mention and the formatter forces. The generated file must be a
> **spotless fixed point**, or the formatter edits it and the test fails on every build: so the
> `import` is emitted **only when a constant uses it** (spotless strips an unused import — which
> bites today, because `exec/components.json` is still a stub and `ComponentCodes` is empty), and
> the javadoc is **pre-wrapped** rather than left for the formatter to reflow.
>
> Regenerate with
> `./mvnw test -pl backend -Dtest=GeneratedConstantsTest -Dtaxrules.regenerate=true`.
> Verified by: `./mvnw test -pl backend -Dtest=GeneratedConstantsTest` — 12 tests.

| Property | Effect |
|---|---|
| A code exists in data | `ComponentCodes.X` compiles |
| A code is **removed** from every pack | every Java reference to it **fails to compile** — the strongest possible deprecation signal |
| A typo in Java | does not compile |
| A typo in a pack | fails the **build**, not startup |
| A new allowance | a JSON row; regenerate; no hand-written enum touched |

`INFERENCE`: this is strictly better than both alternatives I weighed in E4. The closed enum gave
compile safety and blocked data-driven extension; raw strings gave extension and no safety. Generation
gives both, and it makes removal safe too, which neither did.

---

## E4 — Testing beyond golden files

`08-golden-tests.md` is hand-derived arithmetic, which is right and does not scale past a few hundred
cases. Four additions:

### E4.1 — Metamorphic properties

Relations that must hold for **any** input, with no oracle needed:

```java
@Property void moreIncomeNeverMeansLessTax(@ForAll @Money BigDecimal a, @ForAll @Money BigDecimal b) {
    assume(a.compareTo(b) < 0);
    assertThat(tax(a)).isLessThanOrEqualTo(tax(b));          // guards missing marginal relief
}

@Property void aDeductionNeverIncreasesTax(@ForAll Inputs in, @ForAll @Positive Money d) {
    assertThat(tax(in.plusDeduction(DED_80C_001, d))).isLessThanOrEqualTo(tax(in));
}

@Property void splittingAComponentIsNeutral(@ForAll Inputs in, @ForAll @Positive Money x) {
    // BASIC 10,00,000 == BASIC 6,00,000 + BASIC 4,00,000 : the engine must not care how it arrived
    assertThat(tax(in.splitComponent(BASIC, x))).isEqualTo(tax(in));
}

@Property void traceSumsToTheTotal(@ForAll Inputs in) {
    Computation c = compute(in);
    assertThat(c.trace().stream().filter(TraceLine::isTerminal).reduce(...)).isEqualTo(c.netPayable());
}

@Property void proratingAFullYearIsIdentity(@ForAll Inputs in) {
    assertThat(tax(in.withPeriod(FULL_YEAR))).isEqualTo(tax(in));   // C3's proration engine
}
```

`INFERENCE`: `moreIncomeNeverMeansLessTax` is the property that a **missing marginal relief violates**,
for any income near a threshold — it would have caught H-1 without anyone knowing the threshold was
₹50,00,000.

### E4.2 — An independent oracle for the golden expectations

Hand-deriving 60 cases is feasible; 600 is not, and `D-GOLDEN` forbids deriving them from the engine.
`RECOMMENDATION`: a **deliberately naive, deliberately separate** reference implementation — a few
hundred lines of Python, written from the rulebook by a different person, with no shared code, no
packs, and hard-coded numbers.

```
test-oracle/
  oracle.py            # naive, hard-coded, readable; ~400 lines
  cases.csv            # inputs only
  expected.csv         # generated by oracle.py, CHECKED IN and reviewed
```

The Java test reads `expected.csv`. Disagreement means **one of the two is wrong** and a human decides
which. `INFERENCE`: two independent implementations of the same statute is the cheapest real assurance
available here, and it is exactly how the placeholder's O-3 would have been caught — a naive oracle
written from the rulebook would have put surcharge at ₹50 lakh.

### E4.3 — Trace snapshots

The trace is a contract (`D-VIEWFROMTRACE`: JSON, PDF, ITR figures and diffs all read it).

```java
@Test void traceShapeIsStable() {
    approvals.verify(compute(CANONICAL_CASE).trace().toStableString());
}
```

An unreviewed change to trace shape becomes a failing diff, not a silently broken PDF.

### E4.4 — Fuzz the pack loader

```java
@Property void loaderNeverThrowsAnythingButPackValidationException(@ForAll @CorruptedJson String s) {
    assertThatThrownBy(() -> loader.load(s)).isInstanceOf(PackValidationException.class);
}
```

`INFERENCE`: packs are checked-in artefacts, so this is not a security boundary — it is about the
**quality of the failure**. A `NullPointerException` at startup on a mistyped pack costs an hour; a
named validation error costs a minute.

### E4.5 — Test data builders

Without these, the 60 golden cases in `08` and the 77 edge cases in `13` are unwritable.

```java
var in = ATaxpayer.resident().aged(35)
        .employedBy(ACME).from("2026-04-01").to("2027-03-31")
        .with(BASIC, lakhs(12)).with(HRA, lakhs(6)).with(DEARNESS_ALLOWANCE, lakhs(1.2))
        .rentingIn(BENGALURU).at(perMonth(30_000))
        .build();
```

---

## E5 — A closed error catalogue

Errors are currently scattered across documents as ad-hoc exception names. `FACT (CLAUDE.md)`: the
repo's `ControllerAdviser` maps `IllegalArgumentException` → 400 and everything else → 500, with
*"human-readable strings, never codes"*. That is right for a portfolio tracker and insufficient here,
because the UI must **react differently** per error and support must triage from a log line.

`RECOMMENDATION`: keep the human-readable message **and** add a stable code.

```java
public enum TaxError {
    //                                     http  retryable  userActionable
    UNSUPPORTED_TAX_YEAR                  (400,  false,     true),
    UNKNOWN_COMPONENT_CODE                (400,  false,     true),
    COMPONENT_WITHDRAWN_NEEDS_CORRECTION  (409,  false,     true),   // 18 V6
    INCOMPLETE_INPUTS_FOR_RULE            (400,  false,     true),
    STATUTORY_OVERSHOOT                   (400,  false,     true),   // 12 C3.1
    REGIME_BARRED_ENTRY_CLOSED            (409,  false,     true),   // 17 L3
    REGIME_BARRED_PERMANENTLY_INELIGIBLE  (409,  false,     true),
    UNCLASSIFIED_CAPITAL_GAINS            (409,  false,     true),   // X1.3
    DRAFT_REVISION_CONFLICT               (409,  true,      true),   // 11 F5
    DRAFT_FROZEN                          (409,  false,     true),
    ELECTION_ALREADY_CONSUMED             (409,  false,     true),   // 11 I8
    UNDETERMINED_APPORTIONMENT            (500,  false,     false),  // 18 V5 — a pack defect
    MISSING_TEMPORAL_COORDINATE           (500,  false,     false),  // 18 V4 — an engine defect
    RULE_EVALUATION_FAILED                (500,  false,     false),
    PORTFOLIO_UNAVAILABLE                 (503,  true,      false),
    MISSING_RULE_PACK                     (500,  false,     false);  // should be unreachable
}
```

```json
{ "error": { "code": "STATUTORY_OVERSHOOT",
             "message": "The HRA exemption claimed (₹3,00,000) exceeds the statutory maximum on your figures (₹2,28,000).",
             "ruleIds": ["SAL-HRA-001"],
             "field": "employments[0].components[HRA]",
             "retryable": false,
             "receipt": "TXC-2026-9F3A21" } }
```

`INFERENCE`: `retryable` and `userActionable` are the two flags a client actually branches on, and
splitting **pack defects** (`UNDETERMINED_APPORTIONMENT`) from **user errors** (`STATUTORY_OVERSHOOT`)
at the type level is what lets alerting fire on the first and stay quiet on the second.

---

## E6 — The computation receipt

`RECOMMENDATION`: every `Computation` gets a short, shareable, non-guessable id — `TXC-2026-9F3A21` —
printed on the PDF, returned in every API response, and included in every error body.

```java
public record ComputationReceipt(String value) {   // TXC-<taxYear>-<6 base32 chars of the id>
    public static ComputationReceipt of(ComputationId id, TaxYearId y) { ... }
}
```

Resolving it server-side yields: pack version and checksum, inputs fingerprint, regime, portfolio
`asOf`, draft revision, and the full trace. `INFERENCE`: this is the cheapest thing in this document and
close to the highest-value. *"My tax looks wrong"* is unanswerable; *"my tax looks wrong, receipt
TXC-2026-9F3A21"* is a five-minute investigation — and it is the only way to answer it at all once
`13-edge-cases.md` R-8's retrospective amendments mean the same inputs legitimately give two answers.

---

## E7 — API depth

| Concern | Decision |
|---|---|
| **Versioning** | `/api/v1/tax-planning/…`. The **trace shape is the thing that will change**, so version the API, not the trace: a v1 client keeps a v1 projection |
| **Projections** | `?view=summary\|full\|debug`. `summary` is totals plus driver lines; `full` is the trace; `debug` adds node values and timings. **Default `summary`** — a five-regime comparison at `full` is a large response nobody reads |
| **Caching** | `ETag` = `inputsFingerprint` + `packVersion`. A recompute with unchanged inputs is a `304` |
| **Pagination** | draft lists, revisions, documents — cursor-based, reusing the repo's `QueryBuilder`/`QueryFilter` (`shared/entity/query`, confirmed present this session) |
| **Quotas** | the optimiser is ~24,000 pipeline runs (`G6.2`). Per-user: **20 optimiser calls/hour**, 200 computes/hour, 50 uploads/day. Returned as `429` with `Retry-After` |
| **OpenAPI** | generated from controllers; the sealed error enum generates the error component |
| **Bulk** | one endpoint only: compare N regimes. There is no batch-compute-for-many-users — D1 is single-user |

---

## E8 — Background jobs

Six jobs are named across the documents and none was designed: parse, PDF, staleness sweep, gazette
watch, auto-archive, retention purge.

```java
public interface TaxJob {
    JobType type();
    Duration timeout();
    RetryPolicy retry();            // maxAttempts, backoff, and which TaxErrors are retryable
    boolean singletonAcrossCluster();
    void run(JobContext ctx);
}
```

| Job | Trigger | Singleton | Notes |
|---|---|---|---|
| `ParseDocument` | on upload | no | idempotent on `(documentId, parserVersion)`; a new attempt row per run (`M4`) |
| `GeneratePdf` | on `ComputationCompleted` | no | idempotent on `computationId`; cached by receipt |
| `StalenessSweep` | on pack upgrade at startup | **yes** | scans `ACTED_ON`/`READY` drafts; **flags**, never recomputes (`13` R-8, `18` V9) |
| `GazetteWatch` | daily | **yes** | checks the gazette index; **opens a ticket**. Never writes a pack |
| `AutoArchive` | weekly | **yes** | `11-filing-drafts.md` F6's 18-month rule |
| `RetentionPurge` | monthly | **yes** | `14-operational-design.md` O5; **refuses to run** if the retention period cannot be derived from the packs |

`RECOMMENDATION`: singleton jobs take a Mongo-backed lease (`tax_job_leases`, TTL-indexed) rather than
requiring a scheduler. `FACT (CLAUDE.md)`: multi-document transactions need a replica set **and** a
flag, so a lease with a TTL and a fencing token is the shape that works whether or not the flag is on.

**Dead-letter**: a job exhausting retries writes to `tax_job_failures` with the full context and
raises the `taxplanning.job.failed` metric. `INFERENCE`: `StalenessSweep` failing silently is the worst
of these — users would keep filing on figures the law had moved under.

---

## E9 — What to reuse from `shared`, verified present

| Utility | Path (confirmed this session) | Use |
|---|---|---|
| `ExcelParser`, `ExcelSheet`, `ExcelColumn`, `ExcelWorkbooks` | `shared/util/parser/` | **Bulk salary-component import.** See E9.1 |
| `ExcelBuilder` equivalent (`ExcelWorkbooks`) | `shared/util/parser/` | Export a computation and its trace |
| `QueryBuilder`, `QueryFilter` | `shared/entity/query/` | Draft and document listing (`O32`'s "filter in the query") |
| `TJsonMapper` | `shared/util/collection/` | Rule-pack loading |
| `TCollectionUtil`, `TOptional`, `TStringUtil` | `shared/util/collection/` | general |
| `TLocalDate`, `TLocalDateTime` | `shared/util/time/` | **audit** timestamps only — tax dates are IST `LocalDate` (`O11`) |
| `MoneyAssert`, `LogCapture` | `backend/src/test/.../testsupport/` | every money assertion, per CLAUDE.md |
| `TMoney` | `shared/util/money/` | **not used in `taxengine`** — see E1 |
| `SafeExpressions` | `shared/util/expression/` | **not used** — `D-NOFORMULA`, and O-1 is what it produced |

### E9.1 — Excel import as a real feature

`INFERENCE`: `F4` (manual entry of ~30 salary components) is the most tedious step in the product, and
`F11` (Form 16 ingestion) is the largest and lowest-confidence slice. **Excel import sits between them**
— most users have their CTC breakup in a spreadsheet already, the repo has a tested parser, and the
work is a column mapping plus the `ComponentCode` registry.

```java
public final class SalaryStructureImporter {

    private static final ExcelSheet SHEET = ExcelSheet.of("Salary")
            .column(ExcelColumn.of("Component", ExcelDataType.STRING))
            .column(ExcelColumn.of("Annual Amount", ExcelDataType.NUMBER))
            .column(ExcelColumn.of("Monthly Amount", ExcelDataType.NUMBER).optional());

    public ImportResult importInto(DraftId draft, InputStream xlsx, RulePack pack) {
        // Unmapped labels are RETURNED FOR MAPPING, never guessed and never dropped —
        // the same discipline as 18 V6's DraftUpcaster.
    }
}
```

`RECOMMENDATION`: promote this to **`F28`, MVP tier**, sized at one week. It reuses tested
infrastructure, it removes the biggest friction in onboarding, and it de-risks F11 by giving users a
path that does not depend on PDF parsing working.

---

## E10 — Dev and demo fixtures

`RECOMMENDATION`: a `taxplanning-fixtures` test-scope module producing realistic taxpayers, because
"type 30 components" kills demos and slows every integration test.

```java
public final class Fixtures {
    public static Inputs ravi()   { /* 04-product-spec.md P1's primary persona, ₹28 LPA, Bengaluru */ }
    public static Inputs priya()  { /* two employers, the T8-1 double standard deduction */ }
    public static Inputs anand()  { /* 62, pension + interest, DED-80TTB-001, ADV-SENIOR-001 */ }
    public static Inputs meera()  { /* high earner, surcharge + marginal relief, capital gains */ }
}
```

`INFERENCE`: these are the four personas already specified in `P1`. Making them executable means the
persona work is load-bearing rather than documentation — and `priya()` is the fixture that reproduces
the ₹2,14,500 shortfall, which is the product's single best demo.

---

## E11 — What "correct" means

The design has no acceptance oracle. `RECOMMENDATION`, three gates, ordered by cost:

| Gate | What | When |
|---|---|---|
| **1. Internal** | all golden cells filled; metamorphic properties hold; the independent oracle (E4.2) agrees on every case | every build |
| **2. Cross-check** | for TY 2025-26, a sample of computations reconciled against **actual Form 16s** with known outcomes — real documents, real numbers | before release |
| **3. Professional sign-off** | a practising CA reviews **N = 25** cases spanning every income head, both regimes, both years, and the four `STATUS UNCLEAR` items; sign-off recorded per case | before release, and again on any year rollover |

`RECOMMENDATION`: gate 3 is the acceptance criterion for the four `STATUS UNCLEAR` items
(`SAL-ALW-NEW-SUBSET-001`, `REG-OPTOUT-LATE-001`, `ROUND-NEG-001`, `AGE-BOUNDARY-001`) and for
`ITR-1-BAR-002`. Those are not engineering questions and no amount of testing resolves them. Record
each sign-off in `tax_rulepack_reviews` (`15-review-findings.md` Part H) so the answer is attached to
the rule rather than living in an email.

---

## E12 — Plan delta

| Change | Where |
|---|---|
| **PR 0** (new, before PR 1) | amend `ArchitectureTest`'s money rule with its justification, and add the three new money rules (E1). **This is the first commit** — it is the one that would otherwise break |
| PR 1 | `Money` under `..taxengine.money..`; `Rate`; `MoneyBridge` stub |

> **Erratum (W-013, 2026-09-19):** `..taxrules.money..` — see the erratum in E1 above and
> `STATE.md` **B-001**.
| PR 2 | **+** the pack Maven plugin: validate, link, generate constants (E3) |
| **PR 2b** (new) | JSON Schema generation + the CLI goals (E2) |
| PR 3 | **+** test-data builders (E4.5) |
| **PR 7b** (new) | metamorphic properties + the independent oracle (E4.1, E4.2) |
| PR 11 | **+** the error catalogue (E5) and the computation receipt (E6) |
| PR 12 | **+** job framework and leases (E8) |
| **PR 12b** (new) | **Excel import (`F28`)** — promoted to MVP (E9.1) |
| Throughout | fixtures (E10); gate 3 scheduled before release (E11) |

`INFERENCE`: PR 0 is the finding that matters most in this document. Everything else improves the
build; PR 0 is the difference between the design compiling in this repository and not.
