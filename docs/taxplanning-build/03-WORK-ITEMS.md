# 03 — Work items

The ordered backlog. **Do them in order. One at a time. Explain, get approval, code, verify, report,
wait for review.** See `00-START-HERE.md` §1.

`DR` = design reference, always under `docs/taxplanning-design/`.
`TS` = `02-TECH-SPEC.md`. `PRD` = `01-PRD.md`.

Statuses live in `STATE.md`, **not here**. This file is read-only once written.

**On tests — rule B1.** *Every item carries its own test, and the test is not the compiler.*
`Verify: compiles` was swept out by **D-014**: it satisfied the letter of this rule and defeated its
purpose, since a record with a lookup method compiles perfectly while returning the wrong version.
An item that genuinely has nothing to assert beyond compilation should say **why** in one line. An item's `Verify` command may only
name a test that the item itself `Produces`, or one produced by an item **already done**. An item
whose `Verify` names a *later* item's test is malformed: it either ships untested code or cannot be
verified at all. Restructured on 2026-09-19 by decision **D-011** — see the note at the end of
Phase 1.

**On the type audit.** Phases 4–14 were audited in one pass (**Appendix A**, decision D-017), and
again for types named in a `permits` clause but never declared (**Appendix B**, D-021) — see **rule
B2** there: a `permits` clause lists only what exists. When
you reach any item listed there, **its `Produces` is Appendix A's row plus the phase table's** — and
three items in it have no owner at all.

**On retired ids.** Some ids below are **RETIRED** — merged into a neighbour. They are left in place,
not renumbered, because `W-001`…`W-012` are already referenced by commit subjects. A retired id is
no work. **146 base ids, 143 live items** (suffixed ids added by D-013, D-015, D-018 are counted).

**On dependencies.** This backlog deliberately encodes **none**. The ordering *is* the dependency
graph: assume every item depends on every item before it. So there is no such thing as "the next
independent item" — when something is `BLOCKED`, **stop and ask** (rule R6). Do not look for
something else to do.

---

## Phase 0 — Make the build accept the design (3 items)

### W-001 — Create the four module skeletons
- [ ] **Produces**
  `backend/src/main/java/com/thiru/wealthlens/taxrules/package-info.java`
  `backend/src/main/java/com/thiru/wealthlens/taxengine/package-info.java`
  `backend/src/main/java/com/thiru/wealthlens/taxdocs/package-info.java`
  and **modify** `backend/src/main/java/com/thiru/wealthlens/taxplanning/package-info.java`
- **Spec** TS T1
- **Do** four `@ApplicationModule(type = Type.CLOSED, allowedDependencies = {...})` declarations, exactly as TS T1 lists them. Add one placeholder class per new package so the package exists.
- **Verify** `./mvnw test -pl backend -Dtest=WealthLensModulithTest`
- **Done when** `modulithStructureIsValid()` passes with four modules declared.

### W-002 — Amend the money ArchUnit rule
- [ ] **Produces** modify `backend/src/test/java/com/thiru/wealthlens/architecture/ArchitectureTest.java`
- **Spec** `DR 19-engineering-foundations.md` E1 · TS T10
- **Do** amend `money_rounding_lives_in_one_place` to also permit `..taxengine.money..`, keeping the existing `because(...)` text and appending the TL-8 §7 justification. Add `taxengine_does_not_use_TMoney`. Add `only_the_adapter_converts`.
- **Verify** `./mvnw test -pl backend -Dtest=ArchitectureTest`
- **Done when** all existing rules still pass and three new rules are present.
- ⚠ **This item exists because the design would otherwise fail the build on the first commit.**

### W-003 — Scratch directory for rule packs
- [ ] **Produces** `backend/src/main/resources/rulepacks/.gitkeep`
- **Do** create the directory only.
- **Verify** `test -d backend/src/main/resources/rulepacks`

---

## Phase 1 — Foundational types (9 items)

### W-004 — The engine ArchUnit rules
- [ ] **Produces** modify `ArchitectureTest.java`
- **Spec** TS T10 rows 4–8 and 11 · `DR 23` A3, A6
- **Do** add: `taxengine` depends on no `..repository..` / `org.springframework.data..` / `..taxplanning..`; no `LocalDate.now` / `Instant.now` / `Clock`; no `catch (Exception|RuntimeException)`; no `BigDecimal` field outside `..taxengine.money..`. Add a test that asserts the string `ComputationStep` appears nowhere under `backend/src`.

> **Erratum (W-013, 2026-09-19):** the `BigDecimal` exemption is `..taxrules.money..`, and the
> rule now covers `taxengine`, `taxrules` and `taxdocs`. See `STATE.md` **B-001**.
- **Verify** `./mvnw test -pl backend -Dtest=ArchitectureTest`

### W-005 — `Money`
- [ ] **Produces** `taxengine/money/Money.java`

> **Erratum (W-013, 2026-09-19):** produced at `taxrules/money/Money.java`. Moved after the
> modulith test rejected `taxrules -> taxengine`; see `STATE.md` **B-001**. `W-006`'s
> `MoneyTest` and `W-007`'s `Rate`/`RateTest` moved with it.
- **Spec** **TS T3 — copy it verbatim**
- **Verify** `./mvnw test -pl backend -Dtest=ArchitectureTest` (compiles, rule passes)

### W-006 — `MoneyTest`
- [ ] **Produces** `backend/src/test/java/com/thiru/wealthlens/taxengine/money/MoneyTest.java`
- **Spec** `DR 08-golden-tests.md` T9-14 · `DR 13-edge-cases.md` R-9
- **Do** test: construction rounds to scale 2 · `plus`/`minus` exact · `floorAtZero` · `min`/`max` · **`roundToNearestTenPerSection516`** for 1234 → 1230, 1235 → 1240, 1234.99 → 1230, and the negative of each · null rejected.
- **Verify** `./mvnw test -pl backend -Dtest=MoneyTest`
- ⚠ Expected values come from `DR 01-rulebook.md` `RATE-ROUND-002`. **Do not derive them from your code.**

> **Erratum (W-006, 2026-09-19):** the Spec line cites `DR 08-golden-tests.md` **T9-14**, which is
> *"`portfolio` read-model is unavailable → 503"* and has nothing to do with rounding. The rounding
> case is **T13-14** in `13-edge-cases.md`: *"Refund of ₹1,234.99 — paise ignored → 1,234 → last
> figure 4 → ₹1,230."*
> Verified by: `docs/taxplanning-design/08-golden-tests.md:851` and `13-edge-cases.md:514`.

### W-007 — `Rate` and `RateTest`
- [ ] **Produces** `taxengine/money/Rate.java`, `.../RateTest.java`
- **Spec** TS T3 verbatim
- **Do** test `percent("30").of(Money.of("100"))` is `30.00`; `percent("12.5")`; `asPercentLabel()`.
- **Verify** `./mvnw test -pl backend -Dtest=RateTest`

### W-008 — Identifier types
- [ ] **Produces** `taxrules/id/{RuleId,NodeId,ComponentCode,RegimeCode,ItrFormCode,ScheduleCode,TaxYearId}.java`
- **Spec** TS T3 — patterns verbatim
- **Verify** `./mvnw test -pl backend -Dtest=IdentifierTest`

### W-009 — `IdentifierTest`
- [ ] **Produces** `.../taxrules/id/IdentifierTest.java`
- **Do** one valid and one invalid example per type; `TaxYearId(2026).label()` is `"2026-27"`, `startsOn()` is `2026-04-01`, `endsOn()` is `2027-03-31`.
- **Verify** `./mvnw test -pl backend -Dtest=IdentifierTest`

