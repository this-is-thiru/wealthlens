# WealthLens — deploy runbook

**Everything a database needs at a deploy, and how to check it, in the order it runs.** This one runbook covers:
- **the charge engine** (`brokercharges`): seeding, clearing, its standing operations;
- **the trade ledger** (`portfolio`): the prechecks before its unique indexes, and the `version` backfill;
- **tax planning** (`taxplanning`, `taxengine`), and any other module touched because of it, such as `portfolio`
  (W-085 – W-088u7) and `shared`.

`docs/charges-engine/production-runbook.md` and `docs/trade-ledger/migration.md` are pointers to the sections here
(W-176, D-379). `docs/charges-engine/staging-runbook.md` stays the charge engine's **test guide**: §1B uses its §4.1
figure, and its §5, §5b and §7 are optional testing, not deploy steps.

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
(§0.5, §1A, §2.1 – §2.5, §1.s, §1.r) is the owner's own copy, and Act s.8(7)(a) reaches it as it reaches the database. Delete
it once its step's result is in the run log and checked, or once the longest snapshot retention in §1.s has passed,
whichever is first. The run-log line says "export deleted".

**Databases** (from the profile yamls, host and database name only). Each cluster holds one application database:
staging's app writes `it-staging`, prod's writes `Cluster0` (owner's counts, 2026-10-27; this settles the earlier
open question).

| Profile | Host | Database |
|---|---|---|
| default, `staging` | `it-staging.6fu8nvy.mongodb.net` | `it-staging` |
| `prod` | `cluster0.x6trgen.mongodb.net` | `Cluster0` |

`$BASE` is the app's base URL, `$ADMIN_TOKEN` a `SUPER_USER` JWT and `$TOKEN` any user's JWT. `mongosh` steps run
against the named database.

**The order, from a fresh start** (owner, 2026-10-27: "we can run all runbook from starting"):

| § | Step | prod `Cluster0` | staging `it-staging` |
|---|---|---|---|
| 0.1 | Environment, replica set, a `SUPER_USER` | check | check |
| 0.2 | Duplicate checks before the unique indexes | run | run |
| 0.3 | `profit_and_loss.version` present | **done**: 0 without (2026-10-27) | **done**: 0 without (2026-10-27) |
| 1 | Deploy; startup checks | check | check |
| 1A | Clear the charge engine's data | configuration only: 0 / 0 / 0, a no-op | configuration 12 / 11 / 2; `user_charges` 328, `charge_accounts` 1, P&L charge side 2 |
| 1B | Seed the charge engine; drift, unverified, one simulate | run | run |
| 2.0 – 2.14 | Tax planning, by hand, in order | per step | per step |
| 3 | Standing data conditions, read-only | check | check |
| 5 | Standing operations (not deploy steps) | as needed | as needed |

**Why 1A and 1B come straight after the deploy:** the charge flags ship on (`engine-enabled`, `shadow-recording`
and `authoritative` are `true` in all three profile yamls), so a V2 trade is priced as soon as the app is up. Clear
and seed before anyone trades. On staging the owner is the only user.

---

## 0. Before deploying

### 0.1 Environment · owner · **every deploy**

| # | Check | Why |
|---|---|---|
| 0.1.1 | `MONGO_USER`, `MONGO_PASSWORD`, `JWT_SECURITY_KEY`, `TAX_DATA_KEY`, `TAX_DIGEST_KEY`, `PRIVACY_CONTACT` are set | The app does not start without the JWT key, deliberately: a defaulted signing key is a forged-token vulnerability. Nor without the two tax keys (W-134, D-447): they encrypt stored personal data and digest PANs, §0.5. Nor without the privacy contact (W-235; app-wide and renamed from `TAX_PRIVACY_CONTACT` by W-237a, never set under the old name): who answers questions about the processing of personal data, returned in every erasure receipt (DPDP Rules, 2025 r.9), §1.z |
| 0.1.2 | MongoDB is a **replica set** | Multi-document `@Transactional` writes need one. Without it `app.mongodb.transactions-enabled` cannot help, and `TransactionSafetyAuditor` warns at startup (§1.7) |
| 0.1.3 | A `SUPER_USER` account exists | §1B, §2 and §5 need one: seeding, publishing rate cards, the AMC cycle and every `/admin`-style correction are `SUPER_USER`-only. Registration makes only `USER` accounts (W-255), so the owner grants the role in that database: register through `POST /auth/register`, then `db.user_details.updateOne({ email: "<stored owner email>", roles: "USER" }, { $set: { roles: "SUPER_USER" } })` with the stored form from §0.7 (from W-253 the email is stored encrypted and is no longer the `_id`), then sign in again (a token carries the role read at sign-in). A second run matches nothing. Way back: the same update with the two roles swapped |

### 0.2 TL-7 — duplicates that would block a unique index · owner, `mongosh` · **every database · before the deploy that first carries TL-7**

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

### 0.3 Trade ledger — `version` on `profit_and_loss` · owner, `mongosh` · **every database · before the deploy · done**

**Why.** `ProfitAndLossEntity` carries `@Version private Long version`. On a document written without the field, Spring
Data reads the null version as a new document and inserts against an existing `_id`
(`E11000 duplicate key error … profit_and_loss index: _id_`), so every trade for an existing user in an existing year
fails. `LegacyDocumentUpgradeIntegrationTest` proves it against a real MongoDB.

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

### 0.5 W-134 (D-447) — the tax keys, and the tax-planning collections cleared · owner · **every database · before the first deploy carrying W-134**

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
  `tax_` prefix and are **not** touched.

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

   A second run returns `dropped: false` for each and changes nothing.
5. **After the deploy** (read-only): §1.x's `Ensured the indexes of` lines; then create one profile and check
   `db.tax_profiles.find({}, { pan: 1, pan_digest: 1 })` shows `pan` beginning `k1:` and a 64-character `pan_digest`.
   `tax_access_log` gains a row for each read of a profile, family, draft or computation: never cleared, it is the
   O4 audit record.

**Way back.** Deploy the previous build, then `mongoimport` each step-3 file into its collection. Rows written by the new
build are encrypted and the previous build cannot read them: drop those collections again first.

### 0.6 W-134a (D-448) — the legacy salary data, counted; the deploy held until W-159 · owner, `mongosh` · **every database · before the one deploy**

**Why.** `salary_profiles` and `tax_computations` (the `/tax-planning` salary flow) hold salary structure, deduction
figures, a disability flag and pasted CTC text in plain text, which `DR 14` O1.1 says must be encrypted. W-134's
encryption does not cover them. W-159 removes the module and these collections instead (D-448 C1a), so the counts
here are what W-159's plan has to account for.

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

## 1. On every deploy — startup, automatic; confirm in the log

| # | Item / decision | What happens at startup | Check | If it fails |
|---|---|---|---|---|
| 1.1 | **W-079a** (D-210), **W-088s** (D-268) | `TaxEngineConfig` compiles every year in `rulepacks/shipped-years.json` (**2025-26, 2026-27**) and verifies each against its lock (`rulepacks/<year>.lock.json`) | The app starts. There is no database step: the rule packs ship in the JAR | **The boot stops** with a lock mismatch. Nothing reaches a database. Deploy the previous build; never edit a lock in place |
| 1.2 | **W-088u8a** (D-281, D-282) | **Nothing.** Tax policies are no longer seeded at boot: see §2.0 | — | — |
| 1.3 | **W-088u8c** (D-285) | **Nothing.** The W-086a financial-year rewrite no longer runs at boot: see §2.3. No `CommandLineRunner` remains | — | — |
| 1.4 | **W-088u8b** (D-284) | **Nothing.** `trade_outcomes` is no longer rebuilt at boot when empty: see §2.2 | — | — |
| 1.5 | Charge engine (ADR-27) | `ChargeReadinessAuditor` counts the rate cards when the engine is pricing | Grep the log for `NO RATE CARDS`. Expected on a database never seeded (prod today) until §1B runs; `Charges engine ready: N rate card(s)` otherwise | Run §1A / §1B before anyone trades |
| 1.6 | TL-7; charge engine | `PortfolioIndexInitializer` and `ChargeIndexInitializer` create their collections' indexes | Grep the log for `Could not create index` (ERROR from `portfolio`, WARN from the charge engine) | The app has started without that constraint. A unique one almost certainly means duplicates: §0.2, then restart |
| 1.7 | Transactions | `TransactionSafetyAuditor` checks for a `MongoTransactionManager` | Grep the log for `multi-document transactions are DISABLED` | Not a replica set, or `app.mongodb.transactions-enabled` is off (§0.1.2) |

---

### 1.y W-137 (D-449) — the background jobs · owner · **first deploy carrying them, then after the first runs**

Automatic: `JobIndexInitializer` creates the lease's TTL index. **Confirm** the log holds `Ensured the indexes of JobLease`. The jobs run only where `app.tax.jobs.enabled` is true (all three profile yamls): `auto-archive` at 03:00 each Sunday and `retention-purge` at 04:00 on the 1st, in the server's zone (`app.tax.jobs.*-cron`). Each logs `Job <name> archived draft <id>` or `deleted draft <id> of tax year <year>` per draft — ids only. **W-138 (D-450):** `staleness-sweep` runs at every start under the same switch and logs `Job staleness-sweep flagged N draft(s)`; after a deploy that changes a pack (a new `law` or `exec` version in a lock), expect N to be the ready and filed drafts computed under the old one, and 0 on the next start. **W-257 (D-487):** a row a job writes carries the job's name as its actor (`audit_metadata.updatedBy`, or `createdBy` on a new row: `auto-archive`, `staleness-sweep`, `retention-purge`), stored like any actor (§0.7 gives the form to query); rows written before W-257 keep `unknown`. Information only — no step.

**After the first run of each** (read-only):

```js
db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
  .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n);
    return { database: n, failures: d.tax_job_failures.find({}, { _id: 0, job: 1, subject: 1, reason: 1 }).toArray(),
      leasesHeld: d.tax_job_leases.countDocuments({}) }; }) }])
```

