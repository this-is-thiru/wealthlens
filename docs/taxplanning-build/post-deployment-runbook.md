# WealthLens — deploy runbook

**Everything a database needs at a deploy, and how to check it, in the order it runs.** This one runbook covers:
- **the charge engine** (`brokercharges`): seeding, clearing, its standing operations;
- **the trade ledger** (`portfolio`): the prechecks before its unique indexes, and the `version` backfill;
- **tax planning** (`taxplanning`, `taxengine`), and any other module touched because of it, such as `portfolio`
  (W-085 – W-088u7) and `shared`.

The trade ledger's and the charge engine's old runbooks (`docs/trade-ledger/migration.md`, `docs/charges-engine/`) moved here
(W-176, D-379) and were deleted by W-357b7c and W-357b6b (D-913, D-909); §1B now states its own check figure, and what the engine does is
`docs/modules/brokercharges.md`.

Each entry is keyed to the work item and decision that introduced it, and says:
- when it runs, and who runs it;
- the exact command, with a read-only check before and after;
- whether a second run is safe, and the way back.

The **run log** at the end records every run, per database.

**The rule (R15, D-278, D-279, D-379, CLAUDE.md §6).** Any migration or setup that a change causes adds its entry
here **in that change's own commit**, whichever module it sits in, and its plan names it under "Post-deployment
actions". This covers:
- a seed or a data correction;
- a startup runner whose outcome must be checked;
- a flag to set;
- a precheck before a constraint is applied.

A session never runs any of it against a real database. It writes the command; the owner runs it and the result
goes into the run log.

**Every operation here is idempotent (D-282).** A second run, including one after a run that failed part-way, either
completes the work or changes nothing, and a test proves it. **A one-time operation is removed after use:** each has
a row in **Phase 15 — Post-deployment cleanup** of the backlog (YouTrack parent **WL-28**), outside the build order
and taken once its run is logged below for every database it applies to (D-286).

**Every file a step here saves is personal data, and is deleted (W-237f C3a).** A `mongoexport` taken as a step's way back
(§0.5, §0.8, §1A, §2.1 – §2.5, §1.s, §1.r) is the owner's own copy, and Act s.8(7)(a) reaches it as it reaches the database. Delete
it once its step's result is in the run log and checked, or once the longest snapshot retention in §1.s has passed,
whichever is first. The run-log line says "export deleted".

**Databases** (from the profile yamls, host and database name only). Each cluster holds one application database:
staging's app writes `it-staging`, prod's writes `Cluster0` (owner's counts, 2026-10-27; this settles the earlier
open question).

| Profile | Host | Database |
|---|---|---|
| default (dev) | `wealthlens-dev.gmhwxe2.mongodb.net` | `wealthlens-dev` — empty on 2026-11-19 (owner) |
| `staging` | `it-staging.6fu8nvy.mongodb.net` | `it-staging` |
| `prod` | `cluster0.x6trgen.mongodb.net` | `Cluster0` |

The default profile writes the dev cluster since `ec85b188` ("dev db"), no longer `it-staging`
(`backend/src/main/resources/application.yaml:14 – 15`; W-334).

`$BASE` is the app's base URL, `$ADMIN_TOKEN` a `SUPER_USER` JWT and `$TOKEN` any user's JWT. `mongosh` steps run
against the named database.

**The order, from a fresh start** (owner, 2026-10-27: "we can run all runbook from starting").

| § | Step | prod `Cluster0` | staging `it-staging` |
|---|---|---|---|
| 0.1 | Environment, replica set, a `SUPER_USER` | check | check |
| 0.2 | Duplicate checks before the unique indexes | run | run |
| 0.3 | `profit_and_loss.version` present | **done**: 0 without (2026-10-27) | **done**: 0 without (2026-10-27) |
| 0.8 | Legacy salary and policy collections: count, back up, drop (W-159) | **retired** (W-331): done (W-334e1) | **retired** (W-331): done (W-334d2) |
| 0.9 | The move: copy MongoDB into PostgreSQL (W-337g2c), at W-337i, before §1B on that database | at the move | at the move |
| 2.20 | Stored personal data made plain (W-428a), before the first normal start of the release carrying it | **done** 2026-11-28 (`m1.9`) | **done** 2026-11-28 (`m1.9`; no dump before, D-1038) |
| 1 | Deploy; startup checks | check | check |
| 1A | Clear the charge engine's data | configuration only: 0 / 0 / 0, a no-op | configuration 12 / 11 / 2; `user_charges` 328, `charge_accounts` 1, P&L charge side 2 |
| 1B | Seed the charge engine; drift, unverified, one simulate | run | run |
| 2.1 – 2.14 | Tax planning, by hand, in order (retired: 2.0 by W-159; 2.1, 2.3 – 2.7, 2.9, 2.10 by Phase 15, D-874 – D-881; 2.13 by W-294, D-890; 2.14 by W-299, D-891; 2.8 by W-173, D-892; 2.18 by W-368, D-898) | per step | per step |
| 3 | Standing data conditions, read-only | check | check |
| 5 | Standing operations (not deploy steps) | as needed | as needed |

**Why 1A and 1B come straight after the deploy:** the charge flags ship on (`engine-enabled`, `shadow-recording`
and `authoritative` are `true` in all three profile yamls), so a V2 trade is priced as soon as the app is up. Clear
and seed before anyone trades. On staging the owner is the only user.

---

## Next deploy — what prod and staging still need

All three databases are on PostgreSQL (dev W-337i2, staging W-337i4, prod W-337i5; D-1012, D-1014); dev, staging and prod run `m1.9` (W-428b, D-1038); `m1.8` already needed no MongoDB (W-337j2, D-1018); the app runs
locally, nothing is hosted. An item that needs something at the next deploy adds its line here when it is committed; each line goes
when its run is logged for both. Everything below is the full sequence for a fresh database. The move's lines (§0.9, §2.19, §0.10)
and W-370's went with W-337i6 (D-1015), W-337j2's start check once logged for both (2026-11-28); W-427's env-file line and W-428a's §2.20 once logged for all three (2026-11-28, D-1038).

1. §1.h W-358d3 (D-885) — **read-only, a week after m1.6.1 (due 2026-11-29), per database:** §1.h's count. On 2026-11-22 `upgraded` was 1 on each (the owner's sign-in at W-366b / c row 4); a week after, read in SQL on PostgreSQL (`SELECT count(*) AS users, count(*) FILTER (WHERE password LIKE '{argon2}%') AS upgraded FROM wealthlens.user_details;`, D-1007 C2a, D-1013 C1a): the accounts that signed in or registered since. Going back to `m1.5` needs a password reset for each upgraded account (§1.h).

## 0. Before deploying

### 0.1 Environment · owner · **every deploy**

| # | Check | Why |
|---|---|---|
| 0.1.1 | `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` (W-427; a build before it reads `APP_POSTGRES_DATASOURCE_*`), `JWT_SECURITY_KEY`, `TAX_DATA_KEY`, `TAX_DIGEST_KEY`, `PRIVACY_CONTACT` are set | The app does not start without the JWT key, deliberately: a defaulted signing key is a forged-token vulnerability. Nor without the two tax keys (W-134, D-447): they encrypt stored personal data and digest PANs, §0.5. Nor without the privacy contact (W-235; app-wide and renamed from `TAX_PRIVACY_CONTACT` by W-237a, never set under the old name): who answers questions about the processing of personal data, returned in every erasure receipt (DPDP Rules, 2025 r.9), §1.z |
| 0.1.2 | ~~MongoDB is a replica set~~ | **Retired by W-337j2 (D-1018):** the app has no MongoDB client; PostgreSQL's transactions need no setting. `MONGO_USER` / `MONGO_PASSWORD` are no longer read |
| 0.1.3 | A `SUPER_USER` account exists | §1B, §2 and §5 need one: seeding, publishing rate cards, the AMC cycle and every `/admin`-style correction are `SUPER_USER`-only. Registration makes only `USER` accounts (W-255), so the owner grants the role in that database: register through `POST /api/v1/public/auth/register`, then `db.user_details.updateOne({ email: "<stored owner email>", roles: "USER" }, { $set: { roles: "SUPER_USER" } })` with the stored form from §0.7 (from W-253 the email is stored encrypted and is no longer the `_id`), then sign in again (a token carries the role read at sign-in). A second run matches nothing. Way back: the same update with the two roles swapped |

### 0.2 TL-7 — duplicates that would block a unique index · owner, `mongosh` · **retired by W-337j3a (D-1020) · do not run**

> **Retired.** Mongo-only: the unique indexes `PortfolioIndexInitializer` applied are Flyway DDL on PostgreSQL, built before the copy, which refuses a violating row (W-337g), so the step cannot apply on PostgreSQL. Kept below as the record of what it did.

**Why.** `PortfolioIndexInitializer` covers `transactions` and `profit_and_loss`. Both carry unique indexes
(`transactions.source_temp_transaction_id`, unique and sparse; `profit_and_loss {email, financial_year}`) that, with
`auto-index-creation` off, had never been applied. They meet data written without them.

```js
// transactions — each result is a temporary transaction redriven more than once
db.transactions.aggregate([
  { $match: { source_temp_transaction_id: { $ne: null } } },
  { $sort:  { _id: 1 } },
  { $group: { _id: "$source_temp_transaction_id", n: { $sum: 1 }, ids: { $push: "$_id" },
              type: { $first: "$transaction_type" }, code: { $first: "$stock_code" },
              qty:  { $first: "$quantity" },        date: { $first: "$transaction_date" } } },
  { $match: { n: { $gt: 1 } } }
])

// profit_and_loss — group on BOTH fields; financial_year alone proves nothing on a multi-user database
db.profit_and_loss.aggregate([
  { $group: { _id: { email: "$email", fy: "$financial_year" }, n: { $sum: 1 }, ids: { $push: "$_id" } } },
  { $match: { n: { $gt: 1 } } }
])
```

Empty is the expected answer for both. Read-only, so a second run is safe.

**If you deploy without checking, the application still starts.** Index creation is caught and logged at ERROR,
naming the collection and the keys (§1.6), because failing a deploy over existing data is worse. The cost is that the
constraint can be silently absent.

**If either query returns something.** You are not obliged to fix it before deploying: the index is a new safety
constraint, not something the app needs to run, and TL-7's replay guard stops new duplicates either way. The options,
cheapest first:
1. **Deploy anyway.** The constraint is absent and logged; resolve the data later and restart to pick the index up.
2. **Resolve the data, then deploy**, as below.
3. **Never** "fix" it by dropping the unique flag: that retires the constraint silently.

*A duplicate `source_temp_transaction_id`.* **The first row is the legitimate one** (the query sorts by `_id`, whose
ObjectId embeds its creation time); each later one re-applied the trade, so the holding was applied twice and the P&L
double-counted. Deleting the `transactions` row alone fixes the index, not the money. For each extra id, find what it
touched first:

```js
const dup = "<the second or later _id from the aggregate>";
db.assets.find({ $or: [ { buy_transaction_ids: dup }, { sell_transaction_ids: dup } ] })
db.trade_outcomes.find({ source_sell_transaction_id: dup })
db.user_charges.find({ transaction_id: dup })
db.transactions.find({ _id: dup })    // note its email, stock_code, transaction_date, quantity
```

- **A duplicated BUY.** V2 writes one `assets` document per buy, so the duplicate usually has its own document, the
  one whose `buy_transaction_ids` holds only `dup`. Delete it, the `transactions` row and its `user_charges` row. V1
  merges into an existing lot, so there decrement the quantity and `broker_charges` by the duplicate's amounts.
- **A duplicated SELL.** Quantity was decremented twice, possibly across several lots. Restore the quantity on each
  `assets` document listing `dup` in `sell_transaction_ids`, remove `dup` from that array, then delete the
  `trade_outcomes`, `user_charges` and `transactions` rows.
- **Either.** The period's `profit_and_loss` totals include the duplicate. There is no recompute from `transactions`
  for `assets` (W-283a), so correct it by hand. `profit_and_loss` is rebuilt per user by §2.11 once the
  `transactions`, `user_charges` and `trade_outcomes` rows are right (W-283).
  `trade_outcomes` can be recomputed: after the duplicate `transactions` rows are gone, back up and delete **that
  user's** rows, then run §2.2's rebuild for them.

*A duplicate `{email, financial_year}`.* Two documents for one user and year, each holding part of the totals, the
result of a race the `version` field (§0.3) now prevents. There is no merge tool. Look at each
(`db.profit_and_loss.find({ email: "<stored email>", financial_year: "<fy>" })`, §0.7 from W-253; the `email` the duplicate check grouped by is already that form); if one is a stub written to once, keep the
fuller one and delete the stub. If both hold real figures, add them together by hand at the year, each month and each
half. **Never simply delete one:** the figures do not come back.

**Way back:** each correction above starts with a `mongoexport` of the documents it touches; `mongoimport --mode
upsert` restores them.

### 0.3 Trade ledger — `version` on `profit_and_loss` · owner, `mongosh` · **retired by W-337j3a (D-1020) · do not run**

> **Retired.** Mongo-only (Spring Data reading a null `@Version` as a new document); its heading recorded it done before the move. Kept below as the record of what it did.

