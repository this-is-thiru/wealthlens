# 21 — ITR forms as data

**Amends `01-rulebook.md` R22, `04-product-spec.md` F8 and `20-product-gaps.md` F36.**

## I1 — What was missing

`R22` records ITR applicability as **rulebook rows** and `F8`/`F36` as **features**. There is no
**model**: no `ItrFormSpec`, no schedules, no trace-to-cell mapping, and no mechanism for a new form.
Everything about forms was implicitly code.

That is the same mistake as the regime enum, one layer up — and the Rules themselves say so.

---

## I2 — The Rules make the form set explicitly per-year and open-ended

`FACT (S4, Income-tax Rules 2026, rule 164, read this session)`:

| | Evidence |
|---|---|
| **The rule is scoped to one tax year** | r.164(1): *"The return of income … **relating to the tax year commencing on the 1st April, 2026**, shall be in accordance with the provisions of this rule"* |
| **Historical form sets stay live** | r.164(14): *"Where a return of income relates to the tax year commencing on the 1st April, 2025 **or any earlier tax year**, it shall be furnished in **the appropriate form as applicable in that tax year**"* |
| **The set is nine forms, not four** | ITR-1 (SAHAJ), ITR-2, ITR-3, ITR-4 (SUGAM), ITR-5, ITR-6, ITR-7 — plus **ITR-V** (verification) and **ITR-UN** (updated return, r.165) |
| **There is a second dimension: the manner of furnishing** | r.164(12) Table: electronically under digital signature; EVC; data electronically then ITR-V; **and paper — for an individual aged eighty or more filing ITR-1 or ITR-4** |
| **The kind of return selects a different form entirely** | r.165(1): an updated return under s.263(6) is on **ITR-UN**, not on ITR-1..7 |
| **Nothing is attached to the return** | r.164(11): no computation statement, no TDS proof, no audit report accompanies it |

`INFERENCE`: r.164(14) is decisive. It is the statute saying *"forms are versioned by tax year and old
versions remain in force"* — precisely the extensibility question, answered by the Rules rather than
by me. A design that hard-codes a form set cannot satisfy it even for the two years being shipped.

### A correction to `R22`

`20-product-gaps.md` and my earlier framing assumed ITR-2 is a **superset** of ITR-1 and that selection
means "pick the simplest eligible form". **That is wrong.**

`FACT (S4, r.164(4))`: ITR-2 applies to *"a person being an individual **not being an individual to
whom sub-rule (2) applies**"* — sub-rule (2) being ITR-1. The forms are **mutually exclusive by
construction**, and each sub-rule's scope is defined by excluding the ones before it.

`INFERENCE`: selection is a **deterministic cascade**, not an optimisation. That is simpler and more
testable than what I had assumed, and it removes a product decision I was about to invent.

---

## I3 — The model

```java
package com.thiru.wealthlens.taxrules.filing;

public record ItrFormCode(String value) {}          // "ITR-1", "SAHAJ", "ITR-UN", "ITR-V"

public record ItrFormSpec(
        ItrFormCode      code,
        String           displayName,               // "SAHAJ (ITR-1)"
        int              cascadeOrder,              // r.164's own sub-rule order. Lower wins.
        Set<PersonType>  appliesTo,
        Set<ReturnKind>  returnKinds,               // ORIGINAL | BELATED | REVISED | UPDATED | LOSS
        List<Condition>  positiveConditions,        // r.164(2): what the form IS for
        List<Condition>  bars,                      // r.164(3): what disqualifies
        Set<ItrFormCode> excludes,                  // r.164(4)'s "not being a person to whom (2) applies"
        List<ScheduleRef> schedules,                // I5
        List<FurnishingOption> furnishing,          // r.164(12)
        Validity         validity,                  // 18 V4 — forms get revised mid-year
        Provenance       provenance) {}
```

Eligibility reuses the **`Condition` algebra** from `15-review-findings.md` Part E without change —
which is the payoff for having made it a typed, inspectable, sealed family:

```json
{ "code": "ITR-1", "displayName": "SAHAJ (ITR-1)", "cascadeOrder": 1,
  "appliesTo": ["INDIVIDUAL"],
  "returnKinds": ["ORIGINAL", "BELATED", "REVISED"],
  "positiveConditions": [
    { "type": "IsResident" },
    { "type": "Not", "c": { "type": "IsNotOrdinarilyResident" } },
    { "type": "AnyOf", "any": [
        { "type": "HasIncomeUnderHead", "head": "SALARIES" },
        { "type": "HasFamilyPension" } ] } ],
  "bars": [
    { "type": "IncomeAtLeast", "node": "totalincome", "limit": "5000001",
      "ruleRef": "ITR-1-BAR-001", "reason": "total income exceeds ₹50,00,000" },
    { "type": "OwnsMoreThanNHouseProperties", "n": 2,        "ruleRef": "ITR-1-ELIG-001" },
    { "type": "HasCarryForwardLossUnderHead", "head": "HOUSE_PROPERTY" },
    { "type": "HasCapitalGainsOtherThanLongTermUnder", "section": "198" },
    { "type": "IncomeAtLeast", "node": "capitalgains.ltcg198", "limit": "125001" },
    { "type": "HeldUnlistedEquityShare" },
    { "type": "IsDirectorInCompany" },
    { "type": "HasAgriculturalIncomeAbove", "limit": "5000" },
    { "type": "HasAssetsOutsideIndia" },
    { "type": "HasDeductionUnderSection93OtherThan", "clause": "93(1)(d)",
      "ruleRef": "ITR-1-BAR-002", "status": "STATUS UNCLEAR" },
    { "type": "HasDeferredEsopTax" } ],
  "excludes": [],
  "schedules": ["SCH-S", "SCH-HP", "SCH-OS", "SCH-VIA", "SCH-TDS1", "SCH-TDS2", "SCH-IT"],
  "furnishing": [
    { "manner": "ELECTRONIC_DSC" },
    { "manner": "ELECTRONIC_EVC" },
    { "manner": "ELECTRONIC_THEN_ITR_V" },
    { "manner": "PAPER", "conditions": [ { "type": "AgeAtLeast", "years": 80 } ],
      "provenance": { "sourceDescription": "r.164(12) Table Sl. 4", "status": "VERIFIED" } } ],
  "validity": { "type": "Between", "from": "2026-04-01", "to": null },
  "provenance": { "sourceDescription": "Income-tax Rules 2026, r.164(2) and (3)",
                  "sourceUrl": "https://egazette.gov.in/WriteReadData/2026/271092.pdf",
                  "draftOrFinal": "FINAL", "accessedOn": "2026-09-18", "status": "VERIFIED" } }
```

`INFERENCE`: `ITR-1-BAR-002` carries `"status": "STATUS UNCLEAR"` **on the individual bar**, not on the
whole form. `A4.2` can then tell the user *which* disqualification is uncertain, instead of flagging
the entire recommendation.

---

## I4 — Selection: a deterministic cascade

```java
public final class ItrFormSelector {

    public ItrSelection select(Computation c, Taxpayer t, ReturnContext rc, RulePack pack) {

        List<ItrFormSpec> candidates = pack.itrForms().values().stream()
                .filter(f -> f.validity().contains(rc.filingDate()))       // 18 V4
                .filter(f -> f.appliesTo().contains(t.personType()))
                .filter(f -> f.returnKinds().contains(rc.returnKind()))    // UPDATED → ITR-UN only
                .sorted(comparingInt(ItrFormSpec::cascadeOrder))           // r.164's own order
                .toList();

        var evaluated = new ArrayList<FormVerdict>();

        for (ItrFormSpec f : candidates) {
            // r.164(4): "not being an individual to whom sub-rule (2) applies"
            if (evaluated.stream().anyMatch(v -> v.eligible()
                    && f.excludes().contains(v.form().code()))) {
                evaluated.add(FormVerdict.excludedBy(f, ...));
                continue;
            }
            List<Condition> failedPositives = f.positiveConditions().stream()
                    .filter(cond -> !cond.holds(ctx)).toList();
            List<Condition> triggeredBars = f.bars().stream()
                    .filter(cond -> cond.holds(ctx)).toList();

            evaluated.add(failedPositives.isEmpty() && triggeredBars.isEmpty()
                    ? FormVerdict.eligible(f)
                    : FormVerdict.ineligible(f, failedPositives, triggeredBars));
        }

        ItrFormSpec chosen = evaluated.stream().filter(FormVerdict::eligible)
                .findFirst()                                   // cascade order — NOT "simplest"
                .map(FormVerdict::form)
                .orElseThrow(() -> new NoApplicableItrFormException(evaluated));

        return new ItrSelection(chosen, evaluated,
                                furnishingOptionsFor(chosen, t, pack),   // r.164(12)
                                scheduleMappingFor(chosen, c, pack));    // I5
    }
}
```

`RECOMMENDATION`: **return every verdict, not just the winner.** *"You cannot use ITR-1 because you
held unlisted equity shares and your long-term gains exceed ₹1,25,000"* is the useful output, and it
is exactly what `20-product-gaps.md` F33's completeness view and `A4.2`'s provenance need.

`NoApplicableItrFormException` is deliberate. A person with no applicable form means the pack is
incomplete for their profile — `D-NOSILENT` applied to filing.