Expected: no failures, and `leasesHeld` 0 between runs. A failure naming a tax year means a filed draft of a year no shipped pack covers: it is **kept**, and the row stays until the owner decides (ship that year's pack, or a D-row). Nothing to run twice; the collections are new.

**From W-239 (D-505)** the monthly `retention-purge` also deletes **landlords and lenders** once `app.tax.retention.third-party-after` (P2Y, all three profile yamls) has passed since the end of the last tax year the record can support (`DR 14` O5). From W-239a (D-506) that year is the later of the latest tax year of a stored draft **naming** the record (`/thirdParties/landlordIds`, `/thirdParties/lenderIds` in its inputs) and the year the record was last entered or changed; a record no draft names supports only the year of its last change. Drafts stored before W-239a name nothing, so on the first run their records follow that second rule. Each deletion logs `Job retention-purge deleted landlord <id>` or `… deleted lender <id>`; a record with no stored date is **kept** and logged as a refusal in `tax_job_failures` (subject `landlord <id>` / `lender <id>`). Nothing to seed or run. **Before the first run carrying W-239, and after it** (read-only; the drop is the records past their period):

```js
db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
  .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n);
    return { database: n, landlords: d.tax_landlords.countDocuments({}), lenders: d.tax_lenders.countDocuments({}),
      undated: d.tax_landlords.countDocuments({ "audit_metadata.updatedAt": null, "audit_metadata.createdAt": null })
        + d.tax_lenders.countDocuments({ "audit_metadata.updatedAt": null, "audit_metadata.createdAt": null }) }; }) }])
```

Expected after: the counts fall by the number of `deleted landlord|lender` log lines, and `undated` equals the third-party refusals. A second run deletes nothing more. **Way back:** restore the two collections from the backup taken before the deploy — deleting them is the point, so only if a record was deleted in error.

**Way back:** set `app.tax.jobs.enabled=false` and restart; nothing is scheduled, and stored drafts stay as they are. A draft the purge deleted is gone: restore it from the database backup.

### 1.z W-235 — erasure on request · owner · **first deploy carrying it, then after the first purge that erases**

A user's `POST /tax/user/{email}/profiles/{profileId}/erasure` holds the profile at once (`erasure_requested_at`, its
`pan_digest` removed) and answers `202` with the erase date, one year later (`app.privacy.erasure-hold: P1Y` since W-237d, DPDP
Rules, 2025 r.8(3)). The monthly `retention-purge` (§1.y) then erases the profile and everything under it — drafts of every
status, revisions, computations, family, employers, landlords, lenders, bank accounts, workspaces, regime election history,
and the access records naming any of them — logging `Job retention-purge erased profile <id>`. Nothing to seed or correct.

**Before the first start:** `PRIVACY_CONTACT` set (§0.1.1) to the contact the owner publishes; the start is refused
without it. **After a purge that logged an erasure** (read-only; replace `<id>`):

```js
db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
  .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n); const id = "<id>";
    return { database: n, left: ["tax_family_members", "tax_employers", "tax_landlords", "tax_lenders", "tax_bank_accounts",
      "tax_year_workspaces", "tax_filing_drafts", "tax_regime_election_history"].reduce((sum, c) =>
        sum + d.getCollection(c).countDocuments({ profile_id: id }), d.tax_profiles.countDocuments({ _id: id })),
      heldOverdue: d.tax_profiles.countDocuments({ erasure_requested_at: { $lte: new Date(Date.now() - 366 * 864e5) } }) }; }) }])
```

Expected: `left` 0, `heldOverdue` 0 (a held profile past its year still present means the purge did not run or failed:
§1.y's failure check). A second purge changes nothing. **Way back:** none for an erased profile — erasure is the point;
before the hold ends, a held profile can be released only by unsetting `erasure_requested_at` in the console (the PAN
digest is rebuilt on the user's next save), which needs the user's word. **Backups:** an Atlas snapshot keeps erased data
until it expires; when that is, and a restore, are in §1.s (W-237f).

### 1.w W-236 — the Gazette watch · owner, `SUPER_USER` · **when the owner chooses to start it; each database once**

**Why.** A daily job reads new e-Gazette copies and records a lead for each that may change a shipped pack (it names
the Central Board of Direct Taxes, or the Income-tax Act with the Department of Revenue). It **never writes a pack**: a
lead is read by the owner against the Gazette before anything changes. It calls out to `egazette.gov.in`, so it ships
**off** (`app.tax.gazette-watch.enabled: false` in all three profile yamls) and needs a starting point it never guesses.

**Automatic on deploy:** `JobIndexInitializer` creates the unique index on `tax_gazette_leads.url` — **confirm** the log
holds `Ensured the indexes of GazetteLead`.

1. **Check before** (read-only): no cursor yet, no leads.

   ```bash
   curl -sS "$BASE/tax-planning/admin/gazette-leads" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data | length'
   ```

   Expect `0`. In `mongosh`: `db.tax_gazette_cursor.find()` returns nothing.
2. **Choose the starting number.** On egazette.gov.in, open a recent Extraordinary Gazette; its address ends
   `WriteReadData/<year>/<number>.pdf`. The watch reads the numbers **after** the one given, so give the latest one already
   looked at (a lower number only means a longer first run).
3. **Seed the cursor** (recorded with the caller; a year before 2009 or after this year, or a number not positive, is a
   400):

   ```bash
   curl -sS -X PUT "$BASE/tax-planning/admin/gazette-cursor" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H "Content-Type: application/json" -d '{"year": <year>, "lastNumber": <number>}' | jq '.data'
   ```

   Expect the cursor back with `updatedBy` your login.
4. **Turn it on:** set `app.tax.gazette-watch.enabled=true` for the environment (`APP_TAX_GAZETTEWATCH_ENABLED=true`)
   and restart; `app.tax.jobs.enabled` must be true too. It runs at 05:00 daily, server time
   (`app.tax.jobs.gazette-watch-cron`), reading at most 400 numbers and stopping after 20 missing in a row.
5. **After the first run** (read-only): the log holds `Job gazette-watch stopped at <year>/<number>`, one
   `Gazette lead <url> …` WARN line per lead, and no `Job gazette-watch refused`; then

   ```bash
   curl -sS "$BASE/tax-planning/admin/gazette-leads" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data[] | {url, page, note, possiblyAffected}'
   ```

   **Unverified until this run:** whether `egazette.gov.in` serves the server's host daily. A failure in
   `tax_job_failures` naming an HTTP status, or no cursor movement, means it does not — turn the watch off and record it
   in the run log.

**Twice?** Safe: the cursor only moves forward past copies read, and a lead's URL is unique, so a rerun over the same
numbers adds nothing. Re-seeding sets the cursor back; the leads already recorded stay.

**Way back:** `app.tax.gazette-watch.enabled=false` and restart. The cursor and leads stay; they are read by nothing else.
Nothing to back up: the watch writes only its own two collections.

### 1.v W-237b — grievances answered within the published period · owner, `SUPER_USER` · **weekly, from the first deploy carrying it**

A user raises a grievance with `POST /privacy/grievances/user/{email}` (`201`, a receipt naming `respondBy`: the receipt time plus
`app.privacy.grievance.response-days`, 30, published in `GET /privacy/rights`). DPDP Rules, 2025 r.14(3) allow at most
ninety days (`longest-days-allowed: 90`; a start whose period exceeds it is refused). Nothing to seed; the
`privacy_grievances` collection is created by the first grievance. The app sends no mail: **this check is the measure
that keeps answers in time.**

**Weekly (read-only, safe to repeat, nothing to roll back):** as a `SUPER_USER`,

```bash
curl -s -H "Authorization: Bearer $TOKEN" "$BASE/privacy/grievances"
```

Expected: the unanswered grievances, the one due soonest first. Answer each before its `respondBy`:

```bash
curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"text": "<the answer>"}' "$BASE/privacy/grievances/<id>/answer"
```

`200` with `answer.answeredAt`; the user reads it at `GET /privacy/grievances/user/{email}`. A second answer to the same
grievance is refused (`400`), so a repeated call changes nothing. **Way back:** an answer given in error is corrected by
writing to the user from the privacy contact; the stored answer is not edited.

### 1.u W-237d — account-wide erasure · owner · **first deploy carrying it, then after the first run that erases**

A user's `POST /privacy/erasure/user/{email}` holds the whole account at once (`privacy_erasure_requests`: `requested_at`)
and answers `202` with `erasedFrom`, one year later (`app.privacy.erasure-hold: P1Y`, DPDP Rules, 2025 r.8(3)), and the
privacy contact. From then the account cannot sign in, its tokens are refused, the email cannot be registered again, AMC
billing skips its charge accounts, and each of its tax profiles is held as §1.z holds one. The daily `account-erasure`
job (`app.tax.jobs.account-erasure-cron`, 04:30; one of §1.y's jobs, same lease and failure records) erases every
module's data for each account past its year — portfolio, trades, P&L, charges, charge accounts, corporate-action
watermarks, insurance, tax profiles and everything under them, the older salary profiles and computations, the person's
access-log entries, grievances and breach intimations, their email in each breach notice's `affected` — the login last,
then sets `erased_at`. The request row stays a year after `erased_at` as the record that the erasure was done, then §1.t's
`log-retention` job erases it (W-237e C4a). The person's own processing-log rows go with the account; rows others wrote
about them stay until their year (§1.t). Nothing to seed or correct.

**Before the first start:** the hold moved from `app.tax.retention.erasure-hold` to `app.privacy.erasure-hold` (env var
`APP_PRIVACY_ERASUREHOLD`); the run log has no §1.z run, so no environment carries the old name. **After a run that
logged `Account erasure <id> done`** (read-only; replace `<stored email>` with the erased email's stored form, §0.7 — W-253):

```js
db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
  .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n); const e = "<stored email>";
    return { database: n, left: ["assets", "transactions", "trade_outcomes", "profit_and_loss", "user_charges",
      "charge_accounts", "lastly_performed_corporate_action", "insurances", "salary_profiles", "tax_computations",
      "tax_profiles", "tax_draft_computations", "privacy_grievances", "privacy_breach_intimations"].reduce((sum, c) =>
        sum + d.getCollection(c).countDocuments({ email: e }), d.user_details.countDocuments({ email: e })
        + d.tax_access_log.countDocuments({ principal: e }) + d.privacy_processing_log.countDocuments({ principal: e })
        + d.privacy_breach_notices.countDocuments({ affected: e })),
      heldOverdue: d.privacy_erasure_requests.countDocuments({ erased_at: null,
        requested_at: { $lte: new Date(Date.now() - 367 * 864e5) } }) }; }) }])
```

Expected: `left` 0 and `heldOverdue` 0 on every database (an open request past its year means the job did not run or
failed: §1.y's failure check; the next run completes it). A second run changes nothing: each erase deletes by email.
**Way back:** none for an erased account. Before the hold ends, an account is released only with the user's word, by
deleting its open request row in the console (`deleteOne` on its `_id`) and unsetting `erasure_requested_at` on its tax
profiles. **Backups:** an Atlas snapshot keeps erased data until it expires; when that is, and a restore, are in §1.s
(W-237f).

### 1.t W-237e — logs of processing kept a year, then erased · owner · **first deploy carrying it; monthly review; after the first run that erases**

Every request made by a named person — a signed-in user, or a login attempt for an email, failed ones included — writes
one row to `privacy_processing_log`: `principal`, `subject_email` (the path's `{email}`, else the principal), `method`,
`route` (the matched pattern, never the path's values, the query or the body), `status`, `remote_address`, `recorded_at`
(UTC). A request with no named person (no token, no login) is not recorded. The daily `log-retention` job
(`app.tax.jobs.log-retention-cron`, 04:45; one of §1.y's jobs, same lease and failure records) erases what is a year past
its last processing (`app.privacy.log-retention: P1Y`, env var `APP_PRIVACY_LOGRETENTION`; DPDP Rules, 2025 r.6(1)(e),
r.8(3)): processing-log rows and `tax_access_log` reads by their time, grievances by `answered_at` (an unanswered one is
kept), breach intimations by the latest of `posted_at`, `read_at`, `emailed_at`, erasure requests by `erased_at`. Breach
notices stay. **Console logs are not this log:** they live only as long as the container (`aws/runbook-daily.sh` replaces
it on each start) and are diagnostics (W-237e C5a).

**First start (automatic):** `PrivacyIndexInitializer` creates the log's indexes. **Confirm** the log holds `Ensured the
indexes of ProcessingRecord`; a failure stops startup. The collection is new: nothing to check first, nothing to seed.

**Monthly review (read-only, safe to repeat, nothing to roll back)** — r.6(1)(c)'s "monitoring and review": failed logins
by email and address over the last 31 days, and any principal that reached more than one person's data:

```js
db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
  .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n);
    const since = new Date(Date.now() - 31 * 864e5);
    return { database: n,
      failedLogins: d.privacy_processing_log.aggregate([{ $match: { route: "/auth/login", status: { $ne: 200 },
        recorded_at: { $gte: since } } }, { $group: { _id: { principal: "$principal", from: "$remote_address" },
        attempts: { $sum: 1 } } }, { $sort: { attempts: -1 } }]).toArray(),
      reachedOthers: d.privacy_processing_log.aggregate([{ $match: { recorded_at: { $gte: since },
        $expr: { $ne: ["$principal", "$subject_email"] } } }, { $group: { _id: "$principal",
        subjects: { $addToSet: "$subject_email" } } }]).toArray() }; }) }])
```

Expected: few failed logins per email and address, and `reachedOthers` naming only the owner. Anything else is a
possible unauthorised access: §5.7.

**After the first run that erases** (a year after this deploy; read-only):

```js
db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
  .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n);
    const cut = new Date(Date.now() - 367 * 864e5);
    return { database: n, overdue: d.privacy_processing_log.countDocuments({ recorded_at: { $lte: cut } })
      + d.tax_access_log.countDocuments({ read_at: { $lte: cut } })
      + d.privacy_grievances.countDocuments({ answered_at: { $lte: cut } })
      + d.privacy_erasure_requests.countDocuments({ erased_at: { $lte: cut } }) }; }) }])
