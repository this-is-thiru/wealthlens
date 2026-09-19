# 16 — Where to start, and how to proceed

Sixteen documents is a lot to start from. This one is the entry point: **what to decide before writing
code, what the first commit is, and the PR sequence after it.**

---

## S0 — Read in this order

| Order | Document | Why |
|---|---|---|
| 1 | `00-SUMMARY.md` | the shape of everything, and the top risks |
| 2 | `01-rulebook.md` R1–R6 | tax years, regimes, slabs, rebate, surcharge, rounding. The rest of the rulebook is reference |
| 3 | `10-extensibility.md` | the regime and vocabulary model — **supersedes** parts of `05` and `07` |
| 4 | `15-review-findings.md` Parts B, E, G | the DAG, `Condition`, `Money`/`Rate` — the three types everything else is written in |
| 5 | `09-build-plan.md` | the slices |
| 6 | this file | the first two weeks |

Everything else is reference, consulted when the slice you are on needs it.

---

## S1 — Day 0: seven decisions only you can make

Each blocks something concrete. Record the answer in `PROGRESS.md` under a **Decision log** heading.

| # | Decision | Why it blocks | Recommendation |
|---|---|---|---|
| **Q1** | Do we ship TY 2025-26 at all at launch, or TY 2026-27 only? | Nine golden cases are `PROVISIONAL` because the Income-tax Rules 1962 could not be opened (`00-SUMMARY` R1). TY 2026-27 is fully sourced | **Ship TY 2026-27 first.** It is the planning year, it is completely verified, and it removes Slice 0 from the critical path |
| **Q2** | Four modules (`taxrules`, `taxengine`, `taxdocs`, `taxplanning`) or one? | `X1.2`. Four gives a compiler-enforced "the engine cannot reach a repository" | **Four.** The boundary is the reason the performance budget in `O8` holds |
| **Q3** | `Type.CLOSED` or `Type.OPEN` modulith modules? | CLAUDE.md records `OPEN` as a migration affordance, not a goal | **CLOSED.** Greenfield has no legacy to accommodate (`09-build-plan.md` B2) |
| **Q4** | Is the advisor path (D1) coming within 12 months? | Decides whether `AccessPolicy` ships with a mandate table now or just the seam | **Seam only**, per `14-operational-design.md` O2 |
| **Q5** | Is document ingestion in the first release, or a fast-follow? | `F11` is the largest MVP slice and has the lowest confidence | **Fast-follow.** Ship the engine behind `app.taxplanning.documents.enabled=false` |
| **Q6** | Who signs off a rule-pack change? | `15-review-findings.md` C4 makes rollover a reviewed legal change | Name a person. A rollover PR without a named reviewer is a copy-paste with legal effect |
| **Q7** | Is `SAL-ALW-NEW-SUBSET-001` shipped per the **section** or per **rule 280(3)**? | The Rules appear to invert the section (`01-rulebook.md` R11) | **Per the section**, flagged visibly. A rule cannot override its parent section |

---

## S2 — Day 1: the walking skeleton

The goal of day one is **one number, end to end, from a real rule pack** — not a module tree.

> **Target:** `POST /tax-planning/compute` with `{taxYear: "2026-27", regime: "S202_DEFAULT",
> totalIncome: "1200000"}` returns `0`, with a trace naming `RATE-SLAB-NEW-2026` and `REBATE-NEW-001`,
> and the pack version it used.

That is `T1-N1` from `08-golden-tests.md`. It exercises the registry, the pack loader, a cap shape, the
DAG runner, a node, the trace and the API — everything structural — and nothing else.

### 1. Modules

```
backend/src/main/java/com/thiru/wealthlens/
  taxrules/package-info.java
  taxengine/package-info.java
  taxdocs/package-info.java
  taxplanning/package-info.java        ← replaces the placeholder's
```

```java
// taxrules/package-info.java
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    allowedDependencies = {"shared"})
package com.thiru.wealthlens.taxrules;
```

