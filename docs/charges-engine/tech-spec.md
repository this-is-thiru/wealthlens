# Charges Engine — Technical Specification

**Date:** 2026-09-05
**Status:** Draft for review
**Start at** `README.md` — current state and how to resume. **Rationale** lives in `decisions.md`.
**Branch:** `feature/charges-engine`
**Precedent followed (shape only, no shared code):** the `taxplanning` policy layer — seeded JSON policy documents, a SpEL evaluator, a resolution service, pluggable engines. The charges module builds its own equivalents; nothing is imported across that boundary.

---

## 1. Design Summary

Replace a **fixed-schema rate card** with a **rule-list rate card**, and replace a **method-per-charge calculator** with a **strategy-per-basis engine**.

```
                    ChargeScheduleResolver              ChargeCalculator registry
                    (broker × asset × segment                 ┌─ TurnoverChargeCalculator
ChargeContext  ──▶   × exchange × plan × date)   ──▶ rules ──▶ ├─ FlatChargeCalculator
                            │                                  ├─ PerUnitChargeCalculator
                            ▼                                  ├─ SlabChargeCalculator
                    ChargeSchedule                             ├─ ScopedFlatChargeCalculator   (DP: per scrip/day)
                    └─ List<ChargeRule>                        ├─ DerivedChargeCalculator      (GST)
                                                               └─ FormulaChargeCalculator      (SpEL)
                                                                       │
                                                                       ▼
                                                              ChargeComputation
                                                              └─ List<ChargeLine> + total
                                                                       │
                              ┌────────────────────────────────────────┼──────────────────────┐
                              ▼                                        ▼                      ▼
                       UserChargeEntity                        ChargeSummaryReport      AssetEntity /
                       (audit, contract note)                  (P&L aggregation)        TransactionEntity
                                                                                        (cost basis)
```

Two axes of extensibility, deliberately separated:

- **New charge (common case)** → a new rule document. Zero Java.
- **New *kind* of charge maths (rare)** → a new `ChargeCalculator` implementation registered by `ChargeBasis`. One class, no changes to existing ones.

---

## 2. Package Layout

Module stays at `com.thiru.wealthlens.brokercharges` for now (see OD-1). All new types are named `Charge*`.

```
com.thiru.wealthlens.brokercharges
├── package-info.java                      (allowedDependencies unchanged: shared, portfolio, corporate)
├── controller/
│   ├── ChargeScheduleController.java      admin: publish / fetch / close schedules
│   ├── UserChargesController.java         user: charge history, contract-note view
│   └── ChargeSimulationController.java    "what would this trade cost?" — dry run, no persistence
├── dto/
│   ├── context/
│   │   ├── ChargeContext.java             engine input
│   │   └── ChargeComputation.java         engine output (record)
│   ├── enums/
│   │   ├── ChargeBasis.java
│   │   ├── ChargeCategory.java
│   │   ├── ChargeEvent.java
│   │   ├── ChargeSide.java
│   │   ├── DedupeScope.java
│   │   ├── AggregatorType.java            (replaces BrokerageAggregatorType)
│   │   ├── RoundingPolicy.java
│   │   └── TradeSegment.java              → see §9, lives in portfolio (shared vocabulary)
│   ├── request/
│   │   ├── ChargeScheduleRequest.java
│   │   ├── ChargeRuleRequest.java
│   │   └── ChargeSimulationRequest.java
│   └── response/
│       ├── ChargeBreakdownResponse.java
│       └── ChargeScheduleResponse.java
├── entity/
│   ├── ChargeScheduleEntity.java          collection: charge_schedules
│   ├── ChargeRule.java                    embedded document
│   ├── ChargeSlab.java                    embedded document
│   ├── ChargeLine.java                    embedded document (a computed line item)
│   ├── UserChargeEntity.java              collection: user_charges
│   ├── ChargeCatalogueEntity.java         collection: charge_catalogue  (code registry, OD-4)
│   └── ChargeAccountEntity.java           collection: charge_accounts — broker account for AMC / opening cycles (§9.4)
├── repository/
│   ├── ChargeScheduleRepository.java
│   ├── UserChargeRepository.java
│   └── ChargeCatalogueRepository.java
├── config/
│   └── ChargeEngineProperties.java        @ConfigurationProperties("app.charges") — phase flags, cache toggle
├── engine/
│   ├── ChargeEngine.java                  orchestrator
│   ├── ChargeCalculator.java              strategy interface
│   ├── ChargeCalculatorRegistry.java      basis → calculator
│   ├── ChargeAccumulator.java             mutable evaluation state
│   ├── ChargeFormulaEvaluator.java        charges-owned SpEL evaluator (§5.6)
│   ├── ChargeRounding.java                rounding helpers
│   └── calculator/
│       ├── TurnoverChargeCalculator.java
│       ├── FlatChargeCalculator.java
│       ├── PerUnitChargeCalculator.java
│       ├── SlabChargeCalculator.java
│       ├── ScopedFlatChargeCalculator.java
│       ├── DerivedChargeCalculator.java
│       └── FormulaChargeCalculator.java   (uses ChargeFormulaEvaluator)
└── service/
    ├── ChargeScheduleService.java         publish / supersede / fetch
    ├── ChargeScheduleResolver.java        most-specific-match selection + caching
    ├── ChargeScheduleValidator.java       write-time validation (FR-2)
    ├── ChargeSeederService.java           @PostConstruct seed from resources/data/charges/
    ├── UserChargeService.java             compute + persist + query
    ├── ChargeAccountService.java          CRUD over charge_accounts
    └── AmcChargeService.java              AMC cycle billing, self-contained in this module
```

Seed data: `backend/src/main/resources/data/charges/`
```
charge-catalogue.json
zerodha-equity-delivery-2025-04-01.json
zerodha-equity-intraday-2025-04-01.json
upstox-equity-delivery-2025-04-01.json
fyers-equity-delivery-2025-04-01.json
zerodha-mutual-fund-2025-04-01.json
```

---

## 3. Enumerations