```

Expected: `overdue` 0 on every database (more means the job did not run or failed: §1.y's failure check; the next run
completes it). A second run changes nothing: each step deletes by date. **Way back:** set `app.tax.jobs.enabled=false`
(stops every job) and restart; erased rows are gone — restore from the database backup. **Backups:** an Atlas snapshot
keeps erased rows until it expires; when that is, and a restore, are in §1.s (W-237f).

### 1.s W-237f — erased data in the backups, and a restore · owner · **before the duties commence (about May 2027); on every restore**

The Fiduciary must cause its Data Processor to erase what it was given (Act s.8(7)(b)). Backups are one of the expected
safeguards (Rules r.6(1)(d)), and r.8(3) Case 2 has the host keep the data at least a year. Nothing in the law limits how
long a snapshot may be kept: that is the owner's choice. The one bound is set by the app: **no snapshot may be kept
longer than `app.privacy.log-retention` (P1Y)**. A restore relies on the erasure records, and they are kept only a year
after each erasure (§1.t), so a snapshot older than that could hold a person no record still names (W-237f C1a).

**When erased data leaves the backups:** at `erased_at` plus the longest retention below. Data is erased a year after the
request (§1.z, §1.u), so a snapshot taken during the hold holds it until that snapshot expires.

**The owner's record (W-237f facts 1 – 4, C4a)** — filled in by the owner, not yet given:

| | prod `Cluster0` | staging `it-staging` |
|---|---|---|
| Atlas tier; Cloud Backup on? | *not given* | *not given* |
| Snapshot schedule; retention of each kind; point-in-time window | *not given* | *not given* |
| Longest retention ≤ P1Y? (the check) | *not given* | *not given* |
| Snapshots or exports kept outside Atlas | *not given* | *not given* |
| EC2 volume snapshotted (EBS, AMI)? | *not given* | — |
| Processor terms accepted (Atlas, AWS): document and date (Act s.8(2), r.6(1)(f)) | *not given* | *not given* |

**The check (owner, read-only, safe to repeat):** read each cluster's backup policy in Atlas, enter every retention
figure above, and confirm none exceeds one year. A longer one is shortened in Atlas before the duties commence. If a
tier has no backup that can be set, that is a re-plan of W-237f (its risk 2), not a row here.

**On every restore.** A restore puts the database back to the snapshot's moment, and every erasure since is lost from the
data. The records of those erasures are exported first and put back after, and then one call applies them again. It
reapplies only what was done at or after the snapshot's time. An erasure from before that time is already reflected in
the snapshot, and its email may since belong to a new account, which must not be touched.

1. **Before the restore, from the live database** (read-only):

   ```bash
   mongoexport --uri "<uri>" --db <db> --collection privacy_erasure_requests --out erasure-requests-before-restore.json
   mongoexport --uri "<uri>" --db <db> --collection tax_profile_erasures --out profile-erasures-before-restore.json
   ```

   Note the snapshot's time in UTC (`yyyy-MM-ddTHH:mm:ss`) as `S`.
2. **Restore** the snapshot in Atlas.
3. **Put the records back** (an upsert by `_id`: a record the snapshot already holds is replaced by its later state):

   ```bash
   mongoimport --uri "<uri>" --db <db> --collection privacy_erasure_requests --mode upsert --file erasure-requests-before-restore.json
   mongoimport --uri "<uri>" --db <db> --collection tax_profile_erasures --mode upsert --file profile-erasures-before-restore.json
   ```

4. **Apply them again** (`SUPER_USER`):

   ```bash
   curl -X POST "$BASE/privacy/erasure/reapply" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H "Content-Type: application/json" -d '{"snapshotTakenAt": "<S>"}'
   ```

   The answer names `accountsErased` (erased at or after `S`, erased again; `erased_at` unchanged), `accountsHeld`
   (asked at or after `S`, not yet erased, held again) and `profiles` (single profiles erased or held again). A time
   in the future, or none, is refused with 400.
5. **Check after (read-only)**, with `S` as an ISO date:

   ```js
   db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
     .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n);
       const s = new Date("<S>Z");
       const emails = d.privacy_erasure_requests.distinct("email", { erased_at: { $gte: s } });
       return { database: n, accountsBack: d.user_details.countDocuments({ _id: { $in: emails } })
           + d.assets.countDocuments({ email: { $in: emails } }) + d.tax_profiles.countDocuments({ email: { $in: emails } }),
         profilesBack: d.tax_profiles.countDocuments({ _id: { $in: d.tax_profile_erasures.distinct("profile_id",
           { erased_at: { $ne: null } }) } }) }; }) }])
   ```

   Expected: `accountsBack` 0 and `profilesBack` 0 on every database; then §1.u's check for each email in
   `accountsErased`. More than 0 means the call was not made or failed part-way. Make it again: each step deletes by email
   or by profile id, so a second call completes the work or changes nothing (tested).
6. **Delete the two export files** once the run is logged ("export deleted").

**Way back:** none is needed for the call, because it erases only what had been erased before the restore. If the
import was wrong, import the exports again and repeat step 4. **Profile-erasure records** (`tax_profile_erasures`:
`email`, `profile_id`, `requested_at`, `erased_at`) are new with W-237f. They are written by each single-profile request
from that deploy on, and erased a year after `erased_at` by §1.t's `log-retention` job. Nothing to seed.

### 1.r W-248 (D-479), W-253 (D-485), W-256 (D-486) — personal data, identifiers and actors stored before them, encrypted · owner, `SUPER_USER` · **every database · right after the first start carrying W-253 and W-256, with the tax jobs off, before users**

From W-248 these fields are stored encrypted under `TAX_DATA_KEY` (§0.5): `privacy_grievances` `text` and `answer`;
`privacy_processing_log` `remote_address`; `insurances` `insurance_id`, `agent_name`, `agent_email`, `agent_contact`,
`agent_address`, `notes`, and in each `policyDetails` element `policy_id`, the four `agent_*`, `notes` and
`policy_description`. A row written before W-248 holds them in plain text, and the app refuses to read it (a `500`)
until this step has run. The key properties moved from `app.tax.*` to `app.personal-data.data-key` / `digest-key`; they
read the same `TAX_DATA_KEY` / `TAX_DIGEST_KEY`, so the environment does not change and every `k1:` row still reads.

From W-253 the identifiers are stored encrypted too, in a form one value always stores the same, so queries and unique
indexes still work: `email` in `transactions` (and in a temporary transaction's `asset_request`), `assets`,
`profit_and_loss`, `trade_outcomes`, `user_charges`, `charge_accounts`, `lastly_performed_corporate_action`, `insurances`,
`salary_profiles`, `tax_computations`, `tax_profiles`, `tax_profile_erasures`, `tax_draft_computations`,
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
   read from its own claims, not from the database (`AuthFilter`). Right before switching the build, sign in on the
   previous build as your `SUPER_USER` account and keep the token: it lasts 30 minutes. If it runs out first, deploy the
   previous build again, sign in, and switch back. **Start the new build with `APP_TAX_JOBS_ENABLED=false`**
   (`app.tax.jobs.enabled`), so no job reads a plain row before step 4.
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
      lastly_performed_corporate_action: ["email"], salary_profiles: ["email"], tax_computations: ["email"],
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
      loginsToRekey: d.user_details.countDocuments({ email: { $exists: false } }) }; }) }])
```

