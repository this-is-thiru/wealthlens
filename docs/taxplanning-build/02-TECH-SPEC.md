# 02 — Technical Specification

**The current truth** about every type, package, file, collection, endpoint and invariant the
tax-planning build uses (decision **D-046 B1**: rewritten 2026-09-23; history is in **Changes** at
the end and in git). Read `docs/taxplanning-design/00-BRIEF.md` first.

**How to read the status marks** — every row says which of these it is:

| Mark | Meaning |
|---|---|
| ✅ `W-nnn` | **built**, by that item; the source file is the reference, not this document |
| ○ `W-nnn` | **planned**, owned by that item in `03-WORK-ITEMS.md` |
| ⚠ UNOWNED | **no item owns it yet** — a gap; do not build it inside another item, raise it |

**Rules for an agent.** A name used in code must appear here or in the code already. If something you
need is missing, or this document and the code disagree, **stop** (`00-START-HERE.md` §8, prompt
P11) — do not change either side. For a built type, **read the source**; nothing here is to be
copied verbatim.

---

## T1 — Modules

| Module | Package | `Type` | `allowedDependencies` | Named interfaces (what other modules may see) | Database? | Clock? |
|---|---|---|---|---|---|---|
| `taxengine` | `com.thiru.wealthlens.taxengine` | CLOSED | `{"shared"}` | `input`, `result`, `money` (`rules.money`), `id` (`rules.id`) | **no** | **no** |
| `taxplanning` | `com.thiru.wealthlens.taxplanning` | CLOSED | `{"shared", "auth", "taxengine :: *", "portfolio"}` | — | yes | yes |

Two modules since `W-045b2` (**D-048**; D-022/B-002 no longer apply — `taxrules` is now the package
`taxengine.rules`, and the empty `taxdocs` is gone; document code will live in `taxplanning.documents`).
A module here is a package boundary inside one JAR, verified by `WealthLensModulithTest` — not a service.

**Named interfaces.** A closed module shows other modules only the sub-packages it declares with
`@NamedInterface` on their `package-info.java`; `"taxengine :: *"` admits all declared ones (not the
base package). **An item that first needs another `taxengine` package from `taxplanning` adds that
package's named interface** — one line. Proven by the `W-045b2` probes: `input`/`result` visible;
`pipeline.NodeContext` refused; `Money`/`TaxYearId` were **not** carried by `@NamedInterface`'s
`propagate` and needed their own (`money`, `id`).

**Inside `taxengine`**, `rules` (the vocabulary and single-rule arithmetic) may not depend on
`input`, `pipeline`, `trace` or `result` (`ArchitectureTest.rules_do_not_depend_on_the_runner`).

**The trap that remains:** before writing a record that holds another module's type, check the
holder's module may see it (B-003, four times). `taxplanning` also contains the **legacy** module
(56 classes, `/tax-planning/**`) until it is deleted per `09-build-plan.md` B2.

---

## T2 — Package map

### `taxengine.rules` — ✅ built (the former `taxrules` module, D-048)