```java
// taxengine/package-info.java  — note: NO auth, NO portfolio, NO spring-data
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    allowedDependencies = {"shared", "taxrules"})
package com.thiru.wealthlens.taxengine;
```

Run `WealthLensModulithTest.modulithStructureIsValid()` now, while it is cheap to fix.

### 2. The three types everything is written in

`Money` and `Rate` exactly as in `15-review-findings.md` Part G. Then:

```java
package com.thiru.wealthlens.taxrules;

public record RuleId(String value) {
    private static final Pattern P = Pattern.compile("^[A-Z]{2,8}(-[A-Z0-9]{1,12}){1,3}$");
    public RuleId { if (!P.matcher(value).matches()) throw new IllegalArgumentException(value); }
}
```

### 3. Two cap shapes, not six

Only what `T1-N1` needs:

```java
public sealed interface CapShape permits SlabLadder, FlatWithCeiling /* … four more later */ {
    CapResult apply(CapContext ctx);
}

public record CapResult(Money value, String limitedBy) {}

public record SlabLadder(List<Band> bands) implements CapShape {

    public record Band(Money lowerExclusive, Optional<Money> upperInclusive, Rate rate) {}

    /** RATE-SLAB-BAND-002: the arithmetic lower bound IS lowerExclusive. */
    @Override public CapResult apply(CapContext ctx) {
        Money income = ctx.base(), tax = Money.ZERO;
        for (Band b : bands) {
            Money ceiling = b.upperInclusive().orElse(income);
            if (income.isLessThanOrEqual(b.lowerExclusive())) break;
            Money width = Money.min(income, ceiling).minus(b.lowerExclusive());
            tax = tax.plus(b.rate().of(width));          // no rounding here — RATE-ROUND-003
        }
        return new CapResult(tax, "slab");
    }

    /** Load-time guard for H-5: bands must partition the rupee line with no gap and no overlap. */
    public void validate() {
        for (int i = 1; i < bands.size(); i++) {
            Money prevTop = bands.get(i - 1).upperInclusive().orElseThrow();
            if (!bands.get(i).lowerExclusive().equals(prevTop)) {
                throw new PackValidationException(
                    "band %d lowerExclusive %s != previous upperInclusive %s"
                        .formatted(i, bands.get(i).lowerExclusive(), prevTop));
            }
        }
    }
}
```

> **Erratum (W-015, 2026-09-19):** two corrections to this reference implementation.
>
> 1. **`ctx.base()` takes an argument.** `CapContext` holds `Map<BaseId, Money>` and
>    `base(BaseId)` resolves a declared base, so `SlabLadder` carries a `BaseId` like
>    `PercentOfBase` does. Decided in `STATE.md` **D-012**'s sibling call on `CapContext` — a
>    single implicit base cannot express `LeastOf`, whose limbs each read a different one.
> 2. **`validate()` here checks only gaps and overlaps between adjacent bands.** Three more ways
>    to write a malformed ladder pass it silently, each of them untaxing real income: a first band
>    that does not start at zero, a closed top band, and an open band that is not last (every band
>    above it is unreachable). All four are checked in
>    `taxrules/cap/SlabLadder.java`'s `validate()`.
>
> Verified by: `./mvnw test -pl backend -Dtest=SlabLadderTest` — 20 tests, six of them validation.

### 4. The first rule pack — real content

`backend/src/main/resources/rulepacks/2026-27/manifest.json`:

```json
{
  "taxYear": "2026-27",
  "packVersion": "2026-27.1",
  "schemaVersion": 1,
  "instruments": {
    "act": {
      "citation": "Income-tax Act, 2025 (30 of 2025)",
      "assentedOn": "2025-08-21", "inForceFrom": "2026-04-01",
      "gazetteRef": "CG-DL-E-22082025-265620",
      "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
      "status": "VERIFIED"
    },
    "financeAct": {
      "citation": "Finance Act, 2026 (4 of 2026)",
      "assentedOn": "2026-03-30", "inForceFrom": "2026-04-01",
      "gazetteRef": "CG-DL-E-31032026-271439",
      "sourceUrl": "https://egazette.gov.in/WriteReadData/2026/271439.pdf",
      "status": "VERIFIED"
    },
    "rules": {
      "citation": "Income-tax Rules, 2026 (G.S.R. 198(E), 20-03-2026)",
      "inForceFrom": "2026-04-01",
      "gazetteRef": "CG-DL-E-20032026-271092",
      "sourceUrl": "https://egazette.gov.in/WriteReadData/2026/271092.pdf",
      "status": "VERIFIED",
      "note": "amendment chain to G.S.R. 656(E) of 24-07-2026 not traced"
    }
  }
}
```

