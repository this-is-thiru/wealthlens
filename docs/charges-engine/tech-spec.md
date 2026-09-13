# Charges Engine — Technical Specification

**Date:** 2026-09-05
**Status:** Draft for review
**Companion docs:** `prd.md` (requirements), `implementation-checklist.md` (build tracker)
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
| `side` | `side` | `ChargeSide` | |
| `events` | `events` | `Set<ChargeEvent>` | which occasions trigger it |
| `amountBasis` | `amount_basis` | `AmountBasis` | which context amount the rate applies to; default `TURNOVER` |
| `rate` | `rate` | Double | percent, for TURNOVER / DERIVED |
| `flatAmount` | `flat_amount` | Double | for FLAT / SCOPED_FLAT |
| `perUnitAmount` | `per_unit_amount` | Double | for PER_UNIT |
| `slabs` | `slabs` | `List<ChargeSlab>` | for SLAB |
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
`fromTurnover`, `toTurnover` (null = ∞), `rate`, `flatAmount`.

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
| `lines` | `lines` | `List<ChargeLine>` — the contract note (G3) |
| `amountByCode` | `amount_by_code` | `Map<String, Double>` — denormalised for aggregation |
| `totalCharges` | `total_charges` | double |
| `auditMetadata` | `audit_metadata` | `AuditMetadata` |

Indexes: `{email:1, transaction_date:-1}`, and for DP dedupe `{email:1, broker_name:1, stock_code:1, transaction_date:1, event:1}`.

### 4.6 `ChargeCatalogueEntity` — collection `charge_catalogue`
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
        Map<String, Object> attributes           // holdingDays, fundCategory, … -> SpEL vars
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

### 5.7 Scoped (deduplicated) charges
`ScopedFlatChargeCalculator` resolves `dedupeScope` against `UserChargeRepository`:
- `PER_SCRIP_PER_DAY` → `existsByEmailAndBrokerNameAndStockCodeAndTransactionDateAndAmountByCodeKey(...)` — an `exists` query, not a `List` (fixes D9).
- The check runs inside the same `@Transactional` boundary as the write; because MongoDB transactions are enabled (`app.mongodb.transactions-enabled`), read-your-own-write within the transaction holds.

---

## 6. Schedule Resolution

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

**Deliberately deferred** — no seeded card needs them, and each is ~30 lines when a card does:
`PER_UNIT` (per-lot), `SLAB` (full-service tiers). The registry means adding one later changes no existing class.

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
