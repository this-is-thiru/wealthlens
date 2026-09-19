# STATE — the only mutable file

**Read this first, write it last, every session.**

```
Last checkpoint           : W-018 — "taxplanning W-018: sub-cap first, then the ceiling"
Last updated              : 2026-09-19 (session 2 — W-018 DONE)
Current position          : W-001
Session count             : 2
```

**"Last checkpoint"** records the **item id and the commit subject** of the last accepted item — not
a hash. A commit cannot contain its own hash, so storing one here is self-referential and was found
to be unworkable in session 1.

Prompt **P1** compares this line against the most recent **item** commit:

```
git log --oneline --grep='^taxplanning W-' -1
```

- the subject **matches** → the tree is where the protocol left it. Proceed.
- the subject **differs** → an item was committed outside the protocol. Stop, report
  `git log --oneline -10`, and wait.

Commits that do **not** start `taxplanning W-` — documentation, tooling, unrelated repository work —
are ignored by the check. Only item commits move the checkpoint.

This survives amends, rebases and cherry-picks, which a hash does not.

---

## Current position

| | |
|---|---|
| **Next item** | `W-019` — `PerUnitPerPeriod` + test (closes the sealed family) |
| **Status** | `NOT_STARTED` |
| **Phase** | 2 — Cap shapes and conditions |
| **Next milestone** | `W-053` — a correct slab-and-rebate calculator, no database |

---

## Status board

Legend: `·` not started · `E` explained, awaiting approval · `P` in progress ·
`R` awaiting review · `D` done · `B` blocked

```
Phase 0    W-001 D  W-002 D  W-003 D   ← COMPLETE
Phase 1    W-004 D  W-005 D  W-006 D  W-007 D  W-008 D  W-009 D  W-010 D  W-011 D  W-012 D   ← COMPLETE  W-006 ·  W-007 ·  W-008 ·  W-009 ·  W-010 ·  W-011 ·  W-012 ·
Phase 2    W-013 ·  W-014 ·  W-015 ·  W-016 ·  W-017 ·  W-018 ·  W-019 ·  W-020 ·  W-021 ·  W-022 ·
Phase 3    W-023 ·  W-024 ·  W-025 ·  W-026 ·  W-027 ·  W-028 ·  W-029 ·  W-030 ·  W-031 ·  W-032 ·  W-033 ·  W-034 ·
Phase 4    W-035 ·  W-036 ·  W-037 ·  W-038 ·  W-039 ·  W-040 ·  W-041 ·  W-042 ·  W-043 ·  W-044 ·  W-045 ·
Phase 5    W-046 ·  W-047 ·  W-048 ·  W-049 ·  W-050 ·  W-051 ·  W-052 ·  W-053 ·
Phase 6    W-054 ·  W-055 ·  W-056 ·  W-057 ·  W-058 ·
Phase 7    W-059 ·  W-060 ·  W-061 ·  W-062 ·  W-063 ·  W-064 ·  W-065 ·  W-066 ·  W-067 ·  W-068 ·  W-069 ·  W-070 ·
Phase 8    W-071 ·  W-072 ·  W-073 ·  W-074 ·  W-075 ·  W-076 ·  W-077 ·  W-078 ·  W-079 ·  W-080 ·
Phase 9    W-081 ·  W-082 ·  W-083 ·  W-084 ·  W-085 ·  W-086 ·  W-087 ·  W-088 ·  W-089 ·
Phase 10   W-090 ·  W-091 ·  W-092 ·  W-093 ·  W-094 ·  W-095 ·
Phase 11   W-096 ·  W-097 ·  W-098 ·  W-099 ·  W-100 ·  W-101 ·  W-102 ·  W-103 ·  W-104 ·  W-105 ·  W-106 ·  W-107 ·  W-108 ·  W-109 ·  W-110 ·  W-111 ·
Phase 12   W-112 ·  W-113 ·  W-114 ·  W-115 ·  W-116 ·  W-117 ·  W-118 ·  W-119 ·  W-120 ·  W-121 ·  W-122 ·  W-123 ·
Phase 13   W-124 ·  W-125 ·  W-126 ·  W-127 ·  W-128 ·  W-129 ·  W-130 ·  W-131 ·  W-132 ·  W-133 ·
Phase 14   W-134 ·  W-135 ·  W-136 ·  W-137 ·  W-138 ·  W-139 ·  W-140 ·  W-141 ·  W-142 ·  W-143 ·  W-144 ·  W-145 ·  W-146 ·
```

