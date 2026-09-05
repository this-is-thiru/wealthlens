# Charges Engine — Test Plan

**Date:** 2026-09-05
**Companion docs:** `prd.md`, `tech-spec.md`, `implementation-checklist.md`, `../testing/test-framework-audit.md`
**Objective:** the engine is verified to a standard where manual QA of charge calculations is unnecessary.

---

## 1. Why this plan is shaped this way

A charge engine has three properties that decide the testing strategy:

1. **It is combinatorial.** side × basis × aggregator × rounding × event × dedupe. Enumerating these as individual test methods produces hundreds of near-identical bodies. They are written **table-driven** instead.
2. **It computes money.** Correctness is not "did it run" but "is the number right to the paisa". That needs golden files verified against real contract notes, and mutation testing to prove the assertions would catch a wrong number.
3. **Its whole purpose is to change without code changes.** So the *extensibility promise itself* must be a test, or it decays.

Coverage percentage alone would be misleading here — 100% line coverage is achievable with assertions that never check an amount. **Mutation score on the engine package is the real gate.**

---

## 2. Prerequisites

These framework changes land **before** the engine tests (audit items 1–6):

- [ ] `@Tag("unit")` / `@Tag("integration")` split — engine unit tests must run without Docker
- [ ] AssertJ on the backend classpath
- [ ] `MoneyAssert.assertMoney(expected, actual)` helper — no bare `assertEquals` on a double anywhere in this suite
- [ ] JaCoCo, threshold scoped to `com.thiru.wealthlens.brokercharges.**`
- [ ] PIT scoped to `com.thiru.wealthlens.brokercharges.engine.**`
- [ ] Dynamic `cleanDatabase()`

### 2.1 Design decision this plan forces

**Internal arithmetic in `BigDecimal`, not `double`.** GST is a percentage of a *sum of already-rounded* line items; per-line rounding then re-summing is where `double` drifts into visible paise errors against a real contract note. `ChargeLine.amount` is exposed as `double` at the persistence boundary; everything inside `ChargeAccumulator` and the calculators uses `BigDecimal` with an explicit scale and `RoundingMode`.

Without this, golden-file tests to ₹0.01 will be flaky and people will widen the tolerance until the tests stop meaning anything.

---

## 3. Test tiers

| Tier | What it proves | Count (est.) | Docker | Runtime |
|---|---|---|---|---|
| A. Calculator units | Each basis computes correctly in isolation | ~55 | no | <1s |
| B. Engine orchestration | Rules combine, order, filter and derive correctly | ~30 | no | <1s |
| C. Resolver | The right rate card is selected | ~18 | no | <1s |
| D. Validator | Bad rate cards are rejected at write time | ~16 | no | <1s |
| E. Golden contract notes | Totals match reality to the paisa | ~12 | no | <1s |
| F. Invariants | Properties that must hold for *any* rate card | ~10 | no | ~2s |
| G. Seed data | Every shipped card is valid | ~6 | no | <1s |
| H. Persistence & dedupe | DP-once-per-day, provenance, aggregation | ~14 | yes | ~15s |
| I. API | Simulate, publish/supersede, history | ~12 | yes | ~20s |
| J. Extensibility guarantee | A JSON-only charge reaches the report | 3 | yes | ~5s |
| K. Temporal correctness | Backfilled transactions resolve historical rate cards | ~14 | mixed | ~10s |
| **Total** | | **~176** | | |

Tiers A–G are pure JVM. That is deliberate: **~147 of ~176 tests run in under 5 seconds with no Docker**, so the engine is developed against a real feedback loop.

---

## 4. Tier A — Calculator units

Table-driven via `@ParameterizedTest` + `@CsvSource`. One class per calculator.

### `TurnoverChargeCalculatorTest`
| Case | Input | Expected |
|---|---|---|
| plain percentage | turnover 100000, rate 0.1 | 100.00 |
| zero rate | rate 0 | 0.00 |
| zero turnover | turnover 0, rate 0.1 | 0.00 |
| min floor applied | turnover 100, rate 0.1, min 20 | 20.00 |
| max cap applied | turnover 10⁹, rate 0.1, max 1000 | 1000.00 |
| MIN aggregator, percentage wins | rate 0.03 → ₹6, flat 20 | 6.00 |
| MIN aggregator, flat wins | rate 0.03 → ₹60, flat 20 | 20.00 |
| MAX aggregator, flat wins | rate 0.01 → ₹5, flat 20 | 20.00 |
| MAX aggregator, percentage wins | rate 0.5 → ₹500, flat 20 | 500.00 |
| non-default `amountBasis` | basis PREMIUM, premium present | uses premium not turnover |
| absent `amountBasis` in context | basis PREMIUM, only TURNOVER supplied | 0.00, WARN logged |
| negative turnover rejected | turnover −100 | `BadRequestException` |
| rounding HALF_UP_2 | 2.9749 | 2.97 |
| rounding HALF_UP_0 | 100.4 / 100.5 | 100 / 101 |
| rounding CEILING_2 | 2.971 | 2.98 |
| rounding NONE | 2.9749 | 2.9749 |

