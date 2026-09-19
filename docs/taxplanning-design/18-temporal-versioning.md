# 18 — Temporal versioning: rules, clauses, limits and components over time

**Amends `05-domain-model.md` M1, `10-extensibility.md` E4, `15-review-findings.md` Part E.**

The question: do rules, components, clauses and limits change *within* and *across* time in ways the
pack-per-year model cannot express — and should resolution be by **year**, or by something finer?

`D-YEARKEY` — every lookup takes a `TaxYearId` — is **necessary but not sufficient**. The evidence is
below, and it is all from this session.

---

## V1 — Did it happen? Five verified instances, four of them this year

| # | Instance | Evidence | Shape |
|---|---|---|---|
| **1** | **A mid-year rate change, permanently embedded** | `FACT (S2, s.197(3))`: for a resident individual or HUF transferring *"land or building, or both, which was acquired **before the 23rd July, 2024**"*, the excess of tax at 12.5% over tax at 20% **with indexation** *"shall be ignored"*, via `E = A − B` | The July-2024 change survives as a **permanent date-conditioned clause keyed on the asset's acquisition date — not on the tax year** |
| **2** | **One instrument, two commencement dates** | `FACT (S6, Income-tax (Fourth Amendment) Rules 2026, G.S.R. 822(E), 17-09-2026)`: *"(a) **rules 2 to 4 shall be deemed to have come into force on the 1st day of April, 2026**; and (b) **rules 5 to 8 shall come into force on the date of their publication**"* | A single amending instrument, **partly retrospective to the start of the tax year and partly mid-year** |
| **3** | **Four amendments to one instrument in six months** | `FACT`: Income-tax Rules 2026 notified 20-03-2026 (G.S.R. 198(E)); corrigendum 16-04-2026 (G.S.R. 286(E)); *"last amended by G.S.R. 656(E), dated 24 July 2026"*; Fourth Amendment 17-09-2026 (G.S.R. 822(E)) | The Rules governing the **current** tax year moved at least four times inside it |
| **4** | **A nineteen-year retrospective insertion** | `FACT (S3, Finance Act 2026, s.4)`: s.92CA(3AA) *"shall be deemed to have been inserted with effect from the **1st day of June, 2007**"* | Retrospectivity is not theoretical |
| **5** | **A rule in force with no effective date yet** | `FACT (S3, s.123 inserting Sch. III Sl. 38A, Note 12)`: *"the provisions … shall apply **on or after such date as may be notified**"*, and *"**pending such notification**, the entire disability pension … shall be exempt"* | A rule whose validity interval **has no start date yet**, with a stated interim position |

`INFERENCE`: instance 1 is the decisive one. It shows that a mid-year change does not merely affect
the year it lands in — it can become a **permanent clause keyed on a date unrelated to the tax year**,
and a TY 2045-46 computation will still need the 23 July 2024 split. A model in which the only
temporal coordinate is the tax year cannot express it at all.

Instance 5 shows the opposite end: a rule that is in the statute, is **not yet effective**, and has a
**different, stated behaviour in the meantime**. Neither a `VERIFIED` value nor a `NEEDS VERIFICATION`
marker describes it correctly.

---

## V2 — Six kinds of temporal change

| Kind | Example | Handled before this document? |
|---|---|---|
| **T1 — value changes at a year boundary** | the 8-city HRA list arriving with Rules 2026 | ✅ pack per year |
| **T2 — value changes *inside* a tax year** | Fourth Amendment Rules, rules 5–8, effective 17-09-2026 | ❌ one value per rule per pack |
| **T3 — retrospective change** | FA 2026 s.4, effective 2007 | ⚠️ partly — `13-edge-cases.md` R-8 detects it; nothing *represents* it |
| **T4 — a rule keyed on a date that is not the tax year** | s.197(3)'s 23 July 2024 acquisition date; `DED-80EE-001`'s sanction window | ❌ no such coordinate exists |
| **T5 — a clause is added to or removed from an existing rule** | s.126 gaining a sub-limit; FA 2026 s.38 pulling s.22(1)(c) inside the cap | ⚠️ expressible as a whole-rule replacement, invisible in diff |
| **T6 — a component is introduced or withdrawn** | a new FBP component; an allowance abolished | ❌ `ComponentDefinition` is per pack with no lifecycle, and a saved draft can reference a dead code |

---

## V3 — Honest audit: what actually breaks