4. **Dry run**, then the real run, with step 1's token:
   `curl -X POST -H "Authorization: Bearer <token>" "<host>/privacy/stored-fields/encrypt"` (a dry run by default),
   then the same URL ending `?dryRun=false`. Expected: the dry run's `plainRows` per collection equal step 3's counts
   (`user_details` equals `loginsToRekey`) and `encryptedRows` 0; the real run's `encryptedRows` equal `plainRows` and
   `conflicts` is 0 everywhere.
5. **If a collection reports `conflicts` > 0:** a row was written in the gap (a trade, a P&L year, a consent) beside its
   plain twin, and the plain one was left as it was (the log names each `_id`: `left plain: its encrypted identifier
   duplicates a unique index entry`). Compare the two in the backup and the database; keep the fuller, merging figures by
   hand as §0.2 describes for a duplicate `{email, financial_year}`, delete the other, and run step 4 again.
6. **Check after:** step 3 again — `0` everywhere, `identifiers` and `actors` empty, `loginsToRekey` 0. Then sign in on
   the new build: it works.
7. **Turn the tax jobs back on:** restart without `APP_TAX_JOBS_ENABLED=false`. The start completes and the log shows
   `Job staleness-sweep flagged <n> draft(s)`; a stack trace naming `A stored field is not encrypted` means step 6 was
   not `0` — stop the jobs again and repeat step 4.

A second run is safe: it finds nothing and changes nothing. Each update is conditioned on the plain value it read, so
a row that changed during a run is left for the next run, and a run that failed part-way is completed by the next; a
login whose new row was inserted before a failure is only deleted by the next run, not inserted twice.
**Way back:** restore the database from step 2 and redeploy the previous build, which reads plain text and the email as
the login's `_id`.
**One-time:** removed by W-254 (YouTrack WL-43) once this step's run is logged for every database.

### 1.q W-249 (D-480) — erasure once the purpose has lapsed · owner · **first deploy carrying it, then after the first run**

