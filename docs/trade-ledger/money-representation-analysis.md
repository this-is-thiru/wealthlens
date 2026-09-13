# TL-8 — how money should be represented

**Question.** Every stored amount is a `double`. Should it become `BigDecimal`, or minor units, or
stay as it is?

**Recommendation: keep `double`, and canonicalise every stored amount to paise.** Reasoning below,
with the numbers that produced it. The full type migration is not justified *yet*, and §7 says what
would change that.

**Everything numeric here was measured, not reasoned about.** Method in §8.

---

## 1. What is actually wrong

`double` is binary floating point; `0.10` has no exact binary representation. Measured:

| Case | `double` | Exact | Difference |
|---|---|---|---|
| `0.10` added ten times | `0.9999999999999999` | `1.00` | 1e-16 |
| One delivery sell's 7 charge lines | `145.37999999999997` | `145.38` | 3e-14 |
| 319 trades → period turnover | `784693.5800000003` | `784693.58` | 3e-10 |
| 50,000 trades → lifetime turnover | `1.2432964242000148E8` | `124329642.42` | **1.48e-6** |

Every one of those fails an exact equality check.

`ChargeSummaryReport.merge` already works around it — accumulating in `BigDecimal` and rounding to
paise on every write. That workaround is the symptom that prompted this analysis, and §4 argues it is
actually the right pattern rather than a hack.

**The capital-gains side has no such protection.** `ProfitAndLossService` accumulates with raw
`+=` at yearly, monthly and half-month level — five fields × three levels — and
`TradeOutcomeRecorder` computes `netProfit` and `totalBuyValue` in plain `double`.

---

## 2. What is *not* wrong, stated plainly

It would be easy to write this up as a looming disaster. It is not one, and pretending otherwise
would make the recommendation untrustworthy.

**The worst error measured is ₹0.0000015 on ₹12.43 crore of lifetime turnover** — a relative error of
1.2e-14. No user will ever see it. No tax authority will ever see it. `double` carries 15–16
significant digits, and a rupee figure at this application's scale needs nine.

More decisive:

> **Rounding the accumulated total to paise recovers the exact value at every scale tested,
> including 50,000 trades.**

So the money is never *wrong* once rounded. Nobody is being over- or under-taxed by a floating-point
artefact today.

---

## 3. So what is the real problem

Not precision — **canonicalisation.**

The stored value for a charge that everyone agrees is ₹145.38 is `145.37999999999997`. That is close
enough to print, and wrong enough to break everything that treats a number as an identity:

- **Exact equality is unusable.** `MoneyAssert` exists for exactly this reason; every money assertion
  in the test suite carries a tolerance. That is a permanent tax on every test anyone writes.
- **A Mongo query for an exact amount misses.** `db.user_charges.find({total_charges: 145.38})`
  returns nothing for a row that logically holds 145.38.
- **Reconciliation against a broker's statement needs a tolerance**, so a genuine one-paisa
  discrepancy and a floating-point artefact look the same. That matters here: reconciliation is a
  feature of this system, not a hypothetical.
- **Two routes to the same total disagree in the last bits**, so "the total equals the sum of its
  parts" is only true after rounding.

That reframes the decision. The question is not "is `double` precise enough" — it is — but
"are stored amounts canonical". And that has a much cheaper answer than a type migration.

---

## 4. The options

### A. Leave it

Free. Keeps every problem in §3 permanently, including the test tolerance tax. Rejected: the
reconciliation point alone is enough, since it is the feature most likely to produce a false report.

### B. Keep `double`, canonicalise every stored amount *(recommended)*

Every money value is rounded to paise at the moment it is written, using the pattern
`ChargeSummaryReport.scaled` already proves:

```java
BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue()
```

This does **not** make `145.38` exactly representable — nothing can. It makes every route to that
value produce the *same* double, which is the property §3 actually needs. Exact equality then works,
Mongo queries match, and totals agree with the sum of their parts.

- **Cost:** one utility, ~31 money write sites, no schema change, no data migration.
- **Risk:** low, and the pattern is already in production in one place.
- **Weakness:** it is a discipline, not a guarantee. A new field written without the call silently
  reintroduces the problem. Mitigated by an ArchUnit rule (§6), not by hope.