### `FlatChargeCalculatorTest`
Fixed amount returned; zero amount; quantity irrelevant; min/max still applied; rounding respected. *(5 cases)*

### `ScopedFlatChargeCalculatorTest` — pure logic, repository mocked
| Case | Expected |
|---|---|
| `NONE` scope, prior charge exists | charged |
| `PER_SCRIP_PER_DAY`, no prior | charged |
| `PER_SCRIP_PER_DAY`, prior same scrip same day | **0.00** |
| `PER_SCRIP_PER_DAY`, prior same scrip *different* day | charged |
| `PER_SCRIP_PER_DAY`, prior *different* scrip same day | charged |
| `PER_SCRIP_PER_DAY`, prior same scrip same day *different broker* | charged |
| `PER_ORDER`, second trade of same order | 0.00 |
| `PER_DAY`, second trade any scrip same day | 0.00 |
| repository throws | exception propagates, no silent 0 |

### `DerivedChargeCalculatorTest` — the D1 regression suite
| Case | Expected |
|---|---|
| single base code | 18% of that line |
| multiple base codes | 18% of their sum |
| base code present in schedule but not emitted (side filtered) | contributes 0 |
| base code emitted as 0 | contributes 0 |
| **STT deliberately excluded from `baseCodes`** | STT amount absent from the base — *pins D1* |
| empty `baseCodes` | 0.00, WARN |
| base referencing another DERIVED rule of lower order | included |
| rounding applied after summation, not per base | exact expected value |

### `FormulaChargeCalculatorTest`
Formula over `#turnover`; over an `attributes` key; over `#charges['BROKERAGE']`; eligibility true / false / absent; malformed expression → `BadRequestException`; expression returning negative → rejected; expression returning null → 0.00. *(8 cases)*

### `ChargeRoundingTest`
Each `RoundingPolicy` at `.005` boundaries, negative-zero, and very large values. *(6 cases)*

---

## 5. Tier B — Engine orchestration

`ChargeEngineTest`, Mockito for resolver and repository.

**Rule selection**
- event filter: `BUY` context skips `events:[SELL]` rules
- side filter: `side: BUY` rule absent on a sell
- `side: BOTH` present on both
- `active: false` rule skipped
- eligibility predicate false → rule skipped, no line emitted

**Ordering**
- rules evaluate ascending by `order`
- equal `order` → deterministic (by `code`), asserted twice for stability
- DERIVED reads only lines emitted before it
- DERIVED referencing a *later* rule → 0 contribution *(validator prevents this; engine must not crash)*

**Assembly**
- `total` equals the sum of line amounts
- line count equals matched rule count
- `scheduleId` / `scheduleCode` provenance populated
- empty schedule → `ChargeComputation.empty()`, total 0, **no exception**
- **no schedule resolved → empty computation + WARN** *(AC-12)*
- schedule with zero matching rules → empty, no exception

**Modifier order** — the §5.5 contract, asserted explicitly
- aggregator applied before min/max
- min/max applied before rounding
- rounding applied exactly once per line
- a rule with rate + flat but no aggregator → the *validator* rejects it; engine treats it as a hard error, never a silent 0 *(pins D7)*

---

## 6. Tier C — Resolver

`ChargeScheduleResolverTest`

| Case | Expected |
|---|---|
| exact match on all dimensions | selected |
| wildcard `assetType` (null) vs specific | specific wins |
| wildcard `segment` vs specific | specific wins |
| `planCode` beats `exchange` beats `segment` beats `assetType` | specificity weights honoured |
| candidate with conflicting declared dimension | disqualified, not merely outranked |
| two equal-specificity candidates, different `startDate` | later `startDate` wins |
| two equal-specificity, equal `startDate` | `BadRequestException` naming both codes |
| date before `startDate` | not selected |
| date after `endDate` | not selected |
| `endDate` null (open-ended) | selected |
| date exactly on `startDate` / `endDate` | selected (inclusive both ends) |
| `status != ACTIVE` | not selected |
| no candidates | empty, WARN |
| cache returns the same instance twice | one repository call |
| cache evicted after a publish | repository re-queried |

---

## 7. Tier D — Validator

`ChargeScheduleValidatorTest` — one test per FR-2 rejection, each asserting the **message**, not just the exception type.