Counts — done **12** / 146 · in flight **0** · blocked **0**

---

## Completed items

<!-- One line per DONE item, appended in order. Never edit an existing line.
     Format:  W-0nn | YYYY-MM-DD | <one-line summary> | <verify command that passed> -->

W-001 | 2026-09-19 | Four modulith modules declared, all Type.CLOSED; taxplanning OPEN→CLOSED | `./mvnw test -pl backend -Dtest=WealthLensModulithTest`
W-002 | 2026-09-19 | Money ArchUnit rule amended for ..taxengine.money..; 2 guards added (9→11 rules) | `./mvnw test -pl backend -Dtest=ArchitectureTest`
W-003 | 2026-09-19 | rulepacks resource directory created — Phase 0 complete | `test -d backend/src/main/resources/rulepacks`
W-004 | 2026-09-19 | 4 engine-purity rules + the withdrawal gate (11→16 tests) | `./mvnw test -pl backend -Dtest=ArchitectureTest`
W-005 | 2026-09-19 | Money — BigDecimal, scale 2, s.516 rounding. No test yet (W-006) | `./mvnw test -pl backend -Dtest=ArchitectureTest`
W-006 | 2026-09-19 | MoneyTest — 17 tests incl. the 1,234.99→1,230 discriminator | `./mvnw test -pl backend -Dtest=MoneyTest`
W-007 | 2026-09-19 | Rate + RateTest — 9 tests; cess case sourced from T2-1 | `./mvnw test -pl backend -Dtest=RateTest`
W-008 | 2026-09-19 | 7 identifier records + shared validator; 2 spec bugs found | `./mvnw test -pl backend -Dtest=IdentifierTest`
W-009 | 2026-09-19 | IdentifierTest — 73 tests, incl. both erratum regressions | `./mvnw test -pl backend -Dtest=IdentifierTest`
W-010 | 2026-09-19 | Provenance, Instrument, GoverningInstruments + 2 enums; I1 enforced at construction | compiles
W-011 | 2026-09-19 | TemporalAxis, DateRange, Validity (3 variants) | `./mvnw test -pl backend -Dtest=ValidityTest`
W-012 | 2026-09-19 | ValidityTest — 16 tests — Phase 1 complete | `./mvnw test -pl backend -Dtest=ValidityTest`
W-018 | 2026-09-19 | AggregateWithCeiling + AggregateOutcome; CapContext front-loaded to 3 maps; 15 tests incl. T5-1/T5-2/T5-3 | `./mvnw test -pl backend -Dtest=AggregateWithCeilingTest`
W-017 | 2026-09-19 | LeastOf + Limb; first-declared wins on tie; no flooring; 12 tests incl. the HRA worked example | `./mvnw test -pl backend -Dtest=LeastOfTest`
W-015 | 2026-09-19 | SlabLadder + Band + validate() (4 checks), PackValidationException; 20 tests; all 7 rulebook figures | `./mvnw test -pl backend -Dtest=SlabLadderTest`
W-013 | 2026-09-19 | CapShape (permits 2 of 6), CapResult, CapContext, Fixed, PercentOfBase, BaseId + 11 tests; Money/Rate moved to `taxrules.money` per B-001/D-012 | `./mvnw test -pl backend -Dtest=CapShapeTest`

---

## Blockers

### B-001  (W-013)  — 2026-09-19  — **RESOLVED**: option (A), `Money`/`Rate` moved to `taxrules.money` (**D-012**)

**What I needed:** `CapShape.apply` to return a `CapResult` carrying a `Money`, and `PercentOfBase`
to hold a `Rate`. Both are `02-TECH-SPEC.md` T3 types living in `..taxengine.money..`; the cap
shapes are T2 types living in `..taxrules.cap..`.