### C. `BigDecimal` end to end

Correct by construction, and better supported than expected: **Spring Data maps `BigDecimal` to BSON
`Decimal128` natively** — verified against a real MongoDB, `{"$numberDecimal": "145.38"}`. It is a
proper numeric type, so range queries and aggregation keep working. That removes the objection I
expected to be decisive.

What is left is scale and timing:

- ~170 declarations — 48 entity fields, 83 DTO fields, 39 in context records — plus every arithmetic
  expression touching them.
- Every existing document migrates from BSON `Double` to `Decimal128`.
- Arithmetic gets noisier: `a.add(b).setScale(...)` instead of `a + b`. In a codebase this size that
  is a real readability cost, paid on every line, forever.
- It would land immediately after two large branches, on code that is working and tested.

Not wrong — **premature**. The measured harm does not justify it now, and B preserves the option.

### D. `long` paise (minor units)

Exact, fast, and the conventional answer for money. But every rate in the charges engine is a
fraction (`0.001` × turnover for STT), so intermediate arithmetic still needs a decimal type and
rounding at the boundary — `long` only moves the problem to the edges rather than removing it. And
`long` reads as a count, so a field holding `14538` invites being displayed as ₹14,538.

Rejected: the safety it adds over C is small, and it is further from how the domain talks.

---

## 5. Recommendation

**Option B now. Option C when a trigger in §7 fires, not before.**

The honest summary: `double` is not costing money and is not going to. It is costing *exactness*,
and exactness is recoverable by rounding at the write boundary — which one class already does and the
rest do not. Doing that everywhere is a day's work and removes every problem in §3. Migrating 170
declarations to remove a 1.2e-14 error is not a good trade today.

---

## 6. What Option B looks like

1. **`shared/util/TMoney`**, alongside the other `T`-prefixed utilities:
   - `double scale(double)` — round to paise, HALF_UP
   - `double sum(Collection<Double>)` and `double add(double, double)` — accumulate in `BigDecimal`,
     return a canonical `double`
   - Extract `ChargeSummaryReport.scaled` into it so there is one implementation, not two.
2. **Apply at all 31 money write sites**, the unprotected `ProfitAndLossService` accumulation first —
   it is the one that reaches a tax report.
3. **An ArchUnit rule** so a new money field cannot be written un-canonicalised. Without this, B
   decays: `ArchitectureTest` already encodes conventions this way, so it is the natural home.
4. **Then tighten `MoneyAssert`.** Once amounts are canonical, most assertions can compare exactly,
   and the tolerance becomes the exception that has to justify itself rather than the default. That
   is how you tell B actually worked.

---

## 7. What would make Option C the answer

Any one of these, and the calculus changes:

- **Sub-paise amounts ever need storing** — a per-unit rate, an unrounded intermediate, a foreign
  currency. Canonicalising to two places would then destroy information rather than fix it.
- **Multi-currency**, which brings its own scale and rounding rules per currency.
- **A regulator or auditor requires exact decimal storage.** For a personal tracker this is remote;
  for anything filed on someone else's behalf it is not.
- **Aggregation moves into MongoDB.** Database-side `$sum` over BSON `Double` reintroduces drift
  outside application control, where no canonicalisation call can reach it.
- **B is tried and decays anyway** — if the ArchUnit rule is fought rather than followed, the
  discipline is not holding and the type should carry the guarantee instead.

---

## 8. Method

`double` figures from a standalone harness summing 2-decimal rupee amounts — the scale actually
stored — against `BigDecimal` accumulation of the same values, seeded for reproducibility. Storage
behaviour from an integration test writing a `BigDecimal` through the real mapper to the Testcontainers
MongoDB and reading the raw BSON back. Field counts by grepping entity, DTO and context declarations.

**Confidence: high** on the measurements and on `Decimal128` mapping. **Medium** on the 31 write
sites being the complete set — it is a grep over setter names, and a field written through a
constructor or a builder would not appear. Implementing B should start by confirming that list
rather than trusting it.