Duplicate `code`; DERIVED → unknown `baseCode`; DERIVED whose base has `order >=` its own; DERIVED with empty `baseCodes`; TURNOVER without `rate`; FLAT without `flatAmount`; PER_UNIT without `perUnitAmount`; SLAB with empty slabs; SLAB with overlapping bands; SLAB with a gap; rate + flat without `aggregator` *(D7)*; unparseable `formula`; unparseable `eligibility`; `code` absent from `charge_catalogue`; `endDate` before `startDate`; negative rate. *(16 cases)*

---

## 8. Tier E — Golden contract notes

`ChargeGoldenFileTest`, fixtures in `src/test/resources/charges/golden/`. Each file: input trade + expected line items + expected total, verified once by a human against a real broker contract note, then frozen.

```json
{
  "name": "zerodha-equity-delivery-buy-100k-nse",
  "context": { "brokerName": "ZERODHA", "assetType": "EQUITY", "segment": "DELIVERY",
               "exchange": "NSE", "event": "BUY", "price": 1000, "quantity": 100 },
  "expected": {
    "lines": { "BROKERAGE": 0.00, "STT": 100.00, "EXCHANGE_TXN": 2.97,
               "SEBI_FEE": 0.10, "IPFT": 0.10, "STAMP_DUTY": 15.00, "GST": 0.57 },
    "total": 118.74
  }
}
```

Fixtures: buy ₹1L NSE; sell ₹1L NSE; buy ₹1L BSE (different exchange rate); a ₹500 trade where the brokerage floor bites; a ₹50L trade where a cap bites; second sell same scrip same day (no DP); sell of a different scrip same day (DP charged); zero-brokerage vs percentage-brokerage brokers; one per seeded broker.

**Assertion:** every line individually **and** the total, via `assertMoney` at ₹0.01. Line-level assertions matter — a total can be right while two components are compensating errors.

**D1 regression fixture.** One golden file carries the old implementation's output alongside the correct one, documenting the ₹17.45 GST overcharge on a ₹1L sell. If GST is ever computed over STT again, that fixture fails with a self-explanatory diff.

---

## 9. Tier F — Invariants

Properties that must hold for **any** valid rate card. Run against generated rule permutations rather than fixed inputs; the closest thing to a proof the design admits.

1. `total == sum(line.amount)` — always, for every generated schedule
2. no `ChargeLine.amount` is negative
3. no line is emitted for a rule whose `side` conflicts with the event
4. a `STATUTORY`-category line never appears in any DERIVED rule's base **unless explicitly listed** *(structural guard on D1)*
5. computing twice with identical input yields identical output (determinism — catches map-ordering bugs)
6. a rule with `active: false` never contributes
7. removing a rule from a schedule reduces the total by exactly that rule's amount, **except** where it is a DERIVED base
8. scaling turnover by *k* scales every pure-`TURNOVER` line by exactly *k* (linearity)
9. `amountByCode` keys exactly equal the emitted line codes
10. an empty rule list yields total 0 and no exception

---

## 10. Tier G — Seed data

`ChargeSeederServiceTest`

- every file under `resources/data/charges/` parses
- every file passes `ChargeScheduleValidator`
- every rule `code` exists in `charge-catalogue.json`
- no two shipped schedules overlap on the same scope and date range
- every schedule has `sourceUrl` and `verifiedOn` populated
- re-running the seeder is idempotent (no duplicate `scheduleCode`)

This is the test that catches a bad rate card **at build time**. It is the single highest-value test in the plan, because a rate-card typo is the most likely future defect and the one least visible in review.

---

## 11. Tier H — Persistence and dedupe *(integration)*

`ChargesIntegrationTest extends AbstractIntegrationTest`

- computation persists one `UserChargeEntity` with all lines and a correct `amountByCode`
- `scheduleId` provenance recorded and resolvable
- DP charged once across **two separate sell transactions** of the same scrip on the same day — the real dedupe path, inside one transaction boundary *(AC-4)*
- DP charged for a different scrip same day
- DP charged for the same scrip on the next day
- concurrent sells of the same scrip same day → DP charged exactly once *(the read-your-own-write assumption made explicit)*
- `deleteByEmail` removes all of a user's charges and nothing else
- summary aggregation: three trades roll up into one `amountByCode` map with correct sums
- an unknown charge code aggregates without a code change
- AMC cycle billing produces an `AMC_CYCLE` record with GST applied per the card

---

## 12. Tier I — API *(integration)*

