# 02 — Technical Specification

The reference. When a work item names a type, package, collection or endpoint, **it is defined here**.
Do not invent a name that is not in this file. If something you need is missing, mark the item
`BLOCKED` — do not create it.

---

## T1 — Modules

| Module | Package | `allowedDependencies` | May it touch a database? | May it read a clock? |
|---|---|---|---|---|
| `taxrules` | `com.thiru.wealthlens.taxrules` | `{"shared"}` | no | no |
| `taxengine` | `com.thiru.wealthlens.taxengine` | `{"shared", "taxrules"}` | **no** | **no** |
| `taxdocs` | `com.thiru.wealthlens.taxdocs` | `{"shared", "taxrules", "taxengine"}` | no | yes |
| `taxplanning` | `com.thiru.wealthlens.taxplanning` | `{"shared", "auth", "taxrules", "taxengine", "taxdocs", "portfolio"}` | yes | yes |

All four are `@ApplicationModule(type = Type.CLOSED, ...)`.

> **Erratum (W-035, 2026-09-21):** **T1 as written cannot be satisfied** — it declares all four
> modules `CLOSED` *and* has them depend on each other's types, but every shared type lives in a
> sub-package, and a `CLOSED` module exposes only its root package. `taxengine` declared
> `allowedDependencies = {"shared", "taxrules"}` exactly as this table specifies and still failed:
> `Module 'taxengine' depends on module 'taxrules' via ...pipeline.MoneyValue -> ...taxrules.money.Money.`
> The dependency was allowed; the *package* was not. Spring Modulith permits that only through
> `@NamedInterface`, which T1 never mentions and nothing in this repository uses. **Correction:
> `taxrules` is `Type.OPEN`; `taxengine`, `taxdocs` and `taxplanning` remain `CLOSED`** (decision
> **D-022**, blocker **B-002**). `taxrules` is the shared vocabulary of the domain and has no
> internals to hide; the three modules that do have internals keep `CLOSED`, so T1's actual
> objective — the engine cannot reach a repository, `taxplanning` cannot reach engine internals —
> is unchanged. The alternative, `@NamedInterface` on all twelve `taxrules` sub-packages plus a
> per-package dependency list in all three consumers, yields the same visible surface declared
> twelve times over. This was invisible until now because `taxengine` had no classes; `W-035` is
> its first.
> Verified by: `./mvnw test -pl backend -Punit` — 1225 tests, 0 failures, 0 errors (was
> `Errors: 1`, `WealthLensModulithTest`); `backend/src/main/java/com/thiru/wealthlens/taxrules/package-info.java`.

---

## T2 — Package map

