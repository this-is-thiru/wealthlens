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
| `id/` | `RuleId` `NodeId` `ComponentCode` `RegimeCode` `ItrFormCode` `ScheduleCode` `TaxYearId` `BaseId` `ItemFactId` `Identifiers` · named interface `id` |
| `cap/` | `CapShape` (sealed) `Fixed` `PercentOfBase` `LeastOf` `AggregateWithCeiling` `SlabLadder` `PerUnitPerPeriod` `PercentOfBaseByPopulation` `CapResult` `CapContext` `AggregateOutcome` |
| `relief/` | `ReliefShape` (sealed: `FlatWithCeiling`, `ExcessOverThreshold`, `ThresholdLadderCeiling`, `NoRelief`) `ReliefResult` `ReliefContext` `UnrelievedTax` — each relief formula once, applied to (income, tax before relief, optional re-entry for U); result = amount to take off + reason (D-073). `RuleRelief` (shape + `limitedToSlabRateTax`) is what a rule version carries (D-075) |
| `rounding/` | `RuleRounding` — the step a rule version rounds an amount to, a positive whole number of rupees; stated as data, applied by the rounding nodes (D-078) |
| `condition/` | `Condition` (sealed, **15** predicates: `IsResident` `AgeAtLeast` `HasPersonType` `IsCertifiedDisabled` `IncomeAtMost` `IncomeAtLeast` `HasIncomeUnderHead` `ComponentPresent` `EvidenceRecorded` `PaidOtherThanInCash` `ItemFactIs` `ItemFactAtMost` `AllOf` `AnyOf` `Not`) `ConditionContext` `ItemFacts` `PersonType` `AgeSubject` `DisabilitySeverity` `EvidenceType` `HeadCode` |
| `temporal/` | `TemporalAxis` `TemporalContext` `Validity` `RuleVersion` `AmendmentRef` `DateRange` `Provision` `Presence` `MissingTemporalCoordinateException` `NoRuleVersionException` |
| `pack/` | `RulePack` `RulePackLayer` `RulePackRef` `RuleRow` `RulePackProvider` `PackAssembler` `RegimeApplicability` `MidYearCombination` `DisallowanceSet` `ConditionSet` — a regime's disallowance sets are compiled from the timeline; each set's `basis` is a rule whose definition is a `DisallowanceBasis` naming it, checked both ways by `PackValidator` (D-165) |
| `regime/` | `RegimeSpec` `RegimeLifecycle` `ContinuationPolicy` `InvalidationPolicy` `ElectionProtocol` `ElectionKind` `ElectionVehicle` `ElectionRoute` `ElectionState` `BindingSpec` `AfterWithdrawal` `DeadlineSpec` `FormRef` `VehicleRoute` `PipelineSpec` `BehaviourId` `DisallowanceSetId` `ConditionSetId` `RegimeBehaviour` `RegimeBehaviourRegistry` `DataDrivenRegimeBehaviour` |
| `component/` | `ComponentDefinition` `ComponentLifecycle` `DeprecationPolicy` `CashNature` `ComponentKind` `TaxTreatment` (`RateBasisHint` moved to `input/`, D-241; deleted, D-290) |
| `registry/` | `TaxYearRegistry` `TaxYearRegistryEntry` `UnsupportedTaxYearException` `GoverningInstruments` `Instrument` `Provenance` `LegalStatus` `VerificationLevel` `PublicationStatus` — the three status axes (D-036); only `PUBLISHED` versions enter a pack |
| `validate/` | `PackValidator` `PackValidationException` |
| `json/` | `PackJson` `ValueJson` `ShapeJson` `DefinitionJson` `ConditionJson` `RuleJson` `RegimeJson` `RegistryJson` `JsonPackAssembler` — hand-written parsing (D-019); `PackJson` holds the primitives, rupee amounts and percentages among them; `ShapeJson` a version's validity, regimes, cap, relief and rounding; `DefinitionJson` its definition (`W-088u3`, D-273) |
| `generated/` | `RuleIds` `ComponentCodes` `NodeIds` — generated from the timeline, committed, verified (`W-032b`, `W-045i`); every regime `nodeRef` must have a `NodeIds` constant (D-058) |
| `coverage/` | `CoverageRegister` `SectionRef` `Coverage` — the register of the Acts' sections, one classification each; `verify(Timeline)` refuses an unregistered citation and a `MODELLED` section with no published rule; run by `CoverageRegisterTest` (D-059). Five classifications — `DEFERRED` (scope tier + known assessee types) added for what D-033 designs for but has not scheduled; for every Act whose section list the timeline carries (`content/acts/`), `verify` refuses a section the register misses or invents and a list that is not continuous; `countsByClassification()` is the number the build reports (D-066). An Act list may mark sections `omitted`: they keep the list continuous and must not be registered (D-067). `SectionRef` accepts the Act's hyphenated numbers (194-O, 80-IA, 115V-I). `CoverageBacklogTest` (D-161) reads `STATE.md`'s status board and refuses a `PLANNED` section whose item is missing, done or retired (W-078e2 extends it to `BUILT_FIRST`) |
| `catalogue/` | `SectionCatalogue` `SectionCatalogueEntry` `Route` `Disqualifier` `DisqualifierKind` `EnforcedBy` — the section catalogue, one plain-language entry per section, provisions as `Provision`s in both Acts; routes and disqualifiers link a rule (`ByRule`), a `Condition` record (`ByCondition`) or say `NotModelled` with a reason; `of(Timeline)` refuses authored `availability`/`history` (SC-4); `verify(Timeline, CoverageRegister)` checks SC-1 (every `MODELLED` section catalogued), SC-2 (no typed figure, rate, date or section number in text; `{{RuleId.field}}` only), SC-3 (every named rule in the timeline); the `PLANNED` half of SC-1 is a ratchet in `SectionCatalogueTest` (D-060) |
| `timeline/` | `Timeline` `TimelineReader` — the law authored once, one file per identity (D-034); reads a filesystem `Path`, refuses any file outside `law/rules`, `law/regimes`, `law/disallowances` (one set per file, a version per span of years — `DisallowanceSetVersions`, D-165), `exec/pipelines`, `content/sections`, `content/acts` (+ `content/coverage.json`), misnamed, or with overlapping versions (D-053) · `RulePackCompiler` `CompiledPack` `RulePackLock` — compiles a year from the timeline (PUBLISHED versions overlapping it + PUBLISHED pending ones; regimes in force in it; each disallowance set's version in force in it) in canonical JSON, and checks it against `<year>.lock.json` per rule and per layer (D-054) · `ClasspathRulePackProvider` — serves each year compiled from `<root>/timeline/` and refused unless it matches `<root>/<year>.lock.json`; reads the timeline from a directory or from a JAR (the Boot loader's nested filesystem, or a zip filesystem for a plain JAR), any other location refused (D-055, D-210); `RulePackLock` also carries the year's `GoverningInstruments`, read from the lock's `instruments` block (D-210) · `ShippedRulePacks` — every shipped year compiled and verified once, each assembled with its own lock's instruments; made a bean at startup by `taxengine/config/TaxEngineConfig` (`SHIPPED_YEARS` 2025-26, 2026-27, tied by a test to the locks shipped), so a timeline that differs from its lock stops the boot; internal, no named interface (D-048, D-210) · `PackDiff` — the publish diff between two compiled packs: rules and versions added or removed, every value changed, by path (D-057) |

### `taxengine.rules` — planned

| Package | Types | Item |
|---|---|---|
| `validate/` | `PackLinter` and the lint findings (`ProvisionRenumbered` `CarriedForwardUnverified` `SuspiciousCopy`, a `Magnitude` on `ValueChanged`), reusing `timeline.PackDiff` for the cross-year diff; regimes are not yet diffed | ○ `W-139` |
| `catalogue/` | `SectionCatalogueEntry` `Route` `Disqualifier` `DisqualifierKind` · `ClaimCatalogueEntry` `ClaimStep` `ClaimChannel` `Counterparty` `Materiality` | ○ `W-045k` · ○ `W-117` |
| `relief/` | `ReliefShape` (sealed) `FlatWithCeiling` `ExcessOverThreshold` `ThresholdLadderCeiling` `NoRelief` | ○ `W-048`, `W-055` |
| `filing/` | `ItrFormSpec` `ItrSchedule` `ScheduleField` `FieldSource` `FurnishingOption` `ReturnKind` | ○ `W-090`, `W-092` |

### `taxengine` — ✅ built

| Package | Types |
|---|---|
| `input/` (named interface `input`) | `EngineInputs` (10 components: `taxYear` `regime` `temporal` `entered` `assessee` `family` `ledgers` `elections` `capitalGains` `filing`) `Assessee` (sealed: `Individual` `Huf`; nests `ResidentialStatus`) `FamilySnapshot` `LedgerSnapshot` `RegimeElectionSnapshot` `FilingFacts` `EnteredFacts` `CapitalGainsInput` (nests `Bucket`, `BucketFacts`; `RateBasisHint` deleted, D-290) `InputsFingerprint` |
| `pipeline/` | `ComputationNode` `NodeContext` `NodeResult` `NodeRegistry` `NodeValue` (sealed: **`MoneyValue`, `TaxByBasis` only**) `SpecialRateId` `TopologicalSort` `PipelineRunner` · `RegimeVerdict` (sealed: `Applies`, `ExcludedByRow`, `Disallowed`) from `NodeContext.inRegime(rule, version)` — the only way a node asks whether a rule applies in the regime: the row's `regimes`, then the regime's disallowance sets (D-165; `ArchitectureTest.nodes_ask_the_context_whether_a_rule_applies_in_the_regime`); I3 on a pack counts a referenced set's basis as evaluated (`NodeRegistry.rulesNotEvaluatedByAnyNode(RulePack)`) |
| `trace/` | `TraceLine` `TraceAnnotation` `Notice` `StepKind` `Severity` — a node says something about a line by returning a `TraceAnnotation` addressing exactly one of its own lines in `NodeResult.annotations`; `PipelineRunner` renumbers annotations with the lines into `Result.annotations` (D-167); first producer `ExemptionNode` (`SAL-HOUSE-RENT-LANDLORD-PAN`, definition `ParticularsThreshold`) |
| `result/` (named interface `result`) | `TaxComputation` `FilingPosition` `ComputationId` `ComputationReceipt` `ComputationProjector` (a run → `TaxComputation`: `round.totalincome`, `tax.bybasis`, `tax.netpayable`, D-081) |

### `taxengine` — planned