An account not signed in to for `app.privacy.purpose-lapse: P3Y` (the owner's ruling, D-480; not a figure from the law)
is erased by the daily `account-erasure` job (§1.u, same job and holders), at once and with no hold, and recorded in
`privacy_erasure_requests` with `cause: "PURPOSE_LAPSED"`, `requested_at` the lapse moment, `erased_at` the run. Each
sign-in and each registration stores `user_details.last_signed_in_at`. An account without it (every account registered
before W-249) gets the first run's time: **nothing can lapse earlier than three years after the first run on a
database.** No command to run. The privacy notice (`GET /privacy/rights`, `/privacy`) states the period.

**Before the first start:** an environment override `APP_PRIVACY_PURPOSELAPSE` must be longer than `erasure-hold` (P1Y),
or the app refuses to start naming `app.privacy.purpose-lapse`; none is expected. **Read-only, before the first run and
after it** (the day after the deploy):

```js
db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
  .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n);
    return { database: n, users: d.user_details.countDocuments({}),
      notCounted: d.user_details.countDocuments({ last_signed_in_at: { $exists: false } }),
      lapseErasures: d.privacy_erasure_requests.countDocuments({ cause: "PURPOSE_LAPSED" }),
      lapseOpen: d.privacy_erasure_requests.countDocuments({ cause: "PURPOSE_LAPSED", erased_at: null }) }; }) }])
```

Expected before: `notCounted` = `users`, `lapseErasures` 0. After the first run: `notCounted` 0 and `lapseErasures` 0
(the earliest lapse is three years away). From then, `lapseOpen` 0 the day after any run (an open one means the run
failed part-way: §1.y's failure check; the next run completes it). A second run changes nothing: the time is set only
where absent, and an account with a request open is left to it. **Way back:** before the first lapse, `$unset`
`last_signed_in_at` loses nothing (the next run counts again from its own time). An erased account cannot be brought
back; after a restore, §1.s's reapply erases a lapse erasure again like a requested one.

### 1.p W-250 (D-481) — the notice before consent · owner · **first deploy carrying it, then a week after**

Registration (`POST /auth/register`) now needs `privacyNoticeVersion`, the `version` that `GET /privacy/rights` publishes.
It is a SHA-256 of the notice's wording, so any change to `app.privacy` text gives a new one. Without the current version,
registration is refused with 400 and nothing is stored. A successful registration writes a `privacy_consents` row
(`notice_version`, `given_at`). Each sign-in's response carries `privacy_notice` (`/privacy`) and `privacy_notice_version`.
The first sign-in of an account with no row (every account registered before W-250) writes one row with `noticed_at`
(Act s.5(2)(a)). Account erasure deletes the row. **There is no command to run.** **Any client must send the version
before it can register anyone.** The start refuses a missing `app.privacy.consent.*` value, and the yamls carry all of it.
`board-complaint` has no link to the Board yet: add it there when you have one. That changes the version, which is
expected.

**At the first start:** the log shows `Ensured the indexes of PrivacyConsent`, the unique `email` index. **Read-only,
before the deploy and a week after it:**

```js
db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
  .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n);
    return { database: n, users: d.user_details.countDocuments({}),
      consents: d.privacy_consents.countDocuments({ given_at: { $exists: true } }),
      noticed: d.privacy_consents.countDocuments({ noticed_at: { $exists: true } }),
      unique: d.privacy_consents.getIndexes().filter(i => i.unique && i.key.email).length }; }) }])
```

Expected before: `consents` 0, `noticed` 0, `unique` 0. A week after: `unique` 1, and `consents` + `noticed` no more than
`users`. Every account that signed in or registered since the deploy has a row. A second start or sign-in changes
nothing: the index already exists, and a row is never written twice for one account. **Way back:** redeploy the earlier
build. `privacy_consents` can stay (nothing else reads it) or be dropped (`db.privacy_consents.drop()`, after
`mongodump --collection privacy_consents`), and nothing else changes.

### 1.o W-255 (D-482) — accounts that chose their own role · owner · **every database · before the deploy carrying W-255, then after any correction**

Until W-255, `POST /auth/register` stored whatever `role` the body sent, so anyone could register as `SUPER_USER`. Now
every registration stores `USER`, and a body naming `role` is refused with 400. Roles already stored are not changed
by the deploy. **Read-only, before the deploy:**

```js
db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
  .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n);
    return { database: n,
      byRole: d.user_details.aggregate([{ $group: { _id: "$roles", accounts: { $sum: 1 } } }]).toArray(),
      aboveUser: d.user_details.find({ roles: { $ne: "USER" } }, { roles: 1 }).toArray() }; }) }])
```

Expected: `aboveUser` lists only your own account. **Only if it lists another account**, in that database, after
`mongodump --db <database> --collection user_details` and keeping this output (the old roles):

```js
db.user_details.updateMany({ _id: { $in: ["<email>", "<email>"] }, roles: { $ne: "USER" } }, { $set: { roles: "USER" } })
```

Then run the read-only query again: `aboveUser` is your account alone. A second run of the update matches nothing. A
token issued before keeps its old role until it expires (30 minutes at most); the next sign-in carries `USER`. **Way
back:** set each account's old role from the kept output with `updateOne({ _id: "<email>" }, { $set: { roles: "<old>" } })`;
once §1.r has re-keyed the logins (W-253), match `{ email: "<stored email>" }` (§0.7) instead of the `_id`.
The dump is the fallback; restoring it whole would also undo later sign-ins' `last_signed_in_at`.

### 1.x W-096 – W-098 (D-440) — the profile collections' indexes · owner · **first deploy carrying them**

Automatic at startup (`TaxProfileIndexInitializer`). **Confirm** the log holds `Ensured the indexes of` for `TaxpayerProfile`, `FamilyMember`, `Employer`, `Landlord`, `Lender`, `BankAccount`, `TaxYearWorkspace`, `FilingDraft`, `DraftRevision`, `RegimeElectionHistory` (`WorkspaceIndexInitializer`, W-099, D-441) and `DraftComputation` (`DraftComputationIndexInitializer`, W-104, D-442); a failure stops startup. The collections are new, so there is nothing to check first, and a second start changes nothing. Way back: drop the empty collections. **Encryption at rest is built (W-134, D-447; W-232 merged into it): §0.5 before the first deploy carrying it.**

## 1A. W-176 (D-379) — clear the charge engine's data · owner, `mongosh` · **every fresh start**

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
- **Documents are deleted, collections are not dropped.** Dropping would also remove the unique indexes
  `ChargeIndexInitializer` created at startup (§1.6), leaving `user_charges {email, transaction_id}` unenforced until
  the next restart.
- **No trade between 1A and 1B.** With the flags on and no cards, a V2 buy is charged ₹0 and that ₹0 becomes its
  cost basis.
- 1A is a fresh-start step, not a one-time migration, so it has no Phase 15 row and no code to remove.

1. **Count before** (read-only; run it again after step 3):

   ```js
   ({ db: db.getName(),
      charge_catalogue: db.charge_catalogue.countDocuments({}),
      charge_schedules: db.charge_schedules.countDocuments({}),
      charge_instruments: db.charge_instruments.countDocuments({}),
      user_charges: db.user_charges.countDocuments({}),
      charge_accounts: db.charge_accounts.countDocuments({}),
      pnl_charge_side: db.profit_and_loss.countDocuments({ $or: [
        { "realised_profits.yearly_charge_summary": { $exists: true } },
        { "out_sourced_realised_profits.yearly_charge_summary": { $exists: true } } ] }) })
   ```

   Expect (2026-10-27): prod 0 / 0 / 0 / 0 / 0 / 0; staging 12 / 11 / 2 / 328 / 1 / 2. Take it again now; report a
   difference before clearing.
2. **Back up** (staging; on prod only if step 1 is not all 0):

   ```bash
   for c in charge_catalogue charge_schedules charge_instruments user_charges charge_accounts; do
     mongoexport --uri "<uri>" --db <db> --collection $c --out charge-engine-$c-backup.json
   done
   mongoexport --uri "<uri>" --db <db> --collection profit_and_loss \
     --query '{"$or":[{"realised_profits.yearly_charge_summary":{"$exists":true}},{"out_sourced_realised_profits.yearly_charge_summary":{"$exists":true}}]}' \
     --fields _id,realised_profits.yearly_charge_summary,out_sourced_realised_profits.yearly_charge_summary \
     --out charge-engine-pnl-charge-side-backup.json
   ```

3. **Clear.** The per-user part refuses to run unless the database is `it-staging`:

   ```js
   (() => {
     const configuration = Object.fromEntries(["charge_catalogue", "charge_schedules", "charge_instruments"]
       .map(c => [c, db.getCollection(c).deleteMany({}).deletedCount]));
     if (db.getName() !== "it-staging") {
       return { db: db.getName(), configuration, perUser: "not cleared: only it-staging clears per-user data" };
     }
     return { db: db.getName(), configuration,
       user_charges: db.user_charges.deleteMany({}).deletedCount,
       charge_accounts: db.charge_accounts.deleteMany({}).deletedCount,
       pnl_charge_side: db.profit_and_loss.updateMany(
         { $or: [ { "realised_profits.yearly_charge_summary": { $exists: true } },
                  { "out_sourced_realised_profits.yearly_charge_summary": { $exists: true } } ] },
         { $unset: { "realised_profits.yearly_charge_summary": "",
                     "out_sourced_realised_profits.yearly_charge_summary": "" } }).modifiedCount };
   })()
   ```

   Expect the counts from step 1 (on prod, `configuration` all 0).
4. **Verify:** step 1 again reads all 0 (on prod, the per-user counts were 0 before and stay so), and
   `db.user_charges.getIndexes()` still lists `user_charge_txn_idx`.

**Second run:** safe, including after a run that failed part-way: deleting from an empty collection and unsetting an
absent field change nothing (all counts 0). **Way back:** `mongoimport --uri "<uri>" --db <db> --collection <c> --file
charge-engine-<c>-backup.json` per collection, and `mongoimport --mode merge` of the P&L file; then `POST
/charges/seed`, whose run evicts the rate-card caches, so the restored cards are read (§5.5, "written directly").

## 1B. Charge engine — seed the rate cards · `SUPER_USER` · **every database · after §1A, before the first trade**

**Why.** Seeding is not automatic (ADR-27). A database with no cards prices every V2 trade `NO_SCHEDULE` at ₹0, and
with `authoritative` on that ₹0 becomes the cost basis; nothing throws.

1. **Seed:**

   ```bash
   curl -sS -X POST "$BASE/charges/seed" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
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
   curl -sS "$BASE/charge-schedules/drift" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   # Every card with a verifiedOn older than 90 days, or none; rows mean rate verification is due (§5.2), not a failure
   curl -sS "$BASE/charge-schedules/unverified" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   ```

   Expect `unverified` empty. Every card carries a `verifiedOn`: a broker card against its charges page, and a
   statutory card against the law it cites (W-163c, D-384; `sources/2026-10-28-statutory-charge-rates.md`). A card
   listed here is due for verification.

   ```bash
   ```

3. **One simulate** against a known figure, Zerodha equity delivery, ₹1,00,000 sell:

   ```bash
   curl -sS -X POST "$BASE/charges/simulate" -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' -d '{
       "brokerName":"ZERODHA","assetType":"EQUITY","segment":"DELIVERY","event":"SELL",
       "exchange":"NSE","transactionDate":"2025-06-10","quantity":100,"price":1000
     }' | jq '.data'
   ```

   Compare with the expected figure in [`../charges-engine/staging-runbook.md`](../charges-engine/staging-runbook.md)
   §4.1. Its §4 holds every shipped card's figure, and §4.7b the three card generations: the same trade prices
   differently by date, which is correct.

**Second run:** safe; the seed skips what is on file. **Way back:** §1A clears what this wrote.


---

## 2. Once per database, by hand — in this order

> **There is one deploy, after Phases 0 – 14 are complete** (owner, D-287); nothing is deployed before then. Every
> step in this section runs at that deploy, in order, on each database. Take each step's "count before" again at that
> time: the figures recorded here (2026-10-20 and 2026-10-27, run log) are from the databases as they were. They run
> after §1A and §1B.

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
   curl -sS -X POST "$BASE/trade-outcomes/rebuild" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   ```

   **Read `usersRebuilt` and `rowsWritten` before writing.** Only users with transactions and no rows at all should
   be in it. On staging and prod as they stand, that is users who never sold, giving 0 rows, plus any user whose
   rows were deleted.
2. **Write:** the same call with `?dryRun=false`.
3. **Verify:** for each rebuilt user, `db.trade_outcomes.countDocuments({ email: "<stored user email>" })` (§0.7, from W-253) equals the rows the
   report gave for them, and each row's `audit_metadata.createdBy` is `<stored caller email>` (§0.7). A second call reports
   `rowsWritten` 0.

**Know this:** rebuilt rows carry no charge breakdown, so the capital-gains summary refuses a user whose rebuilt rows
have broker charges (D-238 C2a; §3.1). Rebuild one user at a time (delete only that user's rows), never the whole
collection.

**Way back:** delete the rows this run wrote (`audit_metadata.createdBy` = `<stored caller email>` (§0.7), `createdAt` = the run).

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

### 2.4 W-088k4b (D-308) — date the bonus lots processed before W-088k4a from their allotment · `SUPER_USER` · **every database with bonus actions · once · outstanding**

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
   curl -sS -X POST "$BASE/corporate-actions/bonus/allotment-dates/correct" -H "Authorization: Bearer $ADMIN_TOKEN" \
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

### 2.5 W-160c (D-365) — remove the term `portfolio` stored on `trade_outcomes` · `SUPER_USER` · **every database · once · outstanding**

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
   curl -sS -X POST "$BASE/trade-outcomes/stored-terms/remove" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
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

### 2.6 W-160d3 (D-369) — move the P&L's stored short / long pair into its split · `SUPER_USER` · **every database · once · outstanding**

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
   curl -sS -X POST "$BASE/profit-and-loss/split/reshape" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
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

### 2.7 W-160f (D-371) — drop `portfolio`'s holding-period policies · owner, `mongosh` · **every database · once · outstanding**

**Why.** `holding_period_policies` held `portfolio`'s own copy of the holding-period law, seeded by `POST
/charges/seed`. Since W-160f no code reads, writes or seeds it: the term is the engine's `RS-CG-TERM` (D-305, D-362).
This removes the stale law from the database. Nothing reads the collection, so no figure changes.

**When:** at the deploy, any time after it.

1. **Count before** (read-only): `db.holding_period_policies.countDocuments({})`. Expect the shipped policies (11),
   or 0 where `/charges/seed` was never run. 2026-10-27: prod 0, staging 11. §1B no longer writes it.
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

### 2.8 W-172 (D-373) — add the V2 sales' profit, brokerage and misc to the P&L · `SUPER_USER` · **every database · once · outstanding**

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
   curl -sS -X POST "$BASE/profit-and-loss/v2-figures/backfill?recordedBefore=<yyyy-MM-ddTHH:mm:ss>" \
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

### 2.9 W-162b (D-377) — remove the deductible charges stored on `trade_outcomes` · `SUPER_USER` · **every database · once · outstanding**

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
   curl -sS -X POST "$BASE/trade-outcomes/deductible-charges/remove" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
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

### 2.10 W-162c (D-378) — unset the deductibility flag on `charge_catalogue` · owner, `mongosh` · **every database · once · outstanding**

**Why.** `charge_catalogue.deductible_for_capital_gains` was one yes/no per charge code, the same for every year,
saying whether the charge reduces a capital gain. Which charges do is the tax side's rule data for the year
(`RS-CG-CHARGE`, W-162a, D-376), and since W-162c no code reads, writes, seeds or backfills the field. This removes the
stale flag. Nothing reads it, so no figure changes; the catalogue's codes and other fields stay.

**When:** at the deploy, any time after it.

1. **Count before** (read-only): `db.charge_catalogue.countDocuments({ deductible_for_capital_gains: { $exists: true } })`.
   Expect the codes seeded before W-162c (12), or 0 where `/charges/seed` was never run. **After §1A it is 0 on both
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
- The rebuilt documents list every folded row in `pnl_backfilled_outcome_ids`, so §2.8 never adds them again.
- A row is filed under its own `segment`. A V1 or §2.2-rebuilt row stored before W-295 has none and is filed under
  delivery, even when its sale was intraday. **Run §3.19 first, and do not rebuild a user it lists until W-297 has
  corrected their rows** (W-295, D-595). W-297's §2.14 is that correction.

1. **Before (read-only):** note the user's documents and back them up:
   ```bash
   mongoexport --uri "<uri>" --db <db> --collection profit_and_loss \
     --query '{ "email": "<stored user email>" }' --out pnl-<user>-backup.json   # §0.7 for the stored form
   ```
2. **Dry run** (the default):
   ```bash
   curl -sS -X POST "$BASE/portfolio/user/<email>/profit-and-loss/rebuild" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   ```
   Read `years`: per financial year, `stored` against `rebuilt`. For a V2-only user they agree to the paisa; any
   difference is the correction, or a V1 figure (above). Decide before writing.
3. **Write:** the same call with `?dryRun=false`.
4. **Verify (read-only):** `db.profit_and_loss.countDocuments({ email: "<stored user email>" })` equals the number of
   rebuilt years, and a second dry run reports every year's `stored` equal to its `rebuilt`.

**Second run:** safe, including after one that failed part-way: the write is one transaction that replaces the user's
documents with the same fold, so a second run writes the same documents (`ProfitAndLossRebuildIntegrationTest`).
**Way back:** delete the user's documents and `mongoimport --uri "<uri>" --db <db> --collection profit_and_loss
--file pnl-<user>-backup.json`.

### 2.12 W-283a3 (D-586) — rebuild one user's `assets` from `transactions` · `SUPER_USER` · **when needed, not once**

**Why.** `assets` (the lots) is derived. This replays one user's transactions into lots
(`docs/trade-ledger/assets-rebuild.md`; W-283a2, D-582) and replaces the stored ones, instead of hand-editing quantities
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

1. **Before (read-only):** back up the user's lots, and note how many there are:
   ```bash
   mongoexport --uri "<uri>" --db <db> --collection assets \
     --query '{ "email": "<stored user email>" }' --out assets-<user>-backup.json   # §0.7 for the stored form
   ```
   `db.assets.countDocuments({ email: "<stored user email>" })`
2. **Dry run** (the default):
   ```bash
   curl -sS -X POST "$BASE/portfolio/user/<email>/assets/rebuild" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
   ```
   - Read `lotsDiffering`, then each entry of `lots` with non-empty `differingFields`; `stored` and `rebuilt` sit side
     by side.
   - Read `newLots` and the reading lists. Decide before writing.
3. **Write:** the same call with `?dryRun=false`. `lotsWritten` is the number of lots the user now has.
4. **Verify (read-only):**
   - the count above now equals `lotsWritten`;
   - a second dry run reports `lotsDiffering` 0 and `newLots` empty;
   - `GET /portfolio/user/<email>/stocks/all` (and `/mfs`) shows the holdings (`PortfolioController:77 – 86`).

**Second run:** safe, including after one that failed part-way. The write is one transaction that deletes and saves
the user's lots, so a failure leaves them as they were, and a second run writes the same lots under the same ids
(`AssetsRebuildIntegrationTest`).

**Way back:** delete the user's lots and
`mongoimport --uri "<uri>" --db <db> --collection assets --file assets-<user>-backup.json`.

### 2.13 W-291 (D-589) — clear what bonus transactions copied from a buy · owner, `mongosh` · **every database · once · outstanding**

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

### 2.14 W-297 (D-599) — restore the segment stored before W-292 / W-295 · `SUPER_USER` · **every database · once · outstanding**

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
   curl -sS -X POST "$BASE/transactions/segment/correct" -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'
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

---

## 3. Standing data conditions — read-only checks, no action defined

These are states the build decided to **refuse or leave**, rather than repair. Run the check to know their size.
Any repair would be a new work item with its own section-2 entry.

| # | Item / decision | Condition | Read-only check |
|---|---|---|---|
| 3.1 | **W-086** (D-238 C2a) | `CapitalGainsSummaryService` **refuses** a user's summary if any of their disposals carries broker charges with no breakdown ("record those trades again", worded since W-223: a trade a rate card prices can be recorded again, and one no card prices cannot be computed until its charges can be entered by code). The rebuild's rows never have one | Count below |
| 3.2 | **W-088f** (D-249 C4a, "no backfill") | Rows written before W-088f have no `acquisition_mode`; they read as `UNRECORDED` (`Acquisition.stored` maps a missing mode to it) | `db.trade_outcomes.countDocuments({ acquisition_mode: { $exists: false } })` |
| 3.3 | **W-088j** (D-292), narrowed by **W-088j2c2** (D-298), **W-088j2d** (D-299) and **W-088j2e** (D-300) | A side of a disposal with no charge breakdown says nothing of STT, so it reads as `UNKNOWN`, not "no STT". Since D-298 the engine settles such a side from its recorded venue: an equity side on `NSE` or `BSE` in `DELIVERY` (and a sale in `INTRADAY`) is chargeable under Chapter VII s.98 Sl. 1–3 — a buy only when the lot was acquired by purchase (Sl. 1 "Purchase", D-300), and a long-term buy acquired before 1-10-2004 or by a bonus or demerger is settled by S.O. 5054(E) (`CG-PART-LONG-NOTIFIED`). What is still **refused** by the tax engine: a side with nil charges and no breakdown whose exchange is not recorded, is spelt other than exactly `NSE` / `BSE`, or is genuinely off an exchange, and a long-term buy on `NSE` / `BSE` that is not shown to be a purchase and is not a bonus or demerger allotment — a rights allotment, or an unrecorded mode (D-300) (a side with charges and no breakdown is refused earlier, §3.1). No user declaration was built (D-299); a repair, if the count is not nil, is a new work item — the exchange string is the fault for most rows, and §2.2's rebuild fills a row's missing venue from `transactions` | Count below (an upper bound) |
| 3.4 | **W-088j2b** (D-295, "no backfill") | `trade_outcomes` rows written before W-088j2b have no per-side venue (`buy_exchange_name`, `buy_segment`, `sell_exchange_name`, `sell_segment`); they read as `TradeVenue.UNRECORDED`. The old `exchange_name` / `segment` are **not** read for tax (V1 took the exchange from the buy, V2 and the rebuild from the sale). Remedy for a user: §2.2's per-user rebuild, which writes the new fields. The segment is published as recorded: `DELIVERY` is also the entry default (D-295) | Count and values below |
| 3.5 | **W-088k3** (D-305) | The tax engine decides short-term or long-term itself, from `RS-CG-TERM`: an equity disposal is a listed security (twelve months) when its **sale** is recorded on `NSE` or `BSE` (`sell_exchange_name`; a reading, D-305 C1). A disposal whose sale exchange is absent or another string is **refused** for the term — no other period is shipped (D-305 C2). Rows written before W-088j2b have no `sell_exchange_name` (§3.4). Remedy for a user: §2.2's per-user rebuild, which writes it from `transactions.exchange_name` | Count below |
| 3.6 | **W-088k4a** (D-307) | `corporate_action.allotment_date` is new and not backfilled. Performing a `BONUS` action without it is **refused** (400, naming the stock and record date), so a pending bonus whose date is not entered blocks its quarterly perform. Bonus lots processed before W-088k4a are dated at the ex-date; W-088k4b corrects them | List below |
| 3.7 | **W-088g5** (D-433) | A `LAND_OR_BUILDING` trade is now **refused** on V1, V2 and the upload (400). Land was enterable from W-088g2 (D-425) until this guard; a row entered in that window **stays as it is** — the guard refuses new entries and touches nothing stored. Expected 0 on every database; a non-zero count is reported to the owner, not corrected (a correction would be its own item, with W-088g9's property entry) | the 3.7 query below, owner, any time after the deploy that carries W-088g5; safe to run again |
| 3.8 | **W-223** (D-437), **W-230** (D-439) | Until these a V2 buy no rate card priced had its entered `brokerCharges` replaced by nil, and a V2 sale's never reached its row; from them the entered figure stays. A lot or row already written stays nil; its entered charge is still on its source transaction, so nothing is lost. Reported, not corrected: a correction is its own item | the 3.8 query below, owner, any time after the deploy that carries W-223; read-only; safe to run again; nothing to undo |
| 3.9 | **W-213, W-216** (D-438) | Until W-213 a stock split performed through the per-user path changed no lot, yet was marked performed, and a malformed ratio could be too. A lot affected stays unsplit with a performed row. Reported, not corrected: a correction is its own row, placed once a count is seen. **Read the ratios too:** W-213 reads `n:d` as new shares to old (`2:1` doubles). A stored `1:10` meant as ten-for-one would **shrink** a holding when performed: re-enter such an action before anyone performs it | the 3.9 query below, owner, after the deploy that carries W-213 and before any split is performed; read-only; safe to run again; nothing to undo |
| 3.10 | **W-268** (D-553 C1a) | `POST /portfolio/user/{email}/update` is **removed**: it let any signed-in user set a missing `asset_type` to `MUTUAL_FUND` on every user's `assets` and `transactions`. Whether it was ever run on a database is not known. A record with no `asset_type` stays as it is; expected 0. A non-zero count is reported to the owner, not corrected — a scoped, idempotent backfill would be its own row, placed once a count is seen | `({ assets: db.assets.countDocuments({ asset_type: null }), transactions: db.transactions.countDocuments({ asset_type: null }) })`, owner, `mongosh`, before or after the deploy that carries W-268; read-only; safe to run again; nothing to undo |
| 3.11 | **W-270** (D-555 C2a) | A trade held back by a corporate action now records the flow that received it (`trade_flow`, `V1` or `V2`), and redrive records it the same way. A `TEMPORARY` transaction stored before W-270 has none; which flow sent it cannot be recovered, so it is redriven through V1 as before — a V2 trade among them comes back merged into a same-day lot and unpriced by the charge engine (W-264a G-8). Reported, not corrected: the count decides whether a row is placed | `db.transactions.countDocuments({ status: "TEMPORARY", trade_flow: null })`, owner, `mongosh`, after the deploy that carries W-270; read-only; safe to run again; nothing to undo |
| 3.12 | **W-271** (D-556 C2a) | A V1 sale now takes only lots held on its date, as V2's does. Until W-271 a V1 sale (the route, the upload, a V1 redrive) read every lot, so a sale dated before a purchase could consume that later lot; each such allocation left a `trade_outcomes` row whose `buy_date` is after its `sell_date`. Expected 0; a non-zero count is reported to the owner, not corrected — a correction would be its own row | `db.trade_outcomes.countDocuments({ $expr: { $gt: ["$buy_date", "$sell_date"] } })`, owner, `mongosh`, any time; read-only; safe to run again; nothing to undo |
| 3.13 | **W-274** (D-560 C3a) | Sale quantities are now compared within a tolerance of 1e-9. Until W-274 a V1 sale of fractional units could leave a lot holding a float remainder (2.8e-17 units seen in W-264a) that was never deleted. Such a lot is no longer shown in the holdings views, but stays stored. Expected 0; a non-zero count is reported to the owner, not corrected — a removal would be its own row | `db.assets.countDocuments({ quantity: { $gt: 0, $lt: 1e-9 } })`, owner, `mongosh`, any time; read-only; safe to run again; nothing to undo |
| 3.14 | **W-279** (D-565 C2a) | A V1 buy merged into the day's lot now keeps its order time on the lot. Until W-279 the merge dropped it, so a lot built from several V1 buys lists order times for the first only. Lots with two or more buy transactions are V1 merges (V2 writes a lot per buy); one never sold from whose order times sum below its quantity lost some. The lost times are on each buy's transaction row, not the lot. Reported, not corrected — a correction would be its own row | `db.assets.countDocuments({ "buy_transaction_ids.1": { $exists: true }, sell_transaction_ids: { $size: 0 }, $expr: { $lt: [ { $sum: "$order_time_quantities.quantity" }, "$quantity" ] } })`, owner, `mongosh`, any time; read-only; safe to run again; nothing to undo |
| 3.15 | **W-280** (D-566 C2a) | A V1 buy is now priced and recorded under its own segment. Until W-280 V1's buy passed `DELIVERY` whatever the trade said, so a V1 intraday buy was priced on the delivery card and its `user_charges` row says `DELIVERY` while its transaction says `INTRADAY` (V2 always passed the segment, so such a pair is always a V1 buy). Reported, not corrected — re-pricing would be its own row (ADR-32). **This count matched nothing before W-292 and still matches nothing for a trade stored before it** (W-292, D-591): until then no transaction stored its segment, so none said `INTRADAY`. A V1 intraday buy made before W-280 cannot be found from stored data at all, because its charge row was priced as `DELIVERY` too. §3.18 counts the transactions whose charge row knows better | `db.transactions.aggregate([{ $match: { transaction_type: "BUY", segment: "INTRADAY" } }, { $lookup: { from: "user_charges", let: { id: { $toString: "$_id" } }, pipeline: [{ $match: { $expr: { $eq: ["$transaction_id", "$$id"] } } }], as: "charge" } }, { $match: { "charge.segment": "DELIVERY" } }, { $count: "priced_as_delivery" }])`, owner, `mongosh`, any time; read-only; safe to run again; nothing to undo |
| 3.16 | **W-289** (D-587 C2a) | A trade now waits for a split, bonus or demerger recorded in an earlier quarter and not yet performed, when the user holds lots it reaches. Until W-289 only an action in the trade's own quarter held it, so a sale in a later quarter could be applied to pre-split units before the action was performed: holding 10, a 2:1 split, selling 4 left 12, not 16. The count lists **candidates** — users with a sale dated after an action's record date, of a stock they bought with that broker before it. Same-quarter and correctly ordered ones are included, so each user is checked with §2.12's dry run, which shows the actual drift, and corrected through §2.12 then §2.11 | `db.corporate_action.aggregate([{ $match: { type: { $in: ["STOCK_SPLIT", "BONUS", "DEMERGER"] } } }, { $lookup: { from: "transactions", let: { s: "$stock_code", r: "$record_date" }, pipeline: [{ $match: { $expr: { $and: [{ $eq: ["$stock_code", "$$s"] }, { $eq: ["$transaction_type", "SELL"] }, { $gt: ["$transaction_date", "$$r"] }] } } }], as: "sale" } }, { $unwind: "$sale" }, { $lookup: { from: "transactions", let: { s: "$stock_code", r: "$record_date", e: "$sale.email", b: "$sale.broker_name" }, pipeline: [{ $match: { $expr: { $and: [{ $eq: ["$stock_code", "$$s"] }, { $eq: ["$email", "$$e"] }, { $eq: ["$broker_name", "$$b"] }, { $eq: ["$transaction_type", "BUY"] }, { $lt: ["$transaction_date", "$$r"] }] } } }, { $limit: 1 }], as: "bought_before" } }, { $match: { bought_before: { $ne: [] } } }, { $group: { _id: "$sale.email", sales: { $sum: 1 } } }])` — one row per candidate user (the stored email, §0.7), owner, `mongosh`, any time; read-only; safe to run again; nothing to undo |
| 3.17 | **W-291** (D-589 C2a) | A bonus lot and its transaction now carry only the bonus. Until W-291 the lot copied the first held lot's charges, allocated charges, sales and actions, and the transaction copied the first buy's charges and identifiers. A sale of bonus shares therefore charged buy charges paid once already, overstating cost and understating the gain. And a bonus could not be performed at all for a user whose first buy carried an idempotency key or came from a redrive (a duplicate key). The count lists users with bonus lots carrying charges. §2.13 corrects the transactions; then each user listed is put through §2.12 → §2.2 → §2.11 | `db.assets.aggregate([{ $match: { corporate_action: "BONUS", $or: [{ broker_charges: { $gt: 0 } }, { misc_charges: { $gt: 0 } }, { allocated_buy_charges: { $gt: 0 } }, { allocated_buy_misc_charges: { $gt: 0 } }] } }, { $group: { _id: "$email", bonus_lots_with_charges: { $sum: 1 } } }])` — one row per user (stored email, §0.7), owner, `mongosh`, any time; read-only; safe to run again; nothing to undo |
| 3.18 | **W-292** (D-591 C2a) | A transaction and a lot now record the segment the trade was entered in. Until W-292 neither did (`AssetRequest.asTransaction`, `asAsset`), so every stored one says `DELIVERY`, and an outcome row's `buy_segment` (taken from the lot) and a §2.2 rebuild's venues (taken from the transactions) say it too. The trade's `user_charges` row was priced under the request's segment (V2 always, V1 since W-280), so it knows better. The count lists users with transactions whose charge row says another segment. Reported here; §2.14 (W-297) corrects it | `db.transactions.aggregate([{ $lookup: { from: "user_charges", let: { id: { $toString: "$_id" } }, pipeline: [{ $match: { $expr: { $eq: ["$transaction_id", "$$id"] } } }], as: "charge" } }, { $unwind: "$charge" }, { $match: { $expr: { $ne: ["$segment", "$charge.segment"] } } }, { $group: { _id: "$email", segment_differs: { $sum: 1 } } }])` — one row per user (stored email, §0.7), owner, `mongosh`, any time; read-only; safe to run again; nothing to undo |
| 3.19 | **W-295** (D-595 C3a) | An outcome row now records the segment of its sale whichever flow wrote it. Until W-295 only V2 did: a V1 row and a row rebuilt by §2.2 held `sell_segment` but no `segment`, and §2.11 files a row under `segment`, reading an absent one as delivery. So a §2.11 rebuild moved such a sale out of intraday (live P&L was right). The count lists users with rows whose `sell_segment` is not `DELIVERY` and that have no `segment`; those are the rows filed wrongly. Reported here; §2.14 (W-297) corrects it. **Until §2.14 has run, do not run §2.11 for a user this lists** | `db.trade_outcomes.aggregate([{ $match: { segment: null, sell_segment: { $nin: [null, "DELIVERY"] } } }, { $group: { _id: "$email", rows_filed_as_delivery: { $sum: 1 } } }])` — one row per user (stored email, §0.7), owner, `mongosh`, any time; read-only; safe to run again; nothing to undo |
| 3.20 | **W-303** (D-619 C4a) | A fund redeemed inside its exit-load window is now charged the load on its redemption value (the lot's units at the sale price). Until W-303 the engine took a lot's turnover at its **buy** price, so a stored `EXIT_LOAD` on a sale above cost is too low and one below cost too high; the row keeps the sale's `turnover` and each line's `base_amount` (the lot's cost) but not the lots, so it cannot be recomputed from the row alone. The count lists users with rows carrying an exit load; a non-zero count gets a correction row with its own plan (no recompute route exists, CE-4 / W-308) | `db.user_charges.aggregate([{ $match: { "amount_by_code.EXIT_LOAD": { $gt: 0 } } }, { $group: { _id: "$email", exit_load_rows: { $sum: 1 } } }])` — one row per user (stored email, §0.7), owner, `mongosh`, after deploy, on each database; read-only; safe to run again; nothing to undo; log the count in the run log |
| 3.21 | **W-304** (D-623 C4a) | A card is now chosen for a trade only if one of its active rules declares the trade's event (BUY, SELL, AMC_CYCLE). Until W-304 an unscoped card with no rule for the event, such as a broker's maintenance card (AMC_CYCLE only) or the statutory account card, could stand in for a missing trade card. The trade was then stored as `RESOLVED`, or `NO_MATCHING_RULES`, with the missing card's charges silently absent and not listed by `/gaps`. With the shipped cards there is no such hole from 1 Apr 2025, so every count should be 0; a database's own cards may differ. The count lists, per database, the BUY/SELL rows whose `schedule_code` or `statutory_schedule_code` names a card with no active rule for the row's event. A non-zero count gets a correction row with its own plan (no recompute route exists, CE-4 / W-308) | `db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name).filter(n => !["admin","local","config"].includes(n)).filter(n => db.getSiblingDB(n).getCollectionNames().includes("user_charges")).map(n => { const covers = c => ({ $cond: [{ $eq: [{ $size: c }, 0] }, true, { $anyElementTrue: [{ $map: { input: { $ifNull: [{ $first: { $map: { input: c, as: "s", in: "$$s.rules" } } }, []] }, as: "r", in: { $and: [{ $eq: ["$$r.active", true] }, { $in: ["$event", { $ifNull: ["$$r.events", []] }] }] } } }] }] }); const r = db.getSiblingDB(n).user_charges.aggregate([{ $match: { event: { $in: ["BUY", "SELL"] } } }, { $lookup: { from: "charge_schedules", localField: "schedule_code", foreignField: "schedule_code", as: "card" } }, { $lookup: { from: "charge_schedules", localField: "statutory_schedule_code", foreignField: "schedule_code", as: "law" } }, { $project: { broker: covers("$card"), law: covers("$law") } }, { $group: { _id: null, trade_rows: { $sum: 1 }, broker_card_without_the_event: { $sum: { $cond: ["$broker", 0, 1] } }, statutory_card_without_the_event: { $sum: { $cond: ["$law", 0, 1] } } } }]).toArray()[0] ?? { trade_rows: 0, broker_card_without_the_event: 0, statutory_card_without_the_event: 0 }; return { database: n, trade_rows: r.trade_rows, broker_card_without_the_event: r.broker_card_without_the_event, statutory_card_without_the_event: r.statutory_card_without_the_event }; }) }])` — one row per database, owner, console, after deploy, on each cluster; read-only; safe to run again; nothing to undo. Tried on a throwaway `mongo:8.0.4` with hand-made rows: 4 trade rows, 2 broker (a maintenance card on a sale, a card whose only rule is switched off), 1 statutory (the account card on a buy), as worked by hand; a database without `user_charges` is skipped. Log the three counts in the run log |

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

// 3.6 — bonus actions without an allotment date (W-088k4a, D-307): performing them is refused until it is entered
db.corporate_action.find({ type: "BONUS", allotment_date: { $exists: false } }, { stock_code: 1, record_date: 1, ex_date: 1 })

// 3.7 — land or building rows entered on the trade paths before W-088g5's guard (D-433); one query over every app database
db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
  .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n); return {
    database: n,
    transactions: d.transactions.countDocuments({ asset_type: "LAND_OR_BUILDING" }),
    assets: d.assets.countDocuments({ asset_type: "LAND_OR_BUILDING" }),
    trade_outcomes: d.trade_outcomes.countDocuments({ asset_type: "LAND_OR_BUILDING" }) }; }) }])

// 3.8 — lots with nil charges whose source transaction entered a charge above nil (W-223, D-437); one query over every app database
db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
  .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n);
    const ids = lot => lot.buy_transaction_ids.flatMap(id => ObjectId.isValid(id) ? [id, new ObjectId(id)] : [id]);
    return { database: n, lots: d.assets.find({ broker_charges: 0, "buy_transaction_ids.0": { $exists: true } },
        { buy_transaction_ids: 1 }).toArray()
      .filter(lot => d.transactions.countDocuments({ _id: { $in: ids(lot) }, broker_charges: { $gt: 0 } }) > 0).length,
      sales: d.trade_outcomes.find({ sell_broker_charges: 0, sell_charge_breakup: {}, source_sell_transaction_id: { $exists: true } },
        { source_sell_transaction_id: 1 }).toArray().map(r => ({ buy_transaction_ids: [r.source_sell_transaction_id] }))
      .filter(lot => d.transactions.countDocuments({ _id: { $in: ids(lot) }, broker_charges: { $gt: 0 } }) > 0).length }; }) }])

// 3.9 — splits already performed, and every stored split's ratio (W-213, W-216, D-438); one query over every app database
db.aggregate([{ $documents: db.adminCommand({ listDatabases: 1, nameOnly: true }).databases.map(x => x.name)
  .filter(n => !["admin", "local", "config"].includes(n)).map(n => { const d = db.getSiblingDB(n); return {
    database: n,
    performed: d.lastly_performed_corporate_action.countDocuments({ action_type: "STOCK_SPLIT" }),
    ratios: d.corporate_action.distinct("ratio", { type: "STOCK_SPLIT" }) }; }) }])

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
  2026-09-12 (run log), so no stored data needed correcting.
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
curl -sS -X POST "$BASE/charge-accounts/user/$USER_EMAIL" -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{
    "brokerName":"ZERODHA","dematAccountId":"1208160000000001","accountHolder":"self",
    "amcFrequency":"ANNUALLY","openedOn":"2024-01-01"
  }'
```