| # | Defect | Consequence |
|---|---|---|
| **V-1** | `RuleRow` holds **one** `CapShape`. A value that changes mid-year cannot be represented | T2 is inexpressible. The Fourth Amendment's rules 5–8 would have to be modelled as a different *year* |
| **V-2** | `15-review-findings.md` Part E put `EffectiveBetween` in the **`Condition`** algebra. Conditions gate **applicability**, not **value** | A rule whose *limit* changes on 1 October becomes two rules with two IDs — and `D-RULEID` says rule IDs are the contract, referenced by every test and every trace |
| **V-3** | Every lookup is keyed on `TaxYearId` alone | T4 has no coordinate. s.197(3) cannot be asked "what was the rule for an asset acquired on 1 July 2024?" |
| **V-4** | `ComponentDefinition` has no validity window and no successor | A draft saved in TY 2026-27 referencing a component withdrawn in TY 2027-28 either fails to load or silently drops a figure |
| **V-5** | `Provenance` names **one** instrument per rule | A rule amended three times carries the newest citation and loses the chain. `A4.2` renders provenance to the user; a partial chain is a misleading citation |
| **V-6** | No representation for "enacted, not yet effective, interim position applies" | Instance 5 has no home. It is not `VERIFIED`, not `NEEDS VERIFICATION`, and not absent |

---

## V4 — The fix: `D-TEMPORALKEY`

> **`D-TEMPORALKEY`** — every rule lookup takes a **temporal coordinate**, and each rule **declares
> which coordinate it keys on**. `D-YEARKEY` is the common case of this rule, not a replacement for it.

```java
package com.thiru.wealthlens.taxrules.temporal;

/** Which clock a rule is read against. Declared per rule, in the pack. */
public enum TemporalAxis {
    TAX_YEAR,            // the default: RATE-SLAB-*, SAL-STDDED-*, DED-80C-001
    EVENT_DATE,          // when the transaction happened — a perquisite provided, rent paid
    ACQUISITION_DATE,    // s.197(3): the asset's acquisition date. NOT the tax year
    SANCTION_DATE,       // DED-80EE-001, DED-80EEA-001, DED-80EEB-001 loan-sanction windows
    PAYMENT_DATE,        // DED-80D-010's mode-of-payment tests
    FILING_DATE          // DED-RETURN-001, INT-LATEFILE-001, FEE-LATEFILE-001
}

/** The coordinate a computation carries. Assembled once, per computation. */
public record TemporalContext(
        TaxYearId taxYear,
        LocalDate taxYearStart,
        LocalDate taxYearEnd,
        LocalDate asOfDate,                       // when this computation is being made
        Map<TemporalAxis, LocalDate> axisDates) {  // per-fact dates, supplied by the node

    public LocalDate on(TemporalAxis axis) {
        return switch (axis) {
            case TAX_YEAR -> taxYearEnd;          // a year-keyed rule reads at year end
            default -> Optional.ofNullable(axisDates.get(axis))
                    .orElseThrow(() -> new MissingTemporalCoordinateException(axis));
        };
    }
}
```

`MissingTemporalCoordinateException` is the point: a node evaluating `s.197(3)` **must** supply an
acquisition date, and forgetting is an error rather than a silent fallback to the tax year.

### `RuleRow` becomes temporally versioned (V-1, V-2)

```java
public record RuleRow(
        RuleId       id,                 // STABLE across every version. The contract is unchanged.
        TemporalAxis axis,               // which clock this rule is read against
        List<RuleVersion> versions,      // ordered, non-overlapping, gap-free within the pack's year
        Provenance   headProvenance) {

    public RuleVersion versionAt(TemporalContext t) {
        LocalDate at = t.on(axis);
        return versions.stream()
                .filter(v -> v.validity().contains(at))
                .findFirst()
                .orElseThrow(() -> new NoRuleVersionException(id, axis, at));
    }

    /** True where the rule changed inside the tax year — drives V5's apportionment. */
    public boolean changesWithin(TemporalContext t) {
        return versions.stream()
                .filter(v -> v.validity().overlaps(t.taxYearStart(), t.taxYearEnd()))
                .count() > 1;
    }
}

public record RuleVersion(
        Validity            validity,
        RegimeApplicability regimes,
        CapShape            cap,
        List<Condition>     conditions,
        Provision           provision1961,
        Provision           provision2025,
        AmendmentRef        introducedBy,     // V8
        Provenance          provenance) {}
```