| Package | Types |
|---|---|
| `money/` | `Money` `Rate` — the **only** package that may call `BigDecimal.setScale` in the tax modules · named interface `money` |
| `id/` | `RuleId` `NodeId` `ComponentCode` `RegimeCode` `ItrFormCode` `ScheduleCode` `TaxYearId` `BaseId` `Identifiers` · named interface `id` |
| `cap/` | `CapShape` (sealed) `Fixed` `PercentOfBase` `LeastOf` `AggregateWithCeiling` `SlabLadder` `PerUnitPerPeriod` `CapResult` `CapContext` `AggregateOutcome` |
| `condition/` | `Condition` (sealed, **13** predicates: `IsResident` `AgeAtLeast` `HasPersonType` `IsCertifiedDisabled` `IncomeAtMost` `IncomeAtLeast` `HasIncomeUnderHead` `ComponentPresent` `EvidenceRecorded` `PaidOtherThanInCash` `AllOf` `AnyOf` `Not`) `ConditionContext` `PersonType` `AgeSubject` `DisabilitySeverity` `EvidenceType` `HeadCode` |
| `temporal/` | `TemporalAxis` `TemporalContext` `Validity` `RuleVersion` `AmendmentRef` `DateRange` `Provision` `Presence` `MissingTemporalCoordinateException` `NoRuleVersionException` |
| `pack/` | `RulePack` `RulePackLayer` `RulePackRef` `RuleRow` `RulePackProvider` `PackAssembler` `RegimeApplicability` `MidYearCombination` `DisallowanceSet` `ConditionSet` |
| `regime/` | `RegimeSpec` `RegimeLifecycle` `ContinuationPolicy` `InvalidationPolicy` `ElectionProtocol` `ElectionKind` `ElectionVehicle` `ElectionRoute` `ElectionState` `BindingSpec` `AfterWithdrawal` `DeadlineSpec` `FormRef` `PipelineSpec` `BehaviourId` `DisallowanceSetId` `ConditionSetId` `RegimeBehaviour` `RegimeBehaviourRegistry` `DataDrivenRegimeBehaviour` |
| `component/` | `ComponentDefinition` `ComponentLifecycle` `DeprecationPolicy` `CashNature` `ComponentKind` `TaxTreatment` `RateBasisHint` |
| `registry/` | `TaxYearRegistry` `TaxYearRegistryEntry` `UnsupportedTaxYearException` `GoverningInstruments` `Instrument` `Provenance` `LegalStatus` `VerificationLevel` `PublicationStatus` — the three status axes (D-036); only `PUBLISHED` versions enter a pack |
| `validate/` | `PackValidator` `PackValidationException` |
| `json/` | `PackJson` `ValueJson` `ShapeJson` `ConditionJson` `RuleJson` `RegimeJson` `RegistryJson` `JsonPackAssembler` — hand-written parsing (D-019) |
| `generated/` | `RuleIds` `ComponentCodes` `NodeIds` — generated from the timeline, committed, verified (`W-032b`, `W-045i`); every regime `nodeRef` must have a `NodeIds` constant (D-058) |
| `coverage/` | `CoverageRegister` `SectionRef` `Coverage` — the register of the Acts' sections, one classification each; `verify(Timeline)` refuses an unregistered citation and a `MODELLED` section with no published rule; run by `CoverageRegisterTest` (D-059). Five classifications — `DEFERRED` (scope tier + known assessee types) added for what D-033 designs for but has not scheduled; for every Act whose section list the timeline carries (`content/acts/`), `verify` refuses a section the register misses or invents and a list that is not continuous; `countsByClassification()` is the number the build reports (D-066). An Act list may mark sections `omitted`: they keep the list continuous and must not be registered (D-067). `SectionRef` accepts the Act's hyphenated numbers (194-O, 80-IA, 115V-I) |
| `catalogue/` | `SectionCatalogue` `SectionCatalogueEntry` `Route` `Disqualifier` `DisqualifierKind` `EnforcedBy` — the section catalogue, one plain-language entry per section, provisions as `Provision`s in both Acts; routes and disqualifiers link a rule (`ByRule`), a `Condition` record (`ByCondition`) or say `NotModelled` with a reason; `of(Timeline)` refuses authored `availability`/`history` (SC-4); `verify(Timeline, CoverageRegister)` checks SC-1 (every `MODELLED` section catalogued), SC-2 (no typed figure, rate, date or section number in text; `{{RuleId.field}}` only), SC-3 (every named rule in the timeline); the `PLANNED` half of SC-1 is a ratchet in `SectionCatalogueTest` (D-060) |
| `timeline/` | `Timeline` `TimelineReader` — the law authored once, one file per identity (D-034); reads a filesystem `Path`, refuses any file outside `law/rules`, `law/regimes`, `exec/pipelines`, `content/sections`, `content/acts` (+ `content/coverage.json`), misnamed, or with overlapping versions (D-053) · `RulePackCompiler` `CompiledPack` `RulePackLock` — compiles a year from the timeline (PUBLISHED versions overlapping it + PUBLISHED pending ones; regimes in force in it) in canonical JSON, and checks it against `<year>.lock.json` per rule and per layer (D-054) · `ClasspathRulePackProvider` — serves each year compiled from `<root>/timeline/` and refused unless it matches `<root>/<year>.lock.json`; refuses a timeline inside a JAR (D-055) · `PackDiff` — the publish diff between two compiled packs: rules and versions added or removed, every value changed, by path (D-057) |

### `taxengine.rules` — planned

| Package | Types | Item |
|---|---|---|
| — | wiring a provider into the application at startup; loading the timeline from the JAR | ⚠ **UNOWNED** (D-054) |
| `validate/` | `PackLinter` and the lint findings (`ProvisionRenumbered` `CarriedForwardUnverified` `SuspiciousCopy`, a `Magnitude` on `ValueChanged`), reusing `timeline.PackDiff` for the cross-year diff; regimes are not yet diffed | ○ `W-139` |
| `catalogue/` | `SectionCatalogueEntry` `Route` `Disqualifier` `DisqualifierKind` · `ClaimCatalogueEntry` `ClaimStep` `ClaimChannel` `Counterparty` `Materiality` | ○ `W-045k` · ○ `W-117` |
| `relief/` | `ReliefShape` (sealed) `FlatWithCeiling` `ExcessOverThreshold` `ThresholdLadderCeiling` `NoRelief` | ○ `W-048`, `W-055` |
| `filing/` | `ItrFormSpec` `ItrSchedule` `ScheduleField` `FieldSource` `FurnishingOption` `ReturnKind` | ○ `W-090`, `W-092` |

### `taxengine` — ✅ built

| Package | Types |
|---|---|
| `input/` (named interface `input`) | `EngineInputs` (10 components: `taxYear` `regime` `temporal` `entered` `assessee` `family` `ledgers` `elections` `capitalGains` `filing`) `Assessee` (sealed: `Individual` `Huf`; nests `ResidentialStatus`) `FamilySnapshot` `LedgerSnapshot` `RegimeElectionSnapshot` `FilingFacts` `EnteredFacts` `CapitalGainsInput` `InputsFingerprint` |
| `pipeline/` | `ComputationNode` `NodeContext` `NodeResult` `NodeRegistry` `NodeValue` (sealed: **`MoneyValue`, `TaxByBasis` only**) `SpecialRateId` `TopologicalSort` `PipelineRunner` |
| `trace/` | `TraceLine` `TraceAnnotation` `Notice` `StepKind` `Severity` |
| `result/` (named interface `result`) | `TaxComputation` `FilingPosition` `ComputationId` `ComputationReceipt` |

### `taxengine` — planned

| Package | Types | Item |
|---|---|---|
| `input/` | `Assessee` (sealed) `Individual` `Huf` — **replace** `TaxpayerFacts`; `PersonType` gains `HUF` | ✅ `W-045l` (D-062) |
| `result/` | `ComputationProjector` | ○ `W-052b` |
| `node/` | one class per node — T6 | ○ Phases 5–9 |
| `election/` | `Election` `ElectionResolver` `ElectionOutcome` | ○ Phase 9 |
| `proration/` | `ProrationBasis` `CompletedMonths` `CalendarMonths` `Days` `OccupationPeriods` | ○ `W-066` |
| `interest/` | `InterestAndFeeNode` `InterestBreakdown` `SimpleInterest` `TieredFee` | ○ `W-112` |
| `itr/` | `ItrFormSelector` `ItrSelection` `FormVerdict` `ScheduleMapper` | ○ `W-091`, `W-093` |
| `error/` | `TaxEngineException` (sealed) `TaxError` | ○ `W-105` |