**What I looked at:** `02-TECH-SPEC.md` T1 and T2; `05-domain-model.md` M1.3;
`16-getting-started.md:103` (`SlabLadder.Band(Money lowerExclusive, ...)`).

**What is ambiguous or missing — a contradiction, not a gap.** T1 declares

    taxrules   -> {"shared"}
    taxengine  -> {"shared", "taxrules"}

so the dependency runs **taxengine → taxrules**. But T2 puts `Money` in `taxengine.money` and the
cap shapes in `taxrules.cap`, and a cap shape cannot express a rupee limit without `Money`. That
requires **taxrules → taxengine**, the opposite arrow. The two cannot both hold.

Proof, after writing the item exactly as planned:

    $ ./mvnw test -pl backend -Dtest=WealthLensModulithTest
    - Module 'taxrules' depends on module 'taxengine' via
      com.thiru.wealthlens.taxrules.cap.CapResult -> com.thiru.wealthlens.taxengine.money.Money.
      Allowed targets: shared.       (and 3 more)

This is not specific to `CapResult`. **Every** rule pack holds rupee figures — slab band bounds,
the 80C ceiling, the standard deduction — so `taxrules` needs a money type by its nature, and
`16-getting-started.md:103` already writes `Band(Money lowerExclusive, ...)` inside a `taxrules`
type. The placement of `Money` in `taxengine`, not the cap work, is what is wrong.

**What I would need to proceed:** a decision on where `Money` and `Rate` live. Both options move
files that `W-005` and `W-007` produced and are already committed DONE, which is outside W-013's
`Produces` — hence this blocker rather than an erratum.

- **(A) move to `taxrules.money`** *(recommended)* — `taxengine` already allows `taxrules`, so the
  arrow stays as T1 draws it. Costs: move 2 production + 2 test files; amend `W-002`'s ArchUnit rule
  from `..taxengine.money..` to `..taxrules.money..`; errata in TS T2, T3, T10 and `DR 19` E1.
- **(B) move to `shared`** — needs no dependency change at all, since both modules already allow
  `shared`. But `shared` holds the `double`-based `TMoney`, and two money types in one package is
  the same two-vocabularies failure that `02-lessons.md` L1 describes and `ComputationStep` already
  demonstrated once. `shared` also depends on `portfolio`, which would put a pure value type in a
  module that reaches the domain centre.
- **(C) add `taxengine` to `taxrules`** — a cycle; Spring Modulith rejects it. Not viable.
- **(D) move the cap shapes into `taxengine`** — contradicts T2 and the rules-are-data split that
  the whole four-module design rests on.

The reverted code is complete and passes nothing but compilation; it is kept at
`scratchpad/w013-reverted/` and can be replayed into whichever package you choose.


<!-- Use the exact shape from 00-START-HERE.md §8. Never delete a blocker; mark it RESOLVED. -->

*(none)*

---

## Decisions taken during the build

<!-- Anything the design did not settle and a human decided. Format:
     D-00n | YYYY-MM-DD | <question> | <decision> | <who> -->

Recorded on the design's own recommendations (`docs/taxplanning-design/16-getting-started.md` §S1).
**All are reversible at zero cost today; none has been built on yet.**

