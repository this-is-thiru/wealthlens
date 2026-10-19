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
| **W-056a** | `NodeContext.recomputed(target, pinned, value)` + the runner supplying it; `NodeHarness` stub *(added by D-086)* | `DR 06` G3.2 ("re-entering the tax computation at exactly the threshold") | re-runs only the nodes between `pinned` and `target`, in a fresh store; declared slots only |
| **W-056** | `taxengine/node/SurchargeNode.java` + **modifies** `SlabLadder` (adds a band lookup; the relief threshold is the band's lower bound — `DR 06` G3.2; D-050) | `DR 06` G3.2 | **two buckets** — `RATE-SUR-SPLIT-001` caps the special slice at 15% |
| **W-057** | **T2 golden tests** | `DR 08` T2-1 … T2-6 | copy expected values |
| **W-058** | metamorphic property tests | `DR 19` E4.1 | `moreIncomeNeverMeansLessTax` would have caught H-1 unaided |

> **Erratum (W-054, 2026-09-25, D-084):**
> - **W-054's rows.** They are **two per regime**, each a `SlabLadder` whose bands are the Act's clauses:
>   - `RATE-SUR-FIRST-SCHEDULE` / `RATE-SUR-NEW-TAX-REGIME` on total income: 10% above 50L, 15% above
>     1Cr, clauses (iv)/(v) "not covered → 15%" continuing;
>   - `RATE-SUR-FIRST-SCHEDULE-EXCLUDING` / `RATE-SUR-NEW-TAX-REGIME-EXCLUDING` on total income
>     *excluding* dividends and the special gains: 25% above 2Cr, and 37% above 5Cr in the first
>     schedule only.
>
>   Each is one version spanning TY 2025-26 and 2026-27 (FA 2026 states all four tables). `rates.json`
>   is gone (the timeline, `W-045f`).
> - **W-056** takes the **greater** of the two rows' rates for the band the income falls in. The
>   clauses compose exactly that way. It keeps `RATE-SUR-SPLIT-001` (the 15% limit on that part).
> - **I3.** The Phase 6 rows join `T1SlabAndRebateTest.NOT_YET_EVALUATED` as an owner-approved exception
>   to D-043's "may only shrink", and `W-056`'s Verify asserts the set is **empty** again.
>
> Verified by: FA 2026 s.2(4)(b) Table Sl. 10 (p.7), Part I-A Para F Table 1 Sl. 1 (pp.82–83), s.3(4)(b)
> Table Sl. 10 (pp.13–14), Part I-B Para F Table 1 Sl. 1 (pp.86–87).

> **Erratum (W-055, 2026-09-26, D-085):**
> - **W-055's rows.** The four statements of `W = U + V` are two parameterisations, because the two
>   years' tables are identical: `RATE-SUR-FIRST-SCHEDULE-CEILING` (thresholds 50L, 1Cr, 2Cr, 5Cr) and
>   `RATE-SUR-NEW-TAX-REGIME-CEILING` (50L, 1Cr, 2Cr — no 5Cr). Each is a relief-only `ThresholdLadderCeiling`
>   row with `limitedToSlabRateTax: false`, one version spanning TY 2025-26 and 2026-27. They join
>   `NOT_YET_EVALUATED` under D-084.
> - **W-056** reads the relief thresholds **from these rows**, not from the ladders' band lower bounds.
>   Table 2 tests total income against every threshold, while 2Cr and 5Cr sit on the `-EXCLUDING`
>   ladder; the numbers coincide, the bases do not. `W-056` still adds the band lookup for the rate.
>   `SurchargeReliefTest` guards the coincidence: each row's thresholds equal its two ladders' non-zero
>   band lower bounds.
>
> Verified by: FA 2026 s.2(5) Table Sl. 6 (pp.8–9), Part I-A Para F Table 2 Sl. 1 (p.84), s.3(5) Table
> Sl. 6 (p.15), Part I-B Para F Table 2 Sl. 1 (p.88).

> **Erratum (W-056, 2026-09-26, D-086):**
> - **U is priced by re-entry**, the new `W-056a`: `SurchargeNode` asks for `tax.afterrebate` recomputed at
>   the threshold and adds the unrelieved surcharge on it, so the rebate and the age ladders bear on U with
>   nothing duplicated.
> - **The two buckets move to `W-088`.** Neither input exists yet: no node carries dividend income
>   (`W-079`) or special-rate tax (`W-088`). `W-056` takes the excluding income as the total income, which
>   is exact while the graph carries neither, and **refuses** (naming `W-088`) if `tax.afterrebate` carries
>   special-rate tax. `RATE-SUR-SPLIT-001` and `DR 08` T2-6 are `W-088`'s.
> - **The graph:** `tax.surcharge` after `tax.afterrebate` in both regimes; `CessNode`'s base becomes tax
>   after rebate plus surcharge (FA 2026 s.3(15)); `NetPayableNode` adds it. `NOT_YET_EVALUATED` is deleted.
>
> Verified by: `DR 06` G3.2; rulebook `REBATE-ORDER-001`, `RATE-SUR-SPLIT-001`; `DR 08` T2-6 (`PROVISIONAL`).

> **Erratum (W-057, 2026-09-26, D-087):** `W-057` runs `DR 08` **T2-1 … T2-5**, not T2-6. T2-6 needs
> special-rate income and `RATE-SUR-SPLIT-001`, both `W-088`'s (D-086); it is not added as a skipped test,
> which would read as coverage. The cases are `src/test/resources/taxengine/golden/T2/<id>.json`, with
> T2-3 split into T2-3a/T2-3b (two incomes) and T2-4 into T2-4a/T2-4b (old/new), each in both years, run by
> `golden/T2SurchargeTest` through `W-053`'s `GoldenCaseRunner`.
>
> Verified by: `DR 08` T2 (`08-golden-tests.md:159-297`) and its coverage map (`:873-875`).

> **Erratum (W-058, 2026-09-26, D-088):**
> - **The property that catches H-1 is bounded marginal cost, not monotonicity.** A missing marginal relief
>   makes tax jump *up* at the threshold — ₹10 over ₹50,00,000 would cost ₹1,12,323 (`DR 08` T2-2) — so
>   "more income never means less tax" still holds. What it breaks is: extra income ΔI never raises the
>   payable by more than ΔI × 1.04 (each relief caps tax and surcharge at the extra income; the cess adds
>   "four per cent. of such income-tax and surcharge", FA 2026 s.2(6)/s.3(15)). The one lawful exception is
>   the First Schedule step across ₹5,00,000, where the rebate has no marginal relief (H-3, `DR 08` T1-O2).
>   Monotonicity stays: it catches an over-generous or misplaced relief.
> - **W-058 runs three properties** — monotone, bounded marginal cost, the trace reconciles (TAX − RELIEF =
>   `tax.netpayable`) — as a deterministic sweep (no jqwik: random tries rarely land in a relief's ~₹1.6L
>   binding zone), `golden/EngineMetamorphicTest`. The other three `DR 19` E4.1 properties need inputs the
>   graph does not carry yet; Appendix F places them.
>
> Verified by: `DR 19` E4.1; `DR 08` T2-2, T2-3, T1-O2; measured this session (₹1,040 per ₹1,000 at ₹50L).

---

## Phase 7 — Salary head (12 items)

| Item | Produces | Spec | ⚠ |
|---|---|---|---|
| W-059 | `components.json` for both years | `DR 10` E4, `DR 15` F-part | every code has `cashNature` |
| W-060 | `ComponentPartitionNode` | `DR 06` G1.1 step 1 | unknown code = error, never a silent addend |
| W-061a | `rules/definition/RuleDefinition` + `HeadTotal`: a rule row may carry a definition *(added by D-091)* | `DR 01` R7 (`SAL-DEF-001`) | a definition is none of cap, relief, rounding, condition; at most one of the four on a version |
| W-061 | `GrossSalaryNode` | `DR 01` R7 | |
| W-062 | `SalaryBaseNode` | `DR 01` `SAL-DEF-HRA-001` etc. | **basic + DA where the terms provide**; one function, five rules |
| W-063 | `SalaryDeductionNode` | `DR 01` R8 | professional tax **only** in the opted-out regime |
| W-064 | `salary.json` law rows | `DR 01` R7, R8 | |
| W-065 | `SalaryIncomeNode` | | |
| W-066 | `ProrationBasis` family | `DR 15` C3 | named basis per rule; **no caller divides** |
| W-066b | `rules/pack/TemporallyApportionedCap.java` + `RuleRow.prorationBasis` + `.../pack/TemporallyApportionedCapTest.java` *(path corrected by D-097, then to `rules/pack` by D-098 so `rules.pack` and `rules.temporal` do not depend on each other)* | `DR 18` V5 (`:205-245`) | ⚠ **nothing currently applies a mid-year change.** `W-030` made one declarable and validated; without this a rule that changed inside the year is evaluated as though it never did. Test each `MidYearCombination` branch, and that `UNDETERMINED` **throws** |
| W-067a | HRA's inputs and shapes *(added by D-099)*: `input/Tenancy` + `EnteredFacts.tenancies`; `rules/cap/AmountInExcess` (limb (b), "the amount by which … exceeds"); `rules/cap/PercentOfBaseByLocation` + `CapContext.locations` (limb (c)); their JSON | `DR 13` R-11; S4 r.279(1); r.2A | no row, no node — `W-067` uses them |
| W-067 | **creates** `ExemptionNode` — HRA | `DR 06` G1.3 | **per period**; metro % per period; `limitedBy` names the limb |
| W-068a | LTA's inputs *(added by D-100)*: `input/Journey` + `EnteredFacts.leaveTravel`; `input/LeaveTravelBlock` and `LedgerSnapshot`'s per-block map replacing `ltaJourneysClaimedInBlock` / `ltaCarryOverAvailable`; the fingerprint | `DR 13` R-12; S4 r.278; r.2B | a tax year spans two calendar blocks — one counter cannot hold them |
| W-068 | **modifies** `ExemptionNode` — LTA | `DR 01` R10 | **calendar** blocks; four independent caps |
| W-069a | Allowances' inputs *(added by D-101)*: `input/AllowanceUse` + `EnteredFacts.allowanceUses` — per allowance component, the amount actually incurred (Sch. III Sl. 12) or the children and months covered (Sl. 13); the fingerprint | S4 r.280; r.2BB | no row, no node |
| W-069 | **modifies** `ExemptionNode` — allowances | `DR 01` R11 | ⚠ `SAL-ALW-NEW-SUBSET-001` is **STATUS UNCLEAR** — seed from the **section**, flag visibly |
| W-069b | Transport allowance for a blind, deaf-and-dumb or orthopaedically handicapped employee *(added by D-101)*: S4 r.280(2) Table Sl. 10 / r.2BB(2) Table; the one Sl. 13 allowance that survives the new regime (r.280(3)) | `DR 01` R11, H-10 | needs OQ-7 answered, the employee's disability, and TY 2025-26 sourcing |
| W-066a | Retirement and termination *(added by D-096)*: **creates** `RetirementNode` (`salary.retirement`) and the `RET-*` rules — gratuity, leave encashment, commuted pension, VRS, retrenchment — with their components (`DR 05` M2: `GRATUITY`, `LEAVE_ENCASHMENT`, `COMMUTED_PENSION`, `VRS_COMPENSATION`, `RETRENCHMENT_COMPENSATION`), 2025 s.18 profits in lieu of salary (`RET-NOTICE-001`), and `salary.income`'s `salary.retirement` input | `DR 01` RET-*; `DR 06` G1.1 steps 5, 9 | reads `M3.4` cross-year ledgers; uses `SAL-SALARY-FOR-GRATUITY` (`W-062`). Placed before `W-070` by the owner (D-096) |
| W-070 | **T3 golden tests** | `DR 08` T3-1 … T3-5 | T3-2, T3-3 are **PROVISIONAL** — report separately |
| W-064a | Entertainment allowance (`SAL-ENTMT-001`, 1961 s.16(ii)) *(added by D-094)*: `Employment` employer type (Government), component `ENTERTAINMENT_ALLOWANCE`, the "salary (exclusive of any allowance, benefit or other perquisite)" measure, and `SalaryDeductionNode`'s three-limb least-of | `DR 01` R8 | TY 2025-26 only, old regime only (s.115BAC(2)); **no 2025-Act counterpart** — the 2026-27 version is an absence. Placed after `W-070` by the owner (D-095) |
| W-066c | Retirement limits set by notification *(added by D-103; scope per D-106)*: S2 s.19(1) Table Sl. 6 (any other gratuity: least of actual, the notified amount, half a month's salary per completed year on the ten-month average) and Sl. 14 (other employees' leave encashment: four limbs, (c) notified), with s.19(2)(a)/(f)'s A − B aggregates across employers and years; 1961 s.10(10)(iii), (10AA)(ii). Retrenchment (Sl. 10) moved to `W-066d` (D-106) | `DR 01` RET-GRAT-OTHER-001, RET-GRAT-AGG-001, RET-LEAVE-OTHER-001, RET-LEAVE-AGG-001, H-14 | limits read: S.O. 1213(E), S.O. 2276(E) (`sources/2026-09-27-retirement-limits.md`); reads `M3.4` ledgers and `SAL-SALARY-FOR-GRATUITY`. Placed after `W-064a` by the owner |
| W-063a | The TY 2025-26 new-regime standard deduction's source *(added by D-107)*: `SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME`'s TY 2025-26 version cites the Taxation Laws (Amendment) Act, 2025 (29 of 2025) s.3 — "or clause (iii)" in s.16(ia)'s proviso, deemed from 1-4-2025 — in place of the CBDT validation rule; the note corrected; no figure changes | `DR 01` SAL-STDDED-001; `sources/2026-09-27-retirement-limits.md` round 3 | placed before `W-066d` by the owner (D-107) |
| W-066d | Retirement limits set by other Acts *(added by D-103; Sl. 10 whole per D-106)*: S2 s.19(1) Table Sl. 5 (Payment of Gratuity Act, 1972 s.4(2)–(3)) and Sl. 10 whole — retrenchment: least of received, Industrial Disputes Act, 1947 s.25F(b), and the notified amount (₹5,00,000, read only in the DoR consolidation) — with s.19(2)(c)–(d); 1961 s.10(10)(ii), (10B) | `DR 01` RET-GRAT-PGA-001, RET-RETRENCH-001 | both Acts were repealed on 21-11-2025 (S.O. 5319(E), S.O. 465(E)) — mid TY 2025-26 — while the Income-tax Acts still name them; **OQ-9 decided (D-107): read as the Codes' s.53 and s.70(b)**, which carry the same words. Placed after `W-066c` by the owner |

> **Erratum (W-059, 2026-09-26, D-089):**
> - **W-059 builds the registry's plumbing, not its content.** `ComponentDefinition` refuses a component
>   that feeds no rule unless it is `informationalOnly`, and `PackValidator` requires every `feedsInto` rule
>   to exist in the pack; no salary rule does yet. So W-059 adds `exec/components/<CODE>.json` (one file
>   per component), its reader, and the compiler emitting the components live in a year — proven on a
>   fixture — and ships no component.
> - **A component arrives with the first rule that reads it.** Each item that authors a rule consuming a
>   component also authors that component, for both years, with its `cashNature` sourced: `W-063` brings
>   `PROFESSIONAL_TAX` with `SAL-PTAX-001`; `W-064` brings the cash-salary codes with `SAL-DEF-001` and
>   `SAL-STDDED-*`; Phase 8's items bring the perquisites and contributions with their rules; `W-079` and
>   `W-088` bring the other-sources and capital-gains codes. `DR 05` M2 lists the initial codes.
> - **"`DR 15` F-part" is unresolved.** `15-review-findings.md` Part F covers entities (F1–F6), none of them
>   components; the specs used are `DR 10` E4 and `DR 05` M2.
>
> Verified by: `ComponentDefinition` (constructor), `PackValidator` (`feedsInto` / `salaryBaseFor` must
> resolve), `RulePackCompiler` (empty `exec/components.json`), `TimelineReader` Javadoc (D-053).

> **Erratum (W-061, 2026-09-26, D-091):** gross salary is grounded in a rule row. "Salary includes …"
> (2025 Act s.16; 1961 s.17(1); `SAL-DEF-001`) is none of a cap, relief, rounding or condition, so the new
> `W-061a` lets a rule version carry a **definition**, first `HeadTotal(head)`. `W-061` then authors the row
> (concept id `SAL-SALARY-INCLUDES`, D-035), both years, marks 2025 Act s.16 `MODELLED` with its catalogue
> entry, and `GrossSalaryNode` sums the partition's SALARIES lines with an INCOME line citing it. Salary
> components (`W-064`) `feedsInto` it. Perquisites and employer-contribution charges join gross later;
> `SAL-CHARGE-001` stays `W-065`'s.
>
> Verified by: `RuleJson.capOrNull` Javadoc; `CapShape` permits; `coverage.json` (2025 s.16 → `W-061`).

> **Erratum (W-062, 2026-09-27, D-092):** "five rules" is five *uses* (HRA, gratuity, leave encashment,
> employer NPS, employee NPS) over **four** rule ids; `SAL-DEF-RPF-001`'s base is unread (`NEEDS
> VERIFICATION`) and is owed to the RPF item. `W-062` ships the four rows (`SAL-SALARY-FOR-*`, `RuleDefinition`
> `SalaryBase`), both years, and `SalaryBaseNode` evaluating them, with no component: every base is ₹0 until
> `W-064` gives `BASIC`/`DA` their `salaryBaseFor` entries, and nothing reads a base until `W-067`. The DA
> condition is the registry's (`onlyWhereTermsProvide`) and the entered fact `Employment`'s
> (`termsIncludeDearnessAllowance`); the value is per rule per employment; the lines are `StepKind.MEASURE`.
> No coverage status moves: the definitions are sub-provisions of s.19 (`W-063`), s.124 and Sch. XV (`W-076`)
> and Rules r.279.
>
> Verified by: `01-rulebook.md` R7, H-4; `PackValidator` (component→rule only); `coverage.json`.


> **Erratum (W-063, 2026-09-27, D-093):**
> - **`W-063` takes the standard deduction as well as professional tax.** It authors
>   `SAL-TAX-ON-EMPLOYMENT` (s.19(1) Table Sl. 1; 1961 s.16(iii)) and `SAL-STANDARD-DEDUCTION-NEW-TAX-REGIME` /
>   `-ANY-OTHER-CASE` (Sl. 2(a)/(b); 1961 s.16(ia)), and the whole `SalaryDeductionNode`. This amends D-089:
>   `W-064` keeps the cash-salary components and `SAL-ENTMT-001`.
> - **Professional tax sits under SALARIES but is not salary.** A `HeadTotal` therefore counts only the head's
>   lines whose component `feedsInto` the defining rule, and `GrossSalaryNode` filters by the registry.
> - **The new regime's exclusion of professional tax** (s.202(2)(a)(iv)) is the row's `ExceptIn
>   [NEW_TAX_REGIME]`. `DIS-S202-2A` is owed (Appendix F).
>
> Verified by: S2 s.19(1) Table Sl. 1–2 (p.41), s.202(2)(a)(iv) (p.227); `DisallowanceSet.disallows` has no caller.

> **Erratum (W-078g, 2026-10-09):** the `ExceptIn [NEW_TAX_REGIME]` above no longer carries the exclusion — `SAL-TAX-ON-EMPLOYMENT` is
> `AllRegimes` and the new regime forbids it through `DIS-NEW-TAX-REGIME-NO-EXEMPTION` (basis `REG-NEW-TAX-REGIME-NO-EXEMPTION`), so
> the trace gives a nil line naming s.202(2)(a) / 1961 s.115BAC(2)(i) (D-165).
> Verified by: `SalaryDeductionNodeTest.evaluate_whenInTheNewRegime_allowsNoProfessionalTaxAndTheLargerStandardDeduction`.


> **Erratum (W-064, 2026-09-27, D-094):**
> - **`W-064` ships components, not law rows.** After D-091, D-092 and D-093 it holds the seven core cash-salary
>   components — `BASIC`, `DEARNESS_ALLOWANCE`, `HRA`, `LTA`, `SPECIAL_ALLOWANCE`, `BONUS`, `COMMISSION` — each
>   feeding `SAL-SALARY-INCLUDES`. "`salary.json`" predates one-file-per-rule (D-053).
> - **The five allowance-specific codes** (`CHILDREN_EDUCATION_ALLOWANCE`, `HOSTEL_ALLOWANCE`, `UNIFORM_ALLOWANCE`,
>   `CONVEYANCE_ALLOWANCE`, `TRANSPORT_ALLOWANCE_DISABLED`) arrive with their exemptions in `W-069`, which amends
>   D-089.
> - **`SAL-ENTMT-001` is `W-064a`.** It needs an employer type, a fifth salary measure and a node change.
> - **The salary bases hold basic and DA only**, pending OQ-5 (commission, bonus).
>
> Verified by: `ComponentDefinition` (`feedsInto` required), `HeadTotalLines` (D-093), S2 and FA 2026 (no
> "entertainment allowance").


> **Erratum (W-065, 2026-09-27, D-096):**
> - **`SalaryIncomeNode` requires `salary.gross` and `salary.deductions` only.** `W-067` adds `salary.exemptions`
>   and `W-066a` adds `salary.retirement`, each in its own item (the D-079 pattern).
> - **Its value is a `MoneyValue`, and it is untraced**, as `NetPayableNode` is: every part already has its line,
>   and no rule states the difference (`DR 06` G1.1 step 12: "—"). A negative result is refused.
> - **No item owned the retirement node, `RET-*` or 2025 s.18**, and `W-062`/`W-063`'s "the retirement items" named
>   none. `W-066a` is added before `W-070`.
> - **2025 and 1961 s.15 (the charge) stay `PLANNED`**, reassigned to the arrears/advance-salary work
>   (`REL-ARREARS-001`). What an entry means under s.15 is `SalaryComponent`'s contract.
>
> Verified by: S2 s.15 (p.37), s.18 (pp.40–41), s.19(1) (p.41); `02-TECH-SPEC.md` T6; the Phase 7 table (no
> retirement item before this).


> **Erratum (W-066, 2026-09-27, D-097):** a proration basis answers `units(year, window)` (a whole count of days
> or months, which is what `PerUnitPerPeriod` multiplies) and `apportion(forTheYear, year, window)` (the one
> division, rounded once in `Money`). It has no public fraction. `CompletedMonths` counts calendar months lying
> wholly within the window (`DR 13` B2-20). `OccupationPeriods` is `W-067`'s input, not a basis. The family lives
> in `rules/temporal/proration/`.
>
> Verified by: `PerUnitPerPeriod.apply` (`ctx.count(period)`); `Money.times(int)` Javadoc; no `taxengine/temporal`
> package exists.


> **Erratum (W-066b, 2026-09-27, D-098):**
> - **Built, and wired.** `TemporallyApportionedCap` alone would apply nothing, so the four nodes that apply caps
>   route through it. `CessNode` and `SalaryDeductionNode` call `apply`. `TaxByBasisNode` and `SurchargeNode`
>   need the ladder itself, so they call `governingVersion`, which allows only LATEST or EARLIEST in a year the
>   rule changed.
> - **PRORATE_PER_SEGMENT** is each version's cap over the year's figures, apportioned by the segment's share
>   of the year on `RuleRow.prorationBasis`. A cap whose input is itself for part of the year is split by its
>   node (HRA, `W-067`).
> - **STATUTORY is refused** until the first rule that needs it; nothing in the rulebook does.
> - UNDETERMINED throws `UndeterminedApportionmentException` (re-parented by `W-105`).
>
> Verified by: `TemporalContext.on` (TAX_YEAR resolves at year end); the four nodes' `versionAt(...).cap()`
> calls; `rules.pack` → `rules.temporal` imports.


> **Erratum (W-067, 2026-09-27, D-099):**
> - **Split.** `W-067a` builds the inputs and the two shapes; `W-067` builds the row, `ExemptionNode` and the
>   wiring.
> - **HRA is per occupied period (R-11) and needs a tenancy input** that nothing recorded before.
> - **Limb (b) is a subtraction and limb (c) a location lookup**, so there are two new typed shapes (D-NOFORMULA
>   allows a typed addition).
> - **The TY 2025-26 city list is r.2A's four**, `UNVERIFIED` (post-2011 unsearched); TY 2026-27 is r.279's
>   eight (H-2).
> - **Two or more employments are refused** until employment periods exist. `SAL-HRA-006` (landlord PAN) is owed.
>
> Verified by: S2 Sch. III Table Sl. 11 (p.506); 1961 s.10(13A); S4 r.279 (p.1776); r.2A (DoR pp.347–348); the six
> `CapShape` permits; `CapContext`; `NodeResult`.


> **Erratum (W-068, 2026-09-27, D-100):**
> - **Split.** `W-068a` builds the journey input and the per-block ledger; `W-068` builds the rows and the node.
> - **The fares are entered and the rule chooses among them.** Only r.278(1)(iii)(B)'s ₹30/km (TY 2026-27) is a
>   figure the Rules state.
> - **The two years differ in the air limb and the no-transport limb** (r.2B against r.278).
> - **The children limit (`SAL-LTA-CHILD-001`) is not modelled** (Appendix F).
>
> Verified by: S2 Sch. III Table Sl. 8; S4 r.278 (pp.1775–1776); r.2B (DoR pp.348–350); `LedgerSnapshot`.


> **Erratum (W-069, 2026-09-27, D-101):**
> - **Split.** `W-069a` builds the inputs; `W-069` builds uniform, conveyance, CEA and hostel; `W-069b` builds
>   transport for a disabled employee (OQ-7). The armed-forces and area allowances are out of scope.
> - **TY 2025-26 Sl. 13 amounts need Rules 1962 r.2BB as in force for AY 2026-27.** The DoR compendium (to
>   2010–11) is superseded for them, so they are not authored unless sourced.
> - **`SAL-ALW-NEW-SUBSET-001` is seeded from s.202(2)(a)(ii):** r.280(3)'s list applies in the new regime, and
>   the version relying on it is `UNVERIFIED`, so figures using it are visibly PROVISIONAL.
>
> Verified by: S4 r.280 (pp.1777–1783) read in full; DoR r.2BB (pp.353–357) with its footnotes; 1961 s.115BAC(2)(i).

---

## Phase 8 — Perquisites, employer contributions, Chapter VIII (10 items)

| Item | Produces | Spec | ⚠ |
|---|---|---|---|
| W-071a | The perquisite machinery and residential accommodation *(W-071 split by D-110; W-072 folded in)*: **creates** `PerquisiteNode` (before `salary.gross`; every output `NON_CASH`), perquisite components counting in gross salary, the facts shape; accommodation — S4 r.15(2) Table I and sub-rules, r.15(8)'s "salary"; Rules 1962 r.3(1) as substituted by G.S.R. 615(E) (18-8-2023) | `DR 01` R12 `PERQ-ACCOM-*`; `DR 06` G1.1 step 3 | both years sourced (Gazette 271092 pp.1540–1541; CG-DL-E-18082023-248178) |
| W-071b | Medical treatment abroad and the medical-travel threshold *(D-110; re-scoped by D-112)*: S2 s.17(2)(f), s.17(3); Rules 2026 r.19 (₹8,00,000); 1961 s.17(2) proviso (vi), (vii) as amended by FA 2025 s.9(b); Rules 1962 r.3D (₹8,00,000, G.S.R. 555(E)); **creates** `salary.incomebeforetravel`, `salary.medicaltravel` | `DR 01` PERQ-MEDABROAD-001; `DR 06` G1.1 amendment | both years sourced (`sources/2026-09-28-perquisite-thresholds.md`) |
| W-071c | Motor car and other conveyances *(D-110)*: S4 r.15(3) Table II; Rules 1962 r.3(2); **and the specified employee** *(moved from W-071b by D-112)*: S2 s.17(1)(c), S4 r.17 (₹4,00,000); 1961 s.17(2)(iii), Rules 1962 r.3C (₹4,00,000, G.S.R. 555(E)) — the "income under the head Salaries by way of monetary payment" measure | `DR 01` PERQ-CAR-*, PERQ-SPECEMP-001 | both years sourced — TY 2025-26 r.3(2) in the Department's consolidated rule 3 (`UNVERIFIED`), `sources/2026-09-28-perquisite-thresholds.md` V2 |
| W-071a2 | The Cost Inflation Index cap for accommodation continued from an earlier year *(D-114; lifts D-111 C5a)*: S4 r.15(2)(d), (g); Rules 1962 r.3(1) third proviso and Explanation 2, as corrected by G.S.R. 636(E) (owned **or leased**); the index — 1961 s.48 Expl. (v) (S.O. 1790(E) table: 348, 363, 376) and 2025 Act s.72(8)(a) (S.O. 3889(E): 384) | `DR 01` PERQ-ACCOM-CAP-2026 | all four indices READ (`sources/2026-09-28-perquisite-thresholds.md` U1–U3, T4–T6) |
| W-071e | ~~Other benefits~~ **split by D-116** into W-071e1–e3 *(D-110)*: S4 r.15(5) Table IV (loans, travel, meals, gifts, credit card, club, movable assets …); Rules 1962 r.3(7) | `DR 01` PERQ-OTHER-2026, PERQ-LOAN-2026 | ⚠ r.3(7) for TY 2025-26 read only for (iii) |
| W-071e1 | Meals and gifts *(D-116)*: S4 r.15(5) Table IV Sl. 3 (₹200 per meal), Sl. 4 (nil below ₹15,000); Rules 1962 r.3(7)(iii) (fifty rupees; the new-regime voucher bar, G.S.R. 452(E)), (iv) (nil below five thousand rupees) | `DR 01` PERQ-MEAL-2026, PERQ-GIFT-2026, PERQ-GIFT-BOUNDARY-001 | both years sourced (Gazette 271092; consolidated rule 3 V4) |
| W-071e2 | Loans *(D-116)*: S4 r.15(5) Table IV Sl. 1 (SBI rate on 1 April, maximum outstanding monthly balance; nil for rule 18 diseases or ≤ ₹2,00,000); Rules 1962 r.3(7)(i) (rule 3A; twenty thousand rupees) | `DR 01` PERQ-LOAN-2026 | both years sourced; the SBI rate is entered (D-116 C3) |
| W-071e3 | Cost-based benefits *(D-116)*: S4 r.15(5) Table IV Sl. 2, 5–9 and (5)(b); Rules 1962 r.3(7)(ii), (v)–(ix) — holiday, credit card, club, use and transfer of movable assets, other benefits at arm's length (telephones excluded) | `DR 01` PERQ-CREDITCARD/CLUB/MOVABLE-2026, PERQ-PHONE-2026 | both years sourced |
| W-071e4 | Correct the Table IV page citations *(D-122)*: the 18 rows of W-071e1–e3 cite Gazette 271092 p.1546 for every serial; each to its own serial and page (Sl. 1–2 p.1545; Sl. 3 pp.1545–1546; Sl. 4–6 p.1546; Sl. 7 pp.1546–1547; Sl. 8–9, (5)(b) p.1547; r.15(8)(e) p.1548). No figure changes | — | read (Gazette text page by page) |
| W-071d | Services, utilities and education *(D-110)*: S4 r.15(4) Table III; Rules 1962 r.3(3)–(5) | `DR 01` PERQ-SERVANT/UTIL/EDU-2026 | ⚠ r.3 for TY 2025-26 not read |
| W-071f | Specified securities and sweat equity *(D-110)*: S2 s.17(1)(d), s.17(4); S4 r.15(6)–(7); 1961 s.17(2)(vi), r.3(8) | `DR 01` PERQ-ESOP-001, -002 | fair market value as the employer determines it |
| W-071g | Tax paid by the employer on a non-monetary perquisite *(D-110)*: S2 Sch. III Table Sl. 10; 1961 s.10(10CC) | `DR 01` PERQ-TAXPAID-001 | |
| W-073 | ~~`EmployerContributionNode`~~ **split by D-125** into W-073a–c | `DR 01` R13 | **four** independent charges, not one |
| W-073a | The aggregate employer contribution *(D-125)*: **creates** `EmployerContributionNode` (`employer.contrib.charges`, added to gross salary); S2 s.17(1)(h) (₹7,50,000); 1961 s.17(2)(vii) as substituted by FA 2020 s.13 | `DR 01` PF-AGGCAP-001 | both years read in the Gazette (265620 p.38; 218938 p.21) |
| W-073b | The accretion on the excess *(D-125)*: S2 s.17(1)(i), S4 r.16 (TP = (PC/2) × R + (PC1 + TP1) × R); 1961 s.17(2)(viia), Rules 1962 r.3B | `DR 01` PF-AGGCAP-002 | r.16 read in the Gazette (271092 pp.1548–1549); r.3B read in a third-party-hosted Gazette scan — TY 2025-26 `UNVERIFIED` (D-127) |
| W-073c | Recognised-fund employer contributions over 12% of salary and interest over the notified rate *(D-125)*: S2 Sch. XI Pt A para 6; 1961 Sch. IV Pt A r.6 | `DR 01` PF-RPF12-001 | r.6 and r.2(h) read (thc); the 12% limb TY 2025-26 only — FA 2026 s.127(a)(iii) (Gazette 271439 pp.70–71); the 9.5% (S.O. 1046(E)) via a third-party mirror, `UNVERIFIED` (D-151) |
| W-074 | ~~**T4 golden tests**~~ **split by D-128** into W-074a–b | `DR 08` T4-1 … T4-4 | |
| W-074a | **T4 golden tests, the built half** *(D-128)*: T4-1 and T4-2's ₹74,000 added (both years), T4-3's `PF-AGGCAP-001` nil (both years); T4-1/T4-2 enter a first-year fund history | `DR 08` T4-1 … T4-3 | TY 2025-26 runs PROVISIONAL |
| W-075 | ~~`deductions.json` both years~~ **split by D-129** into W-075a–d | `DR 01` R15 | |
| W-075a | Chapter VIII rows, the s.123 family and NPS, TY 2026-27 *(D-129)*: `DED-80C-001` (`AggregateWithCeiling` over the Schedule XV components, `-NPS-CAP-001` sub-cap), `DED-NPS-EMP-001`…`-003`, `DED-NPS-SELF-001`, `-002`, `DED-NPS-MINOR-001`, `DED-GTI-CAP-001`; the Schedule XV components | `DR 01` R13, R15; `DR 06` G2.2 | component list approved in the plan (C3a); OQ-14 |
| W-075a2 | Chapter VIII rows, the held Schedule XV clauses, TY 2026-27 *(D-130)*: (a) life insurance with para 2's share of the sum assured; (c) Government deferred annuity's 20% of salary; (j), (k), (l) and whether para 2 reaches them | `DR 01` R15 | ⚠ (c)'s "salary" and para 2's reach not settled by the words | **Narrowed by D-134 to (a); (c), (j), (k), (l) → W-075a3.**
| W-075b | Chapter VIII rows, 80D, TY 2026-27 *(D-129)*: `DED-80D-001`…`-011` | `DR 01` R15; `DR 06` G2.3 | |
| W-075c | ~~Chapter VIII rows, the rest~~ **split by D-137** into W-075c1–c6 | `DR 01` R15 | |
| W-075c1 | Chapter VIII, disability and treatment, TY 2026-27 *(D-137)*: s.127 (80DD), s.128 (80DDB, with (4)'s senior ₹1,00,000), s.154 (80U) | `DR 01` R15 | s.127's "up to" — fixed or capped at spending |
| W-075c2 | Chapter VIII, loan interest, TY 2026-27 *(D-137)*: s.129 (80E), s.130 (80EE), s.131 (80EEA), s.132 (80EEB) | `DR 01` R15 | the house-property bar on the same interest recorded for W-078 |
| W-075c3 | Chapter VIII, Agniveer Corpus Fund, TY 2026-27 *(D-137)*: s.125 | `DR 01` R15 | whether the Government's contribution is income first (cf. OQ-14) |
| W-075c4 | Chapter VIII, rent without HRA, TY 2026-27 *(D-137)*: s.134 (80GG), last among the deductions | `DR 01` R15 | ⚠ the "prescribed" conditions (Rules 2026) unread | **Erratum (W-075c4, 2026-10-07):** the ⚠ is cleared — S4 r.65 (p.1581) prescribes only the Form No. 31 declaration, no area limit (D-141). Verified by: Gazette CG-DL-E-20032026-271092 p.1581.
| W-075c6 | Chapter VIII, donations, TY 2026-27 *(D-137)*: s.133 (80G) | `DR 01` R15 | |
| W-075d | ~~Chapter VIII rows, TY 2025-26 versions~~ **split by D-143** into W-075d0–d5 *(D-129)* | `DR 01` R15 | ⚠ needs a sourcing round: 1961 Chapter VI-A as amended to AY 2026-27 |
| W-075d0 | Chapter VI-A sourcing round, TY 2025-26 *(D-143)*, docs only: Income-tax Act, 1961 ss.80A–80U and 80CCE as on thc.nic.in (current to Act 7 of 2019), every amendment to them up to AY 2026-27 in F(No.2)A 2019, TLA 2019, FA 2020, FA 2021, FA 2022, FA 2023, interim FA 2024, F(No.2)A 2024, FA 2025; Rules 1962 r.11B, r.11DD; `sources/2026-10-…-chapter-via.md` (READ / REPORTED) | `DR 01` R15 | ⚠ five of the Acts not located — the owner supplies them (C2a) |
| W-075d1 | Chapter VIII, TY 2025-26 *(D-143)*: 80C, 80CCC, 80CCD, 80CCE and the life-policy share — versions of the W-075a/a2 rows | `DR 01` R15 | needs W-075d0 | **Sourced (W-075d0, 2026-10-07):** `sources/2026-10-07-chapter-via.md` B4–B9, B20–B23, M1, M2, M8, M10, M11, M15, M17 (80CCD(2) 14% under s.115BAC(1A); 80CCD(1B) a minor's account from AY 2026-27).
| W-075d2 | Chapter VIII, TY 2025-26 *(D-143)*: 80D — versions of the W-075b rows | `DR 01` R15 | needs W-075d0 | **Sourced (W-075d0, 2026-10-07):** `sources/2026-10-07-chapter-via.md` B11–B13 (no amendment after 2019).
| W-075d3 | Chapter VIII, TY 2025-26 *(D-143)*: 80DD, 80DDB, 80U — versions of the W-075c1 rows | `DR 01` R15 | needs W-075d0 | **Sourced (W-075d0, 2026-10-07):** `sources/2026-10-07-chapter-via.md` B14, B15, B18, M9, R2 (80DD and 80U fixed sums — OQ-19's 1961 side).
| W-075d4 | Chapter VIII, TY 2025-26 *(D-143)*: 80E, 80EE, 80EEA, 80EEB — versions of the W-075c2 rows | `DR 01` R15 | needs W-075d0 | **Sourced (W-075d0, 2026-10-07):** `sources/2026-10-07-chapter-via.md` B16, B17, M3, M5, M7 (80EEA sanctioned to 31-3-2022).
| W-075d5 | Chapter VIII, TY 2025-26 *(D-143)*: 80CCH, 80GG (Rules 1962 r.11B), 80G — versions of the W-075c3, c4, c6 rows | `DR 01` R15 | needs W-075d0 | **Sourced (W-075d0, 2026-10-07):** `sources/2026-10-07-chapter-via.md` B10, B24–B27, M6, M12, M13, M16, R1 (80G's JNMF, IGMT, RGF omitted from AY 2024-25). **Lead (W-075d1, recalled, UNVERIFIED):** the Taxation and Other Laws (Relaxation and Amendment of Certain Provisions) Act, 2020 is said to have added PM CARES to 80G — not one of the nine Acts W-075d0 searched; locate and read before the 80G rows. **Resolved (W-075d5, 2026-10-07):** found at egazette 2020/222110, s.3(XII)(a) p.16 — PM CARES added to s.80G(2)(a)(iiia), whole, deemed from 1-4-2020 (sources M18).
| W-075a3 | Chapter VIII rows, Schedule XV (c), (j), (k), (l), TY 2026-27 *(D-134)* | `DR 01` R15 | OQ-16, OQ-17 answered (D-154); built for both years |
| W-076 | `ChapterEightNode` | `DR 06` G2.2, G2.3 | employee PF consumes 80C; 80D is **seven** rules | **Erratum (W-076, 2026-10-07):** the node was built across W-075a–d5 (D-131 onward); W-076 is the close-out — the coverage register (s.123 / 80C MODELLED, the rest re-pointed) and the brief's M-14 (D-149). Verified by: `ChapterEightNode.rules()` and `CoverageRegisterTest`.
| W-076a | Chapter VIII catalogue entries *(D-149)*: ss.122, 124, 125, 126 / 1961 80A, 80CCC, 80CCD, 80CCE, 80CCH, 80D — one `content/sections` entry per concept (SC-1–SC-4), then those sections `MODELLED` | `00-BRIEF` §6.3 | text from the rows' notes; no figures in text (SC-2) |
| W-076b | Chapter VIII catalogue entries *(D-149)*: ss.127–134, 154 / 1961 80DD, 80DDB, 80E, 80EE, 80EEA, 80EEB, 80G, 80GG, 80U — as W-076a | `00-BRIEF` §6.3 | as W-076a |
| W-077 | **T5 golden tests** | `DR 08` T5-1 … T5-4 | built (D-153): T5-1–T5-3 to Chapter VIII, both years; T5-4 and its variants; the no-evidence variant waits on Form 12BB |
| W-078 | ~~`houseproperty.json`, `HousePropertyNode`~~ **split by D-155** into W-078a–c | `DR 01` R16, `DR 06` G1.4 | occupancy drives **four** rules |
| W-078a | The head without interest *(D-155)*: `HouseProperty` input; annual value (S2 s.21(1)–(3), (6), (7); 1961 s.23); 30% (s.22(1)(a); 1961 s.24(a)); arrears (s.23; 1961 s.25A); co-owner's share (s.24; 1961 s.26); `HousePropertyNode` into gross total income | `DR 01` R16 | both years |
| W-078b | Interest *(D-155)*: S2 s.22(1)(b), (c), (2)–(6) with FA 2026 s.38; 1961 s.24(b) provisos and Explanation; the new regime's disallowance; the ss.130–132 bar (D-139); a loss refused until W-081 | `DR 01` R16 | pre-construction inside the cap in both years (plan W-078) |
| W-078c | Unrealised rent (S2 s.21(4); 1961 s.23(1) Explanation, r.4) after sourcing the Rule; stock-in-trade (s.21(5)) deferred with the business head *(D-155)* | `DR 01` R16 | ⚠ the Rule not read |
| W-078d | House-property catalogue entries *(D-158)*: S2 ss.20, 22–25 / 1961 ss.22, 24–27 — one `content/sections` entry per concept (SC-1–SC-4), then those sections `MODELLED`; S2 s.21 / 1961 s.23 also catalogued but kept `PLANNED` for s.21(5) / s.23(5) stock-in-trade, deferred with the business head | `00-BRIEF` §6.3 | as W-076a; coverage already points here (W-078c) |
| W-078e | Coverage hygiene *(D-159)*: the nine sections `PLANNED` against done items — 2025 Act ss.4, 5, 11, 13, 155 / 1961 ss.4, 5, 14, 87 (W-046, W-049, W-060, W-067) — each re-pointed to a live item or reclassified with its reason; and a build check that every `PLANNED` `backlogItem` names an item in this file that is not done or retired | `00-BRIEF` §6.3; `CoverageRegister` | the check would have caught W-078d's missing row and these nine |
| W-078e2 | Itemise the first-scope gaps *(D-161)*: the 42 sections `DEFERRED BUILT_FIRST` since W-045o / W-045o2 — 2025 ss.15, 157, 266, 392, 396, 403–410, 423–426, 428, 437 / 1961 ss.15, 89, 140A, 192, 198, 199, 207–219, 234A–234D, 234F, 244A (salary TDS, advance tax, self-assessment, credits, interest, the late-return fee, interest on refunds, arrears relief, the salaries charge) — each given a backlog item, new rows placed where none fits, and pointed at it; `CoverageBacklogTest` then refuses `BUILT_FIRST`; Appendix F gains the row `ArchitectureTest` cites for the pre-W-045a duplicate names (`DateRange`, `ExcelParser`) | STATE "Backlog gaps (W-045o, D-066)"; `Coverage.Deferred` | the gap tier exists only to be itemised |
| W-078f | Docs and data fixes *(D-159)*: `PERQ-EMPLOYER-CONTRIBUTION` TY 2025-26 `whatToCheck` (the interim FA 2024 was searched) and the 9.5% READ once the owner saves the India Code PDF of S.O. 1046(E); the `22-claim-catalogue.md` channel T erratum (s.133(6) reaches (1)(b)(ii) only, D-142); `00-BRIEF` SC-1 says "`MODELLED` or `PLANNED`" where the check is `MODELLED` only — settle at plan time (correct the brief, or extend the check) | `00-BRIEF` §6.3; `DR 22`; sources round 6 Z1 | the S.O. 1046(E) part waits on the owner's download | **Erratum (W-078f, 2026-10-09):** the S.O. 1046(E) part is not this item's — the 9.5% sits on the `UNVERIFIED` `PERQ-RPF-INTEREST` versions, which W-078j owns (D-164 C2a); the India Code link still times out from here. Verified by: `PERQ-RPF-INTEREST.json` provenance; sources `2026-09-28-perquisite-thresholds.md` Z1. |
| W-078g | `DIS-S202-2A` *(D-159)*: the new regime's s.202(2)(a) disallowance set — its basis row `REG-NEW-DISALLOW-001`, run-time application so a disallowed rule is **explained** as disallowed, `NEW_TAX_REGIME` referencing it, and the `ExceptIn NEW_TAX_REGIME` rows that rest on s.202(2)(a) moved onto it (1961 s.115BAC(2) for TY 2025-26) | `DR 01` R2 (`REG-NEW-DISALLOW-001`); `NEW_TAX_REGIME.json` `_pendingDisallowanceSets` | Appendix F's trigger (a second s.202(2)(a) rule) passed long ago — 53 rows cite it | **Erratum (W-078g, 2026-10-09):** split by D-165 C0(a) — W-078g builds the set on the timeline, its basis row, `NodeContext.inRegime` and the guard, and moves `SAL-TAX-ON-EMPLOYMENT` only; **W-078g2** moves the rest. The set is `DIS-NEW-TAX-REGIME-NO-EXEMPTION` and its basis `REG-NEW-TAX-REGIME-NO-EXEMPTION` (`REG-NEW-DISALLOW-001` renamed by D-069) — concept names (D-035; C2a). Verified by: `law/disallowances/DIS-NEW-TAX-REGIME-NO-EXEMPTION.json`; `RegimeVerdictTest`. |
| W-078g2 | The rest of the new regime's s.202(2)(a) set *(D-165)*: the 34 `ExceptIn NEW_TAX_REGIME` rows resting on S2 s.202(2)(a) / 1961 s.115BAC(2)(i) — 24 Chapter VIII `DED-*` rows; HRA, LTA concession, journeys, distance rate; CEA, hostel, uniform; `HP-OWN-RESIDENCE-INTEREST`, `HP-OWN-RESIDENCE-PRE-CONSTRUCTION` (TY 2025-26); `SAL-ENTMT-ALLOWANCE` (TY 2025-26) — each read against its clause, made `AllRegimes` and listed in the set's year versions; each node's nil line for a `Disallowed` rule says `RegimeVerdict.Disallowed.reason()` (C5a), and catalogue disqualifiers enforced by a moved row point at the basis; `SAL-STANDARD-DEDUCTION-ANY-OTHER-CASE`, `DED-NPS-EMPLOYER-OTHER` and `PERQ-MEAL-VOUCHER` (TY 2025-26) stay `ExceptIn` with the reason recorded | `DR 01` R2 (`REG-NEW-DISALLOW-001`); plans/W-078g.md finding 3 | every golden figure unchanged; the trace says why |
| W-078h | A notice channel for nodes *(D-159)*: `NodeResult` gains notices beside value and trace; first notice `SAL-HRA-006` — landlord PAN where rent paid in the year exceeds the S4 r.205(2) threshold (the TY 2025-26 counterpart in the Rules 1962 to be sourced in the item) | `DR 01` R9; Appendix F | a notice, never a figure | **Erratum (W-078h, 2026-10-09):** the notices travel as `TraceAnnotation`s addressing the node's own lines (`NodeResult.annotations`, renumbered into `PipelineRunner.Result.annotations`), not a free list beside the trace — `Notice`'s O-5 contract forbids a separate warnings collection; `SAL-HRA-006` is built as `SAL-HOUSE-RENT-LANDLORD-PAN`; the 1961 counterpart is r.26C, read (S.O. 1587(E)) (D-167). Verified by: `PipelineRunnerTest`, `NodeResultTest`, `ExemptionNodeTest`. |
| W-078i | Known-errors notice on the tax-report PDF *(D-159)*: `GET …/document/tax-report` prints the old engine's known-wrong figures without the notice the JSON endpoint carries (D-064 B1) — add it to the PDF template | D-063, D-064 | user-facing correctness warning | **Erratum (W-078i, 2026-10-09):** D-064's follow-up is built — `DocumentGenerationService` puts `KnownErrorsComputation.NOTICE` in the context and the template prints it under the header, before every figure (D-168). Verified by: `DocumentGenerationServiceTest`. |
| W-078j | Sourcing round for every `UNVERIFIED` version *(D-163)*, docs and rule-pack data: read each version's `whatToCheck` in a primary source (Gazette, India Code, thc.nic.in, dor.gov.in; the owner downloads incometaxindia.gov.in PDFs), upgrade it to `SOURCE_CHECKED` with the citation, or record why it stays; re-lock the touched packs; the goldens drop PROVISIONAL for each case whose versions are all checked. 103 versions in 81 files at `30dd8e09` — propose a split at plan stage | STATE "Rules used that are not fully verified"; each row's `whatToCheck` | lifts PROVISIONAL from the TY 2025-26 goldens | **Erratum (W-078f, 2026-10-09):** also reads S.O. 1046(E) (the 9.5% on `PERQ-RPF-INTEREST`), moved here from W-078f (D-164 C2a); the India Code copy (sources Z1) needs the owner's browser download. Verified by: D-164. | **Erratum (W-078h, 2026-10-09):** also `SAL-HOUSE-RENT-LANDLORD-PAN` TY 2025-26 — amendments to Rules 1962 r.26C / Form No. 12BB after 1-6-2016 (sources `2026-10-09-landlord-pan.md` L4; D-167). Verified by: D-167. | **Erratum (W-078j, 2026-10-10):** split by D-170 into W-078j1–j5 (below), in this row's place; the inventory at `7f23906a` is 104 markers in 82 timeline files plus the lock's `rules` row, grouped A–E in `plans/W-078j.md`. Verified by: `grep -c '"UNVERIFIED"'` over `rulepacks/timeline`. |
| W-078j1 | Sourcing, group A *(D-170)*: the 27 TY 2025-26 versions whose 1961 Act text is read in thc.nic.in (current to Act 7 of 2019) — read every Act of the chain (F(No.2)A 2019, TLA 2019, FA 2020, TOLA 2020, FA 2021, FA 2022, FA 2023, interim FA 2024, F(No.2)A 2024, FA 2025, TLA 2025, FA 2026) for each cited section; upgrade to `SOURCE_CHECKED` on the D-164 bar; narrow the group B rows' `whatToCheck` to their Rules half; relock | `plans/W-078j.md` group A; D-164 | an Act that changes a figure or condition stops the item (R13) |
| W-078j2 | Sourcing, group B r.3 *(D-170)*: the ~37 perquisite versions on Income-tax Rules, 1962 r.3 — its Gazette text since the 2009 substitution and every amending G.S.R. to AY 2026-27 | `plans/W-078j.md` group B | the owner's ChatGPT sourcing of the G.S.R. list first; each verified in egazette |
| W-078j3 | Sourcing, group B rest *(D-170)*: Rules 1962 r.2A, 2B, 3B, 4, 26C / Form No. 12BB (`SAL-HOUSE-RENT-LANDLORD-PAN`, D-167), r.21AGA / Form 10-IEA (the two election protocols), and the Rules' own notification (the lock's `rules` row) | `plans/W-078j.md` group B | |
| W-078j3b | The TY 2025-26 opt-out vehicle by business income *(D-174)*: Income-tax Rules, 1962 r.21AGA(1) (G.S.R. 452(E), 21-6-2023, egazette 2023/246719 p.6) — (a) Form No. 10-IEA on or before the s.139(1) due date for a person having income from business or profession, (b) in the return for one not having it; (2) withdrawal in Form No. 10-IEA. `FIRST_SCHEDULE_RATES` TY 2025-26 records `IN_THE_RETURN` for everyone and `ElectionProtocol` holds one vehicle per year: model the vehicle (and form) by business income, update the data, set its provenance | `plans/W-078j3.md` Revision 1; D-133, D-173 | a test that a business-income taxpayer is told Form No. 10-IEA by the s.139(1) due date and a salaried one the return |
| W-078j2a | Rule 3 from the Department's copies *(D-172)*: read Notification No. 94/2009 (S.O. 3245(E), 18-12-2009, the Income-tax (Thirteenth Amendment) Rules, 2009) and No. 2/2010 (S.O. 66(E), 12-1-2010, corrigendum) in the Income Tax Department's PDFs (the owner downloads them; incometaxindia.gov.in answers 403 here); compare every part of the 33 `UNVERIFIED` r.3 versions and Explanation (vi)(f) with them; upgrade to `SOURCE_CHECKED` per D-172, recording that the Gazette facsimile is still unread | `sources/2026-10-10-rule-3.md` R1, R2, E1; D-171, D-172 | a word that differs from the data stops the item (R13); lifts `SAL-SALARY-FOR-ACCOM` |
| W-078j4 | Sourcing, groups C and E *(D-170)*: S.O. 1046(E) of 2011 (the 9.5% on `PERQ-RPF-INTEREST`, D-164 C2a), the 1999 retrenchment-limit notification, the Direct Tax Laws (Amendment) Act, 1987's assent and Gazette, a CBDT clarification on the gratuity reading (D-107); record why the two TY 2026-27 r.280 versions stay (OQ-7) | `plans/W-078j.md` groups C, E | S.O. 1046(E) waits on the owner's India Code download |
| W-078j4b | Sourcing, group C rest *(D-181)*: S.O. 1046(E), 13-5-2011 (Notification No. 24/2011, the 9.5% on `PERQ-RPF-INTEREST` v0, v1 and head) from the owner's India Code download, and whether a later notification changed it; the 25-6-1999 notification of the ₹5,00,000 limit under 1961 s.10(10B) (`RET-RETRENCHMENT` v0, v1; F. No. 200/21/97-IT(A-I)); the Income-tax Rules, 2026 amendments after G.S.R. 198(E) touching r.279 / r.280 (`SAL-ALW-CONVEYANCE`, `SAL-ALW-TRANSPORT-DISABLED` v1); a CBDT clarification on the repealed labour Acts | `plans/W-078j4.md` C1a | the owner's downloads and a ChatGPT share (prompt in `plans/W-078j4.md`); an amendment that changes r.280's text is a data change (R13) |
| W-078j5 | Sourcing, group D *(D-170)*: the 14 `introducedBy` Gazette references marked "not read" — the original amending Acts (Act 14 of 2001, Act 28 of 2016 …) | `plans/W-078j.md` group D | may end in "not reachable", recorded per version |
| W-078j6 | Sourcing, the `introducedBy` precedents *(D-184)*: re-read under D-184 C1a (the instrument itself; `gazetteRef` filled; `notifiedOn` the instrument's own date, C2a) the 21 `introducedBy` blocks marked `SOURCE_CHECKED` with `gazetteRef` "not read" on the thc footnote alone (D-144) — DED-DISABILITY, -SEVERE, DED-DISABLED-DEPENDANT-SEVERE, DED-DONATION-CASH, -HALF, -QUALIFYING, DED-EDUCATION-LOAN, DED-HEALTH-CHECKUP, -FAMILY, -FAMILY-SENIOR, -PARENTS, -PARENTS-SENIOR, DED-HOUSE-LOAN-EARLIER, DED-PREMIUM-SHARE, DED-RENT-PAID, DED-SCHEDULE-XV, DED-SPECIFIED-DISEASE, -SENIOR, DED-WITHIN-GROSS-TOTAL, RATE-ROUND-PAYABLE, RATE-ROUND-TOTAL-INCOME (all TY 2025-26 v0); **and (D-187)** the Gazette of India issue of 1961 that published the Income-tax Act, 1961 — for `SAL-SALARY-FOR-RPF` TY 2025-26 `introducedBy` (left `UNVERIFIED` by W-078j5) and the 2025-26 lock's `act` instrument `gazetteRef` ("not read" since W-052c) | `plans/W-078j5.md` C3 | an instrument not reachable goes back to `UNVERIFIED` with the reason, as in W-078j5 | **Erratum (W-078j6, 2026-10-16):** split by D-188 into W-078j6a and W-078j6b (below), in this row's place; inventory and chains in `plans/W-078j6.md`. Verified by: the parse of `rulepacks/timeline` at `4ceb9f60` (21 blocks `SOURCE_CHECKED` with `gazetteRef` "not read"). |
| W-078j6a | `introducedBy` precedents, the 2012–2018 Acts *(D-188)*: read FA 2012 (23 of 2012), FA 2013 (17 of 2013), F(No.2)A 2014 (25 of 2014), FA 2015 (20 of 2015), FA 2017 (7 of 2017) and FA 2018 (13 of 2018) in egazette (FA 2016 already read, 2016/169739) for the 14 TY 2025-26 v0 blocks DED-DISABILITY, -SEVERE, DED-DISABLED-DEPENDANT-SEVERE, DED-DONATION-CASH, DED-HEALTH-CHECKUP, -FAMILY, -FAMILY-SENIOR, -PARENTS, -PARENTS-SENIOR, DED-HOUSE-LOAN-EARLIER, DED-PREMIUM-SHARE, DED-RENT-PAID, DED-SCHEDULE-XV, DED-SPECIFIED-DISEASE-SENIOR; `gazetteRef`, `notifiedOn` (the last Act's date), `sourceUrl`; every Act of a chain read or the block goes `UNVERIFIED` (C1a) | `plans/W-078j6.md` C3a | a different section, words or w.e.f. date → R13 |
| W-078j6b | `introducedBy` precedents, the 1966–2007 Acts and the 1961 Gazette *(D-188)*: FA 1966, 1967, 1968, 1976, 1985, 1995, 2000, 2003, 2005, 2007, Act 29 of 2006 and DTLA 1987 s.25 for DED-DONATION-HALF, DED-DONATION-QUALIFYING, DED-EDUCATION-LOAN, DED-SPECIFIED-DISEASE, DED-WITHIN-GROSS-TOTAL, RATE-ROUND-PAYABLE, RATE-ROUND-TOTAL-INCOME (C2a: `effectiveFrom` corrected to the cited w.e.f. — 13-7-2006 and 1-4-1969); the 1961 Gazette of India issue publishing Act 43 of 1961 for `SAL-SALARY-FOR-RPF` and the 2025-26 lock's `act` `gazetteRef` (D-187) | `plans/W-078j6.md` | the owner asked once for what is unreachable (C4a) |
| W-078j7 | The r.280 watch *(D-187)*: re-walk the Income-tax Rules, 2026 amendments (from G.S.R. 871(E), 8-10-2026, onward) and CBDT circulars and clarifications for r.279 / r.280. If r.280(3)'s "option under section 202(4)" or r.280(2) Table Sl. 10 ("Metro Cities", "dearness allowance thereon", OQ-7) is amended or clarified, re-read `SAL-ALW-CONVEYANCE` and `SAL-ALW-TRANSPORT-DISABLED` TY 2026-27 (the D-101 / D-102 readings) and move them off `UNVERIFIED`; otherwise record the walk and the date | `sources/2026-10-11-groups-c-e.md` (the walk to 8-10-2026); D-181 C4a | a status change only if the text changes; a changed reading is a rule change → its own plan | **Erratum (W-078j6b, 2026-10-16, D-190):** also, each time it runs, re-search for the four instruments that are not online: FA 1976 (66 of 1976) s.17 (`DED-DONATION-HALF`), FA 1995 (22 of 1995) s.16 (`DED-DONATION-QUALIFYING`), FA 1966 (13 of 1966) s.34 and FA 1968 (19 of 1968) s.30 (`RATE-ROUND-TOTAL-INCOME`), and the 1961 Gazette of India issue publishing Act 43 of 1961 (`SAL-SALARY-FOR-RPF` and the 2025-26 lock's `act`); the places already searched are in `sources/2026-10-16-introduced-by-precedents.md`. Verified by: `grep -rc '"UNVERIFIED"' rulepacks/timeline` (6: these four, plus the r.280 pair). |
| W-078k | The tax-report PDF's failure handling *(D-169)*: `DocumentGenerationService.convertHtmlToPdf` catches `Exception` and rethrows `RuntimeException` — catch only what Flying Saucer and iText throw, and say what failed (CLAUDE.md standard 2, "Nothing silent": no `catch (Exception|RuntimeException)`) | W-078i (STATE notes) | a test that a rendering failure is reported, not swallowed |
| W-079 | `othersources.json`, `OtherSourcesNode` | `DR 01` R17 | dividend deduction is **nil** from TY 2026-27; **also `PF-EEINT-001`** — interest on the employee's own PF contributions over ₹2,50,000 / ₹5,00,000 (Sch. II Sl. 3–4; 1961 s.10(11)–(12) provisos, r.9D), moved here by D-125 | **Erratum (W-079, 2026-10-16):** split by D-193 into W-079b–W-079f (below), in this row's place; lottery and similar winnings refused (1961 s.115BB is `DEFERRED` / `BUILT_LATER` in coverage). Verified by: `plans/W-079.md`. |
| W-079b | Other sources, the head *(D-193)*: the input, `OtherSourcesNode` (`othersources.income`) in both pipelines and `GrossTotalIncomeNode`; interest (OS-INT-001) and dividends (OS-DIV-001) with OS-DIV-DED-2025 (20%, interest only) / OS-DIV-DED-2026 (nil, FA 2026 s.44(b)), OS-COLL-001, OS-GENERAL-001, OS-NODED-001; TY 2025-26 sourced to D-170 C2a; winnings refused (coverage s.115BB DEFERRED); a dividend case reaching the 25% surcharge band refused until W-079e; coverage ss.92–95 / 56–59 re-pointed | `DR 01` R17; `plans/W-079.md` | |
| W-075c5 | Chapter VIII, deposit interest, TY 2026-27 *(D-137)*: s.153 (80TTA / 80TTB) | `DR 01` R15 | after W-079 — needs interest under Other Sources | **Erratum (W-075c5, 2026-10-16):** the row says TY 2026-27 only, but W-075d / d0 / d5 and the coverage register (1961 80TTA, 80TTB → W-075c5) hand it the 1961 sections too — built for both years (D-195). Verified by: `plans/W-075d.md` l.23; `content/coverage.json`.
| W-079c | Other sources: family pension (OS-FAMPEN-001/002: one-third, capped ₹25,000 new / ₹15,000 other), gratuity on death (OS-GRATUITY-DEATH-001), commutation of a Sch. VII Sl. 3 pension (OS-COMMUT-001) *(D-193)* | `DR 01` R17 | TY 2025-26 to D-170 C2a |
| W-079d | `PF-EEINT-001` *(D-125, D-193)*: interest on the employee's own PF contributions made on or after 1-4-2021 above ₹5,00,000 (no employer contribution) / ₹2,50,000 — S2 Sch. II Sl. 3(a), 4(a); 1961 s.10(11)–(12) provisos; the r.9D computation, to be sourced in both years | `DR 01` PF-EEINT-001 | may end BLOCKED on r.9D |
| W-079e | The surcharge split *(D-086, D-193)*: dividend income out of the 25%/37% test's excluding income in `SurchargeNode`; lifts W-079b's interim refusal | `DR 06` G3; WI l.1543 | `RATE-SUR-SPLIT-001`'s 15% cap stays W-088's |
| W-079f | Other sources: gifts (OS-GIFT-001) *(D-193)*: sums and property received for no or inadequate consideration above ₹50,000 (property: the higher of ₹50,000 or 10% of consideration), with the relatives, marriage, inheritance and other exclusions | `DR 01` R17 | |
| W-079g | Other sources, the remaining s.92(2) clauses *(D-194)*: employee contributions received (2025 Act s.92(2)(c); 1961 s.56(2)(ic)), keyman policy sums ((d)), letting machinery, plant or furniture and inseparable buildings ((f), (g)), forfeited advances ((h)), termination compensation ((j)), business-trust specified sums ((k)), life-policy sums ((l)), and any other residual s.92(1) / s.56(1) income — refused by W-079b until built | `DR 01` R17; Gazette 265620 pp.138–139 | each clause with its deduction (s.93(1)(b), (c)) | **Erratum (W-079g, 2026-10-17):** split by D-202 into W-079g1–W-079g3 (below), in this row's place; (c), (f), (g) and s.95 refused as input, their deductions being the business head's (S2 ss.28, 29, 33, 38; 1961 ss.28, 32, 36 `DEFERRED` / `BUILT_LATER` in coverage), as winnings are (D-193). Verified by: `plans/W-079g.md`. |
| W-079g1 | Other sources, the clauses received in full *(D-202)*: Keyman policy sums (S2 s.92(2)(d); 1961 s.56(2)(iv)), forfeited advances ((h); (ix)), termination compensation ((j); (xi)), business-trust specified sums ((k); (xii)) and the residual s.92(1) / s.56(1) income, each with s.93(1)(e) / s.57(iii) expenditure; (c), (f), (g) refused (D-202 C2a); (l) refused, naming W-079g2 | `DR 01` R17; Gazette 265620 pp.138–139 | (j) against salary's profits in lieu (S2 s.17) and `RET-RETRENCHMENT*` read first |
| W-079g2 | Other sources, life-policy sums *(D-202)*: S2 s.92(2)(l) with Schedule II Sl. 2's exclusion test (premium-to-sum-assured ratios by issue period; the ₹2,50,000 / ₹5,00,000 aggregate premium limits) and S4 r.59's computation; 1961 s.56(2)(xiii) with s.10(10D) and its Rules 1962 rule, to source | `DR 01` R17; Gazette 265620 pp.139, 498–499; 271092 p.1578 | Schedule II Sl. 2 is built nowhere yet |
| W-079g2b | Other sources, several capped life policies *(D-205)*: a person holding more than one ULIP issued on or after 1-2-2021, or more than one other policy issued on or after 1-4-2023 — the exemption reaches only *"those"* whose aggregate premium stays within ₹2,50,000 / ₹5,00,000 (S2 Schedule II Sl. 2 col. C (a); 1961 s.10(10D) fifth and seventh provisos), chosen under the Board's guidelines (S2 Schedule II Sl. 2 Note; the 1961 provisos' guidelines), to source — refused by W-079g2 until built | `DR 01` R17; Gazette 265620 p.499 | the guidelines are on incometaxindia.gov.in (403 here) |
| W-079g3 | Other sources, coverage close-out *(D-202)*: S2 ss.92–95 and 1961 ss.56–59 classified, `MODELLED` with their section-catalogue entries (SC-1; two 1961 entries where one 2025 section has two counterparts), the business-linked clauses named as refused with the business head | `00-BRIEF.md` §6.3; D-042 | content only |
| W-074b | **T4 golden tests, the rest** *(D-128)*: T4-1/T4-2 deductions, T4-3's ₹30,000, T4-4 | `DR 08` T4-1 … T4-4 | needs `W-073c`, `W-076`, `W-079`; OQ-14; T4-4's premise (r.9D now read). placed after `W-079` by the owner |
| W-079a | Startup wiring *(D-159; D-054)*: the running application loads the timeline from the JAR, compiles each year against its lock and serves the packs — today the engine runs only from tests | D-054; `DR 07` X2 (packs loaded and verified at startup) | owned at last; before the income-head milestone |
| W-080 | **T6 golden tests** | `DR 08` T6-1 … T6-7 | |
| W-080a | `SAL-LTA-CHILD-001` *(D-159)*: LTA for no more than two surviving children (born before 1-10-1998 and multiple births after one child excepted; S4 r.278(5)–(6); 1961 r.2B counterpart, to source) — per-person fares on each `Journey`, both years | Appendix F | the catalogue marks it `NotModelled` until then |
| W-078j7b | The r.280 watch, next run *(D-191)*: the W-078j7 scope from 16-10-2026 onward — scan egazette from 2026/277213 (all pages; prove on G.S.R. 871(E), 2026/276956) and read the Department's Latest News list (incometax.gov.in) for Income-tax Rules, 2026 amendments and CBDT clarifications touching r.279 / r.280 (`SAL-ALW-CONVEYANCE`, `SAL-ALW-TRANSPORT-DISABLED` TY 2026-27; D-101, D-102, OQ-7); and re-search FA 1966 s.34, FA 1968 s.30, FA 1976 s.17, FA 1995 s.16 and the 1961 Gazette of India (`RATE-ROUND-TOTAL-INCOME`, `DED-DONATION-HALF`, `DED-DONATION-QUALIFYING`, `SAL-SALARY-FOR-RPF`, the lock's `act`; D-190) | `sources/2026-10-16-r280-watch.md`; `sources/2026-10-16-introduced-by-precedents.md` | a changed r.280 reading is a rule change → its own plan; a found Gazette → the D-188 bar | **Erratum (W-079b, 2026-10-16, D-194):** the 1961 Gazette re-search also serves the TY 2025-26 `introducedBy` of `OS-INTEREST` (s.56(1)), `OS-DIVIDEND` (s.56(2)(i)) and `OS-OTHER-EXPENDITURE` (s.57(iii)) — original 1961 text, `UNVERIFIED` until it is found. Verified by: `grep -c '"UNVERIFIED"' law/rules/OS-*.json`. |
| W-078j7b2 | `DED-DONATION-QUALIFYING` TY 2025-26 `introducedBy` *(D-214)*: the Finance Act, 1995 s.16 read in the thc.nic.in copy (Gazette of India, Extraordinary, Part II s.1, No. 52, 27-5-1995; an OCR re-typeset, accepted like D-185) — the chain's last unread Act, so the version goes `SOURCE_CHECKED` under D-188 | D-188, D-214; `sources/2026-10-17-r280-watch.md` | UNVERIFIED 10 → 9; the relock changes this rule only |
| W-078j7b3 | `SAL-ALW-CONVEYANCE`, `SAL-ALW-TRANSPORT-DISABLED` TY 2026-27 *(D-216)*: the owner rules r.280's as-built readings settled (D-101, D-102) — both versions `SOURCE_CHECKED`, the text's defect kept in the provenance, D-181 C4a reversed | D-216; `sources/2026-10-17-r280-watch.md` §6 | UNVERIFIED 2 → 0; W-078j7c reopens them if r.280 is amended or clarified otherwise |

> **Erratum (W-078d, 2026-10-08):** the W-078d row keeps S2 s.21 / 1961 s.23 `PLANNED` for stock-in-trade — `PLANNED`
> must name a live item and none builds the business head (its sections are `DEFERRED BUILT_LATER`), so it would point
> at a done item — they are `MODELLED`, with s.21(5) / s.23(5) a `NotModelled` disqualifier in `SEC-ANNUAL-VALUE` (D-160 C2(a)).
> Verified by: `Coverage.Planned` (backlogItem required); `content/coverage.json` 2025 ss.26–30 `DEFERRED`; `SectionCatalogueTest`.

---

## Phase 9 — Set-off, elections, capital gains (9 items)

| Item | Produces | Spec | ⚠ |
|---|---|---|---|
| W-081 | `setoff.json`, `SetOffNode` | `DR 01` R18 | absorption order is **declared and traced** (`DR 13` R-7); **also (D-159):** remove `LedgerSnapshot.preConstructionInstalmentsClaimed` — the instalments are fixed by tax year (S2 s.22(1)(c), W-078b) and nothing reads it; **also (D-211):** run `DR 08` T6-2, T6-4, T6-5 and T6-3's TY 2026-27 half (−₹40,000, D-156) as golden tests — each a loss under the head, refused until set-off **Split (D-217):** → W-081a, W-081b. |
| W-081a | Current-year set-off *(D-217)*: `SETOFF-ACROSS-HEADS` (S2 s.109(1); 1961 s.71(1), (2); `AbsorptionOrder` declared, OQ-28), `SETOFF-HOUSE-PROPERTY` (s.109(1)(b); s.71(3A); ₹2,00,000), `DIS-NEW-TAX-REGIME-NO-SETOFF` (s.202(2)(b)(ii), (3); s.115BAC(2)(ii)), `SetOffNode` before `gti`; house property and other sources return losses; `DR 08` T6-2, T6-4, T6-5, T6-3 TY 2026-27 (D-211) | `DR 01` R18; `DR 13` R-7 | the unabsorbed loss is reported, not yet carried (W-081b) |
| W-081b | Carry-forward *(D-217)*: the ledger redesign (per year of origin: loss arisen, absorbed since, whether from a deduction the new regime disallows), S2 s.110 / 1961 s.71B eight years against house-property income, s.202(2)(b)(i) and (3) for brought-forward loss, the year's carried amount as a result; remove `LedgerSnapshot.preConstructionInstalmentsClaimed` (D-159); coverage S2 ss.108–110 / 1961 ss.70–71B → MODELLED with catalogue entries, and classify the rest of the chapter it also holds — S2 ss.111–121 / 1961 ss.72–80 (capital-gains and business carry-forward, amalgamation and the like: with W-085/W-088 or deferred with the business head) | `DR 01` R18; D-159 | needs W-081a **Erratum (D-218):** the coverage close-out moves to W-081c; the carried amount stays in the trace (no result field). **Note (D-218), for the unowned ledger persistence (M3.4; invariant I8):** next year's `CarriedHousePropertyLoss` entries are read from this year's `SETOFF-*` lines — the current-year MEASURE *"carried forward"* (the new loss, with its attributable part from the house-property node's own-residence interest lines) and, per year of origin, the SET_OFF / MEASURE lines (absorbed, destroyed, expired). |
| W-081c | The set-off chapter's coverage close-out *(D-218)*: S2 ss.108–110 / 1961 ss.70, 71, 71B `MODELLED` with catalogue entries (`SEC-SET-OFF-WITHIN-HEAD`, `SEC-SET-OFF-ACROSS-HEADS`, `SEC-HOUSE-PROPERTY-LOSS-CARRIED`); S2 ss.111–121 / 1961 ss.71A, 72–80 classified (capital gains with W-088, business with the business head, s.71A not applicable, s.120–121 / s.80 as they fall) — the 31 pointers | `CoverageRegisterTest`, `SectionCatalogueTest` (SC-1 to SC-3) | content only |
| W-082 | `Election` family, `ElectionResolver` | `DR 15` C2 | priced by **recomputation**; record all alternatives **also (D-217):** OQ-28 — whether the assessee may choose the s.109(1)(b) absorption order **also (D-218):** OQ-29 — the order of brought-forward house-property loss **Split (D-220):** → W-082a, W-082b, W-082c, W-082d (after W-099); `HouseElection` → W-083. |
| W-082a | The election core and the set-off order *(D-220)*: `Election<T>` (sealed, `SetOffOrderElection` only), `ElectionMode`, `ElectionOutcome<T>`, `PricedAlternative<T>`, `ElectionResolver` (a whole `PipelineRunner.run` per candidate, compared on `tax.netpayable`, every alternative a MEASURE line, a tie to the declared default); `EnteredFacts.absorptionOrder`, read by `SetOffNode` over the row's `AbsorptionOrder`; the stated brought-forward order if OQ-29 allows it (stated only, never optimised) | `DR 15` C2; `DR 13` R-7; OQ-28, OQ-29 | sourcing OQ-28 / OQ-29 first; if OQ-28 is ruled no choice, `RegimeElection` instead and W-082b folds in |
| W-082b | The regime election *(D-220)*: `RegimeElection` — the regimes the year's pack offers this assessee, read against `RegimeElectionSnapshot` and each regime's `ElectionProtocol` (`DR 17` L3 `eligibleRegimes`, `RegimeOffer` with the reason a regime is barred); priced by one run per regime | `DR 15` C2; `DR 17` L3 | needs W-082a |
| W-082c | The children and gratuity-aggregate elections *(D-220)*: `ChildElection` (CEA / hostel), `AggregateAllocationElection` (`RET-GRATUITY-AGGREGATE`) — each built only where the model holds a choice that changes the tax, otherwise closed by a D-row with the reason (today the allowances are entered as counts against one limit per child) | `DR 15` C2 | needs W-082a **Closed (D-223):** neither choice exists in the model — no code; finding the two-employer gratuity defect, W-066e placed next |
| W-066e | Two employers' gratuity or leave encashment in one year *(D-223)*: today `RetirementNode.paid` sums `GRATUITY` / `LEAVE_ENCASHMENT` across employments and `NotifiedRetirementLimits.employmentOf` measures the sum on the first employment's terms and one separation — first a test written to fail today and a loud refusal; then, in its own plan, each payment measured against its own employment and separation (S2 s.19(1) Table Sl. 6, 14; 1961 s.10(10)(iii), (10AA)(ii)), A − B shared between them, and the split priced by recomputation if the Act leaves it open (`AggregateAllocationElection`, `DR 15` C2) | `DR 01` R14; D-106; D-223 | a silent wrong figure today (CLAUDE.md standard 2) **Split (D-224):** W-066e is the refusal (all six capped entries); the fix → W-066f, W-066g |
| W-066f | Other gratuity and other leave encashment from more than one employer *(D-224)*: `RetirementPayment` names the employment that paid it (one payment per component per employment); each payment measured on its own employment's terms, separation or leave figures (S2 s.19(1) Table Sl. 6, 14; 1961 s.10(10)(iii), (10AA)(ii)); the year's total capped at A − B (S2 s.19(2)(a), (f); 1961 provisos — "from one or more than one employer in the same tax year … the aggregate amount of deduction shall not exceed"); the split of the cap cannot change the tax (same head), so no election (D-223); lifts W-066e's refusal for these two entries | `DR 01` R14; D-106; D-224 | ~50 test call sites gain the employment |
| W-066g | The other capped retirement entries from more than one employer *(D-224)*: commuted pension from another employer (S2 s.19(1) Table Sl. 8; 1961 s.10(10A)(ii)), voluntary retirement (Sl. 12; s.10(10C)), statutory gratuity and retrenchment (D-109) — each to source for a second employer in the year; and whether a commutation's "gratuity also received" limb reads the same employer's gratuity (today every employment's `GRATUITY`); lifts W-066e's refusal for these four | `DR 01` R14; D-103, D-109; D-224 | needs W-066f's per-employment payment |
| W-083 | `HouseElectionNode` | `DR 13` R-6 | which two houses is a **choice**, and optimisable **also (D-220):** the `HouseElection` variant of `Election` (W-082a), and `ElectionOutcome` as a `NodeValue` variant |
| W-075c5b | Deposit interest net of its expenditure *(D-228, OQ-25)*: s.153 (1961 ss.80TTA, 80TTB) worked on the interest net of the expenditure deducted against it under S2 s.93(1)(e) / 1961 s.57(iii) — the amount gross total income includes; lifts D-195 C2a's refusal of a deposit-interest item with an expense where s.153 applies | `DR 01` R15; D-195; D-228 | the `DepositInterestDeduction` reads the head's net interest per item |
| W-080a2 | LTA per-kilometre journeys with an uncovered child *(D-228, OQ-27)*: Rules 2026 r.278(1)(iii)(B)'s ₹30 per kilometre is for the distance of the journey, so a child the two-children limit leaves out takes no share of it; lifts D-212 C6a's refusal | `DR 01` R10; D-212; D-228 | |
| W-080a3 | Rename the test-only `GoldenCaseRunner.SalaryRun` *(D-228)*: it holds a run of the shipped pipeline to a slot and whether any line cites an `UNVERIFIED` version, for salary, house-property and other-sources golden cases alike — a name stating that, its 12 uses updated; no behaviour change | CLAUDE.md standard 3; `plans/W-080.md` | test code only |
| W-079e2 | The TY 2025-26 interest allowance shared by dividends and units *(D-229, OQ-26 Q2)*: where the 1961 s.57 proviso's 20% limit binds over interest claimed against both, the allowed interest is attributed to dividend and unit income in proportion to each's income, giving the dividend income taken out of the surcharge's 25%/37% test; lifts D-200 C3a's refusal | `DR 06` G3; D-200; D-229 | TY 2025-26 only (nil from TY 2026-27, S2 s.93(2)) |
| W-084 | `IncrementalRunner` | `DR 15` B5 | memo only where the graph proves nothing upstream moved **Closed (D-235):** no code — no input read is a graph edge, so the graph cannot prove what an input change reaches; the need placed as W-143a |
| W-084a | Several elections resolved together *(D-230)*: the product of the choices of the elections a computation carries (regime, house specification, set-off order), priced jointly on `IncrementalRunner`; today each is resolved alone, which is not jointly optimal (the regime changes what a house choice is worth) | `DR 15` C2, B5; D-230 | needs W-084 **Erratum (D-235):** priced jointly on whole runs — W-084 closed without code; no longer needs it |
| W-085 | `CapitalGainsSummary`, `CapitalGainsBucket`, `RateBasisHint` | `DR 13` R-10, `DR 15` F5 | gross buckets; **no tax amounts** **Built (D-237):** `portfolio/dto/` records — one bucket per classification, asset type and sub-class (`gainRupees`, `TMoney`-canonical), no brought-forward loss (the tax ledger's, W-088); W-086 builds it from `trade_outcomes` |

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
| W-086 | `portfolio` published read-model | `DR 07` X1.3 | modulith test must pass with the new dependency **Built (D-238):** `CapitalGainsSummaryService.summaryFor(email, financialYear)` over `trade_outcomes` by sell date; gain = sale − adjusted cost − deductible charges; a row with charges and no breakdown refuses; the dependency was already declared and no `taxplanning` class imports `portfolio` yet — the first import is W-087's adapter |
| W-086a | One financial-year form in `trade_outcomes` *(D-238)*: `TradeMatchingService.deriveFinancialYear` writes `FY2025-26` where the rest of `portfolio` writes `TLocalDate.financialYear` (`2025-2026`) — use `TLocalDate.financialYear`, and a migration rewrites the `FY…` rows | D-238 | `portfolio` code; W-086 selects by sell date meanwhile **Built (D-239):** `TLocalDate.financialYear` in `TradeMatchingService`; `TradeOutcomeFinancialYearMigration` + its startup runner rewrite the `FY…` rows from their sell dates |
| W-087 | `MoneyBridge` | `DR 19` E1 | `toTMoney` **throws** — one-way seam **also (D-238):** `CapitalGainsAdapter` in `taxplanning.portfolio` — calls `CapitalGainsSummaryService.summaryFor` and maps the buckets to `CapitalGainsInput` through the bridge (the first `taxplanning` → `portfolio` import, so the modulith check meets it here) **Split (D-240):** the adapter → W-087b; W-087 is the bridge, refusing a non-canonical double |
| W-087b | `CapitalGainsAdapter` *(D-238, D-240)*: in `taxplanning.portfolio`, calls `CapitalGainsSummaryService.summaryFor` and maps each bucket through `MoneyBridge` to `CapitalGainsInput.Bucket` — the (classification, asset type, sub-class) → (`Term`, `RateBasisHint`) mapping sourced first (S2 ss.196–198; 1961 ss.111A, 112, 112A; the STT condition, equity-oriented and s.50AA funds); exposes `RateBasisHint` (a named interface or a move, D-048); decides `SPECULATIVE`, `NOT_CAPITAL_GAINS` and where `UNCLASSIFIED` is refused | `DR 13` R-10; `DR 07` X1.3; D-238, D-240 | source first; the first `taxplanning` → `portfolio` import **Built (D-241):** sourced (`sources/2026-10-18-capital-gains-basis.md`); `RateBasisHint` → `taxengine.input`; buckets carry STT on each side; OQ-31 raised |
| W-088 | `CapitalGainsNode` | `DR 13` R-10 | `UNCLASSIFIED` → 409; `SPECULATIVE` excluded **Erratum (D-241):** both are refused upstream by `CapitalGainsAdapter` (W-087b), as a 400 until W-105's `TaxError` gives the 409; the node receives only buckets with a term and a basis. **also (D-241):** OQ-31 (acquisitions without STT that s.112A(4) / s.198(5) notify) **Split (D-242):** → W-088a – W-088f |

> **Erratum (W-056, 2026-09-26, D-086):** `W-088` also takes the surcharge split: `RATE-SUR-SPLIT-001` (the
> 15% limit on the tax attributable to dividends and the special gains, as pack rows), feeding the special
> gains into the `-EXCLUDING` ladders' income, lifting `SurchargeNode`'s refusal of special-rate tax, and
> `DR 08` T2-6.

> **Erratum (W-079e, 2026-10-17, D-200):** for dividend income the limit needs *"the amount of income-tax computed in
> respect of that part of income"*, and no text read gives that tax where dividends are taxed at slab rates — **OQ-26**.
> W-079e took dividends out of the 25%/37% test and refuses, naming OQ-26 and `W-088`, a case whose income excluding
> dividends still reaches that ladder; `W-088` builds the dividend slice of the limit on OQ-26's answer.
> Verified by: FA 2026 Part I-A Para F Table 1 Sl. 1 (vi), p.83; `DR 06` G3 ("specialTax = tax on the dividend …
> slices", no method); `SurchargeNode`.

> **Erratum (D-229, 2026-10-18):** OQ-26 is answered — `W-088` builds the dividend slice as the pro-rata share (the slab tax ×
> dividend income ÷ aggregate income, as the Department's ITR-2 utility computes it, `V300`), and also answers **OQ-30** (whether unit
> distributions count as dividend income for the limit) before it is planned.
> Verified by: `sources/2026-10-18-dividend-surcharge-attribution.md` sources 8–9.

| W-088a | `CapitalGainsNode` → `capitalgains.income` *(D-242)*: `CapitalGainsInput` buckets netted within the head by term (`SETOFF-INTRA-CG-001`: S2 s.108(2)(a), (b); 1961 s.70(2), (3)), no capital loss set off against another head (`SETOFF-CG-001`: S2 s.109(2); 1961 s.71(3)); the result carries the surviving gain per rate basis; wired into the set-off / gross-total-income chain | `DR 13` R-10; `DR 15` F5; D-242 | source S2 s.108, s.109 first **Built (D-243):** `SETOFF-WITHIN-CAPITAL-GAINS` (a declared order; OQ-32); `CAPITAL_GAINS` last in `SETOFF-ACROSS-HEADS`; `HeadsAfterSetOff`; special-rate parts refused at `gti` until W-088b; W-088's 65 coverage pointers re-pointed |
| W-088b | Special-rate tax *(D-242)*: rate rows for S2 ss.196, 197, 198 and 1961 ss.111A, 112, 112A (TY 2025-26 on FA (No. 2) 2024's post-23-7-2024 rates); the ₹1,25,000 of s.198 / s.112A; the unabsorbed basic exemption shifted onto the gains (S2 s.196(2), s.197(2), s.198(3)); Chapter VIII not allowed against them (s.196(4), s.197(5), s.198(6)); `TaxByBasisNode` produces the special-rate parts | `sources/2026-10-18-capital-gains-basis.md`; D-242 | touches `ChapterEightNode` **Built (D-244):** rows `RATE-CG-SHORT-LISTED-EQUITY`, `-LONG-OTHER`, `-LONG-LISTED-EQUITY`, `RATE-CG-SHORTFALL-ORDER` (the ITR-2 utility's order); Chapter VIII from GTI less the gains; `gti`'s refusal removed — `SurchargeNode` (W-088c) and `RebateNode` (W-089) still refuse special-rate tax; OQ-33 raised; coverage ss.196–198 / 111A, 112, 112A `MODELLED` |
| W-088c | The surcharge split *(D-086, D-200, D-229, D-242)*: `RATE-SUR-SPLIT-001` (the 15% limit on the tax on dividends and the special gains), special gains out of the `-EXCLUDING` ladders, the dividend slice pro-rata (D-229 C1), both `SurchargeNode` refusals lifted, `DR 08` T2-6 | D-086, D-229; D-242 | OQ-30 answered first **Built (D-246):** OQ-30 answered first (D-245: unit income not dividend); rows `RATE-SUR-NEW-TAX-REGIME-LIMIT`, `RATE-SUR-FIRST-SCHEDULE-LIMIT`; T2-6's shape tested in `SurchargeNodeTest` (S1) |
| W-088d | Capital-loss carry-forward *(D-242)*: S2 s.111 / 1961 s.74 (`SETOFF-CF-CG-001`) — eight tax years, a long-term loss only against long-term gains — the ledger as W-081b built it for house property | `DR 01` R18; D-242 | **Built (D-247):** `CarriedCapitalLoss` in the ledger (by term, with the S2 s.121 / 1961 s.80 return condition); `SETOFF-CARRIED-CAPITAL-GAINS`; `CapitalGainsInput.broughtForward` removed; S2 s.111 / 1961 s.74 `MODELLED` |
| W-088e | The capital-gains chapter's coverage close-out *(D-242)*: the 57 coverage pointers re-pointed to it from W-088 by W-088a (D-243; the rates' 6 to W-088b, s.111 / s.74 to W-088d) classified with catalogue entries, as W-081c did — `MODELLED` where a node computes, `portfolio`-quantified provisions as such, the reinvestment exemptions (S2 ss.82–91; 1961 ss.54–54H) deferred or placed in a row the owner places | `CoverageRegisterTest`, `SectionCatalogueTest`; D-242 | content only **Built (D-248):** S2 s.67 / 1961 s.45 `MODELLED` (`SEC-CAPITAL-GAINS-CHARGE`); s.13, s.91 / s.14, s.55A `NOT_COMPUTATIONAL`; s.86 / s.54F `PLANNED` → W-088i; the computation sections and the other exemptions `DEFERRED BUILT_SECOND`; business-asset sections `DEFERRED BUILT_LATER`; 1961 ss.54E, 54EA, 54EB, 54ED, 54EE `OUT_OF_SCOPE` (spent) |
| W-088f | OQ-31 answered *(D-241, D-242)*: the notification under 1961 s.112A(4) / S2 s.198(5) read, and `CapitalGainsAdapter`'s refusal of long-term equity bought without STT replaced by its answer | D-241; D-242 | waits on OQ-31 **Built (D-249):** OQ-31 answered by S.O. 5054(E) (egazette 2018/190360; S2 s.536(2)(j)), recorded in the sources; `portfolio` publishes facts only — `trade_outcomes.acquisition_mode` / `acquisition_corporate_action` from all three writers, the bucket keyed on `acquisition` and `acquiredOn`; no law in code (Revision 2): the no-STT-on-buy case stays refused, naming the facts and W-088f2; F1 → W-088j |
| W-088m | Rules chosen by facts in data, not by Java *(D-250; review E1)*: rows declare what they apply to as conditions on facts and a node evaluates a rule *family* — no hard-wired category → `RuleId` maps (`MotorCarPerquisite`, `PerquisiteNode`, `RetirementNode`, `AssetPerquisite`, `NotifiedRetirementLimits`, `TaxByBasisNode`) or per-regime row sets (`SurchargeRows`); a new regime, column or rate becomes data | `reviews/2026-10-19-branch-extendability-review.md` | foundation for W-088n, W-088o, W-088f2 **Split (D-251):** → W-088m1 – m3 |
| W-088m1 | Rule families *(D-251)*: each rule row names its `family`; `law/families/*.json` state the selection (`EXACTLY_ONE`, `ALL_THAT_APPLY`, `FIRST_THAT_APPLIES` in a stated order); nodes declare families; `NodeContext.family(id)` returns the applicable rows by the existing regime applicability and conditions; reachability (I3) counts members; the validator refuses an unread family and an orphan row; behaviour unchanged, relocked | `plans/W-088m.md`; D-251 | first **Built (D-252):** 166 sets in `law/rule-sets.json` (9 of several rows); nodes name `RuleSetIds` only (no `RuleIds` left in `taxengine.node`); the six regime/person sites on set selection; `AT_MOST_ONE` and `SelectionGround` added; the item-keyed maps remain W-088m2's; relocked law 2025-26.73 / 2026-27.77 |
| W-088m2 | Conditions on the item *(D-251)*: item facts (codes and quantities) the node supplies for the item it computes; `ItemFactIs`, `ItemFactAtMost`, `ItemFactAtLeast`; the six category → rule maps (`MotorCarPerquisite`, `PerquisiteNode`, `RetirementNode`, `NotifiedRetirementLimits`, `AssetPerquisite`, `TaxByBasisNode`) become families whose rows state their item conditions | `plans/W-088m.md`; D-251 | after W-088m1 **Built (D-253):** `ItemFactIs` (the 14th predicate) over `ItemFacts` the node supplies; `NodeContext.ruleOf` / `optionalRuleOf` with an item; seven maps → seven sets plus `RS-DED-NPS-EMPLOYER` on regime and conditions, 32 rows' item conditions, 166 → 143 sets; `ItemFactAtMost` / `ItemFactAtLeast` left to W-088n (C1a, no caller yet); relocked law 2025-26.74 / 2026-27.78 |
| W-088m3 | The guard *(D-251)*: an ArchUnit rule — `taxengine.node` reaches rules only through families (no `RuleIds.*`), baseline that may only shrink, empty after W-088m2 | `plans/W-088m.md`; D-251 | after W-088m2 **Built (D-254):** `ArchitectureTest.nodes_reach_rules_through_rule_sets` (no `RuleIds`, no `new RuleId(String)`) and `nodes_hold_no_map_to_a_rule_set` (source scan, simple or qualified `RuleSetId`); no baseline (none owed, C1a); the elections → W-088m4 |
| W-088m4 | Elections name rule sets *(D-254)*: `HouseElection` and `SetOffOrderElection` name `RuleIds.HP_OWN_RESIDENCE` / `SETOFF_ACROSS_HEADS` directly (`election/HouseElection.java:35, 46–47`; `SetOffOrderElection.java:31, 42–43`); they read their `RS-…` sets, `Election.basis` returns the chosen row, and `nodes_reach_rules_through_rule_sets` widens to `taxengine.election` | `plans/W-088m3.md` C2a; D-254 | after W-088m3 **Built (D-255):** `Election.basis(choice, rules)` / `candidates(rules)` over the election's node's `NodeContext`, which `ElectionResolver` builds; the two elections read `RS-HP-OWN-RESIDENCE` / `RS-SETOFF-ACROSS-HEADS`; `nodes_and_elections_reach_rules_through_rule_sets`; no `RuleIds` left in `taxengine.node` or `taxengine.election` |
| W-088n | Input facts, not legal bands *(D-250; review E2)*: `CarEngine` → engine capacity and whether electric; `PopulationBand` → the place's population; the 1.6-litre column and the census bands as rule data | `reviews/2026-10-19-branch-extendability-review.md` | after W-088m **Split (D-256):** → W-088n1 – n2 |
| W-088n1 | The car's facts *(D-256)*: `CarEngine` → whether electric and the capacity in cc; `ItemFacts` quantities and `ItemFactAtMost` (inclusive; "exceeds" is `Not`); the six car rows state the 1.6-litre column and the electric one as conditions | `plans/W-088n.md`; D-256 | first **Built (D-257):** `ItemFactAtMost`; `ItemFacts.quantities`; `CarEngine(electric, cubicCapacityCc)` with `ofCapacity` / `electricVehicle`; the car rows on `vehicle.engine.electric` / `vehicle.engine.cc`; a non-electric car no row covers is refused as a pack defect; relocked law 2025-26.75 / 2026-27.79 |
| W-088n2 | The city's population *(D-256)*: `EmployerAccommodation.cityPopulation` (as the Table's census counts it) in place of `PopulationBand`; a `PercentOfBaseByPopulation` cap shape holds Table I Sl. 2(a)'s bands on `PERQ-ACCOM-OWNED` (same row id) | `plans/W-088n.md`; D-256 | after W-088n1 **Built (D-258):** `PercentOfBaseByPopulation` (the ninth cap shape); `EmployerAccommodation.cityPopulation`; `PopulationBand` deleted; `PERQ-ACCOM-OWNED` on 40,00,000 → 10%, 15,00,000 → 7.5%, else 5%, "exceeding" strict; relocked law 2025-26.76 / 2026-27.80 |
| W-088o | Rate bases as rule data *(D-250; review E3)*: `RateBasisHint`, `CapitalGainsPart`, `Term` no longer closed sets — a new special rate is data | `reviews/2026-10-19-branch-extendability-review.md` | after W-088m; with W-088f2, W-088k |
| W-088p | Missing types for long parameter lists *(D-250; review S1)*: a trace-line specification replacing the 6–7-parameter `line(...)` helpers, a set-off context for `SetOffNode` / `CarriedCapitalLossSetOff`; 39 methods over 5 parameters | `reviews/2026-10-19-branch-extendability-review.md` |  |
| W-088q | One carried-loss mechanism *(D-250; review S2)*: the house-property and capital-loss carry-forwards as one mechanism parameterised by rule data (heads absorbable, span, return condition) | `reviews/2026-10-19-branch-extendability-review.md` | before any third carried loss |
| W-088r | No null across a boundary *(D-250; review S3)*: `RuleVersion.cap` → `Optional<CapShape>`; `RuleJson.capOrNull` and the four null checks removed | `reviews/2026-10-19-branch-extendability-review.md` |  |
| W-088s | Shipped years as a manifest *(D-250; review E6)*: `TaxEngineConfig.SHIPPED_YEARS` read from a resource beside the locks, still declared | `reviews/2026-10-19-branch-extendability-review.md` |  |
| W-088t | One tax-year derivation *(D-250; review E7)*: `HousePropertyInterest:203`'s inline April rule replaced by one `TaxYearId` method used everywhere | `reviews/2026-10-19-branch-extendability-review.md` |  |
| W-088u | Size and SRP *(D-250; review S4)*: `OtherSourcesNode`, `ShapeJson`, `ChapterEightNode` split per concern; methods over 40 lines (incl. the `portfolio` matching methods the branch extended) | `reviews/2026-10-19-branch-extendability-review.md` |  |
| W-088v | Enforce the written limits *(D-250; review S5)*: Checkstyle `MethodLength` 40 and `ParameterNumber` 5, with a baseline that may only shrink | `reviews/2026-10-19-branch-extendability-review.md` | after W-088p, W-088u |
| W-088f2 | The capital-gains rate basis as rule data *(D-249)*: S2 s.196 / s.198 and 1961 s.111A / s.112A's STT conditions and S.O. 5054(E)'s waiver (acquisition-date windows and qualifying acquisition kinds, any number, per tax year) as rule-pack rows evaluated in the engine from the bucket's facts; `CapitalGainsInput.Bucket` carries the facts (acquisition date, how acquired, STT evidence) in place of the adapter's `RateBasisHint`; the adapter's Java mapping (D-241) removed | `plans/W-088f.md` Revision 2; D-241 | after W-088f; no law in code |
| W-088j | STT evidence in three states *(D-249)*: `portfolio`'s read-model says whether securities transaction tax was paid, not paid, or is unknown (a `trade_outcomes` row with no charge breakdown), on the buy and on the sale; `CapitalGainsAdapter` refuses an unknown state instead of mapping it to `SLAB` / `SPECIAL_RATE_OTHER` (W-088f's F1: short-term equity sold with nil recorded charges is taxed at slab rates, silently) | `plans/W-088f.md` F1; D-241 | after W-088f |
| W-088k | The term decided on the tax side *(D-249)*: `portfolio` publishes acquisition and transfer dates and the asset facts; short-term or long-term is decided from tax-year-versioned holding-period rule data in the engine, not by `portfolio`'s `HoldingPeriodService` (F2; revisits D-237 / R-10's split) | `plans/W-088f.md` Revision 1 F2 | after W-088j |
| W-088l | Repo-wide audit: law in code and rule-derived fields *(D-249)* — every constant, `if`, closed enum mapping or field that encodes a legal boundary or qualifying list outside versioned rule data (engine, `portfolio`, `brokercharges`, `taxplanning`); each finding a placed row. **W-088m2 adds (D-253), computation choices by item still in Java:** `RetirementNode`'s commutation, voluntary-retirement and statutory paths by entry; `NotifiedRetirementLimits.context` / `capped` by entry; `AssetPerquisite`'s straight-line or reducing balance by kind; `MotorCarPerquisite`'s Sl. 1(c)(i)/(ii) column and several-cars proviso; `PerquisiteNode`'s continued-accommodation cap for owned or leased only (`:159`); D-252's club and disability branches | owner, W-088f review ("everything should be extendable") | after W-088k |
| W-088g | Land or building acquired before 23-7-2024 *(D-244, D-245)*: S2 s.197(3) / 1961 s.112(1)(a) second proviso — a resident individual or HUF's tax on the gain limited to 20% of the gain on indexed cost; needs an asset kind (and the indexed gain) on `CapitalGainsInput`, which `portfolio` does not yet send | `sources/2026-10-18-capital-gains-basis.md` rows 17, 19; D-245 | unreachable until a non-equity asset is mapped |
| W-088h | A non-resident's unlisted securities *(D-244, D-245)*: S2 s.197(4) / 1961 s.112(1)(c)(iii) — the gain computed without s.72(6) / the s.48 provisos, at the s.197 rate | `sources/2026-10-18-capital-gains-basis.md` rows 17, 19; D-245 | unreachable until unlisted securities are mapped |
| W-088i | The house-reinvestment exemption *(D-248)*: S2 s.86 / 1961 s.54F — a long-term capital gain on an asset other than a residential house, reinvested in one, not charged in proportion to the amount reinvested; the new house's cost and dates as entered facts; reachable now, for `portfolio`'s long-term equity gains | D-248 | source S2 s.86 / 1961 s.54F first |
| W-089 | **T13-1 test** — rebate with special-rate income | `DR 13` R-5 | **the most dangerous interaction in the design** |
| W-089a | When salary is taxed — due, paid in advance, or arrears — and relief for arrears *(D-162)*: S2 s.15, s.157 with Rules 2026 r.73 / Form No. 39; 1961 s.15, s.89 with r.21A / Form 10E (`REL-ARREARS-*`) | `DR 01` R19 | the relief needs the years the salary belongs to |
| W-089b | `CreditsNode` *(D-162)*: tax already paid, credited — TDS, advance tax, self-assessment tax: S2 s.266, s.396, s.410; 1961 s.140A, s.198, s.199, s.219; produces `credits` for `NetPayableNode` | TS T6 `credits`; `DR 01` R20, R23 | the node TS T6 names and no item owned |
| W-089c | Advance tax *(D-162)*: who must pay, how much, the instalments — S2 ss.403–409; 1961 ss.207–211, 218 | `DR 01` R23 | before W-112's interest on its default |
| W-089d | Salary TDS *(D-162)*: the employer's estimate and deduction — S2 s.392; 1961 s.192 (`TDS-SAL-*`) | `DR 01` R20, R21 | Form No. 130 fields per R21 |
| W-078j7c | The r.280 watch, next run *(D-213)*: the W-078j7b scope from 17-10-2026 onward — scan egazette from 2026/277272 (all pages; prove on G.S.R. 871(E), 2026/276956; ranged GETs, HEAD is unreliable there) and read the Department's Latest News list for Income-tax Rules, 2026 amendments and CBDT clarifications touching r.279 / r.280 (`SAL-ALW-CONVEYANCE`, `SAL-ALW-TRANSPORT-DISABLED` TY 2026-27; D-101, D-102, OQ-7); re-search FA 1966 s.34, FA 1968 s.30, FA 1976 s.17, FA 1995 s.16 and the 1961 Gazette of India (`RATE-ROUND-TOTAL-INCOME`, `DED-DONATION-HALF`, `DED-DONATION-QUALIFYING`, `SAL-SALARY-FOR-RPF`, `OS-INTEREST`, `OS-DIVIDEND`, `OS-OTHER-EXPENDITURE`, `OS-RESIDUAL`, the lock's `act`; D-190) | `sources/2026-10-17-r280-watch.md` | a changed r.280 reading is a rule change → its own plan; a found Gazette → the D-188 bar; placed at the end of Phase 9, a watch per phase (D-213 C3a) **Erratum (W-078j7b2, 2026-10-17, D-215):** the old-instrument re-search is done — FA 1966, 1968, 1976, 1985 and the 1961 Act read in the annual *Acts of the Parliament of India* volumes, and FA 1995 in the thc.nic.in copy (D-214), so seven of the rows named here are `SOURCE_CHECKED`; what is left is the r.280 watch and the lock's `act` Gazette issue reference. |

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
| W-082d | The regime election history *(D-220)*: `RegimeElectionHistory` (per profile and regime, append-only, `stateAsAt(TaxYearId)`) and `ElectionEvent` (`DR 17` L3) in `taxplanning` — the source W-103 projects into `RegimeElectionSnapshot`; moved from W-082 (Appendix A) because it holds `ProfileId` (W-096) and `DraftId` (W-099) | `DR 17` L3 | before W-103 |
| W-082e | The s.202(4)(a) binding, history-aware regime offers *(D-222)*: `DR 17` L3's state branches — IN_EFFECT carried into later years, WITHDRAWN and the withdrawals used (`RegimeElectionSnapshot.withdrawalsUsedFor`), PERMANENTLY_INELIGIBLE with `AfterWithdrawal.IneligibleUnless` revival and its route, LAPSED with `ContinuationPolicy`; `RegimeOffer` gains `Continuing` / `Lapsed`; lifts W-082b's refusal of a regime whose binding applies | `DR 17` L3; `01-rulebook.md` R2 `REG-OPTOUT-YEARLY-001` | needs W-082d's history and, to be reachable, income under the business head |
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
| W-112 | **modifies** `FilingPosition` (created by `W-043`; adds its interest component) + **creates** `InterestAndFeeNode` | `DR 15` C1 | —; **also (D-162):** the sections it computes — S2 ss.423–426, 428, 437; 1961 ss.214–217, 234A–234D, 234F, 244A |
| W-113 | `ForecastProjection` + endpoint | `DR 20` F29 | **F29** |
| W-114 | `CalendarProjection` + endpoint | `DR 20` F30 | **F30** |
| W-115 | `CompletenessProjection` + endpoint | `DR 20` F33 | **F33** |
| W-116 | `content/explanations.json` + renderer | `DR 20` F37 | **F37** |
| W-117 | `content/claim-catalogue.json` (12 entries) | `DR 22` C5 | **F40** |
| W-118 | claim-playbook endpoint | `DR 22` C4 | **F40** |
| W-119 | claim integrity: S / E / D + statutory overshoot | `DR 12` C2, C3.1 | **F23, F24**; **also (D-159):** Form No. 12BB evidence (`TDS-SAL-12BB-001/002`) and `DR 08` T5-4's no-evidence variant, which W-077 left waiting on it |
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
| W-143a | `IncrementalRunner` with declared input reads *(D-235)*: every node declares the `EngineInputs` parts it reads, enforced by `NodeContext` as `rules()` is, so the graph can prove what an input change reaches; then re-evaluate only what it reaches (`DR 15` B5) — built only if W-143's benchmarks of N1–N3 or of W-084a's joint pricing miss their budget, else closed by a D-row with the measurement | `DR 15` B5; D-235 | needs W-143's measurement |
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
| `CapitalGainsSummary`, `CapitalGainsBucket` | `W-085` | named in prose only **Built (D-237)** |
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
| `IncrementalRunner` | `W-084` | named in prose only **Moved (D-235):** → `W-143a` |
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
| `HeadResult`, `ComponentSet`, `ElectionOutcome`, `InterestBreakdown` | `NodeValue` (`DR 15` B4) | the node that produces each — `HeadResult` with the first head set-off reads (house property, Phase 9; `salary.income` is a `MoneyValue`, D-096), `ComponentSet` at `W-060`, `ElectionOutcome` at `W-082`, `InterestBreakdown` at `W-112` |
| `RegimeElection`, `HouseElection`, `ChildElection`, `SetOffOrderElection`, `AggregateAllocationElection` | `Election` (`DR 15` C2) | **`W-082`** — the phase table names only the family |
| `OccupationPeriods` | `ProrationBasis`-adjacent (`DR 15` C3) | **`W-067`** — not a basis: HRA's occupied windows, each measured by `Days` (D-097) |
| `MissingRulePackException`, `UnknownComponentCodeException`, `StatutoryOvershootException`, `NoApplicableItrFormException`, `UndeterminedApportionmentException`, `BlockingDataQualityException` | the `TaxEngineException` hierarchy (`DR 23` A7) | **`W-105`** — which names `TaxError` and `TaxEngineException` and none of the six |

> **Erratum (W-082, 2026-10-18, D-220):** `ElectionOutcome` is not a `NodeValue` at W-082 — W-082a's resolver runs outside the graph (whole-pipeline recomputation; `Recomputation` cannot serve an election read before `gti`), so it is a plain record; it becomes a `NodeValue` variant at **W-083**, whose `elections.houses` node is the first to produce one. The variants: `SetOffOrderElection` W-082a, `RegimeElection` W-082b, `ChildElection` / `AggregateAllocationElection` W-082c, `HouseElection` W-083; `ElectionEvent`, `RegimeElectionHistory` (Appendix A) W-082d.
> Verified by: `plans/W-082.md` findings 2–3; `PipelineRunner.java` `RunRecomputation` (seeds reads from the run's values so far).

> **Erratum (W-082c, 2026-10-18, D-223):** `ChildElection` and `AggregateAllocationElection` are not built — neither choice exists in the model (counts against one per-child limit; one other gratuity a year against a fixed B); the gratuity allocation returns with W-066e if two gratuities in one year leave a choice.
> Verified by: `plans/W-082c.md` findings 1–2; `AllowanceExemption.java` l.157–163; `RetirementNode.java` l.111–119.

> **Erratum (W-083, 2026-10-18, D-230):** W-083 builds no `HouseElectionNode` and no `elections.houses` slot, and `ElectionOutcome` does not become a `NodeValue` there: the house choice is read by `houseproperty.income`, early in the graph, so it is resolved outside the graph like the other elections (`HouseElection`, an `Election` variant); `ElectionOutcome`'s `NodeValue` variant waits for a node that produces one, which no item now plans. This also corrects W-083's row (*"`HouseElectionNode`"*) and D-220's note on it.
> Verified by: `plans/W-083.md` findings 1–2; `PipelineRunner.java` `RunRecomputation`.

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
| `DIS-S202-2A` — the new regime's s.202(2)(a) disallowance set, its basis row (`REG-NEW-DISALLOW-001`) and run-time application, so a disallowed rule is *explained* as disallowed; until then `SAL-TAX-ON-EMPLOYMENT` is excluded by `ExceptIn` (D-093) | the first item that explains a regime difference by rule, or that authors a second s.202(2)(a) rule — owner to place | `NEW_TAX_REGIME.json` `_pendingDisallowanceSets` **Placed as `W-078g` (D-159).** **Built by `W-078g` (D-165)** for professional tax; the other rows in `W-078g2`. |
| `SAL-HRA-006` — landlord PAN where aggregate rent paid in the year exceeds ₹1,00,000 (S4 r.205(2) Table Sl. 1): a notice, not a figure; nodes have no notice channel (`NodeResult` is value and trace) (D-099) | the first item that gives nodes a notice channel | `ExemptionNode` has the rent figures **Placed as `W-078h` (D-159).** **Built by `W-078h` (D-167)** as `SAL-HOUSE-RENT-LANDLORD-PAN`, an annotation on the first HRA line. |
| `SAL-LTA-CHILD-001` — LTA is not available for more than two surviving children (except children born before 1-10-1998 and multiple births after one child; S4 r.278(5)–(6)): needs a per-person fare on each journey; until then a family with three or more later-born children is over-allowed (D-100) | the item that adds per-person journey fares, before any LTA figure is filed | `SEC-LEAVE-TRAVEL-CONCESSION` marks it `NotModelled` **Placed as `W-080a` (D-159).** **Built by `W-080a` (D-212):** `SAL-LEAVE-TRAVEL-CHILDREN`, `Journey.travellers`; the catalogue entry is `ByRule`; per-kilometre journeys carrying a child beyond the limit are refused pending OQ-27 **Built by `W-080a2` (D-232):** the per-kilometre case, the rate whole — the child takes no share (D-228) |
| CBDT guidance, if any, on whether the new regime also disallows s.22(1)(c) for an own residence — W-078b followed the text (s.202(2)(a)(v) names s.22(1)(b) only; D-156 C1(a)); the owner downloads, incometaxindia.gov.in being blocked here (D-159) | `W-080` (the T6 goldens fix TY 2026-27 house-property figures) | if a text says otherwise, one version of `HP-OWN-RESIDENCE-PRE-CONSTRUCTION` changes |
| The pre-W-045a duplicate simple names `DateRange` (`shared/dto`, `taxengine/rules/temporal`) and `ExcelParser` (`shared/util/parser`, `portfolio/service/parser/model`) — `ArchitectureTest.DUPLICATE_NAMES_BEFORE_W045A`, which may only shrink (D-162) | `W-146` | the extensibility claim needs ArchUnit clean without exemptions |
| Section catalogue entries for every modelled section | each item that models a section — standing rule 2 | SC-1 ratchet |
| The rule-authoring admin screen (**OQ-2**) | after `W-111` | files + PR review suffice until an API and persistence exist |
| Dividend income in the surcharge's **excluding** income and in the 15%-limited part (`RATE-SUR-SPLIT-001`), D-086 | `W-079` (`OtherSourcesNode`) | dividends are slab-taxed, so nothing else would take them out of the 25%/37% test |

> **Erratum (W-079e, 2026-10-17, D-200):** only the first half landed with `W-079`'s split — `W-079e` takes dividend
> income (S2 s.2(40), deemed dividends included; Mutual Fund unit income is not dividend) out of the excluding income.
> The 15%-limited part for dividends waits on **OQ-26** and is `W-088`'s, with the special gains (D-086).
> Verified by: `SurchargeNode`; `plans/W-079e.md`.

| The surcharge split, special gains in the excluding income, T2-6 (D-086) | `W-088` | `SurchargeNode` refuses special-rate tax until then |
| `DR 19` E4.1 `aDeductionNeverIncreasesTax` (D-088) | `W-076` (`ChapterEightNode`) | the first node that takes a deduction |
| `DR 19` E4.1 `splittingAComponentIsNeutral` (D-088) | `W-060`/`W-061` (component partition, gross salary) | the first nodes that read components |
| `DR 19` E4.1 `proratingAFullYearIsIdentity` (D-088) | `W-066b` (`TemporallyApportionedCap`) | the proration it tests is built there |
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