### `taxplanning.documents` — planned (the former `taxdocs` module, removed by D-048)

`ingest/` `IngestedDocument` `ParseAttempt` `ExtractedField` `DocumentConfirmation` (○ `W-124`) ·
`parser/` `DocumentParser` `Form130Parser` `Form16Parser` (○ `W-125`–`W-127`) · `reconcile/`
`ReconciliationReport` `ReconciliationLine` (○ `W-130`).

### `taxplanning` (new code) — planned

`profile/` `TaxpayerProfile` `Pan` `FamilyMember` `Employer` `Landlord` `Lender` `BankAccount`
(○ `W-096`–`W-098`) · `workspace/` `TaxYearWorkspace` `FilingDraft` `DraftRevision` `DraftInputs`
`EntryProvenance` (○ `W-099`, `W-100`) · `assemble/` `EngineInputsAssembler` — the **only** place
`EngineInputs` is built (○ `W-103`) · `portfolio/` `MoneyBridge` `CapitalGainsAdapter` (○ Phase 9) ·
`projection/` `ForecastProjection` `CalendarProjection` `CompletenessProjection` (○ `W-113`–`W-115`)
· `scenario/` `Scenario` `ScenarioDiff` `DiffLine` (⚠ UNOWNED) · `controller/` DTOs only · `job/`
`TaxJob` + 6 jobs, `JobLease` (○ `W-137`).

**Name collisions to avoid** (the new `ArchitectureTest.simple_class_names_are_unique` enforces
it): the legacy module already has `TaxComputationService`, `TaxComputationEntity`, `TaxEngine`,
`RegimeAdvisor`. A new class may not reuse a simple name that exists anywhere in
`backend/src/main/java`.

---

## T3 — Foundational contracts

The source is the reference. These are the contracts a change must keep.

### `Money` — ✅ `taxengine/rules/money/Money.java` (`W-005`, `W-006`)

- `record Money(BigDecimal amountInRupees)`; construction sets **scale 2, `HALF_UP`**; `null` rejected.
- Arithmetic: `plus`, `minus`, `times(int)`, `floorAtZero`, `min`, `max`, comparisons.
- `roundToNearestTenPerSection516()`: ignore paise, round to the nearest ₹10 (up at 5), on the
  magnitude with the sign restored — `1234.99 → 1230` (`RATE-ROUND-002`, `13-edge-cases.md` T13-14).
- Asserted with AssertJ `isEqualTo` on `Money` (D-010) — record equality also catches a scale slip.

### `Rate` — ✅ `taxengine/rules/money/Rate.java` (`W-007`)

- `record Rate(BigDecimal fraction)`; `Rate.percent("30")` stores `0.30`. "30" and "0.30" are
  different types by design.
- `of(Money)` rounds to the money scale **once**, at the boundary.

### Identifiers — ✅ `taxengine/rules/id/` (`W-008`, `W-009`)

| Type | Pattern (from the source) |
|---|---|
| `RuleId` | `^[A-Z]{2,8}(-[A-Z0-9]{1,12}){1,5}$` |
| `NodeId` | `^[a-z][a-z0-9.]{2,63}$` |
| `ComponentCode`, `BaseId` | `^[A-Z][A-Z0-9_]{1,63}$` |
| `RegimeCode` | `^[A-Z][A-Z0-9_]{1,31}$` |
| `ItrFormCode`, `ScheduleCode` | `^[A-Z0-9-]{3,16}$` |
| `TaxYearId(int startYear)` | `label()` = `"%d-%02d"` (TY 2099 → `"2099-00"`); `startsOn()` 1 April; `endsOn()` 31 March |

**Naming of identity values** (D-035, D-046 B4): a `RuleId` or `RegimeCode` names the **concept** —
no year, no "old/new", no section number. The year lives in the version, the section in the
provision. The concrete names are chosen at `W-045d` (open question **OQ-4**).

---

## T4 — Rule packs

A **rule pack** is the immutable, checksummed law for one tax year. It is the only word for that
thing (not "snapshot", which names input projections such as `LedgerSnapshot`).

### Layers

| Layer | May change a computed number? | In the cache key? |
|---|---|---|
| `law/` | **yes — the only one that states law** | yes |
| `exec/` | yes, by selecting which law applies | yes |
| `forms/` | **no** | no |
| `content/` | **no** — property P3, invariant I27 | no |

### The per-year copy — removed (`W-045g3`)

Until `W-045g3` each year was a copied directory (`rulepacks/2026-27/`: `manifest.json` + the layer
files). It is gone; the timeline and `<year>.lock.json` are the only source (D-034, D-055).

### Timeline — ✅ `W-045f` (read, not yet compiled) · compile ○ `W-045g` (D-034)

```
backend/src/main/resources/rulepacks/
  timeline/
    law/rules/<RuleId>.json            ✅ one file per rule; versions inside (unchanged law = one version spanning years)
    law/regimes/<RegimeCode>.json      ✅ one file per regime; lifetime inside
    exec/pipelines/<NodeId>.json       ✅ one file per node, verbatim (D-053)
    exec/components/<ComponentCode>.json   with the first component (W-059)
    forms/itr/<ItrFormCode>.json
    content/sections/<SectionKey>.json ✅ the section catalogue: default regime (W-045k), investments deduction and HRA (W-045k2); not compiled into year packs
    content/acts/<Act>.json            ✅ each Act's section list, parsed from its gazette (2025 Act as amended by FA 2026: 537 numbers, 535 in force, W-045o/W-045o3; 1961 Act: 951 numbers, 820 in force for AY 2026-27, W-045o2); not compiled into year packs
    content/coverage.json              ✅ the coverage register (W-045j; the timeline's 2 cited sections `MODELLED`, + 3 `PLANNED` the catalogue cites, W-045k2; W-045o completes it); not compiled into year packs
  2025-26.lock.json  2026-27.lock.json  the compiled rule pack's identity: version ids + per-layer checksums
```