| ID | Date | Question | Decision | Who |
|---|---|---|---|---|
| D-001 | 2026-09-19 | Q1 — ship TY 2025-26 at launch, or TY 2026-27 only? | **TY 2026-27 first.** It is fully verified; TY 2025-26 detail rules are `NEEDS VERIFICATION` and would put Slice 0 on the critical path | provisional — on recommendation |
| D-002 | 2026-09-19 | Q2 — four modules or one? | **Four** — `taxrules`, `taxengine`, `taxdocs`, `taxplanning` | provisional — on recommendation |
| D-003 | 2026-09-19 | Q3 — modulith `Type.CLOSED` or `OPEN`? | **CLOSED.** Greenfield has no flat-layout legacy to accommodate; CLOSED is what makes "the engine cannot reach a repository" enforced rather than conventional | provisional — on recommendation |
| D-004 | 2026-09-19 | Q4 — advisor path within 12 months? | **Seam only** — `AccessPolicy` from day one with a trivial body | provisional — on recommendation |
| D-005 | 2026-09-19 | Q5 — document ingestion in release 1? | **Fast-follow**, behind `app.taxplanning.documents.enabled=false` | provisional — on recommendation |
| D-006 | — | Q6 — who signs off a rule-pack change? | **OPEN — needs a human name.** Does not block any item before `W-032` | — |
| D-012 | 2026-09-19 | `taxrules` cannot see `Money`, which T2 places in `taxengine.money`, but T1 draws the arrow `taxengine -> taxrules`. Where does `Money` live? | **`taxrules.money`** — option (A) of B-001. A rule pack holds rupee figures by its nature, so `taxrules` needs the type; `taxengine` already allows `taxrules`, so T1's arrow is unchanged. Moved `Money`, `Rate`, `MoneyTest`, `RateTest`; renamed `taxengine_uses_money_not_bigdecimal` to `tax_modules_use_money_not_bigdecimal` and widened it to all three tax modules; errata in TS T3/T10, PRD, WORK-ITEMS W-002/W-004/W-005, DR 19 E1 | **approved by the developer** |
| D-011 | 2026-09-19 | The backlog splits implementation from its test: an item's `Verify` names a test that a **later** item produces. Three pairs occurred in Phase 1 alone. Fix or work around? | **Fix — restructure `03-WORK-ITEMS.md`.** Every item now carries its own test (**rule B1**). Merged `W-014→013`, `W-016→015`, `W-020→013/015/017/018/019`, `W-022→021`, `W-041→040`, `W-045→042`, `W-047→046`; Phase 3's six shared users of `RulePackLoadTest` each got their own test; `W-044` moved before `W-042`. **Ids are not renumbered** — `W-001`…`W-012` are referenced by commit subjects — so seven ids are marked RETIRED. 146 ids, 140 live. This supersedes D-009's errata-only restriction for this one change | **approved by the developer** |
| D-010 | 2026-09-19 | How are `Money` values asserted, given `MoneyAssert` is `double`-based? | **AssertJ `isEqualTo` on `Money` directly.** Record equality compares scale as well as value, so it catches a scale slip too. `MoneyAssert` exists to stop bare `assertEquals(double, double)`; that hazard does not exist for an exact type, and routing through it would discard the exactness and assert with a tolerance | **decided by the developer** |
| D-009 | 2026-09-19 | May `03-WORK-ITEMS.md` carry errata, as the design now can? | **Yes**, on the same terms — append only, proof required, original never edited | **decided by the developer** |
| D-008 | 2026-09-19 | May the design be corrected when building proves it wrong? | **Yes — errata only.** Append a marked block below the wrong text; never edit or delete the original; proof required. R7 relaxed, `00-START-HERE.md` §10 | **decided by the developer** |
| D-007 | 2026-09-19 | Q7 — `SAL-ALW-NEW-SUBSET-001` per section or per r.280(3)? | **Per the section**, flagged visibly. A rule cannot override its parent section | provisional — on recommendation |

⚠ **D-003 is the only one that changes what `W-001` writes.** D-006 must be answered before `W-032`
(the first rule pack).

---

## Rules used that are not fully verified

<!-- Append whenever a completed item relies on a NEEDS VERIFICATION or STATUS UNCLEAR rule.
     Format:  <RuleId> | <status> | <item that used it> | <what to check> -->

*(none yet)*

---

## Notes for resume

Session 1. Decisions D-001…D-007 recorded on the design's recommendations; **D-006 (Q6) is still
open** and blocks nothing before `W-032`. The plan for `W-001` is written and is **awaiting
approval** — no code has been written.

`W-011` + `W-012` are implemented and verified; **awaiting review**. 16 tests. Approving them
**closes Phase 1**.

`W-011` names `ValidityTest` as its own Verify and that test is `W-012`'s `Produces` — the same
coupling as `W-008`/`W-009`. **That is now three pairs where an item cannot verify itself**
(`W-008`/`W-009`, `W-011`/`W-012`, and `W-005`/`W-006` where the type shipped untested). A systematic
backlog issue rather than three accidents: implementation and its test should be one item.

