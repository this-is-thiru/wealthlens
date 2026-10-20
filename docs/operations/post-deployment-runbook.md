# Post-deployment runbook — every one-time and per-deploy action, in order

**The one list of what has to happen to a database, by hand or at startup, because of a change in this
repository.** Each entry says when it runs, who runs it, what it does, how to check it before and after, whether a
second run is safe, and the way back. The **run log** at the end records each run per database.

**The rule (D-278, CLAUDE.md §6, `docs/taxplanning-build/00-START-HERE.md` R15).** Any change that needs something
done once, or on every deploy, adds its entry here **in the same commit**:

- a seed call, a data correction or migration, or a flag to set;
- a precheck before an index or constraint is applied;
- a startup runner whose outcome must be checked.

Until the entry exists, the change is not done. **Nothing in this file is run from a Claude Code session against a
real database.** The session writes the command; the owner runs it and pastes the result.

**Databases** (from the profile yamls, host and database name only):

| Profile | Host | Database |
|---|---|---|
| default, `staging` | `it-staging.6fu8nvy.mongodb.net` | `it-staging` |
| `prod` | `cluster0.x6trgen.mongodb.net` | `Cluster0` |

> **Open question.** The owner reports seeing `Cluster0.trade_outcomes` for both staging and prod, while
> `application-staging.yaml` names database `it-staging`. Run every step below in the database each deployed app
> actually writes to, and correct this table once that is confirmed.

**Conventions:**
- `$BASE` is the app's base URL. `$ADMIN_TOKEN` is a `SUPER_USER` JWT. `$TOKEN` is a user's JWT.
- `mongosh` steps are run connected to the database named.
- Responses are wrapped in `ApiResponse`, hence `jq '.data'`.

---

## A. Before every deploy

| # | Action | Check | Source |
|---|---|---|---|
| A1 | `MONGO_USER`, `MONGO_PASSWORD`, `JWT_SECURITY_KEY` are set | The app refuses to start without the JWT key | charges runbook §1.1 |
| A2 | MongoDB is a **replica set** (multi-document `@Transactional`) | Startup log: no `TransactionSafetyAuditor` warning | charges runbook §1.2 |
| A3 | A `SUPER_USER` account exists | It can obtain a token for B2 and C1–C3 | charges runbook §1.3 |
| A4 | **Once, before the first deploy that carries TL-7** (the `transactions` unique index on `source_temp_transaction_id`): the duplicate precheck | Must return nothing; see below | charges runbook "Before deploying TL-7" |

```js
// A4 — read-only. Empty = nothing to do. Any row = a temporary transaction redriven more than once; resolve first.
db.transactions.aggregate([
  { $match: { source_temp_transaction_id: { $ne: null } } },
  { $group: { _id: "$source_temp_transaction_id", n: { $sum: 1 }, ids: { $push: "$_id" } } },
  { $match: { n: { $gt: 1 } } }
])
```

---

## B. At startup — automatic; check the log after every deploy

These run by themselves. The steps here are only to **confirm** each one did what it should.

| # | What runs | What it does | How to check | Second run |
|---|---|---|---|---|
| B1 | Index initializers (`PortfolioIndexInitializer`, `ChargeIndexInitializer`, `@PostConstruct`) | Create the declared indexes, `auto-index-creation` being off | Startup log has **no** `Could not create index` (portfolio logs it at ERROR, charges at WARN; neither blocks startup) | Safe |
| B2 | `ChargeReadinessAuditor` (`@PostConstruct`) | With the charges engine on, warns if no rate cards are on file | Startup log has **no** `NO RATE CARDS`; if it does, C1 has not been done in this database | Read-only |
| B3 | `PolicySeederService` (`@PostConstruct`, `taxplanning`) | Seeds slab, perquisite and allowance policies from `data/tax-policies/*.json` | Log `Starting tax policy seeding for 2025-26` with no load error | Idempotent |
| B4 | `TempTransactionMigrationRunner` (`CommandLineRunner`) | Moves any legacy `temporary_transactions` documents into `transactions` with `status: TEMPORARY`, then drops the old collection | Log: `No legacy 'temporary_transactions' collection found — skipping migration`, or `Migrated N temporary transactions and dropped old collection` once | Safe; nothing to do once dropped |
| B5 | `TradeOutcomeMigrationRunner` (`CommandLineRunner`) | **Only when `trade_outcomes` is empty or absent:** the transaction-based rebuild of every user's trade outcomes | Log `already has data — skipping` on any database that has rows. On an empty database it rebuilds; since W-088u6 (D-276) buy charges are allocated once | Skips once rows exist |
| B6 | `TradeOutcomeFinancialYearMigrationRunner` (`CommandLineRunner`, W-086a, D-239) | Rewrites any `trade_outcomes` row whose financial year is `FY2025-26`-form to `2025-2026`-form, from its sell date | `db.trade_outcomes.countDocuments({ financial_year: /^FY/ })` returns 0 | Idempotent; may be deleted once every database has started on it |

The three `CommandLineRunner`s carry no `@Order`, so their order is not fixed. It does not matter:
- B5 only runs on an empty collection, and with today's code it writes the `2025-2026` form itself;
- B6 only rewrites `FY` rows, whoever wrote them.

---

## C. Once per database, by hand — in this order

### C1. Seed the rate cards — **required before the first trade** (ADR-27)

The charges engine ships switched on in every profile: `engine-enabled`, `shadow-recording` and `authoritative`
are all `true` in `application.yaml`, `-staging` and `-prod`. With no rate cards, every V2 trade is charged ₹0,
and under `authoritative` that zero becomes the cost basis.