`RulePackCompiler` (✅ `W-045g`) builds a year's rule pack from every **`PUBLISHED`** version whose
validity touches that year, plus `PUBLISHED` pending-notification versions, and `RulePackLock.verify`
refuses a mismatch naming each rule that moved. Checksums are over canonical JSON — keys sorted, no
whitespace, `_` note fields dropped (D-054). `ClasspathRulePackProvider` loads every year this way (✅ `W-045g2`);
nothing yet loads a pack at startup (UNOWNED). Packs are to be compiled at
startup only, never on a request path.

---

## T5 — Invariants

One table. **Where** says what enforces it; a check that lives in a constructor or the provider has
no validator branch, because it would be unreachable.

| # | Invariant | Where enforced | Status |
|---|---|---|---|
| I1 | `status != VERIFIED` ⟹ non-blank `whatToCheck` | `Provenance` constructor | ✅ `W-010` |
| I2 | every `RuleId` referenced anywhere in the pack resolves (rate tables, `feedsInto`, `salaryBaseFor`, bases, interim rules) | `PackValidator` | ✅ `W-029` |
| I3 | every rule in the pack is evaluated by some node | `NodeRegistry.rulesNotEvaluatedByAnyNode` | ✅ built `W-039`; **switched on at `W-053`** as a shrink-only ratchet |
| I4 | each layer's checksum matches its content — now: the compiled year matches its lock, per rule and per layer | `RulePackLock.verify`, called by `ClasspathRulePackProvider` | ✅ `W-028`, `W-045g2` |
| I5 | exactly one `isDefault` regime per `(taxYear, PersonType)` among regimes **applicable that year**, for every `PersonType` **some regime in the pack applies to** — a pack serving no HUF owes HUFs no default (D-062; I11 is the same check) | `PackValidator` | ✅ `W-029` |
| I6 | every regime's disallowance/condition set id resolves | `PackValidator` | ✅ `W-029` |
| I7 | every `ComponentCode` a rule references exists | `PackValidator` | ✅ `W-029` |
| I8 | a cross-year ledger entry is consumed by at most one `ACTED_ON` draft per year | persistence (runtime) | ⚠ UNOWNED |
| I9 | every `BehaviourId` resolves to a registered implementation, and a registered one is referenced | `RegimeBehaviourRegistry` | ✅ `W-031b` |
| I10 | `entryWindow ⊆ inForceWindow` | `RegimeLifecycle` constructor | ✅ `W-026` |
| I11 | = I5 | — | ✅ `W-029` |
| I12 | a `MigrateTo` successor exists and is applicable when its predecessor lapses | pack load | ⚠ UNOWNED |
| I13 | a rule's versions cover the tax year with no gap and no overlap (`TAX_YEAR` axis) | `PackValidator` | ✅ `W-030` |
| I14 | more than one version in a year ⟹ `midYearCombination != UNDETERMINED` | `PackValidator` | ✅ `W-030` |
| I15 | every `RuleVersion` carries an `AmendmentRef` | `PackValidator` | ✅ `W-030` |
| I16 | a `PendingNotification` names an interim rule in the same pack | `PackValidator` (part of I2) | ✅ `W-029` |
| I17 | every `ComponentCode` a draft references resolves in the target pack, or has a `DeprecationPolicy` | draft upcasting (runtime) | ⚠ UNOWNED |
| I18 | a non-`TAX_YEAR` rule is always supplied its coordinate | `TemporalContext.on` / `RuleRow.versionAt` throw | ✅ `W-023`, asserted `W-039` |
| I19 | at least one ITR form applies per `(PersonType, ReturnKind)` | pack load | ○ `W-090` |
| I20 | no two ITR forms simultaneously eligible | pack load | ○ `W-090` |
| I21 | every `ScheduleRef`, `FromRule`, `FromNode` resolves | pack load | ○ `W-092` |
| I22 | every ITR form has a satisfiable `FurnishingOption` | pack load | ○ `W-092` |
| I23 | every claimable rule has a catalogue entry | pack load | ○ `W-117` |
| I24 | a catalogue entry's channel matches its rule's Chapter VIII Part; Part C carries the s.122(5) late-return warning | pack load | ⚠ UNOWNED |
| I25 | every `EvidenceRequirement` names what closes it | pack load | ⚠ UNOWNED |
| I26 | every `ClaimStep` with a deadline resolves against the tax calendar | pack load | ⚠ UNOWNED |
| I27 | perturbing `content/` and `forms/` changes no computed number | property test | ○ `W-146` |
| I28 | no occurrence of `ComputationStep` under `backend/src` | `ArchitectureTest` | ✅ `W-004` |
| I29 | every `TaxEngineException` declares a `TaxError`; the advice switch is exhaustive | build (sealed switch) | ○ `W-105`, `W-106` |
| I30 | `taxengine` depends on no repository, Spring Data or `taxplanning` type, and reads no clock | `ArchitectureTest` | ✅ `W-004` |
| I31 | every node's `requires()` is produced by another node; a node may require nothing and read `EngineInputs` (D-031) | `TopologicalSort` | ✅ `W-040`, `W-042` |
| I32 | the inputs fingerprint covers every field of `EngineInputs`, structurally | `InputsFingerprint` (reflection) | ✅ `W-038` |

