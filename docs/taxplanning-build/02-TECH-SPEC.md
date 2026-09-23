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
| `pack/` | `RulePack` `RulePackLayer` `RulePackRef` `RuleRow` `RulePackProvider` `ClasspathRulePackProvider` `PackAssembler` `RegimeApplicability` `MidYearCombination` `DisallowanceSet` `ConditionSet` |
| `regime/` | `RegimeSpec` `RegimeLifecycle` `ContinuationPolicy` `InvalidationPolicy` `ElectionProtocol` `ElectionKind` `ElectionVehicle` `ElectionRoute` `ElectionState` `BindingSpec` `AfterWithdrawal` `DeadlineSpec` `FormRef` `PipelineSpec` `BehaviourId` `DisallowanceSetId` `ConditionSetId` `RegimeBehaviour` `RegimeBehaviourRegistry` `DataDrivenRegimeBehaviour` |
| `component/` | `ComponentDefinition` `ComponentLifecycle` `DeprecationPolicy` `CashNature` `ComponentKind` `TaxTreatment` `RateBasisHint` |
| `registry/` | `TaxYearRegistry` `TaxYearRegistryEntry` `UnsupportedTaxYearException` `GoverningInstruments` `Instrument` `Provenance` `VerificationStatus` `DraftOrFinal` |
| `validate/` | `PackValidator` `PackValidationException` |
| `json/` | `PackJson` `ValueJson` `ShapeJson` `ConditionJson` `RuleJson` `RegimeJson` `RegistryJson` `JsonPackAssembler` — hand-written parsing (D-019) |
| `generated/` | `RuleIds` `ComponentCodes` — generated from the packs, committed, verified (`W-032b`) |

### `taxengine.rules` — planned

| Package | Types | Item |
|---|---|---|
| `registry/` | `LegalStatus` `VerificationLevel` `PublicationStatus` — **replace** `VerificationStatus` and `DraftOrFinal` | ○ `W-045e` |
| `timeline/` | `Timeline` `TimelineReader` · `RulePackCompiler` | ○ `W-045f` · ○ `W-045g` |
| `validate/` | `PackDiff` (**`W-139` reuses it** for the cross-year diff) · `PackLinter` `Finding` | ○ `W-045h` · ○ `W-139` |
| `generated/` | `NodeIds` | ○ `W-045i` |
| `coverage/` | `CoverageRegister` `SectionRef` `Coverage` | ○ `W-045j` |
| `catalogue/` | `SectionCatalogueEntry` `Route` `Disqualifier` `DisqualifierKind` · `ClaimCatalogueEntry` `ClaimStep` `ClaimChannel` `Counterparty` `Materiality` | ○ `W-045k` · ○ `W-117` |
| `relief/` | `ReliefShape` (sealed) `FlatWithCeiling` `ExcessOverThreshold` `ThresholdLadderCeiling` `NoRelief` | ○ `W-048`, `W-055` |
| `filing/` | `ItrFormSpec` `ItrSchedule` `ScheduleField` `FieldSource` `FurnishingOption` `ReturnKind` | ○ `W-090`, `W-092` |

### `taxengine` — ✅ built

| Package | Types |
|---|---|
| `input/` (named interface `input`) | `EngineInputs` (10 components: `taxYear` `regime` `temporal` `entered` `taxpayer` `family` `ledgers` `elections` `capitalGains` `filing`) `TaxpayerFacts` `FamilySnapshot` `LedgerSnapshot` `RegimeElectionSnapshot` `FilingFacts` `EnteredFacts` `CapitalGainsInput` `InputsFingerprint` |
| `pipeline/` | `ComputationNode` `NodeContext` `NodeResult` `NodeRegistry` `NodeValue` (sealed: **`MoneyValue`, `TaxByBasis` only**) `SpecialRateId` `TopologicalSort` `PipelineRunner` |
| `trace/` | `TraceLine` `TraceAnnotation` `Notice` `StepKind` `Severity` |
| `result/` (named interface `result`) | `TaxComputation` `FilingPosition` `ComputationId` `ComputationReceipt` |

### `taxengine` — planned

| Package | Types | Item |
|---|---|---|
| `input/` | `Assessee` (sealed) `Individual` `Huf` — **replace** `TaxpayerFacts`; `PersonType` gains `HUF` | ○ `W-045l` |
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
`RegimeAdvisor`, `ItrFormAdvisor`. A new class may not reuse a simple name that exists anywhere in
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

