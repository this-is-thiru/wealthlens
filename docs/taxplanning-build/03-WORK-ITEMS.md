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

**On package names (D-048, `W-045b2`).** `taxrules/…` in any row below means **`taxengine/rules/…`**, and
`taxdocs/…` means **`taxplanning/documents/…`**. The rows keep their original paths as history; the
code and `02-TECH-SPEC.md` T2 have the current ones. An item that needs a `taxengine` package from
`taxplanning` adds that package's `@NamedInterface` (TS T1).

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

> **Erratum (W-042, 2026-09-21):** `W-039`'s Verify says *"a node's `requires`/`produces` are
> **non-empty** and disjoint"*, and **the non-empty half makes every graph unbuildable**.
> `W-040`'s `TopologicalSort` rejects a required slot with no producer, so every acyclic graph must
> contain a node that requires nothing — its own tests rely on that
> (`orderOf(List.of(node(COMPONENTS)))`). `W-039` forbade exactly such a node. A graph with a source
> is refused by the registry, one without a source by the sort; no graph satisfies both. **`W-042`
> is the first item to pass a graph through both**, which is why it went unnoticed — the same
> vacuous-until-first-used shape as **B-003**.
>
> **Correction (blocker B-004, decision D-031): `requires()` must be non-null and may be empty.**
> A **source** node reads `EngineInputs` through `ctx.inputs()` rather than an upstream slot;
> `EngineInputs` is declared, passed in and covered by the fingerprint, so a source declares its
> input by being given it. The premise behind the original rule — *"everything the engine reads
> arrives through a slot"* — was wrong: there are two sources of data and only one of them is an
> edge. Nothing is lost: reading an **undeclared** slot is still refused by `NodeContext.get`, at
> the moment it happens, which is the stronger check.
> Verified by: `./mvnw test -pl backend -Dtest=NodeRegistryTest,TopologicalSortTest` — 40 tests;
> `NodeRegistryTest.aSourceNodeMayRequireNothing` replaces `aNodeRequiringNothingIsRejected`.
| **W-041** | **RETIRED** — merged into `W-040` by D-011 | — | — |
| **W-042** | `taxengine/pipeline/PipelineRunner.java` + `.../pipeline/PipelineRunnerTest.java` | `DR 15` B3 — **no try/catch** | `-Dtest=PipelineRunnerTest` — uses `W-044`'s harness; `DR 08` T9-20, `DR 23` T23-6, T23-7 |
| **W-043** | `taxengine/result/{TaxComputation,FilingPosition,ComputationId,ComputationReceipt}.java` + `.../result/TaxComputationTest.java` | `DR 23` A6 — **the split** | `-Dtest=TaxComputationTest` — `TaxComputation` holds **no** date-dependent field, which is what makes it cacheable forever; `FilingPosition` holds them all |
| **W-044** | **MOVED — do this before `W-042`.** `taxengine/test/NodeHarness.java` (test scope) + `.../test/NodeHarnessTest.java` | `DR 23` A11 | `-Dtest=NodeHarnessTest` — the harness runs one node in isolation and its stub inputs are honoured |
| **W-045** | **RETIRED** — merged into `W-042` by D-011 | — | — |