### 5.2 Recurring operations · `SUPER_USER`

| Operation | Command | Cadence | If it is never run |
|---|---|---|---|
| **AMC cycle** | `POST /charges/amc/impose` | quarterly / annually, per account frequency | No maintenance charge is billed. Idempotent per account and period: running it twice bills once |
| **Rate verification** | `GET /charge-schedules/unverified` | every 90 days | Cards age silently. The endpoint lists anything older than 90 days; that is the reminder (ADR-26) |
| **Drift check** | `GET /charge-schedules/drift` | every deploy | A card edited in the repository never reaches a database that already seeded it: the seeder skips it. Drift makes that visible |
| **Publishing a rate change** | `POST /charge-schedules` | when a broker changes rates | **Never edit a deployed card** (ADR-26). Publish a successor; it supersedes the incumbent in the same transaction. Rehearsal: `staging-runbook.md` §7 |

### 5.3 History before the engine is not priced

Trades made before the engine priced them carry no computed charge, and **there is no way to give them one**. ADR-32
applies the cutover forward only; the endpoint that could re-price history was removed for that reason. A charge
report over an earlier period is sparse, which is correct. Pricing history, if wanted, is the
[priced-portfolio epic](../epics/priced-portfolio.md), and needs historical rate cards first.

### 5.4 If something goes wrong