Section-catalogue invariants **SC-1…SC-5** are in `00-BRIEF.md` §6.3 (✅ SC-1…SC-4 `W-045k`, `SectionCatalogue.verify` + `SectionCatalogueTest`; SC-5 is T23-1); coverage-register
checks in §7.1 (✅ `W-045j`, `CoverageRegister.verify`).

---

## T6 — Node catalogue

Order is **derived** from `requires()`/`produces()` (canonical: ties broken on `NodeId`, D-030); this
table is documentation. **`EngineInputs.x`** in *Requires* is data read through `ctx.inputs()` — not a
slot and not a `NodeId` (a node may require no slot at all, D-031). Every other entry is a `NodeId`.

| `NodeId` (produces) | Class | Requires | Value type | Status |
|---|---|---|---|---|
| `inputs.components` | `ComponentPartitionNode` | `EngineInputs.entered` | `ComponentSet` ⚠ | ○ `W-060` |
| `salary.perquisites` | `PerquisiteNode` | `inputs.components` | `MoneyValue` | ○ Phase 8 |
| `employer.contrib.charges` | `EmployerContributionNode` | `inputs.components` | `MoneyValue` | ○ Phase 8 |
| `salary.gross` | `GrossSalaryNode` | `inputs.components`, `salary.perquisites`, `employer.contrib.charges` | `MoneyValue` | ○ `W-061` |
| `salary.base` | `SalaryBaseNode` | `inputs.components` | `SalaryBases` ⚠ | ○ `W-062` |
| `salary.exemptions` | `ExemptionNode` | `salary.gross`, `salary.base` | `MoneyValue` | ○ Phase 7 |
| `salary.retirement` | `RetirementNode` | `inputs.components`, `EngineInputs.ledgers` | `MoneyValue` | ○ Phase 7 |
| `salary.deductions` | `SalaryDeductionNode` | `salary.gross` | `MoneyValue` | ○ `W-063` |
| `salary.income` | `SalaryIncomeNode` | `salary.gross`, `salary.exemptions`, `salary.retirement`, `salary.deductions` | `MoneyValue` | ○ `W-065` |
| `elections.houses` | `HouseElectionNode` | `EngineInputs.entered` | `ElectionOutcome` ⚠ | ○ Phase 9 |
| `houseproperty.income` | `HousePropertyNode` | `EngineInputs.entered`, `elections.houses` | `HeadResult` ⚠ | ○ Phase 9 |
| `othersources.income` | `OtherSourcesNode` | `EngineInputs.entered` | `HeadResult` ⚠ | ○ Phase 9 |
| `capitalgains.income` | `CapitalGainsNode` | `EngineInputs.capitalGains` | `HeadResult` ⚠ | ○ Phase 9 |
| `setoff` | `SetOffNode` | every head node, `EngineInputs.ledgers` | `HeadMap` ⚠ | ○ Phase 9 |
| `gti` | `GrossTotalIncomeNode` | `setoff` | `MoneyValue` | ○ Phase 9 |
| `chapterviii` | `ChapterEightNode` | `gti`, `inputs.components`, `EngineInputs.family` | `MoneyValue` | ○ Phase 8 |
| `totalincome` | `TotalIncomeNode` | `gti`, `chapterviii` | `MoneyValue` | ○ |
| `round.totalincome` | `RoundTotalIncomeNode` | `totalincome` | `MoneyValue` | ○ `W-050` |
| `tax.bybasis` | `TaxByBasisNode` | `round.totalincome` | `TaxByBasis` | ○ `W-046` |
| `tax.afterrebate` | `RebateNode` | `tax.bybasis` | `TaxByBasis` | ○ `W-049` |
| `tax.surcharge` | `SurchargeNode` | `tax.afterrebate`, `round.totalincome` | `MoneyValue` | ○ `W-056` |
| `tax.cess` | `CessNode` | `tax.afterrebate`, `tax.surcharge` | `MoneyValue` | ○ `W-051` |
| `credits` | `CreditsNode` | `EngineInputs` (TDS and advance-tax credits; exact components fixed by the owning item) | `MoneyValue` | ○ Phase 9 |
| `tax.netpayable` | `NetPayableNode` | `tax.cess`, `credits` | `MoneyValue` | ○ `W-052` |
| `interest.and.fees` | `InterestAndFeeNode` | `tax.netpayable`, `totalincome`, `EngineInputs.filing` | `InterestBreakdown` ⚠ | ○ `W-112` |
| `round.netpayable` | `RoundNetPayableNode` | `tax.netpayable`, `interest.and.fees` | `MoneyValue` | ○ `W-050` |

⚠ **Value types that do not exist yet.** `NodeValue` permits only `MoneyValue` and `TaxByBasis`. Each
⚠ type must be added to the `permits` clause **by the item that first produces it** (rule B2) — the
item checks this at its EXPLAIN stage (`W-045c` audits Phases 5–6).

The shipped 2026-27 pipeline (`exec/pipelines.json`) today names: `inputs.components`, `salary.gross`,
`totalincome`, `round.totalincome`, `tax.bybasis`, `tax.afterrebate`, `tax.cess`, `tax.netpayable`,
`round.netpayable`.

---

## T7 — MongoDB collections — ○ Phase 11 (`W-096`…`W-102`)

All `snake_case`; `@MongoId`; entities implement `AuditableEntity`. **Every one is added to
`AbstractIntegrationTest.cleanDatabase()`** (`W-102`); none to `SEEDED_REFERENCE_COLLECTIONS`.