**Why.** `ProfitAndLossEntity` carries `@Version private Long version`. On a document written without the field, Spring
Data reads the null version as a new document and inserts against an existing `_id`
(`E11000 duplicate key error … profit_and_loss index: _id_`), so every trade for an existing user in an existing year
fails. This was proven against a real MongoDB by `LegacyDocumentUpgradeIntegrationTest`, which W-337e (D-949) removed:
`profit_and_loss` moves to PostgreSQL, where `version` is `NOT NULL` from the table's first row, so the failure mode
this precheck guards against cannot occur there; this precheck still applies to any MongoDB database not yet moved
(W-337i decides this section's fate once the move runs).

1. **Check** (read-only): `db.profit_and_loss.countDocuments({ version: { $exists: false } })`. Must be `0`.
   **2026-10-27: 0 on prod and on staging** (run log), so there is nothing to do.
2. **Only if not 0:**
   `db.profit_and_loss.updateMany({ version: { $exists: false } }, { $set: { version: NumberLong(0) } })`, then step 1
   again.

**Second run:** safe; only documents without the field are matched. **Way back:** none needed; the field is what the
code requires.

---

### 0.4 W-229 (D-435) — no parked row left open · owner · **before the one deploy**

**Stop the deploy** while `docs/taxplanning-build/PARKED-ROWS.md` holds a row the status board still marks `K` with no waiver
D-row naming it. Read-only, from the repo root, safe to repeat, nothing to undo:

```bash
grep -oE 'W-[0-9]+[a-z0-9]* K( |$)' docs/taxplanning-build/STATE.md
```

Expected: empty, or every row printed named in a waiver D-row by the owner.


### 0.5 W-134 (D-447) — the tax keys, and the tax-planning collections cleared · owner · **retired by W-337j3a (D-1020) · do not run**

> **Retired.** A MongoDB clearing step for the first deploy carrying W-134; the two keys stay required at every start (§0.1.1). Kept below as the record of what it did.

**Why.** From W-134 the profile, reference, draft and computation collections hold personal data encrypted
(`k1:…`) and PANs and TANs as keyed digests (`pan_digest`, `tan_digest`). Rows written before it are plain text, which
the new build refuses to read, and `tax_employers`' unique index `tax_employer_tan_idx` changes its key from `tan` to
`tan_digest`, which a start cannot do over the old index of that name. No real PAN has been stored before the one deploy
(D-447 C7a), so the collections are cleared, not converted. A fresh-start step: no code to remove, so no Phase 15 row.

**Know this:**
- **A lost key is lost data.** Nothing can decrypt a stored field without `TAX_DATA_KEY`, and a new `TAX_DIGEST_KEY`
  lets a second profile take a PAN already held. Keep both in the secret store **and** its backup before the first start.
- **Collections are dropped, not emptied**, unlike §1A: dropping also removes the old indexes, and the new build creates
  every index again at its start (§1.x). Drop while the new build is not yet running. **W-235:** `tax_profiles`' unique
  `pan_digest` index is now **sparse** (a profile held for erasure has no digest); a start cannot change the options of the
  old index of that name, so this drop is also what lets it be created.
- **Only these eleven collections.** The tax-policy collections (`tax_slab_policies`, `tax_year_registry`, …) share the
  `tax_` prefix and are **not** touched here; §0.8 drops them (W-159).

1. **Keys** (once per environment, not per database): `openssl rand -base64 32`, twice — one for `TAX_DATA_KEY`, one for
   `TAX_DIGEST_KEY`. Each must decode to 32 bytes, or the app stops at start naming the property. Never reuse one key
   for both, and never reuse staging's in prod.
2. **Count before** (read-only; one query, every database; run it again after step 4):

   ```js
   db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
     .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n);
       return { database: n, counts: ["tax_profiles", "tax_family_members", "tax_employers", "tax_landlords", "tax_lenders",
         "tax_bank_accounts", "tax_year_workspaces", "tax_filing_drafts", "tax_draft_revisions", "tax_draft_computations",
         "tax_regime_election_history"].map(c => ({ c, n: d.getCollection(c).countDocuments({}),
         plainPan: c === "tax_profiles" ? d.tax_profiles.countDocuments({ pan: { $not: /^k1:/ } }) : null })) }; }) }])
   ```

   Expect: test data only, or nothing. **Stop** if any `tax_profiles` row holds a real person's PAN — say so first.
3. **Back up** any collection step 2 counts above 0:
   `mongoexport --uri "<uri>" --db <db> --collection <c> --out tax-<c>-before-w134.json`.
4. **Drop** (the same eleven, nothing else):

   ```js
   ["tax_profiles", "tax_family_members", "tax_employers", "tax_landlords", "tax_lenders", "tax_bank_accounts",
    "tax_year_workspaces", "tax_filing_drafts", "tax_draft_revisions", "tax_draft_computations",
    "tax_regime_election_history"].map(c => ({ c, dropped: db.getCollection(c).drop() }))
   ```

   A second run changes nothing. On MongoDB 8 `drop()` answers `true` even for a collection that is not there (seen on
   8.0.4, W-334c1's test), so the count after (step 5's §1.x lines, or step 2 again) is the proof, not the flag.
5. **After the deploy** (read-only): §1.x's `Ensured the indexes of` lines; then create one profile and check
   `db.tax_profiles.find({}, { pan: 1, pan_digest: 1 })` shows `pan` beginning `k1:` and a 64-character `pan_digest`.
   `tax_access_log` gains a row for each read of a profile, family, draft or computation: never cleared, it is the
   O4 audit record.

**Way back.** Deploy the previous build, then `mongoimport` each step-3 file into its collection. Rows written by the new
build are encrypted and the previous build cannot read them: drop those collections again first.

### 0.6 W-134a (D-448) — the legacy salary data, counted; the deploy held until W-159 · owner, `mongosh` · **retired by W-331 (D-882) · do not run**

> **Retired.** Replaced by §0.8 (W-159), which counted the same rows before dropping them. Kept below as the record of what it did.

**Why.** `salary_profiles` and `tax_computations` (the `/tax-planning` salary flow) hold salary structure, deduction
figures, a disability flag and pasted CTC text in plain text, which `DR 14` O1.1 says must be encrypted. W-134's
encryption does not cover them. W-159 removes the module and these collections instead (D-448 C1a), so the counts
here are what W-159's plan has to account for. **W-159 is built (D-726):** step 1's count is now §0.8 step 1, run there
with the drop; step 2's hold is met once the board shows `W-159 D`.

1. **Count** (read-only; one query, every database):

   ```js
   db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
     .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n);
       return { database: n,
         salary_profiles: d.salary_profiles.countDocuments({}),
         disabled: d.salary_profiles.countDocuments({ is_physically_disabled: true }),
         sourceText: d.salary_profiles.countDocuments({ source_text: { $nin: [null, ""] } }),
         accounts: d.salary_profiles.distinct("email").length,
         tax_computations: d.tax_computations.countDocuments({}) }; }) }])
   ```

   Record each database's line in the run log. A non-zero `sourceText` means pasted CTC letters are stored.
2. **Stop the deploy** while the status board shows W-159 as anything but `D`. Read-only, from the repo root:

   ```bash
   grep -oE 'W-159 [A-Z·—]' docs/taxplanning-build/STATE.md
   ```

   Expected: `W-159 D`. Anything else holds the deploy unless the owner records a waiver D-row naming W-134a.

Safe to repeat; it writes nothing, so there is nothing to undo.

### 0.7 W-253 (D-485) — an identifier's stored form, for a console query · owner · **whenever a step names `<stored …>`**

From W-253 every email, account holder and demat account id — and from W-256 the email of whoever wrote a row — is stored encrypted, one value always storing the same
form (`k1:…`). A console query that matches one by its plain value finds nothing, silently. Where a step says
`<stored email>` (or `<stored owner email>`, `<stored user email>`, `<stored caller email>`), compute it in a terminal with the database's own
keys, the `TAX_DATA_KEY` and `TAX_DIGEST_KEY` of the environment the database belongs to (§0.5):

```bash
TAX_DATA_KEY='<data key>' TAX_DIGEST_KEY='<digest key>' node -e 'const c = require("crypto"), e = process.argv[1];
const n = c.createHmac("sha256", Buffer.from(process.env.TAX_DIGEST_KEY, "base64")).update("identifier-nonce:" + e).digest().subarray(0, 12);
const g = c.createCipheriv("aes-256-gcm", Buffer.from(process.env.TAX_DATA_KEY, "base64"), n);
console.log("k1:" + Buffer.concat([n, g.update(e, "utf8"), g.final(), g.getAuthTag()]).toString("base64"))' '<email>'
```

It prints the `k1:` value to paste. The value is exact: the email's case and spaces count, as they do at sign-in. Check it
once per environment: for the test keys of `FieldCipherTest` (`AAECAw…Hh8=` / `HyAhIi…PT4=`) and `a@b.in` it prints
`k1:S2S9g4PmcIjT3j52RiPQbAfhd5GQLs4wzEgLXeegdyJPvg==`. It reads nothing and writes nothing. Do not paste the keys into a
shared terminal history: prefix the command with a space where the shell ignores those, or clear the history after.

### 0.8 W-159 (D-726) — the legacy salary and policy collections, backed up and dropped · owner, `mongosh` · **retired by W-331 (D-882) · do not run**

> **Retired.** Run on prod (W-334e1: seven dropped, `present` `[]`) and staging (W-334d2: seven dropped). Its step-2 exports are deleted by the owner (D-882, C1a). Kept below as the record of what it did.

**Why.** W-159 removed the older salary flow (`/tax-planning/user/**`) and the policy store it computed from. Nothing in
the build reads or writes these seven collections any more: `salary_profiles` and `tax_computations` (salary structure,
deduction figures, a disability flag and pasted CTC text, in plain text — §0.6), and `tax_slab_policies`,
`perquisite_policies`, `allowance_catalogue`, `allowance_limits`, `tax_year_registry` (filled by the retired §2.0).
Left in place they would be personal data held for no purpose, and the encryption sweep (§1.r) no longer reaches the
two salary collections — so this runs **before** the deploy and before §1.r.

1. **Count before** (read-only; one query, every database; it replaces §0.6 step 1):

   ```js
   db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
     .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n);
       const legacy = ["salary_profiles", "tax_computations", "tax_slab_policies", "perquisite_policies", "allowance_catalogue",
    "allowance_limits", "tax_year_registry"];
       return { database: n, present: d.getCollectionNames().filter(c => legacy.includes(c)),
         counts: legacy.map(c => ({ c, n: d.getCollection(c).countDocuments({}) })),
         disabled: d.salary_profiles.countDocuments({ is_physically_disabled: true }),
         sourceText: d.salary_profiles.countDocuments({ source_text: { $nin: [null, ""] } }),
         accounts: d.salary_profiles.distinct("email") }; }) }])
   ```

   Record each database's counts, `disabled` and `sourceText` in the run log, and the **number** of `accounts` — never
   the emails themselves. **Stop** and tell the owner first if `sourceText` is above 0 or `accounts` holds anyone but
   the owner and test accounts: that person's data is then exported for them before anything is dropped (W-159 C2).
2. **Back up** each collection step 1 counts above 0:
   `mongoexport --uri "<uri>" --db <db> --collection <c> --out legacy-<c>-before-w159.json`.
3. **Drop** (these seven, nothing else):

   ```js
   ["salary_profiles", "tax_computations", "tax_slab_policies", "perquisite_policies", "allowance_catalogue",
    "allowance_limits", "tax_year_registry"].map(c => ({ c, dropped: db.getCollection(c).drop() }))
   ```

   A second run changes nothing, as §0.5's drop does; `drop()` answers `true` on MongoDB 8 even for an absent collection,
   so step 4's count is the proof.
4. **Count after**: step 1's query again — `present` is `[]` and every count 0 on every database.

**Way back.** Deploy the previous build, then `mongoimport` each step-2 file into its collection. Delete the step-2 files
as the export rule above says, once step 4 is logged.

**Removed after use:** W-331 / WL-47, with §0.6, once this step is logged for every database.


### 0.9 W-337g2c (D-979) — the move: copy MongoDB into PostgreSQL · owner · **retired by W-337j1 (D-1017) · do not run**

> **Retired.** Run for every database: dev (W-337i2), the rehearsal (W-337i3), staging (W-337i4), prod (W-337i5); the copier and its `copier` profile were removed by W-337j1 (D-1017, WL-50). Kept below as the record of what it did.

**Why.** The PostgreSQL build reads only PostgreSQL. The copier (W-337g, D-953; every `m1.6.1` collection specified by D-978) starts
the app with the `copier` profile — no port opened (W-409, D-984: before it, this step failed at start) — reads every MongoDB collection and the documents' bytes, writes each row by key,
and proves each copy three ways (keys on both sides, each row against its source, each row loaded through the app's own entity),
logging one line per source. Ciphertext and digests are carried as stored, so the **target must run with the same `TAX_DATA_KEY`
and `TAX_DIGEST_KEY` the source was written with**; every time is written in UTC (D-954, D-958). MongoDB is only read.

**Order.** W-337h's target database exists with schema `wealthlens` and is empty (the build works only in that schema and
never makes it, W-413, D-989: a host without it stops at start with `schema "wealthlens" does not exist`) → this step → §1B (seed) → the app's normal start. **Never seed (§1B) a new
PostgreSQL database before this step:** the copier refuses a target holding a row its source lacks (D-953 C3a), and the copy brings
the catalogue across.

1. **Precheck, read-only — the source** (the owner's console, against the named database: dev `wealthlens-dev`, staging
   `it-staging`, prod `Cluster0`). Expect `counted` **42**, `loginsWithoutEmail` **0** and `unknownWithData` `[]`; keep `nonZero` for step 4 (a source it does not list holds 0). **Corrected 2026-11-27 (W-337i2 row 2):** the query first returned `counts` keyed by collection name, and `$documents` refuses a field name holding `.` (`tax_document_bytes.files`: error 16412 on dev); tried on a throwaway `mongo:8.0.4` with a login without `email`, the bytes and an unknown collection planted → `counted` 42, both in `nonZero`, `unknownWithData` `['stray']`, `loginsWithoutEmail` 1. **Stop** on
   either: a login without `email` fails the copy (D-964) and each is a decision (re-key or remove), never worked around here; an
   unknown collection holding data is refused by the copier.
   ```js
   db.aggregate([{ $documents: [(() => { const known = ["user_details", "refresh_tokens", "insurances", "charge_catalogue", "charge_schedules", "charge_instruments", "charge_accounts", "user_charges", "corporate_action", "lastly_performed_corporate_action", "assets", "transactions", "trade_outcomes", "profit_and_loss", "privacy_erasure_requests", "privacy_breach_intimations", "privacy_breach_notices", "privacy_grievances", "privacy_consents", "privacy_processing_log", "tax_profiles", "tax_family_members", "tax_employers", "tax_landlords", "tax_lenders", "tax_bank_accounts", "tax_profile_erasures", "tax_year_workspaces", "tax_filing_drafts", "tax_draft_revisions", "tax_regime_election_history", "tax_draft_computations", "tax_documents", "tax_parse_attempts", "tax_document_confirmations", "tax_gazette_cursor", "tax_gazette_leads", "tax_job_failures", "tax_job_leases", "tax_access_log"];
     const bytes = ["tax_document_bytes.files", "tax_document_bytes.chunks"];
     return { database: db.getName(),
       counted: known.concat(bytes).length,
       nonZero: known.concat(bytes).map(c => ({ collection: c, count: db.getCollection(c).countDocuments({}) })).filter(x => x.count > 0),
       unknownWithData: db.getCollectionNames().filter(c => !c.startsWith("system.") && !known.includes(c) && !bytes.includes(c)
         && db.getCollection(c).countDocuments({}, { limit: 1 }) > 0),
       loginsWithoutEmail: db.user_details.countDocuments({ email: { $exists: false } }) }; })()] }])
   ```
2. **Precheck, read-only — the target** (DataGrip, D-936 C3a, on the target database). Expect **no rows** before the first run
   (Flyway builds the schema when the copier starts):
   ```sql
   SELECT table_name FROM information_schema.tables
   WHERE table_schema = 'wealthlens' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history';
   ```
   Tables listed after a run that failed part-way are expected — step 3 again finishes it.
3. **Run** — one block per database, every value by prompt. **Backup:** none is taken: MongoDB is only read, and the target was
   empty at step 2, so its way back is step 6.

   Dev (no profile but `copier`; `application.yaml` names the dev cluster):
```zsh
# Dev: from the release worktree W-337i names; one jar, or stop
[[ -f wealthlens.jar ]] || { echo "no wealthlens.jar here: stop"; return 1 }
read -r 'MONGO_USER?Dev Mongo user: '; read -rs 'MONGO_PASSWORD?Dev Mongo password: '; echo
read -r 'APP_POSTGRES_DATASOURCE_URL?Dev target JDBC URL (jdbc:postgresql://…): '
read -r 'APP_POSTGRES_DATASOURCE_USERNAME?Dev target user: '; read -rs 'APP_POSTGRES_DATASOURCE_PASSWORD?Dev target password: '; echo
read -rs 'TAX_DATA_KEY?Dev TAX_DATA_KEY: '; echo; read -rs 'TAX_DIGEST_KEY?Dev TAX_DIGEST_KEY: '; echo
read -rs 'JWT_SECURITY_KEY?Dev JWT_SECURITY_KEY: '; echo; read -r 'PRIVACY_CONTACT?Dev PRIVACY_CONTACT: '
export MONGO_USER MONGO_PASSWORD APP_POSTGRES_DATASOURCE_URL APP_POSTGRES_DATASOURCE_USERNAME APP_POSTGRES_DATASOURCE_PASSWORD \
  TAX_DATA_KEY TAX_DIGEST_KEY JWT_SECURITY_KEY PRIVACY_CONTACT
java -jar wealthlens.jar --spring.profiles.active=copier --app.copier.written-zone=Asia/Kolkata \
  --app.tax.jobs.enabled=false 2>&1 | tee copier-dev.log
echo "exit: ${pipestatus[1]}"
unset MONGO_PASSWORD APP_POSTGRES_DATASOURCE_PASSWORD TAX_DATA_KEY TAX_DIGEST_KEY JWT_SECURITY_KEY
```
   Staging:
```zsh
# Staging: from the release worktree W-337i names; one jar, or stop
[[ -f wealthlens.jar ]] || { echo "no wealthlens.jar here: stop"; return 1 }
read -r 'MONGO_USER?Staging Mongo user: '; read -rs 'MONGO_PASSWORD?Staging Mongo password: '; echo
read -r 'APP_POSTGRES_DATASOURCE_URL?Staging target JDBC URL (jdbc:postgresql://…): '
read -r 'APP_POSTGRES_DATASOURCE_USERNAME?Staging target user: '; read -rs 'APP_POSTGRES_DATASOURCE_PASSWORD?Staging target password: '; echo
read -rs 'TAX_DATA_KEY?Staging TAX_DATA_KEY: '; echo; read -rs 'TAX_DIGEST_KEY?Staging TAX_DIGEST_KEY: '; echo
read -rs 'JWT_SECURITY_KEY?Staging JWT_SECURITY_KEY: '; echo; read -r 'PRIVACY_CONTACT?Staging PRIVACY_CONTACT: '
export MONGO_USER MONGO_PASSWORD APP_POSTGRES_DATASOURCE_URL APP_POSTGRES_DATASOURCE_USERNAME APP_POSTGRES_DATASOURCE_PASSWORD \
  TAX_DATA_KEY TAX_DIGEST_KEY JWT_SECURITY_KEY PRIVACY_CONTACT
java -jar wealthlens.jar --spring.profiles.active=staging,copier --app.copier.written-zone=Asia/Kolkata \
  --app.tax.jobs.enabled=false 2>&1 | tee copier-staging.log
echo "exit: ${pipestatus[1]}"
unset MONGO_PASSWORD APP_POSTGRES_DATASOURCE_PASSWORD TAX_DATA_KEY TAX_DIGEST_KEY JWT_SECURITY_KEY
```
   Prod:
```zsh
# Prod: from the release worktree W-337i names; one jar, or stop
[[ -f wealthlens.jar ]] || { echo "no wealthlens.jar here: stop"; return 1 }
read -r 'MONGO_USER?Prod Mongo user: '; read -rs 'MONGO_PASSWORD?Prod Mongo password: '; echo
read -r 'APP_POSTGRES_DATASOURCE_URL?Prod target JDBC URL (jdbc:postgresql://…): '
read -r 'APP_POSTGRES_DATASOURCE_USERNAME?Prod target user: '; read -rs 'APP_POSTGRES_DATASOURCE_PASSWORD?Prod target password: '; echo
read -rs 'TAX_DATA_KEY?Prod TAX_DATA_KEY: '; echo; read -rs 'TAX_DIGEST_KEY?Prod TAX_DIGEST_KEY: '; echo
read -rs 'JWT_SECURITY_KEY?Prod JWT_SECURITY_KEY: '; echo; read -r 'PRIVACY_CONTACT?Prod PRIVACY_CONTACT: '
export MONGO_USER MONGO_PASSWORD APP_POSTGRES_DATASOURCE_URL APP_POSTGRES_DATASOURCE_USERNAME APP_POSTGRES_DATASOURCE_PASSWORD \
  TAX_DATA_KEY TAX_DIGEST_KEY JWT_SECURITY_KEY PRIVACY_CONTACT
java -jar wealthlens.jar --spring.profiles.active=prod,copier --app.copier.written-zone=Asia/Kolkata \
  --app.tax.jobs.enabled=false 2>&1 | tee copier-prod.log
echo "exit: ${pipestatus[1]}"
unset MONGO_PASSWORD APP_POSTGRES_DATASOURCE_PASSWORD TAX_DATA_KEY TAX_DIGEST_KEY JWT_SECURITY_KEY
```
4. **Read the report.** Expect `exit: 0`, **41** lines ending `, clean` (40 collections and the documents' bytes) and one line
   `Every source copied and proven, times written in UTC from rows written in Asia/Kolkata` (shutdown lines may follow it):
   ```zsh
   grep -c ', clean$' copier-<database>.log; grep -c 'PROBLEMS' copier-<database>.log; grep -c 'Every source copied and proven' copier-<database>.log
   ```
   → `41`, `0`, `1`. **Stop** on any `PROBLEMS` line or a non-zero exit: keep the log, report it; nothing is worked
   around. Each line's source and target counts equal step 1's `counts` for that collection. **W-417 (D-999):** a line may
   name, before `, clean`, what the source held in a form `m1.6.1` read past — `read as m1.6.1 read them: N × <key> left out`
   (a retired key, `RetiredKeys`) or `N × date at midnight Asia/Kolkata` (a date written before #33, read at UTC as `m1.6.1`
   shows it). Each count is checked against that database's key survey before go; prod's copy (W-337i3) showed:
   `assets` — 220 × `total_value`, 1 date, and in 5 corporate-action entries `exDate`, `recordDate`, `stockCode`,
   `stockName`, `toStockCode`, `toStockName`, `actionPrice` (5 each), `createdAt`, `createdBy` (3 each);
   `transactions` — 1 date and the same corporate-action keys. A form not listed is still refused. **W-419 (D-1002):**
   `N × timezone_id absent, read as Asia/Kolkata` — a transaction stored without `timezone_id`, written as the default
   `m1.6.1`'s `TransactionEntity` read it (`FormerDefaults`); on prod's copy at least 379 (row 10 read the owner's transactions only); W-337i3's next row 7 gives the whole count. **W-421 (D-1008):** `transactions` — `N × assetRequest left out`: a temporary stored with `TemporaryTransactionEntity`'s
   unannotated `assetRequest` (5bc7cf10), which `m1.6.1` never reads (it reads `asset_request`, so the temporary holds none,
   as its redrive says). Staging's survey (W-337i4 row 5, 2026-11-28): `assets` 3 × `total_value`; `transactions` 581 ×
   `timezone_id` absent and 1 × `assetRequest`; no Java-named corporate-action key, no date at midnight Asia/Kolkata.
5. **After-check, read-only** (DataGrip): these six equal step 1's counts — `SELECT (SELECT count(*) FROM wealthlens.user_details),
   (SELECT count(*) FROM wealthlens.transactions), (SELECT count(*) FROM wealthlens.assets),
   (SELECT count(*) FROM wealthlens.trade_outcomes), (SELECT count(*) FROM wealthlens.profit_and_loss),
   (SELECT count(*) FROM wealthlens.tax_profiles);`. The run-log line records step 4's three
   answers and these six. Then delete `copier-<database>.log` (it names row keys, no personal data, but is not kept).
6. **Second run / way back.** A second run is safe: it writes nothing it wrote before and finishes a run that failed part-way
   (`MongoCopierIntegrationTest`). **Way back:** on the target, `DROP SCHEMA wealthlens CASCADE; CREATE SCHEMA wealthlens;` (DataGrip) — MongoDB
   is untouched — then step 2 again.

**Removed after use:** done — W-337j1 (D-1017) removed the copier (YouTrack WL-50, under WL-28), this step logged for every database.

### 0.10 W-406 (D-981, D-983, D-985) — the read comparison · owner · **retired by W-337j1 (D-1017) · do not run**

> **Retired.** Run on the rehearsal (W-337i3), staging (W-337i4) and prod (W-337i5), each `0 differences outside the list`; `tools/read_comparison.py` and its test were removed by W-337j1 (D-1017, WL-50). Kept below as the record of what it did.

**Why.** Before a moved database is signed off, every read the PostgreSQL build answers is compared with what `m1.6.1` answers on the
MongoDB it was copied from, as every user. `tools/read_comparison.py` reads every `GET` route in the `w-337`
build's OpenAPI spec, fills the path ids from list answers, walks every page at 200, and diffs the two answers field by
field. **Allowed, and nothing else** (D-981 C3a, D-983 C5a): a time `m1.6.1` showed in Asia/Kolkata comes back in UTC;
a time `m1.6.1` already held in UTC (`UTC_HELD_FIELDS`, each with its `m1.6.1` line) comes back equal; a field filled
at request time (`REQUEST_TIME_FIELDS`); a list the `w-337` build orders where `m1.6.1` left ties in MongoDB's order
or that neither build orders (`ORDERED_FOR_COMPARISON`, each with its line: the rate cards, W-411; asset allocation's
`HashMap`), put in that order on both sides first (W-419); the
holdings lists (`HOLDING_ROUTES`) compared holding by holding on what the view works out for the holding alone
(`HOLDING_FIELDS`: the key and the summed figures, equal within a relative 1e-9 as `double` sums in another lot order), not
the fields it copies from whichever lot it reads first (each lot's own fields are still compared on `/stocks/{stockCode}`,
W-419 R1-C1a, R3-C1a); an Excel answer compared by cell; a PDF by status and type. **Any other
difference stops the move.** The tool sends `GET` only. Both builds still record each read (processing log):
`m1.6.1` in the MongoDB source — on staging and prod the cluster kept, unused, as the way back (D-991 C5a) — and the
`w-337` build in the moved database.

**Before.** Both builds run on your machine at once, with the **same** `JWT_SECURITY_KEY`, `TAX_DATA_KEY` and
`TAX_DIGEST_KEY`: `m1.6.1` on port 8081 against the MongoDB source, and the `w-337` build on port 8082 against the
PostgreSQL database §0.9 filled.

1. **Precheck, read-only — the two lists** (on the PostgreSQL database §0.9 filled):
   ```sql
   SELECT count(*) AS logins FROM wealthlens.user_details;
   SELECT profile_id || ' ' || tax_year_start FROM wealthlens.tax_year_workspaces ORDER BY 1;
   ```
   Save the second query's rows as `~/wealthlens-runs/w406/workspaces.txt`, one per line — an empty file when it returns
   none, as on prod's copy (D-1001): the tool then reads no workspace route on either side. Write
   `~/wealthlens-runs/w406/users.txt` as **one line, your own account** (D-988 (1)): `your-email USER,SUPER_USER`, the
   roles as `user_details.roles` holds them. Your account is `SUPER_USER`, so the admin routes are read too. Other
   accounts are not compared; their rows are proven by §0.9's own checks. **Stop** unless the file is that one line.
2. **Run** (from the release worktree W-337i names; the tool asks for `JWT_SECURITY_KEY` at each step):
```zsh
# Rehearsal read comparison: both builds already running on 8081 (m1.6.1) and 8082 (w-337)
read -r 'REPO?Release worktree path (W-337i names it): '
W=$REPO/docs/taxplanning-build/tools/read_comparison.py
[[ -f $W ]] || { echo "no $W: stop"; return 1 }
cd ~/wealthlens-runs/w406 || return 1
[[ $(wc -l < users.txt) -eq 1 && -f workspaces.txt ]] || { echo "users.txt must be your one line, workspaces.txt written by step 1: stop"; return 1 }
python3 -I -B $W spec --base http://localhost:8082 --users users.txt --out spec.json
V=(--value taxYear=2025,2026 --value financialYear=2025-2026,2026-2027 --value broker=UPSTOX,FYERS,ZERODHA
   --value frequency=ANNUALLY,QUARTERLY --value billedThrough=$(date +%F) --workspaces workspaces.txt)
python3 -I -B $W fetch --base http://localhost:8081 --users users.txt --spec spec.json --out before "${V[@]}"
python3 -I -B $W fetch --base http://localhost:8082 --users users.txt --spec spec.json --out after "${V[@]}"
python3 -I -B $W compare before after > report.txt; echo "exit: $?"; tail -3 report.txt
```
   `broker` is every `BrokerName` (`BrokerName.java:3 – 7`). Give the `taxYear` and `financialYear` values your copy holds;
   a value with no rows answers the same on both sides.
3. **Read the report.** Expect `exit: 0` and `0 differences outside the list`. The `not reached` lines name the routes no
   list answer led to (both sides alike). The trial (D-985) left three: the admin contract note, a breach notice report and
   a document parse. **Stop** on any difference. Paste `report.txt` to the session; nothing is worked around or allowed
   without a decision (a new `UTC_HELD_FIELDS` or `REQUEST_TIME_FIELDS` entry carries the line that proves it).
4. **After.** `before/` and `after/` hold every user's answers, which is personal data. Once the report is logged,
   `rm -rf ~/wealthlens-runs/w406/before ~/wealthlens-runs/w406/after`, and delete `users.txt` once the move is signed off.
5. **Second run / way back.** A second run is safe: it only reads, and `fetch` writes a fresh folder (remove the old one
   first). There is nothing to undo on either database beyond the read records both builds keep.

**Removed after use:** done — W-337j1 (D-1017) removed the tool with the copier (YouTrack WL-50).

## 1. On every deploy — startup, automatic; confirm in the log

| # | Item / decision | What happens at startup | Check | If it fails |
|---|---|---|---|---|
| 1.1 | **W-079a** (D-210), **W-088s** (D-268) | `TaxEngineConfig` compiles every year in `rulepacks/shipped-years.json` (**2025-26, 2026-27**) and verifies each against its lock (`rulepacks/<year>.lock.json`) | The app starts. There is no database step: the rule packs ship in the JAR | **The boot stops** with a lock mismatch. Nothing reaches a database. Deploy the previous build; never edit a lock in place |
| 1.2 | **W-088u8a** (D-281, D-282) | **Nothing.** Tax policies are neither seeded nor read: §2.0 is retired and §0.8 drops their collections (W-159) | — | — |
| 1.3 | **W-088u8c** (D-285) | **Nothing.** The W-086a financial-year rewrite no longer runs at boot: see §2.3. No `CommandLineRunner` remains | — | — |
| 1.4 | **W-088u8b** (D-284) | **Nothing.** `trade_outcomes` is no longer rebuilt at boot when empty: see §2.2 | — | — |
| 1.5 | Charge engine (ADR-27) | `ChargeReadinessAuditor` counts the rate cards when the engine is pricing | Grep the log for `NO RATE CARDS`. Expected on a database never seeded (prod today) until §1B runs; `Charges engine ready: N rate card(s)` otherwise | Run §1A / §1B before anyone trades |
| 1.6 | TL-7; charge engine | `PortfolioIndexInitializer` and `ChargeIndexInitializer` create their collections' indexes | Grep the log for `Could not create index` (ERROR from `portfolio`, WARN from the charge engine) | The app has started without that constraint. A unique one almost certainly means duplicates: §0.2, then restart |
| 1.7 | Transactions | **Retired by W-337j2 (D-1018):** `TransactionSafetyAuditor` went with the MongoDB client | — | — |

---

### 1.y W-137 (D-449) — the background jobs · owner · **first deploy carrying them, then after the first runs**

Automatic: `JobIndexInitializer` creates the lease's TTL index. **Confirm** the log holds `Ensured the indexes of JobLease`. **From W-336 (D-783)** every shipped profile has `app.tax.jobs.enabled: false`: nothing is scheduled, and the owner runs each job through `POST /api/v1/admin/tax/jobs/{name}/run` (`SUPER_USER`) on §5.2's cadence. The crons — `auto-archive` at 03:00 each Sunday and `retention-purge` at 04:00 on the 1st, in the server's zone (`app.tax.jobs.*-cron`) — apply only where the switch is turned on. Each logs `Job <name> archived draft <id>` or `deleted draft <id> of tax year <year>` per draft — ids only. **W-138 (D-450):** `staleness-sweep` runs at every start where the switch is on, and on request where it is off (after every start of a new build, §5.2); it logs `Job staleness-sweep flagged N draft(s)`; after a deploy that changes a pack (a new `law` or `exec` version in a lock), expect N to be the ready and filed drafts computed under the old one, and 0 on the next run. **W-257 (D-487):** a row a job writes carries the job's name as its actor (`audit_metadata.updatedBy`, or `createdBy` on a new row: `auto-archive`, `staleness-sweep`, `retention-purge`), stored like any actor (§0.7 gives the form to query); rows written before W-257 keep `unknown`. Information only — no step.

**After the first run of each** (read-only; `PSQL`, §2.19):

```sql
SET search_path = wealthlens;
SELECT job, subject, reason FROM tax_job_failures ORDER BY failed_at;
SELECT count(*) AS leases_held FROM tax_job_leases;
```

Expected: no failures, and `leasesHeld` 0 between runs. A failure naming a tax year means a filed draft of a year no shipped pack covers: it is **kept**, and the row stays until the owner decides (ship that year's pack, or a D-row). Nothing to run twice; the collections are new.

**From W-239 (D-505)** the monthly `retention-purge` also deletes **landlords and lenders** once `app.tax.retention.third-party-after` (P2Y, all three profile yamls) has passed since the end of the last tax year the record can support (`DR 14` O5). From W-239a (D-506) that year is the later of the latest tax year of a stored draft **naming** the record (`/thirdParties/landlordIds`, `/thirdParties/lenderIds` in its inputs) and the year the record was last entered or changed; a record no draft names supports only the year of its last change. Drafts stored before W-239a name nothing, so on the first run their records follow that second rule. Each deletion logs `Job retention-purge deleted landlord <id>` or `… deleted lender <id>`; a record with no stored date is **kept** and logged as a refusal in `tax_job_failures` (subject `landlord <id>` / `lender <id>`). Nothing to seed or run. **Before the first run carrying W-239, and after it** (read-only; `PSQL`, §2.19; the drop is the records past their period):

```sql
SET search_path = wealthlens;
SELECT (SELECT count(*) FROM tax_landlords) AS landlords, (SELECT count(*) FROM tax_lenders) AS lenders,
  (SELECT count(*) FROM tax_landlords WHERE created_at IS NULL AND updated_at IS NULL)
  + (SELECT count(*) FROM tax_lenders WHERE created_at IS NULL AND updated_at IS NULL) AS undated;
```

Expected after: the counts fall by the number of `deleted landlord|lender` log lines, and `undated` equals the third-party refusals. A second run deletes nothing more. **Way back:** restore the two collections from the backup taken before the deploy — deleting them is the point, so only if a record was deleted in error.

**Way back:** stop running the job (nothing is scheduled while `app.tax.jobs.enabled` is false, the shipped value from W-336); stored drafts stay as they are. A draft the purge deleted is gone: restore it from the database backup.

### 1.z W-235 — erasure on request · owner · **first deploy carrying it, then after the first purge that erases**

A user's `POST /api/v1/me/tax/profiles/{profileId}/erasure` holds the profile at once (`erasure_requested_at`, its
`pan_digest` removed) and answers `202` with the erase date, one year later (`app.privacy.erasure-hold: P1Y` since W-237d, DPDP
Rules, 2025 r.8(3)). The monthly `retention-purge` (§1.y) then erases the profile and everything under it — drafts of every
status, revisions, computations, family, employers, landlords, lenders, bank accounts, workspaces, regime election history,
and the access records naming any of them — logging `Job retention-purge erased profile <id>`. Nothing to seed or correct.

**Before the first start:** `PRIVACY_CONTACT` set (§0.1.1) to the contact the owner publishes; the start is refused
without it. **After a purge that logged an erasure** (read-only; `PSQL -v id='<id>'`, §2.19):

```sql
SET search_path = wealthlens;
SELECT (SELECT count(*) FROM tax_family_members WHERE profile_id = :'id') + (SELECT count(*) FROM tax_employers WHERE profile_id = :'id')
  + (SELECT count(*) FROM tax_landlords WHERE profile_id = :'id') + (SELECT count(*) FROM tax_lenders WHERE profile_id = :'id')
  + (SELECT count(*) FROM tax_bank_accounts WHERE profile_id = :'id') + (SELECT count(*) FROM tax_year_workspaces WHERE profile_id = :'id')
  + (SELECT count(*) FROM tax_filing_drafts WHERE profile_id = :'id') + (SELECT count(*) FROM tax_regime_election_history WHERE profile_id = :'id')
  + (SELECT count(*) FROM tax_profiles WHERE id = :'id') AS left_rows,
  (SELECT count(*) FROM tax_profiles WHERE erasure_requested_at <= (now() AT TIME ZONE 'UTC') - interval '366 days') AS held_overdue;
```

Expected: `left` 0, `heldOverdue` 0 (a held profile past its year still present means the purge did not run or failed:
§1.y's failure check). A second purge changes nothing. **Way back:** none for an erased profile — erasure is the point;
before the hold ends, a held profile can be released only with the user's word (the PAN digest is rebuilt on the user's
next save): `PSQL -v id='<id>' <<< "UPDATE wealthlens.tax_profiles SET erasure_requested_at = NULL WHERE id = :'id';"`.
**Backups:** a host backup keeps erased data until it expires; when that is, and a restore, are in §1.s (W-237f).

### 1.w W-236 — the Gazette watch · owner, `SUPER_USER` · **when the owner chooses to start it; each database once**

**Why.** A daily job reads new e-Gazette copies and records a lead for each that may change a shipped pack (it names
the Central Board of Direct Taxes, or the Income-tax Act with the Department of Revenue). It **never writes a pack**: a
lead is read by the owner against the Gazette before anything changes. It calls out to `egazette.gov.in`, so it ships
**off** (`app.tax.gazette-watch.enabled: false` in all three profile yamls) and needs a starting point it never guesses.

**Automatic on deploy:** `JobIndexInitializer` creates the unique index on `tax_gazette_leads.url` — **confirm** the log
holds `Ensured the indexes of GazetteLead`.

1. **Check before** (read-only): no cursor yet, no leads.

   ```bash
   curl -sS "$BASE/api/v1/admin/tax/gazette-leads" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.page.totalElements'
   ```

   Expect `0`. In SQL (`PSQL`, §2.19): `SELECT count(*) FROM wealthlens.tax_gazette_cursor;` answers `0`.
2. **Choose the starting number.** On egazette.gov.in, open a recent Extraordinary Gazette; its address ends
   `WriteReadData/<year>/<number>.pdf`. The watch reads the numbers **after** the one given, so give the latest one already
   looked at (a lower number only means a longer first run).
3. **Seed the cursor** (recorded with the caller; a year before 2009 or after this year, or a number not positive, is a
   400):

   ```bash
   curl -sS -X PUT "$BASE/api/v1/admin/tax/gazette-cursor" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H "Content-Type: application/json" -d '{"year": <year>, "lastNumber": <number>}' | jq '.'
   ```

   Expect the cursor back with `updatedBy` your login.
4. **Turn it on:** set `app.tax.gazette-watch.enabled=true` for the environment (`APP_TAX_GAZETTEWATCH_ENABLED=true`)
   and restart. With the jobs off (every shipped profile from W-336) run it on request, on §5.2's cadence:
   `curl -s -X POST "$BASE/api/v1/admin/tax/jobs/gazette-watch/run" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'`
   → `{"job": "gazette-watch", "ran": true}`; where `app.tax.jobs.enabled` is true it also runs at 05:00 daily, server time
   (`app.tax.jobs.gazette-watch-cron`). Each run reads at most 400 numbers and stops after 20 missing in a row.
5. **After the first run** (read-only): the log holds `Job gazette-watch stopped at <year>/<number>`, one
   `Gazette lead <url> …` WARN line per lead, and no `Job gazette-watch refused`; then

   ```bash
   curl -sS "$BASE/api/v1/admin/tax/gazette-leads" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.content[] | {url, page, note, possiblyAffected}'
   ```

   **Unverified until this run:** whether `egazette.gov.in` serves the server's host daily. A failure in
   `tax_job_failures` naming an HTTP status, or no cursor movement, means it does not — turn the watch off and record it
   in the run log.

**Twice?** Safe: the cursor only moves forward past copies read, and a lead's URL is unique, so a rerun over the same
numbers adds nothing. Re-seeding sets the cursor back; the leads already recorded stay.

**Way back:** `app.tax.gazette-watch.enabled=false` and restart. The cursor and leads stay; they are read by nothing else.
Nothing to back up: the watch writes only its own two collections.

### 1.v W-237b — grievances answered within the published period · owner, `SUPER_USER` · **weekly, from the first deploy carrying it**

A user raises a grievance with `POST /api/v1/me/privacy/grievances` (`201`, a receipt naming `respondBy`: the receipt time plus
`app.privacy.grievance.response-days`, 30, published in `GET /api/v1/public/privacy/rights`). DPDP Rules, 2025 r.14(3) allow at most
ninety days (`longest-days-allowed: 90`; a start whose period exceeds it is refused). Nothing to seed; the
`privacy_grievances` collection is created by the first grievance. The app sends no mail: **this check is the measure
that keeps answers in time.**

**Weekly (read-only, safe to repeat, nothing to roll back):** as a `SUPER_USER`,

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/admin/privacy/grievances"
```

Expected: the unanswered grievances, the one due soonest first. Answer each before its `respondBy`:

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"text": "<the answer>"}' "$BASE/api/v1/admin/privacy/grievances/<id>/answer"
```

`200` with `answer.answeredAt`; the user reads it at `GET /api/v1/me/privacy/grievances`. A second answer to the same
grievance is refused (`400`), so a repeated call changes nothing. **Way back:** an answer given in error is corrected by
writing to the user from the privacy contact; the stored answer is not edited.

### 1.u W-237d — account-wide erasure · owner · **first deploy carrying it, then after the first run that erases**

A user's `POST /api/v1/me/privacy/erasure` holds the whole account at once (`privacy_erasure_requests`: `requested_at`)
and answers `202` with `erasedFrom`, one year later (`app.privacy.erasure-hold: P1Y`, DPDP Rules, 2025 r.8(3)), and the
privacy contact. From then the account cannot sign in, its tokens are refused, the email cannot be registered again, AMC
billing skips its charge accounts, and each of its tax profiles is held as §1.z holds one. The `account-erasure`
job (run on request on §5.2's cadence, W-336; 04:30 daily where the jobs are on; one of §1.y's jobs, same lease and failure records) erases every
module's data for each account past its year — portfolio, trades, P&L, charges, charge accounts, corporate-action
watermarks, insurance, tax profiles and everything under them, the older salary profiles and computations, the person's
access-log entries, grievances and breach intimations, their email in each breach notice's `affected` — the login last,
then sets `erased_at`. The request row stays a year after `erased_at` as the record that the erasure was done, then §1.t's
`log-retention` job erases it (W-237e C4a). The person's own processing-log rows go with the account; rows others wrote
about them stay until their year (§1.t). Nothing to seed or correct.

**Before the first start:** the hold moved from `app.tax.retention.erasure-hold` to `app.privacy.erasure-hold` (env var
`APP_PRIVACY_ERASUREHOLD`); the run log has no §1.z run, so no environment carries the old name. **After a run that
logged `Account erasure <id> done`** (read-only; `PSQL -v email='<stored email>'`, §2.19, with the erased email's stored form, §0.7 — W-253):

```sql
SET search_path = wealthlens;
SELECT (SELECT count(*) FROM assets WHERE email = :'email') + (SELECT count(*) FROM transactions WHERE email = :'email')
  + (SELECT count(*) FROM trade_outcomes WHERE email = :'email') + (SELECT count(*) FROM profit_and_loss WHERE email = :'email')
  + (SELECT count(*) FROM user_charges WHERE email = :'email') + (SELECT count(*) FROM charge_accounts WHERE email = :'email')
  + (SELECT count(*) FROM lastly_performed_corporate_action WHERE email = :'email') + (SELECT count(*) FROM insurances WHERE email = :'email')
  + (SELECT count(*) FROM tax_profiles WHERE email = :'email') + (SELECT count(*) FROM tax_draft_computations WHERE email = :'email')
  + (SELECT count(*) FROM privacy_grievances WHERE email = :'email') + (SELECT count(*) FROM privacy_breach_intimations WHERE email = :'email')
  + (SELECT count(*) FROM user_details WHERE email = :'email') + (SELECT count(*) FROM tax_access_log WHERE principal = :'email')
  + (SELECT count(*) FROM privacy_processing_log WHERE principal = :'email')
  + (SELECT count(*) FROM privacy_breach_notice_affected WHERE affected = :'email') AS left_rows,
  (SELECT count(*) FROM privacy_erasure_requests WHERE erased_at IS NULL AND requested_at <= (now() AT TIME ZONE 'UTC') - interval '367 days') AS held_overdue;
```

Expected: `left` 0 and `heldOverdue` 0 on every database (an open request past its year means the job did not run or
failed: §1.y's failure check; the next run completes it). A second run changes nothing: each erase deletes by email.
**Way back:** none for an erased account. Before the hold ends, an account is released only with the user's word:
`PSQL -v email='<stored email>' <<< "BEGIN; UPDATE wealthlens.tax_profiles SET erasure_requested_at = NULL WHERE email = :'email' AND erasure_requested_at IS NOT NULL; DELETE FROM wealthlens.privacy_erasure_requests WHERE email = :'email' AND erased_at IS NULL; COMMIT;"`.
**Backups:** a host backup keeps erased data until it expires; when that is, and a restore, are in §1.s (W-237f).

### 1.t W-237e — logs of processing kept a year, then erased · owner · **first deploy carrying it; monthly review; after the first run that erases**

Every request made by a named person — a signed-in user, or a login attempt for an email, failed ones included — writes
one row to `privacy_processing_log`: `principal`, `subject_email` (the path's `{email}`, else the principal), `method`,
`route` (the matched pattern, never the path's values, the query or the body), `status`, `remote_address`, `recorded_at`
(UTC). A request with no named person (no token, no login) is not recorded. The `log-retention` job
(run on request on §5.2's cadence, W-336; 04:45 daily where the jobs are on; one of §1.y's jobs, same lease and failure records) erases what is a year past
its last processing (`app.privacy.log-retention: P1Y`, env var `APP_PRIVACY_LOGRETENTION`; DPDP Rules, 2025 r.6(1)(e),
r.8(3)): processing-log rows and `tax_access_log` reads by their time, grievances by `answered_at` (an unanswered one is
kept), breach intimations by the latest of `posted_at`, `read_at`, `emailed_at`, erasure requests by `erased_at`. Breach
notices stay. **Console logs are not this log:** they live only as long as the container (each deploy's `docker rm -f wealthlens-container` replaces
it) and are diagnostics (W-237e C5a).

**First start (automatic):** `PrivacyIndexInitializer` creates the log's indexes. **Confirm** the log holds `Ensured the
indexes of ProcessingRecord`; a failure stops startup. The collection is new: nothing to check first, nothing to seed.

**Monthly review (read-only, safe to repeat, nothing to roll back; `PSQL`, §2.19)** — r.6(1)(c)'s "monitoring and review": failed logins
by email and address over the last 31 days, and any principal that reached more than one person's data (as stored, §0.7):

```sql
SET search_path = wealthlens;
SELECT principal, remote_address, count(*) AS attempts FROM privacy_processing_log
  WHERE route = '/api/v1/public/auth/login' AND status <> 200 AND recorded_at >= (now() AT TIME ZONE 'UTC') - interval '31 days'
  GROUP BY principal, remote_address ORDER BY attempts DESC;
SELECT principal, count(DISTINCT subject_email) AS subjects FROM privacy_processing_log
  WHERE recorded_at >= (now() AT TIME ZONE 'UTC') - interval '31 days' AND principal IS DISTINCT FROM subject_email GROUP BY principal;
```

Expected: few failed logins per email and address, and `reachedOthers` naming only the owner. Anything else is a
possible unauthorised access: §5.7.

**After the first run that erases** (a year after this deploy; read-only; `PSQL`, §2.19):

```sql
SET search_path = wealthlens;
SELECT (SELECT count(*) FROM privacy_processing_log WHERE recorded_at <= (now() AT TIME ZONE 'UTC') - interval '367 days')
  + (SELECT count(*) FROM tax_access_log WHERE read_at <= now() - interval '367 days')
  + (SELECT count(*) FROM privacy_grievances WHERE answered_at <= (now() AT TIME ZONE 'UTC') - interval '367 days')
  + (SELECT count(*) FROM privacy_erasure_requests WHERE erased_at <= (now() AT TIME ZONE 'UTC') - interval '367 days') AS overdue;
```

Expected: `overdue` 0 on every database (more means the job did not run or failed: §1.y's failure check; the next run
completes it). A second run changes nothing: each step deletes by date. **Way back:** stop running it (nothing is scheduled while
`app.tax.jobs.enabled` is false, W-336); erased rows are gone — restore from the database backup. **Backups:** a host backup
keeps erased rows until it expires; when that is, and a restore, are in §1.s (W-237f).

### 1.s W-237f — erased data in the backups, and a restore · owner · **before the duties commence (about May 2027); on every restore**

The Fiduciary must cause its Data Processor to erase what it was given (Act s.8(7)(b)). Backups are one of the expected
safeguards (Rules r.6(1)(d)), and r.8(3) Case 2 has the host keep the data at least a year. Nothing in the law limits how
long a snapshot may be kept: that is the owner's choice. The one bound is set by the app: **no snapshot may be kept
longer than `app.privacy.log-retention` (P1Y)**. A restore relies on the erasure records, and they are kept only a year
after each erasure (§1.t), so a snapshot older than that could hold a person no record still names (W-237f C1a).

**When erased data leaves the backups:** at `erased_at` plus the longest retention below. Data is erased a year after the
request (§1.z, §1.u), so a snapshot taken during the hold holds it until that snapshot expires.

**The owner's record on MongoDB (W-237f facts 1 – 4, C4a) — kept as the record of the Mongo era (D-1022 C5a)** — filled in by the owner (tier and backup 2026-11-19, W-334b row 2; the rest not yet given; dev is M0 free with no Cloud Backup too):

| | prod `Cluster0` | staging `it-staging` |
|---|---|---|
| Atlas tier; Cloud Backup on? | M0 free; no Cloud Backup (2026-11-19) | M0 free; no Cloud Backup (2026-11-19) |
| Snapshot schedule; retention of each kind; point-in-time window | none: no Cloud Backup | none: no Cloud Backup |
| Longest retention ≤ P1Y? (the check) | no snapshot is kept in Atlas | no snapshot is kept in Atlas |
| Snapshots or exports kept outside Atlas | none (owner, 2026-11-19) | none (owner, 2026-11-19) |
| EC2 volume snapshotted (EBS, AMI)? | *not given* | — |
| Processor terms accepted (Atlas, AWS): document and date (Act s.8(2), r.6(1)(f)) | *not given* | *not given* |

**After the move (W-337h, D-980)** — the PostgreSQL hosts, from the sources notes; the owner fills in what is not given:

| | prod (Neon, Free) | staging (Aiven, `wealthlens-staging`) | dev (Aiven, `wealthlens-dev`) |
|---|---|---|---|
| Snapshot schedule; retention; point-in-time window | 1 manual snapshot, no scheduled backups; history 6 hours (`sources/2026-11-23-neon-postgresql.md`) | *not given* — retention not shown on the old service's Overview (`sources/2026-11-22-aiven-postgresql.md`); this service not read | *not given* |
| Longest retention ≤ P1Y? (the check) | yes, 6 hours | *not given* | *not given* |
| Processor terms accepted: document and date | *not given* | *not given* | *not given* |

**The check (owner, read-only, safe to repeat):** read each PostgreSQL host's backup settings (Neon: history retention and
snapshots; Aiven: the service's backups), enter every retention figure in the table above, and confirm none exceeds one
year. A longer one is shortened on the host before the duties commence. If a host has no backup that can be set, that is
a re-plan of W-237f (its risk 2), not a row here. **2026-11-19 (MongoDB):** every cluster was M0 free with no Cloud Backup;
the re-plan was folded into W-337 (D-786 C5a).

**On every restore.** A restore puts the database back to the snapshot's moment, and every erasure since is lost from the
data. The records of those erasures are exported first and put back after, and then one call applies them again. It
reapplies only what was done at or after the snapshot's time. An erasure from before that time is already reflected in
the snapshot, and its email may since belong to a new account, which must not be touched.

1. **Before the restore, from the live database** (read-only; `PSQL`, §2.19):

   ```bash
   PSQL <<'EOF'
   COPY (SELECT * FROM wealthlens.privacy_erasure_requests) TO STDOUT WITH CSV HEADER \g /w/erasure-requests-before-restore.csv
   COPY (SELECT * FROM wealthlens.tax_profile_erasures) TO STDOUT WITH CSV HEADER \g /w/profile-erasures-before-restore.csv
   EOF
   ```

   Note the snapshot's time in UTC (`yyyy-MM-ddTHH:mm:ss`) as `S`.
2. **Restore** on the host (D-1022 C5a): prod (Neon) from its history, "up to 6 hours", or its one manual snapshot
   (`sources/2026-11-23-neon-postgresql.md`); staging and dev (Aiven) from a service backup, whose retention is *not given*
   (`sources/2026-11-22-aiven-postgresql.md`). The console steps are in neither note. Every PostgreSQL backup postdates §1.r
   (the move, W-337i), so no restore needs `m1.5`.
3. **Put the records back** (an upsert by `id`: a record the restored database already holds is replaced by its later state):

   ```bash
   PSQL <<'EOF'
   BEGIN;
   CREATE TEMP TABLE er (LIKE wealthlens.privacy_erasure_requests) ON COMMIT DROP;
   \copy er FROM '/w/erasure-requests-before-restore.csv' WITH CSV HEADER
   INSERT INTO wealthlens.privacy_erasure_requests SELECT * FROM er ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email,
     requested_at = EXCLUDED.requested_at, cause = EXCLUDED.cause, erased_at = EXCLUDED.erased_at, created_by = EXCLUDED.created_by,
     updated_by = EXCLUDED.updated_by, created_at = EXCLUDED.created_at, updated_at = EXCLUDED.updated_at;
   CREATE TEMP TABLE pe (LIKE wealthlens.tax_profile_erasures) ON COMMIT DROP;
   \copy pe FROM '/w/profile-erasures-before-restore.csv' WITH CSV HEADER
   INSERT INTO wealthlens.tax_profile_erasures SELECT * FROM pe ON CONFLICT (id) DO UPDATE SET email = EXCLUDED.email,
     profile_id = EXCLUDED.profile_id, requested_at = EXCLUDED.requested_at, erased_at = EXCLUDED.erased_at, created_by = EXCLUDED.created_by,
     updated_by = EXCLUDED.updated_by, created_at = EXCLUDED.created_at, updated_at = EXCLUDED.updated_at;
   COMMIT;
   EOF
   ```

   Expect `INSERT 0 n` twice, each `n` the rows its file holds.

4. **Apply them again** (`SUPER_USER`):

   ```bash
   curl -X POST "$BASE/api/v1/admin/privacy/erasure/reapply" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H "Content-Type: application/json" -d '{"snapshotTakenAt": "<S>"}'
   ```

   The answer names `accountsErased` (erased at or after `S`, erased again; `erased_at` unchanged), `accountsHeld`
   (asked at or after `S`, not yet erased, held again) and `profiles` (single profiles erased or held again). A time
   in the future, or none, is refused with 400.
5. **Check after (read-only)**, with `S` as `-v s='<S>'` (`PSQL`, §2.19):

   ```sql
   SET search_path = wealthlens;
   SELECT (SELECT count(*) FROM user_details WHERE email IN (SELECT email FROM privacy_erasure_requests WHERE erased_at >= :'s'::timestamp))
     + (SELECT count(*) FROM assets WHERE email IN (SELECT email FROM privacy_erasure_requests WHERE erased_at >= :'s'::timestamp))
     + (SELECT count(*) FROM tax_profiles WHERE email IN (SELECT email FROM privacy_erasure_requests WHERE erased_at >= :'s'::timestamp)) AS accounts_back,
     (SELECT count(*) FROM tax_profiles WHERE id IN (SELECT profile_id FROM tax_profile_erasures WHERE erased_at IS NOT NULL)) AS profiles_back;
   ```

   Expected: `accountsBack` 0 and `profilesBack` 0 on every database; then §1.u's check for each email in
   `accountsErased`. More than 0 means the call was not made or failed part-way. Make it again: each step deletes by email
   or by profile id, so a second call completes the work or changes nothing (tested).
6. **Delete the two export files** once the run is logged ("export deleted").

**Way back:** none is needed for the call, because it erases only what had been erased before the restore. If the
import was wrong, import the exports again and repeat step 4. **Profile-erasure records** (`tax_profile_erasures`:
`email`, `profile_id`, `requested_at`, `erased_at`) are new with W-237f. They are written by each single-profile request
from that deploy on, and erased a year after `erased_at` by §1.t's `log-retention` job. Nothing to seed.

### 1.r W-248 (D-479), W-253 (D-485), W-256 (D-486) — personal data, identifiers and actors stored before them, encrypted · owner, `SUPER_USER` · **retired by W-254 (D-893) · do not run**

> **Retired.** Run on prod (W-334e1 row 10b: `encryptedRows` = `plainRows`, conflicts 0; rows 11 – 13: nothing plain but `reports`, dropped by §2.15) and staging (W-334d2 rows 9b – 10: the same; plain actors only in collections no code reads). W-254 removed its route and code, so nothing in the app seals a plain row any more: step 3's query stays below as the record and as a read-only precheck (run before the switch to `m1.6.1`: W-366b / c row 2a, nothing plain on either). Kept below as the record of what it did.

From W-248 these fields are stored encrypted under `TAX_DATA_KEY` (§0.5): `privacy_grievances` `text` and `answer`;
`privacy_processing_log` `remote_address`; `insurances` `insurance_id`, `agent_name`, `agent_email`, `agent_contact`,
`agent_address`, `notes`, and in each `policyDetails` element `policy_id`, the four `agent_*`, `notes` and
`policy_description`. A row written before W-248 holds them in plain text, and the app refuses to read it (a `500`)
until this step has run. The key properties moved from `app.tax.*` to `app.personal-data.data-key` / `digest-key`; they
read the same `TAX_DATA_KEY` / `TAX_DIGEST_KEY`, so the environment does not change and every `k1:` row still reads.

From W-253 the identifiers are stored encrypted too, in a form one value always stores the same, so queries and unique
indexes still work: `email` in `transactions` (and in a temporary transaction's `asset_request`), `assets`,
`profit_and_loss`, `trade_outcomes`, `user_charges`, `charge_accounts`, `lastly_performed_corporate_action`, `insurances`,
`tax_profiles`, `tax_profile_erasures`, `tax_draft_computations`,
`privacy_consents`, `privacy_erasure_requests`, `privacy_breach_intimations`, `privacy_grievances`; `account_holder` in
`transactions` (and `asset_request.accountHolder`), `assets`, `trade_outcomes`, `user_charges`, `charge_accounts`;
`charge_accounts.demat_account_id`; `principal` in `privacy_processing_log` and `tax_access_log`, and
`privacy_processing_log.subject_email`; each email in `privacy_breach_notices.affected`. A login (`user_details`) held its
email as `_id`; it is re-keyed to a generated `_id` and an encrypted `email` (unique index `user_email_idx`, created at
startup by `LoginIndexInitializer`). **Until this step runs, no stored row is found by its user** — portfolios read empty,
and nobody can sign in — and a write in that gap starts a second row beside the plain one (step 5). Hence before users.

From W-256 the email of whoever wrote a row is stored the same way: `audit_metadata.createdBy` and `updatedBy` in every
collection that carries `audit_metadata`, `tax_draft_revisions.changed_by` and `tax_gazette_cursor.updated_by`, whatever
the value (`unknown`, a job's name and an email alike). **Until this step runs, a row whose actor is still plain cannot be
read at all** (a `500`) — reference data included: charge cards, tax policies, corporate actions. And the staleness sweep
that runs on every start reads each `READY` or `ACTED_ON` filing draft, so on a database holding one **the start itself
fails** (`StoredActorEncryptionIntegrationTest`). Hence the first start with the tax jobs off (step 1) and back on after
(step 7).

1. **A `SUPER_USER` token from the previous build.** Sign-in cannot find a login until step 4 re-keys it, but a token is
   read from its own claims, not from the database (`AuthFilter`; `AccessTokenConfig` from W-358b). Right before switching the build, sign in on the
   previous build as your `SUPER_USER` account and keep the token: it lasts 30 minutes. If it runs out first, deploy the
   previous build again, sign in, and switch back. **Start the new build with `APP_TAX_JOBS_ENABLED=false`**
   (`app.tax.jobs.enabled`; the shipped value from W-336, set anyway), so no job reads a plain row before step 4.
2. **Backup first** (the copy is personal data in plain text: delete it as the header says):
   `mongodump --uri "<uri>" --db <db> --out <dir>` — the whole database, since the step writes to nearly every collection.
3. **Check before** (read-only):

```js
db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
  .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n);
    const plain = f => ({ [f]: { $regex: "^(?!k1:)" } });
    const agent = ["agent_name", "agent_email", "agent_contact", "agent_address", "notes"];
    const ids = { transactions: ["email", "account_holder", "asset_request.email", "asset_request.accountHolder"],
      assets: ["email", "account_holder"], profit_and_loss: ["email"], trade_outcomes: ["email", "account_holder"],
      user_charges: ["email", "account_holder"], charge_accounts: ["email", "account_holder", "demat_account_id"],
      lastly_performed_corporate_action: ["email"],
      tax_profiles: ["email"], tax_profile_erasures: ["email"], tax_draft_computations: ["email"],
      tax_access_log: ["principal"], privacy_consents: ["email"], privacy_erasure_requests: ["email"],
      privacy_breach_intimations: ["email"], privacy_breach_notices: ["affected"] };
    return { database: n,
      grievances: d.privacy_grievances.countDocuments({ $or: ["text", "answer", "email"].map(plain) }),
      processingLog: d.privacy_processing_log.countDocuments({ $or: ["remote_address", "principal", "subject_email"].map(plain) }),
      insurances: d.insurances.countDocuments({ $or: ["insurance_id", "email", ...agent].map(plain).concat([{ policyDetails:
        { $elemMatch: { $or: ["policy_id", "policy_description", ...agent].map(plain) } } }]) }),
      identifiers: Object.entries(ids).map(([c, fs]) => ({ c, plain: d.getCollection(c).countDocuments({ $or: fs.map(plain) }) }))
        .filter(x => x.plain > 0),
      actors: d.getCollectionNames().map(c => ({ c, plain: d.getCollection(c).countDocuments({ $or:
        ["audit_metadata.createdBy", "audit_metadata.updatedBy", "changed_by", "updated_by"].map(plain) }) }))
        .filter(x => x.plain > 0),
      embeddedActors: [["trade_outcomes", "applied_corporate_actions"], ["assets", "corporate_actions"], ["transactions", "corporate_actions"]]
        .map(([c, l]) => ({ c, plain: d.getCollection(c).countDocuments({ [l]: { $elemMatch: { $or:
          ["audit_metadata.createdBy", "audit_metadata.updatedBy"].map(plain) } } }) })).filter(x => x.plain > 0),
      loginsToRekey: d.user_details.countDocuments({ email: { $exists: false } }) }; }) }])
```

4. **Dry run**, then the real run, with step 1's token. **Needs `m1.2` (W-341, D-794):** on `m1.1` the actors inside the corporate-action copies that `trade_outcomes`, `assets` and `transactions` embed stay plain (prod's copy: 11, 39 and 43 rows, W-334c4a row 4a), and every read of such a row fails with `A stored field is not encrypted under a known key`; their rows are counted in `plainRows` either way (their top-level email is plain too), so only step 6's `embeddedActors` shows the difference. **And `m1.1` (W-338, D-789):** on `m1` the real run matched no row whose
   `_id` is a string of 24 hex digits — every portfolio row on prod — and reported `encryptedRows` 0 without an error:
   `curl -X POST -H "Authorization: Bearer <token>" "<host>/privacy/stored-fields/encrypt"` (a dry run by default),
   then the same URL ending `?dryRun=false`. Expected: the dry run's `plainRows` per collection equal step 3's counts
   (`user_details` equals `loginsToRekey`) and `encryptedRows` 0; the real run's `encryptedRows` equal `plainRows` and
   `conflicts` is 0 everywhere.
5. **If a collection reports `conflicts` > 0:** a row was written in the gap (a trade, a P&L year, a consent) beside its
   plain twin, and the plain one was left as it was (the log names each `_id`: `left plain: its encrypted identifier
   duplicates a unique index entry`). Compare the two in the backup and the database; keep the fuller, merging figures by
   hand as §0.2 describes for a duplicate `{email, financial_year}`, delete the other, and run step 4 again.
6. **Check after:** step 3 again — `0` everywhere, `identifiers`, `actors` and `embeddedActors` empty, `loginsToRekey` 0. Then sign in on
   the new build: it works.
7. **Run the start-time sweep.** Where the jobs are off (every shipped profile from W-336), on request:
   `curl -s -X POST "$BASE/api/v1/admin/tax/jobs/staleness-sweep/run" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'`
   → `ran: true`, and the log shows `Job staleness-sweep flagged <n> draft(s)`. Where they are on, a restart without
   `APP_TAX_JOBS_ENABLED=false` runs it. A stack trace naming `A stored field is not encrypted` means step 6 was
   not `0` — repeat step 4.

A second run is safe: it finds nothing and changes nothing. Each update is conditioned on the plain value it read, so
a row that changed during a run is left for the next run, and a run that failed part-way is completed by the next; a
login whose new row was inserted before a failure is only deleted by the next run, not inserted twice.
**Way back:** restore the database from step 2 and redeploy the previous build, which reads plain text and the email as
the login's `_id`.
**One-time:** removed by W-254 (YouTrack WL-43) once this step's run is logged for every database.

### 1.q W-249 (D-480) — erasure once the purpose has lapsed · owner · **first deploy carrying it, then after the first run**

An account not signed in to for `app.privacy.purpose-lapse: P3Y` (the owner's ruling, D-480; not a figure from the law)
is erased by the `account-erasure` job (§1.u, same job and holders; run on §5.2's cadence), at once and with no hold, and recorded in
`privacy_erasure_requests` with `cause: "PURPOSE_LAPSED"`, `requested_at` the lapse moment, `erased_at` the run. Each
sign-in and each registration stores `user_details.last_signed_in_at`. An account without it (every account registered
before W-249) gets the first run's time: **nothing can lapse earlier than three years after the first run on a
database.** No command to run. The privacy notice (`GET /api/v1/public/privacy/rights`, `/privacy`) states the period.

**Before the first start:** an environment override `APP_PRIVACY_PURPOSELAPSE` must be longer than `erasure-hold` (P1Y),
or the app refuses to start naming `app.privacy.purpose-lapse`; none is expected. **Read-only (`PSQL`, §2.19), before the first run and
after it** (the day after the deploy):

```sql
SET search_path = wealthlens;
SELECT count(*) AS users, count(*) FILTER (WHERE last_signed_in_at IS NULL) AS not_counted,
  (SELECT count(*) FROM privacy_erasure_requests WHERE cause = 'PURPOSE_LAPSED') AS lapse_erasures,
  (SELECT count(*) FROM privacy_erasure_requests WHERE cause = 'PURPOSE_LAPSED' AND erased_at IS NULL) AS lapse_open FROM user_details;
```

Expected before: `notCounted` = `users`, `lapseErasures` 0. After the first run: `notCounted` 0 and `lapseErasures` 0
(the earliest lapse is three years away). From then, `lapseOpen` 0 the day after any run (an open one means the run
failed part-way: §1.y's failure check; the next run completes it). A second run changes nothing: the time is set only
where absent, and an account with a request open is left to it. **Way back:** before the first lapse, clearing
`last_signed_in_at` (`UPDATE wealthlens.user_details SET last_signed_in_at = NULL;`, run in W-337j3b's local proof only) loses nothing (the next run counts again from its own time). An erased account cannot be brought
back; after a restore, §1.s's reapply erases a lapse erasure again like a requested one.

### 1.p W-250 (D-481) — the notice before consent · owner · **first deploy carrying it, then a week after**

Registration (`POST /api/v1/public/auth/register`) now needs `privacyNoticeVersion`, the `version` that `GET /api/v1/public/privacy/rights` publishes.
It is a SHA-256 of the notice's wording, so any change to `app.privacy` text gives a new one. Without the current version,
registration is refused with 400 and nothing is stored. A successful registration writes a `privacy_consents` row
(`notice_version`, `given_at`). Each sign-in's response carries `privacy_notice` (`/privacy`) and `privacy_notice_version`.
The first sign-in of an account with no row (every account registered before W-250) writes one row with `noticed_at`
(Act s.5(2)(a)). Account erasure deletes the row. **There is no command to run.** **Any client must send the version
before it can register anyone.** W-131d1 (D-765 C5a) changed the `tax-planning` wording, so its deploy publishes a new version. The start refuses a missing `app.privacy.consent.*` value, and the yamls carry all of it.
`board-complaint` has no link to the Board yet: add it there when you have one. That changes the version, which is
expected.

**At the first start:** the unique `email` index is Flyway's (`privacy_consents_email_idx`). **Read-only (`PSQL`, §2.19),
before the deploy and a week after it:**

```sql
SET search_path = wealthlens;
SELECT (SELECT count(*) FROM user_details) AS users,
  (SELECT count(*) FROM privacy_consents WHERE given_at IS NOT NULL) AS consents,
  (SELECT count(*) FROM privacy_consents WHERE noticed_at IS NOT NULL) AS noticed,
  (SELECT count(*) FROM pg_indexes WHERE schemaname = 'wealthlens' AND tablename = 'privacy_consents'
     AND indexdef LIKE 'CREATE UNIQUE INDEX%(email)') AS "unique";
```

Expected before: `consents` 0, `noticed` 0, `unique` 0. A week after: `unique` 1, and `consents` + `noticed` no more than
`users`. Every account that signed in or registered since the deploy has a row. A second start or sign-in changes
nothing: the index already exists, and a row is never written twice for one account. **Way back:** redeploy the earlier
build. `privacy_consents` stays (a Flyway table: dropping it stops the next start's schema check, D-963; nothing else reads
it), and nothing else changes.

### 1.o W-255 (D-482) — accounts that chose their own role · owner · **retired by W-337j3a (D-1020) · do not run**

> **Retired.** Its read was a MongoDB query before W-255's deploy; every account since registers as `USER` and the copy carried the roles as stored (W-337i). Kept below as the record of what it did.

Until W-255, `POST /api/v1/public/auth/register` stored whatever `role` the body sent, so anyone could register as `SUPER_USER`. Now
every registration stores `USER`, and a body naming `role` is refused with 400. Roles already stored are not changed
by the deploy. **Read-only, before the deploy:**

```js
db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
  .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n);
    return { database: n,
      byRole: d.user_details.aggregate([{ $group: { _id: "$roles", accounts: { $sum: 1 } } }]).toArray(),
      aboveUser: d.user_details.find({ roles: { $ne: "USER" } }, { roles: 1 }).toArray() }; }) }])
```

Expected: `aboveUser` lists only your own account. **Roles are a comma list** (`UserDetailsImpl.java:24` splits on `,`;
old rows hold e.g. `USER,TEST_USER`, `ADMIN,USER,TEST_USER`), and only `SUPER_USER` gates anything (every `hasRole` /
`hasAnyRole` names it; `ADMIN` and `TEST_USER` gate nothing), so the check that matters is that only your account's list holds
`SUPER_USER`: `d.user_details.countDocuments({ roles: /(^|,)SUPER_USER(,|$)/ })` is 1 (W-334c1 row 9, D-788: prod's copy had
11 accounts above `USER`, one with `SUPER_USER`, left as they are). **Only if another account holds `SUPER_USER`**, in that database, after
`mongodump --db <database> --collection user_details` and keeping this output (the old roles):

```js
db.user_details.updateMany({ _id: { $in: ["<email>", "<email>"] }, roles: { $ne: "USER" } }, { $set: { roles: "USER" } })
```

Then run the read-only query again: `aboveUser` is your account alone. A second run of the update matches nothing. A
token issued before keeps its old role until it expires (30 minutes at most); the next sign-in carries `USER`. **Way
back:** set each account's old role from the kept output with `updateOne({ _id: "<email>" }, { $set: { roles: "<old>" } })`;
since §1.r re-keyed the logins (W-253), match `{ email: "<stored email>" }` (§0.7) instead of the `_id`.
The dump is the fallback; restoring it whole would also undo later sign-ins' `last_signed_in_at`.

### 1.x W-096 – W-098 (D-440) — the profile collections' indexes · owner · **first deploy carrying them**

Automatic at startup (`TaxProfileIndexInitializer`). **Confirm** the log holds `Ensured the indexes of` for `TaxpayerProfile`, `FamilyMember`, `Employer`, `Landlord`, `Lender`, `BankAccount`, `TaxYearWorkspace`, `FilingDraft`, `DraftRevision`, `RegimeElectionHistory` (`WorkspaceIndexInitializer`, W-099, D-441) and `DraftComputation` (`DraftComputationIndexInitializer`, W-104, D-442); a failure stops startup. The collections are new, so there is nothing to check first, and a second start changes nothing. Way back: drop the empty collections. **Encryption at rest is built (W-134, D-447; W-232 merged into it): §0.5 before the first deploy carrying it.**


### 1.n W-131a (D-761) — the document collections · owner · **first deploy carrying W-131a, then after the first `retention-purge`**

Automatic at startup (`DocumentIndexInitializer`). **Confirm** the log holds `Ensured the indexes of` for `StoredDocument`, `StoredParseAttempt` and `StoredConfirmation`; a failure stops startup. The collections (`tax_documents`, `tax_parse_attempts`, `tax_document_confirmations`) are new and nothing writes to them until W-131d's endpoint exists with `app.tax.documents.enabled` on (D-761 C3a), so there is nothing to check first, and a second start changes nothing.

**After the first `retention-purge`** (read-only): run §1.y's failures query. Expected: no failure whose subject begins `documents of tax year`. One means a stored document of a year no shipped pack covers; it is **kept**, and stays until the owner decides (ship that year's pack, or a D-row), as for drafts.

**From W-131b (D-763):** a document's bytes are kept in GridFS bucket `tax_document_bytes` (`tax_document_bytes.files`, `.chunks`), sealed with the personal-data key (`TAX_DATA_KEY`); GridFS creates its own indexes on first write. **Confirm** the three profile yamls hold `app.tax.documents.max-bytes: 20971520`, `max-pages: 50` and `orphan-after: P1D` (startup fails without them). After each `retention-purge`, a log line `deleted the bytes of N document(s) no longer stored` is expected only after a removal or upload was cut off part-way; it changes nothing on a second run.

**From W-131c (D-764):** the `parse-documents` job reads each stored document with its form's parser. **Confirm** the three profile yamls hold `app.tax.jobs.parse-documents-cron: "0 * * * * *"` and `app.tax.documents.parse-budget: PT30S` (startup fails without them where `app.tax.jobs.enabled` is true). It runs every minute where the jobs are on, and finds nothing until W-131d stores a document. With the jobs off (every shipped profile from W-336) it runs only on request, and an upload starts no run of its own (`ParseRequests` needs the schedule): run `parse-documents` after an upload, or decide when documents are turned on whether an upload starts its own run (W-336 C5a). Expected in the log after an upload: `recorded a SUCCESS|PARTIAL|FAILED attempt at document …`. A line `overran its budget` means a parse thread was abandoned (W-131f isolates it); a `refused document …` failure row means a document has no stored bytes. Nothing to run; a second run reads nothing already read by the same parser version.

**From W-131d1 (D-766):** the endpoints `POST /api/v1/me/tax/documents`, `GET …/documents/{id}/parse` and `POST …/documents/{id}/confirm` ship **off**: the three profile yamls hold `app.tax.documents.enabled: false`, and each answers 503 "Uploading documents is switched off". **Confirm** the yamls hold it, and `spring.servlet.multipart.max-file-size: 21MB` / `max-request-size: 22MB` in `application.yaml`. **Turning it on** is the owner's, only after W-131f (isolation) is done. W-131e's scan ships off in staging and prod by the owner's decision (W-131h, D-769), so §1.m is needed only once the scan is turned on: set `enabled: true` in the profile's yaml and redeploy; then `POST` a known Form No. 130 to a test profile, `GET …/parse` within a minute (where `app.tax.jobs.enabled` is true; where it is off, after running `parse-documents`, W-336), and check the access log holds the read (`tax_access_log`, subject `DOCUMENT`). Off again is the same yaml change. The notice's `tax-planning` part now names uploaded certificates, so the deploy carrying W-131d1 publishes a new notice version (§1.p): any client registering must send it.

**Way back:** drop the three collections and the bucket's two while they are empty. Once documents are stored, profile erasure and retention delete them with their attempts and confirmations (`DocumentRemoval`); a document deleted that should not have been is restored from the database backup.

### 1.k W-131j (D-771) — the app runs as `wealthlens`, not root · owner · **first deploy carrying W-131j**

The image now sets `USER wealthlens` (`Dockerfile`); `/app.jar` stays root-owned and read-only to it. The app writes nothing to disk but Tomcat's upload temp files in `/tmp`. At start, on Linux, it makes itself **non-dumpable** (`ProcessProtection`, `prctl(PR_SET_DUMPABLE, 0)`), so the PDF reader it starts as the same user cannot read its environment (the secrets), memory or maps (CI run 44340879615: denied with the call; read, secret visible, without it). If the call fails, **the app does not start**: `The process could not be made non-dumpable … the application does not start`. **Confirm** (read-only): `docker exec wealthlens-container id` shows `uid=…(wealthlens)`, and the start log holds `The process is non-dumpable` and `PDFs are read in a child process as this process's user`. The app's `docker run` (`docker run -d -p 8080:8080 --name wealthlens-container tperamasani/wealthlens`) does not change. **Cost:** no core dumps of the app; `jcmd` is not in the JRE image anyway, so a thread dump is `docker kill -s QUIT wealthlens-container` into the log. A second start changes nothing. **Way back:** redeploy the previous image.

### 1.l W-131f (D-770) — a PDF is read in a child process · owner · **first deploy carrying W-131f**

Automatic: from W-131f every PDF, at upload (the page count) and in the `parse-documents` job (the words), is opened by a child Java process the application starts from its own jar (`IsolatedPdfRead`, `PdfReadChild`). The child is killed at its budget and capped by `-Xmx` and `ulimit -t`. It starts with an **empty environment**. While the app ran as root it ran as **`nobody`** (BusyBox `su`; CI run 44338803065: `/proc/1/environ` denied); from W-131j (§1.k) the app runs as `wealthlens`, the reader as the same user, and the app is non-dumpable. Either way the reader holds none of the app's secrets and cannot read the app's environment or memory. Its Java sockets go to a dead proxy, and no host name resolves. **Docker's default profile refuses a network namespace** (CI run 44334020550: `unshare -rn` "Operation not permitted", even as root and with `util-linux`), so the operating system's network is **not** cut for the child: native code in it could still reach the network, with no secret to send. That gap is the owner's, recorded in the D-row. **Confirm** the start log holds `PDFs are read in a child process: heap 256 MiB, CPU 30 s` and the three profile yamls hold `app.tax.documents.isolation` (`heap-mebibytes: 256`, `cpu-seconds: 30`, `upload-read-budget: PT10S`, `max-reply-bytes: 16777216`). **Before** turning §1.n's switch on: `free -m` on the host leaves about 200 MB per read at once beyond the app (peak 130 – 177 MB per child in that run). Nothing to run against a database; a second start changes nothing. **After** an upload (switch on): the app log holds no `A PDF could not be read in isolation` line for a good Form No. 130. One names its reason (too slow, stopped, reply too large or not understood); the upload is then a 400, or the attempt `FAILED`. **Way back:** redeploy the previous image; nothing stored changes.

### 1.m W-131e (D-768), W-131h (D-769) — the malware scanner beside the app · owner · **each host, when the scan is turned on (off in staging and prod)**

**From W-131h (D-769):** staging and prod ship `app.tax.documents.scan.enabled: false` (owner: traffic and budget). Uploads are then stored unscanned, each `tax_documents` row records `scanned: false`, and every start logs `The malware scan is off: uploads are stored unscanned (W-131h)`. **Confirm** the staging and prod yamls hold `scan.enabled: false` and `application.yaml` holds `true`. Nothing below runs until the owner turns the scan on. **Turning it on:** steps 1 – 6 below, then `scan.enabled: true` in the profile's yaml and redeploy, then W-131i's rescan of what was stored unscanned. Read-only count before and after: `SELECT count(*) FROM wealthlens.tax_documents WHERE scanned IS NOT TRUE;` (`PSQL`, §2.19).

From W-131e every upload is scanned by ClamAV's `clamd` before a byte is stored or the PDF is opened (`MalwareScan`, `DocumentBytes.store`). It fails closed: with no scanner an upload answers 503 "The malware scan could not be completed; nothing was stored", and a file it finds malware in answers 400 "This file failed the malware scan and was not stored". While `app.tax.documents.enabled` is `false` nothing calls it, so a deploy carrying W-131e needs nothing until the switch is turned on. **Confirm** the three profile yamls hold `app.tax.documents.scan.host: ${CLAMD_HOST:localhost}`, `port: 3310` and `timeout: PT60S`.

1. **Before** (read-only): `free -g` on the host. The `available` column must leave **3 GiB** for ClamAV beyond the app (ClamAV Docker docs: "Minimum: 3 GiB", "Preferred: 4 GiB"; it "uses upwards of 2.4 GiB of RAM for a short period each day when loading new signature definitions"). Less → stop; the switch stays off.
2. Once per host: `docker network create wealthlens` and `docker volume create clam_db`.
3. `docker run -d --name clamd --restart unless-stopped --network wealthlens --mount source=clam_db,target=/var/lib/clamav clamav/clamav:1.5`. No port is published: only containers on `wealthlens` reach it, so a document's bytes never leave the host.
4. **Check** (read-only): `docker inspect --format '{{.State.Health.Status}}' clamd` answers `healthy` (the image's own health check pings `clamd`). The first start loads the signatures; on CI it answered within 17 s (run 44328189271).
5. Run the app on the same network, naming the scanner: add `--network wealthlens -e CLAMD_HOST=clamd` to its `docker run` (`docker run -d -p 8080:8080 --name wealthlens-container tperamasani/wealthlens`) and redeploy.
6. **After**, on staging with the switch on (§1.n): `SELECT count(*) FROM wealthlens.tax_documents;` (`PSQL`, §2.19), then upload the 68-byte EICAR test file from eicar.org: `curl -s -X POST "$BASE/api/v1/me/tax/documents" -H "Authorization: Bearer $TOKEN" -F file=@eicar.com -F profileId=<a test profile> -F taxYear=2026 -F documentType=FORM_130` → 400 as above, the app log holds `An upload failed the malware scan: Eicar-Test-Signature`, and the count is unchanged. Then `docker stop clamd`, upload a real Form No. 130 → 503 and the count unchanged; `docker start clamd`, wait for `healthy`, upload it again → 200 and the count one higher.

**A second run** is safe: step 2 reports the network and volume exist, and step 3 reports the name in use — `docker start clamd` instead. `freshclam` inside the container updates the signatures once a day; nothing to run. **Way back:** turn the switch off first (§1.n), then `docker rm -f clamd` and `docker volume rm clam_db`; the scanner stores nothing of ours.

### 1.i W-358c1 (D-855, D-856) — every route moves under `/api/v1/{public|me|admin}/{module}` · owner · **first deploy carrying W-358c1**

Nothing in a database changes and nothing runs. **For callers:** every URL moved (the old → new table is `plans/W-358c.md`); an old URL
answers 403 with a token and 401 without one. A user's own routes take the user from the token and name nobody
(`/api/v1/me/portfolio/transaction`, was `/portfolio/user/{email}/transaction`); the owner reads one user's holdings, charge gaps,
contract note, reconciliation and erasure receipt at `/api/v1/admin/{module}/users/{email}/…` (D-855 C3a). `/privacy` (the notice
page), `/error` and `/actuator/**` stay where they were. The privacy notice names the erasure and grievance routes, so its wording —
and so its version — changes with this deploy (§1.p): a client registering reads the new version from
`GET /api/v1/public/privacy/rights` first. The api-collection and every command in this runbook use the new paths. **Check after
(read-only):** `curl -s -o /dev/null -w '%{http_code}\n' "$BASE/api/v1/public/privacy/rights"` — expected `200`;
`curl -s -o /dev/null -w '%{http_code}\n' "$BASE/privacy/rights"` — expected `401` (outside every zone). **Way back:** deploy the
previous build; callers go back to the old paths.

### 1.j W-358b (D-854) — refresh tokens; callers sign in again · owner · **retired by W-337j3a (D-1020) · do not run**

> **Retired.** Its checks were MongoDB index reads at W-358b's deploy; on PostgreSQL the indexes are Flyway DDL. Kept below as the record of what it did.

Automatic at startup (`LoginIndexInitializer`). **Before** (read-only, `mongosh` on the database): `db.refresh_tokens.countDocuments({})` — expected `0`
(the collection is new). **Confirm** the log holds `Ensured the indexes of UserDetail` and `Ensured the indexes of RefreshToken`; a failure stops
startup. **After** (read-only): `db.refresh_tokens.getIndexes().map(i => i.name)` — expected `_id_`, `refresh_token_hash_idx`,
`refresh_token_family_idx`, `refresh_token_email_idx`, `refresh_token_expiry_idx` (the last with `expireAfterSeconds: 0`: Mongo removes a row
once its 14 days pass). A second start changes nothing. Nothing to run by hand.

**For callers:** an access token lasts 15 minutes (was 30); the sign-in also returns `refresh_token`, renewed by `POST /api/v1/public/auth/token/refresh`
and ended by `POST /api/v1/public/auth/logout` (both `{"refresh_token": "…"}`). A token signed by the previous build either keeps working until it expires
(at most 30 minutes) or is refused; a refused caller signs in again. A token on another user's `/…/user/{email}/…` path now answers **403**
(was 401). The api-collection's `Auth/Login.yml` keeps the refresh token in `REFRESH_TOKEN`.

**Way back:** deploy the previous build; it never reads `refresh_tokens`. Its rows are hashes that sign nobody in, so no backup is needed
before dropping it: `db.refresh_tokens.drop()`.

### 1.h W-358d3 (D-885) — passwords rehashed as Argon2id at sign-in · owner · **first deploy carrying W-358d3, then a week after**

Nothing to run. Each successful sign-in rewrites that account's stored hash in `user_details.password` as `{argon2}$argon2id$…` when it
was older (every hash stored before W-358d3 is unlabelled BCrypt, `$2a$10$…`, or `$2y$` from `htpasswd`); registration and a password
change store Argon2id from the start. An account nobody signs in to keeps its BCrypt hash, which still signs in. A plain-text
(`{noop}`) or digest (`{MD5}`, `{SHA-1}` …) value never signs anyone in.

**Read-only, before the deploy and a week after** (`PSQL`, §2.19, one query):

```sql
SET search_path = wealthlens;
SELECT count(*) AS users, count(*) FILTER (WHERE password LIKE '{argon2}%') AS upgraded,
  count(*) FILTER (WHERE password NOT LIKE '{argon2}%' OR password IS NULL) AS bcrypt FROM user_details;
```

Expected before: `upgraded` 0. A week after: `upgraded` the accounts that signed in or registered since, `upgraded` + `bcrypt` =
`users`. A second run changes nothing (it only reads).

**Way back (D-885 C3a: no copy of the hashes is kept).** `m1.5` and earlier cannot read a labelled hash, so after going back an
upgraded account is refused until its password is reset: `htpasswd -bnBC 10 "" '<password>' | tr -d ':\n'` gives a BCrypt hash;
`PSQL -v email='<stored email>' -v hash='<hash>' <<< "UPDATE wealthlens.user_details SET password = :'hash' WHERE email = :'email';"` (`UPDATE 1`) with the stored form of the email (§0.7). The
query above (`upgraded`) counts the accounts this concerns.

## 1A. W-176 (D-379) — clear the charge engine's data · owner, SQL · **every fresh start**

**Why.** The owner's fresh start (2026-10-27): the charge engine's data is cleared where it exists and seeded new in
§1B, so every database starts from the cards in the repository. The configuration seeded from the repository
(`charge_catalogue`, `charge_schedules`, `charge_instruments`) is cleared on **every** database; prod has none today.
The engine's per-user data is cleared on **staging only**: `user_charges` (each V2 trade's contract note),
`charge_accounts` (demat accounts registered for AMC) and the P&L's charge side (`yearly_charge_summary` on both
sides of a `profit_and_loss` document). The P&L's gains stay.

**Know this:**
- **Staging loses its 328 recorded contract notes.** The cost basis already written on `assets` stays as recorded. A
  V2 sale recorded before the deploy keeps its brokerage in the P&L's gains split (§2.8). The owner called this
  staging data (W-176 plan, risk 1).
- **Rows are deleted, tables are not dropped.** The tables and their unique indexes (`user_charge_txn_idx` on
  `user_charges (email, transaction_id)`) are Flyway's; the app checks the schema at start (D-963), so a dropped table
  stops the next start.
- **No trade between 1A and 1B.** With the flags on and no cards, a V2 buy is charged ₹0 and that ₹0 becomes its
  cost basis.
- 1A is a fresh-start step, not a one-time migration, so it has no Phase 15 row and no code to remove.

1. **Count before** (read-only; run it again after step 3; `PSQL`, §2.19):

   ```sql
   SELECT (SELECT count(*) FROM wealthlens.charge_catalogue) AS charge_catalogue,
     (SELECT count(*) FROM wealthlens.charge_schedules) AS charge_schedules,
     (SELECT count(*) FROM wealthlens.charge_instruments) AS charge_instruments,
     (SELECT count(*) FROM wealthlens.user_charges) AS user_charges,
     (SELECT count(*) FROM wealthlens.charge_accounts) AS charge_accounts,
     (SELECT count(*) FROM wealthlens.profit_and_loss WHERE jsonb_typeof(realised_profits_json::jsonb -> 'yearlyChargeSummary') = 'object'
        OR jsonb_typeof(out_sourced_realised_profits_json::jsonb -> 'yearlyChargeSummary') = 'object') AS pnl_charge_side;
   ```

   Read 2026-11-28 (W-337j3b, D-1022): dev 13 / 40 / 2 / 0 / 0 / 0, staging 13 / 39 / 2 / 0 / 0 / 0 (on MongoDB, 2026-10-27:
   prod 0 / 0 / 0 / 0 / 0 / 0, staging 12 / 11 / 2 / 328 / 1 / 2). Take it again now; report a difference before clearing.
2. **Back up** (staging; on prod only if step 1 is not all 0). Each child table goes with its parent:

   ```bash
   PSQL <<'EOF'
   COPY (SELECT * FROM wealthlens.charge_catalogue) TO STDOUT WITH CSV HEADER \g /w/charge-engine-charge_catalogue-backup.csv
   COPY (SELECT * FROM wealthlens.charge_schedules) TO STDOUT WITH CSV HEADER \g /w/charge-engine-charge_schedules-backup.csv
   COPY (SELECT * FROM wealthlens.charge_instruments) TO STDOUT WITH CSV HEADER \g /w/charge-engine-charge_instruments-backup.csv
   COPY (SELECT * FROM wealthlens.user_charges) TO STDOUT WITH CSV HEADER \g /w/charge-engine-user_charges-backup.csv
   COPY (SELECT * FROM wealthlens.user_charge_lines) TO STDOUT WITH CSV HEADER \g /w/charge-engine-user_charge_lines-backup.csv
   COPY (SELECT * FROM wealthlens.user_charge_amounts) TO STDOUT WITH CSV HEADER \g /w/charge-engine-user_charge_amounts-backup.csv
   COPY (SELECT * FROM wealthlens.charge_accounts) TO STDOUT WITH CSV HEADER \g /w/charge-engine-charge_accounts-backup.csv
   COPY (SELECT * FROM wealthlens.charge_account_billing_events) TO STDOUT WITH CSV HEADER \g /w/charge-engine-charge_account_billing_events-backup.csv
   COPY (SELECT * FROM wealthlens.profit_and_loss WHERE jsonb_typeof(realised_profits_json::jsonb -> 'yearlyChargeSummary') = 'object'
        OR jsonb_typeof(out_sourced_realised_profits_json::jsonb -> 'yearlyChargeSummary') = 'object') TO STDOUT WITH CSV HEADER \g /w/charge-engine-pnl-charge-side-backup.csv
   EOF
   ```

3. **Clear.** The configuration is cleared on every database; the per-user part refuses unless the host passed is
   staging's (dev and staging are both `defaultdb`, so the database name cannot tell them apart; D-1022 C4a):

   ```bash
   PSQL -v host='<the host PSQL connects to>' <<'EOF'
   BEGIN;
   DELETE FROM wealthlens.charge_catalogue; DELETE FROM wealthlens.charge_schedules; DELETE FROM wealthlens.charge_instruments;
   COMMIT;
   SELECT :'host' = 'wealthlens-staging-aaaaaw.e.aivencloud.com' AS is_staging \gset
   \if :is_staging
   BEGIN;
   DELETE FROM wealthlens.user_charge_lines; DELETE FROM wealthlens.user_charge_amounts; DELETE FROM wealthlens.user_charges;
   DELETE FROM wealthlens.charge_account_billing_events; DELETE FROM wealthlens.charge_accounts;
   UPDATE wealthlens.profit_and_loss SET
     realised_profits_json = CASE WHEN jsonb_typeof(realised_profits_json::jsonb -> 'yearlyChargeSummary') = 'object'
       THEN (realised_profits_json::jsonb - 'yearlyChargeSummary')::text ELSE realised_profits_json END,
     out_sourced_realised_profits_json = CASE WHEN jsonb_typeof(out_sourced_realised_profits_json::jsonb -> 'yearlyChargeSummary') = 'object'
       THEN (out_sourced_realised_profits_json::jsonb - 'yearlyChargeSummary')::text ELSE out_sourced_realised_profits_json END
     WHERE jsonb_typeof(realised_profits_json::jsonb -> 'yearlyChargeSummary') = 'object'
        OR jsonb_typeof(out_sourced_realised_profits_json::jsonb -> 'yearlyChargeSummary') = 'object';
   COMMIT;
   \else
   \echo refused: the per-user clear runs only on staging
   \endif
   EOF
   ```

   Expect each `DELETE` to name step 1's count (on prod, the configuration's are 0, and the per-user part prints `refused`).
4. **Verify:** step 1 again reads all 0 (on prod, the per-user counts were 0 before and stay so), and
   `SELECT count(*) FROM pg_indexes WHERE schemaname = 'wealthlens' AND indexname = 'user_charge_txn_idx';` answers 1.

**Second run:** safe, including after a run that failed part-way: each block is one transaction, and deleting from an empty
table or updating no row changes nothing (all counts 0). **Way back:** put each backup back, parents before children, and
the P&L rows by delete and copy (the form proven by W-337j3b, D-1022):

```bash
PSQL <<'EOF'
BEGIN;
\copy wealthlens.charge_catalogue FROM '/w/charge-engine-charge_catalogue-backup.csv' WITH CSV HEADER
\copy wealthlens.charge_schedules FROM '/w/charge-engine-charge_schedules-backup.csv' WITH CSV HEADER
\copy wealthlens.charge_instruments FROM '/w/charge-engine-charge_instruments-backup.csv' WITH CSV HEADER
\copy wealthlens.user_charges FROM '/w/charge-engine-user_charges-backup.csv' WITH CSV HEADER
\copy wealthlens.user_charge_lines FROM '/w/charge-engine-user_charge_lines-backup.csv' WITH CSV HEADER
\copy wealthlens.user_charge_amounts FROM '/w/charge-engine-user_charge_amounts-backup.csv' WITH CSV HEADER
\copy wealthlens.charge_accounts FROM '/w/charge-engine-charge_accounts-backup.csv' WITH CSV HEADER
\copy wealthlens.charge_account_billing_events FROM '/w/charge-engine-charge_account_billing_events-backup.csv' WITH CSV HEADER
CREATE TEMP TABLE pnl_back (LIKE wealthlens.profit_and_loss) ON COMMIT DROP;
\copy pnl_back FROM '/w/charge-engine-pnl-charge-side-backup.csv' WITH CSV HEADER
DELETE FROM wealthlens.profit_and_loss WHERE id IN (SELECT id FROM pnl_back);
INSERT INTO wealthlens.profit_and_loss SELECT * FROM pnl_back;
COMMIT;
EOF
```

Then `POST /api/v1/admin/charges/seed`, whose run evicts the rate-card caches, so the restored cards are read (§5.5,
"written directly"). Delete the backup files once the run is logged.

## 1B. Charge engine — seed the rate cards · `SUPER_USER` · **every database · after §1A, before the first trade; on a PostgreSQL database, after §0.9**

**Why.** Seeding is not automatic (ADR-27). A database with no cards prices every V2 trade `NO_SCHEDULE` at ₹0, and
with `authoritative` on that ₹0 becomes the cost basis; nothing throws.

1. **Seed:**

   ```bash
   curl -sS -X POST "$BASE/api/v1/admin/charges/seed" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
   ```

   Expect 13 catalogue codes, 39 rate cards and 2 scheme profiles written. That is the 13 entries of
   `data/charges/charge-catalogue.json`, its 39 card files (31 broker cards — W-177 added Upstox and Fyers intraday,
   mutual fund and maintenance, D-392; W-178 equity futures and options for all three, D-393 — and 8 statutory
   cards: the 4 below and W-178's `STATUTORY_EQ_FUTURES_*` / `STATUTORY_EQ_OPTIONS_*`, which name no
   broker: `STATUTORY_EQ_DELIVERY_2025_04`, `STATUTORY_EQ_INTRADAY_2025_04`, `STATUTORY_MF_2025_04`,
   `STATUTORY_ACCOUNT_2025_04`; W-163b, D-382) and `instruments/`'s 2. A second run writes nothing and says so.
2. **Confirm it took** (read-only):

   ```bash
   # Must be empty: the shipped files and the database agree
   curl -sS "$BASE/api/v1/admin/charges/charge-schedules/drift" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
   # Every card with a verifiedOn older than 90 days, or none; rows mean rate verification is due (§5.2), not a failure
   curl -sS "$BASE/api/v1/me/charges/charge-schedules/unverified" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
   ```

   Expect `unverified` empty. Every card carries a `verifiedOn`: a broker card against its charges page, and a
   statutory card against the law it cites (W-163c, D-384; `sources/2026-10-28-statutory-charge-rates.md`). A card
   listed here is due for verification.

   ```bash
   ```

3. **One simulate** against a known figure, Zerodha equity delivery, ₹1,00,000 sell:

   ```bash
   curl -sS -X POST "$BASE/api/v1/me/charges/simulate" -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' -d '{
       "stockCode":"INFY","brokerName":"ZERODHA","assetType":"EQUITY","segment":"DELIVERY","event":"SELL",
       "exchange":"NSE","transactionDate":"2025-06-10","quantity":100,"price":1000
     }' | jq '{scheduleCode, resolution, totalCharges, amountByCode}'
   ```

   `stockCode` is required on a sale since W-365 (a 400 without it; it picks the scheme's own charges, none on an equity
   delivery sale). Expect `scheduleCode` `ZERODHA_EQ_DELIVERY_2025_04`, `resolution` `RESOLVED`, and, on turnover ₹1,00,000: BROKERAGE 0,
   STT 100 (0.1 %, to the rupee), EXCHANGE_TXN 2.97 (0.00297 %), SEBI_FEE 0.10, IPFT 0.10 (0.0001 % each), DP 13.50,
   GST 3.00 (18 % of 0 + 2.97 + 0.10 + 0.10 + 13.50 = 16.67), **total ₹119.67**; no STAMP_DUTY on a sale. Worked by
   hand from `zerodha-equity-delivery-2025-04-01.json` and `statutory-equity-delivery-2025-04-01.json`; the same
   method is shown in `docs/modules/brokercharges.md` §5. A later date prices on a later card, which is correct.

**Second run:** safe; the seed skips what is on file. **Way back:** §1A clears what this wrote.


---

## 2. Once per database, by hand — in this order

> **There is one deploy, after Phases 0 – 14 are complete** (owner, D-287); nothing is deployed before then. Every
> step in this section runs at that deploy, in order, on each database. Take each step's "count before" again at that
> time: the figures recorded here (2026-10-20 and 2026-10-27, run log) are from the databases as they were. They run
> after §1A and §1B.

### 2.0 W-088u8a (D-281, D-282) — seed the TY 2025-26 tax policies · **retired by W-159 (D-726) · do not run**

> **Retired.** W-159 removed the seed endpoint, the policies and the code that read them; W-148 (WL-30), which was to
> remove this step, is merged into W-159. §0.8 backs up and drops the collections this step filled. Kept below as the
> record of what those collections held.

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
   // each document written by this step carries audit_metadata.createdBy = <stored caller email> (§0.7, W-256)
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

**Way back:** delete the documents this step wrote, by `audit_metadata.createdBy` (`<stored caller email>`, §0.7) and `createdAt`.

**Removed after use:** W-148 / WL-30, once this step is logged for every database.

### 2.1 W-088u7 (D-277) — correct the rebuild's double-counted buy charges · **retired by W-147 (D-874) · do not run**

> **Retired.** Run on prod (W-334e2: count only, D-798) and staging (W-334d3a: count only); W-147 removed its route and code. Kept below as the record of what it did.

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
   curl -sS -X POST "$BASE/api/v1/admin/portfolio/trade-outcomes/rebuild-charges/correct" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
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

**Not run on prod (W-342, D-798):** the rehearsal (W-334c4a, W-342) found it refuses both users, because the replay orders same-day sales by date only, so it pairs no row; the over-count it was to remove is **₹15.99** on nine lots (of ₹68.99 carried), accepted and recorded in §3.25. Step 1's count is still taken on prod and logged, and the step recorded "not run". **On the next build that carries W-343 (D-800)** — same-day sales replayed in id order, which by W-342 row 1's ids is the order prod's rows were made in — its rehearsal runs step 3 (the dry run, read-only) once more: if both users then pair, the owner decides whether to correct the ₹15.99 instead (owner, 2026-11-20: D-798 stands until then). **Removed after use:** W-147 / WL-29, once prod's "not run" is logged.

### 2.2 W-088u8b (D-284) — rebuild `trade_outcomes` from raw transactions · `SUPER_USER` · **when needed, not once**

**Why.** `trade_outcomes` is derived from `transactions`; this recomputes it. It is a **standing operation, not a
one-time one** (D-284). The trade-ledger correction procedure needs it (§0.2), so
it is not removed after use. It is never automatic (D-280).

**When:**
- on a new database whose `transactions` came from elsewhere;
- after a correction to a user's `transactions`, with that user's `trade_outcomes` rows backed up and deleted first.

**Per user, idempotent.** A user with **any** `trade_outcomes` row, from any writer, is skipped and listed, so V1 /
V2 rows are never duplicated. A re-run after a failure completes the rest; a re-run after a full run writes nothing.
A failing user is an error naming them; the users before stay written.

1. **Dry run** (the default):

   ```bash
   curl -sS -X POST "$BASE/api/v1/admin/portfolio/trade-outcomes/rebuild" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
   ```

   **Read `usersRebuilt` and `rowsWritten` before writing.** Only users with transactions and no rows at all should
   be in it. On staging and prod as they stand, that is users who never sold, giving 0 rows, plus any user whose
   rows were deleted.
2. **Write:** the same call with `?dryRun=false`.
3. **Verify:** for each rebuilt user, `SELECT count(*) FROM wealthlens.trade_outcomes WHERE email = :'email';` (`PSQL -v email='<stored user email>'`, §2.19; §0.7) equals the rows the
   report gave for them, and each row's `created_by` is `<stored caller email>` (§0.7). A second call reports
   `rowsWritten` 0.

**Know this:** since W-343 (D-800) same-day sales are replayed in id order, so a run gives the same rows every time (before it, MongoDB's unfixed order could move a sale to another lot, W-342). Rebuilt rows carry no charge breakdown, so the capital-gains summary refuses a user whose rebuilt rows
have broker charges (D-238 C2a; §3.1). Rebuild one user at a time (delete only that user's rows), never the whole
collection.

**Way back:** delete the rows this run wrote (`created_by` = `<stored caller email>` (§0.7), `created_at` = the run).

### 2.3 W-088u8c (D-285) — rewrite the `FY`-form financial years · `SUPER_USER` · **retired by W-149 (D-875) · do not run**

> **Retired.** Run on prod (W-334e2: 163 written, after 0) and staging (W-334d3a: 56 written, after 0); W-149 removed its route and code. Kept below as the record of what it did.

**Why.** W-086a (D-239) moved `trade_outcomes` to one financial-year form, `2025-2026`. Its startup rewrite never ran
on prod: the owner's check (2026-10-20) found **163** `FY2025-26`-form rows there, the rebuild's rows, and none on
staging. Nothing queries `trade_outcomes` by financial year (D-239; the capital-gains summary selects by sell date),
so the old form misleads only whoever reads the field. This makes the stored year consistent.

1. **Count before** (read-only): `db.trade_outcomes.countDocuments({ financial_year: /^FY/ })`. Expect 163.
2. **Dry run** (the default):

   ```bash
   curl -sS -X POST "$BASE/api/v1/admin/portfolio/trade-outcomes/financial-years/rewrite" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
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

### 2.4 W-088k4b (D-308) — date the bonus lots processed before W-088k4a from their allotment · `SUPER_USER` · **retired by W-150 (D-876) · do not run**

> **Retired.** Run on prod (W-334e2: written, second dry run 0) and staging (W-334d3a); W-150 removed its route and code. Kept below as the record of what it did.

**Why.** A bonus share's holding period runs from its allotment (1961 Explanation 1(i)(f) to s.2(42A); 2025
s.2(101)(c)(C)(IV)). Before W-088k4a a bonus lot, its transaction and the disposals of it were dated at the action's
ex-date. The tax engine counts the term from that date, so a sale within days of the twelve-month anniversary can fall
in the wrong term. This moves them to the allotment date **you supply**, read from each company's announcement.

1. **Count before** (read-only): the bonus actions, and those without an allotment date (§3.6):
   `db.corporate_action.find({ type: "BONUS" }, { stock_code: 1, record_date: 1, ex_date: 1, allotment_date: 1 })`.
   For each, look up its allotment date.
2. **Write the request** to `allotment-dates.json`: `[{ "corporateActionId": "<_id>", "allotmentDate": "yyyy-MM-dd" }, …]`.
3. **Dry run** (the default):

   ```bash
   curl -sS -X POST "$BASE/api/v1/admin/portfolio/corporate-actions/bonus/allotment-dates/correct" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H "Content-Type: application/json" --data @allotment-dates.json | jq '.data'
   ```

   Expect, per action, `assets` = `transactions` = the users holding it at the record date, and `tradeOutcomes` = the
   sales of those bonus lots. An entry that is not a bonus, names no action, is repeated, or disagrees with an
   allotment date already held is refused (400) **before anything is written**. **Compare `assets` with
   `tradeOutcomes`:** disposals are matched by stock code, so a stock renamed since its bonus shows `tradeOutcomes` 0
   while it was sold. Fix those rows by hand (step 5's backup first).
4. **Back up** the four collections for the stocks named:

   ```bash
   for c in corporate_action assets transactions trade_outcomes; do
     mongoexport --uri "<uri>" --db <db> --collection $c --query '{"stock_code":{"$in":["<STOCK1>","<STOCK2>"]}}' \
       --out bonus-allotment-$c-backup.json
   done
   ```

5. **Write:** the same call with `?dryRun=false`. Each action gets `allotment_date`. Its bonus lots and transactions
   still at the ex-date move to the allotment date. Each disposal of them gets `buy_date` and `holding_period_days`
   recomputed; since W-160c it no longer writes a term. `audit_metadata.updatedBy` is the caller.
6. **Verify:** a second call (dry run) reports `0` for every count, and step 1 shows every bonus with its
   `allotment_date`.

**Second run:** safe, including after a run that failed part-way: only documents still at an ex-date are matched, and
an equal allotment date is a no-op. **Way back:** `mongoimport --mode upsert` of the step-4 files.

**Removed after use:** W-150 / WL-34, once this is logged for every database with bonus actions.

### 2.5 W-160c (D-365) — remove the term `portfolio` stored on `trade_outcomes` · `SUPER_USER` · **retired by W-168 (D-877) · do not run**

> **Retired.** Run on prod (W-334e2: 163, after 0) and staging (W-334d3a: 201, after 0); W-168 removed its route and code. Kept below as the record of what it did.

**Why.** `portfolio` stored a short-term / long-term label (`capital_gains_type`) and its reason
(`classification_reason`) on each disposal, decided from its own copy of the holding-period law. The term is the tax
side's to decide from the row's facts (D-362), and since W-160c no code writes or reads either field. This removes
them from the rows written before. Nothing reads them, so the step only cleans the data; it changes no figure.

**When:** at the one deploy, after §2.4. After W-160c no writer sets either field, so §2.2's rebuild may run before or
after it.

1. **Count before** (read-only):
   `db.trade_outcomes.countDocuments({ $or: [ { capital_gains_type: { $exists: true } }, { classification_reason: { $exists: true } } ] })`.
   2026-10-27, rows with a term: prod 163, staging 201.
2. **Dry run** (the default):

   ```bash
   curl -sS -X POST "$BASE/api/v1/admin/portfolio/trade-outcomes/stored-terms/remove" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   ```

   Expect `rows` equal to step 1's count.
3. **Back up** the two fields:

   ```bash
   mongoexport --uri "<uri>" --db <db> --collection trade_outcomes \
     --query '{"$or":[{"capital_gains_type":{"$exists":true}},{"classification_reason":{"$exists":true}}]}' \
     --fields _id,capital_gains_type,classification_reason --out trade_outcomes-stored-terms-backup.json
   ```

   **Keep the file (W-195, D-411).** Step 4 deletes the only in-database copy, so this export is the record of what the
   old label said: keep it with the deploy record for that database, named for the database
   (`trade_outcomes-stored-terms-backup-<db>.json`), and do not treat it as a working file.

4. **Write:** the same call with `?dryRun=false`. Both fields are unset on every row carrying either, and
   `audit_metadata.updatedBy` is set to the caller.
5. **Verify:** step 1's count is 0, and a second call reports `rows` 0.

**Second run:** safe, including after a run that failed part-way: only rows still carrying either field are matched
(`TradeOutcomeStoredTermRemovalIntegrationTest`). **Way back:** `mongoimport --mode merge` of the step-3 file puts both
fields back; no code reads them.

**Removed after use:** W-168 / WL-35, once this is logged for every database.

### 2.6 W-160d3 (D-369) — move the P&L's stored short / long pair into its split · `SUPER_USER` · **retired by W-170 (D-878) · do not run**

> **Retired.** Run on prod (W-334e2: 3 sides placed, 0 unplaced) and staging (W-334d3a: 7 placed, 0 unplaced); W-170 removed its route and code (`StoredReport` stayed with §2.8's backfill until W-173 removed both). Kept below as the record of what it did.

**Needs `m1.1` (W-338, D-789):** on `m1` the rewrite matched no `profit_and_loss` year whose `_id` is a string of 24 hex digits
(prod's are), and wrote nothing.

**Why.** Since W-160d2 the P&L reads realised figures only from `gains_by_asset_and_segment`, its split by asset type
and segment. Documents written before W-160d1 hold them in the short / long pair (`short_term_capital_gains`,
`long_term_capital_gains`) and V2's duplicate `gains_by_classification`. Until this runs, those years show no
realised figures and add nothing to "total realised profit". This moves the pair's own figures into the split, so no
amount changes, and unsets the old fields. `trade_outcomes` supplies only which asset type and segment a side's year
was.

**When:** at the deploy, **straight after it**, after §2.2 if §2.2 is run, since a user whose `trade_outcomes` are
rebuilt there can then be placed here.

**What it cannot place.** A side whose year has no `trade_outcomes`, more than one asset type or segment, or an outcome
with no asset type, is left as it is and listed with its reason. **Bring that list back**: a session places a backlog
row for those sides and they are decided one by one (D-366 C2a).

1. **Count before** (read-only):

   ```js
   db.profit_and_loss.countDocuments({ $or: [
     { "realised_profits.short_term_capital_gains": { $exists: true } },
     { "realised_profits.long_term_capital_gains": { $exists: true } },
     { "realised_profits.gains_by_classification": { $exists: true } },
     { "out_sourced_realised_profits.short_term_capital_gains": { $exists: true } },
     { "out_sourced_realised_profits.long_term_capital_gains": { $exists: true } },
     { "out_sourced_realised_profits.gains_by_classification": { $exists: true } } ] })
   ```

2. **Dry run** (the default):

   ```bash
   curl -sS -X POST "$BASE/api/v1/admin/portfolio/profit-and-loss/split/reshape" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   ```

   Read `placed` (where each side's pair would go) and `unplaced` (each side left, with its reason). Keep the
   `unplaced` list.
3. **Back up** the collection:

   ```bash
   mongoexport --uri "<uri>" --db <db> --collection profit_and_loss --out profit_and_loss-before-reshape.json
   ```

4. **Write:** the same call with `?dryRun=false`. Each placed side's pair is added into its split entry, at the year,
   each month and each half, and the pair and the map are unset. `audit_metadata.updatedBy` is the caller.
5. **Verify:** step 1's count equals the number of documents with a side in `unplaced`, and a second dry run reports
   `placed` empty.

**Second run:** safe, including after a run that failed part-way: a placed side keeps no old field, so only what is
left is matched (`ProfitAndLossSplitReshapeIntegrationTest`). **Way back:** `mongoimport --mode upsert` of the step-3
file.

**Removed after use:** W-170 / WL-36, once this is logged for every database and its `unplaced` list is placed.

### 2.7 W-160f (D-371) — drop `portfolio`'s holding-period policies · owner, `mongosh` · **retired by W-171 (D-880) · do not run**

> **Retired.** Run on prod (W-334e2: present and empty, dropped) and staging (W-334d3a: 11 backed up, dropped); `holding_period_policies` is gone from both. Kept below as the record of what it did.

**Why.** `holding_period_policies` held `portfolio`'s own copy of the holding-period law, seeded by `POST
/api/v1/admin/charges/seed`. Since W-160f no code reads, writes or seeds it: the term is the engine's `RS-CG-TERM` (D-305, D-362).
This removes the stale law from the database. Nothing reads the collection, so no figure changes.

**When:** at the deploy, any time after it.

1. **Count before** (read-only): `db.holding_period_policies.countDocuments({})`. Expect the shipped policies (11),
   or 0 where `/api/v1/admin/charges/seed` was never run. 2026-10-27: prod 0, staging 11. §1B no longer writes it. The previous build (`ec85b188`) creates it empty when it starts for §1.r step 1's token, so prod shows it present with 0 (W-334c4b row 17).
2. **Back up:**

   ```bash
   mongoexport --uri "<uri>" --db <db> --collection holding_period_policies --out holding_period_policies-backup.json
   ```

3. **Drop:** `db.holding_period_policies.drop()`. Its two indexes go with it.
4. **Verify:** `db.getCollectionNames().includes("holding_period_policies")` is `false`.

**Second run:** safe; dropping a collection that does not exist changes nothing. **Way back:** `mongoimport --uri
"<uri>" --db <db> --collection holding_period_policies --file holding_period_policies-backup.json`. No code would read
it.

**Retired after use:** W-171 / WL-37, once this is logged for every database. There is no code to remove.

### 2.8 W-172 (D-373) — add the V2 sales' profit, brokerage and misc to the P&L · `SUPER_USER` · **retired by W-173 (D-892) · do not run**

> **Retired.** Run on prod (W-334e2: 0 recorder rows, dry run 0 / 0 / 0 / 0 — nothing to write) and staging (W-334d3a B18: 1 row, unplaced — owner "(a) left"; folded by that user's §2.11 in W-334d3b W10). W-173 removed its route and code; §2.18 clears the list it kept. Kept below as the record of what it did.

**Needs `m1.1` (W-338, D-789):** on `m1` the backfill matched no `profit_and_loss` year whose `_id` is a string of 24 hex digits
(prod's are), and added nothing.

**Why.** Before W-169 a V2 sale wrote only its purchase and sale amounts into the P&L, so every V2 year shows no
profit, brokerage or misc, and "total realised profit" counts V1 sales only. Each V2 sale's trade-outcome row, written
by `TradeOutcomeRecorder`, holds those three figures. This adds them into the split entry of the row's asset type and
segment, at the year, month and half. Purchase and sale amounts, and every V1 figure, stay as they are.

**When:** at the deploy, **after §2.6** (the split entries must exist). `recordedBefore` is the deploy moment **in the
server's own time zone**, which is the zone `audit_metadata.createdAt` is written in. Rows written after it are W-169's
and are skipped.

**What it cannot place.** It lists, and leaves alone:
- a row whose P&L document, side, split entry, month or half does not exist (for example a side §2.6 could not
  place);
- a row with no asset type or sell date;
- a row with no record of when it was written.

**Bring that list back** with §2.6's.

**What it cannot find.** V2 sales made before the recorder existed (before 2026-09-13) have no row, so they keep
purchase and sale only.

1. **Count before** (read-only): `db.trade_outcomes.countDocuments({ sell_charge_breakup: { $exists: true } })`, the
   recorder's rows.
2. **Dry run** (the default):

   ```bash
   curl -sS -X POST "$BASE/api/v1/admin/portfolio/profit-and-loss/v2-figures/backfill?recordedBefore=<yyyy-MM-ddTHH:mm:ss>" \
     -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   ```

   Read `placed`, `unplaced` (with reasons), `alreadyCounted` and `writtenAfter`. Keep `unplaced`.
3. **Back up** `profit_and_loss`:

   ```bash
   mongoexport --uri "<uri>" --db <db> --collection profit_and_loss --out profit_and_loss-before-v2-backfill.json
   ```

4. **Write:** the same call with `&dryRun=false`. Each placed row's figures are added, and its id joins the document's
   `pnl_backfilled_outcome_ids` in the same update. `audit_metadata.updatedBy` is the caller.
5. **Verify:** a second dry run reports `placed` empty and `alreadyCounted` equal to step 4's `placed`.

**Second run:** safe, including after a run that failed part-way: a row whose id is in the document's set is never
added again (`ProfitAndLossV2BackfillIntegrationTest`). Before W-283 a live save of the document dropped that set, so a
second run after any trade in the year could add a sale again; the set is now kept on save, and a §2.11 rebuild fills
it with every row it folds (C3a, D-575). A document saved between a first run and W-283's deploy may have lost it:
read `placed` on a second dry run before writing. **Way back:** `mongoimport --mode upsert` of the step-3 file.

**Removed after use:** W-173 / WL-38, which also unsets `pnl_backfilled_outcome_ids`, once this is logged for every
database and its `unplaced` list is placed.

### 2.9 W-162b (D-377) — remove the deductible charges stored on `trade_outcomes` · `SUPER_USER` · **retired by W-174 (D-879) · do not run**

> **Retired.** Run on prod (W-334e2: 0, nothing to write) and staging (W-334d3a: 1, after 0); W-174 removed its route and code. Kept below as the record of what it did.

**Why.** Each V2 recorder row stored `deductible_buy_charges` and `deductible_sell_charges`, the part of its charge
breakdowns the charge catalogue's flag called deductible. Which charges reduce a capital gain is the tax side's rule
data for the year (`RS-CG-CHARGE`, W-162a, D-376), and since W-162b no code writes or reads either field. This removes
them from the rows written before. Nothing reads them, so the step only cleans the data; it changes no figure. The
breakdowns themselves (`buy_charge_breakup`, `sell_charge_breakup`) stay.

**When:** at the deploy, after §2.5. No other step reads either field, so the order among the rest is free.

1. **Count before** (read-only):
   `db.trade_outcomes.countDocuments({ $or: [ { deductible_buy_charges: { $exists: true } }, { deductible_sell_charges: { $exists: true } } ] })`.
   2026-10-27: prod 0, staging 1.
2. **Dry run** (the default):

   ```bash
   curl -sS -X POST "$BASE/api/v1/admin/portfolio/trade-outcomes/deductible-charges/remove" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   ```

   Expect `rows` equal to step 1's count.
3. **Back up** the two fields:

   ```bash
   mongoexport --uri "<uri>" --db <db> --collection trade_outcomes \
     --query '{"$or":[{"deductible_buy_charges":{"$exists":true}},{"deductible_sell_charges":{"$exists":true}}]}' \
     --fields _id,deductible_buy_charges,deductible_sell_charges --out trade_outcomes-deductible-charges-backup.json
   ```

4. **Write:** the same call with `?dryRun=false`. Both fields are unset on every row carrying either, and
   `audit_metadata.updatedBy` is set to the caller.
5. **Verify:** step 1's count is 0, and a second call reports `rows` 0.

**Second run:** safe, including after a run that failed part-way: only rows still carrying either field are matched
(`TradeOutcomeDeductibleChargeRemovalIntegrationTest`). **Way back:** `mongoimport --mode merge` of the step-3 file puts
both fields back; no code reads them.

**Removed after use:** W-174 / WL-39, once this is logged for every database.

### 2.10 W-162c (D-378) — unset the deductibility flag on `charge_catalogue` · owner, `mongosh` · **retired by W-175 (D-881) · do not run**

> **Retired.** Read on prod (W-334e2) and staging (W-334d3a: 0 flagged) — nothing was left to unset. Kept below as the record of what it did.

**Why.** `charge_catalogue.deductible_for_capital_gains` was one yes/no per charge code, the same for every year,
saying whether the charge reduces a capital gain. Which charges do is the tax side's rule data for the year
(`RS-CG-CHARGE`, W-162a, D-376), and since W-162c no code reads, writes, seeds or backfills the field. This removes the
stale flag. Nothing reads it, so no figure changes; the catalogue's codes and other fields stay.

**When:** at the deploy, any time after it.

1. **Count before** (read-only): `db.charge_catalogue.countDocuments({ deductible_for_capital_gains: { $exists: true } })`.
   Expect the codes seeded before W-162c (12), or 0 where `/api/v1/admin/charges/seed` was never run. **After §1A it is 0 on both
   databases** (staging's 12 codes on 2026-10-27 are cleared there and re-seeded without the flag), so the step
   confirms 0 and writes nothing.
2. **Back up:**

   ```bash
   mongoexport --uri "<uri>" --db <db> --collection charge_catalogue \
     --query '{"deductible_for_capital_gains":{"$exists":true}}' \
     --fields _id,code,deductible_for_capital_gains --out charge_catalogue-deductibility-backup.json
   ```

3. **Unset:** `db.charge_catalogue.updateMany({ deductible_for_capital_gains: { $exists: true } }, { $unset: { deductible_for_capital_gains: "" } })`.
   Expect `modifiedCount` equal to step 1's count.
4. **Verify:** step 1's count is 0.

**Second run:** safe, including after a run that failed part-way: only documents still carrying the field are matched, so a
second run finishes what is left or changes nothing (`modifiedCount` 0). **Way back:** `mongoimport --uri "<uri>" --db
<db> --collection charge_catalogue --mode merge --file charge_catalogue-deductibility-backup.json`. No code would read it.

**Retired after use:** W-175 / WL-40, once this is logged for every database. There is no code to remove.

### 2.11 W-283 (D-575) — rebuild one user's `profit_and_loss` from stored rows · `SUPER_USER` · **when needed, not once**

**Why.** `profit_and_loss` is derived; this recomputes one user's documents from `trade_outcomes` (gains) and the
`user_charges` rows of buys and sales (charges), as recorded. Nothing is priced again (ADR-32). It replaces hand edits
of the nested P&L after a correction (B-4). A **standing operation**, not removed after use; never automatic.

**When:** after a correction to a user's `transactions` (§0.2 duplicates, §3 findings). In order: correct
`transactions` and `user_charges`; if that user's `trade_outcomes` are wrong too, back them up, delete them and run
§2.2 for the user; then this.

**Know this:**
- V1 trades' recorded charges are folded too, which V1 never did (C2a), so a V1 user's `chargesRupees` can rise; their
  gains come from their `trade_outcomes` rows, as V2's do.
- An AMC cycle and the other account events are not folded, as the live P&L does not fold them.
- A charge row naming a transaction that is not stored refuses the whole call (400, naming it): delete that
  `user_charges` row or restore the transaction.
- Until W-173 the rebuilt documents listed every folded row in `pnl_backfilled_outcome_ids`, so §2.8 never added them again; W-173 removed both (§2.18 clears the stored lists).
- A row is filed under its own `segment`. A V1 or §2.2-rebuilt row stored before W-295 has none and is filed under
  delivery, even when its sale was intraday. **Run §3.19 first, and do not rebuild a user it lists until W-297 has
  corrected their rows** (W-295, D-595). W-297's §2.14 was that correction (retired by W-299, D-891: nothing to correct on prod or staging).

1. **Before (read-only):** note the user's documents and back them up (`PSQL`, §2.19; §0.7 for the stored form):
   ```bash
   PSQL -v email='<stored user email>' <<'EOF'
   COPY (SELECT * FROM wealthlens.profit_and_loss WHERE email = :'email') TO STDOUT WITH CSV HEADER \g /w/pnl-<user>-backup.csv
   SELECT count(*) AS profit_and_loss FROM wealthlens.profit_and_loss WHERE email = :'email';
   EOF
   ```
2. **Dry run** (the default):
   ```bash
   curl -sS -X POST "$BASE/api/v1/admin/portfolio/users/<email>/profit-and-loss/rebuild" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
   ```
   Read `years`: per financial year, `stored` against `rebuilt`. For a V2-only user they agree to the paisa; any
   difference is the correction, or a V1 figure (above). Decide before writing.
3. **Write:** the same call with `?dryRun=false`.
4. **Verify (read-only):** `SELECT count(*) FROM wealthlens.profit_and_loss WHERE email = :'email';` equals the number of
   rebuilt years, and a second dry run reports every year's `stored` equal to its `rebuilt`.

**Second run:** safe, including after one that failed part-way: the write is one transaction that replaces the user's
documents with the same fold, so a second run writes the same documents (`ProfitAndLossRebuildIntegrationTest`).
**Way back:** delete the user's documents and put the backup back, in one transaction (`PSQL`, §2.19):
```bash
PSQL -v email='<stored user email>' <<'EOF'
BEGIN;
DELETE FROM wealthlens.profit_and_loss WHERE email = :'email';
\copy wealthlens.profit_and_loss FROM '/w/pnl-<user>-backup.csv' WITH CSV HEADER
SELECT count(*) AS profit_and_loss FROM wealthlens.profit_and_loss WHERE email = :'email';
COMMIT;
EOF
```

### 2.12 W-283a3 (D-586) — rebuild one user's `assets` from `transactions` · `SUPER_USER` · **when needed, not once**

**Why.** `assets` (the lots) is derived. This replays one user's transactions into lots
(`docs/modules/portfolio.md` §5; W-283a2, D-582) and replaces the stored ones, instead of hand-editing quantities
and charges after a correction (B-4). A **standing operation**, not removed after use; never automatic.

**When:** after a correction to a user's `transactions`, or when a dry run shows drift. Run order with the other two
rebuilds: §2.2 (`trade_outcomes`, only if those rows are wrong), then this, then §2.11 (`profit_and_loss`).

**Know this:**
- A lot the replay pairs with a stored one, by stock, broker, holder, date and buys, keeps its id, so
  `trade_outcomes.source_buy_lot_id` still names it. It also keeps its order times, its list of corporate actions and
  its creation time. Its figures are the replay's (D-582 C3a, D-586 C2a).
- Any other lot gets a new id and is listed in `newLots`.
- A buy stored before W-283a1 has no recorded flow; its lot shape is read from the stored lots. Each such reading is
  listed (`shapeFromStoredLots`, `shapeNotFound`), and a real run writes regardless (C1a). **Read those lists in the
  dry run.** A sale needs no flow: V1 and V2 both record their share of a lot's buy charges on it (W-293, D-588).
- Splits and demergers are replayed only when performed for the user (`actionsApplied`); later ones stay pending
  (`actionsPending`). The rebuild never performs one.
- A bonus lot is rebuilt as live builds it (W-291). A lot takes its segment from its transaction, as live does since W-292. A lot stored before W-292 says `DELIVERY`, as its transaction does, until W-297 corrects the transactions.
- A sale that finds less held than it sold refuses the whole call (400, naming it and the shortfall). That usually
  means a missing buy or an unperformed action; correct it first.

1. **Before (read-only):** back up the user's lots, and note how many there are (`PSQL`, §2.19; §0.7 for the stored form):
   ```bash
   PSQL -v email='<stored user email>' <<'EOF'
   COPY (SELECT * FROM wealthlens.assets WHERE email = :'email') TO STDOUT WITH CSV HEADER \g /w/assets-<user>-backup.csv
   SELECT count(*) AS assets FROM wealthlens.assets WHERE email = :'email';
   EOF
   ```
2. **Dry run** (the default):
   ```bash
   curl -sS -X POST "$BASE/api/v1/admin/portfolio/users/<email>/assets/rebuild" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.'
   ```
   - Read `lotsDiffering`, then each entry of `lots` with non-empty `differingFields`; `stored` and `rebuilt` sit side
     by side.
   - Read `newLots` and the reading lists. Decide before writing.
3. **Write:** the same call with `?dryRun=false`. `lotsWritten` is the number of lots the user now has.
4. **Verify (read-only):**
   - the count above now equals `lotsWritten`;
   - a second dry run reports `lotsDiffering` 0 and `newLots` empty;
   - `GET /api/v1/admin/portfolio/users/<email>/stocks` (and `…/mutual-funds`) shows the holdings (`HoldingsAdminController`, D-855 C3a).

**Second run:** safe, including after one that failed part-way. The write is one transaction that deletes and saves
the user's lots, so a failure leaves them as they were, and a second run writes the same lots under the same ids
(`AssetsRebuildIntegrationTest`).

**Way back:** delete the user's lots and put the backup back, in one transaction (`PSQL`, §2.19; `<user>` as in step 1):
```bash
PSQL -v email='<stored user email>' <<'EOF'
BEGIN;
DELETE FROM wealthlens.assets WHERE email = :'email';
\copy wealthlens.assets FROM '/w/assets-<user>-backup.csv' WITH CSV HEADER
SELECT count(*) AS assets FROM wealthlens.assets WHERE email = :'email';
COMMIT;
EOF
```
The count equals the backup's rows (W-337j3b's proof, D-1022, rolled back there).

### 2.13 W-291 (D-589) — clear what bonus transactions copied from a buy · owner, `mongosh` · **retired by W-294 (D-890) · do not run**

> **Retired.** Run on prod (W-334e2 C4 – C9: 10 cleared, `copied` 0; step 5 in W-334e3 W9 – W10) and staging (W-334d3a C4 – C7: 6 cleared, `copied` 0; step 5 in W-334d3b W9 – W10). There was no code to remove. Kept below as the record of what it did.

**Why.** Until W-291 a bonus transaction was a copy of the user's first buy. It kept that buy's `broker_charges`,
`misc_charges`, `order_id`, `order_execution_time`, and its submission identifiers (`idempotency_key`,
`trade_fingerprint`, `submitted_at`, `source_temp_transaction_id`). A bonus costs nothing and was never ordered or
submitted. The charges also feed §2.2's `trade_outcomes` rebuild, so they are cleared before it runs. The bonus
*lots* are corrected per user by §2.12 afterwards.

**When:** after the W-291 deploy, after §3.17 shows the count, before the per-user step below.

1. **Before (read-only):**
   ```js
   db.transactions.countDocuments({ corporate_action: "BONUS", $or: [ { broker_charges: { $gt: 0 } }, { misc_charges: { $gt: 0 } },
     { order_id: { $exists: true } }, { order_execution_time: { $exists: true } }, { idempotency_key: { $exists: true } },
     { trade_fingerprint: { $exists: true } }, { submitted_at: { $exists: true } }, { source_temp_transaction_id: { $exists: true } } ] })
   ```
2. **Backup:**
   ```bash
   mongoexport --uri "<uri>" --db <db> --collection transactions --query '{ "corporate_action": "BONUS" }' --out bonus-transactions-backup.json
   ```
3. **Correct:**
   ```js
   db.transactions.updateMany({ corporate_action: "BONUS" }, { $set: { broker_charges: 0, misc_charges: 0 },
     $unset: { order_id: "", order_execution_time: "", idempotency_key: "", trade_fingerprint: "", submitted_at: "", source_temp_transaction_id: "" } })
   ```
4. **After (read-only):** step 1's count is 0.
5. **Then, per user §3.17 lists:** §2.12 (lots) → §2.2 (that user's `trade_outcomes`, backed up and deleted first,
   only if they sold bonus shares) → §2.11 (P&L).

**Second run:** safe. It matches only bonus transactions and sets the same values, so a second run changes nothing.
**Way back:** `mongoimport --uri "<uri>" --db <db> --collection transactions --mode upsert --file bonus-transactions-backup.json`.

**Retired after use:** W-294 / WL-45, once this is logged for every database. There is no code to remove.

### 2.14 W-297 (D-599) — restore the segment stored before W-292 / W-295 · `SUPER_USER` · **retired by W-299 (D-891) · do not run**

> **Retired.** Run on prod (W-334e2: §3.18 / §3.19 empty, dry run 0 / 0) and staging (W-334d3a C8 – C9: empty, dry run 0 / 0, mixed 0): nothing to write on either. W-299 removed its route and code. Kept below as the record of what it did.

**Why.** Until W-292 no transaction or lot stored its segment, so every one says `DELIVERY`. Until W-295 a V1 or
§2.2-rebuilt outcome row held no row-level `segment`, and §2.11 files such a row under delivery. This restores the
segment wherever stored data records it:
1. A transaction takes the segment its BUY or SELL `user_charges` row was priced under, where that is not delivery.
2. An outcome row's `segment` and `sell_segment` take its sale transaction's, where that is not delivery. Failing
   that, an absent `segment` takes the row's own `sell_segment`.
3. Its `buy_segment` takes the segment of the holding's buys on its buy date, where they all agree. Where they
   disagree (a same-day mix merged before W-296) the row is listed in `rowsWithMixedBuys` and left as it is.

A trade no stored fact records stays as stored: a V1 buy before W-280, or a trade recorded with the engine off.
**Do not use §2.2 for these users.** Its rows carry no charge breakdown, so the capital-gains summary would refuse
them (§2.2 "Know this").

**When:** after the deploy that carries W-292 – W-297, before any §2.11 for a user §3.19 lists.

1. **Before (read-only):** §3.18 and §3.19. Note each one's users.
2. **Dry run** (the default):
   ```bash
   curl -sS -X POST "$BASE/api/v1/admin/portfolio/transactions/segment/correct" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   ```
   Read `transactionsCorrected`, `rowsCorrected`, `users` and `rowsWithMixedBuys`. The users should be those §3.18
   and §3.19 listed.
3. **Backup:**
   ```bash
   mongoexport --uri "<uri>" --db <db> --collection transactions --out transactions-segment-backup.json
   mongoexport --uri "<uri>" --db <db> --collection trade_outcomes --out trade-outcomes-segment-backup.json
   ```
4. **Write:** the same call with `?dryRun=false`.
5. **After (read-only):** §3.18 and §3.19 list none of the users corrected. A second dry run reports 0 and 0, and
   `rowsWithMixedBuys` as before.
6. **Then, per user in `users`:**
   - if `transactions` > 0: §2.12 (lots; a same-day mix is now split), then §2.11 (P&L);
   - if only `rows` > 0: §2.11.

   §2.11's W-295 warning no longer applies to a user once this has run.

**Second run:** safe, including after one that failed part-way. Each rule reads the transactions as step 1 leaves
them, and only moves a stored value to a recorded non-delivery fact. A second run therefore finds nothing left
(`SegmentCorrectionIntegrationTest`).
**Way back:** `mongoimport --uri "<uri>" --db <db> --collection transactions --mode upsert --file
transactions-segment-backup.json`, and the same for `trade_outcomes`.

**Retired after use:** W-299 / WL-46, once this is logged for every database. The code
(`SegmentCorrection`, its controller, report and test) is removed then.

### 2.15 W-339 (D-803) — back up and drop `reports` · owner, `mongosh` · **retired by W-337j3a (D-1020) · do not run**

> **Retired.** Moot: `reports` was never copied (no entity maps it) and no table of that name exists on staging or prod (W-337j3a's check, 2026-11-28); the MongoDB clusters holding it are deleted by W-337j4. Kept below as the record of what it did.

**Why.** `reports` holds rows no code has read or written since `4c6c92e0` (2026-06-14): no entity maps it and no code names it
(`grep '"reports"'`, 2026-11-20). On prod it holds 163 rows, 73 of them with a plain actor email (W-334c2 row 5) that §1.r cannot
reach, since no entity describes the collection. Personal data kept for no purpose is dropped, as §0.8 dropped the legacy collections.

**When:** after §1.r, any time; it touches nothing else.

1. **Count before** (read-only): `db.reports.countDocuments({})`. Prod 163 (W-334b row 9); staging not yet counted.
2. **Back up** (personal data in plain text: `umask 077`, kept with the run's other backups and deleted with them):

   ```bash
   mongoexport --uri "<uri>" --db <db> --collection reports --out reports-backup.json
   ```

   Expect `exported <step 1's count> records`.
3. **Drop:** `db.reports.drop()` (MongoDB 8 answers `true` for an absent collection too, so step 4 is the proof).
4. **Verify:** `db.getCollectionNames().includes("reports")` is `false`.

**Second run:** safe; dropping an absent collection changes nothing. **Way back:** `mongoimport --uri "<uri>" --db <db> --collection
reports --file reports-backup.json`. No code would read it.

**Retired after use:** W-339, once this is logged for every database. There is no code to remove.

### 2.16 W-347 (D-808) — remove the owner's test entries `DUMMY` and `LAVANYA` · owner, `mongosh` · **retired by W-337j3a (D-1020) · do not run**

> **Retired.** Moot on PostgreSQL: staging and prod hold no `DUMMY` / `LAVANYA` row and no row without an `asset_type` (W-337j3a's check, 2026-11-28). Kept below as the record of what it did.

**Why.** The owner entered trades in two made-up stocks on prod to test the app (owner, 2026-11-20). They sit in two users' data with no
corporate action behind their bonuses and count in those users' holdings. On prod's copy (W-347 row 1) they are 9 transactions and 8
lots, in two users who hold nothing else and have no P&L year, so removing them needs no rebuild.

1. **Count before** (read-only): every collection's documents naming either stock, per user (`c347-01-test-stocks.js`, the query in the
   rehearsal files). Expect on prod: `transactions` 9, `assets` 8, nothing else; each involved user with no other transaction and no P&L
   year. If a user holds anything else, stop: §2.12 and §2.11 follow for them.
2. **Back up:** `mongoexport` of `transactions` and `assets` with `--query '{"stock_code":{"$in":["DUMMY","LAVANYA"]}}'`.
3. **Delete:** `db.transactions.deleteMany({ stock_code: { $in: ["DUMMY", "LAVANYA"] } })` and the same on `assets`. Expect 9 and 8.
4. **Verify:** step 1 lists nothing.
5. **The two test users themselves** (owner, 2026-11-20: "remove the test logins"; D-815). Take their stored emails from step 1 **before**
   step 3 (`transactions.distinct("email", { stock_code: { $in: ["DUMMY", "LAVANYA"] } })`, kept in a local file, never printed). After step
   4: count every collection's documents carrying either stored email (`email`, `principal`, `subject_email`, `audit_metadata.createdBy`),
   back up those documents, delete them — the login in `user_details` last — and count again: 0. Not the app's account erasure (§1.u), which
   holds an account a year before erasing it: these are the owner's own test accounts, removed at once.

**Second run:** safe; nothing is left to match. **Way back:** `mongoimport` of step 2's two files and step 5's backup.

**Retired after use:** W-347, once logged for prod. There is no code to remove.

### 2.17 W-354 (D-831, D-832) — remove prod's last test entries: `DUMMY3` and `"username"` · owner, `mongosh` · **retired by W-337j3a (D-1020) · do not run**

> **Retired.** Moot on PostgreSQL: staging and prod hold no `DUMMY3` row, no `ICICIBANK` lot `6835e328e9c3af1009a6f4dd` and no row without an `asset_type` (W-337j3a's check, 2026-11-28). Kept below as the record of what it did.

**Why.** Prod's 5 trades and 6 lots with no `asset_type` (§3.10) are all test entries (owner, 2026-11-20: "safe to delete"). §2.16
removes the `LAVANYA` ones; this removes the rest: the stock `DUMMY3` (user A: 1 transaction, 1 lot, a login, nothing else) and the
`ICICIBANK` lot `6835e328e9c3af1009a6f4dd` (1 @ ₹100, 2023-09-21) held by `"username"`, which stores nothing else and whose buy
`6835e327e9c3af1009a6f4dc` does not exist. The script finds both users from the data — whoever holds `DUMMY3`, and that lot's
holder — never from a typed email, so it runs the same before and after §1.r encrypts stored emails. It stops if that lot is not
`ICICIBANK`. It matches `stock_code: "DUMMY3"` and every document whose `email`, `principal`, `subject_email` or `_id` is one of
the two users; `audit_metadata.createdBy` is counted, not deleted; no transaction is deleted by id. Scripts in
`~/wealthlens-runs/w334c`: `w354-04-remove-test-leftovers.js`, `w354-05-restore.js`.

1. **Count before** (read-only). Expect `users` 2; `before` `transactions` 1, `assets` 2, `user_details` 1, and any `principal` /
   `subject_email` rows of user A (processing log, consent); `perUser` with `otherStocks` 0, `profitAndLossYears` 0, `outcomeRows` 0.
   **Stop** if a user shows another stock, a P&L year or an outcome row: that is real data.
```zsh
read -r 'MONGO_USER?Prod Mongo user: '; read -rs 'MONGO_PASSWORD?Prod Mongo password: '; echo
(cd ~/wealthlens-runs/w334c && for i in 1 2 3; do TARGET_DB=Cluster0 mongosh "mongodb+srv://cluster0.x6trgen.mongodb.net/Cluster0" --username "$MONGO_USER" --password "$MONGO_PASSWORD" --quiet --file w354-04-remove-test-leftovers.js && break; echo "retry $i"; sleep 5; done)
unset MONGO_PASSWORD
```
2. **Back up and delete.** The script writes `w354-backup.json` (canonical EJSON, mode 600) in that folder, reads it back and
   deletes only if its counts equal step 1's — the login last. Expect `backup` = `deleted` = step 1's `before`, `after` `{}`. It
   refuses to run if `w354-backup.json` already exists (an earlier run's backup is kept, never overwritten).
```zsh
read -r 'MONGO_USER?Prod Mongo user: '; read -rs 'MONGO_PASSWORD?Prod Mongo password: '; echo
(cd ~/wealthlens-runs/w334c && for i in 1 2 3; do TARGET_DB=Cluster0 REMOVE=yes mongosh "mongodb+srv://cluster0.x6trgen.mongodb.net/Cluster0" --username "$MONGO_USER" --password "$MONGO_PASSWORD" --quiet --file w354-04-remove-test-leftovers.js && break; echo "retry $i"; sleep 5; done)
unset MONGO_PASSWORD
```
3. **Verify.** Step 1 again: `users` 0, `before` `{}`. Then §3.10: `assets` 0, `transactions` 0 (with §2.16 done).

**Second run:** safe — it finds no user and nothing to delete. **Way back:** `w354-05-restore.js` reinserts the backup's documents,
skipping any still present:
```zsh
read -r 'MONGO_USER?Prod Mongo user: '; read -rs 'MONGO_PASSWORD?Prod Mongo password: '; echo
(cd ~/wealthlens-runs/w334c && for i in 1 2 3; do TARGET_DB=Cluster0 RESTORE=yes mongosh "mongodb+srv://cluster0.x6trgen.mongodb.net/Cluster0" --username "$MONGO_USER" --password "$MONGO_PASSWORD" --quiet --file w354-05-restore.js && break; echo "retry $i"; sleep 5; done)
unset MONGO_PASSWORD
```
**Rehearsed** on a throwaway `mongo:8.0.4` (W-354's plan): the three cases plus a real user holding ICICIBANK and TCS — wrong
`TARGET_DB` refused; remove deleted exactly the backup, the real user untouched; second run nothing; restore equal to the start;
restore again 0; remove with a backup present refused.

**Retired after use:** W-354, once logged for prod. There is no code to remove.

---

### 2.18 W-173 (D-892) — unset `pnl_backfilled_outcome_ids` on `profit_and_loss` · owner, IntelliJ console · **retired by W-368 (D-898) · do not run**

> **Retired.** Run on staging (W-366b rows 5a – 6c: 7 backed up, 7 unset, P&L hashes unchanged, backup dropped) and prod (W-366c rows 5a – 6c: the same, 7). Kept below as the record of what it did.

**Why.** §2.8's one-time V2 backfill kept, on each P&L document, the list of `trade_outcomes` rows already in its figures, and a §2.11
rebuild filled it too (D-575). W-173 removed the backfill and the field's mapping, so nothing reads or writes the list any more. A
document the new build saves loses it anyway (a save replaces the whole document); this clears the rest, so no stored document carries a
field no code knows. It holds row ids only, never a figure: no P&L amount changes.

**When:** after the deploy that carries W-173 (`m1.6`, W-366), on each database (prod `Cluster0`, staging `it-staging`). Before it,
the old build would write the list again. Until it runs, the new build reads such a document without complaint
(`ProfitAndLossIntegrationTest.save_whenTheStoredYearStillCarriesTheBackfillsList_readsItAndDropsTheList`).

In the IntelliJ console, with the database chosen (check the console's database field):

1. **Before (read-only):**
   ```js
   db.profit_and_loss.countDocuments({ pnl_backfilled_outcome_ids: { $exists: true } })
   ```
   Note the number, N. A 0 means nothing to do: stop here and log it.
2. **Backup** into a collection (the MongoDB tools were removed, D-849):
   ```js
   db.profit_and_loss.aggregate([{ $match: { pnl_backfilled_outcome_ids: { $exists: true } } }, { $project: { pnl_backfilled_outcome_ids: 1 } }, { $out: "profit_and_loss_backfill_ids_w173" }])
   ```
   then `db.profit_and_loss_backfill_ids_w173.countDocuments({})` must equal N. If not, stop.
3. **Write:**
   ```js
   db.profit_and_loss.updateMany({ pnl_backfilled_outcome_ids: { $exists: true } }, { $unset: { pnl_backfilled_outcome_ids: "" } })
   ```
   `matchedCount` and `modifiedCount` equal N.
4. **After (read-only):** step 1's count is 0. Then sign in and read the P&L by year in the app: the same figures as before.
5. **Drop the backup** once step 4 is checked: `db.profit_and_loss_backfill_ids_w173.drop()`.

**Second run:** safe, including after a run that stopped part-way: step 3 matches only documents that still carry the field, and a second
run matches 0. **Way back** (until step 5):
`db.profit_and_loss_backfill_ids_w173.find().forEach(b => db.profit_and_loss.updateOne({ _id: b._id }, { $set: { pnl_backfilled_outcome_ids: b.pnl_backfilled_outcome_ids } }))`.

**Rehearsed** on a throwaway `mongo:8.0.4` (W-173's build): three documents — one with a list and a 24-hex string `_id`, one with an empty
list, one without — before 2, backup 2, write 2 / 2, after 0, second run 0 / 0, the third untouched; the way back restored both lists
(`["r1","r2"]` on the string id); the backup dropped.

**Retired after use:** W-368 / WL-49, once this is logged for every database. There is no code to remove.

### 2.19 W-337h (D-936, D-980) — the PostgreSQL databases · owner, DataGrip · **every database · once · done 2026-11-27**

What exists, one host per database (D-980). Each has one role used by the owner, Flyway and the app (C2a), over TLS:

| Database | Host | Database, role | TLS |
|---|---|---|---|
| prod | Neon project `wealthlens`, branch `production`: `ep-holy-meadow-a1f4tyxx.ap-southeast-1.aws.neon.tech` | `neondb`, `neondb_owner` | `sslmode=require&channel_binding=require` |
| staging | Aiven project `aaaaaw`: `wealthlens-staging-aaaaaw.e.aivencloud.com:18042` | `defaultdb`, `avnadmin` | `sslmode=require` |
| dev | Aiven service `wealthlens-dev`: `wealthlens-dev-wealthlens-dev.d.aivencloud.com:13196` | `defaultdb`, `avnadmin` | `sslmode=require` |

Passwords are in the owner's password manager only, never in the repo. The app works in schema `wealthlens` and never
makes it (W-413, D-989): step 2 below is what makes it, on each host, before the PostgreSQL build first starts there.

1. **Check before (read-only):** `SELECT version() AS version, current_database() AS db, current_schema() AS schema;` — expect
   `PostgreSQL 18.…`, the database above, `public`.
2. **The schema:** `CREATE SCHEMA IF NOT EXISTS wealthlens;`
3. **Check after (read-only):** `SELECT schema_name FROM information_schema.schemata WHERE schema_name = 'wealthlens';` — one row.
4. **Running SQL** (every SQL step in this runbook, W-337j3b, D-1022): `psql` from the `postgres:18` image, in a folder of
   your own, which a step's backup files are written to (`/w` inside):
   ```zsh
   set -a; source ~/wealthlens-runs/<env>.env; set +a     # dev, staging or prod
   PSQL() { docker run --rm -i -v "$PWD":/w -e PGPASSWORD="$DATABASE_PASSWORD" postgres:18 psql "host=<host> port=<port> dbname=<database> user=<role> sslmode=require" -v ON_ERROR_STOP=1 "$@"; }
   ```
   with the host, port, database and role from the table above (prod: no `port=`, and `channel_binding=require` added). `-v
   email='<stored email>'` (§0.7) reaches a query as `:'email'`; `-v id=…`, `-v s=…`, `-v host=…` likewise. A step's SQL goes in
   as `<<< "…"` or `<<'EOF' … EOF` (`-c` does not substitute variables). Afterwards `unset DATABASE_PASSWORD APP_POSTGRES_DATASOURCE_PASSWORD TAX_DATA_KEY TAX_DIGEST_KEY JWT_SECURITY_KEY APP_PASSWORD`.

**Second run:** safe; `IF NOT EXISTS` changes nothing. **Way back:** `DROP SCHEMA wealthlens;` (empty until §0.9), or delete
the Neon branch or Aiven service. Nothing but the empty schema is in any of them before W-337i.

---

### 2.20 W-428a (D-1036, D-1037) — stored personal data made plain · owner · **every database · once · done 2026-11-28 (`m1.9`; D-1038)**

**Why.** From W-428a the app writes every personal value plain and looks an email up by its plain value. Rows stored before it
hold `k1:` ciphertext, so on such a database **nobody can sign in** until this run has made them plain. The run is the app itself,
started with the `plain-text-rewrite` profile beside the environment's own: no port is opened, it finds every `k1:` value by its
form in every text and `bytea` column of schema `wealthlens` (whole values, strings inside JSON, document bytes), makes it plain,
one transaction per column, logs one line per column it changed, and stops (`PlainTextRewrite`, `PlainTextRewriteRun`). It
needs the database's own `TAX_DATA_KEY` and `TAX_DIGEST_KEY` (in the env file). Passwords and refresh-token hashes are not
ciphertext and are not touched. Order: dev, then staging, then prod; logged in the run log each time.

Running SQL is §2.19 step 4 (`PSQL`, from a folder of your own).

1. **Backup first** (it holds ciphertext, so it is read only with this database's keys — keep the keys while the dump is kept):
   ```zsh
   set -a; source ~/wealthlens-runs/<env>.env; set +a     # dev, staging or prod
   docker run --rm -v "$PWD":/w -e PGPASSWORD="$DATABASE_PASSWORD" postgres:18 pg_dump "host=<host> port=<port> dbname=<database> user=<role> sslmode=require" -n wealthlens -Fc -f /w/<env>-pre-w428a.dump
   ls -l <env>-pre-w428a.dump
   ```
   (prod: no `port=`, `channel_binding=require` added, as §2.19.) Stop if the file is missing or empty.
2. **Check before (read-only):** the columns still holding `k1:` values, and how many:
   ```zsh
   PSQL <<'EOF'
   SELECT 'SELECT * FROM (' || string_agg(format('SELECT %L AS table_name, %L AS column_name, count(*) AS sealed FROM wealthlens.%I WHERE %s',
     table_name, column_name, table_name, CASE data_type WHEN 'bytea' THEN format('substring(%I FROM 1 FOR 3) = %L::bytea', column_name, 'k1:')
     ELSE format('(%I LIKE %L OR %I LIKE %L)', column_name, 'k1:%', column_name, '%"k1:%') END), ' UNION ALL ') || ') x WHERE sealed > 0 ORDER BY 1, 2'
     FROM information_schema.columns WHERE table_schema = 'wealthlens' AND data_type IN ('text', 'character varying', 'bytea')
       AND table_name <> 'flyway_schema_history' \gexec
   EOF
   ```
   Expect rows (`user_details.email` among them) on a database stored before W-428a. Keep the output.
3. **The run** — from the release worktree, with the app stopped:
   ```zsh
   [[ -f wealthlens.jar ]] || { echo "no wealthlens.jar here: stop"; return 1 }
   set -a; source ~/wealthlens-runs/<env>.env; set +a
   java -jar wealthlens.jar --spring.profiles.active=<profile>plain-text-rewrite --app.tax.jobs.enabled=false 2>&1 | tee rewrite-<env>.log
   ```
   with `<profile>` empty for dev, `staging,` or `prod,`. Expect one `<table>.<column>: N made plain` line per column step 2
   listed, then `Plain-text rewrite done: … value(s) made plain in … column(s)`, and the process ends with exit 0
   (`echo $pipestatus[1]`). A value that will not decrypt (another database's keys, an altered value) stops the run with an
   error; its column is left as it was, the columns before it are already plain — fix the cause and run again.
4. **Check after (read-only):** step 2 again — **no rows**. A row left is a value that only names `k1:` as text (the log says
   `… left (no ciphertext in them)`) or one the run missed: read it before going on; W-428c may not start while any database
   answers a row it cannot explain.
5. **Start and sign in:** start the app as usual; sign in as the owner — 200. Then delete `rewrite-<env>.log` (it names
   columns and counts, no personal data, but is not kept).

**Second run:** safe — it finds nothing sealed and changes nothing (`PlainTextRewriteIntegrationTest`); after a failed run it
completes the rest. **Way back:** stop the app and restore the step-1 dump (`pg_restore --clean --if-exists -n wealthlens -d …`),
then start the previous release, which reads only ciphertext. A release carrying W-428a also reads the restored ciphertext,
but nobody signs in on it until this step runs again. **Removed after use:** with the cipher, by W-428c (its row names this
section and the profile; YouTrack WL-51, under WL-28), once this step is logged for dev, staging and prod.

---

## 3. Standing data conditions — read-only checks, no action defined

These are states the build decided to **refuse or leave**, rather than repair. Run the check to know their size.
Any repair would be a new work item with its own section-2 entry.

| # | Item / decision | Condition | Read-only check |
|---|---|---|---|
| 3.1 | **W-086** (D-238 C2a) | `CapitalGainsSummaryService` **refuses** a user's summary if any of their disposals carries broker charges with no breakdown ("record those trades again", worded since W-223: a trade a rate card prices can be recorded again, and one no card prices cannot be computed until its charges can be entered by code). The rebuild's rows never have one. **Prod (W-334c7b2 row 2, D-821):** 61 rows of 2 users (the owner 60: 10 / 34 / 16 by sale year 2023-24 – 2025-26; the other user 1) — **left as they are**: those years are filed; the refusal is per sale year, so TY 2026-27 on is not touched. Do not add a breakdown by hand: one without an `STT` line reads as STT not paid | Count below (SQL, `PSQL`, §2.19) **On PostgreSQL (2026-11-28, W-337j3c):** prod 2 / 61 (unchanged), staging 2 / 40, dev 0. |
| 3.2 | **W-088f** (D-249 C4a, "no backfill") | Rows written before W-088f have no `acquisition_mode`; they read as `UNRECORDED` (`Acquisition.stored` maps a missing mode to it) | the 3.2 query below (SQL, `PSQL`, §2.19) |
| 3.3 | **W-088j** (D-292), narrowed by **W-088j2c2** (D-298), **W-088j2d** (D-299) and **W-088j2e** (D-300) | A side of a disposal with no charge breakdown says nothing of STT, so it reads as `UNKNOWN`, not "no STT". Since D-298 the engine settles such a side from its recorded venue: an equity side on `NSE` or `BSE` in `DELIVERY` (and a sale in `INTRADAY`) is chargeable under Chapter VII s.98 Sl. 1–3 — a buy only when the lot was acquired by purchase (Sl. 1 "Purchase", D-300), and a long-term buy acquired before 1-10-2004 or by a bonus or demerger is settled by S.O. 5054(E) (`CG-PART-LONG-NOTIFIED`). What is still **refused** by the tax engine: a side with nil charges and no breakdown whose exchange is not recorded, is spelt other than exactly `NSE` / `BSE`, or is genuinely off an exchange, and a long-term buy on `NSE` / `BSE` that is not shown to be a purchase and is not a bonus or demerger allotment — a rights allotment, or an unrecorded mode (D-300) (a side with charges and no breakdown is refused earlier, §3.1). No user declaration was built (D-299); a repair, if the count is not nil, is a new work item — the exchange string is the fault for most rows, and §2.2's rebuild fills a row's missing venue from `transactions` | Count below (SQL, `PSQL`, §2.19) (an upper bound) |
| 3.4 | **W-088j2b** (D-295, "no backfill") | `trade_outcomes` rows written before W-088j2b have no per-side venue (`buy_exchange_name`, `buy_segment`, `sell_exchange_name`, `sell_segment`); they read as `TradeVenue.UNRECORDED`. The old `exchange_name` / `segment` are **not** read for tax (V1 took the exchange from the buy, V2 and the rebuild from the sale). Remedy for a user: §2.2's per-user rebuild, which writes the new fields. The segment is published as recorded: `DELIVERY` is also the entry default (D-295) | Count and values below (SQL, `PSQL`, §2.19) |
| 3.5 | **W-088k3** (D-305) | The tax engine decides short-term or long-term itself, from `RS-CG-TERM`: an equity disposal is a listed security (twelve months) when its **sale** is recorded on `NSE` or `BSE` (`sell_exchange_name`; a reading, D-305 C1). A disposal whose sale exchange is absent or another string is **refused** for the term — no other period is shipped (D-305 C2). Rows written before W-088j2b have no `sell_exchange_name` (§3.4). Remedy for a user: §2.2's per-user rebuild, which writes it from `transactions.exchange_name` | Count below (SQL, `PSQL`, §2.19) |
| 3.6 | **W-088k4a** (D-307) | `corporate_action.allotment_date` is new and not backfilled. Performing a `BONUS` action without it is **refused** (400, naming the stock and record date), so a pending bonus whose date is not entered blocks its quarterly perform. Bonus lots processed before W-088k4a are dated at the ex-date; W-088k4b corrects them **Prod (W-346, D-805):** ASHOKLEY's action is stored with record date 2025-07-17 where the company's Annual Report 2025-26 (p. 24) gives 2025-07-16; MSUMI's 2025-07-19 is unconfirmed; no holding changes across either pair of dates, so the bonus reached the same holders — recorded, not corrected | List below (SQL, `PSQL`, §2.19) |
| 3.7 | **W-088g5** (D-433) | A `LAND_OR_BUILDING` trade is now **refused** on V1, V2 and the upload (400). Land was enterable from W-088g2 (D-425) until this guard; a row entered in that window **stays as it is** — the guard refuses new entries and touches nothing stored. Expected 0 on every database; a non-zero count is reported to the owner, not corrected (a correction would be its own item, with W-088g9's property entry) | the 3.7 query below (SQL, `PSQL`, §2.19), owner, any time after the deploy that carries W-088g5; safe to run again |
| 3.8 | **W-223** (D-437), **W-230** (D-439) | Until these a V2 buy no rate card priced had its entered `brokerCharges` replaced by nil, and a V2 sale's never reached its row; from them the entered figure stays. A lot or row already written stays nil; its entered charge is still on its source transaction, so nothing is lost. Reported, not corrected: a correction is its own item | the 3.8 query below (SQL, `PSQL`, §2.19), owner, any time after the deploy that carries W-223; read-only; safe to run again; nothing to undo |
| 3.9 | **W-213, W-216** (D-438) | Until W-213 a stock split performed through the per-user path changed no lot, yet was marked performed, and a malformed ratio could be too. A lot affected stays unsplit with a performed row. Reported, not corrected: a correction is its own row, placed once a count is seen. **Read the ratios too:** W-213 reads `n:d` as new shares to old (`2:1` doubles). A stored `1:10` meant as ten-for-one would **shrink** a holding when performed: re-enter such an action before anyone performs it | the 3.9 query below (SQL, `PSQL`, §2.19), owner, after the deploy that carries W-213 and before any split is performed; read-only; safe to run again; nothing to undo |
| 3.10 | **W-268** (D-553 C1a) | `POST /portfolio/user/{email}/update` is **removed**: it let any signed-in user set a missing `asset_type` to `MUTUAL_FUND` on every user's `assets` and `transactions`. Whether it was ever run on a database is not known. A record with no `asset_type` stays as it is; expected 0. A non-zero count is reported to the owner, not corrected — a scoped, idempotent backfill would be its own row, placed once a count is seen. **Prod (W-354, D-831, 2026-11-20):** `assets` 6, `transactions` 5 — all test entries, removed by §2.16 (`LAVANYA` 4 / 4) and §2.17 (`DUMMY3` 1 / 1, the `"username"` lot 1 / 0); expected 0 / 0 after both | the 3.10 query below, owner, SQL (`PSQL`, §2.19), before or after the deploy that carries W-268; read-only; safe to run again; nothing to undo |
| 3.11 | **W-270** (D-555 C2a) | A trade held back by a corporate action now records the flow that received it (`trade_flow`, `V1` or `V2`), and redrive records it the same way. A `TEMPORARY` transaction stored before W-270 has none; which flow sent it cannot be recovered, so it is redriven through V1 as before — a V2 trade among them comes back merged into a same-day lot and unpriced by the charge engine (W-264a G-8). Reported, not corrected: the count decides whether a row is placed. **Prod (W-334c7b3, D-823):** 18 — all one `TEST_USER` account's buys of codes `A` / `B` / `C`, none keeping its request (a redrive cannot replay them); the whole test account is removed (`c7b3-03-test-account.js`, then `c7b-e5-test-users.js` with backups guarding the delete), so the count is 0 | the 3.11 query below, owner, SQL (`PSQL`, §2.19), after the deploy that carries W-270; read-only; safe to run again; nothing to undo **On PostgreSQL (2026-11-28, W-337j3c):** prod 0, staging 1, dev 0. |
| 3.12 | **W-271** (D-556 C2a) | A V1 sale now takes only lots held on its date, as V2's does. Until W-271 a V1 sale (the route, the upload, a V1 redrive) read every lot, so a sale dated before a purchase could consume that later lot; each such allocation left a `trade_outcomes` row whose `buy_date` is after its `sell_date`. Expected 0; a non-zero count is reported to the owner, not corrected — a correction would be its own row | the 3.12 query below, owner, SQL (`PSQL`, §2.19), any time; read-only; safe to run again; nothing to undo |
| 3.13 | **W-274** (D-560 C3a) | Sale quantities are now compared within a tolerance of 1e-9. Until W-274 a V1 sale of fractional units could leave a lot holding a float remainder (2.8e-17 units seen in W-264a) that was never deleted. Such a lot is no longer shown in the holdings views, but stays stored. Expected 0; a non-zero count is reported to the owner, not corrected — a removal would be its own row | the 3.13 query below, owner, SQL (`PSQL`, §2.19), any time; read-only; safe to run again; nothing to undo |
| 3.14 | **W-279** (D-565 C2a) | A V1 buy merged into the day's lot now keeps its order time on the lot. Until W-279 the merge dropped it, so a lot built from several V1 buys lists order times for the first only. Lots with two or more buy transactions are V1 merges (V2 writes a lot per buy); one never sold from whose order times sum below its quantity lost some. The lost times are on each buy's transaction row, not the lot. Reported, not corrected — a correction would be its own row | the 3.14 query below, owner, SQL (`PSQL`, §2.19), any time; read-only; safe to run again; nothing to undo **On PostgreSQL (2026-11-28, W-337j3c):** prod 21, staging 26, dev 0 lots; every one has an empty sell list stored (none NULL), so the count means what MongoDB's did. |
| 3.15 | **W-280** (D-566 C2a) | A V1 buy is now priced and recorded under its own segment. Until W-280 V1's buy passed `DELIVERY` whatever the trade said, so a V1 intraday buy was priced on the delivery card and its `user_charges` row says `DELIVERY` while its transaction says `INTRADAY` (V2 always passed the segment, so such a pair is always a V1 buy). Reported, not corrected — re-pricing would be its own row (ADR-32). **This count matched nothing before W-292 and still matches nothing for a trade stored before it** (W-292, D-591): until then no transaction stored its segment, so none said `INTRADAY`. A V1 intraday buy made before W-280 cannot be found from stored data at all, because its charge row was priced as `DELIVERY` too. §3.18 counts the transactions whose charge row knows better | the 3.15 query below, owner, SQL (`PSQL`, §2.19), any time; read-only; safe to run again; nothing to undo |
| 3.16 | **W-289** (D-587 C2a) | A trade now waits for a split, bonus or demerger recorded in an earlier quarter and not yet performed, when the user holds lots it reaches. Until W-289 only an action in the trade's own quarter held it, so a sale in a later quarter could be applied to pre-split units before the action was performed: holding 10, a 2:1 split, selling 4 left 12, not 16. The count lists **candidates** — users with a sale dated after an action's record date, of a stock they bought with that broker before it. Same-quarter and correctly ordered ones are included, so each user is checked with §2.12's dry run, which shows the actual drift, and corrected through §2.12 then §2.11 | the 3.16 query below — one row per candidate user (the stored email, §0.7), owner, SQL (`PSQL`, §2.19), any time; read-only; safe to run again; nothing to undo |
| 3.17 | **W-291** (D-589 C2a) | A bonus lot and its transaction now carry only the bonus. Until W-291 the lot copied the first held lot's charges, allocated charges, sales and actions, and the transaction copied the first buy's charges and identifiers. A sale of bonus shares therefore charged buy charges paid once already, overstating cost and understating the gain. And a bonus could not be performed at all for a user whose first buy carried an idempotency key or came from a redrive (a duplicate key). The count lists users with bonus lots carrying charges. §2.13 corrected the transactions (retired by W-294, D-890, once run on prod and staging); then each user listed was put through §2.12 → §2.2 → §2.11 | `db.assets.aggregate([{ $match: { corporate_action: "BONUS", $or: [{ broker_charges: { $gt: 0 } }, { misc_charges: { $gt: 0 } }, { allocated_buy_charges: { $gt: 0 } }, { allocated_buy_misc_charges: { $gt: 0 } }] } }, { $group: { _id: "$email", bonus_lots_with_charges: { $sum: 1 } } }])` — one row per user (stored email, §0.7), owner, `mongosh`, any time; read-only; safe to run again; nothing to undo |
| 3.18 | **W-292** (D-591 C2a) | A transaction and a lot now record the segment the trade was entered in. Until W-292 neither did (`AssetRequest.asTransaction`, `asAsset`), so every stored one says `DELIVERY`, and an outcome row's `buy_segment` (taken from the lot) and a §2.2 rebuild's venues (taken from the transactions) say it too. The trade's `user_charges` row was priced under the request's segment (V2 always, V1 since W-280), so it knows better. The count lists users with transactions whose charge row says another segment. Reported here; §2.14 (W-297) corrected it, finding nothing on prod or staging (retired by W-299, D-891) | `db.transactions.aggregate([{ $lookup: { from: "user_charges", let: { id: { $toString: "$_id" } }, pipeline: [{ $match: { $expr: { $eq: ["$transaction_id", "$$id"] } } }], as: "charge" } }, { $unwind: "$charge" }, { $match: { $expr: { $ne: ["$segment", "$charge.segment"] } } }, { $group: { _id: "$email", segment_differs: { $sum: 1 } } }])` — one row per user (stored email, §0.7), owner, `mongosh`, any time; read-only; safe to run again; nothing to undo |
| 3.19 | **W-295** (D-595 C3a) | An outcome row now records the segment of its sale whichever flow wrote it. Until W-295 only V2 did: a V1 row and a row rebuilt by §2.2 held `sell_segment` but no `segment`, and §2.11 files a row under `segment`, reading an absent one as delivery. So a §2.11 rebuild moved such a sale out of intraday (live P&L was right). The count lists users with rows whose `sell_segment` is not `DELIVERY` and that have no `segment`; those are the rows filed wrongly. Reported here; §2.14 (W-297) corrected it, finding nothing on prod or staging (retired by W-299, D-891). **Until §2.14 has run, do not run §2.11 for a user this lists** | `db.trade_outcomes.aggregate([{ $match: { segment: null, sell_segment: { $nin: [null, "DELIVERY"] } } }, { $group: { _id: "$email", rows_filed_as_delivery: { $sum: 1 } } }])` — one row per user (stored email, §0.7), owner, `mongosh`, any time; read-only; safe to run again; nothing to undo |
| 3.20 | **W-303** (D-619 C4a) | A fund redeemed inside its exit-load window is now charged the load on its redemption value (the lot's units at the sale price). Until W-303 the engine took a lot's turnover at its **buy** price, so a stored `EXIT_LOAD` on a sale above cost is too low and one below cost too high; the row keeps the sale's `turnover` and each line's `base_amount` (the lot's cost) but not the lots, so it cannot be recomputed from the row alone. The count lists users with rows carrying an exit load; a non-zero count gets a correction row with its own plan (no recompute route exists, CE-4 / W-308) | the 3.20 query below — one row per user (stored email, §0.7), owner, SQL (`PSQL`, §2.19), after deploy, on each database; read-only; safe to run again; nothing to undo; log the count in the run log |
| 3.21 | **W-304** (D-623 C4a) | A card is now chosen for a trade only if one of its active rules declares the trade's event (BUY, SELL, AMC_CYCLE). Until W-304 an unscoped card with no rule for the event, such as a broker's maintenance card (AMC_CYCLE only) or the statutory account card, could stand in for a missing trade card. The trade was then stored as `RESOLVED`, or `NO_MATCHING_RULES`, with the missing card's charges silently absent and not listed by `/gaps`. With the shipped cards there is no such hole from 1 Apr 2025, so every count should be 0; a database's own cards may differ. The count lists, per database, the BUY/SELL rows whose `schedule_code` or `statutory_schedule_code` names a card with no active rule for the row's event. A non-zero count gets a correction row with its own plan (no recompute route exists, CE-4 / W-308) | the 3.21 query below — one row, owner, SQL (`PSQL`, §2.19), after deploy, on each database; read-only; safe to run again; nothing to undo. Tried on a throwaway `mongo:8.0.4` with hand-made rows: 4 trade rows, 2 broker (a maintenance card on a sale, a card whose only rule is switched off), 1 statutory (the account card on a buy), as worked by hand; a database without `user_charges` is skipped. Log the three counts in the run log |
| 3.22 | **W-313** (D-637 C2a) | A demerger's share ratio is now read **new:held**, as the market writes it (ITC's "1:10" = one ITC Hotels share for every ten ITC). Until W-313 the second number was taken as new shares per share held, so a demerger other than `1:1` allotted the wrong quantity and cost (ITC: 10,000 shares and ₹5,40,400 instead of 100 and ₹54,040). The query lists, per database, every stored demerger whose ratio is not `1:1` and the users holding lots it created. **Check each listed demerger's ratio against its announcement first**: one entered in the old reading is deleted (`DELETE /api/v1/admin/corporate-actions/{id}`) and added again with the right ratio (`POST /api/v1/admin/corporate-actions`), both `SUPER_USER`, before any rebuild. Then rebuild each listed user with §2.12, which replays demergers through the fixed code | the 3.22 query below — owner, SQL (`PSQL`, §2.19), after deploy, on each database; read-only; safe to run again; nothing to undo. Tried on a throwaway `mongo:8.0.4`: a `1:10` and a `1:1` demerger, lots from each → the `1:10` one only, with its two users, as worked by hand. Log the lists in the run log; each §2.12 run is logged as §2.12 **Corrected 2026-11-20 (W-334c5 plan):** the query named the collection `corporate_actions`; the entity's is `corporate_action` (`CorporateActionEntity.java:19`), so it skipped every real database and reported nothing — the throwaway trial planted the plural name |
| 3.23 | **W-312** (D-638 C3a) | A bonus is now allotted per account holder. Until W-312 it pooled every holder at the broker into one bonus lot **and one bonus transaction** for the first holder. The transaction is the record, so §2.12 cannot repair it. The query lists, per database, each bonus transaction whose stock, broker and user had buys (not held `TEMPORARY`) from more than one holder before it; a non-empty list gets a correction row with its own plan | the 3.23 query below — owner, SQL (`PSQL`, §2.19), after deploy, on each database; read-only; safe to run again; nothing to undo. Tried on a throwaway `mongo:8.0.4`: a pooled bonus, a holder buying after the bonus, and a held trade → the pooled one only, as worked by hand. Log the list in the run log |
| 3.24 | **W-316** (D-640) | A rights issue or buyback no longer holds a trade: the app records them but cannot apply them, so until W-316 a trade on such a stock after its record date was held `TEMPORARY` for good, and the quarter's run stopped at the action with 400. Trades already held that way are recorded by a redrive. The query lists, per database, the users and stocks with `TEMPORARY` trades on a stock that has a rights issue or buyback. **Then**, for each user listed: `curl -sS -X POST "$BASE/api/v1/me/portfolio/temporary-transactions/redrive" -H "Authorization: Bearer $ADMIN_TOKEN"` (`SUPER_USER`). Redrive is the normal route and safe to run again; a trade still held by a bonus, split or demerger stays held | the 3.24 query below — owner, SQL (`PSQL`, §2.19), after deploy, on each database; read-only; safe to run again; nothing to undo. Tried on a throwaway `mongo:8.0.4`: held trades on a rights-issue and a buyback stock, one held by a bonus, one processed → the first two only, as worked by hand. Log the list and each redrive's result in the run log **Corrected 2026-11-20 (W-334c5 plan):** the query named the collection `corporate_actions`; the entity's is `corporate_action` (`CorporateActionEntity.java:19`), so it skipped every real database and reported nothing — the throwaway trial planted the plural name |
| 3.25 | **W-342** (D-798 C1a) · **check retired by W-337j3c (D-1025) · do not run** | The startup rebuild once put a lot's whole buy charge on each later sale of it (W-088u5); §2.1 was to correct those rows by replaying the transactions, but the replay orders same-day sales by date only, so it pairs neither prod user and corrects nothing. **Accepted, not corrected:** on prod (rehearsed on its copy, 2026-11-20) the rows on lots sold more than once carry ₹68.99 of buy charges where ₹53.00 is the lot's own shared by quantity — **₹15.99 over** on nine lots, so those sales' cost is overstated and their gain understated by that much. Reported per lot with no email **After the rebuild (W-348b, D-824, D-836):** over 0, carried ₹53.00 (= 68.99 − 15.99) — the over-charge is gone | Retired (D-1025 C3b): the MongoDB script it named (`~/wealthlens-runs/w334c/c4-w342-overcharge.js`) was never in the repository and is no longer kept, and its condition read 0 after the rebuild. The record: prod 2026-11-20, 12 lots, 29 rows, carried 68.99, over 15.99; after W-348b over 0, carried 53.00 |
| 3.26 | **W-344** (D-799 C1a) | A stock code is matched exactly everywhere — lots, sales, corporate actions, rate cards — so one stored with a leading or trailing space (`MOTHERSON `, seen on prod's copy by W-342 row 3) is a different stock from the clean one: its sales cannot reach the clean code's lots, and a corporate action entered under the clean code never reaches it. Reported, not corrected: a correction is its own row, placed once each database is sized. **Prod (rehearsed on its copy, 2026-11-20):** one code, `"MOTHERSON "` — one user's closed position (3 transactions, 2 outcome rows, no lot, no clean `MOTHERSON` anywhere): nothing split, **not corrected** (D-799 C1a). New entries are refused from W-345 | the 3.26 query below — one row per table and column (`stock_code`, `to_stock_code`) with the count and the codes as stored (quoted, so the space shows); owner, SQL (`PSQL`, §2.19), on each database; read-only; safe to run again; nothing to undo. Tried on a throwaway `mongo:8.0.4`: two `MOTHERSON ` and one ` TCS` in `transactions`, an `ITCHOTELS` + tab in `corporate_action.to_stock_code` → 3 and 1, as worked by hand; an inner space (`A B`), `null` and a number not counted. Log the rows in the run log **On PostgreSQL (2026-11-28, W-337j3c):** prod `MOTHERSON ` only (3 transactions, 2 outcome rows), staging and dev none. |
| 3.27 | **W-353** (D-826, D-828, D-829, D-830) | A trade must now name an exchange its asset type lists in `app.portfolio.trade-exchanges` (EQUITY / MUTUAL_FUND `NSE` or `BSE`; BOND / GOLD_BOND also `OFF_EXCHANGE`; FD / INSURANCE `OFF_EXCHANGE` only), exactly as written, and an asset type; anything else is refused with a 400 on V1, V2, the upload and redrive. **Stored trades are not changed** (`outside`, informational): on prod (owner's count, 2026-11-20) 183 — 178 `MUTUAL_FUND`/`BMF` and 5 with no asset type, test entries removed by §2.16 and §2.17 (W-354), so 178 after the prod run. A **held** trade (`status: TEMPORARY`) is checked again at redrive from its stored request, so one outside the table now fails its redrive item (`held`, expected 0; a non-zero count is reported to the owner, not corrected) | the 3.27 query below — one row; owner, SQL (`PSQL`, §2.19), on each database, after the deploy that carries W-353; read-only; safe to run again; nothing to undo. The lists in it copy the yaml's; if the yaml changes, change them too. Tried on a throwaway `mongo:8.0.4`: 12 trades (`nse`, `NSE `, `BMF`, none, FD/`NSE`, a GOLD_BOND with no exchange, a held `BMF`, a processed one whose request says `bse`, and four listed) → outside 7, held 1, as worked by hand. Log the rows in the run log **On PostgreSQL (2026-11-28, W-337j3c):** prod outside 178, held 0; staging 99, 1; dev 0, 0. |

Every query here only reads. Run them through `PSQL` (§2.19), in one go with `-f` or one at a time; a session opened
`READ ONLY` (first line) refuses any write. Proven on a throwaway `postgres:18` with rows planted for each check, including
missing and null values, then on dev, staging and prod (W-337j3c, D-1024; run log 2026-11-28).

```sql
SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY;
SET search_path = wealthlens;
-- 3.1 — disposals with broker charges and no breakdown: users, rows
SELECT count(*) AS users, COALESCE(sum(rows), 0) AS rows FROM (SELECT email, count(*) AS rows FROM trade_outcomes
  WHERE (buy_broker_charges IS DISTINCT FROM 0 AND COALESCE(NULLIF(buy_charge_breakup_json, 'null')::jsonb, '{}'::jsonb) = '{}'::jsonb)
     OR (sell_broker_charges IS DISTINCT FROM 0 AND COALESCE(NULLIF(sell_charge_breakup_json, 'null')::jsonb, '{}'::jsonb) = '{}'::jsonb) GROUP BY email) u;
-- 3.2 — rows without an acquisition mode
SELECT count(*) AS rows FROM trade_outcomes WHERE acquisition_mode IS NULL;
-- 3.3 — equity disposals the engine still refuses for STT (an upper bound): users, rows
SELECT count(*) AS users, COALESCE(sum(rows), 0) AS rows FROM (SELECT email, count(*) AS rows FROM trade_outcomes
  WHERE asset_type = 'EQUITY' AND (
    (COALESCE(NULLIF(sell_charge_breakup_json, 'null')::jsonb, '{}'::jsonb) = '{}'::jsonb AND COALESCE(sell_broker_charges, 0) = 0
      AND (COALESCE(sell_exchange_name, '') NOT IN ('NSE', 'BSE') OR COALESCE(sell_segment, '') NOT IN ('DELIVERY', 'INTRADAY')))
    OR (COALESCE(acquisition_corporate_action, '') NOT IN ('BONUS', 'DEMERGER') AND COALESCE(NULLIF(buy_charge_breakup_json, 'null')::jsonb, '{}'::jsonb) = '{}'::jsonb
      AND COALESCE(buy_broker_charges, 0) = 0
      AND (acquisition_mode IS DISTINCT FROM 'PURCHASE' OR COALESCE(buy_exchange_name, '') NOT IN ('NSE', 'BSE')
           OR buy_segment IS DISTINCT FROM 'DELIVERY'))) GROUP BY email) u;
-- 3.4 — rows without a sale venue; trades by exchange and segment
SELECT count(*) AS rows_without_sell_exchange FROM trade_outcomes WHERE sell_exchange_name IS NULL;
SELECT exchange_name, segment, count(*) AS n FROM transactions GROUP BY exchange_name, segment ORDER BY exchange_name, segment;
-- 3.5 — equity disposals whose sale is not recorded on NSE or BSE: users, rows
SELECT count(*) AS users, COALESCE(sum(rows), 0) AS rows FROM (SELECT email, count(*) AS rows FROM trade_outcomes
  WHERE asset_type = 'EQUITY' AND COALESCE(sell_exchange_name, '') NOT IN ('NSE', 'BSE') GROUP BY email) u;
-- 3.6 — bonus actions without an allotment date
SELECT stock_code, record_date, ex_date FROM corporate_action WHERE type = 'BONUS' AND allotment_date IS NULL ORDER BY record_date, stock_code;
-- 3.7 — land or building rows
SELECT (SELECT count(*) FROM transactions WHERE asset_type = 'LAND_OR_BUILDING') AS transactions,
  (SELECT count(*) FROM assets WHERE asset_type = 'LAND_OR_BUILDING') AS assets,
  (SELECT count(*) FROM trade_outcomes WHERE asset_type = 'LAND_OR_BUILDING') AS trade_outcomes;
-- 3.8 — nil-charge lots and sales whose transaction entered a charge
SELECT (SELECT count(*) FROM assets a WHERE a.broker_charges = 0 AND jsonb_array_length(COALESCE(NULLIF(a.buy_transaction_ids_json, 'null')::jsonb, '[]'::jsonb)) > 0
    AND EXISTS (SELECT 1 FROM transactions t WHERE t.broker_charges > 0
      AND t.id IN (SELECT jsonb_array_elements_text(COALESCE(NULLIF(a.buy_transaction_ids_json, 'null')::jsonb, '[]'::jsonb))))) AS lots,
  (SELECT count(*) FROM trade_outcomes o WHERE o.sell_broker_charges = 0 AND COALESCE(NULLIF(o.sell_charge_breakup_json, 'null')::jsonb, '{}'::jsonb) = '{}'::jsonb
    AND o.source_sell_transaction_id IS NOT NULL
    AND EXISTS (SELECT 1 FROM transactions t WHERE t.id = o.source_sell_transaction_id AND t.broker_charges > 0)) AS sales;
-- 3.9 — splits performed; stored split ratios
SELECT count(*) AS performed FROM lastly_performed_corporate_action WHERE action_type = 'STOCK_SPLIT';
SELECT DISTINCT ratio FROM corporate_action WHERE type = 'STOCK_SPLIT' ORDER BY ratio;
-- 3.10 — rows without an asset type
SELECT (SELECT count(*) FROM assets WHERE asset_type IS NULL) AS assets, (SELECT count(*) FROM transactions WHERE asset_type IS NULL) AS transactions;
-- 3.11 — held trades without a flow
SELECT count(*) AS held_without_flow FROM transactions WHERE status = 'TEMPORARY' AND trade_flow IS NULL;
-- 3.12 — rows bought after their sale
SELECT count(*) AS rows FROM trade_outcomes WHERE buy_date > sell_date;
-- 3.13 — lots holding a float remainder
SELECT count(*) AS lots FROM assets WHERE quantity > 0 AND quantity < 1e-9;
-- 3.14 — merged unsold lots whose order times cover less than their quantity
SELECT count(*) AS lots FROM assets a WHERE jsonb_array_length(COALESCE(NULLIF(a.buy_transaction_ids_json, 'null')::jsonb, '[]'::jsonb)) >= 2
  AND jsonb_array_length(COALESCE(NULLIF(a.sell_transaction_ids_json, 'null')::jsonb, '[]'::jsonb)) = 0
  AND COALESCE((SELECT sum((q ->> 'quantity')::float8) FROM jsonb_array_elements(COALESCE(NULLIF(a.order_time_quantities_json, 'null')::jsonb, '[]'::jsonb)) q), 0) < a.quantity;
-- 3.15 — intraday buys priced as delivery
SELECT count(*) AS priced_as_delivery FROM transactions t WHERE t.transaction_type = 'BUY' AND t.segment = 'INTRADAY'
  AND EXISTS (SELECT 1 FROM user_charges c WHERE c.transaction_id = t.id AND c.segment = 'DELIVERY');
-- 3.16 — sales after an earlier quarter split, bonus or demerger, by users who held before it (per stored email)
SELECT s.email, count(*) AS sales FROM corporate_action a JOIN transactions s ON s.stock_code = a.stock_code
  AND s.transaction_type = 'SELL' AND s.transaction_date > a.record_date
  WHERE a.type IN ('STOCK_SPLIT', 'BONUS', 'DEMERGER') AND EXISTS (SELECT 1 FROM transactions b WHERE b.stock_code = a.stock_code
    AND b.email = s.email AND b.broker_name IS NOT DISTINCT FROM s.broker_name AND b.transaction_type = 'BUY'
    AND b.transaction_date < a.record_date)
  GROUP BY s.email ORDER BY s.email;
-- 3.20 — users with exit-load rows
SELECT c.email, count(*) AS exit_load_rows FROM user_charges c JOIN user_charge_amounts m ON m.user_charge_id = c.id
  WHERE m.charge_code = 'EXIT_LOAD' AND m.amount > 0 GROUP BY c.email ORDER BY c.email;
-- 3.21 — trade rows whose card has no active rule for the event
SELECT count(*) AS trade_rows,
  count(*) FILTER (WHERE NOT (NOT EXISTS (SELECT 1 FROM charge_schedules s WHERE s.schedule_code = c.schedule_code)
    OR EXISTS (SELECT 1 FROM charge_schedules s, jsonb_array_elements(COALESCE(NULLIF(s.rules_json, 'null')::jsonb, '[]'::jsonb)) r WHERE s.schedule_code = c.schedule_code
      AND (r ->> 'active')::boolean IS TRUE AND COALESCE(r -> 'events', '[]'::jsonb) ? c.event))) AS broker_card_without_the_event,
  count(*) FILTER (WHERE NOT (NOT EXISTS (SELECT 1 FROM charge_schedules s WHERE s.schedule_code = c.statutory_schedule_code)
    OR EXISTS (SELECT 1 FROM charge_schedules s, jsonb_array_elements(COALESCE(NULLIF(s.rules_json, 'null')::jsonb, '[]'::jsonb)) r WHERE s.schedule_code = c.statutory_schedule_code
      AND (r ->> 'active')::boolean IS TRUE AND COALESCE(r -> 'events', '[]'::jsonb) ? c.event))) AS statutory_card_without_the_event
  FROM user_charges c WHERE c.event IN ('BUY', 'SELL');
-- 3.22 — demergers not one for one, and the users holding lots they created
SELECT a.stock_code AS stock, a.demerger_ratio AS ratio, a.record_date, array_agg(d.stock_code ORDER BY d.stock_code) AS creates
  FROM corporate_action a LEFT JOIN corporate_action_demerger_stocks d ON d.corporate_action_id = a.id
  WHERE a.type = 'DEMERGER' AND a.demerger_ratio IS DISTINCT FROM '1:1' GROUP BY a.id, a.stock_code, a.demerger_ratio, a.record_date ORDER BY a.record_date;
SELECT DISTINCT x.email AS user_to_rebuild FROM assets x WHERE x.corporate_action = 'DEMERGER' AND x.stock_code IN (SELECT d.stock_code
  FROM corporate_action a JOIN corporate_action_demerger_stocks d ON d.corporate_action_id = a.id
  WHERE a.type = 'DEMERGER' AND a.demerger_ratio IS DISTINCT FROM '1:1') ORDER BY 1;
-- 3.23 — bonuses pooled over more than one holder
SELECT t.email, t.stock_code, t.broker_name, t.transaction_date AS allotted_on, t.quantity, h.holders FROM transactions t
  CROSS JOIN LATERAL (SELECT count(DISTINCT COALESCE(b.account_type, '-') || '|' || COALESCE(b.account_holder, '-')) AS holders
    FROM transactions b WHERE b.email = t.email AND b.stock_code = t.stock_code AND b.broker_name IS NOT DISTINCT FROM t.broker_name
      AND b.transaction_date < t.transaction_date AND b.transaction_type = 'BUY'
      AND b.corporate_action IS DISTINCT FROM 'BONUS' AND b.status IS DISTINCT FROM 'TEMPORARY') h
  WHERE t.corporate_action = 'BONUS' AND h.holders > 1 ORDER BY t.transaction_date;
-- 3.24 — stocks with a rights issue or buyback; trades held on them
SELECT DISTINCT stock_code AS stocks_with_rights_or_buyback FROM corporate_action WHERE type IN ('RIGHTS_ISSUANCE', 'BUYBACK') ORDER BY 1;
SELECT email, stock_code AS stock, count(*) AS held_trades FROM transactions WHERE status = 'TEMPORARY'
  AND stock_code IN (SELECT stock_code FROM corporate_action WHERE type IN ('RIGHTS_ISSUANCE', 'BUYBACK')) GROUP BY email, stock_code;
-- 3.26 — stock codes with a leading or trailing space, per table and column
SELECT 'SELECT * FROM (' || string_agg(format('SELECT %L AS table_name, %L AS column_name, count(*) AS rows, array_agg(DISTINCT quote_literal(%I)) AS codes FROM wealthlens.%I WHERE %I ~ %L',
  table_name, column_name, column_name, table_name, column_name, '^\s|\s$'), ' UNION ALL ') || ') x WHERE rows > 0'
  FROM information_schema.columns WHERE table_schema = 'wealthlens' AND column_name IN ('stock_code', 'to_stock_code') \gexec
-- 3.27 — trades outside the exchange table, and held ones
SELECT (SELECT count(*) FROM transactions t WHERE NOT EXISTS (SELECT 1 FROM (VALUES ('EQUITY', 'NSE'), ('EQUITY', 'BSE'), ('MUTUAL_FUND', 'NSE'),
    ('MUTUAL_FUND', 'BSE'), ('BOND', 'NSE'), ('BOND', 'BSE'), ('BOND', 'OFF_EXCHANGE'), ('GOLD_BOND', 'NSE'), ('GOLD_BOND', 'BSE'),
    ('GOLD_BOND', 'OFF_EXCHANGE'), ('FD', 'OFF_EXCHANGE'), ('INSURANCE', 'OFF_EXCHANGE')) v(k, e) WHERE v.k = t.asset_type AND v.e = t.exchange_name)) AS outside,
  (SELECT count(*) FROM transactions t WHERE t.status = 'TEMPORARY' AND NOT EXISTS (SELECT 1 FROM (VALUES ('EQUITY', 'NSE'), ('EQUITY', 'BSE'),
    ('MUTUAL_FUND', 'NSE'), ('MUTUAL_FUND', 'BSE'), ('BOND', 'NSE'), ('BOND', 'BSE'), ('BOND', 'OFF_EXCHANGE'), ('GOLD_BOND', 'NSE'),
    ('GOLD_BOND', 'BSE'), ('GOLD_BOND', 'OFF_EXCHANGE'), ('FD', 'OFF_EXCHANGE'), ('INSURANCE', 'OFF_EXCHANGE')) v(k, e)
    WHERE v.k = NULLIF(t.asset_request_json, 'null')::jsonb ->> 'assetType' AND v.e = NULLIF(t.asset_request_json, 'null')::jsonb ->> 'exchangeName')) AS held;
```

---

## 4. Items with no deploy action — and how the past was checked

Every other tax-planning item changes only code or the rule data shipped in the JAR (`rulepacks/`): the rules,
nodes, tax years, catalogue and coverage, W-001 – W-088u5. Those reach a database only through the startup checks
in section 1. `tax_year_registry` has an entity and a repository, but no code reads or writes it, so it needs
nothing.
**W-139 (D-452)** adds a build-time pack review only (`PackReviewTest`, `reviews/pack-review-*.md`): nothing at
runtime, nothing in a database.

**The audit behind this list** (D-279, 2026-10-20). Every commit whose subject starts `taxplanning` was read for
files outside `taxplanning`, `taxengine`, `taxrules` (`taxengine.rules`' former home) and `rulepacks`:
- Each one is in `portfolio` or `shared`: the capital-gains read-model, the trade-outcome writers, the rebuild and
  its correction, and the FY runner. Sections 1 – 3 cover all of them.
- No such commit added an index. W-086's query by email and sell date uses the existing `trade_outcome_user_idx`.
- No such commit changed a `taxplanning` entity or `data/tax-policies/`. Their last changes are the module's own
  PRs (#58, #59), before this build, so nothing in §2.0's files needs a correction.

**The trade ledger's, with no step left:**
- **Trades dated 31 March.** Three financial-year derivations filed a trade made **on** 31 March into the next year;
  fixed in code. `db.transactions.countDocuments({ transaction_date: /-03-31$/ })` returned `0` on prod on
  2026-09-12 (run log; MongoDB, as run then), so no stored data needed correcting. On PostgreSQL:
  `SELECT count(*) FROM wealthlens.transactions WHERE to_char(transaction_date, 'MM-DD') = '03-31';` (`PSQL`, §2.19) —
  2026-11-28: prod 2, staging 0. The count was 0 on 2026-09-12, after the fix, so both rows were stored since then, under the
  fixed derivations; nothing to correct.
- **Additive fields.** `transactions.idempotency_key`, `trade_fingerprint` and `submitted_at` are absent on old rows;
  both indexes are sparse, so old rows are simply not indexed. The trade ledger's other added fields were since
  removed by tax-planning items, and their stored copies are §2.5, §2.6, §2.7 and §2.9.
- **A response rename, not a database change.** `ReportModelResponse` is a DTO, never stored. Its keys changed:
  `purchasePrice` → `purchaseAmount`, `sellPrice` → `sellAmount`, `brokerCharges` → `brokerage`. The old keys always
  returned `0.0`, but a client reading them must move to the new names.

---

## 5. Standing operations — the charge engine (5.1 – 5.6) and a personal data breach (5.7)

Not deploy steps. Each is a deliberate act, when needed; nothing here is scheduled.

### 5.1 Register demat accounts · the user · **only if AMC is wanted**

AMC is **not** billed for an account that is not registered: with no account there is nothing to bill, and nothing
says so.

```bash
curl -sS -X POST "$BASE/api/v1/me/charges/charge-accounts" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "brokerName":"ZERODHA","dematAccountId":"1208160000000001","accountHolder":"self",
    "amcFrequency":"ANNUALLY","openedOn":"2024-01-01"
  }'
```

### 5.2 Recurring operations · `SUPER_USER`

| Operation | Command | Cadence | If it is never run |
|---|---|---|---|
| **AMC cycle** | `POST /api/v1/admin/charges/amc/impose` | quarterly / annually, per account frequency | No maintenance charge is billed. Idempotent per account and period: running it twice bills once |
| **Rate verification** | `GET /api/v1/me/charges/charge-schedules/unverified` | every 90 days | Cards age silently. The endpoint lists anything older than 90 days; that is the reminder (ADR-26) |
| **Drift check** | `GET /api/v1/admin/charges/charge-schedules/drift` | every deploy | A card edited in the repository never reaches a database that already seeded it: the seeder skips it. Drift makes that visible |
| **Tax jobs** (W-336, D-783) | `curl -s -X POST "$BASE/api/v1/admin/tax/jobs/<name>/run" -H "Authorization: Bearer $ADMIN_TOKEN" \| jq '.'` | `account-erasure`, `log-retention`, `auto-archive` weekly; `retention-purge` monthly; `staleness-sweep` after every start of a new build; `gazette-watch` only once §1.w turns it on; `parse-documents` after an upload, while documents are on | A held account, a year-old log or an expired draft stays stored until the next run: while the app is local the duties run on this cadence. `ran: false` means another run holds the lease: run again later. A second run finds nothing new. Log each run |
| **Publishing a rate change** | `POST /api/v1/admin/charges/charge-schedules` | when a broker changes rates | **Never edit a deployed card** (ADR-26). Publish a successor; it supersedes the incumbent in the same transaction. How publishing supersedes: `docs/modules/brokercharges.md` §5 |

### 5.3 History before the engine is not priced

Trades made before the engine priced them carry no computed charge, and **there is no way to give them one**. ADR-32
applies the cutover forward only; the endpoint that could re-price history was removed for that reason. A charge
report over an earlier period is sparse, which is correct. Pricing history, if wanted, is the
work of W-383, and needs historical rate cards first.

### 5.4 If something goes wrong

**Stop the engine without a deploy:** `app.charges.engine-enabled: false`. Simulate and the AMC cycle answer **503**,
and nothing is recorded. Reads keep working (charge history, gaps, the reconciliation report), which is what
you need to decide whether to turn it back on.

**Stop only cost-basis changes**, keeping the engine recording for comparison: `app.charges.authoritative: false`. New
buys go back to the client-supplied `brokerCharges`. Trades already written keep the cost basis they were given;
nothing rewrites a stored `AssetEntity`.

| Symptom | Where to look |
|---|---|
| Charges are zero | `GET /api/v1/admin/charges/user-charges/users/{email}/gaps`: it names the reason per trade |
| Startup warned about rate cards | §1B; grep the log for `NO RATE CARDS` |
| A charge looks wrong | `GET /api/v1/admin/charges/user-charges/users/{email}/transaction/{id}`: the contract note, line by line, with the `scheduleCode` that priced it |
| Figures disagree with the client's | `GET /api/v1/admin/charges/user-charges/users/{email}/reconciliation` |

### 5.5 Known limits — recorded, not defects

Full list in `docs/modules/brokercharges.md` §8.
- **A database with no cards prices nothing until §1B runs.**
- **Nothing before 2025-04-01 can be priced:** no card covers it.
- **Only two mutual-fund schemes have exit-load profiles.** Every other fund resolves `NO_INSTRUMENT_PROFILE` and
  accrues no exit load; recorded, never fatal (ADR-24).
- **A rate card written directly to the database is invisible** until the resolver cache is evicted. `POST
  /api/v1/admin/charges/charge-schedules`, the close endpoint and `POST /api/v1/admin/charges/seed` evict it. Go through the API.
- **Scheme profiles have no publishing endpoint:** they arrive by seeding, and a profile added by hand needs a
  restart or a seed run.
- **No `POST /charges/recompute`.** Amending a card that has already priced charges has no safe mechanism; designed in
  tech-spec §14.4, not built.
- **V1 `buyStock` / `sellStock` do not use the engine.** They are in live use and deliberately untouched. Only the V2
  endpoints (`/api/v1/me/portfolio/transaction/v2`) are priced.

### 5.6 The trade idempotency window (TL-7)

`app.portfolio.idempotency.window-seconds`, default 60. Two identical trades submitted inside it are collapsed into
one. A bulk import that must submit identical rows sends an `idempotencyKey` per row: a supplied key is exact and has
no window. Setting the window to 0 disables the derived-fingerprint fallback and leaves client keys working.

### 5.7 W-237c — on a personal data breach · owner, `SUPER_USER` · **whenever the owner becomes aware of one**

A breach is *"any unauthorised processing of personal data or accidental disclosure, acquisition, sharing, use,
alteration, destruction or loss of access to personal data"* that compromises its confidentiality, integrity or
availability (DPDP Act, 2023 s.2(u)). The clock starts **when you become aware** (Rules r.7). Write that time down
first. Steps, in order (`$BASE`, `$TOKEN` as a `SUPER_USER`; times UTC, `yyyy-MM-ddTHH:mm:ss`):

1. **Without delay: post the notice** to each affected user. It covers r.7(1)(a) – (d), in plain words; the app adds
   the privacy contact, (e). `scope` is `EVERY_REGISTERED_USER` or `LISTED` with `emails`:

   ```bash
   curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" "$BASE/api/v1/admin/privacy/breach-notices" \
     -d '{"text": {"description": "<what, how much, when>", "consequences": "<what may follow for them>",
          "mitigation": "<done and being done>", "safetyMeasures": "<what they can do>"},
          "becameAwareAt": "<UTC>", "scope": "EVERY_REGISTERED_USER"}'
   ```

   `201` with `id`, `affected` and `board.reportDueAt` (awareness + `app.privacy.breach.board-report-hours`, 72).
2. **Without delay: tell the Board** the description, nature, extent, timing, location and likely impact (r.7(2)(a)),
   through **the channel the Board publishes** (not stated in the Gazette copies read for W-237c; owner to fill in
   here). Then record it: `POST $BASE/api/v1/admin/privacy/breach-notices/<id>/board-intimated` → `board.firstIntimatedAt`.
3. **Without delay: email each affected user** the same text from the privacy contact's mailbox (the account email is
   the only mode of communication users register). The text and addresses: `GET $BASE/api/v1/admin/privacy/breach-notices/<id>/report`.
   Record who was emailed: `POST …/<id>/emailed` with `{"emails": [...]}`. A user already recorded keeps their first
   time, so a repeat changes nothing.
4. **Within 72 hours of awareness, or by the later time the Board allows on a written request:** send the Board the
   detailed report, r.7(2)(b)(i) – (vi). Item (vi), the intimations given, is `GET …/<id>/report` (per user: posted,
   read in the account, emailed). If the Board allows longer: `POST …/<id>/board-extension` with
   `{"reportDueAt": "<UTC>", "reference": "<the Board's reference>"}`. When sent: `POST …/<id>/board-report-sent`.

**Read-only check, at any time:** `GET …/<id>/report`: `board.firstIntimatedAt` and `board.reportSentAt` set, the
latter before `board.reportDueAt`; every affected user with `emailedAt`. **Second runs:** each recording call refuses
a repeat (`400`) except `emailed`, which keeps first times. **Way back:** none needed: the calls record what was done
and change no user data. A notice posted in error stays on record; post a correcting notice.

---

## Run log — one line per run, per database

The run log up to 2026-11-21 — every row of W-334 (prod, staging and dev, D-788 – D-849) and the earlier runs — was removed by
W-357a (D-852); it is in git tag `docs-history-m1` (`git show docs-history-m1:docs/taxplanning-build/post-deployment-runbook.md`),
and each run's outcome is in its D-row in `DECISIONS.md`. **As of that date:** prod `Cluster0` and staging `it-staging` are on `m1.4`,
every step of §0 – §3 run or recorded as not needed for each (D-845 – D-849, D-840 – D-843); dev is empty (D-837). New runs append below.

| Date | Database | Step | Run by | Result |
|---|---|---|---|---|
| 2026-11-21 | — (W-362a, D-868) | row 1, preconditions | owner | `master` 2229df87; no `m1.5` tag; PR #70 `OPEN false 356c8e01 MERGEABLE`, first `UNSTABLE` while run 44920213218 ran, then `CLEAN`; run 44920213218 `success` on 356c8e01 — **go** |
| 2026-11-21 | — (W-362a, D-868) | row 2, PR #70 merged as a merge commit | owner | `M` = e832b56a, parents 2229df87 and 356c8e01; `git diff --stat 356c8e01 origin/master` empty — **go** |
| 2026-11-21 | — (W-362a, D-868) | row 3, the `master` pipeline | session | run 44920860461 (`push`, Build and Push Docker Image) `success` on e832b56a; image tags `wealthlens:latest`, `wealthlens:sha-e832b56a5d0a` — **go** |
| 2026-11-21 | — (W-362a, D-868) | row 4, `m1.5` tagged, worktree and jar | owner | tag `m1.5` → e832b56a pushed; `../wealthlens-m1.5` at e832b56a; `wealthlens.jar` 91,407,010 bytes (Spotless's `sun.misc.Unsafe` notices only) — **go** |
| 2026-11-21 | — (W-362a, D-868) | row 5, WL-A-2 published from `m1.5`'s runbook | session | `{"idReadable":"WL-A-2",…}`; read back: 198,836 characters holding the file's 198,654 after the source-of-truth header — **go**; W-362a done |
| 2026-11-21 | staging (W-362b, D-868) | row 1, environment | owner | keys 32 bytes, prefixes bbf4a26647f6 / c59064f277a6 / 5524e829976b; PRIVACY_CONTACT 25; owner email first empty (re-entered: 25); app password set; jobs off; port 8080 free; jars 91,407,010 (`m1.5`) and 87,816,823 (`m1.4`) — **go** |
| 2026-11-21 | staging (W-362b, D-868) | row 2, before (read-only, IntelliJ console) | owner | `it-staging`: users 14, consents 1, `refresh_tokens` absent, billedWithoutEvent 0 (W-206 check) — **go** |
| 2026-11-21 | staging (W-362b, D-868) | row 3, `m1.5` started (staging profile) | owner | `Started` in 7.458 s; `Ensured the indexes of` UserDetail, RefreshToken, PrivacyConsent; no ERROR; WARN: the malware scan is off (staging yaml `scan.enabled: false`, W-131h, D-769) and SpringDoc's "endpoint is enabled by default" (D-867 C1a: the spec in every profile, admin only) — both expected; m1.4's `AuthenticationProvider` notice gone with W-358b — **go** |
| 2026-11-21 | staging (W-362b, D-868) | row 4, routes and a sign-in | owner | rights 200; notice version 6f36c92105ff376fa931f28523cdaddcd1fa80ceea7322b9fc67a6efa3638f44 (new); old rights path 401; expires_in 900, refresh token 43 chars; my stocks 200; basic auth 401; spec paths 136; refreshed 900, rotated yes; logout 204 — **go** |
| 2026-11-21 | prod (W-362b row 5 run on the wrong console) | read-only, not a W-362c row | owner | `Cluster0`: users 12, consents 1, `refresh_tokens` absent, billedWithoutEvent 0 — nothing changed; W-362c row 2 reads prod again |
| 2026-11-21 | staging (W-362b, D-868) | row 5, after (read-only, IntelliJ console) | owner | `it-staging`: refreshTokens 2, refreshLive 0, indexes `_id_`, `refresh_token_email_idx`, `refresh_token_expiry_idx` (TTL 0), `refresh_token_family_idx`, `refresh_token_hash_idx`; billedWithoutEvent 0; users 14; consents 1 — **go** |
| 2026-11-21 | staging (W-362b, D-868) | row 6, `m1.5` stopped | owner | exit 143 (the `kill`); port 8080 free; ERROR lines in the run's log 0 — **go**; awaiting the owner's sign-off |
| 2026-11-21 | staging (W-362b, D-870) | sign-off | owner | "staging signed off" — staging on `m1.5`; W-362b done |
| 2026-11-21 | prod (W-362c, D-868) | row 1, environment | owner | keys 32 bytes, prefixes 8d910c6ebabf / f9bf756780af / af38366a5db3; PRIVACY_CONTACT 25; owner email 25; app password set; jobs off; port 8080 free; both jars — **go** |
| 2026-11-21 | prod (W-362c, D-868) | row 2, before (read-only, IntelliJ console) | owner | `Cluster0`: users 12, consents 1, `refresh_tokens` absent, billedWithoutEvent 0 (W-206 check) — as the earlier read; **go** |
| 2026-11-21 | prod (W-362c, D-868) | row 3, `m1.5` started (prod profile) | owner | `Started` in 6.727 s; the indexes of UserDetail, RefreshToken, PrivacyConsent ensured; no ERROR; WARN exactly staging's two (malware scan off, `application-prod.yaml:135`; SpringDoc's spec notice, D-867 C1a) — **go** |
| 2026-11-21 | prod (W-362c, D-868) | row 4, routes and a sign-in | owner | rights 200; notice version 6f36c92105ff376fa931f28523cdaddcd1fa80ceea7322b9fc67a6efa3638f44 (as staging); old rights path 401; expires_in 900, refresh token 43 chars; my stocks 200; basic auth 401; spec paths 136; refreshed 900, rotated yes; logout 204 — **go** |
| 2026-11-21 | prod (W-362c, D-868) | row 5, after (read-only, IntelliJ console) | owner | `Cluster0`: refreshTokens 2, refreshLive 0, indexes `_id_`, `refresh_token_email_idx`, `refresh_token_expiry_idx` (TTL 0), `refresh_token_family_idx`, `refresh_token_hash_idx`; billedWithoutEvent 0; users 12; consents 1 — **go** |
| 2026-11-21 | prod (W-362c, D-868) | row 6, `m1.5` stopped | owner | exit 143 (the `kill`); port 8080 free; ERROR lines in the run's log 0 — **go**; awaiting the owner's sign-off |
| 2026-11-21 | prod (W-362c, D-871) | sign-off | owner | "prod signed off" — prod on `m1.5`; W-362c done, W-362 complete |
| 2026-11-21 | local (W-331, D-882 C1a) | §0.8's step-2 exports deleted | owner | `find ~ -name 'legacy-*-before-w159.json'` listed none; `-delete` and recount 0 — none on disk; `~/wealthlens-runs/archives` holds only `*-after-m14` dumps, taken after §0.8's drop (no legacy collections) — **done** |
| 2026-11-22 | — (W-366a, D-894) | row 1, plan committed, its run | session | `602cefe3` pushed; run 44978503236 `success` on it; PR #71 `OPEN false 602cefe3 MERGEABLE CLEAN`; `master` e832b56a; no `m1.6` tag — **go** |
| 2026-11-22 | — (W-366a, D-894) | row 2, PR #71 merged as a merge commit | session | `M` = 532485e1, parents e832b56a and 602cefe3; `git diff --stat 602cefe3 origin/master` empty — **go** |
| 2026-11-22 | — (W-366a, D-894) | row 3, the `master` pipeline | session | run 44979069406 (`push`) `success` on 532485e1 — **go** |
| 2026-11-22 | — (W-366a, D-894) | row 4, `m1.6` tagged, worktree and jar | session | tag `m1.6` (7e13f52d) → 532485e1 pushed; `../wealthlens-m1.6` at 532485e1; `wealthlens.jar` 91,329,663 bytes — **go** |
| 2026-11-22 | — (W-366a, D-894) | row 5, WL-A-2 published from `m1.6`'s runbook | session | `{"idReadable":"WL-A-2",…}`; read back: 198,279 characters holding the file's 198,128 after the source-of-truth header; item 11 and §2.18 present — **go**; W-366a done |
| 2026-11-22 | staging `it-staging` (W-366b, D-894) | row 1, environment (from `~/wealthlens-runs/staging.env`, the owner's choice over prompts) | owner | keys 32 bytes, prefixes bbf4a26647f6 / c59064f277a6 / 5524e829976b; PRIVACY_CONTACT 25; owner email 25; Mongo user and both passwords set; jobs off; port 8080 free; jars 91,329,663 (`m1.6`) and 91,407,010 (`m1.5`) — **go** |
| 2026-11-22 | staging `it-staging` (W-366b, D-894) | row 2, before (read-only, IntelliJ console): 2a item 11, 2b counts | owner | 2a: grievances / processing log / insurances 0, `identifiers` `[]`, `actors` `[]` (the ITS-22 collections no longer hold one), `embeddedActors` `[]`, `loginsToRekey` 0 — nothing plain; 2b: users 14, upgraded 0, `dematWithNulls` 0, `tradesMissingFields` 0, P&L years 8, `pnlWithBackfillList` **7** (§2.18's N), backup absent — **go** |
| 2026-11-22 | staging (W-366b, D-894) | row 3, `m1.6` started (staging profile) | owner | pid 11092; `Started` in 7.263 s; no ERROR; WARN exactly W-362b's two (malware scan off, W-131h; SpringDoc's spec notice, D-867 C1a) — **go** |
| 2026-11-22 | staging (W-366b, D-894) | row 4, the routes | owner | rights 200 with `X-Request-Id`; **sign-in 500** (twice, 08:56:01 and 08:56:34): `DuplicateKeyException` E11000 on `user_details.user_email_idx` from `UserDetailsServiceImpl.updatePassword`'s `save` (W-358d3's rehash) — so no token, every later call 401, P&L hashes of empty bodies — **stop**. Nothing written (the failed upsert stores nothing; the hash stays BCrypt). Cause, read and reproduced: staging's 14 logins hold `_id` as an `ObjectId` (console: `objectId` ×14), written by the m1.4 re-key (`rekeyed.put("_id", new ObjectId())`), while `UserDetail`'s `@MongoId String id` writes and matches a string; a save filters on the string, matches nothing, and inserts a second login. Reproduced in a throwaway integration test: an app-registered login (`String` `_id`) signs in 200, one shaped as the re-key wrote it 500 with the same E11000. W-366 paused (R13) |
| 2026-11-22 | staging (W-366b, D-894) | m1.6 stopped after row 4's stop | owner | exit 143 (the `kill`); `m1.6 stopped`, port 8080 free — staging rests on `m1.5`, unchanged |
| 2026-11-22 | — (W-366a again, Revision 1, D-895) | W-369 committed, PR #72 | session | `e5c696d3` pushed; PR #72 run 44981814412 `success`; `OPEN false e5c696d3 MERGEABLE CLEAN`; `master` 532485e1; no `m1.6.1` tag — **go** |
| 2026-11-22 | — (W-366a again, D-895) | PR #72 merged as a merge commit | session | `M` = 8dbb4cf0, parents 532485e1 and e5c696d3; diff to e5c696d3 empty — **go** |
| 2026-11-22 | — (W-366a again, D-895) | the `master` pipeline | session | run 44982257584 (`push`) `success` on 8dbb4cf0 — **go** |
| 2026-11-22 | — (W-366a again, D-895) | `m1.6.1` tagged, worktree and jar; WL-A-2 | session | tag `m1.6.1` (5d3cfda9) → 8dbb4cf0 pushed; `../wealthlens-m1.6.1` at 8dbb4cf0; `wealthlens.jar` 91,330,043 bytes; WL-A-2 published from `m1.6.1`, read back 201,776 characters holding the file's 201,625, item 12 present — **go**; staging resumes from W-366b row 1 |
| 2026-11-22 | staging `it-staging` (W-366b on `m1.6.1`, D-896) | row 1, environment (from `staging.env`) | owner | `dev.env`, `prod.env`, `staging.env` each `-rw-------`; keys 32 bytes, prefixes bbf4a26647f6 / c59064f277a6 / 5524e829976b; PRIVACY_CONTACT 25; owner email 25; Mongo user and both passwords set; jobs off; port 8080 free; jars 91,330,043 (`m1.6.1`) and 91,407,010 (`m1.5`) — **go** |
| 2026-11-22 | staging `it-staging` (W-366b on `m1.6.1`, D-896) | row 2, before (read-only): 2a, 2b | owner | 2a nothing plain (all 0 / `[]`); 2b users 14 (no second login left by m1.6's failed sign-ins), upgraded 0, demat nulls 0, trades missing 0, P&L years 8, `pnlWithBackfillList` 7, backup absent — as the first row 2 — **go** |
| 2026-11-22 | staging (W-366b on `m1.6.1`, D-896) | row 3, `m1.6.1` started (staging profile) | owner | pid 13707; `Started` in 6.13 s; no ERROR; WARN the two known (malware scan off; SpringDoc's spec notice) — **go** |
| 2026-11-22 | staging (W-366b on `m1.6.1`, D-896) | row 4, the routes | owner | rights `HTTP/1.1 200` with `X-Request-Id`; **sign-in `expires_in 900`** (W-369: the re-keyed login signs in and its hash is rewritten); my stocks 200; spec paths **127** (136 − 9, as computed); the three removed routes 404; trade without stock `400 A trade must name its stock code`; P&L hashes 2023-24 17a29db87f971895, 2024-25 d515c12bf1ad8157, 2025-26 458545f47307230e (`w366b/pnl-before.txt`) — **go** |
| 2026-11-22 | staging `it-staging` (W-366b on `m1.6.1`, D-896) | row 5a, §2.18 steps 1 – 2: count, backup | owner | `before` 7 (= row 2b's N), `backup` 7 in `profit_and_loss_backfill_ids_w173` — **go** |
| 2026-11-22 | staging `it-staging` (W-366b on `m1.6.1`, D-896) | row 5b, §2.18 step 3: the write | owner | `updateMany` matched 7, modified 7; `after` 0 — **go** |
| 2026-11-22 | staging (W-366b on `m1.6.1`, D-896) | row 6a, P&L read after §2.18 | owner | refreshed `expires_in` 900; hashes 17a29db87f971895 / d515c12bf1ad8157 / 458545f47307230e = row 4's; `diff` empty, "P&L unchanged" — **go** |
| 2026-11-22 | staging `it-staging` (W-366b on `m1.6.1`, D-896) | row 6b, counts after | owner | users 14, **upgraded 1** (§1.h: the owner's hash now Argon2id), demat nulls 0, trades missing 0, `pnlWithBackfillList` 0, P&L years 8, backup 7 — **go** |
| 2026-11-22 | staging `it-staging` (W-366b on `m1.6.1`, D-896) | row 6c, §2.18 step 5: backup dropped | owner | `profit_and_loss_backfill_ids_w173` present: `false` — **go**; §2.18 done on staging |
| 2026-11-22 | staging (W-366b on `m1.6.1`, D-896) | row 7, logout, `m1.6.1` stopped | owner | `logout 204`; exit 143; `m1.6.1 stopped`; ERROR lines **4**, each one of row 4's deliberate refusals (the three removed routes, `No static resource …`, and `A trade must name its stock code`) — `ControllerAdviser` logs a caller's mistake at ERROR (finding, for a proposal) — **go**; awaiting the owner's sign-off |
| 2026-11-22 | staging (W-366b, D-896) | sign-off | owner | "staging signed off" — staging on `m1.6.1`; W-366b done |
| 2026-11-22 | prod `Cluster0` (W-366c on `m1.6.1`, D-897) | row 1, environment (from `prod.env`, a fresh terminal) | owner | `prod.env` `-rw-------`; keys 32 bytes, prefixes 8d910c6ebabf / f9bf756780af / af38366a5db3; PRIVACY_CONTACT 25; owner email 25; Mongo user and both passwords set; jobs off; port 8080 free; jars 91,330,043 (`m1.6.1`) and 91,407,010 (`m1.5`) — **go** |
| 2026-11-22 | prod `Cluster0` (W-366c on `m1.6.1`, D-897) | row 2, before (read-only): 2a item 11, 2b counts | owner | 2a nothing plain (all 0 / `[]`, `reports` gone with §2.15); 2b users 12, upgraded 0, demat nulls 0, trades missing 0, P&L years 7, `pnlWithBackfillList` **7** (§2.18's N, every year rebuilt by W-334e3's §2.11), backup absent — **go** |
| 2026-11-22 | prod (W-366c on `m1.6.1`, D-897) | row 3, `m1.6.1` started (prod profile) | owner | pid 14224; `Started` in 6.333 s; no ERROR; WARN the two known (malware scan off; SpringDoc's spec notice) — **go** |
| 2026-11-22 | prod (W-366c on `m1.6.1`, D-897) | row 4, the routes | owner | rights `HTTP/1.1 200` with `X-Request-Id`; sign-in `expires_in 900`; my stocks 200; spec paths 127; the three removed routes 404; trade without stock `400 A trade must name its stock code`; P&L hashes 2023-24 0871a3705a747557, 2024-25 c7ca734f284df50a, 2025-26 0cc7dc81e4b7be13 (`w366c/pnl-before.txt`) — **go** |
| 2026-11-22 | prod `Cluster0` (W-366c on `m1.6.1`, D-897) | row 5a, §2.18 steps 1 – 2: count, backup | owner | `before` 7 (= row 2b's N), `backup` 7 in `profit_and_loss_backfill_ids_w173` — **go** |
| 2026-11-22 | prod `Cluster0` (W-366c on `m1.6.1`, D-897) | row 5b, §2.18 step 3: the write | owner | `updateMany` matched 7, modified 7; `after` 0 — **go** |
| 2026-11-22 | prod (W-366c on `m1.6.1`, D-897) | row 6a, P&L read after §2.18 | owner | refreshed `expires_in` 900; hashes 0871a3705a747557 / c7ca734f284df50a / 0cc7dc81e4b7be13 = row 4's; `diff` empty, "P&L unchanged" — **go** |
| 2026-11-22 | prod `Cluster0` (W-366c on `m1.6.1`, D-897) | row 6b, counts after | owner | users 12, **upgraded 1** (§1.h: the owner's hash now Argon2id), demat nulls 0, trades missing 0, `pnlWithBackfillList` 0, P&L years 7, backup 7 — **go** |
| 2026-11-22 | prod `Cluster0` (W-366c on `m1.6.1`, D-897) | row 6c, §2.18 step 5: backup dropped | owner | `profit_and_loss_backfill_ids_w173` present: `false` — **go**; §2.18 done on prod (logged for both databases: W-368 may now retire it) |
| 2026-11-22 | prod (W-366c on `m1.6.1`, D-897) | row 7, logout, `m1.6.1` stopped | owner | `logout 204`; exit 143; `m1.6.1 stopped`; ERROR lines 4, exactly row 4's deliberate refusals (three `No static resource …`, one `A trade must name its stock code`) as on staging — **go**; awaiting the owner's sign-off |
| 2026-11-22 | prod (W-366c, D-897) | sign-off | owner | "approved" (to "say prod signed off") — prod on `m1.6.1`; W-366c done, W-366 complete |
| 2026-11-23 | prod, Neon `production` (W-337h, D-936) | rows 0 – 3, 5, 6 | owner | done per `plans/W-337h.md` step 2a; "prod signed off" — **go** |
| 2026-11-27 | staging, Aiven `wealthlens-staging` (W-337h, D-980) | connection details; rows 0, 2 – 3 | owner | host `wealthlens-staging-aaaaaw.e.aivencloud.com:18042`, `defaultdb`, `avnadmin`, `sslmode=require`; **the version and schema readback was not pasted** |
| 2026-11-27 | dev, Aiven `wealthlens-dev` (W-337h, D-980) | connection details; rows 0, 2 – 3 | owner | host `wealthlens-dev-wealthlens-dev.d.aivencloud.com:13196`, `defaultdb`, `avnadmin`, `sslmode=require`; **the version and schema readback was not pasted**; rows 4 – 5 not taken (D-980) |
| 2026-11-27 | prod, staging, dev (W-337h, D-980) | row 6, sign-off | owner | "credentials saved off-repo, prod + staging + dev signed off" |
| 2026-11-27 | dev, Aiven `wealthlens-dev` (W-337h, W-413; D-980, D-989) | §2.19 steps 1 – 3, readback | owner | `PostgreSQL 18.7 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 15.3.1 20260722 (Red Hat 15.3.1-1), 64-bit`, `defaultdb`, `public`; schema `wealthlens` present — **go**; closes the readback the 2026-11-27 dev line left "not pasted" |
| 2026-11-27 | staging, Aiven `wealthlens-staging` (W-337h, W-413; D-980, D-989) | §2.19 steps 1 – 3, readback | owner | `PostgreSQL 18.7 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 15.3.1 20260722 (Red Hat 15.3.1-1), 64-bit`, `defaultdb`, `public`; schema `wealthlens` present — **go**; the service was created 2026-11-27 01:58 UTC, so the password saved before then failed (`28P01`) until the connection was set up again; closes the staging line left "not pasted" |
| 2026-11-27 | — (W-337i1, D-991) | row 1, plans committed, their run | session | `830f9873` pushed to `w-337`; run 45940025399 (`pull_request`) `success` on it — **go** |
| 2026-11-27 | — (W-337i1, D-991) | row 2, preconditions (read-only) | session | `origin/master` 55b06df0; no `m1.7` tag on `origin`; PR #74 `OPEN false 830f9873 MERGEABLE CLEAN`; row 1's run `success` on 830f9873 — **go** |
| 2026-11-27 | — (W-337i1, D-991) | row 3, PR #74 merged as a merge commit | session | `M` = 4e8209c8, parents 55b06df0 and 830f9873; `git diff --stat 830f9873 origin/master` empty — **go** |
| 2026-11-27 | — (W-337i1, D-991) | row 4, the `master` pipeline | session | run 45941014325 (`push`) `success` on 4e8209c8 — **go** |
| 2026-11-27 | — (W-337i1, D-991) | row 5, `m1.7` tagged, worktree and jar | session | tag `m1.7` (fb86fdce) → 4e8209c8 pushed; `../wealthlens-m1.7` at 4e8209c8; `backend/target/wealthlens.jar` 120,968,297 bytes — **go** |
| 2026-11-27 | — (W-337i1, D-991) | row 6, WL-A-2 published from `m1.7`'s runbook | session | `{"idReadable":"WL-A-2",…}`; read back: 224,063 characters holding the file's 223,912 after the source-of-truth header (its head and tail found); §0.9 and §0.10 present — **go**; W-337i1 rows done |
| 2026-11-27 | — (W-337i1, D-991) | sign-off | owner | "signed off" — `m1.7` is the build every move runs; W-337i1 done |
| 2026-11-27 | dev, Aiven `wealthlens-dev` (W-337i2, D-992) | row 1, environment | owner | keys 32 bytes, prefixes 9dbe9c7018bd / 55b28ef39f76 / 886cb3703540 (first record of dev's); PRIVACY_CONTACT 25; owner email 25; JDBC `wealthlens-dev-wealthlens-dev.d.aivencloud.com:13196 defaultdb`; port 8080 free; m1.7 jar 120,968,297 bytes — **go** |
| 2026-11-27 | dev `wealthlens-dev` (W-337i2, D-992) | row 2, §0.9 step 1 (read-only) | owner | **refused by the server**: error 16412, `$documents` field name may not contain `.` (`tax_document_bytes.files`) — nothing read or written; §0.9 step 1's query corrected (`counted` / `nonZero`), tried on a throwaway `mongo:8.0.4`; row 2 again |
| 2026-11-27 | dev (W-337i2, D-992) | row 2 again, §0.9 step 1 (read-only) | owner | `database` **`test`** — the console's default database, not `wealthlens-dev`; `counted` 42, `nonZero` `[]`, `unknownWithData` `[]`, `loginsWithoutEmail` 0 describe `test` only — **stop**, nothing read from dev; row 2 again on `wealthlens-dev` |
| 2026-11-27 | dev `wealthlens-dev` (W-337i2, D-992) | row 2, §0.9 step 1 (read-only), on `wealthlens-dev` | owner | `database` `wealthlens-dev`; `counted` 42; `nonZero` `[]` (every source 0: dev is empty); `unknownWithData` `[]`; `loginsWithoutEmail` 0 — **go**; row 8 takes its register-and-grant path |
| 2026-11-27 | dev, Aiven `wealthlens-dev` (W-337i2, D-992) | row 3, §0.9 step 2 (read-only) | owner | no tables in schema `wealthlens` — **go** |
| 2026-11-27 | dev, Aiven `wealthlens-dev` (W-337i2, D-992) | row 4, §0.9 step 3, the copy | owner | `exit: 0`; `Started` in 33.6 s with only `copier` active (port -1); Flyway "Successfully applied 13 migrations … now at version v13"; every source 0 rows, `clean`; final line "Every source copied and proven, times written in UTC from rows written in Asia/Kolkata"; WARN: NO RATE CARDS (expected before §1B), `spring.jpa.open-in-view` (Spring's default notice), Tomcat acceptor "did not stop cleanly" (port -1, W-409) — **go** |
| 2026-11-27 | dev (W-337i2, D-992) | row 5, §0.9 step 4, the report | owner | `41`, `0`, `1`; every line 0 = row 2's counts — **go** |
| 2026-11-27 | dev, Aiven `wealthlens-dev` (W-337i2, D-992) | row 6, §0.9 step 5 (read-only), part 2 | owner | `flyway_schema_history` versions 1 – 13, each `success` true — **go**; the six counts awaited |
| 2026-11-27 | dev (W-337i2, D-992) | row 7, `m1.7`'s first normal start (default profile) | owner | pid 21269; `Started` in 9.889 s; no ERROR; Flyway "validated 13", "Schema wealthlens is up to date"; WARN: NO RATE CARDS (before §1B), `open-in-view`, SpringDoc's spec notice (D-867 C1a); row 6's six counts not pasted — each table's 0 target rows is row 4's own check — **go** |
| 2026-11-27 | dev (W-337i2, D-992) | row 8a, register; stored email | owner | notice version 6f36c92105ff…; registered ("added to system"); stored form computed with dev's keys (§0.7), `UPDATE` printed — **go** |
| 2026-11-27 | dev, Aiven `wealthlens-dev` (W-337i2, D-992) | row 8b, the role | owner | `UPDATE wealthlens.user_details SET roles = 'SUPER_USER' WHERE email = '<stored>' AND roles = 'USER'` → 1 row affected — **go** |
| 2026-11-27 | dev (W-337i2, D-992) | row 8c, sign-in as `SUPER_USER` | owner | `expires_in 900`; `admin spec 200` — **go** |
| 2026-11-27 | dev, Aiven `wealthlens-dev` (W-337i2, D-992) | row 9, §1B seed, drift, unverified, simulate | owner | seed `{"catalogue":13,"created":39,"skipped":0,"instruments":2,"instrumentsSkipped":0,"drift":0}`; drift `0`; unverified `[]`; **simulate: every field `null`** — the filter's fields are `ChargeBreakdownResponse`'s, so the answer was not that type; raw answer asked — **hold** |
| 2026-11-27 | dev (W-337i2, D-992) | terminal closed after row 9 | owner | `m1.7` stopped with it (port 8080 free, no `wealthlens.jar` process); dev's data kept (seeded, owner `SUPER_USER`). `~/wealthlens-runs/dev.env` (600) held only empty values (`''`) — rewritten by the owner as the one place for dev's run values (owner: "lets keep somewhere … we can src there"); rows from here start with `source` |
| 2026-11-27 | dev (W-337i2, D-992) | `dev.env` rewritten (step 1), checked (step 2, read by the session, fingerprints only) | owner, session | `-rw-------`, 12 `export` names; keys 32 bytes, prefixes 9dbe9c7018bd / 55b28ef39f76 / 886cb3703540 = row 1; JDBC host `wealthlens-dev-wealthlens-dev.d.aivencloud.com:13196 defaultdb`; owner email 25; the empty template kept as `dev.env.old` — **go** |
| 2026-11-27 | dev (W-337i2, D-992) | `m1.7` restarted detached; row 9d, simulate raw | owner | pid in `m17.pid`; `Started` in 16.6 s; no ERROR; WARN open-in-view, SpringDoc only (NO RATE CARDS gone: seeded); `expires_in 900`; simulate **400** "stockCode is required on a BUY or a SELL: it finds the scheme's own charges, such as an exit load" — the refusal W-365 added (`bd19dd39`, `ChargeSimulationService.java:102`, in `m1.6.1` too); §1B step 3's body predates it, so the first row 9's nulls were this 400 read through the filter. Not a port defect — runbook defect; simulate again with `stockCode` |
| 2026-11-27 | dev (W-337i2, D-992) | row 9d, simulate with `stockCode` | owner | `status 200`; `ZERODHA_EQ_DELIVERY_2025_04` (statutory `STATUTORY_EQ_DELIVERY_2025_04`), `RESOLVED`, `totalCharges` 119.67; BROKERAGE 0, STT 100, EXCHANGE_TXN 2.97, SEBI_FEE 0.1, IPFT 0.1, DP 13.5, GST 3 (on 16.67) = §1B's figures worked by hand — **go**; row 9 done |
| 2026-11-27 | dev (W-337i2, D-992) | row 10, before: ERROR count | owner | `grep -c " ERROR " m17.log` → `0` — **go** to the collection pass |
| 2026-11-27 | dev (W-337i2, D-992) | row 10, api-collection pass (Bruno runner, `Local - Staging`, after `Auth/Login.yml` sent `{{PASSWORD}}`) | owner | all passed but two, both failed in Bruno before sending: `Transactions/Upload Transactions` (EPERM reading `~/Desktop/DEMAT P&L/FYERS.csv`, macOS file permission) and `Tax Profile/Upload a Document` (EISDIR: its file path is a folder) — not server answers; the runner's "passed" does not show status codes, so 5xx is read from the app log in row 11 |
| 2026-11-27 | dev (W-337i2, D-992) | row 11, the log after the pass; `m1.7` stopped | owner, session | ERROR **2** (two 500s): (1) `Text '2024-12-3' could not be parsed at index 8` — `Complex Filters/Assets more than 1 year` sends a malformed date; `QueryBuilder.sanitize` → `TLocalDate.convertToDate` throws, unmapped by `ControllerAdviser`, the same code in `m1.6.1` (`QueryBuilder.java:61 – 63` there) — a caller mistake answered 500, not a port regression; (2) `Failed to parse multipart servlet request` — the upload Bruno could not read (EPERM) sent a broken body; `MultipartException` is mapped in neither build. WARN refusals (10), each explained: simulate without `stockCode` (row 9d's terminal call; the collection's request sends one — corrected planning W-414), stocks dev lacks (`A`, `ASHOKLEYDUMMY`, one blank), unfilled `:transactionId` / `:id`, a Mongo-era corporate-action id, a wrong old password, registration without the current notice. Port 8080 free, no `wealthlens.jar` process (checked by the session) — **stop per the plan (5xx); owner to rule** |
| 2026-11-27 | dev (W-337i2, D-993) | row 11, ruling | owner | "sure" to C3a: both 500s are `m1.6.1`'s too, not the move's; W-415 placed, W-414 widened — **go** to row 12 |
| 2026-11-27 | dev (W-337i2, D-992) | row 12, clean-up | owner | `w337i2/` (logs, pid) and `dev.env.old` deleted (`No such file or directory` ×2); `dev.env` kept, `-rw-------`, the one place for dev's values — **go**; awaiting sign-off |
| 2026-11-27 | dev, Aiven `wealthlens-dev` (W-337i2, D-992) | sign-off | owner | "dev signed off" — dev on `m1.7` / PostgreSQL; §0.9 logged for dev; W-337i2 done |
| 2026-11-27 | — (W-337i1 again, D-997) | row 2, preconditions (read-only) | session | `origin/master` 4e8209c8; no `m1.7.1` tag on `origin`; PR #75 `OPEN 0ebf3c85 MERGEABLE CLEAN`; its run 45953794343 (`pull_request`) `success` on 0ebf3c85 — **go** |
| 2026-11-27 | — (W-337i1 again, D-997) | row 3, PR #75 merged as a merge commit | session | owner: "approved as recommended"; `M` = 9dcc9394, parents 4e8209c8 and 0ebf3c85; `git diff --stat 0ebf3c85 origin/master` empty — **go** |
| 2026-11-27 | — (W-337i1 again, D-997) | row 4, the `master` pipeline | session | run 45954909059 (`push`) `success` on 9dcc9394 — **go** |
| 2026-11-27 | — (W-337i1 again, D-997) | row 5, `m1.7.1` tagged, worktree and jar | session | tag `m1.7.1` (08e111e6) → 9dcc9394 pushed; `../wealthlens-m1.7.1-pg` at 9dcc9394; `backend/target/wealthlens.jar` 120,968,574 bytes — **go** |
| 2026-11-27 | — (W-337i1 again, D-997) | row 6, WL-A-2 published from `m1.7.1`'s runbook | session | `{"idReadable":"WL-A-2",…}`; read back: 233,870 characters holding the file's 233,719 after the source-of-truth header (whole body found); §0.9 and §0.10 present — **go**; rows done, owner's sign-off next |
| 2026-11-27 | — (W-337i1 again, D-997) | sign-off | owner | "sure" — `m1.7.1` is the build every remaining move runs; W-337i1 again done |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | row 1, environment (from `prod.env`, a fresh terminal) | owner | first try: keys **0** bytes (`e3b0c44298fc`, the empty hash) — `prod.env` holds no `export` lines, so `source` set shell variables only; plan rows 1 and 3 corrected to `set -a; source …; set +a`. Again: keys 32 bytes, prefixes 8d910c6ebabf / f9bf756780af / af38366a5db3 (equal to W-366c row 1); MONGO_USER, OWNER_EMAIL, PRIVACY_CONTACT exported; PRIVACY_CONTACT 25; owner email 25; ports 8081, 8082, 27018, 5433 free; jars 120,968,574 (`m1.7.1`) and 91,330,043 (`m1.6.1`); 0 `w337i3` containers — **go** |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | row 2, the two containers | owner | `w337i3-mongo` (834da38a0707) `mongo:8.0.4` on 127.0.0.1:27018, `rs.initiate` ok `1`, `isWritablePrimary` `true`; `w337i3-pg` (e005a12565a2) on 127.0.0.1:5433, PostgreSQL 18.7 (Debian 18.7-1.pgdg13+5), schema `wealthlens` made — **go** |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | row 3, the dump (prod read-only, piped; nothing on disk) | owner | SRV lookup worked from the container (no `--network host`); dumped non-empty: transactions 788, assets 518, trade_outcomes 163, privacy_processing_log 91, charge_schedules 39, charge_catalogue 13, user_details 12, lastly_performed_corporate_action 11, profit_and_loss 7, corporate_action 6, refresh_tokens 4, charge_instruments 2, privacy_consents 1 (sum 1655); `1655 document(s) restored successfully. 0 document(s) failed to restore.`; `exit: 0 0`; Atlas login replaced by `rehearsal` (C2a) — **go** |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | row 4, §0.9 step 1 on prod (console) and on the copy (`mongosh --eval` of the same query from `q.js`, `.toArray()` added) | owner | both: `database` `Cluster0`, `counted` 42, `loginsWithoutEmail` 0, `unknownWithData` `[]`; `nonZero` identical, 13 collections: user_details 12, refresh_tokens 4, charge_catalogue 13, charge_schedules 39, charge_instruments 2, corporate_action 6, lastly_performed_corporate_action 11, assets 518, transactions 788, trade_outcomes 163, profit_and_loss 7, privacy_consents 1, privacy_processing_log 91 — equal to row 3's dump; the copy is whole — **go** |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | rows 5 – 7, target, copy, report | owner | row 5 `0`; first paste never ran (zsh, no interactive comments: an apostrophe in a `#` line left `quote>`); again: row 6 **`exit: 1`** — `IllegalStateException: assets 68d2dd7f59825ea2f98d7ea5: assets has no column total_value for a stored key` (`RowMapping.java:175`, from `MongoCopier.prepare`, before any write); row 7 `0` `0` `0` `0`; target holds Flyway's 54 tables, empty. `m1.6.1`'s `AssetEntity` has no `total_value` (removed by d55dd15f, #45); old prod assets still carry it — **stop (R13)**: key survey of the copy asked, then a decision |
| 2026-11-27 | — (W-337i1 again for `m1.7.2`, D-1000) | rows 2 – 3, preconditions and the merge | session | `origin/master` 9dcc9394, no `m1.7.2` tag; PR #76 first run 45961283088 **failure** (`FinishedPlanTest`: W-417 step 4 open) — fixed by c5868f50; run 45963211386 `success`, `OPEN CLEAN` at c5868f50; owner: "approved as recommended"; `M` = 0e3a4163, parents 9dcc9394 and c5868f50, `git diff --stat c5868f50 origin/master` empty — **go** |
| 2026-11-27 | — (W-337i1 again for `m1.7.2`, D-1000) | row 4, the `master` pipeline | session | run 45965533383 (`push`) `success` on 0e3a4163 — **go** |
| 2026-11-27 | — (W-337i1 again for `m1.7.2`, D-1000) | row 5, `m1.7.2` tagged, worktree and jar | session | tag `m1.7.2` (b6a94ef3) → 0e3a4163 pushed; `../wealthlens-m1.7.2-pg` at 0e3a4163; `backend/target/wealthlens.jar` 120,972,449 bytes — **go** |
| 2026-11-27 | — (W-337i1 again for `m1.7.2`, D-1000) | row 6, WL-A-2 published from `m1.7.2`'s runbook | session | `{"idReadable":"WL-A-2",…}`; read back: 238,838 characters holding the file's 238,687 after the source-of-truth header (whole body found) — **go**; `m1.7.2` is the build W-337i3 – i6 run |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | rows 5 – 7 again, on `m1.7.2` | owner | `DROP SCHEMA wealthlens CASCADE` (54 tables, probe 2's copy), `CREATE SCHEMA`; row 5 `0`; row 6 `exit: 0`; row 7 `41` `0` `1` `0`, every count equal to row 4; `assets` (518 / 518) and `transactions` (788 / 788) name `read as m1.6.1 read them`: `CorporateActionEntity` actionPrice, exDate, recordDate, stockCode, stockName, toStockCode, toStockName 5 each, createdAt, createdBy 3 each, `1 × date at midnight Asia/Kolkata`, and on assets `220 × total_value` — equal to the key survey and dates.js; 2 lines carry the text — **go** |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | row 8, §0.9 step 5, after (read-only) | owner | `12\|788\|518\|163\|7\|0` — user_details, transactions, assets, trade_outcomes, profit_and_loss equal to row 4, tax_profiles 0 (none in row 4); Flyway `1:true` … `13:true`, the 13 migrations `m1.7.2` ships — **go** |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | row 9, both builds up | owner | first paste: both `exit 1`, "Port 8081/8082 was already in use" — an earlier row 9 start (PIDs 13698 `m1.6.1`, 13699 `m1.7.2`, up since 14:21 IST, before rows 5 – 7's `DROP SCHEMA`), its logs overwritten; owner killed both; again: pids 14757 / 14758, `Started` in 4.87 s (`m1.6.1`, 8081) and 7.473 s (`m1.7.2`, 8082); `x6trgen` 0 / 0; no ERROR; Flyway "Successfully validated 13 migrations", "Schema wealthlens is up to date"; "Charges engine ready: 39 rate card(s)" = row 4's charge_schedules; WARN: the malware scan is off (both; `DocumentBytesConfig.java:23`, prod `scan.enabled: false`, D-769), SpringDoc's spec notice (both; D-867 C1a), `open-in-view` (`m1.7.2`; as on dev, W-337i2 row 7) — all expected — **go** |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | row 10, §0.10 step 1, the two lists | owner | logins `12`; `workspaces.txt` 0 lines (no `tax_year_workspaces` on prod's copy); first roles read matched no row (`user_details.email` is `SearchableColumn`, encrypted — the plain email in `WHERE` cannot match); roles grouped: `ADMIN,USER,SUPER_USER,` × 1, `ADMIN,USER,TEST_USER` × 2, `USER` × 4, `USER,TEST_USER` × 5; `users.txt` one line, roles `ADMIN,USER,SUPER_USER` (trailing comma left out: Java's `split` drops it, `UserDetailsImpl.java:24`; Python's keeps it, `read_comparison.py:117`) — **go**; step 2's `-s workspaces.txt` guard stops an empty file — decision D1a/D1b asked |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | row 10, §0.10 step 2, the read comparison (guard as D-1001) | owner | `spec saved: 127 paths`; 591 answers before and after; `not reached` equal on both sides (28 routes: the three of the trial and every tax route — no tax data on the copy); **`exit: 1`, 6496 differences outside the list** — read by the session ignoring list order (a scratch script pairing items, the tool's own `diff_json` and lists): 490 remain — (1) `transactions` `timezoneId` `Asia/Kolkata` before, `null` after, 379; (2) `orderExecutionTime` equal on both sides where a shift is expected, 97 across transactions, stocks, stock, mutual funds; (3) `demergerDetail.empty` only after, 6 (`DemergerDetail.isEmpty()` added in `w-337`, serialised by Jackson); (4) one stock lot NSE / 1 before, BSE / 2 after, unexplained (may be the pairing); the other ~6000 are list order (stocks, mutual funds, transactions pages, charge schedules) — **stop (R13)**; both builds left up, `before/` `after/` kept for the investigation |
| 2026-11-27 | — (W-337i1 again for `m1.7.3`, D-1003) | rows 2 – 3, preconditions and the merge | session | PR #77 (W-419, head cb20cdce) run 46038075123 `success`, `CLEAN` / `MERGEABLE`; owner: "merge and release"; `M` = cfc5a15c, parents 0e3a4163 and cb20cdce, `git diff --stat cb20cdce origin/master` empty — **go** |
| 2026-11-27 | — (W-337i1 again for `m1.7.3`, D-1003) | row 4, the `master` pipeline | session | run 46039276266 (`push`) `success` on cfc5a15c — **go** |
| 2026-11-27 | — (W-337i1 again for `m1.7.3`, D-1003) | row 5, `m1.7.3` tagged, worktree and jar | session | tag `m1.7.3` (68298465) → cfc5a15c pushed; `../wealthlens-m1.7.3-pg` at cfc5a15c; `backend/target/wealthlens.jar` 120,973,903 bytes — **go** |
| 2026-11-27 | — (W-337i1 again for `m1.7.3`, D-1003) | row 6, WL-A-2 published from `m1.7.3`'s runbook | session | `{"idReadable":"WL-A-2",…}`; read back: 244,411 characters holding the file's 244,260 after the source-of-truth header (whole body found; W-419's §0.9 and §0.10 text present) — **go**; `m1.7.3` is the build W-337i3 – i6 run |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | rows 5 – 9 again, on `m1.7.3` | owner | both apps stopped (`exit 143`), ports free; `J17` → `m1.7.3` (120,973,903 bytes); `DROP SCHEMA` (54 objects, `m1.7.2`'s copy), `CREATE SCHEMA`; row 5 `0`; row 6 `exit: 0`; row 7 `41` `0` `1` `0`, `assets` naming the same forms as before, `transactions` adding **`716 × timezone_id absent, read as Asia/Kolkata`** (W-419; ≥ row 10's 379 of one account); `privacy_processing_log` 676, not row 4's 91 — the read records `m1.6.1` wrote to the MongoDB copy during row 10 (plan risk 4), every other count equal to row 4; row 8 `12\|788\|518\|163\|7\|0`, Flyway `1:true` … `13:true`; row 9 `Started` 5.13 s / 7.56 s, `x6trgen` 0 / 0, no ERROR, WARN the three known (scan off, SpringDoc, `open-in-view`), "Successfully validated 13", "up to date", "Charges engine ready: 39" — **go** |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | row 10 again, on `m1.7.3` (tool and runbook from `../wealthlens-m1.7.3-pg`) | owner | `spec saved: 127 paths`; 591 answers before and after; `exit: 1`, **138 differences** (6496 at `m1.7.2`), all on the four holdings lists: ~130 `double` sums in another lot order (`518.02` / `518.0200000000001`), 6 `corporateActionType` / `corporateActions` copied from the first lot — **stop (R13)**; W-419 Revision 3 approved R3-C1a; the saved answers compared again with the fixed tool (session, w-337 working tree): `exit 0`, `0 differences outside the list` |
| 2026-11-27 | — (W-337i1 again for `m1.7.4`, D-1004, D-1005) | rows 2 – 6, merge to WL-A-2 | session | PR #78 (W-419 R3, W-420) run 46043051537 `success` on fc55af6b (the three-job pipeline; its deliberate red commit 88cb68e6 reverted by fc55af6b, the red proof moved to draft PR #79); owner: "merge pr and proceed"; `M` = ccb7037d, parents cfc5a15c and fc55af6b, `git diff --stat fc55af6b origin/master` empty; `master` run 46043898144 `success`; tag `m1.7.4` (8aa6baf5) → ccb7037d pushed; `../wealthlens-m1.7.4-pg` at ccb7037d, jar 120,973,903 bytes (as `m1.7.3`: the app is unchanged); WL-A-2 read back 247,126 characters holding the file's 246,975 (whole body, W-419 R3 text present); `read_comparison.py` identical to the w-337 tree's — **go** |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | row 10, §0.10 step 3, the comparison on `m1.7.4`'s tool | session (owner: "1") | `compare` of row 10's saved `m1.6.1` / `m1.7.3` answers (591 each; the app unchanged at `m1.7.4`) with `../wealthlens-m1.7.4-pg`'s `read_comparison.py`: **`exit: 0`, `0 differences outside the list`**; `not reached` the same 28 routes on both sides (the trial's three and the tax routes — no tax data on the copy) — **go**; `before/` `after/` deleted at row 14 (§0.10 step 4) |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | row 11, §1B on the PostgreSQL copy (8082) | owner | `expires_in 900`; seed `{"catalogue":0,"created":0,"skipped":39,"instruments":0,"instrumentsSkipped":2,"drift":0}` (the copy holds every card); drift `0`; unverified `[]`; first simulate sent without `stockCode` (the session's command, not §1B's) and answered no breakdown; again with §1B step 3's `"stockCode":"INFY"`: `ZERODHA_EQ_DELIVERY_2025_04`, `RESOLVED`, **119.67** — BROKERAGE 0, STT 100, EXCHANGE_TXN 2.97, SEBI_FEE 0.1, IPFT 0.1, DP 13.5, GST 3.0 — **go** |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | row 12, the routes on 8082 | owner | `HTTP/1.1 200` with `X-Request-Id`; `my stocks 200`; `spec paths 127` (row 10's spec count); `trade without stock: 400 A trade must name its stock code` — **go** |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | row 13, stop both | owner | `logout 204`; both `exit 143`, 8081 and 8082 stopped; ERROR lines: `m17.log` **0**, `m161.log` 451 — every one a refusal `m1.6.1` logs at ERROR (`ControllerAdviser.java:232` at `m1.6.1`): 448 "No charges recorded for transaction …" (row 10 read each transaction's charges; the copy holds no `user_charges`), 2 "No erasure was requested", 1 "Stock not found"; `m17.log` holds the same 448 below ERROR (W-370); row 10 showed both answering alike — **go** |
| 2026-11-27 | rehearsal, local copy of `Cluster0` (W-337i3, D-998) | row 14, every copy removed | owner | `w337i3-mongo`, `w337i3-pg` removed with their volumes, then `0` containers; `w337i3` and `w406` folders gone ("No such file or directory" × 2), so the answers (§0.10 step 4) and `users.txt` with them; clipboard cleared; secrets unset — **go**; sign-off asked |
| 2026-11-28 | staging, `it-staging` → Aiven `wealthlens-staging` (W-337i4, D-1007) | row 1, environment (from `staging.env`) | owner | keys 32 bytes, prefixes bbf4a26647f6 / c59064f277a6 / 5524e829976b (= W-366b row 1); PRIVACY_CONTACT 25; owner email 25; Mongo user set; PG URL not yet set (row 2); 8081, 8082 free; jars 91,330,043 (`m1.6.1`) and 120,973,903 (`m1.7.4`); **docker: OrbStack not running** (socket absent), images `0` — **hold**: `orb start`, then the images check again |
| 2026-11-28 | staging (W-337i4, D-1007) | row 1, again: `orb start`, images | owner | images `2` (`postgres:18`, `mongo:8.0.4`) — **go**; row 1 done |
| 2026-11-28 | staging (W-337i4, D-1007) | row 2, `staging.env` gains the target (backup `staging.env.before-w337i4` first) | owner | `-rw-------`, 605 bytes; 12 names, the last three `APP_POSTGRES_DATASOURCE_*`; password length 24; host part `wealthlens-staging-aaaaaw.e.aivencloud.com:18042 defaultdb` — **go** |
| 2026-11-28 | staging, Aiven `wealthlens-staging` (W-337i4, D-1007) | row 3, §0.9 step 2, the target (read-only, `postgres:18` psql, C1a) | owner | `PostgreSQL 18.7 on x86_64-pc-linux-gnu`; schema `wealthlens` `1`; tables `0` — **go** |
| 2026-11-28 | staging `it-staging` (W-337i4, D-1007) | row 4, §0.9 step 1, the source (read-only, IntelliJ console) | owner | `database` `it-staging`, `counted` 42, `loginsWithoutEmail` 0, `unknownWithData` `[]`; `nonZero` 13: user_details 14, refresh_tokens 4, charge_catalogue 13, charge_schedules 39, charge_instruments 2, corporate_action 7, lastly_performed_corporate_action 10, assets 431, transactions 660, trade_outcomes 130, profit_and_loss 8, privacy_consents 1, privacy_processing_log 109 (sum 1428) — **go** |
| 2026-11-28 | staging `it-staging` (W-337i4, D-1007) | row 5, the old-forms survey (read-only, IntelliJ console; `find().toArray()` in `$documents` worked) | owner | `assetsWithTotalValue` 3; `transactionsWithoutTimezone` 581; `corporateActionKeys` `[]`; dates: assets `transaction_date` 431 dated, transactions 660, all at 00:00Z (`at1830Utc` 0, `otherTimes` 0), no `maturity_date` — **go**; row 7 must name `3 × total_value left out` (assets) and `581 × timezone_id absent` (transactions), nothing else |
| 2026-11-28 | staging (W-337i4, D-1007) | row 6, §0.9 step 3, the copy | owner | **`exit: 1`**; tail shows only Hikari shutdown — cause asked (log kept) — **stop** |
| 2026-11-28 | staging (W-337i4, D-1007) | row 6, the cause | owner, session | `IllegalStateException: transactions 693e80b96941753c15696441: transactions has no column asset_request for a stored key` (`RowMapping.java:191`), 0 `, clean` lines (refused in prepare, before any row is written; Flyway's empty tables expected on the target, as W-337i3 row 6). The copier maps `asset_request` to `asset_request_json` (`CollectionCopies.java:101`), so the stored key is likely another spelling of it (`assetRequest`: `TemporaryTransactionEntity` stored it unannotated, 5bc7cf10, in `temporary_transactions` until 117b3a0d) — **unverified**; read-only key survey asked — **stop (R13)** |
| 2026-11-28 | staging `it-staging` (W-337i4, D-1007) | row 6, the key survey (read-only, IntelliJ console) | owner | the document found (string `_id`), `status` `TEMPORARY`, keys snake_case but **`assetRequest`** (14 camelCase sub-keys) — the only camelCase top-level key in any collection; `assetRequest` 1, `asset_request` 0 in `transactions`. `m1.6.1` reads only `asset_request` (`TransactionEntity.java:159 – 160`), so it sees none, and its redrive answers this one `failed: No asset request stored` (`PortfolioService.java:345 – 349` at `m1.6.1`). Prod copied clean (no such key). Decision asked (W-421) |
| 2026-11-28 | — (W-337i1 again for `m1.7.5`, D-1009) | rows 1 – 3, PR #80 merged | session (owner: "merge #80") | run 46091230551 `success` on 168b3f71 (unit, integration, gate); `gh pr merge 80 --merge` → `ee1b155d`, parents `ccb7037d` (`m1.7.4`) and `168b3f71`; `git diff --stat 168b3f71 origin/master` empty; tag `m1.7.5` absent — **go**; row 4 (`master` run 46091734952) running |
| 2026-11-28 | — (W-337i1 again for `m1.7.5`, D-1009) | rows 4 – 6, tag, jar, WL-A-2 | session (owner: "release m1.7.5") | `master` run 46091734952 `push` `success` on `ee1b155d` (unit, integration, gate); tag `m1.7.5` → `ee1b155d` pushed; worktree `../wealthlens-m1.7.5-pg` at `ee1b155d`; `clean install -DskipTests` exit 0, `wealthlens.jar` 120,973,951 bytes, sha256 `fe5bbe986410…`, its `RetiredKeys` naming `assetRequest`; WL-A-2 published from that worktree, read back 254,568 characters holding the file's body (254,417 with its title), W-421's note and §0.10's widened heading present — **go**; `m1.7.5` is the build W-337i4 – i6 run |
| 2026-11-28 | staging, Aiven `wealthlens-staging` (W-337i4, D-1008) | restart: schema dropped and made; row 3 again | owner | `J17` → `m1.7.5` (120,973,951 bytes); tables holding rows `[]`; 54 tables (Flyway's, empty, from row 6's refused run); `DROP SCHEMA wealthlens CASCADE` (54 objects), `CREATE SCHEMA`; `PostgreSQL 18.7`, schema `1`, tables `0` — **go**; rows 4 – 5 stand (`it-staging` not touched since) |
| 2026-11-28 | staging (W-337i4, D-1008) | row 6 again, §0.9 step 3 on `m1.7.5` | owner | `exit: 0` (row 6's `m1.7.4` log kept aside as `copier-staging-m174.log`) — **go** |
| 2026-11-28 | staging (W-337i4, D-1008) | row 7, §0.9 step 4, the report | owner | `41`, `0`, `1`; every source's counts equal row 4 (user_details 14, refresh_tokens 4, charge_catalogue 13, charge_schedules 39, charge_instruments 2, corporate_action 7, lastly_performed_corporate_action 10, assets 431, transactions 660, trade_outcomes 130, profit_and_loss 8, privacy_consents 1, privacy_processing_log 109; the rest 0); assets names `3 × total_value left out`; the transactions line cut at 220 characters — its whole form list asked |
| 2026-11-28 | staging (W-337i4, D-1008) | row 7, the two lines' forms in full | owner | `assets 431/431: … 3 × total_value left out, clean`; `transactions 660/660: … 1 × assetRequest left out, 581 × timezone_id absent, read as Asia/Kolkata, clean` — equal to row 5's survey and W-421 — **go** |
| 2026-11-28 | staging, Aiven `wealthlens-staging` (W-337i4, D-1008) | row 8, §0.9 step 5, after (read-only) | owner | `14\|660\|431\|130\|8\|0` = row 4; Flyway `1:true` … `13:true` — **go**; §0.9 done on staging |
| 2026-11-28 | staging (W-337i4, D-1008) | row 9, both builds up (staging profile) | owner | `m1.6.1` (8081, `it-staging`) `Started` in 8.732 s; `m1.7.5` (8082, Aiven) in 10.179 s; no ERROR; Flyway validated 13, schema up to date; `Charges engine ready: 39 rate card(s)`; WARN: malware scan off on both (`DocumentBytesConfig.java:23`; `application-staging.yaml:133 – 135` `scan.enabled: false`, D-769), `open-in-view` (`m1.7.5`), SpringDoc's notice — **go** |
| 2026-11-28 | staging, Aiven `wealthlens-staging` (W-337i4, D-1008) | row 10, §0.10 step 1, the two lists | owner | logins `14`; `workspaces.txt` 0 lines; roles `SUPER_USER` × 1, `USER` × 13 — `users.txt` is the owner's line with `SUPER_USER` — **go** |
| 2026-11-28 | staging (W-337i4, D-1008) | row 10, §0.10 step 2, the read comparison | owner, session | `spec saved: 127 paths`; 455 answers before and after; `not reached` equal on both sides (28 routes: the trial's three and every tax route but the lists — no tax data on staging); **`exit: 1`, 6 differences outside the list**, all on `/api/v1/me/portfolio/stocks/RELIANCE`: lots 3 and 4 swapped — two bonus lots of 2024-10-29 (FYERS 2, ZERODHA 1, price 0), every field equal once paired. Both builds order that view by trade date only (`findByEmailAndStockCodeOrderByTransactionDate`, `PortfolioRepository.java:52` at `m1.7.5`, `:36` at `m1.6.1`), so a same-day tie comes back in each store's own order — **stop (R13)**; both builds left up, `before/` `after/` kept; decision asked |
| 2026-11-28 | staging (W-337i4, D-1010) | row 10, W-422's tool on the saved answers | session (owner: "W-422 B") | `compare before after` with the working tree's `read_comparison.py` (the route ordered on both sides): `exit 0`, `0 differences outside the list` — the app's own order waits for `m1.7.6`, then row 10 fetched again |
| 2026-11-28 | — (W-337i1 again for `m1.7.6`, D-1011) | rows 1 – 6, PR #81 to WL-A-2 | session (owner: "commit and push W-422, open the PR", "merge #81, release m1.7.6") | PR #81 run 46095907919 `success` on c97216c1 (unit, integration, gate); merged `e1b30700` (parents `ee1b155d`, `c97216c1`), no difference from c97216c1; `master` run 46096560534 `push` `success`; tag `m1.7.6` → `e1b30700` pushed; worktree `../wealthlens-m1.7.6-pg`; jar 120,973,974 bytes, sha256 `b2bd9834e108…`, the finder's new order and the tool's entry present; WL-A-2 published from it, read back 259,068 characters holding the file's body — **go**; `m1.7.6` is the build W-337i4 – i6 run |
| 2026-11-28 | staging (W-337i4, D-1011) | row 9 again, 8082 on `m1.7.6` | owner | jar 120,973,974 bytes; `m1.7.5` stopped (log kept as `m175.log`); `m1.7.6` `Started` in 9.585 s; no ERROR; WARN the three of row 9; Flyway validated 13, up to date; `Charges engine ready: 39`; 8081 (`m1.6.1`) still up — **go** |
| 2026-11-28 | staging (W-337i4, D-1011) | row 10 again, §0.10 step 2 on `m1.6.1` / `m1.7.6` (tool from `../wealthlens-m1.7.6-pg`; the first answers kept as `before-1`, `after-1`) | owner | `spec saved: 127 paths`; 455 answers before and after; **`exit: 0`, `0 differences outside the list`**; `not reached` the same 28 routes on both sides — **go**; §0.10 done on staging |
| 2026-11-28 | staging, Aiven `wealthlens-staging` (W-337i4, D-1011) | row 11, §1B on 8082 (`m1.7.6`) | owner | `expires_in 900`; seed `{"catalogue":0,"created":0,"skipped":39,"instruments":0,"instrumentsSkipped":2,"drift":0}` (the copy holds every card); drift `0`; unverified `[]`; simulate `ZERODHA_EQ_DELIVERY_2025_04`, `RESOLVED`, **119.67** — BROKERAGE 0, STT 100, EXCHANGE_TXN 2.97, SEBI_FEE 0.1, IPFT 0.1, DP 13.5, GST 3.0 — **go**; §1B done on staging |
| 2026-11-28 | staging (W-337i4, D-1011) | row 12, the routes on 8082 (`m1.7.6`) | owner | `HTTP/1.1 200` with `X-Request-Id`; `my stocks 200`; `spec paths 127`; `trade without stock: 400 A trade must name its stock code` — **go** |
| 2026-11-28 | staging (W-337i4, D-1011) | row 13, stop both | owner | `logout 204`; 8081 and 8082 stopped; ERROR lines: `m17.log` (`m1.7.6`) **0**, `m175.log` (`m1.7.5`) **0**; `m1.6.1`'s grouped, each a refusal it logs at ERROR (`ControllerAdviser.java:231` at `m1.6.1`): "No erasure was requested for [email]" ×4 (`AccountErasureService.java:56`), "Stock not found" ×2 (`PortfolioQueryService.java:51`), "No charges recorded for transaction …" ×2 each (`UserChargeService.java:115`, row 10's reads; staging holds no `user_charges`) — **go**, the rest of the list checked at row 14 |
| 2026-11-28 | staging (W-337i4, D-1011) | row 14, the run's files removed | owner | `m161.log` ERROR lines of any other kind `0`; `w337i4`, `w406` (logs, `users.txt`, answers) and `staging.env.before-w337i4` gone ("No such file or directory" ×3); `staging.env` kept, `-rw-------`, 605 bytes, now holding the target's values; secrets unset — **go**; sign-off asked |
| 2026-11-28 | staging, `it-staging` → Aiven `wealthlens-staging` (W-337i4, D-1012) | sign-off | owner | "staging signed off" — staging on `m1.7.6` / PostgreSQL; §0.9, §0.10 and §1B logged for staging; `it-staging` kept, unused, as the way back (D-991 C5a); W-337i4 done |
| 2026-11-28 | prod, `Cluster0` → Neon `production` (W-337i5, D-1013) | row 1, environment (from `prod.env`, a fresh terminal) | owner | keys 32 bytes, prefixes 8d910c6ebabf / f9bf756780af / af38366a5db3 (= W-337i3 row 1); PRIVACY_CONTACT 25; owner email 25; Mongo user set; PG URL not yet set; 8081, 8082 free; jars 91,330,043 (`m1.6.1`) and 120,973,974 (`m1.7.6`); images `2` — **go** |
| 2026-11-28 | prod (W-337i5, D-1013) | row 2, `prod.env` gains the target (backup `prod.env.before-w337i5` first) | owner | `-rw-------`, 617 bytes; 12 names, the last three `APP_POSTGRES_DATASOURCE_*`; password length 16; host part `ep-holy-meadow-a1f4tyxx.ap-southeast-1.aws.neon.tech neondb` — **go** |
| 2026-11-28 | prod, Neon `production` (W-337i5, D-1013) | row 3, §0.9 step 2, the target (read-only) | owner | `PostgreSQL 18.7 (3190d2f) on aarch64`; **schema `wealthlens` `0` — absent**; tables `0` — **stop**: runbook §2.19 and "Next deploy" line 4 say prod's schema was made (W-337h); read-only listing asked |
| 2026-11-28 | prod, Neon `production` (W-337i5, D-1013) | row 3, read-only listing | owner | `neondb` / `neondb_owner`; databases `neondb`, `postgres`, **`wealthlens`**; schemas in `neondb`: `public` only; no tables — W-337h's `wealthlens` seems to be a database, not a schema in `neondb`; its contents asked — **hold** |
| 2026-11-28 | prod, Neon `production` (W-337i5, D-1013) | row 3, the `wealthlens` database (read-only) | owner | database `wealthlens`, owner `neondb_owner`; schemas `public`, `wealthlens`; no tables — W-337h made the schema in a database `wealthlens`, not in `neondb` as §2.19 records; decision asked (B: the schema in `neondb`, recommended; A: prod on database `wealthlens`) — **hold** |
| 2026-11-28 | prod (W-337i5, D-1013) | row 3, ruling | owner | "B": prod stays on `neondb`; §2.19 step 2 run there now; the empty `wealthlens` database left as it is, noted (its removal a separate decision) |
| 2026-11-28 | prod, Neon `production` `neondb` (W-337h correction, W-337i5, D-1013) | §2.19 steps 1 – 3 on `neondb`; row 3 again | owner | before `neondb\|public\|0`; `CREATE SCHEMA IF NOT EXISTS wealthlens`; after `wealthlens\|neondb_owner`; row 3: `PostgreSQL 18.7`, `1`, `0` — **go**. **Correction to 2026-11-23's line:** W-337h had made the schema in a separate database `wealthlens` (left empty), not in `neondb`; prod's schema in `neondb` dates from today |
| 2026-11-28 | prod `Cluster0` (W-337i5, D-1013) | row 4, §0.9 step 1, the source (read-only, IntelliJ console) | owner | `database` `Cluster0`, `counted` 42, `loginsWithoutEmail` 0, `unknownWithData` `[]`; `nonZero` 13, every count equal to 2026-11-27's dump (W-337i3 row 3): user_details 12, refresh_tokens 4, charge_catalogue 13, charge_schedules 39, charge_instruments 2, corporate_action 6, lastly_performed_corporate_action 11, assets 518, transactions 788, trade_outcomes 163, profit_and_loss 7, privacy_consents 1, privacy_processing_log 91 — **go** |
| 2026-11-28 | prod `Cluster0` (W-337i5, D-1013) | row 5, the old-forms survey (read-only) | owner | `assetsWithTotalValue` 220; `transactionsWithoutTimezone` 716; corporate-action keys in assets and in transactions: actionPrice, exDate, recordDate, stockCode, stockName, toStockCode, toStockName 5 entries each, createdAt, createdBy 3 each; `transaction_date` 518 / 788 dated, `at1830Utc` 1 each, `otherTimes` 0; `maturity_date` 1 each at 00:00Z — equal to the forms the copier named on prod's copy (W-337i3 row 7) — **go**; row 5b's answer asked |
| 2026-11-28 | prod `Cluster0` (W-337i5, D-1013) | row 5b, camelCase top-level keys (read-only) | owner | `camelKeys` `[]` — **go** |
| 2026-11-28 | prod (W-337i5, D-1013) | row 6, §0.9 step 3, the copy (`m1.7.6`, first app connection to Neon with `channelBinding=require`) | owner | `exit: 0` — **go** |
| 2026-11-28 | prod (W-337i5, D-1013) | row 7, §0.9 step 4, the report | owner | `41`, `0`, `1`; 13 non-empty sources, each source = target = row 4 (12, 13, 11, 1, 91, 4, 6, 39, 2, 518, 788, 163, 7); assets: the nine CorporateActionEntity keys (5 each, createdAt/createdBy 3), `1 × date at midnight Asia/Kolkata`, `220 × total_value left out`, clean; transactions: the same keys, 1 date, `716 × timezone_id absent`, clean — equal to row 5 and to W-337i3 row 7 — **go** |
| 2026-11-28 | prod, Neon `production` (W-337i5, D-1013) | row 8, §0.9 step 5, after (read-only) | owner | `12\|788\|518\|163\|7\|0` = row 4; Flyway `1:true` … `13:true` — **go**; §0.9 done on prod |
| 2026-11-28 | prod (W-337i5, D-1013) | row 9, both builds up (prod profile) | owner | `m1.6.1` (8081, `Cluster0`) `Started` in 8.649 s; `m1.7.6` (8082, Neon) in 12.356 s; no ERROR; WARN malware scan off (both), `open-in-view` and SpringDoc (`m1.7.6`), SpringDoc (`m1.6.1`); Flyway validated 13, up to date; `Charges engine ready: 39` — **go** |
| 2026-11-28 | prod, Neon `production` (W-337i5, D-1013) | row 10, §0.10 step 1, the two lists | owner | logins `12`; `workspaces.txt` 0 lines; roles `ADMIN,USER,SUPER_USER,` × 1, `ADMIN,USER,TEST_USER` × 2, `USER` × 4, `USER,TEST_USER` × 5 (= prod's copy, W-337i3 row 10) — `users.txt` the owner's line with `ADMIN,USER,SUPER_USER` (trailing comma left out) — **go** |
| 2026-11-28 | prod (W-337i5, D-1013) | row 10, §0.10 step 2, the read comparison (`m1.6.1` on `Cluster0` / `m1.7.6` on Neon) | owner | `spec saved: 127 paths`; 591 answers before and after (= the rehearsal); **`exit: 0`, `0 differences outside the list`**; `not reached` the same on both sides (28 routes) — **go**; §0.10 done on prod |
| 2026-11-28 | prod, Neon `production` (W-337i5, D-1013) | row 11, §1B on 8082 (`m1.7.6`); the answers removed first | owner | `before`, `after` gone (`2`); `expires_in 900`; seed `{"catalogue":0,"created":0,"skipped":39,"instruments":0,"instrumentsSkipped":2,"drift":0}`; drift `0`; unverified `[]`; simulate `ZERODHA_EQ_DELIVERY_2025_04`, `RESOLVED`, **119.67** — BROKERAGE 0, STT 100, EXCHANGE_TXN 2.97, SEBI_FEE 0.1, IPFT 0.1, DP 13.5, GST 3.0 — **go**; §1B done on prod |
| 2026-11-28 | prod (W-337i5, D-1013) | row 12, the routes on 8082 (`m1.7.6`) | owner | `HTTP/1.1 200` with `X-Request-Id`; `my stocks 200`; `spec paths 127`; `trade without stock: 400 A trade must name its stock code` — **go** |
| 2026-11-28 | prod (W-337i5, D-1013) | row 13, stop both | owner | `logout 204`; 8081 and 8082 stopped; `m17.log` (`m1.7.6`) ERROR **0**; `m1.6.1`'s 451, each a refusal it logs at ERROR (`ControllerAdviser.java:231` at `m1.6.1`): "No charges recorded for transaction <id>" ×448 (`UserChargeService.java:115`; prod holds no `user_charges`), "No erasure was requested for [email]" ×2 (`AccountErasureService.java:56`), "Stock not found" ×1 (`PortfolioQueryService.java:51`) — the rehearsal's 451 — **go** |
| 2026-11-28 | prod (W-337i5, D-1013) | row 14, part 1 (read-only, by the session) | session | `m161.log` ERROR lines of any other kind `0`; the run's files still present (`w337i5`, `w406` with `users.txt` and `report.txt`, `prod.env.before-w337i5`) — the removal not yet run; owner's "prod signed off" received, recorded once row 14's removal is pasted |
| 2026-11-28 | prod (W-337i5, D-1013) | row 14, the run's files removed | owner | `w337i5`, `w406` (logs, `users.txt`, `report.txt`) and `prod.env.before-w337i5` gone ("No such file or directory" ×3); `prod.env` kept, `-rw-------`, 617 bytes, now holding the target's values; secrets unset — **go** |
| 2026-11-28 | prod, `Cluster0` → Neon `production` (W-337i5, D-1014) | sign-off | owner | "prod signed off" — prod on `m1.7.6` / PostgreSQL; §0.9, §0.10 and §1B logged for prod (and so for every database); `Cluster0` kept, unused, as the way back (D-991 C5a); W-337i5 done |
| 2026-11-28 | staging, prod (W-337j3a, D-1020) | §2.15 – §2.17 sized on PostgreSQL (read-only) | owner | both: no `DUMMY` / `LAVANYA` / `DUMMY3` row, no row without `asset_type`, `icici lot 6835e328` 0, `reports table` 0 — the three cleanups moot; retired with §0.2, §0.3, §0.5, §1.o, §1.j |
| 2026-11-28 | — (W-337i1's steps for `m1.8`, D-1018) | PR #85 to WL-A-2 | session (owner: "merge #85, release m1.8") | PR #85 run 46103853759 `success` on f979b2af; merged `ce445f6f`; `master` run 46104306496 `push` `success`; tag `m1.8` → `ce445f6f` pushed; worktree `../wealthlens-m1.8-pg`; jar 115,408,953 bytes (5.6 MB smaller: no MongoDB driver), sha256 `8440d4c56b20de92…`, 0 entries naming mongo; WL-A-2 published from it, read back whole — **go**; `m1.8` is the build staging and prod start next ("Next deploy" line 2) |
| 2026-11-28 | staging, prod (W-337j2, D-1018; "Next deploy" line 2) | `m1.8` started without `MONGO_USER` / `MONGO_PASSWORD` (8082, then stopped) | owner | jar 115,408,953 bytes; staging `Started` in 8.956 s, prod in 11.267 s; each log: mongo lines **0**, ERROR **0**, `Charges engine ready: 39 rate card(s)`; `/privacy/rights` 200 on both; run folder removed — **go**; staging and prod on `m1.8`; W-337j4's week starts (due 2026-12-05, D-1016 C2a) |
| 2026-11-28 | — this machine (W-337i6 row 2, D-1015; D-848 C1a, D-991 C6a) | the W-334 MongoDB archives deleted | owner | `ls -la ~/wealthlens-runs/archives/`: the six files of 2026-11-21 (`prod-after-m14.archive` 1,635,293 B, `prod-after-m14.txt` 3,932, `prod-dump.out` 5,451, `staging-after-m14.archive` 1,372,936 B, `staging-after-m14.txt` 3,989, `staging-dump.out` 5,838), each 600; the two archives `rm -P`, the rest `rm`, the folder `rmdir`; after: `ls: …/archives: No such file or directory` — **done**; no way back (the data is in PostgreSQL, `Cluster0` / `it-staging` kept until W-337j4) |
| 2026-11-28 | prod, Neon `production` (W-337i6 row 4, D-1015 C2a) | the empty database `wealthlens` dropped | owner | before: tables outside the catalogues in `wealthlens` `0`; `DROP DATABASE wealthlens;` silent; after: databases `neondb`, `postgres`; `neondb`'s `wealthlens.user_details` `12` (prod's data untouched) — **done**; way back `CREATE DATABASE wealthlens;` (it held nothing) |
| 2026-11-28 | dev, Aiven `wealthlens-dev` (W-337j3b step 2, D-1022 C1a, C2a) | the SQL proof: `r.sql` (18 read-only queries, session read-only) and `w.sql` (writes in `BEGIN … ROLLBACK`) | owner | every query and statement ran, no error. Q1 0 rows; Q2 0; Q3 0/0/0; Q4 0/0; Q5 0; Q6 `left_rows` 60 (owner's stored email matches), `held_overdue` 0; Q7, Q8 0 rows; Q9 0; Q10 0/0; Q11 users 1, not_counted 0, 0, 0; Q12 1/1/0, unique 1; Q13 0; Q14 1/1/0; Q15 catalogue 13, schedules 40, instruments 2, user_charges 0, charge_accounts 0, pnl_charge_side 0; Q16 1; Q17 0/0/0; Q18 0. W1 COPY 0 ×4; W2 0 rows each; W3 INSERT 0 0 ×2; W4 0/0; W5 UPDATE 1; W6 DELETE 13/40/2; W7 `refused`; W8 0 ×5, UPDATE 0; W9 13/40/0/0 = Q15 (nothing kept); CSV files removed, folder holds only the scripts. Dev holds none of the owner's lots, P&L or charges, so W2 and W8 moved 0 rows there; with rows they were run on a throwaway `postgres:18` from the 13 Flyway scripts (2 lots, 2 P&L, 1 charge with line and amount: DELETE/COPY 2, UPDATE 2, charge side left 0, as worked by hand). Q15's 40 schedules: the repository ships 39 cards (39 files under `data/charges/`, one card each; staging and prod log 39), so dev holds one card not seeded from the repository |
| 2026-11-28 | staging, Aiven `wealthlens-staging` (W-337j3b step 2, D-1022 C1a: rows dev lacks) | `r.sql` only (read-only) | owner | no error. Q1 0 rows; Q2 0; Q3 0/0/0; Q4 0/0; Q5 0; Q6 1632, held 0; Q7 2 rows (one principal, two addresses, 1 attempt each); Q8 1 row, the owner, 3 subjects; Q9 0; Q10 0/0; Q11 users 14, not_counted 13 (the jobs ship off on staging, so purpose-lapse never ran), 0, 0; Q12 14/0/1, unique 1; Q13 0; Q14 14/1/13; Q15 13/39/2/0/0/0; Q16 1; Q17 57/3/224; Q18 130 (every staging `trade_outcomes` row was written with the owner as caller). Then every rewritten block of the 14 sections run in order on a throwaway `postgres:18` from the 13 Flyway scripts with hand-made rows — each `COPY`, `DELETE`, `UPDATE` and `INSERT` count as worked by hand; it found that `psql -c` does not substitute `:'…'` variables, so those lines take `<<<` |
| 2026-11-28 | staging, Aiven `wealthlens-staging`; prod, Neon `production` (§1.h, W-358d3, D-885; "Next deploy" line 1) | §1.h's count in SQL (D-1007 C2a, D-1013 C1a), read-only | owner | staging users 14, upgraded 1; prod users 12, upgraded 1 — read six days after the 2026-11-22 reading (due 2026-11-29); `upgraded` unchanged at 1 on each (the owner's sign-in); the other accounts keep their BCrypt hash, which still signs in |
| 2026-11-28 | dev, staging, prod (W-337j3c step 2, D-1024 C1a) | §3's 23 checks in SQL (`c.sql`, read-only session; 3.25 apart) | owner | no error on any. **prod:** 3.1 2 / 61 (= the row); 3.2 0, 3.3 0 / 0, 3.4 0, 3.5 0 / 0 (D-824 read 73, 1 / 48, 73, 2 / 49 on the rehearsal copy before W-348b's rebuild wrote the new facts, D-836); trades BMF 178 / BSE 136 / NSE 474, all `DELIVERY`; 3.6, 3.7 none; 3.8 0 / 0; 3.9 0, no split; 3.10 0 / 0; 3.11 0 (= the row); 3.12, 3.13 0; 3.14 21; 3.15, 3.16, 3.20, 3.21 (0 trade rows), 3.22 – 3.24 none; 3.26 `MOTHERSON ` in `transactions` 3 and `trade_outcomes` 2 (= the row); 3.27 outside 178, held 0 (= the row). **staging:** 3.1 2 / 40; 3.2 – 3.5 0; trades BMF 99 / BSE 118 / NSE 443; 3.8 lots 4, sales 0; 3.11 1; 3.14 26; 3.27 99, held 1; the rest 0 or none. **dev:** every check 0 or none; one split ratio `2:1`, none performed. Before them the script ran on a throwaway `postgres:18` from the 13 Flyway scripts with rows planted for each check (null and missing cases included): every count as worked by hand |
| 2026-11-28 | staging, prod (W-337j3c step 2, D-1024) | `f.sql`, read-only: 3.14's lots by how the sell list is stored; §4's 31-March count | owner | staging `sells_null` 0, empty list 26; prod 0, 21 — 3.14's SQL counts what MongoDB's did. 31 March: staging 0, prod 2 (0 on 2026-09-12, after the fix) |
| 2026-11-28 | dev, staging, prod env files (W-337j3d rows 1 – 3, D-1026) | `MONGO_USER` / `MONGO_PASSWORD` removed from `~/wealthlens-runs/{dev,staging,prod}.env` | owner | dev trimmed first (its lines start `export `); `m1.8` on dev from the trimmed file: `Started WealthLensApplication in 9.345 seconds`, ERROR 0. Staging and prod hold the lines without `export `, so the first `sed` left them; backed up again and trimmed with `/^(export )?MONGO_(USER|PASSWORD)=/d`. Check: `0 mongo` on all three, ten names each. Dev's backup was removed with the start log before the staging / prod rerun (dev had started clean) |
| 2026-11-28 | dev, staging, prod env files (W-427 step 2, D-1034) | `DATABASE_URL` / `_USERNAME` / `_PASSWORD` added beside `APP_POSTGRES_DATASOURCE_*` (backup `*.env.pre-w427` first; values copied by `sed`, never printed) | owner | names listed: both sets on all three; a session check compared each pair without printing — URL, USERNAME, PASSWORD the same on dev, staging, prod; files still `600`. The old lines stay until the release carrying W-427 starts on each ("Next deploy" line 1) |
| 2026-11-28 | — (release `m1.9`, W-428a, D-1037) | PR #86 to tag | session (owner: "merge #86", "release m1.9") | PR #86 run 46113161265 `success` on 8e76c49a; merged `0cc55aff` (parents ce445f6f, 8e76c49a; tree equal to 8e76c49a); `master` run 46113536094 `push` `success`; tag `m1.9` → `0cc55aff` pushed; worktree `../wealthlens-m1.9-pg`; jar 115,413,484 bytes, sha256 `42caf6954562d97b…`, 0 entries naming mongo, 3 `PlainTextRewrite` entries; WL-A-2 already published from the same runbook (8e76c49a) — **go**; `m1.9` is the release "Next deploy" lines 1 and 3 name |
| 2026-11-28 | dev, Aiven `wealthlens-dev` (W-428b, §2.20, D-1037) | the plain-text rewrite, with `m1.9` | owner | jar sha256 `42caf695…e347` matched; dump `dev-pre-w428a.dump` 121,758 B, 54 TABLE DATA; before: 16 columns sealed (`charge_catalogue`, `charge_instruments`, `charge_schedules`, `corporate_action` actors; `privacy_consents` 3; `privacy_processing_log` principal / remote_address / subject_email 59 each; `refresh_tokens.email` 4; `user_details.email` 1); run `exit: 0`, "299 value(s) made plain in 16 column(s)", the same 16, no ERROR, Tomcat acceptor WARN (port -1, expected); after: `(0 rows)`; normal start `Started` in 9.086 s, Flyway v13 up to date, 40 rate cards; owner sign-in `200` — **go**; dev on `m1.9` |
| 2026-11-28 | staging, Aiven `wealthlens-staging` (W-428b, §2.20, D-1037) | the plain-text rewrite, with `m1.9` | owner | jar sha256 `42caf695…e347` matched; **step 1 skipped** (the paste went from step 0 to step 2): no dump before the rewrite, so the way back to `m1.8` is Aiven's own service backup from before 06:04:55Z only (retention not given, §1.s); before: 34 columns sealed (`assets` 4, `trade_outcomes` 4, `transactions` 4, `profit_and_loss` 3, `lastly_performed_corporate_action` 3, `privacy_processing_log` 3 × 1017, charge and consent actors, `refresh_tokens.email` 5, `user_details.email` 14); run `exit: 0`, "6991 value(s) made plain in 34 column(s)", each column's count equal to the check before (sum 6,991), no ERROR, Tomcat acceptor WARN (port -1, expected), 5 m 56 s; dump after `staging-post-w428a.dump` 227,215 B, 54 TABLE DATA (a first try from `~` without the env file failed at authentication, wrote nothing, its empty file removed); after: `(0 rows)`; normal start with `staging`, owner sign-in `200` — **go**, with the deviation; staging on `m1.9` |
| 2026-11-28 | prod, Neon `production` (W-428b, §2.20, D-1037) | the plain-text rewrite, with `m1.9` | owner | jar sha256 `42caf695…e347` matched; dump `prod-pre-w428a.dump` 261,883 B, 54 TABLE DATA, taken before the rewrite (the run was gated on it); before: 34 columns sealed, 6,733 values (`assets` 4, `trade_outcomes` 4, `transactions` 4, `profit_and_loss` 3, `lastly_performed_corporate_action` 3, `privacy_processing_log` 3 × 685, charge and consent actors, `refresh_tokens.email` 5, `user_details.email` 12); run `exit: 0`, "6733 value(s) made plain in 34 column(s)", each column's count equal to the check before, no ERROR, 06:18 – 06:27Z; after: `(0 rows)`; normal start with `prod` (a first sign-in answered `000`, the app not yet started), then owner sign-in `200` — **go**; prod on `m1.9`. W-428b done on all three; W-428c may start |
| 2026-11-28 | dev, staging, prod env files (W-427, D-1034; "Next deploy" line 1) | the three `APP_POSTGRES_DATASOURCE_*` lines removed after `m1.9` started on each (copy `*.env.pre-w427-cleanup` first; `sed -i '' -E '/^(export )?APP_POSTGRES_DATASOURCE_(URL\|USERNAME\|PASSWORD)=/d'`) | owner | each file: old names 0, `DATABASE_*` 3, mode `600` — **go**; line 1 done for all three |