**Third spec conflict found, erratum applied to the design.** `05-domain-model.md` M1.2 makes
`Instrument.assentedOn` mandatory, but the pack manifest in `16-getting-started.md` §S2 omits it for
the `rules` instrument — correctly, because subordinate legislation is **notified, not assented**.
Now `Optional<LocalDate>`, with `Instrument.assented(...)` and `Instrument.notified(...)` factories
so the call site says which kind it is.

`W-010` has **no test** — its Verify is "compiles". `Provenance`'s invariant I1 check is therefore
unverified behaviour; the first thing that exercises it is the pack loader at `W-029`.

**Two bugs found in `02-TECH-SPEC.md` T3, both corrected by erratum:**
1. The `RuleId` pattern was `{1,3}`, which **rejects 10 real rule IDs** — every one with four
   hyphenated segments, e.g. `RATE-SLAB-OLD-2026-A` and `SAL-ALW-NEW-SUBSET-001`. Widened to `{1,5}`.
2. `TaxYearId.label()` did not zero-pad: the tax year beginning 2099 rendered `"2099-0"`, not
   `"2099-00"`. The synthetic extensibility pack is TY 2099-00, so it is exercised.
The other five patterns were checked against every real value and are correct.

**`W-008` cannot satisfy its own Verify command** — it names `IdentifierTest`, which is `W-009`'s
`Produces`. So the two were done together and are reviewed together. `IdentifierTest` is **73 tests**
and both erratum corrections carry a regression test that provably fails against the original spec:
the ten four-segment rule IDs, and `TaxYearId.of(2099).label()` being `"2099-00"` rather than
`"2099-0"`.

**One file outside `Produces`:** `Identifiers.java`, the shared validator. Proposed before the item
started and approved with "do W-008"; recorded because the `Produces` list does not name it.

The cess case in `RateTest` (4% of ₹10,80,000 = ₹43,200) is taken from `08-golden-tests.md` T2-1,
so the arithmetic the surcharge golden tests will depend on at `W-057` is already pinned at the
unit level.

Session 1 was interrupted by a usage limit between `W-007`'s approval and its commit. State was
recovered from `STATE.md` with nothing lost and nothing repeated — the first real exercise of the
resume procedure.

`Money` is now fully specified and tested. The discriminating assertion is `1234.99 → 1230`: a
wrong implementation using `setScale(0, HALF_UP)` yields 1,240, so the test does bite.

Found in `W-006`: the work item cited **T9-14** for the rounding case, which is actually
*"portfolio read-model unavailable → 503"*. The right source is **T13-14** in `13-edge-cases.md`.
Errata are now permitted in `03-WORK-ITEMS.md` too (**D-009**) and the correction is recorded there.

**D-011 restructured the backlog (2026-09-19).** Read `03-WORK-ITEMS.md`'s header before picking an
item. Three things changed:

1. **Rule B1** — an item's `Verify` may only name a test the item itself produces, or one from an
   item already done. This is now checkable.
2. **Seven ids are RETIRED** (`W-014`, `W-016`, `W-020`, `W-022`, `W-041`, `W-045`, `W-047`). They
   are no work. Do not treat a retired id as skipped — read its line, then move on.
3. **`W-013` was not buildable as written.** It declared `sealed interface CapShape permits` six
   types that `W-014`–`W-019` created, and a sealed type does not compile until every permitted type
   exists (JLS 8.1.1.2). Each cap-shape item now adds its own type to the `permits` clause, so it
   **modifies `CapShape.java`** — that modification is in its `Produces`, and is not a §8 blocker.
4. **`W-044` (`NodeHarness`) now comes before `W-042` (`PipelineRunner`)** — a runner cannot be
   tested without test nodes to run. This is the one place the backlog order changed.

Phases 6–14 were deliberately **not** restructured: they have no `Verify` column and test through
golden-suite milestones. The standing fallback for those is in the note above Phase 6.