```java
public sealed interface Validity {

    boolean contains(LocalDate d);

    /** The ordinary case: a closed or open-ended interval. */
    record Between(LocalDate from, Optional<LocalDate> to) implements Validity {}

    /** T3: enacted later, deemed effective earlier. Both dates are kept — they differ. */
    record Retrospective(LocalDate deemedEffectiveFrom, Optional<LocalDate> to,
                         LocalDate actuallyEnactedOn) implements Validity {}

    /**
     * V-6 / instance 5. Enacted, not yet effective, awaiting notification —
     * with a STATED interim position that applies until then.
     */
    record PendingNotification(RuleId interimRule, String interimDescription,
                               Provenance basis) implements Validity {
        @Override public boolean contains(LocalDate d) { return false; }
    }
}
```

The disability-pension case becomes exactly what the statute says:

```json
{ "ruleId": "SAL-DISPENSION-001", "axis": "TAX_YEAR",
  "versions": [
    { "validity": { "type": "PendingNotification",
                    "interimRule": "SAL-DISPENSION-INTERIM-001",
                    "interimDescription": "pending notification, the entire disability pension — service element and disability element — is exempt",
                    "basis": { "sourceDescription": "FA 2026 s.123, Sch. III Note 12", "status": "VERIFIED" } },
      "provision2025": { "reference": "Schedule III Table Sl. No. 38A", "presence": "PRESENT" } },
    { "validity": { "type": "Between", "from": null, "to": null },
      "note": "activates on notification; `from` is filled when the notification is opened" } ] }
```

`INFERENCE`: this is the only honest representation. The rule **is** in the Act, is **not** yet
effective, and has a **stated** interim answer. `A4.2` can then render *"the Act contains a narrower
rule that is not yet in force; the full exemption applies for now"* — which is true, sourced, and
actionable, and which neither `VERIFIED` nor `NEEDS VERIFICATION` could say.

### s.197(3), in data

```json
{ "ruleId": "CG-LTCG-LANDBUILDING-001",
  "axis": "ACQUISITION_DATE",
  "versions": [
    { "validity": { "type": "Between", "from": "1900-01-01", "to": "2024-07-22" },
      "cap": { "type": "BetterOfTwoComputations",
               "a": { "rate": "12.5", "indexation": false },
               "b": { "rate": "20",   "indexation": true },
               "expressedAs": "excess of A over B is ignored (E = A − B)" },
      "provision2025": { "reference": "s.197(1)(b) r/w s.197(3)", "presence": "PRESENT" } },
    { "validity": { "type": "Between", "from": "2024-07-23", "to": null },
      "cap": { "type": "FlatRate", "rate": "12.5", "indexation": false },
      "provision2025": { "reference": "s.197(1)(b)", "presence": "PRESENT" } } ] }
```

`INFERENCE`: `axis: ACQUISITION_DATE` is the whole point. Both versions sit in **every** pack from
TY 2024-25 onward, because the rule keys on the asset, not on the year. Trying to express this with a
year-keyed lookup would require the TY 2045-46 pack to somehow remember 2024.

---

## V5 — Apportionment across a mid-year change

The hard part of T2. When a rule's value changes on, say, 1 October, an annual computation must apply
**both** versions to their own portions.

```java
public final class TemporallyApportionedCap {

    /**
     * Splits the tax year at every version boundary, evaluates each segment against the
     * version in force for it, and combines. The combination rule is DECLARED, not assumed.
     */
    public CapResult apply(RuleRow rule, TemporalContext t, CapContext ctx) {

        if (!rule.changesWithin(t)) {
            return rule.versionAt(t).cap().apply(ctx);           // the common case, unchanged
        }

        List<Segment> segments = Segments.split(t.taxYearStart(), t.taxYearEnd(), rule.versions());

        return switch (rule.midYearCombination()) {

            // Each segment gets its own cap, prorated to the segment. HRA, CEA, car perquisite.
            case PRORATE_PER_SEGMENT -> segments.stream()
                    .map(s -> s.version().cap()
                              .apply(ctx.prorated(s.range(), rule.prorationBasis())))
                    .reduce(CapResult::plus).orElseThrow();

            // One annual cap; the version in force at year end governs. Slab tables.
            case LATEST_VERSION_GOVERNS -> segments.getLast().version().cap().apply(ctx);

            // One annual cap; the version at year start governs. Rare; must be stated.
            case EARLIEST_VERSION_GOVERNS -> segments.getFirst().version().cap().apply(ctx);

            // The statute says how. A named rule supplies it.
            case STATUTORY -> ctx.pack().rule(rule.midYearCombinationRule()).evaluate(segments, ctx);

            // We do not know. Fail loudly rather than pick. D-NOSILENT.
            case UNDETERMINED -> throw new UndeterminedApportionmentException(rule.id(), segments);
        };
    }
}
```