`rulepacks/2026-27/rates.json` — the two rules `T1-N1` needs:

```json
{ "rules": [
  { "ruleId": "RATE-SLAB-NEW-2026",
    "provision2025": { "instrument": "act", "reference": "s.202(1) Table", "presence": "PRESENT" },
    "provision1961": { "instrument": "-",   "reference": "s.115BAC(1A)(iii)", "presence": "PRESENT" },
    "regimes": { "type": "OnlyIn", "regimes": ["S202_DEFAULT"] },
    "cap": { "type": "SlabLadder", "bands": [
      { "lowerExclusive": "0",       "upperInclusive": "400000",  "rate": "0"  },
      { "lowerExclusive": "400000",  "upperInclusive": "800000",  "rate": "5"  },
      { "lowerExclusive": "800000",  "upperInclusive": "1200000", "rate": "10" },
      { "lowerExclusive": "1200000", "upperInclusive": "1600000", "rate": "15" },
      { "lowerExclusive": "1600000", "upperInclusive": "2000000", "rate": "20" },
      { "lowerExclusive": "2000000", "upperInclusive": "2400000", "rate": "25" },
      { "lowerExclusive": "2400000", "upperInclusive": null,      "rate": "30" } ] },
    "provenance": {
      "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf",
      "sourceDescription": "Gazette CG-DL-E-22082025-265620, s.202(1) Table",
      "draftOrFinal": "FINAL", "accessedOn": "2026-09-18", "status": "VERIFIED" } },

  { "ruleId": "REBATE-NEW-001",
    "provision2025": { "instrument": "act", "reference": "s.156(2)(a)", "presence": "PRESENT" },
    "regimes": { "type": "OnlyIn", "regimes": ["S202_DEFAULT"] },
    "conditions": [ { "type": "IsResident" },
                    { "type": "IncomeAtMost", "node": "round.totalincome", "limit": "1200000" } ],
    "cap": { "type": "FlatWithCeiling", "amount": "60000", "incomeCeiling": "1200000" },
    "provenance": { "sourceDescription": "s.156(2)(a)", "status": "VERIFIED",
                    "draftOrFinal": "FINAL", "accessedOn": "2026-09-18",
                    "sourceUrl": "https://egazette.gov.in/WriteReadData/2025/265620.pdf" } }
] }
```

**Note what is already true of this file:** the bands are written as `400000`, not `400001` — H-5 is
designed out at the data level, and `SlabLadder.validate()` enforces it.

`rulepacks/2026-27/regimes.json`:

```json
{ "regimes": [
  { "code": "S202_DEFAULT", "displayName": "Default regime (s.202(1))",
    "appliesTo": ["INDIVIDUAL", "HUF", "AOP", "BOI", "AJP"],
    "rateTable": "RATE-SLAB-NEW-2026",
    "disallowances": ["DIS-S202-2A"],
    "eligibility": [],
    "election": { "kind": "DEFAULT_NO_ACTION", "vehicle": "NONE", "lockIn": "NONE" },
    "isDefault": true,
    "pipeline": { "nodeRefs": ["inputs.components", "salary.gross", "totalincome",
                               "round.totalincome", "tax.bybasis", "tax.afterrebate",
                               "tax.cess", "tax.netpayable", "round.netpayable"] } },

  { "code": "S202_OPTED_OUT", "displayName": "Regime under s.202(4) opt-out",
    "appliesTo": ["INDIVIDUAL", "HUF", "AOP", "BOI", "AJP"],
    "rateTable": "RATE-SLAB-OLD-2026",
    "disallowances": [],
    "eligibility": [],
    "election": { "kind": "OPT_OUT", "vehicle": "IN_THE_RETURN",
                  "deadline": "s.263(1) due date", "lockIn": "NONE",
                  "provenance": { "sourceDescription": "Income-tax Rules 2026, r.136",
                                  "status": "VERIFIED" } },
    "isDefault": false,
    "pipeline": { "nodeRefs": [ /* … */ ] } }
] }
```