| Collection | Key | Indexes |
|---|---|---|
| `tax_profiles` | `profile_id` | `{email}` (an account owns **several** profiles — self, HUF, family: **D-046 B2**); unique `{pan}` |
| `tax_family_members` | `family_member_id` | `{profile_id}` |
| `tax_employers` | `employer_id` | unique `{profile_id, tan}` (per account, not shared: **D-046 B5**) |
| `tax_landlords` | `landlord_id` | `{profile_id}` |
| `tax_lenders` | `lender_id` | `{profile_id}` |
| `tax_bank_accounts` | `bank_account_id` | `{profile_id}` |
| `tax_year_workspaces` | `workspace_id` | unique `{profile_id, tax_year}` |
| `tax_filing_drafts` | `draft_id` | `{workspace_id, status, updated_at desc}`; partial unique `{workspace_id}` where `is_primary` |
| `tax_draft_revisions` | `revision_id` | unique `{draft_id, revision_number}` |
| `tax_scenarios` | `scenario_id` | `{draft_id, created_at desc}` |
| `tax_computations` | `computation_id` | `{draft_id, computed_at desc}`; `{profile_id, tax_year, inputs_fingerprint, law_version, exec_version}` |
| `tax_filing_positions` | `filing_position_id` | `{computation_id, as_of desc}` |
| `tax_projections` | `{draft_id, projection_type}` | unique |
| `tax_documents` | `document_id` | unique `{sha256}`; `{profile_id, tax_year}` |
| `tax_parse_attempts` | `attempt_id` | `{document_id, started_at desc}` |
| `tax_document_confirmations` | `attempt_id` | unique |
| `tax_lta_block_state` | `{profile_id, block_start_year}` | unique |
| `tax_lifetime_events` | `{profile_id, event_type}` | unique where `event_type = VRS_CLAIMED` |
| `tax_carry_forwards` | `{profile_id, head, origin_tax_year}` | `{profile_id, head}` |
| `tax_regime_election_history` | `{profile_id, regime_code}` | unique |
| `tax_elections` | `{draft_id, election_id}` | unique |
| `tax_rulepack_reviews` | `{pack_version, rule_id}` | unique |
| `tax_job_leases` | `job_type` | unique, TTL |
| `tax_job_failures` | `failure_id` | `{job_type, failed_at desc}` |

**No collection holds rules, slabs, limits or perquisite values** — the law lives in rule packs.
The one planned exception is the rule-authoring store behind the publish gate (`00-BRIEF.md` §5.4,
open question OQ-2), which writes **versions** and never bypasses the gate.

---

## T8 — API — ○ Phase 11–13

Base: **`/tax/user/{email}`** — the repository convention `/{resource}/user/{email}`, with a resource
distinct from the legacy `/tax-planning` until that is deleted (**D-046 B3**). Wrapped in
`ApiResponse<T>` except under the `integration-test` profile.

| Method | Path (under the base) | Item |
|---|---|---|
| `GET` | `/tax-years` | ⚠ UNOWNED |
| `GET` | `/workspaces/{taxYear}` | ○ `W-108` |
| `POST` | `/workspaces/{taxYear}/drafts` | ○ `W-108` |
| `GET` / `PATCH` | `/drafts/{draftId}` | ○ `W-108`; `If-Match` ○ `W-110` |
| `POST` | `/drafts/{draftId}/fork` · `/status` · `/primary` | ○ `W-108` |
| `GET` | `/drafts/{draftId}/revisions[/{n}]` | ○ `W-108` (model `W-100`) |
| `POST` | `/drafts/{draftId}/compute` | ○ `W-109` |
| `POST` | `/drafts/{draftId}/compare-regimes` | ○ `W-109` |
| `GET` | `/drafts/{draftId}/forecast` | ○ `W-113` |
| `GET` | `/drafts/{draftId}/calendar` | ○ `W-114` |
| `GET` | `/drafts/{draftId}/completeness` | ○ `W-115` |
| `GET` | `/drafts/{draftId}/itr-suggestion` | ⚠ UNOWNED endpoint (selector ○ `W-091`) |
| `GET` | `/drafts/{draftId}/itr-export` | ⚠ UNOWNED endpoint (mapper ○ `W-093`) |
| `GET` | `/drafts/{draftId}/claim-playbook/{ruleId}` | ○ `W-118` |
| `POST` / `GET` / `POST` | `/documents` · `/documents/{id}/parse` · `/documents/{id}/confirm` | ○ `W-131` |
| `GET` | `/computations/{computationId}` | ○ `W-107` |
| `GET` | `/computations/{computationId}/report.pdf` | ○ `W-123` |

`?view=summary|full|debug` (default `summary`). `ETag` = inputs fingerprint + the rule pack's
`law` and `exec` versions. `If-Match` required on `PATCH`.

---

## T9 — Error catalogue — ○ `W-105`

```java
public enum TaxError {                     //  http  retryable  class
    UNSUPPORTED_TAX_YEAR                  (400, false, USER),
    UNSUPPORTED_ASSESSEE_TYPE             (400, false, USER),   // D-046 B6
    UNSUPPORTED_CASE                      (400, false, USER),   // D-046 B6 — a combination not yet supported, named
    UNKNOWN_COMPONENT_CODE                (400, false, USER),
    COMPONENT_WITHDRAWN_NEEDS_CORRECTION  (409, false, USER),
    INCOMPLETE_INPUTS_FOR_RULE            (400, false, USER),
    STATUTORY_OVERSHOOT                   (400, false, USER),
    REGIME_BARRED_ENTRY_CLOSED            (409, false, USER),
    REGIME_BARRED_PERMANENTLY_INELIGIBLE  (409, false, USER),
    UNCLASSIFIED_CAPITAL_GAINS            (409, false, USER),
    DRAFT_REVISION_CONFLICT               (409, true,  USER),
    DRAFT_FROZEN                          (409, false, USER),
    ELECTION_ALREADY_CONSUMED             (409, false, USER),
    NO_APPLICABLE_ITR_FORM                (500, false, PACK),
    UNDETERMINED_APPORTIONMENT            (500, false, PACK),
    RULE_VERSION_GAP                      (500, false, PACK),
    MISSING_TEMPORAL_COORDINATE           (500, false, ENGINE),
    MISSING_RULE_PACK                     (500, false, ENGINE),
    RULE_EVALUATION_FAILED                (500, false, ENGINE),
    PORTFOLIO_UNAVAILABLE                 (503, true,  UPSTREAM);
}
```