```
taxrules/
  pack/        RulePack RulePackLayer RulePackRef RulePackProvider ClasspathRulePackProvider
  id/          RuleId NodeId ComponentCode RegimeCode ItrFormCode ScheduleCode
  cap/         CapShape Fixed PercentOfBase LeastOf AggregateWithCeiling SlabLadder PerUnitPerPeriod
  relief/      ReliefShape FlatWithCeiling ExcessOverThreshold ThresholdLadderCeiling NoRelief
  condition/   Condition (+ 16 records)
  temporal/    TemporalAxis TemporalContext Validity RuleVersion AmendmentRef DateRange
  regime/      RegimeSpec RegimeLifecycle ElectionProtocol BindingSpec AfterWithdrawal
               ContinuationPolicy InvalidationPolicy RegimeApplicability
  component/   ComponentDefinition ComponentLifecycle DeprecationPolicy CashNature ComponentKind
  filing/      ItrFormSpec ItrSchedule ScheduleField FieldSource FurnishingOption ReturnKind
  catalogue/   ClaimCatalogueEntry ClaimStep ClaimChannel Counterparty Materiality
  registry/    TaxYearRegistry TaxYearRegistryEntry GoverningInstruments Instrument Provenance
  validate/    PackValidator PackLinter PackDiff Finding  (+ invariants I1..I29)

taxengine/
  money/       Money Rate            ← the ONLY package that may call BigDecimal.setScale
  input/       EngineInputs TaxpayerFacts FamilySnapshot LedgerSnapshot
               RegimeElectionSnapshot CapitalGainsSummary FilingFacts InputsFingerprint
  pipeline/    ComputationNode NodeContext NodeResult NodeValue PipelineRunner
               TopologicalSort IncrementalRunner NodeRegistry
  node/        one class per node — see T6
  trace/       TraceLine TraceAnnotation Notice StepKind Severity
  result/      TaxComputation FilingPosition ComputationId ComputationReceipt
  election/    Election ElectionResolver ElectionOutcome
  proration/   ProrationBasis CompletedMonths CalendarMonths Days OccupationPeriods
  interest/    InterestAndFeeNode InterestBreakdown SimpleInterest TieredFee
  itr/         ItrFormSelector ItrSelection FormVerdict ScheduleMapper
  error/       TaxEngineException (sealed) + 8 subclasses, TaxError

taxdocs/
  ingest/      IngestedDocument ParseAttempt ExtractedField DocumentConfirmation
  parser/      DocumentParser Form130Parser Form16Parser
  reconcile/   ReconciliationReport ReconciliationLine

taxplanning/
  profile/     TaxpayerProfile Pan FamilyMember Employer Landlord Lender BankAccount
  workspace/   TaxYearWorkspace FilingDraft DraftRevision DraftInputs EntryProvenance
  scenario/    Scenario ScenarioDiff DiffLine
  assemble/    EngineInputsAssembler          ← the ONLY place EngineInputs is built
  portfolio/   MoneyBridge CapitalGainsAdapter
  projection/  ForecastProjection CalendarProjection CompletenessProjection TimelineProjection
  controller/  ...Controller (DTOs only)
  job/         TaxJob + 6 implementations, JobLease
```

---

## T3 — Foundational types, verbatim

Copy these exactly. Do not add methods not listed without a work item saying so.

### `Money` — `taxengine/money/Money.java`

> **Erratum (W-013, 2026-09-19):** `Money` and `Rate` do **not** live in `taxengine.money` —
> they live in **`taxrules.money`**. `taxrules` may not depend on `taxengine` (T1), and a rule
> pack holds rupee figures by its nature — slab band bounds, the 80C ceiling, the standard
> deduction — so a cap shape in `taxrules.cap` cannot express a limit without them. Decided as
> option (A) of `STATE.md` **B-001**.
> Verified by: `./mvnw test -pl backend -Dtest=WealthLensModulithTest` failed with
> *"Module 'taxrules' depends on module 'taxengine' via …CapResult -> …taxengine.money.Money"*
> before the move, and passes after it.

```java
package com.thiru.wealthlens.taxengine.money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * An exact rupee amount. TL-8 §7 trigger: figures filed on someone else's behalf need exact
 * decimal storage. This is the ONLY package permitted to call BigDecimal.setScale — see
 * ArchitectureTest.money_rounding_lives_in_one_place.
 */
public record Money(BigDecimal amountInRupees) implements Comparable<Money> {

    public static final int SCALE = 2;
    public static final MathContext CALC = new MathContext(12, RoundingMode.HALF_UP);
    public static final Money ZERO = of("0");
    public static final Money TEN  = of("10");

    public Money {
        if (amountInRupees == null) throw new IllegalArgumentException("Money may not be null");
        amountInRupees = amountInRupees.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static Money of(String rupees)        { return new Money(new BigDecimal(rupees)); }
    public static Money ofExact(BigDecimal raw)  { return new Money(raw); }

    public Money plus(Money o)  { return ofExact(amountInRupees.add(o.amountInRupees)); }
    public Money minus(Money o) { return ofExact(amountInRupees.subtract(o.amountInRupees)); }
    public Money floorAtZero()  { return amountInRupees.signum() < 0 ? ZERO : this; }

    public boolean isLessThan(Money o)        { return compareTo(o) < 0; }
    public boolean isLessThanOrEqual(Money o) { return compareTo(o) <= 0; }
    public boolean isGreaterThan(Money o)     { return compareTo(o) > 0; }

    public static Money min(Money a, Money b) { return a.isLessThan(b) ? a : b; }
    public static Money max(Money a, Money b) { return a.isLessThan(b) ? b : a; }

    /**
     * Section 516 of the Income-tax Act 2025: ignore paise, then round to the nearest multiple
     * of ten — up if the last figure is five or more, down if less. Applied to the magnitude,
     * sign restored (13-edge-cases.md R-9, rule ROUND-NEG-001, STATUS UNCLEAR).
     */
    public Money roundToNearestTenPerSection516() {
        BigDecimal whole = amountInRupees.abs().setScale(0, RoundingMode.DOWN);
        BigDecimal rem   = whole.remainder(BigDecimal.TEN);
        BigDecimal out   = rem.compareTo(BigDecimal.valueOf(5)) >= 0
                ? whole.subtract(rem).add(BigDecimal.TEN)
                : whole.subtract(rem);
        return new Money(amountInRupees.signum() < 0 ? out.negate() : out);
    }

    @Override public int compareTo(Money o) { return amountInRupees.compareTo(o.amountInRupees); }
    @Override public String toString() { return amountInRupees.toPlainString(); }
}
```