```java
public enum ChargeBasis {
    TURNOVER,       // rate % of context.turnover
    FLAT,           // fixed amount per event
    PER_UNIT,       // amount × quantity (per share / per lot)
    SLAB,           // tiered by turnover — List<ChargeSlab>
    SCOPED_FLAT,    // FLAT, charged at most once per dedupe scope (DP charges)
    DERIVED,        // rate % over the summed amounts of baseCodes (GST)
    FORMULA         // SpEL over context attributes + accumulator (MF exit load)
}

public enum ChargeCategory {
    BROKERAGE,      // broker's own fee
    STATUTORY,      // STT, stamp duty — government, never GST-able
    EXCHANGE,       // exchange txn charges, IPFT, clearing
    REGULATORY,     // SEBI turnover fee
    DEPOSITORY,     // DP charges
    TAX,            // GST
    SUBSCRIPTION,   // AMC, account opening, platform fees
    FUND            // MF exit load, transaction fee
}

public enum ChargeEvent {
    BUY, SELL,                       // trade sides
    ACCOUNT_OPENING, AMC_CYCLE,      // billing occasions
    CALL_AND_TRADE, AUTO_SQUARE_OFF, PLEDGE   // reserved, unseeded
}

public enum ChargeSide { BUY, SELL, BOTH }

public enum DedupeScope {
    NONE,
    PER_SCRIP_PER_DAY,     // DP charge
    PER_ORDER,
    PER_DAY
}

public enum AggregatorType { MIN, MAX }   // replaces BrokerageAggregatorType

public enum RoundingPolicy { NONE, HALF_UP_2, CEILING_2, HALF_UP_0 }

public enum AmountBasis {
    TURNOVER,     // price × quantity — the only basis used in Phase A
    NOTIONAL,     // futures: price × lotSize × lots
    PREMIUM,      // options: premium × quantity
    INTRINSIC,    // exercised options: (settlement − strike) × quantity
    PRINCIPAL     // amount invested / remitted
}

public enum SlabBandBasis {
    TURNOVER,      // full-service brokerage tiers
    HOLDING_DAYS,  // graded mutual fund exit loads
    QUANTITY
}

public enum ChargeRuleSource { SCHEDULE, INSTRUMENT }   // provenance on each computed line

public enum FundCategory {
    EQUITY, DEBT, HYBRID, LIQUID, ELSS, INDEX, ETF, FUND_OF_FUNDS, OTHER
}

public enum PlanType { DIRECT, REGULAR }   // decides whether a distributor fee can apply at all

public enum ChargeResolution {
    RESOLVED, NO_MATCHING_RULES, NO_SCHEDULE, NO_INSTRUMENT_PROFILE, PROVISIONAL
}

// lives in portfolio.dto.enums — shared trade vocabulary, see §9.1
public enum TradeSegment { DELIVERY, INTRADAY, FUTURES, OPTIONS, NA }
```

`ChargeEvent` deliberately splits trade sides from billing occasions (fixes D5). `ChargeSide` is what a rule declares; `ChargeEvent` is what actually happened.

---

## 4. Persistence Model

### 4.1 `ChargeScheduleEntity` — collection `charge_schedules`

| Field | Mongo | Type | Notes |
|---|---|---|---|
| `id` | `_id` | String | `@MongoId` |
| `scheduleCode` | `schedule_code` | String | human key, e.g. `ZERODHA_EQ_DELIVERY_2025Q1` |
| `brokerName` | `broker_name` | `BrokerName` | **required** |
| `assetType` | `asset_type` | `AssetType` | null = any |
| `segment` | `segment` | `TradeSegment` | null = any |
| `exchange` | `exchange` | String | null = any (fixes D4) |
| `planCode` | `plan_code` | String | null = any (OD-5) |
| `startDate` | `start_date` | LocalDate | inclusive |
| `endDate` | `end_date` | LocalDate | inclusive, null = open-ended (fixes D6) |
| `status` | `status` | `EntityStatus` | |
| `currency` | `currency` | String | default `INR` |
| `rules` | `rules` | `List<ChargeRule>` | |
| `sourceUrl` | `source_url` | String | broker rate-card URL, for verification |
| `verifiedOn` | `verified_on` | LocalDate | when a human last checked the rates |
| `auditMetadata` | `audit_metadata` | `AuditMetadata` | implements `AuditableEntity` |

Indexes: compound `{broker_name:1, asset_type:1, segment:1, start_date:-1}`, plus `{status:1}`.

### 4.2 `ChargeRule` — embedded

| Field | Mongo | Type | Notes |
|---|---|---|---|
| `code` | `code` | String | e.g. `STT`, `GST`, `DP`; unique within schedule; must exist in `charge_catalogue` |
| `displayName` | `display_name` | String | contract-note label |
| `category` | `category` | `ChargeCategory` | |
| `basis` | `basis` | `ChargeBasis` | |
| `source` | `source` | `ChargeRuleSource` | set by the engine from where the rule was read |
| `side` | `side` | `ChargeSide` | |
| `events` | `events` | `Set<ChargeEvent>` | which occasions trigger it |
| `amountBasis` | `amount_basis` | `AmountBasis` | which context amount the rate applies to; default `TURNOVER` |
| `rate` | `rate` | Double | percent, for TURNOVER / DERIVED |
| `flatAmount` | `flat_amount` | Double | for FLAT / SCOPED_FLAT |
| `perUnitAmount` | `per_unit_amount` | Double | for PER_UNIT |
| `slabs` | `slabs` | `List<ChargeSlab>` | for SLAB |
| `slabBandBasis` | `slab_band_basis` | `SlabBandBasis` | which dimension the slabs band over; default `TURNOVER` |
| `perLot` | `per_lot` | boolean | evaluate once per FIFO lot and sum, rather than once per transaction. See §5.8 |
| `baseCodes` | `base_codes` | `List<String>` | for DERIVED — the taxable base (fixes D1) |
| `formula` | `formula` | String | for FORMULA — SpEL |
| `eligibility` | `eligibility` | String | optional SpEL predicate, e.g. `#holdingDays < 365` |
| `minAmount` | `min_amount` | Double | floor |
| `maxAmount` | `max_amount` | Double | cap |
| `aggregator` | `aggregator` | `AggregatorType` | when both `rate` and `flatAmount` are present |
| `dedupeScope` | `dedupe_scope` | `DedupeScope` | default NONE |
| `rounding` | `rounding` | `RoundingPolicy` | default `HALF_UP_2` |
| `order` | `order` | int | evaluation sequence; DERIVED must exceed its bases |
| `taxable` | `taxable` | boolean | convenience flag mirrored onto the emitted line |
| `active` | `active` | boolean | disable a rule without deleting it |
| `notes` | `notes` | String | why this rate, statute reference |

### 4.3 `ChargeSlab` — embedded
`fromValue`, `toValue` (null = ∞), `rate`, `flatAmount`. The banded quantity is named by the owning rule's `slabBandBasis`, so the same structure expresses a turnover tier and a graded exit load that tapers by holding period.

### 4.4 `ChargeLine` — embedded computed line item
`code`, `displayName`, `category`, `basis`, `rate`, `baseAmount`, `amount`, `taxable`, `ruleCode`.