`PACK` and `ENGINE` page an operator; `USER` never does. Until `W-105`: pack problems throw
`PackValidationException`; a pipeline cycle, a missing producer or a duplicate producer throws
`IllegalArgumentException` naming the nodes (D-030).

---

## T10 — Build-enforced rules

| Rule | Enforced by | Status |
|---|---|---|
| `BigDecimal.setScale` only in `..shared.util.money..` and `..taxengine.rules.money..` (plus `ChargeRounding`) | `ArchitectureTest.money_rounding_lives_in_one_place` | ✅ `W-002`, `W-013` |
| tax modules use `Money`, not `BigDecimal`, outside `money/` | `tax_modules_use_money_not_bigdecimal` | ✅ `W-004`, `W-013` |
| `taxengine` does not use `TMoney` | `taxengine_does_not_use_TMoney` | ✅ `W-002` |
| only `..taxplanning.portfolio..` calls `MoneyBridge.fromTMoney` | `only_the_adapter_converts` | ✅ `W-002` (first bites at the bridge, Phase 9) |
| `taxengine` fetches nothing: no repository, Spring Data or `taxplanning` | `taxengine_fetches_nothing` | ✅ `W-004` |
| `taxengine` reads no clock | `taxengine_has_no_clock` | ✅ `W-004` |
| nothing catches `Exception`/`RuntimeException`/`Throwable` (the engine included) | Checkstyle `IllegalCatch`, repo-wide | ✅ `W-045a`; the earlier ArchUnit rule was deleted by `W-045b3` (D-049) |
| `taxengine.rules` does not depend on `input`, `pipeline`, `trace`, `result` | `rules_do_not_depend_on_the_runner` | ✅ `W-045b2` |
| no `ComputationStep` anywhere | `ArchitectureTest` (plain test) | ✅ `W-004` |
| overriding `RegimeBehaviour.adjustPipeline` is listed against a reviewed file | `ArchitectureTest` (plain test, L5 guard 3) | ✅ `W-037c` |
| no generic class suffixes; unique simple class names | `classes_have_no_generic_suffix`, `simple_class_names_are_unique` | ✅ `W-045a` |
| Checkstyle rule set; legacy baseline only shrinks | `checkstyle.xml`, `CheckstyleBaselineTest` | ✅ `W-045a` |
| formatting | `spotless:check` on `validate` | ✅ `W-045a` |
| every `ComputationNode` implementation is referenced by some pack pipeline | `ArchitectureTest` | ○ `W-053` (with I3) |
| every `@RequestBody` carries `@Valid`; no controller returns an `@Document` type | `ArchitectureTest` | ○ `W-095` |

---

## T11 — Build and test commands

```bash
./mvnw test -pl backend -Punit                  # the fast tier — every item; Checkstyle + spotless:check run in validate
./mvnw test -pl backend -Dtest=ClassName        # one class
./mvnw test -pl backend -Dtest=ClassName#method # one method
./mvnw test -pl backend -Pintegration           # Testcontainers; Docker required
./mvnw spotless:apply                           # fixes formatting; the build refuses unformatted code
./mvnw clean install -pl backend -am -DskipTests
```

A Claude Code session cannot `git commit` unless the unit tier passes (`.claude/hooks/pre-commit-gate.sh`).

---

## T12 — Conventions

`CLAUDE.md` "Engineering standards" applies to every line. In addition:

- `@RequiredArgsConstructor` for DI; injected fields `private final`. No hand-written constructors
  on Spring beans (records and value types have compact constructors).
- Services: `@Service @Log4j2 @Transactional @RequiredArgsConstructor`.
- Entities: `@Data @AllArgsConstructor @NoArgsConstructor @Document @Field`, `snake_case` names.
- DTOs: `@Data @NoArgsConstructor @ToString`; no `@Builder` on request DTOs.
- Tests: `@ExtendWith(MockitoExtension.class)`, `// Given` `// When` `// Then`,
  `methodName_whenCondition_expectedResult`.
- Money assertions: **`Money` with AssertJ `isEqualTo`** (D-010); legacy `double` money with
  `MoneyAssert`; never a bare `assertEquals` on money.
- 4-space indent, K&R braces, no star imports.

---

## Changes

This document was append-only with errata until 2026-09-23; D-046 B1 replaced that with current
truth plus this log. Earlier text is in git (last errata version: commit `db8a6b9`).