### `Rate` — `taxengine/money/Rate.java`

> **Erratum (W-013, 2026-09-19):** as for `Money` above — the package is
> `com.thiru.wealthlens.taxrules.money`, not `taxengine.money`. See `STATE.md` **B-001**.

```java
package com.thiru.wealthlens.taxengine.money;

import java.math.BigDecimal;

/** A statutory rate. "30" and "0.30" must never be the same type. */
public record Rate(BigDecimal fraction) implements Comparable<Rate> {

    public static final Rate ZERO = percent("0");

    public static Rate percent(String p) {
        return new Rate(new BigDecimal(p).divide(BigDecimal.valueOf(100), Money.CALC));
    }

    /** Applies the rate; rounds to the money scale ONCE, at the boundary. */
    public Money of(Money base) {
        return Money.ofExact(base.amountInRupees().multiply(fraction, Money.CALC));
    }

    public String asPercentLabel() {
        return fraction.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    @Override public int compareTo(Rate o) { return fraction.compareTo(o.fraction); }
}
```

### Identifier types — `taxrules/id/`

```java
public record RuleId(String value) {          // ^[A-Z]{2,8}(-[A-Z0-9]{1,12}){1,3}$
    public RuleId { requirePattern(value, "^[A-Z]{2,8}(-[A-Z0-9]{1,12}){1,3}$", "RuleId"); }
}
public record NodeId(String value) {          // ^[a-z][a-z0-9.]{2,63}$
public record ComponentCode(String value) {   // ^[A-Z][A-Z0-9_]{1,63}$
public record RegimeCode(String value) {      // ^[A-Z][A-Z0-9_]{1,31}$
public record ItrFormCode(String value) {     // ^[A-Z0-9-]{3,16}$
public record ScheduleCode(String value) {    // ^[A-Z0-9-]{3,16}$
public record TaxYearId(int startYear) {
    public String label() { return startYear + "-" + ((startYear + 1) % 100); }
    public java.time.LocalDate startsOn() { return java.time.LocalDate.of(startYear, 4, 1); }
    public java.time.LocalDate endsOn()   { return java.time.LocalDate.of(startYear + 1, 3, 31); }
}
```