---

## I5 — Schedules are shared, and mapped from the trace

`FACT`: Schedule S (salary), Schedule HP, Schedule OS and Schedule VI-A appear in **several** forms.
`RECOMMENDATION`: model them the way the Act models disallowance sets (`FACT (S2, s.205(1))`: one list
referenced by five regime sections) — **defined once, referenced by code**.

```java
public record ItrSchedule(
        ScheduleCode code,                  // "SCH-S"
        String       displayName,           // "Schedule S — Details of Income from Salary"
        List<ScheduleField> fields,
        Validity     validity,
        Provenance   provenance) {}

public record ScheduleField(
        String    fieldPath,                // "S.1", "S.2(a)"
        String    label,
        FieldSource source,                 // below
        boolean   required) {}

/** How a cell is filled. Data, not code — this is what makes F36 maintainable. */
public sealed interface FieldSource {
    /** The value of a rule from the trace. */
    record FromRule(RuleId rule)                       implements FieldSource {}
    /** The value of a named pipeline node. */
    record FromNode(NodeId node)                       implements FieldSource {}
    /** A sum of other fields in the same schedule. */
    record SumOf(List<String> fieldPaths)              implements FieldSource {}
    /** A component total, by code. */
    record FromComponents(Set<ComponentCode> codes)    implements FieldSource {}
    /** A user-supplied fact the computation does not carry (bank IFSC, for instance). */
    record FromProfile(String profilePath)             implements FieldSource {}
    /** Deliberately not derivable — the user must supply it, and is told so. */
    record ManualEntry(String guidance)                implements FieldSource {}
}
```

```json
{ "code": "SCH-S", "displayName": "Schedule S — Details of Income from Salary",
  "fields": [
    { "fieldPath": "S.1",    "label": "Gross salary",                 "source": { "type": "FromNode", "node": "salary.gross" } },
    { "fieldPath": "S.2",    "label": "Allowances exempt under s.11", "source": { "type": "FromRule", "rule": "SAL-HRA-001" } },
    { "fieldPath": "S.3",    "label": "Deductions under s.19(1)",     "source": { "type": "FromNode", "node": "salary.deductions" } },
    { "fieldPath": "S.4",    "label": "Income chargeable under Salaries",
                             "source": { "type": "SumOf", "fieldPaths": ["S.1", "-S.2", "-S.3"] } } ] }
```

`INFERENCE`: with `FieldSource` as data, `F36`'s export is a **fold over the schedule definition**, not
a hand-written mapper per form. A new form that reuses Schedule S needs no export code at all — which
is the whole point.

`ManualEntry` matters as much as the others: a cell the computation genuinely cannot produce must say
so. Silently leaving it blank is the filing equivalent of O-1's silent zero.

---

## I6 — Versioning, across years and within one

Two axes, both already solved by machinery in `18-temporal-versioning.md`:

| Axis | Mechanism | Evidence it is needed |
|---|---|---|
| **Across tax years** | one `itr-forms.json` **per pack**. TY 2025-26's pack keeps TY 2025-26's forms | `FACT (S4, r.164(14))`: an earlier year's return uses *"the appropriate form as applicable in that tax year"* |
| **Within a tax year** | `ItrFormSpec.validity` and `ItrSchedule.validity` — a form revised mid-year is a second version, selected by **filing date** | `FACT (S6)`: the Rules 2026 were amended four times inside the year, and G.S.R. 822(E) **substituted Forms 169 and 171** outright |

`RECOMMENDATION`: forms resolve on `TemporalAxis.FILING_DATE`, not `TAX_YEAR`. A return for TY 2026-27
filed in July 2027 must use the form version in force **when filed**, which may differ from the one
notified in March 2026. That is the same `D-TEMPORALKEY` discipline as `s.197(3)`, applied to filing.

---

## I7 — Playbook: a new form appears

| Step | Change | Code? |
|---|---|---|
| 1 | Add an `ItrFormSpec` to the year's `itr-forms.json` — code, cascade order, person types, return kinds | no |
| 2 | Express its eligibility and bars as `Condition`s | no |
| 3 | Set `excludes` against the forms it displaces, and adjust their `cascadeOrder` | no |
| 4 | Reference existing `ItrSchedule`s; add only genuinely new ones | no |
| 5 | List its `FurnishingOption`s | no |
| 6 | If a bar needs a predicate the algebra lacks → **a new `Condition` record** | one class |
| 7 | If a cell needs a source the five `FieldSource`s lack → **a new sealed `FieldSource`** | one class |
| 8 | Lint: the selector is exhaustive over person types; no two forms are simultaneously eligible | no |