### W-010 — `Provenance`, `Instrument`, `GoverningInstruments`
- [ ] **Produces** `taxrules/registry/{Provenance,Instrument,GoverningInstruments,VerificationStatus,DraftOrFinal}.java`
- **Spec** `DR 05-domain-model.md` M1.2 · `DR 18` V8 (`AmendmentRef`)
- **Do** records only. `VerificationStatus` = `VERIFIED | NEEDS_VERIFICATION | STATUS_UNCLEAR`.
- **Verify** compiles

### W-011 — `DateRange`, `Validity`, `TemporalAxis`
- [ ] **Produces** `taxrules/temporal/{DateRange,Validity,TemporalAxis}.java`
- **Spec** `DR 18-temporal-versioning.md` V4 — the three `Validity` variants verbatim
- **Verify** `./mvnw test -pl backend -Dtest=ValidityTest`

### W-012 — `ValidityTest`
- [ ] **Produces** `.../taxrules/temporal/ValidityTest.java`
- **Do** `Between` containment incl. both boundaries · `Retrospective` keeps both dates and `contains` uses the deemed date · `PendingNotification.contains` is **always false**.
- **Verify** `./mvnw test -pl backend -Dtest=ValidityTest`

> **Erratum (D-011, 2026-09-19):** `W-005`/`W-006`, `W-008`/`W-009` and `W-011`/`W-012` are each a
> single piece of work split across two ids — the first item's `Verify` names the second item's test.
> All three were **executed as pairs** and committed together; the ids stand because their commit
> subjects reference them. Every later occurrence of this shape is merged below.
> Verified by: `git log --oneline --grep='^taxplanning W-'` shows `W-011/W-012` in one commit.

---

## Phase 2 — Cap shapes and conditions (10 ids, 6 live)

⚠ **Why this phase was restructured.** `W-013` as originally written declared
`sealed interface CapShape permits Fixed, PercentOfBase, LeastOf, AggregateWithCeiling, SlabLadder,
PerUnitPerPeriod` while `W-014`–`W-019` created those six types. **A sealed type does not compile
until every type in its `permits` clause exists** (JLS 8.1.1.2), so the backlog as written left the
build red from `W-013` to `W-019` and gave `W-013` no `Verify` at all. Each item below now grows the
`permits` clause by the one type it adds, and carries the test for that type.

### W-013 — `CapShape`, `CapResult`, `CapContext`, `Fixed`, `PercentOfBase`
- [ ] **Produces** `taxrules/cap/{CapShape,CapResult,CapContext,Fixed,PercentOfBase}.java`, `.../taxrules/cap/CapShapeTest.java`
- **Spec** `DR 05-domain-model.md` M1.3
- **Do** `sealed interface CapShape permits Fixed, PercentOfBase` — **the two that exist**; later items extend the clause. `CapResult(Money value, String limitedBy)`. Test both shapes, and `DR 08-golden-tests.md` **T5-1**.

> **Erratum (W-013, 2026-09-19):** **T5-1 does not belong to this item.** `08-golden-tests.md:456`
> T5-1 is the 80C aggregate with the NPS sub-cap — an `AggregateWithCeiling` case, which is
> **W-018**. This item tests `Fixed` with `SAL-STDDED-001` (₹75,000, `01-rulebook.md:373`) and
> `PercentOfBase` with `HP-DED-30-001` (30% of ₹5,22,000 = ₹1,56,600, `01-rulebook.md:866,897`).
> This item also produces **`taxrules/id/BaseId.java`**, added to its scope by the developer:
> `05-domain-model.md:156` gives `PercentOfBase` a base *identifier*, and no item defined one.
> Verified by: `docs/taxplanning-design/08-golden-tests.md:456`.
- **Verify** `./mvnw test -pl backend -Dtest=CapShapeTest`
- **Done when** the sealed family compiles closed over exactly the types that exist.

### W-014 — RETIRED
- Merged into **W-013** by D-011: `Fixed`/`PercentOfBase` verified with `CapShapeTest`, which was `W-020`'s output. No work.

### W-015 — `SlabLadder` with band-continuity validation
- [ ] **Produces** `taxrules/cap/SlabLadder.java`, `.../taxrules/cap/SlabLadderTest.java`, and **modify** `taxrules/cap/CapShape.java` (add `SlabLadder` to `permits`)
- **Spec** `DR 16-getting-started.md` S2 step 3 — **copy the implementation** · `DR 01-rulebook.md` `RATE-SLAB-BAND-002`
- ⚠ The arithmetic lower bound **is** `lowerExclusive`. A band written `400001` must fail `validate()`.
- **Do** the TY 2026-27 default ladder from `DR 01-rulebook.md` `RATE-SLAB-NEW-2026`: assert tax at ₹4,00,000 = ₹0; ₹8,00,000 = ₹20,000; ₹12,00,000 = ₹60,000; ₹16,00,000 = ₹1,20,000; ₹20,00,000 = ₹2,00,000; ₹24,00,000 = ₹3,00,000; ₹50,00,000 = ₹10,80,000. Assert a `400001` band fails `validate()`. Assert `Σ` per-band `==` whole.
- ⚠ **Every number above is in `DR 01-rulebook.md` R3 and `DR 08-golden-tests.md` T2-1. Copy, do not compute.**
- **Verify** `./mvnw test -pl backend -Dtest=SlabLadderTest`

### W-016 — RETIRED
- Merged into **W-015** by D-011. No work.

### W-017 — `LeastOf` with named limbs
- [ ] **Produces** `taxrules/cap/LeastOf.java`, `.../taxrules/cap/LeastOfTest.java`, and **modify** `CapShape.java` (`permits`)
- ⚠ `CapResult.limitedBy` must name the **winning limb**. `DR 06-algorithms.md` G1.3 depends on it.
- **Do** test that each limb can win, and that `limitedBy` names it. Test the tie case explicitly — state which limb wins and why.
- **Verify** `./mvnw test -pl backend -Dtest=LeastOfTest`

### W-018 — `AggregateWithCeiling`
- [ ] **Produces** `taxrules/cap/AggregateWithCeiling.java`, `.../taxrules/cap/AggregateWithCeilingTest.java`, and **modify** `CapShape.java` (`permits`)
- **Spec** `DR 06-algorithms.md` G2.1
- ⚠ **Sub-cap first, then the ceiling.** Getting this order wrong is ₹60,000 too generous (`DR 08` T5-3).
- **Do** `DR 08-golden-tests.md` **T5-3 verbatim** — correct ₹90,000, wrong ₹1,50,000. Assert the *correct* value; the wrong one belongs in a comment, never as an expectation.
- **Verify** `./mvnw test -pl backend -Dtest=AggregateWithCeilingTest`

### W-019 — `PerUnitPerPeriod`
- [ ] **Produces** `taxrules/cap/PerUnitPerPeriod.java`, `.../taxrules/cap/PerUnitPerPeriodTest.java`, and **modify** `CapShape.java` (`permits`)
- **Spec** `DR 15-review-findings.md` C3 — it computes its own annual equivalent; **no caller divides**.
- **Do** the CEA case from `DR 01-rulebook.md` rule 280(2): ₹3,000/month, hostel ₹9,000/month, per child, capped at the number of children the rule allows. Assert a part-year period does not silently annualise.
- **Verify** `./mvnw test -pl backend -Dtest=PerUnitPerPeriodTest`
- **Done when** `CapShape`'s `permits` clause names all six shapes and the family is closed.

### W-020 — RETIRED
- Merged into **W-013** (T5-1), **W-015**, **W-017**, **W-018** (T5-3) and **W-019** by D-011. The single shared `CapShapeTest` was what made five items unverifiable. No work.

