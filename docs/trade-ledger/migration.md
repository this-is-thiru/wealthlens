# Deploying the trade-ledger branch — what has to happen to the data

**Short answer: no schema migration, but two mandatory data steps and three checks.**

MongoDB does not need a schema change for an added field — an absent field reads as null. Two of the
added fields are not safe as null, and that is what makes this list non-empty.

Verified against a real MongoDB, not reasoned about:
`backend/src/test/java/com/thiru/wealthlens/integration/LegacyDocumentUpgradeIntegrationTest.java`.

---

## 1. MANDATORY — backfill `version` on `profit_and_loss`

**Without this, every trade for an existing user in an existing financial year fails.**

`ProfitAndLossEntity` gained `@Version private Long version` for optimistic locking. On a document
written before that, the field is absent, so the loaded entity's version is null — and Spring Data
reads a null version as *"this is a new document"*. It issues an **insert**, against an `_id` that
already exists:

```
DuplicateKeyException: E11000 duplicate key error
  collection: profit_and_loss index: _id_ dup key: { _id: "..." }
```

Not a corner case: it is every read-modify-write on every pre-existing period.

```js
// run BEFORE deploying
db.profit_and_loss.updateMany(
  { version: { $exists: false } },
  { $set: { version: NumberLong(0) } }
)
```

Idempotent, and safe to run again. Verify with
`db.profit_and_loss.countDocuments({ version: { $exists: false } })` → must be `0`.

---

## 2. MANDATORY — re-seed the charge catalogue

`POST /charges/seed` as `SUPER_USER`, **after** deploying.

`ChargeCatalogueEntity` gained `deductibleForCapitalGains`. Codes already in `charge_catalogue`
do not have it, and an absent flag is **not** read as "unknown" — it is excluded from deductible
cost. Every trade outcome would record `deductibleSellCharges = 0`, understating deductible cost,
overstating the gain, and overstating tax. Silently.

Seeding skips codes already on file, so this used to be a manual migration. It no longer is: the
seeder now backfills the flag **when and only when the stored value is null**, and never overwrites
a value already there. Re-seeding is therefore the migration.

Verify: `db.charge_catalogue.countDocuments({ deductible_for_capital_gains: { $exists: false } })`
→ must be `0`. Expect `STT`, `AMC` and `ACCOUNT_OPENING` to be `false` and the other nine `true`.

---

## 3. CHECK — duplicates that would block a unique index

`PortfolioIndexInitializer` now covers `transactions`, and it already covers `profit_and_loss`.
Both carry unique indexes that, with `auto-index-creation` off, **have never actually been applied**.
They are about to meet data written without them.

```js
// transactions -- each result means a temporary transaction was redriven more than once
db.transactions.aggregate([
  { $match: { source_temp_transaction_id: { $ne: null } } },
  { $sort:  { _id: 1 } },
  { $group: { _id: "$source_temp_transaction_id", n: { $sum: 1 }, ids: { $push: "$_id" },
              type: { $first: "$transaction_type" }, code: { $first: "$stock_code" },
              qty:  { $first: "$quantity" },        date: { $first: "$transaction_date" } } },
  { $match: { n: { $gt: 1 } } }
])

// profit_and_loss -- must group on BOTH fields. Listing financial_year alone looks alarming
// on a multi-user database and proves nothing either way.
db.profit_and_loss.aggregate([
  { $group: { _id: { email: "$email", fy: "$financial_year" }, n: { $sum: 1 }, ids: { $push: "$_id" } } },
  { $match: { n: { $gt: 1 } } }
])
```

Empty is the expected answer for both.

**If you deploy without checking, the application still starts.** Index creation is caught and
logged at ERROR naming the collection and the keys — deliberately, because failing a deploy over
pre-existing data is worse. The cost is that the constraint can be silently absent, so after any
deploy grep the startup log for `Could not create index`.

---

## 3a. If either query returns something — how to correct it

**Read this first: you are not obliged to fix it before deploying.** The unique indexes are a *new*
safety constraint, not something the application needs in order to run. Index creation already fails
soft. So the options are, in order of how much they cost:

1. **Deploy anyway.** The constraint is absent, logged at ERROR, and everything else works. Resolve
   the data when you have time and restart to pick the index up. TL-7's replay guard stops *new*
   duplicates regardless of whether the index exists.
2. **Resolve the data, then deploy.** Correct, and the sections below say how.
3. Do not "fix" it by dropping the unique flag. That silently retires the constraint, which is the
   state we are trying to leave.

### Duplicate `source_temp_transaction_id`

Each duplicate is a temporary transaction that was redriven more than once. **The first row is the
legitimate one** (the aggregate above sorts by `_id`, whose ObjectId embeds its creation time); every
later one re-applied the same trade. So each extra row means the holding was applied twice and profit
and loss double-counted — deleting the transaction row alone corrects the *index*, not the *money*.

For each extra id, find what it touched before deleting anything:

```js
const dup = "<the second or later _id from the aggregate>";

db.assets.find({ $or: [ { buy_transaction_ids: dup }, { sell_transaction_ids: dup } ] })
db.trade_outcomes.find({ source_sell_transaction_id: dup })
db.user_charges.find({ transaction_id: dup })
db.transactions.find({ _id: dup })    // note its email, stock_code, transaction_date, quantity
```

Then, by case:

- **A duplicated BUY.** V2 writes one `assets` document per buy, so the duplicate almost always has
  its own document — identifiable by `buy_transaction_ids` containing only `dup`. Delete that
  document, the `transactions` row, and its `user_charges` row. V1 merges into an existing lot
  instead, so there the quantity and `broker_charges` have to be decremented by the duplicate's
  amounts rather than a document deleted.
- **A duplicated SELL.** Harder, and worth doing carefully: quantity was decremented twice across
  possibly several lots. Restore the quantity on each `assets` document listing `dup` in
  `sell_transaction_ids`, remove `dup` from that array, then delete the `trade_outcomes`,
  `user_charges` and `transactions` rows.
- **Either.** The period's `profit_and_loss` totals now include the duplicate. There is **no
  recompute-from-transactions tool for `profit_and_loss` or `assets`** — see the note below — so the
  affected period has to be corrected by hand or accepted.

**`trade_outcomes` is the one thing that rebuilds itself.** Drop the collection and restart:
`TradeOutcomeMigrationRunner` repopulates it from raw transactions when it finds the collection
empty. Do that *after* the duplicate `transactions` rows are gone, so the rebuild does not reproduce
them.

### Duplicate `{email, financial_year}` in `profit_and_loss`

Two documents for the same user and year, each holding part of the totals — the result of a race,
which is exactly what the `@Version` field added in §1 now prevents.

There is no merge tool and writing one is not worth it for a handful of rows. Practical order:

```js
// see how much is in each before choosing
db.profit_and_loss.find({ email: "<email>", financial_year: "<fy>" })
```

Compare `realised_profits.short_term_capital_gains.sell_amount` and its long-term counterpart across
the documents. If one is clearly a stub — created by a race and written to once — keep the fuller one
and delete the stub. If both hold real figures, they have to be added together by hand: the
hierarchy is yearly → monthly → half-month, and every level needs the same treatment.

**Do not simply delete one.** These are realised capital gains; the deleted figures do not come back,
and nothing recomputes them.

### The gap this exposes

`profit_and_loss` and `assets` are **derived** state with no way to rebuild them from `transactions`,
which is the source of truth. `trade_outcomes` has exactly that (`TradeOutcomeMigrationRunner`), and
it is why that collection is the easy one here. A recompute for the other two would make every
correction above a one-liner instead of a careful manual edit.

Recorded as **B-4** in [`backlog.md`](backlog.md). It is out of scope for this branch, but it is the
reason "resolve the duplicates" is more work than it sounds.

---

## 4. DONE — trades dated 31 March

Three financial-year derivations compared `isBefore(March 31)`, filing a trade made **on** 31 March
into the following year. Fixed in code.

```js
db.transactions.countDocuments({ transaction_date: /-03-31$/ })
```

**Ran against production on 2026-09-12: `0`.** No historical data to correct. Re-run it if the
branch sits unmerged long enough for a 31 March to pass.

---

## 5. NO ACTION — additive fields, and the response rename

Safe as null or absent, confirmed by test:

| Collection | Added | Why it is safe |
|---|---|---|
| `trade_outcomes` | `segment`, `instrument_sub_class`, `classification_reason`, `buy_charge_breakup`, `sell_charge_breakup`, `deductible_buy_charges`, `deductible_sell_charges` | Only new rows carry them. The maps read back non-empty on legacy rows despite `@AllArgsConstructor` — checked, the ADR-27 trap does not bite here |
| `profit_and_loss` | `realised_profits.gains_by_classification` | Absent reads as an empty map; the two legacy buckets are still written exactly as before |
| `transactions` | `idempotency_key`, `trade_fingerprint`, `submitted_at` | Absent on old rows; both indexes are **sparse**, so old rows are simply not indexed |
| `holding_period_policies` | new collection | Created by seeding |

**The response rename is not a database change.** `ReportModelResponse` is a DTO and is never
persisted. It does change the API: `purchasePrice` → `purchaseAmount`, `sellPrice` → `sellAmount`,
`brokerCharges` → `brokerage`. Those three keys previously returned `0.0` in every response, so no
client can have been using their values — but any client reading the old keys must be updated.

---

## Order

1. Run the duplicate checks (§3). Resolve anything they return. (§4 is already done — it returned 0.)
2. Run the `version` backfill (§1). **Before deploying.** — *done 2026-09-12*
3. Deploy.
4. Grep the startup log for `Could not create index`.
5. `POST /charges/seed` (§2), then verify the catalogue count is 0.
6. Update any client reading the three renamed response keys (§5).