> **Erratum (W-026c, 2026-09-19):** these two `election` blocks predate
> `17-regime-lifecycle.md` L3 and carry **`"lockIn": "NONE"`** — the three-valued enum L3 explicitly
> replaced with `BindingSpec`. A pack author copying this writes a field the loader will not
> recognise. The election block is now:
>
> ```json
> "election": { "kind": "DEFAULT_NO_ACTION", "vehicle": "NONE",
>               "deadline": { "type": "NoDeadline" },
>               "binding": { "bindsSubsequentYears": false, "maxWithdrawals": -1,
>                            "afterWithdrawal": { "type": "MayReElect" },
>                            "firstReturnOnly": false, "bindingAppliesWhen": [] } }
> ```
>
> The `S202_OPTED_OUT` block's `"deadline": "s.263(1) due date"` is likewise a bare string where
> `DeadlineSpec` is a tagged union: `{ "type": "DueDateUnder", "section": "263(1)" }`, as
> `17-regime-lifecycle.md:161` writes it.
>
> Same class of staleness as the `"PGBP"` head name — a document revised in one place and copied
> from in another.
> Verified by: `17-regime-lifecycle.md:130-155, 161`; `./mvnw test -pl backend -Dtest=RegimeLifecycleTest`.

`INFERENCE`: two regimes are in the file from day one, and neither is named in Java. Adding a third is
a third array element.

### 5. The first node

```java
package com.thiru.wealthlens.taxengine.node;

public final class TaxByBasisNode implements ComputationNode {

    @Override public NodeId      produces() { return NodeId.TAX_BY_BASIS; }
    @Override public Set<NodeId> requires() { return Set.of(NodeId.ROUNDED_TOTAL_INCOME); }
    @Override public Set<RuleId> rules()    { return Set.of(RuleIds.RATE_SLAB); }

    @Override public NodeResult evaluate(NodeContext ctx) {
        Money ti     = ctx.get(NodeId.ROUNDED_TOTAL_INCOME).asMoney();
        RuleRow row  = ctx.pack().rule(ctx.regime().rateTable());
        CapResult r  = row.cap().apply(CapContext.of(ti, ctx));

        var value = new TaxByBasis(r.value(), Map.of());     // special rates arrive with Slice 8
        return new NodeResult(value, List.of(
            TraceLine.of(row.id(), row.displayName(), StepKind.TAX,
                         MoneyValue.of(ti), value, r.limitedBy(), row.provenance())));
    }
}
```

### 6. The first test — and it must fail first

```java
class T1SlabAndRebateBoundaryTest {

    @Test
    void newRegime_atRebateCeiling_taxIsNil() {
        // Given — 08-golden-tests.md T1-N1, expected value derived from 01-rulebook.md, not from code
        var pack   = RulePacks.load(TaxYearId.of(2026));
        var regime = pack.regime(RegimeCode.of("S202_DEFAULT"));
        var inputs = Inputs.withTotalIncome(Money.of("1200000"));

        // When
        Computation c = runner.run(pack, regime, inputs);

        // Then
        MoneyAssert.assertEquals(Money.ZERO, c.netPayable());
        assertThat(c.valueOf(RuleIds.of("RATE-SLAB-NEW-2026"))).isEqualTo(Money.of("60000"));
        assertThat(c.valueOf(RuleIds.of("REBATE-NEW-001"))).isEqualTo(Money.of("60000"));
        assertThat(c.rulePackVersion()).isEqualTo("2026-27.1");
    }
}
```