- `POST /charges/simulate` returns a full breakdown and **persists nothing** (asserted by counting documents before and after)
- publishing a schedule over an open one closes the incumbent *(AC-8)*
- both schedules retrievable; the older applies to earlier dates
- publishing an invalid schedule → 400 with a readable message *(AC-9)*
- `GET /user-charges/user/{email}` scoped to that user only
- a user cannot read another user's charges
- schedule admin endpoints reject a non-admin token
- per-transaction contract-note endpoint returns line items in `order`

---

## 13. Tier J — The extensibility guarantee

`ChargeExtensibilityTest` — three tests that make AC-1 permanent:

1. **JSON-only charge.** Seed a schedule containing code `SYNTHETIC_LEVY_FOR_TEST`, which appears nowhere in Java. Run a trade. Assert it appears in the computation, in `amountByCode`, and in the aggregated summary report.
2. **JSON-only derived charge.** Seed a DERIVED rule whose base includes the synthetic code. Assert it computes.
3. **Repricing.** Publish a superseding schedule with a changed rate; assert trades before and after the boundary use the respective rates.

If anyone later hard-codes a charge name into a `switch` or a field, test 1 fails. That is the design promise defended by the build.

---

## 13a. Tier K — Temporal correctness

The scenario driving these: **a user uploads a 2024 transaction in 2026, after the 2024 rate card was superseded in 2025.** Tech-spec §14.

**Historical resolution**
- a superseded card still resolves for a date inside its historical window *(pins the §14.1 defect)*
- a card with `status: SUPERSEDED` set by a future maintainer still resolves — the predicate is `!= INACTIVE`, not `== ACTIVE`
- a card with `status: INACTIVE` resolves for **no** date, including dates inside its window
- superseding sets `endDate` and leaves `status` untouched
- three chained supersessions: a date in each window resolves to the correct generation
- an instrument profile revised by the AMC behaves identically for a backdated redemption

**Visible gaps**
- a transaction predating every card persists a `UserChargeEntity` with `resolution: NO_SCHEDULE` and zero lines
- `GET /user-charges/user/{email}/gaps` returns it
- seeding the missing card and recomputing turns it into `RESOLVED`
- a card applies but no rule matches the event → `NO_MATCHING_RULES`, not `NO_SCHEDULE`

**Quarterly batch processing** *(the actual upload model: quarterly, chronological)*
- a quarter of transactions processed in one batch produces one `UserChargeEntity` per transaction
- **re-uploading the same quarter does not double-charge** — rows are replaced, not appended
- two same-day sells of one scrip **within a single batch** yield exactly one DP charge
- ordering within the batch is respected: the earlier transaction carries the DP charge
- an AMC cycle already covered by `lastBilledThrough` is skipped on a re-run
- a batch spanning a rate-card boundary charges each transaction against the card in force on its own date
- resolver cache: a 200-transaction batch on one rate card performs one schedule lookup, not 200

**Out-of-sequence detection** *(the guarantee is operational, so it is verified rather than assumed)*
- a batch reaching back before the latest recorded transaction marks its computations `PROVISIONAL`
- those rows appear in the gaps endpoint
- an in-sequence batch marks nothing `PROVISIONAL`
- recompute after an out-of-sequence batch corrects `#firstTimeInvestor` across both purchases

**Recomputation**
- recompute is idempotent: running twice yields identical rows
- recompute rebuilds the financial year's charge aggregates rather than accumulating, so totals do not drift *(the §14.4 invariant)*
- recomputing after a rate correction updates the stored lines and the P&L projection together

## 14. Gates

| Gate | Threshold | Scope |
|---|---|---|
| Line coverage | ≥ 90% | `brokercharges.**` |
| Branch coverage | ≥ 85% | `brokercharges.**` |
| **Mutation score** | **≥ 85%** | `brokercharges.engine.**` |
| Golden files | 100% pass, ₹0.01 tolerance | all |
| Seed validation | 100% pass | all shipped cards |
| Modulith | green | whole app |

Mutation score is the gate that actually replaces QA effort. Line coverage proves a calculator executed; mutation score proves that if the calculator returned the wrong number, **a test would have failed**. For money code that is the only meaningful standard.

Scope PIT to `engine` only — mutating DTOs and entities produces noise and slows the run for no signal.

---

## 15. What this plan does not cover

Stated so the residual manual QA is a known, small list rather than an assumption:

1. **Are the seeded rates correct?** Tests prove the engine computes what the card says. Only a human comparing against the broker's live charges page proves the card is right. Mitigated by `sourceUrl` + `verifiedOn` and the golden fixtures.
2. **Performance under load.** No throughput testing; the resolver cache is asserted for correctness, not latency.
3. **Phase C P&L equivalence.** That the computed total behaves correctly in cost basis and realised gains is Phase C's test scope, driven by the Phase B reconciliation data.