### W-021 — `Condition`, `ConditionContext`, and the person-fact predicates
- [ ] **Produces** `taxrules/condition/{Condition,ConditionContext,PersonType,DisabilitySeverity,AgeSubject}.java` + `IsResident`, `AgeAtLeast`, `HasPersonType`, `IsCertifiedDisabled`, and `.../condition/ConditionTest.java`
- **Spec** `DR 15-review-findings.md` Part E — the person-facts group, **verbatim except `AgeAtLeast`** (see the erratum below)
- **Do** `sealed interface Condition permits` **the four that exist**; later items extend the clause, as `CapShape` does. One positive and one negative case per record.
- **Verify** `./mvnw test -pl backend -Dtest=ConditionTest`

### W-021b — the amount and structure predicates
- [ ] **Produces** `taxrules/condition/{HeadCode,EvidenceType}.java` + `IncomeAtMost`, `IncomeAtLeast`, `HasIncomeUnderHead`, `ComponentPresent`, `EvidenceRecorded`, `PaidOtherThanInCash`, **modify** `Condition.java` (`permits`), **extend** `ConditionTest`
- **Spec** `DR 15-review-findings.md` Part E — the amounts and structure groups
- ⚠ `EvidenceType` has **no values stated anywhere in the design**. Source them from `DR 12-claim-integrity.md` C3.2 or mark `BLOCKED` — do not invent them.
- **Verify** `./mvnw test -pl backend -Dtest=ConditionTest`

### W-021c — composition, and closing the family
- [ ] **Produces** `taxrules/condition/{AllOf,AnyOf,Not}.java`, **modify** `Condition.java` (`permits`), **extend** `ConditionTest`
- **Do** `AllOf`/`AnyOf`/`Not` composition, including nesting. Close with an **exhaustive switch with no `default`**, as `PerUnitPerPeriodTest` does for `CapShape`.
- ⚠ `EffectiveBetween` was **withdrawn** by `DR 18` V-2. Do not add it.
- **Verify** `./mvnw test -pl backend -Dtest=ConditionTest`

> **Erratum (W-021, 2026-09-19):** three corrections to the item this replaced.
>
> 1. **There are 13 records, not 16.** A sweep of every `implements Condition` across all 25 design
>    documents finds 4 person facts, 2 amounts, 4 structure and 3 composition — 13 — plus the
>    commented-out `EffectiveBetween`, which would make 14. No source states 16.
> 2. **`AgeAtLeast` needs a `subject`.** Part E declares `record AgeAtLeast(int years)`, but the pack
>    JSON five lines below it reads `{"type":"AgeAtLeast","years":60,"subject":"SELF"}` and
>    `"subject":"INSURED_PERSON"`, and `19-engineering-foundations.md:199` writes
>    `anyOf(ageAtLeast(60, SELF), ageAtLeast(60, INSURED_PERSON))`. Without the field both limbs of
>    that `AnyOf` are identical and `DED-80D-007` collapses. Added as `AgeSubject`.
> 3. **Five supporting types were produced by no item** — `ConditionContext`, `PersonType`,
>    `DisabilitySeverity`, `HeadCode`, `EvidenceType`. Assigned above.
>
> The item was also **split three ways by decision D-013**: at ~750 lines it was 2.5x the §7 sizing
> limit. Ids are suffixed rather than renumbered.
> Verified by: `grep -rh "record .*implements Condition" docs/taxplanning-design/`;
> `15-review-findings.md:533-534`; `19-engineering-foundations.md:199`.

### W-022 — RETIRED
- Merged into **W-021** by D-011. No work.

---

## Phase 3 — Rule pack loading (12 items)

⚠ **Restructured by D-011,** then again by **D-014**. Six items (`W-028`–`W-033`) originally
verified with `W-034`'s `RulePackLoadTest`; each now carries its own test, and `W-034` keeps only
what it uniquely proves — the **real** `2026-27` pack, end to end. D-014 then merged `W-023`/`W-024`
and replaced every `Verify: compiles` in this file with a real command.

> **Erratum (W-023, 2026-09-19):** `W-023` as written could not compile. `RuleVersion`'s third field
> is `RegimeApplicability`, which was **`W-024`'s** output — the dependency ran backwards through
> the ordering this backlog relies on. Six further types were produced by **no item**: `Provision`
> and `Presence` (`05-domain-model.md:108-109`, two fields of `RuleVersion`), `TemporalContext`,
> `MissingTemporalCoordinateException` and `NoRuleVersionException` (`18-temporal-versioning.md` V4).
> All are now in the merged `W-023`.
> Verified by: `03-WORK-ITEMS.md:240` assigned `RegimeApplicability` to `W-024`;
> `grep -c` over this file found **0** mentions of each of the six.

> **Erratum (W-026, 2026-09-19):** D-014's sweep, three items earlier, **collapsed six `Produces`
> lists to `{...}`** while rewriting their `Verify` columns — `W-025`, `W-026`, `W-036`, `W-037`,
> `W-039` and `W-043`. `00-START-HERE.md` §6 verifies an item against exactly that list, so an
> abbreviated one removes the check it exists for. The lists are restored above from the pre-sweep
> text, with `TaxTreatment` added to `W-025`'s, since the union decision put it there.
> My error, in an edit whose purpose was to make verification stricter.
> Verified by: `git show f2a7db4^:docs/taxplanning-build/03-WORK-ITEMS.md`.