| Date | Change | Source |
|---|---|---|
| 2026-09-19 | T3 `RuleId` pattern `{1,3}` → `{1,5}`; `TaxYearId.label()` zero-padded | `W-008` erratum |
| 2026-09-19 | `Money`/`Rate` moved `taxengine.money` → `taxrules.money`; money rules renamed and widened | B-001, D-012 |
| 2026-09-19 | T5: I1, I4 enforced outside the validator; I2 strengthened; I6 made checkable | `W-029` erratum |
| 2026-09-21 | T1: `taxrules` is `OPEN` | B-002, D-022 |
| 2026-09-21 | T5: I18 already enforced; I3 built, wired at the first real pipeline | `W-039` erratum, D-029 |
| 2026-09-23 | **Rewritten as current truth**: status marks; T2 regenerated from the source; T3 contracts instead of verbatim code; T4 built vs target (timeline by layer, rule pack not snapshot); T5 one table with "where enforced", 32 rows, 6 marked UNOWNED; T6 requires as full `NodeId`s, value types marked; T7 profiles per account and employers per account; T8 base `/tax/user/{email}` and **every item citation corrected** (18 of 18 were wrong); T9 two refusal codes; T10 built vs planned, citations corrected; T12 Money assertion contradiction resolved | D-046, review `reviews/2026-09-23-prd-techspec-review.md` |
| 2026-09-23 | `PackDiff` built by `W-045h`; `W-139` reuses it (it had been owned twice) | this rewrite |
| 2026-09-23 | T1: planned move to two modules | D-048 |
| 2026-09-23 | open questions `O-n` → `OQ-n` | `W-045b`, ID glossary (START-HERE §11) |
| 2026-09-23 | T1, T2 for two modules and named interfaces; T10 direction rule and the ⚠ on the swallow rule | `W-045b2`, D-048 |
| 2026-09-23 | T10: swallow rule deleted; `IllegalCatch` is the one enforcement | `W-045b3`, D-049 |
| 2026-09-23 | T4: the shipped pack holds 2 rule rows, not 15 (the earlier figure counted `VERIFIED` markers) | `W-045c` |
| 2026-09-24 | Name collisions: `ItrFormAdvisor` removed from the legacy list — the class is deleted | `W-045c1` |
| 2026-09-24 | T4: the pack's rule IDs and regime codes renamed to concepts; law layer `2026-27.law.2` | `W-045d`, D-051 |
| 2026-09-24 | T2: `LegalStatus`, `VerificationLevel`, `PublicationStatus` built, replacing `VerificationStatus` and `DraftOrFinal`; T4: pack rows carry the three axes, law layer `2026-27.law.3` | `W-045e`, D-036, D-052 |
| 2026-09-24 | T2: `Timeline`, `TimelineReader` built; T4: the timeline holds 2 rules, 2 regimes, 9 nodes, equal to the 2026-27 pack until `W-045g` | `W-045f`, D-053 |
| 2026-09-24 | T2: `RulePackCompiler`, `CompiledPack`, `RulePackLock` built; `RulePackLayer.files()`, `RulePackRef.checksumOf` shared by loader and compiler; T4: `2026-27.lock.json`, layers law.4/exec.2/forms.2/content.2; provider move → `W-045g2`; startup wiring UNOWNED | `W-045g`, D-054 |
| 2026-09-24 | T2: `ClasspathRulePackProvider` moved to `timeline/`, serves years compiled from the timeline and checked against the lock; I4 is the lock check; T4: the per-year copy is no longer loaded, removal → `W-045g3` | `W-045g2`, D-055 |
| 2026-09-24 | T4: `rulepacks/2026-27/` removed; its last readers (generator, naming test, data checks, loader-format tests) read the timeline or the compiled pack | `W-045g3`, D-056 |
| 2026-09-24 | T2: `PackDiff` built in `timeline/` (not `validate/` — `timeline` already imports `validate`); `W-139` keeps the linter and lint findings | `W-045h`, D-057 |
| 2026-09-24 | T2: `NodeIds` generated from the timeline's node catalogue; the generated trio reads the timeline | `W-045i`, D-058 |
| 2026-09-24 | T2: `coverage/` built; T4: `content/coverage.json` read by `TimelineReader`, not compiled into year packs (lock unchanged) | `W-045j`, D-059 |
| 2026-09-25 | T2: `catalogue/` built; T4: `content/sections/*.json` read by `TimelineReader`, not compiled into year packs (lock unchanged) | `W-045k`, D-060 |
| 2026-09-25 | T4: two catalogue entries (investments deduction, HRA) and three `PLANNED` register rows (2025 s.123, 1961 s.80C, 1961 s.10) | `W-045k2`, D-061 |
| 2026-09-25 | T2: `input/` — `Assessee`/`Individual`/`Huf` replace `TaxpayerFacts`, `EngineInputs.taxpayer` → `assessee`, `PersonType.HUF`; I5/I11 count only the person types the pack's regimes serve | `W-045l`, D-062 |
| 2026-09-25 | the old compute endpoint's response gains a top-level `knownErrors` notice (`KnownErrorsComputation`, body otherwise unchanged); removed with the endpoint (`09` B2) | `W-045m`, D-063, D-064 |
| 2026-09-25 | T2: `Coverage.Deferred` + `ScopeTier`; `CoverageRegister` checks the register against each Act's section list and counts by classification. T4: `content/acts/` read by `TimelineReader`; the 2025 Act's 536 sections registered | `W-045o`, D-066 |
| 2026-09-25 | T2: `SectionRef` accepts hyphenated sections; Act lists may mark sections `omitted`. T4: `content/acts/INCOME_TAX_ACT_1961.json`; every in-force 1961 section registered | `W-045o2`, D-067 |
| 2026-09-25 | T4: the 2025-Act list reflects FA 2026 (354A inserted; 443, 447 omitted); the parse tool applies an amendments table | `W-045o3`, D-068 |
| 2026-09-25 | T4: the opted-out regime's reopening route is `NO_BUSINESS_OR_PROFESSION` (was `S202_4_B`); `2026-27.lock.json` law layer regenerated and bumped to `2026-27.law.5`; `RuleIdNamingTest` also refuses OLD/NEW fused with REGIME | `W-045d3`, D-070 |