`INFERENCE`: steps 6 and 7 are the escape hatches, both new classes, both serving **every** future form
rather than one. A new form that is "ITR-2 with one extra bar" is a JSON entry.

### And a form is withdrawn

Close its `validity`. Do **not** delete it — r.164(14) means a return for an earlier year still needs
it, and `REL-ARREARS-001` and reassessment reach back further still.

---

## I8 — Effects, invariants, tests

| Document | Change |
|---|---|
| `01-rulebook.md` R22 | rows stay as the **legal record**; the executable form lives in `itr-forms.json`. `ITR-1-BAR-002`'s `STATUS UNCLEAR` moves onto the individual bar |
| `04-product-spec.md` F8 | the advisor returns **all verdicts with reasons**, not one form |
| `20-product-gaps.md` F36 | the export is a fold over `ItrSchedule`, not a per-form mapper |
| `05-domain-model.md` M7 | packs gain `itr-forms.json` and `itr-schedules.json` |
| `16-getting-started.md` | PR 21 gains the selector; a new **PR 21b** adds the schedule mapping |

### New invariants

| | Invariant |
|---|---|
| **I19** | for every `(PersonType, ReturnKind)` the pack supports, **at least one** form is applicable — no taxpayer without a form |
| **I20** | no two forms are simultaneously eligible for the same facts — the `excludes` graph plus `cascadeOrder` must be a total order within a person type |
| **I21** | every `ScheduleRef` on a form resolves; every `FromRule`/`FromNode` in a schedule resolves in the same pack |
| **I22** | every form has at least one `FurnishingOption` whose conditions can be satisfied |

### Tests

| ID | Case | Expected |
|---|---|---|
| **T21-1** | Salary ₹18L, one house, savings interest ₹9,000 | **ITR-1**, all bars listed as not triggered |
| **T21-2** | Same plus LTCG under s.198 of ₹1,25,000 | **ITR-1** — at the limit, not over |
| **T21-3** | Same with ₹1,25,010 | **ITR-2**, reason names the ₹1,25,000 bar |
| **T21-4** | Same with any **short-term** capital gain | **ITR-2** |
| **T21-5** | Held one unlisted equity share for one day | **ITR-2**, reason names the holding |
| **T21-6** | Intraday equity present (`CapitalGainsType.SPECULATIVE`) | **ITR-3** — business income (`ITR-SPEC-001`) |
| **T21-7** | Total income ₹50,00,000 exactly | **ITR-1** — the bar is *exceeds* |
| **T21-8** | `ReturnKind.UPDATED` | **ITR-UN**, regardless of income profile (r.165) |
| **T21-9** | Individual aged 82 eligible for ITR-1 | furnishing options **include PAPER** (r.164(12) Sl. 4) |
| **T21-10** | Individual aged 79 eligible for ITR-1 | furnishing options **exclude PAPER** |
| **T21-11** | A return for TY 2025-26 filed now | the **TY 2025-26 pack's** form set is used (r.164(14)) |
| **T21-12** | Two forms made simultaneously eligible in a test pack | **load error**, invariant I20 |
| **T21-13** | A person type with no applicable form | **load error**, invariant I19 |
| **T21-14** | Schedule S exported for ITR-1 and for ITR-2 | identical field values from the **same** schedule definition |
| **T21-15** | A schedule field whose `FromRule` is absent from the trace | export shows it as **unavailable with a reason**, never blank |
| **T21-16** | Synthetic pack: a new "ITR-1A" inserted at cascade order 2 | selected for the facts it claims, existing forms unaffected, **no production source file changed** |

---

## I9 — The short version

**Yes — and the Rules say so more plainly than I could.** r.164(1) scopes the form set to *one tax
year*; r.164(14) says an earlier year's return uses *"the appropriate form as applicable in that tax
year"*. So forms are per-year data with historical versions retained, and there are nine of them, not
four — ITR-1 through ITR-7 plus ITR-V and ITR-UN.

What changed: `ItrFormSpec` and `ItrSchedule` are **pack data**; eligibility reuses the existing
`Condition` algebra unchanged; schedules are shared by reference the way the Act shares disallowance
sets; and `FieldSource` makes `F36`'s export a fold over data rather than a mapper per form. A new form
is a JSON entry, and a new *kind* of bar or cell source is one new sealed record.

One correction along the way: I had assumed ITR-2 is a superset of ITR-1 and that selection means
"pick the simplest eligible form". r.164(4) defines ITR-2 as being for *"an individual **not being an
individual to whom sub-rule (2) applies**"* — the forms are **mutually exclusive**, so selection is a
deterministic cascade in the Rules' own order, not an optimisation.