`MoneyAssert` is the repo's existing helper, as CLAUDE.md requires.

### Day 1 done when

- [ ] four modules exist and `ApplicationModules.verify()` passes
- [ ] `Money`, `Rate`, `RuleId` exist with their validation
- [ ] `SlabLadder` and `FlatWithCeiling` exist, with `validate()`
- [ ] both packs' `manifest.json`, `rates.json`, `regimes.json` load and checksum
- [ ] a corrupt pack **fails startup** (`T9-3`)
- [ ] `T1-N1` passes with the expected value derived by hand
- [ ] `./mvnw test -pl backend -Punit` is green, still under ~10 seconds

---

## S3 — The PR sequence

Each PR is reviewable in under an hour and leaves `master` green.

| PR | Title | Contains | Gate |
|---|---|---|---|
| **1** | *taxrules: module skeleton, Money, Rate, RuleId* | S2 steps 1–2 | modulith test; the no-`BigDecimal`-outside-`Money`/`Rate` ArchUnit rule (`15` Part G) |
| **2** | *taxrules: pack schema, loader, checksum, fail-fast, **RuleVersion / Validity / TemporalContext*** | manifest + loader + invariants **I1–I4**, **I13–I16, I18** (`18-temporal-versioning.md`) | `T9-1` … `T9-6`, **`T18-3`, `T18-6`, `T18-7`** |
| **3** | *taxrules: SlabLadder, FlatWithCeiling, band continuity* | two cap shapes | `T9-7` — a `400001` band **fails to load** |
| **4** | *taxrules: RegimeSpec, RegimeCode, RegimeApplicability, invariants I5–I7* | `10-extensibility.md` E3 | both regimes load; exactly one default |
| **4b** | *taxrules: RegimeLifecycle, ElectionProtocol, election-history state machine* | `17-regime-lifecycle.md` L2, L3 | **`T17-1` … `T17-5`**; invariants I10–I12 |
| **5** | *taxrules: Condition algebra* | `15-review-findings.md` Part E | each record unit-tested |
| **6** | *taxengine: DAG runner, NodeId, NodeValue, topological sort* | `15` Part B | a cycle fails at load; an undeclared `requires()` throws |
| **7** | *taxengine: tax.bybasis, tax.afterrebate, tax.cess, rounding* | four nodes | **`T1` complete**, all cells |
| **8** | *taxrules: surcharge ladders + ThresholdLadderCeiling relief* | `15` Part E's `ReliefShape` | **`T2` complete** |
| **9** | *taxengine: boundary-case generator* | `15` C5 | generated boundaries match the hand-written `T1`/`T2` inputs exactly |
| **10** | *taxrules: pack linter and cross-year diff* | `15` C4 + **`18` V7 clause-level lints** | the H-2 lint fires on a mislabelled fixture; **`T18-5`** (undeclared mid-year combination) |
| **11** | *taxplanning: profile with PAN, family members, reference entities* | `15` F1–F4 | `tax_profiles` unique on PAN; every collection in `cleanDatabase()` |
| **12** | *taxplanning: workspace, drafts, revisions, optimistic locking* | `11-filing-drafts.md` | `T11-1` … `T11-6` |
| **13** | *taxengine: salary head nodes* | components, perquisites, employer charges, gross, deductions | `T4`, `T9-8`, `T9-17` |
| **14** | *taxengine: exemption nodes + proration engine* | HRA, LTA, allowances, `15` C3 | `T3`, `T5-4`, **`T13-6`** (Pune → Nashik) |
| **15** | *taxengine: Chapter VIII + aggregator* | `G2` | `T5-1` … `T5-3` |
| **16** | *taxengine: house property + election engine* | `15` C2 | `T6`, **`T13-3`**, **`T13-4`** |
| **17** | *taxengine: other sources, set-off, absorption order* | | `T6-6`, `T6-7`, **`T13-5`** |
| **18** | *portfolio: CapitalGainsSummary published read-model* | `15` F5 | modulith test with the new dependency |
| **19** | *taxengine: capital gains + rate-basis partition* | | **`T13-1`** — the s.156(3) rebate cap |
| **20** | *taxengine: interest and fee engine* | `15` C1 | `T13-9`, **`T13-10`** |
| **21** | *taxplanning: compare-regimes, break-even, ITR advisor* | `10` E6, `F8`, **`21` I3–I4** | `A1.4`'s six traps; **`T21-1` … `T21-13`** |
| **21b** | *taxplanning: ITR schedule mapping and export* | **`21` I5**, `F36` | **`T21-14` … `T21-16`** |
| **22** | *taxplanning: claim integrity* | `12-claim-integrity.md` | `T12-1` … `T12-14` |
| **23** | *taxdocs: Form No. 130 parser* | `G5` | `T8` |
| **24** | *taxplanning: PDF, advisory tier, optimiser* | | `A2`, `A3` |