> **Erratum (W-037, 2026-09-21):** `W-037` is **split three ways** by decision **D-025**, and its
> `Produces` is amended by **D-024**. Two things were wrong with the row above.
>
> *It could not be built as written.* `DR 23` A3's `EngineInputs` holds `DraftInputs`
> (`taxplanning`, `W-099`) and `CapitalGainsSummary` (`portfolio`, `W-085`) — neither visible to
> `taxengine`, and the first forbidden by `ArchitectureTest.taxengine_fetches_nothing`, which A3
> itself recommends three code blocks later. **B-003**, resolved by **D-024**: `taxengine` declares
> its own input projections (`EnteredFacts`, `CapitalGainsInput`) and `taxplanning` maps into them.
>
> *It was mis-sized.* Eight records, one enum and a test exercising defensive copying across all of
> them is 900–1,100 lines against §7's ~300 — before L5 guard 3, which `W-031b` moved here. And
> `EngineInputs` cannot compile until its ten component types exist, so the spine must come **last**.
>
> | Item | Produces | Verify |
> |---|---|---|
> | **W-037** | `taxengine/input/{TaxpayerFacts,FamilySnapshot,LedgerSnapshot}.java` + `.../input/TaxpayerFactsTest.java` | `-Dtest=TaxpayerFactsTest` — every collection defensively copied; the projection carries **date of birth, not a derived age** (D-025) |
> | **W-037b** | `taxengine/input/{FilingFacts,RegimeElectionSnapshot,EnteredFacts,CapitalGainsInput}.java` + `taxrules/regime/ElectionState.java` + `.../input/RegimeElectionSnapshotTest.java` | `-Dtest=RegimeElectionSnapshotTest` — `stateOf` defaults to `NEVER_ELECTED`; every collection defensively copied |
> | **W-037c** | `taxengine/input/EngineInputs.java` + `.../input/EngineInputsTest.java` **+ L5 guard 3** | `-Dtest=EngineInputsTest` — the record is complete enough that the engine fetches nothing; plus the `adjustPipeline` override rule |
>
> Ids are suffixed, not renumbered, as D-013, D-015 and D-019 did. `ElectionState` moves from
> `W-082`'s `Produces` to `W-037b`'s — `RegimeElectionSnapshot` cannot compile without it and the
> engine must be able to see it, so it lands in `taxrules.regime` beside `ElectionProtocol`,
> `ElectionKind`, `ElectionVehicle` and `ElectionRoute`. **`W-082`'s row is reduced accordingly.**
> Two further amendments, both recommendations accepted: `TaxpayerFacts` carries **no PAN** (A3 says
> "PAN, DOB, residence", but `Pan` is `W-096`'s, no rule computes on a PAN, and O1.1 wants it masked),
> and `EngineInputs` ships **without `fingerprint()`**, which `W-038` adds with the type it owns.
> Verified by: `find backend/src/main/java -name 'DraftInputs.java' -o -name 'CapitalGainsSummary.java'`
> → both absent; `ArchitectureTest.java:165-170`; `03-WORK-ITEMS.md:516` (`DraftInputs` → `W-099`),
> `:488` (`CapitalGainsSummary` → `W-085`), `:633` (`ElectionState` → `W-082`).

---

## Phase 4b — Foundation corrections from the brief (18 items, all before `W-046`)

Added 2026-09-23 by decision **D-043**, which records the owner's instruction to *"collect every
prerequisite and keep them properly"*. The source is `docs/taxplanning-design/00-BRIEF.md`
(D-033 … D-042). Ids are suffixed onto the retired `W-045`, not renumbered, as D-013, D-015 and
D-019 did.

**Why these come before `W-046`.** They change two things every node depends on: the **rule-row
format** (identity, statuses, where rows live) and the **input model** (`TaxpayerFacts` →
`Assessee`). Today there are zero real nodes. After Phase 5 there are seven, each written against
whatever shape exists then.

**Order is dependency order.** `W-045b2` right after the first docs item, so every later item is written against the final package names. Docs first (they are cheap and inform everything after them), then
the rule format from the inside out (identity → status → storage → compile → diff), then what sits
on it (node ids, coverage, catalogue), then the input model, then the two items that face outward.

| Item | Produces | Spec | Verify |
|---|---|---|---|
| **W-045a** | *Repo-wide, added by **D-045**.* The engineering standards made enforceable: Checkstyle 10.x with the strict rule set, **failing the build**, tests included, test names in the `methodName_whenCondition_expectedResult` form; a **baseline** suppressions file holding today's legacy violations that may **only shrink** (a test enforces it); two ArchUnit naming rules (no generic class suffixes, no duplicate simple class names) with the existing cases listed as exceptions; `spotless:check` bound to `validate`; a Claude Code hook that blocks `git commit` unless the unit tier and Checkstyle pass; the **7** violations in `taxrules`/`taxengine` fixed | `CLAUDE.md` "Engineering standards"; measurements in `plans/W-045a.md` | `./mvnw test -pl backend -Punit` with Checkstyle failing on violation — green; each new rule **proven to bite** by a deliberate violation, then reverted; the hook refuses a commit when a test is broken |
| **W-045b** | *Docs only.* `STATE.md` bookkeeping (status board Phases 2–3 and 4b, the counts line, the misplaced `*(none)*` under Blockers, the duplicated "fourth audit gap" paragraph); an **ID glossary** amendment in `00-START-HERE.md` (every ID scheme, its document prefix, and the collisions it resolves); **Appendix C** here — every type named in more than one `Produces` | `00-BRIEF.md` §10; STATE "duplicate-`Produces` sweep" | the counts line equals the number of completed rows; Appendix C lists each duplicated type with the item that keeps it; `git diff` touches docs only |
| **W-045b2** | *Added by **D-048**.* **Two modules instead of four.** Move `taxrules.*` → `taxengine.rules.*` (111 main + 24 test files, package moves only — no behaviour change); delete the empty `taxdocs` module (its future code goes to `taxplanning.documents`); delete the `TaxRules` and `TaxDocs` markers; `taxengine` `allowedDependencies = {"shared"}`, `taxplanning` drops `taxrules` and `taxdocs`; the generator's output package; every `..taxrules..` pattern in `ArchitectureTest`; **new ArchUnit rule:** `..taxengine.rules..` depends on no other `taxengine` package (keeps the vocabulary-before-runner direction the module boundary used to give). **At EXPLAIN, decide how the closed `taxengine` exposes its public sub-packages** (`input`, `result`, `trace`, `rules.*`) to `taxplanning` — a closed module shows only its root (B-002), and `W-103` would otherwise fail exactly as `W-035` did; options and their exact Spring Modulith syntax are to be taken from the Spring Modulith documentation, not assumed | **D-048**; `00-BRIEF.md`; TS T1, T2 | `./mvnw test -pl backend -Punit` — 1414+ green, 0 Checkstyle violations; `WealthLensModulithTest` shows **two** tax modules; the new direction rule **proven to bite** (a `rules` class importing `pipeline` fails); a probe class in `taxplanning` importing `EngineInputs` **compiles and passes the modulith test** |
| **W-045b3** | *Added by **D-049**.* Delete `ArchitectureTest.taxengine_does_not_swallow_failures`: it forbids *depending on* `Exception`/`RuntimeException`, so it also forbids **defining** an exception (it would reject `W-105`'s `TaxEngineException`); its intent — no catch-all — is enforced repo-wide by Checkstyle `IllegalCatch` (`W-045a`) | OQ-5; D-049 | `IllegalCatch` proven on `taxengine` main code (a `catch (RuntimeException)` probe in `taxengine.pipeline` fails Checkstyle); `./mvnw test -pl backend -Punit` green |
| **W-045c** | *Docs only.* **Appendix D** — for `W-046`…`W-058`: every type each item names, whether it exists (`find`), its module, and whether the consumer may see that module; every enum constant it names and whether it exists. **Appendix E** — `09-build-plan.md`'s slices mapped to phases, and the placeholder deletion schedule (B2) written as items placed after the milestone that replaces each part | STATE "audit categories 3–5"; `09` B1, B2 | each row carries the command that established it; no Phase 5–6 item names a type that is absent, unowned, or invisible to its consumer without an owner recorded |
| **W-045c1** | *Added by D-050.* Delete the legacy dead code (Appendix E, group L1): `engine/FbpOptimizer`, `engine/ItrFormAdvisor`, `recommendation/HrLetterContentBuilder`, `parsing/ParsedCtcDto`, `engine/MarginalReliefCalculator` + its test, the empty `test/…/taxplanning/_review` directory | `09` B2; Appendix E | nothing imports them (grep, `W-045c`); `./mvnw test -pl backend -Punit` green |
| **W-045d** | Rule IDs **and regime codes** renamed to concepts (**D-035**, **D-046 B4**). **At EXPLAIN:** a table proposing each new name *quoted from the Acts' own wording* with its source — the owner chooses (**OQ-4**); no name is invented. Then: the **2** rule rows and the 2 regime codes in the shipped pack *("15 rows" corrected by `W-045c`)*; `taxrules/generated/RuleIds.java` regenerated; every test referencing an old ID; an erratum on `01-rulebook.md` carrying the **old → new mapping for every year- or regime-suffixed ID in the rulebook**, e.g. `RATE-SLAB-NEW-2025` + `RATE-SLAB-NEW-2026` → **one** concept-named rule with one version spanning both years (the rulebook states the bands are identical) + a new `RuleIdNamingTest` | `00-BRIEF.md` §5.2, §10 | `-Dtest=RuleIdNamingTest,GeneratedConstantsTest,RulePackLoadTest` — no `RuleId` or regime code in any pack matches `-(19\|20)\d\d\b`, `-(OLD\|NEW)\b` or a section number (`S\d{3}`); every old ID in the mapping resolves to exactly one new ID |
| **W-045d2** | *Added by D-051. Docs only.* The old → new mapping for the **51** rulebook IDs `W-045d` did not map (every year-, `OLD`/`NEW`- or section-suffixed ID outside R3: `RATE-SUR-*`, `REBATE-NEW/OLD-*`, `REG-NEW-DISALLOW-*`, `HP-NEW-*`, `SETOFF-NEW-*`, `SAL-ALW-*`, `SAL-HRA-METRO-*`, `SAL-LTA-NEW-001`, `PERQ-*-2026`, `OS-DIV-DED-*`, `DED-NEWREGIME-001`), each proposed name **quoted from its own provision** with its source, for the owner to choose; plus the section-numbered values that are not rule IDs or regime codes: `ElectionRoute` `S202_4_B` (7 files: the shipped pack, `ElectionRoute` javadoc, `RegimeLifecycleTest`, 4 broken packs), the `BehaviourId` example `S202_SPECIAL_SETOFF`, and the test-only codes `S202_OPT_OUT`, `S202_ALSO_DEFAULT` | `00-BRIEF.md` §5.2, §10; D-051 | a scripted check: every such ID in `01-rulebook.md` appears exactly once in the mapping and resolves to exactly one new ID; `git grep -E 'S[0-9]{3}_' -- backend` is empty after the code half; `./mvnw test -pl backend -Punit` green |
| **W-045d3** | *Added by D-069 (split from `W-045d2`).* The code half of the rule-ID and regime-value naming: `ElectionRoute` `S202_4_B` in the shipped `FIRST_SCHEDULE_RATES.json` (with its lock) and the five `rulepacks-broken/` copies; the `ElectionRoute`/`BehaviourId` javadoc examples; the test codes `S202_OPT_OUT`, `S202_ALSO_DEFAULT`, `S204_…`; a decision on the synthetic `S999_*` fixtures; and `RuleIdNamingTest`'s OLD/NEW pattern extended to catch a fused token such as `NEWREGIME` | `00-BRIEF.md` §5.2, §10; D-069 | `git grep -E 'S[0-9]{3}_' -- backend` finds only `RuleIdNamingTest`'s deliberate negative example; the lock holds; `./mvnw test -pl backend -Punit` green |
| **W-045e** | The three status axes (**D-036**): `taxrules/registry/{LegalStatus,VerificationLevel,PublicationStatus}.java`; `Provenance`/`RuleVersion` carry all three; `VerificationStatus` and `DraftOrFinal` withdrawn and added to the withdrawal gate; pack JSON and readers migrated + `RuleStatusTest` | `00-BRIEF.md` §5.3 | `-Dtest=RuleStatusTest,RulePackLoadTest,ArchitectureTest` — a non-`PUBLISHED` version never enters a pack; `UNVERIFIED` still requires `whatToCheck` (I1); `PENDING_NOTIFICATION` requires its interim rule. ⚠ **Size risk:** `Provenance` is referenced widely — estimate at EXPLAIN, propose a split if over ~300 lines |
| **W-045f** | The timeline (**D-034**), **laid out by layer** (`00-BRIEF.md` §5.1): `rulepacks/timeline/law/{rules,regimes}/`, `timeline/exec/{pipelines,components}/`, `timeline/forms/itr/`, `timeline/content/` — one file per identity, migrated from `rulepacks/2026-27/`, each file's layer given by its directory; `taxrules/timeline/{Timeline,TimelineReader}.java` + `TimelineReaderTest` | `00-BRIEF.md` §5.1 | `-Dtest=TimelineReaderTest` — every file parses with a path in its error; each identity appears in exactly one file; one rule's versions never overlap in validity; a file outside the four layer directories is refused |
| **W-045g** | The rule-pack compiler (**D-034**): `taxrules/timeline/RulePackCompiler.java`; `rulepacks/2026-27.lock.json` (version ids + per-layer checksums); `ClasspathRulePackProvider` compiles from the timeline and verifies the lock; `rulepacks/2026-27/` removed + `RulePackCompilerTest` | `00-BRIEF.md` §5.1; `W-027`, `W-028` | `-Dtest=RulePackCompilerTest,RulePackLoadTest,NodeHarnessTest` — the compiled 2026-27 rule pack equals today's in canonical form, layer by layer; only `PUBLISHED` versions enter it; a timeline edit without a lock update **fails startup naming the rule**; the synthetic TY 2099-00 still loads with no Java change (T9-21) |
| **W-045g2** | *Added by D-054 (split from `W-045g`).* `ClasspathRulePackProvider` serves each year compiled from `rulepacks/timeline/` and checked against `<year>.lock.json`; the test roots `rulepacks-fixture/2098-99`, `2099-00` and `rulepacks-broken/2090`–`2093` migrated to timeline + lock form; `rulepacks/2026-27/` removed; `RuleIdNamingTest`, `ConstantGenerator` and `TimelineReaderTest`'s drift guard read the timeline (and `RulePackCompilerTest`'s equality tests, which compare against `2026-27/`, are retired with it) | `00-BRIEF.md` §5.1; `W-027`, `W-028`, `W-045g` | `-Dtest=RulePackLoadTest,ClasspathRulePackProviderTest,NodeHarnessTest,PipelineRunnerTest,RuleIdNamingTest,GeneratedConstantsTest` — a timeline edit without a lock update fails the load naming the rule; each broken root still fails for its one defect; the synthetic TY 2099-00 loads with no Java change (T9-21) |
| **W-045g3** | *Added by D-055 (split from `W-045g2`).* Remove `rulepacks/2026-27/` and move its last readers onto the timeline and lock: `RuleIdNamingTest` and `ConstantGenerator` (`RuleIds`) read the timeline; `TimelineReaderTest`'s drift guard and `RulePackCompilerTest`'s two equality tests retired (the lock guards drift now); `PackJsonTest` (3 readers of the real files, incl. the mutation tests whose regexes match today's formatting) and `PackJsonWellFormedTest` (the real pack checked as data) re-pointed at the timeline files and the lock | `00-BRIEF.md` §5.1; D-055 | `-Dtest=RuleIdNamingTest,GeneratedConstantsTest,TimelineReaderTest,RulePackCompilerTest,PackJsonTest,PackJsonWellFormedTest` green with `rulepacks/2026-27/` absent; `git grep 'rulepacks/2026-27/'` finds nothing outside docs; the unit tier green |
| **W-045h** | The publish diff (**D-037**, the gate's first half): `taxrules/validate/PackDiff.java` (the name TS T2 already gives it) + `PackDiffTest` | `00-BRIEF.md` §5.4; TS T2 | `-Dtest=PackDiffTest` — lists every changed version and every changed value between two rule packs; identical packs give an empty diff. Golden re-run = the build; approval = PR review by the person named in **OQ-1** |
| **W-045i** | `NodeIds` generated from the timeline's pipelines (STATE **Q1**): `taxrules/generated/NodeIds.java`; `ConstantGenerator` extended; `GeneratedConstantsTest` extended; the hard-coded real-slot ids in `NodeHarnessTest` and `PipelineRunnerTest` switched to constants | `DR 15` B2; `W-032b` | `-Dtest=GeneratedConstantsTest,NodeHarnessTest,PipelineRunnerTest` — every pipeline node ref has a constant; a stale `NodeIds.java` fails; the `components` / `inputs.components` drift is gone |
| **W-045j** | The coverage register (**D-038**): `rulepacks/timeline/content/coverage.json` (content layer — it can change no number) seeded with the **105 sections** `01-rulebook.md` cites, each classified; `taxrules/coverage/{CoverageRegister,SectionRef,Coverage}.java` + `CoverageRegisterTest` | `00-BRIEF.md` §7.1 | `-Dtest=CoverageRegisterTest` — a rule citing an unregistered section fails; a `MODELLED` section with no published rule fails; every entry has exactly one classification |
| **W-045k** | The section catalogue (**D-042**): `taxrules/catalogue/{SectionCatalogueEntry,Route,Disqualifier,DisqualifierKind}.java`; `rulepacks/timeline/content/sections/` with **three worked entries** — the investments deduction (s.80C / s.123), HRA, and the default regime (s.202) + `SectionCatalogueTest` | `00-BRIEF.md` §6.3; `DR 22` C4, C5 | `-Dtest=SectionCatalogueTest` — SC-2, SC-3 and SC-4 hold on every entry; SC-1 runs as a **ratchet** (the list of catalogued sections may only grow) until the register is complete. ⚠ **Size risk:** estimate at EXPLAIN |
| **W-045k2** | *Added by D-060 (split from `W-045k`).* Two more section-catalogue entries in `rulepacks/timeline/content/sections/`: the investments deduction (1961 s.80C / 2025 s.123 + Schedule XV) and HRA (1961 s.10(13A) / 2025 **Sch. III Table Sl. 11**, which is not a section); their `PLANNED` rows in `coverage.json` (2025 s.123 and 1961 s.80C → `W-076`; 1961 s.10 → `W-067`); `SectionCatalogueTest`'s ratchet extended. Routes only as the rulebook quotes them (`DED-80C-ITEMS-001` is "among others"): the entry states the list is incomplete and what to check (Schedule XV para 1, every clause) | `00-BRIEF.md` §6.3; `DR 22` C5; D-060 | `-Dtest=SectionCatalogueTest,CoverageRegisterTest` — SC-1…SC-4 hold on every entry; every route and disqualifier whose rule is not yet in the timeline is `NotModelled` naming why |
| **W-045l** | The assessee family (**D-039**): `taxengine/input/{Assessee,Individual,Huf}.java` replacing `TaxpayerFacts`; `PersonType` gains `HUF` (**the existing word is kept** — `23` A5); `EngineInputs`, `NodeHarness.plainTaxpayer()`, and the private fixtures in `NodeRegistryTest` and `InputsFingerprintTest` folded into the harness + `AssesseeTest` | `00-BRIEF.md` §8 | `-Dtest=AssesseeTest,EngineInputsTest,InputsFingerprintTest,NodeHarnessTest,WealthLensModulithTest` — one type per assessee; `Huf` has no date of birth; the fingerprint separates an individual from an HUF with otherwise equal facts. `Huf` carries **only residence** until the HUF build adds karta and members (rule B2) |
| **W-045m** | Warning on the old compute endpoint (**OQ-3**): the response of `POST /tax-planning/user/{email}/profile/{profileId}/compute` carries a visible known-errors notice + a test in `TaxPlanningControllerIntegrationTest` | `00-BRIEF.md` §12 OQ-3; `09` B2 | the integration test asserts the notice. **Do only if OQ-3 is answered "warn" or "disable"; if answered "leave", this id is RETIRED by that decision** |
| **W-045n** | *Research, no code.* TY 2025-26 sources for the **Phase 5 rules only** — slabs (both regimes, all age bands), rebate (`REBATE-OLD-001` is `NEEDS VERIFICATION`: open the 1961 Act s.87A base text), cess, rounding. Errata upgrading each row that is confirmed | **D-041**; `09` Slice 0 | every Phase 5 rule has a 2025-26 row at `SOURCE_CHECKED` or an explicit `UNVERIFIED` with `whatToCheck`; STATE's "Rules used that are not fully verified" lists the remainder |
| **W-045o** | *Research, no code.* `coverage.json` completed to **every section of both Acts**, from their tables of contents; each classified | **D-038** | the register's section count equals each Act's; no entry is unclassified; the build reports the count by classification |
| **W-045o2** | *Added by D-066 (split from `W-045o`).* The coverage register completed to every section of the **Income-tax Act, 1961 as in force for AY 2026-27**: the section list reconstructed from the thc.nic.in copy (current to about 2019) plus every section inserted or omitted by the amending Acts since (Finance Acts 2019 (No. 2)–2025, Taxation Laws (Amendment) Acts, TOLA 2020), each with its source; omitted sections not registered; classified by an owner-approved chapter table | **D-038**, D-066 | the 1961 Act's registered count equals the reconstructed list's; the list is continuous; no entry is unclassified; the build reports the count by classification |
| **W-045o3** | *Added by D-067 (found in `W-045o2`).* Apply the Finance Act, 2026's amendments to the **Income-tax Act, 2025** section list (`content/acts/INCOME_TAX_ACT_2025.json`), which `W-045o` built as enacted: s.354A inserted (FA 2026 s.81), ss.443 and 447 omitted (ss.100, 102), each with its effective date; any other insertion or omission FA 2026 makes, found by reading its 2025-Act part in full (pp.42–64); register rows added/marked to match | D-066, D-067 | `-Dtest=CoverageRegisterTest` — the register equals the amended list minus its omitted sections; every change cites its FA 2026 section and page |

> **Changed 2026-09-23 (D-047), before any Phase 4b item started:** `W-045d` also renames regime codes, with concept names sourced from the Acts and chosen by the owner (OQ-4); `W-045f` lays the timeline out by layer; `W-045g`/`W-045h`/`W-052c` say **rule pack**, `RulePackCompiler`, `PackDiff` and `<taxYear>.lock.json` instead of "snapshot"; `coverage.json` moves to the content layer; `W-139` reuses `W-045h`'s `PackDiff` instead of owning a second one. Source: `reviews/2026-09-23-prd-techspec-review.md` C1–C5. Edited in place because no item had started; the prior wording is in git (`72c089b`).

> **Erratum (W-045d, 2026-09-24):** two corrections to the `W-045d` row, decided in **D-051**.
> (1) **The 2025 Act itself uses "new":** Part C is headed "C.—New tax regime" and s.202 "New tax
> regime for individuals, Hindu undivided family and others." (S2 pp.224, 226), so the Verify regex
> `-(OLD|NEW)\b` would reject the statute's own wording. `RuleIdNamingTest` rejects `OLD`/`NEW` as a
> word **except** in `NEW-TAX-REGIME`, treats `_` as `-` so regime codes are checked too, and also
> catches a leading `NEW`/`OLD`. (2) **The mapping covers R3 only** (7 IDs); the other 51 are split
> out to `W-045d2` (§7 sizing: 58 names, each needing its own source and the owner's choice).
> Verified by: `RuleIdNamingTest`; `grep -oE` count over `01-rulebook.md` (58 distinct IDs).

> **Erratum (W-045e, 2026-09-24):** `W-045n` gains one question, decided in **D-052**: from the
> sources, what `LegalStatus.WITHDRAWN` and `STRUCK_DOWN` mean for **dates**, i.e. whether a version
> so marked stops applying from a date or never applied. Until that is sourced, legal status does not
> change which version `RuleRow.versionAt` selects; only `Validity` does.
> Verified by: `RuleRow.versionAt` filters on `validity().contains` alone.

> **Erratum (W-045f, 2026-09-24):** two things `W-045g` inherits (D-053). (1) `TimelineReader` reads
> a filesystem `Path`; loading the timeline from the JAR at runtime is `W-045g`'s. (2)
> `RuleIdNamingTest` and `ConstantGenerator` (which generates `RuleIds`) list `rulepacks/<year>/`
> directories; once `W-045g` removes `2026-27/` they read nothing and must read the timeline instead.
> Verified by: `RuleIdNamingTest.packDirectories`, `ConstantGenerator.packDirectories`.

> **Erratum (W-045g, 2026-09-24):** `W-045g` is split (D-054). It delivers the compiler, the canonical
> form and `2026-27.lock.json`, and proves the compiled pack equal to `rulepacks/2026-27/`. The provider,
> the six test roots, the removal of `2026-27/`, `NodeHarnessTest` and T9-21 move to **`W-045g2`**,
> with the two things `W-045f` handed on. **Unowned:** nothing in main code constructs a
> `RulePackProvider` (`git grep "new ClasspathRulePackProvider"` → tests only), so no item wires a pack
> into the running application or loads the timeline from the JAR. The owner is to name one.
> Verified by: that `git grep`; `03-WORK-ITEMS.md` and `02-TECH-SPEC.md` searched for an owner.

> **Erratum (W-045g2, 2026-09-24):** `W-045g2` is split (D-055); removing `rulepacks/2026-27/` is
> **`W-045g3`**. Two corrections to the row's Verify: (1) "T9-21" is not a test anywhere — the fixture
> year 2099-00 loads through the provider, and no test runs its pipelines; (2) the old
> `aPackFiledUnderTheWrongYearIsRefused` passed because of the 2098 fixture's bad checksum, not its
> year — it now has a real wrong-year lock (`2097-98.lock.json` declaring 2099-00).
> Verified by: `git grep T9-21` (no test); the 2098-99 manifest's `taxYear` was `2098-99`.

> **Erratum (W-045h, 2026-09-24):** what `W-139` inherits from `PackDiff` (D-057). It lives at
> `taxengine/rules/timeline/PackDiff.java`, not `validate/`. Its findings are `RuleAdded`,
> `RuleRemoved`, `VersionAdded`, `VersionRemoved` and `ValueChanged` (no `Magnitude`). `W-139` adds the
> three lint findings and the magnitude its 10× lint needs, and **regimes are not diffed**: a change to
> a regime's rate table, disallowances or lifecycle is not yet listed. That gap is `W-139`'s.
> Verified by: `PackDiff` reads only the compiled `law/rates.json`.

> **Erratum (W-045j, 2026-09-24):** the `W-045j` row's "seeded with the **105 sections**
> `01-rulebook.md` cites" does not hold. `grep -oE "\bs\.[0-9]+[A-Z]*"` finds **116** distinct
> citations, and they mix 1961-Act, 2025-Act and Finance Act sections, which reuse numbers for
> different law. A true count needs a row-by-row reading (D-059). `W-045j` built the register and its
> checks, seeded with the **2** sections the timeline cites. **Seeding the rulebook's sections moves to
> `W-045o`**, which already covers every section of both Acts.
> Verified by: that `grep`; `content/coverage.json`; `CoverageRegisterTest`.

> **Erratum (W-045k, 2026-09-25):** the `W-045k` row's "three worked entries" was over the size limit
> and two of them could not link to anything: none of the 80C or HRA rules is in the timeline yet
> (they come with `W-076` and `W-067`), HRA is not a section of the 2025 Act (Sch. III Table Sl. 11),
> and the rulebook quotes Schedule XV only in part. `W-045k` built the catalogue, its checks and the
> **default-regime** entry; **the 80C and HRA entries move to the new `W-045k2`** (D-060).
> Verified by: `ls rulepacks/timeline/law/rules/`; `01-rulebook.md` `SAL-HRA-001`, `DED-80C-ITEMS-001`.

> **Erratum (W-045o, 2026-09-25):** the `W-045o` row cannot be "research, no code" — its Verify
> ("the build reports the count by classification", "the section count equals each Act's") needs a
> count in code; and brief §7.1's four classifications leave no place for what D-033 designs for but
> builds later. `W-045o` covers the **2025 Act** and adds the classification `DEFERRED` (D-066); the
> **1961 Act moves to the new `W-045o2`**, since no copy of it as in force for AY 2026-27 was
> reachable (India Code 403/504, Income Tax Department 403; thc.nic.in's copy is current to ~2019).
> Verified by: `plans/W-045o.md` (source probes); `Coverage.Planned` requires a backlog item.

> **Erratum (W-045d2, 2026-09-25):** the `W-045d2` row's "**51** rulebook IDs" is 52 — `DED-NEWREGIME-001`,
> which the row names, is not caught by `RuleIdNamingTest`'s OLD/NEW pattern; its "(7 files …)" for
> `S202_4_B` and the section-numbered values is 16; and its Verify "`git grep -E 'S[0-9]{3}_' -- backend`
> is empty" would delete `RuleIdNamingTest`'s deliberate `S202_DEFAULT`. `W-045d2` is docs only; the code
> half is the new `W-045d3` (D-069).
> Verified by: the test's patterns run over `01-rulebook.md`; `git grep -lE 'S[0-9]{3}_' -- backend | wc -l`.

**Standing rules from here on (D-043)** — every later item that models a section must also:

1. mark the section `MODELLED` in `coverage.json` (`W-045j`);
2. write or extend its **section catalogue** entry (`W-045k`);
3. author **both tax years** as versions on the timeline (**D-041**) — verifying the 2025-26 row
   first, or recording it as `UNVERIFIED` with `whatToCheck`;
4. name every new rule by **concept** (**D-035**) and cite the other document by prefix (**D-040**).

---

## Phase 5 — Tax nodes: the walking skeleton (8 ids, 7 live)

### W-046 — `TaxByBasisNode` + `TaxByBasisNodeTest`
- [ ] **Produces** `taxengine/node/TaxByBasisNode.java`, `.../node/TaxByBasisNodeTest.java`; `law/` rows `RATE-SLAB-AGE-001`, `RATE-SLAB-AGE-002` (old-regime age bands, VERIFIED) *(added by `W-045c`, D-050)*
- **Spec** `DR 16-getting-started.md` S2 step 5 — **copy it**
- **Do** `DR 23` A11's harness example, using `W-044`'s `NodeHarness`: TI ₹12,00,000 → `TaxByBasis(60000, {})`.
- **Verify** `./mvnw test -pl backend -Dtest=TaxByBasisNodeTest`

> **Erratum (W-046, 2026-09-25):** the Produces line's *"`law/` rows `RATE-SLAB-AGE-001`, `RATE-SLAB-AGE-002`
> (old-regime age bands)"* names two rulebook **statements** (R3: the senior exemption is for residents,
> age tested at any time during the year; the new regime has no age variants), not ladders. The ladders
> are `RATE-SLAB-FIRST-SCHEDULE-AGE-60TO79` and `-AGE-80` (R3 rename, `W-045d`). They, and the ladder
> selection and `ConditionContext` they need, move to the new `W-046b` (D-071).
> Verified by: `01-rulebook.md:213-217`; FA 2026 First Schedule Part I-B Para A (II)/(III), p.85.

### W-046b — the old-regime age ladders and ladder selection *(added by D-071)*
- [ ] **Produces** `law/rules/RATE-SLAB-FIRST-SCHEDULE-AGE-60TO79.json`, `…-AGE-80.json` (TY 2026-27 from FA 2026 First Schedule Part I-B Para A (II)/(III); TY 2025-26 from Part I-A) with their conditions (resident; age); the lock bumped; `RuleIds` regenerated; `TaxByBasisNode` selects the ladder; a `ConditionContext` built from `EngineInputs` + `TaxByBasisNodeTest` cases
- **Spec** `01-rulebook.md` R3 (`RATE-SLAB-AGE-001`/`-002`); D-071 B1–B3
- **Do** decide and source, at EXPLAIN: B1 (a conditional ladder that holds replaces `rateTable`; two holding is an error), B2 (age = completed years on the last day of the tax year, for "at any time during the tax year"), B3 (RNOR counts as resident)
- **Verify** `./mvnw test -pl backend -Dtest=TaxByBasisNodeTest,RulePackLoadTest,GeneratedConstantsTest` — a resident aged 60 at any time in the year gets the 60–79 ladder; a non-resident of 65 does not; the new regime ignores age (AGE-002)

### W-047 — RETIRED
- Merged into **W-046** by D-011. No work.

### W-048 — `ReliefShape` sealed family
- [ ] **Produces** `taxrules/relief/{ReliefShape,FlatWithCeiling,ExcessOverThreshold,ThresholdLadderCeiling,NoRelief,ReliefResult,ReliefContext}.java` *(`ReliefResult`, `ReliefContext` added by `W-045c`, D-050)*
- **Spec** `DR 10` E5 (`10-extensibility.md:305`) · `DR 06` G3 *(corrected by `W-045c`: this row cited `DR 15` Part E, which is `Condition`)*

> **Erratum (W-048, 2026-09-25):** the `W-048` row has no Verify and names the package `taxrules/relief/`
> — it is `taxengine/rules/relief/` (beside `rules/cap/`), verified by `./mvnw test -pl backend
> -Dtest=ReliefShapeTest`; the family adds `UnrelievedTax` (D-073). And no item attached a relief to a rule
> row: `W-049` authors the first relief row (`REBATE-NEW-001`), so its Produces gains **"`RuleVersion`
> carries a `ReliefShape`; `ShapeJson` reads it"** (D-073).
> Verified by: `RuleVersion` holds one `CapShape cap`; `ShapeJson.cap` reads caps only.

### W-049a — a relief on a rule row *(added by D-074, split from `W-049`)*
- [ ] **Produces** `taxengine/rules/relief/RuleRelief.java` (`ReliefShape` + `limitedToSlabRateTax`); `RuleVersion` gains `Optional<RuleRelief> relief`; `ShapeJson` reads a `relief` block; `RuleJson` requires a cap, a relief or conditions, and refuses both a cap and a relief; the 13 `new RuleVersion(` sites updated + tests
- **Spec** `DR 10` E5; D-073, D-074
- **Verify** `./mvnw test -pl backend -Dtest=RuleJsonReliefTest,PackJsonTest,RulePackLoadTest` — each relief shape round-trips from JSON; a cap and a relief together is refused naming the rule; the shipped pack still loads unchanged (no row carries a relief yet)

### W-049 — `RebateNode`
- [ ] **Produces** `taxengine/node/RebateNode.java` + `.../node/RebateNodeTest.java`; `law/` rows `REBATE-NEW-001`, `REBATE-NEW-MR-001` (both VERIFIED) and `REBATE-OLD-001` (`UNVERIFIED` + `whatToCheck` until `W-045n` sources it) *(added by `W-045c`)*
- **Spec** `DR 06-algorithms.md` G3.1 · `DR 13` R-5
- **Verify** `./mvnw test -pl backend -Dtest=RebateNodeTest` — reproduces `DR 08` **T1-N1** (at the ceiling), **T1-N3** (inside the relief band), **T1-O2** (the H-3 test: no relief in the other regime, with a stated reason — property P5), and `DR 13` **T13-1** (the rebate reads `atSlabRates` only); expected values copied from `DR 08`, never computed
- ⚠ **The rebate reads `TaxByBasis.atSlabRates` ONLY.** Using the total produces a nil tax bill on a ₹12,00,000 income that is mostly capital gains (`DR 13` T13-1 — corrected by `W-045c`; the row cited `DR 08`). This is the single most dangerous line in the engine.
- ⚠ The relief branch exists **only** in the default regime.

> **Erratum (W-049, 2026-09-25):** the ⚠ *"The rebate reads `TaxByBasis.atSlabRates` ONLY"* is too strong —
> s.156(2)(b) is on *"the income-tax payable on such total income"*, and s.156(3) **caps** a s.156(2) deduction
> at slab-rate tax (`DR 06` G3.1, `DR 13` R-5); s.156(1) has no such cap. The rebate is computed on the tax and
> the s.156(2) rows are capped at `atSlabRates` (D-074). And `tax.afterrebate` requires `round.totalincome` as
> well as `tax.bybasis`. The relief wiring moves to `W-049a`.
> Verified by: S2 s.156(1)–(3); `02-TECH-SPEC.md` node table.

### W-049b — the rebate's coverage and catalogue entry *(added by D-076: `W-049` missed standing rules 1–2)*
- [ ] **Produces** `content/coverage.json`: 2025 s.156 and 1961 s.87A → `MODELLED` with `REBATE-NEW-TAX-REGIME`, `REBATE-NEW-TAX-REGIME-EXCESS`, `REBATE-RESIDENT-INDIVIDUAL`; a `content/sections/SEC-…` entry for the rebate. No lock change: `content/coverage.json` and `content/sections/` are not compiled into the year pack (`RulePackCompiler` writes only `content/explanations.json`)
- **Spec** D-043 standing rules 1–2; `W-045j` (coverage), `W-045k` (catalogue, SC-1…SC-4); D-074
- **Verify** `./mvnw test -pl backend -Dtest=CoverageRegisterTest,SectionCatalogueTest,RulePackLoadTest` — no number changes; the lock is untouched

### W-050a — a rounding on a rule row *(added by D-076, split from `W-050`)*
- [ ] **Produces** `taxengine/rules/rounding/RuleRounding.java` (`Money toNearest`); `RuleVersion` gains `Optional<RuleRounding> rounding` after `relief`, never beside a cap or a relief; `ShapeJson` reads a `rounding` block; `RuleJson` requires a cap, a relief, a rounding or conditions; the 13 `new RuleVersion(` sites updated + tests
- **Spec** D-075 (the pattern), D-076
- **Verify** `./mvnw test -pl backend -Dtest=RuleJsonRoundingTest,PackJsonTest,RulePackLoadTest` — the block round-trips; rounding beside a cap or relief is refused; the shipped pack loads unchanged

### W-050 — `RoundTotalIncomeNode` and `RoundNetPayableNode`
- [ ] **Produces** `taxengine/node/{RoundTotalIncomeNode,RoundNetPayableNode}.java` + `.../node/RoundingNodesTest.java`; `law/` rows `RATE-ROUND-002`, `RATE-ROUND-003` (both VERIFIED) *(added by `W-045c`)*
- **Spec** `DR 01-rulebook.md` `RATE-ROUND-002`, `RATE-ROUND-003`
- **Verify** `./mvnw test -pl backend -Dtest=RoundingNodesTest` — each node emits one `StepKind.ROUNDING` line using `Money.roundToNearestTenPerSection516`, on the rulebook's worked examples; a full-pipeline run has **exactly two** `ROUNDING` lines (PRD F2.4)
- ⚠ Exactly **two** rounding points in the whole pipeline.

> **Erratum (W-050, 2026-09-25, D-076):**
> - **The rows.** `RATE-ROUND-003` is the rulebook's RECOMMENDATION, not law, so it gets no row. The
>   rows are **`RATE-ROUND-TOTAL-INCOME`** (1961 s.288A; 2025 s.516) and **`RATE-ROUND-PAYABLE`** (1961
>   s.288B; 2025 s.516), each carrying `rounding: {toNearest: "10"}` (`W-050a`), TY 2026-27. The 2025-26
>   versions come with `W-052c`.
> - **The pipeline check** ("a full-pipeline run has exactly two `ROUNDING` lines") moves to `W-053`.
>   No pipeline reaches `round.netpayable` before `W-052`. Here each node emits exactly one.
> - **Requires.** `round.netpayable` requires `tax.netpayable` only. `W-112` adds `interest.and.fees`
>   when it builds that node.
> - **Standing rules 1–2.** 2025 s.516 and 1961 ss.288A/288B → `MODELLED`, plus a catalogue entry for
>   s.516.
>
> Verified by: rulebook R6 (`RATE-ROUND-003` "RECOMMENDATION"); `NodeIds` (no `interest.and.fees`);
> `coverage.json` (all three `PLANNED` for `W-050`).

### W-051 — `CessNode`
- [ ] **Produces** `taxengine/node/CessNode.java` + `.../node/CessNodeTest.java`; `law/` row `RATE-CESS-001` (VERIFIED) *(added by `W-045c`)*
- **Spec** `DR 01-rulebook.md` `RATE-CESS-001` · `REBATE-ORDER-001` — cess is on **post-rebate** tax plus surcharge.
- **Verify** `./mvnw test -pl backend -Dtest=CessNodeTest` — reproduces the cess line of `DR 08` **T2-1**; cess is computed on post-rebate tax plus surcharge, never before the rebate

> **Erratum (W-051, 2026-09-25, D-079):**
> - **The row** is `RATE-HEALTH-EDUCATION-CESS` (was `RATE-CESS-001`): `PercentOfBase` 4% of base
>   `INCOME_TAX_AND_SURCHARGE`, FA 2026 s.3(15) / s.2(6), TY 2026-27. The TY 2025-26 version comes with
>   `W-052c`.
> - **Requires.** `CessNode` requires **`tax.afterrebate` only**: no `tax.surcharge` node exists before
>   `W-056`. **`W-056` adds `tax.surcharge`** to `CessNode`'s requires and its base (tax plus
>   surcharge), per `RATE-CESS-001` and s.3(15). It also adds it to `NetPayableNode`'s sum (`W-052`,
>   D-080).
> - **Why it is safe until then.** Every `DR 08` T1 case has a nil surcharge, and `W-053` runs T1 only.
>
> Verified by: `NodeIds` (no `tax.surcharge`); `DR 08` T2-1 ("TI does not EXCEED 50,00,000 → no
> surcharge"); FA 2026 s.3(15) p.29.

### W-052 — `NetPayableNode`
- [ ] **Produces** `taxengine/node/NetPayableNode.java` + `.../node/NetPayableNodeTest.java` *(added by `W-045c`)*. In Phase 5 it **requires `tax.cess` only**; the `credits` edge is added by the Phase 9 item that builds `CreditsNode` (D-050)
- **Verify** `./mvnw test -pl backend -Dtest=NetPayableNodeTest` — reproduces the final figures of `DR 08` **T1-N1** and **T1-N3**; see Appendix D on the `credits` edge

> **Erratum (W-052, 2026-09-25, D-080):**
> - **Requires.** It requires **`tax.afterrebate` and `tax.cess`**, not `tax.cess` only. Since `W-051`,
>   `tax.cess` is the cess amount alone, so the tax after rebate must be read too.
> - **The value** is the whole post-rebate tax plus the cess. `W-056` adds `tax.surcharge`; the Phase 9
>   `CreditsNode` item adds `credits` (subtracted, and traced under its own rule).
> - **Untraced.** The node emits **no trace line** (`NodeResult.untraced`): the design lists no rule for
>   `tax.netpayable` (`DR 15` B-table "—"), a row stating only a sum can't load (D-075/D-078), and the
>   figure appears as `round.netpayable`'s `ROUNDING` line `in`.
>
> Verified by: `CessNode` (value = the cess); `DR 15` B-table; rulebook `REBATE-ORDER-001` ("an
> arithmetic sum of already-computed rows").

### W-052b — from the runner's result to a `TaxComputation` *(added by D-043)*
- [ ] **Produces** `taxengine/result/ComputationProjector.java` + `.../result/ComputationProjectorTest.java`
- **Spec** STATE **Q3** — `W-042` produces `PipelineRunner.Result`, `W-043` produces `TaxComputation`, and no item joins them
- **Do** read the anchor slots (`NodeIds.TOTAL_INCOME`, `NodeIds.TAX_BY_BASIS`, `NodeIds.NET_PAYABLE`, …) into `TaxComputation`; a missing anchor fails with its name.
- **Verify** `./mvnw test -pl backend -Dtest=ComputationProjectorTest`

> **Erratum (W-052b, 2026-09-25, D-081):**
> - **The constants.** `NodeIds.TOTAL_INCOME`, `TAX_BY_BASIS` and `NET_PAYABLE` don't exist; the
>   generated constants are named for the timeline's slots.
> - **The anchors:**
>   - `totalIncome` ← **`round.totalincome`**: s.516, "shall be deemed to be the total income";
>   - `taxByBasis` ← **`tax.bybasis`**: before rebate, as the field's javadoc says;
>   - `netPayableBeforeFilingEffects` ← **`tax.netpayable`**, unrounded: `round.netpayable` follows
>     `interest.and.fees`, which is date-dependent (`DR 23` A6), and rounding here would be a third
>     rounding point.
> - **The signature** is `project(Result, EngineInputs, ComputationId)`. The year and regime come from
>   the inputs, which must match the run's fingerprint.
>
> Verified by: `NodeIds`; `TaxComputation` javadoc; `DR 15` B3 graph; S2 s.516.

### W-052c — TY 2025-26 on the timeline for the Phase 5 rules *(added by D-043)*
- [ ] **Produces** 2025-26 versions of the Phase 5 rules in `rulepacks/timeline/law/`; `rulepacks/2025-26.lock.json`; a `TaxYearRegistry` entry for 2025-26 + `TaxYear2025RulePackTest`
- **Spec** **D-041**; the sources confirmed by `W-045n`
- **Do** add versions, not rules — where the rulebook says a value is unchanged (`RATE-SLAB-NEW-2025` is *"identical"* to 2026), **one version spans both years**.
- **Verify** `./mvnw test -pl backend -Dtest=TaxYear2025RulePackTest,RulePackCompilerTest` — both years compile, both locks hold, and a 2025-26 computation cites the 1961-Act provision

> **Erratum (W-052c, 2026-09-25, D-082):** *"one version spans both years"* applies only where **one
> instrument** governs both years: the three First Schedule ladders (FA 2026 Part I-A / I-B) and the cess
> (FA 2026 s.2(6) / s.3(15)). The six rules whose 2025-26 law comes from a different instrument get their
> own 2025-26 version, even with the same figures:
> - the new-regime slab (FA 2025 s.25);
> - the two new-regime rebates (FA 2025 s.20);
> - the ₹12,500 rebate (1961 s.87A; FA 2019 s.8);
> - the two roundings (1961 ss.288A/288B).
>
> A version names one `introducedBy`. The "registry entry" is the 2025-26 lock's `instruments` block (no
> production registry exists, D-054). Regime election data per year → **`W-052d`**.
> Verified by: `RuleVersion` (one `introducedBy`, one provenance); FA 2025 pp.23–25; FA 2026 pp.9, 80.

### W-052d — regime election data per tax year *(added by D-082)*
- [ ] **Produces** per-year election data for `NEW_TAX_REGIME` and `FIRST_SCHEDULE_RATES`: TY 2025-26 under the 1961 Act (s.115BAC(6), the option with the return / Form 10-IEA, deadline s.139(1)) beside TY 2026-27's 2025-Act data (s.202, s.263(1)); the shape (versions on a regime, or an election rule per year) is this item's EXPLAIN decision
- **Spec** D-041, D-082 (finding 3 of `plans/W-052c.md`); 1961 s.115BAC(6), to be read
- **Due** before anything reads an election (Phase 9 `ElectionResolver`)
- **Verify** the 2025-26 pack's regimes cite the 1961 Act's election provisions; the 2026-27 pack is unchanged

### W-053 — **T1 golden tests**
- [ ] **Produces** `backend/src/test/resources/taxplanning/golden/2026-27/S202_DEFAULT/T1-*.json` and `.../taxplanning/golden/GoldenCaseRunner.java`, `T1SlabAndRebateTest.java`
- **Spec** `DR 08-golden-tests.md` **T1-N1 … T1-A1f — every case, every expected value**
- ⚠ **Copy the expected values. Do not compute them.** If one fails, the code is wrong (rule R3).
- **Verify** `./mvnw test -pl backend -Dtest=T1SlabAndRebateTest`
- **This is the first real milestone: a correct calculator for slab tax and rebate.**

> **Amendment (D-043, 2026-09-23):** two additions. (1) **Both tax years** (D-041): the T1 cases for
> TY 2025-26 run too, against `W-052c`'s rule pack; a case whose 2025-26 rule is still `UNVERIFIED`
> runs and is reported as `PROVISIONAL`, never skipped. (2) **Invariant I3 is switched on here**
> (STATE **Q2**, D-029): `NodeRegistry.rulesNotEvaluatedByAnyNode` runs against both real rule packs,
> with a checked-in list of rules not yet implemented that **may only shrink** — a rule that leaves
> the list may never return to it.

> **Erratum (W-053, 2026-09-25, D-083):**
> - **Paths.** The cases are `src/test/resources/taxengine/golden/T1/<id>.json`, one per case, each naming
>   both tax years (`DR 08` gives the same figure for both). The runner and test are in the test package
>   `taxengine/golden/`, because they need `pipeline` and `node`, which the CLOSED `taxengine` doesn't
>   expose to `taxplanning` (D-048). `S202_DEFAULT` was renamed `NEW_TAX_REGIME` (D-051).
> - **Entry.** A run enters at `totalincome` through a test source node, on the shipped pipeline less
>   `inputs.components` and `salary.gross` (not built before Phases 7–9).

> **Erratum (W-052b, 2026-09-25, D-081):** `DR 08`'s "Expected" figures are **rounded**, so `W-053`
> compares them against the run's **`round.netpayable`** value, not against
> `TaxComputation.netPayableBeforeFilingEffects`, which is `tax.netpayable` unrounded (T1-N3: the
> expected ₹52,000 equals both only because 52,000 is already a multiple of ten; T1-N2 expects ₹10, where
> the unrounded figure is ₹10.40).

> **Erratum (W-050, 2026-09-25, D-076):** `W-053`'s Verify also asserts that a pipeline run's trace
> has **exactly two** `StepKind.ROUNDING` lines, citing `RATE-ROUND-TOTAL-INCOME` and
> `RATE-ROUND-PAYABLE` (PRD F2.4, `DR 08` T9-16). The check was `W-050`'s, but no pipeline reaches
> `round.netpayable` before `W-052`.

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
| **W-055** | **uses** `ThresholdLadderCeiling` (created by `W-048` with the relief family): the surcharge-relief parameterisations and their tests | `DR 06` G3.2 | `W = U + V`, one implementation, four parameterisations |
| **W-056** | `taxengine/node/SurchargeNode.java` + **modifies** `SlabLadder` (adds a band lookup; the relief threshold is the band's lower bound — `DR 06` G3.2; D-050) | `DR 06` G3.2 | **two buckets** — `RATE-SUR-SPLIT-001` caps the special slice at 15% |
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
| W-067 | **creates** `ExemptionNode` — HRA | `DR 06` G1.3 | **per period**; metro % per period; `limitedBy` names the limb |
| W-068 | **modifies** `ExemptionNode` — LTA | `DR 01` R10 | **calendar** blocks; four independent caps |
| W-069 | **modifies** `ExemptionNode` — allowances | `DR 01` R11 | ⚠ `SAL-ALW-NEW-SUBSET-001` is **STATUS UNCLEAR** — seed from the **section**, flag visibly |
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

> **Erratum (W-037b, 2026-09-21):** `RateBasisHint` **moves to `W-037b`**, which declares it in
> `taxrules.component`. `W-037b`'s `CapitalGainsInput` is typed in it and cannot wait for Phase 9;
> declaring a second copy inside `taxengine` would be the two-vocabularies failure of
> `02-lessons.md` L1. `taxrules` is its right home regardless — `DR 13` R-10 puts "the rate basis"
> on the tax side of the `portfolio` boundary, and `taxrules` is visible to `taxengine`,
> `taxdocs` and `taxplanning` alike. **`W-085` keeps `CapitalGainsSummary` and
> `CapitalGainsBucket`** (the `portfolio`-side read-model) and drops `RateBasisHint`.
>
> Note also that `W-085`'s `CapitalGainsBucket` carries `CapitalGainsType`, which lives in
> `com.thiru.wealthlens.portfolio.dto.enums` — fine for a `portfolio` read-model, and **not**
> reachable from `taxengine`. `W-037b`'s projection therefore carries a `Term` (s.108(2)) and a
> `RateBasisHint` and no classification: by the time a bucket reaches the engine, `taxplanning` has
> already decided whether the class counts as capital gains, and `UNCLASSIFIED` is a blocking error
> upstream (`X1.3`). This is the same module-placement trap as **B-003**, fourth occurrence.
> Verified by: `find backend/src/main/java -name CapitalGainsType.java`
> → `portfolio/dto/enums/CapitalGainsType.java`; `13-edge-cases.md:254`;
> `./mvnw test -pl backend -Dtest=RegimeElectionSnapshotTest` — 20/20.
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

> **Erratum (W-043, 2026-09-21):** **`ComputationReceipt` was produced by two items** — this row and
> `W-043`'s. A6 never mentions it; `W-043`'s `Produces` lists it; this row lists it too. Neither
> Appendix A nor B checks for a type owned **twice**, so it would have surfaced at `W-107` as "this
> already exists", 64 items after the fact.
>
> **`W-043` builds the type**, since `ComputationReceipt.of(ComputationId, TaxYearId)` needs only
> types available there, and splitting it from `ComputationId` would leave that type alone for 64
> items. **`W-107` keeps the API-side work E6 actually describes**: resolving a receipt server-side
> to pack version and checksum, inputs fingerprint, regime, portfolio `asOf`, draft revision and the
> full trace.
> Verified by: `grep -n ComputationReceipt docs/taxplanning-build/03-WORK-ITEMS.md` → two producing
> rows; `./mvnw test -pl backend -Dtest=TaxComputationTest` — 21 tests.
| W-108 | draft endpoints | TS T8 |
| W-109 | compute + compare-regimes endpoints | TS T8 |
| W-110 | optimistic locking, `If-Match`, 409 merge body | `DR 11` F5 |
| W-111 | **T11 tests** | `DR 11` F10 |

---

## Phase 12 — Product surface (12 items)

| Item | Produces | Spec | Feature |
|---|---|---|---|
| W-112 | **modifies** `FilingPosition` (created by `W-043`; adds its interest component) + **creates** `InterestAndFeeNode` | `DR 15` C1 | — |
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
| W-139 | pack linter + cross-year diff — **the diff reuses `PackDiff` from `W-045h`**; this item adds the linter and the cross-year report on top | `DR 15` C4 |
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

## Appendix C — Ownership sweep (`W-045b`, 2026-09-23)

The fourth audit category (STATE, session 3): **a type owned by more than one item**. Found by scanning
every item row for type names (the script is in `W-045b`'s report). **37** types appear in more than
one row. Classified:

### Created twice — a defect; each now resolved

| Type | Rows | Resolution |
|---|---|---|
| `ComputationReceipt` | `W-043`, `W-107` | `W-043` builds the type; `W-107` the API side (erratum, 2026-09-21) |
| `PackDiff` | `W-045h`, `W-139` | `W-045h` builds it; `W-139` reuses it (D-047) |
| `ThresholdLadderCeiling` | `W-048`, `W-055` | `W-048` creates the relief family; `W-055` uses it (row corrected here) |
| `FilingPosition` | `W-043`, `W-112` | `W-043` built it; `W-112` modifies it (row corrected here; D-032's "grow it later") |
| `ExemptionNode` | `W-067`, `W-068`, `W-069` | `W-067` creates; `W-068`, `W-069` modify (rows corrected here) |

The three row corrections were made in place — none of those items had started; the prior text is in
git (parent of `W-045b`'s commit).

### Superseded row — not a defect

`EngineInputs`, `EngineInputsTest`, `FilingFacts`, `RegimeElectionSnapshot`, `TaxpayerFacts` — named in
the original `W-037` row, which the D-025 erratum split into `W-037`/`W-037b`/`W-037c`. The split
rows are the owners. (`TaxpayerFacts` and `EngineInputs` are later **replaced/modified** by `W-045l`.)

### Created, then modified by a later item — the normal pattern, not a defect

`ArchitectureTest`, `CapShape`, `ClasspathRulePackProvider`, `Condition`, `ConditionJson`, `DraftOrFinal`,
`GeneratedConstantsTest`, `InputsFingerprintTest`, `NodeHarnessTest`, `NodeRegistryTest`, `PackJson`,
`PackJsonTest`, `PackValidator`, `PackValidatorTest`, `PersonType`, `PipelineRunnerTest`, `Provenance`,
`RegimeJson`, `RegimeLifecycleTest`, `RegistryJson`, `RuleIds`, `RuleJson`, `RulePackLoadTest`,
`RuleVersion`, `ShapeJson`, `ValueJson`, `VerificationStatus` — **27**. Each later row modifies (or, for
`DraftOrFinal`/`VerificationStatus`, withdraws) a file an earlier row created.

**Rule from here on:** an item row says **creates**, **modifies** or **uses** for a type another row
also names. A type is created by exactly one row.

### Part 2 — Unowned: in the contracts, owned by no item

Found by the 2026-09-23 review and marked ⚠ UNOWNED in `01-PRD.md` / `02-TECH-SPEC.md`. **Proposed**
owners only — nothing here is assigned until the owner accepts it; until then each stays UNOWNED.

| What | Kind | Proposed owner | Why that item |
|---|---|---|---|
| **I8** a ledger entry consumed by at most one `ACTED_ON` draft per year | invariant (runtime) | `W-099` | it builds `FilingDraft` and the draft statuses |
| **I12** a `MigrateTo` successor exists and applies when its predecessor lapses | invariant (pack load) | `W-139` | the pack linter; no shipped regime uses `MigrateTo` yet |
| **I17** a draft's component codes resolve, or carry a `DeprecationPolicy` | invariant (runtime) | `W-099` | it builds `DraftInputs` |
| **I24** catalogue channel matches the rule's Chapter VIII Part | invariant (pack load) | `W-117` | it builds the claim catalogue |
| **I25** every `EvidenceRequirement` names what closes it | invariant (pack load) | `W-117` | same |
| **I26** every dated `ClaimStep` resolves against the tax calendar | invariant (pack load) | `W-117` | the calendar exists from `W-114`, which precedes it |
| **T23-1** content/forms perturbation changes no number (property P3) | test | `W-146` | it already owns I27, the same property |
| **T23-4** identical inputs ⇒ byte-identical `TaxComputation` (property P2) | test | `W-053` | the first real end-to-end pipeline, after `W-052b` builds the projection |
| **T23-8** an unavailable relief yields a line with a reason (property P5) | test | `W-049` | the rebate's relief exists in only one regime — the first "explained zero" |
| `GET /tax-years` | endpoint | `W-108` | the first endpoint item |
| `GET …/itr-suggestion`, `GET …/itr-export` | endpoints | `W-109` | the computation endpoints |
| `scenario/` — `Scenario`, `ScenarioDiff`, `DiffLine` | types | `W-109` | `compare-regimes` needs the diff (PRD F3.3) |
| F41 section-explorer surface (endpoint / page) | feature | a new item after `W-118` | the catalogue type is `W-045k`; the surface is product work |
| PR-5 HUF persona fixture, and **every HUF item** | fixture, phase | a new **HUF phase** after the individual milestones | **no HUF item exists in the backlog yet** — the brief names HUF "built second" |

## Appendix D — Audit of Phases 5–6 (`W-045c`, 2026-09-23)

Every type, rule row and enum constant that `W-046`…`W-058` name, or that the design sections they
cite name, checked against the source tree and the shipped rule pack. Commands: `find backend/src
-name <Type>.java`; `grep -n` on the cited sections; a JSON read of `rulepacks/2026-27/`. **The shipped
pack holds 2 rule rows** (`RATE-SLAB-NEW-2026`, `RATE-SLAB-OLD-2026`) — the "15" stated earlier in
session 4 counted `"status": "VERIFIED"` occurrences, not rows (corrected in the brief, TS T4 and
`W-045d`).

### Per item

| Item | Named | Exists? | Visible to its user? | Finding |
|---|---|---|---|---|
| `W-046` | `TaxByBasisNode`, `NodeHarness`, `TaxByBasis`, `Money`, `NodeId`, `RuleIds`, `TraceLine` (`DR 23` A11) | node: created here; `NodeHarness` ✅ test scope; others ✅ | ✅ all inside `taxengine` | old-regime **age-band rows** `RATE-SLAB-AGE-001/002` (VERIFIED) are not in the pack and no item authors them — needed by `W-053`'s T1-A1 cases. **Proposed:** add to `W-046`'s Produces |
| `W-048` | `ReliefShape` family (`DR 10` E5) | ✗ created here | ✅ | Spec citation **corrected** (was `DR 15` Part E). **Proposed:** add `ReliefResult` and `ReliefContext` to Produces — named only in E5's `apply` signature, owned by no item (session-3 audit category 3). The pack's own note (`law/rates.json` `_pending`) assigns `REBATE-NEW-001` here; `W-049` now owns that row — the note must be updated by `W-049` |
| `W-049` | `RebateNode`, `TaxByBasis.atSlabRates` ✅, `FlatWithCeiling`/`ExcessOverThreshold` (`W-048`) | node ✗ created here | ✅ | **Produces/Verify added** (were missing). `REBATE-OLD-001` is `NEEDS VERIFICATION` — authored `UNVERIFIED` until `W-045n`. `DR 13` T13-1 here is the node-level check; `W-089` keeps the end-to-end test |
| `W-050` | `RoundTotalIncomeNode`, `RoundNetPayableNode`, `Money.roundToNearestTenPerSection516` ✅, `StepKind.ROUNDING` ✅ | nodes ✗ created here | ✅ | **Produces/Verify added** |
| `W-051` | `CessNode`, `RATE-CESS-001` (VERIFIED, not in pack) | ✗ created here | ✅ | **Produces/Verify added**; row authored here |
| `W-052` | `NetPayableNode` | ✗ created here | ✅ | **Produces/Verify added.** TS T6 has it require `credits`, whose producer (`CreditsNode`) arrives in Phase 9 — `TopologicalSort` rejects a required slot with no producer. **Proposed:** in Phase 5 it requires `tax.cess` only; the `credits` edge is added by the Phase 9 item that builds `CreditsNode` |
| `W-052b` | `ComputationProjector`, `PipelineRunner.Result` ✅, `TaxComputation` ✅ (`totalIncome`, `taxByBasis`, `netPayableBeforeFilingEffects`), `NodeIds` (`W-045i`) | projector ✗ created here | ✅ | none — depends on `W-045i` |
| `W-052c` | `RulePackCompiler` (`W-045g`), `TaxYearRegistry` ✅ | — | ✅ | none |
| `W-053` | `GoldenCaseRunner`, `T1SlabAndRebateTest`, `NodeRegistry.rulesNotEvaluatedByAnyNode` ✅ | test types ✗ created here | ✅ | golden path embeds `S202_DEFAULT` — follows `W-045d`'s rename (OQ-4) |
| `W-054` | surcharge ladders as `SlabLadder` bands (`DR 05` M1.3 table) | `SlabLadder` ✅ | ✅ | none |
| `W-055` | `ThresholdLadderCeiling` | via `W-048` | ✅ | none (row already says *uses*) |
| `W-056` | `SurchargeNode`; `DR 06` G3.2 calls `ladder.bandFor(income)` and `band.marginalReliefThreshold` | `bandFor` ✗, `marginalReliefThreshold` ✗ (`SlabLadder` has `apply`, `shapeName`, `validate` only; `Band` has no threshold) | ✅ | **Proposed:** `W-056` **modifies** `SlabLadder` (adds a band lookup); the threshold is the band's lower bound, as G3.2's own comment says — no new field |
| `W-057` | T2 cases, `GoldenCaseRunner` (`W-053`) | ✅ after `W-053` | ✅ | none |
| `W-058` | metamorphic properties (`DR 19` E4.1) | ✗ created here | ✅ | none |

### Cross-cutting

- **No Phase 5 item authored the law its nodes evaluate** (R1's root cause): the rebate, cess and
  rounding rows are now in `W-049`–`W-051`'s Produces; the age-band rows are the one proposal left
  (`W-046`).
- Every consumer here is inside `taxengine`, so no named interface is needed in Phases 5–6.
- `Severity.SEVERE` appeared in a search but is `DisabilitySeverity.SEVERE` in a STATE history note —
  not a reference. Recorded so nobody repeats the search and reports it.

**All four proposed corrections accepted and applied (D-050):** `W-046` age-band rows; `W-048`
`ReliefResult`/`ReliefContext`; `W-052` `credits` edge deferred to Phase 9; `W-056` modifies `SlabLadder`.

## Appendix E — Slices ↔ phases, and the legacy deletions (`W-045c`, 2026-09-23)

### Slices (`09-build-plan.md` B1) mapped to the backlog

| Slice | Scope | Phases / items | Done when |
|---|---|---|---|
| 0 | verify the unverified | `W-045n` (Phase 5 rules), standing rule 3 per phase | per phase |
| 1 | registry, packs, component codes | Phases 1–3 (`W-004`…`W-034b`) ✅ | ✅ |
| 2 | engine: pipeline, slabs, rebate, surcharge, cess, rounding | Phases 4–6 | `W-053` (T1), `W-058` (T2) |
| 3 | salary head **+ minimal persistence and API** | Phase 7 (salary) · Phase 11 (persistence, API) | salary at Phase 7; "end to end through the API" only at `W-109` — the slice spans two phases |
| 4 | exemptions: HRA, LTA, allowances | Phase 7 (`W-067`…`W-069`) | T3 at `W-070` |
| 5 | perquisites, employer contributions | Phase 8 | — |
| 6 | Chapter VIII | Phase 8 | `W-080` |
| 7 | house property, other sources, set-off | Phase 9 | — |
| 8 | capital-gains feed | Phase 9 | `W-089` |
| 9 | regime comparison, ITR suggestion, PDF | Phase 10 (ITR) · `W-109` (compare) · `W-123` (PDF) | `W-123` |
| 10 | Form 16 / Form No. 130 ingestion | Phase 13 | `W-132` |
| 11+ | the Next tier | **not in the backlog** | — |

### Legacy deletions, in an order that compiles

`09` B2's order deletes the policy classes "after Slice 1" and the engines "after Slice 2" — but the
engines (`AbstractTaxEngine`, `OldRegimeTaxEngine`, `NewRegimeTaxEngine`, `TaxEngine`), `PerquisiteValuationService`,
`TaxComputationService` and `RestructuringEngine` all import the policy types, and the legacy compute
endpoint is live. Grouped by what imports what (checked with `grep` on `import …taxplanning.*`):

| Group | Delete | Imported by anything kept? | Earliest |
|---|---|---|---|
| **L1 — dead code** | `engine/FbpOptimizer`, `engine/ItrFormAdvisor`, `recommendation/HrLetterContentBuilder`, `parsing/ParsedCtcDto` (no importer at all); `engine/MarginalReliefCalculator` + its test (imported only by its test); the empty `test/…/taxplanning/_review` directory | no | **any time** |
| **L2 — the compute path, together** | policy entities, repositories, `PolicySeederService`, `PolicyService`, `AllowanceResolutionService`, `ResolvedAllowance`, the five `data/tax-policies/*.json`; `FormulaEvaluator`, `SafeExpressions`; the engines and `TaxEngineFactory`; `HraExemptionCalculator`, `PerquisiteValuationService`; `salary/**`; `service/TaxComputationService`; `recommendation/**` (incl. `RestructuringEngine`, `RegimeAdvisor`); `document/**` and the Thymeleaf template; both controllers; the legacy enums (keep `RegimeType` if still referenced); their tests and `TaxPlanningControllerIntegrationTest`; the `/tax-planning/public/**` entry in `AuthConfig`; legacy collections in `cleanDatabase()` | each imports the others — so **one** group | **path A:** after `W-109` (replacement compute endpoint) · **path B:** now, if **OQ-3** is answered "disable" |

**Proposed items** (ids suffixed onto what they follow; not added until the owner accepts them):
`W-045c1` = L1 — **added (D-050)**. L2: `W-109b` under path A, or `W-045m` becomes L2 under path B —
**waits on OQ-3**.

## Appendix F — Owed later, and where each lands (D-043, 2026-09-23)

Prerequisites that are real but not needed before `W-046`. Each names the item it must precede, so
none depends on anyone remembering it.

| Owed | Must land before | Why then |
|---|---|---|
| Per-phase TY 2025-26 verification (the other ~40 `NEEDS VERIFICATION` rulebook rows; 9 `PROVISIONAL` golden cases) | the first item of each phase that uses them — standing rule 3 above | verify where it is used, not all at once |
| The placeholder deletions | as placed by `W-045c` Appendix E | nothing is deleted until its replacement passes golden tests (`09` B2) |
| HUF sourcing — each per-type difference in `00-BRIEF.md` §8 as a rulebook row | the first HUF item | rule R1: no per-type number in code without a source |
| `Huf` karta and members | the first rule that reads them | rule B2 |
| Capital gains on assets outside `portfolio` (manager **M-08**'s gap) | `W-089` (capital-gains milestone) | a user who sells a flat otherwise gets a silently incomplete computation |
| Section catalogue entries for every modelled section | each item that models a section — standing rule 2 | SC-1 ratchet |
| The rule-authoring admin screen (**OQ-2**) | after `W-111` | files + PR review suffice until an API and persistence exist |
| Professional review of golden cases (**OQ-1**, `VerificationLevel.PROFESSIONALLY_REVIEWED`) | before anyone outside the owner's family relies on a figure | the gate needs a named reviewer |

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