**Stop the engine without a deploy:** `app.charges.engine-enabled: false`. Simulate and the AMC cycle answer **503**,
and nothing is recorded. Reads keep working (charge history, gaps, the reconciliation report), which is what
you need to decide whether to turn it back on.

**Stop only cost-basis changes**, keeping the engine recording for comparison: `app.charges.authoritative: false`. New
buys go back to the client-supplied `brokerCharges`. Trades already written keep the cost basis they were given;
nothing rewrites a stored `AssetEntity`.

| Symptom | Where to look |
|---|---|
| Charges are zero | `GET /user-charges/user/{email}/gaps`: it names the reason per trade |
| Startup warned about rate cards | §1B; grep the log for `NO RATE CARDS` |
| A charge looks wrong | `GET /user-charges/user/{email}/transaction/{id}`: the contract note, line by line, with the `scheduleCode` that priced it |
| Figures disagree with the client's | `GET /user-charges/user/{email}/reconciliation` |

### 5.5 Known limits — recorded, not defects

Full list in `docs/charges-engine/README.md` §10.
- **A database with no cards prices nothing until §1B runs.**
- **Nothing before 2025-04-01 can be priced:** no card covers it.
- **Only two mutual-fund schemes have exit-load profiles.** Every other fund resolves `NO_INSTRUMENT_PROFILE` and
  accrues no exit load; recorded, never fatal (ADR-24).