### 4.5 `UserChargeEntity` — collection `user_charges` (replaces `user_broker_charges`)

| Field | Mongo | Type |
|---|---|---|
| `id` | `_id` | String |
| `email` | `email` | String |
| `brokerName` | `broker_name` | `BrokerName` |
| `assetType` | `asset_type` | `AssetType` |
| `segment` | `segment` | `TradeSegment` |
| `exchange` | `exchange` | String |
| `stockCode` | `stock_code` | String |
| `transactionId` | `transaction_id` | String |
| `event` | `event` | `ChargeEvent` |
| `transactionDate` | `transaction_date` | LocalDate |
| `scheduleId` / `scheduleCode` | `schedule_id` / `schedule_code` | String — provenance |
| `turnover` | `turnover` | double |
| `quantity` | `quantity` | double |
| `resolution` | `resolution` | `ChargeResolution` — why these lines, or why none. See §14 |
| `instrumentId` | `instrument_id` | String — provenance for scheme-sourced lines |
| `computedOn` | `computed_on` | LocalDateTime — distinct from `transactionDate`; a backfilled trade is computed long after it occurred |
| `lines` | `lines` | `List<ChargeLine>` — the contract note (G3) |
| `amountByCode` | `amount_by_code` | `Map<String, Double>` — denormalised for aggregation |
| `totalCharges` | `total_charges` | double |
| `auditMetadata` | `audit_metadata` | `AuditMetadata` |

Indexes: `{email:1, transaction_date:-1}`, and for DP dedupe `{email:1, broker_name:1, stock_code:1, transaction_date:1, event:1}`.

### 4.6 `ChargeInstrumentEntity` — collection `charge_instruments`

**Charges have two independent origins, and the model must reflect that.** Brokerage, statutory and exchange charges belong to the broker's rate card and are identical across every instrument it trades. **Exit load belongs to the scheme.** Two mutual funds bought through the same broker on the same day carry different exit loads, and an index fund may carry none at all.

Forcing exit load into `ChargeScheduleEntity` would mean one schedule document per fund — thousands of near-identical documents differing in a single rule.

| Field | Mongo | Type | Notes |
|---|---|---|---|
| `id` | `_id` | String | |
| `stockCode` | `stock_code` | String | the identifier transactions actually carry today |
| `isin` | `isin` | String | stored now, keyed on later without a re-key |
| `name` | `name` | String | |
| `assetType` | `asset_type` | `AssetType` | |
| `fundCategory` | `fund_category` | `FundCategory` | classification and rule eligibility |
| `planType` | `plan_type` | `PlanType` | DIRECT or REGULAR — decides whether a distributor transaction fee can apply |
| `equityOriented` | `equity_oriented` | Boolean | **explicit, not inferred.** Whether a scheme is equity-oriented for STT depends on actual allocation, not marketing category — an index fund, an ELSS and a plain equity fund can all qualify |
| `amc` | `amc` | String | |
| `startDate` / `endDate` / `status` | | | same versioning and supersede flow as `ChargeScheduleEntity` — AMCs revise exit loads |
| `rules` | `rules` | `List<ChargeRule>` | exit load, scheme-level fees |
| `auditMetadata` | `audit_metadata` | `AuditMetadata` | |

This document is not optional machinery. The rule *"STT applies to equity-oriented mutual funds but not debt funds"* needs `equityOriented` to exist somewhere; without this collection that rule cannot be expressed at all.

The engine merges schedule rules and instrument rules into **one ordered evaluation**, so GST bases, ordering and rounding behave identically regardless of origin. Each emitted line records its `ChargeRuleSource`.

#### 4.6.1 Which source owns a rule

Charges do not divide cleanly into "broker's" and "scheme's". The mutual fund distributor transaction fee is the awkward case, and it decides the rule:

- Its **amount** is the broker's decision — whether to levy it at all, and ₹100 or ₹150.
- Its **applicability** is the scheme's — a DIRECT plan has no distributor and can never attract one.
- Its **rate** also depends on the *user* — AMFI caps it at ₹150 for a first-time investor and ₹100 thereafter.

Three sources for one charge. The resolving principle:

> **A rule lives where its rate is decided. It reads the other sources through its eligibility predicate.**

So the transaction fee lives on the **broker schedule**, and reads the instrument and the user:

```json
{ "code": "MF_TXN_FEE_NEW", "basis": "FLAT", "flatAmount": 150.0,
  "eligibility": "#planType == 'REGULAR' and #turnover >= 10000 and #firstTimeInvestor",
  "events": ["BUY"], "order": 10 },

{ "code": "MF_TXN_FEE_EXISTING", "basis": "FLAT", "flatAmount": 100.0,
  "eligibility": "#planType == 'REGULAR' and #turnover >= 10000 and !#firstTimeInvestor",
  "events": ["BUY"], "order": 10 }
```

`#planType` comes from the resolved instrument; `#firstTimeInvestor` is derived by `UserChargeService` from prior MF purchase records. Neither needs a model change — both arrive through `ChargeContext.attributes`.

**Instrument attributes are injected into the evaluation context for every rule**, not only instrument-sourced ones. Without that, a schedule rule could not read `planType` and this charge would be inexpressible.

If a platform genuinely varies the fee per scheme, the same rule moves to that instrument's `rules` — no engine change, because both sources feed one evaluation.

#### 4.6.2 Precedence when both sources declare the same code

The instrument rule wins and the schedule rule is skipped; the emitted line records `source: INSTRUMENT`, and the override is logged at DEBUG. This matches the resolver's specificity philosophy — the more specific source overrides the more general — and is the only safe default, since applying both would double-charge silently.

### 4.7 `ChargeCatalogueEntity` — collection `charge_catalogue`
`code`, `displayName`, `category`, `description`, `statutoryReference`, `status`. A registry so `code` stays a validated free string rather than a Java enum (OD-4) — the same trick `AllowanceCatalogueEntity` uses in `taxplanning`.

---

## 5. Engine Contracts

### 5.1 Input

```java
public record ChargeContext(
        String transactionId,
        String orderId,                          // brokerage caps are per *order*, not per trade
        String stockCode,
        BrokerName brokerName,
        AssetType assetType,
        TradeSegment segment,
        String exchange,
        String planCode,
        ChargeEvent event,
        LocalDate transactionDate,
        double quantity,
        double price,
        int lotSize,                             // 1 for cash-segment instruments
        Map<AmountBasis, Double> baseAmounts,    // see below
        List<LotSlice> lots,                     // FIFO lots consumed by this trade; see §5.8
        Map<String, Object> attributes           // fundCategory, equityOriented, … -> SpEL vars
) {
    public static ChargeContext forTrade(...)
    public static ChargeContext forAmcCycle(...)
    public double amount(AmountBasis basis);     // 0.0 when the basis is absent
}
```