`RECOMMENDATION`: **`UNDETERMINED` is the default for a newly authored mid-year change.** The pack
author must state the combination explicitly, and until they do the computation fails rather than
guessing. `INFERENCE`: guessing here is the most dangerous kind of silent error — it produces a
plausible number, it only occurs in the year the rule moved, and no golden test written before the
change would catch it.

The combination is data, with its own status:

```json
{ "ruleId": "SAL-ALW-CEA-2026", "axis": "TAX_YEAR",
  "midYearCombination": "PRORATE_PER_SEGMENT",
  "midYearCombinationStatus": "NEEDS VERIFICATION",
  "whatToCheck": "whether a mid-year change to the r.280(2) Sl.7 monthly cap applies per month or for the whole year" }
```

---

## V6 — Component lifecycle (V-4)

```java
public record ComponentDefinition(
        ComponentCode code,
        String        displayName,
        HeadCode      head,
        ComponentKind kind,
        CashNature    cashNature,
        Set<RuleId>   feedsInto,
        ComponentLifecycle lifecycle,        // *** new ***
        Provenance    provenance) {}

public record ComponentLifecycle(
        DateRange validity,
        Optional<ComponentCode> supersededBy,    // a rename or a merge
        DeprecationPolicy onWithdrawal) {}

public sealed interface DeprecationPolicy {
    /** The code is gone. A draft referencing it must be corrected by the user. */
    record RequireUserCorrection(String guidance) implements DeprecationPolicy {}
    /** The code maps cleanly to a successor; upcast the draft on read. */
    record AutoMigrate(ComponentCode to, Rate conversionFactor) implements DeprecationPolicy {}
    /** Still accepted for historical years, rejected for new ones. */
    record HistoricalOnly() implements DeprecationPolicy {}
}
```

> **Erratum (W-025, 2026-09-19):** this field list **abbreviates** `10-extensibility.md` E4.1 rather
> than superseding it. It correctly adds `lifecycle`, but silently omits two fields E4.1 declares:
>
> - **`TaxTreatment treatment`** — `TAXABLE | DEFERRED | DEDUCTIBLE`, per `05-domain-model.md:252-282`.
> - **`Set<RuleId> salaryBaseFor`** — which salary definitions include this component.
>   `SAL-HRA-004`'s *"basic + DA if the terms of employment so provide"* is exactly such a set, and
>   `W-062` would have nowhere to read it from.
>
> `ComponentDefinition` is implemented as the **union** of both lists, by the developer's decision.
> A third field is added that neither list carries: **`boolean informationalOnly`**, which
> `10-extensibility.md:267` requires by name — every component must feed at least one rule *"or be
> explicitly marked `informationalOnly`"* — and which was declared nowhere.
> Verified by: `10-extensibility.md:244-256, 267`; `./mvnw test -pl backend -Dtest=ComponentLifecycleTest`.

This closes the loop `14-operational-design.md` O12 opened on draft schema evolution:

```java
public final class DraftUpcaster {

    public UpcastResult upcast(DraftInputs in, RulePack targetPack) {
        var problems = new ArrayList<UpcastProblem>();
        var out      = DraftInputs.builder();

        for (SalaryComponent c : in.components()) {
            ComponentDefinition def = targetPack.components().get(c.code());

            if (def == null || !def.lifecycle().validity().coversTaxYear(targetPack.taxYear())) {
                switch (deprecationOf(c.code(), targetPack)) {
                    case AutoMigrate m -> out.add(c.withCode(m.to())
                                                   .withAmount(m.conversionFactor().of(c.amount()))
                                                   .withProvenance(EntryProvenance.MIGRATED));
                    case RequireUserCorrection r -> problems.add(
                            UpcastProblem.needsUser(c.code(), r.guidance()));   // never dropped
                    case HistoricalOnly h -> out.add(c.markedHistorical());
                    case null -> problems.add(UpcastProblem.unknownCode(c.code()));
                }
            } else {
                out.add(c);
            }
        }
        return new UpcastResult(out.build(), problems);   // problems BLOCK compute until resolved
    }
}
```