`INFERENCE`: PRs 1–8 produce a **correct tax calculator for both regimes and both years, with no
database at all**. That is the highest-risk, highest-value part of the system, it is fully unit-tested
in the `-Punit` tier, and it is done before any persistence decision can constrain it.

---

## S4 — Running three people in parallel

After PR 6, three tracks stop blocking each other:

| Track | PRs | Needs |
|---|---|---|
| **A — engine** | 7, 8, 9, 13, 14, 15, 16, 17, 19, 20 | `taxrules` only. No database, no HTTP |
| **B — persistence and API** | 11, 12, 21, 22, 24 | the engine's *interfaces*, not its nodes |
| **C — rule packs and verification** | Slice 0, 10, plus authoring both packs | no Java at all. This is the track for whoever can get at `incometaxindia.gov.in` |

Track C is the one to start **today**, in parallel with PR 1. It has no code dependency, it is the
critical path for TY 2025-26 (`00-SUMMARY` R1), and its output — verified rule rows — is what every
golden test in Track A is measured against.

---

## S5 — How to know it is going well

Four signals, checked weekly:

| Signal | Healthy | Unhealthy |
|---|---|---|
| `taxplanning.unverified_rule.used` by rule ID (`O7`) | falling as Track C lands | flat — the verification backlog is not being worked |
| Golden cells filled (year × regime, `10` E8) | monotonically rising, no empty cell in a shipped year | a cell empty at release |
| `./mvnw test -pl backend -Punit` | under 20 s through PR 24 | creeping up — something acquired I/O it should not have |
| The synthetic-pack test (`T9-21`, `T10`) | passes with **zero** production source changes | needed an engine edit — the Open/Closed claim has failed, and it failed in CI rather than on Budget day |

---

## S6 — The five things most likely to go wrong

| | Failure | Early warning | Prevention |
|---|---|---|---|
| 1 | A `Regime` enum reappears "just for now" | `grep -rn "enum Regime"` | PR 4's review; the ArchUnit reachability rule |
| 2 | A node quietly reads something it did not declare | `NodeContext.get` throws | PR 6 makes it structural, not a convention |
| 3 | The rate-basis partition is retrofitted after the fact | `TaxByBasis` used as `Money` | PR 7 lands it **before** any rebate code (`13` R-5) |
| 4 | A golden expectation is "fixed" to match the code | a test changed in the same commit as the code it tests | `D-GOLDEN`; require a rulebook citation in the PR body for any changed expectation |
| 5 | TY 2025-26 ships with unverified detail rules unflagged | `unverified_rule.used` non-zero with no UI marker | `A4.2` is an acceptance criterion of PR 21, not a nicety |

---

## S7 — The one-paragraph version

Start Track C today — it needs no code and it is the critical path. In parallel, PR 1 through PR 8
build a **pure, database-free tax calculator** for both regimes from an immutable rule pack, and pass
`T1` and `T2` for every year × regime cell. That is roughly two weeks and it retires the three defects
that produce silently wrong tax at a boundary — H-1, H-3 and H-5. Everything after PR 8 attaches to a
calculator that is already right.