**`baseAmounts` instead of a single `turnover`.** A charge is a percentage *of something*, and in derivatives that something is not one number:

| `AmountBasis` | Meaning | Used by |
|---|---|---|
| `TURNOVER` | `price × quantity` | everything in the cash segment |
| `NOTIONAL` | `price × lotSize × lots` | futures brokerage, futures STT |
| `PREMIUM` | option premium × quantity | options brokerage, options STT, options exchange charges |
| `INTRINSIC` | `(settlement − strike) × quantity` | STT on *exercised* options |
| `PRINCIPAL` | amount invested or remitted | MF stamp duty, TCS on foreign remittance |

Each `ChargeRule` declares an `amountBasis` (default `TURNOVER`). In Phase A every seeded rule uses `TURNOVER` and every context supplies only that key — but the *shape* is right, so adding options later is a rule field, not a schema migration. This is the highest-value piece of forward design in the model; see §13.

`lotSize` and `orderId` are carried for the same reason. `orderId` matters because F&O brokerage is capped **per order** while the engine is invoked **per trade** — `DedupeScope.PER_ORDER` needs a key to dedupe on, and `AssetRequest` already carries `orderId` today.

`attributes` is the open-ended valve: a new rule can depend on a new runtime fact without changing the record — the caller populates the map, the FORMULA/eligibility SpEL reads it.

### 5.2 Output

```java
public record ChargeComputation(
        String scheduleId,
        String scheduleCode,
        List<ChargeLine> lines,
        double total
) {
    public static ChargeComputation empty();
    public double amountOf(String code);
    public Map<String, Double> amountByCode();
}
```

### 5.3 Strategy interface

```java
public interface ChargeCalculator {
    ChargeBasis basis();
    double compute(ChargeRule rule, ChargeContext context, ChargeAccumulator accumulator);
}
```

Registered into `ChargeCalculatorRegistry` by Spring collection injection (`List<ChargeCalculator>` → `Map<ChargeBasis, ChargeCalculator>`), so a new basis is a new `@Component` and nothing else.

### 5.4 Orchestrator

```java
public ChargeComputation compute(UserMail userMail, ChargeContext context);
```

Algorithm:
1. `ChargeScheduleResolver.resolve(context)` → schedule, or `ChargeComputation.empty()` + WARN if none (AC-12).
2. Filter rules: `active` && `events.contains(context.event())` && side matches && `eligibility` SpEL is true or absent.
3. Sort by `order` ascending.
4. For each rule: dispatch to `registry.get(rule.basis())`, apply `min`/`max`/`aggregator`, apply `rounding`, append a `ChargeLine` to the accumulator.
5. Sum → `ChargeComputation`.

`DERIVED` calculators read `accumulator.sumOf(rule.baseCodes())`, which is why ordering is validated at write time.

### 5.5 Modifier application order
Deterministic and applied uniformly by the orchestrator, **not** by individual calculators:

```
raw = calculator.compute(...)
if (aggregator == MIN)  raw = min(raw, flatAmount)     // percentage capped by a fixed fee
if (aggregator == MAX)  raw = max(raw, flatAmount)
if (minAmount != null)  raw = max(raw, minAmount)
if (maxAmount != null)  raw = min(raw, maxAmount)
amount = round(raw, rounding)
```

This fixes D7: a rule declaring both `rate` and `flatAmount` **without** an aggregator is rejected at write time rather than silently returning 0.

### 5.6 `ChargeFormulaEvaluator`

The charges module owns its expression evaluation outright — a small `SpelExpressionParser` wrapper local to `brokercharges/engine`, sharing no code with anything in `taxplanning`.

```java
@Component
public class ChargeFormulaEvaluator {
    private final ExpressionParser parser = new SpelExpressionParser();

    public double evaluate(String expression, ChargeContext context, ChargeAccumulator accumulator);
    public boolean matches(String predicate, ChargeContext context, ChargeAccumulator accumulator);
    public void validate(String expression);   // parse-only, called at rate-card write time
}
```

Exposed variables: `#turnover`, `#quantity`, `#price`, `#side`, every key of `ChargeContext.attributes()` (e.g. `#holdingDays`, `#fundCategory`), and `#charges['CODE']` reading the live accumulator. Returns `double` — money to two decimals. `validate` runs during schedule validation so a malformed formula is rejected at seed time, never at trade time.

### 5.7 Per-lot evaluation

A rule marked `perLot` is evaluated once for each FIFO lot the trade consumes, and the results summed. Everything else is evaluated once against the aggregate.

```java
public record LotSlice(double quantity, LocalDate acquisitionDate, double price) {
    public long holdingDays(LocalDate disposalDate);
}
```

**Why this is not optional.** Exit load applies per unit, based on how long *that unit* was held. Averaging over a transaction can be wrong by the entire charge, not by a rounding error:

> Buy 100 units Jan 2024. Buy 100 more Oct 2025. Redeem 150 in Nov 2025.
> FIFO gives 100 units held ~22 months (no load) and 50 held ~1 month (1% load).
> A transaction-level weighted-average holding period of ~15 months computes **zero** exit load.
> The correct answer is 1% on 50 units.

**Why it is cheap.** The data already exists: `PortfolioService.updateQuantityBySavingReportAndProfitAndLoss1` builds `List<BuyContext>` (quantity, date, price) from FIFO matching before P&L is updated. The engine needs one field on the context and one loop; in Phase A the simulate endpoint supplies lots directly, so no portfolio file changes.

**Why now rather than later.** `ChargeContext` is a record. Adding lots afterwards would change the record, change the engine's evaluation loop, and require re-verifying every existing rule — a Tier-3 change by the framework in §13.1. Building it now keeps every holding-period-dependent charge permanently Tier-1.

For a rule that is not `perLot`, `lots` is ignored. For a BUY, it is empty.

### 5.8 Scoped (deduplicated) charges
`ScopedFlatChargeCalculator` resolves `dedupeScope` against `UserChargeRepository`:
- `PER_SCRIP_PER_DAY` → `existsByEmailAndBrokerNameAndStockCodeAndTransactionDateAndAmountByCodeKey(...)` — an `exists` query, not a `List` (fixes D9).
- The check runs inside the same `@Transactional` boundary as the write; because MongoDB transactions are enabled (`app.mongodb.transactions-enabled`), read-your-own-write within the transaction holds.

---

## 6. Rule Resolution

Two sources are resolved independently and merged into one ordered rule list before evaluation:

1. `ChargeScheduleResolver` — the broker rate card, by the specificity rules below.
2. `ChargeInstrumentResolver` — the instrument's own rules, by `stockCode` and date, using the same validity-window and supersede semantics.

Merged rules are sorted by `order` as a single list, so a `DERIVED` GST rule on the broker card can include an instrument-sourced line in its base if the rate card declares that code. Each line records its `ChargeRuleSource`.

### 6.1 Schedule specificity

Candidate query: `brokerName` matches, `status == ACTIVE`, `startDate <= date`, and (`endDate` is null or `endDate >= date`).

Each candidate is scored; a candidate is **disqualified** if any declared dimension conflicts with the context.

| Dimension | Weight |
|---|---|
| `planCode` | 8 |
| `exchange` | 4 |
| `segment` | 2 |
| `assetType` | 1 |
| unset dimension | 0 (matches anything) |

Highest score wins; ties broken by latest `startDate`; a remaining tie is a data error → `BadRequestException` naming both schedule codes.

Cached in a `ConcurrentHashMap` keyed by the resolution tuple, evicted whenever `ChargeScheduleService` writes. Rate cards change monthly at most; per-transaction Mongo lookups are pure waste.

**Supersede on publish (FR-1):** publishing a schedule whose scope matches an existing open one sets the incumbent's `endDate = newStartDate.minusDays(1)` in the same transaction, instead of throwing.

---

## 7. Seed Data Format

`backend/src/main/resources/data/charges/zerodha-equity-delivery-2025-04-01.json` — **illustrative rates, verify before use:**

```json
{
  "scheduleCode": "ZERODHA_EQ_DELIVERY_2025_04",
  "brokerName": "ZERODHA",
  "assetType": "EQUITY",
  "segment": "DELIVERY",
  "exchange": null,
  "startDate": "2025-04-01",
  "endDate": null,
  "status": "ACTIVE",
  "sourceUrl": "https://zerodha.com/charges",
  "verifiedOn": "2025-04-01",
  "rules": [
    { "code": "BROKERAGE", "displayName": "Brokerage", "category": "BROKERAGE",
      "basis": "FLAT", "side": "BOTH", "events": ["BUY","SELL"],
      "flatAmount": 0.0, "taxable": true, "order": 10 },

    { "code": "STT", "displayName": "Securities Transaction Tax", "category": "STATUTORY",
      "basis": "TURNOVER", "side": "BOTH", "events": ["BUY","SELL"],
      "rate": 0.1, "rounding": "HALF_UP_0", "taxable": false, "order": 20 },

    { "code": "EXCHANGE_TXN", "displayName": "Exchange transaction charges", "category": "EXCHANGE",
      "basis": "TURNOVER", "side": "BOTH", "events": ["BUY","SELL"],
      "rate": 0.00297, "taxable": true, "order": 30 },

    { "code": "SEBI_FEE", "displayName": "SEBI turnover fees", "category": "REGULATORY",
      "basis": "TURNOVER", "side": "BOTH", "events": ["BUY","SELL"],
      "rate": 0.0001, "taxable": true, "order": 40 },

    { "code": "IPFT", "displayName": "Investor Protection Fund", "category": "EXCHANGE",
      "basis": "TURNOVER", "side": "BOTH", "events": ["BUY","SELL"],
      "rate": 0.0001, "taxable": true, "order": 50 },

    { "code": "STAMP_DUTY", "displayName": "Stamp duty", "category": "STATUTORY",
      "basis": "TURNOVER", "side": "BUY", "events": ["BUY"],
      "rate": 0.015, "rounding": "HALF_UP_0", "taxable": false, "order": 60 },

    { "code": "DP", "displayName": "DP charges", "category": "DEPOSITORY",
      "basis": "SCOPED_FLAT", "side": "SELL", "events": ["SELL"],
      "flatAmount": 13.5, "dedupeScope": "PER_SCRIP_PER_DAY", "taxable": true, "order": 70 },

    { "code": "GST", "displayName": "GST", "category": "TAX",
      "basis": "DERIVED", "side": "BOTH", "events": ["BUY","SELL"],
      "rate": 18.0, "baseCodes": ["BROKERAGE","EXCHANGE_TXN","SEBI_FEE","IPFT","DP"],
      "taxable": false, "order": 100 }
  ]
}
```

Mutual fund exit load — the case the old design could not express at all:

```json
{ "code": "EXIT_LOAD", "displayName": "Exit load", "category": "FUND",
  "basis": "FORMULA", "side": "SELL", "events": ["SELL"],
  "eligibility": "#holdingDays < 365",
  "formula": "#turnover * 0.01",
  "taxable": false, "order": 15 }
```

`ChargeSeederService` mirrors `PolicySeederService`: `@PostConstruct`, idempotent by `scheduleCode`, validates every file through `ChargeScheduleValidator` before persisting, fails fast on a bad rate card (AC-9).

---

## 8. Reporting Model Changes

`portfolio/entity/model/BrokerChargesReport` → **`ChargeSummaryReport`**:

```java
@Data
public class ChargeSummaryReport {
    @Field("amount_by_code")   private Map<String, Double> amountByCode = new HashMap<>();
    @Field("total_charges")    private double totalCharges;
    @Field("last_updated_time") @LastModifiedDate private LocalDateTime lastUpdatedTime;

    public void merge(Map<String, Double> increments) { ... }   // replaces the 6 setters
}
```

`YearlyBrokerCharges` / `MonthlyBrokerCharges` keep their shape (they extend the report and hold the `Map<Month, …>` / fortnight halves) and are renamed `YearlyChargeSummary` / `MonthlyChargeSummary`. `ProfitAndLossService.updateBrokerCharges` (`:507`) collapses from six hard-coded `set(get + get)` lines to a single `report.merge(userCharge.getAmountByCode())` — which is precisely why a new charge code needs no report change (G6/AC-1).

---

## 9. Delivery Phasing and Portfolio Integration

The engine is built **standalone first**. `portfolio` is not touched until the engine is provably correct in isolation, and the cutover is a flag flip rather than a rewrite.

`ChargeEngineProperties` (`app.charges.*`) drives the phase:

```yaml
app:
  charges:
    engine-enabled: true      # Phase A — engine live, simulate API usable
    shadow-recording: false   # Phase B — persist computed charges alongside existing flow
    authoritative: false      # Phase C — computed total drives cost basis
```

### 9.1 Phase A — standalone engine *(this branch's scope)*

No `portfolio` file changes at all. Deliverables:
- schedules, rules, catalogue, seeds, resolver, validator, calculators, engine;
- `POST /charges/simulate` — post a hypothetical trade, get the full breakdown back, persist nothing;
- golden-file tests asserting line items against real contract notes.

