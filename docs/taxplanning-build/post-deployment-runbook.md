# WealthLens tax planning — post-deployment runbook

**What a database needs because of the tax-planning build, and how to check it, in deploy order.** It covers
everything related to tax planning, past and future: `taxplanning` and `taxengine`, and **any other module touched
because of them**, such as `portfolio` (W-085 – W-088u7) and `shared`. Each entry is keyed to the work item and
decision that introduced it, and says:
- when it runs, and who runs it;
- the exact command, with a read-only check before and after;
- whether a second run is safe, and the way back.

The **run log** at the end records every run, per database.

**The rule (R15, D-278, D-279, CLAUDE.md §6).** Any migration or setup that tax-planning work causes adds its
entry here **in its own commit**, whichever module the change sits in, and its plan names it under
"Post-deployment actions". This covers:
- a seed or a data correction;
- a startup runner whose outcome must be checked;
- a flag to set;
- a precheck before a constraint is applied.

A session never runs any of it against a real database. It writes the command; the owner runs it and the result
goes into the run log.

**Every operation here is idempotent (D-282).** A second run, including one after a run that failed part-way, either
completes the work or changes nothing, and a test proves it. **A one-time operation is removed after use:** each has
a row in **Phase 15 — Post-deployment cleanup** of the backlog (YouTrack parent **WL-28**), outside the build order
and taken once its run is logged below for every database it applies to (D-286). Operations from work unrelated to tax planning (the charges engine's own, the trade
ledger's) are kept in [`../charges-engine/production-runbook.md`](../charges-engine/production-runbook.md).

**Databases** (from the profile yamls, host and database name only):

| Profile | Host | Database |
|---|---|---|
| default, `staging` | `it-staging.6fu8nvy.mongodb.net` | `it-staging` |
| `prod` | `cluster0.x6trgen.mongodb.net` | `Cluster0` |

> **Open question.** The owner sees `Cluster0.trade_outcomes` for both staging and prod, while
> `application-staging.yaml` names `it-staging`. Run each step in the database each deployed app actually writes to.

`$BASE` is the app's base URL and `$ADMIN_TOKEN` a `SUPER_USER` JWT. `mongosh` steps run against the named database.

---

## 1. On every deploy — startup, automatic; confirm in the log

| # | Item / decision | What happens at startup | Check | If it fails |
|---|---|---|---|---|
| 1.1 | **W-079a** (D-210), **W-088s** (D-268) | `TaxEngineConfig` compiles every year in `rulepacks/shipped-years.json` (**2025-26, 2026-27**) and verifies each against its lock (`rulepacks/<year>.lock.json`) | The app starts. There is no database step: the rule packs ship in the JAR | **The boot stops** with a lock mismatch. Nothing reaches a database. Deploy the previous build; never edit a lock in place |
| 1.2 | **W-088u8a** (D-281, D-282) | **Nothing.** Tax policies are no longer seeded at boot: see §2.0 | — | — |
| 1.3 | **W-088u8c** (D-285) | **Nothing.** The W-086a financial-year rewrite no longer runs at boot: see §2.3. No `CommandLineRunner` remains | — | — |
| 1.4 | **W-088u8b** (D-284) | **Nothing.** `trade_outcomes` is no longer rebuilt at boot when empty: see §2.2 | — | — |

---

## 2. Once per database, by hand — in this order

> **There is one deploy, after Phases 0 – 14 are complete** (owner, D-287); nothing is deployed before then. Every
> step in this section runs at that deploy, in order, on each database. Take each step's "count before" again at that
> time: the figures recorded here (2026-10-20) are from today's databases.

### 2.0 W-088u8a (D-281, D-282) — seed the TY 2025-26 tax policies · `SUPER_USER` · **required on a fresh database**

**Why.** The tax engines read the slab, perquisite and allowance policies. They are seeded only when a `SUPER_USER`
asks (D-280), never at boot.

**Needs** a deploy carrying W-088u8a. On staging and prod, which the old boot seeder already filled, the dry run
should report everything already present, so there is nothing to write.

1. **Dry run** (the default):

   ```bash
   curl -sS -X POST "$BASE/tax-planning/admin/policies/seed" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   ```

   Expect, per part (`SLAB_NEW_REGIME`, `SLAB_OLD_REGIME`, `PERQUISITE`, `ALLOWANCE_CATALOGUE`, `ALLOWANCE_LIMITS`):
   - **fresh database:** `newDocuments` 1 / 1 / 1 / 39 / 14;
   - **existing database:** `newDocuments` all 0, `alreadyPresent` 1 / 1 / 1 / 39 / 14. If not, report it before
     writing.
2. **Write** (only if the dry run showed something to write): the same call with `?dryRun=false`.
3. **Verify** (read-only):

   ```bash
   curl -sS "$BASE/tax-planning/admin/policies?taxYear=2025-26" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   ```

   ```js
   db.tax_slab_policies.countDocuments({ tax_year: "2025-26", status: "ACTIVE" })     // 2
   db.perquisite_policies.countDocuments({ tax_year: "2025-26", status: "ACTIVE" })   // 1
   db.allowance_catalogue.countDocuments({ tax_year: "2025-26" })                    // 39
   db.allowance_limits.countDocuments({ tax_year: "2025-26" })                       // 14
   // each document written by this step carries audit_metadata.createdBy = the caller
   ```

**Idempotent, per document.** A document is skipped when one with its key is on file:
- slab: tax year, regime, active;
- perquisite: tax year, active;
- catalogue: code, tax year, regime;
- limits: allowance code, tax year, regime, employer type.

So a second run, including one after a run that failed part-way, seeds only what is missing. A file that fails to
load is an error naming it; the files before it stay seeded.

**Know this:**
- A changed file under `data/tax-policies/` does not replace a document already on file. Changing a seeded policy
  needs its own entry in this section.
- **Policies the old boot seeder wrote carry no `audit_metadata` at all.** Jackson 3 built them through the
  all-arguments constructor, leaving it null. Their author is unrecorded; nothing re-seeds them.

**Way back:** delete the documents this step wrote, by `audit_metadata.createdBy` and `createdAt`.

**Removed after use:** W-148 / WL-30, once this step is logged for every database.

### 2.1 W-088u7 (D-277) — correct the rebuild's double-counted buy charges · **prod only · outstanding**

**Why.** The startup rebuild (`TransactionBasedTradeOutcomeMigration`) once charged a lot's buy charge again on
each later sell (found in W-088u5, fixed in W-088u6). Those rows feed the capital-gains read-model (W-085 / W-086)
and `AnalyticsService`.

**Count already taken** (owner, 2026-10-20):
- **prod:** 163 rebuild rows, 146 lots, **12 lots sold more than once, 29 rows on them** (so at most 17 inflated),
  ₹68.99 charged on them;
- **staging:** none, so nothing to do there.

**Needs** a deploy carrying commit `725bf436` or later.

1. **Count before** (read-only):

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

   ```bash
   curl -sS -X POST "$BASE/trade-outcomes/rebuild-charges/correct" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   ```

   Expect `rowsExamined` ≤ 29 and `rowsCorrected` ≤ 17. Read every entry in `skipped`; a skipped user is left
   untouched.
4. **Write:** the same call with `?dryRun=false`.
5. **Count after:**
   - `chargedOnThoseLots` falls by exactly the dry run's `buyChargesBefore − buyChargesAfter`, and the other counts
     are unchanged;
   - a second `?dryRun=false` reports `rowsCorrected` 0.

**Second run:** safe, it changes nothing. **Way back:** `mongoimport --mode upsert` of the step-2 file. Detail:
`plans/W-088u7.md`.

**Removed after use:** W-147 / WL-29, once this is logged for prod.

### 2.2 W-088u8b (D-284) — rebuild `trade_outcomes` from raw transactions · `SUPER_USER` · **when needed, not once**

**Why.** `trade_outcomes` is derived from `transactions`; this recomputes it. It is a **standing operation, not a
one-time one** (D-284). The trade-ledger correction procedure needs it (`docs/trade-ledger/migration.md` §3a), so
it is not removed after use. It is never automatic (D-280).

**When:**
- on a new database whose `transactions` came from elsewhere;
- after a correction to a user's `transactions`, with that user's `trade_outcomes` rows backed up and deleted first.

**Per user, idempotent.** A user with **any** `trade_outcomes` row, from any writer, is skipped and listed, so V1 /
V2 rows are never duplicated. A re-run after a failure completes the rest; a re-run after a full run writes nothing.
A failing user is an error naming them; the users before stay written.

1. **Dry run** (the default):

   ```bash
   curl -sS -X POST "$BASE/trade-outcomes/rebuild" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   ```

   **Read `usersRebuilt` and `rowsWritten` before writing.** Only users with transactions and no rows at all should
   be in it. On staging and prod as they stand, that is users who never sold, giving 0 rows, plus any user whose
   rows were deleted.
2. **Write:** the same call with `?dryRun=false`.
3. **Verify:** for each rebuilt user, `db.trade_outcomes.countDocuments({ email: "<user>" })` equals the rows the
   report gave for them, and each row's `audit_metadata.createdBy` is the caller. A second call reports
   `rowsWritten` 0.

**Know this:** rebuilt rows carry no charge breakdown, so the capital-gains summary refuses a user whose rebuilt rows
have broker charges (D-238 C2a; §3.1). Rebuild one user at a time (delete only that user's rows), never the whole
collection.

**Way back:** delete the rows this run wrote (`audit_metadata.createdBy` = the caller, `createdAt` = the run).

### 2.3 W-088u8c (D-285) — rewrite the `FY`-form financial years · `SUPER_USER` · **prod only · once · outstanding**

**Why.** W-086a (D-239) moved `trade_outcomes` to one financial-year form, `2025-2026`. Its startup rewrite never ran
on prod: the owner's check (2026-10-20) found **163** `FY2025-26`-form rows there, the rebuild's rows, and none on
staging. Nothing queries `trade_outcomes` by financial year (D-239; the capital-gains summary selects by sell date),
so the old form misleads only whoever reads the field. This makes the stored year consistent.

1. **Count before** (read-only): `db.trade_outcomes.countDocuments({ financial_year: /^FY/ })`. Expect 163.
2. **Dry run** (the default):

   ```bash
   curl -sS -X POST "$BASE/trade-outcomes/financial-years/rewrite" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   ```

   Expect `rows` 163. A row with no sell date is refused by id here, before any write; correct it by hand first.
3. **Back up** the rows:

   ```bash
   mongoexport --uri "<prod uri>" --db Cluster0 --collection trade_outcomes \
     --query '{"financial_year":{"$regex":"^FY"}}' --out trade_outcomes-fy-rows-backup.json
   ```

4. **Write:** the same call with `?dryRun=false`. Each row's year comes from its own sell date, and
   `audit_metadata.updatedBy` is set to the caller.
5. **Verify:** the count from step 1 is 0, and a second call reports `rows` 0.

**Second run:** safe; nothing is left to rewrite. **Way back:** `mongoimport --mode upsert` of the step-3 file.

**Removed after use:** W-149 / WL-32, once this is logged for prod.

---

## 3. Standing data conditions — read-only checks, no action defined

These are states the build decided to **refuse or leave**, rather than repair. Run the check to know their size.
Any repair would be a new work item with its own section-2 entry.

| # | Item / decision | Condition | Read-only check |
|---|---|---|---|
| 3.1 | **W-086** (D-238 C2a) | `CapitalGainsSummaryService` **refuses** a user's summary if any of their disposals carries broker charges with no breakdown ("record those trades again"). The rebuild's rows never have one | Count below |
| 3.2 | **W-088f** (D-249 C4a, "no backfill") | Rows written before W-088f have no `acquisition_mode`; they read as `UNRECORDED` (`Acquisition.stored` maps a missing mode to it) | `db.trade_outcomes.countDocuments({ acquisition_mode: { $exists: false } })` |
| 3.3 | **W-088j** (D-292), narrowed by **W-088j2c2** (D-298), **W-088j2d** (D-299) and **W-088j2e** (D-300) | A side of a disposal with no charge breakdown says nothing of STT, so it reads as `UNKNOWN`, not "no STT". Since D-298 the engine settles such a side from its recorded venue: an equity side on `NSE` or `BSE` in `DELIVERY` (and a sale in `INTRADAY`) is chargeable under Chapter VII s.98 Sl. 1–3 — a buy only when the lot was acquired by purchase (Sl. 1 "Purchase", D-300), and a long-term buy acquired before 1-10-2004 or by a bonus or demerger is settled by S.O. 5054(E) (`CG-PART-LONG-NOTIFIED`). What is still **refused** by the tax engine: a side with nil charges and no breakdown whose exchange is not recorded, is spelt other than exactly `NSE` / `BSE`, or is genuinely off an exchange, and a long-term buy on `NSE` / `BSE` that is not shown to be a purchase and is not a bonus or demerger allotment — a rights allotment, or an unrecorded mode (D-300) (a side with charges and no breakdown is refused earlier, §3.1). No user declaration was built (D-299); a repair, if the count is not nil, is a new work item — the exchange string is the fault for most rows, and §2.2's rebuild fills a row's missing venue from `transactions` | Count below (an upper bound) |
| 3.4 | **W-088j2b** (D-295, "no backfill") | `trade_outcomes` rows written before W-088j2b have no per-side venue (`buy_exchange_name`, `buy_segment`, `sell_exchange_name`, `sell_segment`); they read as `TradeVenue.UNRECORDED`. The old `exchange_name` / `segment` are **not** read for tax (V1 took the exchange from the buy, V2 and the rebuild from the sale). Remedy for a user: §2.2's per-user rebuild, which writes the new fields. The segment is published as recorded: `DELIVERY` is also the entry default (D-295) | Count and values below |
| 3.5 | **W-088k3** (D-305) | The tax engine decides short-term or long-term itself, from `RS-CG-TERM`: an equity disposal is a listed security (twelve months) when its **sale** is recorded on `NSE` or `BSE` (`sell_exchange_name`; a reading, D-305 C1). A disposal whose sale exchange is absent or another string is **refused** for the term — no other period is shipped (D-305 C2). Rows written before W-088j2b have no `sell_exchange_name` (§3.4). Remedy for a user: §2.2's per-user rebuild, which writes it from `transactions.exchange_name` | Count below |

```js
// 3.1 — rows, and users, whose summary would be refused
db.trade_outcomes.aggregate([
  { $match: { $or: [
      { buy_broker_charges: { $ne: 0 }, $or: [ { buy_charge_breakup: { $exists: false } }, { buy_charge_breakup: {} } ] },
      { sell_broker_charges: { $ne: 0 }, $or: [ { sell_charge_breakup: { $exists: false } }, { sell_charge_breakup: {} } ] } ] } },
  { $group: { _id: "$email", rows: { $sum: 1 } } },
  { $group: { _id: null, users: { $sum: 1 }, rows: { $sum: "$rows" } } }
])

// 3.4 — rows without per-side venues (W-088j2b, D-295), and the exchange strings and segments trades hold
db.trade_outcomes.countDocuments({ sell_exchange_name: { $exists: false } })
db.transactions.aggregate([{ $group: { _id: { exchange: "$exchange_name", segment: "$segment" }, n: { $sum: 1 } } }])

// 3.5 — equity capital-gains disposals whose term the engine refuses: the sale not recorded on NSE or BSE (W-088k3, D-305)
db.trade_outcomes.aggregate([
  { $match: { asset_type: "EQUITY", capital_gains_type: { $in: ["SHORT_TERM", "LONG_TERM"] },
              sell_exchange_name: { $nin: ["NSE", "BSE"] } } },
  { $group: { _id: "$email", rows: { $sum: 1 } } },
  { $group: { _id: null, users: { $sum: 1 }, rows: { $sum: "$rows" } } }
])

// 3.3 — equity disposals the engine still refuses for STT after W-088j2c (D-298, D-299, D-300): a side with nil charges and no
// breakdown that no RS-STT-CHARGEABLE row settles. An upper bound: a long-term buy by purchase before 1-10-2004 is
// settled by CG-PART-LONG-NOTIFIED but is counted here.
db.trade_outcomes.aggregate([
  { $match: { asset_type: "EQUITY", $or: [
      { $and: [
          { $or: [ { sell_charge_breakup: { $exists: false } }, { sell_charge_breakup: {} } ] },
          { sell_broker_charges: { $in: [0, null] } },
          { $or: [ { sell_exchange_name: { $nin: ["NSE", "BSE"] } },
                   { sell_segment: { $nin: ["DELIVERY", "INTRADAY"] } } ] } ] },
      { $and: [
          { capital_gains_type: "LONG_TERM" },
          { acquisition_corporate_action: { $nin: ["BONUS", "DEMERGER"] } },
          { $or: [ { buy_charge_breakup: { $exists: false } }, { buy_charge_breakup: {} } ] },
          { buy_broker_charges: { $in: [0, null] } },
          { $or: [ { acquisition_mode: { $ne: "PURCHASE" } },
                   { buy_exchange_name: { $nin: ["NSE", "BSE"] } }, { buy_segment: { $ne: "DELIVERY" } } ] } ] } ] } },
  { $group: { _id: "$email", rows: { $sum: 1 } } },
  { $group: { _id: null, users: { $sum: 1 }, rows: { $sum: "$rows" } } }
])
```

---

## 4. Items with no deploy action — and how the past was checked

Every other tax-planning item changes only code or the rule data shipped in the JAR (`rulepacks/`): the rules,
nodes, tax years, catalogue and coverage, W-001 – W-088u5. Those reach a database only through the startup checks
in section 1. `tax_year_registry` has an entity and a repository, but no code reads or writes it, so it needs
nothing.

**The audit behind this list** (D-279, 2026-10-20). Every commit whose subject starts `taxplanning` was read for
files outside `taxplanning`, `taxengine`, `taxrules` (`taxengine.rules`' former home) and `rulepacks`:
- Each one is in `portfolio` or `shared`: the capital-gains read-model, the trade-outcome writers, the rebuild and
  its correction, and the FY runner. Sections 1 – 3 cover all of them.
- No such commit added an index. W-086's query by email and sell date uses the existing `trade_outcome_user_idx`.
- No such commit changed a `taxplanning` entity or `data/tax-policies/`. Their last changes are the module's own
  PRs (#58, #59), before this build, so nothing in §2.0's files needs a correction.

---

## Run log — one line per run, per database

| Date | Database | Step | Run by | Result |
|---|---|---|---|---|
| 2026-10-20 | prod `Cluster0` | 2.1 count | owner | `rebuildRows` 163, `lots` 146, `lotsSoldMoreThanOnce` 12, `rowsOnThoseLots` 29, `chargedOnThoseLots` 68.99166666666666 |
| 2026-10-20 | staging | 2.1 count | owner | no rebuild rows, so 2.1 not needed |
| 2026-10-20 | staging | W-088u8c checks | owner | `FY` rows 0; `temporary_transactions` absent |
| 2026-10-20 | prod `Cluster0` | W-088u8c checks | owner | `FY` rows **163** (W-086a's rewrite never ran here: prod predates `91359d35`); `temporary_transactions` absent |

**Not yet in this log:** §2.0 for each database (expected on staging and prod: all already present), 1.3's 0 and
the section 3 counts, for each database.