- **A rate card written directly to MongoDB is invisible** until the resolver cache is evicted. `POST
  /charge-schedules`, the close endpoint and `POST /charges/seed` evict it. Go through the API.
- **Scheme profiles have no publishing endpoint:** they arrive by seeding, and a profile added by hand needs a
  restart or a seed run.
- **No `POST /charges/recompute`.** Amending a card that has already priced charges has no safe mechanism; designed in
  tech-spec §14.4, not built.
- **V1 `buyStock` / `sellStock` do not use the engine.** They are in live use and deliberately untouched. Only the V2
  endpoints (`/portfolio/user/{email}/transaction/v2`) are priced.

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
   curl -s -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" "$BASE/privacy/breach-notices" \
     -d '{"text": {"description": "<what, how much, when>", "consequences": "<what may follow for them>",
          "mitigation": "<done and being done>", "safetyMeasures": "<what they can do>"},
          "becameAwareAt": "<UTC>", "scope": "EVERY_REGISTERED_USER"}'
   ```

   `201` with `id`, `affected` and `board.reportDueAt` (awareness + `app.privacy.breach.board-report-hours`, 72).
2. **Without delay: tell the Board** the description, nature, extent, timing, location and likely impact (r.7(2)(a)),
   through **the channel the Board publishes** (not stated in the Gazette copies read for W-237c; owner to fill in
   here). Then record it: `POST $BASE/privacy/breach-notices/<id>/board-intimated` → `board.firstIntimatedAt`.
3. **Without delay: email each affected user** the same text from the privacy contact's mailbox (the account email is
   the only mode of communication users register). The text and addresses: `GET $BASE/privacy/breach-notices/<id>/report`.
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

| Date | Database | Step | Run by | Result |
|---|---|---|---|---|
| 2026-09-12 | prod | trade ledger: trades dated 31 March (§4) | owner | `0` |
| 2026-10-20 | prod `Cluster0` | 2.1 count | owner | `rebuildRows` 163, `lots` 146, `lotsSoldMoreThanOnce` 12, `rowsOnThoseLots` 29, `chargedOnThoseLots` 68.99166666666666 |
| 2026-10-20 | staging | 2.1 count | owner | no rebuild rows, so 2.1 not needed |
| 2026-10-20 | staging | W-088u8c checks | owner | `FY` rows 0; `temporary_transactions` absent |
| 2026-10-20 | prod `Cluster0` | W-088u8c checks | owner | `FY` rows **163** (W-086a's rewrite never ran here: prod predates `91359d35`); `temporary_transactions` absent |
| 2026-10-27 | prod `Cluster0` | W-176 counts (one query) | owner | `charge_catalogue` 0 (with the old flag 0); `charge_schedules` 0, `charge_instruments` 0; `user_charges` 0, `charge_accounts` 0; `transactions` 817, `assets` 529; `trade_outcomes` 163 (engine-priced 0, with a term 163, with `deductible_*` 0); `profit_and_loss` 7 (with a charge side 0, without `version` 0); `holding_period_policies` 0 |
| 2026-10-27 | staging `it-staging` | W-176 counts (one query) | owner | `charge_catalogue` 12 (with the old flag 12); `charge_schedules` 11, `charge_instruments` 2; `user_charges` 328, `charge_accounts` 1; `transactions` 1009, `assets` 649; `trade_outcomes` 201 (engine-priced 1, with a term 201, with `deductible_*` 1); `profit_and_loss` 12 (with a charge side 2, without `version` 0); `holding_period_policies` 11 |

**Not yet in this log:** §0.2, §1A, §1B and §2.0 for each database (§2.0 expected on staging and prod: all already
present), 1.3's 0 and the section 3 counts, for each database.