The engine takes `TradeSegment`, `turnover` and `attributes` on its **own** `ChargeContext` record. Callers construct it; no portfolio type gains a field yet. This is what makes Phase A a pure addition.

**Exit criterion:** every acceptance criterion except AC-10 passes without a single line changing under `portfolio/`.

### 9.2 Phase B — shadow recording

A thin adapter lets `portfolio` hand trades to the engine without depending on its result:

```java
// portfolio/service/ChargeRecordingGateway.java  — interface owned by portfolio
public interface ChargeRecordingGateway {
    Optional<ChargeComputation> record(UserMail userMail, ProfitLossContext context);
}
```

Implemented in `brokercharges` as `ChargeRecordingGatewayImpl`; injected into `ProfitAndLossService`. When `shadow-recording` is on it computes and persists a `UserChargeEntity`; the return value is **ignored** by cost basis. Nothing about existing behaviour changes.

What this buys: on real transactions you can compare the engine's total against what the user actually typed into `AssetRequest.brokerCharges`, per trade, before trusting it. That comparison is the reason this phase exists (PRD OD-8).

Also in Phase B:
- remove the `assetType == EQUITY` gate at `ProfitAndLossService:333` and `:361`, so non-equity trades start producing shadow records (FR-8);
- a reconciliation endpoint, `GET /user-charges/user/{email}/reconciliation`, listing computed vs entered per transaction with the delta.

### 9.3 Phase C — cutover

Only once the Phase B deltas are understood:
- `authoritative: true` — `assetEntity.setBrokerCharges(computation.total())`;
- in `PortfolioService.buyStock`, the charge computation moves **ahead** of the entity mutation (today it runs after, at `:311`), so the computed total reaches the lot;
- `AssetRequest.brokerCharges` is removed from the request contract; `TransactionEntity` gains `userChargeId` for traceability;
- `TradeSegment` is added to `AssetRequest` / `TransactionEntity` / `AssetEntity`, defaulting to `DELIVERY`;
- `toTradeOutcomeContext` pro-rating (`:569`) is re-verified against computed charges across partial sells;
- the old implementation is deleted (checklist Chunk 10).

### 9.4 AMC and account-level charges

`AssetManagementService.imposeAmcCharges` currently lives in `portfolio`, constructs `brokercharges` DTOs, and calls back into `ProfitAndLossService` — a boundary inversion.

Rather than moving the existing entity (which would be a Phase C-scale change), Phase A introduces a **new** `ChargeAccountEntity` (`charge_accounts`) owned by `brokercharges`, holding broker account id, plan code, AMC frequency, last-billed date and billing history. `AmcChargeService` bills against it via the same engine, using `ChargeEvent.AMC_CYCLE`.

The existing `AssetManagementDetails` flow keeps running untouched until Phase C, when it is retired in favour of `charge_accounts`.

### 9.5 Modulith

Phase A adds no cross-module imports: `brokercharges` already declares `allowedDependencies = {shared, portfolio, corporate}` and only reads `BrokerName` / `AssetType` from `portfolio`. Phase B adds one interface owned by `portfolio` and implemented in `brokercharges` — legal in both directions under the existing declarations. `WealthLensModulithTest.modulithStructureIsValid()` must stay green throughout (AC-11).

---