`INFERENCE`: a withdrawn component must never be **silently dropped** — that is O-1's silent zero in a
new costume, and it would be worse, because the figure would vanish from a draft the user had already
reviewed. `RequireUserCorrection` surfaces it; `UpcastProblem` blocks the computation.

---

## V7 — Clause-level change (T5, V-5)

A rule gaining or losing a clause is a new `RuleVersion` whose `CapShape` differs. What was missing is
**detecting it in review**. Extending `15-review-findings.md` C4's linter:

| Lint | Catches |
|---|---|
| A rule's `CapShape` **type** changed between versions (`Fixed` → `AggregateWithCeiling`) | a clause added; forces a reviewer to read the amendment |
| An `AggregateWithCeiling` gained or lost a **contributor** | `HP-CAP-SELFOCC-002`'s shape — FA 2026 s.38 pulling s.22(1)(c) inside the cap |
| A `LeastOf` gained or lost a **limb** | a new statutory ceiling |
| A `SlabLadder` changed **band count** | a slab added or merged |
| A rule has more than one version in a pack and `midYearCombination` is `UNDETERMINED` | **V5's fail-loud case, at authoring time** |
| A `RuleVersion` whose `validity` leaves a **gap** inside the pack's tax year | a period with no rule at all |
| Two versions of one rule with **overlapping** validity | ambiguity |

---

## V8 — The amendment ledger (V-5)

`Provenance` naming one instrument loses the chain. A rule amended three times needs all three, because
`A4.2` renders provenance to the user and a partial citation is a misleading one.

```java
public record AmendmentRef(
        String     instrumentCitation,   // "Finance Act, 2026 (4 of 2026), s.38"
        String     gazetteRef,           // "CG-DL-E-31032026-271439"
        LocalDate  notifiedOn,
        LocalDate  effectiveFrom,
        boolean    retrospective,
        URI        sourceUrl,
        VerificationStatus status) {}
```

Each `RuleVersion` carries the `AmendmentRef` that **introduced** it, so the version list *is* the
amendment history, ordered. The trace can then say:

> Standard deduction ₹75,000 — s.19(1) Table Sl. 2(a), Income-tax Act 2025, as in force for TY 2026-27.
> *This rule has not been amended since enactment.*

or

> Self-occupied interest cap ₹2,00,000 — s.22(2), **as amended by Finance Act 2026 s.38 with effect
> from 1 April 2026**, which brought pre-construction interest under s.22(1)(c) within the cap.
> *Before that amendment, pre-construction interest was outside it.*

`INFERENCE`: the second form is what a user with a five-year pre-construction instalment run actually
needs (`13-edge-cases.md` R-15), and it is only renderable if the chain is kept.

---

## V9 — Polling: no. Pinning and sweeping: yes.

The question raised polling. `RECOMMENDATION`: **do not poll for rule changes at runtime.** Three
reasons, and the third is decisive:

1. `D-IMMUTABLE` — packs ship with the JAR, checksummed. A runtime fetch reintroduces exactly the
   mutable-reference-data hazard that O-16 caused in the placeholder.
2. `O8` — the optimiser runs thousands of evaluations per request. A lookup that can vary between them
   makes a single computation internally inconsistent.
3. **A change must be *reviewed*, not *absorbed*.** `15-review-findings.md` C4 makes a rollover a
   signed-off legal change. Polling would apply a legal change with no reviewer, no diff and no
   golden-test run.

What replaces polling:

| Need | Mechanism |
|---|---|
| A stored result must stay explainable | pack version + checksum pinned on every `Computation` |
| A stored result must be known to be stale | `RulePack.supersedes` + the **staleness sweep** (`13-edge-cases.md` R-8), extended to fire on a new `RuleVersion` overlapping a computed year |
| The team must learn of a new instrument | an **out-of-band watch** — a scheduled job that checks the gazette index for new income-tax instruments and **opens a ticket**. It never writes a pack |
| A user must learn their figures moved | the sweep flags their `ACTED_ON` and `READY` drafts with a **diff**, never a silent recompute |

`INFERENCE`: the distinction is between **discovering** a change (automatable, and worth automating)
and **adopting** one (a reviewed code change). Conflating them is how a tax engine silently starts
computing a different answer.

---

## V10 — Effects, invariants, tests