| Package | Types | Item |
|---|---|---|
| `input/` | `Assessee` (sealed) `Individual` `Huf` — **replace** `TaxpayerFacts`; `PersonType` gains `HUF` | ✅ `W-045l` (D-062) |
| `result/` | `ComputationProjector` | ✅ `W-052b` (D-081) |
| `node/` | one class per node — T6 | ◐ `TaxByBasisNode` (`W-046`), `RebateNode` (`W-049`), `RoundTotalIncomeNode`, `RoundNetPayableNode` (`W-050`), `CessNode` (`W-051`), `NetPayableNode` (`W-052`); the rest Phases 5–9 |
| `election/` | `Election` (sealed: `SetOffOrderElection`, `RegimeElection`, `HouseElection` — sets of at most two houses not let, the entered specification first, relabelled `OWN_RESIDENCE` / `NOT_LET`, D-230; `basis(choice, rules)` and `candidates(rules)` over a `NodeContext` scoped to the election's `node()`, built by `ElectionResolver` in the computing regime — an election reads a row only through a set its node declares (D-255), `regimeFor(choice, computing, pack)` — each choice under its own regime, D-222) `RegimeOffer` (sealed: `Available`, `Barred(reason)`) `RegimeOffers.of(pack, inputs)` (person type, in force, open to new entrants or IN_EFFECT if grandfathered, eligibility sets; a binding that applies refused → W-082e; the default first) `ElectionMode` (`USER_SPECIFIED`, `AUTO_OPTIMISE`) `ElectionOutcome` `PricedAlternative` `ElectionResolver` — a stated choice computed once, else a whole `PipelineRunner.run` per choice, the least `tax.netpayable` taken, a tie to the earlier (the declared default first); each alternative a MEASURE line of the basis rule under its node, appended to the chosen run's trace; **`resolveTogether(elections, …)` → `JointResolution(outcomes, run)`** (`W-084a`, D-236) — every combination of two or more elections' choices a whole run, the regime folded through each `regimeFor`, the least taken, a tie to the earlier combination (every default first); each outcome prices each choice at its best combination, one MEASURE line per election per choice; `ElectionOutcome` is not a `NodeValue` until W-083 | ◐ `W-082a` (D-220, D-221), `W-082b` (D-222; a regime's line cites its rate table under `tax.bybasis`); the s.202(4)(a) history W-082e, `ChildElection` / `AggregateAllocationElection` W-082c, `HouseElection` W-083 |
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
`EngineInputs` is built (○ `W-103`) · `portfolio/` `MoneyBridge` (✅ `W-087`, D-240: `fromTMoney` refuses a non-canonical or non-finite double; `toTMoney` throws) `CapitalGainsAdapter` (✅ `W-087b`, D-241: `inputFor(email, taxYear)` — term and rate basis from S2 ss.196–198 / 1961 ss.111A, 112, 112A on `EQUITY` and the bucket's STT flags; unclassified, speculative and unmapped assets refused; OQ-31 refused), reading `portfolio`'s `dto.CapitalGainsSummary` / `CapitalGainsBucket` (✅ `W-085`, D-237: gross per classification, asset type and sub-class; no brought-forward loss) from `service.CapitalGainsSummaryService.summaryFor(email, financialYear)` (✅ `W-086`, D-238; the adapter → `W-087b`) ·
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
- Arithmetic: `plus`, `minus`, `times(int)`, `floorAtZero`, `min`, `max`, comparisons; `isWholeRupees` (no paise — `W-050a`).
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
| `TaxYearId(int startYear)` | `label()` = `"%d-%02d"` (TY 2099 → `"2099-00"`); `startsOn()` 1 April; `endsOn()` 31 March; `containing(date)` the year a date falls in — the one derivation (✅ `W-088t`) |

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
    exec/components/<ComponentCode>.json ✅ read and compiled (W-059); empty until the first rule that reads a component (D-089)
    forms/itr/<ItrFormCode>.json
    content/sections/<SectionKey>.json ✅ the section catalogue: default regime (W-045k), investments deduction and HRA (W-045k2), the rebate (W-049b), the two roundings (W-050); not compiled into year packs
    content/acts/<Act>.json            ✅ each Act's section list, parsed from its gazette (2025 Act as amended by FA 2026: 537 numbers, 535 in force, W-045o/W-045o3; 1961 Act: 951 numbers, 820 in force for AY 2026-27, W-045o2); not compiled into year packs
    content/coverage.json              ✅ the coverage register (W-045j; the timeline's 2 cited sections `MODELLED`, + 3 `PLANNED` the catalogue cites, W-045k2; W-045o completes it); not compiled into year packs
  2025-26.lock.json  2026-27.lock.json  the compiled rule pack's identity: version ids + per-layer checksums + the year's instruments (2025-26: W-052c)
  shipped-years.json                 the years shipped, by label — declared, not scanned (✅ W-088s, D-268)
```

`RulePackCompiler` (✅ `W-045g`) builds a year's rule pack from every **`PUBLISHED`** version whose
validity touches that year, plus `PUBLISHED` pending-notification versions, and `RulePackLock.verify`
refuses a mismatch naming each rule that moved. Checksums are over canonical JSON — keys sorted, no
whitespace, `_` note fields dropped (D-054). `ClasspathRulePackProvider` loads every year this way (✅ `W-045g2`);
`ShippedRulePacks` compiles every shipped year at startup (✅ `W-079a`, D-210), from the JAR when the application
runs from one; the years are those `shipped-years.json` declares (`ClasspathRulePackProvider.shippedYears`, ✅ `W-088s`). Packs are compiled at startup only, never on a request path.

A rule version carries a `cap`, a `relief`, a `rounding` or `conditions` — at least one, and at most one of
cap, relief and rounding (the `RuleVersion` constructor refuses two). Each of `cap`, `relief`, `rounding` and `definition` is an
`Optional`, empty where the rule states none — never null (`W-088r`). `relief` is tagged like a cap — `FlatWithCeiling
{maximum, incomeCeiling}`, `ExcessOverThreshold {threshold}`, `ThresholdLadderCeiling {thresholds}`,
`NoRelief {}` — and always states `limitedToSlabRateTax` (s.156(3)); absent is refused (`W-049a`, D-075). `rounding` is untagged, `{toNearest: "10"}` (`W-050a`, D-078).

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
| `salary.monetaryincome` | `MonetarySalaryIncomeNode` | `inputs.components`, `salary.base`, `salary.exemptions`, `salary.retirement` | `MoneyValue` | ✅ `W-071c` (D-113) — the head's pay (`SAL-SALARY-INCLUDES`, via `GrossSalaryNode.pay`) less exemptions, retirement deductions and the s.19 deductions on that pay (`SalaryDeductionNode.on`); untraced; read by the specified-employee test |
| `salary.perquisites` | `PerquisiteNode` | `salary.base`, `salary.exemptions`, `salary.monetaryincome` (`W-071c`), `EngineInputs.entered` (accommodations, vehicles, meals, gifts, loans, expense benefits, assets lent and transferred, household benefits, children's education, share allotments) | `MoneyValue` (non-cash) | ✅ `W-071a` (D-111) — residential accommodation: `PERQ-ACCOM-GOVERNMENT` (licence fee), `-OWNED` (10 / 7.5 / 5% by the stated 2011-census band), `-LEASED` (least of rental, 10%), `-HOTEL` (least of charges, 24%), less rent paid; `-FURNITURE` (10% a year, or hire charges, less the employee's); on `SAL-SALARY-FOR-ACCOM` less the exemptions' total, apportioned by days; accommodation continued from an earlier year (owned or leased) refused until the Cost Inflation Index is in the pack. Runs after `salary.exemptions`, so every regime's pipeline orders base → exemptions → perquisites → gross. The other perquisites are `W-071b`–`g`; `W-071c` (D-113) — vehicles via `MotorCarPerquisite`: `PERQ-CAR-PARTLY-*` / `-OWNRUN-*` (per month by engine; `-ELECTRIC` TY 2026-27 only), `-CHAUFFEUR`, `-PRIVATE` (running + 10% wear and tear − charge), `-EMPLOYEE` / `-CONVEYANCE` (expenditure less the Table's or a higher claimed official use); an employer's car only for a specified employee (`PERQ-SPECIFIED-EMPLOYEE`, OQ-10); `W-071a2` (D-115) — owned or leased accommodation continued from an earlier year capped at the first year's amount × CII this year / CII first year (`PERQ-ACCOM-CONTINUED`, `RATE-COST-INFLATION-INDEX` — `IndexByYear`); first provided before 2023-24 refused (OQ-11); `W-071e1` (D-117) — meals via `MealPerquisite` (`PERQ-MEAL`, `-AT-WORK`, `-VOUCHER` — the excess over the limit per meal; TY 2025-26 vouchers in the new regime in full) and gifts via `GiftPerquisite` (`PERQ-GIFT`, per employer, nil strictly below the limit, else the whole); `W-071e2` (D-118) — loans via `LoanPerquisite` (`PERQ-LOAN` at the entered SBI rate, a month's interest per month-end balance via `Rate.ofShare`; `PERQ-LOAN-PETTY` on amounts lent per employer, inclusive; listed-disease loans nil save the insurance-reimbursed part); `W-071e3` (D-119) — `ExpenseBenefitPerquisite` (`PERQ-HOLIDAY`, `-CREDIT-CARD`, `-CLUB` with by-year exclusion rows, OQ-12; `-OTHER-BENEFIT`, telephones nil) and `AssetPerquisite` (`PERQ-ASSET-USE` 10% by months, `-EXCLUDED` via `ExcludedAssets`; `-ASSET-TRANSFER-*` via `Rate.leftAfter` / straight line); `W-071d` (D-120) — `HouseholdBenefitPerquisite` (`PERQ-SERVICES`, `-UTILITY`, `-EDUCATION`, `-EDUCATION-FREE` per child per month all-or-nothing, `-CARRIER-TRAVEL`; the arrangement decides whom, via the shared `SpecifiedEmployee`); `W-071f` (D-121) — `SpecifiedSecurityPerquisite` (`PERQ-SPECIFIED-SECURITY`: the fair market value on exercise by the entered basis, less the price; `PERQ-SPECIFIED-DATE`, a `DayWindow` of 180) |
| `employer.contrib.charges` | `EmployerContributionNode` | `salary.base`; `EngineInputs.entered` (employer contributions, retirement fund history, recognised fund interest) | `MoneyValue` | ✅ `W-073a` (D-126) — `PERQ-EMPLOYER-CONTRIBUTION`: every employment's contributions to a recognised PF, the notified pension scheme and an approved superannuation fund, the excess over ₹7,50,000; `W-073b` (D-127) — `PERQ-EMPLOYER-ACCRETION` on a second line: TP = (PC/2 + min(PC1 + TP1, opening balance)) × I ÷ F(avg) from `RetirementFundHistory`, one division, rounded once; a positive excess with no history refused; `W-073c` (D-151) — `PERQ-RPF-CONTRIBUTION` per employment, the recognised-fund contribution beyond 12% of `SAL-SALARY-FOR-RPF` (TY 2025-26 only — FA 2026 s.127 removed it), and `PERQ-RPF-INTEREST` per fund, interest credited beyond 9.5% of the balance it was allowed on (`RecognisedFundInterest`: interest ÷ the rate allowed), both years; a nil rate with interest refused |
| `salary.gross` | `GrossSalaryNode` | `inputs.components`, `salary.perquisites`, `employer.contrib.charges` | `MoneyValue` | ✅ `W-061` — counts only SALARIES lines feeding `SAL-SALARY-INCLUDES` (D-093); `W-071a` adds `salary.perquisites` on a second line (D-111); employer contributions `W-073a` on a line of their own (D-126); `W-075c3` (D-140) — the employer's pension-scheme contribution (s.16(k); 1961 s.17(1)(viii)) and the Government's Agniveer Corpus Fund contribution (s.16(l); 1961 s.17(1)(ix)) each on a line of its own, both years |
| `salary.base` | `SalaryBaseNode` | `inputs.components` | `SalaryBases` | ✅ `W-062` — `SAL-SALARY-FOR-HRA`, `-GRATUITY`, `-EMPLOYER-PENSION`, `-EMPLOYEE-PENSION` (`SalaryBase` definition); per rule per employment; a registry `salaryBaseFor` entry's `onlyWhereTermsProvide` against `Employment.termsIncludeDearnessAllowance`; one `MEASURE` line per rule (D-092); `SAL-SALARY-FOR-ENTMT` (TY 2025-26 only, `BASIC` only — 1961 s.16(ii) "exclusive of any allowance") joins with `W-064a`, and a base the year's pack does not hold is not measured (D-105) |
| `salary.exemptions` | `ExemptionNode` | `inputs.components`, `salary.base`, `EngineInputs.entered` (tenancies) | `MoneyValue` | ✅ `W-067` (HRA) — `SAL-HOUSE-RENT-ALLOWANCE`, per tenancy: the allowance and `SAL-SALARY-FOR-HRA` apportioned by days, `LeastOf` (received / rent in excess of one-tenth / percentage by location), one EXEMPTION line per period with `limitedBy`; owned or rent-free → nil line; new regime → a nil line per tenancy naming the regime, none where no home is entered (D-104, `DR 08` T3-4b); two or more employments refused (D-099). **LTA (`W-068`, D-100):** `SAL-LEAVE-TRAVEL-CONCESSION` per journey (least of amount incurred and fare admissible; TY 2026-27's no-transport fare from `SAL-LEAVE-TRAVEL-DISTANCE-RATE`, ₹30/km), `SAL-LEAVE-TRAVEL-JOURNEYS` two per calendar block with the carry-over uncounted, read against `EngineInputs.ledgers` by block; total ≤ LTA received; **two children (`W-080a`, D-212):** `SAL-LEAVE-TRAVEL-CHILDREN` (`SurvivingChildrenLimit` 2, except born before 1-10-1998 and multiple births after one child, read from `EngineInputs.family`) — where more children are counted the two eldest are covered, each journey must give `Journey.travellers` (`JourneyTraveller`), and a child left out gets a nil line; a per-kilometre journey carrying one keeps the whole rate for its distance — the child takes no share (`W-080a2`, D-228, D-232). **Allowances (`W-069`, D-101):** per `AllowanceUse`, the component's allowance rule — `SAL-ALW-UNIFORM`, `-CONVEYANCE` (least of paid and actually incurred), `-CHILDREN-EDUCATION`, `-HOSTEL` (least of paid and ₹ per month per child, max 2); conveyance in every regime (TY 2026-27 version SOURCE_CHECKED on the owner's ruling on r.280(3), D-216); **transport for a disabled employee (`W-069b`, D-102):** `SAL-ALW-TRANSPORT-DISABLED` from `EnteredFacts.disabledTransport`, a `DisabledTransportLimit` definition — least of paid and the monthly limit × months covered (TY 2025-26 ₹3,200, three disabilities; TY 2026-27 ₹15,000 metro / ₹8,000 other plus DA thereon as stated, four disabilities, version SOURCE_CHECKED on the owner's ruling on OQ-7, D-216); a disability not listed → nil line naming it; every regime |
| `salary.retirement` | `RetirementNode` | `inputs.components`, `EngineInputs.entered` (retirement payments), `EngineInputs.ledgers` | `MoneyValue` | ✅ `W-066a` (D-103) — one DEDUCTION line per `RetirementPayment`, under the entry the taxpayer states (`RetirementEntry`): `RET-GRATUITY-GOVERNMENT`, `RET-COMMUTED-PENSION-GOVERNMENT`, `-FUND`, `RET-RETRENCHMENT-APPROVED-SCHEME`, `RET-LEAVE-ENCASHMENT-GOVERNMENT` in full; `RET-COMMUTED-PENSION-OTHER` least of paid and ⅓ (gratuity received) or ½ of the whole pension's stated commuted value (`CommutedPensionShare`); `RET-VOLUNTARY-RETIREMENT` least of paid and ₹500000, nil once allowed in another year (`vrsClaimedIn`) or after arrears relief; every regime; a retirement component paid without an entry refused; `NOTICE_PAY` feeds gross only (s.18(1)(a)). **`W-066c` (D-106):** `RET-GRATUITY-OTHER` — least of received, ₹20,00,000 (S.O. 1213(E)) and ½ × ten-month average (by `SAL-SALARY-FOR-GRATUITY`'s inclusions, from `Separation`'s months) × completed years; `RET-LEAVE-ENCASHMENT-OTHER` — least of received, the employer's leave at credit — its cash equivalent (TY 2026-27) or valued at the ten-month average salary (TY 2025-26, 1961 s.10(10AA)(ii)) — 10 × the employer's ten-month average, ₹25,00,000 (S.O. 2276(E)); each capped at A − B across years (`RET-*-AGGREGATE`, B from `LedgerSnapshot`), the line then citing the aggregate; an event before the notification's `effectiveFrom` refused (`NotifiedRetirementLimits`). **`W-066d` (D-109):** `RET-GRATUITY-STATUTORY` (least of received and the amount under the gratuity statute) and `RET-RETRENCHMENT` (least of received, the amount under the retrenchment statute, ₹5,00,000), the amount being the employer's figure (`StatutoryEntitlement`) under PGA s.4 / IDA s.25F(b) before 21-11-2025 and the Codes' s.53 / s.70(b) after (D-107), the line naming the separation day; every version `UNVERIFIED`. Every Table entry is built. **`W-066f` (D-225):** `RetirementPayment.employment` — one payment per component per employment, each measured on its own employment's pay lines, terms, separation or leave figures; other gratuity and other leave encashment from several employers each take their own least-of, the year's total capped at A − B less this year's earlier payments in entered order (the line cut cites the aggregate); `requireOneDay` per employment; **`W-066g` (D-226, D-227):** statutory gratuity, retrenchment and another employer's commutation limited per payment (the Acts aggregate only Sl. 6 and 14), a commutation's gratuity limb counting any employer's gratuity; voluntary retirement limited once for the employee's year (the rule applied to the year's allowed amounts together); nothing from two employments is refused any longer |
| `salary.deductions` | `SalaryDeductionNode` | `inputs.components`, `salary.gross`, `salary.base` | `MoneyValue` | ✅ `W-063` — `SAL-TAX-ON-EMPLOYMENT` (`HeadTotal`, `ExceptIn [NEW_TAX_REGIME]`) and the regime's one `SAL-STANDARD-DEDUCTION-*` (`LeastOf` "statutory amount" / "salary" on base `SALARY` = gross); DEDUCTION lines; a row not applying in the regime leaves none (D-093); `W-064a` adds `SAL-ENTMT-ALLOWANCE` (TY 2025-26 only, `ExceptIn [NEW_TAX_REGIME]`): for the one Government employment (`EmployerKind`) paying an entertainment allowance, least of one-fifth of `SAL-SALARY-FOR-ENTMT` and ₹5,000; two such employments refused (D-105). Requires `salary.base` |
| `salary.incomebeforetravel` | `SalaryIncomeBeforeTravelNode` | `salary.gross`, `salary.exemptions` (`W-067`), `salary.retirement` (`W-066a`), `salary.deductions` | `MoneyValue` | ✅ `W-065`, `W-067`, `W-066a` — income under "Salaries" before medical travel abroad (D-112; was `salary.income` until `W-071b`): gross less exemptions, retirement deductions and deductions; untraced; negative refused |
| `salary.medicaltravel` | `MedicalTreatmentAbroadNode` | `salary.incomebeforetravel`, `EngineInputs.entered` (medical treatment abroad) | `MoneyValue` (non-cash) | ✅ `W-071b` (D-112) — per claim, `PERQ-MEDABROAD-STAY` (the spending less the lesser of it and the Reserve Bank's permitted amount) and `PERQ-MEDABROAD-TRAVEL` (wholly salary unless `IncomeAtMost` `salary.incomebeforetravel` ≤ ₹8,00,000); the income tested is the salary head's until other heads land |
| `salary.income` | `SalaryIncomeNode` | `salary.incomebeforetravel`, `salary.medicaltravel` | `MoneyValue` | ✅ `W-071b` (D-112) — their sum; untraced |
| `elections.houses` | — | — | — | ✗ not built (D-230): the house choice is read by `houseproperty.income` early in the graph and resolved outside it by `HouseElection`; no slot, no node |
| `houseproperty.income` | `HousePropertyNode` | `EngineInputs.entered` (house properties); `elections.houses` from `W-083` | `MoneyValue` (`HeadResult` with set-off, `W-081`) | ◐ `W-078a` (D-155) — per property: `HP-OWN-RESIDENCE` nil (at most `OwnResidenceHouses` 2, more refused until `W-083`), else `HP-ANNUAL-VALUE` the higher of expected letting value and rent (the rent where let, vacant and below), less local taxes paid; `HP-STANDARD-DEDUCTION` 30%; `HP-ARREARS` arrears less 30%; the ownership share applied. ◐ `W-078b` (D-156) — `HouseProperty.loans` (`HousingLoan`, the assessee's own, after the share) and `acquiredOrConstructedOn`; `HousePropertyInterest`: `HP-INTEREST` / `HP-OWN-RESIDENCE-INTEREST` (s.22(1)(b); the latter `ExceptIn NEW_TAX_REGIME`), `HP-PRE-CONSTRUCTION` / `HP-OWN-RESIDENCE-PRE-CONSTRUCTION` (`TaxYearSpan(4)`; the latter every regime from TY 2026-27, D-156 C1(a)), `HP-OWN-RESIDENCE-INTEREST-CAP` (`OwnResidenceInterestLimit`); less what s.130/131 took (sums must match `purposeLoanInterest`); nil where payable abroad untaxed (s.22(6)); properties netted within the head, the head below nil refused until `W-081`. ◐ `W-078c` (D-157) — `HouseProperty.unrealisedRent` (Rules 2026 r.21 / 1962 r.4, an entry condition) leaves the actual rent through `HP-UNREALISED-RENT` before the higher-of and the vacancy rule; stock-in-trade (s.21(5)) deferred with the business head. `W-078d` (D-160) — catalogue entries `SEC-HOUSE-PROPERTY-INCOME` (s.22 / s.20), `SEC-ANNUAL-VALUE` (s.23 / s.21), `SEC-HOUSE-PROPERTY-DEDUCTIONS` (s.24 / s.22), `SEC-INTEREST-PAYABLE-OUTSIDE-INDIA` (s.25 / s.22(6)), `SEC-ARREARS-OF-RENT` (s.25A / s.23), `SEC-HOUSE-CO-OWNERS` (s.26 / s.24), `SEC-DEEMED-OWNER` (s.27 / s.25); those thirteen sections `MODELLED` |
| `othersources.income` | `OtherSourcesNode` | `EngineInputs.entered` (`otherSourceItems`, `providentFundAccounts`, `giftsReceived`, `businessTrustUnits`, `lifePolicyReceipts`) | `MoneyValue` (`HeadResult` with set-off, `W-081`) | ◐ `W-079b` (D-194) — interest (`OS-INTEREST`, banker's commission on securities only `OS-BANKER-COMMISSION`, `OS-OTHER-EXPENDITURE`), dividends and unit income (`OS-DIVIDEND`; interest expense within `OS-DIVIDEND-INTEREST` — 20% of the year's dividend and unit income in TY 2025-26, nil from TY 2026-27), deemed dividends (`OS-DEEMED-DIVIDEND` nil), interest on compensation (`OS-COMPENSATION-INTEREST` 50%); winnings and the other s.92(2) clauses refused (coverage s.115BB DEFERRED; `W-079g`); the head below nil refused until `W-081`. `W-079c` (D-196, D-197) — family pension (`OS-FAMILY-PENSION` outside the new regime, ₹15,000; `OS-FAMILY-PENSION-NEW-TAX-REGIME`, ₹25,000; each the lesser of one-third, held as `FamilyPensionShare`'s divisor 3, and the limit), then other expenditure; a Government death-cum-retirement gratuity received on the employee's death (`OS-DEATH-GRATUITY`) and a fund pension's commutation (`OS-COMMUTED-PENSION`) deducted in full, any expense against them a nil line. `W-079d` (D-198) — each `ProvidentFundAccount`'s taxable-account interest in full (`OS-PF-TAXABLE-INTEREST`, every regime; S2 Sch. II Sl. 3(a), 4(a) with S4 r.277, 1961 s.10(11), (12) provisos with Rules 1962 r.9D), the fund's figure; a MEASURE line shows the year's contribution beyond `ProvidentFundThreshold` (₹5,00,000 without an employer contribution, ₹2,50,000 otherwise), per fund. `W-079f` (D-201) — gifts (`OS-GIFT`, `GiftThreshold` ₹50,000 / 10%, both years): the year's sums together, the year's other property received free together, each immovable property and each other purchase below value alone; an excluded `GiftReceived` counts toward nothing; TY 2025-26's COVID-19 grounds, (XIII)(B) within `OS-GIFT-COVID-DEATH`'s ₹10,00,000, refused in TY 2026-27. `W-079g1` (D-203, D-204) — Keyman policy sums (`OS-KEYMAN-POLICY`), forfeited advances (`OS-FORFEITED-ADVANCE`), termination compensation not from the employer (`OS-TERMINATION-COMPENSATION`) and the residue (`OS-RESIDUAL`, `OtherSourceKind.OTHER`), each in full less other expenditure; business-trust specified sums (`OS-BUSINESS-TRUST`, `BusinessTrustUnits` A − B − C, nil floor); employee contributions and machinery letting refused with the business head (D-202), life-policy sums refused (W-079g2). `W-079g2` (D-205, D-206) — `LifePolicyReceipt` via `LifePolicySums`: excluded on a death, before 1-4-2003, or within `OS-LIFE-POLICY-RATIO` (`PremiumShareBands`, Sch. II Sl. 2 / s.10(10D)) and, for a capped policy, `OS-LIFE-POLICY-PREMIUM` (`PolicyPremiumLimits`); else `OS-LIFE-POLICY`, received less premiums not deducted or used, never below nil (S4 r.59; Rules 1962 r.11UACA); a non-excluded ULIP refused (W-088). `W-079g2b` (D-207) — a capped receipt is excluded only if claimed (`claimsExemption`) and if, per class, the claimed receipts with `EnteredFacts.claimedLifePolicies` (`ClaimedPolicy`, the old policies) stay within the limit in every premium year (CBDT Circulars No. 2/2022, 15/2023; S2 s.536(2)(j)); a single breaching claim is charged, several are refused for the person to choose. `W-088u1` (D-271): gifts in `GiftIncome`, the s.93(2) allowance in `DividendInterestAllowance` (also read by `tax.surcharge`), the trace writer `OtherSourcesLines` |
| `capitalgains.income` | `CapitalGainsNode` | `EngineInputs.capitalGains` | `CapitalGainsHead` | ✅ `W-088a` (D-243) — buckets to parts (`CapitalGainsPart`: short-term slab, short-term listed equity, long-term other, long-term listed equity); a part's net loss set off within the head (`SETOFF-WITHIN-CAPITAL-GAINS`, `CapitalGainsAbsorptionOrder`, the declared default; OQ-32) — the long-term loss first, against long-term parts only (S2 s.108(2); 1961 s.70(2), (3)); what is left shown, not set off across heads (S2 s.109(2)), carried by W-088d. `W-088q3` (D-266): the in-head set-off through `CapitalGainsAbsorption`, as `setoff`'s |
| `setoff` | `SetOffNode` | every head node, `EngineInputs.ledgers` | `HeadMap` ⚠ | ○ Phase 9 ✅ `W-088d` (D-247) — the capital losses of `LedgerSnapshot.capitalLossCarriedForward` (`CarriedCapitalLoss`, by term) after the house-property carry-forward, oldest first, long-term first, `SETOFF-CARRIED-CAPITAL-GAINS` (`LossCarryForward`: the head, eight years, the return condition; `W-088q1`, D-264). ✅ `W-088q2` (D-265): both carry-forwards are set off by one mechanism, `CarriedLossSetOff`, over each ledger's portions (`CarriedLosses` / `CarriedLoss` / `Portion`), every portion's row asked of the regime; the capital-gains parts absorb a loss in order through `CapitalGainsAbsorption`. `W-088q3` (D-266): this year's set-offs share a private `SetOffContext` (the order in use, income, parts, lines) |
| `setoff` | `SetOffNode` | `salary.income`, `houseproperty.income`, `othersources.income`, `capitalgains.income` (`W-088a`) | `HeadsAfterSetOff` (`W-088a`, D-243; `MoneyValue` before) | ✅ `W-081a` (D-217) — **`W-088a` (D-243):** `CAPITAL_GAINS` last in `SETOFF-ACROSS-HEADS`, absorbing another head's loss part by part in `SETOFF-WITHIN-CAPITAL-GAINS`'s order; the value keeps the parts for W-088b. Before: each head's loss, in the declared `AbsorptionOrder` (`SETOFF-ACROSS-HEADS`: SALARIES, HOUSE_PROPERTY, OTHER_SOURCES; OQ-28), set off against the other heads: a house-property loss up to `SETOFF-HOUSE-PROPERTY`'s ₹2,00,000 (S2 s.109(1)(b); 1961 s.71(3A)), none in the new regime (`DIS-NEW-TAX-REGIME-NO-SETOFF`; s.202(2)(b)(ii), (3)), where a MEASURE line records it deemed given full effect to; an other-sources loss without a limit. SET_OFF lines name the order; what is left is a MEASURE line — carried forward under s.110 / s.71B (W-081b) or, for other sources, not carried. Then (`W-081b`, D-218) the carried loss from `EngineInputs.ledgers` (`CarriedHousePropertyLoss` per year of origin): oldest first against the house-property income left, `SETOFF-CARRIED-DISALLOWED-PART` before `SETOFF-CARRIED-HOUSE-PROPERTY` (OQ-29), expired after eight years (`TaxYearSpan(8)`), the attributable part destroyed in the new regime; MEASURE lines for still carried, expired, destroyed. Value: the heads' income after set-off `W-082a` (D-221) — the order is `EnteredFacts.absorptionOrder` where elected (the row's heads, each once, else refused), else the row's `AbsorptionOrder`; each SET_OFF line says which; `SetOffOrderElection` prices the orders |
| `gti` | `GrossTotalIncomeNode` | `setoff` (its `total()`, D-243; the special-rate parts pass through to `tax.bybasis`, W-088b, D-244) (`W-081a`, D-217; before it `salary.income`, `houseproperty.income`, `othersources.income`) | `MoneyValue` | ✅ `W-075a` (D-132) — total income before Chapter VIII (s.122(10)); Salaries is the only head built, so it is `salary.income` until Phase 9 replaces the input with `setoff`; untraced |
| `chapterviii` | `ChapterEightNode` | `gti`, `setoff` (`W-088b`: every base is GTI less the special-rate capital gains, s.196(4), s.197(5), s.198(6) / 1961 ss.111A(2), 112(2), 112A(5); D-244), `inputs.components`, `salary.base`, `EngineInputs.entered` (qualifying payments, employer contributions); `EngineInputs.family` with 80D | `MoneyValue` | ✅ `W-075a` (D-130, D-132), TY 2026-27 — `DED-SCHEDULE-XV` (s.123: pay lines and `qualifyingPayments` under ₹1,50,000, `EMPLOYEE_NPS` sub-capped at 10% of `SAL-SALARY-FOR-EMPLOYEE-PENSION` first); `DED-NPS-OWN` (s.124(3)–(4) on the sub-cap's residue and `NPS_MINOR_DEPOSIT`, ₹50,000); `DED-NPS-EMPLOYER-GOVT` / `-OTHER` / `-NEW-TAX-REGIME` (s.124(1)–(2) by `EmployerKind` and regime, on `SAL-SALARY-FOR-EMPLOYER-PENSION`); `DED-WITHIN-GROSS-TOTAL` (s.122(2), a line where it binds); nil line naming the regime where entered but excluded; two employers' pension contributions refused (OQ-15); TY 2025-26 with anything entered refused (W-075d). `W-075a2` (D-134, D-135) — `EnteredFacts.lifeInsurancePolicies`: each premium within `DED-PREMIUM-SHARE`'s band (`PremiumShareBands`: 20% to 31-3-2012, 10% from 1-4-2012, 15% from 1-4-2013 covering a s.154 disability or s.128 disease) of the actual capital sum assured, a `MEASURE` line per policy, the total as `LIFE_INSURANCE_PREMIUM` into s.123; only `DEDUCTION` lines sum. Schedule XV (c), (j), (k), (l): W-075a3 (OQ-16, OQ-17). `W-075b` (D-136) — s.126 via `HealthInsuranceDeduction` from `EnteredFacts.healthPayments`: `DED-HEALTH-FAMILY` / `-PARENTS` (insurance, scheme and check-ups to 25,000, medical to 50,000, ceiling 50,000), `-SENIOR` rows (50,000; `AgeAtLeast 60 INSURED_PERSON`, residence from `FamilySnapshot.Member.residentInIndia`), `DED-HEALTH-CHECKUP` (5,000 across both, split in the ratio claimed); a premium for a senior and a non-senior refused (OQ-18); cash, a senior's medical with insurance, a non-dependant child and a HUF refused; a lump sum by its fraction. `W-075c1` (D-138) — `DisabilityDeduction`: s.154 `DED-DISABILITY` / `-SEVERE` from the assessee's certificate and residence (IsResident, IsCertifiedDisabled); s.127 `DED-DISABLED-DEPENDANT` / `-SEVERE` from `disabledDependantSupport` — the lesser of spent and the limit, one dependant (OQ-19); s.128 `DED-SPECIFIED-DISEASE` / `-SENIOR` from `specifiedDiseaseTreatment` — paid less reimbursed within the limit, mixed senior refused (OQ-20); a non-resident gets nil lines. `W-075c2` (D-139) — `LoanInterestDeduction`: s.129 `DED-EDUCATION-LOAN` (`TaxYearSpan` 7, whole interest within the span); s.130 `DED-HOUSE-LOAN-EARLIER`, s.131 `DED-HOUSE-LOAN`, s.132 `DED-ELECTRIC-VEHICLE-LOAN` (`SanctionedLoanLimit`: limit, sanction window, ceilings; s.130 before s.131); a nil line naming the first failed condition. Definitions are read through `RuleDefinitions.require`. `W-075c3` (D-140) — `AgniveerDeduction`: `DED-AGNIVEER-OWN` (s.125(1), old regime) and `DED-AGNIVEER-GOVERNMENT` (s.125(2), every regime), the whole amount, from `EnteredFacts.agniveerCorpusFund`. `W-075c4` (D-141) — `RentPaidDeduction`: s.134 `DED-RENT-PAID` from `EnteredFacts.rentDeclaration` (Form No. 31: months of stay, the two s.134(4)(a) ownership bars) on the rented tenancies' rent — least of rent over 10% of total income, ₹5,000 × months, 25% of total income, total income being GTI less the other deductions held within it, unrounded, run last; nil lines for the new regime, each bar and any `HRA` pay line (s.134(4)(b)); months over the calendar months the tenancies occupy, a HUF and TY 2025-26 refused. `W-075c6` (D-142) — `DonationDeduction`: s.133 from `EnteredFacts.donations` (`Donation(DonationClause, amount, paidInCash, reportedByDonee)`; clauses NAMED_FUND, FAMILY_PLANNING, DROUGHT_RELIEF_FUND, CHARITABLE_INSTITUTION, PUBLIC_BODY): `DED-DONATION-WHOLE` (100%) / `-HALF` (50%) per group, the limited sums first held to `DED-DONATION-QUALIFYING` (10% of adjusted gross total income — GTI less the earlier deductions held within it) on a `MEASURE` line; `DED-DONATION-CASH` (`CashPaymentLimit` 2,000, s.133(5)) and s.133(6) for a charitable institution give nil lines; s.133 before s.134, refused where the limit binds with rent claimed (OQ-23) or mixes whole and half sums (OQ-24); a HUF and TY 2025-26 refused. `W-075d1` (D-144) — TY 2025-26 versions of `DED-SCHEDULE-XV`, `DED-NPS-OWN`, the three `DED-NPS-EMPLOYER-` rows, `DED-WITHIN-GROSS-TOTAL` and `DED-PREMIUM-SHARE` under 1961 ss.80C, 80CCC, 80CCD, 80CCE, 80A(2), the same shapes and figures; the 23 Schedule XV / s.124 components live from 2025-04-01; the year guard is per section — a section with something entered and its rows not in the year's pack is refused naming its item (80D W-075d2, s.127/128/154 W-075d3, s.129–132 W-075d4, s.125/133/134 W-075d5). `W-075d2` (D-145) — TY 2025-26 versions of the five `DED-HEALTH-` rows (1961 s.80D) and their components; both years: a scheme contribution for a parent refused (s.126(2)(b) / 80D(2)(b)), and only an insurance premium raises the senior's limit (s.126(8) / 80D(4)). `W-075d3` (D-146) — TY 2025-26 versions of the six disability and specified-disease rows (1961 ss.80U, 80DD, 80DDB); "the lesser of" moves from `DisabilityDeduction` into the rows (`LeastOf` of `AMOUNT_CLAIMED` and the limit), so TY 2025-26's 80DD can be a fixed sum (`Fixed`); nothing spent or deposited gives a nil line, both years. `W-075d4` (D-147) — TY 2025-26 versions of the four loan-interest rows (1961 ss.80E, 80EE, 80EEA, 80EEB), the same definitions. `W-075d5` (D-148) — TY 2025-26 versions of `DED-AGNIVEER-OWN`, `-GOVERNMENT` (1961 s.80CCH), `DED-RENT-PAID` (s.80GG, r.11B) and the four `DED-DONATION-` rows (s.80G, PM CARES by TOLA 2020); `DonationClause` names each 1961 counterpart; 80G before 80GG as D-142. Every section now computes for both years. `W-076` (D-149) — the close-out: the coverage register marks s.123 / 80C `MODELLED`; the other built sections wait on their catalogue entries (W-076a/b, SC-1); s.153 / 80TTA–TTB W-075c5; ss.135, 137, 152 deferred. `W-076a` (D-150) — catalogue entries `SEC-DEDUCTIONS-FROM-GROSS-TOTAL-INCOME` (80A / s.122), `SEC-PENSION-SCHEME` (80CCD / s.124), `SEC-AGNIVEER-CORPUS-FUND` (80CCH / s.125), `SEC-HEALTH-INSURANCE` (80D / s.126), `SEC-PENSION-FUND-ANNUITY` (80CCC / s.123), `SEC-INVESTMENT-AGGREGATE-LIMIT` (80CCE / s.123); those ten sections `MODELLED`. `W-076b` (D-152) — `SEC-DISABLED-DEPENDANT` (80DD / s.127), `SEC-SPECIFIED-DISEASE` (80DDB / s.128), `SEC-EDUCATION-LOAN-INTEREST` (80E / s.129), `SEC-HOUSE-LOAN-INTEREST-EARLIER` (80EE / s.130), `SEC-HOUSE-LOAN-INTEREST` (80EEA / s.131), `SEC-ELECTRIC-VEHICLE-LOAN-INTEREST` (80EEB / s.132), `SEC-DONATIONS` (80G / s.133), `SEC-RENT-PAID` (80GG / s.134), `SEC-DISABILITY` (80U / s.154); those eighteen `MODELLED` (80DD `Fixed`, its 1961 sum). Every built Chapter VIII section has its entry. The rest: W-075c5 (D-195) — `DepositInterestDeduction`, both years, before s.133: `DED-SAVINGS-INTEREST` (s.153(2)(a) / 80TTA, ₹10,000 of `SAVINGS_DEPOSIT_INTEREST`, an individual not a senior citizen or a HUF) and `DED-DEPOSIT-INTEREST-SENIOR` (s.153(2)(b) / 80TTB, ₹50,000 of savings and `DEPOSIT_INTEREST` together, `IsResident` and `AgeAtLeast` 60 `SELF`); `OtherSourceKind.OTHER_INTEREST` split into those two and the rest; a non-senior's deposit interest a nil line; both rows in `DIS-NEW-TAX-REGIME-NO-EXEMPTION`; an expense against deposit interest s.153 would reach refused (OQ-25) — **`W-075c5b` (D-231):** no longer refused — s.153 works on the items' interest net of the expenditure the head allows (`OtherSourcesNode.interestIncluded`), together floored at nil (OQ-25 answered, D-228); catalogue `SEC-SAVINGS-INTEREST` (80TTA / s.153), `SEC-DEPOSIT-INTEREST-SENIOR` (80TTB / s.153), all three sections `MODELLED`. `W-088u2` (D-272): s.123 with Schedule XV paras 1(c) and 2 in `ScheduleXvDeduction`, s.124 in `PensionDeduction` |
| `totalincome` | `TotalIncomeNode` | `gti`, `chapterviii` | `MoneyValue` | ✅ `W-075a` (D-132) — `gti` − `chapterviii`; untraced. Golden T1/T2 runs still enter here with `GivenTotalIncome` |
| `round.totalincome` | `RoundTotalIncomeNode` | `totalincome` | `MoneyValue` | ✅ `W-050` — s.516 via `RATE-ROUND-TOTAL-INCOME`, one `ROUNDING` line |
| `tax.bybasis` | `TaxByBasisNode` | `round.totalincome`, `setoff` (`W-088b`) | `TaxByBasis` | ✅ `W-046`, `W-046b` — the regime's `rateTable`, replaced by the one conditional ladder whose conditions hold (First Schedule 60–79 / 80+; two holding is refused); conditions read `NodeContext.conditions()` (D-072). ✅ `W-088b` (D-244) — the special-rate parts by `RATE-CG-SHORT-LISTED-EQUITY` / `-LONG-OTHER` / `-LONG-LISTED-EQUITY` (`SpecialRateId` = row id), the ladder on total income less them; a resident individual or HUF's unused nil band shifted onto them in `RATE-CG-SHORTFALL-ORDER`'s order (`SpecialRateShortfallOrder`); OQ-33 |
| `tax.afterrebate` | `RebateNode` | `tax.bybasis`, `round.totalincome` | `TaxByBasis` | ✅ `W-049` |
| `tax.surcharge` | `SurchargeNode` | `tax.afterrebate`, `round.totalincome`, `setoff` (`W-088c`) (and `EngineInputs.entered.otherSourceItems` for dividend income) | `MoneyValue` | ○ `W-056`. `W-079e` (D-200) — the `-EXCLUDING` ladders read total income less dividend income (dividends and deemed dividends, less the interest allowed against them by `OS-DIVIDEND-INTEREST`, shared with `OtherSourcesNode`; unit income stays in); refused, naming OQ-26 and `W-088`, where that ladder still bites with dividends present. Where the TY 2025-26 allowance is shared with unit income (`W-079e2`, D-229, D-234), the dividends' part is the allowed interest × their share of the dividend and unit income, bounded to [allowed − the units' claim, the dividends' claim] ✅ `W-088c` (D-246) — the excluding income also leaves out the special-rate parts of `setoff`; the tax on dividends (their pro-rata share of the slab tax on aggregate income, D-229) and at the special rates bears the total-income ladder's rate within `RATE-SUR-*-LIMIT` (15%), the rest the chosen rate; both refusals lifted |
| `tax.cess` | `CessNode` | `tax.afterrebate` (`tax.surcharge` added by `W-056`, D-079) | `MoneyValue` | ✅ `W-051` — `RATE-HEALTH-EDUCATION-CESS`, 4% of the whole post-rebate tax, one `TAX` line |
| `credits` | `CreditsNode` | `EngineInputs` (TDS and advance-tax credits; exact components fixed by the owning item) | `MoneyValue` | ○ `W-089b` (D-162) |
| `tax.netpayable` | `NetPayableNode` | `tax.afterrebate`, `tax.cess` (`tax.surcharge` by `W-056`; `credits` by the Phase 9 item, D-080) | `MoneyValue` | ✅ `W-052` — whole post-rebate tax + cess; untraced (no rule states a sum) |
| `interest.and.fees` | `InterestAndFeeNode` | `tax.netpayable`, `totalincome`, `EngineInputs.filing` | `InterestBreakdown` ⚠ | ○ `W-112` |
| `round.netpayable` | `RoundNetPayableNode` | `tax.netpayable` (`interest.and.fees` added by `W-112`, D-076) | `MoneyValue` | ✅ `W-050` — s.516 via `RATE-ROUND-PAYABLE`; a refund keeps its sign (`ROUND-NEG-001`) |

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
| a `taxengine.node` or `taxengine.election` class names no rule row: no `RuleIds`, no `new RuleId(String)` — it names a `RuleSetId` | `nodes_and_elections_reach_rules_through_rule_sets` | ✅ `W-088m3`, widened `W-088m4` |
| no `taxengine/node` source declares a `Map<…, RuleSetId>` (an item is chosen by `ItemFactIs`) | `ArchitectureTest.nodes_hold_no_map_to_a_rule_set` (plain test) | ✅ `W-088m3` |
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
| 2026-09-25 | T2/T6: `node/TaxByBasisNode` built — tax at slab rates from `regime.rateTable()`, one `TAX` trace line with the in-force version's provenance; age-band ladder selection split to `W-046b` | `W-046`, D-071 |
| 2026-09-25 | T4/T6: `RATE-SLAB-FIRST-SCHEDULE-AGE-60TO79`, `-AGE-80` authored (TY 2026-27); lock → `2026-27.law.6`; `NodeContext.conditions()` builds a `ConditionContext` from `EngineInputs` (RNOR resident, age at the tax year's end); `TaxByBasisNode` selects the ladder | `W-046b`, D-072 |
| 2026-09-25 | T2: `relief/` built — the relief family (`DR 10` E5); rule-row attachment and JSON move to `W-049` | `W-048`, D-073 |
| 2026-09-25 | T2/T4: `RuleRelief`; `RuleVersion.relief` (never beside a cap); the rule-row `relief` block, `limitedToSlabRateTax` required; a rule needs a cap, relief or conditions. No pack or lock change | `W-049a`, D-075 |
| 2026-09-25 | T2/T4/T6: `node/RebateNode` built — each s.156 rebate from its own row, only in its row's regime, computed on the whole tax, s.156(2) held to `atSlabRates`, off the slab tax; one `RELIEF` line per applicable row with its reason in `limitedBy`. Rows `REBATE-NEW-TAX-REGIME`, `-EXCESS`, `REBATE-RESIDENT-INDIVIDUAL` (TY 2026-27); lock → `2026-27.law.7`; `tax.afterrebate` also requires `round.totalincome` | `W-049`, D-074 |
| 2026-09-25 | T4: `content/coverage.json` — 2025 s.156 and 1961 s.87A `MODELLED` by the three rebate rules (manager `ReliefShape`); `content/sections/SEC-REBATE-FOR-CERTAIN-INDIVIDUALS.json`. No lock change | `W-049b`, D-077 |
| 2026-09-25 | T2/T4: `rounding/RuleRounding`; `RuleVersion.rounding` (at most one of cap, relief, rounding); the untagged rule-row `rounding` block; a rule needs a cap, relief, rounding or conditions; T3: `Money.isWholeRupees`. No pack or lock change | `W-050a`, D-078 |
| 2026-09-25 | T2/T4/T6: `node/RoundTotalIncomeNode`, `RoundNetPayableNode` (sharing `RoundingStep`) built; rows `RATE-ROUND-TOTAL-INCOME`, `RATE-ROUND-PAYABLE` (TY 2026-27, `rounding {toNearest: 10}`); lock → `2026-27.law.8`; `round.netpayable` requires `tax.netpayable` only; 2025 s.516 and 1961 ss.288A/288B `MODELLED` (manager `RuleRounding`), catalogue `SEC-ROUNDING-OFF-TOTAL-INCOME`, `-AMOUNT-PAYABLE` | `W-050`, D-076 |
| 2026-09-25 | T2/T4/T6: `node/CessNode` built — 4% of the whole post-rebate tax via row `RATE-HEALTH-EDUCATION-CESS` (`PercentOfBase`, FA 2026 s.3(15) / s.2(6), TY 2026-27); requires `tax.afterrebate` only until `W-056` adds `tax.surcharge`; lock → `2026-27.law.9` | `W-051`, D-079 |
| 2026-09-25 | T2/T6: `node/NetPayableNode` built — tax after rebate (whole) + cess, `NodeResult.untraced`; requires `tax.afterrebate`, `tax.cess`. No pack change | `W-052`, D-080 |
| 2026-09-25 | T2: `result/ComputationProjector` built — `project(Result, EngineInputs, ComputationId)`; anchors `round.totalincome`, `tax.bybasis`, `tax.netpayable` (unrounded); inputs must match the run's fingerprint | `W-052b`, D-081 |
| 2026-09-25 | T4: TY 2025-26 on the timeline — per-year versions for the new-regime slab, the three rebates and the two roundings (their 1961-side instruments), spanning versions for the three First Schedule ladders and the cess (FA 2026 governs both years; ladders' `provision1961` corrected to FA 2026 Part I-A); `2025-26.lock.json` (law.1, instruments: 1961 Act, FA 2026, 1962 Rules UNVERIFIED); `2026-27.lock.json` → `law.10` | `W-052c`, D-082 |
| 2026-09-25 | T4: `FIRST_SCHEDULE_RATES`'s pipeline gains `tax.afterrebate` (s.156(1) rebate; missing since W-045f), locks → 2025-26 `law.2`, 2026-27 `law.11`. T11: T1 golden cases (`taxengine/golden`, 14 cases × 2 years) run end to end from `totalincome` through the shipped pipeline | `W-053`, D-083 |
| 2026-09-25 | T4: surcharge rows — `RATE-SUR-FIRST-SCHEDULE`, `RATE-SUR-NEW-TAX-REGIME` (total income: 10% >50L, 15% >1Cr) and `…-EXCLUDING` (total income excluding dividends and the special gains, base `TOTAL_INCOME_EXCLUDING_DIVIDENDS_AND_GAINS`: 25% >2Cr; 37% >5Cr first schedule only), one version spanning both years; no node until `W-056` (listed in `NOT_YET_EVALUATED`); locks 2025-26 `law.3`, 2026-27 `law.12` | `W-054`, D-084 |
| 2026-09-26 | T4: surcharge marginal-relief rows — `RATE-SUR-FIRST-SCHEDULE-CEILING` (`ThresholdLadderCeiling` 50L/1Cr/2Cr/5Cr) and `RATE-SUR-NEW-TAX-REGIME-CEILING` (50L/1Cr/2Cr), relief-only, `limitedToSlabRateTax` false, one version spanning both years; the thresholds restate FA 2026 Table 2 / s.2(5), s.3(5) Table Sl. 6 and `W-056` reads them from these rows; no node until `W-056` (listed in `NOT_YET_EVALUATED`); locks 2025-26 `law.4`, 2026-27 `law.13` | `W-055`, D-085 |
| 2026-09-26 | T3: `NodeContext.recomputed(target, pinned, value)` — a node may recompute a slot it declared at another value of a slot it declared; the runner re-runs only the nodes between them in a fresh store (`Recomputation`, `PipelineRunner.RunRecomputation`); a context built without one refuses; `NodeHarness.recomputation(...)` | `W-056a`, D-086 |
| 2026-09-26 | T6: node `tax.surcharge` (`SurchargeNode`, `MoneyValue`) after `tax.afterrebate` in both regimes — the greater of the two surcharge ladders' rates on the tax after rebate, less `W = U + V` from the `-CEILING` row with U by recomputation; refuses special-rate tax until `W-088`. `tax.cess` requires it (base: tax after rebate + surcharge), `tax.netpayable` adds it. `SlabLadder.bandFor`. Locks 2025-26 `law.5`/`exec.2`, 2026-27 `law.14`/`exec.3`; I3 has no exception list | `W-056`, D-086 |
| 2026-09-26 | T4: the component registry on the timeline — `exec/components/<CODE>.json`, one per file, read by `TimelineReader` (named for its code), held on `Timeline`, emitted into a year's `exec/components.json` when `ComponentLifecycle.isLiveIn`; no component ships until the first rule that reads one (`W-063`, `W-064`) | `W-059`, D-089 |
| 2026-09-26 | T3/T6: the first entered income — `EnteredFacts(List<Employment>)`, `Employment(List<SalaryComponent>)`, `SalaryComponent(code, annualAmount)`; `ComponentSet` (a `NodeValue`: lines by head, unsummed); node `inputs.components` (`ComponentPartitionNode`) partitions by the year's registry and refuses an unknown or non-live code (`D-CLOSEDSUM`) | `W-060`, D-090 |
| 2026-09-26 | T4: a rule version may carry a **definition** — `rules/definition/RuleDefinition` (sealed; first `HeadTotal(HeadCode)`), JSON `"definition": {"type": "HeadTotal", "head": …}`; at most one of cap, relief, rounding, definition; counted as content by the "means nothing" refusal | `W-061a`, D-091 |
| 2026-09-26 | T4/T6: rule `SAL-SALARY-INCLUDES` (`HeadTotal(SALARIES)`; 1961 s.17(1) as amended by FA 2023 s.10(i) for TY 2025-26, 2025 Act s.16 for TY 2026-27); node `salary.gross` (`GrossSalaryNode`) totals the partition's SALARIES lines with one INCOME line; 2025 Act s.16 `MODELLED`; catalogue `SEC-WHAT-SALARY-INCLUDES`; every shipped pipeline node now built | `W-061`, D-091 |
| 2026-10-11 | T2: `regime/VehicleRoute(appliesWhen, vehicle, form)`; `ElectionProtocol` gains `vehicleWhen` (empty = everyone uses its own vehicle) and `vehicleFor(ConditionContext)` — first matching route wins; JSON key `vehicleWhen`, optional. TY 2025-26 `FIRST_SCHEDULE_RATES`: Form No. 10-IEA for a person with business income, the return otherwise (Income-tax Rules, 1962 r.21AGA(1)) | `W-078j3b`, D-179 |
| 2026-10-16 | T6: node `othersources.income` (`OtherSourcesNode`) built (W-079b, D-194), fed into `gti`; input `OtherSourceItem(kind, received, bankerCommission, interestExpense, otherExpenditure, payableOutsideIndiaUntaxed)` on `EnteredFacts.otherSourceItems`; rules `OS-INTEREST`, `OS-DIVIDEND`, `OS-DIVIDEND-INTEREST`, `OS-DEEMED-DIVIDEND`, `OS-BANKER-COMMISSION`, `OS-OTHER-EXPENDITURE`, `OS-COMPENSATION-INTEREST`, both years; `tax.surcharge` refuses a dividend case in the excluding-income band until `W-079e` |
| 2026-10-17 | T6: `othersources.income` reads `EnteredFacts.providentFundAccounts` — input `ProvidentFundAccount(contributed, employerContributes, taxableAccountInterest)`; rule `OS-PF-TAXABLE-INTEREST` with definition `ProvidentFundThreshold(withoutEmployerContribution, otherwise)`, both years (W-079d, D-198) |
| 2026-10-17 | T6: `tax.surcharge` takes dividend income out of the 25%/37% test (W-079e, D-200); `SurchargeNode.rules()` adds `OS-DIVIDEND-INTEREST`; W-079b's interim refusal narrowed to the cases OQ-26 leaves open |
| 2026-10-17 | T6: `othersources.income` reads `EnteredFacts.giftsReceived` — input `GiftReceived(kind, consideration, value, excludedAs)` with `GiftKind`, `GiftExclusion`; rules `OS-GIFT` (definition `GiftThreshold(exceeds, considerationShare)`, both years) and `OS-GIFT-COVID-DEATH` (TY 2025-26 only, `Fixed`) (W-079f, D-201) |
| 2026-10-17 | T6: `othersources.income` reads `EnteredFacts.businessTrustUnits` — input `BusinessTrustUnits(distributed, issuedAt, chargedEarlier)`; `OtherSourceKind` gains `KEYMAN_POLICY`, `FORFEITED_ADVANCE`, `TERMINATION_COMPENSATION` (computed) and `EMPLOYEE_CONTRIBUTIONS`, `MACHINERY_LETTING`, `MACHINERY_AND_BUILDING_LETTING`, `LIFE_POLICY` (refused); `OTHER` is the residue, computed; rules `OS-KEYMAN-POLICY`, `OS-FORFEITED-ADVANCE`, `OS-TERMINATION-COMPENSATION`, `OS-BUSINESS-TRUST`, `OS-RESIDUAL` (W-079g1, D-203) |
| 2026-10-17 | T6: `othersources.income` reads `EnteredFacts.lifePolicyReceipts` — input `LifePolicyReceipt`; package-private `LifePolicySums`; `OtherSourceKind.LIFE_POLICY` removed; `PremiumShareBands.bandHolding`; definition `PolicyPremiumLimits`; rules `OS-LIFE-POLICY-RATIO`, `OS-LIFE-POLICY-PREMIUM`, `OS-LIFE-POLICY` (W-079g2, D-205) |
| 2026-10-17 | T6: `othersources.income` reads `EnteredFacts.claimedLifePolicies` — input `ClaimedPolicy(issuedOn, unitLinked, annualPremium, premiumFrom, premiumTo)`; `LifePolicyReceipt.heldWithOthersOfItsClass` → `claimsExemption`, `premiumFrom`, `premiumTo`; `LifePolicySums` tests the claimed set per class (W-079g2b, D-207) |
| 2026-10-17 | Content: coverage S2 ss.92–94 / 1961 ss.56–58 `MODELLED` (manager `PercentOfBase`), ss.95 / 59 `DEFERRED` `BUILT_LATER`; catalogue entries `SEC-OTHER-SOURCES`, `SEC-OTHER-SOURCES-DEDUCTIONS`, `SEC-OTHER-SOURCES-NOT-DEDUCTIBLE` (W-079g3, D-208). No rule or number changes |
| 2026-10-17 | T4: startup wiring — `ClasspathRulePackProvider` reads the timeline from a JAR; `RulePackLock.instruments`; `ShippedRulePacks` + `taxengine/config/TaxEngineConfig` compile and verify 2025-26 and 2026-27 at boot; the UNOWNED row removed | `W-079a`, D-210 |
| 2026-10-17 | T6: `salary.exemptions` applies the leave travel concession's two-children limit — row `SAL-LEAVE-TRAVEL-CHILDREN` (definition `SurvivingChildrenLimit`), input `JourneyTraveller` on `Journey.travellers`; `FamilySnapshot.Member.bornBeforeOct1998` removed (the date is the rule's) | `W-080a`, D-212 |
| 2026-10-17 | T6: `SAL-ALW-CONVEYANCE`, `SAL-ALW-TRANSPORT-DISABLED` TY 2026-27 `SOURCE_CHECKED` on the owner's ruling of r.280's defective text (r.280(3)'s "option under section 202(4)"; Sl. 10's "Metro Cities" and "thereon"); no `UNVERIFIED` rule version remains | `W-078j7b3`, D-216 |
| 2026-10-17 | T6: `setoff` (`SetOffNode`) before `gti`; rows `SETOFF-ACROSS-HEADS` (definition `AbsorptionOrder`), `SETOFF-HOUSE-PROPERTY`, `REG-NEW-TAX-REGIME-NO-SETOFF`; set `DIS-NEW-TAX-REGIME-NO-SETOFF` in `NEW_TAX_REGIME`; house property and other sources return losses | `W-081a`, D-217 |
| 2026-10-17 | T6: `setoff` sets off the carried house-property loss — rows `SETOFF-CARRIED-HOUSE-PROPERTY`, `SETOFF-CARRIED-DISALLOWED-PART` (in `DIS-NEW-TAX-REGIME-NO-SETOFF`); T3 input: `LedgerSnapshot.housePropertyLossCarriedForward` is `Map<TaxYearId, CarriedHousePropertyLoss>`, `preConstructionInstalmentsClaimed` removed | `W-081b`, D-218 |
| 2026-10-17 | Content: coverage S2 ss.108–110 / 1961 ss.70, 71, 71B `MODELLED` (s.108 / s.70 by the house-property rows `HousePropertyNode` nets), s.111 / s.74 `PLANNED` → W-088, S2 ss.112–121 / 1961 ss.72–80 `DEFERRED BUILT_LATER`, 1961 s.71A `OUT_OF_SCOPE` (spent); catalogue entries `SEC-SET-OFF-WITHIN-HEAD`, `SEC-SET-OFF-ACROSS-HEADS`, `SEC-HOUSE-PROPERTY-LOSS-CARRIED` | `W-081c`, D-219 |
| 2026-10-18 | T2: `election/` — `Election`, `SetOffOrderElection`, `ElectionMode`, `ElectionOutcome`, `PricedAlternative`, `ElectionResolver`; T3 input: `EnteredFacts.absorptionOrder` (`Optional<List<HeadCode>>`), `withAbsorptionOrder`; T6: `setoff` uses the elected order (OQ-28 answered: the assessee may choose) | `W-082a`, D-220, D-221 |
| 2026-10-18 | T2: `election/` — `RegimeElection`, `RegimeOffer`, `RegimeOffers`; `Election.basis` takes the choice and the pack, `Election.regimeFor` and `describe(choice, pack)` added | `W-082b`, D-222 |
| 2026-10-18 | T3 input: `RetirementPayment.employment` (factories take it first); T6: `salary.retirement` per employment, A − B over the year's total for other gratuity and leave encashment; the other capped entries from two employments refused | `W-066e`, `W-066f`, D-224, D-225 |
| 2026-10-18 | T6: `salary.retirement` — the other capped entries from more than one employer measured (statutory gratuity, retrenchment, commutation per payment; voluntary retirement one ₹5,00,000 for the year); the W-066e refusal removed | `W-066g`, D-226, D-227 |
| 2026-10-18 | T2: `election/` — `HouseElection`; T3 input: `EnteredFacts.withHouseProperties`; T6: `elections.houses` / `HouseElectionNode` not built — resolved outside the graph | `W-083`, D-230 |
| 2026-10-18 | T6: `chapterviii` — s.153 on deposit interest net of its expenditure (`OtherSourcesNode.interestIncluded`, shared with the head); the OQ-25 refusal removed | `W-075c5b`, D-231 |
| 2026-10-18 | T6: `salary.exemptions` — a per-kilometre LTA journey with a child beyond the limit priced on the whole rate for its distance; the OQ-27 refusal removed | `W-080a2`, D-232 |
| 2026-10-18 | T6: `tax.surcharge` — the TY 2025-26 interest allowance shared by dividends and units attributed by income share, bounded by each side's claim (`DividendInterest.receivedOnDividends`); the OQ-26 Q2 refusal removed | `W-079e2`, D-234 |
| 2026-10-18 | T2: `election/` — `ElectionResolver.resolveTogether`, `JointResolution`: several elections priced jointly on whole runs | `W-084a`, D-236 |
| 2026-10-18 | T2: `portfolio`'s read-model `CapitalGainsSummary`, `CapitalGainsBucket` named beside the adapter | `W-085`, D-237 |
| 2026-10-18 | T2: `portfolio`'s `CapitalGainsSummaryService` publishes it; `CapitalGainsAdapter` joins `MoneyBridge` in W-087 | `W-086`, D-238 |
| 2026-10-18 | T2: `taxplanning/portfolio/MoneyBridge`; `CapitalGainsAdapter` split to W-087b | `W-087`, D-240 |
| 2026-10-18 | T2: `CapitalGainsAdapter`; `RateBasisHint` moved `rules.component` → `input`; the read-model bucket gains `sttOnAcquisition`, `sttOnTransfer` | `W-087b`, D-241 |
| 2026-10-19 | T1 `definition/`: `LossCarryForward(absorbedBy, span, requiresReturnFiledInTime)`, the 25th definition. T4 pack: `SETOFF-CARRIED-HOUSE-PROPERTY` (`HOUSE_PROPERTY`, 8, false) and `SETOFF-CARRIED-CAPITAL-GAINS` (`CAPITAL_GAINS`, 8, true) on it in place of `TaxYearSpan`; relocked law 2025-26.77 / 2026-27.81. T6: `setoff` reads the head and the return condition from those rows, and refuses a row its path cannot follow | `W-088q1`, D-264 |
| 2026-10-20 | T2 `input`: `CapitalGainsInput.Bucket(gain, term, facts, portfolioRuleRef)` with `BucketFacts` (asset kind, STT on acquisition / transfer, acquisition mode, corporate action, acquisition date — `portfolio`'s names); `RateBasisHint` and `grossGainOn` deleted. T1 `definition/`: `BucketPart(CapitalGainsPart)`. T4 pack: rule set `RS-CG-PART` (`AT_MOST_ONE`, `REGIME_AND_CONDITIONS`), rows `CG-PART-SHORT-LISTED`, `-SHORT-SLAB`, `-LONG-LISTED`, `-LONG-OTHER` (both years). T6: `capitalgains.income` chooses each bucket's part from its item facts (`capitalgains.term`, `.asset`, `.stt.acquisition`, `.stt.transfer`, `.acquisition`, `.corporate.action`) and refuses a bucket no row covers. T2 `CapitalGainsAdapter` maps facts only. Relocked law 2025-26.78 / 2026-27.82 | `W-088f2a`, D-289, D-290 |
| 2026-10-20 | T1 `condition/`: `ItemDateBefore(fact, date)` (strict), the 16th predicate; `ItemFacts` gains `dates` and `date(fact)`. T4 pack: `CG-PART-LONG-NOTIFIED` in `RS-CG-PART` — S.O. 5054(E) clause (I) by `capitalgains.acquired.on`, clause (II) for `BONUS` / `DEMERGER` allotments. T6: `capitalgains.income` passes the acquisition date. Relocked law 2025-26.79 / 2026-27.83 | `W-088f2b`, D-291 |
| 2026-10-20 | T2: the read-model bucket's `sttOnAcquisition` / `sttOnTransfer` are `SttEvidence` (`PAID`, `NOT_PAID`, `UNKNOWN` — no breakdown is `UNKNOWN`); `input` `BucketFacts` carries them as codes; `capitalgains.income` passes them to `RS-CG-PART` unchanged. T4 pack: `CG-PART-LONG-NOTIFIED`'s buy limb `Not(stt.acquisition PAID)`. Relocked law 2025-26.80 / 2026-27.84 | `W-088j`, D-292 |
| 2026-10-20 | T2: `trade_outcomes` gains `buy_exchange_name`, `buy_segment`, `sell_exchange_name`, `sell_segment` (all three writers); the read-model bucket gains `acquisitionVenue`, `transferVenue` (`portfolio/dto/TradeVenue`), in its key. The engine is unchanged | `W-088j2b`, D-295 |
| 2026-10-20 | T2 `input`: `CapitalGainsInput.SideVenue`; `BucketFacts` gains `acquisitionVenue`, `transferVenue`. T1 `definition/`: `SttChargeability(code)`. T4 pack: `RS-STT-CHARGEABLE` (`STT-PURCHASE-DELIVERY`, `STT-SALE-DELIVERY`, `STT-SALE-NONDELIVERY`); `CG-PART-SHORT-LISTED`, `-LONG-LISTED`, `-LONG-NOTIFIED` accept `CHARGEABLE`. T6: `capitalgains.income` derives an `UNKNOWN` side's STT through `CapitalGainsStt`, traced. Relocked law 2025-26.81 / 2026-27.85 | `W-088j2c2`, D-298 |
| 2026-10-21 | T2: the read-model bucket gains `transferredOn` (from `sell_date`, in its key; W-088k2, D-304). T2 `input`: `CapitalGainsInput.Bucket(gain, facts, portfolioRuleRef)` — `term` removed; `BucketFacts` gains `transferredOn`; the adapter no longer passes `portfolio`'s term. T1 `definition/`: `ShortTermHoldingPeriod(months)`. T4 pack: rule set `RS-CG-TERM` (`AT_MOST_ONE`, `REGIME_AND_CONDITIONS`), row `CG-TERM-LISTED-SECURITY` (equity sold on `NSE` / `BSE`, twelve months; 1961 s.2(42A) as amended by FA (No. 2) 2024, 2025 s.2(101)(b)(i)). T6: `capitalgains.income` derives each bucket's term first (`node/CapitalGainsTerm`, calendar months, D-303), traced; a bucket no row covers is refused. Relocked law 2025-26.84 / 2026-27.88 | `W-088k3`, D-305 |
| 2026-10-19 | T6: `setoff`'s carried losses through one mechanism, `node/CarriedLossSetOff` (portions under their rows, read by the row's `LossCarryForward`), and `node/CapitalGainsAbsorption` (the parts in declared order, also `SetOffNode.absorbCapitalGains`); `CarriedCapitalLossSetOff` removed; traces identical | `W-088q2`, D-265 |
| 2026-10-19 | T6: `SetOffNode.SetOffContext` replaces `Absorbing` (`setOff` / `absorb` / `absorbCapitalGains` 3–4 parameters); `CapitalGainsNode.setOff` on `CapitalGainsAbsorption` (with `order()`), its `line` helper → `ctx.traceLine`; traces identical | `W-088q3`, D-266 |
| 2026-10-19 | T1/T2: `RuleVersion.cap` is `Optional<CapShape>`, a null refused like its siblings; `RuleJson.cap` in place of `capOrNull`; the readers unwrap it (no message changed) | `W-088r`, D-267 |
| 2026-10-19 | T4: `rulepacks/shipped-years.json` declares the shipped years by label; `ClasspathRulePackProvider.shippedYears(root)` reads it (a label must be its year's own, none twice); `TaxEngineConfig.SHIPPED_YEARS` removed, the bean and `GoldenCaseRunner` read the manifest | `W-088s`, D-268 |
| 2026-10-19 | T3: `TaxYearId.containing(LocalDate)`, from the type's own boundaries; `HousePropertyInterest`'s inline April rule removed | `W-088t`, D-269 |
| 2026-10-19 | T6: `othersources` per concern — `node/GiftIncome`, `node/DividendInterestAllowance` (the `DividendInterest` record moved from the node), `node/OtherSourcesLines` (was `OtherSourcesNode.Lines`); traces identical | `W-088u1`, D-271 |
| 2026-10-20 | T6: `chapterviii` per concern — `node/ScheduleXvDeduction` (s.123: the premium bands, the Schedule XV ceiling, para 1(c)'s Government salary and its refusal), `node/PensionDeduction` (s.124(1)–(4), OQ-15's refusal); the node keeps `paid`, the year guards, the section order and s.122(2); traces identical | `W-088u2`, D-272 |
| 2026-10-20 | T2 `rules/json`: the definition readers from `ShapeJson` to `DefinitionJson` (`definition`, public); `PackJson.money`, `optionalMoney`, `rate` (were `ShapeJson`'s private readers; `ConditionJson`'s copy of `money` removed); `DefinitionJson.constant` reads every enum element (the movable-asset and transport-disability copies folded in); loaded packs and messages identical | `W-088u3`, D-273 |
| 2026-10-19 | T2 `node/`, `election/`: no public signature changed. Private types for long parameter lists — `DonationDeduction.Group`, `OtherSourcesNode.KindRow` (was `InFullRow`) with `WHOLE_DEDUCTED`, `ElectionResolver.Combination`; `RetirementNode`'s and `ExemptionNode`'s HRA lines through `NodeContext.traceLine` | `W-088p2`, D-262 |
| 2026-10-19 | T3 `pipeline`: `NodeContext.traceLine(sequence, rule, kind, in, out)` — a line under the context's own node with the rule's governing-version provenance; a run-level context refuses; the node-local `line(...)` helpers of 13 nodes removed | `W-088p1`, D-261 |
| 2026-10-19 | T1 `cap/`: `PercentOfBaseByPopulation` (bands by a strict "exceeding" threshold over a count). T3 input: `EmployerAccommodation.cityPopulation` replaces `band`; `PopulationBand` removed. T4 pack: `PERQ-ACCOM-OWNED` on the new shape, 40,00,000 / 15,00,000 | `W-088n2`, D-258 |
| 2026-10-19 | T1 `condition/`: `ItemFactAtMost` (the 15th predicate, inclusive); `ItemFacts.quantities` / `quantity`. T3 input: `CarEngine` is a record of facts — `electric`, `cubicCapacityCc` (present exactly when not electric). T4 pack: the six car rows choose by `vehicle.engine.electric` and `vehicle.engine.cc` against 1600 | `W-088n1`, D-257 |
| 2026-10-19 | T2 `election/`: `Election.basis(choice, rules)` / `candidates(rules)` take the election's node's `NodeContext` (`ElectionResolver.rulesFor`); `HouseElection` / `SetOffOrderElection` read `RS-HP-OWN-RESIDENCE` / `RS-SETOFF-ACROSS-HEADS`. T10: the rule widened to `taxengine.election`, renamed `nodes_and_elections_reach_rules_through_rule_sets` | `W-088m4`, D-255 |
| 2026-10-19 | T10: `nodes_reach_rules_through_rule_sets` (no `RuleIds`, no `new RuleId(String)` in `taxengine.node`) and `nodes_hold_no_map_to_a_rule_set` (source scan); no baseline — none owed | `W-088m3`, D-254 |
| 2026-10-19 | T3 `pipeline`: `NodeContext.ruleOf` / `ruleIdOf` / `optionalRuleOf` take an item's `ItemFacts`; a set chosen on conditions tests even a lone row; `AT_MOST_ONE` covers an item no row is for. T1 `condition/`: `ItemFactIs` (the 14th predicate), `ItemFacts`, `ConditionContext.item` / `about`; `id/`: `ItemFactId`. T4 pack: seven item sets (`RS-PERQ-CAR-PARTLY`, `-OWNRUN`, `RS-PERQ-ACCOM`, `RS-RET-ENTRY`, `RS-RET-AGGREGATE`, `RS-PERQ-ASSET-TRANSFER`, `RS-RATE-CG-SPECIAL`) and `RS-DED-NPS-EMPLOYER` on `REGIME_AND_CONDITIONS`, their rows' `ItemFactIs` conditions (`vehicle.engine`, `accommodation.provider`, `retirement.entry`, `asset.kind`, `capitalgains.part`, `employer.kind`); 143 sets | `W-088m2`, D-253 |
| 2026-10-19 | T3 `pipeline`: `ComputationNode.ruleSets()` replaces `rules()`; `NodeContext.ruleOf` / `ruleIdOf` / `optionalRuleOf` / `rulesOf` choose a set's row by the set's declared selection (`EXACTLY_ONE`, `AT_MOST_ONE`, `ALL_THAT_APPLY`) and ground (`REGIME`, `REGIME_AND_CONDITIONS`); I3 counts set members. T4 pack: `law/rule-sets.json`, each rule row's `ruleSet` (and `ruleSetPosition` for an ordered set); `RulePack.ruleSets`, `RuleSet`, `RuleSetId`, generated `RuleSetIds`; `PackValidator` refuses a row in no set and an empty `EXACTLY_ONE` set; `SalaryBases.of(RuleSet, …)` | `W-088m1`, D-252 |
| 2026-10-19 | T2: `portfolio`'s read-model bucket gains `acquisition` (`Acquisition`: `AcquisitionMode` + the `CorporateActionType`) and `acquiredOn` — facts only; `trade_outcomes.acquisition_mode`, `acquisition_corporate_action` | `W-088f`, D-249 |
| 2026-10-19 | Content: coverage S2 s.67 / 1961 s.45 `MODELLED`, s.13, s.91 / s.14, s.55A `NOT_COMPUTATIONAL`, s.86 / s.54F `PLANNED` → W-088i, the computation sections and other exemptions `DEFERRED BUILT_SECOND`, business-asset sections `DEFERRED BUILT_LATER`, 1961 ss.54E–54EE `OUT_OF_SCOPE` (spent); catalogue entry `SEC-CAPITAL-GAINS-CHARGE` | `W-088e`, D-248 |
| 2026-10-18 | T6: `setoff` sets off capital losses carried in (`CarriedCapitalLossSetOff`, `SETOFF-CARRIED-CAPITAL-GAINS`); T2 `input`: `LedgerSnapshot.capitalLossCarriedForward`, `CarriedCapitalLoss`; `CapitalGainsInput.broughtForward` removed | `W-088d`, D-247 |
| 2026-10-18 | T6: `tax.surcharge` splits the tax: dividends' share and special-rate tax limited to 15% (`RATE-SUR-NEW-TAX-REGIME-LIMIT`, `RATE-SUR-FIRST-SCHEDULE-LIMIT`), special gains out of the excluding income, reads `setoff`; both refusals lifted | `W-088c`, D-246 |
| 2026-10-18 | T6: `tax.bybasis` charges the special-rate capital gains (three rate rows, `RATE-CG-SHORTFALL-ORDER`) and reads `setoff`; `chapterviii` reads `setoff` and works from GTI less those gains; `gti`'s refusal removed; `rules.definition`: `SpecialRateShortfallOrder` | `W-088b`, D-244 |
| 2026-10-18 | T6: `capitalgains.income` built (`CapitalGainsHead`); `setoff` produces `HeadsAfterSetOff` with `CAPITAL_GAINS` in the absorption order; `gti` refuses special-rate parts until W-088b; T3 `pipeline`: two `NodeValue` variants; `rules.definition`: `CapitalGainsPart`, `CapitalGainsAbsorptionOrder` | `W-088a`, D-243 |