> **Erratum (W-026, 2026-09-19):** `W-026` is **split three ways** by decision **D-015** — at ~600
> lines with tests it was twice the §7 limit, and six types it needs (`ElectionKind`,
> `ElectionVehicle`, `FormRef`, `DeadlineSpec`, `ElectionRoute`, plus `RegimeSpec`'s own id types)
> were produced by no item. The split follows the design's own seams: L2 is the lifecycle, L3 is the
> election, and `RegimeSpec` binds them and cannot be written before both. The withdrawal and
> revival clauses of the original `Verify` line move to `W-026b`, where `AfterWithdrawal` lives.
> Verified by: `17-regime-lifecycle.md` L2, L3, `:105-119`; `grep -c` found 0 backlog mentions of each.

> **Erratum (W-030, 2026-09-19):** `W-030` was assigned **twelve** invariants. Eight of them
> constrain types Phase 3 does not build, and an invariant cannot be written before its subject
> exists. Redistributed by decision **D-016** to the items that build what they constrain:
>
> | Invariant | Now belongs to | Why it could not live here |
> |---|---|---|
> | **I10** `entryWindow ⊆ inForceWindow` | **already enforced**, `RegimeLifecycle` constructor (W-026) | unconstructable, like I1 |
> | **I16** `PendingNotification` names an interim rule in the pack | **already done**, W-029's generalised I2 | — |
> | **I18** a non-`TAX_YEAR` rule is supplied its coordinate | **W-039** (`NodeRegistry`) | the node-to-rule mapping is engine-side |
> | **I19, I20** ITR form applicability and uniqueness | **W-090** (`ItrFormSpec`) | no form type exists until Phase 10 |
> | **I21, I22** `ScheduleRef`/`FromRule`/`FromNode` resolution; furnishing options | **W-092** (`ItrSchedule`) | same |
> | **I23** every claimable rule has a catalogue entry | **W-117** (claim catalogue) | the catalogue is Phase 12 |
> | **I27** perturbing `content`/`forms` changes no number | **W-146** | it is a property test needing a working engine |
>
> Each receiving item must add the invariant **and a test for it**; none is dropped. `I3` was
> deferred to `W-039` on the same grounds by `W-029`'s erratum.
>
> `MidYearCombination` and `RuleRow.midYearCombination`/`midYearCombinationRule` were declared by
> `18-temporal-versioning.md` V5 and produced by no item; added to `W-030` by the developer's
> decision, which is what makes I14 checkable.
> Verified by: `02-TECH-SPEC.md:244-281`; `03-WORK-ITEMS.md:440-481`.

> **Erratum (W-033, 2026-09-19):** `W-033` is **split three ways** by decision **D-019** — ~55
> distinct shapes and ~700 lines was four times the §7 limit.
>
> Its stated `Verify`, *"round-trips the fixture pack from W-028"*, also proves almost nothing: that
> fixture has **empty arrays by design**, so round-tripping it exercises no shape. The three items
> test their parsers directly against JSON snippets, including the real pack's own content, and the
> end-to-end load stays with `W-034` where it belongs.
>
> Parsing is **hand-written** rather than Jackson-annotated, by the developer's decision.
> `19-engineering-foundations.md` E4 makes *"the loader throws nothing but
> `PackValidationException`"* a property test, and Jackson's `InvalidTypeIdException` is addressed to
> a Java developer rather than to whoever wrote the pack. Annotating the sealed families would also
> put Jackson on `taxrules` domain types.
> Verified by: the shape count above; `19-engineering-foundations.md` E4, E9.

| Item | Produces | Spec | Verify |
|---|---|---|---|
| **W-023** | **MERGED with W-024.** `taxrules/temporal/{RuleVersion,AmendmentRef,Provision,Presence,TemporalContext,MissingTemporalCoordinateException,NoRuleVersionException}.java`, `taxrules/pack/{RuleRow,RegimeApplicability}.java`, `.../pack/RuleRowTest.java` | `DR 18` V4, V8 · `DR 05` M1.2 · `DR 10` E3.3 | `-Dtest=RuleRowTest` — `versionAt` on a hit, a gap, both boundaries and a `PendingNotification`; `changesWithin` true and false |
| **W-024** | **RETIRED** — merged into `W-023` by D-014 | — | — |
| **W-025** | `taxrules/component/{ComponentDefinition,ComponentLifecycle,DeprecationPolicy,CashNature,ComponentKind,TaxTreatment}.java` + `.../component/ComponentLifecycleTest.java` | `DR 10` E4, `DR 18` V6 | `-Dtest=ComponentLifecycleTest` — a withdrawn component is rejected, not silently zero (`D-NOSILENT`); each `DeprecationPolicy` branch |
| **W-026** | `taxrules/regime/{RegimeLifecycle,ContinuationPolicy,InvalidationPolicy}.java` + `.../regime/RegimeLifecycleTest.java` | `DR 17` L2 | `-Dtest=RegimeLifecycleTest` — entry closed but still in force (ss.201/204 grandfathering); `entryWindow` outside `inForceWindow` rejected; each `ContinuationPolicy` and `InvalidationPolicy` branch |
| **W-026b** | `taxrules/regime/{ElectionProtocol,BindingSpec,AfterWithdrawal,ElectionKind,ElectionVehicle,FormRef,DeadlineSpec,ElectionRoute}.java`, **extend** `RegimeLifecycleTest` | `DR 17` L3 | `-Dtest=RegimeLifecycleTest` — withdrawal once then permanently barred; the **s.202(4)(a)(iv) revival carve-out**; `bindingAppliesWhen` makes one regime bind a person with business income and not one without |
| **W-026c** | `taxrules/regime/RegimeSpec.java` + the id types it needs (`DisallowanceSetId`, `ConditionSetId`, `BehaviourId`, `PipelineSpec`), **extend** `RegimeLifecycleTest` | `DR 17` `:105-119` | `-Dtest=RegimeLifecycleTest` — a regime binds its lifecycle and election together; `isDefault` is per `(taxYear, PersonType)` (invariant I11) |
| **W-027** | `taxrules/pack/{RulePack,RulePackLayer,RulePackRef}.java` + `.../pack/RulePackLayerTest.java` | `DR 23` A2 — **four layers, separate versions** | `-Dtest=RulePackLayerTest` — the cache key covers `law`+`exec` and **not** `forms`/`content`; each layer versions independently |
| **W-028** | `taxrules/pack/{RulePackProvider,ClasspathRulePackProvider}.java` + `.../pack/ClasspathRulePackProviderTest.java` + a **minimal fixture pack** under `src/test/resources/rulepacks-fixture/` | `DR 10` E3.5, `DR 23` A2 | `-Dtest=ClasspathRulePackProviderTest` |
| **W-029** | `taxrules/validate/PackValidator.java` — invariants I1–I7 — + `.../validate/PackValidatorTest.java` | TS T5 | `-Dtest=PackValidatorTest` — one violating and one clean case **per invariant**; build the rows in code, no files needed |

> **Erratum (W-015, 2026-09-19):** `PackValidationException` — which `02-TECH-SPEC.md:244` names as
> the failure type for **every** T5 invariant, and which `19-engineering-foundations.md:343` makes a
> property test subject — was produced by **no item**. `W-015` needed it for `SlabLadder.validate()`
> and created it at `taxrules/validate/PackValidationException.java`, by the developer's decision.
> `W-029` should extend it, not redefine it.
> Verified by: `grep -rn PackValidationException docs/` found three design uses and no `Produces`.

| **W-030** | `taxrules/validate/PackValidator.java` — invariants **I13, I14, I15** + `taxrules/pack/MidYearCombination.java` — + **extend** `PackValidatorTest` | TS T5 · `DR 18` V5 | `-Dtest=PackValidatorTest` — a year with a gap; two versions on one day; `UNDETERMINED` where the rule actually changes; a version with no `AmendmentRef` |
| **W-031** | `taxrules/registry/{TaxYearRegistry,TaxYearRegistryEntry}.java` + `.../registry/TaxYearRegistryTest.java` | `DR 05` M1.1 | `-Dtest=TaxYearRegistryTest` |
| **W-031b** | `taxrules/regime/{RegimeBehaviour,RegimeBehaviourRegistry,DataDrivenRegimeBehaviour}.java` + `.../regime/RegimeBehaviourRegistryTest.java`, **extend** `PackValidator` with **invariant I9** and `PackValidatorTest` | `DR 17` L5 (`:315-350`) | `-Dtest=RegimeBehaviourRegistryTest,PackValidatorTest` — a pack naming a `BehaviourId` with no registered implementation is a **load error**; a regime declaring none gets `DataDrivenRegimeBehaviour`'s defaults |
| **W-032** | pack JSON: `rulepacks/2026-27/manifest.json` + `law/rates.json` + `exec/pipelines.json` + `law/regimes.json` + `.../pack/PackJsonWellFormedTest.java` | `DR 16` S2 step 4 — **copy the JSON verbatim** | `-Dtest=PackJsonWellFormedTest` — parses, and **every slab band lower bound ends in `0000`** (the `400001` trap) |
| **W-032b** | the **build-time constant generator**: a Maven plugin execution reading `rulepacks/**/*.json` and emitting `taxrules/generated/{RuleIds,ComponentCodes}.java` into `target/generated-sources`, + `.gitignore` for them, + `.../generated/GeneratedConstantsTest.java` | `DR 19` E-series (`:236-264`) | `-Dtest=GeneratedConstantsTest` — `RuleIds.RATE_SLAB_NEW_2026` resolves to the pack's own id; a code removed from every pack makes its Java references **fail to compile**; a typo in a pack fails the **build**, not startup |
| **W-033** | `taxrules/json/{PackJson,ValueJson,ShapeJson}.java` + `.../json/PackJsonTest.java` | `DR 19` E9 — reuse `TJsonMapper` | `-Dtest=PackJsonTest` — leaf values, `Validity`, `RegimeApplicability` and all six `CapShape`s; **every** malformed input yields a `PackValidationException` naming the field |
| **W-033b** | `taxrules/json/{ConditionJson,RuleJson}.java` + **extend** `PackJsonTest` | `DR 15` Part E · `DR 18` V4 | `-Dtest=PackJsonTest` — all 13 `Condition` variants; a `RuleRow` with versions, axis and `introducedBy` |
| **W-033c** | `taxrules/json/{RegimeJson,RegistryJson,JsonPackAssembler}.java` + **extend** `PackJsonTest` | `DR 17` L2, L3 · `DR 10` E4 | `-Dtest=PackJsonTest` — regimes, disallowance and condition sets, components; `JsonPackAssembler` implements `W-028`'s seam |
| **W-034** | `.../taxrules/pack/RulePackLoadTest.java` | `DR 08` T9-1…T9-7 | `-Dtest=RulePackLoadTest` — the **real** `2026-27` pack, end to end: provider → deserialiser → validator → registry |
| **W-034b** | **modify** `taxrules/json/{PackJson,ValueJson,ShapeJson,ConditionJson,RuleJson,RegimeJson,RegistryJson}.java`, **modify** `RulePackLoadTest` | `DR 19` E4 | `-Dtest=PackJsonTest,RulePackLoadTest` — **every** malformed pack yields a `PackValidationException` carrying the path, including one rejected by a domain constructor. Added after `W-034` found T9-4 throwing `IllegalArgumentException` |

> **Erratum (W-034, 2026-09-19):** `W-034` found that `19-engineering-foundations.md` E4's property
> — *"the loader throws nothing but `PackValidationException`"* — **does not hold**. T9-4 surfaces as
> `IllegalArgumentException`, thrown by `Provenance`'s compact constructor: correct for a Java type,
> wrong at the loader boundary, and the same is true of every other domain-constructor rejection
> reached through parsing. A pack author gets a Java-flavoured error with no file, no path and no
> rule id, where every other defect names `law/rates.json rules[0] (RATE-SLAB-NEW-2026)…`.
>
> That is the exact argument `W-033` made for hand-writing the parser, so it is a defect rather than
> a preference. Fixed by **`W-034b`** (decision **D-020**), added above.
> Verified by: `RulePackLoadTest.T9-4` asserted `IllegalArgumentException` until `W-034b`.

⚠ **W-032 is the highest-risk item in Phase 3.** Every number in it is in `DR 16-getting-started.md`
S2 step 4 and `DR 01-rulebook.md` R3/R4. Copy character by character. Bands are `400000`, **not**
`400001`.

---

## Phase 4 — The DAG runner (10 ids, 8 live)

⚠ **Restructured by D-011.** `W-038`, `W-042` and `W-044` verified with `W-045`; `W-040` with
`W-041`. `W-044`'s `NodeHarness` moves **before** `W-042`, because a runner cannot be tested without
test nodes to run.

| Item | Produces | Spec | Verify |
|---|---|---|---|
| **W-035** | `taxengine/pipeline/{NodeValue,MoneyValue,TaxByBasis,SpecialRateId}.java` + `.../pipeline/NodeValueTest.java` | `DR 15` B2, B4 | `-Dtest=NodeValueTest` — `NodeValue` is closed over what exists; `TaxByBasis` keeps slab and special-rate tax apart |
| **W-036** | `taxengine/trace/{TraceLine,TraceAnnotation,Notice,StepKind,Severity}.java` + `.../trace/TraceLineTest.java` | `DR 23` A8 — **two separate records** | `-Dtest=TraceLineTest` — a trace line carries its rule id and provenance; an annotation is not a notice |
| **W-037** | `taxengine/input/{EngineInputs,TaxpayerFacts,FamilySnapshot,LedgerSnapshot,RegimeElectionSnapshot,FilingFacts}.java` + `.../input/EngineInputsTest.java` | `DR 23` A3 — **verbatim** | `-Dtest=EngineInputsTest` — every collection is defensively copied; the record is complete enough that the engine fetches nothing. **+ L5 guard 3** (moved here by `W-031b`): once `EngineInputs` exists, add `RegimeBehaviour.adjustPipeline` and the ArchUnit rule that **lists every class overriding it** against a reviewed file — overriding is allowed, doing so invisibly is not |
| **W-038** | `taxengine/input/InputsFingerprint.java` + `.../input/InputsFingerprintTest.java` | `DR 15` F6 + `DR 23` A3 | `-Dtest=InputsFingerprintTest` — equal inputs ⇒ equal fingerprint; **any** field change ⇒ different; field **order** irrelevant |
| **W-039** | `taxengine/pipeline/{ComputationNode,NodeContext,NodeResult,NodeRegistry}.java` **+ invariants I3 and I18** (moved here by D-016 — reachability and temporal-coordinate supply both need the node-to-rule mapping)  + `.../pipeline/NodeRegistryTest.java` | `DR 15` B2 | `-Dtest=NodeRegistryTest` — two nodes producing the same `NodeId` is an error; a node's `requires`/`produces` are non-empty and disjoint |
| **W-040** | `taxengine/pipeline/TopologicalSort.java` + `.../pipeline/TopologicalSortTest.java` | `DR 15` B3 | `-Dtest=TopologicalSortTest` — a cycle fails with the cycle named; order respects every edge; a missing `requires` producer fails |
| **W-041** | **RETIRED** — merged into `W-040` by D-011 | — | — |
| **W-042** | `taxengine/pipeline/PipelineRunner.java` + `.../pipeline/PipelineRunnerTest.java` | `DR 15` B3 — **no try/catch** | `-Dtest=PipelineRunnerTest` — uses `W-044`'s harness; `DR 08` T9-20, `DR 23` T23-6, T23-7 |
| **W-043** | `taxengine/result/{TaxComputation,FilingPosition,ComputationId,ComputationReceipt}.java` + `.../result/TaxComputationTest.java` | `DR 23` A6 — **the split** | `-Dtest=TaxComputationTest` — `TaxComputation` holds **no** date-dependent field, which is what makes it cacheable forever; `FilingPosition` holds them all |
| **W-044** | **MOVED — do this before `W-042`.** `taxengine/test/NodeHarness.java` (test scope) + `.../test/NodeHarnessTest.java` | `DR 23` A11 | `-Dtest=NodeHarnessTest` — the harness runs one node in isolation and its stub inputs are honoured |
| **W-045** | **RETIRED** — merged into `W-042` by D-011 | — | — |

---

## Phase 5 — Tax nodes: the walking skeleton (8 ids, 7 live)

### W-046 — `TaxByBasisNode` + `TaxByBasisNodeTest`
- [ ] **Produces** `taxengine/node/TaxByBasisNode.java`, `.../node/TaxByBasisNodeTest.java`
- **Spec** `DR 16-getting-started.md` S2 step 5 — **copy it**
- **Do** `DR 23` A11's harness example, using `W-044`'s `NodeHarness`: TI ₹12,00,000 → `TaxByBasis(60000, {})`.
- **Verify** `./mvnw test -pl backend -Dtest=TaxByBasisNodeTest`

### W-047 — RETIRED
- Merged into **W-046** by D-011. No work.

### W-048 — `ReliefShape` sealed family
- [ ] **Produces** `taxrules/relief/{ReliefShape,FlatWithCeiling,ExcessOverThreshold,ThresholdLadderCeiling,NoRelief}.java`
- **Spec** `DR 15` Part E

### W-049 — `RebateNode`
- [ ] **Spec** `DR 06-algorithms.md` G3.1 · `DR 13` R-5
- ⚠ **The rebate reads `TaxByBasis.atSlabRates` ONLY.** Using the total produces a nil tax bill on a ₹12,00,000 income that is mostly capital gains (`DR 08` T13-1). This is the single most dangerous line in the engine.
- ⚠ The relief branch exists **only** in the default regime.

### W-050 — `RoundTotalIncomeNode` and `RoundNetPayableNode`
- [ ] **Spec** `DR 01-rulebook.md` `RATE-ROUND-002`, `RATE-ROUND-003`
- ⚠ Exactly **two** rounding points in the whole pipeline.

### W-051 — `CessNode`
- [ ] **Spec** `DR 01-rulebook.md` `RATE-CESS-001` · `REBATE-ORDER-001` — cess is on **post-rebate** tax plus surcharge.

### W-052 — `NetPayableNode`

### W-053 — **T1 golden tests**
- [ ] **Produces** `backend/src/test/resources/taxplanning/golden/2026-27/S202_DEFAULT/T1-*.json` and `.../taxplanning/golden/GoldenCaseRunner.java`, `T1SlabAndRebateTest.java`
- **Spec** `DR 08-golden-tests.md` **T1-N1 … T1-A1f — every case, every expected value**
- ⚠ **Copy the expected values. Do not compute them.** If one fails, the code is wrong (rule R3).
- **Verify** `./mvnw test -pl backend -Dtest=T1SlabAndRebateTest`
- **This is the first real milestone: a correct calculator for slab tax and rebate.**

---

## A note on Phases 6–14

D-011 merged every item whose `Verify` named a **later** item's test. Phases 6–14 have a different
shape and were **not** restructured: their tables carry no `Verify` column at all, and their testing
is organised as **golden-suite milestones** (`W-053` T1, `W-057` T2, `W-070` T3 …) that deliberately
span several implementation items. That is a design choice, not the defect rule B1 describes.

But it leaves those implementation items with **no stated `Verify`**, so `00-START-HERE.md` §6's
first checkbox has nothing to point at. Until each is expanded at its EXPLAIN stage, the standing
rule for a Phase 6–14 item with no `Verify` line is: **`./mvnw test -pl backend -Punit`, and name in
the report which golden milestone will actually prove the item.** Raise it as a `BLOCKED` ambiguity
if that is not enough for the item in hand.

---

## Phase 6 — Surcharge and marginal relief (5 items)

| Item | Produces | Spec | ⚠ |
|---|---|---|---|
| **W-054** | `law/rates.json` surcharge ladders for all four (year × regime) | `DR 01` R5 | entry is **₹50,00,000**; the default regime has **no 37% band** |
| **W-055** | `taxrules/relief/ThresholdLadderCeiling.java` | `DR 06` G3.2 | `W = U + V`, one implementation, four parameterisations |
| **W-056** | `taxengine/node/SurchargeNode.java` | `DR 06` G3.2 | **two buckets** — `RATE-SUR-SPLIT-001` caps the special slice at 15% |
| **W-057** | **T2 golden tests** | `DR 08` T2-1 … T2-6 | copy expected values |
| **W-058** | metamorphic property tests | `DR 19` E4.1 | `moreIncomeNeverMeansLessTax` would have caught H-1 unaided |

---

## Phase 7 — Salary head (12 items)

| Item | Produces | Spec | ⚠ |
|---|---|---|---|
| W-059 | `components.json` for both years | `DR 10` E4, `DR 15` F-part | every code has `cashNature` |
| W-060 | `ComponentPartitionNode` | `DR 06` G1.1 step 1 | unknown code = error, never a silent addend |
| W-061 | `GrossSalaryNode` | `DR 01` R7 | |
| W-062 | `SalaryBaseNode` | `DR 01` `SAL-DEF-HRA-001` etc. | **basic + DA where the terms provide**; one function, five rules |
| W-063 | `SalaryDeductionNode` | `DR 01` R8 | professional tax **only** in the opted-out regime |
| W-064 | `salary.json` law rows | `DR 01` R7, R8 | |
| W-065 | `SalaryIncomeNode` | | |
| W-066 | `ProrationBasis` family | `DR 15` C3 | named basis per rule; **no caller divides** |
| W-066b | `taxengine/temporal/TemporallyApportionedCap.java` + `RuleRow.prorationBasis` + `.../temporal/TemporallyApportionedCapTest.java` | `DR 18` V5 (`:205-245`) | ⚠ **nothing currently applies a mid-year change.** `W-030` made one declarable and validated; without this a rule that changed inside the year is evaluated as though it never did. Test each `MidYearCombination` branch, and that `UNDETERMINED` **throws** |
| W-067 | `ExemptionNode` — HRA | `DR 06` G1.3 | **per period**; metro % per period; `limitedBy` names the limb |
| W-068 | `ExemptionNode` — LTA | `DR 01` R10 | **calendar** blocks; four independent caps |
| W-069 | `ExemptionNode` — allowances | `DR 01` R11 | ⚠ `SAL-ALW-NEW-SUBSET-001` is **STATUS UNCLEAR** — seed from the **section**, flag visibly |
| W-070 | **T3 golden tests** | `DR 08` T3-1 … T3-5 | T3-2, T3-3 are **PROVISIONAL** — report separately |

---

## Phase 8 — Perquisites, employer contributions, Chapter VIII (10 items)

| Item | Produces | Spec | ⚠ |
|---|---|---|---|
| W-071 | `perquisites.json` both years | `DR 01` R12 | TY 2025-26 values are **NEEDS VERIFICATION** — mark them |
| W-072 | `PerquisiteNode` | `DR 01` R12 | every output is `NON_CASH` |
| W-073 | `EmployerContributionNode` | `DR 01` R13 | **four** independent charges, not one |
| W-074 | **T4 golden tests** | `DR 08` T4-1 … T4-4 | |
| W-075 | `deductions.json` both years | `DR 01` R15 | |
| W-076 | `ChapterEightNode` | `DR 06` G2.2, G2.3 | employee PF consumes 80C; 80D is **seven** rules |
| W-077 | **T5 golden tests** | `DR 08` T5-1 … T5-4 | |
| W-078 | `houseproperty.json`, `HousePropertyNode` | `DR 01` R16, `DR 06` G1.4 | occupancy drives **four** rules |
| W-079 | `othersources.json`, `OtherSourcesNode` | `DR 01` R17 | dividend deduction is **nil** from TY 2026-27 |
| W-080 | **T6 golden tests** | `DR 08` T6-1 … T6-7 | |

---

## Phase 9 — Set-off, elections, capital gains (9 items)

| Item | Produces | Spec | ⚠ |
|---|---|---|---|
| W-081 | `setoff.json`, `SetOffNode` | `DR 01` R18 | absorption order is **declared and traced** (`DR 13` R-7) |
| W-082 | `Election` family, `ElectionResolver` | `DR 15` C2 | priced by **recomputation**; record all alternatives |
| W-083 | `HouseElectionNode` | `DR 13` R-6 | which two houses is a **choice**, and optimisable |
| W-084 | `IncrementalRunner` | `DR 15` B5 | memo only where the graph proves nothing upstream moved |
| W-085 | `CapitalGainsSummary`, `CapitalGainsBucket`, `RateBasisHint` | `DR 13` R-10, `DR 15` F5 | gross buckets; **no tax amounts** |
| W-086 | `portfolio` published read-model | `DR 07` X1.3 | modulith test must pass with the new dependency |
| W-087 | `MoneyBridge` | `DR 19` E1 | `toTMoney` **throws** — one-way seam |
| W-088 | `CapitalGainsNode` | `DR 13` R-10 | `UNCLASSIFIED` → 409; `SPECULATIVE` excluded |
| W-089 | **T13-1 test** — rebate with special-rate income | `DR 13` R-5 | **the most dangerous interaction in the design** |

---

## Phase 10 — ITR forms and export (6 items)

| Item | Produces | Spec | ⚠ |
|---|---|---|---|
| W-090 | `ItrFormSpec`, `forms/itr-forms.json` | `DR 21` I3 | **nine** forms; eligibility in `Condition` |
| W-091 | `ItrFormSelector` | `DR 21` I4 | deterministic **cascade**; return **all** verdicts |
| W-092 | `ItrSchedule`, `FieldSource`, `forms/itr-schedules.json` | `DR 21` I5 | schedules **shared by reference** |
| W-093 | `ScheduleMapper` | `DR 21` I5 | a **fold**, not a per-form mapper |
| W-094 | **T21 tests** | `DR 21` I8 | T21-1 … T21-16 |
| W-095 | ArchUnit: `@Valid` on every `@RequestBody`; no `@Document` returned | TS T10 | |

---

## Phase 11 — Persistence and API (16 items)

| Item | Produces | Spec |
|---|---|---|
| W-096 | `TaxpayerProfile`, `Pan` | `DR 15` F1 — **PAN was missing entirely** |
| W-097 | `FamilyMember` | `DR 15` F2 |
| W-098 | `Employer`, `Landlord`, `Lender`, `BankAccount` | `DR 15` F3, F4 |
| W-099 | `TaxYearWorkspace`, `FilingDraft`, `DraftInputs` | `DR 11` F2 |
| W-100 | `DraftRevision` + JSON-Patch apply/replay | `DR 11` F2 |
| W-101 | repositories + indexes | TS T7 |
| W-102 | `cleanDatabase()` additions | TS T7 — **every** collection |
| W-103 | `EngineInputsAssembler` | `DR 23` A3 — the **only** place `EngineInputs` is built |
| W-104 | `TaxComputationService` | `DR 23` A6 — core/overlay split |
| W-105 | `TaxError`, `TaxEngineException` sealed hierarchy | TS T9, `DR 23` A7 |
| W-106 | `ControllerAdviser` extension — exhaustive switch | `DR 23` A7 |
| W-107 | `ComputationReceipt` | `DR 19` E6 |
| W-108 | draft endpoints | TS T8 |
| W-109 | compute + compare-regimes endpoints | TS T8 |
| W-110 | optimistic locking, `If-Match`, 409 merge body | `DR 11` F5 |
| W-111 | **T11 tests** | `DR 11` F10 |

---

## Phase 12 — Product surface (12 items)

| Item | Produces | Spec | Feature |
|---|---|---|---|
| W-112 | `FilingPosition` + `InterestAndFeeNode` | `DR 15` C1 | — |
| W-113 | `ForecastProjection` + endpoint | `DR 20` F29 | **F29** |
| W-114 | `CalendarProjection` + endpoint | `DR 20` F30 | **F30** |
| W-115 | `CompletenessProjection` + endpoint | `DR 20` F33 | **F33** |
| W-116 | `content/explanations.json` + renderer | `DR 20` F37 | **F37** |
| W-117 | `content/claim-catalogue.json` (12 entries) | `DR 22` C5 | **F40** |
| W-118 | claim-playbook endpoint | `DR 22` C4 | **F40** |
| W-119 | claim integrity: S / E / D + statutory overshoot | `DR 12` C2, C3.1 | **F23, F24** |
| W-120 | documentation checklist | `DR 12` C3.2, `DR 22` C7 | **F25** |
| W-121 | materiality banding | `DR 22` C6 | **F40.4** |
| W-122 | progressive onboarding steps | `DR 20` F34 | **F34** |
| W-123 | PDF report | `DR 04` F10 | **F10** |

---

## Phase 13 — Documents (10 items)

| Item | Produces | Spec |
|---|---|---|
| W-124 | `IngestedDocument`, `ParseAttempt`, `ExtractedField`, `DocumentConfirmation` | `DR 05` M4 |
| W-125 | `DocumentParser` SPI | `DR 07` X2.5 |
| W-126 | `Form130Parser` L1 structural | `DR 06` G5.1 |
| W-127 | L2 anchored numeric + L3 checksum-driven | `DR 06` G5.1 |
| W-128 | `G5.2` validation identities | `DR 06` G5.2 |
| W-129 | multi-employer assembly | `DR 06` G5.3 |
| W-130 | `ReconciliationReport` | `DR 05` M4.2 |
| W-131 | upload / parse / confirm endpoints + async job | TS T8 |
| W-132 | **T8 tests** | `DR 08` T8-1 … T8-6 |
| W-133 | Excel import (**F28**) | `DR 19` E9.1 |

---

## Phase 14 — Hardening (13 items)

| Item | Produces | Spec |
|---|---|---|
| W-134 | PII: field encryption, log masking | `DR 14` O1 |
| W-135 | third-party PII exclusion from exports | `DR 14` O1.1, O5 |
| W-136 | retention derived from pack carry-forward periods | `DR 14` O5 |
| W-137 | `TaxJob` + `JobLease` + 6 jobs | `DR 19` E8 |
| W-138 | staleness sweep | `DR 13` R-8 |
| W-139 | pack linter + cross-year diff | `DR 15` C4 |
| W-140 | boundary-case generator | `DR 15` C5 |
| W-141 | independent Python oracle + `expected.csv` | `DR 19` E4.2 |
| W-142 | trace snapshot tests | `DR 19` E4.3 |
| W-143 | performance benchmarks N1–N3 | `01-PRD.md` §5 |
| W-144 | JaCoCo + PIT gates | `DR 07` X5.5 |
| W-145 | persona fixtures | `DR 19` E10 |
| W-146 | **synthetic pack test** — three regimes, synthetic year, sunset | `DR 10` E8, `DR 17` T17-11, `DR 18` T18-13 |

⚠ **W-146 is the acceptance test for the whole architecture.** It must pass with **zero** changes to
production source. If it does not, the Open/Closed claim has failed.

---


---

## Appendix A — Type audit of Phases 4–14 (D-017, 2026-09-19)

> **Erratum (W-035, 2026-09-19):** **this audit had a blind spot, and `W-035` fell into it.** It
> matched types the design *declares* — `record X(`, `class X`, `interface X` — and is therefore
> blind to types the design only ever *names*, inside a `permits` clause. That is the more dangerous
> category: a sealed type naming a class that exists nowhere **does not compile**, which is exactly
> what killed the original `W-013`.
>
> Re-run with the wider net: **29 types appear in a `permits` clause; 19 are never declared; 14 of
> those are in no `Produces` either.** Appendix B tables them.
> Verified by: `NodeValue permits MoneyValue, TaxByBasis, HeadResult, ComponentSet, ElectionOutcome,
> InterestBreakdown` — `15-review-findings.md:154`; five of the six are declared nowhere.

Phases 0–3 hit the same defect at **every** boundary: an item needed a type that no item produced
(`BaseId`, `PackValidationException`, five `Condition` types, six temporal types, `DisallowanceSet`,
`MidYearCombination`), or was assigned work whose subject did not exist yet (D-016's eight
invariants). Each was found mid-item, costing a stop.

So the remaining phases were audited in one pass instead: **every type the design declares, matched
against every item's `Produces`.** Method:

```
design declares 157 types · backlog produces 139 · already built 428
→ 86 declared, unproduced, unbuilt
   21 are nested variants of families already built (Between, MigrateTo, …) — not gaps
    3 are withdrawn (RegimeProvider, ElectionMechanics, IncomeHead as SPI)
   24 are named in backlog prose but in no `Produces` list
   38 appear nowhere in the backlog at all
```

**Rule A1 — when you reach an item below, its `Produces` is the row here, not just the one in the
phase table.** Nothing in this appendix is new work; it is work that was already implied and never
written down. Where a type's owning item is `—`, it has **no home yet**: stop and ask.

| Types | Owning item | Note |
|---|---|---|
| `TemporallyApportionedCap` | **`W-066b`** | V5's mid-year `apply`. `W-030` made the *data* checkable; nothing yet *applies* it |
| `Computation` | `W-043` | `05-domain-model.md`'s name for what A6 split into `TaxComputation` + `FilingPosition` — confirm it is the same thing before building both |
| `CompletedMonths`, `CalendarMonths`, `Days` | `W-066` | the three `ProrationBasis` variants; the phase table names only the family |
| `Election`, `ElectionResolver`, `ElectionState`, `ElectionEvent`, `RegimeElectionHistory` | `W-082` | L3's second half — per-taxpayer election state, append-only |
| `CapitalGainsSummary`, `CapitalGainsBucket` | `W-085` | named in prose only |
| `MoneyBridge` | `W-087` | named in prose only |
| `ItrFormSpec`, `ItrFormSelector` | `W-090`, `W-091` | **+ invariants I19, I20** (D-016) |
| `ItrSchedule`, `FieldSource`, `FromRule`, `FromNode`, `FromComponents`, `FromProfile`, `ManualEntry`, `SumOf`, `ScheduleField` | `W-092` | `FieldSource` is a sealed family of six; the table names the parent. **+ I21, I22** |
| `TaxpayerProfile`, `Pan` | `W-096` | named in prose only |
| `FamilyMember` | `W-097` | named in prose only |
| `Employer`, `Landlord`, `Lender`, `BankAccount` | `W-098` | named in prose only |
| `DraftUpcaster` | `W-100` | V6's second half; consumes `DeprecationPolicy`, built in `W-025` |
| `TaxError` | `W-105` | named in prose only |
| `Scenario`, `ScenarioDiff` | `W-109` | compare-regimes returns a diff, not two numbers |
| `InterestAndFeeNode` | `W-112` | named in prose only |
| `ClaimCatalogueEntry`, `ClaimStep`, `Counterparty` | `W-117`, `W-118` | **+ invariant I23** (D-016) |
| `Materiality` | `W-121` | materiality banding |
| `DocumentParser` | `W-125` | named in prose only |
| `SalaryStructureImporter` | `W-133` | the Excel import, F28 |
| `TaxJob` | `W-137` | named in prose only |
| `PackDiff`, `Finding`, `ValueChanged`, `RuleAdded`, `RuleRemoved`, `ProvisionRenumbered`, `SuspiciousCopy`, `CarriedForwardUnverified` | `W-139` | `Finding` is a sealed family of six; the table names neither |
| `BoundaryCaseGenerator` | `W-140` | named in prose only |
| `Fixtures`, `Pack2026` | `W-145` | the persona fixtures and the pack they read |
| `IncrementalRunner` | `W-084` | named in prose only |
| `T1SlabAndRebateBoundaryTest` | `W-053` | `W-053` names `T1SlabAndRebateTest`; `16-getting-started.md` also declares a boundary variant |
| `RegimeBehaviour`, `DataDrivenRegimeBehaviour` | **`W-031b`** | L5's escape hatch. `BehaviourId` (built, `W-026c`) carries invariant I9 — *a behaviour id with no registered implementation is a load error* — which **cannot be checked until a registry exists**. No item builds one |
| `ComponentCodes`, `RuleIds` | **`W-032b`** | `19-engineering-foundations.md` E-series: **build-time generated** constants giving compile-safety over pack data. No item generates them, and no item sets up the generator |
| `RulePackDocument` | **—** | `19-engineering-foundations.md`. Purpose unclear from one declaration — resolve before Phase 11 |

> **Erratum (D-018, 2026-09-19):** the three ownerless entries below were given items —
> **`W-031b`** (the `RegimeBehaviour` registry, making invariant I9 enforceable), **`W-032b`** (the
> build-time constant generator) and **`W-066b`** (applying a mid-year change). `W-066b` sits after
> `W-066` rather than at `W-042`, because `PRORATE_PER_SEGMENT` needs the `ProrationBasis` family
> that `W-066` builds. `RulePackDocument` remains unresolved and is still a stop-and-ask.
> **143 live items.**

### Three findings that are not just missing rows

1. **`TemporallyApportionedCap` has no owner and is load-bearing.** `W-030` made a mid-year change
   *declarable* and *validated*; nothing in the backlog *applies* one. Without it, a rule that
   changes inside the year passes validation and is then evaluated by `versionAt` as though it never
   changed — a silent wrong answer in exactly the case the whole temporal design exists for. Assigned
   to `W-042` above; it may deserve its own item.

2. **Invariant I9 is unenforceable as the backlog stands.** It requires every `BehaviourId` to
   resolve to a registered implementation, and no item builds a registry. Either L5's escape hatch
   gets an item, or I9 should be struck — but it must not stay as an invariant nobody can check.

3. **`ComponentCodes` / `RuleIds` are a missing build step, not a missing class.** `DR 19` resolves
   the data-versus-compile-safety tension by *generating* constants from the pack at build time. No
   item creates the generator, so the resolution is currently unimplemented and the tension is
   unresolved.



---

## Appendix B — Types named in a `permits` clause and declared nowhere (D-021, 2026-09-19)

Appendix A found types the design **declares** and no item produces. This is the complementary set:
types the design **names in a sealed `permits` clause** and never declares at all. A sealed type
naming a class that does not exist **fails to compile**, so every row here is a build break waiting
for whoever writes that family.

**Rule B2 — a `permits` clause lists only what exists.** Each family grows one variant at a time, as
`CapShape`, `Condition` and `NodeValue` already do. Copying a design `permits` clause verbatim is a
build break, not a head start.

| Types | Family | Owning item |
|---|---|---|
| `HeadResult`, `ComponentSet`, `ElectionOutcome`, `InterestBreakdown` | `NodeValue` (`DR 15` B4) | the node that produces each — `HeadResult` at `W-065`, `ComponentSet` at `W-060`, `ElectionOutcome` at `W-082`, `InterestBreakdown` at `W-112` |
| `RegimeElection`, `HouseElection`, `ChildElection`, `SetOffOrderElection`, `AggregateAllocationElection` | `Election` (`DR 15` C2) | **`W-082`** — the phase table names only the family |
| `OccupationPeriods` | `ProrationBasis`-adjacent (`DR 15` C3) | **`W-066`** |
| `MissingRulePackException`, `UnknownComponentCodeException`, `StatutoryOvershootException`, `NoApplicableItrFormException`, `UndeterminedApportionmentException`, `BlockingDataQualityException` | the `TaxEngineException` hierarchy (`DR 23` A7) | **`W-105`** — which names `TaxError` and `TaxEngineException` and none of the six |

Two of that last group are already reachable and have no thrower:
**`UndeterminedApportionmentException`** is what `W-066b`'s `TemporallyApportionedCap` must throw on
`UNDETERMINED` — `W-030` made that state declarable and validated, and nothing yet raises it — and
**`MissingRulePackException`** is golden case **T9-1**, which `W-034` currently satisfies with a
plain `PackValidationException`. Both belong with `W-105`'s hierarchy; note the ordering, since
`W-066b` is reached first.

## Milestones

| After | You have |
|---|---|
| **W-053** | a correct slab-and-rebate calculator, both years, both regimes, no database |
| **W-058** | surcharge and both marginal reliefs — H-1, H-3, H-5 retired |
| **W-080** | every income head computing; golden tests T1–T6 green |
| **W-089** | capital gains wired; the s.156(3) trap closed |
| **W-095** | ITR suggestion and export |
| **W-111** | drafts, API, persistence |
| **W-123** | the product surface — forecast, calendar, completeness, playbooks |
| **W-133** | document ingestion |
| **W-146** | hardened, and the extensibility claim proven in CI |