| Document | Change |
|---|---|
| `05-domain-model.md` M1.2 | `RuleRow` gains `axis` and `List<RuleVersion>`; `CapShape`, `conditions`, `regimes` and `provision*` move onto the version |
| `15-review-findings.md` Part E | `Condition.EffectiveBetween` is **withdrawn** — validity belongs on the version, not in the predicate algebra (V-2) |
| `10-extensibility.md` E4 | `ComponentDefinition` gains `ComponentLifecycle` |
| `11-filing-drafts.md` F4 | `DraftUpcaster` runs on read; `UpcastProblem`s block compute |
| `13-edge-cases.md` R-8 | the staleness sweep fires on a new overlapping `RuleVersion`, not only on a superseded pack |
| `14-operational-design.md` O12 | the draft-upcast seam is now specified, not just named |
| `16-getting-started.md` | PR 2 gains `RuleVersion`, `Validity` and `TemporalContext`; PR 10's linter gains the V7 checks |

### New invariants

| | Invariant |
|---|---|
| **I13** | within a pack's tax year, a rule's versions **cover it with no gap and no overlap** — for `TAX_YEAR`-axis rules |
| **I14** | a rule with more than one version overlapping the tax year **must** declare a `midYearCombination` other than `UNDETERMINED` |
| **I15** | every `RuleVersion` carries an `AmendmentRef`; the first carries the enacting instrument |
| **I16** | a `PendingNotification` validity **must** name an `interimRule` that exists in the same pack |
| **I17** | every `ComponentCode` referenced by a draft resolves in the target pack, or has a `DeprecationPolicy` |
| **I18** | a rule whose `axis` is not `TAX_YEAR` **must** be supplied that coordinate by every node that evaluates it — checked at pack link time against the node's declared inputs |

### New tests

| ID | Case | Expected |
|---|---|---|
| **T18-1** | s.197(3): land acquired 22 July 2024, sold TY 2026-27 | the **better of** 12.5% without indexation and 20% with; the `E = A − B` excess ignored |
| **T18-2** | Same, acquired 23 July 2024 | flat 12.5%, no indexation. **The one-day boundary is the assertion** |
| **T18-3** | A node evaluates an `ACQUISITION_DATE` rule without supplying the date | `MissingTemporalCoordinateException`, **not** a fallback to the tax year |
| **T18-4** | A cap changes on 1 October with `PRORATE_PER_SEGMENT` | two segments, each at its own cap, summed; two trace lines |
| **T18-5** | Same, with `midYearCombination: UNDETERMINED` | **computation fails** with `UndeterminedApportionmentException` |
| **T18-6** | Pack with a rule-version gap inside the tax year | **load error**, invariant I13 |
| **T18-7** | Pack with two overlapping versions of one rule | **load error** |
| **T18-8** | `PendingNotification` rule reached | the **interim** rule is applied and the trace says the narrower rule is not yet in force |
| **T18-9** | Draft references a component withdrawn in the target pack, `RequireUserCorrection` | compute **blocked** with an `UpcastProblem`; the figure is **never silently dropped** |
| **T18-10** | Same with `AutoMigrate` and a conversion factor | migrated, amount converted, `EntryProvenance.MIGRATED` recorded |
| **T18-11** | A rule amended twice; the trace is rendered | **both** amendments cited in order, with effective dates |
| **T18-12** | A new pack adds a `RuleVersion` overlapping an already-computed year | the staleness sweep flags the affected `ACTED_ON` drafts with a diff; **no silent recompute** |
| **T18-13** | Synthetic pack: a limit that changes on 1 July of a synthetic year | both segments computed correctly, **no production source file changed** |

---

## V11 — The short answer

**Yes, it happens — four times in the current year alone, and once in a way that is permanent.**

- `D-YEARKEY` was necessary and not sufficient. The sharper rule is **`D-TEMPORALKEY`**: every lookup
  takes a temporal coordinate, and **each rule declares which clock it reads** — because s.197(3) is
  keyed on an asset's acquisition date and will still be keyed on it in twenty years.
- A rule is now a **stable ID with an ordered list of versions**, not a single value. The ID stays the
  contract; the version carries the cap, the conditions, the regime applicability and the amendment
  that introduced it.
- Mid-year changes are **apportioned by a declared combination rule**, and an undeclared one **fails
  the computation** rather than guessing.
- Components have lifecycles, and a withdrawn one **blocks** rather than silently vanishing from a
  draft the user already reviewed.
- And **no polling.** Discovering a change is automatable and worth automating; adopting one is a
  reviewed change with a diff and a golden-test run. Conflating the two is how an engine quietly
  starts computing a different answer.