### Today — ✅ built (`W-027`, `W-028`, `W-032`)

```
backend/src/main/resources/rulepacks/2026-27/
  manifest.json                 layer versions + checksums
  law/      rates.json regimes.json
  exec/     pipelines.json components.json
  forms/    itr-forms.json
  content/  explanations.json
```

15 rule rows, all `VERIFIED`; TY 2026-27 only.

### Target — ○ `W-045f`, `W-045g` (D-034)

```
backend/src/main/resources/rulepacks/
  timeline/
    law/rules/<RuleId>.json            one file per rule; versions inside (unchanged law = one version spanning years)
    law/regimes/<RegimeCode>.json      one file per regime; lifetime inside
    exec/pipelines/  exec/components/<ComponentCode>.json
    forms/itr/<ItrFormCode>.json
    content/sections/<SectionKey>.json the section catalogue (W-045k)
    content/coverage.json              the coverage register (W-045j)
  2025-26.lock.json  2026-27.lock.json  the compiled rule pack's identity: version ids + per-layer checksums
```

`RulePackCompiler` builds a year's rule pack from every **`PUBLISHED`** version whose validity touches
that year and verifies it against the lock; a mismatch **fails startup** naming the rule. Packs are
compiled at startup only, never on a request path.

---

## T5 — Invariants

One table. **Where** says what enforces it; a check that lives in a constructor or the provider has
no validator branch, because it would be unreachable.

| # | Invariant | Where enforced | Status |
|---|---|---|---|
| I1 | `status != VERIFIED` ⟹ non-blank `whatToCheck` | `Provenance` constructor | ✅ `W-010` |
| I2 | every `RuleId` referenced anywhere in the pack resolves (rate tables, `feedsInto`, `salaryBaseFor`, bases, interim rules) | `PackValidator` | ✅ `W-029` |
| I3 | every rule in the pack is evaluated by some node | `NodeRegistry.rulesNotEvaluatedByAnyNode` | ✅ built `W-039`; **switched on at `W-053`** as a shrink-only ratchet |
| I4 | each layer's checksum matches its content | `ClasspathRulePackProvider` | ✅ `W-028` |
| I5 | exactly one `isDefault` regime per `(taxYear, PersonType)` among regimes **applicable that year** (I11 is the same check) | `PackValidator` | ✅ `W-029` |
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

Section-catalogue invariants **SC-1…SC-5** are in `00-BRIEF.md` §6.3 (○ `W-045k`); coverage-register
checks in §7.1 (○ `W-045j`).

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
| `taxengine` catches no `Exception`/`RuntimeException` | repo-wide Checkstyle `IllegalCatch`; `taxengine_does_not_swallow_failures` (runner packages only — see ⚠) | ✅ `W-004`, `W-045a` |
| `taxengine.rules` does not depend on `input`, `pipeline`, `trace`, `result` | `rules_do_not_depend_on_the_runner` | ✅ `W-045b2` |
| no `ComputationStep` anywhere | `ArchitectureTest` (plain test) | ✅ `W-004` |
| overriding `RegimeBehaviour.adjustPipeline` is listed against a reviewed file | `ArchitectureTest` (plain test, L5 guard 3) | ✅ `W-037c` |
| no generic class suffixes; unique simple class names | `classes_have_no_generic_suffix`, `simple_class_names_are_unique` | ✅ `W-045a` |
| Checkstyle rule set; legacy baseline only shrinks | `checkstyle.xml`, `CheckstyleBaselineTest` | ✅ `W-045a` |
| formatting | `spotless:check` on `validate` | ✅ `W-045a` |
| every `ComputationNode` implementation is referenced by some pack pipeline | `ArchitectureTest` | ○ `W-053` (with I3) |
| every `@RequestBody` carries `@Valid`; no controller returns an `@Document` type | `ArchitectureTest` | ○ `W-095` |

⚠ **`taxengine_does_not_swallow_failures` is miswritten.** It forbids *depending on* `Exception` /
`RuntimeException`, which also forbids **defining** an exception — so it excludes `taxengine.rules`
(which has four exception types) to keep its pre-D-048 scope, and it will reject `W-105`'s
`TaxEngineException`. What it means — *no catch* — is already enforced repo-wide by Checkstyle
`IllegalCatch`. Open question for the owner in `STATE.md`; must be settled before `W-105`.

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