> **Erratum (W-008, 2026-09-19) — two corrections to the block above. Copy from here, not from it.**
>
> **1. `RuleId` was `{1,3}`, which rejects 10 real rule IDs.** Every ID with four hyphenated segments
> fails it: `RATE-SLAB-OLD-2025-A/B/C`, `RATE-SLAB-OLD-2026-A/B`, `SAL-ALW-TRANSPORT-DIS-2026`,
> `SAL-ALW-DUTY-LIST-2026`, `SAL-ALW-NEW-SUBSET-001`, `DED-80C-NPS-CAP-001`, `SAL-DEF-80C-NPS-001`.
> Four is the observed maximum across **309** rule IDs in the design; `{1,5}` leaves one segment of
> headroom without weakening a check that exists to catch case and separator errors, not segment
> counts.
>
> **2. `label()` did not zero-pad the ending year.** `2100 % 100` is `0`, so the tax year beginning
> in 2099 rendered as `"2099-0"`. The synthetic pack that proves the architecture's extensibility is
> **TY 2099-00**, so this boundary is exercised, not hypothetical.
>
> ```java
> private static final Pattern PATTERN = Pattern.compile("^[A-Z]{2,8}(-[A-Z0-9]{1,12}){1,5}$");
>
> public String label() { return "%d-%02d".formatted(startYear, (startYear + 1) % 100); }
> ```
>
> Verified by: a scan of all 309 rule IDs under `docs/taxplanning-design/`, and of every real
> `NodeId`, `ComponentCode`, `RegimeCode`, `ItrFormCode` and `ScheduleCode` value — **the other five
> patterns are correct.**

---

## T4 — The four pack layers

```
backend/src/main/resources/rulepacks/<taxYear>/
  manifest.json                     versions + checksums, one per layer
  law/      rates.json salary.json perquisites.json retirement.json deductions.json
            houseproperty.json othersources.json setoff.json tds.json filing.json
            regimes.json disallowances.json conditions.json
  exec/     pipelines.json components.json axes.json
  forms/    itr-forms.json itr-schedules.json
  content/  claim-catalogue.json explanations.json
```

| Layer | May change a computed number? |
|---|---|
| `law/` | **yes — the only one** |
| `exec/` | yes, by selecting which law applies |
| `forms/` | **no** |
| `content/` | **no — property-tested (P3)** |

The compute cache keys on **`law` + `exec` only**.

---

## T5 — Pack invariants (checked at load; `PackValidationException` on failure)

| | Invariant |
|---|---|
| I1 | `status != VERIFIED` ⟹ `whatToCheck` non-blank |
| I2 | every `RuleId` a pipeline references exists |
| I3 | every `RuleId` in the pack is reachable from some pipeline |
| I4 | each layer's checksum matches its content |
| I5 | exactly one `isDefault` regime per `(taxYear, PersonType)` among **applicable** regimes |
| I6 | every regime's disallowance/condition set ids resolve |
| I7 | every `ComponentCode` a rule references exists in `components.json` |

> **Erratum (W-029, 2026-09-19):** three of I1–I7 are not checked by `PackValidator`, each for a
> different reason.
>
> - **I1** is enforced by `Provenance`'s compact constructor (`W-010`). A row with
>   `status != VERIFIED` and a blank `whatToCheck` cannot be *constructed*, so a validator branch
>   would be unreachable. Asserted as a property of `Provenance` instead.
> - **I4** is enforced by `ClasspathRulePackProvider` (`W-028`), which hashes the bytes it read.
>   Re-hashing in the validator would mean two implementations to keep in step.
> - **I3** is **not yet checkable and is deferred.** Reachability means "evaluated by some node",
>   and the node-to-rule mapping is `ComputationNode.rules()` — engine-side, `W-039`. A pack-only
>   version would have to count a rule as reachable because a component mentions it, which passes
>   exactly the dead rule O-19 describes. It needs its own item once the node registry exists.
>
> **I2 is implemented more strongly than worded.** T5 says "every `RuleId` a pipeline references",
> but a `PipelineSpec` holds `NodeId`s. The implemented check is *every `RuleId` referenced anywhere
> in the pack resolves* — rate tables, `feedsInto`, `salaryBaseFor`, policy bases, and a
> `PendingNotification`'s interim rule.
>
> **I6 became checkable in this item.** `DisallowanceSet` and `ConditionSet` — the things
> `DisallowanceSetId` and `ConditionSetId` name — were modelled by no type and no work item, and
> `RulePack` had no field for them. Added by the developer's decision.
> Verified by: `./mvnw test -pl backend -Dtest=PackValidatorTest`.
| I8 | a cross-year ledger entry is consumed by at most one `ACTED_ON` draft per year |
| I9 | every `BehaviourId` resolves to a registered implementation |
| I10 | `entryWindow ⊆ inForceWindow` for every regime |
| I11 | exactly one `isDefault` per `(taxYear, PersonType)` among regimes **applicable in that year** (strengthens I5) |
| I12 | a `MigrateTo` successor exists and is applicable in the year its predecessor lapses |
| I13 | a rule's versions cover the tax year with no gap and no overlap (`TAX_YEAR` axis) |
| I14 | >1 version overlapping the year ⟹ `midYearCombination != UNDETERMINED` |
| I15 | every `RuleVersion` carries an `AmendmentRef` |
| I16 | `PendingNotification` names an `interimRule` present in the same pack |
| I17 | every `ComponentCode` a draft references resolves in the target pack, or has a `DeprecationPolicy` |
| I18 | a non-`TAX_YEAR` axis rule is supplied its coordinate by every node that evaluates it |