## 10. API Surface

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/charge-schedules` | Publish a schedule (auto-supersedes the incumbent) |
| `GET` | `/charge-schedules/{id}` | Fetch one |
| `GET` | `/charge-schedules` | List by broker / asset type / date |
| `PATCH` | `/charge-schedules/{id}/close` | Explicitly close with an end date |
| `GET` | `/charge-catalogue` | The registry of valid charge codes |
| `POST` | `/charges/simulate` | Dry-run a trade, return the breakdown, persist nothing |
| `GET` | `/user-charges/user/{email}` | Charge history, filterable by date range / asset type |
| `GET` | `/user-charges/user/{email}/transaction/{transactionId}` | Contract-note breakdown for one trade |
| `POST` | `/charges/amc/impose` | AMC cycle run against `charge_accounts` (the old `/broker-charges/amc/impose` keeps working until Phase C) |
| `GET` | `/user-charges/user/{email}/reconciliation` | Phase B — computed vs user-entered charge per transaction, with delta |
| `POST` | `/charge-accounts/user/{email}` | Register a broker account for AMC / opening charges |

`/charges/simulate` is new and worth having: it makes the whole engine testable from the API collection without mutating a portfolio, and gives the UI a "what will this cost?" preview.

---

## 11. Testing Strategy

**Unit — calculators.** One test class per calculator; boundary cases for min/max/aggregator, rounding, and the empty-schedule path.

**Unit — engine.** Rule ordering, DERIVED base summation (AC-5), eligibility predicates (AC-6), side filtering (AC-3, AC-7).

**Unit — resolver.** Specificity ranking, wildcard dimensions, overlapping windows, tie → exception.

**Unit — validator.** Every FR-2 rejection case, each asserting the message.

**Golden-file — contract notes.** `src/test/resources/charges/golden/*.json`: input trade + expected line items, checked to ₹0.01 (AC-2). These are the regression net when rates are reseeded.

**Integration.** `ChargesIntegrationTest extends AbstractIntegrationTest` — seed a schedule, post a buy then two sells of the same scrip same day, assert DP is charged once (AC-4) and the P&L report aggregates by code. **Add `charge_schedules`, `user_charges`, `charge_catalogue` to `cleanDatabase()`.**

**Seeder test.** Every shipped JSON file parses and validates (AC-9) — this catches a bad rate card at build time, not in production.

**Phase A isolation check.** `git diff master --stat -- backend/src/main/java/com/thiru/wealthlens/portfolio/` must be empty at the end of Phase A. If it is not, scope has leaked.

⚠️ Surefire runs with `testFailureIgnore=true`; read `backend/target/surefire-reports/TEST-*.xml` before declaring green.

---

## 12. Open Decisions

| # | Question | Recommendation | Impact if deferred |
|---|---|---|---|
| OD-1 | Rename module `brokercharges` → `charges`? | Yes, as a separate mechanical commit **after** the engine lands | Cosmetic; `package-info` + imports only |
| OD-2 | ~~Engine as sole source of cost-basis charges?~~ | **Settled** — replacing manual entry is the point of the work. Sequenced A → B → C (§9) | — |
| OD-3 | Model F&O now? | Design-for-later: `TradeSegment` and explicit `turnover` support it, ship no F&O seed cards | None; the model already accommodates it |
| OD-4 | `ChargeCode` as enum or catalogued string? | Catalogued string | An enum would reintroduce a Java change per charge, defeating G1 |
| OD-5 | Per-user negotiated brokerage plans? | `planCode` dimension exists; no UI or user-account field in phase 1 | Adding it later is data-only |
| OD-6 | Should `AmcChargeService` run on a schedule or stay manually triggered? | Keep the manual `POST` endpoint for now; a `@Scheduled` job is trivial to add later | None |
| OD-7 | Do we keep `ChargeSimulationController`? | Yes — cheap, and it is how Phase A is verified without touching `portfolio` | Phase A loses its exit criterion |
| OD-8 | Run Phase B (shadow) or cut straight over? | Run it — the computed-vs-entered delta on real data is the only real proof | Cutover on untested numbers |
| OD-9 | Retire `AssetManagementDetails` in favour of `charge_accounts`, or adapt it in place? | New entity in Phase A, retire the old one in Phase C (§9.4) | Phase A stops being additive-only |

---

## 13. Extensibility Analysis

Phase A ships **EQUITY / DELIVERY only**. F&O, and everything after it, must land without redesign. This section is the evidence that it will — and an honest list of the places it will not.

### 13.1 Three tiers of future change

| Tier | Change | Cost | Design target |
|---|---|---|---|
| **1** | New charge code, or a repriced existing one | Edit seed JSON, restart | Every routine change must land here |
| **2** | New charge *arithmetic* (per-lot, slab, accrual) | One new `ChargeCalculator` `@Component` | Acceptable — rare, additive, touches nothing existing |
| **3** | New *dimension* of variation | Schema change on `ChargeScheduleEntity` + reseed + resolver change | Must be designed out now |

The current implementation puts almost everything in Tier 3. The goal of the rule model is to leave Tier 3 empty for anything foreseeable.

### 13.2 F&O mapped against the model

| F&O characteristic | Tier | How it lands |
|---|---|---|
| Brokerage `min(0.03%, ₹20)` per order | **1** | `basis: TURNOVER`, `rate: 0.03`, `flatAmount: 20`, `aggregator: MIN` — already expressible |
| Brokerage capped per *order*, engine runs per *trade* | **1** | `dedupeScope: PER_ORDER` — needs `orderId` on the context (§5.1, added) |
| STT on futures: sell side only | **1** | `side: SELL` |
| STT on options: on **premium**, not notional | **1** | `amountBasis: PREMIUM` (§5.1, added) |
| STT on exercised options: on **intrinsic value** | **1** | `amountBasis: INTRINSIC` + `events: [EXERCISE]` |
| Options exchange charges ≈ 0.035% of premium vs 0.00297% of equity turnover | **1** | different rate, different `amountBasis` |
| No DP charges in F&O | **1** | omit the rule — the old fixed schema could not omit anything |
| Auto square-off ₹50 per position | **1** | `basis: FLAT`, `events: [AUTO_SQUARE_OFF]` |
| Per-lot charges | **2** | `PER_UNIT` calculator with `lotSize` on the context |
| Full-service brokerage slabs | **2** | `SLAB` calculator |
| Physical delivery on expiry | **1** | new `ChargeEvent` value + rules |

**Nothing in the F&O rate card requires a Tier-3 change** — provided `amountBasis`, `lotSize` and `orderId` are on the context from day one. They cost three fields now and a reseed-plus-migration later, which is why they are in Phase A despite being unused by equity delivery.

### 13.3 Other instruments

| Instrument | Tier | Notes |
|---|---|---|
| Mutual funds | 1 | Exit load = `FORMULA` + `eligibility: #holdingDays < 365`; STT on equity-MF redemption only; stamp duty on `PRINCIPAL` |
| Bonds / G-Sec | 1 | Mostly the *absence* of rules — brokerage + GST only |
| SGB | 1 | Secondary market = brokerage; primary issuance = a new `ChargeEvent`, no charges |
| US / international equity | 2 | Forex markup as a `TURNOVER` rule; TCS as `DERIVED` on `PRINCIPAL`; `currency` already exists on the schedule |
| MTF (margin funding) | **3** | See §13.5 — genuinely does not fit the per-event model |

### 13.4 What "extensible" is worth in Phase A

Building for F&O now would be waste. Building so F&O *cannot* be added later would be worse. The line drawn:

**Built in Phase A** — because equity delivery needs them:
`TURNOVER`, `FLAT`, `SCOPED_FLAT`, `DERIVED` calculators.

**Built in Phase A despite not being needed** — because it is the escape hatch that makes the Tier-1 promise true for charges nobody has thought of yet:
`FORMULA` calculator + `ChargeFormulaEvaluator`.

**Also built, after correction** — an earlier draft deferred these as unused:
`PER_UNIT` and `SLAB`. `SlabBandBasis` exists so graded exit loads can band on holding days, so deferring `SlabChargeCalculator` would leave that enum dead. And a `ChargeBasis` constant with no registered calculator is a trap for whoever first writes a rule using it — `ChargeCalculatorRegistry` therefore fails fast at startup if any constant lacks a calculator.

**Carried but unused** — cheap now, a migration later:
`amountBasis` on the rule; `lotSize`, `orderId`, `baseAmounts` on the context; `ChargeEvent` values for `CALL_AND_TRADE`, `AUTO_SQUARE_OFF`, `PLEDGE`; the `planCode` and `exchange` schedule dimensions.

### 13.5 Where the model genuinely does not stretch

Named honestly, so they are decisions rather than surprises:

1. **Time-accrued charges.** MTF interest and pledge holding fees accrue *daily on an open position*, not at a trade event. The engine computes at an event. This needs a second execution mode — a cycle runner, structurally like `AmcChargeService` — not a new calculator. **Not designed for; flagged.**
2. **Aggregate caps.** "Maximum ₹X brokerage per day across all trades" requires evaluating a set of transactions together. `DedupeScope` charges *once*; it cannot *cap a sum*. Would need a post-processing pass over a day's `UserChargeEntity` rows. **Not designed for; flagged.**
3. **Volume-tiered pricing.** Slabs based on *cumulative monthly turnover* need historical aggregation before the current trade can be priced. `SLAB` handles per-trade turnover only. **Not designed for; flagged.**
4. **Retrospective rate corrections.** If a seeded rate is wrong, existing `UserChargeEntity` rows are stale. `scheduleId` provenance makes them findable; a `POST /charges/recompute` job is the fix. **Designed for, not built.**

### 13.6 Keeping the promise honest

`ChargeExtensibilityTest` — the guarantee as an executable test, not a claim in a document:

1. seed a schedule containing a charge code that exists nowhere in Java;
2. run a trade through the engine;
3. assert the line appears in the computation, in `amountByCode`, and in the aggregated summary report.

If someone later hard-codes a charge name in a `switch` or a field, this test fails. That is AC-1 made permanent.

---

## 14. Temporal Correctness and Backfilled Transactions

Rate cards change. Users upload transactions long after they occurred, including transactions predating anything currently on file. A charge must always be computed against the rate card **in force on the transaction date**, never the card in force when the upload happened.

This section exists because the design as first written got that wrong.

### 14.1 The defect being corrected

The existing `BrokerChargesRepository` resolves with:

```java
@Query("{'broker_name': ?0, 'status': 'ACTIVE', 'start_date': {$lte: ?1}, 'end_date': {$gte: ?1}}")
```

`EntityStatus` carries `ACTIVE`, `INACTIVE` and `SUPERSEDED`. If superseding a rate card marks it `SUPERSEDED`, then:

> A 2024 card is superseded in 2025. In 2026 the user uploads a 2024 transaction.
> The query requires `ACTIVE`; the 2024 card is `SUPERSEDED`; **no schedule matches**.
> The charge computes as zero, silently.

Two orthogonal concepts share one field.

| Concept | Expressed by |
|---|---|
| Which card applies on a given date | `startDate` / `endDate` **alone** |
| Whether the record is legitimate data | `status` — `ACTIVE` versus retracted-in-error |
| Whether a card is the *current* one | `endDate == null` — **never** a status |

**Rules:**
1. Superseding sets `endDate = newStartDate.minusDays(1)`. It **never** changes `status`.
2. `INACTIVE` means the card was entered in error and must not be used for **any** date — it is a data retraction, not an expiry.
3. The resolver filters `status: { $ne: "INACTIVE" }`, not `status == "ACTIVE"`. Defensive: if a future maintainer sets `SUPERSEDED` believing it correct, backfill still resolves rather than silently returning nothing.
4. `ChargeScheduleValidatorTest` and `ChargeInstrumentResolverTest` both assert that a superseded card still resolves for a date inside its historical window.

The same applies to `ChargeInstrumentEntity`: an AMC that revises exit load closes the old profile's window, and a redemption backdated into that window uses the old load.

### 14.2 A missing charge must be visible, not merely logged

Backfilling several years will cross periods with no rate card on file. AC-12 says the engine returns an empty computation and logs a WARN — correct, but a warning scrolls away and the gap becomes invisible.

Therefore a `UserChargeEntity` is persisted **even when nothing is computed**, carrying a `ChargeResolution`. `NO_SCHEDULE` rows are queryable, so a data-quality report can list every transaction whose charges could not be assessed, and re-running after seeding the missing card fixes them.

`GET /user-charges/user/{email}/gaps` returns exactly that.

### 14.3 The upload model: quarterly and chronological

Transactions arrive in **quarterly batches, in chronological order**. A user may load 2024's quarters during 2026, but Q1 precedes Q2 precedes Q3. That guarantee removes most of the ordering risk:

| Rule | Risk without sequencing | Under sequential upload |
|---|---|---|
| `#firstTimeInvestor` (₹150 vs ₹100) | Both computations wrong if the later purchase arrives first | **Safe** — the earliest purchase always arrives first |
| Exit load, `perLot` | Incomplete FIFO lots give wrong holding periods | **Safe** — purchases always precede their redemption |
| DP charge, `PER_SCRIP_PER_DAY` | — | **Safe** either way; dedupe is keyed on transaction date |

So `PROVISIONAL` becomes a safety net rather than a routine state.

**It stays, because the guarantee is operational, not enforced.** Sequencing is a process convention: someone will eventually upload a forgotten file, re-run a quarter, or load two quarters in the wrong order. A system that assumes an unenforced convention produces silently wrong numbers when it breaks.

The guard is cheap. Before processing a batch, `UserChargeService` compares its earliest transaction date against the latest already recorded for that user. If the batch reaches back before it, every affected computation is marked `PROVISIONAL` and surfaced through the gaps endpoint. That converts a silent wrongness into a visible flag, and costs one indexed query per batch.

### 14.4 Quarterly batch processing

Four properties the batch path must hold, none of which follow from single-transaction correctness:

**Idempotent re-upload.** Re-loading a quarter — to correct a file, or by accident — must not double-charge. `UserChargeEntity` is keyed on `transactionId`: a recomputation replaces the existing row rather than appending. Enforced by a unique index on `{email, transaction_id}`.

**Intra-batch deduplication.** Two sells of the same scrip on the same day within one batch must produce exactly one DP charge. `ScopedFlatChargeCalculator` queries rows written earlier in the *same* batch, so the batch must run inside a single MongoDB transaction where read-your-own-writes holds, and transactions must be processed in order within it. `app.mongodb.transactions-enabled=true` is a prerequisite, not an optimisation.

**AMC not double-billed.** `ChargeAccountEntity` carries `lastBilledThrough`; a cycle already covered by it is skipped. Re-running a quarter's AMC is then a no-op rather than a second charge — the same guard `AssetManagementDetails.lastAmcChargesDeductedOn` provides today.

**Resolution cost amortised.** A quarter may hold hundreds of transactions resolving to the same one or two rate cards. The resolver cache (§6) turns that into one lookup per distinct scope rather than one per transaction, which is the difference between a batch that completes and one that hammers Mongo.

### 14.4 Recomputation

`POST /charges/recompute` re-evaluates a scope — a user, a date range, or every row referencing a given `scheduleId`. Needed in three situations:

1. A seeded rate was wrong and has been corrected.
2. A batch arrived out of sequence and was flagged `PROVISIONAL`.
3. A previously `NO_SCHEDULE` period now has a card.

Idempotent, keyed on `transactionId`: recomputing replaces that transaction's `UserChargeEntity` rather than appending.

**This forces a change to how P&L charge aggregates are maintained.** The current design accumulates incrementally (`report.merge(...)` per transaction), which cannot survive a recomputation — the old contribution is already folded into a sum and cannot be subtracted reliably.

So: the live path stays incremental for speed, but recomputation **rebuilds the affected financial year's charge aggregates wholesale from `user_charges`** rather than applying deltas. `user_charges` is the source of truth; the P&L charge hierarchy is a derived projection of it. Stating that explicitly is what keeps the two from drifting.

### 14.5 What this costs

One enum, two fields on `UserChargeEntity`, a changed resolver predicate, one endpoint, and a rebuild path in the recompute job. Every one of them is cheaper now than after a user has backfilled four years of transactions against rate cards that have since been superseded.