`MoneyAssert` cannot assert this `Money` — it is `double`-based with a half-paisa tolerance
(**D-010**). Tests use AssertJ `isEqualTo` on `Money` directly, which compares scale as well as
value and so catches a scale slip too.

`Money` has **no test yet** — that is `W-006`, and it is where the first hand-derived expected values
appear (the s.516 rounding cases). Until then its behaviour is unverified; only that it compiles and
satisfies the ArchUnit guards is known.

**The money guards are still not proven to bite.** `Money` sits *inside* `..taxengine.money..`, which
is exactly the package both guards exempt, so it passes them by exemption rather than by compliance.
Proving they fail on violation needs a deliberate violation in a file outside the item's `Produces`.

Learned in `W-004`:
- A plain `@Test` **does** run inside an `@AnalyzeClasses` class (16 tests = 15 `@ArchTest` + 1).
  The plan's main risk is resolved; no separate test class was needed.
- The withdrawal gate **caught a real violation on its first run — itself**. Its own javadoc names
  the withdrawn abstraction in order to forbid it. Exempted by filename, the same shape as
  `ChargeRounding`'s exemption from the money rule. Unlike `W-002`'s guards, this one is **proven
  to bite**.
- TS T10 words the `BigDecimal` rule as a *field* rule; `taxengine` has no fields, and ArchUnit 1.3
  fails a rule that checks nothing. Written as a dependency rule instead — non-empty input set, and
  strictly stronger (catches parameters, returns and locals too).

**Protocol deviation, recorded.** The developer said "do W-003" rather than approving a plan, so
stage 2's stop point was collapsed for this item only. The review gate at stage 5 was kept. R12 is
otherwise unchanged; this was a one-item instruction, not a standing exception.

Found while planning `W-002`: the existing `money_rounding_lives_in_one_place` carries a clause the
design did not quote — `.and().haveSimpleNameNotEndingWith("ChargeRounding")`, an exemption for the
charges engine's configurable per-rule policy. Preserved. `19-engineering-foundations.md` E1 quotes
the rule without it; anyone reading E1 alone would drop it and break the charges build.

**Resolved (D-008).** Errata are now permitted in the design — append only, proof required, original
never edited. `19-engineering-foundations.md` E1 carries the first one.

Both `W-002` guards pass **vacuously** today: `taxengine_does_not_use_TMoney` first bites at `W-005`
(`Money`), `only_the_adapter_converts` at `W-087` (`MoneyBridge`). Neither has been proven to fail on
violation — proving it needs a deliberate violation outside the item's `Produces`.

Learned in this session:
- `taxplanning` moving `OPEN` → `CLOSED` was the risk in `W-001`; `modulith verify()` passes, so
  nothing outside the module reaches into it. Decision **D-003** stands.
- The three marker classes (`TaxRules`, `TaxEngine`, `TaxDocs`) may be unnecessary — `package-info`
  alone might suffice for module detection. Not tested either way; they are harmless and each
  carries a note to delete it once the module has real public types.
- Baseline before any taxplanning work: **730 unit tests green, 7.5 s**. NFR N12 budgets 20 s.
- **Protocol gap found while committing.** The design and build docs were untracked, so `STATE.md`
  could not be committed without either orphaning it or dragging in the whole specification. Handled
  with a **baseline commit** of `docs/taxplanning-design/` and `docs/taxplanning-build/` immediately
  before the `W-001` commit. `P4` should say so; it currently assumes the spec is already tracked.
- **Second protocol gap.** `P4` says commit `STATE.md`, *then* write the new hash into it — which
  dirties the tree again. Resolved by committing, reading the hash, updating `STATE.md`, then
  `git commit --amend`. `P4` should spell that out.

Reminders that matter most:
- `W-002` exists because the design would otherwise **fail the existing build on the first commit** —
  `ArchitectureTest` forbids `BigDecimal.setScale` outside `..shared.util.money..`.
- `W-032` (the first pack JSON) is the highest-risk item in Phase 3. Slab bands are `400000`, **not**
  `400001`.
- `W-049` (the rebate node) contains the single most dangerous line in the engine: the rebate must
  read `TaxByBasis.atSlabRates` **only**.