> **Erratum (W-039, 2026-09-21):** **I18 is already enforced, and needs no checker of its own.**
> `TemporalContext.on(axis)` throws `MissingTemporalCoordinateException` for any non-`TAX_YEAR`
> axis with no coordinate, and `RuleRow.versionAt(t)` calls it on **every** rule lookup — so a node
> evaluating such a rule without supplying the coordinate gets an exception rather than a plausible
> wrong figure. Asserted as a property instead, the same shape `W-029` used for **I1** (enforced by
> `Provenance`'s constructor, so a validator branch would be unreachable). A separate static check
> would need nodes to declare which axes they supply — machinery nothing else wants, duplicating a
> guarantee already in place.
>
> **I3 is implemented but deliberately not yet wired to the shipped pack.**
> `NodeRegistry.rulesNotEvaluatedByAnyNode` is the check `W-029`'s erratum deferred here. There are
> **zero** `ComputationNode` implementations until Phases 5-9, so running it against the real
> 2026-27 pack today reports every rule unreachable. It is proven against fixtures now and enabled
> at the item that first ships a real pipeline (decision **D-029**).
> Verified by: `./mvnw test -pl backend -Dtest=NodeRegistryTest` — 22 tests.
| I19 | at least one ITR form is applicable per `(PersonType, ReturnKind)` |
| I20 | no two ITR forms simultaneously eligible |
| I21 | every `ScheduleRef`, `FromRule`, `FromNode` resolves |
| I22 | every ITR form has at least one `FurnishingOption` whose conditions can be satisfied |
| I23 | every claimable rule has a catalogue entry |
| I24 | a catalogue entry's `channel` matches its rule's Chapter VIII Part; **Part C rules carry the s.122(5) late-return warning** |
| I25 | every `EvidenceRequirement` names what closes it |
| I26 | every `ClaimStep` with a deadline resolves against the tax calendar |
| I27 | perturbing `content/` and `forms/` changes no computed number |
| I28 | no occurrence of `ComputationStep` anywhere in docs or source |
| I29 | every `TaxEngineException` declares a `TaxError`; the advice switch is exhaustive over the sealed hierarchy |
| I30 | `taxengine` depends on no repository, no Spring Data, no `taxplanning` type, and calls no clock |
| I31 | every node's `requires()` is satisfied by a node's `produces()` or an `EngineInputs` field |
| I32 | `EngineInputs.fingerprint()` covers every field (reflection test) |

**All 30 are listed.** I1–I27 and I31–I32 are checked at pack load (`PackValidator`); I28, I29, I30
are build-time gates (ArchUnit and a CI `grep`). An item that says "implement the invariants" means
**these**, and nothing beyond them.

---

## T6 — Node catalogue

Order is **derived** from `requires()`/`produces()`. This table is documentation.

| `NodeId` | Class | requires | produces |
|---|---|---|---|
| `inputs.components` | `ComponentPartitionNode` | — | `ComponentSet` |
| `salary.perquisites` | `PerquisiteNode` | components | `MoneyValue` |
| `employer.contrib.charges` | `EmployerContributionNode` | components | `MoneyValue` |
| `salary.gross` | `GrossSalaryNode` | components, perquisites, charges | `MoneyValue` |
| `salary.base` | `SalaryBaseNode` | components | `SalaryBases` |
| `salary.exemptions` | `ExemptionNode` | gross, base | `MoneyValue` |
| `salary.retirement` | `RetirementNode` | components, ledgers | `MoneyValue` |
| `salary.deductions` | `SalaryDeductionNode` | gross | `MoneyValue` |
| `salary.income` | `SalaryIncomeNode` | gross, exemptions, retirement, deductions | `MoneyValue` |
| `elections.houses` | `HouseElectionNode` | properties | `ElectionOutcome` |
| `houseproperty.income` | `HousePropertyNode` | properties, elections.houses | `HeadResult` |
| `othersources.income` | `OtherSourcesNode` | othersources | `HeadResult` |
| `capitalgains.income` | `CapitalGainsNode` | portfolio snapshot | `HeadResult` |
| `setoff` | `SetOffNode` | all heads, ledgers | `HeadMap` |
| `gti` | `GrossTotalIncomeNode` | setoff | `MoneyValue` |
| `chapterviii` | `ChapterEightNode` | gti, components, family | `MoneyValue` |
| `totalincome` | `TotalIncomeNode` | gti, chapterviii | `MoneyValue` |
| `round.totalincome` | `RoundTotalIncomeNode` | totalincome | `MoneyValue` |
| `tax.bybasis` | `TaxByBasisNode` | round.totalincome, rate hints | **`TaxByBasis`** |
| `tax.afterrebate` | `RebateNode` | tax.bybasis | `TaxByBasis` |
| `tax.surcharge` | `SurchargeNode` | tax.afterrebate, round.totalincome | `MoneyValue` |
| `tax.cess` | `CessNode` | tax.afterrebate, tax.surcharge | `MoneyValue` |
| `credits` | `CreditsNode` | tds, advance tax | `MoneyValue` |
| `tax.netpayable` | `NetPayableNode` | cess, credits | `MoneyValue` |
| `interest.and.fees` | `InterestAndFeeNode` | netpayable, totalincome, filing facts | `InterestBreakdown` |
| `round.netpayable` | `RoundNetPayableNode` | netpayable, interest | `MoneyValue` |

---

## T7 — MongoDB collections

All `snake_case`; `@MongoId`; entities implement `AuditableEntity`. **Every one is added to
`AbstractIntegrationTest.cleanDatabase()`. None is added to `SEEDED_REFERENCE_COLLECTIONS`.**

| Collection | Key | Indexes |
|---|---|---|
| `tax_profiles` | `profile_id` | unique `{email}`; unique `{pan}` |
| `tax_family_members` | `family_member_id` | `{profile_id}` |
| `tax_employers` | `employer_id` | unique `{tan}` |
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

**Deliberately absent:** any collection holding rules, slabs, allowance limits or perquisite values.

---

## T8 — API

Base `/api/v1/tax-planning`. Wrapped in `ApiResponse<T>` except under the `integration-test` profile.

| Method | Path | Item |
|---|---|---|
| `GET` | `/tax-years` | W-095 |
| `GET` | `/user/{email}/workspaces/{taxYear}` | W-096 |
| `POST` | `/user/{email}/workspaces/{taxYear}/drafts` | W-097 |
| `GET`/`PATCH` | `/user/{email}/drafts/{draftId}` | W-098 |
| `POST` | `/user/{email}/drafts/{draftId}/fork` · `/status` · `/primary` | W-099 |
| `GET` | `/user/{email}/drafts/{draftId}/revisions[/{n}]` | W-100 |
| `POST` | `/user/{email}/drafts/{draftId}/compute` | W-101 |
| `POST` | `/user/{email}/drafts/{draftId}/compare-regimes` | W-102 |
| `GET` | `/user/{email}/drafts/{draftId}/forecast` | W-110 |
| `GET` | `/user/{email}/drafts/{draftId}/calendar` | W-111 |
| `GET` | `/user/{email}/drafts/{draftId}/completeness` | W-112 |
| `GET` | `/user/{email}/drafts/{draftId}/itr-suggestion` | W-090 |
| `GET` | `/user/{email}/drafts/{draftId}/itr-export` | W-092 |
| `GET` | `/user/{email}/drafts/{draftId}/claim-playbook/{ruleId}` | W-115 |
| `POST` | `/user/{email}/documents` · `GET .../parse` · `POST .../confirm` | W-128…W-130 |
| `GET` | `/user/{email}/computations/{computationId}[/report.pdf]` | W-105, W-120 |

Query parameters: `?view=summary|full|debug` (default `summary`); `ETag` = `inputsFingerprint` +
`lawVersion` + `execVersion`; `If-Match` required on `PATCH`.

---

## T9 — Error catalogue

```java
public enum TaxError {                     //  http  retryable  class
    UNSUPPORTED_TAX_YEAR                  (400, false, USER),
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

`PACK` and `ENGINE` page an operator. `USER` never does.

---

## T10 — ArchUnit rules to add

| Rule | Item |
|---|---|
| amend `money_rounding_lives_in_one_place` to also permit `..taxengine.money..` | W-002 |

> **Erratum (W-013, 2026-09-19):** the permitted package is `..taxrules.money..`. The same row's
> companion rule was also renamed `taxengine_uses_money_not_bigdecimal` →
> **`tax_modules_use_money_not_bigdecimal`**, and widened to `taxengine`, `taxrules` **and**
> `taxdocs`, since the type it protects now sits in `taxrules`.
> Verified by: `backend/src/test/java/com/thiru/wealthlens/architecture/ArchitectureTest.java:216`.
| `taxengine` must not access `TMoney` | W-002 |
| only `..taxplanning.portfolio..` may call `MoneyBridge.fromTMoney` | W-002 |
| `taxengine` must not depend on `..repository..`, `org.springframework.data..`, `..taxplanning..` | W-004 |
| `taxengine` must not call `LocalDate.now`, `Instant.now`, or use `Clock` | W-004 |
| no class in `taxengine` catches `Exception` or `RuntimeException` | W-004 |
| no `BigDecimal` field or parameter in `taxengine` outside `money/` | W-004 |
| every `ComputationNode` implementation is referenced by some pack node set | W-044 |
| every `@RequestBody` parameter carries `@Valid` | W-094 |
| no controller returns an `@Document`-annotated type | W-094 |
| CI `grep` gate: no occurrence of `ComputationStep` | W-004 |

---

## T11 — Build and test commands

```bash
./mvnw test -pl backend -Punit                      # the fast tier — run for EVERY item
./mvnw test -pl backend -Dtest=ClassName            # one class
./mvnw test -pl backend -Dtest=ClassName#method     # one method
./mvnw test -pl backend -Pintegration               # Testcontainers; Docker required
./mvnw spotless:apply                               # before every report
./mvnw clean install -pl backend -am -DskipTests     # compile only
```

---

## T12 — Conventions inherited from this repository

- `@RequiredArgsConstructor` for DI; injected fields `private final`. No hand-written constructors.
- Services: `@Service @Log4j2 @Transactional @RequiredArgsConstructor`.
- Entities: `@Data @AllArgsConstructor @NoArgsConstructor @Document @Field`, `snake_case` names.
- DTOs: `@Data @NoArgsConstructor @ToString`; no `@Builder` on request DTOs.
- Tests: `@ExtendWith(MockitoExtension.class)`, `// Given` `// When` `// Then`,
  `methodName_whenCondition_expectedResult`.
- Money assertions use `MoneyAssert`, never bare `assertEquals`.
- 4-space indent, K&R braces, no star imports.