```bash
curl -sS -X POST "$BASE/charges/seed" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
# expect 12 catalogue codes, 11 rate cards, 2 scheme profiles; a second run writes nothing and says so
```

**Verify:**

```bash
curl -sS "$BASE/charge-schedules/drift" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'       # must be empty
curl -sS "$BASE/charge-schedules/unverified" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'  # empty, or rate verification is due (D3)
```

And B2's warning is gone on the next start. Idempotent. Full detail: `docs/charges-engine/production-runbook.md` §2.

### C2. Verify a known charge before real trades go through

`POST /charges/simulate` with a known contract (Zerodha equity delivery, a ₹1,00,000 sell) and compare it with the
expected figure in `docs/charges-engine/production-runbook.md` §3. Read-only.

### C3. Register demat accounts — only if AMC billing is wanted

`POST /charge-accounts/user/{email}`, per `docs/charges-engine/production-runbook.md` §4. Then the AMC cycle, D1,
applies.

### C4. Correct the rebuild's double-counted buy charges — **prod only, outstanding** (W-088u7, D-277)

The rebuild that B5 once ran charged a lot's buy charge again on each later sell. The owner's read-only count
(2026-10-20) found:
- **prod:** `rebuildRows` 163, `lots` 146, `lotsSoldMoreThanOnce` 12, `rowsOnThoseLots` 29, `chargedOnThoseLots`
  ₹68.99, so at most 17 inflated rows;
- **staging:** none.

Needs a deploy carrying commit `725bf436` or later.

1. **Count (read-only, before):**

   ```js
   db.trade_outcomes.aggregate([
     { $match: { source_buy_lot_id: /^lot-/ } },
     { $group: {
         _id: { e: "$email", b: "$broker_name", h: "$account_holder", l: "$source_buy_lot_id" },
         rows: { $sum: 1 },
         charged: { $sum: { $add: [ { $ifNull: ["$buy_broker_charges", 0] }, { $ifNull: ["$buy_misc_charges", 0] } ] } } } },
     { $group: {
         _id: null,
         rebuildRows: { $sum: "$rows" },
         lots: { $sum: 1 },
         lotsSoldMoreThanOnce: { $sum: { $cond: [ { $gt: ["$rows", 1] }, 1, 0 ] } },
         rowsOnThoseLots: { $sum: { $cond: [ { $gt: ["$rows", 1] }, "$rows", 0 ] } },
         chargedOnThoseLots: { $sum: { $cond: [ { $gt: ["$rows", 1] }, "$charged", 0 ] } } } }
   ])
   ```

2. **Back up** the rebuild rows (163 documents expected):

   ```bash
   mongoexport --uri "<prod uri>" --db Cluster0 --collection trade_outcomes \
     --query '{"source_buy_lot_id":{"$regex":"^lot-"}}' --out trade_outcomes-rebuild-rows-backup.json
   ```

3. **Dry run** (the default):
   `curl -sS -X POST "$BASE/trade-outcomes/rebuild-charges/correct" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'`.
   Expect `rowsExamined` ≤ 29 and `rowsCorrected` ≤ 17. Read every entry in `skipped`; a skipped user's rows are
   not touched.
4. **Write:** the same call with `?dryRun=false`.
5. **Verify:**
   - re-run step 1: `chargedOnThoseLots` falls by exactly the dry run's `buyChargesBefore − buyChargesAfter`, and
     the other counts are unchanged;
   - a second `?dryRun=false` reports `rowsCorrected` 0.

**Way back:** `mongoimport --mode upsert` of the step-2 file. Full detail: `docs/taxplanning-build/plans/W-088u7.md`.

---

## D. Recurring, by hand

| # | Action | When | Check |
|---|---|---|---|
| D1 | AMC cycle, `POST /charges/amc/impose` (`SUPER_USER`) | Quarterly or annually, per account frequency (only after C3) | Idempotent per account and period |
| D2 | Drift check, `GET /charge-schedules/drift` | Every deploy | Empty |
| D3 | Rate verification, `GET /charge-schedules/unverified` | Every 90 days (ADR-26) | Empty after re-verifying the listed cards |
| D4 | A broker's rate change: publish a successor card, `POST /charge-schedules` | When rates change | **Never edit a deployed card** (ADR-26); the successor supersedes |

---

## E. Done and closed — kept for history

| # | What | When | Note |
|---|---|---|---|
| E1 | `db.charge_schedules.deleteMany({ schedule_code: /_2025_04$/ })` before re-seeding the corrected 2025 cards | Charges engine AC2, before Phase B recorded anything | One-off, `docs/charges-engine/ac2-rate-verification.md` §6. **Never again:** ADR-26 publishes successors instead |
| E2 | The three superseded charge collections (`broker_charges`, `user_broker_charges`, `asset_management_details`) | Charges engine Chunk 11 | Code deleted; nothing reads them; no migration |

---

## Run log — one line per run, per database

| Date | Database | Step | Run by | Result (paste the counts / report) |
|---|---|---|---|---|
| 2026-10-20 | prod `Cluster0` | C4.1 count | owner | `rebuildRows` 163, `lots` 146, `lotsSoldMoreThanOnce` 12, `rowsOnThoseLots` 29, `chargedOnThoseLots` 68.99166666666666 |
| 2026-10-20 | staging | C4.1 count | owner | no rows: no rebuild rows, C4 not needed |

**Not yet recorded in this log** (done or not, the repository cannot tell): A4, C1, C2 and C3 for each database.
Add a line when each is confirmed.
